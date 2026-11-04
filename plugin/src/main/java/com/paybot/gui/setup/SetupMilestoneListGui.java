package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import com.paybot.milestone.MilestoneConfig;
import com.paybot.milestone.MilestoneManager;
import com.paybot.milestone.MilestoneModel;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * SetupMilestoneListGui — v7.0.2 (Part 186)
 * TUÂN THỦ RULE 17: Container Menu 54 ô Quản lý Mốc Nạp 100% Custom Phân Trang.
 * <p>
 * ĐẶC TẢ:
 * 1. 2 Nút riêng biệt: Mốc Cá Nhân & Mốc Toàn Server.
 * 2. Phân trang mượt mà (21 mốc / trang).
 * 3. Cho phép sửa tiền mốc, sửa lệnh thưởng, xóa mốc (kể cả mốc mặc định) và thêm mốc mới.
 */
public class SetupMilestoneListGui implements InventoryHolder {

    public static final String TITLE = "§d§lQuản Lý Mốc Nạp §8(v7.0.2)";
    public static final int[] MILESTONE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final PayBotPlugin plugin;
    private final Inventory inventory;
    private final boolean isServerMode;
    private final int page;
    private final Map<Integer, Long> slotToAmountMap = new HashMap<>();

    public SetupMilestoneListGui(PayBotPlugin plugin, boolean isServerMode, int page) {
        this.plugin = plugin;
        this.isServerMode = isServerMode;
        this.page = Math.max(0, page);
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

        // Lấy danh sách mốc thật từ MilestoneConfig
        List<Map.Entry<Long, MilestoneModel>> allMilestones = new ArrayList<>();
        if (MilestoneManager.getInstance() != null) {
            MilestoneConfig config = MilestoneManager.getInstance().getConfig();
            if (config != null) {
                Map<Long, MilestoneModel> map = isServerMode ? config.getGlobalMilestones() : config.getSingleMilestones();
                allMilestones.addAll(map.entrySet());
            }
        }
        allMilestones.sort(Comparator.comparingLong(Map.Entry::getKey));

        int pageSize = MILESTONE_SLOTS.length; // 21
        int totalItems = allMilestones.size();
        int startIndex = page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalItems);

        Material star = VanillaItemRegistry.resolveMaterial("NETHER_STAR", "DIAMOND", "EMERALD");

        if (totalItems == 0) {
            // Hiển thị ô trống thông báo
            Material barrier = VanillaItemRegistry.resolveMaterial("BARRIER", "REDSTONE_BLOCK", "GLASS");
            inventory.setItem(22, VanillaItemRegistry.createItem(barrier,
                    "§c§lChưa Có Mốc Nạp Nào",
                    "§7Hiện chưa có mốc nạp nào trong danh mục này.",
                    "",
                    "§e▶ Bấm ô [+ Thêm Mốc Nạp Mới] bên dưới để tạo ngay!"));
        } else {
            for (int i = startIndex; i < endIndex; i++) {
                int slotIndex = i - startIndex;
                int slot = MILESTONE_SLOTS[slotIndex];
                Map.Entry<Long, MilestoneModel> entry = allMilestones.get(i);
                long amount = entry.getKey();
                MilestoneModel model = entry.getValue();
                slotToAmountMap.put(slot, amount);

                List<String> lore = new ArrayList<>();
                lore.add("§7Lệnh thưởng: §e(" + (model != null ? model.getCommands().size() : 0) + " lệnh)");
                if (model != null && !model.getCommands().isEmpty()) {
                    int previewCount = Math.min(model.getCommands().size(), 3);
                    for (int c = 0; c < previewCount; c++) {
                        lore.add("  §8• §b" + model.getCommands().get(c));
                    }
                    if (model.getCommands().size() > 3) {
                        lore.add("  §8... và " + (model.getCommands().size() - 3) + " lệnh khác");
                    }
                }
                lore.add("");
                lore.add("§e▶ [Click Trái] để sửa Số tiền mốc");
                lore.add("§e▶ [Shift + Click Trái] để sửa Lệnh thưởng");
                lore.add("§c▶ [Shift + Click Phải] để Xóa mốc này");

                inventory.setItem(slot, VanillaItemRegistry.createItem(star,
                        "§6§lMốc Nạp: §e" + String.format("%,d", amount) + " VNĐ",
                        lore.toArray(new String[0])));
            }
        }

        // Slot 45: Quay lại Hub chính
        Material door = VanillaItemRegistry.resolveMaterial("OAK_DOOR", "WOODEN_DOOR", "ARROW");
        inventory.setItem(45, VanillaItemRegistry.createItem(door, "§c§l◀ Quay Lại Menu Chính",
                "§7Trở về Master Setup Hub."));

        // Slot 47: Trang trước
        if (page > 0) {
            Material prevArrow = VanillaItemRegistry.resolveMaterial("ARROW", "FEATHER");
            inventory.setItem(47, VanillaItemRegistry.createItem(prevArrow,
                    "§e§l◀ Trang Trước (" + page + ")",
                    "§7Bấm để quay về trang trước."));
        }

        // Slot 49: Nút Thêm Mốc Nạp Mới
        Material anvil = VanillaItemRegistry.resolveMaterial("ANVIL", "EMERALD_BLOCK", "WORKBENCH");
        inventory.setItem(49, VanillaItemRegistry.createItem(anvil, "§a§l+ Thêm Mốc Nạp Mới",
                "§7Tạo một mốc thưởng tích lũy mới cho " + (isServerMode ? "Toàn Server" : "Cá Nhân") + ".",
                "",
                "§e▶ [Click] để nhập số tiền mốc mới"));

        // Slot 51: Trang sau
        if (endIndex < totalItems) {
            Material nextArrow = VanillaItemRegistry.resolveMaterial("ARROW", "FEATHER");
            inventory.setItem(51, VanillaItemRegistry.createItem(nextArrow,
                    "§e§lTrang Sau (" + (page + 2) + ") ▶",
                    "§7Bấm để xem các mốc ở trang tiếp theo."));
        }

        // Slot 53: Nút sang Chu Kỳ Reset
        Material clock = VanillaItemRegistry.resolveMaterial("CLOCK", "COMPASS");
        inventory.setItem(53, VanillaItemRegistry.createItem(clock, "§3§lCài Đặt Chu Kỳ Reset Mốc",
                "§7Cấu hình thời gian và chu kỳ reset mốc " + (isServerMode ? "Toàn Server" : "Cá Nhân") + ".",
                "",
                "§e▶ [Click] để mở menu Chu Kỳ Reset"));
    }

    public boolean isServerMode() {
        return isServerMode;
    }

    public int getPage() {
        return page;
    }

    public Long getAmountAtSlot(int slot) {
        return slotToAmountMap.get(slot);
    }

    public void open(Player player) {
        if (player != null) player.openInventory(this.inventory);
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }
}
