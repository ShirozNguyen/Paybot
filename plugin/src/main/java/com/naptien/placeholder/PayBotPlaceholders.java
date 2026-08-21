package com.naptien.placeholder;

import com.naptien.NapTienPlugin;
import com.naptien.managers.TopupStatsManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import java.util.List;
import java.util.Map;

/**
 * PayBotPlaceholders — PlaceholderAPI expansion cho PayBot (3 loại nạp Card, Bank, Total & DB status).
 */
public class PayBotPlaceholders extends PlaceholderExpansion {

    private final NapTienPlugin plugin;

    public PayBotPlaceholders(NapTienPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "paybot";
    }

    @Override
    public String getAuthor() {
        return "TheRealShiroz";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(org.bukkit.entity.Player player, String params) {
        TopupStatsManager stats = plugin.getTopupStatsManager();
        if (stats == null) return "";

        switch (params.toLowerCase()) {
            case "total_topup":      return formatVnd(stats.getServerTotal());
            case "total_topup_raw":  return String.valueOf(stats.getServerTotal());
            case "total_card":       return formatVnd(stats.getServerCardTotal());
            case "total_card_raw":   return String.valueOf(stats.getServerCardTotal());
            case "total_bank":       return formatVnd(stats.getServerBankTotal());
            case "total_bank_raw":   return String.valueOf(stats.getServerBankTotal());

            case "total_players":    return String.valueOf(stats.getTotalPlayerCount());

            case "player_topup":
                return player != null ? formatVnd(stats.getPlayerTotal(player.getName())) : "0";
            case "player_topup_raw":
                return player != null ? String.valueOf(stats.getPlayerTotal(player.getName())) : "0";
            case "player_card":
                return player != null ? formatVnd(stats.getPlayerCardTotal(player.getName())) : "0";
            case "player_card_raw":
                return player != null ? String.valueOf(stats.getPlayerCardTotal(player.getName())) : "0";
            case "player_bank":
                return player != null ? formatVnd(stats.getPlayerBankTotal(player.getName())) : "0";
            case "player_bank_raw":
                return player != null ? String.valueOf(stats.getPlayerBankTotal(player.getName())) : "0";

            case "db_status":
                return plugin.getDatabaseManager() != null ? plugin.getDatabaseManager().getDbStatus() : "Unknown";
        }

        // %paybot_topN_name% / %paybot_topN_amount%  (N = 1..10)
        if (params.toLowerCase().startsWith("top")) {
            try {
                String rest = params.substring(3);
                int underscoreIdx = rest.indexOf('_');
                if (underscoreIdx > 0) {
                    int rank = Integer.parseInt(rest.substring(0, underscoreIdx));
                    String field = rest.substring(underscoreIdx + 1);
                    List<Map.Entry<String, Long>> top = stats.getTopPlayers(Math.max(rank, 10));
                    if (rank >= 1 && rank <= top.size()) {
                        Map.Entry<String, Long> e = top.get(rank - 1);
                        if ("name".equalsIgnoreCase(field))   return e.getKey();
                        if ("amount".equalsIgnoreCase(field)) return formatVnd(e.getValue());
                    } else {
                        if ("name".equalsIgnoreCase(field))   return "-";
                        if ("amount".equalsIgnoreCase(field)) return "0";
                    }
                }
            } catch (NumberFormatException ignored) {}
        }

        return null;
    }

    private static String formatVnd(long amount) {
        StringBuilder sb = new StringBuilder(String.valueOf(amount));
        for (int i = sb.length() - 3; i > 0; i -= 3) sb.insert(i, '.');
        return sb.toString();
    }
}
