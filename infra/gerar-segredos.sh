#!/usr/bin/env bash
# Gera segredos novos para as variáveis do stack (Portainer). Imprime NO TERMINAL, não grava nada:
# copie direto para o Portainer (Stacks > paysi > Environment variables) e não cole em chats.
# Uso: bash infra/gerar-segredos.sh
set -euo pipefail

forte() { openssl rand -base64 48 | tr -d '/+=\n' | cut -c1-40; }

echo "PAYSI_DB_PASSWORD=$(forte)"
echo "PAYSI_APP_DB_PASSWORD=$(forte)"
echo "PAYSI_RABBIT_PASSWORD=$(forte)"
echo "KYC_WEBHOOK_SECRET=$(forte)"
echo "PAYMENT_WEBHOOK_SECRET=$(forte)"
# 32 bytes em base64 (AES-256). Trocar esta chave invalida qualquer dado já cifrado com a antiga
# (segredos de MFA): só troque com o banco zerado, como no go-live.
echo "MFA_ENCRYPTION_KEY_BASE64=$(openssl rand -base64 32)"
echo
echo "# ASAAS_API_KEY e ASAAS_WEBHOOK_TOKEN NÃO são gerados aqui:"
echo "#  - ASAAS_API_KEY: chave de produção criada no painel da Asaas (lembre do \$\$ no início no Portainer)."
echo "#  - ASAAS_WEBHOOK_TOKEN: use o botão 'Gerar token' ao criar o webhook de produção na Asaas."
