"use client";

import Link from "next/link";
import { useEffect, useId, useRef, useState } from "react";
import { ApiRequestError } from "../../../../lib/api";
import { allScopes, ApiKey, ApiScope, createApiKey, CreatedApiKey, scopeOptions, toggleScope, updateApiKey } from "../../../../lib/apikeys";

/** Criar ou editar uma API Key: nome e endpoints liberados. Na edição mostra também os dados de conexão. */
export function ApiKeyDrawer({ editing, onClose, onSaved }: { editing: ApiKey | null; onClose: () => void; onSaved: (created: CreatedApiKey | null) => void }) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const [name, setName] = useState(editing?.name ?? "");
  const [scopes, setScopes] = useState<ApiScope[]>(editing?.scopes ?? allScopes);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => { ref.current?.showModal(); }, []);

  async function save() {
    if (!name.trim()) { setError("Informe um nome para a API Key."); return; }
    if (scopes.length === 0) { setError("Escolha ao menos um endpoint."); return; }
    setSaving(true);
    setError(null);
    try {
      if (editing) { await updateApiKey(editing.id, name, scopes); onSaved(null); }
      else onSaved(await createApiKey(name, scopes));
    } catch (saveError) {
      setError(saveError instanceof ApiRequestError ? saveError.message : "Não foi possível salvar a API Key.");
    } finally {
      setSaving(false);
    }
  }

  const allOn = scopes.length === allScopes.length;

  return <dialog ref={ref} className="mk-drawer" aria-labelledby={titleId} onCancel={event => { event.preventDefault(); if (!saving) onClose(); }}>
    <header className="vd-head">
      <h2 id={titleId}>{editing ? "Editar API Key" : "Criar API Key"}</h2>
      <button type="button" className="cp-close" aria-label="Fechar" onClick={() => !saving && onClose()}>✕</button>
    </header>
    <div className="mk-drawer-body api-drawer-body">
      <label className="pe-field"><span>Nome</span><input value={name} maxLength={60} autoComplete="off" onChange={event => { setName(event.target.value); setError(null); }} /></label>
      <p className="api-help"><Link href="/ajuda/api" target="_blank" rel="noopener noreferrer">Aprenda mais sobre a API</Link></p>

      <fieldset className="pe-fieldset api-scopes"><legend>Endpoints</legend>
        <button type="button" className="pe-linkbtn" onClick={() => setScopes(allOn ? [] : allScopes)}>{allOn ? "(Desmarcar todos)" : "(Marcar todos)"}</button>
        {scopeOptions.map(option => <label key={option.scope} className={`mk-terms ${option.parent ? "api-sub" : ""}`}>
          <input type="checkbox" checked={scopes.includes(option.scope)} onChange={event => setScopes(current => toggleScope(current, option.scope, event.target.checked))} /> {option.label}</label>)}
      </fieldset>

      {editing && <section aria-label="Seus dados para conectar com a API">
        <p className="pe-hint">Seus dados para conectar com a API</p>
        <label className="pe-field"><span>client_id</span><input readOnly value={editing.clientId} /></label>
        <label className="pe-field"><span>client_secret</span><input readOnly value="**************" aria-describedby="api-secret-hint" /><small id="api-secret-hint" className="pe-hint">O client_secret só é mostrado na criação. Se o perder, crie uma nova API Key.</small></label>
        <label className="pe-field"><span>account_id</span><input readOnly value={editing.accountId} /></label>
      </section>}
      {error && <p className="pe-error" role="alert">{error}</p>}
    </div>
    <footer className="api-foot">
      <button type="button" className="ui-button ui-button-secondary" disabled={saving} onClick={onClose}>Cancelar</button>
      <button type="button" className="ui-button ui-button-primary" disabled={saving} onClick={() => void save()}>{saving ? "Salvando…" : editing ? "Salvar alterações" : "Criar API Key"}</button>
    </footer>
  </dialog>;
}
