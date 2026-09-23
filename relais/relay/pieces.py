"""Pièces jointes d'un prompt : images vues directement, autres fichiers lus par Claude.

Le client envoie chaque pièce en entier (``donnees``, base64) — et non un chemin : le futur client
Android n'a pas accès aux fichiers du PC. Le relais en fait :

- pour une **image** (PNG, JPEG, GIF, WebP), un bloc ``image`` du message, comme un collage dans
  le terminal : Claude la voit sans outil ;
- pour **tout autre fichier**, une copie sous ``%APPDATA%\\Relay\\pieces\\<session>\\``, dont le
  chemin est ajouté au prompt ; ce dossier est ouvert à Claude par ``add_dirs``.

Le fil ne garde que le nom, le type et une vignette (JPEG réduit, fabriqué par le client) : l'état
de la session est rediffusé à chaque changement, il ne doit pas porter les images entières.
"""

from __future__ import annotations

import base64
import binascii
import re
import shutil
import time
from dataclasses import dataclass, field
from pathlib import Path

from .config import dossier_donnees

TYPES_IMAGE = {"image/png", "image/jpeg", "image/gif", "image/webp"}
# Limite de l'API pour une image ; le client réduit avant d'envoyer.
TAILLE_MAX_IMAGE = 5 * 1024 * 1024
TAILLE_MAX_FICHIER = 20 * 1024 * 1024
TAILLE_MAX_VIGNETTE = 64 * 1024
CONSERVATION_JOURS = 30


class ErreurPiece(ValueError):
    """Pièce refusée : illisible, trop lourde, mal formée."""


@dataclass
class Pieces:
    """Ce qu'un lot de pièces devient : blocs d'image, chemins écrits, et ce que montre le fil."""

    images: list[dict] = field(default_factory=list)
    chemins: list[Path] = field(default_factory=list)
    affichage: list[dict] = field(default_factory=list)

    def texte_chemins(self) -> str:
        if not self.chemins:
            return ""
        lignes = "\n".join(f"- {chemin}" for chemin in self.chemins)
        return f"\n\n[Fichiers joints — lis-les avec l'outil Read]\n{lignes}"


def dossier_pieces() -> Path:
    dossier = dossier_donnees() / "pieces"
    dossier.mkdir(parents=True, exist_ok=True)
    return dossier


def _nom_sur(nom: str, pris: set[str]) -> str:
    """Un nom de fichier sans chemin ni caractère interdit, et unique dans le lot."""
    base = re.sub(r'[<>:"/\\|?*\x00-\x1f]', "_", Path(str(nom)).name).strip(" .") or "piece"
    candidat, n = base, 1
    while candidat.lower() in pris:
        n += 1
        racine, point, extension = base.rpartition(".")
        candidat = f"{racine}-{n}.{extension}" if point else f"{base}-{n}"
    pris.add(candidat.lower())
    return candidat


def _decoder(valeur: str, nom: str) -> bytes:
    try:
        return base64.b64decode(valeur, validate=True)
    except (binascii.Error, ValueError, TypeError) as erreur:
        raise ErreurPiece(f"Pièce illisible : {nom}") from erreur


def preparer(session: str, brutes: list[dict] | None) -> Pieces:
    pieces = Pieces()
    pris: set[str] = set()
    for brute in brutes or []:
        nom = _nom_sur(brute.get("nom", ""), pris)
        type_mime = str(brute.get("type_mime") or "application/octet-stream").lower()
        octets = _decoder(brute.get("donnees", ""), nom)
        vignette = brute.get("vignette") or None
        if vignette and len(vignette) > TAILLE_MAX_VIGNETTE:
            vignette = None

        if type_mime in TYPES_IMAGE:
            if len(octets) > TAILLE_MAX_IMAGE:
                raise ErreurPiece(f"Image trop lourde (5 Mo au plus) : {nom}")
            pieces.images.append({
                "type": "image",
                "source": {"type": "base64", "media_type": type_mime, "data": base64.b64encode(octets).decode()},
            })
        else:
            if len(octets) > TAILLE_MAX_FICHIER:
                raise ErreurPiece(f"Fichier trop lourd (20 Mo au plus) : {nom}")
            dossier = dossier_pieces() / session
            dossier.mkdir(parents=True, exist_ok=True)
            chemin = dossier / nom
            chemin.write_bytes(octets)
            pieces.chemins.append(chemin)
            vignette = None
        pieces.affichage.append({"nom": nom, "type_mime": type_mime, "taille": len(octets), "vignette": vignette})
    return pieces


def purger_anciennes() -> None:
    """Oublie les pièces des sessions vieilles d'un mois : leurs conversations sont closes depuis."""
    limite = time.time() - CONSERVATION_JOURS * 86400
    for dossier in dossier_pieces().iterdir():
        try:
            if dossier.is_dir() and dossier.stat().st_mtime < limite:
                shutil.rmtree(dossier, ignore_errors=True)
        except OSError:
            pass
