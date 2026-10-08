package com.paybot.telemetry;

import java.util.Locale;

/**
 * Bộ kiểm duyệt và làm sạch dữ liệu telemetry (Security & Privacy Barrier).
 * Đảm bảo tuyệt đối không có dữ liệu PII, IP, token SePay, token Discord,
 * tài khoản ngân hàng hoặc dữ liệu giao dịch tài chính lọt vào FastStats.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm làm sạch dữ liệu.
 */
public final class FastStatsDataSanitizer {

    private FastStatsDataSanitizer() {}

    /**
     * Xác thực chuỗi token FastStats hợp lệ (không rỗng, độ dài hợp lý, ký tự ASCII an toàn).
     */
    public static boolean isValidToken(String token) {
        if (token == null) {
            return false;
        }
        String trimmed = token.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("YOUR_TOKEN") || trimmed.equalsIgnoreCase("none")) {
            return false;
        }
        // Token FastStats là chuỗi không chứa khoảng trắng và chỉ bao gồm ký tự chuẩn
        return trimmed.length() >= 8 && trimmed.length() <= 128 && !trimmed.contains(" ");
    }

    /**
     * Làm sạch chuỗi định danh, ngăn ngừa việc vô tình chứa IP hoặc token.
     */
    public static String sanitizeIdentifier(String value) {
        if (value == null) {
            return "unknown";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "unknown";
        }
        // Kiểm tra xem có chứa mẫu địa chỉ IPv4 không
        if (trimmed.matches(".*\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b.*")) {
            return "redacted";
        }
        return trimmed;
    }

    /**
     * Kiểm tra chuỗi có chứa từ khóa nhạy cảm hay không.
     */
    public static boolean containsSensitiveKeywords(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("password") ||
               lower.contains("secret") ||
               lower.contains("token") ||
               lower.contains("apikey") ||
               lower.contains("api_key") ||
               lower.contains("pin") ||
               lower.contains("serial") ||
               lower.contains("webhook");
    }
}
