package com.paybot.compat.modern;

import com.paybot.utils.PayBotDebug;
import com.paybot.utils.TagCompatHelper;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * ModernCustomDataHelper — Chuyên trách tạo và đọc CustomData component (chứa CompoundTag)
 * để lưu trữ và truy xuất paybot_invoice_id.
 *
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn chức năng CustomData component.
 */
public class ModernCustomDataHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-ModernCustomDataHelper");

    private static final String[] CUSTOM_DATA_CLASS_CANDIDATES = {
            "net.minecraft.world.item.component.CustomData",    // Mojang Official
            "net.minecraft.class_9279",                         // Intermediary (Fabric Production)
            "net.minecraft.component.type.NbtComponent"         // Yarn (Fabric Dev)
    };

    private static volatile Class<?> cachedCustomDataClass = null;

    public static Class<?> resolveCustomDataClass() {
        if (cachedCustomDataClass != null) return cachedCustomDataClass;

        try {
            Class<?> flClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object flInstance = flClass.getMethod("getInstance").invoke(null);
            Object resolver = flClass.getMethod("getMappingResolver").invoke(flInstance);
            if (resolver != null) {
                Method mapClassName = resolver.getClass().getMethod("mapClassName", String.class, String.class);
                String intermediary = (String) mapClassName.invoke(resolver, "intermediary", "net.minecraft.class_9279");
                if (intermediary != null) {
                    cachedCustomDataClass = Class.forName(intermediary);
                    return cachedCustomDataClass;
                }
            }
        } catch (Throwable ignored) {}

        for (String candidate : CUSTOM_DATA_CLASS_CANDIDATES) {
            try {
                cachedCustomDataClass = Class.forName(candidate);
                return cachedCustomDataClass;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /**
     * Đóng gói CompoundTag thành instance CustomData.
     */
    public static Object buildCustomData(CompoundTag tag) {
        if (tag == null) return null;
        Class<?> customDataClass = resolveCustomDataClass();
        if (customDataClass == null) {
            LOGGER.error("[ModernCustomDataHelper] Không tìm thấy class CustomData (class_9279) trên runtime!");
            return null;
        }

        // 1. Ưu tiên static factory: CustomData.of(CompoundTag)
        try {
            for (Method m : customDataClass.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers())) {
                    Class<?>[] pTypes = m.getParameterTypes();
                    if (pTypes.length == 1 && pTypes[0].isAssignableFrom(CompoundTag.class)
                            && customDataClass.isAssignableFrom(m.getReturnType())) {
                        m.setAccessible(true);
                        return m.invoke(null, tag);
                    }
                }
            }
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("ModernCustomDataHelper.buildCustomData: static factory failed", t);
        }

        // 2. Dự phòng: constructor nhận CompoundTag
        try {
            for (Constructor<?> ctor : customDataClass.getDeclaredConstructors()) {
                ctor.setAccessible(true);
                Class<?>[] pTypes = ctor.getParameterTypes();
                if (pTypes.length == 1 && pTypes[0].isAssignableFrom(CompoundTag.class)) {
                    return ctor.newInstance(tag);
                }
            }
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("ModernCustomDataHelper.buildCustomData: constructor failed", t);
        }

        LOGGER.error("[ModernCustomDataHelper] Không thể tạo instance CustomData từ {}", customDataClass.getName());
        return null;
    }

    /**
     * Trích xuất CompoundTag từ instance CustomData.
     */
    public static CompoundTag extractCompoundTag(Object customDataObj) {
        if (customDataObj == null) return null;
        if (customDataObj instanceof CompoundTag) return (CompoundTag) customDataObj;

        try {
            for (Method m : customDataObj.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && CompoundTag.class.isAssignableFrom(m.getReturnType())) {
                    m.setAccessible(true);
                    Object result = m.invoke(customDataObj);
                    if (result instanceof CompoundTag) return (CompoundTag) result;
                }
            }
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("ModernCustomDataHelper.extractCompoundTag failed", t);
        }
        return null;
    }

    /**
     * Lấy invoice ID từ CustomData object.
     */
    public static String getInvoiceIdFromCustomData(Object customDataObj) {
        CompoundTag tag = extractCompoundTag(customDataObj);
        if (tag != null && TagCompatHelper.contains(tag, "paybot_invoice_id")) {
            return TagCompatHelper.getString(tag, "paybot_invoice_id");
        }
        return null;
    }
}
