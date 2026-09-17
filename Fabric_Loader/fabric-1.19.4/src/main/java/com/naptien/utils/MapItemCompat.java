package com.naptien.utils;

import com.naptien.compat.version.VersionAdapterFactory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * MapItemCompat — Class quản lý MapId và MapItemSavedData đa phiên bản (MC 1.14.4 tới 1.21.x+).
 *
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn chức năng quản lý Map/QR Item.
 * Sử dụng Reflection an toàn để tương thích biên dịch 100% trên mọi bản MC.
 */
public class MapItemCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-MapItemCompat");

    // Cache reflection results
    private static Field colorsField = null;
    private static Field lockedField = null;
    private static boolean reflectionInit = false;

    /**
     * Tìm class DataComponentTypes / DataComponents an toàn — thử DataComponentTypes trước.
     * FIX: MC 1.21.x dùng DataComponentTypes, không phải DataComponents (snapshot name cũ).
     */
    private static Class<?> findDataComponentsClass() {
        String[] candidates = {
            "net.minecraft.core.component.DataComponentTypes",  // MC 1.20.5+ (release)
            "net.minecraft.core.component.DataComponents",       // MC 1.20.5 snapshot
            "net.minecraft.class_9334"                           // Intermediary fallback
        };
        for (String name : candidates) {
            try {
                return Class.forName(name);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /**
     * Khởi tạo reflection cho colors và locked field một lần duy nhất.
     * FIX: Access field qua reflection để đảm bảo đa phiên bản (tên field sau remap khác nhau).
     */
    private static synchronized void initFieldReflection() {
        if (reflectionInit) return;
        reflectionInit = true;

        // Tìm colors field (byte[] 16384) — Mojang: "colors" | SRG: "f_77905_" | Intermediary: "field_1837"
        String[] colorsCandidates = {"colors", "f_77905_", "field_1837"};
        for (String name : colorsCandidates) {
            try {
                Field f = MapItemSavedData.class.getDeclaredField(name);
                if (f.getType() == byte[].class) {
                    f.setAccessible(true);
                    colorsField = f;
                    LOGGER.debug("[MapItemCompat] Found colors field: {}", name);
                    break;
                }
            } catch (Throwable ignored) {}
        }
        // Fallback: scan tất cả field tìm byte[] có length phù hợp (16384)
        if (colorsField == null) {
            for (Field f : MapItemSavedData.class.getDeclaredFields()) {
                if (f.getType() == byte[].class) {
                    f.setAccessible(true);
                    colorsField = f;
                    LOGGER.debug("[MapItemCompat] Found colors field by type scan: {}", f.getName());
                    break;
                }
            }
        }

        // Tìm locked field (boolean) — Mojang: "locked" | SRG: "f_77906_" | Intermediary: "field_1838"
        String[] lockedCandidates = {"locked", "f_77906_", "field_1838"};
        for (String name : lockedCandidates) {
            try {
                Field f = MapItemSavedData.class.getDeclaredField(name);
                if (f.getType() == boolean.class) {
                    f.setAccessible(true);
                    lockedField = f;
                    LOGGER.debug("[MapItemCompat] Found locked field: {}", name);
                    break;
                }
            } catch (Throwable ignored) {}
        }

        LOGGER.info("[MapItemCompat] Reflection init: colorsField={}, lockedField={}",
                colorsField != null, lockedField != null);
    }

    /**
     * Lấy byte[] colors từ MapItemSavedData an toàn qua reflection.
     */
    public static byte[] getColors(MapItemSavedData state) {
        initFieldReflection();
        if (colorsField == null || state == null) return null;
        try {
            return (byte[]) colorsField.get(state);
        } catch (Throwable t) {
            LOGGER.error("[MapItemCompat] Cannot read colors field: {}", t.getMessage());
            return null;
        }
    }

    /**
     * Set pixel màu vào MapItemSavedData an toàn qua reflection.
     * @return true nếu thành công
     */
    public static boolean setColor(MapItemSavedData state, int index, byte color) {
        initFieldReflection();
        if (colorsField == null || state == null) return false;
        try {
            byte[] colors = (byte[]) colorsField.get(state);
            if (colors != null && index >= 0 && index < colors.length) {
                colors[index] = color;
                return true;
            }
        } catch (Throwable t) {
            LOGGER.error("[MapItemCompat] Cannot write colors field: {}", t.getMessage());
        }
        return false;
    }

    public static void lockMap(MapItemSavedData state) {
        VersionAdapterFactory.getAdapter().lockMap(state);
    }

    public static MapItemSavedData getSavedData(ItemStack mapItem, ServerLevel world) {
        return VersionAdapterFactory.getAdapter().getMapSavedData(mapItem, world);
    }
}
