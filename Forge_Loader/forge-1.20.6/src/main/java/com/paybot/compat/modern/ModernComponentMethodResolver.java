package com.paybot.compat.modern;

import com.paybot.utils.PayBotDebug;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * ModernComponentMethodResolver — Chuyên trách tìm kiếm và giải quyết chính xác
 * method set() và get() của DataComponent trên ItemStack.
 *
 * Tuân thủ Quy tắc 17: Đơn nhiệm, tách biệt hoàn toàn logic giải quyết method.
 * Giải quyết dứt điểm lỗi 3 tháng qua: Không bao giờ nhầm lẫn giữa set() (method_57379)
 * và getOrDefault() (method_57825).
 */
public class ModernComponentMethodResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-ModernMethodResolver");

    private final Method setMethod;
    private final Method getMethod;

    public ModernComponentMethodResolver(Class<?> itemStackClass, Object anchorComponentType) {
        Method resolvedSet = null;
        Method resolvedGet = null;

        // 1. Thử dùng Fabric/Quilt MappingResolver nếu môi trường có sẵn
        try {
            Class<?> flClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object flInstance = flClass.getMethod("getInstance").invoke(null);
            Object resolver = flClass.getMethod("getMappingResolver").invoke(flInstance);
            if (resolver != null) {
                Method mapMethodName = resolver.getClass().getMethod("mapMethodName", String.class, String.class, String.class, String.class);
                String runtimeSetName = (String) mapMethodName.invoke(resolver, "intermediary",
                        "net.minecraft.class_1799", "method_57379", "(Lnet/minecraft/class_9331;Ljava/lang/Object;)Ljava/lang/Object;");
                String runtimeGetName = (String) mapMethodName.invoke(resolver, "intermediary",
                        "net.minecraft.class_9278", "method_57824", "(Lnet/minecraft/class_9331;)Ljava/lang/Object;");

                if (runtimeSetName != null && !runtimeSetName.isEmpty()) {
                    for (Method m : itemStackClass.getMethods()) {
                        if (m.getName().equals(runtimeSetName) && m.getParameterCount() == 2) {
                            resolvedSet = m;
                            break;
                        }
                    }
                }
                if (runtimeGetName != null && !runtimeGetName.isEmpty()) {
                    for (Method m : itemStackClass.getMethods()) {
                        if (m.getName().equals(runtimeGetName) && m.getParameterCount() == 1) {
                            resolvedGet = m;
                            break;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("ModernComponentMethodResolver: MappingResolver lookup skipped", t);
        }

        // 2. Thử theo tên chuẩn Mojang/NeoForge ("set" và "get")
        if (resolvedSet == null) {
            for (Method m : itemStackClass.getMethods()) {
                if ("set".equals(m.getName()) && m.getParameterCount() == 2
                        && (anchorComponentType == null || m.getParameterTypes()[0].isInstance(anchorComponentType))) {
                    if (m.getDeclaringClass() == itemStackClass) {
                        resolvedSet = m;
                        break;
                    }
                }
            }
        }
        if (resolvedGet == null) {
            for (Method m : itemStackClass.getMethods()) {
                if ("get".equals(m.getName()) && m.getParameterCount() == 1
                        && (anchorComponentType == null || m.getParameterTypes()[0].isInstance(anchorComponentType))
                        && m.getReturnType() != void.class && m.getReturnType() != boolean.class) {
                    resolvedGet = m;
                    break;
                }
            }
        }

        // 3. Fallback theo CẤU TRÚC (Structural Match) với quy tắc nghiêm ngặt:
        // Method set() BẮT BUỘC phải được khai báo trên chính ItemStack.class (loại trừ getOrDefault kế thừa từ ComponentHolder)
        if (resolvedSet == null && anchorComponentType != null) {
            for (Method m : itemStackClass.getMethods()) {
                if (m.getParameterCount() == 2 && m.getParameterTypes()[0].isInstance(anchorComponentType)) {
                    if (m.getDeclaringClass() == itemStackClass) {
                        resolvedSet = m;
                        break; // Đã tìm thấy method của ItemStack, dừng ngay
                    }
                }
            }
        }

        if (resolvedGet == null && anchorComponentType != null) {
            for (Method m : itemStackClass.getMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(anchorComponentType)
                        && m.getReturnType() != void.class && m.getReturnType() != boolean.class) {
                    resolvedGet = m;
                    break;
                }
            }
        }

        this.setMethod = resolvedSet;
        this.getMethod = resolvedGet;

        if (this.setMethod != null) {
            try { this.setMethod.setAccessible(true); } catch (Throwable ignored) {}
        }
        if (this.getMethod != null) {
            try { this.getMethod.setAccessible(true); } catch (Throwable ignored) {}
        }

        LOGGER.info("[ModernMethodResolver] Resolved setMethod={} (declaring={}), getMethod={} (declaring={})",
                (this.setMethod != null ? this.setMethod.getName() : "null"),
                (this.setMethod != null ? this.setMethod.getDeclaringClass().getSimpleName() : "null"),
                (this.getMethod != null ? this.getMethod.getName() : "null"),
                (this.getMethod != null ? this.getMethod.getDeclaringClass().getSimpleName() : "null"));
    }

    public Method getSetMethod() {
        return setMethod;
    }

    public Method getGetMethod() {
        return getMethod;
    }

    public boolean isReady() {
        return setMethod != null && getMethod != null;
    }
}
