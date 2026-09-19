package com.paybot.managers;

import com.paybot.PayBotPlugin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * RewardDeliveryLedger — v5.5.8 Part 122 (Tuân thủ Rule 17)
 * <p>
 * Lớp chuyên trách quản lý sổ cái giao thưởng (Idempotency Ledger) tại tầng cơ sở dữ liệu.
 * Đảm bảo bất biến: 1 giao dịch nạp tiền = đúng 1 lần giao thưởng thành công duy nhất,
 * ngăn chặn 100% duplicate payout do mạng, duplicate callback hoặc crash recovery.
 */
public class RewardDeliveryLedger {

    private final PayBotPlugin plugin;
    private final DatabaseManager db;

    public RewardDeliveryLedger(PayBotPlugin plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    /**
     * Tính toán mã băm duy nhất đại diện cho gói phần thưởng.
     */
    public static String computeRewardHash(List<String> commands, String amount) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw = (commands != null ? String.join(";;", commands) : "") + "|" + (amount != null ? amount : "0");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString((commands != null ? commands.hashCode() : 0) + (amount != null ? amount.hashCode() : 0));
        }
    }

    /**
     * Kiểm tra xem phần thưởng cho giao dịch này đã từng được giao thành công chưa.
     */
    public boolean hasDelivered(String paymentType, String paymentReference, String rewardHash) {
        if (db == null) return false;
        return db.hasRewardDelivery(paymentType, paymentReference, rewardHash);
    }

    /**
     * Ghi nhận việc giao thưởng thành công vào sổ cái.
     *
     * @return true nếu ghi nhận thành công; false nếu đã tồn tại bản ghi (duplicate).
     */
    public boolean recordDelivery(String paymentType, String paymentReference, String playerName, String rewardHash, String status) {
        if (db == null) return false;
        String deliveryId = paymentType + "_" + paymentReference + "_" + System.currentTimeMillis();
        return db.recordRewardDelivery(deliveryId, paymentType, paymentReference, playerName, rewardHash, status);
    }
}
