#!/bin/sh
set -eu

psql \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  --set=ON_ERROR_STOP=1 \
  --set=app_password="$PAYSI_APP_DB_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE paysi_app LOGIN PASSWORD %L', :'app_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'paysi_app') \gexec

SELECT format('ALTER ROLE paysi_app WITH LOGIN PASSWORD %L', :'app_password') \gexec
SQL

