package com.paybot.gui;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * ModernItemProvider — Tiện ích cung cấp Item & ItemStack an toàn cho môi trường 26.x (Rule 17).
 * Sử dụng BuiltInRegistries hoặc Dynamic Reflection để độc lập hoàn toàn với việc đổi tên field
 * của class Items giữa các snapshot / loader mapping.
 */
public final class ModernItemProvider {

    private static final Map<String, Item> ITEM_CACHE = new HashMap<>();

    private ModernItemProvider() {}

    public static Item getItem(String name) {
        if (name == null || name.isEmpty()) return Items.BARRIER;
        String key = name.toLowerCase().replace(" ", "_");
        if (ITEM_CACHE.containsKey(key)) {
            return ITEM_CACHE.get(key);
        }

        Item item = resolveItem(key);
        if (item == null) {
            item = Items.BARRIER;
        }
        ITEM_CACHE.put(key, item);
        return item;
    }

    public static ItemStack createStack(String name) {
        Item it = getItem(name);
        return new ItemStack(it);
    }

    private static Item resolveItem(String name) {
        try {
            Field f = Items.class.getField(name.toUpperCase());
            Object val = f.get(null);
            if (val instanceof Item it) return it;
        } catch (Throwable ignored) {}

        try {
            Class<?> regClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            Field regField = regClass.getField("ITEM");
            Object registry = regField.get(null);
            for (Method m : registry.getClass().getMethods()) {
                if (m.getName().equals("get") && m.getParameterCount() == 1) {
                    Class<?> paramType = m.getParameterTypes()[0];
                    Object loc = createResourceLocation("minecraft", name);
                    if (loc != null && paramType.isInstance(loc)) {
                        Object res = m.invoke(registry, loc);
                        if (res instanceof Item it) return it;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static Object createResourceLocation(String namespace, String path) {
        try {
            Class<?> rlClass = Class.forName("net.minecraft.resources.ResourceLocation");
            try {
                Method mOf = rlClass.getMethod("fromNamespaceAndPath", String.class, String.class);
                return mOf.invoke(null, namespace, path);
            } catch (Throwable t1) {
                try {
                    Method mParse = rlClass.getMethod("parse", String.class);
                    return mParse.invoke(null, namespace + ":" + path);
                } catch (Throwable t2) {
                    var ctor = rlClass.getDeclaredConstructor(String.class, String.class);
                    ctor.setAccessible(true);
                    return ctor.newInstance(namespace, path);
                }
            }
        } catch (Throwable ignored) {
            return null;
        }
    }
}
