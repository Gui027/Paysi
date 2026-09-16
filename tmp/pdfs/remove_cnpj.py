from pathlib import Path
import re

import pymupdf


SOURCE = Path(
    r"C:/Users/evald/OneDrive/Área de Trabalho/Certificados Empreenda Ja/"
    r"Certificado teste - Alcidinei - 4 logos - primeira pagina.pdf"
)
REVISED = Path(r"C:/Projetos/Paysi/tmp/pdfs/certificate-without-cnpj.pdf")
RENDER = Path(r"C:/Projetos/Paysi/tmp/pdfs/certificate-without-cnpj-render.png")


def main() -> None:
    document = pymupdf.open(SOURCE)
    page = document[0]
    matches = 0

    # The unwanted label is a standalone text operation in the original
    # certificate stream. Removing only that operation preserves the text
    # underneath it and every other visual element.
    pattern = re.compile(
        rb"BT\s+142\.387\s+32\.932\s+Td\s+/F3\s+10\.5\s+Tf\s+"
        rb"\[\(.*?\)\]\s+TJ\s+ET",
        re.DOTALL,
    )

    for xref in page.get_contents():
        stream = document.xref_stream(xref)
        updated, count = pattern.subn(b"", stream)
        if count:
            document.update_stream(xref, updated)
            matches += count

    if matches != 1:
        raise ValueError(f"Esperada uma ocorrência de CNPJ; encontradas: {matches}")

    document.save(REVISED, garbage=3, deflate=True)
    document.close()

    check = pymupdf.open(REVISED)
    text = check[0].get_text("text")
    if "CNPJ" in text:
        raise ValueError("O texto CNPJ ainda está presente")
    if check.page_count != 1:
        raise ValueError(f"Esperada uma página; encontradas: {check.page_count}")
    pixmap = check[0].get_pixmap(matrix=pymupdf.Matrix(2, 2), alpha=False)
    pixmap.save(RENDER)
    print(f"matches_removed={matches}")
    print(f"pages={check.page_count}")
    print(f"render={RENDER}")
    check.close()


if __name__ == "__main__":
    main()
