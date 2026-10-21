package com.paybot.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Md5Signer — Chuyên trách tạo mã băm MD5 và kiểm tra chữ ký số cho API thẻ cào & Webhook.
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn tính năng băm mật mã thành class đơn nhiệm độc lập.
 */
public final class Md5Signer {

    private Md5Signer() {}

    /**
     * Băm chuỗi đầu vào thành mã hex MD5 viết thường.
     * @param input Chuỗi văn bản cần băm
     * @return Chuỗi băm hex MD5, hoặc chuỗi rỗng nếu có lỗi
     */
    public static String hash(String input) {
        if (input == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Xác thực chữ ký đối tác gửi về so với chữ ký kỳ vọng.
     * @param incomingSign Chữ ký nhận được từ webhook/callback
     * @param rawData Chuỗi nguyên bản dùng để tính chữ ký kỳ vọng
     * @return true nếu chữ ký hợp lệ, false nếu giả mạo hoặc sai lệch
     */
    public static boolean verify(String incomingSign, String rawData) {
        if (incomingSign == null || incomingSign.trim().isEmpty()) return false;
        String expected = hash(rawData);
        return expected.equalsIgnoreCase(incomingSign.trim());
    }
}
