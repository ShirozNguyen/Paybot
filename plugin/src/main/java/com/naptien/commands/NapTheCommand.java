package com.naptien.commands;

import com.naptien.NapTienPlugin;
import com.naptien.gui.NapTheGui;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

/**
 * /napthe — opens the card top-up GUI directly.
 * Text-mode fallback: /napthe <nhà mạng> <mệnh giá> <mã thẻ> <serial>
 */
public final class NapTheCommand implements CommandExecutor {

    private final NapTienPlugin plugin;

    public NapTheCommand(NapTienPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fLệnh này chỉ dành cho player!"));
            return true;
        }
        // No args → open GUI directly (GUI itself shows config prompt if needed)
        if (args.length == 0) {
            NapTheGui.openTelcoGui(player);
            return true;
        }

        // Text-mode fallback: /napthe <telco> <denom> <code> <serial> — requires card API
        if (!isReady()) {
            player.sendMessage(NapTienPlugin.f("§c[PayBot] §fServer chưa được cấu hình API gạch thẻ."));
            player.sendMessage("§7Admin dùng §e/cardsetup §7để cấu hình.");
            return true;
        }

        // Text-mode fallback: /napthe <telco> <denom> <code> <serial>
        if (args.length < 4) {
            player.sendMessage(NapTienPlugin.f("§c[PayBot] §fCách dùng: §e/napthe §7[nhà mạng] [mệnh giá] [mã thẻ] [serial]"));
            player.sendMessage("§7Hoặc dùng §e/napthe §7để mở giao diện.");
            return true;
        }

        String telco  = args[0].toUpperCase();
        String denom  = args[1].toLowerCase();
        String code   = args[2];
        String serial = args[3];

        if (!isValidTelco(telco)) {
            player.sendMessage(NapTienPlugin.f("§c[PayBot] §fNhà mạng không hợp lệ! Hợp lệ: VIETTEL, VINAPHONE, MOBIFONE, ZING, GATE, GARENA, VCOIN, APPOTA"));
            return true;
        }
        int denomInt = com.naptien.gui.GuiUtil.parseDenom(denom);
        if (denomInt < 0) {
            player.sendMessage(NapTienPlugin.f("§c[PayBot] §fMệnh giá không hợp lệ! Hợp lệ: 10k, 20k, 30k, 50k, 100k, 200k, 300k, 500k, 1000k"));
            return true;
        }

        plugin.getCardManager().setPending(player.getName(),
                new com.naptien.managers.CardManager.PendingCard(
                        player.getName(), telco, denomInt, code, serial));

        player.sendMessage(NapTienPlugin.f("§e[PayBot] §fKiểm tra lại thẻ:"));
        player.sendMessage("§7Nhà mạng: §f" + telco + "  §7Mệnh giá: §f" + com.naptien.gui.GuiUtil.formatVnd(denomInt) + " VND");
        player.sendMessage("§7Serial: §f" + serial + "  §7Mã thẻ: §f" + code);
        player.sendMessage("§aDùng §e/ok §ađể xác nhận.");
        return true;
    }

    private boolean isReady() {
        if (!plugin.isStandaloneMode()) return true;
        return plugin.getSetupManager().isCardApiConfigured();
    }

    private boolean isValidTelco(String t) {
        return switch (t) {
            case "VIETTEL","VINAPHONE","MOBIFONE","ZING","GATE","GARENA","VCOIN","APPOTA" -> true;
            default -> false;
        };
    }
}
