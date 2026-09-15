"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  Botao,
  Cartao,
  EmptyState,
  Etiqueta,
  Skeleton,
  Tabela,
  Toast,
} from "../../../../components/ui";
import { formatarCentavos } from "../../../../lib/moeda";
import {
  ChargeDetail,
  getOrder,
  OrderDetail,
  orderStatusLabel,
  OrderStatus,
  paymentMethodLabel,
  splitRoleLabel,
} from "../../../../lib/vendas";

const statusTone: Record<OrderStatus, "neutral" | "success" | "warning" | "danger"> = {
  PENDING: "warning",
  PAID: "success",
  REFUNDED: "neutral",
  PARTIALLY_REFUNDED: "warning",
  CHARGEBACK: "danger",
  FAILED: "danger",
};

type TabType = "resumo" | "cobrancas" | "split" | "evidencias";

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

export function OrderDetailPage({ orderId }: { orderId: string }) {
  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<TabType>("resumo");

  const loadOrder = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getOrder(orderId);
      setOrder(data);
    } catch {
      setError("Não foi possível carregar os detalhes da venda.");
    } finally {
      setLoading(false);
    }
  }, [orderId]);

  useEffect(() => {
    void loadOrder();
  }, [loadOrder]);

  if (loading) {
    return <Skeleton label="Carregando detalhes do pedido" />;
  }

  if (error || !order) {
    return (
      <div className="order-detail-error">
        <nav aria-label="Navegação estrutural" style={{ marginBottom: "1rem" }}>
          <Link href="/vendas" className="text-secondary">
            ← Voltar para Vendas
          </Link>
        </nav>
        <EmptyState
          title="Venda não encontrada"
          description={error ?? "O pedido informado não foi encontrado ou pertence a outro vendedor."}
          action={
            <Botao variant="secondary" onClick={() => void loadOrder()}>
              Tentar novamente
            </Botao>
          }
        />
      </div>
    );
  }

  return (
    <div className="order-detail-page">
      <nav aria-label="Navegação estrutural" style={{ marginBottom: "1rem" }}>
        <Link href="/vendas" className="text-secondary" style={{ textDecoration: "none" }}>
          ← Voltar para Vendas
        </Link>
      </nav>

      <header className="content-header order-header" style={{ marginBottom: "1.5rem" }}>
        <div>
          <span className="paysi-rotulo">Pedido #{order.id.slice(0, 12)}</span>
          <div style={{ display: "flex", alignItems: "center", gap: "0.75rem", marginTop: "0.25rem" }}>
            <h1 style={{ margin: 0 }}>{order.product.name}</h1>
            <Etiqueta tone={statusTone[order.status]}>
              {orderStatusLabel[order.status] ?? order.status}
            </Etiqueta>
          </div>
          <p style={{ margin: "0.25rem 0 0" }}>
            Realizado em {formatDate(order.createdAt)} • Total pago:{" "}
            <strong>{formatarCentavos(order.paidCents)}</strong>
          </p>
        </div>
      </header>

      {/* Tabs */}
      <div
        role="tablist"
        aria-label="Seções do pedido"
        className="mode-switch"
        style={{ marginBottom: "1.5rem", width: "fit-content" }}
      >
        <button
          role="tab"
          aria-selected={activeTab === "resumo"}
          aria-controls="panel-resumo"
          id="tab-resumo"
          onClick={() => setActiveTab("resumo")}
        >
          Resumo
        </button>
        <button
          role="tab"
          aria-selected={activeTab === "cobrancas"}
          aria-controls="panel-cobrancas"
          id="tab-cobrancas"
          onClick={() => setActiveTab("cobrancas")}
        >
          Cobranças ({order.charges.length})
        </button>
        <button
          role="tab"
          aria-selected={activeTab === "split"}
          aria-controls="panel-split"
          id="tab-split"
          onClick={() => setActiveTab("split")}
        >
          Divisão e Recebíveis
        </button>
        <button
          role="tab"
          aria-selected={activeTab === "evidencias"}
          aria-controls="panel-evidencias"
          id="tab-evidencias"
          onClick={() => setActiveTab("evidencias")}
        >
          Segurança e 3DS
        </button>
      </div>

      {/* Tab: Resumo */}
      {activeTab === "resumo" && (
        <section id="panel-resumo" role="tabpanel" aria-labelledby="tab-resumo">
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: "1.5rem" }}>
            <Cartao>
              <h2>Comprador</h2>
              <dl style={{ display: "grid", gridTemplateColumns: "auto 1fr", gap: "0.5rem 1rem", margin: "1rem 0 0" }}>
                <dt style={{ color: "var(--paysi-text-muted)" }}>Nome:</dt>
                <dd>{order.buyer.nameMasked}</dd>
                <dt style={{ color: "var(--paysi-text-muted)" }}>E-mail:</dt>
                <dd>{order.buyer.emailMasked}</dd>
                <dt style={{ color: "var(--paysi-text-muted)" }}>Documento:</dt>
                <dd>{order.buyer.documentMasked}</dd>
              </dl>
              <small style={{ display: "block", marginTop: "0.75rem", color: "var(--paysi-text-muted)" }}>
                * Dados mascarados em conformidade com as diretrizes de privacidade e LGPD.
              </small>
            </Cartao>

            <Cartao>
              <h2>Produto e Oferta</h2>
              <dl style={{ display: "grid", gridTemplateColumns: "auto 1fr", gap: "0.5rem 1rem", margin: "1rem 0 0" }}>
                <dt style={{ color: "var(--paysi-text-muted)" }}>Produto:</dt>
                <dd>
                  <Link href={`/produtos/${order.product.id}`} className="font-semibold text-primary">
                    {order.product.name}
                  </Link>
                </dd>
                <dt style={{ color: "var(--paysi-text-muted)" }}>Oferta:</dt>
                <dd>{order.offer.title} ({order.offer.slug})</dd>
                {order.terms && (
                  <>
                    <dt style={{ color: "var(--paysi-text-muted)" }}>Termos aceitos:</dt>
                    <dd>
                      Versão {order.terms.version} em {formatDate(order.terms.acceptedAt)}
                    </dd>
                  </>
                )}
                {order.affiliation && (
                  <>
                    <dt style={{ color: "var(--paysi-text-muted)" }}>Afiliado:</dt>
                    <dd>
                      {order.affiliation.affiliateName} ({formatarCentavos(order.affiliation.commissionCents)})
                    </dd>
                  </>
                )}
              </dl>
            </Cartao>

            <Cartao>
              <h2>Valores da Transação</h2>
              <dl style={{ display: "grid", gridTemplateColumns: "auto 1fr", gap: "0.5rem 1rem", margin: "1rem 0 0" }}>
                <dt style={{ color: "var(--paysi-text-muted)" }}>Valor bruto:</dt>
                <dd>{formatarCentavos(order.grossCents)}</dd>
                <dt style={{ color: "var(--paysi-text-muted)" }}>Descontos:</dt>
                <dd>{formatarCentavos(order.discountCents)}</dd>
                <dt style={{ color: "var(--paysi-text-muted)" }}>Valor pago:</dt>
                <dd>
                  <strong>{formatarCentavos(order.paidCents)}</strong>
                </dd>
              </dl>
              <small style={{ display: "block", marginTop: "0.75rem", color: "var(--paysi-text-muted)" }}>
                * Todos os valores são totalizados no backend (sem aritmética local).
              </small>
            </Cartao>
          </div>
        </section>
      )}

      {/* Tab: Cobranças */}
      {activeTab === "cobrancas" && (
        <section id="panel-cobrancas" role="tabpanel" aria-labelledby="tab-cobrancas">
          <Tabela
            caption="Histórico de cobranças do pedido"
            headers={["#", "Código da Cobrança", "Método", "Status", "Valor", "Estornado", "Confirmada em"]}
            rows={order.charges.map((c) => [
              `#${c.sequence}`,
              <code key={c.id}>{c.id}</code>,
              paymentMethodLabel[c.method] ?? c.method,
              <Etiqueta key="status" tone={statusTone[c.status]}>
                {orderStatusLabel[c.status] ?? c.status}
              </Etiqueta>,
              formatarCentavos(c.amountCents),
              c.refundedCents > 0 ? formatarCentavos(c.refundedCents) : "—",
              formatDate(c.confirmedAt),
            ])}
          />
        </section>
      )}

      {/* Tab: Divisão e Recebíveis */}
      {activeTab === "split" && (
        <section id="panel-split" role="tabpanel" aria-labelledby="tab-split">
          <div style={{ display: "flex", flexDirection: "column", gap: "2rem" }}>
            <div>
              <h3>Divisão de Pagamentos (Split)</h3>
              <p style={{ color: "var(--paysi-text-muted)", marginBottom: "1rem" }}>
                Distribuição das cobranças entre os participantes da venda.
              </p>
              {order.charges.map((charge) => (
                <div key={charge.id} style={{ marginBottom: "1.5rem" }}>
                  <h4>Cobrança #{charge.sequence} ({formatarCentavos(charge.amountCents)})</h4>
                  <Tabela
                    caption={`Divisão de pagamentos da cobrança #${charge.sequence}`}
                    headers={["Participante", "Papel", "Valor Destinado"]}
                    rows={charge.split.map((s, idx) => [
                      s.recipient,
                      splitRoleLabel[s.role] ?? s.role,
                      <strong>{formatarCentavos(s.amountCents)}</strong>,
                    ])}
                  />
                </div>
              ))}
            </div>

            <div>
              <h3>Cronograma de Recebíveis</h3>
              <p style={{ color: "var(--paysi-text-muted)", marginBottom: "1rem" }}>
                Previsão de liquidação por parcela e alocação nos buckets de saldo.
              </p>
              {order.charges.map((charge) => (
                <div key={charge.id} style={{ marginBottom: "1.5rem" }}>
                  <h4>Cobrança #{charge.sequence}</h4>
                  <Tabela
                    caption={`Recebíveis da cobrança #${charge.sequence}`}
                    headers={["Parcela", "Bucket de Saldo", "Previsão de Liberação", "Status", "Valor"]}
                    rows={charge.receivables.map((r) => [
                      `Parcela ${r.installmentNumber}`,
                      <Etiqueta key="b" tone="neutral">{r.bucket}</Etiqueta>,
                      formatDate(r.availableAt),
                      r.status,
                      <strong>{formatarCentavos(r.amountCents)}</strong>,
                    ])}
                  />
                </div>
              ))}
            </div>
          </div>
        </section>
      )}

      {/* Tab: Segurança e 3DS */}
      {activeTab === "evidencias" && (
        <section id="panel-evidencias" role="tabpanel" aria-labelledby="tab-evidencias">
          <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
            {order.charges.map((charge) => (
              <Cartao key={charge.id}>
                <h3>Segurança da Cobrança #{charge.sequence}</h3>
                {charge.threeDS ? (
                  <div style={{ marginTop: "1rem" }}>
                    <dl style={{ display: "grid", gridTemplateColumns: "auto 1fr", gap: "0.5rem 1rem" }}>
                      <dt style={{ color: "var(--paysi-text-muted)" }}>Protocolo 3DS:</dt>
                      <dd>Versão {charge.threeDS.version}</dd>
                      <dt style={{ color: "var(--paysi-text-muted)" }}>ECI (Electronic Commerce Indicator):</dt>
                      <dd><code>{charge.threeDS.eci}</code></dd>
                      <dt style={{ color: "var(--paysi-text-muted)" }}>Assinatura CAVV presente:</dt>
                      <dd>{charge.threeDS.cavvPresent ? "Sim (verificada)" : "Não"}</dd>
                      <dt style={{ color: "var(--paysi-text-muted)" }}>Repasse de responsabilidade (Liability Shift):</dt>
                      <dd>
                        {charge.threeDS.liabilityShifted ? (
                          <Etiqueta tone="success">Ativo (Responsabilidade do Emissor)</Etiqueta>
                        ) : (
                          <Etiqueta tone="warning">Inativo</Etiqueta>
                        )}
                      </dd>
                    </dl>
                  </div>
                ) : (
                  <p style={{ color: "var(--paysi-text-muted)", marginTop: "0.5rem" }}>
                    Esta cobrança utilizou método direto ({paymentMethodLabel[charge.method] ?? charge.method}) sem necessidade de autenticação 3DS.
                  </p>
                )}

                {charge.events && charge.events.length > 0 && (
                  <div style={{ marginTop: "1.5rem" }}>
                    <h4>Linha do Tempo de Eventos</h4>
                    <ul style={{ listStyle: "none", padding: 0, margin: "0.5rem 0 0" }}>
                      {charge.events.map((evt) => (
                        <li
                          key={evt.id}
                          style={{
                            padding: "0.5rem 0",
                            borderBottom: "1px solid var(--paysi-border, #eee)",
                            display: "flex",
                            justifyContent: "space-between",
                            alignItems: "center",
                          }}
                        >
                          <div>
                            <strong>{evt.type}</strong>: {evt.description}
                          </div>
                          <span style={{ fontSize: "0.85rem", color: "var(--paysi-text-muted)" }}>
                            {formatDate(evt.occurredAt)}
                          </span>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </Cartao>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}

