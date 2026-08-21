package com.naptien.managers;

import com.naptien.NapTienPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * TopupStatsManager — Lưu thống kê 3 loại tiền (Card, Bank, Total) của từng player + toàn server,
 * phục vụ PlaceholderAPI expansion.
 */
public class TopupStatsManager {

    private final NapTienPlugin plugin;
    private final File file;

    private final Map<String, Long> playerCardTotals = new ConcurrentHashMap<>();
    private final Map<String, Long> playerBankTotals = new ConcurrentHashMap<>();
    private final Map<String, Long> playerTotals = new ConcurrentHashMap<>();
    private final Map<String, String> displayNames = new ConcurrentHashMap<>();

    private long serverCardTotal = 0L;
    private long serverBankTotal = 0L;
    private long serverTotal = 0L;

    public TopupStatsManager(NapTienPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "topup-stats.yml");
        load();
    }

    public synchronized void recordTopup(String playerName, long amountVnd, String type) {
        if (playerName == null || playerName.isEmpty() || amountVnd <= 0) return;
        String key = playerName.toLowerCase();
        displayNames.put(key, playerName);

        boolean isCard = "CARD".equalsIgnoreCase(type);
        if (isCard) {
            playerCardTotals.merge(key, amountVnd, Long::sum);
            serverCardTotal += amountVnd;
        } else {
            playerBankTotals.merge(key, amountVnd, Long::sum);
            serverBankTotal += amountVnd;
        }

        playerTotals.merge(key, amountVnd, Long::sum);
        serverTotal += amountVnd;
        save();
    }

    public synchronized void recordTopup(String playerName, long amountVnd) {
        recordTopup(playerName, amountVnd, "BANK");
    }

    public long getPlayerCardTotal(String playerName) {
        return playerCardTotals.getOrDefault(playerName.toLowerCase(), 0L);
    }

    public long getPlayerBankTotal(String playerName) {
        return playerBankTotals.getOrDefault(playerName.toLowerCase(), 0L);
    }

    public long getPlayerTotal(String playerName) {
        return playerTotals.getOrDefault(playerName.toLowerCase(), 0L);
    }

    public long getServerCardTotal() {
        return serverCardTotal;
    }

    public long getServerBankTotal() {
        return serverBankTotal;
    }

    public long getServerTotal() {
        return serverTotal;
    }

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
            serverCardTotal = yml.getLong("server-card-total", 0L);
            serverBankTotal = yml.getLong("server-bank-total", 0L);

            if (yml.isConfigurationSection("players")) {
                for (String key : yml.getConfigurationSection("players").getKeys(false)) {
                    long total = yml.getLong("players." + key + ".total", 0L);
                    long card = yml.getLong("players." + key + ".card", 0L);
                    long bank = yml.getLong("players." + key + ".bank", 0L);
                    String display = yml.getString("players." + key + ".name", key);

                    playerTotals.put(key, total);
                    playerCardTotals.put(key, card);
                    playerBankTotals.put(key, bank);
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
            yml.set("server-card-total", serverCardTotal);
            yml.set("server-bank-total", serverBankTotal);

            for (Map.Entry<String, Long> e : playerTotals.entrySet()) {
                String k = e.getKey();
                yml.set("players." + k + ".total", e.getValue());
                yml.set("players." + k + ".card", playerCardTotals.getOrDefault(k, 0L));
                yml.set("players." + k + ".bank", playerBankTotals.getOrDefault(k, 0L));
                yml.set("players." + k + ".name", displayNames.getOrDefault(k, k));
            }
            yml.save(file);
        } catch (IOException e) {
            NotificationManager.warn(plugin, "http-error", "[PayBot] Lỗi lưu topup-stats.yml: " + e.getMessage());
        }
    }
}
