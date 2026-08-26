package com.paysi.identity.adapter;

import com.paysi.identity.domain.Account;
import com.paysi.identity.domain.AccountStatus;
import com.paysi.identity.domain.DuplicateAccountField;
import com.paysi.identity.port.AccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Repository
class JpaAccountRepository implements AccountRepository {

    private static final Pattern CONSTRAINT_NAME = Pattern.compile("constraint \"([^\"]+)\"");

    private final AccountJpaRepository jpaRepository;

    JpaAccountRepository(AccountJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public boolean existsActiveByEmail(String normalizedEmail) {
        return jpaRepository.existsByEmailAndStatusNot(normalizedEmail, AccountStatus.CLOSED.name());
    }

    @Override
    public boolean existsActiveByTaxId(String taxIdDigits) {
        return jpaRepository.existsByTaxIdAndStatusNot(taxIdDigits, AccountStatus.CLOSED.name());
    }

    @Override
    public Optional<Account> findOpenByEmail(String normalizedEmail) {
        return jpaRepository.findFirstByEmailAndStatusNot(normalizedEmail, AccountStatus.CLOSED.name())
                .map(AccountEntity::toDomain);
    }

    @Override
    public Optional<Account> findById(java.util.UUID accountId) {
        return jpaRepository.findById(accountId).map(AccountEntity::toDomain);
    }

    @Override
    public void updatePassword(java.util.UUID accountId, String passwordHash) {
        AccountEntity entity = jpaRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Conta do token de recuperação não encontrada"));
        entity.changePassword(passwordHash);
        jpaRepository.saveAndFlush(entity);
    }

    @Override
    public void insert(Account account) {
        try {
            // saveAndFlush força a checagem dos índices únicos ainda dentro deste
            // método, onde a corrida com outra requisição concorrente pode ser
            // traduzida para o mesmo ConflictException do caminho síncrono.
            jpaRepository.saveAndFlush(AccountEntity.fromDomain(account));
        } catch (DataIntegrityViolationException ex) {
            throw translateUniqueViolation(ex);
        }
    }

    private RuntimeException translateUniqueViolation(DataIntegrityViolationException ex) {
        return extractConstraintName(ex)
                .flatMap(DuplicateAccountField::byConstraintName)
                .<RuntimeException>map(DuplicateAccountField::toException)
                .orElse(ex);
    }

    private static Optional<String> extractConstraintName(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlEx && "23505".equals(sqlEx.getSQLState())) {
                Matcher matcher = CONSTRAINT_NAME.matcher(String.valueOf(sqlEx.getMessage()));
                if (matcher.find()) {
                    return Optional.of(matcher.group(1));
                }
            }
        }
        return Optional.empty();
    }
}
