"""Le bilan chiffré d'un tour : fichiers, lignes, tests, actions."""

from __future__ import annotations

import asyncio

from claude_agent_sdk import AssistantMessage, ToolResultBlock, ToolUseBlock, UserMessage

from relay.bilan import Bilan
from relay.session import Session
from test_relais import FauxClient, attendre, resultat

PATCH_EDIT = [{"oldStart": 1, "oldLines": 2, "newStart": 1, "newLines": 3,
               "lines": [" fun a() {}", "-fun b() {}", "+fun b() = 1", "+@Test fun c() {}"]}]


def test_edit_compte_les_lignes_du_patch_et_les_tests():
    bilan = Bilan()
    bilan.lance("1", "Edit", {"file_path": "A.kt", "old_string": "x", "new_string": "y"})
    bilan.termine("1", False, {"filePath": "A.kt", "structuredPatch": PATCH_EDIT})
    assert bilan.en_dict() == {
        "fichiers_modifies": 1, "fichiers_crees": 0, "lignes_ajoutees": 2, "lignes_retirees": 1,
        "tests_ecrits": 1, "tests_lances": 0, "tests_echoues": 0, "actions": 1,
    }


def test_fichier_cree_puis_retouche_reste_cree():
    bilan = Bilan()
    contenu = "def test_un():\n    pass\n\ndef test_deux():\n    pass"
    bilan.lance("1", "Write", {"file_path": "t.py", "content": contenu})
    bilan.termine("1", False, {"type": "create", "filePath": "t.py", "structuredPatch": []})
    bilan.lance("2", "Edit", {"file_path": "t.py", "old_string": "pass", "new_string": "assert 1"})
    bilan.termine("2", False, None)  # sans détail : repli sur l'entrée de l'outil
    d = bilan.en_dict()
    assert (d["fichiers_crees"], d["fichiers_modifies"]) == (1, 0)
    assert (d["lignes_ajoutees"], d["lignes_retirees"]) == (6, 1)
    assert d["tests_ecrits"] == 2


def test_lancements_de_tests_et_echecs():
    bilan = Bilan()
    commandes = ["python -m pytest -q", "./gradlew :shared:jvmTest", "./gradlew assembleDebug", "ls"]
    for i, commande in enumerate(commandes):
        bilan.lance(str(i), "Bash", {"command": commande})
        bilan.termine(str(i), i == 1)
    d = bilan.en_dict()
    assert (d["tests_lances"], d["tests_echoues"], d["actions"]) == (2, 1, 4)


def test_edition_en_echec_ne_compte_pas():
    bilan = Bilan()
    bilan.lance("1", "Edit", {"file_path": "A.kt", "old_string": "x", "new_string": "y"})
    bilan.termine("1", True)
    assert bilan.en_dict()["fichiers_modifies"] == 0


class ClientQuiEdite(FauxClient):
    """Un Edit, son résultat, puis la fin du tour."""

    async def receive_response(self):
        yield AssistantMessage(content=[ToolUseBlock(id="e1", name="Edit", input={"file_path": "A.kt"})], model="m")
        self.etape = asyncio.Event()
        await self.etape.wait()
        yield UserMessage(
            content=[ToolResultBlock(tool_use_id="e1", content="ok")],
            tool_use_result={"filePath": "A.kt", "structuredPatch": PATCH_EDIT},
        )
        yield resultat()


def test_session_expose_le_tour_puis_le_fige_dans_le_resultat():
    async def scenario():
        clients: list = []

        def creer(options):
            clients.append(ClientQuiEdite(options, []))
            return clients[-1]

        session = Session("P", ".", "bypassPermissions", lambda s: None, fabrique_client=creer)
        session.demarrer()
        session.envoyer("Corrige")
        await attendre(lambda: clients and hasattr(clients[0], "etape"))
        tour = session.instantane()["tour"]
        assert tour["debut"] > 0 and tour["bilan"]["actions"] == 1

        clients[0].etape.set()
        await attendre(lambda: session.etat == "inactive")
        assert session.instantane()["tour"] is None
        fin = session.evenements[-1]
        assert fin["type"] == "resultat" and fin["debut"] == tour["debut"]
        assert fin["bilan"]["fichiers_modifies"] == 1 and fin["bilan"]["lignes_ajoutees"] == 2
        await session.fermer()
    asyncio.run(scenario())
