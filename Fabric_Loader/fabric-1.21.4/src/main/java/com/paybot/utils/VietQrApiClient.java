package com.paybot.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * VietQrApiClient — Chuyên trách giao tiếp với VietQR Official API v2 (Lớp 3).
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn API Client bên thứ ba thành class đơn nhiệm.
 * Dùng làm phương án dự phòng (Fallback 2) khi Lớp 1 và Lớp 2 gặp sự cố kết nối.
 */
public final class VietQrApiClient {

    private static final String VIETQR_GENERATE_API = "https://api.vietqr.io/v2/generate";

    private VietQrApiClient() {}

    /**
     * Gửi yêu cầu tới VietQR API v2 để lấy chuỗi mã hóa EMVCo chính thống.
     * @param bin Mã BIN ngân hàng (6 số)
     * @param accountNumber Số tài khoản
     * @param accountName Tên chủ tài khoản
     * @param amount Số tiền
     * @param description Nội dung chuyển khoản
     * @return Chuỗi EMVCo qrCode, hoặc null nếu lỗi
     */
    public static String fetchEmvcoString(String bin, String accountNumber, String accountName, int amount, String description) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(VIETQR_GENERATE_API).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("User-Agent", "PayBot/7.0.0");

            String jsonPayload = String.format(
                    "{\"accountNo\":\"%s\",\"accountName\":\"%s\",\"acqId\":%s,\"amount\":%d,\"addInfo\":\"%s\",\"format\":\"text\",\"template\":\"compact\"}",
                    escapeJson(accountNumber),
                    escapeJson(accountName),
                    bin,
                    amount,
                    escapeJson(description)
            );

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                return null;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            String response = sb.toString();
            // Trích xuất trường "qrCode" từ response JSON mà không cần thêm thư viện json nặng
            int idx = response.indexOf("\"qrCode\":\"");
            if (idx != -1) {
                int start = idx + 10;
                int end = response.indexOf("\"", start);
                if (end > start) {
                    return response.substring(start, end).replace("\\/", "/");
                }
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
