"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { ApiRequestError, fieldErrors } from "../../../lib/api";
import { FiscalProfileInput, getFiscalProfile, saveFiscalProfile, TaxRegime, taxRegimeLabel } from "../../../lib/fiscal";
import { Botao, Campo, Etiqueta, Select, Skeleton, Toast } from "../../../components/ui";

const blankInput: FiscalProfileInput = {
  municipalityCode: "",
  municipalRegistration: "",
  serviceItem: "",
  taxBps: 200,
  taxRegime: "SIMPLES",
  credentialRef: "",
};

export function PerfilFiscal() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const nextParam = searchParams.get("next");

  const [values, setValues] = useState<FiscalProfileInput>(blankInput);
  const [validated, setValidated] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState<Partial<Record<keyof FiscalProfileInput, string>>>({});
  const [generalError, setGeneralError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    getFiscalProfile().then(profile => {
      if (!active) return;
      if (profile) {
        setValues({ ...profile, credentialRef: "" });
        setValidated(profile.validated);
      }
    }).catch(() => { if (active) setLoadError(true); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (validated && nextParam) router.replace(nextParam);
  }, [validated, nextParam, router]);

  function change<K extends keyof FiscalProfileInput>(field: K, value: FiscalProfileInput[K]) {
    setValues(current => ({ ...current, [field]: value }));
    setErrors(current => ({ ...current, [field]: undefined }));
    setGeneralError(null);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setGeneralError(null);
    try {
      const saved = await saveFiscalProfile(values);
      setValidated(saved.validated);
    } catch (error) {
      if (error instanceof ApiRequestError) {
        setErrors(fieldErrors(error.problem) as Partial<Record<keyof FiscalProfileInput, string>>);
        setGeneralError(error.message);
      } else {
        setGeneralError("Não foi possível salvar o perfil fiscal. Tente novamente.");
      }
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <Skeleton label="Carregando perfil fiscal" />;
  if (loadError) return <Toast tone="danger">Não foi possível carregar o perfil fiscal.</Toast>;

  return <>
    <header className="content-header">
      <div>
        <h1>Perfil fiscal</h1>
        <p>Dados usados para emitir nota fiscal de serviço nas suas vendas.</p>
      </div>
      <Etiqueta tone={validated ? "success" : "neutral"}>{validated ? "Validado" : "Não configurado"}</Etiqueta>
    </header>

    {validated && <Toast tone="success">Perfil fiscal validado. {nextParam ? "Redirecionando…" : ""}</Toast>}

    <form className="ui-card" onSubmit={event => void submit(event)} noValidate>
      {generalError && <Toast tone="danger">{generalError}</Toast>}
      <div className="offer-form-grid">
        <Campo label="Código do município (IBGE)" value={values.municipalityCode} error={errors.municipalityCode}
          onChange={event => change("municipalityCode", event.target.value)} required />
        <Campo label="Inscrição municipal" value={values.municipalRegistration} error={errors.municipalRegistration}
          onChange={event => change("municipalRegistration", event.target.value)} />
        <Campo label="Item de serviço (LC 116)" value={values.serviceItem} error={errors.serviceItem}
          onChange={event => change("serviceItem", event.target.value)} required placeholder="1.05" />
        <Campo label="Alíquota de ISS (bps, 200 = 2%)" type="number" min={0} max={500} value={values.taxBps}
          error={errors.taxBps} onChange={event => change("taxBps", Number(event.target.value))} required />
        <Select label="Regime tributário" value={values.taxRegime} error={errors.taxRegime}
          onChange={event => change("taxRegime", event.target.value as TaxRegime)}>
          {(Object.keys(taxRegimeLabel) as TaxRegime[]).map(regime => <option key={regime} value={regime}>{taxRegimeLabel[regime]}</option>)}
        </Select>
        <Campo label="Credencial do emissor" value={values.credentialRef} error={errors.credentialRef}
          onChange={event => change("credentialRef", event.target.value)} required
          hint="Fornecida pelo emissor de nota fiscal contratado." />
      </div>
      <div className="ui-actions">
        <Botao type="submit" disabled={saving}>{saving ? "Salvando…" : "Salvar e validar"}</Botao>
      </div>
    </form>
  </>;
}
