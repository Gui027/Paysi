"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  Botao,
  Cartao,
  EmptyState,
  Etiqueta,
  Select,
  Skeleton,
  Tabela,
  Toast,
} from "../../../components/ui";
import { formatarCentavos } from "../../../lib/moeda";
import { listProducts, Product } from "../../../lib/produtos";
import {
  listSubscriptions,
  SubscriptionFilters,
  subscriptionMatchesFilters,
  subscriptionMethodLabel,
  SubscriptionMethod,
  SubscriptionStatus,
  SubscriptionSummary,
  subscriptionStatusLabel,
  cycleLabel,
} from "../../../lib/assinaturas";

const statusTone: Record<
  SubscriptionStatus,
  "neutral" | "success" | "warning" | "danger"
> = {
  TRIALING: "neutral",
  ACTIVE: "success",
  PAST_DUE: "danger",
  CANCELED: "neutral",
  EXPIRED: "neutral",
};

function formatDate(iso: string | null) {
  if (!iso) return "—";
  try {
    return new Intl.DateTimeFormat("pt-BR", {
      dateStyle: "short",
      timeStyle: "short",
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

export function AssinaturasPage() {
  const [subscriptions, setSubscriptions] = useState<SubscriptionSummary[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [filters, setFilters] = useState<SubscriptionFilters>({
    query: "",
    status: "",
    method: "",
    productId: "",
  });

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [subPage, productPage] = await Promise.all([
        listSubscriptions(filters),
        listProducts().catch(() => ({ items: [], nextCursor: null })),
      ]);
      setSubscriptions(subPage.items);
      setNextCursor(subPage.nextCursor);
      setProducts(productPage.items);
    } catch {
      setError("Não foi possível carregar as assinaturas. Tente novamente.");
    } finally {
      setLoading(false);
    }
  }, [filters]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const loadMore = async () => {
    if (!nextCursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const page = await listSubscriptions(filters, nextCursor);
      setSubscriptions((prev) => [...prev, ...page.items]);
      setNextCursor(page.nextCursor);
    } catch {
      setError("Não foi possível carregar mais assinaturas.");
    } finally {
      setLoadingMore(false);
    }
  };

  const filtered = subscriptions.filter((s) =>
    subscriptionMatchesFilters(s, filters),
  );

  const tableRows = filtered.map((s) => [
    <Link
      key={s.id}
      href={`/assinaturas/${s.id}`}
      className="font-semibold text-primary"
    >
      {s.id.slice(0, 8)}…
    </Link>,
    <span key="buyer">{s.buyerNameMasked || "—"}</span>,
    <span key="product">{s.productName}</span>,
    <span key="method">{subscriptionMethodLabel[s.method] ?? s.method}</span>,
    <span key="cycle">{cycleLabel[s.cycle] ?? s.cycle}</span>,
    <Etiqueta key="status" tone={statusTone[s.status]}>
      {subscriptionStatusLabel[s.status] ?? s.status}
    </Etiqueta>,
    <span key="price">{formatarCentavos(s.priceCents)}</span>,
    <span key="next">
      {s.cancelAtPeriodEnd
        ? "Cancela ao fim do período"
        : formatDate(s.nextChargeAt)}
    </span>,
    <span key="date">{formatDate(s.createdAt)}</span>,
  ]);

  return (
    <div className="assinaturas-page">
      <header className="content-header">
        <div>
          <span className="paysi-rotulo">Operação</span>
          <h1>Assinaturas</h1>
          <p>Acompanhe ciclos, inadimplência e cancelamentos agendados.</p>
        </div>
      </header>

      {error && (
        <Toast tone="danger">
          {error}{" "}
          <button className="toast-action" onClick={() => void loadData()}>
            Tentar novamente
          </button>
        </Toast>
      )}

      <Cartao
        className="assinaturas-filtros"
        aria-label="Filtros de assinaturas"
      >
        <div
          className="grid-filtros"
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))",
            gap: "1rem",
          }}
        >
          <label className="ui-field">
            <span>Buscar assinatura ou comprador</span>
            <input
              type="search"
              placeholder="Código, produto, comprador..."
              value={filters.query}
              onChange={(e) =>
                setFilters((f) => ({ ...f, query: e.target.value }))
              }
            />
          </label>

          <Select
            label="Status"
            value={filters.status}
            onChange={(e) =>
              setFilters((f) => ({
                ...f,
                status: e.target.value as "" | SubscriptionStatus,
              }))
            }
          >
            <option value="">Todos os status</option>
            {Object.entries(subscriptionStatusLabel).map(([val, label]) => (
              <option key={val} value={val}>
                {label}
              </option>
            ))}
          </Select>

          <Select
            label="Forma de pagamento"
            value={filters.method}
            onChange={(e) =>
              setFilters((f) => ({
                ...f,
                method: e.target.value as "" | SubscriptionMethod,
              }))
            }
          >
            <option value="">Todas as formas</option>
            {Object.entries(subscriptionMethodLabel).map(([val, label]) => (
              <option key={val} value={val}>
                {label}
              </option>
            ))}
          </Select>

          <Select
            label="Produto"
            value={filters.productId}
            onChange={(e) =>
              setFilters((f) => ({ ...f, productId: e.target.value }))
            }
          >
            <option value="">Todos os produtos</option>
            {products.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </Select>
        </div>
      </Cartao>

      {loading ? (
        <Skeleton label="Carregando assinaturas" />
      ) : filtered.length === 0 ? (
        <EmptyState
          title="Nenhuma assinatura encontrada"
          description={
            filters.query ||
            filters.status ||
            filters.method ||
            filters.productId
              ? "Nenhum resultado corresponde aos filtros selecionados. Tente limpar ou ajustar a busca."
              : "Suas assinaturas ativas e encerradas aparecerão aqui assim que os primeiros ciclos ocorrerem."
          }
          action={
            filters.query ||
            filters.status ||
            filters.method ||
            filters.productId ? (
              <Botao
                variant="secondary"
                onClick={() =>
                  setFilters({
                    query: "",
                    status: "",
                    method: "",
                    productId: "",
                  })
                }
              >
                Limpar filtros
              </Botao>
            ) : undefined
          }
        />
      ) : (
        <>
          <Tabela
            caption="Lista de assinaturas ativas e encerradas"
            headers={[
              "Assinatura",
              "Comprador",
              "Produto",
              "Método",
              "Ciclo",
              "Status",
              "Valor",
              "Próxima cobrança",
              "Criada em",
            ]}
            rows={tableRows}
          />

          {nextCursor && (
            <div style={{ marginTop: "1.5rem", textAlign: "center" }}>
              <Botao
                variant="secondary"
                disabled={loadingMore}
                onClick={() => void loadMore()}
              >
                {loadingMore ? "Carregando..." : "Carregar mais assinaturas"}
              </Botao>
            </div>
          )}
        </>
      )}
    </div>
  );
}
