package com.naptien.managers;

import com.naptien.PayBotMod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OfflineRewardManager — v5.1.0 (Refactored to SQLite for Fabric)
 */
public class OfflineRewardManager {

    private static final long TTL_MS = 7L * 24 * 60 * 60 * 1000;

    private final PayBotMod mod;
    private final DatabaseManager db;

    private final Map<String, List<Map<String, String>>> cache = new ConcurrentHashMap<>();

    public OfflineRewardManager(PayBotMod mod) {
        this.mod = mod;
        this.db     = mod.getDatabaseManager();
        Set<String> playerNames = db.getPlayersWithPendingRewards();
        for (String name : playerNames) {
            cache.put(name, null);
        }
    }

    public synchronized void addReward(String rewardId, String playerName,
                                        String rawCmd, String rewardAmt,
                                        String denomVnd, String type,
                                        String invoiceId, String discordUid) {
        long now = System.currentTimeMillis();
        db.insertOfflineReward(rewardId, playerName, rawCmd, rewardAmt,
                denomVnd, type, invoiceId, discordUid, now);

        cache.put(playerName.toLowerCase(), null);
    }

    public synchronized List<Map<String, String>> getRewardsForPlayer(String playerName) {
        String key = playerName.toLowerCase();
        List<Map<String, String>> cached = cache.get(key);
        if (!cache.containsKey(key)) return Collections.emptyList();
        if (cached == null || cached.isEmpty()) {
            cached = db.getOfflineRewardsForPlayer(playerName);
            if (cached.isEmpty()) {
                cache.remove(key);
            } else {
                cache.put(key, cached);
            }
        }
        return cached != null ? cached : Collections.emptyList();
    }

    public boolean hasPendingRewards(String playerName) {
        return cache.containsKey(playerName.toLowerCase());
    }

    public synchronized boolean removeReward(String playerName, String rewardId) {
        boolean removed = db.deleteOfflineReward(rewardId);
        String key = playerName.toLowerCase();
        cache.put(key, null);
        List<Map<String, String>> remaining = db.getOfflineRewardsForPlayer(playerName);
        if (remaining.isEmpty()) {
            cache.remove(key);
        } else {
            cache.put(key, remaining);
        }
        return removed;
    }

    public synchronized Set<String> getPendingPlayerNames() {
        return new HashSet<>(cache.keySet());
    }

    public synchronized void checkAndExpireOldRewards() {
        long cutoff = System.currentTimeMillis() - TTL_MS;
        int expired = db.deleteExpiredOfflineRewards(cutoff);
        if (expired > 0) {
            PayBotMod.LOGGER.info("[OfflineRewards] Đã xoá " + expired + " reward hết hạn (> 7 ngày).");
            cache.clear();
            for (String name : db.getPlayersWithPendingRewards()) {
                cache.put(name, null);
            }
        }
    }

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

            db.insertOfflineReward(rewardId, playerName, rawCmd, rewardAmt,
                    denomVnd, type, invoiceId, discordUid, createdAt);
            cache.put(playerName.toLowerCase(), null);
        }
    }
}
