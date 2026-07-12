package com.naptien.managers;

import com.naptien.NapTienPlugin;

import java.security.SecureRandom;

/**
 * TransferContentGenerator — sinh mã nội dung chuyển khoản (invoice/transfer content)
 * theo cấu hình admin tự chọn, thay cho format cố định "PB"+12 số trước đây.
 * <p>
 * 4 config liên quan (xem config.yml, đặt ngay dưới {@code plugin-port}):
 * <ul>
 *   <li>{@code transfer-content.prefix} — tiền tố (mặc định "NT"), 2-5 ký tự chữ cái.</li>
 *   <li>{@code transfer-content.require-underscore} — true/false, có chèn "_" ngay
 *       sau tiền tố hay không (vd "NT_..." thay vì "NT...").</li>
 *   <li>{@code transfer-content.code-length} — độ dài phần mã random (KHÔNG tính tiền
 *       tố/underscore), tối đa 30. Vượt quá → CRASH plugin ngay lúc khởi động, ghi rõ lý
 *       do ra console (cố ý — sai cấu hình nghiêm trọng cần admin sửa ngay, không thể
 *       chạy tiếp với giá trị sai vì ảnh hưởng trực tiếp tới việc khớp giao dịch).</li>
 *   <li>{@code transfer-content.code-mode} — {@code 0} = chỉ số (MẶC ĐỊNH, khuyến nghị
 *       mạnh — xem CẢNH BÁO bên dưới); {@code 1} = chỉ chữ (luôn random HOA); {@code -1}
 *       = random cả số và chữ.</li>
 * </ul>
 * <p>
 * Tương thích với hệ thống khớp giao dịch CỦA PLUGIN/MOD/BOT.PY: matching nội bộ
 * (matchPendingByContent / poll_sepay_self_check / handle_sepay_ipn) luôn so SUBSTRING
 * không phân biệt hoa/thường — đổi prefix/độ dài/kiểu ký tự KHÔNG ảnh hưởng matching nội
 * bộ này.
 * <p>
 * <b>CẢNH BÁO QUAN TRỌNG — code-mode KHÔNG chỉ ảnh hưởng matching nội bộ:</b> SePay tự
 * detect "payment code" trong nội dung chuyển khoản theo pattern riêng của SePay (dạng
 * PREFIX+SỐ, vd {@code NT[0-9]+}). Nếu cấu hình webhook SePay bật {@code skip_if_no_code
 * = true} (mặc định khi tạo webhook qua dashboard SePay) và nội dung KHÔNG khớp pattern
 * đó (vd lẫn chữ A-F do {@code code-mode: -1}), SePay sẽ ÂM THẦM KHÔNG gửi webhook cho
 * giao dịch đó — không lỗi, không log, chỉ đơn giản là không bao giờ nhận được tiền dù
 * khách đã chuyển khoản thành công. Đây CHÍNH XÁC là bug đã từng gặp và fix ở bot.py
 * {@code new_invoice_id()} (xem comment "BUG 1 FIX" ở đó) — TransferContentGenerator
 * (thêm mới ở v5.0.0) đã vô tình tái lập lại đúng bug này do default code-mode=-1. Vì
 * vậy {@code code-mode} mặc định đã đổi về {@code 0} (chỉ số) từ v5.0.2 — CHỈ đổi sang
 * {@code -1}/{@code 1} nếu chắc chắn webhook SePay đã tắt skip_if_no_code, hoặc đang
 * dùng hoàn toàn SePay API self-poll (sepay-api.enabled=true, không qua webhook).
 *
 * Changelog: v5.0.0 — Thêm mới (thay generateInvoiceId() cố định "PB"+12 số trong
 * NapBankCommand.java).
 *            v5.0.2 — FIX: default prefix "PB"→"NT", code-mode -1→0 (xem CẢNH BÁO ở
 *            trên — tránh SePay âm thầm bỏ qua webhook do skip_if_no_code).
 */
public final class TransferContentGenerator {

    private TransferContentGenerator() {}

    public static final int MAX_CODE_LENGTH = 30;

    private static final String DIGITS  = "0123456789";
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RNG = new SecureRandom();

    /**
     * Đọc config + validate độ dài — gọi 1 LẦN lúc plugin khởi động (onEnable/migrateConfig)
     * để CRASH SỚM (fail-fast) nếu admin set sai, thay vì lỗi âm thầm lúc tạo đơn thật.
     *
     * @throws IllegalStateException nếu code-length vượt quá {@link #MAX_CODE_LENGTH}
     */
    public static void validateConfig(NapTienPlugin plugin) {
        int length = plugin.getConfig().getInt("transfer-content.code-length", 12);
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
        // v5.0.2: cảnh báo sớm nếu code-mode khác 0 — xem CẢNH BÁO trong javadoc class
        // này về SePay skip_if_no_code. Chỉ CẢNH BÁO (không crash) vì admin có thể đã
        // tắt skip_if_no_code chủ ý hoặc dùng hẳn sepay-api self-poll (không qua webhook).
        int mode = plugin.getConfig().getInt("transfer-content.code-mode", 0);
        if (mode != 0 && !plugin.getConfig().getBoolean("sepay-api.enabled", false)) {
            plugin.getLogger().warning("[PayBot] CẢNH BÁO: transfer-content.code-mode = " + mode
                    + " (không phải 0/chỉ-số) trong khi đang dùng webhook SePay (sepay-api chưa bật). "
                    + "Nếu webhook SePay đang bật \"skip_if_no_code\" (mặc định), giao dịch có lẫn chữ "
                    + "trong nội dung CK có thể KHÔNG được SePay gửi webhook — tiền vào nhưng plugin "
                    + "không hề biết. Khuyến nghị đặt code-mode: 0 trừ khi chắc chắn đã tắt "
                    + "skip_if_no_code phía dashboard SePay.");
        }
    }

    /** Sinh 1 mã nội dung chuyển khoản mới theo đúng config hiện tại. */
    public static String generate(NapTienPlugin plugin) {
        String prefix = plugin.getConfig().getString("transfer-content.prefix", "NT").trim();
        if (prefix.isEmpty()) prefix = "NT";
        boolean underscore = plugin.getConfig().getBoolean("transfer-content.require-underscore", false);
        int     length     = plugin.getConfig().getInt("transfer-content.code-length", 12);
        int     mode       = plugin.getConfig().getInt("transfer-content.code-mode", 0);

        StringBuilder sb = new StringBuilder(prefix.toUpperCase());
        if (underscore) sb.append('_');

        String pool = switch (mode) {
            case 1  -> LETTERS;         // chỉ chữ
            case -1 -> DIGITS + LETTERS; // cả số và chữ
            default -> DIGITS;          // 0 (hoặc giá trị lạ khác) → CHỈ SỐ — an toàn nhất cho SePay
        };
        for (int i = 0; i < length; i++) {
            sb.append(pool.charAt(RNG.nextInt(pool.length())));
        }
        return sb.toString();
    }
}
