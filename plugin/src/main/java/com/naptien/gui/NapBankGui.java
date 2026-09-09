package com.naptien.gui;

import com.naptien.NapTienPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Arrays;
import java.util.List;

import static com.naptien.gui.GuiUtil.*;

/**
 * GUI for /napbank — denom selection → create QR.
 * "Nhập tuỳ chọn" replaced with "Đóng UI".
 */
public final class NapBankGui {

    private NapBankGui() {}

    private static final int CLOSE_SLOT = 49;

    public static void open(Player player, NapTienPlugin plugin) {
        List<Integer> amounts = new java.util.ArrayList<>(parseAmountList(
                plugin.getConfig().getList("quick-amounts",
                        Arrays.asList(10000, 20000, 50000, 100000, 200000, 500000, 1000000))
        ));
        // Đảm bảo 1tr luôn có dù config.yml cũ không có
        if (!amounts.contains(1_000_000)) {
            amounts.add(1_000_000);
            java.util.Collections.sort(amounts);
        }

        Inventory inv = Bukkit.createInventory(null, 54, "§6§lNạp tiền ngân hàng");
        fillGlass(inv);

        // Center amounts in row 3 (slots 18-26)
        boolean customLoreEnabled = plugin.getConfig().getBoolean("custom-lore.enabled", false);
        int start = centerStart(18, amounts.size());
        for (int i = 0; i < amounts.size() && i < 9; i++) {
            int amt = amounts.get(i);
            List<String> lore = null;
            if (customLoreEnabled) {
                List<String> rawLore = plugin.getConfig().getStringList("custom-lore.bank." + amt);
                if (rawLore != null && !rawLore.isEmpty()) {
                    String coinAmt = plugin.getConfig().getString("denom-rewards-bank." + amt + ".amt", "");
                    lore = com.naptien.utils.CustomLoreFormatter.formatLore(player, rawLore, amt, coinAmt);
                }
            }
            if (lore == null || lore.isEmpty()) {
                lore = Arrays.asList(
                        "§7Click để chọn mệnh giá này",
                        "§aNạp tiền qua chuyển khoản"
                );
            }
            inv.setItem(start + i, makeDenomItem(
                    amt,
                    "§e§l" + formatVnd(amt) + " VND",
                    lore
            ));
        }


        // Close button
        inv.setItem(CLOSE_SLOT, makeItem(Material.BARRIER, "§c§lĐóng UI",
                List.of("§7Click để đóng giao diện")));

        GuiSession s = GuiSession.get(player.getUniqueId());
        s.stage = GuiSession.Stage.BANK_DENOM;
        player.openInventory(inv);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
    }

    public static int getCloseSlot() { return CLOSE_SLOT; }

    /** Find amount by slot index given the config list. */
    public static int getAmountBySlot(int slot, List<Integer> amounts) {
        int start = centerStart(18, amounts.size());
        for (int i = 0; i < amounts.size() && i < 9; i++) {
            if (start + i == slot) return amounts.get(i);
        }
        return -1;
    }
}
