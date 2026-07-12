package com.naptien.log;

import com.naptien.NapTienPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

/**
 * LogManager — Ghi log mỗi khi phát hiện giao dịch nạp thẻ hoặc ngân hàng.
 *
 * Cấu trúc thư mục (trong plugin data folder = plugins/PayBot/):
 *   plugins/PayBot/Card/card-log.txt   — toàn bộ lịch sử nạp thẻ
 *   plugins/PayBot/Bank/bank-log.txt   — toàn bộ lịch sử nạp ngân hàng
 *
 * Mỗi lần giao dịch được phát hiện → append 1 entry vào file tương ứng.
 * File KHÔNG chia theo ngày — tất cả giao dịch nằm trong 1 file liên tục.
 *
 * Format nạp thẻ:
 *   Thông tin nạp thẻ của thành viên:
 *   Tên player nạp: ...
 *   Ngày nạp: dd/MM/yyyy HH:mm:ss
 *   Mệnh giá nạp: ... VND
 *   Mã thẻ: ...
 *   Serial thẻ: ...
 *   Mã đơn: ...
 *   Trạng thái nạp: ...
 *   [5 dòng trắng phân cách]
 *
 * Format nạp bank:
 *   Thông tin nạp bank của thành viên:
 *   Tên player nạp: ...
 *   Ngày nạp: dd/MM/yyyy HH:mm:ss
 *   Mệnh giá nạp: ... VND
 *   Mã đơn: ...
 *   Trạng thái nạp: ...
 *   [5 dòng trắng phân cách]
 *
 * Changelog:
 *   v4.1.0 — Tạo mới: log per-transaction (không phải per-day),
 *             mỗi giao dịch phát hiện → ghi ngay vào file
 */
public class LogManager {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    private final File cardLogFile;
    private final File bankLogFile;
    private final NapTienPlugin plugin;

    public LogManager(NapTienPlugin plugin) {
        this.plugin = plugin;
        File cardDir = new File(plugin.getDataFolder(), "Card");
        File bankDir = new File(plugin.getDataFolder(), "Bank");
        cardDir.mkdirs();
        bankDir.mkdirs();
        this.cardLogFile = new File(cardDir, "card-log.txt");
        this.bankLogFile = new File(bankDir, "bank-log.txt");
    }

    // ─── Card log ─────────────────────────────────────────────────────────────

    /**
     * Ghi entry nạp thẻ ngay khi phát hiện giao dịch (hoặc cập nhật trạng thái).
     *
     * @param playerName  tên player nạp
     * @param denom       mệnh giá (VND)
     * @param cardCode    mã thẻ
     * @param cardSerial  serial thẻ
     * @param requestId   mã đơn từ web thứ 3 (request ID card API)
     * @param status      trạng thái nạp (human-readable)
     */
    public synchronized void logCard(String playerName, int denom,
                                     String cardCode, String cardSerial,
                                     String requestId, String status) {
        String dateStr = DATE_FMT.format(new Date());
        String entry = "Thông tin nạp thẻ của thành viên:\n"
                + "Tên player nạp: "   + playerName + "\n"
                + "Ngày nạp: "         + dateStr    + "\n"
                + "Mệnh giá nạp: "     + formatVnd(denom) + " VND\n"
                + "Mã thẻ: "           + cardCode   + "\n"
                + "Serial thẻ: "       + cardSerial + "\n"
                + "Mã đơn: "           + requestId  + "\n"
                + "Trạng thái nạp: "   + status     + "\n"
                + "\n\n\n\n\n";   // 5 dòng trắng
        appendToFile(cardLogFile, entry);
    }

    // ─── Bank log ─────────────────────────────────────────────────────────────

    /**
     * Ghi entry nạp bank ngay khi phát hiện giao dịch (hoặc cập nhật trạng thái).
     *
     * @param playerName  tên player nạp
     * @param amount      số tiền (VND)
     * @param invoiceId   mã đơn / nội dung chuyển khoản
     * @param status      trạng thái nạp (human-readable)
     */
    public synchronized void logBank(String playerName, int amount,
                                     String invoiceId, String status) {
        String dateStr = DATE_FMT.format(new Date());
        String entry = "Thông tin nạp bank của thành viên:\n"
                + "Tên player nạp: "   + playerName + "\n"
                + "Ngày nạp: "         + dateStr    + "\n"
                + "Mệnh giá nạp: "     + formatVnd(amount) + " VND\n"
                + "Mã đơn: "           + invoiceId  + "\n"
                + "Trạng thái nạp: "   + status     + "\n"
                + "\n\n\n\n\n";   // 5 dòng trắng
        appendToFile(bankLogFile, entry);
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private void appendToFile(File file, String content) {
        try (OutputStreamWriter w = new OutputStreamWriter(
                new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
            w.write(content);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "[PayBot] LogManager: không ghi được vào " + file.getName()
                            + " — " + e.getMessage());
        }
    }

    private static String formatVnd(int amount) {
        StringBuilder sb = new StringBuilder(String.valueOf(amount));
        int len = sb.length();
        for (int i = len - 3; i > 0; i -= 3) sb.insert(i, '.');
        return sb.toString();
    }

    // ─── Status label helpers (dùng cho status đọc được) ─────────────────────

    public static String cardStatusLabel(String raw) {
        return switch (raw) {
            case "99"       -> "Đang xử lý";
            case "1"        -> "Thẻ thành công ✓";
            case "2"        -> "Sai mệnh giá ✗";
            case "3"        -> "Thẻ đã sử dụng ✗";
            case "4"        -> "Hệ thống bảo trì ⚠";
            case "100"      -> "Thẻ sai/không hợp lệ ✗";
            case "APPROVED" -> "Đã cấp thưởng ✅";
            default         -> raw;
        };
    }

    public static String bankStatusLabel(String raw) {
        return switch (raw) {
            case "PENDING"  -> "Đang chờ thanh toán ⏳";
            case "PAID"     -> "Đã thanh toán — chờ cấp thưởng ✓";
            case "APPROVED" -> "Đã cấp thưởng ✅";
            case "EXPIRED"  -> "Hết hạn ⏰";
            default         -> raw;
        };
    }
}
