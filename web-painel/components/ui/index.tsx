"use client";

import { ButtonHTMLAttributes, HTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes, useEffect, useId, useRef } from "react";

export function Botao({ variant = "primary", className = "", ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "primary" | "secondary" | "danger" }) {
  return <button className={`ui-button ui-button-${variant} ${className}`} {...props} />;
}

export function Campo({ label, error, hint, id, ...props }: InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string; hint?: string }) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const helpId = `${inputId}-help`;
  return <label className="ui-field" htmlFor={inputId}><span>{label}</span><input id={inputId} aria-invalid={Boolean(error)} aria-describedby={(error || hint) ? helpId : undefined} {...props} />{(error || hint) && <small id={helpId} className={error ? "ui-error" : "ui-hint"}>{error ?? hint}</small>}</label>;
}

export function Select({ label, error, children, id, ...props }: SelectHTMLAttributes<HTMLSelectElement> & { label: string; error?: string; children: ReactNode }) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  return <label className="ui-field" htmlFor={inputId}><span>{label}</span><select id={inputId} aria-invalid={Boolean(error)} {...props}>{children}</select>{error && <small className="ui-error">{error}</small>}</label>;
}

export function Checkbox({ label, ...props }: InputHTMLAttributes<HTMLInputElement> & { label: string }) {
  return <label className="ui-choice"><input type="checkbox" {...props} /><span>{label}</span></label>;
}

export function Radio({ label, ...props }: InputHTMLAttributes<HTMLInputElement> & { label: string }) {
  return <label className="ui-choice"><input type="radio" {...props} /><span>{label}</span></label>;
}

export function Cartao({ className = "", ...props }: HTMLAttributes<HTMLElement>) {
  return <article className={`ui-card ${className}`} {...props} />;
}

export function Tabela({ caption, headers, rows }: { caption: string; headers: string[]; rows: ReactNode[][] }) {
  return <div className="ui-table-wrap"><table className="ui-table"><caption>{caption}</caption><thead><tr>{headers.map(header => <th key={header} scope="col">{header}</th>)}</tr></thead><tbody>{rows.map((row, rowIndex) => <tr key={rowIndex}>{row.map((cell, cellIndex) => <td key={cellIndex}>{cell}</td>)}</tr>)}</tbody></table></div>;
}

export function Etiqueta({ tone = "neutral", children }: { tone?: "neutral" | "success" | "warning" | "danger"; children: ReactNode }) {
  return <span className={`ui-label ui-label-${tone}`}>{children}</span>;
}

export function Dialog({ open, title, children, onClose }: { open: boolean; title: string; children: ReactNode; onClose: () => void }) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  useEffect(() => { const dialog = dialogRef.current; if (!dialog) return; if (open && !dialog.open) dialog.showModal(); if (!open && dialog.open) dialog.close(); }, [open]);
  return <dialog ref={dialogRef} className="ui-dialog" aria-labelledby="dialog-title" onCancel={event => { event.preventDefault(); onClose(); }}><h2 id="dialog-title">{title}</h2>{children}<Botao variant="secondary" onClick={onClose}>Fechar</Botao></dialog>;
}

export function Toast({ tone = "success", children }: { tone?: "success" | "danger"; children: ReactNode }) {
  return <div className={`ui-toast ui-toast-${tone}`} role={tone === "danger" ? "alert" : "status"}>{children}</div>;
}

export function Skeleton({ label = "Carregando conteúdo" }: { label?: string }) {
  return <div className="ui-skeleton" role="status" aria-label={label}><span /><span /><span /></div>;
}

export function EmptyState({ title, description, action }: { title: string; description: string; action?: ReactNode }) {
  return <div className="ui-empty"><span aria-hidden="true">◇</span><h2>{title}</h2><p>{description}</p>{action}</div>;
}

export function UploadImagem({ label, hint, error, previewUrl, uploading, onSelect, onRemove }: {
  label: string;
  hint?: string;
  error?: string;
  previewUrl: string | null;
  uploading?: boolean;
  onSelect: (file: File) => void;
  onRemove?: () => void;
}) {
  const generatedId = useId();
  const helpId = `${generatedId}-help`;
  return <div className="ui-field ui-upload">
    <span>{label}</span>
    {previewUrl && <img className="ui-upload-preview" src={previewUrl} alt="" />}
    <input id={generatedId} type="file" accept="image/png,image/jpeg" disabled={uploading}
      aria-invalid={Boolean(error)} aria-describedby={(error || hint) ? helpId : undefined}
      onChange={event => {
        const file = event.target.files?.[0];
        if (file) onSelect(file);
        event.target.value = "";
      }} />
    <div className="ui-upload-actions">
      {uploading && <span role="status">Enviando…</span>}
      {previewUrl && onRemove && !uploading && <Botao type="button" variant="secondary" onClick={onRemove}>Remover</Botao>}
    </div>
    {(error || hint) && <small id={helpId} className={error ? "ui-error" : "ui-hint"}>{error ?? hint}</small>}
  </div>;
}

export function SeletorCor({ label, value, error, onChange }: {
  label: string;
  value: string;
  error?: string;
  onChange: (value: string) => void;
}) {
  const generatedId = useId();
  const helpId = `${generatedId}-help`;
  const previewColor = /^#[0-9A-Fa-f]{6}$/.test(value) ? value : "#000000";
  return <label className="ui-field" htmlFor={generatedId}>
    <span>{label}</span>
    <div className="ui-color-row">
      <input type="color" aria-label={`${label} (seletor visual)`} value={previewColor} onChange={event => onChange(event.target.value.toUpperCase())} />
      <input id={generatedId} type="text" value={value} maxLength={7} placeholder="#2563EB"
        aria-invalid={Boolean(error)} aria-describedby={error ? helpId : undefined}
        onChange={event => onChange(event.target.value)} />
    </div>
    {error && <small id={helpId} className="ui-error">{error}</small>}
  </label>;
}
