package com.paysi.payout.adapter;

import com.paysi.payout.domain.BankAccountCommand;
import com.paysi.payout.port.BankVerificationProvider;
import org.springframework.stereotype.Component;

@Component
public class ConfiguredBankVerificationProvider implements BankVerificationProvider {
    @Override public boolean verifyOwnership(BankAccountCommand command, String expectedTaxId) {
        return digits(command.holderTaxId()).equals(digits(expectedTaxId));
    }
    private static String digits(String value) { return value == null ? "" : value.replaceAll("\\D", ""); }
}
