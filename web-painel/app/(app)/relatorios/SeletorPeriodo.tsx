"use client";

import { useEffect, useRef, useState } from "react";
import { formatDay, monthGrid, monthNames, PeriodPreset, presetLabel, presetRange, toIso } from "../../../lib/relatorios";

export type PeriodValue = { preset: PeriodPreset; from: string; to: string };

const weekdays = ["Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"];

export function periodText(value: PeriodValue): string {
  if (value.preset !== "custom") return presetLabel[value.preset];
  if (value.from && value.to) return value.from === value.to ? formatDay(value.from) : `${formatDay(value.from)} – ${formatDay(value.to)}`;
  return presetLabel.custom;
}

/** Lista de períodos prontos mais um calendário para escolher um intervalo (clique no primeiro e no último dia). */
export function SeletorPeriodo({ value, presets, onChange }: { value: PeriodValue; presets: readonly PeriodPreset[]; onChange: (value: PeriodValue) => void }) {
  const [open, setOpen] = useState(false);
  const today = new Date();
  const [cursor, setCursor] = useState({ year: today.getFullYear(), month: today.getMonth() });
  const [start, setStart] = useState<string | null>(null);
  const box = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const close = (event: MouseEvent) => { if (box.current && !box.current.contains(event.target as Node)) setOpen(false); };
    const escape = (event: KeyboardEvent) => { if (event.key === "Escape") setOpen(false); };
    document.addEventListener("mousedown", close);
    document.addEventListener("keydown", escape);
    return () => { document.removeEventListener("mousedown", close); document.removeEventListener("keydown", escape); };
  }, [open]);

  function pick(preset: PeriodPreset) {
    onChange({ preset, ...presetRange(preset, new Date()) });
    setStart(null);
    setOpen(false);
  }

  function pickDay(day: string) {
    if (!start) { setStart(day); return; }
    const [from, to] = day < start ? [day, start] : [start, day];
    onChange({ preset: "custom", from, to });
    setStart(null);
    setOpen(false);
  }

  function move(step: number) {
    setCursor(current => {
      const next = new Date(current.year, current.month + step, 1);
      return { year: next.getFullYear(), month: next.getMonth() };
    });
  }

  const selectedFrom = start ?? value.from;
  const selectedTo = start ? "" : value.to;
  const todayIso = toIso(today);

  return <div className="rel-period" ref={box}>
    <button type="button" className="rel-period-button" aria-haspopup="dialog" aria-expanded={open} onClick={() => setOpen(current => !current)}>
      <span>{periodText(value)}</span><span aria-hidden="true">⌄</span>
    </button>
    {open && <div className="rel-popover" role="dialog" aria-label="Escolher período">
      <ul className="rel-presets">
        {presets.map(preset => <li key={preset}><button type="button" aria-current={value.preset === preset ? "true" : undefined} onClick={() => pick(preset)}>{presetLabel[preset]}</button></li>)}
      </ul>
      <div className="rel-cal">
        <div className="rel-cal-head">
          <button type="button" aria-label="Mês anterior" onClick={() => move(-1)}>←</button>
          <strong>{monthNames[cursor.month]} {cursor.year}</strong>
          <button type="button" aria-label="Próximo mês" onClick={() => move(1)}>→</button>
        </div>
        <table className="rel-cal-grid" aria-label={`${monthNames[cursor.month]} ${cursor.year}`}>
          <thead><tr>{weekdays.map(day => <th key={day} scope="col">{day}</th>)}</tr></thead>
          <tbody>{monthGrid(cursor.year, cursor.month).map((week, index) => <tr key={index}>
            {week.map((day, position) => <td key={position}>{day && <button type="button" aria-label={formatDay(day)}
              className={`${day === todayIso ? "is-today" : ""} ${selectedFrom && day >= selectedFrom && (selectedTo ? day <= selectedTo : day === selectedFrom) ? "is-selected" : ""}`}
              onClick={() => pickDay(day)}>{Number(day.slice(8, 10))}</button>}</td>)}
          </tr>)}</tbody>
        </table>
        {start && <p className="rel-cal-hint">Agora escolha o último dia do período.</p>}
      </div>
    </div>}
  </div>;
}
