package com.paybot.managers;

import com.paybot.PayBotPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OfflineRewardManager — v5.1.0 (Refactored to SQLite)
 * <p>
 * Lưu phần thưởng chờ cho player đang offline. Dữ liệu persist bằng SQLite
 * (thay vì offline-rewards.yml như trước). Migration tự động khi khởi động
 * lần đầu sau upgrade (xem DatabaseManager.migrateOfflineRewards()).
 * <p>
 * Changelog:
 *   v4.1.0 — Tái tạo từ .class
 *   v5.1.0 — Migrate sang SQLite
 */
public class OfflineRewardManager {

    /** TTL reward offline: 7 ngày. */
    private static final long TTL_MS = 7L * 24 * 60 * 60 * 1000;

    private final PayBotPlugin plugin;
    private final DatabaseManager db;

    /**
     * Set các playerName (lowercase) đang có phần thưởng chờ.
     * Sử dụng ConcurrentHashMap.newKeySet() thread-safe, không bao giờ chứa null.
     */
    private final Set<String> pendingPlayers = ConcurrentHashMap.newKeySet();

    /**
     * playerName (lowercase) → Danh sách phần thưởng đã được tải từ DB vào bộ nhớ đệm.
     */
    private final Map<String, List<Map<String, String>>> cache = new ConcurrentHashMap<>();

    public OfflineRewardManager(PayBotPlugin plugin) {
        this.plugin = plugin;
        this.db     = plugin.getDatabaseManager();
        // Nạp danh sách tên người chơi có đơn chờ để hasPendingRewards() đạt tốc độ O(1)
        Set<String> playerNames = db.getPlayersWithPendingRewards();
        for (String name : playerNames) {
            if (name != null && !name.trim().isEmpty()) {
                pendingPlayers.add(name.toLowerCase().trim());
            }
        }
    }

    // ─── API ──────────────────────────────────────────────────────────────────

    /**
     * Thêm reward vào hàng chờ cho player offline.
     */
    public synchronized void addReward(String rewardId, String playerName,
                                        String rawCmd, String rewardAmt,
                                        String denomVnd, String type,
                                        String invoiceId, String discordUid) {
        long now = System.currentTimeMillis();
        db.insertOfflineReward(rewardId, playerName, rawCmd, rewardAmt,
                denomVnd, type, invoiceId, discordUid, now);

        String key = playerName.toLowerCase().trim();
        pendingPlayers.add(key);
        cache.remove(key); // Invalidate cache để load lại dữ liệu mới nhất từ DB khi cần

        NotificationManager.log(plugin, "reward-queued-offline",
                "[OfflineRewards] Đã lưu reward cho " + playerName + " (type=" + type + ")");
    }

    public synchronized List<Map<String, String>> getRewardsForPlayer(String playerName) {
        String key = playerName.toLowerCase().trim();
        if (!pendingPlayers.contains(key)) {
            return Collections.emptyList();
        }

        List<Map<String, String>> cached = cache.get(key);
        if (cached == null || cached.isEmpty()) {
            // Lazy load từ CSDL
            cached = db.getOfflineRewardsForPlayer(playerName);
            if (cached == null || cached.isEmpty()) {
                pendingPlayers.remove(key);
                cache.remove(key);
                return Collections.emptyList();
            }
            cache.put(key, cached);
        }
        return cached;
    }

    public boolean hasPendingRewards(String playerName) {
        if (playerName == null) return false;
        return pendingPlayers.contains(playerName.toLowerCase().trim());
    }

    /**
     * Trả về tập hợp tên người chơi đang có phần thưởng chờ (dùng để tối ưu zero-lag cho autoRewardPollTask).
     */
    public Set<String> getPendingPlayerNames() {
        return new HashSet<>(pendingPlayers);
    }

    public synchronized void removeReward(String playerName, String rewardId) {
        db.deleteOfflineReward(rewardId);
        String key = playerName.toLowerCase().trim();
        cache.remove(key);

        // Kiểm tra xem còn reward nào không
        List<Map<String, String>> remaining = db.getOfflineRewardsForPlayer(playerName);
        if (remaining == null || remaining.isEmpty()) {
            pendingPlayers.remove(key);
        } else {
            cache.put(key, remaining);
        }
    }

    public synchronized boolean claimReward(String rewardId) {
        return db.claimOfflineReward(rewardId);
    }

    public synchronized void completeReward(String playerName, String rewardId) {
        removeReward(playerName, rewardId);
    }

    public synchronized void failReward(String playerName, String rewardId) {
        db.failOfflineReward(rewardId);
        String key = playerName.toLowerCase().trim();
        cache.remove(key);
    }

    /**
     * Kiểm tra và xoá reward quá cũ (hơn 7 ngày). Gọi định kỳ mỗi giờ.
     */
    public synchronized void checkAndExpireOldRewards() {
        long cutoff = System.currentTimeMillis() - TTL_MS;
        int expired = db.deleteExpiredOfflineRewards(cutoff);
        if (expired > 0) {
            plugin.getLogger().info("[OfflineRewards] Đã xoá " + expired + " reward hết hạn (> 7 ngày).");
            pendingPlayers.clear();
            cache.clear();
            for (String name : db.getPlayersWithPendingRewards()) {
                if (name != null && !name.trim().isEmpty()) {
                    pendingPlayers.add(name.toLowerCase().trim());
                }
            }
        }
    }

    /**
     * Khôi phục danh sách reward từ bot (legacy — chỉ dùng khi bot-url còn được cấu hình).
     */
    public synchronized void restoreFromBot(List<Map<String, String>> restored) {
        for (Map<String, String> r : restored) {
            String playerName = r.getOrDefault("playerName", "");
            String rewardId   = r.getOrDefault("rewardId",   "");
            if (playerName.isEmpty() || rewardId.isEmpty()) continue;

            String rawCmd     = r.getOrDefault("rewardCmd",    "");
            String rewardAmt  = r.getOrDefault("rewardAmount", "0");
            String denomVnd   = r.getOrDefault("denom",        "");
            String type       = r.getOrDefault("type",         "card");
            String invoiceId  = r.getOrDefault("invoiceId",    "");
            String discordUid = r.getOrDefault("discordUid",   "");
            long createdAt;
            try { createdAt = Long.parseLong(r.getOrDefault("createdAt", "0")); }
            catch (NumberFormatException e) { createdAt = System.currentTimeMillis(); }

            // insertOfflineReward dùng INSERT OR IGNORE → tự không thêm trùng
            db.insertOfflineReward(rewardId, playerName, rawCmd, rewardAmt,
                    denomVnd, type, invoiceId, discordUid, createdAt);
            String key = playerName.toLowerCase().trim();
            pendingPlayers.add(key);
            cache.remove(key);
        }
    }
}

