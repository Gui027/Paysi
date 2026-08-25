import java.util.*;

/**
 * Implementação de referência das três regras de truncamento do Paysi,
 * mais as varreduras exaustivas que provam as invariantes.
 *
 *  1. SplitEngine        — RF-035, RF-037, RF-038
 *  2. InstallmentSplit   — RF-041 (maior resto)
 *  3. RefundSplit        — RF-105 (truncagem cumulativa)
 */
public class PaysiSweep {

    // ---------- tabela de preços, doc 1 §5.1 ----------
    record Method(String name, int provBps, long provFixed,
                  int feeBpsTx, int feeBpsEscala, long feeFixed) {}

    static final Method[] METHODS = {
        new Method("PIX",     0,   199,  399, 199, 200),
        new Method("BOLETO",  0,   199,  399, 199, 200),
        new Method("CARD_1",  299,  49,  599, 399, 200),
        new Method("CARD_6",  349,  49,  649, 449, 200),
        new Method("CARD_12", 399,  49,  699, 499, 200),
    };

    static long trunc(long paid, int bps) { return paid * bps / 10_000L; }

    // ---------- 1. SplitEngine ----------
    record Split(long seller, long affiliate, long platformNet,
                 long providerCost, long sellerFee) {}

    static Split split(long paid, Method m, boolean escala, int commissionBps) {
        long providerCost = trunc(paid, m.provBps()) + m.provFixed();
        int  feeBps       = escala ? m.feeBpsEscala() : m.feeBpsTx();
        long sellerFee    = trunc(paid, feeBps) + m.feeFixed();      // taxa cobrada
        long affiliate    = trunc(paid, commissionBps);              // truncada
        long seller       = paid - sellerFee - affiliate;            // resto exato
        long platformNet  = sellerFee - providerCost;                // residual
        return new Split(seller, affiliate, platformNet, providerCost, sellerFee);
    }

    // ---------- 2. InstallmentSplit ----------
    static long[] installments(long total, int n) {
        if (n < 1) throw new IllegalArgumentException("n < 1");
        if (total < 0) throw new IllegalArgumentException("total < 0");
        long base = total / n, resto = total % n;
        long[] out = new long[n];
        for (int i = 0; i < n; i++) out[i] = base + (i < resto ? 1 : 0);
        return out;
    }

    // ---------- 3. RefundSplit (truncagem cumulativa) ----------
    record RefundPart(long seller, long affiliate, long platform, long provider) {}

    static RefundPart refundSlice(long paid, long A, long P, long V,
                                  long cumBefore, long cumAfter) {
        long aB = A * cumBefore / paid, aA = A * cumAfter / paid;
        long pB = P * cumBefore / paid, pA = P * cumAfter / paid;
        long vB = V * cumBefore / paid, vA = V * cumAfter / paid;
        long sB = cumBefore - aB - pB - vB;
        long sA = cumAfter  - aA - pA - vA;
        return new RefundPart(sA - sB, aA - aB, pA - pB, vA - vB);
    }

    // =================================================================
    public static void main(String[] args) {
        int fail = 0;
        fail += sweepSplit();
        fail += sweepInstallments();
        fail += sweepRefunds();
        fail += adversarial();
        System.out.println(fail == 0
            ? "\n=== TODAS AS VARREDURAS PASSARAM ==="
            : "\n=== " + fail + " FALHAS ===");
        if (fail > 0) System.exit(1);
    }

    // ---- varredura 1: divisão ----
    static int sweepSplit() {
        int[] comissoes = {0, 1, 500, 1000, 1500, 2000, 2500, 3000, 4000, 4999, 5000};
        long lo = 500, hi = 200_000;          // R$ 5,00 (piso técnico) a R$ 2.000,00
        long n = 0; int fail = 0;
        long minSeller = Long.MAX_VALUE, minPlatform = Long.MAX_VALUE;

        for (Method m : METHODS)
          for (boolean escala : new boolean[]{false, true})
            for (int c : comissoes)
              for (long paid = lo; paid <= hi; paid++) {
                Split s = split(paid, m, escala, c);
                n++;
                if (s.seller() + s.affiliate() + s.platformNet() + s.providerCost() != paid) {
                    if (fail++ < 5) System.out.printf(
                        "INVARIANTE: paid=%d %s escala=%s c=%d -> %s%n", paid, m.name(), escala, c, s);
                }
                if (s.seller() < 0 || s.affiliate() < 0 || s.providerCost() < 0) {
                    if (fail++ < 5) System.out.printf(
                        "NEGATIVO: paid=%d %s escala=%s c=%d -> %s%n", paid, m.name(), escala, c, s);
                }
                int feeBps = escala ? m.feeBpsEscala() : m.feeBpsTx();
                if (s.sellerFee() != paid * feeBps / 10_000L + m.feeFixed()) {
                    if (fail++ < 5) System.out.println("TAXA ANUNCIADA divergente em " + paid);
                }
                minSeller = Math.min(minSeller, s.seller());
                minPlatform = Math.min(minPlatform, s.platformNet());
              }

        System.out.printf("[1] divisão .............. %,d cenários | menor vendedor=%d | menor margem=%d | falhas=%d%n",
                          n, minSeller, minPlatform, fail);
        return fail;
    }

    // ---- varredura 2: parcelas ----
    static int sweepInstallments() {
        long n = 0; int fail = 0;
        for (long total = 0; total <= 200_000; total++)
            for (int k = 1; k <= 12; k++) {
                long[] p = installments(total, k);
                long soma = 0, prev = Long.MAX_VALUE; boolean naoCrescente = true;
                for (long v : p) { soma += v; if (v > prev) naoCrescente = false; prev = v; }
                n++;
                if (soma != total && fail++ < 5)
                    System.out.printf("PARCELA soma: total=%d n=%d soma=%d%n", total, k, soma);
                if (!naoCrescente && fail++ < 5)
                    System.out.printf("PARCELA ordem: total=%d n=%d%n", total, k);
                if (p[0] - p[k-1] > 1 && fail++ < 5)
                    System.out.printf("PARCELA desvio>1: total=%d n=%d%n", total, k);
            }
        System.out.printf("[2] parcelas ............. %,d cenários | falhas=%d%n", n, fail);
        return fail;
    }

    // ---- varredura 3: reembolso parcial ----
    static int sweepRefunds() {
        int[] comissoes = {0, 500, 1000, 2500, 5000};
        int[] fatias    = {1, 2, 3, 5, 10, 17};
        long n = 0; int fail = 0;

        for (Method m : METHODS)
          for (int c : comissoes)
            for (long paid = 500; paid <= 200_000; paid += 7) {   // amostragem densa
              Split s = split(paid, m, false, c);
              long A = s.affiliate(), P = s.sellerFee() - s.providerCost(), V = s.providerCost();
              long S = s.seller();
              for (int k : fatias) {
                long[] valores = installments(paid, k);   // k fatias que somam o pago
                long cum = 0, accS = 0, accA = 0, accP = 0, accV = 0;
                boolean ok = true;
                for (long v : valores) {
                    long antes = cum; cum += v;
                    RefundPart r = refundSlice(paid, A, P, V, antes, cum);
                    if (r.seller() < 0 || r.affiliate() < 0 || r.platform() < 0 || r.provider() < 0) ok = false;
                    if (r.seller() + r.affiliate() + r.platform() + r.provider() != v) ok = false;
                    accS += r.seller(); accA += r.affiliate(); accP += r.platform(); accV += r.provider();
                }
                n++;
                if (!ok && fail++ < 5)
                    System.out.printf("REEMBOLSO fatia inválida: paid=%d %s c=%d k=%d%n", paid, m.name(), c, k);
                if ((accS != S || accA != A || accP != P || accV != V) && fail++ < 5)
                    System.out.printf("REEMBOLSO total: paid=%d c=%d k=%d -> S%d/%d A%d/%d P%d/%d V%d/%d%n",
                                      paid, c, k, accS, S, accA, A, accP, P, accV, V);
              }
            }
        System.out.printf("[3] reembolso parcial .... %,d cenários | falhas=%d%n", n, fail);
        return fail;
    }

    // ---- casos adversariais ----
    static int adversarial() {
        int fail = 0;

        // exemplo do doc 1 §5.3 — R$ 100,00 cartão à vista, Transacional, afiliado 10%
        Split a = split(10_000, METHODS[2], false, 1000);
        if (a.seller() != 8201 || a.affiliate() != 1000 || a.platformNet() != 451 || a.providerCost() != 348) {
            System.out.println("DOC1 §5.3 divergente: " + a); fail++;
        }
        // exemplo do doc 2 §4.3 — R$ 177,00
        Split b = split(17_700, METHODS[2], false, 1000);
        if (b.seller() != 14670 || b.affiliate() != 1770 || b.platformNet() != 682
            || b.providerCost() != 578 || b.sellerFee() != 1260) {
            System.out.println("DOC2 §4.3 divergente: " + b); fail++;
        }
        // taxa efetiva anunciada, doc 1 §3.2
        long[][] esperado = {{2000, 319}, {5000, 499}, {19700, 1380}, {500, 229}};
        for (long[] e : esperado) {
            long fee = split(e[0], METHODS[2], false, 0).sellerFee();
            if (fee != e[1]) { System.out.printf("TAXA EFETIVA %d -> %d, esperado %d%n", e[0], fee, e[1]); fail++; }
        }
        // rateio do doc de referência: 8201 em 12x
        long[] r = installments(8201, 12);
        if (!Arrays.toString(r).equals("[684, 684, 684, 684, 684, 683, 683, 683, 683, 683, 683, 683]")) {
            System.out.println("RATEIO 8201/12 divergente: " + Arrays.toString(r)); fail++;
        }
        // reembolso: primeira fatia de R$ 20,00 sobre venda de R$ 100,00
        RefundPart p = refundSlice(10_000, 1000, 451, 348, 0, 2000);
        if (p.affiliate() != 200 || p.platform() != 90 || p.provider() != 69 || p.seller() != 1641) {
            System.out.println("REEMBOLSO exemplo divergente: " + p); fail++;
        }
        // a regra ingênua — truncar afiliado/vendedor/provedor por fatia e deixar
        // a plataforma absorver o resíduo — quebra em 10 × R$ 9,99 + R$ 0,10
        long nS=0, nA=0, nP=0, nV=0;
        for (int i = 0; i < 10; i++) {
            long v = 999;
            long s_ = 8201L*v/10_000, a_ = 1000L*v/10_000, v_ = 348L*v/10_000;
            nS += s_; nA += a_; nV += v_; nP += v - s_ - a_ - v_;
        }
        System.out.printf("[4] regra ingênua ........ apos 10 fatias: vendedor=%d afiliado=%d plataforma=%d provedor=%d%n",
                          nS, nA, nP, nV);
        System.out.printf("[4] regra ingênua ........ resta para a fatia final: vendedor=%d afiliado=%d plataforma=%d provedor=%d%n",
                          8201-nS, 1000-nA, 451-nP, 348-nV);
        if (451 - nP >= 0) { System.out.println("*** a regra ingenua NAO produziu alocacao negativa"); fail++; }
        // antecipação D+7 sobre 8201, doc lançamentos §4
        long cobrado = 8201L * 229 / 10_000, custo = 8201L * 104 / 10_000;
        if (cobrado != 187 || custo != 85 || cobrado - custo != 102) {
            System.out.printf("ANTECIPAÇÃO divergente: cobrado=%d custo=%d%n", cobrado, custo); fail++;
        }
        long reserva8 = (8201 - cobrado) * 800 / 10_000;
        if (reserva8 != 641) { System.out.println("RESERVA 8% divergente: " + reserva8); fail++; }

        if (fail == 0) System.out.println("[4] adversariais ......... todos os exemplos dos documentos conferem");
        return fail;
    }
}
