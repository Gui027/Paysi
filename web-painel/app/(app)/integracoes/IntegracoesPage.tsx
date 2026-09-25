"use client";

import { useCallback, useEffect, useState } from "react";
import { Botao, Campo, Cartao, Checkbox, Dialog, EmptyState, Etiqueta, Skeleton, Tabela, Toast } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/api";
import {
  createWebhookEndpoint,
  deliveryStatusLabel,
  listWebhookDeliveries,
  listWebhookEndpoints,
  resendWebhookDelivery,
  rotateWebhookSecret,
  SUGGESTED_WEBHOOK_EVENTS,
  WEBHOOK_EVENT_DESCRIPTIONS,
  updateWebhookEndpoint,
  WebhookDelivery,
  WebhookEndpoint,
} from "../../../lib/integracoes";

type Tab = "webhooks" | "historico";

function formatDate(iso: string | null) {
  if (!iso) return "—";
  try {
    return new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(iso));
  } catch {
    return iso;
  }
}

function maskUrl(url: string): string {
  try {
    const parsed = new URL(url);
    return `${parsed.protocol}//${parsed.host}${parsed.pathname.length > 1 ? "/…" : ""}`;
  } catch {
    return url;
  }
}

type EditorState = { mode: "create" } | { mode: "edit"; endpoint: WebhookEndpoint };

export function IntegracoesPage() {
  const [tab, setTab] = useState<Tab>("webhooks");

  const [endpoints, setEndpoints] = useState<WebhookEndpoint[]>([]);
  const [loadingEndpoints, setLoadingEndpoints] = useState(true);
  const [endpointsError, setEndpointsError] = useState<string | null>(null);

  const [deliveries, setDeliveries] = useState<WebhookDelivery[]>([]);
  const [loadingDeliveries, setLoadingDeliveries] = useState(true);
  const [deliveriesError, setDeliveriesError] = useState<string | null>(null);
  const [resendingId, setResendingId] = useState<string | null>(null);

  const [editor, setEditor] = useState<EditorState | null>(null);
  const [formUrl, setFormUrl] = useState("");
  const [formEvents, setFormEvents] = useState<string[]>([]);
  const [formEnabled, setFormEnabled] = useState(true);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const [revealedSecret, setRevealedSecret] = useState<{ endpointUrl: string; secret: string } | null>(null);
  const [secretCopied, setSecretCopied] = useState(false);
  const [rotatingId, setRotatingId] = useState<string | null>(null);

  const loadEndpoints = useCallback(async () => {
    setLoadingEndpoints(true);
    setEndpointsError(null);
    try {
      setEndpoints(await listWebhookEndpoints());
    } catch {
      setEndpointsError("Não foi possível carregar os webhooks.");
    } finally {
      setLoadingEndpoints(false);
    }
  }, []);

  const loadDeliveries = useCallback(async () => {
    setLoadingDeliveries(true);
    setDeliveriesError(null);
    try {
      setDeliveries(await listWebhookDeliveries());
    } catch {
      setDeliveriesError("Não foi possível carregar o histórico de entregas.");
    } finally {
      setLoadingDeliveries(false);
    }
  }, []);

  useEffect(() => {
    void loadEndpoints();
    void loadDeliveries();
  }, [loadEndpoints, loadDeliveries]);

  const openCreate = () => {
    setEditor({ mode: "create" });
    setFormUrl("");
    setFormEvents([]);
    setFormEnabled(true);
    setFormError(null);
  };

  const openEdit = (endpoint: WebhookEndpoint) => {
    setEditor({ mode: "edit", endpoint });
    setFormUrl(endpoint.url);
    setFormEvents(endpoint.events);
    setFormEnabled(endpoint.enabled);
    setFormError(null);
  };

  const toggleEvent = (event: string) => {
    setFormEvents((prev) => (prev.includes(event) ? prev.filter((e) => e !== event) : [...prev, event]));
  };

  const submitForm = async () => {
    if (formEvents.length === 0) {
      setFormError("Selecione ao menos um evento.");
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      if (editor?.mode === "create") {
        const created = await createWebhookEndpoint(formUrl.trim(), formEvents, formEnabled);
        setRevealedSecret({ endpointUrl: created.endpoint.url, secret: created.secret });
      } else if (editor?.mode === "edit") {
        await updateWebhookEndpoint(editor.endpoint.id, formUrl.trim(), formEvents, formEnabled);
      }
      setEditor(null);
      await loadEndpoints();
    } catch (err) {
      if (err instanceof ApiRequestError && err.problem.code === "WEBHOOK_URL_INVALID") {
        setFormError("Essa URL não é aceita (precisa ser HTTPS pública).");
      } else if (err instanceof ApiRequestError && err.problem.code === "WEBHOOK_EVENTS_INVALID") {
        setFormError("Selecione de 1 a 32 eventos.");
      } else {
        setFormError("Não foi possível salvar o webhook. Tente novamente.");
      }
    } finally {
      setSaving(false);
    }
  };

  const doRotate = async (endpoint: WebhookEndpoint) => {
    setRotatingId(endpoint.id);
    try {
      const rotated = await rotateWebhookSecret(endpoint.id);
      setRevealedSecret({ endpointUrl: endpoint.url, secret: rotated.secret });
      await loadEndpoints();
    } finally {
      setRotatingId(null);
    }
  };

  const copySecret = async () => {
    if (!revealedSecret) return;
    await navigator.clipboard.writeText(revealedSecret.secret);
    setSecretCopied(true);
    window.setTimeout(() => setSecretCopied(false), 1800);
  };

  const closeSecretDialog = () => {
    setRevealedSecret(null);
    setSecretCopied(false);
  };

  const doResend = async (eventId: string) => {
    setResendingId(eventId);
    try {
      await resendWebhookDelivery(eventId);
      await loadDeliveries();
    } finally {
      setResendingId(null);
    }
  };

  const endpointRows = endpoints.map((endpoint) => [
    <span key="url">{maskUrl(endpoint.url)}</span>,
    <span key="events">{endpoint.events.join(", ")}</span>,
    <Etiqueta key="status" tone={endpoint.enabled ? "success" : "neutral"}>
      {endpoint.enabled ? "Ativo" : "Desativado"}
    </Etiqueta>,
    <span key="rotated">{formatDate(endpoint.secretRotatedAt)}</span>,
    <span key="actions" style={{ display: "flex", gap: "0.5rem" }}>
      <Botao variant="secondary" onClick={() => openEdit(endpoint)}>
        Editar
      </Botao>
      <Botao variant="secondary" disabled={rotatingId === endpoint.id} onClick={() => void doRotate(endpoint)}>
        {rotatingId === endpoint.id ? "Rotacionando..." : "Rotacionar segredo"}
      </Botao>
    </span>,
  ]);

  const deliveryRows = deliveries.map((delivery) => [
    <span key="event">{delivery.eventId.slice(0, 8)}…</span>,
    <span key="attempt">{delivery.attempt}ª tentativa</span>,
    <Etiqueta
      key="status"
      tone={
        delivery.httpStatus && delivery.httpStatus >= 200 && delivery.httpStatus < 300
          ? "success"
          : delivery.nextRetryAt
            ? "warning"
            : "danger"
      }
    >
      {deliveryStatusLabel(delivery)}
    </Etiqueta>,
    <span key="http">{delivery.httpStatus ?? "—"}</span>,
    <span key="error">{delivery.error ?? "—"}</span>,
    <span key="date">{formatDate(delivery.createdAt)}</span>,
    <Botao
      key="resend"
      variant="secondary"
      disabled={resendingId === delivery.eventId}
      onClick={() => void doResend(delivery.eventId)}
    >
      {resendingId === delivery.eventId ? "Reenviando..." : "Reenviar"}
    </Botao>,
  ]);

  return (
    <div className="integracoes-page">
      <header className="content-header">
        <div>
          <span className="paysi-rotulo">Conta</span>
          <h1>Integrações</h1>
          <p>Configure webhooks e acompanhe o histórico de entregas.</p>
        </div>
      </header>

      <div
        role="tablist"
        aria-label="Seções"
        className="mode-switch"
        style={{ marginBottom: "1.5rem", width: "fit-content" }}
      >
        <button role="tab" aria-selected={tab === "webhooks"} aria-controls="panel-webhooks" id="tab-webhooks" onClick={() => setTab("webhooks")}>
          Webhooks
        </button>
        <button role="tab" aria-selected={tab === "historico"} aria-controls="panel-historico" id="tab-historico" onClick={() => setTab("historico")}>
          Histórico de entregas
        </button>
      </div>

      {tab === "webhooks" && (
        <section id="panel-webhooks" role="tabpanel" aria-labelledby="tab-webhooks">
          {endpointsError && (
            <Toast tone="danger">
              {endpointsError}{" "}
              <button className="toast-action" onClick={() => void loadEndpoints()}>
                Tentar novamente
              </button>
            </Toast>
          )}
          <div style={{ marginBottom: "1rem" }}>
            <Botao variant="primary" onClick={openCreate}>
              Novo webhook
            </Botao>
          </div>
          {loadingEndpoints ? (
            <Skeleton label="Carregando webhooks" />
          ) : endpoints.length === 0 ? (
            <EmptyState title="Nenhum webhook cadastrado" description="Cadastre um endpoint para receber eventos da sua conta." />
          ) : (
            <Tabela
              caption="Webhooks cadastrados"
              headers={["URL", "Eventos", "Status", "Segredo rotacionado em", "Ações"]}
              rows={endpointRows}
            />
          )}
        </section>
      )}

      {tab === "historico" && (
        <section id="panel-historico" role="tabpanel" aria-labelledby="tab-historico">
          {deliveriesError && (
            <Toast tone="danger">
              {deliveriesError}{" "}
              <button className="toast-action" onClick={() => void loadDeliveries()}>
                Tentar novamente
              </button>
            </Toast>
          )}
          {loadingDeliveries ? (
            <Skeleton label="Carregando histórico de entregas" />
          ) : deliveries.length === 0 ? (
            <EmptyState title="Nenhuma entrega ainda" description="O histórico de envios de webhook aparece aqui." />
          ) : (
            <Tabela
              caption="Histórico de entregas de webhook"
              headers={["Evento", "Tentativa", "Status", "HTTP", "Erro", "Data", "Ação"]}
              rows={deliveryRows}
            />
          )}
        </section>
      )}

      <Dialog
        open={editor !== null}
        title={editor?.mode === "edit" ? "Editar webhook" : "Novo webhook"}
        onClose={() => setEditor(null)}
      >
        <Campo label="URL de destino" value={formUrl} onChange={(e) => setFormUrl(e.target.value)} hint="Precisa aceitar HTTPS." />
        <fieldset>
          <legend>Eventos</legend>
          {SUGGESTED_WEBHOOK_EVENTS.map((event) => (
            <Checkbox key={event} label={`${event} — ${WEBHOOK_EVENT_DESCRIPTIONS[event]}`} checked={formEvents.includes(event)} onChange={() => toggleEvent(event)} />
          ))}
        </fieldset>
        <Checkbox label="Ativo" checked={formEnabled} onChange={(e) => setFormEnabled(e.target.checked)} />
        {formError && <Toast tone="danger">{formError}</Toast>}
        <Botao variant="primary" disabled={saving} onClick={() => void submitForm()}>
          {saving ? "Salvando..." : "Salvar"}
        </Botao>
      </Dialog>

      <Dialog open={revealedSecret !== null} title="Segredo do webhook" onClose={closeSecretDialog}>
        <p>
          Copie o segredo agora — ele não será mostrado de novo depois de fechar esta janela. Use-o para validar a
          assinatura das requisições recebidas em {revealedSecret ? maskUrl(revealedSecret.endpointUrl) : ""}.
        </p>
        <code style={{ display: "block", wordBreak: "break-all", padding: "0.75rem", background: "var(--paysi-surface-muted, #f3f3f3)" }}>
          {revealedSecret?.secret}
        </code>
        <Botao variant="secondary" onClick={() => void copySecret()}>
          {secretCopied ? "Copiado!" : "Copiar segredo"}
        </Botao>
      </Dialog>
    </div>
  );
}
