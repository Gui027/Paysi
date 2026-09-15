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
  listOrders,
  OrderFilters,
  orderMatchesFilters,
  OrderPeriodPreset,
  orderStatusLabel,
  OrderStatus,
  OrderSummary,
  paymentMethodLabel,
  PaymentMethod,
} from "../../../lib/vendas";

const statusTone: Record<OrderStatus, "neutral" | "success" | "warning" | "danger"> = {
  PENDING: "warning",
  PAID: "success",
  REFUNDED: "neutral",
  PARTIALLY_REFUNDED: "warning",
  CHARGEBACK: "danger",
  FAILED: "danger",
};

const periodLabels: Record<string, string> = {
  "": "Todos os períodos",
  today: "Hoje",
  "7d": "Últimos 7 dias",
  "30d": "Últimos 30 dias",
};

function formatDate(iso: string) {
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

export function VendasPage() {
  const [orders, setOrders] = useState<OrderSummary[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [filters, setFilters] = useState<OrderFilters>({
    query: "",
    status: "",
    method: "",
    productId: "",
    period: "",
  });

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [orderPage, productPage] = await Promise.all([
        listOrders(filters),
        listProducts().catch(() => ({ items: [], nextCursor: null })),
      ]);
      setOrders(orderPage.items);
      setNextCursor(orderPage.nextCursor);
      setProducts(productPage.items);
    } catch {
      setError("Não foi possível carregar as vendas. Tente novamente.");
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
      const page = await listOrders(filters, nextCursor);
      setOrders((prev) => [...prev, ...page.items]);
      setNextCursor(page.nextCursor);
    } catch {
      setError("Não foi possível carregar mais vendas.");
    } finally {
      setLoadingMore(false);
    }
  };

  const filteredOrders = orders.filter((o) => orderMatchesFilters(o, filters));

  const tableRows = filteredOrders.map((o) => [
    <Link key={o.id} href={`/vendas/${o.id}`} className="font-semibold text-primary">
      {o.id.slice(0, 8)}…
    </Link>,
    <span key="buyer">{o.buyerNameMasked || "—"}</span>,
    <span key="product">{o.productName}</span>,
    <span key="method">{paymentMethodLabel[o.method] ?? o.method}</span>,
    <Etiqueta key="status" tone={statusTone[o.status]}>
      {orderStatusLabel[o.status] ?? o.status}
    </Etiqueta>,
    <span key="charges">
      {o.chargesCount} {o.chargesCount === 1 ? "cobrança" : "cobranças"}
    </span>,
    <strong key="amount">{formatarCentavos(o.paidCents)}</strong>,
    <span key="date">{formatDate(o.createdAt)}</span>,
  ]);

  return (
    <div className="vendas-page">
      <header className="content-header">
        <div>
          <span className="paysi-rotulo">Operação</span>
          <h1>Vendas e Cobranças</h1>
          <p>Consulte pedidos, histórico de cobranças e memória financeira.</p>
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

      <Cartao className="vendas-filtros" aria-label="Filtros de vendas">
        <div className="grid-filtros" style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: "1rem" }}>
          <label className="ui-field">
            <span>Buscar pedido ou comprador</span>
            <input
              type="search"
              placeholder="Código, produto, comprador..."
              value={filters.query}
              onChange={(e) => setFilters((f) => ({ ...f, query: e.target.value }))}
            />
          </label>

          <Select
            label="Período"
            value={filters.period}
            onChange={(e) =>
              setFilters((f) => ({ ...f, period: e.target.value as OrderPeriodPreset }))
            }
          >
            {Object.entries(periodLabels).map(([val, label]) => (
              <option key={val} value={val}>
                {label}
              </option>
            ))}
          </Select>

          <Select
            label="Status"
            value={filters.status}
            onChange={(e) =>
              setFilters((f) => ({ ...f, status: e.target.value as "" | OrderStatus }))
            }
          >
            <option value="">Todos os status</option>
            {Object.entries(orderStatusLabel).map(([val, label]) => (
              <option key={val} value={val}>
                {label}
              </option>
            ))}
          </Select>

          <Select
            label="Forma de pagamento"
            value={filters.method}
            onChange={(e) =>
              setFilters((f) => ({ ...f, method: e.target.value as "" | PaymentMethod }))
            }
          >
            <option value="">Todas as formas</option>
            {Object.entries(paymentMethodLabel).map(([val, label]) => (
              <option key={val} value={val}>
                {label}
              </option>
            ))}
          </Select>

          <Select
            label="Produto"
            value={filters.productId}
            onChange={(e) => setFilters((f) => ({ ...f, productId: e.target.value }))}
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
        <Skeleton label="Carregando vendas" />
      ) : filteredOrders.length === 0 ? (
        <EmptyState
          title="Nenhuma venda encontrada"
          description={
            filters.query || filters.status || filters.method || filters.productId || filters.period
              ? "Nenhum resultado corresponde aos filtros selecionados. Tente limpar ou ajustar a busca."
              : "Suas vendas confirmadas e cobranças geradas aparecerão aqui assim que as primeiras transações ocorrerem."
          }
          action={
            (filters.query || filters.status || filters.method || filters.productId || filters.period) ? (
              <Botao
                variant="secondary"
                onClick={() =>
                  setFilters({ query: "", status: "", method: "", productId: "", period: "" })
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
            caption="Lista de pedidos e cobranças realizadas"
            headers={[
              "Pedido",
              "Comprador",
              "Produto",
              "Método",
              "Status",
              "Cobranças",
              "Total pago",
              "Data",
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
                {loadingMore ? "Carregando..." : "Carregar mais vendas"}
              </Botao>
            </div>
          )}
        </>
      )}
    </div>
  );
}

