package com.paybot.commands;

import com.paybot.PayBotPlugin;
import com.paybot.gui.ChinhSuaGui;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

/**
 * /chinhsuamenhgianap — opens the denom reward editor GUI.
 * Op/naptien.admin only.
 */
public final class ChinhSuaCommand implements CommandExecutor {

    private final PayBotPlugin plugin;

    public ChinhSuaCommand(PayBotPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PayBotPlugin.f("§c[PayBot] §fLệnh này chỉ dành cho player!"));
            return true;
        }
        if (!player.hasPermission("naptien.admin")) {
            player.sendMessage(PayBotPlugin.f("§c[PayBot] §fBạn không có quyền dùng lệnh này."));
            return true;
        }
        ChinhSuaGui.open(player, plugin);
        return true;
    }
}
