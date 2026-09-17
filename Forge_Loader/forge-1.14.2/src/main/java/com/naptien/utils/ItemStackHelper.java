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

    // [v5.5.5 — DỌN DẸP] Đã xoá cụm hàm setItemNameAndLore()/applyNbtLore()/applyDataComponentsLore()/
    // getOrCreateDisplayTag()/safeComponentToJson() từng nằm ở đây — xác nhận (grep toàn project)
    // KHÔNG có bất kỳ nơi nào gọi tới, chỉ tự gọi lẫn nhau trong chính cụm này. File duy nhất dùng
    // ItemStackHelper là FireworkCompat.java, và nó CHỈ gọi getOrCreateTag() (còn giữ bên dưới) —
    // KHÔNG liên quan gì tới việc set tên/lore item (việc đó do VersionAdapter.setItemNameAndLore
    // đảm nhiệm, xem fabric/v1_21_x/FabricVersionAdapter1_21.java). Trùng tên hàm với method SỐNG
    // là đúng loại bẫy đã khiến các lần sửa trước (theo LOG.md) "tưởng đã fix" nhưng có thể đã sửa
    // nhầm cụm chết này thay vì cụm đang thực sự chạy.

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
