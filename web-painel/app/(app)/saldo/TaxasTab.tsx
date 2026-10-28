"use client";

import { useEffect, useState } from "react";
import { Skeleton, Toast } from "../../../components/ui";
import { feeMethodLabel, Fees, getFees, planLabel } from "../../../lib/financeiro";
import { formatCommissionBps } from "../../../lib/afiliados";
import { formatarCentavos } from "../../../lib/moeda";

function Secao({ titulo, texto, children }: { titulo: string; texto?: React.ReactNode; children: React.ReactNode }) {
  return <section className="pe-section"><div className="pe-section-intro"><h2>{titulo}</h2>{texto && <p>{texto}</p>}</div><div className="pe-card">{children}</div></section>;
}

/** Aba Taxas e Prazos: a comissão do plano por forma de pagamento, o prazo de recebimento e a reserva de segurança. */
export function TaxasTab() {
  const [fees, setFees] = useState<Fees | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    getFees().then(result => { if (active) setFees(result); }).catch(() => { if (active) setError("Não foi possível carregar as taxas. Tente novamente."); });
    return () => { active = false; };
  }, []);

  if (error) return <Toast tone="danger">{error}</Toast>;
  if (!fees) return <Skeleton label="Carregando taxas e prazos" />;

  return <>
    <Secao titulo="Vendas para o Brasil" texto={<>{planLabel[fees.plan]}. As taxas valem para as vendas aprovadas.</>}>
      <div className="pe-field"><span>Comissão da plataforma (produtor)</span>
        <ul className="tx-list">{fees.methods.map(method => <li key={method.method}><strong>{feeMethodLabel[method.method]}:</strong> {formatCommissionBps(method.feeBps)} + {formatarCentavos(method.fixedCents)} por venda aprovada</li>)}</ul>
        <small className="pe-hint">Remuneração da Paysi pelo uso do software, descontada de cada venda aprovada por divisão do pagamento (split). A Paysi não cobra tarifa de saque, antecipação ou abertura de conta; os serviços financeiros são prestados pelo Asaas.</small></div>
      <div className="pe-field"><span>Prazo de recebimento</span>
        <p className="tx-line">Suas vendas ficam disponíveis para saque <strong>{fees.payoutDelayDays} dias</strong> após a aprovação, para todas as formas de pagamento.</p>
        <small className="pe-hint">Cada oferta pode ter o seu próprio prazo, definido ao criar a oferta.</small></div>
      <div className="pe-field"><span>Reserva de segurança para cobrir reembolsos e chargebacks</span>
        <p className="tx-line"><strong>{formatCommissionBps(fees.reserveBps)}</strong> de cada venda fica reservado por <strong>{fees.reserveDays} dias</strong> e depois é liberado para você.</p></div>
    </Secao>
  </>;
}
