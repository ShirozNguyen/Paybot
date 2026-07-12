package com.naptien.commands;

import com.naptien.NapTienPlugin;
import org.bukkit.command.*;

public class ConnectCommand implements CommandExecutor {

    private final NapTienPlugin plugin;

    public ConnectCommand(NapTienPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("naptien.admin")) {
            sender.sendMessage("§cYou Don't Have Permission To Use This!");
            return true;
        }
        if (args.length == 0) {
            // v5.0.0 (theo yêu cầu): /connect KHÔNG kèm gì cả → gợi ý add bot, có link bấm được.
            sender.sendMessage(NapTienPlugin.f("§e[PayBot] §fCách dùng: §e/connect discord <guild_id>"));
            net.kyori.adventure.text.Component msg = net.kyori.adventure.text.Component.text("§eNếu bạn muốn có hệ thống tự động thì vui lòng add bot ")
                    .append(net.kyori.adventure.text.Component.text("Tại đây")
                            .color(net.kyori.adventure.text.format.NamedTextColor.AQUA)
                            .decorate(net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl("https://discord.gg/QdE5uNYqrV"))
                            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                    net.kyori.adventure.text.Component.text("Click để mở Discord"))))
                    .append(net.kyori.adventure.text.Component.text("§e và làm theo hướng dẫn"));
            sender.sendMessage(msg);
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("discord")) {
            sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fCách dùng: §e/connect discord <guild_id>"));
            sender.sendMessage("§7Lấy guild_id bằng lệnh §e/id §7trong Discord của hệ thống auto.");
            return true;
        }

        String guildId = args[1].trim();
        // Cập nhật bot-url nếu được chỉ định
        if (args.length >= 3) {
            String newBotUrl = args[2].trim();
            plugin.getConfig().set("bot-url", newBotUrl);
            plugin.saveConfig();
            plugin.reloadConfig();
            sender.sendMessage(NapTienPlugin.f("§a[PayBot] §7Đã cập nhật bot-url thành: §e" + newBotUrl));
        }

        // BUG FIX v4.1.0: Phải check guild-id (không phải server-id).
        // server-id được tự sinh khi plugin khởi động nên luôn khác rỗng,
        // dùng nó để check sẽ luôn báo "đã kết nối" dù chưa connect bot nào.
        String existingGuildId = plugin.getConfig().getString("guild-id", "").trim();

        if (!existingGuildId.isEmpty()) {
            sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fPlugin đã kết nối với §e" + existingGuildId + "§f!"));
            sender.sendMessage("§7Dùng §e/disconnect §7để ngắt trước.");
            return true;
        }

        sender.sendMessage(NapTienPlugin.f("§e[PayBot] §fĐang kết nối với: §e" + guildId + "§f..."));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String serverId = plugin.getBotHttpClient().connectToGuild(guildId);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (serverId != null) {
                    plugin.getConfig().set("guild-id",  guildId);
                    plugin.getConfig().set("server-id", serverId);
                    plugin.saveConfig();
                    sender.sendMessage(NapTienPlugin.f("§a[PayBot] §fĐã kết nối thành công với hệ thống auto: §e" + guildId + "§f!"));
                    sender.sendMessage("§7Server ID: §e" + serverId);
                    sender.sendMessage("§7Dùng §e/chinhsuamenhgianap §7trong Discord để hoàn tất cấu hình.");
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                            () -> plugin.getBotHttpClient().fetchAndApplyConfig());
                } else {
                    sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fKết nối thất bại! Kiểm tra:"));
                    sender.sendMessage("§7• Guild ID có đúng không? (Dùng §e/id §7trong Discord)");
                    sender.sendMessage("§7• §ebot-url §7trong config có trỏ đúng đến hệ thống auto không?");
                    sender.sendMessage("§7• Hệ thống auto có đang hoạt động không?");
                }
            });
        });
        return true;
    }
}
