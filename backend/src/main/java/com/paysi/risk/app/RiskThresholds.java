package com.paysi.risk.app;

/**
 * Limiares objetivos de risco, em pontos-base (bps = 1/100 de 1%), lidos de docs/01-requisitos.md
 * §3.8. Testados exatamente na borda: acima OU IGUAL ao limiar já dispara a ação.
 */
final class RiskThresholds {
    private RiskThresholds() {
    }

    /** RF-076: contestação por vendedor — alerta 0,5%, suspende vendas 1,0%, bloqueia saldo 1,5%. */
    static final int SELLER_DISPUTE_ALERT_BPS = 50;
    static final int SELLER_DISPUTE_SUSPEND_BPS = 100;
    static final int SELLER_DISPUTE_LIMIT_BPS = 150;

    /** RF-077: reembolso por vendedor — alerta 8%, revisão 12%, suspensão 20%. */
    static final int SELLER_REFUND_ALERT_BPS = 800;
    static final int SELLER_REFUND_REVIEW_BPS = 1200;
    static final int SELLER_REFUND_SUSPEND_BPS = 2000;

    /** RF-106: índice agregado da plataforma — alerta 0,4%, congela aprovação de contas novas 0,7%. */
    static final int PLATFORM_DISPUTE_ALERT_BPS = 40;
    static final int PLATFORM_DISPUTE_FREEZE_BPS = 70;
}
