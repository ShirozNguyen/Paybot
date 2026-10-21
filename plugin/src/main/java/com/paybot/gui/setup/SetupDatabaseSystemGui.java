package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
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
 * SetupDatabaseSystemGui — v7.0.0 Part 172
 * Cấu hình CSDL SQLite / MySQL & Port HTTP, khớp chuẩn 100% bản mô phỏng paybot_setup_gui.html.
 * Chế độ Staging Buffer: Nút Xác Nhận (Slot 47) & Nút Undo (Slot 51).
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn thành 1 class đơn nhiệm độc lập.
 */
public class SetupDatabaseSystemGui implements InventoryHolder {

    public static final String TITLE = "§b§lCơ Sở Dữ Liệu & Hệ Thống";

    private final PayBotPlugin plugin;
    private final Inventory inventory;

    public SetupDatabaseSystemGui(PayBotPlugin plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, TITLE);
        buildLayout();
    }

    private void buildLayout() {
        ItemStack border = createBorder();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, border);
        }

        String dbType = plugin.getConfig().getString("database-type", "SQLite");
        String host = plugin.getConfig().getString("mysql.host", "localhost");
        int port = plugin.getConfig().getInt("mysql.port", 3306);
        String db = plugin.getConfig().getString("mysql.database", "paybot");
        String user = plugin.getConfig().getString("mysql.user", "root");

        // Slot 11: Loại CSDL
        inventory.setItem(11, createGuiItem("redstone_block", "emerald_block", "iron_block",
                "§6§lCơ Sở Dữ Liệu: §e" + dbType,
                Arrays.asList(
                        "§7Chuyển đổi giữa SQLite (Local File) và MySQL (Remote Server).",
                        "§c⚠ NẾU LỖI CSDL: Tự tắt Mod/Plugin & tắt Server an toàn.",
                        "§c⚠ TUYỆT ĐỐI KHÔNG FALLBACK tự ý.",
                        "§7Hiện tại: §a" + dbType,
                        "",
                        "§e▶ [Click] để chuyển đổi giữa SQLite / MySQL"
                )));

        // Slot 13: Host MySQL
        inventory.setItem(13, createGuiItem("paper", "paper", "book",
                "§b§lMySQL Host / Địa Chỉ IP",
                Arrays.asList(
                        "§7Địa chỉ máy chủ cơ sở dữ liệu MySQL.",
                        "§7Hiện tại: §f" + host,
                        "",
                        "§e▶ [Click] để sửa MySQL Host"
                )));

        // Slot 15: Port MySQL
        inventory.setItem(15, createGuiItem("iron_ingot", "gold_ingot", "iron_ingot",
                "§e§lMySQL Port",
                Arrays.asList(
                        "§7Cổng kết nối MySQL (Mặc định: 3306).",
                        "§7Hiện tại: §f" + port,
                        "",
                        "§e▶ [Click] để sửa MySQL Port"
                )));

        // Slot 29: Database Name
        inventory.setItem(29, createGuiItem("chest", "chest", "hopper",
                "§a§lMySQL Database Name",
                Arrays.asList(
                        "§7Tên cơ sở dữ liệu (schema) trong MySQL.",
                        "§7Hiện tại: §f" + db,
                        "",
                        "§e▶ [Click] để sửa Tên Database"
                )));

        // Slot 31: Username
        inventory.setItem(31, createGuiItem("player_head", "skull_item", "book",
                "§d§lMySQL Username",
                Arrays.asList(
                        "§7Tài khoản đăng nhập MySQL.",
                        "§7Hiện tại: §f" + user,
                        "",
                        "§e▶ [Click] để sửa Username"
                )));

        // Slot 33: Password
        inventory.setItem(33, createGuiItem("tripwire_hook", "lever", "iron_ingot",
                "§c§lMySQL Password",
                Arrays.asList(
                        "§7Mật khẩu đăng nhập MySQL.",
                        "§7Hiện tại: §f••••••••",
                        "",
                        "§e▶ [Click] để nhập Password mới"
                )));

        // Slot 35: Test Kết Nối CSDL
        inventory.setItem(35, createGuiItem("nether_star", "diamond", "gold_ingot",
                "§a§lKiểm Tra Kết Nối CSDL",
                Arrays.asList(
                        "§7Thử kết nối tới MySQL / SQLite ngay lập tức.",
                        "§7Kết quả test CHỈ gửi riêng tới bạn trong chat.",
                        "",
                        "§e▶ [Click] để gửi request kiểm tra ngay"
                )));

        // ── HÀNG 6: STAGING BUFFER XÁC NHẬN, QUAY LẠI, UNDO (Slots 47, 49, 51) ───────
        // Slot 47: Xác Nhận Cấu Hình CSDL
        inventory.setItem(47, createGuiItem("emerald_block", "emerald", "gold_block",
                "§a§l[Xác Nhận Lưu & Áp Dụng CSDL]",
                Arrays.asList(
                        "§7Bấm để lưu toàn bộ thông số CSDL đang nhập vào hệ thống.",
                        "§e• Bắt buộc bấm xác nhận mới có hiệu lực!",
                        "",
                        "§e▶ [Click] để Lưu & Áp Dụng"
                )));

        // Slot 49: Quay Lại (Không Lưu)
        inventory.setItem(49, createGuiItem("arrow", "arrow", "bedrock",
                "§c§l◀ Quay Lại (Hủy Bỏ)",
                Arrays.asList("§7Trở về Master Setup Hub mà không lưu các thay đổi CSDL.")
        ));

        // Slot 51: Hoàn Tác (Undo CSDL)
        inventory.setItem(51, createGuiItem("barrier", "redstone_block", "bedrock",
                "§e§l[Hoàn Tác CSDL Về Ban Đầu]",
                Arrays.asList(
                        "§7Hủy toàn bộ thông số đang sửa dở trong buffer,",
                        "§7khôi phục lại cấu hình đang lưu ban đầu trên đĩa.",
                        "",
                        "§e▶ [Click] để Hoàn Tác"
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
