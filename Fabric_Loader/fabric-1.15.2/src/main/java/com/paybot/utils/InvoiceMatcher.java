package com.paybot.utils;

import java.util.regex.Pattern;

/**
 * InvoiceMatcher — v5.5.8 Part 122 (Tuân thủ Rule 17)
 * <p>
 * Lớp chuyên trách so khớp mã hóa đơn thanh toán trong nội dung chuyển khoản ngân hàng.
 * Thay thế cơ chế {@code content.contains(invoiceId)} lỏng lẻo bằng Regex Tokenization
 * có ranh giới từ (word boundary), loại bỏ 100% rủi ro false-positive nhận nhầm chuỗi con.
 */
public final class InvoiceMatcher {

    private InvoiceMatcher() {
        // Utility class
    }

    /**
     * So khớp chính xác xem invoiceId có xuất hiện như một token độc lập trong nội dung CK hay không.
     *
     * @param content   Nội dung giao dịch chuyển khoản từ ngân hàng / SePay
     * @param invoiceId Mã đơn cần so khớp (ví dụ PB123456)
     * @return true nếu khớp chính xác token
     */
    public static boolean matches(String content, String invoiceId) {
        if (content == null || invoiceId == null) {
            return false;
        }
        String cleanInvoice = invoiceId.trim();
        if (cleanInvoice.isEmpty()) {
            return false;
        }

        // Tạo pattern có ranh giới token: bắt đầu bằng đầu chuỗi hoặc ký tự không phải chữ/số/gạch dưới,
        // kết thúc bằng cuối chuỗi hoặc ký tự không phải chữ/số/gạch dưới.
        Pattern pattern = Pattern.compile("(?i)(?:^|[^A-Za-z0-9_-])" + Pattern.quote(cleanInvoice) + "(?:$|[^A-Za-z0-9_-])");
        return pattern.matcher(content).find();
    }
}
