package com.naptien.managers;

import com.naptien.NapTienPlugin;

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

    private final NapTienPlugin plugin;
    private final DatabaseManager db;

    /**
     * playerName (lowercase) → List of reward maps.
     * In-memory cache để hasPendingRewards() nhanh (gọi mỗi khi player join).
     */
    private final Map<String, List<Map<String, String>>> cache = new ConcurrentHashMap<>();

    public OfflineRewardManager(NapTienPlugin plugin) {
        this.plugin = plugin;
        this.db     = plugin.getDatabaseManager();
        // Không cần load toàn bộ vào RAM lúc khởi động —
        // query DB theo player khi cần (lazy load per-player).
        // Nhưng cần biết DANH SÁCH player nào có reward để hasPendingRewards()
        // hoạt động nhanh → load chỉ player names.
        Set<String> playerNames = db.getPlayersWithPendingRewards();
        for (String name : playerNames) {
            // Đánh dấu "có data" bằng cách put list rỗng làm sentinel;
            // list thực sẽ được load lazy khi getRewardsForPlayer() được gọi.
            cache.put(name, null); // null = chưa load, nhưng biết là có
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

        // Invalidate cache để lần sau load lại từ DB
        cache.put(playerName.toLowerCase(), null);

        NotificationManager.log(plugin, "reward-queued-offline",
                "[OfflineRewards] Đã lưu reward cho " + playerName + " (type=" + type + ")");
    }

    public synchronized List<Map<String, String>> getRewardsForPlayer(String playerName) {
        String key = playerName.toLowerCase();
        List<Map<String, String>> cached = cache.get(key);
        // null = sentinel "có data nhưng chưa load", cần query DB
        // key không có trong map = chắc chắn không có data
        if (!cache.containsKey(key)) return Collections.emptyList();
        if (cached == null || cached.isEmpty()) {
            // Load từ DB và cache lại
            cached = db.getOfflineRewardsForPlayer(playerName);
            if (cached.isEmpty()) {
                cache.remove(key); // không còn gì → xoá khỏi cache
            } else {
                cache.put(key, cached);
            }
        }
        return cached != null ? cached : Collections.emptyList();
    }

    public boolean hasPendingRewards(String playerName) {
        return cache.containsKey(playerName.toLowerCase());
    }

    public synchronized void removeReward(String playerName, String rewardId) {
        db.deleteOfflineReward(rewardId);
        // Invalidate cache
        String key = playerName.toLowerCase();
        cache.put(key, null); // force reload lần sau
        // Kiểm tra còn reward nào không → nếu không, xoá khỏi cache
        List<Map<String, String>> remaining = db.getOfflineRewardsForPlayer(playerName);
        if (remaining.isEmpty()) {
            cache.remove(key);
        } else {
            cache.put(key, remaining);
        }
    }

    public synchronized Set<String> getPendingPlayerNames() {
        return new HashSet<>(cache.keySet());
    }

    /**
     * Kiểm tra và xoá reward quá cũ (hơn 7 ngày). Gọi định kỳ mỗi giờ.
     */
    public synchronized void checkAndExpireOldRewards() {
        long cutoff = System.currentTimeMillis() - TTL_MS;
        int expired = db.deleteExpiredOfflineRewards(cutoff);
        if (expired > 0) {
            plugin.getLogger().info("[OfflineRewards] Đã xoá " + expired + " reward hết hạn (> 7 ngày).");
            // Reload danh sách player có reward
            cache.clear();
            for (String name : db.getPlayersWithPendingRewards()) {
                cache.put(name, null);
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
            cache.put(playerName.toLowerCase(), null);
        }
    }
}
