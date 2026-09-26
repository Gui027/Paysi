"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { EmptyState, Skeleton, Toast } from "../../../../components/ui";
import { Janela } from "../../../../components/Janela";
import { ApiRequestError } from "../../../../lib/api";
import { Product } from "../../../../lib/produtos";
import { CreatedWebhook, deleteWebhook, listAllProducts, listWebhooks, WebhookItem } from "../../../../lib/webhooks";
import { SegredoWebhookDialog } from "./SegredoWebhookDialog";
import { WebhookDrawer } from "./WebhookDrawer";

function message(error: unknown, fallback: string) {
  return error instanceof ApiRequestError ? error.message : fallback;
}

export function WebhooksPage() {
  const [search, setSearch] = useState("");
  const [term, setTerm] = useState("");
  const [productId, setProductId] = useState("");
  const [items, setItems] = useState<WebhookItem[] | null>(null);
  const [products, setProducts] = useState<Product[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [drawer, setDrawer] = useState<{ editing: WebhookItem | null } | null>(null);
  const [menuId, setMenuId] = useState<string | null>(null);
  const [removing, setRemoving] = useState<WebhookItem | null>(null);
  const [secret, setSecret] = useState<{ value: string; title: string } | null>(null);
  const requestId = useRef(0);

  const load = useCallback(async (q: string, product: string) => {
    const request = ++requestId.current;
    setError(null);
    try {
      const result = await listWebhooks(q, product);
      if (request === requestId.current) setItems(result);
    } catch (loadError) {
      if (request === requestId.current) setError(message(loadError, "Não foi possível carregar os webhooks."));
    }
  }, []);

  useEffect(() => { void load(term, productId); }, [load, term, productId]);
  useEffect(() => { const timer = setTimeout(() => setTerm(search), 300); return () => clearTimeout(timer); }, [search]);
  useEffect(() => { listAllProducts().then(setProducts).catch(() => undefined); }, []);

  async function confirmRemove() {
    if (!removing) return;
    try {
      await deleteWebhook(removing.id);
      setRemoving(null);
      setNotice("Webhook excluído");
      void load(term, productId);
    } catch (removeError) {
      setRemoving(null);
      setError(message(removeError, "Não foi possível excluir o webhook."));
    }
  }

  return <div className="vd">
    <header className="rel-title api-title">
      <Link href="/apps" className="rel-back" aria-label="Voltar para Apps">←</Link>
      <svg width="46" height="46" viewBox="0 0 24 24" fill="none" stroke="#111827" strokeWidth="1.700" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><circle cx="12" cy="6" r="2.500" /><circle cx="6" cy="17" r="2.500" /><circle cx="18" cy="17" r="2.500" /><path d="M12 8.500 8 15M9 17h6M13.500 8.500 16.500 14.500" /></svg>
      <h1>Webhooks</h1>
    </header>

    {error && <Toast tone="danger">{error} <button className="toast-action" onClick={() => void load(term, productId)}>Tentar novamente</button></Toast>}
    {notice && <Toast>{notice} <button className="toast-action" onClick={() => setNotice(null)}>Fechar</button></Toast>}

    <section className="prod-panel">
      <div className="rel-toolbar">
        <div className="rel-filters wh-filters">
          <label className="mk-search-field rel-search"><span className="sr-only">Buscar webhook por nome ou URL</span>
            <input type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder="Buscar…" /></label>
          <label className="rel-select"><span className="sr-only">Produto</span>
            <select value={productId} onChange={event => setProductId(event.target.value)}>
              <option value="">Todos os produtos</option>
              {products.map(product => <option key={product.id} value={product.id}>{product.name}</option>)}
            </select></label>
        </div>
        <button type="button" className="ui-button ui-button-primary" onClick={() => setDrawer({ editing: null })}>Criar webhook</button>
      </div>
      {items === null ? <Skeleton label="Carregando webhooks" /> : items.length === 0 ?
        <EmptyState title="Nenhum webhook" description={term || productId ? "Nenhum webhook encontrado com esses filtros." : "Crie um webhook para receber avisos de vendas, reembolsos e assinaturas no seu sistema."} /> :
        <div className="dash-table-wrap"><table className="prod-table vd-table">
          <thead><tr><th scope="col">Produto</th><th scope="col">Nome</th><th scope="col">URL</th><th scope="col"><span className="sr-only">Ações</span></th></tr></thead>
          <tbody>{items.map(item => <tr key={item.id}>
            <td><strong>{item.productName ?? "Todos que sou produtor"}</strong></td>
            <td className="prod-muted">{item.name ?? "Sem nome"}</td>
            <td className="prod-muted wh-url" title={item.url}>{item.url}</td>
            <td className="col-actions"><div className="vd-menu">
              <button type="button" className="vd-kebab" aria-label={`Ações do webhook ${item.name ?? item.url}`} aria-haspopup="menu" aria-expanded={menuId === item.id} onClick={() => setMenuId(current => current === item.id ? null : item.id)}>⋮</button>
              {menuId === item.id && <div className="prod-menu-list vd-menu-list" role="menu">
                <button type="button" role="menuitem" className="col-item" onClick={() => { setMenuId(null); setDrawer({ editing: item }); }}>Editar</button>
                <Link role="menuitem" href={`/apps/webhooks/${item.id}`} className="col-item">Ver logs</Link>
                <button type="button" role="menuitem" onClick={() => { setMenuId(null); setRemoving(item); }}>Excluir</button>
              </div>}
            </div></td>
          </tr>)}</tbody>
        </table></div>}
    </section>
    <p className="col-info">Aprenda mais sobre os <Link href="/ajuda/webhooks" target="_blank" rel="noopener noreferrer">webhooks</Link></p>

    {drawer && <WebhookDrawer editing={drawer.editing} products={products} onClose={() => setDrawer(null)}
      onSaved={created => {
        setDrawer(null);
        void load(term, productId);
        if (created) { setSecret({ value: created.secret, title: "Copiar o segredo do webhook" }); setNotice("Webhook criado"); } else setNotice("Webhook atualizado");
      }}
      onRotated={value => { setDrawer(null); setSecret({ value, title: "Novo segredo do webhook" }); }} />}
    {secret && <SegredoWebhookDialog secret={secret.value} title={secret.title} onClose={() => setSecret(null)} />}
    {removing && <Janela open title="Excluir webhook" onClose={() => setRemoving(null)}>
      <p>Excluir o webhook <strong>{removing.name ?? removing.url}</strong>? Nada mais será enviado para ele e os logs deixam de aparecer. Essa ação não pode ser desfeita.</p>
      <div className="ui-actions">
        <button type="button" className="ui-button ui-button-secondary" onClick={() => setRemoving(null)}>Cancelar</button>
        <button type="button" className="ui-button ui-button-danger" onClick={() => void confirmRemove()}>Excluir</button>
      </div>
    </Janela>}
  </div>;
}
