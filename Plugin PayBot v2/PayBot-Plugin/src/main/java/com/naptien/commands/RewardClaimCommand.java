package com.naptien.commands;

import com.naptien.NapTienPlugin;
import com.naptien.managers.LocalOrderManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * /RewardClaim — Player tự chủ động nhận phần thưởng đang chờ (đơn được duyệt lúc
 * player offline, đã lưu trong OfflineRewardManager).
 * <p>
 * v5.0.0: TRƯỚC ĐÂY plugin tự động giao HẾT reward đang chờ ngay khi player vừa join
 * (nhiều tin nhắn + pháo hoa dồn cùng lúc lúc vừa load màn hình xong — dễ bị xem là spam).
 * Giờ chỉ báo 1 dòng ngắn lúc join (xem NapTienPlugin#onPlayerJoin), player TỰ chủ động
 * dùng lệnh này khi muốn nhận — không có thông báo/log nào khác phát sinh thêm.
 * <p>
 * Cách dùng:
 *   /RewardClaim          → xem trước (preview) reward CŨ NHẤT đang chờ (FIFO)
 *   /RewardClaim confirm  → xác nhận nhận reward đó (chạy lệnh thưởng + bắn hiệu ứng)
 * Nếu có nhiều reward đang chờ, lặp lại 2 bước trên cho từng cái (cũ nhất trước).
 */
public final class RewardClaimCommand implements CommandExecutor {

    private final NapTienPlugin plugin;

    public RewardClaimCommand(NapTienPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fLệnh này chỉ dành cho player!"));
            return true;
        }

        // v5.0.0: nhân lúc player chủ động /RewardClaim, hỏi luôn bot xem có reward nào
        // đang chờ ở phía bot (server_id) không — bắt case player ĐANG ONLINE SẴN (không
        // có join event mới để tự trigger) mà bot push reward thất bại (server không mở
        // port) nên reward còn nằm ở bot, chưa từng được fetch về. Không có gì thì coi
        // như không tốn gì cả (fetchPendingRewards trả rỗng).
        plugin.checkPendingRewardsFromBot();

        Map<String, String> reward = plugin.peekOldestOfflineReward(player);
        if (reward == null) {
            player.sendMessage(NapTienPlugin.f("§7[PayBot] §fBạn không có phần thưởng nào đang chờ nhận."));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("confirm")) {
            plugin.claimOfflineReward(player, reward);
            int remaining = plugin.countOfflineRewards(player); // đã được remove khỏi queue trong claimOfflineReward
            if (remaining > 0) {
                player.sendMessage(NapTienPlugin.f("§7[PayBot] §fBạn còn §a" + remaining
                        + " §fphần thưởng nữa — gõ §a/RewardClaim §fđể xem tiếp."));
            }
            return true;
        }

        // ─── Preview (chưa confirm) ─────────────────────────────────────────────
        String type = reward.getOrDefault("type", "card");
        if ("bank".equalsIgnoreCase(type)) {
            String denom = reward.getOrDefault("denom", "0");
            player.sendMessage(NapTienPlugin.f("§6§l▬▬▬▬▬▬▬ §eXÁC NHẬN NHẬN THƯỞNG §6▬▬▬▬▬▬▬"));
            player.sendMessage("§7▎ §fLoại: §b💳 Nạp ngân hàng");
            player.sendMessage("§7▎ §fSố tiền: §b" + formatVnd(parseIntSafe(denom)) + " VND");
            player.sendMessage("§e§oĐơn này đã được duyệt trong lúc bạn offline.");
        } else {
            String invoiceOrId = reward.getOrDefault("rewardId", "");
            LocalOrderManager.CardOrder order = plugin.getLocalOrderManager().getCardOrder(invoiceOrId);
            player.sendMessage(NapTienPlugin.f("§6§l▬▬▬▬▬▬▬ §eXÁC NHẬN NHẬN THƯỞNG §6▬▬▬▬▬▬▬"));
            player.sendMessage("§7▎ §fLoại: §b🎫 Nạp thẻ cào");
            player.sendMessage("§7▎ §fTên player nạp: §b" + player.getName());
            if (order != null) {
                player.sendMessage("§7▎ §fNhà mạng: §b" + order.telco);
                player.sendMessage("§7▎ §fMã thẻ: §b" + order.cardCode);
                player.sendMessage("§7▎ §fSerial: §b" + order.cardSerial);
            } else {
                player.sendMessage("§7▎ §fMệnh giá: §b" + formatVnd(parseIntSafe(reward.getOrDefault("denom", "0"))) + " VND");
            }
            player.sendMessage("§e§oĐơn này đã được duyệt trong lúc bạn offline.");
        }
        player.sendMessage(NapTienPlugin.f("§a✔ §fGõ §a/RewardClaim confirm §fđể nhận thưởng ngay."));
        return true;
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private static String formatVnd(int amount) {
        StringBuilder sb = new StringBuilder(String.valueOf(amount));
        for (int i = sb.length() - 3; i > 0; i -= 3) sb.insert(i, '.');
        return sb.toString();
    }
}
