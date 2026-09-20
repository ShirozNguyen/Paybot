package com.paybot.compat.modern;

import com.paybot.utils.PayBotDebug;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * ModernItemLoreHelper — Chuyên trách tạo đối tượng ItemLore record wrapper
 * từ danh sách Component cho DataComponents.LORE.
 *
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn chức năng tạo ItemLore wrapper.
 */
public class ModernItemLoreHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-ModernItemLoreHelper");

    private static final String[] LORE_CLASS_CANDIDATES = {
            "net.minecraft.world.item.component.ItemLore",      // Mojang Official
            "net.minecraft.class_9290",                         // Intermediary (Fabric Production)
            "net.minecraft.component.type.LoreComponent"        // Yarn (Fabric Dev)
    };

    private static volatile Class<?> cachedLoreClass = null;

    public static Class<?> resolveLoreClass() {
        if (cachedLoreClass != null) return cachedLoreClass;

        try {
            Class<?> flClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object flInstance = flClass.getMethod("getInstance").invoke(null);
            Object resolver = flClass.getMethod("getMappingResolver").invoke(flInstance);
            if (resolver != null) {
                Method mapClassName = resolver.getClass().getMethod("mapClassName", String.class, String.class);
                String intermediary = (String) mapClassName.invoke(resolver, "intermediary", "net.minecraft.class_9290");
                if (intermediary != null) {
                    cachedLoreClass = Class.forName(intermediary);
                    return cachedLoreClass;
                }
            }
        } catch (Throwable ignored) {}

        for (String candidate : LORE_CLASS_CANDIDATES) {
            try {
                cachedLoreClass = Class.forName(candidate);
                return cachedLoreClass;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static Object buildItemLore(List<Component> componentList) {
        if (componentList == null) return null;
        Class<?> loreClass = resolveLoreClass();
        if (loreClass == null) {
            LOGGER.error("[ModernItemLoreHelper] Không tìm thấy class ItemLore (class_9290) trên runtime!");
            return null;
        }

        // 1. Thử Constructor record 1 tham số: ItemLore(List<Component> lines)
        try {
            for (Constructor<?> ctor : loreClass.getDeclaredConstructors()) {
                ctor.setAccessible(true);
                Class<?>[] pTypes = ctor.getParameterTypes();
                if (pTypes.length == 1 && pTypes[0].isAssignableFrom(List.class)) {
                    return ctor.newInstance(componentList);
                }
            }
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("ModernItemLoreHelper.buildItemLore: ctor(List) failed", t);
        }

        // 2. Thử Constructor record 2 tham số: ItemLore(List<Component> lines, List<Component> styledLines)
        try {
            for (Constructor<?> ctor : loreClass.getDeclaredConstructors()) {
                ctor.setAccessible(true);
                Class<?>[] pTypes = ctor.getParameterTypes();
                if (pTypes.length == 2 && pTypes[0].isAssignableFrom(List.class) && pTypes[1].isAssignableFrom(List.class)) {
                    return ctor.newInstance(componentList, componentList);
                }
            }
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("ModernItemLoreHelper.buildItemLore: ctor(List, List) failed", t);
        }

        // 3. Dự phòng static factory methods nếu có
        try {
            for (Method m : loreClass.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers())) continue;
                if (!loreClass.isAssignableFrom(m.getReturnType())) continue;
                Class<?>[] pTypes = m.getParameterTypes();
                if (pTypes.length == 1 && pTypes[0].isAssignableFrom(List.class)) {
                    m.setAccessible(true);
                    return m.invoke(null, componentList);
                }
            }
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("ModernItemLoreHelper.buildItemLore: static factory failed", t);
        }

        LOGGER.error("[ModernItemLoreHelper] Không thể tạo instance ItemLore từ {}", loreClass.getName());
        return null;
    }
}
