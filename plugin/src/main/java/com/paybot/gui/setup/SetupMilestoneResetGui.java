package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * SetupMilestoneResetGui — v7.0.0 (Part 160)
 * TUÂN THỦ RULE 17: Container Menu 54 ô Cấu hình Chu Kỳ Reset Mốc Nạp Độc Lập.
 * <p>
 * ĐẶC TẢ:
 * 1. Chu kỳ reset mốc cá nhân và toàn server tách biệt, không trùng lặp.
 * 2. Hỗ trợ CUSTOM_DAYS (số ngày nguyên dương > 0).
 * 3. Logic phụ thuộc: Bật broadcast mới cho sửa tin nhắn broadcast.
 * 4. Nút Reset Ngay Lập Tức.
 */
public class SetupMilestoneResetGui implements InventoryHolder {

    public static final String TITLE = "§3§lChu Kỳ Reset Mốc Nạp §8(v7.0.0)";

    private final PayBotPlugin plugin;
    private final Inventory inventory;
    private final boolean isServerMode;

    public SetupMilestoneResetGui(PayBotPlugin plugin, boolean isServerMode) {
        this.plugin = plugin;
        this.isServerMode = isServerMode;
        this.inventory = Bukkit.createInventory(this, 54, TITLE + (isServerMode ? " [Server]" : " [Cá Nhân]"));
        buildLayout();
    }

    private void buildLayout() {
        ItemStack border = VanillaItemRegistry.createBorderPane();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, border);
        }

        String unit = plugin.getConfig().getString("milestone.reset-unit", "MONTHLY");
        int customDays = plugin.getConfig().getInt("milestone.custom-days", 30);
        boolean broadcast = plugin.getConfig().getBoolean("milestone.reset-broadcast", true);
        String broadcastMsg = plugin.getConfig().getString("milestone.reset-message", "§a[Mốc Nạp] §fMốc nạp tích lũy đã được reset cho chu kỳ mới!");

        // Slot 11: Đơn vị chu kỳ Reset
        Material clock = VanillaItemRegistry.resolveMaterial("CLOCK", "COMPASS");
        inventory.setItem(11, VanillaItemRegistry.createItem(clock, "§6§lChu Kỳ Reset Mốc: §e" + unit,
                "§7Các chu kỳ: NEVER, DAILY, WEEKLY, MONTHLY, CUSTOM_DAYS.",
                "§7Hiện tại: §a" + unit,
                "",
                "§e▶ [Click] để đổi sang chu kỳ khác"));

        // Slot 13: Khoảng cách chu kỳ (CUSTOM_DAYS)
        Material repeater = VanillaItemRegistry.resolveMaterial("REPEATER", "REDSTONE");
        inventory.setItem(13, VanillaItemRegistry.createItem(repeater, "§b§lKhoảng Cách Reset (CUSTOM_DAYS)",
                "§7Áp dụng khi chọn chu kỳ CUSTOM_DAYS.",
                "§7Số ngày nguyên dương: Sau đúng X ngày sẽ tự động reset.",
                "§7Hiện tại: §e" + customDays + " ngày",
                "",
                "§e▶ [Click] để sửa số ngày reset (chỉ nhập số nguyên > 0)"));

        // Slot 15: Thông báo Broadcast khi Reset
        Material bell = VanillaItemRegistry.resolveMaterial("BELL", "NOTE_BLOCK", "GOLD_BLOCK");
        inventory.setItem(15, VanillaItemRegistry.createItem(bell, "§d§lThông Báo Khi Reset",
                "§7Phát thông báo cho toàn server khi tới chu kỳ reset.",
                "§7Trạng thái: " + (broadcast ? "§aBẬT" : "§cTẮT"),
                "§7Tin nhắn: §f" + broadcastMsg,
                "",
                "§e▶ [Click Trái] để Bật / Tắt thông báo",
                broadcast ? "§e▶ [Shift + Click] để sửa Tin nhắn broadcast" : "§c⚠ Phải BẬT thông báo mới sửa được tin nhắn"));

        // Slot 31: Nút Reset Ngay Lập Tức
        Material tnt = VanillaItemRegistry.resolveMaterial("TNT", "REDSTONE_BLOCK", "FIREWORK_STAR");
        inventory.setItem(31, VanillaItemRegistry.createItem(tnt, "§c§l[!] RESET NGAY LẬP TỨC",
                "§7Xóa và đặt lại tiến trình mốc nạp " + (isServerMode ? "Toàn Server" : "Cá Nhân") + " ngay bây giờ.",
                "§c⚠ Hành động này không thể hoàn tác!",
                "",
                "§c▶ [Shift + Click] để xác nhận Reset"));

        // Slot 45: Quay lại danh sách mốc nạp
        Material door = VanillaItemRegistry.resolveMaterial("OAK_DOOR", "WOODEN_DOOR", "ARROW");
        inventory.setItem(45, VanillaItemRegistry.createItem(door, "§c§l◀ Quay Lại Menu Mốc Nạp",
                "§7Trở về Quản lý Mốc Nạp."));
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
