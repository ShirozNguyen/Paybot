package com.naptien.commands;

import com.naptien.NapTienPlugin;
import com.naptien.gui.GuiListener;
import com.naptien.gui.GuiSession;
import com.naptien.gui.NapBankGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /testnapbank — Lệnh DEV/ADMIN: mở UI nạp bank như bình thường, nhưng khi chọn mệnh giá
 * thì GIẢ LẬP đơn đó đã thanh toán THÀNH CÔNG ngay (không tạo QR thật, không gọi SePay,
 * không lưu vào lịch sử /topuplist) — chỉ để admin tự kiểm tra chuỗi reward + hiệu ứng
 * (pháo hoa/sound/thông báo) hoạt động đúng, không cần chờ giao dịch ngân hàng thật.
 * <p>
 * Permission: naptien.admin
 *
 * Changelog:
 *   v5.0.0 — Thêm mới.
 */
public final class TestNapBankCommand implements CommandExecutor {

    private final NapTienPlugin plugin;

    public TestNapBankCommand(NapTienPlugin plugin) {
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

        // Cho phép gõ tắt: /testnapbank <mệnh giá> (vd. /testnapbank 100k) để test nhanh không cần mở UI
        if (args.length > 0) {
            int amount = com.naptien.gui.GuiUtil.parseDenom(args[0].toLowerCase());
            if (amount <= 0) {
                player.sendMessage(NapTienPlugin.f("§c[PayBot] §fMệnh giá không hợp lệ! Vd: §e/testnapbank 100k"));
                return true;
            }
            GuiListener.simulateBankSuccess(plugin, player, amount);
            return true;
        }

        GuiSession s = GuiSession.get(player.getUniqueId());
        s.testMode = true;
        player.sendMessage(NapTienPlugin.f("§e§l[TEST MODE] §r§eChọn 1 mệnh giá — đơn sẽ được giả lập "
                + "THÀNH CÔNG ngay lập tức (không tạo QR/giao dịch thật)."));
        NapBankGui.open(player, plugin);
        return true;
    }
}
