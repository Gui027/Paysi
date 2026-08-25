-- Exclusivo do ambiente local. Em produção, a credencial vem do cofre.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'paysi_app') THEN
    CREATE ROLE paysi_app LOGIN PASSWORD 'paysi_app';
  ELSE
    ALTER ROLE paysi_app WITH LOGIN PASSWORD 'paysi_app';
  END IF;
END $$;

