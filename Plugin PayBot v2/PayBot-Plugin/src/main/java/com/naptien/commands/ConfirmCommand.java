package com.naptien.commands;

import com.naptien.NapTienPlugin;
import org.bukkit.command.*;

public class ConfirmCommand implements CommandExecutor {

    private final NapTienPlugin plugin;

    public ConfirmCommand(NapTienPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("naptien.admin")) {
            sender.sendMessage("§cYou Don't Have Permission To Use This!");
            return true;
        }
        // BUG FIX v4.1.0: Chỉ check guild-id (tương tự DisconnectCommand).
        String guildId = plugin.getConfig().getString("guild-id", "").trim();
        if (guildId.isEmpty()) {
            sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fPlugin chưa kết nối hệ thống auto!"));
            return true;
        }
        sender.sendMessage(NapTienPlugin.f("§e[PayBot] §fĐang ngắt kết nối..."));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean ok = plugin.getBotHttpClient().confirmDisconnect();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (ok) {
                    plugin.getConfig().set("guild-id",            "");
                    plugin.getConfig().set("server-id",           "");
                    plugin.getConfig().set("reward-command",      "");
                    plugin.getConfig().set("reward-command-card", "");
                    plugin.getConfig().set("reward-command-bank", "");
                    plugin.getConfig().set("denom-rewards",       null);
                    plugin.getConfig().set("denom-rewards-card",  null);
                    plugin.getConfig().set("denom-rewards-bank",  null);
                    plugin.saveConfig();
                    sender.sendMessage(NapTienPlugin.f("§a[PayBot] §fĐã ngắt kết nối thành công!"));
                    sender.sendMessage("§7Toàn bộ cấu hình hệ thống auto đã được xoá.");
                } else {
                    sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fThất bại, thử lại sau."));
                }
            });
        });
        return true;
    }
}
