package com.naptien.log;

import com.naptien.PayBotMod;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * LogManager — Ghi log mỗi khi phát hiện giao dịch.
 * Mỗi giao dịch → append 1 entry vào file log (không chia theo ngày).
 *
 *   PayBot/Card/card-log.txt
 *   PayBot/Bank/bank-log.txt
 *
 * Changelog:
 *   v4.1.0-fabric — Port từ plugin, dùng java.nio.Path thay java.io.File
 */
public class LogManager {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    private final Path cardLogFile;
    private final Path bankLogFile;

    public LogManager(Path dataDir) {
        Path cardDir = dataDir.resolve("Card");
        Path bankDir = dataDir.resolve("Bank");
        try {
            Files.createDirectories(cardDir);
            Files.createDirectories(bankDir);
        } catch (IOException e) {
            PayBotMod.LOGGER.error("[LogManager] Không tạo được thư mục log: " + e.getMessage());
        }
        this.cardLogFile = cardDir.resolve("card-log.txt");
        this.bankLogFile = bankDir.resolve("bank-log.txt");
    }

    // ─── Card ─────────────────────────────────────────────────────────────────

    public synchronized void logCard(String playerName, int denom,
                                     String cardCode, String cardSerial,
                                     String requestId, String status) {
        String entry = "Thông tin nạp thẻ của thành viên:\n"
                + "Tên player nạp: "   + playerName + "\n"
                + "Ngày nạp: "         + DATE_FMT.format(new Date()) + "\n"
                + "Mệnh giá nạp: "     + formatVnd(denom) + " VND\n"
                + "Mã thẻ: "           + cardCode   + "\n"
                + "Serial thẻ: "       + cardSerial + "\n"
                + "Mã đơn: "           + requestId  + "\n"
                + "Trạng thái nạp: "   + status     + "\n"
                + "\n\n\n\n\n";
        append(cardLogFile, entry);
    }

    // ─── Bank ─────────────────────────────────────────────────────────────────

    public synchronized void logBank(String playerName, int amount,
                                     String invoiceId, String status) {
        String entry = "Thông tin nạp bank của thành viên:\n"
                + "Tên player nạp: "   + playerName + "\n"
                + "Ngày nạp: "         + DATE_FMT.format(new Date()) + "\n"
                + "Mệnh giá nạp: "     + formatVnd(amount) + " VND\n"
                + "Mã đơn: "           + invoiceId  + "\n"
                + "Trạng thái nạp: "   + status     + "\n"
                + "\n\n\n\n\n";
        append(bankLogFile, entry);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void append(Path file, String content) {
        try {
            Files.writeString(file, content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            PayBotMod.LOGGER.warn("[LogManager] Không ghi được " + file.getFileName() + ": " + e.getMessage());
        }
    }

    private static String formatVnd(int amount) {
        StringBuilder sb = new StringBuilder(String.valueOf(amount));
        int len = sb.length();
        for (int i = len - 3; i > 0; i -= 3) sb.insert(i, '.');
        return sb.toString();
    }

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
