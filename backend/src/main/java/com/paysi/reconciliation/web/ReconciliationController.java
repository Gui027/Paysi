package com.paysi.reconciliation.web;

import com.paysi.admin.app.AdminAuthService;
import com.paysi.reconciliation.app.ImportStatementLine;
import com.paysi.reconciliation.app.ReconciliationService;
import com.paysi.reconciliation.domain.ReconciliationEntry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** BE-14.4: importação de extrato do provedor e disparo manual da conciliação diária. */
@RestController
@RequestMapping("/v1/admin/reconciliation")
public class ReconciliationController {
    private final AdminAuthService auth;
    private final ReconciliationService reconciliation;

    public ReconciliationController(AdminAuthService auth, ReconciliationService reconciliation) {
        this.auth = auth;
        this.reconciliation = reconciliation;
    }

    @PostMapping("/statement")
    public ResponseEntity<Void> importStatement(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Totp", required = false) String totp,
            @RequestBody List<ImportStatementLine> lines) {
        auth.authenticate(authorization, totp, Set.of("RISK", "COMPLIANCE"));
        reconciliation.importStatement(lines);
        return ResponseEntity.noContent().build();
    }

    /** Reexecutável: rodar de novo para o mesmo dia apenas atualiza as linhas, sem duplicar alerta. */
    @PostMapping("/run")
    public ReconciliationEntry.Report run(@RequestHeader(value = "Authorization", required = false) String authorization,
                                           @RequestHeader(value = "X-Admin-Totp", required = false) String totp,
                                           @RequestParam LocalDate date) {
        auth.authenticate(authorization, totp, Set.of("RISK", "COMPLIANCE"));
        return reconciliation.reconcile(date);
    }
}
