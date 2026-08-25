-- [FIX-D01] V011 e V023 faziam REVOKE ... FROM paysi_app sobre um papel que
-- nenhuma migração criava: a migração aborta em ambiente limpo (CI, máquina
-- nova, flyway:clean flyway:migrate). Criar o papel é pré-requisito, não
-- configuração de infraestrutura.
--
-- E vale o alerta que faltava: o REVOKE só protege se a aplicação NÃO for a
-- dona das tabelas. O dono ignora GRANT/REVOKE. O .env do documento 5 conecta
-- como `paysi`, que é o dono — logo o REVOKE não protegeria nada. Quem migra
-- é `paysi`; quem atende requisição é `paysi_app`.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'paysi_app') THEN
    CREATE ROLE paysi_app LOGIN;
  END IF;
END $$;
