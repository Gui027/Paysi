package com.paysi.reports.app;

import com.paysi.reports.app.ReportsModels.AxisTick;
import com.paysi.reports.app.ReportsModels.Chart;
import com.paysi.reports.app.ReportsModels.ChartPoint;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Eixo "redondo" (1, 2 ou 5 × 10^n) com cinco divisões, e a altura de cada barra em porcentagem do eixo. */
final class ChartScale {
    private static final int DIVISIONS = 5;

    private ChartScale() { }

    static Chart build(List<String> labels, List<Long> first, List<Long> second) {
        long max = 1;
        for (long value : first) max = Math.max(max, value);
        if (second != null) for (long value : second) max = Math.max(max, value);
        long step = niceStep((max + DIVISIONS - 1) / DIVISIONS);
        long axisMax = step * DIVISIONS;
        List<AxisTick> ticks = new ArrayList<>();
        for (int index = 0; index <= DIVISIONS; index++) {
            ticks.add(new AxisTick(step * index, percent(index, DIVISIONS)));
        }
        List<ChartPoint> points = new ArrayList<>();
        for (int index = 0; index < labels.size(); index++) {
            long a = first.get(index);
            long b = second == null ? 0 : second.get(index);
            points.add(new ChartPoint(labels.get(index), a, percent(a, axisMax), b, percent(b, axisMax)));
        }
        return new Chart(points, ticks);
    }

    static Chart empty() {
        return build(List.of(), List.of(), null);
    }

    static long niceStep(long rough) {
        long magnitude = 1;
        while (magnitude * 10 <= rough) magnitude *= 10;
        for (long factor : new long[] {1, 2, 5, 10}) {
            if (factor * magnitude >= rough) return factor * magnitude;
        }
        return 10 * magnitude;
    }

    private static String percent(long part, long whole) {
        return BigDecimal.valueOf(part * 100).divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP).toPlainString();
    }
}
