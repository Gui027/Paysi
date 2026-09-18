# Alertas operacionais — BE-15.1

## Decisão: o que são "os oito alertas"

O card BE-15.1 pede que "oito alertas cheguem ao canal", mas nenhum dos
documentos (`01-requisitos.md`, `03-seguranca-e-conformidade.md`) enumera uma
lista de exatamente oito alertas sob esse nome. Em vez disso, a busca por
"alerta" nesses documentos encontra pelo menos nove condições distintas
(RF-039, RF-076, RF-077, RF-106, RF-113, RNF-016, RNF-030, RNF-043 e o alerta
de reembolso residual do documento 5). Implementar as nove teria exigido tocar
serviços de negócio já prontos e testados em cards anteriores (cálculo de taxa
de contestação por vendedor, taxa de aprovação por hora, etc.) — o tipo de
mudança espalhada que o próprio card veda: *"Não aproveitar o cartão para
refatoração ampla"*.

A âncora textual mais literal para "oito alertas" é o **checklist #12** do
documento 3: *"As oito verificações de integridade rodando diariamente, com
alerta, e cada uma testada com defeito injetado"* — junto com **RNF-043**:
*"As oito verificações de integridade rodam diariamente com alerta; resultado
não vazio em qualquer uma é incidente de severidade máxima."* `LedgerIntegrityMonitor`
já testa exatamente oito views (`VIEWS` tem 8 entradas) e já é o único ponto do
código onde "alerta" e "oito" coincidem literalmente. Por isso este PR adota
essa leitura: **os oito alertas são as oito verificações de integridade do
razão, cada uma capaz de disparar seu próprio alerta para o canal**, não um
alerta agregado.

## O que foi implementado

- `com.paysi.observability.alert` — infraestrutura genérica e reutilizável de
  alerta: `AlertService` grava evidência durável em `ops_alerts` (migração
  `V052__ops_alerts.sql`) **antes** de tentar o canal externo, e só depois
  chama `AlertChannel`. Uma falha do canal (Slack fora do ar) nunca some com o
  alerta — a evidência já está gravada.
- `SlackAlertChannel` — entrega por webhook de entrada do Slack, configurável
  por `paysi.alerts.slack-webhook-url`. Sem a URL configurada (caso do
  ambiente local e de CI), o canal cai para modo somente-log, e a evidência em
  `ops_alerts` continua sendo gravada de qualquer forma.
- `LedgerIntegrityMonitor` passou a chamar `AlertService.raise(...)` para cada
  uma das oito views violadas, com severidade `CRITICAL` — mantendo o
  `LOG.error` que já existia (nenhum comportamento anterior foi removido).
- Métrica `paysi.alerts.raised` (Micrometer `Counter`, tags `type` e
  `severity`) e `paysi.ledger.integrity.checks` (tag `view`), expostas em
  `/actuator/metrics` (já habilitado antes deste card).

## O que ficou como gap documentado

As demais condições de alerta identificadas nos documentos (RF-039, RF-076,
RF-077, RF-106, RF-113, RNF-016, RNF-030) **não foram religadas ao novo canal
neste PR**. Cada uma já tem sua regra de negócio implementada em cards
anteriores (limites de contestação, reembolso, etc.); o que falta é só o
último passo — trocar o `LOG.warn`/`LOG.error` existente (ou adicionar um,
onde nem isso existe) por uma chamada a `AlertService.raise(...)`, agora que a
infraestrutura existe. É uma mudança pequena e mecânica por serviço, mas
tocaria sete módulos de negócio diferentes numa única PR — o oposto do que o
card pede. Fica registrado aqui como o próximo passo natural, card por card,
não como item esquecido.

## Como estender

```java
alertService.raise("SELLER_CHARGEBACK_RATE_ABOVE_THRESHOLD", "HIGH",
        Map.of("sellerId", sellerId, "rateBps", rateBps));
```

`AlertService` é `@Service` do Spring, injetável em qualquer camada `app` da
mesma forma que `OutboxService` já é usado para eventos de webhook.
