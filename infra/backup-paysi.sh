#!/usr/bin/env bash
set -Eeuo pipefail

umask 077

readonly PAYSI_ROOT=/opt/paysi
readonly BACKUP_DIR="$PAYSI_ROOT/backups"
readonly RETENTION_DAYS="${PAYSI_BACKUP_RETENTION_DAYS:-14}"
readonly TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
readonly DB_FINAL="$BACKUP_DIR/paysi-db-$TIMESTAMP.dump"
readonly ASSETS_FINAL="$BACKUP_DIR/paysi-assets-$TIMESTAMP.tar.gz"
readonly DB_TMP="$DB_FINAL.tmp"
readonly ASSETS_TMP="$ASSETS_FINAL.tmp"

# systemd expõe credenciais criptografadas como arquivos. Convertemos apenas
# em memória para clientes S3 que não aceitam a convenção *_FILE.
if [[ -n "${AWS_ACCESS_KEY_ID_FILE:-}" ]]; then
  export AWS_ACCESS_KEY_ID="$(<"$AWS_ACCESS_KEY_ID_FILE")"
fi
if [[ -n "${AWS_SECRET_ACCESS_KEY_FILE:-}" ]]; then
  export AWS_SECRET_ACCESS_KEY="$(<"$AWS_SECRET_ACCESS_KEY_FILE")"
fi

mkdir -p "$BACKUP_DIR"
exec 9>"$BACKUP_DIR/.backup.lock"
flock -n 9 || { echo "Outro backup do Paysi já está em execução"; exit 0; }

cleanup() {
  rm -f "$DB_TMP" "$ASSETS_TMP"
}
trap cleanup EXIT

cd "$PAYSI_ROOT"
if [[ -z "${PAYSI_IMAGE_TAG:-}" ]]; then
  PAYSI_IMAGE_TAG="$(cat current-tag 2>/dev/null || docker inspect --format='{{.Config.Image}}' paysi-backend-1 | sed 's/.*://')"
  export PAYSI_IMAGE_TAG
fi

docker compose --env-file .env -f compose.yml exec -T db \
  pg_dump --username=paysi --dbname=paysi --format=custom --compress=6 > "$DB_TMP"
test -s "$DB_TMP"

# A listagem integral falha se o dump estiver truncado ou não for um arquivo válido.
docker compose --env-file .env -f compose.yml exec -T db \
  pg_restore --list < "$DB_TMP" > /dev/null

docker compose --env-file .env -f compose.yml exec -T backend \
  tar -C /var/lib/paysi -czf - assets > "$ASSETS_TMP"
test -s "$ASSETS_TMP"

mv "$DB_TMP" "$DB_FINAL"
mv "$ASSETS_TMP" "$ASSETS_FINAL"
sha256sum "$DB_FINAL" "$ASSETS_FINAL" > "$BACKUP_DIR/paysi-$TIMESTAMP.sha256"

# Cópia externa cifrada. O restic cifra conteúdo e metadados antes do envio.
# RESTIC_REPOSITORY pode apontar para S3/S3-compatible em região/conta distinta;
# senhas e credenciais são fornecidas ao serviço por credenciais do systemd,
# nunca por este arquivo ou pelo repositório.
if [[ -n "${RESTIC_REPOSITORY:-}" ]]; then
  command -v restic >/dev/null || { echo "restic não instalado" >&2; exit 1; }
  restic backup "$DB_FINAL" "$ASSETS_FINAL" "$BACKUP_DIR/paysi-$TIMESTAMP.sha256" \
    --tag paysi-production --tag daily
  restic forget --keep-daily 14 --keep-weekly 8 --keep-monthly 12 --prune
elif [[ "${PAYSI_BACKUP_REQUIRE_OFFSITE:-false}" == "true" ]]; then
  echo "Backup externo obrigatório, mas RESTIC_REPOSITORY não foi configurado" >&2
  exit 1
fi

find "$BACKUP_DIR" -type f \
  \( -name 'paysi-db-*.dump' -o -name 'paysi-assets-*.tar.gz' -o -name 'paysi-*.sha256' \) \
  -mtime "+$RETENTION_DAYS" -delete

echo "Backup do Paysi concluído: $TIMESTAMP"
