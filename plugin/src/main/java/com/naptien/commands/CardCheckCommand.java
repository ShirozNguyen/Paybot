package com.naptien.commands;

import com.naptien.NapTienPlugin;
import com.naptien.gui.TopupListGui;
import com.naptien.managers.LocalOrderManager;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class CardCheckCommand implements CommandExecutor {

    private static final SimpleDateFormat SDF = new SimpleDateFormat("dd/MM HH:mm:ss");

    private final NapTienPlugin plugin;
    private final BankCheckCommand bankCheck;

    public CardCheckCommand(NapTienPlugin plugin, BankCheckCommand bankCheck) {
        this.plugin    = plugin;
        this.bankCheck = bankCheck;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("naptien.admin")) {
            sender.sendMessage("§cYou Don't Have Permission To Use This!");
            return true;
        }
        if (plugin.isStandaloneMode()) {
            handleStandalone(sender, args);
        } else {
            sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fHệ thống tự động đang hoạt động, đơn được xử lý tự động."));
        }
        return true;
    }

    private void handleStandalone(CommandSender sender, String[] args) {
        // Nếu sender là player và không có args → mở GUI — v5.0.0 (fix): CHỈ hiện đơn
        // THẺ, không hiện chung với bank (hiện chung chỉ /topuplist all mới đúng).
        if (args.length == 0 && sender instanceof Player player) {
            TopupListGui.open(player, plugin, 0, "card");
            return;
        }

        bankCheck.addCheckCount(1);
        int count = bankCheck.getCheckCount();
        if (count % 15 == 0) {
            sender.sendMessage(NapTienPlugin.f("§6[PayBot] §fNếu quá mệt với việc làm thủ công bạn có thể cân nhắc inbox admin để setup hệ thống auto giao dịch nhé :3"));
        }

        LocalOrderManager lom = plugin.getLocalOrderManager();

        if (args.length == 0) {
            List<LocalOrderManager.CardOrder> all = lom.getAllCardOrders();
            if (all.isEmpty()) {
                sender.sendMessage(NapTienPlugin.f("§7[PayBot] Chưa có đơn nạp thẻ nào."));
                sender.sendMessage("§7Dữ liệu lưu tại: §f" + lom.getCardDataFilePath());
                return;
            }
            sender.sendMessage("§6§l══════ §eĐơn Nạp Thẻ Cào §6§l══════");
            for (LocalOrderManager.CardOrder o : all) {
                String statusColor = statusColor(o.status);
                String denomStr = formatDenom(o.denom);
                sender.sendMessage("§7#§f" + o.requestId.substring(0, 8) + "... §7| §e" + o.playerName
                        + " §7| §b" + o.telco.toUpperCase() + " " + denomStr
                        + " §7| " + statusColor + statusLabel(o.status)
                        + " §7| §f" + SDF.format(new Date(o.createdAt)));
            }
            sender.sendMessage("§7Tổng: §f" + all.size() + " đơn");
            sender.sendMessage("§7Dữ liệu: §f" + lom.getCardDataFilePath());
        } else {
            String idArg = args[0].trim();
            LocalOrderManager.CardOrder o = findOrder(lom, idArg);
            if (o == null) {
                sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fKhông tìm thấy đơn với ID: §e" + idArg));
                return;
            }
            showCardDetail(sender, o);
        }
    }

    private LocalOrderManager.CardOrder findOrder(LocalOrderManager lom, String idArg) {
        LocalOrderManager.CardOrder exact = lom.getCardOrder(idArg);
        if (exact != null) return exact;
        for (LocalOrderManager.CardOrder o : lom.getAllCardOrders()) {
            if (o.requestId.startsWith(idArg)) return o;
        }
        return null;
    }

    private void showCardDetail(CommandSender sender, LocalOrderManager.CardOrder o) {
        String statusColor = statusColor(o.status);
        sender.sendMessage("§6§l═══════════════════════════════");
        sender.sendMessage("§7Request ID : §f" + o.requestId);
        sender.sendMessage("§7Người chơi : §e" + o.playerName);
        sender.sendMessage("§7Nhà mạng   : §b" + o.telco.toUpperCase());
        sender.sendMessage("§7Mệnh giá   : §b" + formatDenom(o.denom));
        sender.sendMessage("§7Mã thẻ     : §f||" + o.cardCode + "||");
        sender.sendMessage("§7Serial     : §f||" + o.cardSerial + "||");
        sender.sendMessage("§7Trạng thái : " + statusColor + statusLabel(o.status));
        if (!o.message.isEmpty()) sender.sendMessage("§7Ghi chú    : §f" + o.message);
        sender.sendMessage("§7Thời gian  : §f" + SDF.format(new Date(o.createdAt)));
        if (LocalOrderManager.CARD_SUCCESS.equals(o.status)) {
            sender.sendMessage("§a→ Dùng §e/approve " + o.requestId + " §ađể cấp thưởng.");
        }
        sender.sendMessage("§6§l═══════════════════════════════");
    }

    private String statusColor(String status) {
        switch (status) {
            case LocalOrderManager.CARD_SUCCESS:  return "§a";
            case LocalOrderManager.CARD_APPROVED: return "§2";
            case LocalOrderManager.CARD_PROCESSING: return "§e";
            case LocalOrderManager.CARD_MAINTENANCE: return "§6";
            default: return "§c";
        }
    }

    private String statusLabel(String status) {
        switch (status) {
            case LocalOrderManager.CARD_PROCESSING:  return "Đang xử lý ⏳";
            case LocalOrderManager.CARD_SUCCESS:     return "Thành công — chờ approve ✓";
            case LocalOrderManager.CARD_WRONG_DENOM: return "Sai mệnh giá ✗";
            case LocalOrderManager.CARD_USED:        return "Thẻ đã sử dụng ✗";
            case LocalOrderManager.CARD_MAINTENANCE: return "Hệ thống bảo trì ⚠";
            case LocalOrderManager.CARD_WRONG:       return "Thẻ sai/đã dùng ✗";
            case LocalOrderManager.CARD_APPROVED:    return "Đã cấp thưởng ✅";
            default:                                 return status;
        }
    }

    private String formatDenom(int denom) {
        if (denom >= 1_000_000 && denom % 1_000_000 == 0) return (denom / 1_000_000) + "tr";
        if (denom >= 1_000    && denom % 1_000    == 0)   return (denom / 1_000) + "k";
        return String.valueOf(denom);
    }
}
