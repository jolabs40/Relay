"""Serveur WebSocket du relais : un seul point d'entrée, ``/ws?jeton=…``.

Protocole : des objets JSON portant un champ ``type``.

Relais → client
  ``bonjour``        {version, projets, modes, sessions}   à la connexion
  ``session``        {session}                              après chaque changement
  ``session_fermee`` {id}
  ``historique``     {chemin, sessions}                     en réponse à ``historique``
  ``erreur``         {message, ref?}

Client → relais (``ref`` facultatif, renvoyé dans l'erreur éventuelle)
  ``nouvelle``    {chemin, prompt, mode, reprendre?, pieces?}
  ``envoyer``     {session, prompt, pieces?}
                  pieces : [{nom, type_mime, donnees (base64), vignette? (JPEG base64)}]
  ``repondre``    {session, demande, reponse}
  ``interrompre`` {session}
  ``mode``        {session, mode}
  ``fermer``      {session}
  ``historique``  {chemin}
  ``arreter``     {}                                        ferme les sessions, puis le relais
"""

from __future__ import annotations

import asyncio
import hmac
import json
import logging
from http import HTTPStatus
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

from websockets.asyncio.server import ServerConnection, serve
from websockets.exceptions import ConnectionClosed

from .config import Config
from .projets import historique, lister_projets
from .session import MODES, ErreurCommande, FabriqueClient, Session

log = logging.getLogger("relay.serveur")

# 2 : pièces jointes (``pieces`` dans ``nouvelle`` et ``envoyer``).
VERSION_PROTOCOLE = 2


class Relais:
    def __init__(self, config: Config, fabrique_client: FabriqueClient | None = None) -> None:
        self.config = config
        self.sessions: dict[str, Session] = {}
        self.clients: set[ServerConnection] = set()
        self._fabrique = fabrique_client
        self.arret = asyncio.Event()

    # ------------------------------------------------------------------ diffusion

    def _diffuser(self, message: dict) -> None:
        brut = json.dumps(message, ensure_ascii=False)
        for client in list(self.clients):
            asyncio.ensure_future(self._envoyer_brut(client, brut))

    @staticmethod
    async def _envoyer_brut(client: ServerConnection, brut: str) -> None:
        try:
            await client.send(brut)
        except ConnectionClosed:
            pass

    def _session_changee(self, session: Session) -> None:
        self._diffuser({"type": "session", "session": session.instantane()})

    # ------------------------------------------------------------------ connexion

    def verifier_requete(self, connexion: ServerConnection, requete):
        """Refuse tout ce qui n'est pas un client Relay muni du jeton."""
        url = urlsplit(requete.path)
        if url.path == "/sante":
            # Sonde du lanceur : dit que c'est bien un relais qui écoute, sans rien révéler d'autre.
            return connexion.respond(HTTPStatus.OK, "relay\n")
        if url.path != "/ws":
            return connexion.respond(HTTPStatus.NOT_FOUND, "Introuvable\n")
        # Un navigateur envoie toujours Origin : aucune page web ne doit pouvoir piloter Claude.
        if requete.headers.get("Origin"):
            return connexion.respond(HTTPStatus.FORBIDDEN, "Origine refusée\n")
        jeton = (parse_qs(url.query).get("jeton") or [""])[0]
        if not hmac.compare_digest(jeton.encode(), self.config.jeton.encode()):
            return connexion.respond(HTTPStatus.UNAUTHORIZED, "Jeton invalide\n")
        return None

    async def accueillir(self, client: ServerConnection) -> None:
        self.clients.add(client)
        try:
            await client.send(json.dumps(self._bonjour(), ensure_ascii=False))
            async for brut in client:
                await self._traiter(client, brut)
        except ConnectionClosed:
            pass
        finally:
            self.clients.discard(client)

    def _bonjour(self) -> dict:
        return {
            "type": "bonjour",
            "version": VERSION_PROTOCOLE,
            "racine": self.config.racine,
            "projets": lister_projets(self.config.racine),
            "modes": list(MODES),
            "sessions": [s.instantane() for s in self.sessions.values()],
        }

    # ------------------------------------------------------------------ commandes

    async def _traiter(self, client: ServerConnection, brut: str | bytes) -> None:
        ref = None
        try:
            commande = json.loads(brut)
            ref = commande.get("ref")
            reponse = await self.executer(commande)
            if reponse is not None:
                await client.send(json.dumps(reponse, ensure_ascii=False))
        except (ErreurCommande, ValueError, KeyError, TypeError) as erreur:
            message = str(erreur) if isinstance(erreur, ErreurCommande) else f"Commande invalide : {erreur!r}"
            await client.send(json.dumps({"type": "erreur", "message": message, "ref": ref}, ensure_ascii=False))
        except Exception as erreur:
            log.exception("Commande en échec")
            await client.send(json.dumps({"type": "erreur", "message": str(erreur), "ref": ref}, ensure_ascii=False))

    def _session(self, commande: dict) -> Session:
        session = self.sessions.get(commande["session"])
        if session is None:
            raise ErreurCommande("Session inconnue")
        return session

    def _chemin_autorise(self, chemin: str) -> Path:
        """Claude ne travaille que sous la racine des projets."""
        cible = Path(chemin).resolve()
        racine = Path(self.config.racine).resolve()
        if cible != racine and racine not in cible.parents:
            raise ErreurCommande("Dossier hors de la racine des projets")
        if not cible.is_dir():
            raise ErreurCommande("Dossier introuvable")
        return cible

    async def executer(self, commande: dict) -> dict | None:
        type_ = commande["type"]
        if type_ == "nouvelle":
            cible = self._chemin_autorise(commande["chemin"])
            kwargs = {"fabrique_client": self._fabrique} if self._fabrique else {}
            session = Session(
                projet=cible.name,
                cwd=str(cible),
                mode=commande.get("mode") or "default",
                reprendre=commande.get("reprendre") or None,
                notifier=self._session_changee,
                **kwargs,
            )
            self.sessions[session.id] = session
            session.demarrer()
            prompt = (commande.get("prompt") or "").strip()
            pieces = commande.get("pieces") or []
            if prompt or pieces:
                session.envoyer(prompt, pieces)
            else:
                self._session_changee(session)
            return None
        if type_ == "envoyer":
            self._session(commande).envoyer(commande.get("prompt", ""), commande.get("pieces") or [])
            return None
        if type_ == "repondre":
            self._session(commande).repondre(commande["demande"], commande["reponse"])
            return None
        if type_ == "interrompre":
            await self._session(commande).interrompre()
            return None
        if type_ == "mode":
            await self._session(commande).changer_mode(commande["mode"])
            return None
        if type_ == "fermer":
            session = self._session(commande)
            await session.fermer()
            self.sessions.pop(session.id, None)
            self._diffuser({"type": "session_fermee", "id": session.id})
            return None
        if type_ == "historique":
            cible = self._chemin_autorise(commande["chemin"])
            sessions = await asyncio.to_thread(historique, str(cible))
            return {"type": "historique", "chemin": commande["chemin"], "sessions": sessions}
        if type_ == "arreter":
            self.arret.set()
            return None
        raise ErreurCommande(f"Commande inconnue : {type_}")

    async def fermer_tout(self) -> None:
        for session in list(self.sessions.values()):
            await session.fermer()


async def servir(relais: Relais, hote: str, port: int, pret: asyncio.Event | None = None) -> None:
    async with serve(
        relais.accueillir,
        hote,
        port,
        process_request=relais.verifier_requete,
        # Les pièces jointes voyagent en base64 dans la commande : jusqu'à quelques fichiers de 20 Mo.
        max_size=64 * 1024 * 1024,
    ) as serveur:
        if pret:
            pret.set()
        log.info("Relais à l'écoute sur %s:%s", hote, port)
        try:
            await relais.arret.wait()
            log.info("Arrêt demandé par un client")
        finally:
            # Chaque session ferme son processus Claude : rien ne reste orphelin derrière le relais.
            await relais.fermer_tout()
