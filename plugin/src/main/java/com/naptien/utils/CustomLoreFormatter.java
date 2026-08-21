package com.naptien.utils;

import com.naptien.gui.GuiUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * CustomLoreFormatter — Chức năng chuyên biệt xử lý thay thế placeholder và format lore custom trong GUI.
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn thành 1 class riêng biệt.
 */
public final class CustomLoreFormatter {

    private CustomLoreFormatter() {}

    /**
     * Format danh sách lore custom cho item mệnh giá hoặc nhà mạng.
     *
     * @param player Player đang xem GUI (để áp dụng PlaceholderAPI)
     * @param rawLore Danh sách lore thô từ config
     * @param amount Mệnh giá nạp (VD: 10000, 20000...)
     * @param coinReward Số coin/điểm thưởng cấu hình cho mệnh giá này (nếu có)
     * @return Danh sách lore đã format và tô màu
     */
    public static List<String> formatLore(Player player, List<String> rawLore, int amount, String coinReward) {
        if (rawLore == null || rawLore.isEmpty()) return new ArrayList<>();

        List<String> formatted = new ArrayList<>();
        boolean hasPapi = isPapiEnabled();

        String playerName = player != null ? player.getName() : "";
        String amtStr = amount > 0 ? String.valueOf(amount) : "";
        String amtFormatted = amount > 0 ? GuiUtil.formatVnd(amount) : "";
        String amtK = amount > 0 ? GuiUtil.formatDenom(amount) : "";
        String coin = (coinReward != null && !coinReward.trim().isEmpty()) ? coinReward.trim() : amtFormatted;

        for (String line : rawLore) {
            if (line == null) continue;

            // 1. Thay thế biến nội bộ
            line = line.replace("%player%", playerName)
                       .replace("%player_name%", playerName)
                       .replace("%amount%", amtStr)
                       .replace("%amount_formatted%", amtFormatted)
                       .replace("%amount_k%", amtK)
                       .replace("%coin%", coin);

            // 2. Thay thế PlaceholderAPI (nếu plugin PlaceholderAPI khả dụng)
            if (hasPapi && player != null) {
                line = applyPapi(player, line);
            }

            // 3. Tô màu Hex, Gradient và ChatColor
            line = ColorGradientUtil.colorize(line);

            formatted.add(line);
        }

        return formatted;
    }

    private static boolean isPapiEnabled() {
        try {
            return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String applyPapi(Player player, String text) {
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable ignored) {
            return text;
        }
    }
}
