package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * SetupTransactionManagerGui — v6.1.0 (Part 160)
 * TUÂN THỦ RULE 17: Container Menu 54 ô Quản lý & Lọc Đơn Nạp.
 * <p>
 * ĐẶC TẢ:
 * 1. 3 Bộ lọc trạng thái: ⏳ Chờ duyệt, ✅ Thành công, ❌ Thất bại.
 * 2. Lọc theo hình thức: Bank & Thẻ cào.
 * 3. Phân trang nhiều trang (21 items / trang).
 */
public class SetupTransactionManagerGui implements InventoryHolder {

    public static final String TITLE = "§e§lQuản Lý Đơn Nạp §8(v6.1.0)";

    private final PayBotPlugin plugin;
    private final Inventory inventory;
    private final String statusFilter; // ALL, PENDING, SUCCESS, FAILED
    private final int page;

    public SetupTransactionManagerGui(PayBotPlugin plugin, String statusFilter, int page) {
        this.plugin = plugin;
        this.statusFilter = statusFilter != null ? statusFilter : "ALL";
        this.page = page;
        this.inventory = Bukkit.createInventory(this, 54, TITLE + " [" + this.statusFilter + "]");
        buildLayout();
    }

    private void buildLayout() {
        ItemStack border = VanillaItemRegistry.createBorderPane();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, border);
        }

        // Slot 2: Bộ lọc Chờ Duyệt
        Material clock = VanillaItemRegistry.resolveMaterial("CLOCK", "WATCH");
        inventory.setItem(2, VanillaItemRegistry.createItem(clock,
                ("PENDING".equals(statusFilter) ? "§a§l▶ " : "§7") + "§e§l⏳ Đơn Chờ Duyệt (PENDING)",
                "§7Lọc các đơn đang chờ đối soát hoặc xác nhận thẻ.",
                "",
                "PENDING".equals(statusFilter) ? "§a✔ Đang lọc theo trạng thái này" : "§e▶ [Click] để lọc đơn Chờ duyệt"));

        // Slot 4: Bộ lọc Thành Công
        Material emerald = VanillaItemRegistry.resolveMaterial("EMERALD", "EMERALD_BLOCK");
        inventory.setItem(4, VanillaItemRegistry.createItem(emerald,
                ("SUCCESS".equals(statusFilter) ? "§a§l▶ " : "§7") + "§a§l✅ Đơn Thành Công (SUCCESS)",
                "§7Lọc các đơn nạp tiền đã hoàn tất và giao thưởng.",
                "",
                "SUCCESS".equals(statusFilter) ? "§a✔ Đang lọc theo trạng thái này" : "§e▶ [Click] để lọc đơn Thành công"));

        // Slot 6: Bộ lọc Thất Bại
        Material redstone = VanillaItemRegistry.resolveMaterial("REDSTONE", "REDSTONE_BLOCK");
        inventory.setItem(6, VanillaItemRegistry.createItem(redstone,
                ("FAILED".equals(statusFilter) ? "§a§l▶ " : "§7") + "§c§l❌ Đơn Thất Bại (FAILED)",
                "§7Lọc các đơn bị hủy, thẻ sai hoặc hết hạn giao dịch.",
                "",
                "FAILED".equals(statusFilter) ? "§a✔ Đang lọc theo trạng thái này" : "§e▶ [Click] để lọc đơn Thất bại"));

        // Mẫu đơn tượng trưng (Slots 10-16, 19-25)
        Material paper = VanillaItemRegistry.resolveMaterial("PAPER", "MAP", "BOOK");
        inventory.setItem(11, VanillaItemRegistry.createItem(paper, "§e§lĐơn: §fPB_B260901 §7(Bank)",
                "§7Player: §aTheRealShiroz",
                "§7Số tiền: §e500,000 VNĐ",
                "§7Trạng thái: §a✅ THÀNH CÔNG",
                "§7Thời gian: 2026-10-20 12:00"));

        inventory.setItem(13, VanillaItemRegistry.createItem(paper, "§e§lĐơn: §fPB_C109282 §7(Thẻ Viettel)",
                "§7Player: §aStevePro",
                "§7Mệnh giá: §e100,000 VNĐ",
                "§7Trạng thái: §e⏳ ĐANG CHỜ DUYỆT",
                "",
                "§a▶ [Shift + Click] để Duyệt thủ công"));

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
