package com.naptien.utils;

import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;

/**
 * ItemStackHelper — Lớp trợ giúp thao tác trên ItemStack tương thích 100% Đa Loader (Fabric/Quilt/Forge/NeoForge)
 * và Đa Phiên Bản Minecraft (1.14+ tới 1.21.x+).
 * 
 * Sử dụng Type-Signature Reflection Inspection để tự tìm phương thức mà không phụ thuộc vào mã hóa Intermediary hay SRG.
 */
public class ItemStackHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-ItemStackHelper");

    private static Method getOrCreateTagMethod = null;
    private static Method getTagMethod = null;
    private static Method getOrCreateTagElementMethod = null;
    private static Method setHoverNameMethod = null;
    private static boolean reflectionInitialized = false;

    private static synchronized void initReflection() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;

        Method[] methods = ItemStack.class.getMethods();

        // 1. Soi tìm getOrCreateTag theo Type Signature (param: empty, return: CompoundTag)
        for (Method m : methods) {
            if (m.getParameterCount() == 0 && m.getReturnType().equals(CompoundTag.class)) {
                // Ưu tiên getOrCreateTag nếu có tên chứa 'getOrCreateTag' hoặc 'method_7948' hoặc 'm_41784_'
                if (getOrCreateTagMethod == null) {
                    getOrCreateTagMethod = m;
                }
                if (m.getName().equals("getOrCreateTag") || m.getName().equals("method_7948") || m.getName().equals("m_41784_")) {
                    getOrCreateTagMethod = m;
                    break;
                }
            }
        }

        // 2. Soi tìm getTag theo Type Signature (param: empty, return: CompoundTag)
        for (Method m : methods) {
            if (m.getParameterCount() == 0 && m.getReturnType().equals(CompoundTag.class)) {
                if (m.getName().equals("getTag") || m.getName().equals("method_7969") || m.getName().equals("m_41783_")) {
                    getTagMethod = m;
                    break;
                }
            }
        }
        if (getTagMethod == null) {
            getTagMethod = getOrCreateTagMethod;
        }

        // 3. Soi tìm getOrCreateTagElement theo Type Signature (param: String, return: CompoundTag)
        for (Method m : methods) {
            if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class && m.getReturnType().equals(CompoundTag.class)) {
                getOrCreateTagElementMethod = m;
                if (m.getName().equals("getOrCreateTagElement") || m.getName().equals("method_7950") || m.getName().equals("m_41698_")) {
                    break;
                }
            }
        }

        // 4. Soi tìm setHoverName / setCustomName theo Type Signature (param: Component)
        for (Method m : methods) {
            if (m.getParameterCount() == 1 && Component.class.isAssignableFrom(m.getParameterTypes()[0])) {
                setHoverNameMethod = m;
                if (m.getName().equals("setHoverName") || m.getName().equals("setCustomName") || m.getName().equals("method_7980") || m.getName().equals("m_41714_")) {
                    break;
                }
            }
        }
    }

    /**
     * Cài đặt Tên hiển thị (Name) và Chú thích (Lore) cho ItemStack an toàn 100% trên mọi Loader và phiên bản MC.
     */
    public static void setItemNameAndLore(ItemStack stack, String name, List<String> lore) {
        if (stack == null || stack.isEmpty()) return;
        initReflection();

        // 1. Đặt Tên bằng Native API setHoverName trực tiếp + Reflection + NBT Fallback
        if (name != null && !name.isEmpty()) {
            Component nameComponent = ComponentColorParser.parse(name);
            boolean setViaMethod = false;

            // Thử gọi stack.setHoverName(Component) trực tiếp nếu có thể
            try {
                stack.setHoverName(nameComponent);
                setViaMethod = true;
            } catch (Throwable ignored) {}

            if (!setViaMethod && setHoverNameMethod != null) {
                try {
                    setHoverNameMethod.invoke(stack, nameComponent);
                    setViaMethod = true;
                } catch (Throwable ignored) {}
            }

            // Luôn ghi NBT display.Name làm backup cho MC <= 1.20.4 và ViaVersion client
            if (MinecraftVersionDetector.isLegacyNbtEra() || !setViaMethod) {
                CompoundTag displayTag = getOrCreateDisplayTag(stack);
                if (displayTag != null) {
                    displayTag.putString("Name", safeComponentToJson(nameComponent));
                }
            }
        }

        // 2. Đặt Lore an toàn
        if (lore != null && !lore.isEmpty()) {
            if (MinecraftVersionDetector.isDataComponentsEra()) {
                // MC >= 1.20.5: Thử dùng DataComponentTypes.LORE
                boolean appliedDataComponent = applyDataComponentsLore(stack, lore);
                if (!appliedDataComponent) {
                    // Fallback
                    CompoundTag displayTag = getOrCreateDisplayTag(stack);
                    if (displayTag != null) {
                        applyNbtLore(displayTag, lore);
                    }
                }
            } else {
                // MC <= 1.20.4: Thao tác qua NBT Tag display.Lore
                CompoundTag displayTag = getOrCreateDisplayTag(stack);
                if (displayTag != null) {
                    applyNbtLore(displayTag, lore);
                }
            }
        }
    }

    /**
     * Gán NBT Lore truyền thống cho MC <= 1.20.4
     */
    private static void applyNbtLore(CompoundTag displayTag, List<String> lore) {
        ListTag loreTag = new ListTag();
        for (String line : lore) {
            loreTag.add(StringTag.valueOf(safeComponentToJson(ComponentColorParser.parse(line))));
        }
        displayTag.put("Lore", loreTag);
    }

    /**
     * Gán DataComponent LORE cho MC >= 1.20.5
     */
    private static boolean applyDataComponentsLore(ItemStack stack, List<String> lore) {
        try {
            Class<?> dataComponentTypesClass = Class.forName("net.minecraft.core.component.DataComponentTypes");
            Object loreComponentType = dataComponentTypesClass.getField("LORE").get(null);

            Class<?> itemLoreClass = Class.forName("net.minecraft.world.item.component.ItemLore");
            List<Component> componentList = ComponentColorParser.parseLore(lore);
            Object itemLoreInstance = null;
            for (java.lang.reflect.Constructor<?> ctor : itemLoreClass.getDeclaredConstructors()) {
                ctor.setAccessible(true);
                Class<?>[] pTypes = ctor.getParameterTypes();
                if (pTypes.length == 1 && pTypes[0].isAssignableFrom(List.class)) {
                    itemLoreInstance = ctor.newInstance(componentList);
                    break;
                } else if (pTypes.length == 2 && pTypes[0].isAssignableFrom(List.class) && pTypes[1].isAssignableFrom(List.class)) {
                    itemLoreInstance = ctor.newInstance(componentList, componentList);
                    break;
                }
            }

            if (itemLoreInstance != null) {
                Method setMethod = ItemStack.class.getMethod("set", Class.forName("net.minecraft.core.component.DataComponentType"), Object.class);
                setMethod.invoke(stack, loreComponentType, itemLoreInstance);
                return true;
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Lấy hoặc tạo NBT Tag 'display' của ItemStack an toàn.
     */
    public static CompoundTag getOrCreateDisplayTag(ItemStack stack) {
        initReflection();
        if (getOrCreateTagElementMethod != null) {
            try {
                return (CompoundTag) getOrCreateTagElementMethod.invoke(stack, "display");
            } catch (Throwable ignored) {}
        }

        CompoundTag tag = getOrCreateTag(stack);
        if (tag != null) {
            if (!tag.contains("display", 10)) {
                CompoundTag displayTag = new CompoundTag();
                tag.put("display", displayTag);
                return displayTag;
            }
            return tag.getCompound("display");
        }
        return null;
    }

    /**
     * Lấy hoặc tạo NBT Tag an toàn (không bị NoSuchMethodError ở MC 1.20.5+).
     */
    public static CompoundTag getOrCreateTag(ItemStack stack) {
        initReflection();
        if (getOrCreateTagMethod != null) {
            try {
                return (CompoundTag) getOrCreateTagMethod.invoke(stack);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /**
     * Lấy NBT Tag nếu có (trả về null nếu không có tag hoặc method không tồn tại).
     */
    public static CompoundTag getTag(ItemStack stack) {
        initReflection();
        if (getTagMethod != null) {
            try {
                return (CompoundTag) getTagMethod.invoke(stack);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /**
     * Kiểm tra xem ItemStack có NBT tag hay không.
     */
    public static boolean hasTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CompoundTag tag = getTag(stack);
        return tag != null && !tag.isEmpty();
    }

    /**
     * Safe conversion Component -> JSON String an toàn signature trên mọi bản MC.
     */
    public static String safeComponentToJson(Component component) {
        if (component == null) return "{\"text\":\"\"}";

        try {
            Class<?> serializerClass = null;
            try {
                serializerClass = Component.Serializer.class;
            } catch (Throwable t) {
                try {
                    serializerClass = Class.forName("net.minecraft.class_2561$class_2562");
                } catch (Throwable ignored) {}
            }

            if (serializerClass != null) {
                for (Method m : serializerClass.getMethods()) {
                    if (m.getParameterCount() == 1 && m.getReturnType().equals(String.class)) {
                        Class<?> p0 = m.getParameterTypes()[0];
                        if (Component.class.isAssignableFrom(p0) || p0.getName().contains("class_2561")) {
                            m.setAccessible(true);
                            String res = (String) m.invoke(null, component);
                            if (res != null && !res.isEmpty()) return res;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        String plain = component.getString();
        return "{\"text\":\"" + plain.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }
}
