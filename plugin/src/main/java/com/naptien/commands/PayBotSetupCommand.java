package com.naptien.commands;

import com.naptien.NapTienPlugin;
import org.bukkit.command.*;

/**
 * PayBotSetupCommand — /PayBotSetup
 *
 * Hiển thị tổng quan trạng thái cấu hình của plugin ở Standalone mode.
 * Admin dùng lệnh này để xem nhanh: SePay, Card API, Webhook port có OK không.
 *
 * Changelog:
 *   v4.1.0 — Tái tạo từ .class (file nguồn gốc không có trong ZIP)
 */
public class PayBotSetupCommand implements CommandExecutor {

    private final NapTienPlugin plugin;

    public PayBotSetupCommand(NapTienPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("naptien.admin")) {
            sender.sendMessage("§cYou Don't Have Permission To Use This!");
            return true;
        }

        boolean sePayOk   = plugin.getConfig().getBoolean("sepay.configured",    false);
        boolean cardApiOk = plugin.getConfig().getBoolean("card-api.configured", false);
        boolean botMode   = !plugin.isStandaloneMode();
        int     port      =  plugin.getConfig().getInt("plugin-port", 25580);

        String sePaySite = plugin.getConfig().getString("sepay.bank-name", "").trim();
        String sePayAcc  = plugin.getConfig().getString("sepay.bank-account", "").trim();
        String cardSite  = plugin.getConfig().getString("card-api.site", "").trim();

        sender.sendMessage(NapTienPlugin.f("§8§m────────────────────────────────────"));
        sender.sendMessage(NapTienPlugin.f("§6§l  PayBot §r§7— Setup Status §7(v" + plugin.getDescription().getVersion() + ")"));
        sender.sendMessage(NapTienPlugin.f("§8§m────────────────────────────────────"));

        // Chế độ
        sender.sendMessage("§7Chế độ : " + (botMode
                ? "§b[Bot-connected] §7— các cấu hình sau do bot quản lý"
                : "§a[Standalone]"));

        // v5.0.3 [Part 21]: thêm trạng thái kết nối bot — trước đây /paybotsetup
        // không hề nhắc tới bot-url/cert dù đây là phần quan trọng nhất của chế độ
        // Bot-connected (đã bỏ ngrok, giờ HTTPS self-signed + TOFU cert-pinning).
        if (botMode) {
            String botUrl = plugin.getConfig().getString("bot-url", "").trim();
            String fp     = plugin.getConfig().getString("bot-cert-fingerprint", "").trim();
            sender.sendMessage("§7Bot URL : " + (botUrl.isEmpty()
                    ? "§c✗ Chưa có §7— dùng §e/connect §7để kết nối"
                    : "§a✓ " + botUrl));
            if (!botUrl.isEmpty()) {
                sender.sendMessage("§7Cert    : " + (fp.isEmpty()
                        ? "§e⏳ Chưa xác thực lần nào (sẽ tự lưu ở request đầu tiên)"
                        : "§a✓ Đã pin fingerprint (TOFU)"));
            }
        }
        sender.sendMessage("§7Auto-sync config §7: " + (plugin.getConfig().getBoolean("auto-config-sync", true)
                ? "§a✓ Bật §7(tự thêm/xoá key mỗi 10s)" : "§c✗ Tắt"));

        // SePay
        String sePayStatus = sePayOk
                ? "§a✓ Đã cấu hình §7(" + sePaySite + " / " + maskAcct(sePayAcc) + ")"
                : "§c✗ Chưa cấu hình §7— dùng §e/sepaysetup §7để thiết lập";
        sender.sendMessage("§7SePay   : " + sePayStatus);

        // Card API
        String cardStatus = cardApiOk
                ? "§a✓ Đã cấu hình §7(site: §e" + cardSite + "§7)"
                : "§c✗ Chưa cấu hình §7— dùng §e/cardsetup §7để thiết lập";
        sender.sendMessage("§7Card API: " + cardStatus);

        // HTTP port — v5.0.3: đổi nhãn "Webhook port" → mô tả đúng hơn (port này giờ
        // chủ yếu phục vụ bot push reward/config, webhook SePay chỉ còn là tuỳ chọn).
        sender.sendMessage("§7HTTP port (nhận lệnh từ bot) §7: §e" + port
                + " §7(forward từ router nếu dùng VPS ngoài)");

        // Hướng dẫn
        if (!sePayOk || !cardApiOk) {
            sender.sendMessage("");
            sender.sendMessage("§eHướng dẫn:");
            if (!sePayOk)   sender.sendMessage("  §7• §e/sepaysetup §7→ cấu hình SePay (nạp bank)");
            // v5.0.2 FIX: dòng cũ mô tả sai — "/cardsetup" KHÔNG phải lệnh chỉnh mệnh giá,
            // mà là cấu hình site/Partner ID/Partner Key/channel Discord cho API gạch thẻ
            // (đúng phạm vi /cardapisetup cũ trước khi bị gộp vào). Mệnh giá + lệnh thưởng
            // là việc của /chinhsuamenhgianap (dòng dưới) — 2 lệnh khác hẳn nhau.
            if (!cardApiOk) sender.sendMessage("  §7• §e/cardsetup  §7→ cấu hình API gạch thẻ (site/Partner ID/Key/channel)");
            sender.sendMessage("  §7• §e/chinhsuamenhgianap §7→ thiết lập mệnh giá + lệnh thưởng");
        } else {
            sender.sendMessage("");
            sender.sendMessage("§a✓ Plugin đã sẵn sàng hoạt động Standalone!");
        }

        sender.sendMessage(NapTienPlugin.f("§8§m────────────────────────────────────"));
        return true;
    }

    private static String maskAcct(String acc) {
        if (acc.length() <= 4) return "****";
        return acc.substring(0, 2) + "****" + acc.substring(acc.length() - 2);
    }
}
