from __future__ import annotations

import json
import re
from pathlib import Path

import pymupdf
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(r"C:/Projetos/Paysi")
TMP = ROOT / "tmp" / "pdfs"
MANIFEST_PATH = TMP / "empreenda_batch_manifest.json"
FINAL_DIR = Path(
    r"C:/Users/evald/OneDrive/Área de Trabalho/Certificados Empreenda Ja/"
    r"Certificados finais - 10-08-2026 a 14-09-2026"
)
QA_DIR = TMP / "batch_qa"

LOGO_SOURCES = [
    Path(r"C:/Users/evald/OneDrive/Área de Trabalho/FAPES@2x.png"),
    Path(r"C:/Users/evald/OneDrive/Área de Trabalho/BARRACÃO@2x.png"),
    Path(r"C:/Users/evald/OneDrive/Área de Trabalho/GOVERNO@2x.png"),
    Path(r"C:/Users/evald/OneDrive/Área de Trabalho/PREFEITURA@2x.png"),
]

# Approved lower-right layout.
LOGO_RECTS = [
    pymupdf.Rect(590, 466, 675, 509),
    pymupdf.Rect(686, 474, 780, 504),
    pymupdf.Rect(590, 523, 680, 551),
    pymupdf.Rect(690, 524, 780, 549),
]

CNPJ_PATTERN = re.compile(
    rb"BT\s+[^\r\n]*\s+Td\s+/F\d+\s+[\d.]+\s+Tf\s+"
    rb"\[\(\x00C\x00N\x00P\x00J\x00:\)\]\s+TJ\s+ET",
    re.DOTALL,
)


def safe_filename(name: str) -> str:
    cleaned = re.sub(r'[<>:"/\\|?*]', "-", name).strip().rstrip(".")
    cleaned = re.sub(r"\s+", " ", cleaned)
    return cleaned or "Aluno"


def crop_logos() -> list[Path]:
    result: list[Path] = []
    for index, source in enumerate(LOGO_SOURCES, start=1):
        image = Image.open(source).convert("RGBA")
        bbox = image.getchannel("A").getbbox()
        if bbox is None:
            raise ValueError(f"Logo sem pixels visíveis: {source}")
        destination = TMP / f"batch-logo-{index}.png"
        image.crop(bbox).save(destination, optimize=True)
        result.append(destination)
    return result


def remove_cnpj(document: pymupdf.Document, page: pymupdf.Page) -> int:
    removed = 0
    for xref in page.get_contents():
        stream = document.xref_stream(xref)
        updated, count = CNPJ_PATTERN.subn(b"", stream)
        if count:
            document.update_stream(xref, updated)
            removed += count
    return removed


def make_certificate(row: dict, logo_paths: list[Path]) -> tuple[Path, tuple[float, ...]]:
    source = pymupdf.open(row["sourcePath"])
    if source.page_count < 1:
        raise ValueError(f"Certificado sem páginas: {row['name']}")

    output_doc = pymupdf.open()
    output_doc.insert_pdf(source, from_page=0, to_page=0)
    source.close()

    page = output_doc[0]
    removed = remove_cnpj(output_doc, page)
    if removed != 1:
        raise ValueError(
            f"Esperada uma ocorrência de CNPJ em {row['name']}; encontradas: {removed}"
        )

    for logo_path, rectangle in zip(logo_paths, LOGO_RECTS):
        page.insert_image(
            rectangle,
            filename=str(logo_path),
            keep_proportion=True,
            overlay=True,
        )

    filename = (
        f"{row['sequence']:03d} - {safe_filename(row['name'])} - "
        f"{row['certificateCode']}.pdf"
    )
    destination = FINAL_DIR / filename
    output_doc.save(destination, garbage=3, deflate=True)
    output_doc.close()

    check = pymupdf.open(destination)
    if check.page_count != 1:
        raise ValueError(f"Quantidade de páginas inválida: {row['name']}")
    text = check[0].get_text("text")
    if "CNPJ" in text:
        raise ValueError(f"CNPJ ainda presente: {row['name']}")
    if "10/08/2026" not in text or "14/09/2026" not in text:
        raise ValueError(f"Datas incorretas: {row['name']}")

    # Capture the student-name text box for overflow diagnostics.
    name_boxes = [
        tuple(span["bbox"])
        for block in check[0].get_text("dict")["blocks"]
        if "lines" in block
        for line in block["lines"]
        for span in line["spans"]
        if 30 <= span["size"] <= 40 and span["bbox"][1] > 200
    ]
    widest = max(name_boxes, key=lambda box: box[2] - box[0]) if name_boxes else ()
    check.close()
    return destination, widest


def build_qa_sheets(files: list[Path]) -> list[Path]:
    QA_DIR.mkdir(parents=True, exist_ok=True)
    cell_w, cell_h = 390, 300
    columns, rows_per_sheet = 3, 8
    per_sheet = columns * rows_per_sheet
    font = ImageFont.load_default()
    sheets: list[Path] = []

    for sheet_index, start in enumerate(range(0, len(files), per_sheet), start=1):
        subset = files[start : start + per_sheet]
        sheet = Image.new("RGB", (columns * cell_w, rows_per_sheet * cell_h), "white")
        draw = ImageDraw.Draw(sheet)
        for local_index, pdf_path in enumerate(subset):
            document = pymupdf.open(pdf_path)
            pixmap = document[0].get_pixmap(matrix=pymupdf.Matrix(0.43, 0.43), alpha=False)
            thumbnail = Image.frombytes("RGB", (pixmap.width, pixmap.height), pixmap.samples)
            document.close()

            x = (local_index % columns) * cell_w
            y = (local_index // columns) * cell_h
            sheet.paste(thumbnail, (x, y + 20))
            draw.text((x + 4, y + 3), pdf_path.stem[:55], fill="black", font=font)

        sheet_path = QA_DIR / f"qa-sheet-{sheet_index}.png"
        sheet.save(sheet_path, optimize=True)
        sheets.append(sheet_path)
    return sheets


def main() -> None:
    payload = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    rows = payload["rows"]
    if len(rows) != 66:
        raise ValueError(f"Esperados 66 registros; encontrados: {len(rows)}")

    FINAL_DIR.mkdir(parents=True, exist_ok=True)
    logo_paths = crop_logos()
    outputs: list[Path] = []
    overflow: list[dict] = []

    for row in rows:
        destination, name_box = make_certificate(row, logo_paths)
        outputs.append(destination)
        if name_box and (name_box[0] < 10 or name_box[2] > 840):
            overflow.append({"name": row["name"], "bbox": name_box})

    if len(outputs) != 66 or len(list(FINAL_DIR.glob("*.pdf"))) != 66:
        raise ValueError("A pasta final não contém exatamente 66 PDFs")

    index_lines = [
        "CERTIFICADOS - EMPREENDA JA",
        "Periodo: 10/08/2026 a 14/09/2026",
        "Carga horaria: 24 horas | Nota final: 10",
        "",
    ]
    index_lines.extend(
        f"{row['sequence']:03d} | {row['name']} | {row['certificateCode']}"
        for row in rows
    )
    (FINAL_DIR / "Lista de certificados.txt").write_text(
        "\n".join(index_lines) + "\n", encoding="utf-8"
    )

    sheets = build_qa_sheets(outputs)
    print(f"final_dir={FINAL_DIR}")
    print(f"pdf_count={len(outputs)}")
    print(f"overflow={json.dumps(overflow, ensure_ascii=False)}")
    print(f"qa_sheets={json.dumps([str(path) for path in sheets], ensure_ascii=False)}")


if __name__ == "__main__":
    main()
