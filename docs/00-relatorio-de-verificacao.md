# Paysi — Relatório de verificação e índice do conjunto

**21 de agosto de 2026 · interno · Quarta passada de revisão**

---

## O que você pediu e o que dá para prometer

Você pediu documentos únicos e organizados, tudo atualizado, testado, e a garantia de que nunca existirão erros no sistema.

As três primeiras coisas estão feitas. A quarta ninguém entrega, e prometer isso seria o primeiro erro do projeto. O que dá para fazer — e foi feito — é outra coisa, mais útil:

**Reduzir a classe de erro que passa despercebido.** Um sistema financeiro não quebra por causa do erro que derruba a aplicação; quebra por causa do erro que produz número certo na tela e errado no extrato. A cada passada de revisão, mais defeitos deixaram de ser "achado do dia seguinte" e passaram a ser **impossíveis de gravar** — recusados pelo banco, no momento da escrita.

Onde isso não foi possível, existe verificação diária. Onde nem isso, existe pendência escrita com responsável.

### O perímetro, sem retoque

| | Situação |
|---|---|
| **Verificado por execução** | Esquema aplicado num PostgreSQL 16.15 real; 70 asserções de comportamento passando; 4 testes de concorrência com sessões simultâneas; 28,6 milhões de cenários aritméticos |
| **Verificado por leitura** | Consistência cruzada entre os cinco documentos: identificadores RF/RNF/AM/ADR/PEN, contagens, somas de esforço, tabelas de preço |
| **Não verificado** | Checkout, painel, integração real com provedor e parceiro fiscal — não existem ainda. O Java de referência não compila num projeto Spring porque não há projeto Spring |
| **Fora do alcance de qualquer teste** | As 25 pendências. PEN-10 e PEN-21 podem invalidar o modelo inteiro com o esquema perfeito |

---

## Como foi testado

Não é revisão de leitura. Foi montado um ambiente real e cada afirmação dos documentos foi exercitada contra ele.

### Banco de dados

PostgreSQL 16.15, base criada do zero, conjunto completo de migrações aplicado com `ON_ERROR_STOP`. Depois, `paysi-testes-v3.0.sql`: **70 asserções, zero falhas**, cobrindo

- os gatilhos de catálogo, afiliação, conta bancária e cupom;
- a cadeia completa do razão — venda, saída da garantia, liberação, reserva, reembolso total e parcial, contestação com cascata até `DEBT`, compensação, baixa de incobrável, saque, reversão de saque, taxa de verificação, antecipação e contestação ganha na defesa;
- as oito verificações de integridade, cada uma testada nos dois sentidos: vazia quando o razão está são, **e não vazia quando um defeito é injetado de propósito**. Verificação que nunca acusou nada não é verificação, é decoração;
- a imutabilidade do razão e da trilha de auditoria, inclusive contra o dono das tabelas.

### Concorrência

Quatro cenários com sessões simultâneas de verdade, não simulação:

| Cenário | Resultado |
|---|---|
| Consolidação do resumo com escrita concorrente não confirmada | **Correção confirmada.** Sob bloqueio: 800. Sem bloqueio: exibe 700, verdadeiro 800 — o defeito B2 reproduzido, um real desaparecendo |
| Aquisição de bloqueio fora de ordem canônica | **Defeito B4 reproduzido**: `ERROR: deadlock detected` |
| Dois saques simultâneos de R$ 100,00 sobre saldo de R$ 100,00 | Bloqueio consultivo serializa; um passa, um falha; saldo final zero |
| Saque acima do saldo | Recusado no `COMMIT` pelo gatilho novo, não descoberto no dia seguinte |

### Aritmética

Implementação de referência em Java, executada de verdade:

| Varredura | Cenários | Resultado |
|---|---|---|
| Divisão vendedor/afiliado/plataforma | 21.945.110 | Invariante exata em todos; nenhuma alocação negativa |
| Rateio por parcela (maior resto) | 2.400.012 | Soma exata; desvio máximo de 1 centavo entre parcelas |
| Reembolso parcial (truncagem cumulativa) | 4.275.150 | Soma exata; nenhuma parte negativa em nenhuma fatia |

Todos os exemplos numéricos dos cinco documentos foram reconferidos contra o motor: R$ 100,00 do §5.3, R$ 177,00 do contrato da API, taxas efetivas de R$ 20,00 / R$ 50,00 / R$ 197,00, o rateio de 8201 em 12 parcelas, a antecipação D+7 e a reserva de 8%. **Todos conferem.**

E a regra ingênua de reembolso parcial foi reproduzida para confirmar que ela realmente quebra: dez fatias de R$ 9,99 deixam a plataforma devendo **−19 centavos** na fatia final. É exatamente o número que a revisão anterior afirmava, agora medido em vez de afirmado.

> **Duas correções na própria varredura.** A faixa documentada começava em R$ 20,00, o piso comercial. Mas `orders.paid_cents` desce legitimamente até R$ 5,00 com cupom (FIX-A2) — a faixa passou a começar em 500 centavos, e é por isso que o número de cenários subiu de 21.780.110 para 21.945.110. Uma varredura que não cobre a faixa que o banco aceita não cobre o sistema.
>
> Nesse extremo apareceu um resultado que não é defeito, mas precisa ser decidido: em venda de R$ 5,00 com comissão de 50% e cartão em 12x, **o vendedor fica com 16 centavos**. É legal, fecha, e é comercialmente indefensável. A recomendação está no documento 1, RF-027.

---

## Os treze defeitos desta passada

Todos reproduzidos em banco **antes** da correção e verificados **depois**. Sete deles são da categoria cara: o sistema continua funcionando e nada acusa.

| # | Defeito | O que acontecia | Correção |
|---|---|---|---|
| **D01** | `REVOKE ... FROM paysi_app` sobre papel que nenhuma migração cria | Migração aborta em ambiente limpo. Pior: o `.env` conecta como dono das tabelas, e **dono ignora `REVOKE`** — a proteção nunca valeu nada | `V000__roles.sql`; separação entre quem migra e quem atende requisição |
| **D02** | `accounts.email` era `UNIQUE` global | Quem encerrava a conta nunca mais se recadastrava com o mesmo e-mail | Índice parcial `WHERE status <> 'CLOSED'`, igual ao de `tax_id` |
| **D03** | Gatilho de imutabilidade lia `orders.status` | Deixou de funcionar quando o estado de reembolso saiu do pedido | Passa a olhar cobrança confirmada |
| **D04** | Acumulador de reembolso no **pedido** | Assinatura tem N cobranças e um pedido. Reembolsar dois ciclos de R$ 100 acumula 20000 contra teto de 10000 → **reembolso legítimo recusado pelo banco** | Reembolso e contestação vivem na cobrança; estado do pedido vira a visão `v_order_status` |
| **D05** | Rateio por parcela sem amarração | Parcela aceitava parte do vendedor maior que a própria parcela | `CHECK` na tabela + verificação nº 6 |
| **D06** | `release_at` aceito em lançamento de débito | Agendava a liberação de dinheiro que já saiu | `CHECK (release_at IS NULL OR direction = 'CREDIT')` |
| **D07** | Popular `ledger_release_schedule` era item de lista de revisão | Esquecer significa dinheiro que **nunca sai da garantia**, em silêncio, para sempre | Gatilho popula a partir do próprio lançamento + verificação nº 8 |
| **D08** | Não negatividade era "consulta diária" | A consulta descobre amanhã que o saldo ficou negativo ontem. O saque já saiu | Gatilho de restrição **deferido**: recusa no `COMMIT`, independente da ordem dos lançamentos |
| **D09** | Nada ligava `payouts.account_id` a `bank_accounts.account_id` | **Saque da conta A para a conta bancária de B era aceito.** É a classe AM-12 no caminho onde ela custa mais caro | Gatilho de titularidade, verificação e arquivamento |
| **D10** | `disputes.kind` ainda aceitava `REFUND` | O mesmo reembolso gravado em duas entidades, com dois estados, nenhuma autoritativa | Disputa é contestação; reembolso vive em `refunds` |
| **D11** | `invoices.issuer_id` era uuid livre | NFS-e emitida em nome do afiliado. Quem responde pelo ISS é o vendedor (PEN-19) | Gatilho amarra o emissor ao vendedor do produto |
| **D12** | Nada garantia linha em `platform_subscriptions` | FIX-C4 elegeu a tabela como fonte única do plano e deixou 7 de 8 contas sem plano. Conta sem plano é cobrança sem tabela de preço | Plano padrão nasce com a conta |
| **D13** | `charges.refunded_cents` sem amarração com `refunds` | Reembolsar acima do valor da venda sem nenhuma restrição perceber | Verificação nº 7 |

### E quatro defeitos no código de referência

Não estão em banco, então não dá para gatilho. Estão corrigidos no documento 06.

- `pg_advisory_xact_lock` devolve `void`; o código mapeava para `Boolean`. Confirmado no catálogo do PostgreSQL.
- A ordenação anti-deadlock ordenava por `UUID`, mas o bloqueio é adquirido sobre `hashtext(uuid)`. **A ordem precisa ser a da chave do bloqueio**, não a do identificador.
- O `inbox` do provedor grava fora da transação do efeito e responde `200` a qualquer duplicata, sem olhar o estado. Um evento gravado e não processado fica perdido para sempre, porque a retentativa do provedor bate na chave e recebe `200`.
- O `write()` do razão validava saldo só em débito. Crédito em `DEBT` acima da dívida passava.

---

## O que mudou nas contagens

| Item | v2.0 | v2.1 | Agora |
|---|---|---|---|
| Verificações de integridade | 3 | 5 | **8** |
| Itens bloqueantes antes do lançamento | 29 | 33 | **37** |
| Pendências | 20 | 23 | **25** |
| Esforço de desenvolvimento (dias úteis) | 165 | 176 | **176** |
| Prazo, Rota A | 40 sem. | 42 sem. | **43 sem.** |
| Tabelas no esquema | 28 | 33 | **33** + 4 visões novas |

O esforço não subiu apesar de mais dois dias de gatilhos e verificações, porque o pacote 2.4 (esquema inicial) caiu de 4 para 2 dias: **o esquema vai junto, aplicado e testado**. As 43 semanas contra as 42 anteriores são correção de arredondamento, não escopo novo — 176 dias úteis são 42,5 semanas com a folga de 15%, e 42,5 arredonda para cima quando se está planejando caixa.

---

## Os documentos

| Arquivo | O que é |
|---|---|
| `01-requisitos.md` | O que o sistema deve fazer. RF, RNF, regras de negócio, preços, pendências |
| `02-arquitetura-e-dados.md` | Por que é assim. ADRs, modelo de dados, o razão inteiro, contrato da API |
| `03-seguranca-e-conformidade.md` | Ameaças, controles, PCI, LGPD, regulação, quem paga o quê, lista de lançamento |
| `04-plano-de-projeto.md` | Ordem, prazo, custo, tributos, riscos, marcos |
| `05-guia-de-implementacao.md` | Como começar. Repositório, ambiente, os primeiros quarenta dias |
| `06-referencia-de-codigo.md` | Os sete pontos onde a prosa não basta, com o código corrigido |
| `paysi-esquema-v3.0.sql` | Migrações V000 a V029, aplicadas e testadas. Divida antes de rodar |
| `paysi-testes-v3.0.sql` | As 70 asserções. Rode a cada mudança de esquema |
| `PaysiSweep.java` | Motor de divisão, rateio e reembolso, com as três varreduras |

Cada documento substitui integralmente a versão anterior e incorpora `paysi-correcoes-v2.1.md`, `paysi-lancamentos-completos.md` e `paysi-referencia-critica.md`, que deixam de ser necessários.

### Como rodar o que foi testado

```bash
createdb paysi
psql -d paysi -v ON_ERROR_STOP=1 -f paysi-esquema-v3.0.sql
psql -d paysi -f paysi-testes-v3.0.sql        # espera 70 PASS, 0 FAIL
javac PaysiSweep.java && java PaysiSweep      # espera "TODAS AS VARREDURAS PASSARAM"
```

---

## O que continua aberto, e é onde mora o risco

Nenhuma das três coisas abaixo se resolve com código. As três precisam começar esta semana.

1. **PEN-10** — o enquadramento como facilitadora dispensa autorização própria? É a única pendência que justifica parar o projeto inteiro.
2. **PEN-21** — o provedor permite reter e debitar saldo dentro da subconta nominal do vendedor? Se não permitir, `GUARANTEE`, `PENDING` e `RESERVE` não têm lastro: o razão descreveria um estado que o dinheiro real não tem, com o esquema perfeito e testado.
3. **PEN-04** — a contestação pode ser debitada do saldo do vendedor? Se não, a exposição residual deixa de ser exceção e vira regra, e o preço muda.

A curva das quatro passadas é a que se espera: a primeira encontrou contradições entre documentos; a segunda, perda silenciosa de dinheiro; a terceira, tipos declarados sem especificação; esta, ausência de amarração entre tabelas que já existiam. Os defeitos ficam mais rasos.

O ponto de parada não é zero defeitos — é quando os que restam são detectados pelo sistema em vez de descobertos pelo cliente. As oito verificações, a conciliação diária, as chaves naturais e os gatilhos de sinal existem para essa lista ser curta e para ela chegar por alarme, não por reclamação.

---

*Relatório de verificação. Execuções realizadas em PostgreSQL 16.15 e JDK 21. Itens marcados JUR, FIS e PSP nos documentos 1 a 5 continuam exigindo validação por profissional habilitado antes do início da operação.*
