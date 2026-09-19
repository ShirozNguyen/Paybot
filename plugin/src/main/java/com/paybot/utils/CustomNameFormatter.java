package com.paybot.utils;

import com.paybot.gui.GuiUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * CustomNameFormatter — Chức năng chuyên biệt xử lý thay thế placeholder và format tên hiển thị (Custom Name) trong GUI.
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn thành 1 class riêng biệt.
 */
public final class CustomNameFormatter {

    private CustomNameFormatter() {}

    /**
     * Format tên hiển thị custom cho item mệnh giá hoặc nhà mạng.
     *
     * @param player Player đang xem GUI (để áp dụng PlaceholderAPI)
     * @param rawName Tên thô từ cấu hình config (custom-name)
     * @param amount Mệnh giá nạp (VD: 10000, 20000...)
     * @param coinReward Số coin/điểm thưởng cấu hình cho mệnh giá này (nếu có)
     * @return Tên đã format và tô màu Hex / Gradient
     */
    public static String formatName(Player player, String rawName, int amount, String coinReward) {
        if (rawName == null || rawName.trim().isEmpty()) return "";

        String line = rawName;
        String playerName = player != null ? player.getName() : "";
        String amtStr = amount > 0 ? String.valueOf(amount) : "";
        String amtFormatted = amount > 0 ? GuiUtil.formatVnd(amount) : "";
        String amtK = amount > 0 ? GuiUtil.formatDenom(amount) : "";
        String coin = (coinReward != null && !coinReward.trim().isEmpty()) ? coinReward.trim() : amtFormatted;

        // 1. Thay thế biến nội bộ
        line = line.replace("%player%", playerName)
                   .replace("%player_name%", playerName)
                   .replace("%amount%", amtStr)
                   .replace("%amount_formatted%", amtFormatted)
                   .replace("%amount_k%", amtK)
                   .replace("%coin%", coin);

        // 2. Thay thế PlaceholderAPI (nếu plugin PlaceholderAPI khả dụng)
        if (isPapiEnabled() && player != null) {
            line = applyPapi(player, line);
        }

        // 3. Tô màu Hex, Gradient và ChatColor
        return ColorGradientUtil.colorize(line);
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
