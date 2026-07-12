package com.naptien.placeholder;

import com.naptien.NapTienPlugin;
import com.naptien.managers.TopupStatsManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Map;

/**
 * PayBotPlaceholders — PlaceholderAPI expansion cho PayBot (Phase B).
 * <p>
 * Danh sách placeholder hỗ trợ:
 *   %paybot_total_topup%          → tổng VND đã nạp toàn server (đã format có dấu chấm)
 *   %paybot_total_topup_raw%      → tổng VND đã nạp toàn server (số thô, không format)
 *   %paybot_player_topup%        → tổng VND player ĐANG xem placeholder đã nạp
 *   %paybot_player_topup_raw%    → như trên, số thô
 *   %paybot_total_players%       → số lượng player đã từng nạp (≥1 lần)
 *   %paybot_top1_name% .. %paybot_top10_name%    → tên player hạng N (top nạp)
 *   %paybot_top1_amount% .. %paybot_top10_amount% → số tiền (đã format) của hạng N
 * <p>
 * Dữ liệu lấy từ {@link TopupStatsManager} — chỉ tính các lượt nạp ĐÃ giao thưởng
 * thành công (xem RewardDispatcher#deliverNow).
 *
 * Changelog:
 *   v5.0.0 (Phase B) — Thêm mới.
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
        return "5.0.0";
    }

    /** true → giữ expansion sống dù /papi reload (PayBot tự quản lý lifecycle). */
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
            case "total_players":    return String.valueOf(stats.getTotalPlayerCount());
            case "player_topup":
                return player != null ? formatVnd(stats.getPlayerTotal(player.getName())) : "0";
            case "player_topup_raw":
                return player != null ? String.valueOf(stats.getPlayerTotal(player.getName())) : "0";
        }

        // %paybot_topN_name% / %paybot_topN_amount%  (N = 1..10)
        if (params.toLowerCase().startsWith("top") ) {
            try {
                String rest = params.substring(3); // "1_name" hoặc "1_amount"
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

        return null; // không khớp placeholder nào của PayBot
    }

    private static String formatVnd(long amount) {
        StringBuilder sb = new StringBuilder(String.valueOf(amount));
        for (int i = sb.length() - 3; i > 0; i -= 3) sb.insert(i, '.');
        return sb.toString();
    }
}
