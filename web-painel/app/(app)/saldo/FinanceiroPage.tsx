"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Skeleton, Toast } from "../../../components/ui";
import { formatarCentavos } from "../../../lib/moeda";
import { FinanceOverview, getFinance } from "../../../lib/financeiro";
import { CnpjDialog } from "./CnpjDialog";
import { DadosBancariosTab } from "./DadosBancariosTab";
import { ExtratoTab } from "./ExtratoTab";
import { IdentidadeTab } from "./IdentidadeTab";
import { MfaSetupDialog } from "./MfaSetupDialog";
import { SaqueDialog } from "./SaqueDialog";
import { SaquesTab } from "./SaquesTab";
import { TaxasTab } from "./TaxasTab";

type Aba = "saques" | "extrato" | "dados" | "taxas" | "identidade";
const abas: readonly [Aba, string][] = [["saques", "Saques"], ["extrato", "Extrato"], ["dados", "Dados bancários"], ["taxas", "Taxas e Prazos"], ["identidade", "Identidade"]];

/** Financeiro: saldo e saques, extrato, dados bancários, taxas e prazos, e a verificação de identidade, tudo em um lugar. */
export function FinanceiroPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const aba = (abas.find(([id]) => id === searchParams.get("aba"))?.[0] ?? "saques") as Aba;
  const [overview, setOverview] = useState<FinanceOverview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [saqueOpen, setSaqueOpen] = useState(searchParams.get("sacar") === "1");
  const [cnpjOpen, setCnpjOpen] = useState(false);
  const [mfaSetupOpen, setMfaSetupOpen] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  const load = useCallback(async () => {
    setError(null);
    try {
      setOverview(await getFinance());
    } catch {
      setError("Não foi possível carregar o financeiro. Tente novamente.");
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  function selectAba(next: Aba) {
    const query = new URLSearchParams(searchParams.toString());
    query.delete("sacar");
    if (next === "saques") query.delete("aba"); else query.set("aba", next);
    const text = query.toString();
    router.replace(text ? `/saldo?${text}` : "/saldo", { scroll: false });
  }

  function afterPayout() {
    setSaqueOpen(false);
    setNotice("Solicitação de saque efetuada");
    setReloadKey(key => key + 1);
    selectAba("saques");
    void load();
  }

  function afterCompany() {
    setCnpjOpen(false);
    setNotice("Conta alterada para CNPJ. Agora verifique a identidade da empresa.");
    selectAba("identidade");
    void load();
  }

  if (error) return <Toast tone="danger">{error} <button className="toast-action" onClick={() => void load()}>Tentar novamente</button></Toast>;
  if (!overview) return <Skeleton label="Carregando financeiro" />;

  return <div className="vd fin">
    <header className="prod-head">
      <h1>Financeiro</h1>
      <label className="fin-currency"><span className="sr-only">Moeda</span><select aria-label="Moeda" value="BRL" disabled><option value="BRL">BRL</option></select></label>
    </header>

    {notice && <Toast>{notice} <button className="toast-action" onClick={() => setNotice(null)}>Fechar</button></Toast>}

    <div className="fin-cards">
      <section className="fin-card fin-card-ok" aria-label="Saldo disponível"><span>Saldo disponível</span><strong>{formatarCentavos(overview.balance.availableCents)}</strong></section>
      <section className="fin-card fin-card-wait" aria-label="Saldo pendente"><span>Saldo pendente <span className="fin-help" title="Vendas ainda em garantia ou aguardando a data de liberação. Ficam disponíveis para saque quando o prazo termina." aria-label="Vendas ainda em garantia ou aguardando a data de liberação">?</span></span><strong>{formatarCentavos(overview.balance.pendingCents)}</strong></section>
    </div>

    <div className="fin-actions">
      {overview.holder.personType === "PF" ? <button type="button" className="pe-linkbtn" onClick={() => setCnpjOpen(true)}>Alterar minha conta para CNPJ</button> : <span />}
      <button type="button" className="ui-button ui-button-primary" onClick={() => setSaqueOpen(true)}>Efetuar saque</button>
    </div>

    <div className="pe-tabs" role="tablist" aria-label="Seções do financeiro">
      {abas.map(([id, label]) => <button key={id} type="button" role="tab" id={`fin-aba-${id}`} aria-selected={aba === id} aria-controls="fin-painel" onClick={() => selectAba(id)}>{label}</button>)}
    </div>

    <div id="fin-painel" role="tabpanel" aria-labelledby={`fin-aba-${aba}`}>
      {aba === "saques" && <SaquesTab reloadKey={reloadKey} />}
      {aba === "extrato" && <ExtratoTab />}
      {aba === "dados" && <DadosBancariosTab overview={overview} onChanged={() => void load()} onNeedMfa={() => setMfaSetupOpen(true)} onOpenCnpj={() => setCnpjOpen(true)} />}
      {aba === "taxas" && <TaxasTab />}
      {aba === "identidade" && <IdentidadeTab onStatus={status => setOverview(current => current && current.kycStatus !== status ? { ...current, kycStatus: status } : current)} />}
    </div>

    {/* Janelas só existem enquanto estão abertas: fechadas, não deixam campos escondidos na página. */}
    {saqueOpen && <SaqueDialog open overview={overview} onClose={() => setSaqueOpen(false)} onDone={afterPayout} onOpenDados={() => { setSaqueOpen(false); selectAba("dados"); }} />}
    {cnpjOpen && <CnpjDialog open overview={overview} onClose={() => setCnpjOpen(false)} onNeedMfa={() => { setCnpjOpen(false); setMfaSetupOpen(true); }} onDone={afterCompany} />}
    {mfaSetupOpen && <MfaSetupDialog open onCancel={() => setMfaSetupOpen(false)} onEnabled={() => { setMfaSetupOpen(false); setNotice("Verificação em duas etapas ativada."); void load(); }} />}
  </div>;
}
