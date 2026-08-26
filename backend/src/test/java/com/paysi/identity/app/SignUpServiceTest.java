package com.paysi.identity.app;

import com.paysi.core.error.ConflictException;
import com.paysi.identity.domain.Account;
import com.paysi.identity.domain.InitialMode;
import com.paysi.identity.domain.PersonType;
import com.paysi.identity.port.AccountRepository;
import com.paysi.identity.port.PasswordHasher;
import com.paysi.identity.port.PlatformPlanReader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignUpServiceTest {

    @Test
    void createsNormalizedAccountWithHashedPasswordAndDefaultPlan() {
        var accounts = new InMemoryAccounts();
        PasswordHasher hasher = raw -> "argon2:" + raw;
        PlatformPlanReader plans = accountId -> "TRANSACIONAL";
        var service = new SignUpService(accounts, plans, hasher);

        var created = service.signUp(command(" Pessoa Teste ", " PESSOA@EXEMPLO.COM ", "529.982.247-25"));

        assertThat(created.fullName()).isEqualTo("Pessoa Teste");
        assertThat(created.email()).isEqualTo("pessoa@exemplo.com");
        assertThat(created.personType()).isEqualTo(PersonType.PF);
        assertThat(created.kycStatus().name()).isEqualTo("PENDING");
        assertThat(created.activeMode()).isEqualTo(InitialMode.SELLER);
        assertThat(created.plan()).isEqualTo("TRANSACIONAL");
        assertThat(accounts.inserted).singleElement().satisfies(account -> {
            assertThat(account.taxId().digits()).isEqualTo("52998224725");
            assertThat(account.passwordHash()).isEqualTo("argon2:senha-segura");
            assertThat(account.passwordHash()).isNotEqualTo("senha-segura");
        });
    }

    @Test
    void rejectsDuplicateActiveEmailWithoutHashingOrPersisting() {
        var accounts = new InMemoryAccounts();
        accounts.duplicateEmail = true;
        var hashCalls = new ArrayList<String>();
        var service = new SignUpService(accounts, accountId -> "TRANSACIONAL", raw -> {
            hashCalls.add(raw);
            return "hash";
        });

        assertThatThrownBy(() -> service.signUp(command("Pessoa Teste", "pessoa@exemplo.com", "52998224725")))
                .isInstanceOfSatisfying(ConflictException.class, error -> {
                    assertThat(error.code()).isEqualTo("EMAIL_ALREADY_REGISTERED");
                    assertThat(error.field()).isEqualTo("email");
                });

        assertThat(hashCalls).isEmpty();
        assertThat(accounts.inserted).isEmpty();
    }

    @Test
    void rejectsDuplicateActiveTaxId() {
        var accounts = new InMemoryAccounts();
        accounts.duplicateTaxId = true;
        var service = new SignUpService(accounts, accountId -> "TRANSACIONAL", raw -> "hash");

        assertThatThrownBy(() -> service.signUp(command("Pessoa Teste", "pessoa@exemplo.com", "52998224725")))
                .isInstanceOfSatisfying(ConflictException.class, error -> {
                    assertThat(error.code()).isEqualTo("TAX_ID_ALREADY_REGISTERED");
                    assertThat(error.field()).isEqualTo("taxId");
                });
    }

    private static SignUpCommand command(String fullName, String email, String taxId) {
        return new SignUpCommand(fullName, email, "senha-segura", PersonType.PF, taxId,
                InitialMode.SELLER, "sha256:termos-v1");
    }

    private static final class InMemoryAccounts implements AccountRepository {
        private final List<Account> inserted = new ArrayList<>();
        private boolean duplicateEmail;
        private boolean duplicateTaxId;

        @Override
        public boolean existsActiveByEmail(String normalizedEmail) {
            return duplicateEmail;
        }

        @Override
        public boolean existsActiveByTaxId(String taxIdDigits) {
            return duplicateTaxId;
        }

        @Override
        public void insert(Account account) {
            inserted.add(account);
        }
    }
}
