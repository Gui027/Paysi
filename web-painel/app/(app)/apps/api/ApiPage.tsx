"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { EmptyState, Skeleton, Toast } from "../../../../components/ui";
import { Janela } from "../../../../components/Janela";
import { ApiRequestError } from "../../../../lib/api";
import { ApiKey, CreatedApiKey, deleteApiKey, listApiKeys } from "../../../../lib/apikeys";
import { ApiKeyDrawer } from "./ApiKeyDrawer";

const dateTime = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" });

function message(error: unknown, fallback: string) {
  return error instanceof ApiRequestError ? error.message : fallback;
}

/** Mostra o client_secret uma única vez: depois de fechar, ele não pode mais ser consultado. */
function SegredoDialog({ created, onClose }: { created: CreatedApiKey; onClose: () => void }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(created.clientSecret);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  return <Janela wide open title="Copiar sua API Key" onClose={onClose}>
    <p>Por favor, copie o client_secret e salve em um local seguro e acessível. Por razões de segurança, você não conseguirá visualizá-lo novamente. Se você o perder, será necessário gerar uma nova API Key.</p>
    <label className="pe-field"><span>client_secret</span><input readOnly value={created.clientSecret} onFocus={event => event.target.select()} /></label>
    <label className="pe-field"><span>client_id</span><input readOnly value={created.key.clientId} onFocus={event => event.target.select()} /></label>
    <label className="pe-field"><span>account_id</span><input readOnly value={created.key.accountId} onFocus={event => event.target.select()} /></label>
    {copied && <p className="vd-msg vd-ok" role="status">client_secret copiado.</p>}
    <div className="ui-actions">
      <button type="button" className="ui-button ui-button-secondary" onClick={onClose}>Fechar</button>
      <button type="button" className="ui-button ui-button-primary" onClick={() => void copy()}>Copiar client_secret</button>
    </div>
  </Janela>;
}

export function ApiPage() {
  const [search, setSearch] = useState("");
  const [term, setTerm] = useState("");
  const [keys, setKeys] = useState<ApiKey[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [drawer, setDrawer] = useState<{ editing: ApiKey | null } | null>(null);
  const [menuId, setMenuId] = useState<string | null>(null);
  const [removing, setRemoving] = useState<ApiKey | null>(null);
  const [secret, setSecret] = useState<CreatedApiKey | null>(null);
  const requestId = useRef(0);

  const load = useCallback(async (q: string) => {
    const request = ++requestId.current;
    setError(null);
    try {
      const result = await listApiKeys(q);
      if (request === requestId.current) setKeys(result);
    } catch (loadError) {
      if (request === requestId.current) setError(message(loadError, "Não foi possível carregar as API Keys."));
    }
  }, []);

  useEffect(() => { void load(term); }, [load, term]);
  useEffect(() => { const timer = setTimeout(() => setTerm(search), 300); return () => clearTimeout(timer); }, [search]);

  async function confirmRemove() {
    if (!removing) return;
    try {
      await deleteApiKey(removing.id);
      setRemoving(null);
      setNotice("API Key excluída");
      void load(term);
    } catch (removeError) {
      setRemoving(null);
      setError(message(removeError, "Não foi possível excluir a API Key."));
    }
  }

  return <div className="vd">
    <header className="rel-title api-title">
      <Link href="/apps" className="rel-back" aria-label="Voltar para Apps">←</Link>
      <svg width="46" height="46" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><rect x="1" y="1" width="22" height="22" rx="5" fill="#111827" /><path d="m7 9 3 3-3 3M12 16h5" fill="none" stroke="#fff" strokeWidth="1.800" strokeLinecap="round" strokeLinejoin="round" /></svg>
      <h1>API</h1>
    </header>

    {error && <Toast tone="danger">{error} <button className="toast-action" onClick={() => void load(term)}>Tentar novamente</button></Toast>}
    {notice && <Toast>{notice} <button className="toast-action" onClick={() => setNotice(null)}>Fechar</button></Toast>}

    <section className="prod-panel">
      <div className="rel-toolbar">
        <label className="mk-search-field rel-search"><span className="sr-only">Buscar API Key por nome</span>
          <input type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder="Buscar…" /></label>
        <button type="button" className="ui-button ui-button-primary" onClick={() => setDrawer({ editing: null })}>Criar API Key</button>
      </div>
      {keys === null ? <Skeleton label="Carregando API Keys" /> : keys.length === 0 ?
        <EmptyState title="Nenhuma API Key" description={term ? "Nenhuma API Key com esse nome." : "Crie uma API Key para integrar seu sistema com a Paysi."} /> :
        <div className="dash-table-wrap"><table className="prod-table vd-table">
          <thead><tr><th scope="col">Nome</th><th scope="col">API Key</th><th scope="col">Criada em</th><th scope="col">Último uso</th><th scope="col"><span className="sr-only">Ações</span></th></tr></thead>
          <tbody>{keys.map(key => <tr key={key.id}>
            <td><strong>{key.name}</strong></td>
            <td className="prod-muted">{key.keyHint}</td>
            <td className="prod-muted">{dateTime.format(new Date(key.createdAt))}</td>
            <td className="prod-muted">{key.lastUsedAt ? dateTime.format(new Date(key.lastUsedAt)) : "------"}</td>
            <td className="col-actions"><div className="vd-menu">
              <button type="button" className="vd-kebab" aria-label={`Ações da API Key ${key.name}`} aria-haspopup="menu" aria-expanded={menuId === key.id} onClick={() => setMenuId(current => current === key.id ? null : key.id)}>⋮</button>
              {menuId === key.id && <div className="prod-menu-list vd-menu-list" role="menu">
                <button type="button" role="menuitem" className="col-item" onClick={() => { setMenuId(null); setDrawer({ editing: key }); }}>Editar</button>
                <button type="button" role="menuitem" onClick={() => { setMenuId(null); setRemoving(key); }}>Excluir</button>
              </div>}
            </div></td>
          </tr>)}</tbody>
        </table></div>}
    </section>
    <p className="col-info">Aprenda mais sobre a <Link href="/ajuda/api" target="_blank" rel="noopener noreferrer">API</Link></p>

    {drawer && <ApiKeyDrawer editing={drawer.editing} onClose={() => setDrawer(null)} onSaved={created => {
      setDrawer(null);
      void load(term);
      if (created) { setSecret(created); setNotice("API Key criada"); } else setNotice("API Key atualizada");
    }} />}
    {secret && <SegredoDialog created={secret} onClose={() => setSecret(null)} />}
    {removing && <Janela open title="Excluir API Key" onClose={() => setRemoving(null)}>
      <p>Excluir a API Key <strong>{removing.name}</strong>? Os sistemas que a usam perdem o acesso imediatamente. Essa ação não pode ser desfeita.</p>
      <div className="ui-actions">
        <button type="button" className="ui-button ui-button-secondary" onClick={() => setRemoving(null)}>Cancelar</button>
        <button type="button" className="ui-button ui-button-danger" onClick={() => void confirmRemove()}>Excluir</button>
      </div>
    </Janela>}
  </div>;
}
