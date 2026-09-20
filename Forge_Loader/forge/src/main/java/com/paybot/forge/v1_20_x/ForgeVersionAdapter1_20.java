package com.paybot.forge.v1_20_x;

import com.paybot.compat.legacy.LegacyItemTagHelper;
import com.paybot.compat.legacy.LegacyMapLockHelper;
import com.paybot.compat.version.VersionAdapter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;

/**
 * ForgeVersionAdapter1_20 — v5.5.10 Part 126
 * Adapter NBT chuẩn hóa cho nhánh Minecraft 1_20_x (Forge).
 * Tuân thủ Quy tắc 17: Toàn bộ logic NBT và Map Lock được ủy quyền sang các lớp helper chuyên biệt độc lập.
 */
public class ForgeVersionAdapter1_20 implements VersionAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-Forge-Adapter-1_20_x");

    @Override
    public void setItemNameAndLore(ItemStack stack, String name, List<String> lore) {
        LegacyItemTagHelper.setItemNameAndLore(stack, name, lore);
    }

    @Override
    public void setInvoiceId(ItemStack stack, String invoiceId) {
        LegacyItemTagHelper.setInvoiceId(stack, invoiceId);
    }

    @Override
    public String getInvoiceId(ItemStack stack) {
        return LegacyItemTagHelper.getInvoiceId(stack);
    }

    @Override
    public MapItemSavedData getMapSavedData(ItemStack mapItem, ServerLevel world) {
        if (mapItem == null || world == null) return null;
        try {
            for (Method m : MapItem.class.getMethods()) {
                if (m.getParameterCount() == 2
                        && m.getParameterTypes()[0] == ItemStack.class
                        && net.minecraft.world.level.Level.class.isAssignableFrom(m.getParameterTypes()[1])
                        && MapItemSavedData.class.isAssignableFrom(m.getReturnType())) {
                    Object res = m.invoke(null, mapItem, world);
                    if (res instanceof MapItemSavedData) return (MapItemSavedData) res;
                }
            }
        } catch (Throwable t) {
            LOGGER.error("[Forge1_20_x] getMapSavedData error: {}", t.getMessage());
        }
        return null;
    }

    @Override
    public void lockMap(MapItemSavedData state) {
        LegacyMapLockHelper.lock(state);
    }
}
