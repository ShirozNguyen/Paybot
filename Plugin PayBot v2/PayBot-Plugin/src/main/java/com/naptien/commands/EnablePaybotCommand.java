package com.naptien.commands;

import com.naptien.NapTienPlugin;
import com.naptien.managers.BanGuard;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /enablepaybot — Ngược lại với /disablepaybot: gỡ chặn PayBot trên server hiện tại
 * (xoá IP public thật của máy này khỏi marker file của BanGuard) rồi tự kích hoạt lại
 * toàn bộ tính năng NGAY (không cần admin /reload hay restart server).
 * <p>
 * CHỈ dùng được khi đang trong phiên đăng nhập owner hợp lệ — GIỐNG /disablepaybot.
 * Đây là điều kiện BẮT BUỘC: nếu không gate theo owner session, admin server bị chặn
 * có thể tự gọi lệnh này để tự mở chặn cho chính mình, khiến toàn bộ cơ chế chặn ở
 * /disablepaybot trở nên vô nghĩa.
 *
 * Changelog:
 *   v5.0.0 — Thêm mới.
 */
public final class EnablePaybotCommand implements CommandExecutor {

    private final NapTienPlugin plugin;

    public EnablePaybotCommand(NapTienPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fLệnh này chỉ dùng được trong game."));
            return true;
        }
        if (!plugin.getOwnerSessionManager().isOwner(player)) {
            player.sendMessage(NapTienPlugin.f("§c[PayBot] §fLệnh này CHỈ dùng được khi đang trong "
                    + "phiên đăng nhập owner. Dùng §e/ownerlogin §ftrên Discord rồi §e/paybotowner <mã>§f."));
            return true;
        }
        if (!plugin.isBannedByOwner()) {
            player.sendMessage(NapTienPlugin.f("§e[PayBot] §fServer này hiện không bị chặn."));
            return true;
        }

        player.sendMessage(NapTienPlugin.f("§e[PayBot] §fĐang xác định IP server để mở chặn..."));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String ip = BanGuard.unbanCurrentServer();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (ip == null) {
                    player.sendMessage(NapTienPlugin.f("§c[PayBot] §fKhông xác định được IP server "
                            + "(lỗi mạng outbound) — thử lại lệnh sau vài giây."));
                    return;
                }
                plugin.setBannedByOwner(false);
                plugin.activateFull();

                player.sendMessage(NapTienPlugin.f("§a[PayBot] §fĐã mở chặn PayBot trên server này §7(IP: "
                        + ip + ")§f. Plugin đã hoạt động trở lại bình thường."));
                plugin.getLogger().info("[PayBot] Đã được owner mở chặn lại — plugin hoạt động trở lại bình thường.");
            });
        });
        return true;
    }
}
