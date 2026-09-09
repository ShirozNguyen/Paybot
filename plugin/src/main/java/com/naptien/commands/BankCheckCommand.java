package com.naptien.commands;

import com.naptien.NapTienPlugin;
import com.naptien.gui.TopupListGui;
import com.naptien.managers.LocalOrderManager;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class BankCheckCommand implements CommandExecutor {

    private static final SimpleDateFormat SDF = new SimpleDateFormat("dd/MM HH:mm:ss");

    private final NapTienPlugin plugin;
    private int checkCount = 0;

    public BankCheckCommand(NapTienPlugin plugin) { this.plugin = plugin; }

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
        // Không có args + là Player → mở GUI (giống /cardcheck) — v5.0.0 (fix): CHỈ hiện
        // đơn BANK, không hiện chung với thẻ (hiện chung chỉ /topuplist all mới đúng).
        if (args.length == 0 && sender instanceof Player player) {
            TopupListGui.open(player, plugin, 0, "bank");
            return;
        }
        incrementAndWarn(sender);
        LocalOrderManager lom = plugin.getLocalOrderManager();

        if (args.length == 0) {
            List<LocalOrderManager.BankOrder> all = lom.getAllBankOrders();
            if (all.isEmpty()) {
                sender.sendMessage(NapTienPlugin.f("§7[PayBot] Chưa có đơn nạp ngân hàng nào."));
                sender.sendMessage("§7Dữ liệu lưu tại: §f" + lom.getBankDataFilePath());
                return;
            }
            sender.sendMessage("§6§l══════ §eĐơn Nạp Ngân Hàng §6§l══════");
            for (LocalOrderManager.BankOrder o : all) {
                String statusColor = statusColor(o.status);
                sender.sendMessage("§7#§f" + o.invoiceId + " §7| §e" + o.playerName
                        + " §7| §b" + formatVnd(o.amount) + " VND"
                        + " §7| " + statusColor + o.status
                        + " §7| §f" + SDF.format(new Date(o.createdAt)));
            }
            sender.sendMessage("§7Tổng: §f" + all.size() + " đơn");
            sender.sendMessage("§7Dữ liệu: §f" + lom.getBankDataFilePath());
        } else {
            String id = args[0].trim();
            LocalOrderManager.BankOrder o = lom.getBankOrder(id);
            if (o == null) {
                sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fKhông tìm thấy đơn với ID: §e" + id));
                return;
            }
            showBankDetail(sender, o);
        }
    }

    private void showBankDetail(CommandSender sender, LocalOrderManager.BankOrder o) {
        String statusColor = statusColor(o.status);
        sender.sendMessage("§6§l═══════════════════════════════");
        sender.sendMessage("§7Mã hoá đơn : §f" + o.invoiceId);
        sender.sendMessage("§7Người chơi : §e" + o.playerName);
        sender.sendMessage("§7Số tiền    : §b" + formatVnd(o.amount) + " VND");
        sender.sendMessage("§7Trạng thái : " + statusColor + statusLabel(o.status));
        sender.sendMessage("§7Thời gian  : §f" + SDF.format(new Date(o.createdAt)));
        if (LocalOrderManager.BANK_PAID.equals(o.status)) {
            sender.sendMessage("§a→ Dùng §e/approve " + o.invoiceId + " §ađể cấp thưởng.");
        }
        sender.sendMessage("§6§l═══════════════════════════════");
    }

    private String statusColor(String status) {
        switch (status) {
            case LocalOrderManager.BANK_PAID:     return "§a";
            case LocalOrderManager.BANK_APPROVED: return "§2";
            case LocalOrderManager.BANK_EXPIRED:  return "§8";
            default:                              return "§e";
        }
    }

    private String statusLabel(String status) {
        switch (status) {
            case LocalOrderManager.BANK_PENDING:  return "Đang chờ thanh toán ⏳";
            case LocalOrderManager.BANK_PAID:     return "Đã thanh toán — chờ approve ✓";
            case LocalOrderManager.BANK_APPROVED: return "Đã cấp thưởng ✅";
            case LocalOrderManager.BANK_EXPIRED:  return "Hết hạn ⏰";
            default:                              return status;
        }
    }

    private void incrementAndWarn(CommandSender sender) {
        checkCount++;
        if (checkCount % 15 == 0) {
            sender.sendMessage(NapTienPlugin.f("§6[PayBot] §fNếu quá mệt với việc làm thủ công bạn có thể cân nhắc inbox admin để setup hệ thống auto giao dịch nhé :3"));
        }
    }

    private String formatVnd(int amount) {
        StringBuilder sb = new StringBuilder(String.valueOf(amount));
        int len = sb.length();
        for (int i = len - 3; i > 0; i -= 3) sb.insert(i, '.');
        return sb.toString();
    }

    public int getCheckCount() { return checkCount; }
    public void addCheckCount(int n) { checkCount += n; }
}
