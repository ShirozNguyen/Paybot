package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import com.paybot.compat.SafeItemFallbackResolver;
import com.paybot.commands.DynamicPaymentCommandRegistry;
import com.paybot.managers.PlayerCooldownManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

/**
 * SetupMainHubGui — v7.0.0 Part 172
 * Master Setup Hub 54 ô trung tâm của PayBot, khớp chuẩn 100% bản mô phỏng paybot_setup_gui.html.
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn thành 1 class đơn nhiệm độc lập.
 */
public class SetupMainHubGui implements InventoryHolder {

    public static final String TITLE = "§6§lPayBot Master Setup Hub";

    private final PayBotPlugin plugin;
    private final Inventory inventory;

    public SetupMainHubGui(PayBotPlugin plugin) {
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
        boolean cardOn = DynamicPaymentCommandRegistry.getInstance().isCardEnabled();
        String prefix = plugin.getConfig().getString("sepay.prefix", "PAYBOT_");
        int pollSec = plugin.getConfig().getInt("sepay.poll-seconds", 30);
        String cardSite = plugin.getConfig().getString("card.partner-url", "thesieure.com");
        String partnerId = plugin.getConfig().getString("card.partner-id", "(Chưa đặt)");
        String dbType = plugin.getConfig().getString("database-type", "SQLite");
        int cdQr = PlayerCooldownManager.getInstance().getQrMapSeconds();
        int cdCard = PlayerCooldownManager.getInstance().getCardSubmitSeconds();
        boolean msEnabled = plugin.getConfig().getBoolean("milestones.enabled", false);

        // ── HÀNG 2: CÁC CỔNG THANH TOÁN & NẠP (Slots 10, 12, 14, 16) ──────────────
        // Slot 10: SePay Gateway
        inventory.setItem(10, createGuiItem("emerald_block", "emerald", "gold_block",
                "§e§lCài Đặt SePay & Ngân Hàng",
                Arrays.asList(
                        "§7Cấu hình API Token, Ngân hàng, STK,",
                        "§7Prefix mã nạp, và định dạng VietQR.",
                        "",
                        "§a• Trạng thái: " + (bankOn ? "§aBẬT (/napbank MỞ)" : "§cTẮT (/napbank ĐÓNG)"),
                        "§a• Prefix: §f" + prefix,
                        "§a• Tần suất quét: §b" + pollSec + "s",
                        "",
                        "§e▶ [Click] để mở menu SePay"
                )));

        // Slot 12: Thẻ Cào
        inventory.setItem(12, createGuiItem("gold_block", "gold_ingot", "iron_block",
                "§6§lCài Đặt Thẻ Cào (Web Chuẩn)",
                Arrays.asList(
                        "§7Tích hợp cổng gạch thẻ uy tín đã kiểm chứng.",
                        "§7Tự động duyệt thẻ cào trong 5-15 giây.",
                        "",
                        "§a• Trạng thái: " + (cardOn ? "§aBẬT (/napthe MỞ)" : "§cTẮT (/napthe ĐÓNG)"),
                        "§a• Site đối tác: §b" + cardSite,
                        "§a• Partner ID: §f" + partnerId,
                        "",
                        "§e▶ [Click] để mở menu Thẻ Cào"
                )));

        // Slot 14: Lệnh Thưởng Nạp
        inventory.setItem(14, createGuiItem("diamond", "emerald", "gold_ingot",
                "§a§lLệnh Thưởng Nạp Thành Công",
                Arrays.asList(
                        "§7Cấu hình chuỗi lệnh console chạy khi nạp tiền.",
                        "§e• Tách biệt cấu hình riêng Bank và Thẻ Cào.",
                        "§e• Hỗ trợ cấu hình riêng cho từng mệnh giá (;;).",
                        "",
                        "§a• Pháo hoa & Âm thanh: §fBẬT",
                        "",
                        "§e▶ [Click] để cài đặt Lệnh Thưởng"
                )));

        // Slot 16: Quản Lý Mốc Nạp
        inventory.setItem(16, createGuiItem("nether_star", "diamond", "diamond",
                "§d§lQuản Lý Mốc Nạp (Milestones)",
                Arrays.asList(
                        "§7Thiết lập các mốc nạp tích lũy và phần thưởng.",
                        "§7Hỗ trợ phân chia Mốc Cá Nhân & Mốc Server.",
                        "§7Chu kỳ tự động Reset định kỳ hoặc Reset ngay.",
                        "",
                        "§f• Trạng thái hệ thống: " + (msEnabled ? "§aBẬT" : "§cTẮT (Mặc định)"),
                        "§7(Không kích hoạt mặc định, an toàn tuyệt đối)",
                        "",
                        "§e▶ [Click] để quản lý Mốc Nạp"
                )));

        // ── HÀNG 3: HỆ THỐNG & QUẢN TRỊ (Slots 19, 21, 23, 25) ────────────────────
        // Slot 19: CSDL
        inventory.setItem(19, createGuiItem("redstone_block", "redstone", "redstone",
                "§b§lCơ Sở Dữ Liệu & Hệ Thống",
                Arrays.asList(
                        "§7Cấu hình SQLite / MySQL (Host, Port, User, Pass).",
                        "§c⚠ Nếu CSDL lỗi: Tự tắt Mod/Plugin & tắt Server để bảo vệ dữ liệu.",
                        "§c⚠ TUYỆT ĐỐI KHÔNG FALLBACK tự ý.",
                        "",
                        "§a• CSDL: §f" + dbType,
                        "",
                        "§e▶ [Click] để vào cấu hình CSDL"
                )));

        // Slot 21: Player GUI
        inventory.setItem(21, createGuiItem("painting", "item_frame", "book",
                "§a§lTùy Biến Giao Diện Player GUI",
                Arrays.asList(
                        "§7Custom Name & Lore từng cục len nhà mạng.",
                        "§a• 9 Cục len nhà mạng dàn đều trọn vẹn trên 1 hàng duy nhất!",
                        "§7Tùy chỉnh riêng tiêu đề Container /napbank & /napthe.",
                        "§7Hỗ trợ mã màu Hex, Gradient, PlaceholderAPI.",
                        "",
                        "§e▶ [Click] để tùy biến Player GUI"
                )));

        // Slot 23: Lịch Sử & Lọc Đơn
        inventory.setItem(23, createGuiItem("writable_book", "book", "paper",
                "§e§lLịch Sử & Bộ Lọc Đơn Nạp",
                Arrays.asList(
                        "§7Bộ lọc động 100%: Chờ duyệt, Thành công, Thất bại.",
                        "§7Lọc theo Bank / Thẻ cào, duyệt đơn nạp thủ công.",
                        "",
                        "§e▶ [Click] để mở Lịch sử Đơn nạp"
                )));

        // Slot 25: Test Simulation
        inventory.setItem(25, createGuiItem("brewing_stand", "potion", "glass_bottle",
                "§c§lGiả Lập Nạp Thử (Test Simulation)",
                Arrays.asList(
                        "§7Mô phỏng nạp tiền ảo (Bank & Thẻ cào).",
                        "§7Đầy đủ 9 mệnh giá & 9 nhà mạng.",
                        "",
                        "§e▶ [Click] để vào Giả lập nạp ảo"
                )));

        // ── HÀNG 4: BẢO MẬT & COOLDOWN (Slots 29, 31, 33) ──────────────────────────
        // Slot 29: Cooldown QR Map
        inventory.setItem(29, createGuiItem("clock", "compass", "paper",
                "§e§lCooldown Tạo QR Map: §b" + cdQr + " giây",
                Arrays.asList(
                        "§7Thời gian giãn cách tối thiểu giữa 2 lần player",
                        "§7gõ lệnh tạo bản đồ VietQR (/napbank).",
                        "§c• Chống spam Packet tạo map tràn RAM server.",
                        "§f• Định dạng: Số nguyên dương (giây)",
                        "",
                        "§a• Hiện tại: §e" + cdQr + "s",
                        "§7(Left-Click: xoay vòng 5s->10s->15s->30s->60s)",
                        "§7(Shift-Click: nhập số giây tùy ý qua Chat)"
                )));

        // Slot 31: Bảo Mật Chat & Network Zero-Leak
        inventory.setItem(31, createGuiItem("shield", "iron_door", "obsidian",
                "§b§lBảo Mật Chat & Network Zero-Leak",
                Arrays.asList(
                        "§7Chặn bắt gói Netty Packet trước pipeline Minecraft.",
                        "§7Loại bỏ hoàn toàn rò rỉ mã PIN, Serial, Token",
                        "§7khỏi Console Server và file latest.log.",
                        "§a• Trạng thái: §aHoạt động toàn diện 102 targets"
                )));

        // Slot 33: Cooldown Gửi Thẻ
        inventory.setItem(33, createGuiItem("repeater", "redstone_torch", "lever",
                "§6§lCooldown Gửi Thẻ Cào: §b" + cdCard + " giây",
                Arrays.asList(
                        "§7Thời gian giãn cách tối thiểu giữa 2 lần player",
                        "§7gửi thông tin thẻ cào lên cổng gạch thẻ (/napthe).",
                        "§c• Chống spam DDoS làm cổng gạch thẻ trả mã lỗi 429.",
                        "§f• Định dạng: Số nguyên dương (giây)",
                        "",
                        "§a• Hiện tại: §e" + cdCard + "s",
                        "§7(Left-Click: xoay vòng 15s->30s->45s->60s->120s)",
                        "§7(Shift-Click: nhập số giây tùy ý qua Chat)"
                )));

        // ── HÀNG 6: THANH ĐIỀU HƯỚNG & HỆ THỐNG (Slots 47, 49, 51) ─────────────────
        // Slot 47: Reload Cấu Hình
        inventory.setItem(47, createGuiItem("nether_star", "command_block", "compass",
                "§a§lNạp Lại Toàn Bộ Cấu Hình (/paybot reload)",
                Arrays.asList(
                        "§7Đọc lại toàn bộ file cấu hình từ ổ đĩa và áp dụng ngay.",
                        "§a• Cơ chế: Mọi thay đổi trong Menu đều được tự động ngầm reload.",
                        "§e• Nhấn nút này nếu tương tác chưa nhận hoặc cần force reload.",
                        "",
                        "§f• Lệnh chat: §b/paybot reload",
                        "",
                        "§e▶ [Click] để Nạp lại ngay"
                )));

        // Slot 49: Đóng Menu
        inventory.setItem(49, createGuiItem("barrier", "redstone_block", "bedrock",
                "§c§lĐóng Menu",
                Arrays.asList("§7Nhấn để thoát giao diện cài đặt.")
        ));

        // Slot 51: Trạng Thái Lưu Trữ & Đồng Bộ (Auto-Reload Live)
        inventory.setItem(51, createGuiItem("book", "book", "paper",
                "§b§lTrạng Thái Lưu Trữ & Đồng Bộ",
                Arrays.asList(
                        "§7Mọi thao tác chỉnh sửa đều được tự động lưu vào đĩa",
                        "§7và tự động ngầm reload vào bộ nhớ runtime O(1).",
                        "",
                        "§a• Cơ chế ngầm: §fHoạt động Live 100%"
                )));
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
