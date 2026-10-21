package com.paybot.utils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * SePayQrUrlBuilder — Chuyên trách xây dựng URL gọi SePay Direct PNG API (Lớp 2).
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn logic xây dựng URL dịch vụ SePay thành class đơn nhiệm.
 * Trả về đường dẫn ảnh bitmap PNG trực tiếp, có thể tải về bằng ImageIO.read().
 */
public final class SePayQrUrlBuilder {

    private static final String SEPAY_QR_BASE_URL = "https://qr.sepay.vn/img";

    private SePayQrUrlBuilder() {}

    /**
     * Xây dựng URL ảnh QR SePay direct PNG.
     * @param bankName Tên hoặc mã ngân hàng
     * @param accountNumber Số tài khoản
     * @param amount Số tiền
     * @param description Nội dung chuyển khoản (InvoiceId)
     * @return URL ảnh PNG hoàn chỉnh
     */
    public static String buildUrl(String bankName, String accountNumber, int amount, String description) {
        String bank = (bankName != null) ? bankName.trim() : "";
        String acc = (accountNumber != null) ? accountNumber.trim() : "";
        String des = (description != null) ? description.trim() : "";

        try {
            return String.format("%s?acc=%s&bank=%s&amount=%d&des=%s&template=compact",
                    SEPAY_QR_BASE_URL,
                    URLEncoder.encode(acc, StandardCharsets.UTF_8),
                    URLEncoder.encode(bank, StandardCharsets.UTF_8),
                    amount,
                    URLEncoder.encode(des, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return SEPAY_QR_BASE_URL + "?acc=" + acc + "&bank=" + bank + "&amount=" + amount + "&des=" + des + "&template=compact";
        }
    }
}
