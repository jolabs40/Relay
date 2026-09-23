"""Le relais, sans Claude : un faux client rejoue ce que ferait le SDK."""

from __future__ import annotations

import asyncio
import json
from pathlib import Path

import pytest
from claude_agent_sdk import AssistantMessage, ResultMessage, TextBlock, ToolUseBlock
from claude_agent_sdk.types import PermissionResultAllow, PermissionResultDeny
from websockets.asyncio.client import connect
from websockets.exceptions import InvalidStatus

from relay.config import Config
from relay.serveur import Relais, servir
from relay.session import Session, decrire_activite


def resultat(texte: str | None = "Fini.") -> ResultMessage:
    return ResultMessage(
        subtype="success", duration_ms=1200, duration_api_ms=1000, is_error=False,
        num_turns=2, session_id="sid-claude", total_cost_usd=0.01, result=texte,
    )


class FauxClient:
    """Chaque prompt déclenche le scénario : une liste d'appels d'outils puis un résultat."""

    def __init__(self, options, scenario):
        self.options = options
        self.scenario = scenario
        self.decisions: list = []
        self.interrompu = False
        self.modes: list[str] = []

    async def __aenter__(self):
        return self

    async def __aexit__(self, *exc):
        return False

    async def query(self, prompt):
        if isinstance(prompt, str):
            self.prompt = prompt
        else:  # flux de messages à blocs (images)
            self.prompt = [message async for message in prompt]

    async def receive_response(self):
        for nom, entree in self.scenario:
            yield AssistantMessage(content=[ToolUseBlock(id="t", name=nom, input=entree)], model="m")
            decision = await self.options.can_use_tool(nom, entree, None)
            self.decisions.append(decision)
            if isinstance(decision, PermissionResultDeny) and decision.interrupt:
                break
        yield AssistantMessage(content=[TextBlock(text="Dernier mot.")], model="m")
        yield resultat()

    async def interrupt(self):
        self.interrompu = True

    async def set_permission_mode(self, mode):
        self.modes.append(mode)


def fabrique(scenario, clients: list):
    def creer(options):
        client = FauxClient(options, scenario)
        clients.append(client)
        return client
    return creer


async def attendre(condition, delai=2.0):
    fin = asyncio.get_running_loop().time() + delai
    while not condition():
        if asyncio.get_running_loop().time() > fin:
            raise AssertionError("condition jamais remplie")
        await asyncio.sleep(0.01)


def lancer(coro):
    return asyncio.run(coro)


QUESTION = {"questions": [{"question": "Thé ou café ?", "header": "Boisson", "multiSelect": False,
                           "options": [{"label": "Thé", "description": ""}, {"label": "Café", "description": ""}]}]}


def test_question_remonte_et_reponse_repart():
    async def scenario():
        clients: list = []
        session = Session("P", ".", "bypassPermissions", lambda s: None,
                          fabrique_client=fabrique([("AskUserQuestion", QUESTION)], clients))
        session.demarrer()
        session.envoyer("Pose-moi une question")
        await attendre(lambda: session.etat == "attente")
        question = session.evenements[-1]
        assert question["type"] == "question" and question["en_attente"]
        assert question["questions"][0]["question"] == "Thé ou café ?"

        session.repondre(question["demande"], {"reponses": {"Thé ou café ?": "Thé"}})
        await attendre(lambda: session.etat == "inactive")
        decision = clients[0].decisions[0]
        assert isinstance(decision, PermissionResultAllow)
        assert decision.updated_input["answers"] == {"Thé ou café ?": "Thé"}
        assert [e["type"] for e in session.evenements] == ["prompt", "question", "resultat"]
        assert session.evenements[-1]["texte"] == "Fini."
        assert session.claude_session_id == "sid-claude"
        assert session.titre == "Pose-moi une question"
        await session.fermer()
    lancer(scenario())


def test_permission_refusee_avec_motif():
    async def scenario():
        clients: list = []
        session = Session("P", ".", "default", lambda s: None,
                          fabrique_client=fabrique([("Bash", {"command": "rm -rf build"})], clients))
        session.demarrer()
        session.envoyer("Nettoie")
        await attendre(lambda: session.etat == "attente")
        permission = session.evenements[-1]
        assert permission["outil"] == "Bash" and permission["detail"] == "rm -rf build"
        session.repondre(permission["demande"], {"autoriser": False, "message": "Pas build"})
        await attendre(lambda: session.etat == "inactive")
        decision = clients[0].decisions[0]
        assert isinstance(decision, PermissionResultDeny) and decision.message == "Pas build"
        await session.fermer()
    lancer(scenario())


def test_plan_approuve_change_de_mode():
    async def scenario():
        clients: list = []
        session = Session("P", ".", "plan", lambda s: None,
                          fabrique_client=fabrique([("ExitPlanMode", {"plan": "# Étapes"})], clients))
        session.demarrer()
        session.envoyer("Planifie")
        await attendre(lambda: session.etat == "attente")
        plan = session.evenements[-1]
        assert plan["type"] == "plan" and plan["plan"] == "# Étapes"
        session.repondre(plan["demande"], {"approuver": True, "mode": "bypassPermissions"})
        await attendre(lambda: session.etat == "inactive")
        decision = clients[0].decisions[0]
        assert isinstance(decision, PermissionResultAllow)
        assert decision.updated_permissions[0].mode == "bypassPermissions"
        assert session.mode == "bypassPermissions"
        await session.fermer()
    lancer(scenario())


def test_interruption_libere_la_demande_en_attente():
    async def scenario():
        clients: list = []
        session = Session("P", ".", "default", lambda s: None,
                          fabrique_client=fabrique([("AskUserQuestion", QUESTION)], clients))
        session.demarrer()
        session.envoyer("Question")
        await attendre(lambda: session.etat == "attente")
        await session.interrompre()
        await attendre(lambda: session.etat == "inactive")
        decision = clients[0].decisions[0]
        assert isinstance(decision, PermissionResultDeny) and decision.interrupt
        assert clients[0].interrompu
        with pytest.raises(Exception):
            session.repondre(session.evenements[1]["demande"], {"reponses": {}})
        await session.fermer()
    lancer(scenario())


def test_activite_lisible():
    assert decrire_activite("Edit", {"file_path": "C:/a/b/Main.kt"}) == "Edit Main.kt"
    assert decrire_activite("Bash", {"command": "./gradlew test"}) == "Bash ./gradlew test"
    assert decrire_activite("TodoWrite", {}) == "TodoWrite"


# ---------------------------------------------------------------------- serveur


def _config(racine: Path, port: int) -> Config:
    return Config(port=port, jeton="secret-de-test", racine=str(racine))


def test_serveur_refuse_sans_jeton_et_depuis_un_navigateur(tmp_path):
    async def scenario():
        relais = Relais(_config(tmp_path, 0))
        pret = asyncio.Event()
        port = 18787
        tache = asyncio.create_task(servir(relais, "127.0.0.1", port, pret))
        await pret.wait()
        with pytest.raises(InvalidStatus) as refus:
            async with connect(f"ws://127.0.0.1:{port}/ws?jeton=faux"):
                pass
        assert refus.value.response.status_code == 401
        with pytest.raises(InvalidStatus) as refus:
            async with connect(f"ws://127.0.0.1:{port}/ws?jeton=secret-de-test", origin="https://evil.example"):
                pass
        assert refus.value.response.status_code == 403
        # La sonde du lanceur, elle, passe sans jeton et ne dit que « relay ».
        lecteur, ecrivain = await asyncio.open_connection("127.0.0.1", port)
        ecrivain.write(b"GET /sante HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n")
        reponse = await lecteur.read()
        ecrivain.close()
        assert reponse.startswith(b"HTTP/1.1 200") and reponse.endswith(b"relay\n")
        relais.arret.set()
        await tache
    lancer(scenario())


def test_serveur_bout_en_bout(tmp_path):
    (tmp_path / "Projet").mkdir()
    (tmp_path / "Projet" / "CLAUDE.md").write_text("x")

    async def scenario():
        clients: list = []
        relais = Relais(_config(tmp_path, 0), fabrique_client=fabrique([("AskUserQuestion", QUESTION)], clients))
        pret = asyncio.Event()
        port = 18788
        tache = asyncio.create_task(servir(relais, "127.0.0.1", port, pret))
        await pret.wait()
        async with connect(f"ws://127.0.0.1:{port}/ws?jeton=secret-de-test") as ws:
            bonjour = json.loads(await ws.recv())
            assert bonjour["type"] == "bonjour"
            assert [p["nom"] for p in bonjour["projets"]] == [tmp_path.name, "Projet"]

            await ws.send(json.dumps({"type": "nouvelle", "chemin": "C:/Windows", "prompt": "x", "ref": "r1"}))
            erreur = json.loads(await ws.recv())
            assert erreur == {"type": "erreur", "message": "Dossier hors de la racine des projets", "ref": "r1"}

            await ws.send(json.dumps({"type": "nouvelle", "chemin": str(tmp_path / "Projet"),
                                      "prompt": "Question ?", "mode": "default"}))
            question = None
            while question is None:
                message = json.loads(await ws.recv())
                evenements = message.get("session", {}).get("evenements", [])
                if evenements and evenements[-1]["type"] == "question" and evenements[-1]["en_attente"]:
                    question = (message["session"]["id"], evenements[-1]["demande"])
            await ws.send(json.dumps({"type": "repondre", "session": question[0], "demande": question[1],
                                      "reponse": {"reponses": {"Thé ou café ?": "Café"}}}))
            while True:
                message = json.loads(await ws.recv())
                session = message.get("session", {})
                if session.get("etat") == "inactive" and session["evenements"][-1]["type"] == "resultat":
                    break
            await ws.send(json.dumps({"type": "fermer", "session": question[0]}))
            fermee = json.loads(await ws.recv())
            assert fermee == {"type": "session_fermee", "id": question[0]}
        tache.cancel()
    lancer(scenario())


# ---------------------------------------------------------------------- pièces jointes

import base64


def _b64(octets: bytes) -> str:
    return base64.b64encode(octets).decode()


def test_image_en_bloc_et_fichier_sur_disque(tmp_path, monkeypatch):
    monkeypatch.setenv("RELAY_DONNEES", str(tmp_path))

    async def scenario():
        clients: list = []
        session = Session("P", ".", "bypassPermissions", lambda s: None, fabrique_client=fabrique([], clients))
        session.demarrer()
        session.envoyer("Regarde", [
            {"nom": "capture.png", "type_mime": "image/png", "donnees": _b64(b"PNG-faux"), "vignette": "dmln"},
            {"nom": r"..\..\rapport.pdf", "type_mime": "application/pdf", "donnees": _b64(b"%PDF-1.7")},
        ])
        await attendre(lambda: session.etat == "inactive" and session.evenements[-1]["type"] == "resultat")

        message = clients[0].prompt[0]["message"]
        texte, image = message["content"]
        assert image == {"type": "image", "source": {"type": "base64", "media_type": "image/png", "data": _b64(b"PNG-faux")}}
        # Le nom est réduit à lui-même : aucune sortie du dossier des pièces.
        chemin = tmp_path / "pieces" / session.id / "rapport.pdf"
        assert chemin.read_bytes() == b"%PDF-1.7"
        assert texte["text"].startswith("Regarde") and str(chemin) in texte["text"]

        # Le fil garde nom, type et vignette — jamais l'image entière.
        pieces = session.evenements[0]["pieces"]
        assert [p["nom"] for p in pieces] == ["capture.png", "rapport.pdf"]
        assert pieces[0]["vignette"] == "dmln" and pieces[1]["vignette"] is None
        assert "donnees" not in json.dumps(session.instantane())
        await session.fermer()
    lancer(scenario())


def test_piece_seule_sans_texte(tmp_path, monkeypatch):
    monkeypatch.setenv("RELAY_DONNEES", str(tmp_path))

    async def scenario():
        clients: list = []
        session = Session("P", ".", "default", lambda s: None, fabrique_client=fabrique([], clients))
        session.demarrer()
        session.envoyer("", [{"nom": "ecran.png", "type_mime": "image/png", "donnees": _b64(b"x")}])
        await attendre(lambda: session.evenements[-1]["type"] == "resultat")
        assert [bloc["type"] for bloc in clients[0].prompt[0]["message"]["content"]] == ["image"]
        assert session.titre == "ecran.png"
        await session.fermer()
    lancer(scenario())


def test_piece_illisible_ou_trop_lourde_refusee(tmp_path, monkeypatch):
    monkeypatch.setenv("RELAY_DONNEES", str(tmp_path))
    session = Session("P", ".", "default", lambda s: None)
    with pytest.raises(Exception, match="illisible"):
        session.envoyer("x", [{"nom": "a.png", "type_mime": "image/png", "donnees": "pas du base64 !"}])
    with pytest.raises(Exception, match="trop lourde"):
        session.envoyer("x", [{"nom": "a.png", "type_mime": "image/png", "donnees": _b64(b"0" * (5 * 1024 * 1024 + 1))}])
    assert session.evenements == []
