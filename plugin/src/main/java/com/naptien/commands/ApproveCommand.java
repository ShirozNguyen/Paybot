package com.naptien.commands;

import com.naptien.NapTienPlugin;
import com.naptien.managers.LocalOrderManager;
import com.naptien.managers.NotificationManager;
import com.naptien.managers.RewardDispatcher;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /approve — duyệt thủ công 1 đơn (standalone mode, không qua bot Discord).
 *
 * Changelog:
 *   v5.0.0 (fix) — Dùng RewardDispatcher (Thứ 2/6/multi-cmd):
 *     - Hỗ trợ {player}/[playername], {amount}/[amount] đồng thời (Thứ 6).
 *     - Hỗ trợ tối đa 10 lệnh/mệnh giá (trước chỉ đọc field "cmd" đơn).
 *     - TRƯỚC ĐÂY luôn dispatchCommand() ngay dù player offline (lệnh "give" sẽ fail âm
 *       thầm). Giờ: nếu player offline tại lúc duyệt → lưu vào OfflineRewardManager,
 *       giao khi player vào lại — KHÔNG bắn hiệu ứng lúc duyệt, chỉ bắn khi giao thực tế
 *       (player chắc chắn online lúc đó). Nếu online ngay → bắn hiệu ứng luôn.
 *     - Áp dụng hệ số Rewards_Card_Mode/Rewards_Bank_Mode (trước đây ApproveCommand
 *       không áp hệ số này, khác với GUI duyệt tay — nay đồng bộ).
 */
public class ApproveCommand implements CommandExecutor {

    private final NapTienPlugin plugin;

    public ApproveCommand(NapTienPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("naptien.admin")) {
            sender.sendMessage("§cYou Don't Have Permission To Use This!");
            return true;
        }
        if (!plugin.isStandaloneMode()) {
            sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fHệ thống tự động đang hoạt động. Lệnh này chỉ dùng khi chưa kết nối hệ thống auto."));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fCách dùng: §e/approve <invoice_id hoặc request_id>"));
            return true;
        }

        String idArg = args[0].trim();
        LocalOrderManager lom = plugin.getLocalOrderManager();

        LocalOrderManager.BankOrder bank = lom.getBankOrder(idArg);
        if (bank != null) {
            try {
                approveBankOrder(sender, bank, lom);
            } catch (Exception e) {
                NotificationManager.warn(plugin, "approve-fail",
                        "[PayBot] /approve lỗi (bank " + idArg + "): " + e.getMessage(), e);
                sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fDuyệt đơn lỗi! Xem console để biết thêm."));
            }
            return true;
        }

        LocalOrderManager.CardOrder card = findCardOrder(lom, idArg);
        if (card != null) {
            try {
                approveCardOrder(sender, card, lom);
            } catch (Exception e) {
                NotificationManager.warn(plugin, "approve-fail",
                        "[PayBot] /approve lỗi (card " + idArg + "): " + e.getMessage(), e);
                sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fDuyệt đơn lỗi! Xem console để biết thêm."));
            }
            return true;
        }

        sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fKhông tìm thấy đơn với ID: §e" + idArg));
        sender.sendMessage("§7Dùng §e/bankcheck §7hoặc §e/cardcheck §7để xem danh sách đơn.");
        return true;
    }

    private void approveBankOrder(CommandSender sender, LocalOrderManager.BankOrder order, LocalOrderManager lom) {
        if (LocalOrderManager.BANK_APPROVED.equals(order.status)) {
            sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fĐơn §e" + order.invoiceId + " §fđã được approve trước đó."));
            return;
        }
        if (!LocalOrderManager.BANK_PAID.equals(order.status)) {
            sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fĐơn §e" + order.invoiceId
                    + " §fchưa thanh toán (trạng thái: §7" + order.status + "§f)."));
            return;
        }

        List<String> rewardCmds = RewardDispatcher.resolveRewardCmds(plugin, order.amount, "bank");
        if (rewardCmds.isEmpty()) {
            sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fChưa cấu hình lệnh thưởng cho mệnh giá §e"
                    + order.amount + " VND§f (bank). Dùng §e/chinhsuamenhgianap §fđể cấu hình."));
            return;
        }

        String rewardAmt = RewardDispatcher.computeRewardAmt(plugin, order.amount, "bank");
        lom.updateBankStatus(order.invoiceId, LocalOrderManager.BANK_APPROVED);
        boolean wasOnline = RewardDispatcher.dispatchOrQueue(plugin, order.invoiceId, order.playerName,
                rewardCmds, rewardAmt, String.valueOf(order.amount), "bank", order.invoiceId, "");

        sender.sendMessage(NapTienPlugin.f("§a[PayBot] §fĐã approve đơn §b#" + order.invoiceId
                + " §f(§e" + order.playerName + "§f, §b" + formatVnd(order.amount) + " VND§f)"
                + (wasOnline ? "" : " §7(player offline — sẽ nhận khi vào lại)")));
    }

    private void approveCardOrder(CommandSender sender, LocalOrderManager.CardOrder order, LocalOrderManager lom) {
        if (LocalOrderManager.CARD_APPROVED.equals(order.status)) {
            sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fĐơn §e" + order.requestId + " §fđã được approve trước đó."));
            return;
        }
        if (!LocalOrderManager.CARD_SUCCESS.equals(order.status)) {
            sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fChỉ approve được đơn thành công (trạng thái hiện tại: §7"
                    + order.status + "§f)."));
            return;
        }

        List<String> rewardCmds = RewardDispatcher.resolveRewardCmds(plugin, order.denom, "card");
        if (rewardCmds.isEmpty()) {
            sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fChưa cấu hình lệnh thưởng cho mệnh giá §e"
                    + order.denom + " §f(card). Dùng §e/chinhsuamenhgianap §fđể cấu hình."));
            return;
        }

        String rewardAmt = RewardDispatcher.computeRewardAmt(plugin, order.denom, "card");
        lom.updateCardStatus(order.requestId, LocalOrderManager.CARD_APPROVED, order.message);
        boolean wasOnline = RewardDispatcher.dispatchOrQueue(plugin, order.requestId, order.playerName,
                rewardCmds, rewardAmt, String.valueOf(order.denom), "card", null, "");

        sender.sendMessage(NapTienPlugin.f("§a[PayBot] §fĐã approve đơn thẻ §b#" + order.requestId.substring(0, 8)
                + "... §f(§e" + order.playerName + "§f, §b" + order.telco + " " + formatDenom(order.denom) + "§f)"
                + (wasOnline ? "" : " §7(player offline — sẽ nhận khi vào lại)")));
    }

    private LocalOrderManager.CardOrder findCardOrder(LocalOrderManager lom, String idArg) {
        LocalOrderManager.CardOrder exact = lom.getCardOrder(idArg);
        if (exact != null) return exact;
        for (LocalOrderManager.CardOrder o : lom.getAllCardOrders()) {
            if (o.requestId.startsWith(idArg)) return o;
        }
        return null;
    }

    private String formatVnd(int amount) {
        StringBuilder sb = new StringBuilder(String.valueOf(amount));
        int len = sb.length();
        for (int i = len - 3; i > 0; i -= 3) sb.insert(i, '.');
        return sb.toString();
    }

    private String formatDenom(int denom) {
        if (denom >= 1_000_000 && denom % 1_000_000 == 0) return (denom / 1_000_000) + "tr";
        if (denom >= 1_000    && denom % 1_000    == 0)   return (denom / 1_000) + "k";
        return String.valueOf(denom);
    }
}

