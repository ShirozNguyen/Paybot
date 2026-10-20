package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * SetupDiscordGui — v7.0.0 (Part 161)
 * TUÂN THỦ RULE 17: Container Menu 54 ô Cấu hình Bot Discord Nội Bộ (Local Companion Engine).
 */
public class SetupDiscordGui implements InventoryHolder {

    public static final String TITLE = "§9§lCấu Hình Bot Discord §8(v7.0.0)";

    private final PayBotPlugin plugin;
    private final Inventory inventory;

    public SetupDiscordGui(PayBotPlugin plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, TITLE);
        buildLayout();
    }

    private void buildLayout() {
        ItemStack border = VanillaItemRegistry.createBorderPane();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, border);
        }

        boolean enabled = plugin.getConfig().getBoolean("discord.enabled", false);
        String token = plugin.getConfig().getString("discord.bot-token", "");
        String displayToken = token.length() > 8 ? token.substring(0, 4) + "••••••••" + token.substring(token.length() - 4) : "(Chưa cấu hình)";
        String guildId = plugin.getConfig().getString("discord.guild-id", "(Chưa cấu hình)");
        String channelId = plugin.getConfig().getString("discord.notify-channel-id", "(Chưa cấu hình)");
        int ipcPort = plugin.getConfig().getInt("discord.ipc-port", 24733);

        // Slot 11: Trạng thái Bật / Tắt Bot Discord
        Material toggleMat = VanillaItemRegistry.resolveMaterial(enabled ? "LIME_DYE" : "GRAY_DYE", "INK_SACK", "EMERALD");
        inventory.setItem(11, VanillaItemRegistry.createItem(toggleMat,
                enabled ? "§a§lTrạng Thái: ĐANG BẬT" : "§7§lTrạng Thái: ĐANG TẮT",
                "§7Bật hoặc tắt tích hợp Bot Discord nội bộ.",
                "",
                enabled ? "§a▶ [Click] để TẮT tính năng" : "§e▶ [Click] để BẬT tính năng"));

        // Slot 13: Bot Token Discord
        Material tokenMat = VanillaItemRegistry.resolveMaterial("NAME_TAG", "PAPER", "BOOK");
        inventory.setItem(13, VanillaItemRegistry.createItem(tokenMat, "§e§lBot Token Discord",
                "§7Token tạo từ Discord Developer Portal.",
                "§7Hiện tại: §f" + displayToken,
                "",
                "§e▶ [Click] để nhập Token mới qua chat"));

        // Slot 15: Discord Guild ID
        Material guildMat = VanillaItemRegistry.resolveMaterial("ITEM_FRAME", "PAINTING", "BOOK");
        inventory.setItem(15, VanillaItemRegistry.createItem(guildMat, "§b§lServer Discord (Guild ID)",
                "§7ID của Discord Server cần liên kết.",
                "§7Hiện tại: §e" + guildId,
                "",
                "§e▶ [Click] để sửa Guild ID"));

        // Slot 29: Kênh thông báo nạp tiền
        Material channelMat = VanillaItemRegistry.resolveMaterial("CHEST", "ENDER_CHEST", "DISPENSER");
        inventory.setItem(29, VanillaItemRegistry.createItem(channelMat, "§d§lKênh Thông Báo Nạp Tiền",
                "§7ID kênh Discord nhận bill chúc mừng nạp thành công.",
                "§7Hiện tại: §b" + channelId,
                "",
                "§e▶ [Click] để sửa ID Kênh"));

        // Slot 31: Port IPC Localhost
        Material portMat = VanillaItemRegistry.resolveMaterial("REDSTONE", "REPEATER", "COMPASS");
        inventory.setItem(31, VanillaItemRegistry.createItem(portMat, "§6§lCổng Giao Tiếp IPC (Port)",
                "§7Cổng kết nối nội bộ giữa Plugin và Bot Python.",
                "§7Hiện tại: §a" + ipcPort + " §8(Localhost)",
                "",
                "§e▶ [Click] để đổi Cổng IPC"));

        // Slot 33: Test Ping Kết Nối IPC
        Material pingMat = VanillaItemRegistry.resolveMaterial("NETHER_STAR", "EMERALD", "GLOWSTONE_DUST");
        inventory.setItem(33, VanillaItemRegistry.createItem(pingMat, "§a§lKiểm Tra Kết Nối (Ping Bot)",
                "§7Gửi request kiểm tra xem tiến trình Bot Python",
                "§7đang chạy trên localhost:" + ipcPort + " hay không.",
                "",
                "§e▶ [Click] để Ping kiểm tra ngay"));

        // Slot 45: Quay lại Menu Chính
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
