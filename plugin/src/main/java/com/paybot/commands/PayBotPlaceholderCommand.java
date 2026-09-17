package com.paybot.commands;

import com.paybot.PayBotPlugin;
import com.paybot.gui.PayBotPlaceholderGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /paybotleaderboard — mở GUI xem các chỉ số thống kê nạp (tương đương %paybot_...%)
 * (v5.0.3: đổi tên từ /paybotplaceholder — class name giữ nguyên, chỉ đổi tên lệnh)
 * TRỰC TIẾP trong game, không cần cài thêm PlaceholderAPI.
 *
 * Changelog:
 *   v5.0.0 (Phase B) — Thêm mới.
 */
public final class PayBotPlaceholderCommand implements CommandExecutor {

    private final PayBotPlugin plugin;

    public PayBotPlaceholderCommand(PayBotPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PayBotPlugin.f("§c[PayBot] §fLệnh này chỉ dành cho player!"));
            return true;
        }
        PayBotPlaceholderGui.openMain(player, plugin);
        return true;
    }
}
