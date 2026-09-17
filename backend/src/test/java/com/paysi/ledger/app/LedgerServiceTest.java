package com.paysi.ledger.app;

import com.paysi.core.error.ConflictException;
import com.paysi.core.error.ValidationException;
import com.paysi.ledger.domain.*;
import com.paysi.ledger.port.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;

class LedgerServiceTest {
    private static final UUID USER=UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SYSTEM=UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @Test void writesOnceAndReturnsSameTransactionForReplay() {
        var repo=new MemoryRepository();var service=new LedgerService(repo);var command=credit("charge-one",100);
        var first=service.write(command);var replay=service.write(command);
        assertThat(first.idempotentReplay()).isFalse();assertThat(replay.idempotentReplay()).isTrue();
        assertThat(replay.transactionId()).isEqualTo(first.transactionId());assertThat(repo.transactions).hasSize(1);
    }

    @Test void rejectsNaturalKeyWithDifferentPayload() {
        var service=new LedgerService(new MemoryRepository());service.write(credit("same",100));
        assertThatThrownBy(() -> service.write(credit("same",200))).isInstanceOf(ConflictException.class);
    }

    @Test void oneHundredConcurrentDebitsNeverMakeBalanceNegative() throws Exception {
        var repo=new MemoryRepository();repo.balances.put(key(USER,Bucket.AVAILABLE),50L);var service=new LedgerService(repo);
        try(var executor=Executors.newFixedThreadPool(20)) {
            List<Future<Boolean>> futures=new ArrayList<>();
            for(int i=0;i<100;i++){int n=i;futures.add(executor.submit(() -> {try{service.write(debit("payout-"+n,1));return true;}catch(ValidationException error){return false;}}));}
            int successes=0;for(var future:futures)if(future.get())successes++;
            assertThat(successes).isEqualTo(50);assertThat(repo.rawBalance(USER,Bucket.AVAILABLE)).isZero();
        }
    }

    @Test void cascadeDebitDrainsGuaranteeThenPendingThenAvailableThenDebt() {
        var repo=new MemoryRepository();
        repo.balances.put(key(USER,Bucket.GUARANTEE),30L);
        repo.balances.put(key(USER,Bucket.PENDING),20L);
        repo.balances.put(key(USER,Bucket.AVAILABLE),10L);
        var service=new LedgerService(repo);

        var result=service.writeCascadeDebit(TransactionType.REFUND,
                new LedgerReference(ReferenceType.REFUND,"refund-1"),"Reembolso",USER,90,Origin.OTHER,
                List.of(new LedgerEntry(SYSTEM,Bucket.SYSTEM,Direction.CREDIT,90,Origin.OTHER,null)));

        assertThat(result.idempotentReplay()).isFalse();
        assertThat(repo.rawBalance(USER,Bucket.GUARANTEE)).isZero();
        assertThat(repo.rawBalance(USER,Bucket.PENDING)).isZero();
        assertThat(repo.rawBalance(USER,Bucket.AVAILABLE)).isZero();
        assertThat(repo.rawBalance(USER,Bucket.DEBT)).isEqualTo(-30L);
        assertThat(repo.rawBalance(USER,Bucket.RESERVE)).isZero();
    }

    @Test void cascadeDebitNeverTouchesReserve() {
        var repo=new MemoryRepository();
        repo.balances.put(key(USER,Bucket.RESERVE),500L);
        var service=new LedgerService(repo);

        service.writeCascadeDebit(TransactionType.REFUND,new LedgerReference(ReferenceType.REFUND,"refund-2"),
                "Reembolso",USER,40,Origin.OTHER,
                List.of(new LedgerEntry(SYSTEM,Bucket.SYSTEM,Direction.CREDIT,40,Origin.OTHER,null)));

        assertThat(repo.rawBalance(USER,Bucket.RESERVE)).isEqualTo(500L);
        assertThat(repo.rawBalance(USER,Bucket.DEBT)).isEqualTo(-40L);
    }

    @Test void cascadeDebitReplaysIdempotentlyOnSameNaturalKey() {
        var repo=new MemoryRepository();
        repo.balances.put(key(USER,Bucket.AVAILABLE),100L);
        var service=new LedgerService(repo);
        var reference=new LedgerReference(ReferenceType.REFUND,"refund-3");
        var counter=List.of(new LedgerEntry(SYSTEM,Bucket.SYSTEM,Direction.CREDIT,40,Origin.OTHER,null));

        var first=service.writeCascadeDebit(TransactionType.REFUND,reference,"Reembolso",USER,40,Origin.OTHER,counter);
        var replay=service.writeCascadeDebit(TransactionType.REFUND,reference,"Reembolso",USER,40,Origin.OTHER,counter);

        assertThat(first.idempotentReplay()).isFalse();
        assertThat(replay.idempotentReplay()).isTrue();
        assertThat(replay.transactionId()).isEqualTo(first.transactionId());
        assertThat(repo.rawBalance(USER,Bucket.AVAILABLE)).isEqualTo(60L);
    }

    private static LedgerCommand credit(String ref,long amount){return new LedgerCommand(TransactionType.SALE,new LedgerReference(ReferenceType.CHARGE,ref),"Venda",List.of(new LedgerEntry(SYSTEM,Bucket.SYSTEM,Direction.DEBIT,amount,Origin.SALE,null),new LedgerEntry(USER,Bucket.GUARANTEE,Direction.CREDIT,amount,Origin.SALE,null)));}
    private static LedgerCommand debit(String ref,long amount){return new LedgerCommand(TransactionType.PAYOUT,new LedgerReference(ReferenceType.PAYOUT,ref),"Saque",List.of(new LedgerEntry(USER,Bucket.AVAILABLE,Direction.DEBIT,amount,Origin.OTHER,null),new LedgerEntry(SYSTEM,Bucket.SYSTEM,Direction.CREDIT,amount,Origin.OTHER,null)));}
    private static String key(UUID account,Bucket bucket){return account+":"+bucket;}

    private static final class MemoryRepository implements LedgerRepository {
        final Map<String,StoredLedgerTransaction> transactions=new HashMap<>();final Map<String,Long> balances=new HashMap<>();LedgerCommand pending;
        public synchronized <T>T withAccountLocks(Collection<UUID> ids,Supplier<T> work){return work.get();}
        public Optional<StoredLedgerTransaction> find(TransactionType type,LedgerReference ref){return Optional.ofNullable(transactions.get(type+":"+ref.type()+":"+ref.id()));}
        public long rawBalance(UUID account,Bucket bucket){return balances.getOrDefault(key(account,bucket),0L);}
        public Optional<UUID> tryInsertTransaction(LedgerCommand command,String hash){String key=command.naturalKey();if(transactions.containsKey(key))return Optional.empty();UUID id=UUID.randomUUID();transactions.put(key,new StoredLedgerTransaction(id,hash));pending=command;return Optional.of(id);}
        public void insertEntries(UUID tx,List<LedgerEntry> entries){for(var e:entries)balances.merge(key(e.accountId(),e.bucket()),e.direction()==Direction.CREDIT?e.amountCents():-e.amountCents(),Long::sum);}
    }
}
