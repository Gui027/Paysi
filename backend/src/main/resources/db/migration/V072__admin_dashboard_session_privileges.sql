-- A sessão administrativa é gravada pela conexão da aplicação (paysi_app).
-- O grant explícito também corrige bancos criados antes de os privilégios
-- padrão do papel proprietário estarem ativos para esta tabela.
GRANT SELECT, INSERT, UPDATE, DELETE ON admin_dashboard_sessions TO paysi_app;
