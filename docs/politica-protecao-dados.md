# Política de Proteção de Dados e Criptografia

**Responsável:** Tecnologia e Segurança da Informação
**Aprovação:** representante legal da PAYSI INOVA SIMPLES
**Revisão:** anual ou após mudança relevante/incidente
**Versão:** 1.0 — 06/11/2026

## Escopo e princípios

Esta política abrange aplicação, banco de dados, arquivos, backups, logs,
integrações e fornecedores que tratem dados pessoais ou financeiros em nome da
Paysi. Aplicam-se minimização, necessidade, menor privilégio, segregação de
ambientes, retenção limitada e descarte seguro.

Dados de cartão não devem ser persistidos nem registrados. Tokens de pagamento
e credenciais de subcontas são tratados como segredos. Logs não podem conter
senha, número completo de cartão, CVV, chave de API ou documento completo.

## Dados em trânsito

- Todo acesso público usa HTTPS com TLS 1.2 ou superior e HSTS.
- A API do Asaas é acessada somente por endpoint HTTPS correspondente ao
  ambiente da credencial.
- Banco, Redis e RabbitMQ não publicam portas no host; o tráfego ocorre apenas
  na rede privada da stack.
- SMTP externo deve exigir STARTTLS ou TLS direto; falha de negociação não pode
  rebaixar silenciosamente para texto claro.

## Dados em repouso

- Segredos MFA, credenciais de subcontas e campos financeiros classificados
  como restritos usam AES-GCM no nível da aplicação.
- Senhas são armazenadas apenas como hash Argon2id; chaves públicas da API são
  armazenadas apenas como hash verificável.
- Backups externos devem ser criptografados antes de sair do servidor.
- O volume que contém PostgreSQL e arquivos da aplicação deve usar criptografia
  de disco fornecida e evidenciada pelo provedor de infraestrutura.

**Condição de conformidade:** enquanto a criptografia do volume de produção e
do destino externo de backup não estiver comprovada, não se declara
criptografia integral dos dados em repouso.

## Gestão de chaves

Chaves de API e chaves criptográficas de produção ficam em gerenciador de
segredos, nunca no repositório ou em arquivos `.env`. Acesso é restrito ao
responsável técnico e ao processo que efetivamente utiliza a chave. Rotação
ocorre anualmente, na troca de responsável e imediatamente em caso de suspeita
de exposição. O procedimento detalhado está em `docs/gestao-segredos.md`.

## Retenção e descarte

Os prazos seguem finalidade, contrato e obrigação legal. Logs de segurança e
acesso são mantidos por no mínimo seis meses; registros de incidente, por cinco
anos; trilhas administrativas e financeiras, pelo prazo legal/contratual
aplicável. Ao final, os dados são anonimizados ou eliminados de forma segura.

## Evidência e revisão

A revisão anual registra sistemas, categorias de dados, controles verificados,
gaps, responsável e prazo. Mudança relevante de arquitetura ou fornecedor
dispara revisão extraordinária.
