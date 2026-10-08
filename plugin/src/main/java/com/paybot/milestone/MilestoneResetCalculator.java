package com.paybot.milestone;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Thuật toán tính toán chu kỳ thời gian và thời điểm kích hoạt tự động reset mốc nạp.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm phụ trách tính toán chu kỳ reset.
 */
public final class MilestoneResetCalculator {

    private MilestoneResetCalculator() {}

    /**
     * Xác định xem thời điểm hiện tại đã vượt qua hạn reset mốc nạp so với lần reset trước hay chưa.
     */
    public static boolean shouldReset(long currentMillis, long lastResetMillis, MilestoneResetUnit unit, int interval) {
        if (lastResetMillis <= 0) {
            return false; // Lần đầu ghi nhận timestamp, chưa đủ điều kiện reset
        }
        long nextReset = calculateNextResetMillis(lastResetMillis, unit, interval);
        return currentMillis >= nextReset;
    }

    /**
     * Tính toán timestamp mili-giây của lần reset tiếp theo theo múi giờ hệ thống.
     */
    public static long calculateNextResetMillis(long lastResetMillis, MilestoneResetUnit unit, int interval) {
        if (lastResetMillis <= 0) {
            return System.currentTimeMillis();
        }

        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime last = Instant.ofEpochMilli(lastResetMillis).atZone(zone);

        ZonedDateTime next = switch (unit) {
            case MINUTE -> last.plusMinutes(interval);
            case HOUR -> last.plusHours(interval);
            case DAILY -> last.truncatedTo(ChronoUnit.DAYS).plusDays(interval);
            case MONTHLY -> last.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).plusMonths(interval);
            case YEARLY -> last.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS).plusYears(interval);
        };

        return next.toInstant().toEpochMilli();
    }
}
