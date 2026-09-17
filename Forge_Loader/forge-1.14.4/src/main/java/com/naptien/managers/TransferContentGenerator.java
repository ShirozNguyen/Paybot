package com.naptien.managers;

import com.naptien.PayBotMod;

import java.security.SecureRandom;

/**
 * TransferContentGenerator (Fabric) — mirror chính xác bản Bukkit/Paper cùng tên, sinh
 * mã nội dung chuyển khoản theo config tuỳ biến thay cho format cố định 30 ký tự random
 * (không tiền tố) trước đây trong StandaloneBankPoller.generateInvoiceId().
 * <p>
 * 4 config liên quan (xem config-template.yml, đặt ngay dưới {@code plugin-port}):
 * {@code transfer-content.prefix}, {@code .require-underscore}, {@code .code-length}
 * (tối đa 30, vượt quá → crash mod lúc khởi động với lý do rõ ràng), {@code .code-mode}
 * ({@code -1} số+chữ | {@code 0} chỉ số | {@code 1} chỉ chữ).
 * <p>
 * Tương thích hệ thống khớp giao dịch: matching luôn so SUBSTRING không phân biệt
 * hoa/thường — đổi format KHÔNG ảnh hưởng gì, miễn đủ dài để tránh trùng ngẫu nhiên.
 *
 * Changelog: v5.0.0 — Thêm mới.
 */
public final class TransferContentGenerator {

    private TransferContentGenerator() {}

    public static final int MAX_CODE_LENGTH = 70;

    private static final String DIGITS  = "0123456789";
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RNG = new SecureRandom();
    private static final java.time.format.DateTimeFormatter DATE_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("dMyyyy");

    /**
     * Đọc config + validate độ dài — gọi 1 LẦN lúc mod khởi động để CRASH SỚM (fail-fast)
     * nếu admin set sai, thay vì lỗi âm thầm lúc tạo đơn thật.
     * @throws IllegalStateException nếu code-length vượt quá {@link #MAX_CODE_LENGTH} hoặc < 1
     */
    public static void validateConfig(PayBotMod mod) {
        int length = mod.getConfig().getInt("transfer-content.code-length", 12);
        if (length > MAX_CODE_LENGTH) {
            throw new IllegalStateException(
                    "[PayBot] CONFIG SAI — transfer-content.code-length = " + length +
                    " VƯỢT QUÁ giới hạn cho phép (" + MAX_CODE_LENGTH + "). " +
                    "Nội dung chuyển khoản quá dài sẽ làm QR khó scan và dễ bị ngân hàng " +
                    "cắt bớt nội dung (gây KHÔNG khớp được giao dịch). " +
                    "Vui lòng sửa 'transfer-content.code-length' trong config.yml về giá trị " +
                    "từ 1 đến " + MAX_CODE_LENGTH + ", sau đó khởi động lại server."
            );
        }
        if (length < 1) {
            throw new IllegalStateException(
                    "[PayBot] CONFIG SAI — transfer-content.code-length = " + length +
                    " không hợp lệ (phải từ 1 đến " + MAX_CODE_LENGTH + "). " +
                    "Vui lòng sửa trong config.yml rồi khởi động lại server."
            );
        }
    }

    /** Sinh 1 mã nội dung chuyển khoản mới theo đúng config hiện tại (tự chống trùng mã nạp vĩnh viễn). */
    public static String generate(PayBotMod mod) {
        String prefix          = mod.getConfig().getString("transfer-content.prefix", "NT").trim();
        if (prefix.isEmpty()) prefix = "NT";
        boolean underscore     = mod.getConfig().getBoolean("transfer-content.require-underscore", false);
        boolean includeDate    = mod.getConfig().getBoolean("transfer-content.include-date-prefix", false);
        boolean checkDuplicate = mod.getConfig().getBoolean("transfer-content.check-duplicate-orders", true);
        int     length         = mod.getConfig().getInt("transfer-content.code-length", 12);
        int     mode           = mod.getConfig().getInt("transfer-content.code-mode", 0);

        String dateStr = includeDate ? java.time.LocalDate.now().format(DATE_FORMATTER) : "";

        String pool = switch (mode) {
            case 1  -> LETTERS;         // chỉ chữ
            case -1 -> DIGITS + LETTERS; // cả số và chữ
            default -> DIGITS;          // 0 → CHỈ SỐ — an toàn nhất cho SePay
        };

        int maxAttempts = checkDuplicate ? 100 : 1;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            StringBuilder sb = new StringBuilder(prefix.toUpperCase());
            if (underscore) sb.append('_');
            if (includeDate) sb.append(dateStr);
            for (int i = 0; i < length; i++) {
                sb.append(pool.charAt(RNG.nextInt(pool.length())));
            }
            String candidate = sb.toString();

            if (!checkDuplicate) {
                return candidate;
            }

            // Kiểm tra xem mã đã từng tồn tại trong cache hoặc CSDL chưa
            boolean existsInCache = mod.getLocalOrderManager() != null && mod.getLocalOrderManager().getBankOrder(candidate) != null;
            boolean existsInDb    = mod.getDatabaseManager() != null && mod.getDatabaseManager().hasBankOrder(candidate);

            if (!existsInCache && !existsInDb) {
                return candidate;
            }
        }

        return prefix.toUpperCase() + (underscore ? "_" : "") + (includeDate ? dateStr : "") + System.currentTimeMillis();
    }
}
