package com.paysi.finance.port;

import com.paysi.finance.app.FinanceModels.AccountRow;
import com.paysi.finance.app.FinanceModels.BankRow;
import com.paysi.finance.app.FinanceModels.PayoutRaw;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinanceRepository {
    Optional<AccountRow> account(UUID accountId);

    /** Conta de recebimento vigente (a mais recente, verificada e não arquivada). */
    Optional<BankRow> activeBank(UUID accountId);

    List<UUID> activeBankIds(UUID accountId);

    List<PayoutRaw> payouts(UUID accountId, int limit, int offset);

    long countPayouts(UUID accountId);

    /**
     * Passa a conta de pessoa física para jurídica: novo documento e razão social, verificação de identidade
     * reiniciada (a empresa precisa ser verificada) e um registro de auditoria com o documento anterior.
     */
    void convertToCompany(UUID accountId, String legalName, String cnpj, String previousPersonType, String previousTaxId);
}
