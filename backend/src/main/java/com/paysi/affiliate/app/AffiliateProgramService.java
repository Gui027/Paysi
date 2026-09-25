package com.paysi.affiliate.app;

import com.paysi.affiliate.domain.AffiliateProgram;
import com.paysi.affiliate.domain.AffiliationRecurrence;
import com.paysi.affiliate.port.AffiliateProgramRepository;
import com.paysi.catalog.product.app.ProductService;
import com.paysi.core.error.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AffiliateProgramService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final AffiliateProgramRepository programs;
    private final ProductService products;

    public AffiliateProgramService(AffiliateProgramRepository programs, ProductService products) {
        this.programs = programs;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public AffiliateProgram get(UUID sellerId, UUID productId) {
        products.get(sellerId, productId);
        return programs.find(productId).orElseGet(() -> AffiliateProgram.defaults(productId));
    }

    @Transactional
    public AffiliateProgram update(UUID sellerId, UUID productId, int commissionBps,
                                   AffiliationRecurrence recurrence, boolean autoApprove,
                                   String supportEmail, String description) {
        products.get(sellerId, productId);
        if (commissionBps < 0 || commissionBps > 5_000) {
            throw new ValidationException("AFFILIATE_PROGRAM_COMMISSION_INVALID",
                    "A comissão deve estar entre 0% e 50%", "commissionBps");
        }
        if (recurrence == null) {
            throw new ValidationException("AFFILIATE_PROGRAM_RECURRENCE_REQUIRED",
                    "Informe a recorrência da comissão", "recurrence");
        }
        String email = blankToNull(supportEmail);
        if (email != null && (email.length() > 254 || !EMAIL.matcher(email).matches())) {
            throw new ValidationException("AFFILIATE_PROGRAM_EMAIL_INVALID",
                    "Informe um e-mail de suporte válido", "supportEmail");
        }
        String text = blankToNull(description);
        if (text != null && text.length() > 1_000) {
            throw new ValidationException("AFFILIATE_PROGRAM_DESCRIPTION_TOO_LONG",
                    "A descrição deve ter no máximo 1000 caracteres", "description");
        }
        var program = new AffiliateProgram(productId, commissionBps, recurrence, autoApprove, email, text);
        programs.save(program);
        return program;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
