package com.projectpilot.ui.pages.admin.widgets;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public final class TimeBinning {

    private TimeBinning() {}

    /**
     * Bins epochMillis values into day buckets for the last N days (inclusive of today).
     * Output is length days, index 0 = oldest day, last index = today.
     */
    public static int[] binEpochMillisByDay(List<Long> epochMillis, int days, ZoneId zone) {
        if (days <= 0) return new int[0];
        int[] out = new int[days];

        LocalDate today = LocalDate.now(zone);
        LocalDate start = today.minusDays(days - 1L);

        if (epochMillis == null || epochMillis.isEmpty()) return out;

        for (Long ms : epochMillis) {
            if (ms == null || ms <= 0) continue;
            LocalDate d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate();
            if (d.isBefore(start) || d.isAfter(today)) continue;
            int idx = (int) (d.toEpochDay() - start.toEpochDay());
            if (idx >= 0 && idx < days) out[idx]++;
        }
        return out;
    }

    public static double[] toDoubleSeries(int[] bins) {
        if (bins == null) return new double[0];
        double[] out = new double[bins.length];
        for (int i = 0; i < bins.length; i++) out[i] = bins[i];
        return out;
    }
}
