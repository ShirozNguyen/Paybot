package com.naptien.managers;

import com.naptien.NapTienPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * TopupStatsManager — Lưu thống kê tổng số tiền (VND) đã nạp của từng player + toàn
 * server, phục vụ PlaceholderAPI expansion (Phase B — "top nạp", "tổng nạp toàn server"
 * giống reference plugin Card2K).
 * <p>
 * Được cập nhật DUY NHẤT từ {@link RewardDispatcher#deliverNow} — tức chỉ tính những
 * lượt nạp ĐÃ THỰC SỰ giao thưởng thành công (không tính đơn fail/chưa duyệt).
 * <p>
 * File lưu: plugins/PayBot/topup-stats.yml
 *
 * Changelog:
 *   v5.0.0 (Phase B) — Thêm mới.
 */
public class TopupStatsManager {

    private final NapTienPlugin plugin;
    private final File file;
    private final Map<String, Long> playerTotals = new ConcurrentHashMap<>(); // lowercase name -> tổng VND
    private final Map<String, String> displayNames = new ConcurrentHashMap<>(); // lowercase -> tên hiển thị gốc
    private long serverTotal = 0L;

    public TopupStatsManager(NapTienPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "topup-stats.yml");
        load();
    }

    /** Ghi nhận 1 lượt nạp THÀNH CÔNG (đã giao thưởng) — gọi từ RewardDispatcher. */
    public synchronized void recordTopup(String playerName, long amountVnd) {
        if (playerName == null || playerName.isEmpty() || amountVnd <= 0) return;
        String key = playerName.toLowerCase();
        playerTotals.merge(key, amountVnd, Long::sum);
        displayNames.put(key, playerName);
        serverTotal += amountVnd;
        save();
    }

    public long getPlayerTotal(String playerName) {
        return playerTotals.getOrDefault(playerName.toLowerCase(), 0L);
    }

    public long getServerTotal() {
        return serverTotal;
    }

    /** Top N player nạp nhiều nhất, sắp giảm dần. */
    public List<Map.Entry<String, Long>> getTopPlayers(int limit) {
        return playerTotals.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(Math.max(0, limit))
                .map(e -> Map.entry(displayNames.getOrDefault(e.getKey(), e.getKey()), e.getValue()))
                .collect(Collectors.toList());
    }

    public int getTotalPlayerCount() {
        return playerTotals.size();
    }

    private synchronized void load() {
        if (!file.exists()) return;
        try {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            serverTotal = yml.getLong("server-total", 0L);
            if (yml.isConfigurationSection("players")) {
                for (String key : yml.getConfigurationSection("players").getKeys(false)) {
                    long total = yml.getLong("players." + key + ".total", 0L);
                    String display = yml.getString("players." + key + ".name", key);
                    playerTotals.put(key, total);
                    displayNames.put(key, display);
                }
            }
        } catch (Exception e) {
            NotificationManager.warn(plugin, "http-error", "[PayBot] Lỗi đọc topup-stats.yml: " + e.getMessage());
        }
    }

    private synchronized void save() {
        try {
            YamlConfiguration yml = new YamlConfiguration();
            yml.set("server-total", serverTotal);
            for (Map.Entry<String, Long> e : playerTotals.entrySet()) {
                yml.set("players." + e.getKey() + ".total", e.getValue());
                yml.set("players." + e.getKey() + ".name", displayNames.getOrDefault(e.getKey(), e.getKey()));
            }
            yml.save(file);
        } catch (IOException e) {
            NotificationManager.warn(plugin, "http-error", "[PayBot] Lỗi lưu topup-stats.yml: " + e.getMessage());
        }
    }
}
