package com.paybot.utils;

import java.util.regex.Pattern;

/**
 * SecuritySanitizer — v5.5.5 Part 116 (Tuân thủ Rule 17)
 * <p>
 * Chuyên trách duy nhất việc làm sạch và kiểm tra dữ liệu đầu vào
 * cho các placeholder trong câu lệnh thưởng Minecraft ([playername], [amount])
 * nhằm ngăn chặn triệt để lỗ hổng Command Injection (Mục 30 của Master Spec).
 */
public final class SecuritySanitizer {

    private static final Pattern VALID_MINECRAFT_NAME = Pattern.compile("^[a-zA-Z0-9_]{2,16}$");
    private static final Pattern DISALLOWED_CHARS = Pattern.compile("[;\\n\\r|&§\"'`\\\\]");

    private SecuritySanitizer() {
        // Utility class
    }

    /**
     * Làm sạch tên người chơi Minecraft, đảm bảo chỉ chứa ký tự hợp lệ [a-zA-Z0-9_].
     * Loại bỏ mọi ký tự ngắt lệnh hoặc điều khiển độc hại.
     *
     * @param playerName Tên người chơi truyền vào
     * @return Tên người chơi đã làm sạch an toàn
     */
    public static String sanitizePlayerName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return "UnknownPlayer";
        }
        String clean = playerName.trim();
        if (VALID_MINECRAFT_NAME.matcher(clean).matches()) {
            return clean;
        }
        // Loại bỏ mọi ký tự không thuộc bảng chữ cái, số hoặc dấu gạch dưới
        String sanitized = clean.replaceAll("[^a-zA-Z0-9_]", "");
        if (sanitized.isEmpty()) {
            return "UnknownPlayer";
        }
        if (sanitized.length() > 16) {
            sanitized = sanitized.substring(0, 16);
        }
        return sanitized;
    }

    /**
     * Làm sạch giá trị số tiền hoặc số lượng thưởng.
     * Loại bỏ triệt để các ký tự phân tách lệnh.
     *
     * @param amountStr Chuỗi số tiền / số lượng
     * @return Chuỗi số lượng an toàn
     */
    public static String sanitizeAmount(String amountStr) {
        if (amountStr == null || amountStr.isBlank()) {
            return "0";
        }
        // Loại bỏ các ký tự nguy hiểm có thể ngắt câu lệnh
        String clean = DISALLOWED_CHARS.matcher(amountStr.trim()).replaceAll("");
        return clean.isEmpty() ? "0" : clean;
    }
}
