"use client";

import Link from "next/link";
import { useState } from "react";
import { MfaConfirmation } from "../../../../components/MfaConfirmation";
import { Botao, Campo, Cartao, Checkbox, Select, Toast } from "../../../../components/ui";
import { BankAccount, BankAccountErrors, BankAccountInput, bankFieldErrors, createBankAccount, maskTaxId, onlyDigits, payoutError, validateBankAccount } from "../../../../lib/payout";

const initial: BankAccountInput = { holderType: "PF", holderTaxId: "", holderName: "", bankCode: "", branch: "", accountNumber: "", digit: "", accountType: "CHECKING", pixKeyType: "CPF", pixKey: "" };

export function BankAccountPage() {
  const [values, setValues] = useState(initial);
  const [errors, setErrors] = useState<BankAccountErrors>({});
  const [ownership, setOwnership] = useState(false);
  const [mfaOpen, setMfaOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [saved, setSaved] = useState<BankAccount | null>(null);
  const [error, setError] = useState<string | null>(null);

  function change<K extends keyof BankAccountInput>(field: K, value: BankAccountInput[K]) {
    setValues(current => ({ ...current, [field]: value }));
    setErrors(current => ({ ...current, [field]: undefined }));
  }

  function begin() {
    const validation = validateBankAccount(values);
    if (!ownership) validation.holderTaxId = validation.holderTaxId ?? "Confirme que a conta pertence ao titular cadastrado.";
    setErrors(validation);
    setError(null);
    if (Object.keys(validation).length === 0) setMfaOpen(true);
  }

  async function save(challengeId: string) {
    if (submitting) return;
    setSubmitting(true);
    try {
      const result = await createBankAccount(values, challengeId);
      sessionStorage.setItem("paysi:bank-account", JSON.stringify(result));
      setSaved(result);
      setMfaOpen(false);
    } catch (requestError) {
      setErrors(bankFieldErrors(requestError));
      setError(payoutError(requestError));
      throw requestError;
    } finally {
      setSubmitting(false);
    }
  }

  return <>
    <header className="content-header"><div><span className="paysi-rotulo">Financeiro</span><h1>Conta bancária</h1><p>Cadastre um destino Pix verificado do mesmo titular da sua conta Paysi.</p></div><Link className="ui-button ui-button-secondary" href="/saldo">Voltar ao saldo</Link></header>
    {error && <Toast tone="danger">{error}</Toast>}
    {saved ? <Cartao className="bank-success"><span className="paysi-rotulo">Destino verificado</span><h2>Banco {saved.bankCode} · agência {saved.branch}</h2><p>Conta final <strong>{saved.numberLast4}</strong>. Os dados completos não são exibidos novamente por segurança.</p><Link className="ui-button ui-button-primary" href="/saldo/sacar">Solicitar saque</Link></Cartao> :
      <form className="ui-card payout-form" onSubmit={event => { event.preventDefault(); begin(); }} noValidate>
        <section><h2>Titular</h2><div className="payout-form-grid"><Select label="Tipo de titular" value={values.holderType} onChange={event => { const holderType = event.target.value as BankAccountInput["holderType"]; change("holderType", holderType); change("holderTaxId", maskTaxId(values.holderTaxId, holderType)); }}><option value="PF">Pessoa física</option><option value="PJ">Pessoa jurídica</option></Select><Campo label={values.holderType === "PF" ? "CPF" : "CNPJ"} value={values.holderTaxId} inputMode="numeric" autoComplete="off" error={errors.holderTaxId} onChange={event => change("holderTaxId", maskTaxId(event.target.value, values.holderType))} /><Campo label="Nome completo ou razão social" value={values.holderName} autoComplete="name" error={errors.holderName} onChange={event => change("holderName", event.target.value)} /></div></section>
        <section><h2>Dados bancários</h2><div className="payout-form-grid"><Campo label="Código do banco" value={values.bankCode} inputMode="numeric" maxLength={3} error={errors.bankCode} onChange={event => change("bankCode", onlyDigits(event.target.value, 3))} /><Campo label="Agência" value={values.branch} inputMode="numeric" maxLength={8} error={errors.branch} onChange={event => change("branch", onlyDigits(event.target.value, 8))} /><Campo label="Conta" value={values.accountNumber} inputMode="numeric" maxLength={20} error={errors.accountNumber} onChange={event => change("accountNumber", onlyDigits(event.target.value, 20))} /><Campo label="Dígito" value={values.digit} inputMode="numeric" maxLength={2} error={errors.digit} onChange={event => change("digit", onlyDigits(event.target.value, 2))} /><Select label="Tipo de conta" value={values.accountType} onChange={event => change("accountType", event.target.value as BankAccountInput["accountType"])}><option value="CHECKING">Conta corrente</option><option value="SAVINGS">Conta poupança</option><option value="PAYMENT">Conta de pagamento</option></Select></div></section>
        <section><h2>Chave Pix</h2><div className="payout-form-grid"><Select label="Tipo de chave" value={values.pixKeyType} onChange={event => change("pixKeyType", event.target.value as BankAccountInput["pixKeyType"])}><option value="CPF">CPF</option><option value="CNPJ">CNPJ</option><option value="EMAIL">E-mail</option><option value="PHONE">Telefone</option><option value="EVP">Chave aleatória</option></Select><Campo label="Chave Pix" value={values.pixKey} autoComplete="off" error={errors.pixKey} onChange={event => change("pixKey", event.target.value)} /></div></section>
        <Checkbox label="Confirmo que esta conta bancária pertence ao mesmo titular cadastrado no Paysi." checked={ownership} onChange={event => setOwnership(event.target.checked)} />
        <div className="ui-actions"><Botao type="submit" disabled={submitting || mfaOpen}>{submitting ? "Salvando…" : "Verificar e cadastrar"}</Botao></div>
      </form>}
    <MfaConfirmation open={mfaOpen} operation="BANK_ACCOUNT_CHANGE" onCancel={() => setMfaOpen(false)} onVerified={save} />
  </>;
}
