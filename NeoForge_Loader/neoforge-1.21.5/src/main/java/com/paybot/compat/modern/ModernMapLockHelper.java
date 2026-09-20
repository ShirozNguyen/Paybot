package com.paybot.compat.modern;

import com.paybot.utils.PayBotDebug;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * ModernMapLockHelper — Chuyên trách tìm kiếm field 'locked' và khóa bản đồ QR
 * trên MapItemSavedData để ngăn không bị ghi đè địa hình.
 *
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn chức năng khóa bản đồ QR.
 */
public class ModernMapLockHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-ModernMapLockHelper");

    private static volatile Field cachedLockedField = null;
    private static volatile boolean searched = false;

    public static synchronized Field findLockedField() {
        if (searched) return cachedLockedField;
        searched = true;

        String[] candidates = {"locked", "field_1838", "f_77914_", "f_77910_", "f_77906_"};
        for (String name : candidates) {
            try {
                Field f = MapItemSavedData.class.getDeclaredField(name);
                if (f.getType() == boolean.class) {
                    f.setAccessible(true);
                    cachedLockedField = f;
                    return cachedLockedField;
                }
            } catch (Throwable ignored) {}
        }

        try {
            Class<?> flClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object flInstance = flClass.getMethod("getInstance").invoke(null);
            Object resolver = flClass.getMethod("getMappingResolver").invoke(flInstance);
            if (resolver != null) {
                Method mapFieldName = resolver.getClass().getMethod("mapFieldName", String.class, String.class, String.class, String.class);
                String runtimeName = (String) mapFieldName.invoke(resolver, "intermediary", "net.minecraft.class_22", "field_1838", "Z");
                if (runtimeName != null) {
                    Field f = MapItemSavedData.class.getDeclaredField(runtimeName);
                    if (f.getType() == boolean.class) {
                        f.setAccessible(true);
                        cachedLockedField = f;
                        return cachedLockedField;
                    }
                }
            }
        } catch (Throwable ignored) {}

        List<Field> boolFields = new ArrayList<>();
        for (Field f : MapItemSavedData.class.getDeclaredFields()) {
            if (f.getType() == boolean.class) boolFields.add(f);
        }
        if (boolFields.size() == 1) {
            Field f = boolFields.get(0);
            f.setAccessible(true);
            cachedLockedField = f;
            LOGGER.info("[ModernMapLockHelper] Dùng fallback type-scan, tìm thấy đúng 1 field boolean: {}", f.getName());
            return cachedLockedField;
        }

        PayBotDebug.logSwallowed("ModernMapLockHelper: Không xác định được field 'locked' trong MapItemSavedData", null);
        return null;
    }

    public static void lockMap(MapItemSavedData state) {
        if (state == null) return;
        Field f = findLockedField();
        if (f != null) {
            try {
                f.set(state, true);
            } catch (Throwable t) {
                PayBotDebug.logSwallowed("ModernMapLockHelper.lockMap failed", t);
            }
        }
    }
}
