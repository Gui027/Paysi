#!/usr/bin/env bash
# Cenário de carga do checkout (BE-15.1). Documentado em docs/load-test-scenarios.md.
#
# Este ambiente não tem acesso de rede para instalar k6/Gatling/JMeter, então o
# cenário é expresso com o que já está disponível em qualquer máquina com bash
# e curl: N requisições concorrentes via `curl` em background, medindo
# taxa de sucesso e duração. Não substitui k6 em CI/staging — é o runbook
# mínimo executável hoje, documentado como tal (ver docs/load-test-scenarios.md,
# seção "O que foi executado vs. documentado").
#
# Uso:
#   BASE_URL=http://localhost:8080 CONCURRENCY=20 REQUESTS=200 ./checkout-load-test.sh
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SLUG="${SLUG:-curso-de-teste}"
CONCURRENCY="${CONCURRENCY:-20}"
REQUESTS="${REQUESTS:-200}"
OUT_DIR="$(mktemp -d)"

echo "Alvo: $BASE_URL/v1/checkout/$SLUG/orders"
echo "Concorrência: $CONCURRENCY | Total de requisições: $REQUESTS"
echo "Saída bruta em: $OUT_DIR"

request() {
  local i="$1"
  local key="load-test-$RANDOM-$i-$(date +%s%N)"
  local body='{"buyer":{"name":"Carga Teste","email":"carga+'"$i"'@example.com","personType":"PF","taxId":"52998224725"},"method":"PIX","installments":1,"termsHash":"hash-de-carga"}'
  local start end status
  start=$(date +%s%3N)
  status=$(curl -s -o "$OUT_DIR/resp-$i.json" -w "%{http_code}" \
    -X POST "$BASE_URL/v1/checkout/$SLUG/orders" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: $key" \
    -d "$body" || echo "000")
  end=$(date +%s%3N)
  echo "$i $status $((end - start))" >> "$OUT_DIR/results.txt"
}

for ((i = 1; i <= REQUESTS; i++)); do
  request "$i" &
  if (( i % CONCURRENCY == 0 )); then wait; fi
done
wait

echo "=== Resultado ==="
total=$(wc -l < "$OUT_DIR/results.txt")
ok=$(awk '$2 ~ /^20[01]$/' "$OUT_DIR/results.txt" | wc -l)
p95=$(awk '{print $3}' "$OUT_DIR/results.txt" | sort -n | awk -v n="$total" 'BEGIN{i=0} {a[i++]=$1} END{print a[int(n*0.95)]}')
echo "Total: $total | 2xx: $ok | p95 (ms): ${p95:-n/a}"
echo "Detalhe em $OUT_DIR/results.txt"
