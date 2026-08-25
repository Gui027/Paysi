# Paysi — Documento 2: Arquitetura e Modelo de Dados

**Versão 3.0 · 21 de agosto de 2026 · interno · Substitui a versão 2.0 e incorpora as revisões v2.1 e v3.0**

---

> **Princípio que rege este documento.** Em sistema financeiro, o erro caro não é o que derruba a aplicação — é o que produz um número errado sem ninguém perceber. Toda decisão aqui prioriza **detectabilidade** sobre elegância. E, quando possível, prioriza **impossibilidade** sobre detectabilidade: o defeito que o banco recusa é melhor que o defeito que o relatório de amanhã encontra.

> **Onde mora a verdade sobre o esquema.** Este documento explica o modelo e as decisões. O DDL autoritativo é `paysi-esquema-v3.0.sql`, que foi aplicado num PostgreSQL 16.15 e exercitado por `paysi-testes-v3.0.sql` — 70 asserções, zero falhas. Quando este texto e o arquivo divergirem, **o arquivo está certo**, porque só ele foi executado. Os trechos abaixo são os que precisam de explicação, não o esquema inteiro.

---

## 1. Decisões de arquitetura

```
Checkout                Painel                    
Vite + React            Next.js · só visão        
       │                       │                  
       └───────────┬───────────┘                  
                   ▼                              
          API Spring Boot                          
       toda a regra de dinheiro                    
                   │                              
    ┌──────────────┼──────────────┐               
    ▼              ▼              ▼               
 Postgres        Redis          Fila              
 livro-razão   idempotência   webhooks            
                   │                              
       ┌───────────┴───────────┐                  
       ▼                       ▼                  
   Provedor              Parceiro fiscal          
   pagamento             NFS-e do vendedor        
```

Duas integrações externas, ambas atrás de porta do domínio (ADR-09, ADR-11).

**ADR-01 — Java com Spring Boot no backend.** O núcleo é um livro-razão com concorrência real: várias cobranças e saques podendo tocar o mesmo saldo simultaneamente. Java 21 com Spring Boot 3, por controle transacional maduro com nível de isolamento explícito, bloqueio pessimista e propagação previsível; tipagem forte reduzindo erro de unidade monetária; agendador nativo; base de contratação ampla no Brasil. *Consequência:* mais verbosidade. Aceito — em código financeiro, explícito é virtude.

**ADR-02 — JDBC explícito no livro-razão, JPA no restante.** JPA traz carregamento tardio, verificação de alterações e consultas geradas: conveniente em cadastro, perigoso em contabilidade. *Consequência:* mais SQL à mão no módulo financeiro, que é onde se quer enxergar o bloqueio e o isolamento na própria linha de código.

**ADR-03 — PostgreSQL 16 como banco único.** Isolamento serializável que funciona de fato, bloqueios consultivos, gatilhos de restrição deferidos, índices parciais e `bigint` nativo para centavos. *Consequência:* instância única é ponto de falha até haver réplica. Réplica de leitura entra na fase 2.

**ADR-04 — Checkout como aplicação separada do painel.** O painel tolera 3 segundos de carregamento; o checkout, não. *Consequência:* duplicação de alguns componentes, aceita em troca do orçamento de desempenho.

**ADR-05 — Next.js não executa regra de dinheiro.** Ações de servidor e rotas de API do Next são convenientes o bastante para que, sem disciplina, a regra acabe em dois lugares. Next é camada de visão. *Verificação:* revisão de código rejeita qualquer aritmética monetária em arquivo do frontend.

**ADR-06 — Dados de cartão nunca tocam a Paysi.** Campos de cartão são componente do provedor; o frontend recebe apenas um token. *Consequência:* escopo PCI DSS restrito a SAQ A. Perde-se controle visual sobre os campos e ganha-se a remoção de uma classe inteira de risco.

**ADR-07 — Padrão outbox para eventos de saída.** O evento é gravado na mesma transação que o fato; um processo separado lê a tabela e publica. *Consequência:* entrega ao menos uma vez, nunca exatamente uma vez. O consumidor precisa tratar repetição, e isso vai documentado.

**ADR-08 — Idempotência obrigatória em escrita.** Todo endpoint que movimenta dinheiro exige cabeçalho `Idempotency-Key`, gravado com `SET NX`. Rede instável e usuário clicando duas vezes são certeza, não hipótese.

**ADR-09 — Interface de provedor desde a primeira linha.** Toda comunicação passa por uma interface do domínio; a implementação concreta é adaptador substituível.

> **Limite do ADR-09, que precisa ser dito.** Duas coisas não são portáveis por adaptador:
>
> **Tokens de cartão.** O token de recorrência pertence ao provedor que o emitiu. Trocar de provedor com base de assinaturas ativa significa migração de tokens — que só acontece com cooperação dos dois e não é garantida. Quanto mais tarde a troca, mais cara.
>
> **Redundância simultânea.** A Resolução Conjunta 16/2025 restringe a entidade tomadora a um prestador por tipo de conta. O adaptador viabiliza a **troca**, não a operação em paralelo como contingência de disponibilidade.

**ADR-10 — Hospedagem em região brasileira.** Compradores e vendedores estão no Brasil; ida e volta transatlântica em cada requisição do checkout custa conversão. Também simplifica o tratamento de dados pessoais.

> **Restrição prática a verificar antes de contratar.** O documento 3 exige cópia de segurança em região geográfica distinta, dentro do território nacional. **Nem todo provedor de nuvem oferece duas regiões no Brasil** — em vários existe apenas uma, e a separação disponível é entre zonas de disponibilidade da mesma região. É decisão de infraestrutura com consequência de conformidade, e precisa ser resolvida antes de escolher a nuvem. As saídas: provedor com duas regiões brasileiras, separação por zona documentando a limitação, ou cópia fria em provedor secundário nacional.

**ADR-11 — Módulo fiscal atrás de porta, como o provedor.** A emissão de NFS-e envolve prefeitura, certificado e regra municipal — variabilidade que não pode vazar para o domínio. A emissão é assíncrona e nunca bloqueia a confirmação do pagamento (RF-113). *Consequência:* a nota pode sair minutos depois da venda, e o estado dela é uma máquina de estados própria.

**ADR-12 — O saldo devedor é um bucket do razão, não uma tabela à parte.** A alternativa seria uma tabela `debts` com valor mutável — que reintroduz exatamente o campo de saldo que o razão existe para proibir. Como bucket, o saldo devedor é somado a partir de lançamentos, é imutável, aparece no extrato com memória de cálculo e participa da verificação de integridade. *Consequência:* `DEBT` é o único bucket negativo por construção, e a verificação de sinal precisa excetuá-lo explicitamente.

**ADR-13 — Segmento é atributo do produto, não da conta.** Uma mesma conta pode vender SaaS e produto digital. Colocar o segmento na conta obrigaria contas separadas para quem faz os dois — reintroduzindo o problema que a regra de identidade única resolve. *Consequência:* o checkout decide os campos a partir da oferta carregada, não a partir do vendedor.

**ADR-14 — A regra vai para o banco sempre que couber no banco. *(novo v3.0)*** Item de lista de revisão é disciplina; `CHECK` e gatilho são física. Quando uma regra pode ser expressa como restrição, gatilho ou chave natural, ela vai para lá — mesmo custando latência. *Consequência:* algumas regras vivem em dois lugares (domínio e banco). Aceito: a duplicação de uma guarda é barata; a ausência dela custa dinheiro real. Sete itens da lista de revisão do documento 5 deixaram de ser itens de lista porque viraram restrição.

**ADR-15 — Dois papéis de banco: quem migra não é quem opera. *(novo v3.0)*** O dono das tabelas ignora `GRANT` e `REVOKE`. Com a aplicação conectando como dona, o `REVOKE UPDATE ON ledger_entries` nunca protegeu nada — restava só o gatilho. `paysi` migra e é dono; `paysi_app` atende requisição e não tem `UPDATE` nem `DELETE` no razão nem na auditoria.

---

## 2. Estrutura de módulos

```
com.paysi
├── core
│   ├── money          Money, Percentual em pontos-base
│   ├── id             Identificadores tipados
│   └── error          Exceções de domínio
├── identity           Conta, autenticação, verificação, papéis, segundo fator
├── catalog            Produto, oferta, plano, cupom, segmento
├── buyer              Comprador, dados fiscais, deduplicação
├── affiliate          Afiliação, vitrine, atribuição de clique
├── checkout           Sessão de checkout, coleta, aceite de termos
├── payment
│   ├── split          Motor de divisão, rateio e reembolso (sem dependências)
│   ├── charge         Cobrança, tentativa, régua, recebíveis
│   └── provider       Interface, adaptadores e inbox de eventos
├── ledger             Livro-razão, saldos, reserva, garantia, dívida (JDBC puro)
├── payout             Saque, conta bancária
├── risk               Limites, faixas, antifraude, contestação, evidências
├── fiscal             Perfil fiscal, emissão de NFS-e, cancelamento
├── billing            Plano comercial e cobrança da mensalidade
├── notification       Outbox, entrega, assinatura HMAC por endpoint
├── reconciliation     Conciliação diária contra o provedor
└── admin              Operação interna, auditoria, ajustes
```

> **Regra de dependência.** `ledger` e `payment.split` não dependem de nada além de `core`. Sem Spring, sem provedor, sem web. São os módulos que precisam de cobertura de teste próxima de 100% e que sobrevivem a qualquer troca de infraestrutura.

---

## 3. Modelo de dados

### 3.1 Convenções

| Convenção | Regra |
|---|---|
| Dinheiro | `bigint` em centavos. `numeric` e `float` proibidos |
| Percentual | `integer` em pontos-base (599 = 5,99%) |
| Identificador | `uuid` gerado pela aplicação, exceto o livro-razão |
| Horário | `timestamptz`, sempre em UTC |
| Estado | Coluna `text` com `CHECK`, nunca inteiro sem significado |
| Exclusão | Lógica por `archived_at`. Registro financeiro nunca é excluído |
| Unicidade reutilizável | Índice parcial `WHERE status <> 'CLOSED'`, para que encerramento libere o identificador |

**Ordem das migrações — V000 a V029.** `V000__roles.sql` cria `paysi_app` de forma idempotente; sem ele, `V011` e `V023` abortam em ambiente limpo. `V005__buyers` vem antes de `V008__orders`, porque o pedido referencia o comprador. `V010__ledger` antes de `V012__ledger_checkpoints`. `V029__system_accounts_seed` fecha a sequência, semeando as contas de sistema — sem as quais nenhum lançamento de venda fecha.

### 3.2 Identidade e conta

```sql
CREATE TABLE accounts (
  id                  uuid PRIMARY KEY,
  email               citext NOT NULL,          -- unicidade por índice parcial
  password_hash       text NOT NULL,
  full_name           text NOT NULL,
  person_type         text NOT NULL CHECK (person_type IN ('PF','PJ')),
  tax_id              text NOT NULL,
  kyc_status          text NOT NULL DEFAULT 'PENDING'
                        CHECK (kyc_status IN ('PENDING','SUBMITTED','APPROVED','REJECTED')),
  provider_account_id text UNIQUE,
  payout_delay        text NOT NULL DEFAULT 'D32'
                        CHECK (payout_delay IN ('D32','D15','D7','D2')),
  -- `plan` NÃO existe aqui: fonte única é platform_subscriptions
  risk_tier           int NOT NULL DEFAULT 0 CHECK (risk_tier BETWEEN 0 AND 3),
  status              text NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE','LIMITED','SUSPENDED','CLOSED')),
  created_at          timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_accounts_tax_id_open ON accounts (tax_id) WHERE status <> 'CLOSED';
CREATE UNIQUE INDEX uq_accounts_email_open  ON accounts (email)  WHERE status <> 'CLOSED';
```

> **Sem tabela de papéis.** Não existe `sellers` nem `affiliates`. Uma conta é vendedora quando tem produto e afiliada quando tem afiliação. Papel é consequência de dado, não coluna — foi o que evitou o problema de saldo duplicado.

> **Toda conta nasce com plano (D12).** Um gatilho `AFTER INSERT` cria a linha em `platform_subscriptions` com o plano Transacional. Eleger uma fonte única sem garantir que ela exista apenas troca o problema de lugar: sete de oito contas da massa de teste ficaram sem plano, e conta sem plano é cobrança sem tabela de preço.

Operador interno tem credencial de segundo fator **própria** (`admin_mfa_credentials`), não a de usuário. Tabelas separadas em vez de discriminador porque as populações têm ciclo de vida, política de recuperação e superfície de ataque diferentes.

### 3.3 Catálogo

A oferta guarda `charge_type` e `segment` desnormalizados de `products`, **preenchidos por gatilho** — a aplicação não os informa. Isso existe porque a restrição de ciclo precisa deles na própria tabela:

```sql
CONSTRAINT cycle_matches_charge_type CHECK (
  (charge_type = 'SUBSCRIPTION' AND cycle IS NOT NULL) OR
  (charge_type = 'ONE_TIME'     AND cycle IS NULL)
),
CONSTRAINT trial_card_rule CHECK (trial_requires_card OR segment = 'SAAS')
```

> **A versão 1.1 escrevia `CHECK ((cycle IS NOT NULL) OR (SELECT true))`, com o comentário "validado na aplicação".** O PostgreSQL rejeita subconsulta em restrição de verificação: `ERROR: cannot use subquery in check constraint`. A migração falharia na primeira execução. Desnormalizar duas colunas imutáveis é barato; deixar uma regra financeira "validada na aplicação" não é.

Três gatilhos sustentam a imutabilidade (RF-118):

1. `BEFORE INSERT ON offers` copia `charge_type` e `segment` de `products`. Como roda antes, os `CHECK` já enxergam o valor certo.
2. `BEFORE UPDATE ON offers` recusa alteração de `product_id`, `charge_type` e `segment`; recusa `cycle` e `guarantee_days` **depois que existe cobrança confirmada**.
3. `BEFORE UPDATE ON products` recusa mudança de `segment` e `charge_type` quando já existe oferta.

> **O gatilho 2 mudou na v3.0 (D03).** Ele testava `orders.status IN ('PAID','REFUNDED','PARTIALLY_REFUNDED','CHARGEBACK')`. Quando o estado de reembolso saiu do pedido, a condição deixou de funcionar silenciosamente — o gatilho continuava existindo e parava de proteger. O fato "houve venda paga" mora na cobrança confirmada.

Preço continua editável: `orders` guarda `paid_cents` do momento da venda, então o histórico não se altera.

**Cupom.** `redeemed_count` é campo mutável com teto, e o resgate usa `UPDATE` condicional:

```sql
UPDATE coupons SET redeemed_count = redeemed_count + 1
WHERE id = :id AND archived_at IS NULL
  AND (expires_at IS NULL OR expires_at > now())
  AND (max_redemptions IS NULL OR redeemed_count < max_redemptions);
-- 0 linhas afetadas => cupom esgotado, recusa
```

> **Por que isso basta, e por que a ordem importa.** Dois checkouts simultâneos que leem 99 de 100 e ambos passam é o mesmo defeito que o razão inteiro existe para evitar. O `UPDATE` condicional resolve porque **toma o bloqueio de linha do cupom**: a segunda transação espera a primeira confirmar. E é por isso que a verificação de limite por comprador vem **depois** do `UPDATE`, na mesma transação — nesse ponto as duas já estão serializadas. Invertida, a verificação por comprador voltaria a ter corrida. Há ainda `CHECK (redeemed_count <= max_redemptions)` como rede.

`BOLETO` só existe em oferta de segmento `SAAS`, por gatilho sobre `offer_payment_methods`.

### 3.4 Comprador

```sql
CREATE TABLE buyers (
  id             uuid PRIMARY KEY,
  email          citext NOT NULL,
  tax_id         text NOT NULL,
  person_type    text NOT NULL CHECK (person_type IN ('PF','PJ')),
  name           text NOT NULL,
  legal_name     text,                    -- razão social, PJ
  municipal_reg  text,
  address        jsonb,                   -- exigido quando há nota
  anonymized_at  timestamptz,             -- LGPD
  created_at     timestamptz NOT NULL DEFAULT now()
);
```

> **Por que o comprador virou entidade.** A versão anterior copiava nome, e-mail e documento em cada linha de `orders`. Três coisas ficavam difíceis ou impossíveis:
>
> **Detectar lavagem (AM-03).** "Mesmo comprador repetido com ticket alto" exige identidade de comprador. Sem ela, é varredura em texto livre.
>
> **Cumprir a LGPD.** Anonimizar um titular significava atualizar N linhas de pedido, sem garantia de ter achado todas.
>
> **Emitir nota.** Os dados de PJ não cabem em três colunas de `orders`, e reaproveitá-los na segunda compra é o mínimo de cortesia.
>
> `orders` mantém um retrato imutável dos dados usados naquela venda, para prova de contestação; `buyers` mantém o registro vivo.

### 3.5 Afiliação

Dois gatilhos que a versão 1.1 deixava "validados na aplicação": comissão imutável após aprovação (RF-046) e autoafiliação vedada (RF-049) — este último substituindo um `CONSTRAINT no_self_affiliation CHECK (true)`, que é a forma mais honesta de não verificar nada.

A atribuição é resolvida na criação do pedido: busca-se o clique mais recente e não expirado para aquele visitante, restrito ao produto. Último clique vence, janela de 60 dias.

### 3.6 Livro-razão

É o coração do sistema. Partidas dobradas, imutável, com saldo sempre calculado por soma.

```
GUARANTEE ──▶ PENDING ──▶ AVAILABLE  (sacável)
                       └▶ RESERVE ──▶ AVAILABLE
                          4% a 10%, sai em D+90

DEBT      saldo devedor · sempre negativo ou zero
SYSTEM    contas internas · sinal conforme o saldo normal declarado
```

> **Saída de `GUARANTEE` no prazo de garantia. Saída de `PENDING` no prazo de recebimento. Ambos contados da data do pagamento.**
>
> A regra `max(recebimento, garantia)` do RF-111 **não é implementada em lugar nenhum: ela emerge.** As duas datas partem do mesmo marco e cada processo só move o que já venceu. O dinheiro chega em `AVAILABLE` na data mais tardia por consequência da cadeia.
>
> ```
> release_at do lançamento em GUARANTEE = pagamento + guarantee_days
> release_at do lançamento em PENDING   = pagamento + prazo de recebimento
> ```
>
> O rótulo anterior do diagrama dizia "Saída de `GUARANTEE` em max(recebimento, garantia)", e o próprio comentário do SQL logo abaixo já tropeçava nisso. Implementar o `max` na saída da garantia faz `PENDING` virar bucket de duração zero e a cadeia de quatro estados perder sentido.

#### Estrutura

```sql
CREATE TABLE ledger_accounts (            -- contas de sistema, fora de accounts
  id             uuid PRIMARY KEY,
  code           text NOT NULL UNIQUE,
  name           text NOT NULL,
  normal_balance text NOT NULL CHECK (normal_balance IN ('DEBIT','CREDIT')),
  created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE ledger_transactions (
  id             uuid PRIMARY KEY,
  type           text NOT NULL CHECK (type IN
                   ('SALE','GUARANTEE_RELEASE','RELEASE','RESERVE_RELEASE',
                    'REFUND','CHARGEBACK','CHARGEBACK_REVERSAL',
                    'PAYOUT','PAYOUT_REVERSAL','PLATFORM_FEE',
                    'DEBT_WRITEOFF','ADJUSTMENT','ANTICIPATION')),
  reference_type text NOT NULL CHECK (reference_type IN
                   ('CHARGE','REFUND','DISPUTE','PAYOUT','RECEIVABLE',
                    'PLATFORM_SUB','VERIFICATION','DEBT_WRITEOFF','ADJUSTMENT')),
  reference_id   text NOT NULL,
  description    text NOT NULL,
  created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_ledger_tx_natural
  ON ledger_transactions (type, reference_type, reference_id);

CREATE TABLE ledger_entries (
  id             bigserial PRIMARY KEY,
  transaction_id uuid NOT NULL REFERENCES ledger_transactions(id),
  account_id     uuid NOT NULL,          -- accounts.id OU ledger_accounts.id
  bucket         text NOT NULL CHECK (bucket IN
                   ('GUARANTEE','PENDING','RESERVE','AVAILABLE','DEBT','SYSTEM')),
  direction      text NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),
  amount_cents   bigint NOT NULL CHECK (amount_cents > 0),
  origin         text NOT NULL CHECK (origin IN ('SALE','COMMISSION','FEE','DEBT','OTHER')),
  release_at     timestamptz,
  created_at     timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT release_only_on_credit  CHECK (release_at IS NULL OR direction = 'CREDIT'),
  CONSTRAINT release_never_on_system CHECK (release_at IS NULL OR bucket <> 'SYSTEM')
);
```

> **A chave natural é o que impede creditar duas vezes.** `(type, reference_type, reference_id)` é `UNIQUE`. A segunda notificação de `payment.confirmed` do provedor entrega uma segunda transação `SALE` que soma zero, passa em todas as verificações e credita o vendedor duas vezes — e só a conciliação pega, dias depois. Com a chave natural, o banco recusa.
>
> Ela também obrigou a trocar `reference text` por `reference_type` + `reference_id`, o que resolveu um problema que apareceria depois: `REFUND` ocorre várias vezes para a mesma cobrança (referência é o `refund_id`), `RELEASE` ocorre por parcela (referência é o `receivable_id`), `PLATFORM_FEE` de mensalidade referencia `{account_id}:{period_start}` — e é essa composição que impede cobrar duas vezes o mesmo mês numa retentativa do processo diário.

> **`release_at` só em crédito (D06).** Agendar a saída de um valor que já saiu do bucket não significa nada, e era aceito. Duas restrições fecham: nunca em débito, nunca em conta de sistema.

#### Imutabilidade

```sql
CREATE TRIGGER trg_ledger_no_update BEFORE UPDATE ON ledger_entries
  FOR EACH ROW EXECUTE FUNCTION ledger_is_append_only();
CREATE TRIGGER trg_ledger_no_delete BEFORE DELETE ON ledger_entries
  FOR EACH ROW EXECUTE FUNCTION ledger_is_append_only();
REVOKE UPDATE, DELETE ON ledger_entries FROM paysi_app;
```

O `REVOKE` só vale porque `paysi_app` não é dono das tabelas (ADR-15). O gatilho vale sempre, inclusive contra o dono — é ele que carrega a garantia.

Um gatilho adicional exige que `account_id` exista na tabela certa conforme o bucket: `SYSTEM` aponta para `ledger_accounts`, qualquer outro para `accounts`. Antes, `account_id` era um uuid livre com um comentário dizendo "conta de usuário ou conta de sistema" e ninguém verificando.

#### Agendamento de liberação

`ledger_entries` é append-only, logo não há onde carimbar "já liberado". Sem uma projeção mutável ao lado, o processo horário encontra as mesmas entradas vencidas e **move o mesmo dinheiro toda hora, para sempre**.

```sql
CREATE TABLE ledger_release_schedule (
  entry_id               bigint PRIMARY KEY REFERENCES ledger_entries(id),
  account_id             uuid NOT NULL,
  bucket                 text NOT NULL,
  amount_cents           bigint NOT NULL CHECK (amount_cents > 0),
  release_at             timestamptz NOT NULL,
  released_at            timestamptz,
  release_transaction_id uuid REFERENCES ledger_transactions(id)
);
CREATE INDEX ON ledger_release_schedule (release_at) WHERE released_at IS NULL;
```

> **A linha é criada pelo banco, não pela aplicação (D07).** Era item 20 da lista de revisão de código: "lançamento com `release_at` tem linha correspondente em `ledger_release_schedule`". Esquecer significa dinheiro que nunca sai da garantia, em silêncio, para sempre — e uma lista de revisão não é lugar para guardar isso. Um gatilho `AFTER INSERT` popula a partir do próprio lançamento, e a verificação nº 8 acusa agendamento vencido e não executado.
>
> O razão é o fato; o agendamento é estado operacional. Separar os dois é o que permite que o razão seja append-only sem tornar o agendador impossível. A tabela é derivada e reconstruível.

#### Contas de sistema

| Conta | Função | Saldo normal |
|---|---|---|
| `SYS_CLEARING` | Contrapartida do dinheiro que entra do provedor | DEBIT |
| `SYS_PLATFORM_REVENUE` | Receita da plataforma | CREDIT |
| `SYS_PROVIDER_FEE` | Custo do provedor, reconhecido como despesa | CREDIT |
| `SYS_ACQUIRER_FEE` | Tarifa retida pela adquirente | CREDIT |
| `SYS_REFUND_LOSS` | Perda absorvida em reembolso | DEBIT |
| `SYS_CHARGEBACK_LOSS` | Perda residual em contestação e baixa | DEBIT |

> **Convenção de sinal, que precisa estar escrita porque não é óbvia: `saldo = créditos − débitos`.** Contas de origem e de perda são devedoras e acumulam negativo; contas de destino e de receita são credoras. `SYS_CLEARING` é debitada em toda venda e é negativa por construção.
>
> Era isto que a verificação nº 2 não sabia. Ela acusava `SYS_CLEARING` como bucket negativo **todo dia, desde a primeira venda**. Em duas semanas ninguém olha mais o alerta — e é assim que a verificação de verdade passa batido.

#### Fluxo completo, conferido contra o banco

Venda de R$ 100,00 no cartão à vista, plano Transacional, afiliado 10%, garantia 7 dias, recebimento D+32, reserva 4%.

```
-- 1. SALE — na CONFIRMAÇÃO do pagamento (nunca na criação do pedido)
DEBIT  SYS_CLEARING            10000
CREDIT vendedor.GUARANTEE       8201  origin=SALE        release_at = pagamento + 7d
CREDIT afiliado.GUARANTEE       1000  origin=COMMISSION  release_at = pagamento + 7d
CREDIT SYS_PLATFORM_REVENUE      451  origin=FEE
CREDIT SYS_PROVIDER_FEE          348  origin=FEE
-----                                 débitos = créditos = 10000 ✓

-- 2. GUARANTEE_RELEASE — quando o release_at de GUARANTEE vence (D+7)
DEBIT  vendedor.GUARANTEE       8201
CREDIT vendedor.PENDING         8201  release_at = pagamento + 32d
DEBIT  afiliado.GUARANTEE       1000
CREDIT afiliado.PENDING         1000  release_at = pagamento + 32d
-----                                 soma = 0 ✓

-- 3. RELEASE — quando o release_at de PENDING vence (D+32)
DEBIT  vendedor.PENDING         8201
CREDIT vendedor.AVAILABLE       7873
CREDIT vendedor.RESERVE          328  release_at = pagamento + 90d
DEBIT  afiliado.PENDING         1000
CREDIT afiliado.AVAILABLE       1000
-----                                 soma = 0 ✓

-- 4. RESERVE_RELEASE — D+90
DEBIT  vendedor.RESERVE          328
CREDIT vendedor.AVAILABLE        328
-----                                 soma = 0 ✓
```

> **A reserva incide só sobre o vendedor.** A contestação é debitada dele; é dele o risco que a reserva cobre. A comissão do afiliado é estornada em reembolso ou contestação, mas contra o saldo que ele tiver — e, na falta dele, contra o `DEBT` do próprio afiliado. Reter reserva do afiliado seria cobrar duas vezes pelo mesmo risco.
>
> A reserva incide sobre o valor que **efetivamente entra em `PENDING`** — depois da antecipação e depois de eventual compensação de dívida, não sobre o valor bruto da venda.

**Reembolso total, dentro da garantia**

```
DEBIT  vendedor.GUARANTEE       8201
DEBIT  afiliado.GUARANTEE       1000
DEBIT  SYS_PLATFORM_REVENUE      451
DEBIT  SYS_REFUND_LOSS           348   -- taxa do provedor não volta
CREDIT SYS_CLEARING            10000
-----                                  soma = 0 ✓
```

> **Era aqui que a versão 1.1 quebrava.** Ela creditava a venda em `PENDING` e debitava o reembolso de `GUARANTEE` — um bucket que nenhum lançamento jamais alimentava. O resultado seria saldo negativo em garantia e inflado em pendente, para toda venda reembolsada. E a verificação diária não pegaria: a transação de reembolso soma zero corretamente. O erro só apareceria na conciliação, dias depois, como divergência sem causa óbvia.

**Reembolso parcial de R$ 20,00** — truncagem cumulativa (RF-105), verificada em 4.275.150 cenários:

```
DEBIT  vendedor.GUARANTEE       1641   -- 2000 − 200 − 90 − 69, o resto exato
DEBIT  afiliado.GUARANTEE        200   -- trunc(1000 × 2000 ÷ 10000)
DEBIT  SYS_PLATFORM_REVENUE       90   -- trunc(451 × 2000 ÷ 10000)
DEBIT  SYS_REFUND_LOSS            69   -- trunc(348 × 2000 ÷ 10000)
CREDIT SYS_CLEARING             2000
-----                                  soma = 0 ✓
```

**Contestação de R$ 100 com tarifa de R$ 30, reserva de R$ 3,28 e disponível de R$ 50,00**

```
DEBIT  vendedor.RESERVE          328
DEBIT  vendedor.AVAILABLE       5000
DEBIT  vendedor.DEBT            6672   -- o que não coube
DEBIT  afiliado.AVAILABLE       1000   -- devolve a comissão
CREDIT SYS_CLEARING            10000   -- devolvido ao comprador
CREDIT SYS_ACQUIRER_FEE         3000   -- retido pela adquirente
-----                                  soma = 0 ✓
```

> **A tarifa da adquirente saía do lugar errado (B7), e é o defeito mais instrutivo da revisão.** O lançamento anterior creditava os R$ 130,00 inteiros em `SYS_CLEARING`. Soma zero. Passa nas três verificações originais. E está errado: dos R$ 130,00, só R$ 100,00 voltam ao comprador pelo provedor. Os R$ 30,00 **ficam com a adquirente** — não retornam por lugar nenhum. O lançamento afirmava que o provedor devolveu um dinheiro que ele nunca devolveu.
>
> Consequência: `SYS_CLEARING` acumularia saldo positivo fantasma de R$ 30,00 por contestação. É exatamente a conta que a conciliação diária compara contra o extrato do provedor. A divergência apareceria dias depois, sem causa óbvia.
>
> Ele foi encontrado quando a verificação de sinal nova rodou contra o lançamento do próprio documento. **É o argumento mais forte a favor de escrever a verificação antes do código.**

**Compensação da dívida, na saída da garantia de uma venda posterior**

```
DEBIT  vendedor.GUARANTEE       8201
CREDIT vendedor.DEBT            6672   -- quita, DEBT volta a zero
CREDIT vendedor.PENDING         1529   release_at = pagamento + 32d
-----                                  soma = 0 ✓
```

> **A compensação acontece na saída da garantia, não na venda.** Compensar no momento da venda usaria dinheiro que ainda pode ser reembolsado pelo comprador. Se o reembolso viesse depois, seria preciso desfazer a quitação — e o razão é imutável. Custa alguns dias a mais para recuperar o valor e elimina uma classe inteira de reversão.

**Contestação ganha na defesa** — restitui na **ordem inversa** da cascata:

```
DEBIT  SYS_CLEARING          10000   -- o provedor devolve o valor contestado
DEBIT  SYS_CHARGEBACK_LOSS    3000   -- a tarifa NÃO volta; a plataforma absorve
CREDIT vendedor.DEBT          6672   -- 1º quita a dívida
CREDIT vendedor.AVAILABLE     5000   -- 2º devolve o disponível
CREDIT vendedor.RESERVE        328   -- 3º recompõe a reserva
CREDIT afiliado.AVAILABLE     1000   -- devolve a comissão estornada
-----                        13000 = 13000 ✓
```

> Restituir na ordem direta deixaria o vendedor com reserva cheia e dívida em aberto — bloqueado para saque sem motivo. E a prática de mercado quanto à tarifa varia: alguns adquirentes devolvem na disputa ganha, outros não. O lançamento assume que não, que é o caso mais comum no Brasil e o mais conservador. Se o provedor devolver, a segunda linha vira `DEBIT SYS_ACQUIRER_FEE 3000`. É a PEN-23.

**Saque, e a reversão quando o provedor recusa**

```
-- PAYOUT, na solicitação aceita, dentro do bloqueio consultivo
DEBIT  vendedor.AVAILABLE     5000
CREDIT SYS_CLEARING           5000

-- PAYOUT_REVERSAL, quando o payout vai para FAILED
DEBIT  SYS_CLEARING           5000
CREDIT vendedor.AVAILABLE     5000
```

O saldo sai de `AVAILABLE` quando o saque é **aceito**, não quando é confirmado — é o que impede o mesmo dinheiro de ser sacado duas vezes enquanto a transferência está em trânsito. Não se apaga o lançamento: reverte-se. A chave natural garante que uma reversão duplicada não credite duas vezes.

**Guardas obrigatórias antes de gravar**, todas dentro do bloqueio: `AVAILABLE ≥ valor`; `DEBT = 0` (RF-103); conta bancária **pertencente à conta, verificada e não arquivada** (RF-068, agora imposto por gatilho); segundo fator acima do limite configurável.

**Taxa de verificação, mensalidade e antecipação**

```
-- PLATFORM_FEE (verificação), na APROVAÇÃO do KYC — taxa de R$ 12,00
DEBIT  vendedor.DEBT          1200
CREDIT SYS_PROVIDER_FEE        900
CREDIT SYS_PLATFORM_REVENUE    300

-- PLATFORM_FEE (mensalidade do Escala), no 1º dia do ciclo
DEBIT  vendedor.AVAILABLE    29700
CREDIT SYS_PLATFORM_REVENUE  29700

-- ANTICIPATION, vendedor com prazo D+7 sobre uma parte de R$ 82,01
-- cobrado 2,29% × 8201 = 187,80 → 187 ; custo 1,04% × 8201 = 85,29 → 85
DEBIT  vendedor.PENDING        187
CREDIT SYS_PROVIDER_FEE         85
CREDIT SYS_PLATFORM_REVENUE    102
```

Depois da antecipação, o `RELEASE` move os 8014 restantes, com reserva de 8% (a faixa do D+7): `AVAILABLE 7373 + RESERVE 641`. **A antecipação não incide sobre o afiliado** — o prazo de recebimento é atributo da conta do vendedor; a comissão segue o prazo do próprio afiliado.

**Baixa de incobrável**, com aprovação registrada (RF-116, RF-126):

```
DEBIT  SYS_CHARGEBACK_LOSS    6672
CREDIT vendedor.DEBT          6672
```

#### Consulta de saldo

```sql
SELECT COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount_cents
                         ELSE -amount_cents END), 0) AS saldo
FROM ledger_entries
WHERE account_id = ? AND bucket = ?;
```

Nunca existe coluna `saldo`. Se a consulta ficar lenta com o volume, a solução é a tabela de resumo da seção 3.7 — derivada, reconstruível e verificada, nunca campo mutável.

#### As oito verificações de integridade

Rodam diariamente. **Resultado não vazio em qualquer uma é incidente de severidade máxima.** Todas estão como visões no esquema, e todas foram testadas nos dois sentidos: vazias com o razão são, não vazias com defeito injetado.

| # | Verificação | O que pega |
|---|---|---|
| 1 | `v_check_unbalanced_transactions` | Transação que não soma zero (RNF-014) |
| 2 | `v_check_negative_user_buckets` | Bucket de **usuário** negativo, exceto `DEBT` (RNF-032) |
| 3 | `v_check_positive_debt` | `DEBT` positivo (RF-070) |
| 4 | `v_check_system_sign_violation` | Conta de sistema contrariando o saldo normal declarado |
| 5 | `v_check_checkpoint_drift` | Resumo de saldo divergindo do razão até a mesma fronteira |
| 6 | `v_check_receivable_schedule` | Cronograma de parcelas que não fecha com a cobrança |
| 7 | `v_check_refund_accumulator` | Acumulado de reembolso divergindo da soma dos reembolsos |
| 8 | `v_check_release_schedule` | Agendamento ausente, ou vencido e não executado |

> **As verificações 2 e 3 mudaram de papel na v3.0.** Elas continuam existindo, mas o que elas encontravam agora é recusado na escrita por um gatilho de restrição **deferido**:
>
> ```sql
> CREATE CONSTRAINT TRIGGER trg_ledger_bucket_sign
>   AFTER INSERT ON ledger_entries
>   DEFERRABLE INITIALLY DEFERRED
>   FOR EACH ROW EXECUTE FUNCTION ledger_assert_bucket_sign();
> ```
>
> Ser deferido é o que torna isso viável: a verificação roda no `COMMIT`, então a ordem dos lançamentos dentro da transação não importa — uma cascata pode debitar antes de creditar sem falso positivo.
>
> As visões continuam rodando porque protegem contra escrita feita **por fora da aplicação**, que é justamente o cenário em que ninguém está olhando.

### 3.7 Concorrência e desempenho do saldo

> **O ponto onde sistemas financeiros quebram.** Dois saques simultâneos podem ambos ler o mesmo saldo disponível e ambos passar na validação. Não use apenas `SELECT` seguido de `INSERT`.

```sql
-- bloqueio consultivo por conta, com espaço de nomes próprio, dentro da transação
SELECT pg_advisory_xact_lock(4210, hashtext(:account_id));
-- só então calcula e valida
SELECT COALESCE(SUM(...),0) INTO saldo FROM ledger_entries WHERE ...;
IF saldo < :valor THEN RAISE EXCEPTION 'saldo insuficiente'; END IF;
INSERT INTO ledger_entries ...;
```

O bloqueio é liberado no fim da transação, com ou sem erro. Custa microssegundos e elimina a classe inteira de saque duplicado. **Verificado**: dois saques simultâneos de R$ 100,00 sobre saldo de R$ 100,00 — um passa, um falha, saldo final zero.

> **A forma de dois argumentos não é detalhe.** `hashtext` devolve inteiro de 32 bits; colisão entre contas é estatisticamente esperada a partir de algumas dezenas de milhares de contas. Colisão não corrompe saldo — apenas serializa duas contas sem relação, custando latência. Mas o espaço de nomes fixo evita algo pior: colidir com um bloqueio consultivo de outra parte do sistema que use o mesmo espaço numérico.

**Duas regras que precisam viver no mesmo lugar** (RNF-039), e por isso a aquisição de bloqueio existe num método único:

1. **Toda escrita no razão adquire o bloqueio da conta**, inclusive quando não valida saldo. A consolidação do resumo depende disso para ter fronteira segura.
2. **Bloqueio de várias contas é adquirido em ordem canônica** — pela chave de bloqueio, não pelo identificador. Sem isso, contestação e venda cruzadas em vendedor e afiliado geram deadlock intermitente sob carga, no caminho mais crítico e difícil de reproduzir. **Verificado**: sem ordenação, `ERROR: deadlock detected`.

#### Tabela de resumo de saldo

O RNF-004 exige p95 abaixo de 200 ms com 5 milhões de lançamentos, e um vendedor ativo acumula lançamentos indefinidamente.

```sql
CREATE TABLE ledger_checkpoints (
  account_id     uuid NOT NULL,
  bucket         text NOT NULL,
  up_to_entry_id bigint NOT NULL,
  balance_cents  bigint NOT NULL,
  updated_at     timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (account_id, bucket)
);
```

O saldo é `checkpoint.balance_cents` mais a soma das entradas com `id > up_to_entry_id`.

> **E aqui está o defeito mais silencioso que a revisão encontrou.** `up_to_entry_id` pressupõe que todo id menor já está visível. **Não está.** `bigserial` atribui o número **antes do commit**.
>
> Sequência reproduzida em duas sessões reais:
>
> 1. Sessão A abre transação e insere crédito de R$ 1,00 → recebe id 10, não confirma.
> 2. Sessão B insere crédito de R$ 7,00 → id 11, confirma.
> 3. Consolidador roda: enxerga `max(id) = 11` e soma só o confirmado. Grava `up_to_entry_id = 11`, `balance = 700`.
> 4. Sessão A confirma. O lançamento 10 existe — mas `10 <= 11`, então nunca mais entra na soma. Nem no checkpoint, nem no `WHERE id > up_to_entry_id`.
>
> ```
> SEM BLOQUEIO -> saldo exibido:      700
> SEM BLOQUEIO -> saldo verdadeiro:   800
> ```
>
> Um real desaparece. As três verificações originais rodam sobre `ledger_entries` e passam — elas nunca olham o checkpoint. O sintoma aparece como "saldo do painel diferente do extrato", que o manual manda tratar reconstruindo o resumo. **A reconstrução conserta o número e apaga a pista**, e o problema volta na semana seguinte.
>
> **A correção usa a serialização que já existe:** a consolidação adquire o mesmo bloqueio consultivo da conta. Enquanto ele é mantido, não existe lançamento em voo para aquela conta, e `max(id)` é fronteira segura. É isso que torna a regra 1 acima obrigatória. **Verificado sob concorrência real: 800, e a verificação nº 5 zera.**

A tabela é integralmente reconstruível a partir de `ledger_entries` — se divergir, a verdade é sempre o razão, e a reconstrução é o conserto.

### 3.8 Pedido, cobrança, assinatura e recebíveis

```sql
CREATE TABLE orders (
  id              uuid PRIMARY KEY,
  offer_id        uuid NOT NULL REFERENCES offers(id),
  buyer_id        uuid NOT NULL REFERENCES buyers(id),
  affiliation_id  uuid REFERENCES affiliations(id),
  buyer_snapshot  jsonb NOT NULL,          -- retrato imutável para prova
  gross_cents     bigint NOT NULL CHECK (gross_cents >= 2000),
  discount_cents  bigint NOT NULL DEFAULT 0,
  coupon_id       uuid REFERENCES coupons(id),
  paid_cents      bigint NOT NULL,
  method          text NOT NULL CHECK (method IN ('PIX','CARD','BOLETO')),
  installments    int NOT NULL DEFAULT 1 CHECK (installments BETWEEN 1 AND 12),
  status          text NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING','PAID','FAILED','EXPIRED')),
  idempotency_key text NOT NULL,
  request_hash    text NOT NULL,           -- mesma chave + corpo diferente => 409
  confirmed_at    timestamptz,             -- marco do fato gerador contábil
  created_at      timestamptz NOT NULL DEFAULT now(),
  CHECK (paid_cents = gross_cents - discount_cents),
  CHECK (paid_cents >= 500)                -- piso técnico; o comercial é 2000
);
CREATE UNIQUE INDEX uq_orders_idem ON orders (offer_id, idempotency_key);
```

> **O pedido carrega o ciclo de vida da compra; o dinheiro vive na cobrança (D04).** Com o acumulador de reembolso no pedido, uma assinatura de doze ciclos tem doze cobranças apontando para **um** pedido cujo `paid_cents` é o do primeiro ciclo. Reembolsar dois ciclos de R$ 100 acumula 20000 contra teto de 10000 e a transação aborta — **o banco recusando um reembolso legítimo**. Reproduzido em teste antes da correção.
>
> O estado consolidado do pedido passa a ser a visão `v_order_status`, derivada das cobranças. É o mesmo princípio que tirou `plan` de `accounts`: um fato, um lugar.

> **O escopo da chave de idempotência era global.** Duas integrações de vendedores diferentes que gerem a mesma chave — o que não é hipotético, já que várias bibliotecas usam contadores ou UUID v1 — causariam rejeição cruzada, com um vendedor vendo o pedido do outro ser recusado. O escopo correto é `(offer_id, idempotency_key)`. E o `Idempotency-Key` no Redis é gravado com `SET NX`, não `GET` seguido de `SET`: sem isso, duas requisições idênticas simultâneas passam as duas pela verificação antes de qualquer uma gravar.

`charges` guarda a **memória de cálculo congelada** — `plan`, `platform_fee_bps`, `platform_fee_fixed_cents`, `platform_fee_cents`, `affiliate_fee_cents`, `seller_amount_cents` — com a invariante verificada no próprio banco:

```sql
CHECK (seller_amount_cents + affiliate_fee_cents + platform_fee_cents = amount_cents)
```

Isso faz a conciliação deixar de depender de recalcular qualquer coisa a partir da tabela de preços vigente hoje. E `(subscription_id, cycle_number)` é `UNIQUE`: a cobrança de assinatura é o único caminho que movimenta dinheiro **sem chave de idempotência vinda do cliente** — não existe navegador para gerar uma. Sem unicidade, uma retentativa do processo de 15 minutos cobra o mesmo ciclo duas vezes. ShedLock estreita a janela; não a fecha.

**Recebíveis.** O RF-041 manda liberar cada parte proporcionalmente à entrada de cada parcela, e em 12x o provedor liquida em doze momentos. `receivables` é a cópia local desse cronograma, com a parte do vendedor e do afiliado **já rateadas pelo método do maior resto e gravadas** — a liberação lê o valor gravado, nunca recalcula. Recalcular na liberação é o caminho para duas implementações divergirem.

```sql
CHECK (seller_amount_cents + affiliate_amount_cents <= amount_cents)
```

> **O rateio por parcela era o segundo ponto de truncamento do sistema, com zero cobertura.** 8201 ÷ 12 = 683,41 — quem fica com o centavo? Não estava escrito em lugar nenhum. Maior resto: `base = total ÷ n`, `resto = total mod n`, as `resto` primeiras parcelas recebem `base + 1`. Determinístico, soma exata, e favorece o recebimento antecipado, que é o desejável para o vendedor. Verificado em 2.400.012 cenários.

### 3.9 Reembolso, saque, contestação e evidência

`refunds` é entidade própria, como em toda plataforma de pagamento, com a repartição gravada linha a linha:

```sql
CHECK (seller_cents + affiliate_cents + platform_cents + provider_cents = amount_cents)
```

O acumulado por parte é `SUM` sobre os reembolsos daquela cobrança — consulta, nunca campo mutável. A verificação nº 7 confere o acumulador contra a soma.

> **`disputes.kind` não aceita mais `REFUND` (D10).** Desde que `refunds` passou a existir, o mesmo reembolso podia ser gravado em duas entidades, com dois estados divergentes e nenhuma delas autoritativa. Disputa é contestação.

`payouts` ganhou o gatilho que faltava:

> **O defeito mais grave desta passada (D09).** Nada ligava `payouts.account_id` a `bank_accounts.account_id`. **Um saque da conta A para a conta bancária de B era aceito pelo banco.** É a classe AM-12 — acesso a recurso de outra conta — no caminho onde ela custa mais caro: dinheiro saindo para o titular errado. O gatilho verifica titularidade, verificação e arquivamento, e recusa os três casos.

`sale_evidence` guarda IP, agente, impressão de dispositivo, hash e horário do aceite dos termos, resultado do 3DS, entrega e abertura do e-mail e registro de acesso — é o que monta a defesa automática do RF-075.

### 3.10 Fiscal

`invoices.issuer_id` aponta para a conta do **vendedor**, porque é ele quem presta o serviço ao comprador. A Paysi é meio técnico de emissão, agindo como terceiro autorizado.

> **Essa distinção não é semântica: ela define quem responde por erro de alíquota, quem recolhe o ISS e quem é autuado (PEN-19).** E era um uuid livre: a nota podia sair em nome do afiliado. Um gatilho amarra o emissor ao vendedor do produto daquela cobrança.

A nota da taxa da Paysi (RF-090) é outra coisa: emitida pela Paysi contra o vendedor, e vive no módulo `billing`.

### 3.11 Inbox de eventos do provedor

```sql
CREATE TABLE provider_events (
  provider           text NOT NULL,
  provider_event_id  text NOT NULL,
  event_type         text NOT NULL,
  payload            jsonb NOT NULL,
  signature_valid    boolean NOT NULL,
  received_at        timestamptz NOT NULL DEFAULT now(),
  processed_at       timestamptz,
  status             text NOT NULL DEFAULT 'RECEIVED'
                       CHECK (status IN ('RECEIVED','PROCESSED','IGNORED','FAILED')),
  error              text,
  attempt_count      int NOT NULL DEFAULT 0,
  next_retry_at      timestamptz,
  PRIMARY KEY (provider, provider_event_id)
);
```

Espelho do outbox. O ADR-07 cuida da saída, o ADR-08 da API; **nada tratava a entrada**. O registro acontece na mesma transação do efeito, e a coluna `status` é o que distingue "já processado, ignore" de "recebido e nunca processado, reprocesse" — a distinção que faz a diferença entre deduplicar e perder o evento. Depende da PEN-22: sem identificador estável do provedor, a deduplicação recai só sobre a chave natural do razão, que é rede de segurança, não controle primário.

### 3.12 Administração, ajustes e LGPD

`admin_audit_log` é append-only pelos mesmos gatilhos do razão, com `reason` obrigatório sem exceção.

`ledger_adjustments` é a válvula de escape do razão imutável, e por isso a que precisa de mais cerimônia. A segregação vai para dentro do banco:

```sql
CONSTRAINT segregacao_de_funcao CHECK (
  status <> 'APPROVED' OR auto_approved
  OR (approved_by IS NOT NULL AND approved_by <> requested_by)
)
```

Quem pede não aprova. Abaixo do limiar de valor, `auto_approved` dispensa a segunda assinatura — o arranjo possível enquanto a equipe for de uma pessoa.

`lgpd_requests` registra pedido de titular com prazo, responsável e evidência (RF-115).

---

## 4. Contrato da API

REST sobre JSON. Autenticação por token de sessão no painel e por chave de API na integração. Todos os valores em centavos.

### 4.1 Convenções

| Item | Regra |
|---|---|
| Idempotência | `Idempotency-Key` obrigatório em `POST` que movimenta dinheiro |
| Versionamento | Prefixo `/v1`. Mudança incompatível cria `/v2` |
| Erros | Corpo com `code` estável, `message` legível e `field` quando aplicável |
| Paginação | Por cursor, nunca por deslocamento |
| Limite de uso | Por chave e por IP, com `429` e `Retry-After` |

### 4.2 Endpoints principais

| Método | Rota | Função |
|---|---|---|
| POST | `/v1/accounts` | Criar conta |
| GET | `/v1/accounts/me` | Dados e estado da verificação |
| POST | `/v1/accounts/me/kyc` | Iniciar verificação, devolve link do provedor |
| POST | `/v1/products` | Criar produto |
| POST | `/v1/products/{id}/offers` | Criar oferta |
| GET | `/v1/offers/{slug}/checkout` | Dados públicos, incluindo campos exigidos pelo segmento |
| POST | `/v1/checkout/{slug}/orders` | Criar pedido e cobrança |
| GET | `/v1/orders/{id}` | Consultar pedido, com estado consolidado |
| POST | `/v1/charges/{id}/refunds` | Reembolsar, total ou parcial |
| GET | `/v1/charges/{id}/refunds` | Listar reembolsos de uma cobrança |
| GET | `/v1/subscriptions` | Listar assinaturas |
| POST | `/v1/subscriptions/{id}/cancel` | Cancelar |
| GET | `/v1/balance` | Saldo nos cinco estados |
| GET | `/v1/ledger` | Extrato paginado com memória de cálculo |
| POST | `/v1/payouts` | Solicitar saque |
| GET | `/v1/marketplace` | Vitrine de produtos |
| POST | `/v1/affiliations` | Pedir afiliação |
| POST | `/v1/affiliations/{id}/approve` | Aprovar e fixar comissão |
| GET | `/v1/invoices/{chargeId}` | Situação e link da nota fiscal |
| POST | `/v1/webhook-endpoints` | Cadastrar destino e receber o segredo, uma única vez |
| POST | `/v1/webhook-endpoints/{id}/rotate` | Rotacionar segredo com sobreposição |

> **O reembolso é rota de cobrança, não de pedido.** Era `/v1/orders/{id}/refund`. Com assinatura, o pedido tem N cobranças e a rota não sabe qual delas estornar — a mesma raiz do defeito D04.

### 4.3 Criação de pedido

```http
POST /v1/checkout/crm-pro/orders
Idempotency-Key: 5f2c9a1e-3b7d-4c8f-9e0a-1d2b3c4d5e6f

{
  "buyer": { "name": "Marina Duarte",
             "email": "marina@estudioml.com.br",
             "personType": "PJ",
             "taxId": "00000000000000",
             "legalName": "Estudio ML Servicos Digitais LTDA",
             "address": { "zip": "01310100", "number": "1000" } },
  "method": "CARD",
  "installments": 1,
  "cardToken": "tok_prov_9f3a...",     // token do provedor, nunca o numero
  "coupon": "BEMVINDO20",
  "visitorKey": "vk_8271...",          // resolve a atribuicao do afiliado
  "termsHash": "sha256:4b1c..."
}
```

```json
201 Created
{
  "orderId": "9b1d...",
  "status": "PAID",
  "paidCents": 17700,
  "split": [
    { "role": "SELLER",    "accountId": "a1...", "amountCents": 14670 },
    { "role": "AFFILIATE", "accountId": "a2...", "amountCents": 1770  },
    { "role": "PLATFORM",  "accountId": null,    "amountCents": 682   }
  ],
  "providerFeeCents": 578,
  "sellerFeeCents": 1260,
  "availableAt": "2026-09-14T00:00:00Z",
  "invoice": { "status": "QUEUED" }
}
```

> **O exemplo da versão 1.1 violava a invariante do próprio sistema.** Os números publicados eram `15181 + 1770 + 749`, que somam exatamente os 17700 pagos — deixando os 578 de custo do provedor fora da conta. Somando tudo dava 18278: **R$ 5,78 criados do nada.**
>
> Conferência dos números corretos, gerada pelo motor:
>
> | Item | Cálculo | Valor |
> |---|---|---|
> | Custo do provedor | 2,99% × 17700 = 529,23 → 529, mais 49 | 578 |
> | Taxa cobrada do vendedor | 5,99% × 17700 = 1060,23 → 1060, mais 200 | 1260 |
> | Afiliado | 10% de 17700 | 1770 |
> | Vendedor | 17700 − 1260 − 1770 | 14670 |
> | Plataforma | 1260 − 578 | 682 |
>
> `14670 + 1770 + 682 + 578 = 17700` ✓
>
> O exemplo tinha sido escrito à mão e nunca passou pelo motor de divisão — que é justamente o componente já pronto e testado. Um cliente que integrasse por ele construiria a conciliação errada. **Todo exemplo numérico de contrato de API passa a ser gerado por teste, não digitado.**

### 4.4 Eventos emitidos

| Evento | Quando |
|---|---|
| `payment.approved` | Cobrança confirmada. É aqui que o vendedor libera o acesso |
| `payment.declined` | Cobrança recusada |
| `payment.expired` | Pix ou boleto vencido sem pagamento |
| `payment.refunded` | Reembolso **total**. Bloquear acesso |
| `payment.partially_refunded` | Reembolso parcial, com `refundedCents` e `remainingCents`. **Não** instrui bloqueio |
| `subscription.created` | Assinatura iniciada, inclusive em teste |
| `subscription.renewed` | Ciclo cobrado com sucesso |
| `subscription.past_due` | Cobrança falhou, régua em andamento |
| `subscription.canceled` | Encerrada por cancelamento ou falha final |
| `chargeback.opened` | Contestação aberta pelo comprador |
| `invoice.issued` | Nota fiscal emitida, com link |
| `invoice.failed` | Emissão falhou após as retentativas |

```http
POST https://api.docliente.com.br/paysi
X-Paysi-Signature: t=1786646822,v1=5f2c9a1e3b7d...
X-Paysi-Event-Id: 7c4e...
```

A assinatura é HMAC-SHA256 sobre `timestamp + "." + corpo`, com o segredo **daquele endpoint**. O destino deve rejeitar desvio de horário maior que 5 minutos e tratar `X-Paysi-Event-Id` repetido como duplicata. Durante rotação, os dois segredos são aceitos por 24 horas.

---

## 5. Processos agendados

| Processo | Frequência | Função |
|---|---|---|
| Cobrança de assinatura | 15 min | Cobra assinaturas com `next_charge_at` vencido |
| Emissão de boleto de ciclo | Diária | Emite o boleto do próximo ciclo com a antecedência da oferta |
| Régua de retentativa | 15 min | Reprocessa **a mesma cobrança** em D+1, D+3, D+7 e D+14 |
| Saída da garantia | Horária | Lê `ledger_release_schedule`, move `GUARANTEE` → `PENDING`, compensando dívida |
| Liberação de saldo | Horária | Move `PENDING` → `AVAILABLE` + `RESERVE` |
| Liberação de reserva | Diária | Move `RESERVE` → `AVAILABLE` após 90 dias |
| Publicação de eventos | Contínua | Lê o outbox com `SKIP LOCKED` e entrega |
| Reprocessamento de evento do provedor | 5 min | Evento em `RECEIVED` ou `FAILED` com retentativa vencida |
| Fila fiscal | 5 min | Emite e cancela notas, com retentativa exponencial |
| Cobrança do plano Escala | Diária | Debita a mensalidade do saldo disponível |
| Conciliação | Diária | Compara livro-razão com extrato do provedor |
| Integridade do razão | Diária | As oito verificações da seção 3.6 |
| Consolidação de saldo | Horária | Atualiza `ledger_checkpoints`, sob bloqueio |
| Índices de risco | Diária | Recalcula contestação e reembolso por vendedor e agregado |
| Expurgo de cliques | Diária | Remove cliques de afiliado com mais de 60 dias |

> **Agendamento com mais de uma instância.** Com duas instâncias, o mesmo agendamento roda duas vezes e pode cobrar duas vezes. Use ShedLock ou bloqueio consultivo por nome de tarefa. Isto não é otimização: é correção.

> **O publicador precisa de `SKIP LOCKED`.** Com mais de uma instância, dois publicadores leem a mesma linha não publicada e entregam o mesmo evento duas vezes. A entrega continua sendo "ao menos uma vez" por causa de falha de rede após o envio, e isso é inerente ao padrão. O que `SKIP LOCKED` elimina é a duplicação gratuita, causada por nós mesmos. Vale igual para a fila de liberação e a fila fiscal.

> **O processo de liberação precisa marcar o que liberou.** `released_at` e `release_transaction_id` são escritos **na mesma transação** que grava os lançamentos de destino. Se o processo cair no meio, ou os dois aconteceram ou nenhum.

---

## 6. Ambientes, implantação e testes

| Ambiente | Provedor | Uso |
|---|---|---|
| Local | Docker Compose, provedor e emissor fiscal falsos | Desenvolvimento sem contrato |
| Homologação | Sandbox do provedor e do parceiro fiscal | Testes de integração e ensaio de conciliação |
| Produção | Provedor real, região brasileira | Operação |

### 6.1 Regras de migração

- Flyway desde a primeira versão. **Migração aplicada nunca é editada** — vale a partir da primeira execução em qualquer ambiente que não seja o seu.
- Toda migração precisa ser compatível com a versão anterior da aplicação, para permitir reversão.
- Alteração destrutiva em dois passos: primeiro adicionar e escrever nos dois lugares, depois remover em versão seguinte.
- Nenhuma migração altera dado do livro-razão.

### 6.2 Cobertura mínima de testes

| Módulo | Meta | Justificativa |
|---|---|---|
| `payment.split` | 100% de ramos | Erro aqui vira divergência silenciosa |
| `ledger` | 100% de ramos | Idem, com teste de concorrência e de cascata de dívida |
| Régua de retentativa | 90% | Erro custa receita recorrente |
| Risco e limites | 90% | Erro custa dinheiro em fraude |
| `fiscal` | 80% | Erro é visível e corrigível; não move dinheiro |
| Demais módulos | 70% | Padrão |

### 6.3 A varredura de arredondamento

| Dimensão | v1.1 | Agora |
|---|---|---|
| Faixa de ticket | R$ 5,00 a R$ 500,00 | **R$ 5,00 a R$ 2.000,00** |
| Valores de ticket | 49.501 | 199.501 |
| Meios | 4 | 5, com boleto |
| Comissões | 1 | 11, incluindo 0, 1, 4999 e 5000 pontos-base |
| Planos | 1 | 2 |
| **Total de cenários** | 198.004 | **21.945.110** |

> **A faixa começa em R$ 5,00, não em R$ 20,00, e isso é correção da v3.0.** R$ 20,00 é o piso **comercial** da oferta. `orders.paid_cents` desce legitimamente até R$ 5,00 com cupom. Uma varredura que não cobre a faixa que o banco aceita não cobre o sistema.
>
> Nesse extremo a varredura devolveu dois números que valem como fato registrado: a **menor margem da plataforma em toda a faixa é de 10 centavos** — logo o RF-039 nunca dispara com a tabela publicada; e o **menor valor que sobra para o vendedor é 16 centavos**, em R$ 5,00 com comissão de 50% no cartão em 12x. O segundo motivou a guarda nova do RF-027.

Duas varreduras irmãs, pelo mesmo motivo — são pontos de truncamento:

| Varredura | Cenários | O que prova |
|---|---|---|
| Rateio por parcela | 2.400.012 | Soma exata em 1 a 12 parcelas; desvio máximo de 1 centavo |
| Reembolso parcial | 4.275.150 | Soma exata em 1 a 17 fatias; **nenhuma parte negativa em nenhuma fatia** |

> A segunda condição é a que pega o defeito. A primeira sozinha passa: a regra ingênua fecha a soma e mesmo assim exige alocação negativa de −19 centavos na fatia final.

Continua sendo aritmética inteira sem entrada e saída. As três varreduras juntas rodam em menos de um minuto.

### 6.4 Testes que precisam existir desde o primeiro dia

| Teste | O que prova |
|---|---|
| `RoundingSweepTest` | Nenhum centavo perdido em toda a faixa, comissão e plano |
| `InstallmentSplitTest` | Rateio por parcela soma exato em toda faixa e todo número de parcelas |
| `PartialRefundSplitTest` | N parciais somam a alocação original, sem parte negativa |
| `BalanceConcurrencyTest` | Cem saques simultâneos, nenhum saldo negativo |
| `LedgerBucketFlowTest` | Garantia, pendente, reserva e disponível seguem a cadeia sem órfãos |
| `DebtCascadeTest` | Contestação sem reserva gera dívida, compensa e baixa corretamente |
| `GuaranteeVsPayoutTest` | D+2 com garantia de 30 dias não libera antes de 30 dias |
| `IntegrityCheckTest` | As oito verificações detectam desbalanceamento injetado |
| `SystemAccountSignTest` | Conta de sistema nunca contraria o saldo normal declarado |
| `CheckpointConcurrencyTest` | Consolidação com escrita concorrente não perde lançamento |
| `CheckpointRebuildTest` | Resumo reconstruído bate com o razão |
| `ProviderEventReplayTest` | Mesmo evento entregue duas vezes e simultaneamente gera um efeito só |
| `LockOrderingTest` | Transações cruzadas em duas contas não geram deadlock |
| `ReleaseIdempotencyTest` | Processo de liberação rodado duas vezes move o dinheiro uma vez |
| `PayoutReversalTest` | Saque recusado devolve o saldo exatamente uma vez |
| `PayoutOwnershipTest` | Saque para conta bancária de outro titular é recusado |
| `VerificationFeeTest` | Taxa de verificação em conta zerada gera `DEBT`, nunca `AVAILABLE` negativo |
| `ChargebackReversalTest` | Defesa vencida restitui na ordem inversa da cascata |
| `AnticipationTest` | Antecipação trunca a favor da plataforma e não toca o afiliado |
| `PartialRefundTest` | Reembolso parcial não emite `payment.refunded` nem fecha o pedido |
| `SubscriptionCycleUniqueTest` | Retentativa do processo não cobra o mesmo ciclo duas vezes |
| `NegativeBucketGuardTest` | Lançamento que deixaria bucket negativo é recusado no `COMMIT` |
| `IdempotencyTest` | Requisição repetida e simultânea devolvem o mesmo resultado |
| `CrossAccountAccessTest` | Nenhuma rota devolve recurso de outra conta |
| `PackageDependencyTest` | ArchUnit: `ledger` não importa Spring, `web` não chama adapter |
| `ApiExampleTest` | Os exemplos numéricos da seção 4.3 são gerados pelo motor |

`paysi-testes-v3.0.sql` já cobre a camada de banco de 70 dessas asserções e roda em segundos. Os testes de aplicação acima são o que falta escrever em Java.

---

*Documento técnico interno. O DDL autoritativo é `paysi-esquema-v3.0.sql`, aplicado e exercitado em PostgreSQL 16.15. Nomes de tabela e coluna podem mudar durante a implementação sem alterar as decisões registradas.*
