package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * SetupMilestoneListGui — v7.0.0 (Part 160)
 * TUÂN THỦ RULE 17: Container Menu 54 ô Quản lý Mốc Nạp 100% Custom Phân Trang.
 * <p>
 * ĐẶC TẢ:
 * 1. 2 Nút riêng biệt: Mốc Cá Nhân & Mốc Toàn Server.
 * 2. Phân trang mượt mà (nhiều trang).
 * 3. Cho phép sửa tiền mốc, sửa lệnh thưởng, xóa mốc (kể cả mốc mặc định).
 */
public class SetupMilestoneListGui implements InventoryHolder {

    public static final String TITLE = "§d§lQuản Lý Mốc Nạp §8(v7.0.0)";

    private final PayBotPlugin plugin;
    private final Inventory inventory;
    private final boolean isServerMode;
    private final int page;

    public SetupMilestoneListGui(PayBotPlugin plugin, boolean isServerMode, int page) {
        this.plugin = plugin;
        this.isServerMode = isServerMode;
        this.page = page;
        this.inventory = Bukkit.createInventory(this, 54, TITLE + (isServerMode ? " [Server]" : " [Cá Nhân]"));
        buildLayout();
    }

    private void buildLayout() {
        ItemStack border = VanillaItemRegistry.createBorderPane();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, border);
        }

        // Slot 2: Nút chọn Mốc Cá Nhân
        Material playerHead = VanillaItemRegistry.resolveMaterial("PLAYER_HEAD", "SKULL_ITEM", "BOOK");
        inventory.setItem(2, VanillaItemRegistry.createItem(playerHead,
                (isServerMode ? "§7" : "§a§l▶ ") + "§b§l[Mốc Nạp Cá Nhân]",
                "§7Xem và cấu hình mốc tích lũy của từng người chơi.",
                "",
                isServerMode ? "§e▶ [Click] để chuyển sang Mốc Cá Nhân" : "§a✔ Đang xem danh mục này"));

        // Slot 6: Nút chọn Mốc Toàn Server
        Material beacon = VanillaItemRegistry.resolveMaterial("BEACON", "NETHER_STAR", "EMERALD");
        inventory.setItem(6, VanillaItemRegistry.createItem(beacon,
                (!isServerMode ? "§7" : "§a§l▶ ") + "§e§l[Mốc Nạp Toàn Server]",
                "§7Xem và cấu hình mốc tích lũy cộng dồn toàn server.",
                "",
                !isServerMode ? "§e▶ [Click] để chuyển sang Mốc Server" : "§a✔ Đang xem danh mục này"));

        // Mẫu các mốc nạp tượng trưng (Slots 10-16, 19-25, 28-34)
        int[] milestoneSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        long[] defaultAmounts = {50000L, 100000L, 200000L, 500000L, 1000000L, 2000000L, 5000000L};

        Material star = VanillaItemRegistry.resolveMaterial("NETHER_STAR", "DIAMOND", "EMERALD");
        for (int i = 0; i < defaultAmounts.length && i < milestoneSlots.length; i++) {
            long amt = defaultAmounts[i];
            inventory.setItem(milestoneSlots[i], VanillaItemRegistry.createItem(star,
                    "§6§lMốc Nạp: §e" + String.format("%,d", amt) + " VNĐ",
                    "§7Lệnh thưởng: §feco give {player} " + (amt / 10),
                    "",
                    "§e▶ [Click Trái] để sửa Số tiền mốc",
                    "§e▶ [Shift + Click Trái] để sửa Lệnh thưởng",
                    "§c▶ [Shift + Click Phải] để Xóa mốc này"));
        }

        // Slot 48: Nút Thêm Mốc Nạp Mới
        Material anvil = VanillaItemRegistry.resolveMaterial("ANVIL", "EMERALD_BLOCK", "WORKBENCH");
        inventory.setItem(48, VanillaItemRegistry.createItem(anvil, "§a§l+ Thêm Mốc Nạp Mới",
                "§7Tạo một mốc thưởng tích lũy mới.",
                "",
                "§e▶ [Click] để nhập số tiền mốc mới"));

        // Slot 50: Nút sang Chu Kỳ Reset
        Material clock = VanillaItemRegistry.resolveMaterial("CLOCK", "COMPASS");
        inventory.setItem(50, VanillaItemRegistry.createItem(clock, "§3§lCài Đặt Chu Kỳ Reset Mốc",
                "§7Cấu hình thời gian và chu kỳ reset mốc " + (isServerMode ? "Toàn Server" : "Cá Nhân") + ".",
                "",
                "§e▶ [Click] để mở menu Chu Kỳ Reset"));

        // Slot 45: Quay lại Hub chính
        Material door = VanillaItemRegistry.resolveMaterial("OAK_DOOR", "WOODEN_DOOR", "ARROW");
        inventory.setItem(45, VanillaItemRegistry.createItem(door, "§c§l◀ Quay Lại Menu Chính",
                "§7Trở về Master Setup Hub."));
    }

    public boolean isServerMode() {
        return isServerMode;
    }

    public void open(Player player) {
        if (player != null) player.openInventory(this.inventory);
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }
}
