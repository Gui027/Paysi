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
