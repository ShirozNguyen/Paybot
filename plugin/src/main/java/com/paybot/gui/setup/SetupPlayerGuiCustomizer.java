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
 * SetupPlayerGuiCustomizer — v7.0.0 Part 172
 * Tùy biến Player GUI, khớp chuẩn 100% bản mô phỏng paybot_setup_gui.html.
 * - Hàng 2 (Slots 9-17): 9 Cục len nhà mạng dàn đều trọn vẹn trên 1 hàng duy nhất!
 * - Hàng 4 (Slots 27-35): 9 Mệnh giá nạp ngân hàng VietQR dàn đều trọn vẹn trên 1 hàng duy nhất!
 * - Hàng 5 (Slots 38, 42): Tiêu đề Container /napbank và /napthe.
 * - Hàng 6 (Slot 49): Quay lại Menu chính.
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn thành 1 class đơn nhiệm độc lập.
 */
public class SetupPlayerGuiCustomizer implements InventoryHolder {

    public static final String TITLE = "§a§lTùy Biến Giao Diện Player GUI";

    private final PayBotPlugin plugin;
    private final Inventory inventory;

    public SetupPlayerGuiCustomizer(PayBotPlugin plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, TITLE);
        buildLayout();
    }

    private void buildLayout() {
        ItemStack border = createBorder();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, border);
        }

        String titleBank = plugin.getConfig().getString("gui.title-bank", "§b§lChọn Mệnh Giá Nạp Bank");
        String titleCard = plugin.getConfig().getString("gui.title-card", "§e§lChọn Nhà Mạng Nạp Thẻ");

        // ── HÀNG 2 (SLOTS 9 ĐẾN 17): 9 CỤC LEN NHÀ MẠNG THẺ CÀO (/napthe) ─────────
        String[] telcos = {"VIETTEL", "VINAPHONE", "MOBIFONE", "VIETNAMOBILE", "ZING", "GATE", "GARENA", "VCOIN", "APPOTA"};
        String[] woolColors = {"red_wool", "blue_wool", "yellow_wool", "orange_wool", "green_wool", "purple_wool", "lime_wool", "cyan_wool", "pink_wool"};

        for (int i = 0; i < 9; i++) {
            String telco = telcos[i];
            inventory.setItem(9 + i, createGuiItem(woolColors[i], "wool", "wool",
                    "§f§lNhà Mạng: §e" + telco,
                    Arrays.asList(
                            "§7Tùy biến Tên hiển thị và Lore của len " + telco + ".",
                            "§7Hỗ trợ mã màu Hex, Gradient (#RRGGBB, &a, &e...).",
                            "",
                            "§e▶ [Click Trái] để sửa Tên hiển thị",
                            "§e▶ [Shift + Click] để sửa Dòng Lore"
                    )));
        }

        // ── HÀNG 4 (SLOTS 27 ĐẾN 35): 9 MỆNH GIÁ BANK VIETQR (/napbank) ───────────
        int[] bankDenoms = {10000, 20000, 30000, 50000, 100000, 200000, 300000, 500000, 1000000};
        String[] bankItems = {"coal", "iron_ingot", "gold_ingot", "redstone", "lapis_lazuli", "diamond", "emerald", "netherite_ingot", "nether_star"};

        for (int i = 0; i < 9; i++) {
            int val = bankDenoms[i];
            inventory.setItem(27 + i, createGuiItem(bankItems[i], "paper", "paper",
                    "§a§lMệnh Giá Bank: §e" + String.format("%,d", val) + " VNĐ",
                    Arrays.asList(
                            "§7Tùy biến hiển thị ô mệnh giá " + String.format("%,d", val) + " VNĐ trong /napbank.",
                            "",
                            "§e▶ [Click Trái] để sửa Tên hiển thị",
                            "§e▶ [Shift + Click] để sửa Dòng Lore"
                    )));
        }

        // ── HÀNG 5 (SLOTS 38, 42): TIÊU ĐỀ CONTAINER ──────────────────────────────
        // Slot 38: Tiêu đề Container Nạp Bank
        inventory.setItem(38, createGuiItem("chest", "chest", "ender_chest",
                "§6§lTiêu Đề Container /napbank",
                Arrays.asList(
                        "§7Tiêu đề hiển thị trên thanh Container khi mở /napbank.",
                        "§7Hiện tại: " + titleBank,
                        "",
                        "§e▶ [Click] để sửa Tiêu đề Container Nạp Bank"
                )));

        // Slot 42: Tiêu đề Container Nạp Thẻ
        inventory.setItem(42, createGuiItem("book", "book", "paper",
                "§e§lTiêu Đề Container /napthe",
                Arrays.asList(
                        "§7Tiêu đề hiển thị trên thanh Container khi mở /napthe.",
                        "§7Hiện tại: " + titleCard,
                        "",
                        "§e▶ [Click] để sửa Tiêu đề Container Nạp Thẻ"
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
