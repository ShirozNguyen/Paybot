package com.naptien.commands;

import com.naptien.NapTienPlugin;
import com.naptien.gui.ChinhSuaGui;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

/**
 * /chinhsuamenhgianap — opens the denom reward editor GUI.
 * Op/naptien.admin only.
 */
public final class ChinhSuaCommand implements CommandExecutor {

    private final NapTienPlugin plugin;

    public ChinhSuaCommand(NapTienPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fLệnh này chỉ dành cho player!"));
            return true;
        }
        if (!player.hasPermission("naptien.admin")) {
            player.sendMessage(NapTienPlugin.f("§c[PayBot] §fBạn không có quyền dùng lệnh này."));
            return true;
        }
        ChinhSuaGui.open(player, plugin);
        return true;
    }
}
