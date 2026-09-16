"use client";

import { useCallback, useEffect, useState } from "react";
import { Botao, Campo, Cartao, Dialog, EmptyState, Etiqueta, Skeleton, Tabela, Toast } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/api";
import { formatarCentavos } from "../../../lib/moeda";
import {
  CommercialPlan,
  getPlan,
  getPlanHistory,
  planLabel,
  planStatusLabel,
  PlanStatus,
  PlanView,
  PlanChangeRecord,
  requestPlanChange,
} from "../../../lib/plano";

const statusTone: Record<PlanStatus, "neutral" | "success" | "warning" | "danger"> = {
  ACTIVE: "success",
  PAST_DUE: "warning",
  DOWNGRADED: "danger",
};

function formatDate(iso: string | null) {
  if (!iso) return "—";
  try {
    return new Intl.DateTimeFormat("pt-BR", { dateStyle: "long" }).format(new Date(iso));
  } catch {
    return iso;
  }
}

export function PlanoPage() {
  const [plan, setPlan] = useState<PlanView | null>(null);
  const [history, setHistory] = useState<PlanChangeRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [targetPlan, setTargetPlan] = useState<CommercialPlan | null>(null);
  const [cardToken, setCardToken] = useState("");
  const [changeError, setChangeError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [feedback, setFeedback] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [planView, historyList] = await Promise.all([getPlan(), getPlanHistory()]);
      setPlan(planView);
      setHistory(historyList);
    } catch {
      setError("Não foi possível carregar o plano comercial.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const openChangeDialog = (next: CommercialPlan) => {
    setTargetPlan(next);
    setCardToken("");
    setChangeError(null);
  };

  const confirmChange = async () => {
    if (!targetPlan) return;
    setSubmitting(true);
    setChangeError(null);
    try {
      const updated = await requestPlanChange(targetPlan, cardToken.trim() || undefined);
      setPlan(updated);
      setTargetPlan(null);
      setFeedback(`Troca para ${planLabel[targetPlan]} agendada para ${formatDate(updated.pendingEffectiveAt)}.`);
      const historyList = await getPlanHistory();
      setHistory(historyList);
    } catch (err) {
      if (err instanceof ApiRequestError && err.problem.code === "CARD_REQUIRED_FOR_ESCALA") {
        setChangeError("Escala exige um cartão cadastrado como alternativa ao saldo disponível.");
      } else {
        setChangeError("Não foi possível confirmar a troca de plano. Tente novamente.");
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return <Skeleton label="Carregando plano comercial" />;
  if (error || !plan) {
    return (
      <EmptyState
        title="Não foi possível carregar o plano"
        description={error ?? "Tente novamente em instantes."}
        action={
          <Botao variant="secondary" onClick={() => void load()}>
            Tentar novamente
          </Botao>
        }
      />
    );
  }

  const otherPlan: CommercialPlan = plan.currentPlan === "TRANSACIONAL" ? "ESCALA" : "TRANSACIONAL";

  const historyRows = history.map((h) => [
    <span key="from">{h.fromPlan ? planLabel[h.fromPlan as CommercialPlan] ?? h.fromPlan : "—"}</span>,
    <span key="to">{planLabel[h.toPlan as CommercialPlan] ?? h.toPlan}</span>,
    <span key="table">{h.priceTable}</span>,
    <span key="date">{formatDate(h.createdAt)}</span>,
  ]);

  return (
    <div className="plano-page">
      <header className="content-header">
        <div>
          <span className="paysi-rotulo">Conta</span>
          <h1>Plano comercial</h1>
          <p>Compare os planos, acompanhe a mensalidade e a próxima cobrança.</p>
        </div>
      </header>

      {feedback && <Toast tone="success">{feedback}</Toast>}

      <Cartao aria-label="Plano vigente">
        <dl style={{ display: "grid", gridTemplateColumns: "auto 1fr", gap: "0.5rem 1rem", margin: "1rem 0 0" }}>
          <dt style={{ color: "var(--paysi-text-muted)" }}>Plano atual:</dt>
          <dd>
            <strong>{planLabel[plan.currentPlan]}</strong> — {formatarCentavos(plan.monthlyFee)}/mês
          </dd>

          <dt style={{ color: "var(--paysi-text-muted)" }}>Status:</dt>
          <dd>
            <Etiqueta tone={statusTone[plan.status]}>{planStatusLabel[plan.status]}</Etiqueta>
            {plan.status === "PAST_DUE" && (
              <span> — se o atraso passar de 10 dias, o plano é rebaixado automaticamente para Transacional.</span>
            )}
          </dd>

          <dt style={{ color: "var(--paysi-text-muted)" }}>Próxima cobrança:</dt>
          <dd>{formatDate(plan.nextBilling)}</dd>

          {plan.pendingPlan && (
            <>
              <dt style={{ color: "var(--paysi-text-muted)" }}>Mudança agendada:</dt>
              <dd>
                Vai virar <strong>{planLabel[plan.pendingPlan]}</strong> ({formatarCentavos(plan.pendingMonthlyFee ?? 0)}
                /mês) em {formatDate(plan.pendingEffectiveAt)}. A cobrança do ciclo atual não muda.
              </dd>
            </>
          )}
        </dl>
      </Cartao>

      <section aria-label="Comparar planos">
        <h2>Planos disponíveis</h2>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))", gap: "1rem" }}>
          {(["TRANSACIONAL", "ESCALA"] as const).map((option) => (
            <Cartao key={option} aria-label={planLabel[option]}>
              <h3>{planLabel[option]}</h3>
              <p>
                <strong>{formatarCentavos(plan.priceTable[option])}</strong>/mês
              </p>
              {option === plan.currentPlan ? (
                <Etiqueta tone="success">Plano atual</Etiqueta>
              ) : plan.pendingPlan === option ? (
                <Etiqueta tone="warning">Já agendado</Etiqueta>
              ) : (
                <Botao variant="secondary" onClick={() => openChangeDialog(option)}>
                  Mudar para {planLabel[option]}
                </Botao>
              )}
            </Cartao>
          ))}
        </div>
      </section>

      <section aria-label="Histórico de mudanças de plano">
        <h2>Histórico</h2>
        {history.length === 0 ? (
          <EmptyState title="Nenhuma mudança ainda" description="O histórico de trocas de plano aparece aqui." />
        ) : (
          <Tabela caption="Histórico de mudanças de plano" headers={["De", "Para", "Tabela", "Data"]} rows={historyRows} />
        )}
      </section>

      <Dialog
        open={targetPlan !== null}
        title={targetPlan ? `Mudar para ${planLabel[targetPlan]}` : "Mudar de plano"}
        onClose={() => setTargetPlan(null)}
      >
        <p>
          A troca vale a partir do fim do ciclo atual, em {formatDate(plan.nextBilling)}. A cobrança já feita neste
          ciclo não é afetada.
        </p>
        {targetPlan === "ESCALA" && (
          <Campo
            label="Token do cartão (alternativa ao saldo disponível)"
            value={cardToken}
            onChange={(e) => setCardToken(e.target.value)}
            hint="A mensalidade do Escala sai primeiro do seu saldo disponível; o cartão só é usado se o saldo não cobrir."
          />
        )}
        {changeError && <Toast tone="danger">{changeError}</Toast>}
        <Botao variant="primary" disabled={submitting} onClick={() => void confirmChange()}>
          {submitting ? "Confirmando..." : "Confirmar troca"}
        </Botao>
      </Dialog>
    </div>
  );
}
