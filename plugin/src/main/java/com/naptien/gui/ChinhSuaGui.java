package com.naptien.gui;

import com.naptien.NapTienPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * ChinhSuaGui — GUI 54 slot hiển thị tất cả cấu hình mệnh giá thưởng.
 *
 * Layout (54 slot, 6 hàng × 9 cột):
 *   Hàng 1  (slot 0-8)  : glass + label "Nạp Thẻ"
 *   Hàng 2  (slot 9-17) : 9 mệnh giá nạp thẻ cào
 *   Hàng 3  (slot 18-26): glass + label "Nạp Bank"
 *   Hàng 4  (slot 27-35): 9 mệnh giá nạp bank
 *   Hàng 5-6 (slot 36-53): glass + close ở slot 49
 *
 * Changelog:
 *   v4.1.0 — Tái tạo từ .class (file nguồn gốc không có trong ZIP)
 */
public class ChinhSuaGui {

    public static final String TITLE = "§6§lChỉnh sửa mệnh giá nạp";

    // Thứ tự denom khớp với GuiUtil.DENOMS
    private static final int[] DENOMS = {
        10_000, 20_000, 30_000, 50_000, 100_000, 200_000, 300_000, 500_000, 1_000_000
    };

    // Slot 9-17: card denoms | Slot 27-35: bank denoms
    private static final int CARD_ROW_START = 9;
    private static final int BANK_ROW_START = 27;
    private static final int CLOSE_SLOT     = 49;

    // ─── SlotInfo record ──────────────────────────────────────────────────────

    /** Trả về thông tin denom và loại của slot bất kỳ trong GUI. */
    public record SlotInfo(int denom, String type) {}

    /**
     * @return SlotInfo nếu slot là denom có thể click, null nếu không.
     */
    public static SlotInfo getSlotInfo(int slot) {
        if (slot >= CARD_ROW_START && slot < CARD_ROW_START + DENOMS.length) {
            return new SlotInfo(DENOMS[slot - CARD_ROW_START], "card");
        }
        if (slot >= BANK_ROW_START && slot < BANK_ROW_START + DENOMS.length) {
            return new SlotInfo(DENOMS[slot - BANK_ROW_START], "bank");
        }
        return null;
    }

    // ─── Open ─────────────────────────────────────────────────────────────────

    public static void open(Player player, NapTienPlugin plugin) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        // Hàng 1: label nạp thẻ
        inv.setItem(4, GuiUtil.makeItem(
                org.bukkit.Material.YELLOW_STAINED_GLASS_PANE,
                "§6§l✦ Nạp Thẻ Cào ✦",
                Collections.singletonList("§7Click vào mệnh giá để cấu hình thưởng")));

        // Hàng 2: 9 card denoms
        for (int i = 0; i < DENOMS.length; i++) {
            int denom = DENOMS[i];
            inv.setItem(CARD_ROW_START + i, makeDenomSlot(plugin, denom, "card"));
        }

        // Hàng 3: label nạp bank
        inv.setItem(22, GuiUtil.makeItem(
                org.bukkit.Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                "§b§l✦ Nạp Ngân Hàng ✦",
                Collections.singletonList("§7Click vào mệnh giá để cấu hình thưởng")));

        // Hàng 4: 9 bank denoms
        for (int i = 0; i < DENOMS.length; i++) {
            int denom = DENOMS[i];
            inv.setItem(BANK_ROW_START + i, makeDenomSlot(plugin, denom, "bank"));
        }

        // Close
        inv.setItem(CLOSE_SLOT, GuiUtil.makeItem(
                org.bukkit.Material.BARRIER,
                "§c§lĐóng",
                Collections.singletonList("§7Nhấn để đóng menu")));

        // Điền glass vào các ô trống
        GuiUtil.fillGlass(inv);

        player.openInventory(inv);
    }

    // ─── Build denom item ─────────────────────────────────────────────────────

    private static ItemStack makeDenomSlot(NapTienPlugin plugin, int denom, String type) {
        String section = "card".equals(type) ? "denom-rewards-card" : "denom-rewards-bank";
        String key     = GuiUtil.formatDenom(denom);
        String amt     = plugin.getConfig().getString(section + "." + denom + ".amt", "").trim();
        String cmd     = plugin.getConfig().getString(section + "." + denom + ".cmd", "").trim();

        boolean configured = !amt.isEmpty() || !cmd.isEmpty();
        String label = ("card".equals(type) ? "§6[Thẻ] " : "§b[Bank] ")
                + "§l" + GuiUtil.formatVnd(denom) + " VND";

        List<String> lore = new ArrayList<>();
        lore.add("§7Mệnh giá: §f" + GuiUtil.formatVnd(denom) + " VND");
        if (configured) {
            if (!amt.isEmpty()) lore.add("§7Số thưởng: §a" + amt);
            if (!cmd.isEmpty()) lore.add("§7Lệnh: §a" + (cmd.length() > 30 ? cmd.substring(0,30) + "..." : cmd));
            lore.add("");
            lore.add("§aĐã cấu hình §7— Click để sửa");
        } else {
            lore.add("§cChưa cấu hình");
            lore.add("");
            lore.add("§eClick để cấu hình");
        }

        return GuiUtil.makeWool(configured, label, lore);
    }
}
