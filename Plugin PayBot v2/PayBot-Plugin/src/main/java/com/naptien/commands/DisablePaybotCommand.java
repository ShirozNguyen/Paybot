package com.naptien.commands;

import com.naptien.NapTienPlugin;
import com.naptien.managers.BanGuard;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /disablepaybot — Chặn VĨNH VIỄN PayBot trên server hiện tại (theo IP public thật của
 * máy, không phải theo config/jar) — kể cả khi admin server đó xoá hẳn plugin rồi tải
 * lại bản mới, đổi port chơi, hay tự sửa config.yml, server CÙNG MÁY (cùng IP) đó vẫn
 * sẽ bị nhận diện lại và tự ngừng khởi động ở lần chạy kế tiếp (xem BanGuard).
 * <p>
 * CHỈ dùng được khi đang trong phiên đăng nhập owner hợp lệ (/paybotowner <mã> từ
 * /ownerlogin trên Discord) — KHÔNG có permission node nào mở được lệnh này, để admin
 * server thường (kể cả OP) không thể tự gọi nhầm hoặc cố ý.
 *
 * Changelog:
 *   v5.0.0 — Thêm mới.
 */
public final class DisablePaybotCommand implements CommandExecutor {

    private final NapTienPlugin plugin;

    public DisablePaybotCommand(NapTienPlugin plugin) {
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
        if (plugin.isBannedByOwner()) {
            player.sendMessage(NapTienPlugin.f("§e[PayBot] §fServer này đã đang bị chặn rồi."));
            return true;
        }

        player.sendMessage(NapTienPlugin.f("§e[PayBot] §fĐang xác định IP server để chặn..."));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String ip = BanGuard.banCurrentServer();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (ip == null) {
                    player.sendMessage(NapTienPlugin.f("§c[PayBot] §fKhông xác định được IP server "
                            + "(lỗi mạng outbound) — thử lại lệnh sau vài giây."));
                    return;
                }
                plugin.deactivateFull();
                plugin.setBannedByOwner(true);

                // v5.0.3 [Part 24]: báo IP lên bot để owner quản lý tập trung qua Discord
                // (/viewblockedips, /removeip) — fire-and-forget, không chặn UX nếu lỗi mạng.
                String sid = plugin.getConfig().getString("server-id", "").trim();
                if (!sid.isEmpty()) {
                    Bukkit.getScheduler().runTaskAsynchronously(plugin,
                            () -> plugin.getBotHttpClient().reportBlockedIp(sid));
                }

                player.sendMessage(NapTienPlugin.f("§a[PayBot] §fĐã chặn PayBot trên server này §7(IP: "
                        + ip + ")§f. Dùng §e/enablepaybot §f(cũng cần phiên owner) để mở lại."));
                plugin.getLogger().severe("==============================================");
                plugin.getLogger().severe(BanGuard.BLOCKED_MESSAGE);
                plugin.getLogger().severe("==============================================");
                Bukkit.broadcastMessage(NapTienPlugin.f("§c[PayBot] §f" + BanGuard.BLOCKED_MESSAGE));
            });
        });
        return true;
    }
}
