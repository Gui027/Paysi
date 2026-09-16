"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Botao, Cartao, Dialog, EmptyState, Etiqueta, Skeleton, Tabela, Toast } from "../../../../components/ui";
import { formatarCentavos } from "../../../../lib/moeda";
import {
  cancelSubscription,
  chargeStatusLabel,
  ChargeStatus,
  DUNNING_SCHEDULE_DAYS,
  getSubscription,
  isTrialWithoutCard,
  Subscription,
  SubscriptionCharge,
  subscriptionStatusLabel,
  SubscriptionStatus,
} from "../../../../lib/assinaturas";

const statusTone: Record<SubscriptionStatus, "neutral" | "success" | "warning" | "danger"> = {
  TRIAL: "neutral",
  ACTIVE: "success",
  PAST_DUE: "warning",
  CANCELED: "danger",
};

const chargeTone: Record<ChargeStatus, "neutral" | "success" | "warning" | "danger"> = {
  PENDING: "neutral",
  PAID: "success",
  FAILED: "danger",
  EXPIRED: "danger",
  PARTIALLY_REFUNDED: "warning",
  REFUNDED: "neutral",
  CHARGEBACK: "danger",
};

function formatDate(iso: string | null) {
  if (!iso) return "—";
  try {
    return new Intl.DateTimeFormat("pt-BR", { dateStyle: "long", timeStyle: "short" }).format(new Date(iso));
  } catch {
    return iso;
  }
}

function retrySchedule(charge: SubscriptionCharge): string {
  if (charge.status !== "FAILED" || !charge.nextRetryAt) return "—";
  const dayIndex = Math.min(charge.attemptCount - 1, DUNNING_SCHEDULE_DAYS.length - 1);
  const day = DUNNING_SCHEDULE_DAYS[Math.max(dayIndex, 0)];
  return `Próxima tentativa em D+${day} (${formatDate(charge.nextRetryAt)})`;
}

export function SubscriptionDetailPage({ subscriptionId }: { subscriptionId: string }) {
  const [subscription, setSubscription] = useState<Subscription | null>(null);
  const [charges, setCharges] = useState<SubscriptionCharge[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [toastMessage, setToastMessage] = useState<{ tone: "success" | "danger"; text: string } | null>(null);

  const [cancelDialogOpen, setCancelDialogOpen] = useState(false);
  const [canceling, setCanceling] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const detail = await getSubscription(subscriptionId);
      setSubscription(detail.subscription);
      setCharges(detail.charges);
    } catch {
      setError("Não foi possível carregar a assinatura.");
    } finally {
      setLoading(false);
    }
  }, [subscriptionId]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleCancel = async () => {
    setCanceling(true);
    try {
      await cancelSubscription(subscriptionId);
      setCancelDialogOpen(false);
      setToastMessage({ tone: "success", text: "Cancelamento agendado para o fim do ciclo vigente." });
      await load();
    } catch {
      setToastMessage({ tone: "danger", text: "Não foi possível agendar o cancelamento. Tente novamente." });
    } finally {
      setCanceling(false);
    }
  };

  if (loading) return <Skeleton label="Carregando detalhes da assinatura" />;
  if (error || !subscription) {
    return (
      <EmptyState
        title="Assinatura não encontrada"
        description={error ?? "Não foi possível localizar esta assinatura."}
        action={
          <Botao variant="secondary" onClick={() => void load()}>
            Tentar novamente
          </Botao>
        }
      />
    );
  }

  const canCancel = subscription.status !== "CANCELED" && !subscription.cancelPending;

  const chargeRows = charges.map((c) => [
    <span key="cycle">{c.cycleNumber === 0 ? "Teste" : `Ciclo ${c.cycleNumber}`}</span>,
    <strong key="amount">{formatarCentavos(c.amountCents)}</strong>,
    <Etiqueta key="status" tone={chargeTone[c.status]}>
      {chargeStatusLabel[c.status]}
    </Etiqueta>,
    <span key="attempts">{c.attemptCount}ª tentativa</span>,
    <span key="retry">{retrySchedule(c)}</span>,
    <span key="paid">{formatDate(c.paidAt)}</span>,
    <span key="created">{formatDate(c.createdAt)}</span>,
  ]);

  return (
    <div className="assinatura-detalhe">
      <header className="content-header">
        <div>
          <Link href="/assinaturas">← Voltar para assinaturas</Link>
          <h1>Assinatura {subscription.id.slice(0, 8)}…</h1>
        </div>
      </header>

      {toastMessage && <Toast tone={toastMessage.tone}>{toastMessage.text}</Toast>}
      {toastMessage?.tone === "danger" && (
        <button className="toast-action" onClick={() => void handleCancel()}>
          Tentar de novo
        </button>
      )}

      <Cartao aria-label="Resumo da assinatura">
        <dl style={{ display: "grid", gridTemplateColumns: "auto 1fr", gap: "0.5rem 1rem", margin: "1rem 0 0" }}>
          <dt style={{ color: "var(--paysi-text-muted)" }}>Status:</dt>
          <dd>
            <Etiqueta tone={statusTone[subscription.status]}>
              {subscriptionStatusLabel[subscription.status]}
            </Etiqueta>
            {isTrialWithoutCard(subscription) && (
              <Etiqueta tone="neutral">Teste sem cartão cadastrado</Etiqueta>
            )}
          </dd>

          <dt style={{ color: "var(--paysi-text-muted)" }}>Ciclo atual:</dt>
          <dd>{subscription.cycleNumber === 0 ? "Período de teste" : `Ciclo ${subscription.cycleNumber}`}</dd>

          {subscription.trialEndsAt && subscription.status === "TRIAL" && (
            <>
              <dt style={{ color: "var(--paysi-text-muted)" }}>Teste termina em:</dt>
              <dd>{formatDate(subscription.trialEndsAt)}</dd>
            </>
          )}

          <dt style={{ color: "var(--paysi-text-muted)" }}>Próxima cobrança:</dt>
          <dd>{subscription.status === "CANCELED" ? "—" : formatDate(subscription.nextChargeAt)}</dd>

          <dt style={{ color: "var(--paysi-text-muted)" }}>Cancelamento:</dt>
          <dd>
            {subscription.status === "CANCELED"
              ? `Encerrada em ${formatDate(subscription.canceledAt)}`
              : subscription.cancelPending
                ? `Agendado — a assinatura continua ativa até ${formatDate(subscription.nextChargeAt)}, sem renovar depois`
                : "Nenhum cancelamento agendado"}
          </dd>

          <dt style={{ color: "var(--paysi-text-muted)" }}>Criada em:</dt>
          <dd>{formatDate(subscription.createdAt)}</dd>
        </dl>

        {canCancel && (
          <Botao variant="danger" onClick={() => setCancelDialogOpen(true)}>
            Cancelar assinatura
          </Botao>
        )}
      </Cartao>

      <section aria-label="Histórico de cobranças">
        <h2>Histórico de cobranças</h2>
        {charges.length === 0 ? (
          <EmptyState
            title="Nenhuma cobrança ainda"
            description="As cobranças desta assinatura aparecerão aqui conforme os ciclos forem fechando."
          />
        ) : (
          <Tabela
            caption="Cobranças da assinatura por ciclo"
            headers={["Ciclo", "Valor", "Status", "Tentativa", "Retentativa", "Pago em", "Criada em"]}
            rows={chargeRows}
          />
        )}
      </section>

      <Dialog open={cancelDialogOpen} title="Cancelar assinatura" onClose={() => setCancelDialogOpen(false)}>
        <p>
          A assinatura continuará ativa até o fim do ciclo vigente
          {subscription.nextChargeAt ? ` (${formatDate(subscription.nextChargeAt)})` : ""}. Depois disso ela não
          renova mais. Nenhuma cobrança já feita é afetada.
        </p>
        <Botao variant="danger" disabled={canceling} onClick={() => void handleCancel()}>
          {canceling ? "Cancelando..." : "Confirmar cancelamento"}
        </Botao>
      </Dialog>
    </div>
  );
}
