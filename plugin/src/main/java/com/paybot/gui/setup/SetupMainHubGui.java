package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

/**
 * SetupMainHubGui — v6.1.0 (Part 160)
 * TUÂN THỦ RULE 17: Container Menu 54 ô Trung tâm Master Setup Hub của PayBot.
 */
public class SetupMainHubGui implements InventoryHolder {

    public static final String TITLE = "§b§lPayBot Setup Hub §8(v6.1.0)";

    private final PayBotPlugin plugin;
    private final Inventory inventory;

    public SetupMainHubGui(PayBotPlugin plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, TITLE);
        buildLayout();
    }

    private void buildLayout() {
        ItemStack border = VanillaItemRegistry.createBorderPane();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, border);
        }

        // Slot 10: SePay Gateway
        Material gold = VanillaItemRegistry.resolveMaterial("GOLD_INGOT", "GOLD_NUGGET", "EMERALD");
        inventory.setItem(10, VanillaItemRegistry.createItem(gold, "§6§lCấu Hình Ngân Hàng (SePay)",
                "§7Cài đặt API Token, Số tài khoản, Ngân hàng,",
                "§7Thời gian poll (30s, 45s, 1p, 2p, 5p, 10p), Cú pháp mã nạp.",
                "",
                "§e▶ [Click] để vào menu Cài đặt SePay"));

        // Slot 12: Thẻ Cào
        Material cardMat = VanillaItemRegistry.resolveMaterial("PAPER", "MAP", "BOOK");
        inventory.setItem(12, VanillaItemRegistry.createItem(cardMat, "§e§lCấu Hình Thẻ Cào (Card API)",
                "§7Chọn website gạch thẻ (TheSieuRe, GachThePro...),",
                "§7Cài đặt Partner ID, Partner Key, Webhook URL.",
                "",
                "§e▶ [Click] để vào menu Cài đặt Thẻ Cào"));

        // Slot 14: Lệnh Thưởng Nạp Thành Công
        Material cmdBlock = VanillaItemRegistry.resolveMaterial("COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "DISPENSER");
        inventory.setItem(14, VanillaItemRegistry.createItem(cmdBlock, "§a§lLệnh Thưởng Nạp Thành Công",
                "§7Cấu hình lệnh chạy khi nạp Bank & Thẻ cào thành công.",
                "§7Lệnh theo mệnh giá 10k-1M, Hệ số nhân x1/x2/x3,",
                "§7Hiệu ứng pháo hoa, âm thanh, thông báo broadcast.",
                "",
                "§e▶ [Click] để cài đặt Lệnh Thưởng"));

        // Slot 16: Quản Lý Mốc Nạp
        Material star = VanillaItemRegistry.resolveMaterial("NETHER_STAR", "DIAMOND", "EMERALD");
        inventory.setItem(16, VanillaItemRegistry.createItem(star, "§d§lQuản Lý Mốc Nạp (Custom 100%)",
                "§72 Nút riêng biệt: Mốc Cá Nhân & Mốc Toàn Server.",
                "§7Phân trang nhiều trang, sửa tiền mốc, lệnh mốc, xóa mốc.",
                "",
                "§e▶ [Click] để quản lý Mốc Nạp"));

        // Slot 28: CSDL SQLite / MySQL & Port HTTP
        Material redstone = VanillaItemRegistry.resolveMaterial("REDSTONE_BLOCK", "REDSTONE", "HOPPER");
        String dbType = plugin.getConfig().getString("database-type", "SQLite");
        inventory.setItem(28, VanillaItemRegistry.createItem(redstone, "§b§lCơ Sở Dữ Liệu & Hệ Thống",
                "§7Cấu hình SQLite / MySQL (Host, Port, User, Pass).",
                "§c⚠ NẾU LỖI CSDL: Tự tắt Mod/Plugin & tắt Server an toàn.",
                "§c⚠ TUYỆT ĐỐI KHÔNG FALLBACK tự ý.",
                "",
                "§a• CSDL hiện tại: §f" + dbType,
                "",
                "§e▶ [Click] để cấu hình CSDL & Hệ thống"));

        // Slot 30: Tùy Biến Giao Diện Player GUI
        Material painting = VanillaItemRegistry.resolveMaterial("PAINTING", "ITEM_FRAME", "BOOK");
        inventory.setItem(30, VanillaItemRegistry.createItem(painting, "§a§lTùy Biến Giao Diện Player GUI",
                "§7Tùy chỉnh riêng Tiêu đề Container /napbank & /napthe.",
                "§7Custom Name & Lore 9 cục len nhà mạng viễn thông.",
                "§7Hỗ trợ mã màu Hex, Gradient, PlaceholderAPI.",
                "",
                "§e▶ [Click] để mở Menu Tùy biến Player GUI"));

        // Slot 32: Quản Lý Đơn Nạp
        Material book = VanillaItemRegistry.resolveMaterial("WRITABLE_BOOK", "BOOK_AND_QUILL", "BOOK");
        inventory.setItem(32, VanillaItemRegistry.createItem(book, "§e§lLịch Sử & Tra Cứu Đơn Nạp",
                "§73 Bộ lọc trạng thái: ⏳ Chờ duyệt, ✅ Thành công, ❌ Thất bại.",
                "§7Lọc theo hình thức Bank / Thẻ cào, phân trang nhiều trang.",
                "",
                "§e▶ [Click] để xem danh sách Đơn nạp"));

        // Slot 34: Giả Lập Nạp Ảo Test
        Material firework = VanillaItemRegistry.resolveMaterial("FIREWORK_ROCKET", "GUNPOWDER", "BLAZE_POWDER");
        inventory.setItem(34, VanillaItemRegistry.createItem(firework, "§c§lGiả Lập Nạp Ảo (Simulation Test)",
                "§7Test nạp Bank ảo & nạp Thẻ cào ảo từ 10k đến 1000k.",
                "§7Kiểm tra ngay lập tức lệnh thưởng và hiệu ứng in-game.",
                "",
                "§e▶ [Click] để vào khu vực Giả lập nạp ảo"));

        // Slot 40: Chu Kỳ Reset Mốc Nạp Độc Lập
        Material clock = VanillaItemRegistry.resolveMaterial("CLOCK", "COMPASS", "WATCH");
        inventory.setItem(40, VanillaItemRegistry.createItem(clock, "§3§lChu Kỳ Reset Mốc Nạp Độc Lập",
                "§7Reset mốc cá nhân và toàn server tách biệt hoàn toàn.",
                "§7Hỗ trợ CUSTOM_DAYS (số ngày nguyên dương > 0).",
                "§7Logic phụ thuộc: Bật thông báo mới cho sửa tin nhắn broadcast.",
                "",
                "§e▶ [Click] để cài đặt chu kỳ Reset"));

        // Slot 49: Reload cấu hình
        Material barrier = VanillaItemRegistry.resolveMaterial("NETHER_STAR", "EMERALD_BLOCK", "GLOWSTONE");
        inventory.setItem(49, VanillaItemRegistry.createItem(barrier, "§a§lÁp Dụng & Nạp Lại Cấu Hình (/paybot reload)",
                "§7Lưu toàn bộ thay đổi và nạp lại runtime an toàn 100%.",
                "",
                "§e▶ [Click] để thực hiện Reload ngay"));
    }

    public void open(Player player) {
        if (player != null) player.openInventory(this.inventory);
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }
}
