package com.paybot.compat.modern;

import com.paybot.utils.PayBotDebug;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * ModernFallbackHoverName — Chuyên trách fallback setHoverName khi đường chính registry gặp sự cố.
 * Đảm bảo KHÔNG BAO GIỜ văng ClassNotFoundException do hardcode tên Mojang.
 *
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn chức năng fallback hover name.
 */
public class ModernFallbackHoverName {
    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-ModernFallbackHoverName");

    private static final String[] DATA_COMPONENTS_CLASS_CANDIDATES = {
            "net.minecraft.class_9334",                        // Intermediary (Fabric/Quilt Production)
            "net.minecraft.component.DataComponentTypes",       // Yarn (Fabric Dev)
            "net.minecraft.core.component.DataComponents"       // Mojang Official / NeoForge / Forge
    };

    public static void trySetHoverNameFallback(ItemStack stack, Component nameComp) {
        if (stack == null || nameComp == null) return;

        // 1. Thử method setHoverName(Component) trực tiếp
        try {
            Method m = stack.getClass().getMethod("setHoverName", Component.class);
            m.invoke(stack, nameComp);
            return;
        } catch (Throwable ignored) {}

        // 2. Thử qua reflection DataComponents (CUSTOM_NAME)
        try {
            Class<?> dcClass = null;
            for (String candidate : DATA_COMPONENTS_CLASS_CANDIDATES) {
                try {
                    dcClass = Class.forName(candidate);
                    if (dcClass != null) break;
                } catch (Throwable ignored) {}
            }

            if (dcClass != null) {
                Object compType = null;
                for (String fName : new String[]{"field_49626", "CUSTOM_NAME"}) {
                    try {
                        Field f = dcClass.getField(fName);
                        compType = f.get(null);
                        if (compType != null) break;
                    } catch (Throwable ignored) {}
                }

                if (compType != null) {
                    for (Method m : stack.getClass().getMethods()) {
                        if (m.getParameterCount() == 2
                                && m.getParameterTypes()[0].isInstance(compType)
                                && m.getDeclaringClass() == stack.getClass()) {
                            m.setAccessible(true);
                            m.invoke(stack, compType, nameComp);
                            return;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("ModernFallbackHoverName.trySetHoverNameFallback failed", t);
        }
    }
}
