package com.naptien.gui;

import com.naptien.compat.GuiBackend;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;

import java.util.List;

/**
 * GuiUtil (Fabric) — tiện ích cho SGUI.
 *
 * Changelog:
 *   v4.1.0-fabric — initial
 *   v4.2.0        — Fix closeButton(): thêm callback g.close()
 *   v5.2.0        — Tách biệt hoàn toàn khỏi SGUI API (để hỗ trợ đa version).
 */
public class GuiUtil {

    public static final int[] DENOMS = {
        10_000, 20_000, 30_000, 50_000, 100_000, 200_000, 300_000, 500_000, 1_000_000
    };

    public static final String[] TELCOS = {
        "Viettel", "Vinaphone", "Mobifone", "Zing", "Gate", "Garena", "Vcoin", "Appota", "Vietnamobile"
    };

    // ─── SGUI helpers ─────────────────────────────────────────────────────────

    public static void fillGlass(GuiBackend gui) {
        gui.fillGlass();
    }

    public static ItemStack getCloseItem() {
        return new ItemStack(Items.BARRIER);
    }

    public static Item getTelcoWool(String telco) {
        return switch (telco.toLowerCase()) {
            case "viettel"      -> Items.RED_WOOL;
            case "vinaphone"    -> Items.BLUE_WOOL;
            case "mobifone"     -> Items.GREEN_WOOL;
            case "zing"         -> Items.YELLOW_WOOL;
            case "gate"         -> Items.CYAN_WOOL;
            case "garena"       -> Items.ORANGE_WOOL;
            case "vcoin"        -> Items.PURPLE_WOOL;
            case "appota"       -> Items.PINK_WOOL;
            case "vietnamobile" -> Items.LIGHT_BLUE_WOOL;
            default             -> Items.WHITE_WOOL;
        };
    }

    public static Item getDenomItem(int denom) {
        return switch (denom) {
            case 10_000  -> Items.COAL;
            case 20_000  -> Items.IRON_INGOT;
            case 30_000  -> Items.GOLD_INGOT;
            case 50_000  -> Items.DIAMOND;
            case 100_000 -> Items.PRISMARINE_SHARD;
            case 200_000 -> Items.AMETHYST_SHARD;
            case 300_000 -> Items.ECHO_SHARD;
            case 500_000 -> Items.NETHERITE_SCRAP;
            default      -> Items.NETHERITE_INGOT;
        };
    }

    public static ScreenHandlerType<?> screenType(int slots) {
        return switch (slots) {
            case 9  -> ScreenHandlerType.GENERIC_9X1;
            case 18 -> ScreenHandlerType.GENERIC_9X2;
            case 27 -> ScreenHandlerType.GENERIC_9X3;
            case 36 -> ScreenHandlerType.GENERIC_9X4;
            case 45 -> ScreenHandlerType.GENERIC_9X5;
            default -> ScreenHandlerType.GENERIC_9X6;
        };
    }

    public static String formatVnd(int amount) {
        StringBuilder sb = new StringBuilder(String.valueOf(amount));
        int len = sb.length();
        for (int i = len - 3; i > 0; i -= 3) sb.insert(i, '.');
        return sb.toString();
    }

    public static String formatDenom(int denom) {
        if (denom >= 1_000_000) return (denom / 1_000_000) + "m";
        return (denom / 1_000) + "k";
    }
}
