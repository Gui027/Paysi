package com.paysi.ledger.query.app;
import java.util.List;
public record LedgerPage(List<LedgerItem> items,String nextCursor) { }
