"""Point d'entrée : ``python -m relay`` (ou ``pythonw -m relay``, sans fenêtre)."""

from __future__ import annotations

import argparse
import asyncio
import logging
import sys
from logging.handlers import RotatingFileHandler

from .config import annoncer, charger_config, dossier_donnees, retirer_annonce
from .serveur import Relais, servir


def _journaliser() -> None:
    handlers: list[logging.Handler] = [
        RotatingFileHandler(dossier_donnees() / "relais.log", maxBytes=1_000_000, backupCount=2, encoding="utf-8")
    ]
    if sys.stderr is not None:  # pythonw n'a pas de console
        handlers.append(logging.StreamHandler())
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s : %(message)s", handlers=handlers)


def main() -> int:
    parser = argparse.ArgumentParser(prog="relay", description="Relais entre Claude Code et ses clients")
    parser.add_argument("--port", type=int, help="port d'écoute (défaut : config.json)")
    parser.add_argument("--jeton", action="store_true", help="affiche le jeton d'accès et quitte")
    args = parser.parse_args()

    _journaliser()
    config = charger_config()
    if args.jeton:
        print(config.jeton)
        return 0
    port = args.port or config.port
    hote = "0.0.0.0" if config.reseau_local else "127.0.0.1"

    async def lancer() -> None:
        pret = asyncio.Event()
        tache = asyncio.create_task(servir(Relais(config), hote, port, pret))
        attente = asyncio.create_task(pret.wait())
        await asyncio.wait({tache, attente}, return_when=asyncio.FIRST_COMPLETED)
        if tache.done():
            tache.result()  # remonte l'erreur de démarrage (port occupé…)
        annoncer(port)
        await tache

    try:
        asyncio.run(lancer())
    except OSError as erreur:
        logging.error("Démarrage impossible sur le port %s : %s", port, erreur)
        return 1
    except KeyboardInterrupt:
        pass
    finally:
        retirer_annonce()
    return 0


if __name__ == "__main__":
    sys.exit(main())
