"""Dessine l'icône Windows de Relay : windows/packaging/relay.ico.

Même tracé que shared/src/commonMain/composeResources/drawable/ic_relay.xml, sur sa grille de 24 :
une invite de terminal « >_ » blanche sur un carré ocre arrondi. Chaque taille est dessinée à
part, suréchantillonnée, pour rester nette jusqu'à 16 pixels.

    pip install pillow
    python outils/icone.py
"""

from pathlib import Path

from PIL import Image, ImageDraw

OCRE = (200, 100, 59, 255)
BLANC = (255, 255, 255, 255)
TAILLES = [256, 128, 64, 48, 40, 32, 24, 20, 16]
SURECHANTILLONNAGE = 8


def dessiner(cote: int) -> Image.Image:
    grand = cote * SURECHANTILLONNAGE
    e = grand / 24
    image = Image.new("RGBA", (grand, grand), (0, 0, 0, 0))
    trace = ImageDraw.Draw(image)
    trace.rounded_rectangle((1 * e, 1 * e, 23 * e, 23 * e), radius=4 * e, fill=OCRE)

    # Aux petites tailles, le trait de 2,2/24 tomberait sous le pixel : on l'épaissit.
    trait = max(2.2 * e, 1.6 * SURECHANTILLONNAGE)
    chevron = [(6.5 * e, 7.5 * e), (11 * e, 12 * e), (6.5 * e, 16.5 * e)]
    trace.line(chevron, fill=BLANC, width=round(trait), joint="curve")
    trace.line([(13 * e, 16.5 * e), (17.5 * e, 16.5 * e)], fill=BLANC, width=round(trait))
    # Bouts arrondis, comme strokeLineCap="round".
    for x, y in (chevron[0], chevron[2], (13 * e, 16.5 * e), (17.5 * e, 16.5 * e)):
        r = trait / 2
        trace.ellipse((x - r, y - r, x + r, y + r), fill=BLANC)
    return image.resize((cote, cote), Image.LANCZOS)


def main() -> None:
    sortie = Path(__file__).resolve().parent.parent / "windows" / "packaging" / "relay.ico"
    sortie.parent.mkdir(parents=True, exist_ok=True)
    images = [dessiner(cote) for cote in TAILLES]
    images[0].save(sortie, format="ICO", sizes=[(c, c) for c in TAILLES], append_images=images[1:])
    images[0].save(sortie.with_name("relay-256.png"))
    print(sortie)


if __name__ == "__main__":
    main()
