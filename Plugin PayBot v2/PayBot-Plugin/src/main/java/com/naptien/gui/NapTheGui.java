package com.naptien.gui;

import com.naptien.utils.VersionCompat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.*;

import static com.naptien.gui.GuiUtil.*;

/**
 * GUI for /napthe — telco selection → denom selection → chat for serial/code → /ok
 */
public final class NapTheGui {

    private NapTheGui() {}

    // ── Telco definitions ─────────────────────────────────────────────────────

    /**
     * {@code woolData} = 1.12 wool damage value (0-15).
     * VersionCompat.makeWoolItem() converts this to the correct Material on any version:
     * 1.13+ → flat name (RED_WOOL etc.), 1.12 → WOOL with data value.
     *
     * Reference: 0=white 1=orange 3=light_blue 4=yellow 6=pink 9=cyan 10=purple
     *            11=blue 13=green(dark) 14=red
     */
    public record TelcoEntry(String name, int woolData, String color, String denomRange, int slot) {}

    public static final List<TelcoEntry> TELCOS = List.of(
        new TelcoEntry("Viettel",      14, "§c§l", "10k - 1000k", 9),   // RED_WOOL
        new TelcoEntry("Vinaphone",    11, "§9§l", "10k - 500k",  10),  // BLUE_WOOL
        new TelcoEntry("Mobifone",     13, "§a§l", "10k - 500k",  11),  // GREEN_WOOL (dark)
        new TelcoEntry("Zing",          4, "§e§l", "10k - 1000k", 12),  // YELLOW_WOOL
        new TelcoEntry("Gate",          9, "§b§l", "10k - 500k",  13),  // CYAN_WOOL
        new TelcoEntry("Garena",        1, "§6§l", "20k - 500k",  14),  // ORANGE_WOOL
        new TelcoEntry("Vcoin",        10, "§5§l", "10k - 1000k", 15),  // PURPLE_WOOL
        new TelcoEntry("Appota",        6, "§d§l", "10k - 500k",  16),  // PINK_WOOL
        new TelcoEntry("Vietnamobile",  3, "§3§l", "10k - 500k",  17)   // LIGHT_BLUE_WOOL
    );

    // Slots for 9 denominations (row 3 of 54-slot inv: slots 18-26)
    private static final int[] DENOM_SLOTS = {18, 19, 20, 21, 22, 23, 24, 25, 26};

    // ─── Open telco GUI ───────────────────────────────────────────────────────
    public static void openTelcoGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§6§lChọn nhà mạng");
        fillGlass(inv);

        for (TelcoEntry t : TELCOS) {
            inv.setItem(t.slot(), VersionCompat.makeWoolItem(
                    t.woolData(),
                    t.color() + t.name(),
                    Arrays.asList(
                            "§7Click để chọn " + t.name(),
                            "§aMệnh giá: §f" + t.denomRange()
                    )
            ));
        }

        GuiSession s = GuiSession.get(player.getUniqueId());
        s.stage = GuiSession.Stage.CARD_TELCO;
        player.openInventory(inv);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
    }

    // ─── Open denom GUI ───────────────────────────────────────────────────────
    public static void openDenomGui(Player player, String telco) {
        Inventory inv = Bukkit.createInventory(null, 54, "§6§lChọn mệnh giá - §e" + telco);
        fillGlass(inv);

        int i = 0;
        for (Map.Entry<String, Integer> e : DENOMS.entrySet()) {
            if (i >= DENOM_SLOTS.length) break;
            int denom = e.getValue();
            inv.setItem(DENOM_SLOTS[i++], makeDenomItem(
                    denom,
                    "§e§l" + formatVnd(denom) + " VND",
                    Arrays.asList(
                            "§7Click để chọn mệnh giá này",
                            "§aNhà mạng: §f" + telco
                    )
            ));
        }

        // Back button
        inv.setItem(49, makeItem(Material.BARRIER, "§c§lQuay lại",
                List.of("§7Click để quay lại chọn nhà mạng")));

        GuiSession s = GuiSession.get(player.getUniqueId());
        s.stage = GuiSession.Stage.CARD_DENOM;
        s.telco = telco;
        player.openInventory(inv);
    }

    // ─── Slot → TelcoEntry ────────────────────────────────────────────────────
    public static TelcoEntry getTelcoBySlot(int slot) {
        for (TelcoEntry t : TELCOS) {
            if (t.slot() == slot) return t;
        }
        return null;
    }

    // ─── Slot → denom entry ───────────────────────────────────────────────────
    public static Map.Entry<String, Integer> getDenomBySlot(int slot) {
        int i = 0;
        for (Map.Entry<String, Integer> e : DENOMS.entrySet()) {
            if (DENOM_SLOTS[i] == slot) return e;
            i++;
            if (i >= DENOM_SLOTS.length) break;
        }
        return null;
    }
}
