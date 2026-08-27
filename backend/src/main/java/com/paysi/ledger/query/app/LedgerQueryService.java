package com.paysi.ledger.query.app;

import com.paysi.core.error.ValidationException;
import com.paysi.ledger.query.port.LedgerQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
public class LedgerQueryService {
    private final LedgerQueryRepository repository;private final Clock clock;
    public LedgerQueryService(LedgerQueryRepository repository){this(repository,Clock.systemUTC());}
    LedgerQueryService(LedgerQueryRepository repository,Clock clock){this.repository=repository;this.clock=clock;}

    @Transactional(readOnly=true) public BalanceView balance(UUID accountId){return BalanceView.from(repository.balances(accountId),clock.instant());}

    @Transactional(readOnly=true) public LedgerPage entries(UUID accountId,String cursor,int requestedLimit){
        int limit=Math.min(Math.max(requestedLimit,1),100);Long before=decode(cursor);
        var rows=repository.entries(accountId,before,limit+1);boolean more=rows.size()>limit;
        var items=List.copyOf(more?rows.subList(0,limit):rows);
        return new LedgerPage(items,more?encode(items.getLast().entryId()):null);
    }

    @Transactional public void consolidate(UUID accountId){repository.consolidate(accountId);}

    static String encode(long id){return Base64.getUrlEncoder().withoutPadding().encodeToString(Long.toString(id).getBytes(StandardCharsets.US_ASCII));}
    static Long decode(String cursor){
        if(cursor==null||cursor.isBlank())return null;
        try{long value=Long.parseLong(new String(Base64.getUrlDecoder().decode(cursor),StandardCharsets.US_ASCII));if(value<=0)throw new IllegalArgumentException();return value;}
        catch(IllegalArgumentException error){throw new ValidationException("CURSOR_INVALID","Cursor do extrato é inválido","cursor");}
    }
}
