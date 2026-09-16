"use client";

import { useCallback, useEffect, useState } from "react";
import { Botao, Cartao, EmptyState, Etiqueta, Skeleton, Tabela, Toast } from "../../../components/ui";
import {
  AffiliateLink,
  buildAffiliateLinkUrl,
  commissionEntryStatus,
  commissionStatusTone,
  isCommissionEntry,
  listMyLinks,
} from "../../../lib/afiliados";
import { getLedgerEntries, LedgerItem } from "../../../lib/dashboard";
import { formatarCentavos } from "../../../lib/moeda";
import { currentSession } from "../../../lib/sessao";

const CHECKOUT_BASE_URL = process.env.NEXT_PUBLIC_CHECKOUT_BASE_URL ?? "https://checkout.paysi.com.br";

type Tab = "links" | "comissoes";

function formatDate(iso: string | null) {
  if (!iso) return "—";
  try {
    return new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(iso));
  } catch {
    return iso;
  }
}

export function MeusLinksPage() {
  const [tab, setTab] = useState<Tab>("links");
  const [affiliateId, setAffiliateId] = useState<string | null>(null);

  const [links, setLinks] = useState<AffiliateLink[]>([]);
  const [loadingLinks, setLoadingLinks] = useState(true);
  const [linksError, setLinksError] = useState<string | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const [entries, setEntries] = useState<LedgerItem[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loadingEntries, setLoadingEntries] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [entriesError, setEntriesError] = useState<string | null>(null);

  const loadLinks = useCallback(async () => {
    setLoadingLinks(true);
    setLinksError(null);
    try {
      const [session, myLinks] = await Promise.all([currentSession(), listMyLinks()]);
      setAffiliateId(session.accountId);
      setLinks(myLinks);
    } catch {
      setLinksError("Não foi possível carregar seus links.");
    } finally {
      setLoadingLinks(false);
    }
  }, []);

  const loadEntries = useCallback(async () => {
    setLoadingEntries(true);
    setEntriesError(null);
    try {
      const page = await getLedgerEntries();
      setEntries(page.items.filter(isCommissionEntry));
      setNextCursor(page.nextCursor);
    } catch {
      setEntriesError("Não foi possível carregar o extrato de comissões.");
    } finally {
      setLoadingEntries(false);
    }
  }, []);

  useEffect(() => {
    void loadLinks();
    void loadEntries();
  }, [loadLinks, loadEntries]);

  const loadMoreEntries = async () => {
    if (!nextCursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const page = await getLedgerEntries(nextCursor);
      setEntries((prev) => [...prev, ...page.items.filter(isCommissionEntry)]);
      setNextCursor(page.nextCursor);
    } catch {
      setEntriesError("Não foi possível carregar mais lançamentos.");
    } finally {
      setLoadingMore(false);
    }
  };

  const copyLink = async (link: AffiliateLink) => {
    if (!affiliateId || !link.offerSlug) return;
    const url = buildAffiliateLinkUrl(CHECKOUT_BASE_URL, link.offerSlug, affiliateId);
    await navigator.clipboard.writeText(url);
    setCopiedId(link.affiliationId);
    window.setTimeout(() => setCopiedId((current) => (current === link.affiliationId ? null : current)), 1800);
  };

  const linkRows = links.map((link) => [
    <span key="product">{link.productName}</span>,
    <span key="clicks">{link.clicks}</span>,
    <span key="orders">{link.orders}</span>,
    <span key="action">
      {link.offerSlug ? (
        <Botao variant="secondary" onClick={() => void copyLink(link)}>
          {copiedId === link.affiliationId ? "Link copiado!" : "Copiar link"}
        </Botao>
      ) : (
        <span title="Este produto ainda não tem oferta publicada">Sem oferta publicada</span>
      )}
    </span>,
  ]);

  const commissionRows = entries.map((entry) => {
    const status = commissionEntryStatus(entry);
    return [
      <strong key="amount">{formatarCentavos(entry.amountCents)}</strong>,
      <Etiqueta key="status" tone={commissionStatusTone[status]}>
        {status}
      </Etiqueta>,
      <span key="bucket">{entry.bucket}</span>,
      <span key="reference">{entry.reference ?? "—"}</span>,
      <span key="available">{formatDate(entry.availableAt)}</span>,
      <span key="created">{formatDate(entry.createdAt)}</span>,
    ];
  });

  return (
    <div className="meus-links-page">
      <header className="content-header">
        <div>
          <span className="paysi-rotulo">Divulgação</span>
          <h1>Meus links e comissões</h1>
          <p>Compartilhe seus links de afiliado e acompanhe a previsão, liberação e estorno das comissões.</p>
        </div>
      </header>

      <div
        role="tablist"
        aria-label="Seções"
        className="mode-switch"
        style={{ marginBottom: "1.5rem", width: "fit-content" }}
      >
        <button
          role="tab"
          aria-selected={tab === "links"}
          aria-controls="panel-links"
          id="tab-links"
          onClick={() => setTab("links")}
        >
          Meus links
        </button>
        <button
          role="tab"
          aria-selected={tab === "comissoes"}
          aria-controls="panel-comissoes"
          id="tab-comissoes"
          onClick={() => setTab("comissoes")}
        >
          Extrato de comissões
        </button>
      </div>

      {tab === "links" && (
        <section id="panel-links" role="tabpanel" aria-labelledby="tab-links">
          {linksError && (
            <Toast tone="danger">
              {linksError}{" "}
              <button className="toast-action" onClick={() => void loadLinks()}>
                Tentar novamente
              </button>
            </Toast>
          )}
          {loadingLinks ? (
            <Skeleton label="Carregando seus links" />
          ) : links.length === 0 ? (
            <EmptyState
              title="Nenhum link ainda"
              description="Assim que uma afiliação for aprovada, seu link de divulgação aparece aqui."
            />
          ) : (
            <Tabela
              caption="Links de afiliado com cliques e pedidos"
              headers={["Produto", "Cliques", "Pedidos", "Link"]}
              rows={linkRows}
            />
          )}
        </section>
      )}

      {tab === "comissoes" && (
        <section id="panel-comissoes" role="tabpanel" aria-labelledby="tab-comissoes">
          {entriesError && (
            <Toast tone="danger">
              {entriesError}{" "}
              <button className="toast-action" onClick={() => void loadEntries()}>
                Tentar novamente
              </button>
            </Toast>
          )}
          {loadingEntries ? (
            <Skeleton label="Carregando extrato de comissões" />
          ) : commissionRows.length === 0 ? (
            <EmptyState
              title="Nenhuma comissão ainda"
              description="Comissões aparecem aqui assim que um pedido atribuído a você for pago. Comissões de assinaturas recorrentes continuam aparecendo a cada ciclo cobrado, mesmo depois do encerramento comum da afiliação."
            />
          ) : (
            <>
              <Cartao aria-label="Como funciona a garantia">
                <p>
                  Toda comissão fica marcada como <strong>&quot;A liberar&quot;</strong> durante o período de garantia
                  da oferta e passa para <strong>&quot;Disponível&quot;</strong> automaticamente depois disso. Se a
                  venda for reembolsada ou contestada, a comissão aparece como <strong>&quot;Estornada&quot;</strong>.
                </p>
              </Cartao>
              <Tabela
                caption="Extrato de comissões"
                headers={["Valor", "Status", "Saldo", "Referência", "Liberação", "Data"]}
                rows={commissionRows}
              />
              {nextCursor && (
                <div style={{ marginTop: "1.5rem", textAlign: "center" }}>
                  <Botao variant="secondary" disabled={loadingMore} onClick={() => void loadMoreEntries()}>
                    {loadingMore ? "Carregando..." : "Carregar mais lançamentos"}
                  </Botao>
                </div>
              )}
            </>
          )}
        </section>
      )}
    </div>
  );
}
