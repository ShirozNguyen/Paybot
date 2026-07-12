package com.naptien.commands;

import com.naptien.NapTienPlugin;
import com.naptien.managers.CardManager;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class OkCommand implements CommandExecutor {

    private final NapTienPlugin plugin;

    public OkCommand(NapTienPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Lệnh này chỉ có player mới được sử dụng!");
            return true;
        }

        CardManager.PendingCard pending = plugin.getCardManager().getPending(player.getName());
        if (pending == null) {
            player.sendMessage(NapTienPlugin.f("§c[PayBot] §fBạn chưa nhập thẻ! Dùng §e/napthe §ftrước."));
            return true;
        }
        if (pending.isExpired()) {
            plugin.getCardManager().clearPending(player.getName());
            player.sendMessage(NapTienPlugin.f("§c[PayBot] §fThẻ đã hết hạn xác nhận (2 phút). Dùng §e/napthe §fđể nhập lại."));
            return true;
        }

        plugin.getCardManager().clearPending(player.getName());
        player.sendMessage(NapTienPlugin.f("§e[PayBot] §fĐang gửi thẻ lên hệ thống..."));
        com.naptien.managers.NotificationManager.log(plugin, "card-submitted",
                "[PayBot] " + player.getName() + " gửi thẻ: " + pending.telco + " "
                        + pending.denom + " VND (standalone=" + plugin.isStandaloneMode() + ")");

        // v5.1.0: Luôn xử lý thẻ trực tiếp qua Card API (không phụ thuộc bot).
        // Nếu Card API chưa cấu hình → báo lỗi dù có hay không có kết nối bot.
        // Khi kết nối bot, bot.py sẽ nhận thông báo kết quả qua StandaloneCardProcessor
        // (sau khi card API trả kết quả, submitCard() tự gọi notifyBotCardResult nếu connected).
        if (!plugin.getSetupManager().isCardApiConfigured()) {
            player.sendMessage(NapTienPlugin.f("§c[PayBot] §fServer chưa cấu hình Card API. Admin dùng §e/cardsetup §ftrước!"));
            return true;
        }
        plugin.getStandaloneCardProcessor().submitCard(
                player, pending.telco, pending.denom, pending.cardCode, pending.cardSerial);
        return true;
    }
}
