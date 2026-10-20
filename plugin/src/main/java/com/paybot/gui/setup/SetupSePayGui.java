package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * SetupSePayGui — v6.1.0 (Part 160)
 * TUÂN THỦ RULE 17: Container Menu 54 ô Cấu hình Cổng Ngân Hàng SePay.
 */
public class SetupSePayGui implements InventoryHolder {

    public static final String TITLE = "§6§lCấu Hình SePay §8(v6.1.0)";

    private final PayBotPlugin plugin;
    private final Inventory inventory;

    public SetupSePayGui(PayBotPlugin plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, TITLE);
        buildLayout();
    }

    private void buildLayout() {
        ItemStack border = VanillaItemRegistry.createBorderPane();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, border);
        }

        String token = plugin.getConfig().getString("sepay.api-token", "");
        String displayToken = token.length() > 8 ? token.substring(0, 4) + "••••••••" + token.substring(token.length() - 4) : "(Chưa đặt)";
        String stk = plugin.getConfig().getString("sepay.account-number", "(Chưa đặt)");
        String bank = plugin.getConfig().getString("sepay.bank-name", "MBBank");
        String prefix = plugin.getConfig().getString("sepay.prefix", "NAP");
        int poll = plugin.getConfig().getInt("sepay.poll-interval-seconds", 10);

        // Slot 11: API Token
        Material paper = VanillaItemRegistry.resolveMaterial("PAPER", "BOOK");
        inventory.setItem(11, VanillaItemRegistry.createItem(paper, "§e§lAPI Token SePay",
                "§7Token xác thực từ https://my.sepay.vn",
                "§7Hiện tại: §f" + displayToken,
                "",
                "§e▶ [Click] để nhập Token mới qua chat"));

        // Slot 13: Số tài khoản
        Material gold = VanillaItemRegistry.resolveMaterial("GOLD_INGOT", "IRON_INGOT");
        inventory.setItem(13, VanillaItemRegistry.createItem(gold, "§6§lSố Tài Khoản Nhận Tiền",
                "§7STK ngân hàng đã kết nối với SePay.",
                "§7Hiện tại: §e" + stk,
                "",
                "§e▶ [Click] để sửa Số tài khoản"));

        // Slot 15: Ngân hàng
        Material chest = VanillaItemRegistry.resolveMaterial("CHEST", "ENDER_CHEST");
        inventory.setItem(15, VanillaItemRegistry.createItem(chest, "§b§lTên Ngân Hàng",
                "§7Mã ngân hàng (MBBank, Vietcombank, ACB...).",
                "§7Hiện tại: §a" + bank,
                "",
                "§e▶ [Click] để sửa Tên ngân hàng"));

        // Slot 29: Cú pháp mã nạp (Prefix)
        Material tag = VanillaItemRegistry.resolveMaterial("NAME_TAG", "BOOK", "PAPER");
        inventory.setItem(29, VanillaItemRegistry.createItem(tag, "§d§lCú Pháp Mã Nạp (Prefix)",
                "§7Chuỗi ký tự nhận diện đơn chuyển khoản.",
                "§7Quy tắc: Giữ nguyên hoa/thường, chỉ A-Z, a-z, 0-9 và '_'.",
                "§7Hiện tại: §e" + prefix,
                "",
                "§e▶ [Click] để đổi Cú pháp mã nạp"));

        // Slot 31: Chu kỳ Poll SePay
        Material clock = VanillaItemRegistry.resolveMaterial("CLOCK", "COMPASS");
        String pollStr = poll >= 60 ? (poll / 60) + " phút" : poll + " giây";
        inventory.setItem(31, VanillaItemRegistry.createItem(clock, "§3§lThời Gian Poll Đơn SePay",
                "§7Khoảng thời gian kiểm tra giao dịch mới từ SePay.",
                "§7Các mốc hỗ trợ: 5s, 10s, 15s, 30s, 45s, 1p, 2p, 5p, 10p.",
                "§7Hiện tại: §a" + pollStr,
                "",
                "§e▶ [Click Trái] để chuyển sang mốc tiếp theo"));

        // Slot 33: Test Kết Nối SePay
        Material star = VanillaItemRegistry.resolveMaterial("NETHER_STAR", "EMERALD");
        inventory.setItem(33, VanillaItemRegistry.createItem(star, "§a§lKiểm Tra Kết Nối SePay",
                "§7Gửi request kiểm tra Token và trạng thái tài khoản SePay.",
                "§7Kết quả test CHỈ gửi riêng cho bạn trong chat.",
                "",
                "§e▶ [Click] để kiểm tra ngay"));

        // Slot 45: Quay lại Hub chính
        Material door = VanillaItemRegistry.resolveMaterial("OAK_DOOR", "WOODEN_DOOR", "ARROW");
        inventory.setItem(45, VanillaItemRegistry.createItem(door, "§c§l◀ Quay Lại Menu Chính",
                "§7Trở về Master Setup Hub."));
    }

    public void open(Player player) {
        if (player != null) player.openInventory(this.inventory);
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }
}
