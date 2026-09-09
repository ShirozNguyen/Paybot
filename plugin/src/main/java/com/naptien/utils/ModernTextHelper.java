package com.naptien.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Modern text helper using Paper's Adventure API (net.kyori.adventure.*).
 *
 * <p><strong>IMPORTANT:</strong> This class is only ever loaded by the JVM when
 * {@link TextHelper} confirms that Adventure is available on the classpath
 * ({@link VersionCompat#hasAdventure()} returns {@code true}).
 * Never reference this class directly from outside {@link TextHelper}.</p>
 *
 * Preserves the original 1.18+ behavior: clickable URL buttons, hover tooltips.
 */
final class ModernTextHelper {

    private ModernTextHelper() {}

    // ── /muapaybot ────────────────────────────────────────────────────────────

    /** Sends /muapaybot info panel with clickable Modrinth, Profile, Discord buttons. */
    static void sendMuaPayBotInfo(CommandSender sender) {
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                .color(NamedTextColor.GOLD));

        // Dòng 1: tên plugin
        sender.sendMessage(
            Component.text("  💳 ")
                .color(NamedTextColor.YELLOW)
            .append(Component.text("PayBot")
                .color(NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD))
            .append(Component.text(" — Plugin nạp thẻ & ngân hàng cho Minecraft")
                .color(NamedTextColor.GRAY))
        );

        // Dòng 2: liên hệ admin Shiroz với link Modrinth inline
        sender.sendMessage(
            Component.text("  Vui lòng liên hệ admin ")
                .color(NamedTextColor.WHITE)
            .append(Component.text("Shiroz")
                .color(NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD))
            .append(Component.text(", có thể tìm link ở ")
                .color(NamedTextColor.WHITE))
            .append(
                Component.text("tại đây")
                    .color(NamedTextColor.GREEN)
                    .decorate(TextDecoration.BOLD)
                    .decorate(TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.openUrl("https://modrinth.com/plugin/paybot"))
                    .hoverEvent(HoverEvent.showText(
                        Component.text("Click để mở: modrinth.com/plugin/paybot")
                            .color(NamedTextColor.YELLOW)
                    ))
            )
            .append(Component.text("!").color(NamedTextColor.WHITE))
        );

        // Dòng 3: 3 nút bấm — Modrinth | Profile | Discord
        sender.sendMessage(
            Component.text("  ")
            .append(
                Component.text("[🌐 Modrinth]")
                    .color(NamedTextColor.GREEN)
                    .decorate(TextDecoration.BOLD)
                    .clickEvent(ClickEvent.openUrl("https://modrinth.com/plugin/paybot"))
                    .hoverEvent(HoverEvent.showText(
                        Component.text("modrinth.com/plugin/paybot").color(NamedTextColor.YELLOW)
                    ))
            )
            .append(Component.text("  ").color(NamedTextColor.DARK_GRAY))
            .append(
                Component.text("[👤 Profile]")
                    .color(NamedTextColor.AQUA)
                    .decorate(TextDecoration.BOLD)
                    .clickEvent(ClickEvent.openUrl("https://guns.lol/therealshiroz"))
                    .hoverEvent(HoverEvent.showText(
                        Component.text("guns.lol/therealshiroz").color(NamedTextColor.YELLOW)
                    ))
            )
            .append(Component.text("  ").color(NamedTextColor.DARK_GRAY))
            .append(
                Component.text("[💬 Discord]")
                    .color(NamedTextColor.LIGHT_PURPLE)
                    .decorate(TextDecoration.BOLD)
                    .clickEvent(ClickEvent.openUrl("https://discord.gg/ETXaFxhH3d"))
                    .hoverEvent(HoverEvent.showText(
                        Component.text("discord.gg/ETXaFxhH3d").color(NamedTextColor.YELLOW)
                    ))
            )
        );

        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                .color(NamedTextColor.GOLD));
    }

    // ── /cardsetup site-picker ────────────────────────────────────────────────

    /** Exact replica of the original CardSetupCommand site-button rendering. */
    static void sendSiteButtons(Player player, List<String> sites) {
        TextComponent.Builder row = Component.text();
        for (int i = 0; i < sites.size(); i++) {
            String site = sites.get(i);
            Component btn = Component.text("[" + site + "]")
                    .color(NamedTextColor.AQUA)
                    .decorate(TextDecoration.BOLD)
                    // FIX v4.1.0: suggestCommand thay runCommand → click điền vào chat box,
                    // player bấm Enter → chat session handler nhận → không còn "Unknown command"
                    .clickEvent(ClickEvent.suggestCommand(site))
                    .hoverEvent(HoverEvent.showText(
                            Component.text("Chọn " + site).color(NamedTextColor.YELLOW)));
            row.append(btn);
            if (i < sites.size() - 1)
                row.append(Component.text(" | ").color(NamedTextColor.DARK_GRAY));
        }
        player.sendMessage(row.build());
        player.sendMessage(
            Component.text("Hoặc gõ tên trang (VD: thesieure.com)")
                .color(NamedTextColor.GRAY)
                .decorate(TextDecoration.ITALIC)
        );
    }
}
