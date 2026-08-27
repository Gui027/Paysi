package com.paysi.ledger.query.app;

import com.paysi.core.error.ValidationException;
import com.paysi.ledger.domain.*;
import com.paysi.ledger.query.port.LedgerQueryRepository;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class LedgerQueryServiceTest {
    private static final UUID ACCOUNT=UUID.randomUUID();private static final Instant NOW=Instant.parse("2026-08-27T12:00:00Z");

    @Test void returnsFiveBalancesIncludingZeroWhenCheckpointIsAbsent(){
        var repository=new StubRepository();repository.balances.put(Bucket.AVAILABLE,123L);var service=service(repository);
        var balance=service.balance(ACCOUNT);
        assertThat(balance.available()).isEqualTo(123);assertThat(balance.guarantee()).isZero();assertThat(balance.debt()).isZero();assertThat(balance.asOf()).isEqualTo(NOW);
    }

    @Test void cursorPaginationDoesNotRepeatOrSkipEntries(){
        var repository=new StubRepository();for(long id=5;id>=1;id--)repository.all.add(item(id));var service=service(repository);
        var first=service.entries(ACCOUNT,null,2);var second=service.entries(ACCOUNT,first.nextCursor(),2);var third=service.entries(ACCOUNT,second.nextCursor(),2);
        assertThat(first.items()).extracting(LedgerItem::entryId).containsExactly(5L,4L);
        assertThat(second.items()).extracting(LedgerItem::entryId).containsExactly(3L,2L);
        assertThat(third.items()).extracting(LedgerItem::entryId).containsExactly(1L);assertThat(third.nextCursor()).isNull();
    }

    @Test void rejectsInvalidCursorAndCapsPageSize(){
        var repository=new StubRepository();var service=service(repository);
        assertThatThrownBy(()->service.entries(ACCOUNT,"not-base64",20)).isInstanceOf(ValidationException.class);
        service.entries(ACCOUNT,null,500);assertThat(repository.lastLimit).isEqualTo(101);
    }

    private static LedgerQueryService service(StubRepository repository){return new LedgerQueryService(repository,Clock.fixed(NOW,ZoneOffset.UTC));}
    private static LedgerItem item(long id){return new LedgerItem(id,Bucket.AVAILABLE,Direction.CREDIT,100,Origin.SALE,"Venda","CHARGE:"+id,null,NOW);}
    private static final class StubRepository implements LedgerQueryRepository {
        final Map<Bucket,Long> balances=new EnumMap<>(Bucket.class);final List<LedgerItem> all=new ArrayList<>();int lastLimit;
        public Map<Bucket,Long> balances(UUID id){return balances;}
        public List<LedgerItem> entries(UUID id,Long before,int limit){lastLimit=limit;return all.stream().filter(item->before==null||item.entryId()<before).limit(limit).toList();}
        public void consolidate(UUID id){}
    }
}
