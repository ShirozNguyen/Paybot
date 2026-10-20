package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * SetupSimulationTestGui — v6.1.0 (Part 160)
 * TUÂN THỦ RULE 17: Container Menu 54 ô Giả lập nạp ảo Bank & Thẻ cào để kiểm thử.
 */
public class SetupSimulationTestGui implements InventoryHolder {

    public static final String TITLE = "§c§lGiả Lập Nạp Ảo (Simulation Test) §8(v6.1.0)";

    private final PayBotPlugin plugin;
    private final Inventory inventory;

    public SetupSimulationTestGui(PayBotPlugin plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, TITLE);
        buildLayout();
    }

    private void buildLayout() {
        ItemStack border = VanillaItemRegistry.createBorderPane();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, border);
        }

        long[] denoms = {10000L, 20000L, 50000L, 100000L, 200000L, 500000L, 1000000L};
        String[] denomLabels = {"10K", "20K", "50K", "100K", "200K", "500K", "1 TRIỆU"};

        // Hàng 2: Giả lập nạp Bank (Slots 10 đến 16)
        Material gold = VanillaItemRegistry.resolveMaterial("GOLD_INGOT", "EMERALD");
        for (int i = 0; i < denoms.length; i++) {
            long amt = denoms[i];
            inventory.setItem(10 + i, VanillaItemRegistry.createItem(gold, "§6§lTest Nạp Bank: §e" + denomLabels[i],
                    "§7Giả lập nhận tiền ngân hàng " + String.format("%,d", amt) + " VNĐ.",
                    "§7Kiểm tra ngay lập tức: Lệnh thưởng, pháo hoa, âm thanh.",
                    "",
                    "§e▶ [Click] để kích hoạt giả lập nạp Bank"));
        }

        // Hàng 4: Giả lập nạp Thẻ cào (Slots 28 đến 34)
        Material redstone = VanillaItemRegistry.resolveMaterial("REDSTONE", "REDSTONE_BLOCK");
        for (int i = 0; i < denoms.length; i++) {
            long amt = denoms[i];
            inventory.setItem(28 + i, VanillaItemRegistry.createItem(redstone, "§c§lTest Nạp Thẻ: §e" + denomLabels[i],
                    "§7Giả lập web gạch thẻ duyệt thành công " + String.format("%,d", amt) + " VNĐ.",
                    "§7Kiểm tra ngay lập tức: Lệnh thưởng, pháo hoa, âm thanh.",
                    "",
                    "§e▶ [Click] để kích hoạt giả lập nạp Thẻ"));
        }

        // Slot 40: Mệnh giá tùy ý
        Material diamond = VanillaItemRegistry.resolveMaterial("DIAMOND", "EMERALD");
        inventory.setItem(40, VanillaItemRegistry.createItem(diamond, "§b§l+ Nạp Mệnh Giá Tùy Ý",
                "§7Nhập số tiền bất kỳ qua chat để test nạp.",
                "",
                "§e▶ [Click] để nhập số tiền"));

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
