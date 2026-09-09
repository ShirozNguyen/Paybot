package com.naptien.utils;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * DataComponentReflector — Lớp chuyên biệt soi tìm và cài đặt Data Components (Custom Name & Lore)
 * bằng Type-Signature Inspection không phụ thuộc tên mã hóa Intermediary, Mojmap hay SRG.
 * 
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn chức năng Reflection Data Components.
 */
public class DataComponentReflector {

    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-DataComponentReflector");

    private static Method setMethod = null;
    private static Method setHoverNameMethod = null;
    private static Object customNameType = null;
    private static Object itemNameType = null;
    private static Object loreType = null;
    private static Class<?> itemLoreClass = null;
    private static boolean initialized = false;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        try {
            // 1. Tìm set method trên ItemStack (param: [DataComponentType, Object])
            for (Method m : ItemStack.class.getMethods()) {
                if (m.getParameterCount() == 2) {
                    String p0Name = m.getParameterTypes()[0].getName();
                    String p1Name = m.getParameterTypes()[1].getName();
                    if ((p0Name.contains("DataComponentType") || p0Name.contains("class_9331"))
                            && !p1Name.contains("DataComponentPatch")) {
                        setMethod = m;
                        setMethod.setAccessible(true);
                        break;
                    }
                }
            }

            // 2. Tìm setHoverName method — ưu tiên tên method trước (tránh bắt sai method)
            // Mojmap: setHoverName | Intermediary: method_7980 | SRG: m_41714_
            for (Method m : ItemStack.class.getMethods()) {
                String mName = m.getName();
                if (m.getParameterCount() == 1
                        && (mName.equals("setHoverName") || mName.equals("setCustomName")
                            || mName.equals("method_7980") || mName.equals("m_41714_"))) {
                    setHoverNameMethod = m;
                    setHoverNameMethod.setAccessible(true);
                    break;
                }
            }
            // Fallback: type-signature — chỉ khớp khi tên method liên quan hover/name/custom
            if (setHoverNameMethod == null) {
                for (Method m : ItemStack.class.getMethods()) {
                    if (m.getParameterCount() == 1) {
                        String p0Name = m.getParameterTypes()[0].getName();
                        String mName = m.getName().toLowerCase();
                        boolean isComponentParam = p0Name.contains("net.minecraft.network.chat.Component")
                                || p0Name.contains("class_2561");
                        boolean isNameMethod = mName.contains("hover") || mName.contains("name")
                                || mName.contains("custom");
                        if (isComponentParam && isNameMethod) {
                            setHoverNameMethod = m;
                            setHoverNameMethod.setAccessible(true);
                            break;
                        }
                    }
                }
            }

            // 3. Tìm class DataComponentTypes / DataComponents / class_9334
            Class<?> holderClass = null;
            String[] possibleHolders = {
                "net.minecraft.core.component.DataComponentTypes",  // MC 1.20.5+ release (ĐÚng cho 1.21.x)
                "net.minecraft.core.component.DataComponents",       // MC 1.20.5 snapshot
                "net.minecraft.class_9334"                           // Intermediary fallback
            };
            for (String name : possibleHolders) {
                try {
                    holderClass = Class.forName(name);
                    break;
                } catch (Throwable ignored) {}
            }

            if (holderClass != null) {
                for (Field f : holderClass.getFields()) {
                    String fName = f.getName();
                    if (fName.equalsIgnoreCase("CUSTOM_NAME") || fName.equals("field_49576")) {
                        customNameType = f.get(null);
                    } else if (fName.equalsIgnoreCase("ITEM_NAME") || fName.equals("field_49588")) {
                        itemNameType = f.get(null);
                    } else if (fName.equalsIgnoreCase("LORE") || fName.equals("field_49589")) {
                        loreType = f.get(null);
                    }
                }
            }

            // 4. Tìm ItemLore class (net.minecraft.world.item.component.ItemLore / class_9299)
            String[] possibleLoreClasses = {
                "net.minecraft.world.item.component.ItemLore",
                "net.minecraft.class_9299"
            };
            for (String name : possibleLoreClasses) {
                try {
                    itemLoreClass = Class.forName(name);
                    break;
                } catch (Throwable ignored) {}
            }

            LOGGER.info("[PayBot] DataComponentReflector init: setMethod={}, setHoverName={}, customName={}, loreType={}, itemLoreClass={}",
                    setMethod != null, setHoverNameMethod != null, customNameType != null, loreType != null, itemLoreClass != null);

        } catch (Throwable t) {
            LOGGER.error("[PayBot] DataComponentReflector init error: {}", t.getMessage(), t);
        }
    }

    /**
     * Cài đặt Custom Name và Lore an toàn 100% qua Data Components trên MC 1.20.5 - 1.21.1+.
     */
    public static boolean setCustomNameAndLore(ItemStack stack, String name, List<String> lore) {
        init();
        if (stack == null || stack.isEmpty()) return false;

        boolean successName = false;
        boolean successLore = false;

        Component nameComp = (name != null && !name.isEmpty()) ? ComponentColorParser.parse(name) : null;

        // Ưu tiên 1: Đặt name bằng Native API setHoverName
        if (nameComp != null && setHoverNameMethod != null) {
            try {
                setHoverNameMethod.invoke(stack, nameComp);
                successName = true;
            } catch (Throwable t) {
                LOGGER.warn("[PayBot] setHoverName invoke failed: {}", t.getMessage());
            }
        }

        // Ưu tiên 2: Đặt name bằng DataComponentType (CUSTOM_NAME / ITEM_NAME)
        if (!successName && nameComp != null && setMethod != null) {
            if (customNameType != null) {
                try {
                    setMethod.invoke(stack, customNameType, nameComp);
                    successName = true;
                } catch (Throwable t) {
                    LOGGER.warn("[PayBot] set customNameType failed: {}", t.getMessage());
                }
            }
            if (!successName && itemNameType != null) {
                try {
                    setMethod.invoke(stack, itemNameType, nameComp);
                    successName = true;
                } catch (Throwable t) {
                    LOGGER.warn("[PayBot] set itemNameType failed: {}", t.getMessage());
                }
            }
        } else if (name == null || name.isEmpty()) {
            successName = true;
        }

        // Đặt Lore bằng DataComponentType LORE + ItemLore
        if (lore != null && !lore.isEmpty() && setMethod != null && loreType != null && itemLoreClass != null) {
            try {
                List<Component> componentList = ComponentColorParser.parseLore(lore);
                Object itemLoreInstance = createItemLoreInstance(componentList);
                if (itemLoreInstance != null) {
                    setMethod.invoke(stack, loreType, itemLoreInstance);
                    successLore = true;
                }
            } catch (Throwable t) {
                LOGGER.warn("[PayBot] set loreType failed: {}", t.getMessage());
            }
        } else {
            successLore = true;
        }

        // Chỉ trả true khi CẢ hai đều thành công, tránh bỏ fallback NBT lore khi lore fail
        return successName && successLore;
    }

    private static Object createItemLoreInstance(List<Component> componentList) {
        if (itemLoreClass == null) return null;
        try {
            for (Constructor<?> ctor : itemLoreClass.getDeclaredConstructors()) {
                ctor.setAccessible(true);
                Class<?>[] pTypes = ctor.getParameterTypes();
                if (pTypes.length == 1 && pTypes[0].isAssignableFrom(List.class)) {
                    return ctor.newInstance(componentList);
                } else if (pTypes.length == 2 && pTypes[0].isAssignableFrom(List.class) && pTypes[1].isAssignableFrom(List.class)) {
                    return ctor.newInstance(componentList, componentList);
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[PayBot] createItemLoreInstance error: {}", t.getMessage());
        }
        return null;
    }
}
