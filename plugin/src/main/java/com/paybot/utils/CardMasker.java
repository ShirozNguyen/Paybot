package com.paybot.utils;

/**
 * CardMasker — v5.5.5 Part 116 (Tuân thủ Rule 17)
 * <p>
 * Chuyên trách duy nhất việc che giấu thông tin nhạy cảm của thẻ cào
 * (Mã thẻ PIN và Số Serial) trước khi ghi log hoặc hiển thị đối soát.
 * Đảm bảo tuân thủ tiêu chuẩn bảo mật P0 (Mục 36 & 100 của Master Spec).
 */
public final class CardMasker {

    private CardMasker() {
        // Utility class
    }

    /**
     * Che giấu mã thẻ cào hoặc số serial.
     * Ví dụ:
     * - "123456789012" -> "1234****12"
     * - "123456"       -> "12****6"
     * - "123"          -> "****"
     *
     * @param rawCode Mã thẻ hoặc serial gốc
     * @return Chuỗi đã được làm mờ an toàn
     */
    public static String mask(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return "N/A";
        }
        String s = rawCode.trim();
        int len = s.length();

        if (len <= 4) {
            return "****";
        }

        if (len <= 8) {
            return s.substring(0, 2) + "****" + s.substring(len - 1);
        }

        return s.substring(0, 4) + "****" + s.substring(len - 2);
    }
}
