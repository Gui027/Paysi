# Cenários de carga — BE-15.1

Cobre "cenários de carga" do card BE-15.1, mapeando os alvos do documento 1
(`RNF-001` a `RNF-005`) para cenários executáveis.

## 1. O que foi executado vs. documentado

Este ambiente de desenvolvimento não tem acesso de rede para instalar k6,
Gatling ou JMeter (nenhum dos três já existia no repositório — verificado por
busca antes de escrever este documento). Em vez de pular o critério, este
documento entrega dois níveis:

1. **Cenários e metas**, formais o bastante para virar um script k6 real assim
   que a ferramenta puder ser instalada em CI/staging (seção 2).
2. **Um script executável hoje** (`infra/load-test/checkout-load-test.sh`),
   usando apenas `bash` e `curl` — presentes em qualquer runner Linux do GitHub
   Actions e em qualquer máquina de desenvolvimento — para dar um sinal
   imediato de comportamento sob concorrência sem depender de instalação nova.
   Ele não foi executado neste ambiente contra uma instância viva do backend:
   subir a aplicação completa (Postgres com as 53 migrações, Redis, RabbitMQ)
   e mantê-la de pé para um teste de carga excede o orçamento de memória desta
   sessão (o mesmo motivo pelo qual os testes de integração do Maven já
   precisaram rodar com heap reduzido nesta mesma sessão). O script está
   pronto para ser rodado contra `docker compose up` local ou contra
   staging.

## 2. Cenários e metas

| Cenário | Endpoint | Meta (documento 1) | Método sugerido |
|---|---|---|---|
| Checkout interativo | Carregamento da SPA de checkout | RNF-001: p95 < 1,5 s em 4G | Lighthouse/WebPageTest, não k6 — é métrica de front-end |
| Criação de cobrança | `POST /v1/checkout/{slug}/orders` | RNF-002: p95 < 800 ms | k6 com rampa de 1 a 50 VUs por 2 min |
| Consulta de saldo | `GET /v1/balance` | RNF-004: p95 < 200 ms com 5 milhões de lançamentos | k6 contra banco pré-populado (fixture de carga, fora deste PR) |
| Régua de cobrança de assinatura | Job de cobrança recorrente | RNF-005: 10.000 cobranças/hora | Teste de throughput do job, não de API — cronometrar `SubscriptionBillingJob` processando um lote sintético |

### Cenário de referência para k6 (a instalar quando houver rede)

```js
// infra/load-test/checkout.k6.js (esqueleto — não executado neste PR)
import http from 'k6/http';
import { check } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 10 },
    { duration: '1m', target: 50 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<800'], // RNF-002
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const body = JSON.stringify({
    buyer: { name: 'Carga k6', email: `k6-${__VU}-${__ITER}@example.com`, personType: 'PF', taxId: '52998224725' },
    method: 'PIX', installments: 1, termsHash: 'hash-de-carga',
  });
  const res = http.post(`${__ENV.BASE_URL}/v1/checkout/curso-de-teste/orders`, body, {
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': `k6-${__VU}-${__ITER}-${Date.now()}` },
  });
  check(res, { 'status é 201 ou 200': (r) => r.status === 201 || r.status === 200 });
}
```

Este esqueleto documenta o cenário formalmente; não foi executado (sem
runtime k6 disponível). Ele é o ponto de partida assim que a ferramenta puder
ser instalada — inclusive em CI, como um job adicional opcional que não bloqueia
o pipeline principal (`Maven`, `ArchUnit`, `70 SQL`, `lint/build` continuam sendo
os gates obrigatórios; carga é observação, não gate, até que metas estejam
validadas em staging).

## 3. Script disponível hoje

`infra/load-test/checkout-load-test.sh` — dispara requisições concorrentes de
criação de pedido via `curl`, cada uma com `Idempotency-Key` própria (para não
testar o caminho de replay por engano), e reporta contagem de sucesso (2xx) e
p95 aproximado de latência.

```bash
BASE_URL=http://localhost:8080 CONCURRENCY=20 REQUESTS=200 \
  infra/load-test/checkout-load-test.sh
```

Pré-requisito: a oferta `curso-de-teste` (ou o slug passado em `SLUG=`)
precisa existir e estar publicada no banco de destino — o script não cria
dados de catálogo, só carrega o endpoint de checkout.

## 4. Limite conhecido

RNF-004 (saldo com 5 milhões de lançamentos) exige uma fixture de dados de
carga que não existe hoje no repositório. Criá-la é trabalho de fixture de
teste, não de infraestrutura de observabilidade, e fica fora do escopo deste
card por ser um esforço de geração de massa de dados sintética separado —
registrado aqui como pendência explícita, não como item silenciosamente
ignorado.
