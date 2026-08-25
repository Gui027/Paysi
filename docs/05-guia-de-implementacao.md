# Paysi — Documento 5: Guia de Implementação

**Versão 3.0 · 21 de agosto de 2026 · interno · Substitui a versão 2.0 e incorpora as revisões v2.1 e v3.0**

---

> **Para que serve este documento.** Os documentos 1 a 4 dizem o que construir, por que e em que ordem. Este diz **como começar**: estrutura de repositório, preparação do ambiente, organização de pastas e o que fazer em cada um dos primeiros quarenta dias. É o documento que alguém abre no primeiro dia de trabalho.
>
> O código dos sete pontos onde a prosa não basta está no documento 6.

---

## 1. Estrutura de repositório

> **Ponto que exige sua confirmação — desvio do que foi pedido.** Você definiu Next.js para o frontend. Este documento propõe **Next.js no painel e Vite com React no checkout** — React nos dois, mas não Next nos dois. O motivo está no ADR-04.
>
> Se preferir Next nos dois por padronização, é escolha válida — meça o pacote gerado antes de decidir. A estrutura muda pouco: troca-se `vite.config.ts` por `next.config.js` e a pasta `telas/` por rotas de página. O restante — camadas, nomenclatura, regra de nenhuma aritmética no frontend — continua igual.
>
> Há uma tensão honesta aqui: o argumento do peso favorece Vite, mas o RNF-001 pede interatividade em 1,5 s no 4G, e para isso renderização no servidor ajuda. A saída que resolve os dois é **casca estática pré-renderizada com os dados da oferta injetados na borda** — possível nas duas ferramentas, e é isso que precisa ser medido.

### 1.1 Como as três aplicações convivem

Um repositório, três aplicações publicadas de forma independente.

```
shared/            tokens e tipos · só em compilação
   │
   ├── web-painel      Next.js · vendedor e afiliado
   └── web-checkout    Vite + React · público
              │
              ▼
       API Spring Boot
    com.paysi — a única fonte da verdade
              │
    ┌─────────┼─────────┐
    ▼         ▼         ▼
 Postgres  Redis+fila  Provedor · Parceiro fiscal
```

HTTPS com JWT no painel; HTTPS com idempotência no checkout.

### 1.2 Decisão: repositório único

Backend, painel e checkout evoluem juntos e compartilham contratos.

| Alternativa | Por que não |
|---|---|
| Três repositórios | Mudança de contrato exige coordenar três pedidos de revisão. Com uma pessoa, é atrito puro |
| Uma aplicação só | Checkout carregaria o peso do painel. Viola o ADR-04 |

```
paysi/
├── backend/                Spring Boot · a fonte da verdade
├── web-painel/             Next.js · vendedor e afiliado
├── web-checkout/           Vite + React · público, enxuto
├── shared/
│   ├── tokens.css          cores e tipografia, usado pelos dois frontends
│   └── api-types.ts        tipos gerados do contrato da API
├── infra/
│   ├── docker-compose.yml  Postgres, Redis, RabbitMQ, Mailpit
│   └── deploy/
├── docs/                   os seis documentos, o esquema, os testes e as telas
├── .github/workflows/ci.yml
└── README.md
```

---

## 2. Preparação do ambiente

### 2.1 O que instalar

| Ferramenta | Versão | Uso |
|---|---|---|
| JDK | 21 LTS | Backend. Use Temurin ou Corretto |
| Node.js | 20 LTS | Frontends |
| Docker e Compose | Atual | Banco, cache, fila e e-mail locais |
| Maven | Wrapper no projeto | Não instale global; use `./mvnw` |
| Git | Atual | — |

### 2.2 Infraestrutura local

`infra/docker-compose.yml`:

```yaml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: paysi
      POSTGRES_USER: paysi
      POSTGRES_PASSWORD: paysi
    ports: ["5432:5432"]
    volumes: ["pgdata:/var/lib/postgresql/data"]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U paysi"]
      interval: 5s
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
  rabbit:
    image: rabbitmq:3-management-alpine
    ports: ["5672:5672", "15672:15672"]
  mail:
    image: axllent/mailpit
    ports: ["1025:1025", "8025:8025"]
volumes:
  pgdata:
```

```bash
cd infra && docker compose up -d
docker compose ps                   # confere se tudo subiu
# painel do RabbitMQ: http://localhost:15672 (guest / guest)
# caixa de e-mail:    http://localhost:8025
```

### 2.3 Primeira execução do backend

```bash
cd backend
./mvnw flyway:migrate      # aplica o esquema (V000 a V029)
./mvnw spring-boot:run     # sobe em http://localhost:8080
./mvnw test                # roda a bateria completa
```

E, enquanto a suíte Java não existir, os testes de banco rodam direto:

```bash
psql -d paysi -f docs/paysi-testes-v3.0.sql   # espera 70 PASS, 0 FAIL
```

### 2.4 Dois papéis de banco, e por que isso não é detalhe de infraestrutura

`V000__roles.sql` cria `paysi_app`. A migração roda como `paysi`, que é o dono das tabelas; **a aplicação conecta como `paysi_app`**, que não tem `UPDATE` nem `DELETE` no razão nem na auditoria.

> **Sem essa separação, o `REVOKE` do razão não protege nada.** Dono de tabela ignora `GRANT` e `REVOKE` — se a aplicação conectar como `paysi`, ela pode alterar `ledger_entries` à vontade, e sobra apenas o gatilho segurando a imutabilidade. Um gatilho pode ser desabilitado por quem tem privilégio sobre a tabela. Dois papéis, dois trabalhos.
>
> E há o motivo prosaico: `V011` e `V023` fazem `REVOKE ... FROM paysi_app`. Sem o papel, **a migração aborta em qualquer ambiente limpo** — CI, máquina nova, `flyway:clean flyway:migrate`.

### 2.5 Variáveis de ambiente

`.env.example` versionado; `.env` nunca vai para o repositório.

```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/paysi
DATABASE_USER=paysi_app            # a aplicação NÃO é dona das tabelas
DATABASE_PASSWORD=
DATABASE_MIGRATION_USER=paysi      # só o Flyway usa este
REDIS_URL=redis://localhost:6379
RABBIT_URL=amqp://guest:guest@localhost:5672

PAYSI_PROVIDER=fake                # fake | asaas
ASAAS_API_KEY=
ASAAS_BASE_URL=https://api-sandbox.asaas.com/v3

PAYSI_FISCAL=fake                  # fake | parceiro
FISCAL_API_KEY=

JWT_SECRET=
COLUMN_ENCRYPTION_KEY_REF=         # referência no cofre, não a chave
API_KEY_PEPPER_REF=                # pepper do HMAC das chaves de API
VAULT_ADDR=
```

> **`WEBHOOK_SIGNING_SECRET` foi removido de propósito.** Um segredo global para assinar as notificações de todos os vendedores significa que um vazamento compromete a base inteira e que rotacionar exige quebrar todas as integrações no mesmo instante. O segredo nasce **por endpoint**, na tabela `webhook_endpoints`, cifrado em coluna. **Se você vir essa variável voltar ao `.env`, é regressão.**

> **Regra que nunca se quebra.** Nenhum segredo entra no repositório, nem em arquivo de exemplo, nem em comentário, nem em teste. Adicione `.env` ao `.gitignore` **antes do primeiro commit** — depois de vazar, trocar a chave é a única saída.

---

## 3. Camadas da aplicação

Cada requisição atravessa as mesmas camadas, na mesma ordem. Nenhuma camada pula outra.

| Camada | Responsabilidade |
|---|---|
| Controlador | Recebe HTTP, valida formato, extrai identidade. Não contém regra de negócio |
| Serviço de aplicação | Orquestra o caso de uso, abre a transação, chama domínio e portas |
| Domínio | Motor de divisão, livro-razão, máquinas de estado. Sem Spring, sem banco |
| Porta | Interface para o mundo externo: provedor, emissor fiscal, repositório, fila |
| Adaptador | Implementação concreta: cliente do Asaas, parceiro fiscal, JDBC, RabbitMQ |

### 3.1 Fluxo de uma compra

| # | Onde | O que acontece |
|---|---|---|
| 1 | Checkout | Carrega a oferta pela rota pública, que informa os campos exigidos pelo segmento. **Nenhum valor vem do cliente** |
| 2 | Checkout | Componente do provedor tokeniza o cartão. O dado vai direto para o provedor |
| 3 | Checkout | Envia pedido com token, dados do comprador e chave de idempotência |
| 4 | Controlador | Valida formato e grava a chave de idempotência no Redis com `SET NX` |
| 5 | Serviço | Lê a oferta do banco e **recalcula o valor**. Resolve o afiliado pelo último clique |
| 6 | Domínio | Motor de divisão calcula vendedor e afiliado e verifica a invariante de soma |
| 7 | Porta do provedor | Cria a cobrança enviando **apenas vendedor e afiliado** como valores fixos |
| 8 | Serviço | **Transação 1 — criação.** Grava comprador, pedido, cobrança, recebíveis e o evento de pedido criado no outbox. **Nenhum lançamento no razão** |
| 8b | Provedor | Confirmação chega por webhook (cartão à vista: em geral no mesmo instante; Pix e boleto: depois) |
| 8c | Serviço | **Transação 2 — confirmação.** Registra o evento em `provider_events`, marca a cobrança como paga, grava os lançamentos em `GUARANTEE` e o `payment.approved` no outbox. Tudo na mesma transação |
| 9 | Publicador | Processo separado lê o outbox com `SKIP LOCKED` e entrega ao sistema do vendedor |
| 10 | Fila fiscal | Se o segmento exigir, enfileira a emissão da nota. Assíncrona, nunca bloqueia |
| 11 | Controlador | Devolve o resultado e memoriza a resposta pela chave de idempotência |

> **Os quatro pontos que não podem ser invertidos.**
>
> **Passo 5** — o valor sempre vem do banco, nunca do corpo da requisição. É o controle contra a ameaça AM-15.
>
> **Passo 7** — a instrução de divisão leva só vendedor e afiliado, calculados a partir do valor pago. A plataforma é a recebedora residual e absorve a diferença entre custo estimado e real. Mandar o valor da plataforma calculado por estimativa é o que produz o furo de conciliação de um centavo.
>
> **Passo 8c** — **o razão só é tocado na confirmação do pagamento.** A versão anterior deste guia mandava gravar os lançamentos na criação do pedido, contrariando o documento 2. Para Pix e boleto, criação e confirmação são momentos diferentes: escrever na criação credita garantia para todo Pix abandonado, e `SYS_CLEARING` passa a afirmar que recebeu dinheiro que não recebeu.
>
> **Passo 8c, de novo** — razão, inbox e outbox gravam na **mesma transação**. Fora dela, existe evento sem fato e fato sem evento. E o crédito vai para `GUARANTEE`, não para `PENDING`: era exatamente aqui que a versão 1.1 errava.

---

## 4. Estrutura do backend

Um módulo Maven só, com pacotes que impõem a fronteira. Módulos Maven separados viram atrito antes de virarem benefício com uma pessoa na equipe.

```
backend/src/main/java/com/paysi/
├── PaysiApplication.java
│
├── core/                    sem dependências. nem de Spring
│   ├── money/               Money.java  CONCLUÍDO · centavos em long
│   │                        Bps.java    percentual em pontos-base
│   ├── id/                  AccountId, OrderId, ChargeId
│   └── error/               DomainException, NotFound, Forbidden
│
├── identity/
│   ├── domain/              Account, KycStatus, PersonType
│   ├── app/                 SignUpService, LoginService, KycService, MfaService
│   ├── port/                AccountRepository
│   ├── adapter/             JpaAccountRepository
│   └── web/                 AccountController, dto/
│
├── catalog/
│   ├── domain/              Product, Offer, Coupon, Segment
│   ├── app/                 ProductService, OfferService, CouponService
│   ├── port/                ProductRepository, OfferRepository
│   └── web/                 ProductController, PublicOfferController
│
├── buyer/
│   ├── domain/              Buyer, TaxProfile
│   ├── app/                 BuyerService, AnonymizationService
│   └── port/                BuyerRepository
│
├── affiliate/
│   ├── domain/              Affiliation, Attribution
│   ├── app/                 AffiliationService, MarketplaceService, ClickTrackingService
│   ├── port/                AffiliationRepository, ClickRepository
│   └── web/                 AffiliationController, MarketplaceController
│
├── payment/
│   ├── split/               CONCLUÍDO · sem dependências externas
│   │                        SplitEngine, InstallmentSplit, RefundSplit, PaymentMethod
│   ├── charge/
│   │   ├── domain/          Order, Charge, Subscription, Receivable, Refund,
│   │   │                    OrderStatus, RetrySchedule
│   │   ├── app/             CreateOrderService, SubscriptionBillingService,
│   │   │                    RefundService, BoletoCycleService
│   │   └── web/             CheckoutController, OrderController, RefundController
│   └── provider/
│       ├── PaymentProvider.java      a interface. tudo passa por aqui
│       ├── inbox/                    ProviderEventController, ProviderEventProcessor,
│       │                             ProviderEventRetryJob
│       ├── dto/                      CreateChargeRequest, ChargeResult,
│       │                             SubAccountRequest, ThreeDsResult
│       ├── fake/                     FakeProvider — desenvolver sem contrato
│       └── asaas/                    AsaasProvider, AsaasClient, AsaasMapper
│
├── ledger/                  JDBC puro. nada de JPA aqui
│   ├── domain/              Entry, Bucket, Direction, LedgerTransaction, ReferenceType
│   ├── app/                 LedgerService, BalanceService, GuaranteeReleaseService,
│   │                        ReleaseService, DebtService, CheckpointService,
│   │                        AnticipationService, AdjustmentService
│   ├── adapter/             JdbcLedgerRepository, AdvisoryLock, ReleaseScheduleRepository
│   └── check/               IntegrityCheckJob
│
├── payout/
│   ├── domain/              Payout, BankAccount
│   ├── app/                 PayoutService
│   └── web/                 PayoutController
│
├── risk/
│   ├── domain/              RiskIndex, SellerTier, RiskEvent
│   ├── app/                 TierService, FraudRuleService, DisputeService,
│   │                        EvidenceService, PlatformRiskService
│   └── web/                 DisputeController
│
├── fiscal/
│   ├── domain/              Invoice, InvoiceStatus, FiscalProfile
│   ├── app/                 InvoiceService, InvoiceQueueJob
│   ├── port/                InvoiceIssuer            a interface
│   └── adapter/             fake/FakeIssuer, partner/PartnerIssuer
│
├── billing/
│   ├── domain/              PlatformPlan, PlatformSubscription
│   └── app/                 PlanService, PlatformBillingJob, VerificationFeeService
│
├── notification/
│   ├── domain/              OutboxEvent, EventType, WebhookEndpoint
│   ├── app/                 OutboxWriter, WebhookPublisher, HmacSigner,
│   │                        SecretRotationService
│   └── adapter/             HttpWebhookSender
│
├── reconciliation/
│   ├── app/                 ReconciliationJob, DivergenceReport
│   └── web/                 ReconciliationController
│
├── admin/
│   ├── app/                 AdminSearchService, SuspensionService, AuditLog,
│   │                        LgpdRequestService, AdjustmentApprovalService
│   └── web/                 AdminController
│
└── config/
    ├── SecurityConfig, IdempotencyFilter
    ├── SchedulerConfig       com ShedLock
    └── ObservabilityConfig
```

### 4.1 Regras de dependência entre pacotes

| Pacote | Pode depender de |
|---|---|
| `core` | Nada |
| `payment.split` | Apenas `core` |
| `ledger` | Apenas `core` e JDBC |
| `*.domain` | `core` e o próprio domínio |
| `*.app` | Domínio e portas do próprio pacote; outros `*.app` |
| `*.web` | Apenas `*.app` do próprio pacote |
| `*.adapter` | Portas que implementa |

Vale automatizar com ArchUnit: um teste que falha se `ledger` importar Spring, se `web` chamar `adapter` direto, ou **se qualquer classe fora de `AdvisoryLock` chamar `pg_advisory_xact_lock`**. Custa uma hora e impede erosão silenciosa.

### 4.2 Recursos e migrações

```
backend/src/main/resources/
├── application.yml
├── application-local.yml
├── application-prod.yml
└── db/migration/
    ├── V000__roles.sql                        [novo v3.0]
    ├── V001__accounts.sql
    ├── V002__mfa_credentials.sql
    ├── V003__catalog.sql                      products, offers, gatilhos
    ├── V004__coupons.sql                      com coupon_redemptions
    ├── V005__buyers.sql
    ├── V006__affiliations.sql
    ├── V007__affiliation_triggers.sql
    ├── V008__orders_charges_subscriptions.sql
    ├── V009__receivables.sql
    ├── V010__ledger.sql                       contas, transações, lançamentos, agendamento
    ├── V011__ledger_triggers.sql              imutabilidade, referência, agendamento, sinal
    ├── V012__ledger_checkpoints.sql           consolidação sob bloqueio
    ├── V013__bank_accounts.sql
    ├── V014__payouts.sql                      com gatilho de titularidade
    ├── V015__disputes_evidence.sql
    ├── V016__account_risk.sql
    ├── V017__api_keys.sql
    ├── V018__webhook_endpoints.sql
    ├── V019__outbox.sql
    ├── V020__idempotency_keys.sql
    ├── V021__fiscal_profiles_invoices.sql     com gatilho de emissor
    ├── V022__platform_subscriptions.sql       com plano padrão por conta
    ├── V023__admin_users_audit.sql
    ├── V024__lgpd_requests.sql
    ├── V025__refunds.sql
    ├── V026__ledger_adjustments.sql
    ├── V027__provider_events.sql
    ├── V028__integrity_views.sql              as oito verificações
    └── V029__system_accounts_seed.sql
```

> **A ordem não é arbitrária.** `V000__roles` vem antes de tudo, ou `V011` e `V023` abortam. `V005__buyers` antes de `V008__orders`, porque o pedido referencia o comprador. `V010__ledger` antes de `V012__ledger_checkpoints`. E `V029__system_accounts_seed` fecha a sequência porque semeia as contas de sistema, sem as quais nenhum lançamento de venda fecha.
>
> Se você reordenar durante o desenvolvimento, reordene **antes** de rodar em qualquer ambiente que não seja o seu — migração aplicada nunca é editada.

`docs/paysi-esquema-v3.0.sql` é o conjunto completo em arquivo único, para revisão. Cada bloco delimitado por `=== Vnnn__nome.sql ===` vira um arquivo. **Divida antes de rodar em ambiente compartilhado.**

### 4.3 Testes

```
backend/src/test/java/com/paysi/
├── payment/split/
│   ├── SplitEngineTest.java              CONCLUÍDO
│   ├── RoundingSweepTest.java            faixa a partir de R$ 5,00, comissão, plano
│   ├── InstallmentSplitTest.java         maior resto, 1 a 12 parcelas
│   └── PartialRefundSplitTest.java       truncagem cumulativa, sem parte negativa
├── ledger/
│   ├── LedgerServiceTest.java
│   ├── LedgerBucketFlowTest.java         cadeia sem órfãos
│   ├── DebtCascadeTest.java              contestação, compensação, baixa
│   ├── GuaranteeVsPayoutTest.java        D+2 com garantia de 30 dias
│   ├── BalanceConcurrencyTest.java       saques simultâneos
│   ├── LockOrderingTest.java             transações cruzadas não geram deadlock
│   ├── NegativeBucketGuardTest.java      recusa no COMMIT
│   ├── CheckpointRebuildTest.java        resumo reconstruído bate com o razão
│   ├── CheckpointConcurrencyTest.java    escrita não confirmada não some
│   ├── ReleaseIdempotencyTest.java       processo rodado duas vezes move uma vez
│   ├── AnticipationTest.java             trunca a favor da plataforma, não toca o afiliado
│   └── IntegrityCheckTest.java           as oito verificações
├── payment/charge/
│   ├── CreateOrderServiceTest.java
│   ├── ReceivableScheduleTest.java       liberação por parcela
│   ├── PartialRefundTest.java            não emite payment.refunded nem fecha o pedido
│   ├── SubscriptionCycleUniqueTest.java  retentativa não cobra o ciclo duas vezes
│   └── IdempotencyTest.java              repetida E simultânea
├── payment/provider/
│   └── ProviderEventReplayTest.java      entrega repetida e simultânea, um efeito só
├── payout/
│   ├── PayoutReversalTest.java           devolve o saldo exatamente uma vez
│   └── PayoutOwnershipTest.java          conta bancária de outro titular é recusada
├── billing/
│   └── VerificationFeeTest.java          conta zerada gera DEBT, nunca AVAILABLE negativo
├── risk/
│   └── ChargebackReversalTest.java       restitui na ordem inversa da cascata
├── fiscal/
│   └── InvoiceQueueTest.java             falha de emissão não bloqueia pagamento
├── contract/
│   └── ApiExampleTest.java               gera os exemplos do doc 2, §4.3
├── architecture/
│   └── PackageDependencyTest.java        ArchUnit
└── security/
    └── CrossAccountAccessTest.java       tenta acessar recurso alheio em toda rota
```

> **`ApiExampleTest` é pequeno e evita um erro caro.** O exemplo numérico do contrato da API na versão 1.1 estava errado: as alocações somavam o valor pago e deixavam o custo do provedor fora da conta, criando R$ 5,78 do nada. Foi escrito à mão e nunca conferido contra o motor, que já existia e já era testado. O teste gera os números do documento a partir do motor e falha se divergirem. Custa vinte linhas. Um cliente integrando por um exemplo errado constrói a conciliação dele errada, e isso só aparece semanas depois, do lado dele.

> **`IntegrityCheckTest` precisa testar nos dois sentidos.** Verificação que só é exercitada com o razão são nunca provou que detecta alguma coisa. Cada uma das oito precisa de um teste que injeta o defeito correspondente e confirma que a verificação acusa. `paysi-testes-v3.0.sql` já faz isso para as cinco de banco; as demais vêm junto com o `IntegrityCheckJob`.

---

## 5. Estrutura do painel

Next.js com App Router. Camada de visão apenas — **nenhuma aritmética monetária**.

```
web-painel/
├── app/
│   ├── (auth)/
│   │   ├── entrar/page.tsx
│   │   ├── criar-conta/page.tsx
│   │   └── recuperar-senha/page.tsx
│   ├── (app)/
│   │   ├── layout.tsx              barra lateral e troca de painel
│   │   ├── inicio/page.tsx
│   │   ├── produtos/
│   │   │   ├── page.tsx
│   │   │   ├── novo/page.tsx       inclui escolha de segmento
│   │   │   └── [id]/page.tsx
│   │   ├── vendas/
│   │   │   ├── page.tsx
│   │   │   └── [id]/page.tsx
│   │   ├── assinaturas/page.tsx
│   │   ├── afiliados/page.tsx
│   │   ├── saldo/
│   │   │   ├── page.tsx            cinco estados, incluindo devedor
│   │   │   └── sacar/page.tsx
│   │   ├── reembolsos/page.tsx     total e parcial, com histórico por cobrança
│   │   ├── notas-fiscais/page.tsx  situação da fila e reenvio
│   │   ├── plano/page.tsx          Transacional ou Escala
│   │   ├── integracoes/page.tsx    chaves de API e webhooks
│   │   ├── aparencia/page.tsx
│   │   └── notificacoes/page.tsx
│   ├── (afiliado)/
│   │   ├── layout.tsx
│   │   ├── inicio/page.tsx
│   │   ├── meus-links/page.tsx
│   │   ├── comissoes/page.tsx
│   │   └── vitrine/
│   │       ├── page.tsx
│   │       └── [slug]/page.tsx
│   └── verificacao/page.tsx
├── components/
│   ├── ui/        Botao, Campo, Cartao, Tabela, Etiqueta, Interruptor, Abas
│   ├── layout/    BarraLateral, Cabecalho, TrocaDePainel
│   └── dominio/   BarraDeDivisao, CartaoDeSaldo, LinhaDeVenda,
│                  GradeDeVitrine, AvisoDeDivida, EtiquetaFiscal
├── lib/
│   ├── api.ts        cliente HTTP com token e correlação
│   ├── formato.ts    formata centavos → "R$ 1.234,56"
│   ├── sessao.ts
│   └── tipos.ts      gerados do contrato da API
├── styles/tokens.css
└── next.config.js
```

> **A regra que a revisão de código verifica.** O arquivo `formato.ts` **apenas formata**. Ele nunca soma, multiplica, calcula percentual nem aplica taxa. Se aparecer uma operação aritmética sobre valor monetário em qualquer arquivo do frontend, o pedido de revisão é rejeitado — a regra migrou para o lugar errado.
>
> Isso vale inclusive para a tela de plano, que é tentadora: "quanto eu economizaria no plano Escala?" é uma conta, e a conta é do servidor.

> **A tela de saldo devedor precisa dizer a verdade desconfortável.** A primeira dívida que a maioria dos vendedores vai ver **não é contestação: é a taxa de verificação**, que nasce em `DEBT` porque a conta ainda não tem saldo. Se a tela disser só "saldo devedor" sem explicar a origem, vira reclamação no primeiro dia. Cada linha precisa mostrar origem, motivo e como será compensada.

---

## 6. Estrutura do checkout

Vite com React. Sem roteador pesado, sem gerenciador de estado global, sem biblioteca de componentes. Uma tela e seus estados.

```
web-checkout/
├── src/
│   ├── main.tsx
│   ├── App.tsx                     lê o slug da URL e decide o estado
│   ├── telas/
│   │   ├── Formulario.tsx          desktop e mobile no mesmo componente
│   │   ├── PixAguardando.tsx       QR, código copiável, contador
│   │   ├── BoletoEmitido.tsx       linha digitável, PDF, vencimento
│   │   ├── Aprovado.tsx
│   │   └── Recusado.tsx            oferece Pix como alternativa
│   ├── componentes/
│   │   ├── ResumoDaOferta.tsx
│   │   ├── SeletorDeMetodo.tsx
│   │   ├── CamposDoComprador.tsx   alterna PF e PJ conforme o segmento
│   │   ├── CamposFiscais.tsx       razão social, endereço, inscrição
│   │   ├── CampoDeCartao.tsx       envolve o componente do provedor
│   │   ├── DesafioTresDS.tsx       moldura da autenticação do portador
│   │   └── RodapeLegal.tsx         identificação do provedor e declaração
│   ├── lib/
│   │   ├── api.ts
│   │   ├── formato.ts
│   │   ├── idempotencia.ts         gera e guarda a chave por sessão
│   │   └── rastreio.ts             lê o parâmetro de afiliado e grava
│   └── estilos/tokens.css
├── index.html
└── vite.config.ts
```

> **Orçamento de desempenho — o número mudou e a medição também.** O RNF-003 passou de 150 KB para 180 KB comprimido, e agora mede o **total transferido até a primeira interação, incluindo o SDK do provedor**. O número anterior media só o código da Paysi e ignorava o SDK, que é carregado na mesma página, não é opcional e não está sob nosso controle.
>
> React com ReactDOM já ocupa cerca de 45 KB comprimidos. Se o SDK do provedor passar de 90 KB, trocar React por Preact no checkout deixa de ser preferência e vira aritmética. **Meça na primeira semana em que houver SDK real, não na véspera do lançamento.**

### 6.1 Envelope do campo de cartão

É o ponto mais sensível do frontend. O componente próprio apenas posiciona e estiliza a moldura; os campos internos são do provedor.

```
// CampoDeCartao.tsx — esboço da responsabilidade
// 1. monta o container
// 2. inicializa o SDK do provedor apontando para esse container
// 3. escuta o evento de token pronto
// 4. entrega apenas o token para o componente pai
//
// nunca: ler valor de input, montar objeto com numero do cartao,
//        enviar dado de cartao para a API da Paysi
```

### 6.2 Personalização e escopo PCI

O RF-029 permite logo, banner e imagem lateral. **As imagens vêm de upload para o armazenamento da Paysi, servidas do domínio da Paysi — nunca de URL informada pelo vendedor.**

A razão é a ameaça AM-20 e o requisito RNF-034: uma URL externa numa página de pagamento conflita com a política de segurança de conteúdo e com o inventário de scripts exigidos pelo PCI DSS 4.0. É uma daquelas restrições que parecem burocráticas até o dia da auditoria.

---

## 7. Os primeiros quarenta dias

Sequência concreta de execução. Cada dia entrega algo verificável. A trilha comercial e jurídica corre em paralelo e não bloqueia nenhum destes itens.

### Semana 1 — fundação e primeira linha

| Dia | Entrega | Como verificar |
|---|---|---|
| 1 | Enviar contato aos três provedores e ao parceiro fiscal, **com PEN-04 e PEN-21 por escrito**. Marcar consulta jurídica e conversa com o contador. Iniciar abertura de CNPJ | Mensagens enviadas |
| 1 | Criar repositório, definir `.gitignore`, subir o docker-compose | `docker compose ps` com quatro serviços |
| 2 | Esqueleto do Spring Boot com Flyway, Actuator e perfil local | Aplicação sobe e `/actuator/health` responde |
| 2 | Integração contínua rodando testes a cada envio | Fluxo verde no repositório |
| 3 | Portar `Money`, `SplitEngine`, `InstallmentSplit` e `RefundSplit`, já prontos | Bateria de testes passando |
| 3–4 | Ligar as três varreduras ao CI: divisão, parcela e reembolso parcial | Cenários verdes; execução abaixo de um minuto |
| 4 | Teste de arquitetura com ArchUnit | Falha se `ledger` importar Spring |
| 5 | Redigir política de conheça seu cliente e lista de produtos proibidos | Documentos prontos para a reunião comercial |

### Semana 2 — esquema

| Dia | Entrega | Como verificar |
|---|---|---|
| 6 | Dividir `paysi-esquema-v3.0.sql` nos 30 arquivos de migração; V000 a V009 | `flyway:migrate` limpo em base nova |
| 7 | V010 a V019: razão, gatilhos, resumo, contas bancárias, saques, risco | `UPDATE` em lançamento lança exceção |
| 8 | V020 a V029: idempotência, fiscal, planos, admin, reembolsos, ajustes, inbox, verificações, semente | `flyway:clean flyway:migrate` recria do zero sem erro |
| 9 | Ligar `paysi-testes-v3.0.sql` ao CI | 70 PASS, 0 FAIL a cada envio |
| 10 | Configurar os dois papéis de banco e provar que o `REVOKE` funciona | `UPDATE` no razão como `paysi_app` é recusado por privilégio **e** por gatilho |

> **A semana 2 encolheu porque o esquema veio pronto, e o dia 9 é o que mais rende.** Com a suíte no CI desde a segunda semana, toda alteração de esquema daqui em diante é conferida contra 70 asserções antes de virar migração. É a diferença entre descobrir a regressão hoje e descobrir no dia 38.

### Semana 3 — o livro-razão

| Dia | Entrega | Como verificar |
|---|---|---|
| 11–12 | `JdbcLedgerRepository` e `LedgerService`: gravar transação com N lançamentos, com bloqueio universal e ordem canônica | Transação que não soma zero é rejeitada; duas contas cruzadas não geram deadlock |
| 13 | `BalanceService`: saldo por soma, com os cinco estados | Consulta devolve os cinco valores |
| 14 | Bloqueio consultivo e teste de concorrência | Cem saques simultâneos, nenhum saldo negativo |
| 15 | `GuaranteeReleaseService` e `ReleaseService`, lendo `ledger_release_schedule` | Venda simulada percorre garantia, pendente, disponível e reserva; processo rodado duas vezes move o dinheiro uma vez |

### Semana 4 — dívida, resumo e integridade

| Dia | Entrega | Como verificar |
|---|---|---|
| 16–17 | `DebtService`: cascata, compensação na saída da garantia, baixa aprovada com segregação | Contestação sem reserva gera dívida; venda seguinte quita; quem pede não aprova |
| 18 | `IntegrityCheckJob` com as oito verificações e alerta | Cada uma acusa o defeito correspondente injetado |
| 19 | `CheckpointService`: consolidação sob bloqueio e reconstrução | Escrita concorrente não confirmada não desaparece do saldo |
| 20 | Regra `max(recebimento, garantia)` implementada e testada | D+2 com garantia de 30 dias não libera antes de 30 dias |

> **Marco do dia 20.** Neste ponto você registra uma venda no razão, vê os cinco saldos, prova que dois saques simultâneos não furam o saldo, que uma contestação sem reserva vira dívida em vez de saldo negativo, e que o resumo de saldo não perde lançamento sob concorrência. **É a peça mais perigosa do sistema, resolvida antes de existir tela** — e é a parte que as versões anteriores dos documentos tinham errado no papel.

### Semana 5 — provedor falso, inbox e máquinas de estado

| Dia | Entrega | Como verificar |
|---|---|---|
| 21 | Interface `PaymentProvider` e `FakeProvider` | Cobrança falsa devolve resultado plausível |
| 22 | **Inbox de eventos do provedor**, com registro na mesma transação do efeito e reprocessamento por estado | Mesmo evento entregue duas vezes e simultaneamente gera um efeito só |
| 23 | Interface `InvoiceIssuer` e `FakeIssuer`; máquinas de estado de pedido, cobrança e assinatura | Emissão falsa devolve número e link; transição inválida lança exceção |
| 24 | Filtro de idempotência com Redis usando `SET NX` e espelho durável | Requisição repetida e duas simultâneas devolvem o mesmo resultado |
| 25 | `AdvisoryLock` com espaço de nomes e ShedLock no agendador | Duas instâncias não rodam a mesma tarefa |

### Semana 6 — a venda simulada de ponta a ponta

| Dia | Entrega | Como verificar |
|---|---|---|
| 26–27 | `CreateOrderService`: as **duas** transações — criação sem razão, confirmação com razão | Pix criado e abandonado não gera nenhum lançamento |
| 28 | Cronograma de recebíveis, rateio por maior resto e liberação por parcela | 12x gera 12 entradas em pendente com datas distintas e soma exata |
| 29 | `ApiExampleTest`: exemplos do contrato gerados pelo motor | Números do documento 2, §4.3 conferem |
| 30 | Outbox: gravação na mesma transação e publicador com `SKIP LOCKED` | Queda entre gravar e publicar não perde evento; duas instâncias não duplicam |

### Semana 7 — reversões

| Dia | Entrega | Como verificar |
|---|---|---|
| 31–32 | `RefundService`: total e parcial, com truncagem cumulativa e entidade `refunds` | N parciais somam a alocação original, sem parte negativa; parcial não emite `payment.refunded` |
| 33–34 | Contestação: cascata, tarifa em `SYS_ACQUIRER_FEE`, e reversão na ordem inversa | Reserva insuficiente aciona a cascata até `DEBT`; `SYS_CLEARING` fecha em zero para a venda |
| 35 | Fila fiscal com retentativa, sem bloquear pagamento, com emissor amarrado ao vendedor | Emissor falso falhando não impede a confirmação; nota em nome de outra conta é recusada |

### Semana 8 — fechamento do núcleo

| Dia | Entrega | Como verificar |
|---|---|---|
| 36–37 | Cobertura: `ledger` e `payment.split` em 100% de ramos | Relatório anexado |
| 38 | Revisão da lista de verificação de código nos 24 itens da seção 8.3 | Nenhuma pendência aberta |
| 39–40 | Folga deliberada: bug, ajuste, o que atrasou | — |

> **Marco do dia 40 — corresponde ao M2 do plano.** Venda, divisão, cadeia completa de buckets, liberação, reserva, reembolso total e parcial, contestação com cascata de dívida e reversão, inbox à prova de reentrega e oito verificações de integridade funcionando de ponta a ponta — **sem contrato com provedor nenhum.** Se o comercial demorar, você não ficou parado.
>
> Os dias 39 e 40 são folga de propósito. Um plano de oito semanas sem folga nenhuma é um plano que já está atrasado na terceira semana.

---

## 8. Convenções

### 8.1 Nomenclatura

| Elemento | Convenção | Exemplo |
|---|---|---|
| Classe de domínio | Substantivo, sem sufixo | `Affiliation` |
| Serviço de aplicação | Sufixo `Service` | `PayoutService` |
| Porta | Sufixo por natureza | `AccountRepository`, `InvoiceIssuer` |
| Adaptador | Prefixo pela tecnologia | `JdbcLedgerRepository` |
| Tabela | Plural, minúsculas, sublinhado | `ledger_entries` |
| Visão de verificação | Prefixo `v_check_` | `v_check_positive_debt` |
| Coluna monetária | Sempre com sufixo `_cents` | `amount_cents` |
| Coluna percentual | Sempre com sufixo `_bps` | `commission_bps` |
| Coluna cifrada | Sempre com sufixo `_enc` | `secret_enc` |
| Rota da API | Plural, minúsculas | `/v1/subscriptions` |
| Componente de tela | Português, em maiúscula inicial | `BarraDeDivisao` |

O sufixo `_cents` não é estética: é o que impede alguém de somar reais com centavos sem perceber. O `_enc` cumpre o mesmo papel para segredo: impede que alguém devolva a coluna num JSON sem notar. E o prefixo `v_check_` faz a lista das oito verificações ser um `\dv v_check_*`, não um item de documentação que envelhece.

### 8.2 Versionamento

| Item | Regra |
|---|---|
| Ramo principal | `main`, sempre com testes verdes |
| Ramo de trabalho | `feat/2.5-livro-razao` — prefixo mais identificador da estrutura analítica |
| Mensagem de commit | `tipo(escopo): descrição`, em português |
| Migração aplicada | Nunca é editada. Corrige-se com nova migração |

### 8.3 Lista de verificação de revisão

Toda alteração é conferida contra estes itens antes de entrar no ramo principal.

| # | Item | Garantido por |
|---|---|---|
| 1 | Nenhum `double` ou `float` em valor monetário | Verificação estática |
| 2 | Nenhuma aritmética monetária no frontend | Revisão |
| 3 | Nenhum valor vindo do corpo da requisição define preço, taxa ou comissão | Revisão |
| 4 | Toda consulta por identificador verifica posse antes de responder | Revisão + `CrossAccountAccessTest` |
| 5 | Endpoint que movimenta dinheiro exige chave de idempotência | Revisão |
| 6 | Escrita no razão acontece dentro de transação com bloqueio da conta, **mesmo quando não valida saldo** | Revisão + ArchUnit |
| 7 | Bloqueio de mais de uma conta adquirido em ordem canônica, pelo método único | **ArchUnit** |
| 8 | Evento do outbox gravado na mesma transação do fato | Revisão |
| 9 | Evento recebido do provedor registrado em `provider_events` na mesma transação do efeito | Revisão |
| 10 | Nenhum lançamento no razão fora do fato gerador confirmado | Revisão |
| 11 | Crédito de venda vai para `GUARANTEE`, nunca direto para `PENDING` | Revisão |
| 12 | Nenhum bucket além de `DEBT` pode terminar negativo | **Gatilho de sinal** |
| 13 | `DEBT` nunca termina positivo | **Gatilho de sinal** |
| 14 | Lançamento com `release_at` tem linha em `ledger_release_schedule` | **Gatilho** |
| 15 | Tarifa de terceiro creditada na conta de destino própria, nunca em `SYS_CLEARING` | Revisão + verificação nº 4 |
| 16 | Instrução de divisão ao provedor não contém valor estimado de custo | Revisão |
| 17 | Segredo de webhook lido do endpoint, nunca de configuração global | Revisão |
| 18 | Emissão fiscal e demais integrações não essenciais fora do caminho crítico | Revisão |
| 19 | Consulta de outbox e de fila usa `SKIP LOCKED` | Revisão |
| 20 | Nenhum dado de cartão, documento completo, senha ou token em log | Revisão |
| 21 | Migração reversível e testada, e `paysi-testes-v3.0.sql` passando | **CI** |
| 22 | Teste cobrindo o caminho de erro, não só o de sucesso | Revisão |
| 23 | Nenhuma coluna nova duplica fato que já existe em outra tabela **novo v3.0** | Revisão |
| 24 | Ponto de truncamento novo vem com varredura que prova soma exata **e** ausência de parte negativa **novo v3.0** | Revisão |

> **Sete itens desta lista já não dependem de ninguém lembrar.** Os itens 7, 12, 13, 14 e 21 são impostos por gatilho, ArchUnit ou CI. Isso é deliberado: **um item de lista de revisão é uma promessa de que alguém vai olhar; uma restrição é o banco recusando.** Sempre que um item puder migrar da primeira coluna para a terceira, ele deve migrar.
>
> O item 23 nasceu de três defeitos da mesma família: `accounts.plan` duplicando `platform_subscriptions`, `orders.refunded_cents` duplicando `charges.refunded_cents`, e `disputes.kind='REFUND'` duplicando `refunds`. Em todos os três, as duas cópias podiam divergir e nenhuma era autoritativa.
>
> O item 24 nasceu do reembolso parcial, que era o **terceiro** ponto de truncamento do sistema e chegou à revisão com zero cobertura. A primeira condição sozinha não pega o defeito: a regra ingênua fecha a soma e mesmo assim exige alocação negativa.

---

## 9. Comandos do dia a dia

```bash
# infraestrutura
docker compose -f infra/docker-compose.yml up -d
docker compose -f infra/docker-compose.yml down -v   # zera o banco

# backend
cd backend
./mvnw spring-boot:run
./mvnw test
./mvnw test -Dtest=RoundingSweepTest                 # só a varredura de divisão
./mvnw test -Dtest='*SplitTest'                      # parcela e reembolso
./mvnw test -Dtest='Ledger*Test,Debt*Test'           # só o razão
./mvnw flyway:migrate
./mvnw flyway:clean flyway:migrate                   # recria do zero

# testes de banco, independentes da aplicação
psql -d paysi -f docs/paysi-testes-v3.0.sql          # 70 PASS, 0 FAIL

# painel e checkout
cd web-painel   && npm run dev                       # localhost:3000
cd web-checkout && npm run dev                       # localhost:5173
npm run build && npx vite-bundle-visualizer          # confere o peso
```

---

## 10. Onde encontrar cada coisa

| Preciso de… | Está em |
|---|---|
| O que o sistema deve fazer | Documento 1, requisitos RF e RNF |
| Regras de preço, planos, divisão e prazos | Documento 1, §5 |
| A regra de `max(recebimento, garantia)` | Documento 1, §5.4 |
| Faixas de liberação de limite | Documento 1, §5.5 |
| As 25 pendências | Documento 1, §6 |
| O que estava errado antes e onde foi corrigido | Documento 1, §7 |
| Por que a arquitetura é assim | Documento 2, ADR-01 a ADR-15 |
| Esquema de banco e livro-razão | Documento 2, §3 · e `paysi-esquema-v3.0.sql` |
| Os lançamentos do razão, conferidos | Documento 2, §3.6 |
| As oito verificações de integridade | Documento 2, §3.6 |
| Contrato da API e eventos | Documento 2, §4 |
| Ameaças e controles | Documento 3, §1 |
| Obrigações de LGPD | Documento 3, §3.3 e §5.4 |
| Enquadramento regulatório e BaaS | Documento 3, §4 |
| Quem absorve cada perda | Documento 3, §6 |
| O que verificar antes de lançar | Documento 3, §7 |
| Ordem, prazo, custo, tributos e riscos | Documento 4 |
| As três rotas de escopo e prazo | Documento 4, §4.3 |
| Como começar e onde criar cada arquivo | Este documento |
| O código dos pontos delicados | Documento 6 |
| O que foi testado e como | `00-relatorio-de-verificacao.md` |
| Telas do sistema | `docs/paysi-sistema.html` — 27 telas |
| Telas do aplicativo | `docs/paysi-mobile.html` — 13 telas |

### 10.1 O que as telas não cobrem

Construir a partir dos requisitos, sem prévia visual, continua sendo aceitável para tela interna de uso raro — mas não para as quatro primeiras da lista, que o usuário vê.

| Tela ausente | Requisitos | Prioridade |
|---|---|---|
| Campos fiscais de comprador PJ no checkout | RF-093 | Alta — é caminho de compra |
| Boleto emitido: linha digitável, PDF, vencimento | RF-097 | Alta — é caminho de compra |
| Saldo devedor no painel, com origem e memória de cálculo | RF-103, RF-069 | **Alta — a primeira dívida da maioria é a taxa de verificação** |
| Reembolso parcial: histórico por cobrança, valor restante | RF-105 | Alta — é comunicação sensível |
| Notas fiscais: fila, situação, reenvio | RF-094 a RF-096 | Média |
| Plano comercial e calculadora de indiferença | RF-101, RF-102 | Média |
| Integrações: chaves de API e webhooks com rotação | RF-109 | Média |
| Cadastro proibido e fluxo de suspensão | RF-080, RF-088 | Baixa — interna |
| Painel administrativo, conciliação e aprovação de ajuste | RF-087, RF-089, RF-126 | Baixa — interna |
| Pedidos de titular sob a LGPD | RF-115 | Baixa — interna |

---

## 11. Se algo der errado

| Sintoma | Primeiro lugar a olhar |
|---|---|
| Saldo não bate com o extrato do provedor | Verificações de integridade e relatório de conciliação. **Congele liberações antes de investigar** |
| Transação do razão não soma zero | Incidente crítico. Documento 3, §5.2, na ordem exata |
| Bucket com saldo negativo fora de `DEBT` | Não deveria ser possível: o gatilho de sinal recusa. Se aconteceu, houve escrita **por fora da aplicação** — investigue privilégios antes do código |
| `DEBT` com saldo positivo | Idem. Compensação aplicou mais do que devia por caminho não coberto pelo gatilho |
| Saldo do painel diferente do extrato | `ledger_checkpoints` desatualizado. **Registre a causa antes de reconstruir** — a reconstrução conserta o número e apaga a pista |
| Dinheiro parado em garantia depois do prazo | Verificação nº 8: existe linha em `ledger_release_schedule`? Está com `released_at` nulo e `release_at` vencido? |
| Mesmo dinheiro movido mais de uma vez | O processo marcou `released_at` na mesma transação dos lançamentos? |
| Cobrança duplicada | Filtro de idempotência: chave chegou? foi gravada com `SET NX`? escopo correto? |
| Vendedor creditado duas vezes pela mesma venda | Inbox: o evento foi registrado antes de processar? A duplicata devolveu `200` sem olhar o `status`? |
| Evento não chegou ao vendedor | Outbox: foi gravado? foi publicado? qual código de resposta? o segredo é o do endpoint? |
| Evento chegou duas vezes ao vendedor | Publicador sem `SKIP LOCKED`, ou mais de uma instância sem ShedLock |
| Divisão com centavo divergente | A instrução ao provedor levou valor de plataforma calculado por estimativa? Deve levar só vendedor e afiliado |
| Reembolso parcial recusado pelo banco | Acumulador no lugar errado. O estado de reembolso vive na **cobrança**, não no pedido |
| Nota fiscal não saiu | Fila fiscal: qual erro do parceiro? Perfil fiscal validado? Município fora da cobertura? |
| Nota fiscal recusada na gravação | O emissor precisa ser o vendedor do produto daquela cobrança |
| Pagamento travado esperando nota | Regressão grave: a emissão voltou ao caminho crítico |
| Saldo liberado antes da garantia | O `release_at` do lançamento em `GUARANTEE` foi calculado a partir do pagamento? |
| Deadlock intermitente no razão | Alguém adquiriu bloqueio fora do `AdvisoryLock`, ou ordenou por UUID em vez da chave de bloqueio |
| Saque para conta errada | Não deveria ser possível: o gatilho de titularidade recusa. Verifique se ele existe no ambiente |
| Agendamento rodou duas vezes | ShedLock configurado? Há mais de uma instância ativa? |

---

*Guia de implementação. Estruturas de pasta e trechos de configuração são referência inicial e podem evoluir durante o desenvolvimento sem alterar as decisões registradas nos documentos 1 a 4. Nomes de arquivo em português foram adotados nas camadas de apresentação para reduzir tradução mental; o domínio mantém termos técnicos consagrados.*
