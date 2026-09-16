from pathlib import Path

import pymupdf
from PIL import Image


ROOT = Path(r"C:/Projetos/Paysi")
TMP = ROOT / "tmp" / "pdfs"
OUTPUT = Path(
    r"C:/Users/evald/OneDrive/Área de Trabalho/Certificados Empreenda Ja/"
    r"Certificado teste - Alcidinei - 4 logos - primeira pagina.pdf"
)
BASE = TMP / "base-certificate.pdf"

LOGOS = [
    (
        Path(r"C:/Users/evald/OneDrive/Área de Trabalho/FAPES@2x.png"),
        pymupdf.Rect(590, 466, 675, 509),
    ),
    (
        Path(r"C:/Users/evald/OneDrive/Área de Trabalho/BARRACÃO@2x.png"),
        pymupdf.Rect(686, 474, 780, 504),
    ),
    (
        Path(r"C:/Users/evald/OneDrive/Área de Trabalho/GOVERNO@2x.png"),
        pymupdf.Rect(590, 523, 680, 551),
    ),
    (
        Path(r"C:/Users/evald/OneDrive/Área de Trabalho/PREFEITURA@2x.png"),
        pymupdf.Rect(690, 524, 780, 549),
    ),
]


def crop_to_visible(source: Path, destination: Path) -> None:
    image = Image.open(source).convert("RGBA")
    alpha_bbox = image.getchannel("A").getbbox()
    if alpha_bbox is None:
        raise ValueError(f"Logo sem pixels visíveis: {source}")
    image.crop(alpha_bbox).save(destination, optimize=True)


def main() -> None:
    TMP.mkdir(parents=True, exist_ok=True)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)

    source_document = pymupdf.open(BASE)
    if source_document.page_count < 1:
        raise ValueError("O certificado-base não possui páginas")

    document = pymupdf.open()
    document.insert_pdf(source_document, from_page=0, to_page=0)
    source_document.close()

    page = document[0]
    for index, (source, rectangle) in enumerate(LOGOS, start=1):
        cropped = TMP / f"logo-cropped-{index}.png"
        crop_to_visible(source, cropped)
        page.insert_image(rectangle, filename=str(cropped), keep_proportion=True, overlay=True)

    document.save(OUTPUT, garbage=3, deflate=True)
    document.close()

    check = pymupdf.open(OUTPUT)
    if check.page_count != 1:
        raise ValueError(f"Esperada 1 página; encontrado: {check.page_count}")
    pixmap = check[0].get_pixmap(matrix=pymupdf.Matrix(1.5, 1.5), alpha=False)
    pixmap.save(TMP / "certificate-test-render.png")
    print(f"output={OUTPUT}")
    print(f"pages={check.page_count}")
    print(f"size={check[0].rect.width}x{check[0].rect.height}")
    check.close()


if __name__ == "__main__":
    main()
