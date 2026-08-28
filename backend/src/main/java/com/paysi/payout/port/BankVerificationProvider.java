package com.paysi.payout.port;

import com.paysi.payout.domain.BankAccountCommand;

public interface BankVerificationProvider {
    boolean verifyOwnership(BankAccountCommand command, String expectedTaxId);
}
