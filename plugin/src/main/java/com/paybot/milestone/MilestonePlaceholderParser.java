package com.paybot.milestone;

import java.util.Locale;

/**
 * Xử lý thay thế các biến (placeholders) trong các câu lệnh trao thưởng mốc nạp.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm phân tích và thay thế biến.
 */
public final class MilestonePlaceholderParser {

    private MilestonePlaceholderParser() {}

    /**
     * Thay thế toàn bộ các biến hợp lệ trong câu lệnh trao thưởng.
     */
    public static String parse(String commandTemplate, String playerName, long milestoneAmount, long playerTotal, long serverTotal) {
        if (commandTemplate == null || commandTemplate.isBlank()) {
            return "";
        }

        String safePlayer = playerName != null ? playerName : "";
        String strAmount = String.valueOf(milestoneAmount);
        String strPlayerTotal = String.valueOf(playerTotal);
        String strServerTotal = String.valueOf(serverTotal);

        return commandTemplate
                .replace("[playername]", safePlayer)
                .replace("%player%", safePlayer)
                .replace("%player_name%", safePlayer)
                .replace("[amount]", strAmount)
                .replace("%amount%", strAmount)
                .replace("[player_total]", strPlayerTotal)
                .replace("%player_total%", strPlayerTotal)
                .replace("[server_total]", strServerTotal)
                .replace("%server_total%", strServerTotal)
                .replace("[milestone]", strAmount)
                .replace("%milestone%", strAmount);
    }
}
