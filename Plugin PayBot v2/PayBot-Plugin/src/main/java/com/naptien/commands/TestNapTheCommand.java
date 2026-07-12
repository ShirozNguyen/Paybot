package com.naptien.commands;

import com.naptien.NapTienPlugin;
import com.naptien.gui.GuiListener;
import com.naptien.gui.GuiSession;
import com.naptien.gui.NapTheGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /testnapthe — Lệnh DEV/ADMIN: mở UI nạp thẻ như bình thường (chọn nhà mạng → chọn mệnh
 * giá), nhưng khi chọn mệnh giá thì GIẢ LẬP thẻ đó đã gạch THÀNH CÔNG ngay (không hỏi mã
 * thẻ/serial, không gọi API gạch thẻ thật, không lưu vào lịch sử /topuplist) — chỉ để
 * admin tự kiểm tra chuỗi reward + hiệu ứng (pháo hoa/sound/thông báo) hoạt động đúng.
 * <p>
 * Permission: naptien.admin
 *
 * Changelog:
 *   v5.0.0 — Thêm mới.
 */
public final class TestNapTheCommand implements CommandExecutor {

    private final NapTienPlugin plugin;

    public TestNapTheCommand(NapTienPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fLệnh này chỉ dành cho player!"));
            return true;
        }
        if (!player.isOp()) {
            player.sendMessage(NapTienPlugin.f("§c[PayBot] §fLệnh test chỉ dành cho OP!"));
            return true;
        }

        // Cho phép gõ tắt: /testnapthe <nhà mạng> <mệnh giá> để test nhanh không cần mở UI
        if (args.length >= 2) {
            String telco  = args[0].toUpperCase();
            int    amount = com.naptien.gui.GuiUtil.parseDenom(args[1].toLowerCase());
            if (amount <= 0) {
                player.sendMessage(NapTienPlugin.f("§c[PayBot] §fMệnh giá không hợp lệ! Vd: §e/testnapthe VIETTEL 100k"));
                return true;
            }
            GuiListener.simulateCardSuccess(plugin, player, telco, amount);
            return true;
        }

        GuiSession s = GuiSession.get(player.getUniqueId());
        s.testMode = true;
        player.sendMessage(NapTienPlugin.f("§e§l[TEST MODE] §r§eChọn nhà mạng rồi mệnh giá — thẻ sẽ được "
                + "giả lập THÀNH CÔNG ngay (không cần nhập mã thẻ/serial)."));
        NapTheGui.openTelcoGui(player);
        return true;
    }
}
