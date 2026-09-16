"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  Botao,
  Cartao,
  Dialog,
  EmptyState,
  Etiqueta,
  Skeleton,
  Tabela,
  Toast,
} from "../../../../components/ui";
import { formatarCentavos } from "../../../../lib/moeda";
import {
  cancelSubscription,
  ChargeRecord,
  cycleLabel,
  dunningDayLabel,
  getSubscription,
  SubscriptionDetail,
  SubscriptionStatus,
  subscriptionStatusLabel,
  subscriptionMethodLabel,
} from "../../../../lib/assinaturas";

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

const chargeStatusTone: Record<
  ChargeRecord["status"],
  "neutral" | "success" | "warning" | "danger"
> = {
  APPROVED: "success",
  DECLINED: "danger",
  PENDING: "warning",
  REFUNDED: "neutral",
};

const chargeStatusLabel: Record<ChargeRecord["status"], string> = {
  APPROVED: "Aprovada",
  DECLINED: "Recusada",
  PENDING: "Pendente",
  REFUNDED: "Reembolsada",
};

type TabType = "resumo" | "ciclos";

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

function formatDateOnly(iso: string | null) {
  if (!iso) return "—";
  try {
    return new Intl.DateTimeFormat("pt-BR", { dateStyle: "medium" }).format(
      new Date(iso),
    );
  } catch {
    return iso;
  }
}

/** Régua de retentativa D+1/3/7/14 mostrada quando status = PAST_DUE */
function DunningRuler({
  attemptCount,
  nextRetryAt,
}: {
  attemptCount: number;
  nextRetryAt: string | null;
}) {
  const steps = [
    { day: "D+1", attempt: 1 },
    { day: "D+3", attempt: 2 },
    { day: "D+7", attempt: 3 },
    { day: "D+14", attempt: 4 },
  ];

  return (
    <div
      aria-label="Régua de retentativas de cobrança"
      style={{ marginTop: "1rem" }}
    >
      <p style={{ color: "var(--paysi-text-muted)", marginBottom: "0.75rem" }}>
        A cobrança será retentada automaticamente conforme a régua abaixo.
        Esgotadas as tentativas, a assinatura é cancelada.
      </p>
      <ol
        style={{
          display: "flex",
          gap: "0.5rem",
          listStyle: "none",
          padding: 0,
          margin: 0,
          flexWrap: "wrap",
        }}
      >
        {steps.map(({ day, attempt }) => {
          const done = attemptCount >= attempt;
          const current = attemptCount === attempt - 1;
          return (
            <li
              key={day}
              style={{
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                gap: "0.25rem",
                flex: "1 1 64px",
                minWidth: "64px",
              }}
            >
              <span
                style={{
                  width: 32,
                  height: 32,
                  borderRadius: "50%",
                  display: "inline-flex",
                  alignItems: "center",
                  justifyContent: "center",
                  fontWeight: 700,
                  fontSize: "0.8rem",
                  backgroundColor: done
                    ? "var(--paysi-danger, #dc2626)"
                    : current
                      ? "var(--paysi-warning, #d97706)"
                      : "var(--paysi-muted, #e5e7eb)",
                  color: done || current ? "#fff" : "var(--paysi-text-muted)",
                }}
                aria-current={current ? "step" : undefined}
              >
                {attempt}
              </span>
              <span style={{ fontSize: "0.75rem", fontWeight: 600 }}>
                {day}
              </span>
              <span
                style={{ fontSize: "0.7rem", color: "var(--paysi-text-muted)" }}
              >
                {done ? "Falhou" : current ? "Próxima" : "Pendente"}
              </span>
            </li>
          );
        })}
      </ol>
      {nextRetryAt && (
        <p style={{ marginTop: "0.75rem", fontSize: "0.875rem" }}>
          <strong>Próxima tentativa:</strong> {formatDate(nextRetryAt)}
        </p>
      )}
    </div>
  );
}

export function SubscriptionDetailPage({
  subscriptionId,
}: {
  subscriptionId: string;
}) {
  const [sub, setSub] = useState<SubscriptionDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<TabType>("resumo");

  const [cancelDialog, setCancelDialog] = useState(false);
  const [canceling, setCanceling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);
  const [cancelSuccess, setCancelSuccess] = useState(false);

  const loadSub = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getSubscription(subscriptionId);
      setSub(data);
    } catch {
      setError("Não foi possível carregar os detalhes da assinatura.");
    } finally {
      setLoading(false);
    }
  }, [subscriptionId]);

  useEffect(() => {
    void loadSub();
  }, [loadSub]);

  const handleCancel = async () => {
    if (!sub) return;
    setCanceling(true);
    setCancelError(null);
    try {
      await cancelSubscription(sub.id);
      setCancelSuccess(true);
      setCancelDialog(false);
      // Recarrega para refletir cancelAtPeriodEnd = true
      await loadSub();
    } catch {
      setCancelError(
        "Não foi possível agendar o cancelamento. Tente novamente.",
      );
    } finally {
      setCanceling(false);
    }
  };

  if (loading) {
    return <Skeleton label="Carregando detalhes da assinatura" />;
  }

  if (error || !sub) {
    return (
      <div className="subscription-detail-error">
        <nav aria-label="Navegação estrutural" style={{ marginBottom: "1rem" }}>
          <Link href="/assinaturas" className="text-secondary">
            ← Voltar para Assinaturas
          </Link>
        </nav>
        <EmptyState
          title="Assinatura não encontrada"
          description={
            error ??
            "A assinatura informada não foi encontrada ou pertence a outro vendedor."
          }
          action={
            <Botao variant="secondary" onClick={() => void loadSub()}>
              Tentar novamente
            </Botao>
          }
        />
      </div>
    );
  }

  const canCancel =
    (sub.status === "ACTIVE" ||
      sub.status === "TRIALING" ||
      sub.status === "PAST_DUE") &&
    !sub.cancelAtPeriodEnd;

  return (
    <div className="subscription-detail-page">
      <nav aria-label="Navegação estrutural" style={{ marginBottom: "1rem" }}>
        <Link
          href="/assinaturas"
          className="text-secondary"
          style={{ textDecoration: "none" }}
        >
          ← Voltar para Assinaturas
        </Link>
      </nav>

      {cancelSuccess && (
        <Toast tone="success">
          Cancelamento agendado com sucesso. A assinatura permanece ativa até o
          fim do período atual.
        </Toast>
      )}

      {cancelError && (
        <Toast tone="danger">
          {cancelError}{" "}
          <button className="toast-action" onClick={() => void handleCancel()}>
            Tentar novamente
          </button>
        </Toast>
      )}

      {/* Cabeçalho */}
      <header className="content-header" style={{ marginBottom: "1.5rem" }}>
        <div style={{ flex: 1 }}>
          <span className="paysi-rotulo">
            Assinatura #{sub.id.slice(0, 12)}
          </span>
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "0.75rem",
              marginTop: "0.25rem",
              flexWrap: "wrap",
            }}
          >
            <h1 style={{ margin: 0 }}>{sub.productName}</h1>
            <Etiqueta tone={statusTone[sub.status]}>
              {subscriptionStatusLabel[sub.status] ?? sub.status}
            </Etiqueta>
            {sub.cancelAtPeriodEnd && (
              <Etiqueta tone="warning">Cancelamento agendado</Etiqueta>
            )}
          </div>
          <p style={{ margin: "0.25rem 0 0" }}>
            {cycleLabel[sub.cycle]} •{" "}
            {subscriptionMethodLabel[sub.method] ?? sub.method} •{" "}
            <strong>{formatarCentavos(sub.priceCents)}</strong>
          </p>
        </div>

        {canCancel && (
          <div>
            <Botao variant="danger" onClick={() => setCancelDialog(true)}>
              Cancelar assinatura
            </Botao>
          </div>
        )}
      </header>

      {/* Alerta de inadimplência */}
      {sub.status === "PAST_DUE" && (
        <Cartao
          style={{
            borderLeft: "4px solid var(--paysi-danger, #dc2626)",
            marginBottom: "1.5rem",
          }}
        >
          <h2 style={{ color: "var(--paysi-danger, #dc2626)" }}>
            ⚠ Assinatura Inadimplente
          </h2>
          <p>
            A cobrança do ciclo atual foi recusada. A assinatura permanece ativa
            durante o processo de retentativa.
          </p>
          <DunningRuler
            attemptCount={sub.attemptCount}
            nextRetryAt={sub.nextRetryAt}
          />
        </Cartao>
      )}

      {/* Alerta de período de teste sem cartão */}
      {sub.status === "TRIALING" && !sub.nextChargeAt && (
        <Cartao
          style={{
            borderLeft: "4px solid var(--paysi-warning, #d97706)",
            marginBottom: "1.5rem",
          }}
        >
          <h2>Período de teste sem cartão cadastrado</h2>
          <p>
            O assinante está em período de teste e ainda não cadastrou um método
            de pagamento. Ao término do trial em{" "}
            <strong>{formatDateOnly(sub.trialEnd)}</strong>, a assinatura ficará
            em aguardando atualização de cartão (PAST_DUE) até que o assinante
            adicione um cartão.
          </p>
        </Cartao>
      )}

      {/* Cancelamento agendado */}
      {sub.cancelAtPeriodEnd && (
        <Cartao
          style={{
            borderLeft: "4px solid var(--paysi-warning, #d97706)",
            marginBottom: "1.5rem",
          }}
        >
          <h2>Cancelamento agendado</h2>
          <p>
            Esta assinatura foi cancelada pelo comprador e permanecerá ativa até
            o fim do período já pago (
            <strong>{formatDateOnly(sub.nextChargeAt)}</strong>). Após essa
            data, o acesso será encerrado automaticamente sem nova cobrança.
          </p>
        </Cartao>
      )}

      {/* Tabs */}
      <div
        role="tablist"
        aria-label="Seções da assinatura"
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
          aria-selected={activeTab === "ciclos"}
          aria-controls="panel-ciclos"
          id="tab-ciclos"
          onClick={() => setActiveTab("ciclos")}
        >
          Ciclos e Cobranças ({sub.charges.length})
        </button>
      </div>

      {/* Tab: Resumo */}
      {activeTab === "resumo" && (
        <section id="panel-resumo" role="tabpanel" aria-labelledby="tab-resumo">
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))",
              gap: "1.5rem",
            }}
          >
            <Cartao>
              <h2>Comprador</h2>
              <dl
                style={{
                  display: "grid",
                  gridTemplateColumns: "auto 1fr",
                  gap: "0.5rem 1rem",
                  margin: "1rem 0 0",
                }}
              >
                <dt style={{ color: "var(--paysi-text-muted)" }}>Nome:</dt>
                <dd>{sub.buyerNameMasked || "—"}</dd>
              </dl>
              <small
                style={{
                  display: "block",
                  marginTop: "0.75rem",
                  color: "var(--paysi-text-muted)",
                }}
              >
                * Dados mascarados em conformidade com a LGPD.
              </small>
            </Cartao>

            <Cartao>
              <h2>Produto e Oferta</h2>
              <dl
                style={{
                  display: "grid",
                  gridTemplateColumns: "auto 1fr",
                  gap: "0.5rem 1rem",
                  margin: "1rem 0 0",
                }}
              >
                <dt style={{ color: "var(--paysi-text-muted)" }}>Produto:</dt>
                <dd>
                  <Link
                    href={`/produtos/${sub.productId}`}
                    className="font-semibold text-primary"
                  >
                    {sub.productName}
                  </Link>
                </dd>
                <dt style={{ color: "var(--paysi-text-muted)" }}>Oferta:</dt>
                <dd>{sub.offerTitle}</dd>
                {sub.affiliateName && (
                  <>
                    <dt style={{ color: "var(--paysi-text-muted)" }}>
                      Afiliado:
                    </dt>
                    <dd>{sub.affiliateName}</dd>
                  </>
                )}
              </dl>
            </Cartao>

            <Cartao>
              <h2>Datas e Ciclo</h2>
              <dl
                style={{
                  display: "grid",
                  gridTemplateColumns: "auto 1fr",
                  gap: "0.5rem 1rem",
                  margin: "1rem 0 0",
                }}
              >
                <dt style={{ color: "var(--paysi-text-muted)" }}>Criada em:</dt>
                <dd>{formatDate(sub.createdAt)}</dd>
                {sub.trialEnd && (
                  <>
                    <dt style={{ color: "var(--paysi-text-muted)" }}>
                      Fim do trial:
                    </dt>
                    <dd>{formatDateOnly(sub.trialEnd)}</dd>
                  </>
                )}
                <dt style={{ color: "var(--paysi-text-muted)" }}>
                  {sub.cancelAtPeriodEnd ? "Acesso até:" : "Próxima cobrança:"}
                </dt>
                <dd>
                  {sub.cancelAtPeriodEnd
                    ? formatDateOnly(sub.nextChargeAt)
                    : formatDate(sub.nextChargeAt)}
                </dd>
                <dt style={{ color: "var(--paysi-text-muted)" }}>Garantia:</dt>
                <dd>{sub.guaranteeDays} dias</dd>
              </dl>
              <small
                style={{
                  display: "block",
                  marginTop: "0.75rem",
                  color: "var(--paysi-text-muted)",
                }}
              >
                * Todas as datas exibidas no fuso horário local do navegador.
              </small>
            </Cartao>
          </div>
        </section>
      )}

      {/* Tab: Ciclos e Cobranças */}
      {activeTab === "ciclos" && (
        <section id="panel-ciclos" role="tabpanel" aria-labelledby="tab-ciclos">
          {sub.charges.length === 0 ? (
            <EmptyState
              title="Nenhuma cobrança registrada"
              description="Ainda não há cobranças de ciclo para esta assinatura."
            />
          ) : (
            <Tabela
              caption="Histórico de cobranças por ciclo da assinatura"
              headers={["Ciclo", "Status", "Método", "Valor", "Data"]}
              rows={sub.charges.map((c) => [
                <span key={c.id}>#{c.cycleNumber}</span>,
                <Etiqueta key="status" tone={chargeStatusTone[c.status]}>
                  {chargeStatusLabel[c.status] ?? c.status}
                </Etiqueta>,
                subscriptionMethodLabel[c.method] ?? c.method,
                formatarCentavos(c.amountCents),
                <span key="date">{formatDate(c.occurredAt)}</span>,
              ])}
            />
          )}
        </section>
      )}

      {/* Dialog de confirmação de cancelamento */}
      <Dialog
        open={cancelDialog}
        title="Cancelar assinatura"
        onClose={() => setCancelDialog(false)}
      >
        <p>
          Ao confirmar, o cancelamento será agendado para o fim do período
          atual. O assinante continuará com acesso até essa data e{" "}
          <strong>não será cobrado novamente</strong>.
        </p>
        <p style={{ color: "var(--paysi-text-muted)", fontSize: "0.875rem" }}>
          Esta ação não pode ser revertida. O cancelamento não é imediato.
        </p>
        {cancelError && (
          <Toast tone="danger" style={{ marginBottom: "1rem" }}>
            {cancelError}
          </Toast>
        )}
        <div style={{ display: "flex", gap: "1rem", marginTop: "1.5rem" }}>
          <Botao
            variant="danger"
            disabled={canceling}
            onClick={() => void handleCancel()}
          >
            {canceling ? "Cancelando..." : "Confirmar cancelamento"}
          </Botao>
          <Botao
            variant="secondary"
            disabled={canceling}
            onClick={() => setCancelDialog(false)}
          >
            Manter assinatura
          </Botao>
        </div>
      </Dialog>
    </div>
  );
}
