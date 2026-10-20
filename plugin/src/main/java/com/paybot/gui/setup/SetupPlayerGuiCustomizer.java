package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * SetupPlayerGuiCustomizer — v7.0.0 (Part 160)
 * TUÂN THỦ RULE 17: Container Menu 54 ô Tùy biến Giao diện Player GUI (/napbank & /napthe).
 * <p>
 * ĐẶC TẢ:
 * 1. Khôi phục 2 slot tùy chỉnh riêng Tiêu đề Container /napbank và /napthe.
 * 2. Tùy biến Display Name & Lore 9 cục len nhà mạng (Viettel, Vina, Mobi, Zing...).
 * 3. Hỗ trợ mã màu Hex, Gradient, Color Codes vanilla.
 */
public class SetupPlayerGuiCustomizer implements InventoryHolder {

    public static final String TITLE = "§a§lTùy Biến Player GUI §8(v7.0.0)";

    private final PayBotPlugin plugin;
    private final Inventory inventory;

    public SetupPlayerGuiCustomizer(PayBotPlugin plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, TITLE);
        buildLayout();
    }

    private void buildLayout() {
        ItemStack border = VanillaItemRegistry.createBorderPane();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, border);
        }

        String titleBank = plugin.getConfig().getString("gui.title-bank", "§b§lChọn Mệnh Giá Nạp Bank");
        String titleCard = plugin.getConfig().getString("gui.title-card", "§e§lChọn Nhà Mạng Nạp Thẻ");

        // Slot 11: Tiêu đề Container Nạp Bank
        Material chest = VanillaItemRegistry.resolveMaterial("CHEST", "ENDER_CHEST");
        inventory.setItem(11, VanillaItemRegistry.createItem(chest, "§6§lTiêu Đề Container /napbank",
                "§7Tiêu đề hiển thị trên thanh Container khi người chơi mở /napbank.",
                "§7Hiện tại: " + titleBank,
                "",
                "§e▶ [Click] để sửa Tiêu đề Container Nạp Bank"));

        // Slot 15: Tiêu đề Container Nạp Thẻ
        Material book = VanillaItemRegistry.resolveMaterial("BOOK", "PAPER");
        inventory.setItem(15, VanillaItemRegistry.createItem(book, "§e§lTiêu Đề Container /napthe",
                "§7Tiêu đề hiển thị trên thanh Container khi người chơi mở /napthe.",
                "§7Hiện tại: " + titleCard,
                "",
                "§e▶ [Click] để sửa Tiêu đề Container Nạp Thẻ"));

        // 9 Cục len nhà mạng (Slots 28 đến 36)
        String[] telcos = {"VIETTEL", "VINAPHONE", "MOBIFONE", "VIETNAMOBILE", "ZING", "GATE", "GARENA", "VCOIN", "APPOTA"};
        String[] colors = {"RED_WOOL", "BLUE_WOOL", "YELLOW_WOOL", "ORANGE_WOOL", "GREEN_WOOL", "PURPLE_WOOL", "LIME_WOOL", "CYAN_WOOL", "PINK_WOOL"};
        String[] fbColors = {"WOOL", "STAINED_CLAY", "CONCRETE"};

        for (int i = 0; i < telcos.length && i < 9; i++) {
            String telco = telcos[i];
            Material wool = VanillaItemRegistry.resolveMaterial(colors[i], fbColors);
            inventory.setItem(28 + i, VanillaItemRegistry.createItem(wool, "§f§lNhà Mạng: §e" + telco,
                    "§7Tùy biến Tên hiển thị và Lore của cục len " + telco + ".",
                    "§7Hỗ trợ mã màu Hex, Gradient (#RRGGBB, &a, &e...).",
                    "",
                    "§e▶ [Click Trái] để sửa Tên hiển thị",
                    "§e▶ [Shift + Click] để sửa Dòng Lore"));
        }

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
