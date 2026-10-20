package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * SetupRewardCommandGui — v7.0.0 (Part 160)
 * TUÂN THỦ RULE 17: Container Menu 54 ô Cấu hình Lệnh Thưởng & Hiệu ứng khi nạp thành công.
 */
public class SetupRewardCommandGui implements InventoryHolder {

    public static final String TITLE = "§a§lLệnh Thưởng Nạp Thành Công §8(v7.0.0)";

    private final PayBotPlugin plugin;
    private final Inventory inventory;

    public SetupRewardCommandGui(PayBotPlugin plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, TITLE);
        buildLayout();
    }

    private void buildLayout() {
        ItemStack border = VanillaItemRegistry.createBorderPane();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, border);
        }

        String cmdBank = plugin.getConfig().getString("reward-command-bank", "eco give {player} {amount}");
        String cmdCard = plugin.getConfig().getString("reward-command-card", "eco give {player} {amount}");
        double multiplier = plugin.getConfig().getDouble("reward-multiplier", 1.0);
        boolean firework = plugin.getConfig().getBoolean("effects.firework", true);
        boolean sound = plugin.getConfig().getBoolean("effects.sound", true);
        boolean notif = plugin.getConfig().getBoolean("effects.notification", true);

        // Slot 11: Lệnh thưởng nạp Bank
        Material emerald = VanillaItemRegistry.resolveMaterial("EMERALD", "DIAMOND");
        inventory.setItem(11, VanillaItemRegistry.createItem(emerald, "§6§lLệnh Thưởng Nạp Bank",
                "§7Lệnh thực thi khi người chơi nạp Bank thành công.",
                "§7Placeholder: {player}, {amount}, {prefix}",
                "§7Hiện tại: §e" + cmdBank,
                "",
                "§e▶ [Click] để sửa lệnh nạp Bank"));

        // Slot 13: Lệnh thưởng nạp Thẻ
        Material gold = VanillaItemRegistry.resolveMaterial("GOLD_INGOT", "IRON_INGOT");
        inventory.setItem(13, VanillaItemRegistry.createItem(gold, "§e§lLệnh Thưởng Nạp Thẻ Cào",
                "§7Lệnh thực thi khi người chơi nạp Thẻ cào thành công.",
                "§7Placeholder: {player}, {amount}, {telco}",
                "§7Hiện tại: §e" + cmdCard,
                "",
                "§e▶ [Click] để sửa lệnh nạp Thẻ"));

        // Slot 15: Hệ số nhân (Multiplier)
        Material star = VanillaItemRegistry.resolveMaterial("NETHER_STAR", "DIAMOND");
        inventory.setItem(15, VanillaItemRegistry.createItem(star, "§d§lHệ Số Nhân Nạp (Khuyến Mãi)",
                "§7Hệ số nhân tiền tệ nạp (x1, x1.5, x2, x3...).",
                "§7Hiện tại: §a" + multiplier + "x",
                "",
                "§e▶ [Click] để đổi mốc hệ số nhân"));

        // Slot 29: Pháo hoa
        Material fw = VanillaItemRegistry.resolveMaterial("FIREWORK_ROCKET", "GUNPOWDER");
        inventory.setItem(29, VanillaItemRegistry.createItem(fw, "§c§lHiệu Ứng Bắn Pháo Hoa",
                "§7Bắn pháo hoa ăn mừng tại vị trí người chơi nạp thành công.",
                "§7Trạng thái: " + (firework ? "§aBẬT" : "§cTẮT"),
                "",
                "§e▶ [Click] để Bật / Tắt pháo hoa"));

        // Slot 31: Âm thanh
        Material note = VanillaItemRegistry.resolveMaterial("NOTE_BLOCK", "JUKEBOX");
        inventory.setItem(31, VanillaItemRegistry.createItem(note, "§b§lÂm Thanh Ăn Mừng",
                "§7Phát âm thanh chúc mừng khi hoàn thành đơn nạp.",
                "§7Trạng thái: " + (sound ? "§aBẬT" : "§cTẮT"),
                "",
                "§e▶ [Click] để Bật / Tắt âm thanh"));

        // Slot 33: Thông báo nạp
        Material paper = VanillaItemRegistry.resolveMaterial("PAPER", "BOOK");
        inventory.setItem(33, VanillaItemRegistry.createItem(paper, "§6§lThông Báo Nạp Thưởng",
                "§7Tin nhắn chúc mừng gửi tới người chơi.",
                "§7Trạng thái: " + (notif ? "§aBẬT" : "§cTẮT"),
                "",
                "§e▶ [Click Trái] để Bật / Tắt thông báo",
                "§e▶ [Shift + Click] để sửa nội dung tin nhắn"));

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
