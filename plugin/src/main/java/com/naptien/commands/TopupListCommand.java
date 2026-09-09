package com.naptien.commands;

import com.naptien.NapTienPlugin;
import com.naptien.gui.TopupListGui;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

/**
 * /topuplist all — opens the pending orders GUI.
 * Replaces the old /approve command.
 * Op/naptien.admin only.
 */
public final class TopupListCommand implements CommandExecutor {

    private final NapTienPlugin plugin;

    public TopupListCommand(NapTienPlugin plugin) {
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

        // Bot-connected mode: đơn do bot quản lý, không có trong local storage
        if (!plugin.isStandaloneMode()) {
            player.sendMessage(NapTienPlugin.f("§e[PayBot] §fBạn đang kết nối với Discord bot."));
            player.sendMessage("§7Đơn nạp được quản lý tự động bởi bot.");
            player.sendMessage("§7Dùng §e/cardcheck §7và §e/bankcheck §7để xem & duyệt đơn.");
            return true;
        }

        // Standalone mode: mở GUI local orders
        if (args.length == 0 || args[0].equalsIgnoreCase("all")) {
            TopupListGui.open(player, plugin, 0);
            return true;
        }

        player.sendMessage(NapTienPlugin.f("§c[PayBot] §fCách dùng: §e/topuplist all"));
        return true;
    }
}
