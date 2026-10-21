package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import com.paybot.commands.DynamicPaymentCommandRegistry;
import com.paybot.compat.SafeItemFallbackResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

/**
 * SetupSePayGui — v7.0.0 Part 172
 * Cấu hình Cổng Ngân Hàng SePay, khớp chuẩn 100% bản mô phỏng paybot_setup_gui.html.
 * Bố cục 4-4 đối xứng chuẩn mực.
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn thành 1 class đơn nhiệm độc lập.
 */
public class SetupSePayGui implements InventoryHolder {

    public static final String TITLE = "§6§lCài Đặt SePay & Ngân Hàng";

    private final PayBotPlugin plugin;
    private final Inventory inventory;

    public SetupSePayGui(PayBotPlugin plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, TITLE);
        buildLayout();
    }

    private void buildLayout() {
        ItemStack border = createBorder();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, border);
        }

        boolean bankOn = DynamicPaymentCommandRegistry.getInstance().isBankEnabled();
        String token = plugin.getConfig().getString("sepay.api-token", "");
        String displayToken = token.length() > 8 ? token.substring(0, 4) + "••••••••" + token.substring(token.length() - 4) : "(Chưa đặt)";
        String stk = plugin.getConfig().getString("sepay.account-number", "(Chưa đặt)");
        String bank = plugin.getConfig().getString("sepay.bank-name", "MBBank");
        String prefix = plugin.getConfig().getString("sepay.prefix", "NAP");
        int poll = plugin.getConfig().getInt("sepay.poll-interval-seconds", 10);
        String pollStr = poll >= 60 ? (poll / 60) + " phút" : poll + " giây";
        String template = plugin.getConfig().getString("sepay.qr-template", "qr_only");

        // ── HÀNG 2: 4 Ô CẤU HÌNH TRÊN (Slots 10, 12, 14, 16) ──────────────────────
        // Slot 10: Trạng thái SePay API
        String dyeItem = bankOn ? "lime_dye" : "gray_dye";
        inventory.setItem(10, createGuiItem(dyeItem, "ink_sack", "wool",
                "§e§lTrạng Thái SePay API: " + (bankOn ? "§aĐang BẬT" : "§cĐang TẮT"),
                Arrays.asList(
                        "§7Bật hoặc tắt hệ thống nạp ngân hàng tự động.",
                        "",
                        "§f• Trạng thái: " + (bankOn ? "§aBẬT (/napbank MỞ)" : "§cTẮT (/napbank ĐÓNG)"),
                        "§c• Khi tắt: Lệnh /napbank sẽ bị vô hiệu hóa hoàn toàn!",
                        "",
                        "§e▶ [Click] để Bật/Tắt cổng SePay"
                )));

        // Slot 12: API Token SePay
        inventory.setItem(12, createGuiItem("tripwire_hook", "lever", "iron_ingot",
                "§b§lAPI Token SePay",
                Arrays.asList(
                        "§7Mã token API từ my.sepay.vn.",
                        "",
                        "§7Token: §f" + displayToken,
                        "",
                        "§e▶ [Click] để nhập Token mới qua chat"
                )));

        // Slot 14: Số Tài Khoản (STK)
        inventory.setItem(14, createGuiItem("paper", "paper", "book",
                "§a§lSố Tài Khoản (STK)",
                Arrays.asList(
                        "§7STK nhận tiền chuyển khoản.",
                        "",
                        "§7STK: §f" + stk,
                        "",
                        "§e▶ [Click] để sửa Số tài khoản"
                )));

        // Slot 16: Tên Ngân Hàng Nhận
        inventory.setItem(16, createGuiItem("gold_ingot", "iron_ingot", "iron_ingot",
                "§e§lTên Ngân Hàng Nhận",
                Arrays.asList(
                        "§7Tên ngân hàng (MBBank, VCB, Techcombank...).",
                        "",
                        "§7Ngân hàng: §f" + bank,
                        "",
                        "§e▶ [Click] để sửa Tên ngân hàng"
                )));

        // ── HÀNG 4: 4 Ô CẤU HÌNH DƯỚI (Slots 28, 30, 32, 34) ──────────────────────
        // Slot 28: Tần Suất Quét SePay
        inventory.setItem(28, createGuiItem("clock", "compass", "watch",
                "§d§lTần Suất Quét Giao Dịch: §b" + pollStr,
                Arrays.asList(
                        "§7Thời gian giãn cách giữa các lần quét API SePay.",
                        "§7(Xoay vòng: 5s -> 10s -> 15s -> 30s -> 45s -> 1p -> 2p -> 5p -> 10p)",
                        "",
                        "§e▶ [Click Trái] để chuyển sang mốc tiếp theo",
                        "§b▶ [Shift + Click] để nhập số giây tùy ý qua Chat"
                )));

        // Slot 30: Mẫu Ảnh VietQR Template
        inventory.setItem(30, createGuiItem("filled_map", "map", "paper",
                "§e§lMẫu Ảnh VietQR: §b" + template,
                Arrays.asList(
                        "§7Giao diện ảnh VietQR từ my.sepay.vn.",
                        "§7(Xoay vòng: qr_only -> compact -> compact2 -> print)",
                        "",
                        "§e▶ [Click] để đổi template VietQR"
                )));

        // Slot 32: Tiền Tố Chuyển Khoản (Prefix)
        inventory.setItem(32, createGuiItem("name_tag", "paper", "sign",
                "§b§lTiền Tố Chuyển Khoản (Prefix)",
                Arrays.asList(
                        "§7Tiền tố trong nội dung CK (vd: naptien_, NT...).",
                        "",
                        "§7Prefix hiện tại: §e" + prefix,
                        "§c• Bắt buộc: Giữ nguyên chữ hoa/thường, không dấu.",
                        "",
                        "§e▶ [Click] để đổi Cú pháp mã nạp"
                )));

        // Slot 34: Kiểm Tra Kết Nối SePay API
        inventory.setItem(34, createGuiItem("emerald", "diamond", "gold_ingot",
                "§a§lKiểm Tra Kết Nối SePay",
                Arrays.asList(
                        "§7Gửi request test token đến API my.sepay.vn.",
                        "§7Kết quả test CHỈ gửi riêng cho bạn trong chat.",
                        "",
                        "§e▶ [Click] để gửi request kiểm tra ngay"
                )));

        // ── HÀNG 6: QUAY LẠI (Slot 49) ───────────────────────────────────────────
        inventory.setItem(49, createGuiItem("arrow", "arrow", "bedrock",
                "§c§l◀ Quay Lại Menu Chính",
                Arrays.asList("§7Trở về Master Setup Hub.")
        ));
    }

    private ItemStack createBorder() {
        return createGuiItem("gray_stained_glass_pane", "stained_glass_pane", "glass", "§8", null);
    }

    private ItemStack createGuiItem(String p, String s, String u, String name, List<String> lore) {
        ItemStack item = SafeItemFallbackResolver.resolveItemStack(p, s, u, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public void open(Player player) {
        if (player != null) player.openInventory(this.inventory);
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }
}
