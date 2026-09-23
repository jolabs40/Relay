"""Le bilan chiffré d'un tour : ce que Claude a fait, sans rien en raconter.

Fichiers modifiés et créés, lignes ajoutées et retirées, tests écrits, lancements de tests,
actions. Les chiffres viennent du résultat de chaque outil (``tool_use_result`` : Claude Code y
joint le ``structuredPatch`` d'Edit et de Write, et ``type: "create"`` pour un nouveau fichier) ;
à défaut, de l'entrée de l'outil.
"""

from __future__ import annotations

import difflib
import re
from typing import Any

OUTILS_EDITION = ("Edit", "MultiEdit", "Write", "NotebookEdit")
OUTILS_SHELL = ("Bash", "PowerShell")

# Une fonction de test dans les langages de nos projets : Kotlin/Java, Python, JS/TS.
_TEST_ECRIT = re.compile(r"@Test\b|^\s*(?:async\s+)?def\s+test_\w*\s*\(|^\s*(?:it|test)\s*\(\s*['\"`]")

# Une commande qui lance des tests.
_TEST_LANCE = re.compile(
    r"\bpytest\b|gradlew\S*\s+(?:.*\s)?\S*[tT]est\b|\b(?:npm|pnpm|yarn)\s+(?:run\s+)?test\b"
    r"|\bjest\b|\bvitest\b|\bcargo\s+test\b|\bgo\s+test\b|\bunittest\b"
)


def _lignes(texte: str) -> list[str]:
    return texte.splitlines() if texte else []


def _depuis_patch(patch: list[dict]) -> tuple[list[str], int]:
    """Lignes ajoutées (leur texte) et nombre de lignes retirées, d'après les hunks."""
    ajoutees: list[str] = []
    retirees = 0
    for hunk in patch:
        for ligne in hunk.get("lines", []):
            if ligne.startswith("+"):
                ajoutees.append(ligne[1:])
            elif ligne.startswith("-"):
                retirees += 1
    return ajoutees, retirees


def _depuis_textes(avant: str, apres: str) -> tuple[list[str], int]:
    ajoutees: list[str] = []
    retirees = 0
    for ligne in difflib.ndiff(_lignes(avant), _lignes(apres)):
        if ligne.startswith("+ "):
            ajoutees.append(ligne[2:])
        elif ligne.startswith("- "):
            retirees += 1
    return ajoutees, retirees


class Bilan:
    def __init__(self) -> None:
        self.modifies: set[str] = set()
        self.crees: set[str] = set()
        self.lignes_ajoutees = 0
        self.lignes_retirees = 0
        self.tests_ecrits = 0
        self.tests_lances = 0
        self.tests_echoues = 0
        self.actions = 0
        self._en_cours: dict[str, tuple[str, dict]] = {}

    def lance(self, id_outil: str, nom: str, entree: dict) -> None:
        self.actions += 1
        self._en_cours[id_outil] = (nom, entree or {})

    def termine(self, id_outil: str, erreur: bool, resultat: Any = None) -> bool:
        """Un outil a rendu son résultat. Vrai si le bilan a changé au-delà du compte d'actions."""
        nom, entree = self._en_cours.pop(id_outil, ("", {}))
        if erreur and nom not in OUTILS_SHELL:
            return False
        if nom in OUTILS_EDITION:
            self._edition(nom, entree, resultat if isinstance(resultat, dict) else None)
            return True
        if nom in OUTILS_SHELL and _TEST_LANCE.search(str(entree.get("command", ""))):
            self.tests_lances += 1
            if erreur:
                self.tests_echoues += 1
            return True
        return False

    def _edition(self, nom: str, entree: dict, resultat: dict | None) -> None:
        chemin = str((resultat or {}).get("filePath") or entree.get("file_path") or entree.get("notebook_path") or "")
        cree = bool(resultat) and resultat.get("type") == "create"
        if resultat and resultat.get("structuredPatch"):
            ajoutees, retirees = _depuis_patch(resultat["structuredPatch"])
        elif cree or nom == "Write":
            ajoutees, retirees = _lignes(str(entree.get("content", ""))), 0
        elif nom == "Edit":
            ajoutees, retirees = _depuis_textes(str(entree.get("old_string", "")), str(entree.get("new_string", "")))
        elif nom == "MultiEdit":
            ajoutees, retirees = [], 0
            for edition in entree.get("edits", []):
                plus, moins = _depuis_textes(str(edition.get("old_string", "")), str(edition.get("new_string", "")))
                ajoutees += plus
                retirees += moins
        else:  # NotebookEdit
            ajoutees, retirees = _lignes(str(entree.get("new_source", ""))), 0

        # Un fichier créé puis retouché dans le même tour reste un fichier créé.
        if cree:
            self.crees.add(chemin)
            self.modifies.discard(chemin)
        elif chemin and chemin not in self.crees:
            self.modifies.add(chemin)
        self.lignes_ajoutees += len(ajoutees)
        self.lignes_retirees += retirees
        self.tests_ecrits += sum(1 for ligne in ajoutees if _TEST_ECRIT.search(ligne))

    def en_dict(self) -> dict:
        return {
            "fichiers_modifies": len(self.modifies),
            "fichiers_crees": len(self.crees),
            "lignes_ajoutees": self.lignes_ajoutees,
            "lignes_retirees": self.lignes_retirees,
            "tests_ecrits": self.tests_ecrits,
            "tests_lances": self.tests_lances,
            "tests_echoues": self.tests_echoues,
            "actions": self.actions,
        }
