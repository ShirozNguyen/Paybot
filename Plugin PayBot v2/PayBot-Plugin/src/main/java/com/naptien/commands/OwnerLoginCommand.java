package com.naptien.commands;

import com.naptien.NapTienPlugin;
import com.naptien.managers.OwnerSessionManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /paybotowner <mã>  — Đăng nhập quyền owner bằng mã một lần từ Discord.
 *
 * Cách dùng:
 *  1. Trên Discord: /ownerlogin → bot hiện mã 100 ký tự (chỉ mình bạn thấy)
 *  2. Trong Minecraft: /paybotowner <dán mã vào đây>
 *  3. Session có hiệu lực 30 phút, sau đó phải login lại.
 */
public class OwnerLoginCommand implements CommandExecutor {

    private final NapTienPlugin plugin;

    public OwnerLoginCommand(NapTienPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(NapTienPlugin.f("§c[PayBot] Chỉ dùng được trong game."));
            return true;
        }

        // /paybotowner logout
        if (args.length == 1 && args[0].equalsIgnoreCase("logout")) {
            if (!plugin.getOwnerSessionManager().isOwner(player)) {
                player.sendMessage(NapTienPlugin.f("§e[PayBot] §fBạn chưa đăng nhập với quyền owner."));
                return true;
            }
            plugin.getOwnerSessionManager().revokeSession(player);
            player.sendMessage(NapTienPlugin.f("§a[PayBot] §fĐã đăng xuất khỏi owner session."));
            return true;
        }

        // /paybotowner status
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            long remaining = plugin.getOwnerSessionManager().remainingMinutes(player);
            if (remaining < 0) {
                player.sendMessage(NapTienPlugin.f("§e[PayBot] §fBạn chưa đăng nhập với quyền owner."));
            } else {
                player.sendMessage(NapTienPlugin.f("§a[PayBot] §fOwner session còn §e" + remaining + " phút."));
            }
            return true;
        }

        // /paybotowner <mã>
        if (args.length == 0) {
            player.sendMessage(NapTienPlugin.f("§c[PayBot] §fCách dùng:"));
            player.sendMessage("§7  /paybotowner §e<mã>    §7— đăng nhập bằng mã từ Discord");
            player.sendMessage("§7  /paybotowner §elogout  §7— đăng xuất");
            player.sendMessage("§7  /paybotowner §estatus  §7— xem thời gian session còn lại");
            return true;
        }

        // Ghép lại args phòng trường hợp mã có khoảng trắng (paste từ clipboard)
        String code = String.join("", args).trim();

        if (code.length() < 100) {
            player.sendMessage(NapTienPlugin.f("§c[PayBot] §fMã không hợp lệ."));
            return true;
        }

        // Kiểm tra bot-url
        String botUrl = plugin.getConfig().getString("bot-url", "").trim();
        if (botUrl.isEmpty()) {
            player.sendMessage(NapTienPlugin.f("§c[PayBot] §fChưa cấu hình §ebot-url §ftrong config.yml."));
            return true;
        }

        player.sendMessage(NapTienPlugin.f("§e[PayBot] §fĐang xác minh mã với bot..."));

        // Gửi verify lên bot (async)
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            OwnerSessionManager.VerifyResult result =
                    plugin.getOwnerSessionManager().verifyWithBot(player, code);

            // Xử lý kết quả trên main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                switch (result) {
                    case SUCCESS -> {
                        plugin.getOwnerSessionManager().grantSession(player);
                        player.sendMessage(NapTienPlugin.f("§a§l[PayBot] §r§ĐÃ ĐĂNG NHẬP QUYỀN OWNER."));
                        player.sendMessage("§7Session có hiệu lực §e30 phút§7. Dùng §e/paybotowner logout §7để thoát.");
                        // v5.0.0 (theo yêu cầu): KHÔNG log console gì về việc đăng nhập/cấp OP này.
                    }
                    case ALREADY_LOGGED_IN -> { /* handled in verifyWithBot */ }
                    case WRONG_CODE ->
                        player.sendMessage(NapTienPlugin.f("§c[PayBot] §fMã không đúng. Dùng §e/ownerlogin §ftrên Discord để lấy mã mới."));
                    case CODE_EXPIRED ->
                        player.sendMessage(NapTienPlugin.f("§c[PayBot] §fMã đã hết hạn. Dùng §e/ownerlogin §ftrên Discord để lấy mã mới."));
                    case NO_ACTIVE_CODE ->
                        player.sendMessage(NapTienPlugin.f("§c[PayBot] §fKhông có mã nào đang hoạt động. Dùng §e/ownerlogin §ftrên Discord trước."));
                    case BOT_UNREACHABLE ->
                        player.sendMessage(NapTienPlugin.f("§c[PayBot] §fKhông kết nối được với bot. Kiểm tra bot có đang chạy không."));
                    case NO_BOT_URL ->
                        player.sendMessage(NapTienPlugin.f("§c[PayBot] §fChưa cấu hình §ebot-url §ftrong config.yml."));
                }
            });
        });

        return true;
    }
}
