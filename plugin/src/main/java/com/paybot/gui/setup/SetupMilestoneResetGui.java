package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import com.paybot.milestone.MilestoneConfig;
import com.paybot.milestone.MilestoneManager;
import com.paybot.milestone.MilestoneResetUnit;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * SetupMilestoneResetGui — v7.0.2 (Part 186)
 * TUÂN THỦ RULE 17: Container Menu 54 ô Cấu hình Chu Kỳ Reset Mốc Nạp Độc Lập.
 * <p>
 * ĐẶC TẢ:
 * 1. Đọc và lưu trực tiếp vào milestones.yml (scheduled-reset).
 * 2. Đơn vị: MINUTE, HOUR, DAILY, MONTHLY, YEARLY.
 * 3. Hỗ trợ thay đổi khoảng cách chu kỳ (interval > 0).
 * 4. Bật/Tắt Broadcast & sửa nội dung tin nhắn.
 * 5. Nút Reset Ngay Lập Tức (kết nối trực tiếp MilestoneManager.triggerManualReset()).
 */
public class SetupMilestoneResetGui implements InventoryHolder {

    public static final String TITLE = "§3§lChu Kỳ Reset Mốc Nạp §8(v7.0.2)";

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

        MilestoneConfig config = null;
        if (MilestoneManager.getInstance() != null) {
            config = MilestoneManager.getInstance().getConfig();
        }

        boolean enabled = config != null && config.isScheduledResetEnabled();
        MilestoneResetUnit unit = config != null ? config.getResetUnit() : MilestoneResetUnit.MONTHLY;
        int interval = config != null ? config.getResetInterval() : 1;
        boolean broadcast = config == null || config.isBroadcastOnReset();
        String broadcastMsg = config != null ? config.getResetBroadcastMessage() : "§aMùa nạp mới đã bắt đầu! Mốc nạp server đã được làm mới!";

        // Slot 10: Công tắc Bật/Tắt Tự Động Reset
        Material lever = VanillaItemRegistry.resolveMaterial(enabled ? "EMERALD" : "REDSTONE", "LEVER");
        inventory.setItem(10, VanillaItemRegistry.createItem(lever,
                "§6§lTự Động Reset Theo Chu Kỳ: " + (enabled ? "§aĐang BẬT" : "§cĐang TẮT"),
                "§7Tự động xóa và bắt đầu chu kỳ nạp mới theo lịch trình.",
                "§7Trạng thái: " + (enabled ? "§aBẬT (Tự động reset)" : "§cTẮT (Không bao giờ reset)"),
                "",
                "§e▶ [Click] để Bật / Tắt tự động reset"));

        // Slot 12: Đơn vị chu kỳ Reset
        Material clock = VanillaItemRegistry.resolveMaterial("CLOCK", "COMPASS");
        inventory.setItem(12, VanillaItemRegistry.createItem(clock,
                "§e§lĐơn Vị Chu Kỳ: §a" + (unit != null ? unit.name() : "MONTHLY"),
                "§7Các đơn vị hỗ trợ: MINUTE, HOUR, DAILY, MONTHLY, YEARLY.",
                "§7Hiện tại: §e" + (unit != null ? unit.name() : "MONTHLY"),
                "",
                "§e▶ [Click] để đổi sang đơn vị kế tiếp"));

        // Slot 14: Khoảng cách chu kỳ (interval)
        Material repeater = VanillaItemRegistry.resolveMaterial("REPEATER", "REDSTONE");
        inventory.setItem(14, VanillaItemRegistry.createItem(repeater,
                "§b§lKhoảng Cách Chu Kỳ (Interval): §e" + interval + " " + (unit != null ? unit.name() : "MONTHLY"),
                "§7Khoảng cách giữa các lần reset tự động.",
                "§7Ví dụ: 1 MONTHLY (hàng tháng), 7 DAILY (hàng tuần), 12 HOUR (mỗi 12 tiếng).",
                "",
                "§e▶ [Click] để sửa số khoảng cách"));

        // Slot 16: Thông báo Broadcast khi Reset
        Material bell = VanillaItemRegistry.resolveMaterial("BELL", "NOTE_BLOCK", "GOLD_BLOCK");
        inventory.setItem(16, VanillaItemRegistry.createItem(bell,
                "§d§lThông Báo Khi Reset: " + (broadcast ? "§aBẬT" : "§cTẮT"),
                "§7Phát thông báo cho toàn server khi tới chu kỳ reset.",
                "§7Nội dung: §f" + broadcastMsg,
                "",
                "§e▶ [Click Trái] để Bật / Tắt thông báo",
                broadcast ? "§e▶ [Shift + Click Trái] để sửa Tin nhắn broadcast" : "§c⚠ Cần BẬT thông báo mới sửa được tin nhắn"));

        // Slot 31: Nút Reset Ngay Lập Tức
        Material tnt = VanillaItemRegistry.resolveMaterial("TNT", "REDSTONE_BLOCK", "FIREWORK_STAR");
        inventory.setItem(31, VanillaItemRegistry.createItem(tnt,
                "§c§l[!] RESET NGAY LẬP TỨC",
                "§7Xóa và đặt lại tiến trình mốc nạp " + (isServerMode ? "Toàn Server" : "Cá Nhân") + " ngay bây giờ.",
                "§c⚠ Hành động này đưa tiến trình chu kỳ về 0 và kích hoạt mùa mới!",
                "",
                "§c▶ [Shift + Click] để XÁC NHẬN Reset Ngay"));

        // Slot 45: Quay lại danh sách mốc nạp
        Material door = VanillaItemRegistry.resolveMaterial("OAK_DOOR", "WOODEN_DOOR", "ARROW");
        inventory.setItem(45, VanillaItemRegistry.createItem(door,
                "§c§l◀ Quay Lại Menu Mốc Nạp",
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
