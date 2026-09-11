"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { MfaConfirmation } from "../../../../components/MfaConfirmation";
import { Botao, Campo, Cartao, EmptyState, Skeleton, Toast } from "../../../../components/ui";
import { getBalance, BalanceView } from "../../../../lib/dashboard";
import { formatarCentavos } from "../../../../lib/moeda";
import { BankAccount, parsePayoutAmount, payoutError, PayoutResult, requestPayout } from "../../../../lib/payout";

export function SaquePage() {
  const [balance, setBalance] = useState<BalanceView | null>(null);
  const [bank, setBank] = useState<BankAccount | null>(null);
  const [amount, setAmount] = useState("");
  const [amountError, setAmountError] = useState<string | undefined>();
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [mfaOpen, setMfaOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [receipt, setReceipt] = useState<PayoutResult | null>(null);
  const idempotencyKey = useRef<string | null>(null);

  useEffect(() => {
    try { const stored = sessionStorage.getItem("paysi:bank-account"); if (stored) setBank(JSON.parse(stored) as BankAccount); } catch { sessionStorage.removeItem("paysi:bank-account"); }
    getBalance().then(setBalance).catch(() => setError("Não foi possível consultar o saldo disponível.")).finally(() => setLoading(false));
  }, []);

  function begin() {
    if (!balance || !bank || submitting || mfaOpen) return;
    const cents = parsePayoutAmount(amount);
    if (cents === null || cents < 200) return setAmountError("O saque mínimo é de R$ 2,00.");
    if (balance.debt !== 0) return setAmountError("Quite ou compense o saldo devedor antes de solicitar um saque.");
    if (cents > balance.available) return setAmountError("O valor informado é maior que o saldo disponível.");
    setAmountError(undefined);
    setError(null);
    idempotencyKey.current ??= globalThis.crypto?.randomUUID?.() ?? `payout-${Date.now()}`;
    setMfaOpen(true);
  }

  async function withdraw(challengeId: string) {
    if (!bank || submitting || !idempotencyKey.current) return;
    const cents = parsePayoutAmount(amount);
    if (cents === null) return;
    setSubmitting(true);
    try {
      const result = await requestPayout(cents, bank.id, challengeId, idempotencyKey.current);
      setReceipt(result);
      setMfaOpen(false);
      idempotencyKey.current = null;
    } catch (requestError) {
      setError(payoutError(requestError));
      throw requestError;
    } finally {
      setSubmitting(false);
    }
  }

  return <>
    <header className="content-header"><div><span className="paysi-rotulo">Financeiro</span><h1>Solicitar saque</h1><p>Transfira seu saldo disponível para uma conta verificada.</p></div><Link className="ui-button ui-button-secondary" href="/saldo">Voltar ao saldo</Link></header>
    {error && <Toast tone="danger">{error}</Toast>}
    {loading ? <Skeleton label="Consultando saldo disponível" /> : !bank ? <EmptyState title="Cadastre uma conta bancária" description="Você precisa de um destino verificado antes de solicitar um saque." action={<Link className="ui-button ui-button-primary" href="/saldo/conta-bancaria">Cadastrar conta</Link>} /> : receipt ? <Cartao className="bank-success"><span className="paysi-rotulo">Saque solicitado</span><h2>Operação recebida</h2><dl className="detail-list"><div><dt>Identificador</dt><dd><code>{receipt.payoutId}</code></dd></div><div><dt>Status</dt><dd>{receipt.status}</dd></div></dl>{receipt.receiptUrl?.startsWith("https://") && <a className="ui-button ui-button-secondary" href={receipt.receiptUrl} target="_blank" rel="noreferrer">Abrir comprovante</a>}</Cartao> : <div className="payout-layout">
      <Cartao className="payout-balance"><span>Saldo disponível</span><strong className="paysi-valor">{formatarCentavos(balance?.available ?? 0)}</strong><small>Saque mínimo: {formatarCentavos(200)}</small>{balance?.debt !== 0 && <p className="ui-error" role="alert">Saque bloqueado: existe saldo devedor.</p>}</Cartao>
      <form className="ui-card payout-form" onSubmit={event => { event.preventDefault(); begin(); }} noValidate><h2>Dados do saque</h2><p>Destino: banco {bank.bankCode}, agência {bank.branch}, conta final {bank.numberLast4}.</p><Campo label="Valor do saque" value={amount} inputMode="decimal" placeholder="0,00" error={amountError} hint="Informe o valor em reais." onChange={event => { setAmount(event.target.value.replace(/[^\d.,]/g, "")); setAmountError(undefined); }} /><div className="ui-actions"><Botao type="submit" disabled={submitting || mfaOpen || balance?.debt !== 0}>{submitting ? "Solicitando…" : "Continuar com MFA"}</Botao></div></form>
    </div>}
    <MfaConfirmation open={mfaOpen} operation="PAYOUT" onCancel={() => setMfaOpen(false)} onVerified={withdraw} />
  </>;
}
