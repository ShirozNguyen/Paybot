package com.naptien.utils;

import com.naptien.PayBotMod;

/**
 * PayBotDebug — v5.5.5 (Fabric)
 *
 * Trung tâm log lỗi/warning chi tiết khi config "debug-mode: true".
 * Thay cho các khối `catch (Throwable ignored) {}` rải rác toàn module (xem
 * BÁO CÁO PHÂN TÍCH Mục 5.6 — 166 vị trí riêng ở module này, phân loại Nhóm A/B).
 *
 * Mặc định (debug-mode=false): hoàn toàn im lặng, giống hệt hành vi trước khi có class này.
 * Khi bật: log WARNING qua PayBotMod.LOGGER (SLF4J) kèm context + stacktrace nếu có.
 *
 * Độc lập hoàn toàn với PayBotDebug bên plugin/forge — không share code, đúng "Rule 17"
 * (mỗi module tự chứa, không phụ thuộc chéo) đã áp dụng xuyên suốt dự án.
 */
public final class PayBotDebug {

    private static volatile boolean enabled = false;

    private PayBotDebug() {}

    /** Gọi trong lúc load config (PayBotConfig.load()/reload) để đồng bộ giá trị mới nhất. */
    public static void setEnabled(boolean value) {
        boolean was = enabled;
        enabled = value;
        if (was != value) {
            PayBotMod.LOGGER.info("[PayBot] debug-mode = " + value
                    + (value ? " — từ giờ mọi lỗi/warning bị nuốt trước đây sẽ được log chi tiết."
                             : " — trở lại im lặng như mặc định."));
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Dùng thay cho {@code catch (Throwable ignored) {}} ở các vị trí Nhóm B.
     * An toàn khi debug-mode tắt: return ngay, không tốn chi phí ngoài 1 lần đọc boolean.
     *
     * @param context Mô tả ngắn, đủ rõ nơi lỗi xảy ra để tra ra ngay không cần đọc lại source.
     * @param t       Throwable gốc bị nuốt trước đây, hoặc {@code null} nếu chỉ là 1 tình huống
     *                bất thường không gắn với exception cụ thể.
     */
    public static void logSwallowed(String context, Throwable t) {
        if (!enabled) return;
        if (t != null) {
            PayBotMod.LOGGER.warn("[PayBot-Debug] {} — {}: {}", context, t.getClass().getSimpleName(), t.getMessage(), t);
        } else {
            PayBotMod.LOGGER.warn("[PayBot-Debug] {}", context);
        }
    }
}
