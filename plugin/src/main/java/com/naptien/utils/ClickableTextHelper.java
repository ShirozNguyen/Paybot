package com.naptien.utils;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * ClickableTextHelper (Plugin / ServerLoader) — Lớp trợ giúp tạo và gửi tin nhắn Chat
 * có thể CLICK BẤM vào để tự động nhập lệnh (SUGGEST_COMMAND) hoặc gửi lệnh (RUN_COMMAND)
 * tương thích 100% trên 3 ServerLoader (Spigot, Paper, Purpur từ 1.12 - 1.21.1+).
 * 
 * Bắt buộc tuân thủ Quy tắc 17: Tách biệt hoàn toàn chức năng tạo tin nhắn Chat bấm được.
 */
public class ClickableTextHelper {

    private ClickableTextHelper() {}

    /**
     * Gửi tin nhắn có thể click bấm gợi ý lệnh chat tới Player.
     */
    public static void sendSuggestCommand(Player player, String text, String commandSuggest, String hoverTooltip) {
        if (player == null) return;
        try {
            TextComponent message = new TextComponent(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', text)));
            message.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, commandSuggest));
            
            if (hoverTooltip != null && !hoverTooltip.isEmpty()) {
                message.setHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(ChatColor.translateAlternateColorCodes('&', hoverTooltip)).create()
                ));
            }
            player.spigot().sendMessage(message);
        } catch (Throwable t) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', text));
        }
    }

    /**
     * Gửi tin nhắn có thể click bấm thực thi lệnh tới Player.
     */
    public static void sendRunCommand(Player player, String text, String commandRun, String hoverTooltip) {
        if (player == null) return;
        try {
            TextComponent message = new TextComponent(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', text)));
            message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, commandRun));
            
            if (hoverTooltip != null && !hoverTooltip.isEmpty()) {
                message.setHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(ChatColor.translateAlternateColorCodes('&', hoverTooltip)).create()
                ));
            }
            player.spigot().sendMessage(message);
        } catch (Throwable t) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', text));
        }
    }
}
