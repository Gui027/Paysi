package com.paysi.identity.port;

import com.paysi.identity.domain.Account;

import java.util.Optional;

/**
 * Porta de persistência de contas. A verificação de duplicidade ignora contas
 * encerradas (RF-117): quem encerra e volta não é bloqueado por si mesmo.
 */
public interface AccountRepository {

    boolean existsActiveByEmail(String normalizedEmail);

    boolean existsActiveByTaxId(String taxIdDigits);

    Optional<Account> findOpenByEmail(String normalizedEmail);

    /** Persiste a conta nova. Dispara, no banco, a criação automática do plano padrão (V022). */
    void insert(Account account);
}
