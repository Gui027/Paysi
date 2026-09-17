package com.paysi.fiscal.app;

import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.fiscal.domain.FiscalProfile;
import com.paysi.fiscal.issuer.InvoiceIssuer;
import com.paysi.fiscal.issuer.IssuerCredentials;
import com.paysi.fiscal.port.FiscalProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * RF-095: coleta os dados de emissão e valida a credencial em homologação antes de habilitar
 * o vendedor a emitir. Uma credencial errada nunca é aceita silenciosamente — ela fica
 * persistida (para reenvio/correção) mas sem {@code validatedAt}, e a tentativa de validação
 * falha de forma explícita.
 */
@Service
public class FiscalProfileService {
    private static final Set<String> VALID_REGIMES = Set.of("SIMPLES", "PRESUMIDO", "REAL");

    private final FiscalProfileRepository repository;
    private final InvoiceIssuer issuer;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public FiscalProfileService(FiscalProfileRepository repository, InvoiceIssuer issuer) {
        this(repository, issuer, Clock.systemUTC());
    }

    FiscalProfileService(FiscalProfileRepository repository, InvoiceIssuer issuer, Clock clock) {
        this.repository = repository;
        this.issuer = issuer;
        this.clock = clock;
    }

    @Transactional
    public FiscalProfile saveAndValidate(UUID accountId, FiscalProfileCommand command) {
        validateInput(command);
        repository.upsert(accountId, command.municipalityCode(), command.municipalRegistration(),
                command.serviceItem(), command.taxBps(), command.taxRegime(), command.credentialRef());

        var credentials = new IssuerCredentials(command.municipalityCode(), command.municipalRegistration(),
                command.serviceItem(), command.taxBps(), command.taxRegime(), command.credentialRef());
        var validation = issuer.validate(credentials);
        if (!validation.valid()) {
            throw new ValidationException("FISCAL_ISSUER_REJECTED",
                    "Emissor fiscal recusou as credenciais informadas: " + validation.error(), "credentialRef");
        }

        Instant now = clock.instant();
        repository.markValidated(accountId, now);
        return repository.findByAccount(accountId)
                .orElseThrow(() -> new IllegalStateException("Perfil fiscal sumiu após validação"));
    }

    public FiscalProfile find(UUID accountId) {
        return repository.findByAccount(accountId)
                .orElseThrow(() -> new NotFoundException("FISCAL_PROFILE_NOT_FOUND",
                        "Perfil fiscal não configurado para esta conta"));
    }

    private static void validateInput(FiscalProfileCommand command) {
        requireText(command.municipalityCode(), "municipalityCode");
        requireText(command.serviceItem(), "serviceItem");
        requireText(command.credentialRef(), "credentialRef");
        if (command.taxBps() == null || command.taxBps() < 0 || command.taxBps() > 500) {
            throw new ValidationException("FISCAL_TAX_BPS_INVALID",
                    "Alíquota de ISS deve estar entre 0 e 500 bps", "taxBps");
        }
        if (command.taxRegime() == null || !VALID_REGIMES.contains(command.taxRegime())) {
            throw new ValidationException("FISCAL_TAX_REGIME_INVALID",
                    "Regime tributário deve ser SIMPLES, PRESUMIDO ou REAL", "taxRegime");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("FISCAL_FIELD_REQUIRED", "Campo obrigatório: " + field, field);
        }
    }
}
