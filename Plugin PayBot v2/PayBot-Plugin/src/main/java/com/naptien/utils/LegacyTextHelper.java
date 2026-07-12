package com.naptien.utils;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Fallback text helper for servers that do NOT have Adventure API
 * (Minecraft 1.12 – 1.15, or non-Paper 1.16/1.17).
 *
 * Uses plain § color codes only — no clickable / hoverable text.
 * This class must NEVER import any {@code net.kyori.adventure.*} class.
 */
public final class LegacyTextHelper {

    private LegacyTextHelper() {}

    // ── /muapaybot ────────────────────────────────────────────────────────────

    /**
     * Sends the /muapaybot info panel without Adventure components.
     * Provides a plain hyperlink text instead of a clickable button.
     */
    public static void sendMuaPayBotInfo(CommandSender sender) {
        sender.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("  §e💳 §bPayBot §7— Plugin nạp thẻ & ngân hàng cho Minecraft");
        sender.sendMessage("  §fVui lòng liên hệ admin §b§lShiroz§f, có thể tìm link ở:");
        sender.sendMessage("  §a§l[Modrinth] §rhttps://modrinth.com/plugin/paybot");
        sender.sendMessage("  §b§l[Profile]  §rhttps://guns.lol/therealshiroz");
        sender.sendMessage("  §d§l[Discord]  §rhttps://discord.gg/ETXaFxhH3d");
        sender.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    // ── /cardsetup site-picker ────────────────────────────────────────────────

    /**
     * Sends site-picker buttons as plain text (no clickable events on legacy).
     * Players type the site name manually after seeing the list.
     */
    public static void sendSiteButtons(Player player, List<String> sites) {
        player.sendMessage("§7Các trang hỗ trợ:");
        StringBuilder row = new StringBuilder("  §b");
        for (int i = 0; i < sites.size(); i++) {
            if (i > 0) row.append(" §8| §b");
            row.append(sites.get(i));
        }
        player.sendMessage(row.toString());
        player.sendMessage("§7§oGõ tên trang vào chat để chọn (VD: §fthesieure.com§7§o).");
    }
}
