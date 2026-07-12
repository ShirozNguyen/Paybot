package com.naptien.gui;

import com.naptien.utils.VersionCompat;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.NumberFormat;
import java.util.*;

/**
 * Shared utility methods used by all GUI classes.
 * Handles cross-version compatibility (1.18+).
 */
public final class GuiUtil {

    private GuiUtil() {}

    // ─── Number formatter ──────────────────────────────────────────────────────
    public static final NumberFormat VND_FMT;
    static {
        VND_FMT = NumberFormat.getInstance(new Locale("vi", "VN"));
        VND_FMT.setGroupingUsed(true);
    }

    // ─── Denom → Material mapping ──────────────────────────────────────────────
    public static final LinkedHashMap<String, Integer> DENOMS = new LinkedHashMap<>();
    static {
        DENOMS.put("10k",    10_000);
        DENOMS.put("20k",    20_000);
        DENOMS.put("30k",    30_000);
        DENOMS.put("50k",    50_000);
        DENOMS.put("100k",  100_000);
        DENOMS.put("200k",  200_000);
        DENOMS.put("300k",  300_000);
        DENOMS.put("500k",  500_000);
        DENOMS.put("1000k", 1_000_000);
    }

    /**
     * Maps denom value → display material.
     * Delegates to {@link VersionCompat#getDenomMaterial(int)} which handles
     * NETHERITE_* (1.16+), AMETHYST_SHARD (1.17+), ECHO_SHARD (1.19+) fallbacks.
     * <p>
     * <b>Note:</b> For GUI items, prefer {@link #makeDenomItem} which automatically
     * switches to coloured wool on servers below 1.18.
     */
    public static Material getDenomMaterial(int denom) {
        return VersionCompat.getDenomMaterial(denom);
    }

    /**
     * Creates the denomination display ItemStack for a GUI slot.
     * <ul>
     *   <li>1.18+ → original materials (COAL, DIAMOND, NETHERITE_INGOT…)
     *   <li>1.12–1.17 → coloured wool (consistent with telco selection GUI)
     * </ul>
     */
    public static ItemStack makeDenomItem(int denom, String displayName, List<String> lore) {
        return VersionCompat.makeDenomItem(denom, displayName, lore);
    }

    // ─── Key → denom int ──────────────────────────────────────────────────────
    public static int parseDenom(String key) {
        return DENOMS.getOrDefault(key.toLowerCase(), -1);
    }

    public static String formatDenom(int denom) {
        if (denom >= 1_000_000 && denom % 1_000_000 == 0) return (denom / 1_000_000) + "tr";
        if (denom >= 1_000    && denom % 1_000    == 0)   return (denom / 1_000) + "k";
        return String.valueOf(denom);
    }

    public static String formatVnd(int amount) {
        return VND_FMT.format(amount);
    }

    /** Overload cho số lớn (vd tổng nạp toàn server) — tránh overflow int. */
    public static String formatVnd(long amount) {
        return VND_FMT.format(amount);
    }

    // ─── Inventory helpers ────────────────────────────────────────────────────
    /** Fills empty slots with a gray glass pane (version-safe via {@link VersionCompat}). */
    public static void fillGlass(Inventory inv) {
        ItemStack glass = VersionCompat.makeGlassPaneItem();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack cur = inv.getItem(i);
            if (cur == null || cur.getType() == Material.AIR) {
                inv.setItem(i, glass);
            }
        }
    }

    public static ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates a green or red wool indicator item (version-safe via {@link VersionCompat}).
     * green=true  → GREEN_WOOL  (data 13 on 1.12)
     * green=false → RED_WOOL    (data 14 on 1.12)
     */
    public static ItemStack makeWool(boolean configured, String name, List<String> lore) {
        return VersionCompat.makeStatusWool(configured, name, lore);
    }

    // ─── Parse amounts from config list ───────────────────────────────────────
    public static List<Integer> parseAmountList(List<?> raw) {
        List<Integer> result = new ArrayList<>();
        for (Object o : raw) {
            try {
                if (o instanceof Number n) { result.add(n.intValue()); continue; }
                String s = o.toString().trim().toLowerCase()
                        .replace(",", "").replace(".", "");
                if (s.endsWith("tr")) result.add((int)(Double.parseDouble(s.replace("tr","")) * 1_000_000));
                else if (s.endsWith("k")) result.add((int)(Double.parseDouble(s.replace("k","")) * 1_000));
                else result.add(Integer.parseInt(s));
            } catch (Exception ignored) {}
        }
        result.removeIf(n -> n <= 0);
        return result;
    }

    // ─── Center slots in a row ────────────────────────────────────────────────
    /** Returns starting slot to center `count` items in a 9-wide row starting at `rowStart`. */
    public static int centerStart(int rowStart, int count) {
        int offset = (9 - Math.min(count, 9)) / 2;
        return rowStart + offset;
    }
}
