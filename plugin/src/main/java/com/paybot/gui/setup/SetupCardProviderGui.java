package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import com.paybot.commands.DynamicPaymentCommandRegistry;
import com.paybot.compat.SafeItemFallbackResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

/**
 * SetupCardProviderGui — v7.0.0 Part 172
 * Cấu hình Cổng Gạch Thẻ Cào, khớp chuẩn 100% bản mô phỏng paybot_setup_gui.html.
 * Bố cục 4-4 đối xứng thẩm mỹ hoàn mỹ.
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn thành 1 class đơn nhiệm độc lập.
 */
public class SetupCardProviderGui implements InventoryHolder {

    public static final String TITLE = "§6§lCài Đặt Thẻ Cào (Web Chuẩn)";

    private final PayBotPlugin plugin;
    private final Inventory inventory;

    public SetupCardProviderGui(PayBotPlugin plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, TITLE);
        buildLayout();
    }

    private void buildLayout() {
        ItemStack border = createBorder();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, border);
        }

        boolean cardOn = DynamicPaymentCommandRegistry.getInstance().isCardEnabled();
        String site = plugin.getConfig().getString("card.partner-url", "thesieure.com");
        String partnerId = plugin.getConfig().getString("card.partner-id", "(Chưa đặt)");
        String partnerKey = plugin.getConfig().getString("card.partner-key", "");
        String displayKey = partnerKey.length() > 6 ? partnerKey.substring(0, 3) + "••••••" : "(Chưa đặt)";
        String channelId = plugin.getConfig().getString("discord.notify-channel-id", "(Chưa đặt)");

        // ── HÀNG 2: 4 Ô CẤU HÌNH TRÊN (Slots 10, 12, 14, 16) ──────────────────────
        // Slot 10: Công tắc Bật/Tắt Thẻ Cào
        String dyeItem = cardOn ? "lime_dye" : "gray_dye";
        inventory.setItem(10, createGuiItem(dyeItem, "ink_sack", "wool",
                "§e§lTrạng Thái Nạp Thẻ: " + (cardOn ? "§aĐang BẬT" : "§cĐang TẮT"),
                Arrays.asList(
                        "§7Bật hoặc tắt hệ thống gạch thẻ cào tự động.",
                        "",
                        "§f• Trạng thái: " + (cardOn ? "§aBẬT (/napthe MỞ)" : "§cTẮT (/napthe ĐÓNG)"),
                        "§c• Khi tắt: Lệnh /napthe sẽ bị vô hiệu hóa hoàn toàn!",
                        "",
                        "§e▶ [Click] để Bật/Tắt cổng Thẻ cào"
                )));

        // Slot 12: Site Đối Tác Gạch Thẻ
        inventory.setItem(12, createGuiItem("beacon", "diamond_block", "glass",
                "§e§lSite Đối Tác Gạch Thẻ",
                Arrays.asList(
                        "§e⚠ Tôi đã test qua, vui lòng cân nhắc kĩ khi chọn site, có thể là scam, idk:",
                        "§7• gachthepro.com",
                        "§7• thesieure.com",
                        "§7• gachthefast.com",
                        "§7• gachthe1s.com",
                        "§7• card2k.net",
                        "",
                        "§aSite đang dùng: §b" + site,
                        "",
                        "§e▶ [Click] để đổi Site đối tác"
                )));

        // Slot 14: Partner ID
        inventory.setItem(14, createGuiItem("paper", "paper", "book",
                "§b§lPartner ID",
                Arrays.asList(
                        "§7Mã đối tác do web gạch thẻ cấp.",
                        "",
                        "§7Partner ID: §f" + partnerId,
                        "",
                        "§e▶ [Click] để sửa Partner ID"
                )));

        // Slot 16: Partner Key (Mật Khẩu API)
        inventory.setItem(16, createGuiItem("tripwire_hook", "lever", "iron_ingot",
                "§c§lPartner Key (Mật Khẩu API)",
                Arrays.asList(
                        "§7Khóa bí mật tạo chữ ký MD5 gạch thẻ.",
                        "",
                        "§7Partner Key: §f" + displayKey,
                        "",
                        "§e▶ [Click] để nhập Partner Key mới"
                )));

        // ── HÀNG 4: 4 Ô CẤU HÌNH DƯỚI (Slots 28, 30, 32, 34) ──────────────────────
        // Slot 28: Discord Channel ID
        inventory.setItem(28, createGuiItem("book", "book", "paper",
                "§9§lDiscord Channel ID",
                Arrays.asList(
                        "§7ID kênh Discord thông báo kết quả gạch thẻ.",
                        "",
                        "§7Channel ID: §f" + channelId,
                        "",
                        "§e▶ [Click] để sửa Discord Channel ID"
                )));

        // Slot 30: Quản Lý 9 Nhà Mạng
        inventory.setItem(30, createGuiItem("white_wool", "wool", "wool",
                "§a§lQuản Lý 9 Nhà Mạng Hỗ Trợ",
                Arrays.asList(
                        "§7Xem danh sách & tùy chỉnh 9 nhà mạng trên 1 hàng ngang duy nhất.",
                        "",
                        "§e▶ [Click] để mở Menu Tùy biến Player GUI"
                )));

        // Slot 32: Nhập Site Tùy Chỉnh (Custom URL)
        inventory.setItem(32, createGuiItem("hopper", "chest", "chest",
                "§f§lNhập Site Tùy Chỉnh (Custom URL)",
                Arrays.asList(
                        "§7Nhập endpoint API gạch thẻ riêng do bạn tự host.",
                        "",
                        "§e▶ [Click] để nhập custom endpoint"
                )));

        // Slot 34: Kiểm Tra Kết Nối Cổng Thẻ
        inventory.setItem(34, createGuiItem("redstone_torch", "torch", "lever",
                "§a§lKiểm Tra Kết Nối Cổng Thẻ",
                Arrays.asList(
                        "§7Gửi request test chữ ký và kết nối đến web gạch thẻ.",
                        "",
                        "§e▶ [Click] để gửi request kiểm tra ngay"
                )));

        // ── HÀNG 6: QUAY LẠI (Slot 49) ───────────────────────────────────────────
        inventory.setItem(49, createGuiItem("arrow", "arrow", "bedrock",
                "§c§l◀ Quay Lại Menu Chính",
                Arrays.asList("§7Trở về Master Setup Hub.")
        ));
    }

    private ItemStack createBorder() {
        return createGuiItem("gray_stained_glass_pane", "stained_glass_pane", "glass", "§8", null);
    }

    private ItemStack createGuiItem(String p, String s, String u, String name, List<String> lore) {
        ItemStack item = SafeItemFallbackResolver.resolveItemStack(p, s, u, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public void open(Player player) {
        if (player != null) player.openInventory(this.inventory);
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }
}
