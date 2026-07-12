package com.naptien.commands;

import com.naptien.NapTienPlugin;
import org.bukkit.command.*;

public class DisconnectCommand implements CommandExecutor {

    private final NapTienPlugin plugin;

    public DisconnectCommand(NapTienPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("naptien.admin")) {
            sender.sendMessage("§cYou Don't Have Permission To Use This!");
            return true;
        }
        // BUG FIX v4.1.0: Chỉ check guild-id (không phải server-id || guildId).
        // server-id luôn có giá trị (tự sinh) nên điều kiện cũ (serverId && guildId cùng rỗng)
        // không bao giờ đúng → luôn cố gọi bot dù chưa kết nối.
        String guildId = plugin.getConfig().getString("guild-id", "").trim();
        if (guildId.isEmpty()) {
            sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fPlugin chưa kết nối hệ thống auto."));
            return true;
        }

        // --force: xoá config cục bộ ngay mà không cần liên hệ bot
        boolean force = args.length > 0 && args[0].equalsIgnoreCase("--force");
        if (force) {
            clearBotConfig();
            sender.sendMessage(NapTienPlugin.f("§a[PayBot] §fĐã ngắt kết nối cục bộ (force)."));
            sender.sendMessage("§7Guild §e" + guildId + " §7đã bị xoá khỏi config.");
            return true;
        }
        sender.sendMessage(NapTienPlugin.f("§e[PayBot] §fĐang gửi yêu cầu ngắt kết nối..."));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean ok = plugin.getBotHttpClient().requestDisconnect();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (ok) {
                    sender.sendMessage(NapTienPlugin.f("§a[PayBot] §fYêu cầu đã gửi thành công!"));
                    sender.sendMessage("§7Dùng §e/confirm §7để xác nhận ngắt kết nối.");
                } else {
                    sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fGửi yêu cầu thất bại!"));
                    sender.sendMessage("§7Nếu bot không phản hồi, dùng §e/disconnect --force §7để xoá config cục bộ.");
                }
            });
        });
        return true;
    }

    private void clearBotConfig() {
        plugin.getConfig().set("guild-id",            "");
        plugin.getConfig().set("reward-command",      "");
        plugin.getConfig().set("reward-command-card", "");
        plugin.getConfig().set("reward-command-bank", "");
        plugin.getConfig().set("denom-rewards",       null);
        plugin.getConfig().set("denom-rewards-card",  null);
        plugin.getConfig().set("denom-rewards-bank",  null);
        plugin.saveConfig();
    }
}
