package com.paybot.milestone;

import java.util.Locale;

/**
 * Đơn vị chu kỳ thời gian cho hệ thống tự động Reset mốc nạp (Scheduled Reset).
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm biểu diễn và chuẩn hóa đơn vị chu kỳ.
 */
public enum MilestoneResetUnit {
    MINUTE(60_000L),
    HOUR(3_600_000L),
    DAILY(86_400_000L),
    MONTHLY(30L * 86_400_000L),
    YEARLY(365L * 86_400_000L);

    private final long approximateMillis;

    MilestoneResetUnit(long approximateMillis) {
        this.approximateMillis = approximateMillis;
    }

    public long getApproximateMillis() {
        return approximateMillis;
    }

    public static MilestoneResetUnit fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return MONTHLY;
        }
        String clean = raw.trim().toLowerCase(Locale.ROOT);
        return switch (clean) {
            case "minute", "minutes", "min", "m" -> MINUTE;
            case "hour", "hours", "h" -> HOUR;
            case "daily", "day", "days", "d" -> DAILY;
            case "monthly", "month", "months" -> MONTHLY;
            case "yearly", "year", "years", "y" -> YEARLY;
            default -> MONTHLY;
        };
    }
}
