package com.paybot.gui.setup;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

/**
 * VanillaItemRegistry — v6.1.0 (Part 160)
 * TUÂN THỦ RULE 17: Quản lý danh mục Item Vanilla an toàn và cơ chế Fallback
 * đa phiên bản Minecraft (1.16 đến 1.21.x).
 * <p>
 * Nếu Material chính không tồn tại ở phiên bản server này, tự động thử lần lượt
 * Fallback 1, Fallback 2, Fallback 3 để đảm bảo menu luôn hiển thị đẹp mắt và không crash.
 */
public final class VanillaItemRegistry {

    private VanillaItemRegistry() {}

    public static Material resolveMaterial(String mainName, String... fallbacks) {
        Material mat = Material.matchMaterial(mainName);
        if (mat != null) return mat;

        if (fallbacks != null) {
            for (String fb : fallbacks) {
                if (fb != null && !fb.isEmpty()) {
                    mat = Material.matchMaterial(fb);
                    if (mat != null) return mat;
                }
            }
        }
        return Material.STONE; // Safe ultimate fallback
    }

    public static ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material != null ? material : Material.STONE, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createItem(Material material, String name, String... lore) {
        return createItem(material, name, Arrays.asList(lore));
    }

    public static ItemStack createBorderPane() {
        Material glass = resolveMaterial("GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", "GLASS_PANE");
        return createItem(glass, "§7");
    }
}
