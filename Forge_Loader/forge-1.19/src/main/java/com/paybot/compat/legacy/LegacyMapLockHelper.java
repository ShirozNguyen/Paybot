package com.paybot.compat.legacy;

import com.paybot.utils.PayBotDebug;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * LegacyMapLockHelper — Quản lý khóa cứng bản đồ QR Map (MapItemSavedData) cho Minecraft < 1.20.5.
 * Hỗ trợ đa môi trường: Fabric (Intermediary), Forge (SRG), Development (Mojang Official).
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn thành 1 class đơn nhiệm độc lập.
 */
public final class LegacyMapLockHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-LegacyMapLock");
    private static volatile Field resolvedLockedField = null;
    private static volatile boolean initialized = false;

    private LegacyMapLockHelper() {}

    public static boolean lock(MapItemSavedData state) {
        if (state == null) return false;
        ensureInitialized();

        if (resolvedLockedField != null) {
            try {
                resolvedLockedField.setBoolean(state, true);
                return true;
            } catch (Throwable t) {
                PayBotDebug.logSwallowed("LegacyMapLockHelper.lock: set boolean field thất bại", t);
            }
        }

        try {
            Method m = state.getClass().getMethod("lock");
            m.invoke(state);
            return true;
        } catch (Throwable ignored) {}

        PayBotDebug.logSwallowed("LegacyMapLockHelper.lock: không thể khóa QR Map — có thể bị ghi đè địa hình", null);
        return false;
    }

    private static synchronized void ensureInitialized() {
        if (initialized) return;
        initialized = true;

        List<String> candidates = new ArrayList<>();
        candidates.add("locked");

        try {
            Class<?> flClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object flInstance = flClass.getMethod("getInstance").invoke(null);
            Object resolver = flClass.getMethod("getMappingResolver").invoke(flInstance);
            Method mapFieldMethod = resolver.getClass().getMethod("mapFieldName", String.class, String.class, String.class, String.class);
            String mapped = (String) mapFieldMethod.invoke(resolver, "intermediary", "net.minecraft.class_22", "field_1838", "Z");
            if (mapped != null && !mapped.isEmpty() && !candidates.contains(mapped)) {
                candidates.add(mapped);
            }
        } catch (Throwable ignored) {}

        candidates.add("field_1838");
        candidates.add("f_77914_");
        candidates.add("f_77910_");
        candidates.add("f_77906_");

        for (String name : candidates) {
            try {
                Field f = MapItemSavedData.class.getDeclaredField(name);
                if (f.getType() == boolean.class) {
                    f.setAccessible(true);
                    resolvedLockedField = f;
                    LOGGER.info("[LegacyMapLock] Đã xác định field khóa map: '{}'", name);
                    return;
                }
            } catch (Throwable ignored) {}
        }

        try {
            List<Field> boolFields = new ArrayList<>();
            for (Field f : MapItemSavedData.class.getDeclaredFields()) {
                if (f.getType() == boolean.class) boolFields.add(f);
            }
            if (boolFields.size() == 1) {
                Field f = boolFields.get(0);
                f.setAccessible(true);
                resolvedLockedField = f;
                LOGGER.info("[LegacyMapLock] Fallback field boolean duy nhất: '{}'", f.getName());
                return;
            }
        } catch (Throwable ignored) {}

        LOGGER.warn("[LegacyMapLock] Không tìm thấy field 'locked' trên MapItemSavedData!");
    }
}
