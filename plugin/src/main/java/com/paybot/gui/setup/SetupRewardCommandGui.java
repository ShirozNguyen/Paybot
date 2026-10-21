package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
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
 * SetupRewardCommandGui — v7.0.0 Part 172
 * Cấu hình Lệnh Thưởng Nạp, khớp chuẩn 100% bản mô phỏng paybot_setup_gui.html.
 * Tách biệt cấu hình riêng Bank & Thẻ Cào, tùy biến bộ lệnh riêng cho 9 mệnh giá (;;).
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn thành 1 class đơn nhiệm độc lập.
 */
public class SetupRewardCommandGui implements InventoryHolder {

    public static final String TITLE = "§a§lLệnh Thưởng Nạp (Ngân Hàng / Thẻ Cào)";

    private final PayBotPlugin plugin;
    private final Inventory inventory;
    private final boolean isCardTab;

    public SetupRewardCommandGui(PayBotPlugin plugin) {
        this(plugin, false);
    }

    public SetupRewardCommandGui(PayBotPlugin plugin, boolean isCardTab) {
        this.plugin = plugin;
        this.isCardTab = isCardTab;
        this.inventory = Bukkit.createInventory(this, 54, TITLE);
        buildLayout();
    }

    public boolean isCardTab() {
        return isCardTab;
    }

    private void buildLayout() {
        ItemStack border = createBorder();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, border);
        }

        String fallbackKey = isCardTab ? "reward-command-card" : "reward-command-bank";
        String fallbackCmd = plugin.getConfig().getString(fallbackKey, "eco give {player} {amount}");
        double multiplier = plugin.getConfig().getDouble("reward-multiplier", 1.0);
        boolean firework = plugin.getConfig().getBoolean("effects.firework", true);
        boolean sound = plugin.getConfig().getBoolean("effects.sound", true);
        boolean notif = plugin.getConfig().getBoolean("effects.notification", true);
        String soundName = plugin.getConfig().getString("effects.sound-name", "ui.toast.challenge_complete");

        // ── HÀNG 1: TABS & TỔNG QUAN ──────────────────────────────────────────────
        // Slot 1: Tab Bank
        inventory.setItem(1, createGuiItem("emerald_block", "emerald", "gold_block",
                !isCardTab ? "§a§l✦ [ĐANG CHỌN] LỆNH THƯỞNG BANK ✦" : "§7[Bấm Để Chọn] Lệnh Thưởng Bank",
                Arrays.asList(
                        "§7Cấu hình lệnh chạy khi nạp bank qua VietQR thành công.",
                        "",
                        !isCardTab ? "§a• Đang xem cài đặt thưởng Bank" : "§e▶ [Click] để chuyển sang Bank"
                )));

        // Slot 3: Tab Thẻ Cào
        inventory.setItem(3, createGuiItem("gold_block", "gold_ingot", "iron_block",
                isCardTab ? "§6§l✦ [ĐANG CHỌN] LỆNH THƯỞNG THẺ CÀO ✦" : "§7[Bấm Để Chọn] Lệnh Thưởng Thẻ Cào",
                Arrays.asList(
                        "§7Cấu hình lệnh chạy khi nạp thẻ cào thành công.",
                        "",
                        isCardTab ? "§a• Đang xem cài đặt thưởng Thẻ Cào" : "§e▶ [Click] để chuyển sang Thẻ Cào"
                )));

        // Slot 5: Lệnh Dự Phòng (Fallback)
        inventory.setItem(5, createGuiItem("book", "book", "paper",
                "§e§lLệnh Thưởng Dự Phòng (Fallback)",
                Arrays.asList(
                        "§7Chạy khi mệnh giá không có bộ lệnh riêng.",
                        "§f• Lệnh: §b" + fallbackCmd,
                        "",
                        "§e▶ [Click] để sửa lệnh dự phòng"
                )));

        // Slot 7: Hệ Số Nhân Thưởng
        inventory.setItem(7, createGuiItem("experience_bottle", "glowstone_dust", "glowstone_dust",
                "§e§lHệ Số Nhân Thưởng Sự Kiện: §b" + multiplier + "x",
                Arrays.asList(
                        "§7Tự động nhân số tiền [amount] trong lệnh thưởng.",
                        "§a• Hiện tại: §e" + multiplier + "x",
                        "",
                        "§e▶ [Click] để xoay vòng 1x -> 1.5x -> 2x -> 3x"
                )));

        // ── 9 MỆNH GIÁ CẤU HÌNH RIÊNG ─────────────────────────────────────────────
        int[] denomValues = {10000, 20000, 30000, 50000, 100000, 200000, 300000, 500000, 1000000};
        String[] denomItems = {"coal", "iron_ingot", "gold_ingot", "redstone", "lapis_lazuli", "diamond", "emerald", "netherite_ingot", "nether_star"};
        int[] denomSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20};

        String section = isCardTab ? "denom-rewards-card" : "denom-rewards-bank";
        for (int i = 0; i < denomValues.length; i++) {
            int val = denomValues[i];
            String valKey = String.valueOf(val);
            String customCmd = plugin.getConfig().getString(section + "." + valKey + ".cmd", fallbackCmd);
            String itemType = denomItems[i];

            inventory.setItem(denomSlots[i], createGuiItem(itemType, "diamond", "gold_ingot",
                    "§a§lThưởng Mệnh Giá: §e" + String.format("%,d", val) + " VNĐ",
                    Arrays.asList(
                            "§7Lệnh riêng biệt khi nạp đúng mệnh giá này.",
                            "§f• Bộ lệnh hiện tại:",
                            "§b" + customCmd,
                            "",
                            "§7Hỗ trợ chạy nhiều lệnh bằng dấu ;;",
                            "§e▶ [Click] để sửa chuỗi lệnh"
                    )));
        }

        // ── HÀNG 5: HIỆU ỨNG & THÔNG BÁO ─────────────────────────────────────────
        // Slot 38: Pháo Hoa
        inventory.setItem(38, createGuiItem("firework_rocket", "gunpowder", "sulphur",
                "§d§lBắn Pháo Hoa Ăn Mừng: " + (firework ? "§aBẬT" : "§cTẮT"),
                Arrays.asList(
                        "§7Bắn pháo hoa ăn mừng tại vị trí người chơi.",
                        "",
                        "§e▶ [Click] để Bật/Tắt pháo hoa"
                )));

        // Slot 40: Âm Thanh
        inventory.setItem(40, createGuiItem("note_block", "jukebox", "note_block",
                "§b§lÂm Thanh Chúc Mừng: " + (sound ? "§aBẬT" : "§cTẮT"),
                Arrays.asList(
                        "§f• Sound: §e" + soundName,
                        "",
                        "§e▶ [Click] để Bật/Tắt âm thanh",
                        "§b▶ [Shift + Click] để sửa Sound ID Vanilla"
                )));

        // Slot 42: Thông Báo Chat
        inventory.setItem(42, createGuiItem("writable_book", "book", "paper",
                "§6§lThông Báo Chat In Đậm: " + (notif ? "§aBẬT" : "§cTẮT"),
                Arrays.asList(
                        "§7Thông báo broadcast chúc mừng toàn server.",
                        "",
                        "§e▶ [Click] để Bật/Tắt thông báo",
                        "§b▶ [Shift + Click] để sửa nội dung tin nhắn"
                )));

        // Slot 49: Quay Lại
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
