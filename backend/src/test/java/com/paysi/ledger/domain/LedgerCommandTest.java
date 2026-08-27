package com.paysi.ledger.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class LedgerCommandTest {
    @Test void rejectsUnbalancedTransactionAndInvalidSchedule() {
        UUID account=UUID.randomUUID();
        assertThatThrownBy(() -> command(List.of(new LedgerEntry(account,Bucket.AVAILABLE,Direction.CREDIT,100,Origin.OTHER,null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LedgerEntry(account,Bucket.SYSTEM,Direction.CREDIT,100,Origin.OTHER,Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
    private static LedgerCommand command(List<LedgerEntry> entries){return new LedgerCommand(TransactionType.SALE,new LedgerReference(ReferenceType.CHARGE,"one"),"Venda",entries);}
}
