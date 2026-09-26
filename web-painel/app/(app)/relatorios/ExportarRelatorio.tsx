"use client";

import { useState } from "react";
import { Janela } from "../../../components/Janela";
import { ApiRequestError } from "../../../lib/api";
import { downloadReport, ExportFormat, ReportQuery } from "../../../lib/relatorios";

/** Botão "Exportar": pergunta o formato (planilha ou CSV) e baixa o relatório com os filtros da tela. */
export function ExportarRelatorio({ id, query, fileName, onNotice, onError }: { id: string; query: ReportQuery; fileName: string; onNotice: (text: string) => void; onError: (text: string) => void }) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState<ExportFormat | null>(null);

  async function run(format: ExportFormat) {
    setBusy(format);
    try {
      const blob = await downloadReport(id, format, query);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `paysi_${fileName}.${format}`;
      document.body.append(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      setOpen(false);
      onNotice("Exportação iniciada com sucesso.");
    } catch (error) {
      setOpen(false);
      onError(error instanceof ApiRequestError ? error.message : "Não foi possível exportar o relatório.");
    } finally {
      setBusy(null);
    }
  }

  return <>
    <button type="button" className="ui-button ui-button-secondary" onClick={() => setOpen(true)}>Exportar</button>
    {open && <Janela open title="Exportar relatório" onClose={() => !busy && setOpen(false)}>
      <p className="pe-hint">Escolha o formato do arquivo. Ele leva os filtros que estão na tela.</p>
      <div className="rel-formats">
        <button type="button" className="ui-button ui-button-primary" disabled={busy !== null} onClick={() => void run("xlsx")}>{busy === "xlsx" ? "Gerando…" : "Planilha do Excel (.xlsx)"}</button>
        <button type="button" className="ui-button ui-button-secondary" disabled={busy !== null} onClick={() => void run("csv")}>{busy === "csv" ? "Gerando…" : "Arquivo CSV (.csv)"}</button>
      </div>
    </Janela>}
  </>;
}
