"""Une session Claude pilotée à distance.

Le relais ne fait remonter que ce qui demande l'utilisateur ou lui rend des comptes :

- ``prompt``     — ce qu'il a envoyé (pour qu'il relise le fil) ;
- ``question``   — l'outil AskUserQuestion : questions à choix, réponse attendue ;
- ``permission`` — un outil à autoriser (hors mode bypass) ;
- ``plan``       — la proposition du mode plan (ExitPlanMode), à approuver ou refuser ;
- ``resultat``   — le compte rendu de fin de tour ;
- ``info``       — interruption, erreur.

Tout le reste — lectures, éditions, réflexion — se résume à une ligne d'``activite`` qui
s'écrase à chaque outil, et ne s'accumule jamais.
"""

from __future__ import annotations

import asyncio
import json
import logging
import time
import uuid
from collections.abc import Callable
from pathlib import Path
from typing import Any

from claude_agent_sdk import (
    AssistantMessage,
    ClaudeAgentOptions,
    ClaudeSDKClient,
    ResultMessage,
    SystemMessage,
    TextBlock,
    ToolResultBlock,
    ToolUseBlock,
    UserMessage,
)
from claude_agent_sdk.types import (
    PermissionResultAllow,
    PermissionResultDeny,
    PermissionUpdate,
    ToolPermissionContext,
)

from .bilan import Bilan
from .pieces import Pieces, dossier_pieces, preparer

log = logging.getLogger("relay.session")

MODES = ("default", "acceptEdits", "plan", "bypassPermissions")

# Ajouté au prompt système de Claude Code : il doit savoir qu'aucun terminal ne le regarde.
CONSIGNE_RELAIS = (
    "Cette session est pilotée à distance par Relay, sans terminal interactif : "
    "l'utilisateur ne voit que tes questions (outil AskUserQuestion), les permissions, "
    "ton plan et ton message final. Ne renomme pas la session et n'injecte rien dans une "
    "console. Quand un choix revient à l'utilisateur, pose-le avec AskUserQuestion plutôt "
    "qu'en texte libre. Termine chaque tour par un compte rendu bref et autonome : "
    "c'est la seule chose qu'il lira."
)

FabriqueClient = Callable[[ClaudeAgentOptions], Any]


def _maintenant() -> int:
    return int(time.time() * 1000)


def _court(texte: str, limite: int) -> str:
    texte = " ".join(str(texte).split())
    return texte if len(texte) <= limite else texte[: limite - 1] + "…"


def decrire_activite(nom: str, entree: dict) -> str:
    """Une ligne lisible pour l'outil en cours : « Edit Main.kt », « Bash ./gradlew test »."""
    for cle in ("file_path", "notebook_path", "path"):
        if entree.get(cle):
            return f"{nom} {Path(str(entree[cle])).name}"
    for cle in ("description", "command", "pattern", "query", "url", "prompt"):
        if entree.get(cle):
            return f"{nom} {_court(entree[cle], 70)}"
    return nom


def detailler_permission(nom: str, entree: dict) -> str:
    """Ce qu'il faut voir pour décider d'autoriser un outil."""
    if nom in ("Bash", "PowerShell"):
        return str(entree.get("command", ""))
    if nom == "Edit":
        return (
            f"{entree.get('file_path', '')}\n\n"
            f"- {_court(entree.get('old_string', ''), 600)}\n"
            f"+ {_court(entree.get('new_string', ''), 600)}"
        )
    if nom == "Write":
        contenu = str(entree.get("content", ""))
        return f"{entree.get('file_path', '')}\n\n{contenu[:1500]}"
    return json.dumps(entree, ensure_ascii=False, indent=2)[:2000]


async def _un_message(blocs: list[dict]):
    """Le SDK n'accepte un contenu à blocs que sous forme de flux de messages."""
    yield {"type": "user", "parent_tool_use_id": None, "message": {"role": "user", "content": blocs}}


class ErreurCommande(Exception):
    """Commande du client impossible dans l'état présent de la session."""


class Session:
    def __init__(
        self,
        projet: str,
        cwd: str,
        mode: str,
        notifier: Callable[["Session"], None],
        reprendre: str | None = None,
        fabrique_client: FabriqueClient = ClaudeSDKClient,
    ) -> None:
        if mode not in MODES:
            raise ErreurCommande(f"Mode inconnu : {mode}")
        self.id = uuid.uuid4().hex[:12]
        self.projet = projet
        self.cwd = cwd
        self.mode = mode
        self.reprendre = reprendre
        self.titre = ""
        self.etat = "demarrage"  # demarrage, inactive, travaille, attente, erreur, fermee
        self.activite = ""
        self.claude_session_id = reprendre or ""
        self.cree_a = _maintenant()
        self.maj_a = self.cree_a
        self.evenements: list[dict] = []
        self._notifier = notifier
        self._fabrique = fabrique_client
        # Chaque prompt en attente : son texte, et ses pièces déjà préparées.
        self._file: asyncio.Queue[tuple[str, Pieces]] = asyncio.Queue()
        self._demandes: dict[str, asyncio.Future] = {}
        self._client: Any = None
        self._tache: asyncio.Task | None = None
        self._dernier_texte = ""
        self._compteur = 0
        self._occupe = False
        # Le tour en cours : son début et ce qu'il a déjà fait, montrés pendant qu'il travaille.
        self._debut_tour = 0
        self._bilan = Bilan()

    # ------------------------------------------------------------------ état public

    def instantane(self) -> dict:
        return {
            "id": self.id,
            "projet": self.projet,
            "cwd": self.cwd,
            "titre": self.titre,
            "etat": self.etat,
            "mode": self.mode,
            "activite": self.activite,
            "claude_session_id": self.claude_session_id,
            "en_file": self._file.qsize(),
            "tour": {"debut": self._debut_tour, "bilan": self._bilan.en_dict()} if self._occupe else None,
            "cree_a": self.cree_a,
            "maj_a": self.maj_a,
            "evenements": self.evenements,
        }

    def _changer(self, **champs: Any) -> None:
        for cle, valeur in champs.items():
            setattr(self, cle, valeur)
        self.maj_a = _maintenant()
        self._notifier(self)

    def _ajouter(self, type_: str, **contenu: Any) -> dict:
        self._compteur += 1
        evenement = {"id": self._compteur, "type": type_, "horodatage": _maintenant(), **contenu}
        self.evenements.append(evenement)
        self._changer()
        return evenement

    def _etat_courant(self) -> str:
        if self._demandes:
            return "attente"
        return "travaille" if self._occupe else "inactive"

    # ------------------------------------------------------------------ cycle de vie

    def demarrer(self) -> None:
        self._tache = asyncio.create_task(self._executer(), name=f"session-{self.id}")

    async def _executer(self) -> None:
        options = ClaudeAgentOptions(
            cwd=self.cwd,
            permission_mode=self.mode,
            resume=self.reprendre,
            can_use_tool=self._decider,
            # Les fichiers joints y sont déposés : Claude doit pouvoir les lire hors du projet.
            add_dirs=[str(dossier_pieces())],
            setting_sources=["user", "project", "local"],
            system_prompt={"type": "preset", "preset": "claude_code", "append": CONSIGNE_RELAIS},
        )
        try:
            async with self._fabrique(options) as client:
                self._client = client
                self._changer(etat="inactive")
                while True:
                    texte, pieces = await self._file.get()
                    await self._tour(texte, pieces)
        except asyncio.CancelledError:
            raise
        except Exception as erreur:  # le processus Claude est mort, ou n'a pas démarré
            log.exception("Session %s interrompue", self.id)
            self._liberer_demandes("Session arrêtée")
            self._ajouter("info", texte=f"Session arrêtée : {erreur}", erreur=True)
            self._changer(etat="erreur", activite="")

    async def _tour(self, texte: str, pieces: Pieces) -> None:
        self._occupe = True
        self._dernier_texte = ""
        self._debut_tour = _maintenant()
        self._bilan = Bilan()
        self._changer(etat=self._etat_courant(), activite="Réflexion…")
        try:
            texte = texte + pieces.texte_chemins()
            if pieces.images:
                # Un message à blocs : le texte, puis les images, comme un collage dans le terminal.
                blocs = ([{"type": "text", "text": texte}] if texte.strip() else []) + pieces.images
                await self._client.query(_un_message(blocs))
            else:
                await self._client.query(texte)
            async for message in self._client.receive_response():
                self._traiter(message)
        finally:
            self._occupe = False
            self._changer(etat=self._etat_courant(), activite="")

    def _traiter(self, message: Any) -> None:
        if isinstance(message, SystemMessage) and message.subtype == "init":
            sid = message.data.get("session_id")
            if sid and sid != self.claude_session_id:
                self._changer(claude_session_id=sid)
        elif isinstance(message, AssistantMessage):
            for bloc in message.content:
                if isinstance(bloc, ToolUseBlock):
                    self._bilan.lance(bloc.id, bloc.name, bloc.input or {})
                    self._changer(activite=decrire_activite(bloc.name, bloc.input or {}))
                elif isinstance(bloc, TextBlock) and bloc.text.strip():
                    self._dernier_texte = bloc.text
        elif isinstance(message, UserMessage) and isinstance(message.content, list):
            resultats = [b for b in message.content if isinstance(b, ToolResultBlock)]
            # Le détail (structuredPatch…) ne vaut que pour un résultat seul dans son message.
            detail = message.tool_use_result if len(resultats) == 1 else None
            if any([self._bilan.termine(b.tool_use_id, bool(b.is_error), detail) for b in resultats]):
                self._changer()
        elif isinstance(message, ResultMessage):
            if message.session_id:
                self.claude_session_id = message.session_id
            texte = message.result or self._dernier_texte
            self._ajouter(
                "resultat",
                texte=texte,
                erreur=bool(message.is_error),
                duree_ms=message.duration_ms,
                cout_usd=message.total_cost_usd,
                tours=message.num_turns,
                debut=self._debut_tour,
                bilan=self._bilan.en_dict(),
            )

    async def fermer(self) -> None:
        self._liberer_demandes("Session fermée")
        if self._tache:
            self._tache.cancel()
            try:
                await self._tache
            except (asyncio.CancelledError, Exception):
                pass
        self.etat = "fermee"

    # ------------------------------------------------------------------ commandes du client

    def envoyer(self, prompt: str, pieces: list[dict] | None = None) -> None:
        prompt = prompt.strip()
        if not prompt and not pieces:
            raise ErreurCommande("Prompt vide")
        if self.etat in ("erreur", "fermee"):
            raise ErreurCommande("Session arrêtée")
        try:
            preparees = preparer(self.id, pieces)
        except ValueError as erreur:
            raise ErreurCommande(str(erreur)) from erreur
        if not self.titre:
            self.titre = _court(prompt or preparees.affichage[0]["nom"], 60)
        self._ajouter("prompt", texte=prompt, pieces=preparees.affichage)
        self._file.put_nowait((prompt, preparees))
        self._changer()

    async def interrompre(self) -> None:
        self._liberer_demandes("Interrompu par l'utilisateur")
        while not self._file.empty():
            self._file.get_nowait()
        if self._client and self._occupe:
            await self._client.interrupt()
            self._ajouter("info", texte="Interrompu")

    async def changer_mode(self, mode: str) -> None:
        if mode not in MODES:
            raise ErreurCommande(f"Mode inconnu : {mode}")
        if self._client:
            await self._client.set_permission_mode(mode)
        self._changer(mode=mode)

    def repondre(self, demande: str, reponse: dict) -> None:
        futur = self._demandes.get(demande)
        if futur is None or futur.done():
            raise ErreurCommande("Cette demande n'attend plus de réponse")
        futur.set_result(reponse)

    # ------------------------------------------------------------------ permissions

    def _liberer_demandes(self, motif: str) -> None:
        for futur in self._demandes.values():
            if not futur.done():
                futur.set_result({"annule": motif})

    async def _attendre(self, evenement: dict) -> dict:
        futur: asyncio.Future = asyncio.get_running_loop().create_future()
        self._demandes[evenement["demande"]] = futur
        self._changer(etat="attente")
        try:
            reponse = await futur
        finally:
            self._demandes.pop(evenement["demande"], None)
        evenement["en_attente"] = False
        evenement["reponse"] = reponse
        self._changer(etat=self._etat_courant())
        return reponse

    async def _decider(self, nom: str, entree: dict, contexte: ToolPermissionContext):
        demande = uuid.uuid4().hex[:12]
        if nom == "AskUserQuestion":
            return await self._question(demande, entree)
        if nom == "ExitPlanMode":
            return await self._plan(demande, entree)

        evenement = self._ajouter(
            "permission",
            demande=demande,
            en_attente=True,
            outil=nom,
            resume=decrire_activite(nom, entree),
            detail=detailler_permission(nom, entree),
        )
        reponse = await self._attendre(evenement)
        if reponse.get("annule"):
            return PermissionResultDeny(message=reponse["annule"], interrupt=True)
        if reponse.get("autoriser"):
            return PermissionResultAllow(updated_input=entree)
        motif = reponse.get("message") or "L'utilisateur a refusé."
        return PermissionResultDeny(message=motif)

    async def _question(self, demande: str, entree: dict):
        questions = entree.get("questions", [])
        evenement = self._ajouter("question", demande=demande, en_attente=True, questions=questions)
        reponse = await self._attendre(evenement)
        if reponse.get("annule"):
            return PermissionResultDeny(message=reponse["annule"], interrupt=True)
        reponses = reponse.get("reponses") or {}
        return PermissionResultAllow(updated_input={**entree, "answers": reponses})

    async def _plan(self, demande: str, entree: dict):
        evenement = self._ajouter(
            "plan", demande=demande, en_attente=True, plan=str(entree.get("plan", ""))
        )
        reponse = await self._attendre(evenement)
        if reponse.get("annule"):
            return PermissionResultDeny(message=reponse["annule"], interrupt=True)
        if not reponse.get("approuver"):
            motif = reponse.get("message") or "Plan refusé, revois-le."
            return PermissionResultDeny(message=motif)
        suite = reponse.get("mode") or "acceptEdits"
        if suite not in MODES or suite == "plan":
            suite = "acceptEdits"
        self._changer(mode=suite)
        return PermissionResultAllow(
            updated_input=entree,
            updated_permissions=[PermissionUpdate(type="setMode", mode=suite, destination="session")],
        )
