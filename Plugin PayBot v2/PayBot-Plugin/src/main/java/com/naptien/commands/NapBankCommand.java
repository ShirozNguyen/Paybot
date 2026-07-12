package com.naptien.commands;

import com.naptien.NapTienPlugin;
import com.naptien.gui.NapBankGui;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

/**
 * /napbank — opens the bank transfer GUI directly.
 * Text-mode fallback: /napbank <amount>
 *
 * Changelog:
 *   v5.0.0 — Đổi QR primary sang VietQR template "qr_only" (không logo, không
 *            tên ngân hàng) thay cho qr.sepay.vn; thêm BANK_BIN_MAP để map
 *            tên ngân hàng admin nhập (VD: "MB", "Vietcombank") sang đúng
 *            BIN code NAPAS mà VietQR yêu cầu. qr.sepay.vn lùi xuống làm fallback.
 */
public final class NapBankCommand implements CommandExecutor {

    private final NapTienPlugin plugin;

    public NapBankCommand(NapTienPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fLệnh này chỉ dành cho player!"));
            return true;
        }
        if (args.length == 0) {
            NapBankGui.open(player, plugin);
            return true;
        }

        int amount;
        try {
            String raw = args[0].toLowerCase().trim();
            if (raw.endsWith("k"))  amount = (int)(Double.parseDouble(raw.replace("k",""))  * 1_000);
            else if (raw.endsWith("tr")) amount = (int)(Double.parseDouble(raw.replace("tr","")) * 1_000_000);
            else amount = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            player.sendMessage(NapTienPlugin.f("§c[PayBot] §fSố tiền không hợp lệ! VD: §e/napbank 50000 §fhoặc §e/napbank 50k"));
            return true;
        }

        requestAndGiveQR(player, amount);
        return true;
    }

    /**
     * Called both from command (shorthand /napbank <amount>) and from NapBankGui click.
     * Creates QR order and sends it to the player.
     * <p>
     * v5.0.0 (fix Thứ 7): TRƯỚC ĐÂY validate (10k-50tr bounds + isSepayConfigured) chỉ nằm
     * trong onCommand() — đường GUI-click (GuiListener gọi trực tiếp method này) hoàn toàn
     * KHÔNG qua check nào, dẫn tới 2 đường vào (GUI vs lệnh tắt) không đồng nhất validate.
     * Giờ validate nằm DUY NHẤT ở đây — áp dụng như nhau cho cả 2 đường vào.
     * Cũng thêm log rõ "mode=STANDALONE/BOT-CONNECTED" để chẩn đoán trực tiếp qua console
     * nếu nghi ngờ server bị rơi vào standalone dù đang connect bot (báo cáo trước đó).
     */
    public void requestAndGiveQR(Player player, int amount) {
        if (amount < 10_000) {
            player.sendMessage(NapTienPlugin.f("§c[PayBot] §fSố tiền tối thiểu là §e10,000 VND§f."));
            return;
        }
        if (amount > 50_000_000) {
            player.sendMessage(NapTienPlugin.f("§c[PayBot] §fSố tiền quá lớn! Vui lòng liên hệ admin."));
            return;
        }
        if (!isSepayConfigured()) {
            player.sendMessage(NapTienPlugin.f("§c[PayBot] §fServer chưa được cấu hình SePay."));
            player.sendMessage("§7Admin dùng §e/sepaysetup §7để cấu hình.");
            return;
        }

        boolean standalone = plugin.isStandaloneMode();
        plugin.getLogger().info("[PayBot] /napbank: player=" + player.getName() + " amount=" + amount
                + " mode=" + (standalone ? "STANDALONE" : "BOT-CONNECTED"));

        // v5.1.0: Luôn tạo QR trực tiếp qua SePay/VietQR (không phụ thuộc bot).
        // Nếu bot-connected → notify bot sau khi tạo đơn (fire-and-forget) để Discord thông báo.
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String invoiceId   = com.naptien.managers.TransferContentGenerator.generate(plugin);
            String bankAccount = plugin.getConfig().getString("sepay.bank-account", "").trim();
            String bankName    = plugin.getConfig().getString("sepay.bank-name",    "").trim();
            String accountName = plugin.getConfig().getString("sepay.account-name", "").trim();

            if (bankAccount.isEmpty() || bankName.isEmpty()) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.sendMessage(NapTienPlugin.f("§c[PayBot] §fChưa cấu hình SePay! Admin dùng §e/sepaysetup§f.")));
                return;
            }

            plugin.getLocalOrderManager().createBankOrder(invoiceId, player.getName(), amount);
            // v5.1.0: không cần registerOrder lên bot nữa — tự poll SePay API trực tiếp.
            // Nếu bot-connected, tự notify sau khi có thanh toán (trong pollSePayApiTransactions).

            // v5.0.0: VietQR (qr_only, không logo) làm PRIMARY, qr.sepay.vn làm fallback
            String qrPrimary  = buildVietQRUrl(bankName, bankAccount, amount, invoiceId);
            String qrFallback = buildSepayQRUrl(bankName, bankAccount, accountName, amount, invoiceId);

            boolean showOverlay = plugin.getConfig().getBoolean("qr-show-info-overlay", false);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.sendMessage(NapTienPlugin.f("§e[PayBot] §fĐang tạo QR chuyển khoản..."));
                if (showOverlay) {
                    plugin.getQRMapManager().giveQRMap(player, qrPrimary, qrFallback, invoiceId,
                            bankName, bankAccount, accountName, amount);
                } else {
                    plugin.getQRMapManager().giveQRMap(player, qrPrimary, qrFallback, invoiceId);
                }
                player.sendMessage("§7Số tiền: §f" + formatVnd(amount) + " VND");
                player.sendMessage("§7Nội dung CK: §e" + invoiceId);
                player.sendMessage("§7QR hết hạn sau §e30 phút§7.");
            });
        });
    }

    // v5.0.0: generateInvoiceId() cố định "PB"+12 số đã được thay bằng
    // TransferContentGenerator.generate(plugin) (tuỳ biến qua config transfer-content.*).

    // ── Bank BIN map (NAPAS) — public reference data, không phải secret ────────
    // Dùng để VietQR.io nhận diện đúng ngân hàng dù admin nhập tên/viết tắt khác nhau.
    private static final java.util.Map<String, String> BANK_BIN_MAP = new java.util.HashMap<>();
    static {
        String[][] groups = {
            {"VCB,VIETCOMBANK",                  "970436"},
            {"ICB,CTG,VIETINBANK",                "970415"},
            {"BIDV",                              "970418"},
            {"AGRIBANK,VBA",                      "970405"},
            {"MB,MBB,MBBANK",                     "970422"},
            {"TCB,TECHCOMBANK",                   "970407"},
            {"ACB",                                "970416"},
            {"VPB,VPBANK",                          "970432"},
            {"TPB,TPBANK",                          "970423"},
            {"STB,SACOMBANK",                       "970403"},
            {"HDB,HDBANK",                          "970437"},
            {"VIB",                                  "970441"},
            {"SHB",                                  "970443"},
            {"OCB",                                  "970448"},
            {"MSB,MARITIMEBANK",                     "970426"},
            {"SEAB,SEABANK",                         "970440"},
            {"EIB,EXIMBANK",                         "970431"},
            {"LPB,LIENVIETPOSTBANK,LVB",             "970449"},
            {"NAB,NAMABANK",                         "970428"},
            {"PVCOMBANK,PVCB",                       "970412"},
            {"BAB,BACABANK",                         "970409"},
            {"BVB,BAOVIETBANK",                      "970438"},
            {"VCCB,BANVIET,VIETCAPITALBANK",         "970454"},
            {"SCB",                                  "970429"},
            {"ABB,ABBANK",                           "970425"},
            {"VAB,VIETABANK",                        "970427"},
            {"PGB,PGBANK",                           "970430"},
            {"KLB,KIENLONGBANK",                     "970452"},
            {"DAB,DONGABANK",                        "970406"},
            {"NCB",                                  "970419"},
            {"PBVN,PUBLICBANK",                      "970439"},
            {"WOORI",                                "970457"},
            {"SHBVN,SHINHAN",                        "970424"},
            {"SCVN,STANDARDCHARTERED",               "970410"},
            {"COOPBANK",                             "970446"},
            {"GPB,GPBANK",                           "970408"},
            {"VRB",                                  "970421"},
            {"IVB",                                  "970434"},
            {"CIMB",                                 "422589"},
            {"CAKE",                                 "546034"},
            {"UBANK",                                "546035"},
            {"TIMO",                                 "963388"},
        };
        for (String[] pair : groups) {
            for (String alias : pair[0].split(",")) {
                BANK_BIN_MAP.put(alias, pair[1]);
            }
        }
    }

    /** Chuẩn hoá tên ngân hàng admin nhập (bỏ khoảng trắng/dấu câu, in hoa) → tra BIN. */
    private static String resolveBankBin(String bankName) {
        if (bankName == null) return null;
        String norm = bankName.toUpperCase().replaceAll("[^A-Z0-9]", "");
        String bin = BANK_BIN_MAP.get(norm);
        if (bin != null) return bin;
        for (java.util.Map.Entry<String, String> e : BANK_BIN_MAP.entrySet()) {
            if (norm.contains(e.getKey())) return e.getValue();
        }
        return null;
    }

    /**
     * v5.0.0 — PRIMARY: VietQR.io template "qr_only" — chỉ ảnh QR thuần,
     * KHÔNG logo, KHÔNG tên ngân hàng/khung VietQR. Không cần API key.
     * Docs: https://www.vietqr.io/danh-sach-api/link-tao-ma-vietqr
     */
    private static String buildVietQRUrl(String bankName, String bankAccount, int amount, String invoiceId) {
        try {
            String bin    = resolveBankBin(bankName);
            String bankId = (bin != null) ? bin : bankName.trim();
            return "https://img.vietqr.io/image/"
                    + java.net.URLEncoder.encode(bankId,      "UTF-8") + "-"
                    + java.net.URLEncoder.encode(bankAccount, "UTF-8")
                    + "-qr_only.png"
                    + "?amount=" + amount
                    + "&addInfo=" + java.net.URLEncoder.encode(invoiceId, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    /** FALLBACK (v5.0.0): qr.sepay.vn — dùng khi VietQR không khả dụng. */
    private static String buildSepayQRUrl(String bankName, String bankAccount,
                                           String accountName, int amount, String invoiceId) {
        try {
            StringBuilder sb = new StringBuilder("https://qr.sepay.vn/img?");
            sb.append("acc=").append(java.net.URLEncoder.encode(bankAccount, "UTF-8"));
            sb.append("&bank=").append(java.net.URLEncoder.encode(bankName, "UTF-8"));
            sb.append("&amount=").append(amount);
            sb.append("&des=").append(java.net.URLEncoder.encode(invoiceId, "UTF-8"));
            if (!accountName.isEmpty()) {
                sb.append("&accountName=").append(java.net.URLEncoder.encode(accountName, "UTF-8"));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String formatVnd(int amount) {
        StringBuilder sb = new StringBuilder(String.valueOf(amount));
        int len = sb.length();
        for (int i = len - 3; i > 0; i -= 3) sb.insert(i, '.');
        return sb.toString();
    }

    private boolean isSepayConfigured() {
        // v5.1.0: luôn kiểm tra SePay config (không bypass khi bot-connected
        // vì giờ cả 2 mode đều xử lý QR trực tiếp, không qua bot)
        return plugin.getSetupManager().isSePayConfigured();
    }
}
