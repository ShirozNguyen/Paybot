package com.naptien.commands;

import com.naptien.NapTienPlugin;
import org.bukkit.command.*;

/**
 * IdCommand — /naptienid
 *
 * Hiển thị thông tin định danh của server và trạng thái kết nối bot.
 * Chỉ admin (naptien.admin) mới xem được.
 *
 * Changelog:
 *   v4.1.0 — Tái tạo từ .class (file nguồn gốc không có trong ZIP)
 */
public class IdCommand implements CommandExecutor {

    private final NapTienPlugin plugin;

    public IdCommand(NapTienPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("naptien.admin")) {
            sender.sendMessage("§cYou Don't Have Permission To Use This!");
            return true;
        }

        String serverId = plugin.getConfig().getString("server-id", "").trim();
        String guildId  = plugin.getConfig().getString("guild-id",  "").trim();
        String botUrl   = plugin.getConfig().getString("bot-url",   "").trim();
        String port     = String.valueOf(plugin.getConfig().getInt("plugin-port", 25580));
        boolean standalone = plugin.isStandaloneMode();

        sender.sendMessage(NapTienPlugin.f("§8§m────────────────────────────────"));
        sender.sendMessage(NapTienPlugin.f("§6§l PayBot — Thông tin định danh"));
        sender.sendMessage(NapTienPlugin.f("§8§m────────────────────────────────"));
        sender.sendMessage("§7Server ID  : §e" + (serverId.isEmpty() ? "§c(chưa tạo)" : serverId));
        sender.sendMessage("§7Guild ID   : §e" + (guildId.isEmpty()  ? "§7(chưa kết nối)" : guildId));
        sender.sendMessage("§7Bot URL    : §e" + (botUrl.isEmpty()   ? "§7(chưa cấu hình)" : botUrl));
        sender.sendMessage("§7HTTP Port  : §e" + port);
        sender.sendMessage("§7Chế độ     : " + (standalone
                ? "§a[Standalone]"
                : "§b[Bot-connected — Guild: " + guildId + "]"));

        if (standalone) {
            sender.sendMessage("");
            sender.sendMessage("§7→ Dùng §e/connect discord <guild_id> §7để kết nối bot.");
        } else {
            sender.sendMessage("");
            sender.sendMessage("§7→ Dùng §e/disconnect §7để ngắt kết nối bot.");
        }
        sender.sendMessage(NapTienPlugin.f("§8§m────────────────────────────────"));
        return true;
    }
}
