# Paysi — Documento 6: Referência de Código

**Versão 1.0 · 21 de agosto de 2026 · interno · Novo nesta revisão**

---

## Para que serve este documento

Os documentos 1 a 5 descrevem o sistema em prosa e SQL. Existem sete pontos em que a prosa não basta — onde a descrição correta admite mais de uma implementação e algumas delas estão erradas de forma que nenhum teste de caminho feliz revela.

Este documento traz o código desses sete pontos, com a correção explicada. **Quatro defeitos foram encontrados no código de referência anterior**, e os quatro estão marcados abaixo.

> **O que este código é e o que não é.** É referência de implementação, escrita para ser lida e copiada. **Não foi compilada num projeto Spring**, porque não existe projeto Spring ainda. O que **foi** executado e verificado contra um PostgreSQL 16.15 real é o SQL: a função de bloqueio, as consultas de saldo, o comportamento sob concorrência e a aritmética (esta em Java, em `PaysiSweep.java`).
>
> Trate os trechos Java como o desenho que precisa ser preservado quando você os escrever de verdade, não como código pronto para produção.

| # | Ponto | Defeito corrigido |
|---|---|---|
| 1 | Bloqueio consultivo e ordem canônica | **Dois defeitos novos** |
| 2 | Escrita no razão | **Um defeito novo** |
| 3 | Resumo de saldo sob concorrência | Defeito B2 |
| 4 | Processo de liberação | Defeito G1 |
| 5 | Inbox de eventos do provedor | **Um defeito novo** |
| 6 | Divisão, rateio por parcela e reembolso | Defeitos B6 e a regra ingênua |
| 7 | Resgate de cupom | Defeito B5 |

---

## 1. Bloqueio consultivo e ordem canônica

É a peça mais delicada do sistema e a que mais parece trivial. Dois defeitos, ambos novos.

### 1.1 Primeiro defeito: o tipo de retorno

O código anterior era:

```java
// ERRADO
Boolean ok = jdbc.queryForObject(
    "SELECT pg_advisory_xact_lock(?, hashtext(?))",
    Boolean.class, NAMESPACE, accountId.toString());
```

`pg_advisory_xact_lock` **não devolve booleano. Devolve `void`.** Verificado no catálogo:

```
 proname                  | pg_get_function_result
--------------------------+------------------------
 pg_advisory_xact_lock    | void
 pg_try_advisory_xact_lock| boolean
```

Quem devolve booleano é a variante `try_`, que é outra função com outra semântica: ela **não espera**, devolve falso na hora e é justamente a que não serve aqui. Mapear `void` para `Boolean` produz um `null` ou uma exceção de conversão, dependendo da versão do driver — e, se o desenvolvedor "consertar" trocando para `pg_try_advisory_xact_lock` porque assim compila, o bloqueio deixa de esperar e a serialização inteira desaparece em silêncio.

### 1.2 Segundo defeito: a ordem canônica estava sobre a coisa errada

O código anterior ordenava por UUID:

```java
// ERRADO
accounts.stream().sorted().forEach(this::lock);   // ordem do UUID
```

O raciocínio parece correto: ordem total, aplicada por todos os chamadores, elimina deadlock. **Mas o bloqueio não é adquirido sobre o UUID — é adquirido sobre `hashtext(uuid)`.** E `hashtext` devolve um inteiro de 32 bits, ou seja, **não é injetora**.

Isso não é hipótese. Numa varredura de 400 mil identificadores no próprio PostgreSQL, colisões aparecem às centenas:

```
      chave     |                uuid1                 |                uuid2
----------------+--------------------------------------+--------------------------------------
    -2017617621 | a0000000-0000-0000-0000-000000000938 | a0000000-0000-0000-0000-000000243734
    -1145307643 | a0000000-0000-0000-0000-000000016706 | a0000000-0000-0000-0000-000000280303
```

O limite de aniversário sobre 32 bits coloca a primeira colisão perto de 77 mil contas — não é um problema de escala distante.

**Com uma colisão, ordenar por UUID deixa de produzir uma ordem consistente das chaves.** Tomando `X` e `Z` colidentes e `Y` com UUID entre os dois:

| Transação | Contas | Ordem por UUID | Chaves adquiridas, nessa ordem |
|---|---|---|---|
| Txn1 | {X, Y} | X, Y | `-2017617621`, `1572043124` |
| Txn2 | {Y, Z} | Y, Z | `1572043124`, `-2017617621` |

As duas transações adquirem **as mesmas duas chaves em ordens opostas**, embora ambas tenham ordenado canonicamente por UUID. Executado com duas sessões simultâneas reais:

```
=== CENARIO 1: ordenacao por UUID ===
ERROR:  deadlock detected
```

**A correção é ordenar pela chave de bloqueio, não pelo identificador**, e deduplicar antes. Isso não é conveniente de fazer em Java, porque `hashtext` é interna do PostgreSQL e não tem equivalente trivial na aplicação. Então a ordenação vai para o banco, num único lugar:

```sql
-- V011__ledger_triggers.sql
CREATE OR REPLACE FUNCTION ledger_lock_accounts(p_accounts uuid[]) RETURNS void AS $$
DECLARE h int;
BEGIN
  -- DISTINCT: colisao vira uma unica aquisicao
  -- ORDER BY: a ordem canonica e a da CHAVE, nunca a do uuid
  FOR h IN SELECT DISTINCT hashtext(a::text) FROM unnest(p_accounts) a ORDER BY 1 LOOP
    PERFORM pg_advisory_xact_lock(4210, h);
  END LOOP;
END $$ LANGUAGE plpgsql;
```

Com ela, as mesmas duas transações:

```
=== CENARIO 2: ordenacao pela CHAVE DE BLOQUEIO ===
PASS: sem deadlock

txn1 {X,Y}: -2017617621 -> 1572043124
txn2 {Y,Z}: -2017617621 -> 1572043124
```

### 1.3 A classe corrigida

```java
package com.paysi.ledger.adapter;

import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.PreparedStatement;
import java.util.*;

/**
 * O UNICO lugar do sistema autorizado a adquirir bloqueio consultivo de conta.
 * ArchUnit falha a compilacao se pg_advisory_xact_lock aparecer em outra classe.
 *
 * Precondicao: existe transacao aberta. O bloqueio e liberado no COMMIT ou ROLLBACK,
 * nunca por chamada explicita - por isso nao existe metodo unlock() aqui.
 */
public final class AdvisoryLock {

    private final JdbcTemplate jdbc;

    public AdvisoryLock(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Bloqueia uma conta. */
    public void lock(UUID account) {
        lockAll(List.of(account));
    }

    /**
     * Bloqueia N contas em ordem canonica DA CHAVE DE BLOQUEIO.
     * A ordenacao e a deduplicacao acontecem no banco, dentro de ledger_lock_accounts:
     * hashtext e interna do PostgreSQL e nao tem equivalente confiavel em Java, e
     * ordenar por UUID NAO produz ordem consistente das chaves quando ha colisao.
     */
    public void lockAll(Collection<UUID> accounts) {
        if (accounts.isEmpty()) return;

        String[] ids = accounts.stream().map(UUID::toString).toArray(String[]::new);

        // execute(), nao queryForObject(): a funcao devolve void.
        jdbc.execute((java.sql.Connection c) -> {
            try (PreparedStatement ps =
                     c.prepareStatement("SELECT ledger_lock_accounts(?::uuid[])")) {
                ps.setArray(1, c.createArrayOf("uuid", ids));
                ps.execute();
            }
            return null;
        });
    }
}
```

---

## 2. Escrita no razão

### 2.1 O defeito novo: a validação de saldo só olhava débito

O código anterior validava saldo suficiente apenas quando a direção era `DEBIT`. Parece correto — é debitando que se estoura saldo. **Só que `DEBT` é o bucket invertido:** ele é sempre negativo ou zero, e quem o empurra para o lado errado é o **crédito**.

Creditar `DEBT` em valor maior que a dívida existente deixa o bucket positivo, o que significa "a plataforma deve ao vendedor por dívida" — que não quer dizer nada. Passava sem nenhuma verificação, e só a verificação nº 3 do dia seguinte acusaria.

O gatilho de restrição deferido do banco recusa isso no `COMMIT` (RF-121). A guarda na aplicação continua existindo para produzir mensagem de erro útil em vez de exceção de restrição no fim da transação.

### 2.2 O que saiu do código: o agendamento

O código anterior inseria em `ledger_release_schedule` junto com o lançamento. **Isso agora é gatilho** (D07). O `write()` ficou menor, e a garantia ficou maior — a linha de agendamento passa a existir mesmo se alguém escrever no razão por outro caminho.

```java
package com.paysi.ledger.app;

import com.paysi.ledger.domain.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

public class LedgerService {

    private final JdbcLedgerRepository repo;
    private final AdvisoryLock lock;
    private final BalanceService balances;

    /**
     * Grava uma transacao contabil. Unico caminho de escrita no razao.
     *
     * @param naturalKey (tipo, tipo_de_referencia, id_de_referencia) - o banco recusa
     *                   duplicata, e e isso que impede creditar duas vezes a mesma venda
     *                   quando o provedor reentrega o webhook (RF-123).
     */
    @Transactional
    public UUID write(TransactionType type,
                      ReferenceType refType, String refId,
                      String description,
                      List<Entry> entries) {

        if (entries.isEmpty())
            throw new DomainException("transacao sem lancamentos");

        // 1. INVARIANTE DE SOMA ZERO, antes de qualquer escrita (RNF-014).
        long debits  = sum(entries, Direction.DEBIT);
        long credits = sum(entries, Direction.CREDIT);
        if (debits != credits)
            throw new DomainException(
                "transacao nao soma zero: debitos=" + debits + " creditos=" + credits);

        // 2. BLOQUEIO DE TODAS AS CONTAS DE USUARIO, em ordem canonica da chave.
        //    Inclusive quando nenhum lancamento valida saldo: a consolidacao do
        //    resumo (secao 3) depende disso para ter fronteira segura (RNF-039).
        Set<UUID> userAccounts = new HashSet<>();
        for (Entry e : entries)
            if (e.bucket() != Bucket.SYSTEM) userAccounts.add(e.accountId());
        lock.lockAll(userAccounts);            // ordena e deduplica no banco

        // 3. GUARDAS DE SINAL, ja dentro do bloqueio.
        //    Rede de mensagem util; a garantia dura e o gatilho deferido (RF-121).
        for (UUID account : userAccounts) {
            for (Bucket b : Bucket.userBuckets()) {
                long delta = net(entries, account, b);
                if (delta == 0) continue;
                long saldo = balances.raw(account, b);   // soma do razao, sem checkpoint

                if (b == Bucket.DEBT) {
                    // DEBT e invertido: nunca pode terminar POSITIVO.
                    // Era este o caso que o codigo anterior nao verificava.
                    if (saldo + delta > 0)
                        throw new DomainException(
                            "credito em DEBT excede a divida: divida=" + (-saldo)
                            + " credito=" + delta);
                } else {
                    if (saldo + delta < 0)
                        throw new DomainException(
                            "saldo insuficiente em " + b + ": tem=" + saldo
                            + " precisa=" + (-delta));
                }
            }
        }

        // 4. ESCRITA. A linha de ledger_release_schedule NAO e escrita aqui:
        //    um gatilho AFTER INSERT a cria a partir do proprio lancamento (D07).
        UUID txId = repo.insertTransaction(type, refType, refId, description);
        for (Entry e : entries) repo.insertEntry(txId, e);
        return txId;
    }

    private static long sum(List<Entry> es, Direction d) {
        return es.stream().filter(e -> e.direction() == d)
                 .mapToLong(Entry::amountCents).sum();
    }

    /** Efeito liquido desta transacao sobre um par (conta, bucket). */
    private static long net(List<Entry> es, UUID account, Bucket b) {
        return es.stream()
                 .filter(e -> e.accountId().equals(account) && e.bucket() == b)
                 .mapToLong(e -> e.direction() == Direction.CREDIT
                                 ? e.amountCents() : -e.amountCents())
                 .sum();
    }
}
```

> **Por que o efeito líquido, e não lançamento a lançamento.** Uma cascata de contestação debita `RESERVE`, debita `AVAILABLE` e credita `DEBT` na mesma transação, em ordem qualquer. Validando lançamento a lançamento, a ordem passa a importar e aparecem falsos positivos. Validando o efeito líquido por par (conta, bucket), a ordem deixa de importar — é o mesmo motivo pelo qual o gatilho do banco é **deferido**.

### 2.3 O saque, como exemplo de uso

```java
@Transactional
public UUID requestPayout(UUID accountId, UUID bankAccountId, long amountCents,
                          MfaToken mfa) {

    if (amountCents < 200) throw new DomainException("saque minimo de R$ 2,00");

    lock.lock(accountId);                    // antes de ler qualquer saldo

    if (balances.raw(accountId, Bucket.DEBT) < 0)
        throw new DomainException("saque bloqueado: ha saldo devedor");   // RF-103

    if (amountCents > mfaThreshold) mfa.require();

    // A titularidade da conta bancaria NAO e verificada aqui e sim no banco,
    // por gatilho (D09). Esta chamada existe para dar erro legivel antes.
    bankAccounts.assertOwnedVerifiedAndActive(accountId, bankAccountId);

    UUID payoutId = payouts.create(accountId, bankAccountId, amountCents);

    // O saldo sai de AVAILABLE quando o saque e ACEITO, nao quando e confirmado:
    // e o que impede o mesmo dinheiro de ser sacado duas vezes em transito.
    write(TransactionType.PAYOUT, ReferenceType.PAYOUT, payoutId.toString(),
          "Saque solicitado",
          List.of(Entry.debit(accountId, Bucket.AVAILABLE, amountCents, Origin.OTHER),
                  Entry.creditSystem("SYS_CLEARING", amountCents)));

    return payoutId;
}
```

---

## 3. Resumo de saldo sob concorrência

O defeito B2, que é o mais silencioso do sistema inteiro: `bigserial` atribui o id **antes** do commit, então `up_to_entry_id` pode ultrapassar um lançamento ainda em voo — que nunca mais entra em soma nenhuma.

Reproduzido com sessões reais: sem bloqueio, saldo exibido **700**, verdadeiro **800**.

**A correção não é código novo — é reusar a serialização que já existe.** A consolidação adquire o mesmo bloqueio da conta. Enquanto o mantém, não existe lançamento em voo para aquela conta, e `max(id)` é fronteira segura.

```java
package com.paysi.ledger.app;

public class CheckpointService {

    private final JdbcTemplate jdbc;
    private final AdvisoryLock lock;

    /** Consolida (conta, bucket). Roda de hora em hora, sob ShedLock. */
    @Transactional
    public void consolidate(UUID accountId, Bucket bucket) {

        // ESTA LINHA E A CORRECAO INTEIRA.
        // Sem ela, max(id) inclui ids de transacoes ainda nao confirmadas,
        // e o lancamento em voo desaparece do saldo para sempre.
        lock.lock(accountId);

        jdbc.update("""
            INSERT INTO ledger_checkpoints (account_id, bucket, up_to_entry_id,
                                            balance_cents, updated_at)
            SELECT ?::uuid, ?, COALESCE(MAX(e.id), 0),
                   COALESCE(SUM(CASE WHEN e.direction='CREDIT'
                                     THEN e.amount_cents ELSE -e.amount_cents END), 0),
                   now()
            FROM ledger_entries e
            WHERE e.account_id = ?::uuid AND e.bucket = ?
            ON CONFLICT (account_id, bucket) DO UPDATE
              SET up_to_entry_id = EXCLUDED.up_to_entry_id,
                  balance_cents  = EXCLUDED.balance_cents,
                  updated_at     = EXCLUDED.updated_at
            """, accountId, bucket.name(), accountId, bucket.name());
    }
}
```

### 3.1 A consulta de saldo, e a armadilha do checkpoint ausente

```java
/** Saldo rapido: checkpoint + a cauda. Usado pelas telas. */
public long balance(UUID accountId, Bucket bucket) {
    return jdbc.queryForObject("""
        SELECT COALESCE(c.balance_cents, 0)
             + COALESCE((SELECT SUM(CASE WHEN e.direction='CREDIT'
                                         THEN e.amount_cents ELSE -e.amount_cents END)
                         FROM ledger_entries e
                         WHERE e.account_id = ?::uuid AND e.bucket = ?
                           AND e.id > COALESCE(c.up_to_entry_id, 0)), 0)
        FROM (SELECT 1) dummy
        LEFT JOIN ledger_checkpoints c
               ON c.account_id = ?::uuid AND c.bucket = ?
        """, Long.class, accountId, bucket.name(), accountId, bucket.name());
}

/** Saldo autoritativo: soma pura do razao. Usado por TODA validacao de escrita. */
public long raw(UUID accountId, Bucket bucket) {
    return jdbc.queryForObject("""
        SELECT COALESCE(SUM(CASE WHEN direction='CREDIT'
                                 THEN amount_cents ELSE -amount_cents END), 0)
        FROM ledger_entries WHERE account_id = ?::uuid AND bucket = ?
        """, Long.class, accountId, bucket.name());
}
```

> **O `FROM (SELECT 1) dummy` não é enfeite.** Conta nova não tem linha em `ledger_checkpoints`. Sem a linha artificial à esquerda, o `LEFT JOIN` não tem de onde partir, a consulta devolve **zero linhas** e `queryForObject` lança `EmptyResultDataAccessException` — na primeira consulta de saldo de todo usuário novo do sistema. Verificado: com a linha artificial, conta sem checkpoint devolve `0`, que é a resposta certa.

> **Duas funções, dois usos, e não os troque.** `balance()` é rápido e serve para exibir. `raw()` é a verdade e serve para **validar escrita**. Validar contra o checkpoint seria validar contra um valor que pode estar atrás do razão. O `write()` da seção 2 chama `raw()`, sempre.

---

## 4. Processo de liberação

`ledger_entries` é append-only, então não há onde carimbar "já liberado". Sem projeção mutável ao lado, o processo horário move o mesmo dinheiro toda hora, para sempre (defeito G1).

```java
public class GuaranteeReleaseService {

    /** De hora em hora, sob ShedLock. Move GUARANTEE -> PENDING, compensando divida. */
    @Transactional
    public void run() {
        List<DueRelease> due = jdbc.query("""
            SELECT entry_id, account_id, amount_cents
            FROM ledger_release_schedule
            WHERE released_at IS NULL
              AND release_at <= now()
              AND bucket = 'GUARANTEE'
            ORDER BY release_at
            LIMIT 500
            FOR UPDATE SKIP LOCKED
            """, mapper);

        for (DueRelease d : due) releaseOne(d);
    }

    @Transactional
    private void releaseOne(DueRelease d) {
        lock.lock(d.accountId());

        long divida = -balances.raw(d.accountId(), Bucket.DEBT);   // positivo ou zero
        long quita  = Math.min(divida, d.amountCents());           // RF-104
        long resta  = d.amountCents() - quita;

        List<Entry> entries = new ArrayList<>();
        entries.add(Entry.debit(d.accountId(), Bucket.GUARANTEE, d.amountCents(), origem));
        if (quita > 0)
            entries.add(Entry.credit(d.accountId(), Bucket.DEBT, quita, Origin.DEBT));
        if (resta > 0)
            entries.add(Entry.credit(d.accountId(), Bucket.PENDING, resta, origem)
                             .releasingAt(pagamento.plus(prazoDeRecebimento)));

        UUID txId = ledger.write(TransactionType.GUARANTEE_RELEASE,
                                 ReferenceType.CHARGE, d.chargeId(),
                                 "Saida da garantia", entries);

        // NA MESMA TRANSACAO. Se o processo cair aqui, ou os dois aconteceram
        // ou nenhum - nunca "moveu o dinheiro e esqueceu de marcar".
        jdbc.update("""
            UPDATE ledger_release_schedule
            SET released_at = now(), release_transaction_id = ?::uuid
            WHERE entry_id = ?
            """, txId, d.entryId());
    }
}
```

> **`FOR UPDATE SKIP LOCKED` e não apenas `FOR UPDATE`.** Com duas instâncias, `FOR UPDATE` faz a segunda esperar a primeira e depois processar as **mesmas** linhas — que já foram liberadas. `SKIP LOCKED` faz cada instância pegar um lote disjunto.
>
> **`releaseOne` é transação própria, não o laço inteiro.** Uma conta com problema não deve impedir a liberação das outras 499.
>
> **Note o que não aparece aqui: nenhuma chamada a `max()`.** A regra `max(recebimento, garantia)` do RF-111 emerge da cadeia — as duas datas partem do pagamento, e cada processo só move o que já venceu. Se você vir um `Math.max` entre prazo de recebimento e prazo de garantia em algum lugar, alguém entendeu a regra ao contrário.

---

## 5. Inbox de eventos do provedor

### 5.1 O defeito novo, e por que ele perde dinheiro em silêncio

O código anterior tinha dois problemas que se somavam:

```java
// ERRADO
@PostMapping("/webhooks/asaas")
public ResponseEntity<?> receive(@RequestBody String body, @RequestHeader ...) {
    if (inbox.exists(providerId, eventId)) return ResponseEntity.ok().build();  // (b)
    inbox.record(providerId, eventId, body);                                    // (a)
    processor.process(eventId);
    return ResponseEntity.ok().build();
}
```

**(a)** O registro acontecia **fora** da transação do efeito. Se a aplicação caísse entre gravar o evento e gravar o lançamento, o evento fica registrado e o dinheiro não entra.

**(b)** A duplicata devolvia `200` **sem olhar o estado**. Combinado com (a), isso é fatal: a retentativa do provedor — que é a única chance de recuperar o evento perdido — bate na chave, recebe `200` e vai embora satisfeita. **O evento fica perdido para sempre**, e só a conciliação encontra, dias depois.

A coluna `status` existe justamente para distinguir "já processado, ignore" de "recebido e nunca processado, reprocesse". Não olhar para ela transforma o inbox de proteção em armadilha.

### 5.2 O código corrigido

```java
@RestController
public class ProviderEventController {

    @PostMapping("/webhooks/{provider}")
    public ResponseEntity<Void> receive(@PathVariable String provider,
                                        @RequestHeader("X-Signature") String signature,
                                        @RequestBody String body) {

        boolean valid = signatures.verify(provider, signature, body);
        String eventId = parser.extractEventId(provider, body);   // PEN-22

        if (eventId == null) {
            // Provedor sem identificador estavel: cai na chave natural do razao,
            // que e rede de seguranca, nao controle primario.
            eventId = "sha256:" + Hashing.sha256(body);
        }

        Outcome outcome = processor.handle(provider, eventId, body, valid);

        return switch (outcome) {
            // 200: nao reenvie. O efeito ja existe ou foi deliberadamente ignorado.
            case PROCESSED, ALREADY_PROCESSED, IGNORED -> ResponseEntity.ok().build();
            // 500: REENVIE. Nada foi gravado, ou foi gravado e nao processado.
            // Devolver 200 aqui e o que perde o evento para sempre.
            case FAILED -> ResponseEntity.status(500).build();
        };
    }
}
```

```java
public class ProviderEventProcessor {

    /**
     * Registro e efeito na MESMA transacao. Ou os dois acontecem, ou nenhum.
     */
    @Transactional
    public Outcome handle(String provider, String eventId, String body, boolean sigValid) {

        // INSERT ... ON CONFLICT DO NOTHING RETURNING: uma ida ao banco decide
        // se somos o primeiro a ver este evento. Sem janela entre checar e gravar.
        Integer inserted = jdbc.queryForObject("""
            WITH ins AS (
              INSERT INTO provider_events
                (provider, provider_event_id, event_type, payload,
                 signature_valid, status)
              VALUES (?, ?, ?, ?::jsonb, ?, 'RECEIVED')
              ON CONFLICT (provider, provider_event_id) DO NOTHING
              RETURNING 1
            )
            SELECT COALESCE((SELECT 1 FROM ins), 0)
            """, Integer.class, provider, eventId, type, body, sigValid);

        if (inserted == 0) {
            // DUPLICATA. O estado decide, nunca a mera presenca da linha.
            String status = jdbc.queryForObject("""
                SELECT status FROM provider_events
                WHERE provider = ? AND provider_event_id = ?
                FOR UPDATE
                """, String.class, provider, eventId);

            switch (status) {
                case "PROCESSED", "IGNORED":
                    return Outcome.ALREADY_PROCESSED;      // 200, correto
                case "RECEIVED", "FAILED":
                    break;                                 // segue e PROCESSA agora
                default:
                    return Outcome.FAILED;
            }
        }

        if (!sigValid) {
            markIgnored(provider, eventId, "assinatura invalida");
            return Outcome.IGNORED;
        }

        try {
            // O efeito. Escreve no razao com a chave natural (tipo, ref, id):
            // segunda camada de protecao, caso a deduplicacao acima falhe.
            effects.apply(provider, eventId, body);

            jdbc.update("""
                UPDATE provider_events SET status='PROCESSED', processed_at=now()
                WHERE provider=? AND provider_event_id=?
                """, provider, eventId);
            return Outcome.PROCESSED;

        } catch (DuplicateNaturalKeyException dup) {
            // O razao ja tinha o lancamento. Nao e erro: e a rede funcionando.
            jdbc.update("""
                UPDATE provider_events SET status='PROCESSED', processed_at=now(),
                       error='efeito ja existia (chave natural)'
                WHERE provider=? AND provider_event_id=?
                """, provider, eventId);
            return Outcome.ALREADY_PROCESSED;
        }
        // Qualquer outra excecao: a transacao inteira reverte, inclusive o INSERT
        // do inbox. Devolvemos 500 e o provedor reenvia. Nada fica pela metade.
    }
}
```

> **O que fica perdido se a transação reverter?** Nada. O evento volta a não existir no inbox, o provedor reenvia, e o processamento recomeça do zero. É o comportamento desejado — melhor um evento ausente e reenviado que um evento presente e não processado.
>
> **Por que o `FOR UPDATE` na duplicata.** Duas entregas simultâneas do mesmo evento: uma insere, outra vê conflito e lê o estado. Sem `FOR UPDATE`, a segunda pode ler `RECEIVED` enquanto a primeira ainda processa e tentar processar também. A chave natural do razão pegaria, mas o erro certo é não chegar lá.

### 5.3 O processo de retentativa

O inbox só funciona se alguém reprocessar o que ficou para trás.

```java
/** A cada 5 minutos. Pega o que foi recebido e nunca processado. */
@Transactional
public void retryStuck() {
    List<StuckEvent> stuck = jdbc.query("""
        SELECT provider, provider_event_id, payload
        FROM provider_events
        WHERE status IN ('RECEIVED','FAILED')
          AND (next_retry_at IS NULL OR next_retry_at <= now())
          AND attempt_count < 8
        ORDER BY received_at
        LIMIT 100
        FOR UPDATE SKIP LOCKED
        """, mapper);

    for (StuckEvent e : stuck) processor.reprocess(e);
    // attempt_count >= 8 nao e descartado: fica como alerta operacional.
    // Evento em RECEIVED ha mais de uma hora e incidente, nao pendencia.
}
```

---

## 6. Divisão, rateio por parcela e reembolso

Estes três são os pontos de truncamento do sistema. O código está integralmente em `PaysiSweep.java`, executável e verificado em **28,6 milhões de cenários**. Aqui ficam apenas as três regras e o porquê de cada uma.

### 6.1 Divisão da venda — o vendedor é o residual

```java
public static Split split(long paidCents, Method method, Plan plan, int commissionBps) {

    // Custo do provedor: so para simulacao e conciliacao. NAO entra na instrucao
    // de divisao enviada ao provedor.
    long providerCost = trunc(paidCents * method.costBps(), 10_000) + method.costFixed();

    // Taxa cobrada do vendedor: e exatamente o que ele ve anunciado.
    long platformFee  = trunc(paidCents * plan.feeBps(method), 10_000) + 200;

    long affiliate = trunc(paidCents * commissionBps, 10_000);
    long seller    = paidCents - platformFee - affiliate;      // RESIDUAL EXATO
    long platform  = platformFee - providerCost;               // residual no provedor

    // Invariante, verificada antes de qualquer persistencia (RNF-014):
    assert seller + affiliate + platform + providerCost == paidCents;

    return new Split(seller, affiliate, platform, providerCost, platformFee);
}
```

> **O vendedor é residual porque é ele quem tem uma promessa a cumprir.** Ele viu "5,99% + R$ 2,00" na tela; se o centavo do arredondamento saísse dele, a taxa efetiva não bateria com o anunciado. Truncando plataforma e afiliado e dando o resto exato ao vendedor, **a taxa que ele paga é sempre exatamente a anunciada** — verificado em 21.945.110 cenários.
>
> Consequência aceita: a margem da plataforma flutua em centavos. É o lugar certo para a variação ficar.

### 6.2 Rateio por parcela — maior resto

```java
/** Distribui um total em n parcelas. Soma exata, desvio maximo de 1 centavo. */
public static long[] byInstallment(long total, int n) {
    long base  = total / n;
    long resto = total % n;
    long[] out = new long[n];
    for (int i = 0; i < n; i++) out[i] = base + (i < resto ? 1 : 0);
    return out;    // as `resto` primeiras parcelas recebem 1 centavo a mais
}
```

> **Calculado uma única vez, na criação dos recebíveis, e gravado** (RNF-040). A liberação lê o valor gravado. Recalcular na liberação é o caminho para duas implementações divergirem — e a `CHECK` de `receivables` recusa parcela cuja parte exceda a própria parcela (D05).
>
> As primeiras parcelas ficam com o centavo extra deliberadamente: favorece o recebimento antecipado. Verificado em 2.400.012 cenários.

### 6.3 Reembolso parcial — truncagem cumulativa

A regra óbvia não funciona, e essa é a lição mais cara deste documento.

```java
// ERRADO - trunca fatia a fatia, plataforma absorve o residuo
long affSlice  = trunc(affTotal * sliceCents, paidCents);
long platSlice = sliceCents - sellerSlice - affSlice - provSlice;
```

Passa em dezenas de milhares de cenários regulares. Quebra em fatias sucessivas próximas do total — venda de R$ 100,00, dez reembolsos de R$ 9,99:

| | Vendedor | Afiliado | Plataforma | Provedor |
|---|---|---|---|---|
| Alocado na venda | 8201 | 1000 | 451 | 348 |
| Devolvido em 10 fatias | 8190 | 990 | **470** | 340 |
| Resta para a fatia final | 11 | 10 | **−19** | 8 |

Cada fatia isolada soma zero e passa em todas as verificações. Só a última quebra, exigindo creditar 19 centavos negativos.

```java
/**
 * Truncagem sobre o ACUMULADO, nunca sobre a fatia. Vendedor residual,
 * mesma politica da venda. Verificado em 4.275.150 cenarios.
 */
public static RefundPart refundSlice(Split original, long paidCents,
                                     long alreadyRefunded, long sliceCents) {

    long acum = alreadyRefunded + sliceCents;      // C, incluindo esta fatia

    long affAcum  = trunc(original.affiliate() * acum, paidCents);
    long platAcum = trunc(original.platform()  * acum, paidCents);
    long provAcum = trunc(original.provider()  * acum, paidCents);
    long sellAcum = acum - affAcum - platAcum - provAcum;

    long affPrev  = trunc(original.affiliate() * alreadyRefunded, paidCents);
    long platPrev = trunc(original.platform()  * alreadyRefunded, paidCents);
    long provPrev = trunc(original.provider()  * alreadyRefunded, paidCents);
    long sellPrev = alreadyRefunded - affPrev - platPrev - provPrev;

    return new RefundPart(sellAcum - sellPrev, affAcum - affPrev,
                          platAcum - platPrev, provAcum - provPrev);
}
```

> **Por que funciona.** A deriva não acumula: cada fatia é a diferença entre dois acumulados exatos. Em `C = paidCents`, os acumulados voltam a ser exatamente a alocação original, por construção. Nenhuma parte fica negativa em nenhuma fatia.
>
> **Duas guardas na aplicação:** reembolso parcial mínimo de R$ 1,00, para haver o que repartir entre quatro partes; e alerta operacional se a receita acumulada da plataforma naquela cobrança não cobrir a parte que lhe cabe, espelhando o RF-039.
>
> **A lição de teste:** verificar apenas "a soma fecha" **não pega este defeito** — a regra errada fecha a soma. É preciso verificar também **ausência de parte negativa em cada fatia**. É o item 24 da lista de revisão do documento 5.

---

## 7. Resgate de cupom

Defeito B5: `redeemed_count` é campo mutável com teto. Dois checkouts simultâneos leem 99 de 100 e ambos passam.

```java
@Transactional
public void redeem(UUID couponId, UUID buyerId, UUID orderId) {

    // UPDATE condicional: a propria escrita e a verificacao.
    // Toma o BLOQUEIO DE LINHA do cupom - a segunda transacao espera a primeira.
    int rows = jdbc.update("""
        UPDATE coupons
        SET redeemed_count = redeemed_count + 1
        WHERE id = ?::uuid
          AND archived_at IS NULL
          AND (expires_at IS NULL OR expires_at > now())
          AND (max_redemptions IS NULL OR redeemed_count < max_redemptions)
        """, couponId);

    if (rows == 0) throw new DomainException("cupom indisponivel");

    // A verificacao POR COMPRADOR vem DEPOIS, e e por isso que e segura:
    // neste ponto as transacoes concorrentes ja estao serializadas pelo
    // bloqueio de linha adquirido acima. Invertida, volta a ter corrida.
    long usos = jdbc.queryForObject("""
        SELECT count(*) FROM coupon_redemptions
        WHERE coupon_id = ?::uuid AND buyer_id = ?::uuid
        """, Long.class, couponId, buyerId);

    if (usos >= maxPorComprador)
        throw new DomainException("limite por comprador atingido");  // reverte o UPDATE

    jdbc.update("""
        INSERT INTO coupon_redemptions (coupon_id, buyer_id, order_id)
        VALUES (?::uuid, ?::uuid, ?::uuid)
        """, couponId, buyerId, orderId);
}
```

> **A ordem é a correção.** Verificar o limite por comprador primeiro e incrementar depois reintroduz exatamente a corrida que o `UPDATE` condicional resolve. Há ainda `CHECK (redeemed_count <= max_redemptions)` como rede.
>
> **Validação de piso é na criação do cupom, não aqui.** Se o cupom levar a oferta mais barata vinculada abaixo do piso técnico de R$ 5,00, ele é recusado no cadastro — erro na cara de quem configurou, não de quem ia pagar.

---

## 8. O que verificar antes de considerar isto pronto

| Teste | Prova |
|---|---|
| `LockOrderingTest` | Duas contas colidentes em `hashtext` não geram deadlock. **Use um par de colisão real, não dois UUIDs quaisquer** — com UUIDs quaisquer o teste passa mesmo com o código errado |
| `NegativeBucketGuardTest` | Crédito em `DEBT` acima da dívida é recusado; débito acima do saldo é recusado |
| `CheckpointConcurrencyTest` | Escrita não confirmada durante a consolidação não desaparece do saldo |
| `CheckpointRebuildTest` | Conta sem checkpoint devolve saldo, não exceção |
| `ReleaseIdempotencyTest` | Processo rodado duas vezes move o dinheiro uma vez |
| `ProviderEventReplayTest` | Evento em `RECEIVED` que chega de novo **é processado**, não descartado com 200 |
| `PartialRefundSplitTest` | Dez fatias de R$ 9,99 sobre R$ 100,00: soma exata **e nenhuma parte negativa** |
| `CouponConcurrencyTest` | Cem resgates simultâneos de cupom com 50 unidades resultam em exatamente 50 |

> **O primeiro item merece atenção.** O teste de ordenação de bloqueio escrito com dois UUIDs arbitrários passa com o código errado, porque sem colisão a ordem por UUID coincide com uma ordem consistente. O teste só tem valor com um par que efetivamente colida — e agora você tem um: `a0000000-0000-0000-0000-000000000938` e `a0000000-0000-0000-0000-000000243734`, ambos com chave `-2017617621`.
>
> Vale o mesmo princípio das oito verificações de integridade: **teste que nunca falhou com o código errado não provou nada.**

---

*Referência de implementação. O SQL e a aritmética foram executados e verificados contra PostgreSQL 16.15 e JDK 21; os trechos Java são desenho de referência e não foram compilados num projeto Spring. Nomes de classe e assinatura podem mudar durante a implementação sem alterar as correções registradas.*
