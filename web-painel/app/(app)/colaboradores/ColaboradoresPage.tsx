"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { EmptyState, Skeleton, Toast } from "../../../components/ui";
import { Janela } from "../../../components/Janela";
import { Paginacao } from "../../../components/Paginacao";
import { ApiRequestError } from "../../../lib/api";
import {
  addCollaborator, areaLabels, areaOrder, Collaborator, CollaboratorArea, CollaboratorsPage, hasFullAccess, isValidEmail,
  listCollaborators, permissionsPayload, removeCollaborator, resendInvite, statusLabel, updateCollaborator,
} from "../../../lib/colaboradores";

const dateTime = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" });

function message(error: unknown, fallback: string) {
  return error instanceof ApiRequestError ? error.message : fallback;
}

/** Janela de convite e de edição: e-mail (só no convite) e permissões, com "Acesso total" ou área por área. */
function ColaboradorDialog({ editing, onClose, onSaved }: { editing: Collaborator | null; onClose: () => void; onSaved: (text: string) => void }) {
  const [email, setEmail] = useState(editing?.email ?? "");
  const [full, setFull] = useState(editing ? hasFullAccess(editing) : true);
  const [areas, setAreas] = useState<CollaboratorArea[]>(editing && !hasFullAccess(editing) ? editing.permissions as CollaboratorArea[] : []);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function save() {
    if (!editing && !isValidEmail(email)) { setError("Informe um e-mail válido."); return; }
    if (!full && areas.length === 0) { setError("Escolha ao menos uma permissão."); return; }
    setSaving(true);
    setError(null);
    try {
      const permissions = permissionsPayload(full, areas);
      if (editing) await updateCollaborator(editing.id, permissions);
      else await addCollaborator(email, permissions);
      onSaved(editing ? "Permissões atualizadas" : "Colaborador adicionado");
    } catch (saveError) {
      setError(message(saveError, "Não foi possível salvar o colaborador."));
    } finally {
      setSaving(false);
    }
  }

  return <Janela wide open title={editing ? "Editar colaborador" : "Adicionar colaborador"} onClose={() => !saving && onClose()}>
    {!editing && <label className="pe-field"><span>E-mail</span><input type="email" value={email} autoComplete="off" onChange={event => { setEmail(event.target.value); setError(null); }} /></label>}
    {editing && <p className="pe-hint">{editing.email}</p>}
    <fieldset className="pe-fieldset"><legend>Permissões</legend>
      <label className="mk-terms"><input type="checkbox" checked={full} onChange={event => setFull(event.target.checked)} /> Acesso total</label>
      {!full && <div className="vd-status-grid">{areaOrder.map(area => <label className="mk-terms" key={area}>
        <input type="checkbox" checked={areas.includes(area)} onChange={event => setAreas(current => event.target.checked ? [...current, area] : current.filter(item => item !== area))} /> {areaLabels[area]}</label>)}</div>}
    </fieldset>
    {error && <p className="pe-error" role="alert">{error}</p>}
    <div className="ui-actions">
      <button type="button" className="ui-button ui-button-primary col-wide" disabled={saving} onClick={() => void save()}>{saving ? "Salvando…" : editing ? "Salvar" : "Adicionar colaborador"}</button>
    </div>
  </Janela>;
}

export function ColaboradoresPage() {
  const [search, setSearch] = useState("");
  const [term, setTerm] = useState("");
  const [page, setPage] = useState(1);
  const [data, setData] = useState<CollaboratorsPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [dialog, setDialog] = useState<{ editing: Collaborator | null } | null>(null);
  const [menuId, setMenuId] = useState<string | null>(null);
  const [removing, setRemoving] = useState<Collaborator | null>(null);
  const requestId = useRef(0);

  const load = useCallback(async (q: string, current: number) => {
    const request = ++requestId.current;
    setLoading(true);
    setError(null);
    try {
      const result = await listCollaborators(q, current);
      if (request === requestId.current) setData(result);
    } catch (loadError) {
      if (request === requestId.current) setError(message(loadError, "Não foi possível carregar os colaboradores."));
    } finally {
      if (request === requestId.current) setLoading(false);
    }
  }, []);

  useEffect(() => { void load(term, page); }, [load, term, page]);
  useEffect(() => { const timer = setTimeout(() => { setTerm(search); setPage(1); }, 300); return () => clearTimeout(timer); }, [search]);

  async function resend(collaborator: Collaborator) {
    setMenuId(null);
    try {
      await resendInvite(collaborator.id);
      setNotice("Convite reenviado");
      void load(term, page);
    } catch (resendError) {
      setError(message(resendError, "Não foi possível reenviar o convite."));
    }
  }

  async function confirmRemove() {
    if (!removing) return;
    try {
      await removeCollaborator(removing.id);
      setRemoving(null);
      setNotice("Acesso removido");
      void load(term, page);
    } catch (removeError) {
      setRemoving(null);
      setError(message(removeError, "Não foi possível remover o acesso."));
    }
  }

  const rows = data?.items ?? [];

  return <div className="vd">
    <header className="prod-head"><h1>Colaboradores</h1></header>

    {error && <Toast tone="danger">{error} <button className="toast-action" onClick={() => void load(term, page)}>Tentar novamente</button></Toast>}
    {notice && <Toast>{notice} <button className="toast-action" onClick={() => setNotice(null)}>Fechar</button></Toast>}

    <section className="prod-panel">
      <div className="rel-toolbar">
        <label className="mk-search-field rel-search"><span className="sr-only">Buscar colaborador por e-mail</span>
          <input type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder="Buscar…" /></label>
        <button type="button" className="ui-button ui-button-primary" onClick={() => setDialog({ editing: null })}>Adicionar colaborador</button>
      </div>
      {loading && !data ? <Skeleton label="Carregando colaboradores" /> : rows.length === 0 ?
        <EmptyState title="Nenhum colaborador" description={term ? "Nenhum colaborador com esse e-mail." : "Convide pessoas para ajudar a cuidar da sua conta, com as permissões que você escolher."} /> :
        <div className="dash-table-wrap"><table className="prod-table vd-table" aria-busy={loading}>
          <thead><tr><th scope="col">E-mail</th><th scope="col">Status</th><th scope="col">Data do convite</th><th scope="col"><span className="sr-only">Ações</span></th></tr></thead>
          <tbody>{rows.map(row => <tr key={row.id}>
            <td className="prod-muted">{row.email}</td>
            <td><span className={`pe-pill ${row.status === "ACTIVE" ? "pe-pill-on" : "vd-pill-warn"}`}>{statusLabel[row.status]}</span></td>
            <td className="prod-muted">{dateTime.format(new Date(row.invitedAt))}</td>
            <td className="col-actions"><div className="vd-menu">
              <button type="button" className="vd-kebab" aria-label={`Ações para ${row.email}`} aria-haspopup="menu" aria-expanded={menuId === row.id} onClick={() => setMenuId(current => current === row.id ? null : row.id)}>⋮</button>
              {menuId === row.id && <div className="prod-menu-list vd-menu-list" role="menu">
                <button type="button" role="menuitem" className="col-item" onClick={() => { setMenuId(null); setDialog({ editing: row }); }}>Editar</button>
                {row.status === "PENDING" && <button type="button" role="menuitem" className="col-item" onClick={() => void resend(row)}>Reenviar convite</button>}
                <button type="button" role="menuitem" onClick={() => { setMenuId(null); setRemoving(row); }}>Remover acesso</button>
              </div>}
            </div></td>
          </tr>)}</tbody>
        </table></div>}
      {data && <Paginacao page={data.page} totalPages={data.totalPages} onChange={setPage} />}
    </section>
    <p className="col-info">Colaboradores acessam a sua conta apenas com as permissões que você definir.</p>

    {dialog && <ColaboradorDialog editing={dialog.editing} onClose={() => setDialog(null)} onSaved={text => { setDialog(null); setNotice(text); void load(term, page); }} />}
    {removing && <Janela open title="Remover acesso" onClose={() => setRemoving(null)}>
      <p>Remover o acesso de <strong>{removing.email}</strong>? Essa pessoa deixa de colaborar na sua conta.</p>
      <div className="ui-actions">
        <button type="button" className="ui-button ui-button-secondary" onClick={() => setRemoving(null)}>Cancelar</button>
        <button type="button" className="ui-button ui-button-danger" onClick={() => void confirmRemove()}>Remover acesso</button>
      </div>
    </Janela>}
  </div>;
}
