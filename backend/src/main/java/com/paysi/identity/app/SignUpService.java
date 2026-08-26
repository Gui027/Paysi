package com.paysi.identity.app;

import com.paysi.identity.domain.Account;
import com.paysi.identity.domain.DuplicateAccountField;
import com.paysi.identity.domain.TaxId;
import com.paysi.identity.port.AccountRepository;
import com.paysi.identity.port.PasswordHasher;
import com.paysi.identity.port.PlatformPlanReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * RF-001/RF-005/RF-125: cria a conta já vinculada ao plano Transacional.
 * A criação de {@code platform_subscriptions} é responsabilidade do gatilho
 * {@code trg_account_default_plan} (V022) — este serviço confirma o resultado, não o repete.
 */
@Service
public class SignUpService {

    private final AccountRepository accountRepository;
    private final PlatformPlanReader platformPlanReader;
    private final PasswordHasher passwordHasher;

    public SignUpService(AccountRepository accountRepository, PlatformPlanReader platformPlanReader,
                          PasswordHasher passwordHasher) {
        this.accountRepository = accountRepository;
        this.platformPlanReader = platformPlanReader;
        this.passwordHasher = passwordHasher;
    }

    @Transactional
    public AccountCreated signUp(SignUpCommand command) {
        String normalizedEmail = normalizeEmail(command.email());
        TaxId taxId = TaxId.of(command.taxId(), command.personType());

        if (accountRepository.existsActiveByEmail(normalizedEmail)) {
            throw DuplicateAccountField.EMAIL.toException();
        }
        if (accountRepository.existsActiveByTaxId(taxId.digits())) {
            throw DuplicateAccountField.TAX_ID.toException();
        }

        String passwordHash = passwordHasher.hash(command.rawPassword());
        Account account = Account.createNew(normalizedEmail, passwordHash, command.fullName().strip(),
                command.personType(), taxId);

        // Corrida entre a checagem acima e este insert: uq_accounts_email_open /
        // uq_accounts_tax_id_open (V001) são a garantia real; o adaptador traduz a
        // violação de índice para o mesmo ConflictException do caminho síncrono.
        accountRepository.insert(account);

        String plan = platformPlanReader.currentPlan(account.id());

        return new AccountCreated(account.id(), account.fullName(), account.email(), account.personType(),
                account.kycStatus(), command.initialMode(), plan);
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
