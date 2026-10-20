package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * SetupDatabaseSystemGui — v7.0.0 (Part 160)
 * TUÂN THỦ RULE 17: Container Menu 54 ô Cấu hình CSDL SQLite / MySQL & Port HTTP.
 * <p>
 * NGUYÊN TẮC AN TOÀN:
 * 1. Nếu CSDL không kết nối được hoặc hỏng data: Tự tắt plugin & tắt Server ngay để bảo vệ dữ liệu.
 * 2. TUYỆT ĐỐI KHÔNG FALLBACK tự ý.
 * 3. Kết quả test CSDL CHỈ gửi riêng tới Admin trong chat.
 */
public class SetupDatabaseSystemGui implements InventoryHolder {

    public static final String TITLE = "§b§lCơ Sở Dữ Liệu & Hệ Thống §8(v7.0.0)";

    private final PayBotPlugin plugin;
    private final Inventory inventory;

    public SetupDatabaseSystemGui(PayBotPlugin plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, TITLE);
        buildLayout();
    }

    private void buildLayout() {
        ItemStack border = VanillaItemRegistry.createBorderPane();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, border);
        }

        String dbType = plugin.getConfig().getString("database-type", "SQLite");
        String host = plugin.getConfig().getString("mysql.host", "localhost");
        int port = plugin.getConfig().getInt("mysql.port", 3306);
        String db = plugin.getConfig().getString("mysql.database", "paybot");
        String user = plugin.getConfig().getString("mysql.user", "root");
        int httpPort = plugin.getConfig().getInt("http-server.port", 8080);

        // Slot 11: Loại CSDL (SQLite / MySQL)
        Material dbMat = VanillaItemRegistry.resolveMaterial("REDSTONE_BLOCK", "EMERALD_BLOCK", "IRON_BLOCK");
        inventory.setItem(11, VanillaItemRegistry.createItem(dbMat, "§6§lCơ Sở Dữ Liệu: §e" + dbType,
                "§7Chuyển đổi giữa SQLite (Local File) và MySQL (Remote Server).",
                "§c⚠ NẾU LỖI CSDL: Tự tắt Mod/Plugin & tắt Server an toàn.",
                "§c⚠ TUYỆT ĐỐI KHÔNG FALLBACK tự ý.",
                "§7Hiện tại: §a" + dbType,
                "",
                "§e▶ [Click] để chuyển đổi giữa SQLite / MySQL"));

        // Slot 13: Host MySQL
        Material paper = VanillaItemRegistry.resolveMaterial("PAPER", "MAP");
        inventory.setItem(13, VanillaItemRegistry.createItem(paper, "§b§lMySQL Host / Địa Chỉ IP",
                "§7Địa chỉ máy chủ cơ sở dữ liệu MySQL.",
                "§7Hiện tại: §f" + host,
                "",
                "§e▶ [Click] để sửa MySQL Host"));

        // Slot 15: Port MySQL
        Material iron = VanillaItemRegistry.resolveMaterial("IRON_INGOT", "GOLD_INGOT");
        inventory.setItem(15, VanillaItemRegistry.createItem(iron, "§e§lMySQL Port",
                "§7Cổng kết nối MySQL (Mặc định: 3306).",
                "§7Hiện tại: §f" + port,
                "",
                "§e▶ [Click] để sửa MySQL Port"));

        // Slot 29: Database Name
        Material chest = VanillaItemRegistry.resolveMaterial("CHEST", "HOPPER");
        inventory.setItem(29, VanillaItemRegistry.createItem(chest, "§a§lMySQL Database Name",
                "§7Tên cơ sở dữ liệu (schema) trong MySQL.",
                "§7Hiện tại: §f" + db,
                "",
                "§e▶ [Click] để sửa Tên Database"));

        // Slot 31: Username
        Material skull = VanillaItemRegistry.resolveMaterial("PLAYER_HEAD", "SKULL_ITEM", "BOOK");
        inventory.setItem(31, VanillaItemRegistry.createItem(skull, "§d§lMySQL Username",
                "§7Tài khoản đăng nhập MySQL.",
                "§7Hiện tại: §f" + user,
                "",
                "§e▶ [Click] để sửa Username"));

        // Slot 33: Password
        Material trip = VanillaItemRegistry.resolveMaterial("TRIPWIRE_HOOK", "LEVER");
        inventory.setItem(33, VanillaItemRegistry.createItem(trip, "§c§lMySQL Password",
                "§7Mật khẩu đăng nhập MySQL.",
                "§7Hiện tại: §f••••••••",
                "",
                "§e▶ [Click] để nhập Password mới"));

        // Slot 35: Test Kết Nối CSDL
        Material star = VanillaItemRegistry.resolveMaterial("NETHER_STAR", "DIAMOND");
        inventory.setItem(35, VanillaItemRegistry.createItem(star, "§a§lKiểm Tra Kết Nối CSDL",
                "§7Thử kết nối tới MySQL / SQLite ngay lập tức.",
                "§7Kết quả test CHỈ gửi riêng tới bạn trong chat.",
                "§c⚠ Cảnh báo: CSDL lỗi sẽ làm tắt Server để bảo vệ an toàn nạp tiền.",
                "",
                "§e▶ [Click] để kiểm tra kết nối ngay"));

        // Slot 40: Port HTTP Server
        Material comp = VanillaItemRegistry.resolveMaterial("COMPARATOR", "REPEATER");
        inventory.setItem(40, VanillaItemRegistry.createItem(comp, "§3§lPort Plugin HTTP Server",
                "§7Cổng mở trên máy chủ để nhận Webhook / Discord Bot.",
                "§7Hiện tại: §e" + httpPort,
                "",
                "§e▶ [Click] để sửa Port HTTP Server"));

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
