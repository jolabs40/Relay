"""Les dossiers où Claude peut travailler, et les sessions passées de chacun."""

from __future__ import annotations

from pathlib import Path

from claude_agent_sdk import list_sessions

# Ce qui fait d'un dossier un projet : Claude y trouvera des consignes ou du code suivi.
_MARQUEURS = ("CLAUDE.md", ".git", "build.gradle.kts", "build.gradle", "package.json", "pyproject.toml")
_IGNORES = {"build", "node_modules", "__pycache__", "gradle", "dist", "out"}


def _est_projet(dossier: Path) -> bool:
    return any((dossier / marqueur).exists() for marqueur in _MARQUEURS)


def _visible(dossier: Path) -> bool:
    return dossier.is_dir() and not dossier.name.startswith((".", "_", "+")) and dossier.name not in _IGNORES


def lister_projets(racine: str) -> list[dict]:
    """La racine elle-même, ses sous-dossiers, et les projets rangés dans une suite.

    Une suite (``hippietv-suite``, ``TVSlim Suite``…) est listée, et ses sous-dossiers qui sont
    des projets aussi — c'est dans l'un d'eux qu'on travaille le plus souvent.
    """
    base = Path(racine)
    if not base.is_dir():
        return []
    projets = [{"nom": base.name, "chemin": str(base)}]
    for dossier in sorted(base.iterdir(), key=lambda d: d.name.lower()):
        if not _visible(dossier):
            continue
        projets.append({"nom": dossier.name, "chemin": str(dossier)})
        for sous in sorted(dossier.iterdir(), key=lambda d: d.name.lower()):
            if _visible(sous) and _est_projet(sous) and not _est_code_source(dossier, sous):
                projets.append({"nom": f"{dossier.name}/{sous.name}", "chemin": str(sous)})
    return projets


def _est_code_source(parent: Path, enfant: Path) -> bool:
    """Un module Gradle (``app``, ``core``) d'un projet n'est pas un projet à part."""
    return (parent / "settings.gradle.kts").exists() or (parent / "settings.gradle").exists()


def historique(chemin: str, limite: int = 30) -> list[dict]:
    """Sessions passées de ce dossier, les plus récentes d'abord."""
    sessions = list_sessions(directory=chemin, limit=limite, include_worktrees=False)
    return [
        {
            "id": s.session_id,
            "resume": s.custom_title or s.summary or s.first_prompt or "",
            "modifiee_a": s.last_modified,
        }
        for s in sessions
    ]
