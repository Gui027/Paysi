"use client";

import Link from "next/link";
import { useEffect, useId, useRef, useState } from "react";
import { ApiRequestError } from "../../../../lib/api";
import { Product, productStatusLabel } from "../../../../lib/produtos";
import {
  allEventKeys, createWebhook, CreatedWebhook, eventCatalog, isValidWebhookUrl, keysFromTypes, rotateWebhookSecret, testWebhook,
  typesFromKeys, updateWebhook, WebhookItem, WebhookTestResult,
} from "../../../../lib/webhooks";

function message(error: unknown, fallback: string) {
  return error instanceof ApiRequestError ? error.message : fallback;
}

/** Criar ou editar um webhook: nome, URL (com envio de teste), produto e eventos. */
export function WebhookDrawer({ editing, products, onClose, onSaved, onRotated }: {
  editing: WebhookItem | null; products: Product[]; onClose: () => void;
  onSaved: (created: CreatedWebhook | null) => void; onRotated: (secret: string) => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const [name, setName] = useState(editing?.name ?? "");
  const [url, setUrl] = useState(editing?.url ?? "");
  const [productId, setProductId] = useState(editing?.productId ?? "");
  const [events, setEvents] = useState<string[]>(editing ? keysFromTypes(editing.events) : []);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [test, setTest] = useState<WebhookTestResult | null>(null);
  const [rotating, setRotating] = useState(false);

  useEffect(() => { ref.current?.showModal(); }, []);

  const active = products.filter(product => product.status === "ACTIVE");
  const others = products.filter(product => product.status !== "ACTIVE");
  const urlOk = isValidWebhookUrl(url);
  const allOn = events.length === allEventKeys.length;

  async function runTest() {
    setTesting(true);
    setTest(null);
    try {
      setTest(await testWebhook(url, editing?.id ?? null));
    } catch (testError) {
      setTest({ success: false, statusCode: null, error: message(testError, "Não foi possível enviar o teste."), responseBody: null });
    } finally {
      setTesting(false);
    }
  }

  async function save() {
    if (!name.trim()) { setError("Informe o nome do webhook."); return; }
    if (!urlOk) { setError("Informe uma URL HTTPS válida, como https://seusite.com/webhooks/paysi."); return; }
    if (events.length === 0) { setError("Selecione ao menos um evento."); return; }
    setSaving(true);
    setError(null);
    const input = { name, productId, url, events: typesFromKeys(events) };
    try {
      if (editing) { await updateWebhook(editing.id, input); onSaved(null); }
      else onSaved(await createWebhook(input));
    } catch (saveError) {
      setError(message(saveError, "Não foi possível salvar o webhook."));
    } finally {
      setSaving(false);
    }
  }

  async function rotate() {
    if (!editing) return;
    setRotating(true);
    setError(null);
    try {
      onRotated((await rotateWebhookSecret(editing.id)).secret);
    } catch (rotateError) {
      setError(message(rotateError, "Não foi possível gerar um novo segredo."));
    } finally {
      setRotating(false);
    }
  }

  return <dialog ref={ref} className="mk-drawer" aria-labelledby={titleId} onCancel={event => { event.preventDefault(); if (!saving) onClose(); }}>
    <header className="vd-head">
      <h2 id={titleId}>{editing ? "Editar webhook" : "Criar webhook"}</h2>
      <button type="button" className="cp-close" aria-label="Fechar" onClick={() => !saving && onClose()}>✕</button>
    </header>
    <div className="mk-drawer-body api-drawer-body">
      <label className="pe-field"><span>Nome</span><input value={name} maxLength={60} autoComplete="off" onChange={event => { setName(event.target.value); setError(null); }} /></label>
      <label className="pe-field"><span>URL do Webhook</span><input type="url" value={url} placeholder="https://example.com/api/webhooks/paysi" autoComplete="off" onChange={event => { setUrl(event.target.value); setTest(null); setError(null); }} /></label>
      <div className="wh-test">
        <button type="button" className="ui-button ui-button-secondary" disabled={!urlOk || testing} onClick={() => void runTest()}>{testing ? "Enviando…" : "Testar Webhook"}</button>
        {test && <p className={`wh-test-result ${test.success ? "vd-ok" : "pe-error"}`} role="status">
          {test.success ? `Recebemos a resposta HTTP ${test.statusCode}. A URL está funcionando.` : `Falhou${test.statusCode ? ` (HTTP ${test.statusCode})` : ""}${test.error ? `: ${test.error}` : ""}`}
        </p>}
      </div>

      <div className="pe-field"><span>Token</span>
        {editing ? <div className="wh-token">
          <input readOnly aria-label="Token" value="••••••••••••" />
          <button type="button" className="ui-button ui-button-secondary" disabled={rotating} onClick={() => void rotate()}>{rotating ? "Gerando…" : "Gerar novo segredo"}</button>
        </div> : <input readOnly aria-label="Token" value="Será gerado ao criar" />}
        <small className="pe-hint">O token assina cada envio no cabeçalho X-Paysi-Signature. Ele só aparece na criação ou ao gerar um novo.</small>
      </div>
      <p className="api-help"><Link href="/ajuda/webhooks" target="_blank" rel="noopener noreferrer">Aprenda mais sobre os webhooks</Link></p>

      <label className="pe-field"><span>Produtos</span>
        <select value={productId} onChange={event => setProductId(event.target.value)}>
          <option value="">Todos que sou produtor</option>
          {active.length > 0 && <optgroup label="Ativos">{active.map(product => <option key={product.id} value={product.id}>{product.name}</option>)}</optgroup>}
          {others.length > 0 && <optgroup label="Outros">{others.map(product => <option key={product.id} value={product.id}>{product.name} ({productStatusLabel[product.status]})</option>)}</optgroup>}
        </select></label>

      <fieldset className="pe-fieldset api-scopes"><legend>Evento</legend>
        <button type="button" className="pe-linkbtn" onClick={() => setEvents(allOn ? [] : allEventKeys)}>{allOn ? "(Desmarcar todos)" : "(Selecionar todos)"}</button>
        {eventCatalog.map(item => <label key={item.key} className="mk-terms">
          <input type="checkbox" checked={events.includes(item.key)} onChange={event => setEvents(current => event.target.checked ? [...current, item.key] : current.filter(key => key !== item.key))} /> {item.label}</label>)}
      </fieldset>
      {error && <p className="pe-error" role="alert">{error}</p>}
    </div>
    <footer className="api-foot">
      <button type="button" className="ui-button ui-button-secondary" disabled={saving} onClick={onClose}>Cancelar</button>
      <button type="button" className="ui-button ui-button-primary" disabled={saving} onClick={() => void save()}>{saving ? "Salvando…" : editing ? "Salvar alterações" : "Criar"}</button>
    </footer>
  </dialog>;
}
