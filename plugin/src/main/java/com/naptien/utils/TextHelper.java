package com.naptien.utils;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Public dispatcher for rich text sending.
 *
 * <p>This class has <strong>zero Adventure imports</strong> so it can always be
 * loaded safely regardless of server version.  It delegates to either
 * {@link ModernTextHelper} (Adventure — loaded lazily on 1.16+ Paper) or
 * {@link LegacyTextHelper} (§ codes — always safe).</p>
 *
 * <pre>
 * 1.16+ Paper (Adventure on classpath) → ModernTextHelper  (clickable, hoverable)
 * 1.12 – 1.15 / non-Paper             → LegacyTextHelper  (plain § text)
 * </pre>
 *
 * Callers: {@link com.naptien.commands.MuaPayBotCommand},
 *           {@link com.naptien.commands.CardSetupCommand}.
 */
public final class TextHelper {

    private TextHelper() {}

    // ── /muapaybot ────────────────────────────────────────────────────────────

    /**
     * Sends the PayBot info / purchase panel.
     * 1.16+ Paper → clickable link; legacy → plain URL.
     */
    public static void sendMuaPayBotInfo(CommandSender sender) {
        if (VersionCompat.hasAdventure()) {
            ModernTextHelper.sendMuaPayBotInfo(sender);
        } else {
            LegacyTextHelper.sendMuaPayBotInfo(sender);
        }
    }

    // ── /cardsetup ────────────────────────────────────────────────────────────

    /**
     * Sends the card-API site picker.
     * 1.16+ Paper → clickable [site] buttons; legacy → plain list.
     */
    public static void sendSiteButtons(Player player, List<String> sites) {
        if (VersionCompat.hasAdventure()) {
            ModernTextHelper.sendSiteButtons(player, sites);
        } else {
            LegacyTextHelper.sendSiteButtons(player, sites);
        }
    }
}
