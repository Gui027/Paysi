package com.paysi.compliance.lgpd.app;

import com.paysi.checkout.order.port.BuyerRepository;
import com.paysi.compliance.lgpd.domain.LgpdRequest;
import com.paysi.compliance.lgpd.port.LgpdRequestRepository;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * BE-14.4: rastreio de pedido de titular sob a LGPD (RF-115). Fluxo interno — quem
 * registra, atribui e resolve é a operação (papel {@code COMPLIANCE} ou {@code ADMIN}),
 * não o próprio titular; a resolução exige evidência e, para exclusão de comprador,
 * dispara a anonimização do registro vivo (RNF-026) sem tocar o retrato da venda ou
 * o razão (documento 2, §3.4 e §3.12).
 */
@Service
public class LgpdRequestService {
    private static final Duration SLA = Duration.ofDays(15); // RNF-027
    private static final Set<String> SUBJECT_KINDS = Set.of(LgpdRequest.SUBJECT_BUYER, LgpdRequest.SUBJECT_ACCOUNT);
    private static final Set<String> KINDS = Set.of(LgpdRequest.KIND_ACCESS, LgpdRequest.KIND_DELETION,
            LgpdRequest.KIND_CORRECTION, LgpdRequest.KIND_PORTABILITY);
    private static final Set<String> OPEN_STATUSES = Set.of(LgpdRequest.STATUS_OPEN, LgpdRequest.STATUS_IN_PROGRESS);
    private static final Set<String> RESOLUTIONS = Set.of(LgpdRequest.STATUS_DONE, LgpdRequest.STATUS_REJECTED);

    private final LgpdRequestRepository repository;
    private final BuyerRepository buyers;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public LgpdRequestService(LgpdRequestRepository repository, BuyerRepository buyers) {
        this(repository, buyers, Clock.systemUTC());
    }

    LgpdRequestService(LgpdRequestRepository repository, BuyerRepository buyers, Clock clock) {
        this.repository = repository;
        this.buyers = buyers;
        this.clock = clock;
    }

    @Transactional
    public LgpdRequestView create(CreateLgpdRequestCommand command) {
        String subjectKind = upper(command.subjectKind());
        String kind = upper(command.kind());
        if (!SUBJECT_KINDS.contains(subjectKind)) {
            throw new ValidationException("LGPD_SUBJECT_KIND_INVALID", "Tipo de titular inválido", "subjectKind");
        }
        if (!KINDS.contains(kind)) {
            throw new ValidationException("LGPD_KIND_INVALID", "Tipo de pedido inválido", "kind");
        }
        if (command.subjectRef() == null || command.subjectRef().isBlank()) {
            throw new ValidationException("LGPD_SUBJECT_REF_REQUIRED", "Informe o identificador do titular",
                    "subjectRef");
        }
        if (LgpdRequest.SUBJECT_BUYER.equals(subjectKind)) {
            UUID buyerId = parseBuyerId(command.subjectRef());
            if (!buyers.exists(buyerId)) {
                throw new NotFoundException("LGPD_BUYER_NOT_FOUND", "Comprador não encontrado");
            }
        }

        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        LgpdRequest stored = repository.insert(id, subjectKind, command.subjectRef().strip(), kind,
                now.plus(SLA), now);
        return LgpdRequestView.from(stored);
    }

    @Transactional
    public LgpdRequestView assign(UUID requestId, UUID assigneeId) {
        if (assigneeId == null) {
            throw new ValidationException("LGPD_ASSIGNEE_REQUIRED", "Informe o responsável", "assigneeId");
        }
        LgpdRequest request = lock(requestId);
        if (!OPEN_STATUSES.contains(request.status())) {
            throw new ConflictException("LGPD_REQUEST_CLOSED", "Pedido já foi encerrado", null);
        }
        repository.assign(requestId, assigneeId, LgpdRequest.STATUS_IN_PROGRESS);
        return LgpdRequestView.from(lock(requestId));
    }

    @Transactional
    public LgpdRequestView resolve(UUID requestId, String decision, String evidence) {
        String status = upper(decision);
        if (!RESOLUTIONS.contains(status)) {
            throw new ValidationException("LGPD_DECISION_INVALID", "Decisão inválida", "status");
        }
        if (evidence == null || evidence.isBlank()) {
            throw new ValidationException("LGPD_EVIDENCE_REQUIRED", "Evidência da resolução é obrigatória",
                    "evidence");
        }
        LgpdRequest request = lock(requestId);
        if (!OPEN_STATUSES.contains(request.status())) {
            throw new ConflictException("LGPD_REQUEST_CLOSED", "Pedido já foi encerrado", null);
        }
        if (request.handledBy() == null) {
            throw new ValidationException("LGPD_REQUEST_NOT_ASSIGNED",
                    "Pedido precisa de um responsável antes de ser resolvido", null);
        }

        if (LgpdRequest.STATUS_DONE.equals(status) && LgpdRequest.KIND_DELETION.equals(request.kind())
                && LgpdRequest.SUBJECT_BUYER.equals(request.subjectKind())) {
            anonymizeBuyer(request);
        }

        repository.resolve(requestId, status, evidence.strip());
        return LgpdRequestView.from(lock(requestId));
    }

    private void anonymizeBuyer(LgpdRequest request) {
        UUID buyerId = parseBuyerId(request.subjectRef());
        // Só apaga o registro vivo em `buyers`; `orders.buyer_snapshot` e o razão
        // (imutável por gatilho, V011) nunca são tocados — são prova legal.
        buyers.anonymize(buyerId, clock.instant());
    }

    private LgpdRequest lock(UUID requestId) {
        return repository.lockForUpdate(requestId)
                .orElseThrow(() -> new NotFoundException("LGPD_REQUEST_NOT_FOUND", "Pedido não encontrado"));
    }

    private static UUID parseBuyerId(String subjectRef) {
        try {
            return UUID.fromString(subjectRef.strip());
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("LGPD_SUBJECT_REF_INVALID",
                    "Identificador do titular deve ser um UUID de comprador", "subjectRef");
        }
    }

    private static String upper(String value) {
        return value == null ? "" : value.strip().toUpperCase();
    }
}
