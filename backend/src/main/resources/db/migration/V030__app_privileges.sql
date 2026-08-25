-- Complemento operacional do ADR-15: o papel de aplicação recebe somente os
-- privilégios necessários. O DDL v3.0 criava e revogava o papel, mas não lhe
-- concedia acesso às tabelas criadas pelo papel proprietário.
GRANT USAGE ON SCHEMA public TO paysi_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO paysi_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO paysi_app;

-- Razão e auditoria são append-only também no nível de privilégio.
REVOKE UPDATE, DELETE ON ledger_entries, ledger_transactions, admin_audit_log FROM paysi_app;

-- Mantém o mesmo comportamento para tabelas e sequências de migrações futuras.
ALTER DEFAULT PRIVILEGES FOR ROLE paysi IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO paysi_app;
ALTER DEFAULT PRIVILEGES FOR ROLE paysi IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO paysi_app;

