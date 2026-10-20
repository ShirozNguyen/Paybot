package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * SetupCardProviderGui — v6.1.0 (Part 160)
 * TUÂN THỦ RULE 17: Container Menu 54 ô Cấu hình Cổng Gạch Thẻ Cào.
 */
public class SetupCardProviderGui implements InventoryHolder {

    public static final String TITLE = "§e§lCấu Hình Thẻ Cào §8(v6.1.0)";

    private final PayBotPlugin plugin;
    private final Inventory inventory;

    public SetupCardProviderGui(PayBotPlugin plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, TITLE);
        buildLayout();
    }

    private void buildLayout() {
        ItemStack border = VanillaItemRegistry.createBorderPane();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, border);
        }

        String site = plugin.getConfig().getString("card.site", "thesieure.com");
        String partnerId = plugin.getConfig().getString("card.partner-id", "(Chưa đặt)");
        String partnerKey = plugin.getConfig().getString("card.partner-key", "");
        String displayKey = partnerKey.length() > 6 ? partnerKey.substring(0, 3) + "••••••" : "(Chưa đặt)";
        String webhook = plugin.getConfig().getString("card.callback-url", "http://your-server-ip:8080/card-callback");

        // Slot 11: Chọn Website gạch thẻ
        Material book = VanillaItemRegistry.resolveMaterial("ENCHANTED_BOOK", "BOOK");
        inventory.setItem(11, VanillaItemRegistry.createItem(book, "§6§lWebsite Gạch Thẻ Hỗ Trợ",
                "§7Hệ thống hỗ trợ: TheSieuRe, GachThePro, Doithe247,",
                "§7Card24h, NapTheCucNhanh, TrumThe.",
                "§7Hiện tại: §e" + site,
                "",
                "§e▶ [Click] để đổi sang Website khác hoặc nhập custom"));

        // Slot 13: Partner ID
        Material paper = VanillaItemRegistry.resolveMaterial("PAPER", "MAP");
        inventory.setItem(13, VanillaItemRegistry.createItem(paper, "§b§lPartner ID",
                "§7Mã đối tác được cấp từ web gạch thẻ.",
                "§7Hiện tại: §f" + partnerId,
                "",
                "§e▶ [Click] để sửa Partner ID"));

        // Slot 15: Partner Key
        Material tripwire = VanillaItemRegistry.resolveMaterial("TRIPWIRE_HOOK", "IRON_INGOT");
        inventory.setItem(15, VanillaItemRegistry.createItem(tripwire, "§c§lPartner Key (Secret)",
                "§7Khóa bí mật dùng để ký và xác thực webhook thẻ.",
                "§7Hiện tại: §f" + displayKey,
                "",
                "§e▶ [Click] để nhập Partner Key mới"));

        // Slot 31: Webhook Callback URL
        Material comp = VanillaItemRegistry.resolveMaterial("COMPARATOR", "REDSTONE");
        inventory.setItem(31, VanillaItemRegistry.createItem(comp, "§d§lWebhook Callback URL",
                "§7Địa chỉ để web gạch thẻ trả kết quả về plugin.",
                "§7Hiện tại: §e" + webhook,
                "",
                "§e▶ [Click] để đổi URL Webhook"));

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
