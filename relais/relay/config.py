"""Configuration du relais : dossier de données, jeton d'accès, port, racine des projets.

Tout vit dans ``%APPDATA%\\Relay`` :

- ``config.json``  — port, jeton, racine des projets ; créé au premier démarrage ;
- ``relais.json``  — pid et port du relais en cours, que le client lit pour le joindre ;
- ``relais.log``   — traces.
"""

from __future__ import annotations

import json
import os
import secrets
from dataclasses import asdict, dataclass
from pathlib import Path

PORT_PAR_DEFAUT = 8787


def dossier_donnees() -> Path:
    base = os.environ.get("RELAY_DONNEES") or os.path.join(
        os.environ.get("APPDATA") or str(Path.home()), "Relay"
    )
    dossier = Path(base)
    dossier.mkdir(parents=True, exist_ok=True)
    return dossier


@dataclass
class Config:
    port: int
    jeton: str
    racine: str
    # Écoute sur le réseau local : désactivée tant que l'app Android n'existe pas.
    reseau_local: bool = False


def _ecrire_atomique(chemin: Path, contenu: str) -> None:
    temporaire = chemin.with_suffix(chemin.suffix + ".tmp")
    temporaire.write_text(contenu, encoding="utf-8")
    os.replace(temporaire, chemin)


def charger_config() -> Config:
    """Lit ``config.json``, ou le crée avec un jeton tiré au sort."""
    chemin = dossier_donnees() / "config.json"
    brut: dict = {}
    if chemin.exists():
        brut = json.loads(chemin.read_text(encoding="utf-8"))
    config = Config(
        port=int(brut.get("port", PORT_PAR_DEFAUT)),
        jeton=brut.get("jeton") or secrets.token_urlsafe(32),
        # Le dossier personnel à défaut : à resserrer dans config.json sur le dossier des projets.
        racine=brut.get("racine") or str(Path.home()),
        reseau_local=bool(brut.get("reseau_local", False)),
    )
    if asdict(config) != brut:
        _ecrire_atomique(chemin, json.dumps(asdict(config), indent=2, ensure_ascii=False))
    return config


def annoncer(port: int) -> Path:
    """Écrit ``relais.json`` : le client y lit le port et sait qu'un relais tourne."""
    chemin = dossier_donnees() / "relais.json"
    _ecrire_atomique(chemin, json.dumps({"pid": os.getpid(), "port": port}))
    return chemin


def retirer_annonce() -> None:
    chemin = dossier_donnees() / "relais.json"
    try:
        if json.loads(chemin.read_text(encoding="utf-8")).get("pid") == os.getpid():
            chemin.unlink()
    except (OSError, ValueError):
        pass
