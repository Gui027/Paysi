"use client";

import { ReactNode, useEffect, useId, useRef } from "react";

/** Janela modal no padrão do painel: título, ✕ para fechar e conteúdo com respiro. Fecha com Esc. */
export function Janela({ open, title, onClose, wide = false, children }: { open: boolean; title: string; onClose: () => void; wide?: boolean; children: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);
  return <dialog ref={ref} className={`cp-dialog ${wide ? "cp-dialog-wide" : ""}`} aria-labelledby={titleId} onCancel={event => { event.preventDefault(); onClose(); }}>
    <header className="cp-head"><h2 id={titleId}>{title}</h2><button type="button" className="cp-close" aria-label="Fechar" onClick={onClose}>✕</button></header>
    <div className="cp-body">{children}</div>
  </dialog>;
}
