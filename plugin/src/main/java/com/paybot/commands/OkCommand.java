package com.paybot.commands;

import com.paybot.PayBotPlugin;
import com.paybot.managers.CardManager;
import com.paybot.managers.PlayerCooldownManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /ok — Xác nhận gửi thẻ sau khi đã kiểm tra lại thông tin.
 * (v5.1.0: Thêm kiểm tra isCardApiConfigured() trước khi gửi).
 * (v7.0.0 Part 172: Thêm rate-limit Cooldown per-player chống spam).
 */
public class OkCommand implements CommandExecutor {

    private final PayBotPlugin plugin;

    public OkCommand(PayBotPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[PayBot] Chỉ người chơi mới dùng được lệnh này.");
            return true;
        }

        CardManager.PendingCard pending = plugin.getCardManager().getPending(player.getName());
        if (pending == null) {
            player.sendMessage(PayBotPlugin.f("§c[PayBot] §fBạn chưa nhập thẻ! Dùng §e/napthe §ftrước."));
            return true;
        }
        if (pending.isExpired()) {
            plugin.getCardManager().clearPending(player.getName());
            player.sendMessage(PayBotPlugin.f("§c[PayBot] §fThẻ đã hết hạn xác nhận (2 phút). Dùng §e/napthe §fđể nhập lại."));
            return true;
        }

        PlayerCooldownManager.CooldownCheckResult cd =
                PlayerCooldownManager.getInstance().checkCardSubmitCooldown(player.getUniqueId());
        if (!cd.isAllowed()) {
            player.sendMessage("§c[PayBot] Vui lòng chờ " + cd.getRemainingSeconds() + " giây nữa để gửi thẻ cào tiếp theo!");
            return true;
        }
        PlayerCooldownManager.getInstance().recordCardSubmit(player.getUniqueId());

        plugin.getCardManager().clearPending(player.getName());
        player.sendMessage(PayBotPlugin.f("§e[PayBot] §fĐang gửi thẻ lên hệ thống..."));
        com.paybot.managers.NotificationManager.log(plugin, "card-submitted",
                "[PayBot] " + player.getName() + " gửi thẻ: " + pending.telco + " "
                        + pending.denom + " VND (standalone=" + plugin.isStandaloneMode() + ")");

        if (!plugin.getSetupManager().isCardApiConfigured()) {
            player.sendMessage(PayBotPlugin.f("§c[PayBot] §fServer chưa cấu hình Card API. Admin dùng §e/cardsetup §ftrước!"));
            return true;
        }
        plugin.getStandaloneCardProcessor().submitCard(
                player, pending.telco, pending.denom, pending.cardCode, pending.cardSerial);
        return true;
    }
}
