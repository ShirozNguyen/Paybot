package com.naptien.forge.v1_18_x;

import com.naptien.compat.version.VersionAdapter;
import com.naptien.utils.ComponentColorParser;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class ForgeVersionAdapter1_18 implements VersionAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-Forge-Adapter1_18_x");
    private static Field lockedField = null;
    private static boolean reflectionInit = false;

    private static synchronized void initFieldReflection() {
        if (reflectionInit) return;
        reflectionInit = true;
        String[] lockedCandidates = {"locked", "f_77906_", "field_1838"};
        for (String name : lockedCandidates) {
            try {
                Field f = MapItemSavedData.class.getDeclaredField(name);
                if (f.getType() == boolean.class) {
                    f.setAccessible(true);
                    lockedField = f;
                    break;
                }
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void setItemNameAndLore(ItemStack stack, String name, List<String> lore) {
        if (stack == null || stack.isEmpty()) return;
        if (name != null && !name.isEmpty()) {
            Component nameComponent = ComponentColorParser.parse(name);
            try { stack.setHoverName(nameComponent); } catch (Throwable ignored) {}
            CompoundTag displayTag = getOrCreateDisplayTag(stack);
            if (displayTag != null) displayTag.putString("Name", safeComponentToJson(nameComponent));
        }
        if (lore != null && !lore.isEmpty()) {
            CompoundTag displayTag = getOrCreateDisplayTag(stack);
            if (displayTag != null) {
                ListTag loreTag = new ListTag();
                for (String line : lore) {
                    Component lineComp = ComponentColorParser.parse(line);
                    loreTag.add(StringTag.valueOf(safeComponentToJson(lineComp)));
                }
                displayTag.put("Lore", loreTag);
            }
        }
    }

    @Override
    public void setInvoiceId(ItemStack stack, String invoiceId) {
        if (stack == null || stack.isEmpty() || invoiceId == null) return;
        CompoundTag tag = getOrCreateTag(stack);
        if (tag != null) tag.putString("paybot_invoice_id", invoiceId);
    }

    @Override
    public String getInvoiceId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CompoundTag tag = getTag(stack);
        return (tag != null && tag.contains("paybot_invoice_id")) ? tag.getString("paybot_invoice_id") : null;
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
        } catch (Throwable t) { LOGGER.error("[Forge1_18_x] getMapSavedData error: {}", t.getMessage()); }
        return null;
    }

    @Override
    public void lockMap(MapItemSavedData state) {
        initFieldReflection();
        if (state == null) return;
        if (lockedField != null) {
            try { lockedField.set(state, true); return; } catch (Throwable ignored) {}
        }
        try { Method lockMethod = MapItemSavedData.class.getMethod("lock"); lockMethod.invoke(state); } catch (Throwable ignored) {}
    }

    private CompoundTag getOrCreateTag(ItemStack stack) {
        try { return stack.getOrCreateTag(); } catch (Throwable ignored) { return null; }
    }

    private CompoundTag getTag(ItemStack stack) {
        try { return stack.getTag(); } catch (Throwable ignored) { return null; }
    }

    private CompoundTag getOrCreateDisplayTag(ItemStack stack) {
        try { return stack.getOrCreateTagElement("display"); } catch (Throwable t) { return null; }
    }

    private String safeComponentToJson(Component comp) {
        if (comp == null) return "{\"text\":\"\"}";
        try { return Component.Serializer.toJson(comp); } catch (Throwable t) { return "{\"text\":\"" + comp.getString() + "\"}"; }
    }
}
