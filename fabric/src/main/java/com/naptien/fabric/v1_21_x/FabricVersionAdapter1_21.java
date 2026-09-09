package com.naptien.fabric.v1_21_x;

import com.naptien.compat.version.VersionAdapter;
import com.naptien.utils.ComponentColorParser;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class FabricVersionAdapter1_21 implements VersionAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-Fabric-Adapter1_21_x");

    private Object loreComponentType = null;
    private Object customNameComponentType = null;
    private Object customDataComponentType = null;
    private Class<?> itemLoreClass = null;
    private Method setComponentMethod = null;
    private Method getComponentMethod = null;
    private boolean initialized = false;

    private synchronized void ensureInitialized() {
        if (initialized) return;
        initialized = true;

        try {
            Class<?> holderClass = null;
            String[] possibleHolders = {
                "net.minecraft.class_9334",
                "net.minecraft.core.component.DataComponentTypes",
                "net.minecraft.core.component.DataComponents"
            };
            for (String name : possibleHolders) {
                try { holderClass = Class.forName(name); break; } catch (Throwable ignored) {}
            }

            if (holderClass != null) {
                for (Field f : holderClass.getFields()) {
                    String fName = f.getName();
                    if (fName.equalsIgnoreCase("CUSTOM_NAME") || fName.equals("field_49576")) customNameComponentType = f.get(null);
                    else if (fName.equalsIgnoreCase("LORE") || fName.equals("field_49589")) loreComponentType = f.get(null);
                    else if (fName.equalsIgnoreCase("CUSTOM_DATA") || fName.equals("field_49575")) customDataComponentType = f.get(null);
                }
            }

            try { itemLoreClass = Class.forName("net.minecraft.world.item.component.ItemLore"); } catch (Throwable ignored) {}

            for (Method m : ItemStack.class.getMethods()) {
                if (m.getParameterCount() == 2 && m.getName().equals("set")) setComponentMethod = m;
                else if (m.getParameterCount() == 1 && m.getName().equals("get")) getComponentMethod = m;
            }
        } catch (Throwable t) { LOGGER.error("[Fabric1_21_x] Init error: {}", t.getMessage()); }
    }

    @Override
    public void setItemNameAndLore(ItemStack stack, String name, List<String> lore) {
        if (stack == null || stack.isEmpty()) return;
        ensureInitialized();

        if (name != null && !name.isEmpty()) {
            Component nameComp = ComponentColorParser.parse(name);
            try { stack.setHoverName(nameComp); } catch (Throwable ignored) {
                if (customNameComponentType != null && setComponentMethod != null) {
                    try { setComponentMethod.invoke(stack, customNameComponentType, nameComp); } catch (Throwable ignored2) {}
                }
            }
        }

        if (lore != null && !lore.isEmpty() && loreComponentType != null && itemLoreClass != null && setComponentMethod != null) {
            try {
                List<Component> componentList = ComponentColorParser.parseLore(lore);
                Object itemLoreInstance = createItemLoreInstance(componentList);
                if (itemLoreInstance != null) setComponentMethod.invoke(stack, loreComponentType, itemLoreInstance);
            } catch (Throwable t) { LOGGER.warn("[Fabric1_21_x] set lore failed: {}", t.getMessage()); }
        }
    }

    @Override
    public void setInvoiceId(ItemStack stack, String invoiceId) {
        if (stack == null || stack.isEmpty() || invoiceId == null) return;
        ensureInitialized();
        if (customDataComponentType != null) {
            try {
                Class<?> customDataClass = Class.forName("net.minecraft.world.item.component.CustomData");
                Method updateMethod = customDataClass.getMethod("update",
                        Class.forName("net.minecraft.core.component.DataComponentType"),
                        ItemStack.class,
                        java.util.function.Consumer.class);
                java.util.function.Consumer<net.minecraft.nbt.CompoundTag> consumer = tag -> tag.putString("paybot_invoice_id", invoiceId);
                updateMethod.invoke(null, customDataComponentType, stack, consumer);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public String getInvoiceId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ensureInitialized();
        if (customDataComponentType != null && getComponentMethod != null) {
            try {
                Object customDataObj = getComponentMethod.invoke(stack, customDataComponentType);
                if (customDataObj != null) {
                    Method copyTagMethod = customDataObj.getClass().getMethod("copyTag");
                    net.minecraft.nbt.CompoundTag tag = (net.minecraft.nbt.CompoundTag) copyTagMethod.invoke(customDataObj);
                    if (tag != null && tag.contains("paybot_invoice_id")) return tag.getString("paybot_invoice_id");
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    @Override
    public MapItemSavedData getMapSavedData(ItemStack mapItem, ServerLevel world) {
        if (mapItem == null || world == null) return null;
        ensureInitialized();
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
        } catch (Throwable ignored) {}
        return null;
    }

    @Override
    public void lockMap(MapItemSavedData state) {
        if (state == null) return;
        try {
            Field lockedField = MapItemSavedData.class.getDeclaredField("locked");
            lockedField.setAccessible(true);
            lockedField.set(state, true);
        } catch (Throwable ignored) {}
    }

    private Object createItemLoreInstance(List<Component> componentList) {
        if (itemLoreClass == null) return null;
        try {
            for (Constructor<?> ctor : itemLoreClass.getDeclaredConstructors()) {
                ctor.setAccessible(true);
                Class<?>[] pTypes = ctor.getParameterTypes();
                if (pTypes.length == 1 && pTypes[0].isAssignableFrom(List.class)) return ctor.newInstance(componentList);
                else if (pTypes.length == 2 && pTypes[0].isAssignableFrom(List.class) && pTypes[1].isAssignableFrom(List.class)) return ctor.newInstance(componentList, componentList);
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
