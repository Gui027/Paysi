-- Controles BaaS 5.1.2/5.1.3: trilhas administrativas e alertas são append-only.
-- O papel da aplicação já não possuía UPDATE/DELETE em admin_audit_log; o
-- gatilho também impede mutação acidental pelo papel de migração/DBA enquanto
-- estiver habilitado, deixando qualquer manutenção excepcional explícita.
CREATE OR REPLACE FUNCTION paysi_reject_audit_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'registro de auditoria imutavel: % em % nao permitido', TG_OP, TG_TABLE_NAME;
END;
$$;

DROP TRIGGER IF EXISTS trg_admin_audit_log_immutable ON admin_audit_log;
CREATE TRIGGER trg_admin_audit_log_immutable
BEFORE UPDATE OR DELETE ON admin_audit_log
FOR EACH ROW EXECUTE FUNCTION paysi_reject_audit_mutation();

DROP TRIGGER IF EXISTS trg_ops_alerts_immutable ON ops_alerts;
CREATE TRIGGER trg_ops_alerts_immutable
BEFORE UPDATE OR DELETE ON ops_alerts
FOR EACH ROW EXECUTE FUNCTION paysi_reject_audit_mutation();

REVOKE UPDATE, DELETE ON admin_audit_log FROM paysi_app;
REVOKE UPDATE, DELETE ON ops_alerts FROM paysi_app;
