package com.naptien.utils;

import com.naptien.gui.GuiUtil;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * CustomLoreFormatter — Chức năng chuyên biệt xử lý thay thế placeholder và format Custom Name & Custom Lore cho GUI.
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn thành 1 class riêng biệt.
 */
public final class CustomLoreFormatter {

    private CustomLoreFormatter() {}

    /**
     * Format 1 chuỗi tên hiển thị Custom (Custom Name) cho item GUI.
     */
    public static String formatName(ServerPlayer player, String rawName, int amount, String coinReward) {
        if (rawName == null || rawName.isEmpty()) return "";
        return formatLine(player, rawName, amount, coinReward);
    }

    /**
     * Format danh sách lore custom cho item mệnh giá hoặc nhà mạng.
     *
     * @param player Player đang xem GUI
     * @param rawLore Danh sách lore thô từ config
     * @param amount Mệnh giá nạp (VD: 10000, 20000...)
     * @param coinReward Số coin/điểm thưởng cấu hình cho mệnh giá này (nếu có)
     * @return Danh sách lore đã format và tô màu
     */
    public static List<String> formatLore(ServerPlayer player, List<String> rawLore, int amount, String coinReward) {
        if (rawLore == null || rawLore.isEmpty()) return new ArrayList<>();

        List<String> formatted = new ArrayList<>();
        for (String line : rawLore) {
            if (line == null) continue;
            formatted.add(formatLine(player, line, amount, coinReward));
        }

        return formatted;
    }

    private static String formatLine(ServerPlayer player, String line, int amount, String coinReward) {
        String playerName = player != null ? player.getName().getString() : "";
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

        // 2. Tô màu Hex, Gradient và ChatColor
        return ColorGradientUtil.colorize(line);
    }
}
