package com.paybot.utils;

import net.minecraft.nbt.CompoundTag;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * TagCompatHelper — Class riêng biệt chuyên trách các thao tác đọc/ghi NBT CompoundTag an toàn đa phiên bản.
 * 
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn chức năng xử lý NBT tương thích giữa Minecraft cũ và Minecraft Modern (1.21.5+).
 */
public class TagCompatHelper {

    public static String getString(CompoundTag tag, String key) {
        if (tag == null || key == null) return "";
        try {
            Method m = tag.getClass().getMethod("getString", String.class);
            Object res = m.invoke(tag, key);
            if (res instanceof String) return (String) res;
            if (res instanceof Optional) {
                Optional<?> opt = (Optional<?>) res;
                return opt.isPresent() && opt.get() instanceof String ? (String) opt.get() : "";
            }
        } catch (Throwable ignored) {}
        return "";
    }

    public static CompoundTag getCompound(CompoundTag tag, String key) {
        if (tag == null || key == null) return new CompoundTag();
        try {
            Method m = tag.getClass().getMethod("getCompound", String.class);
            Object res = m.invoke(tag, key);
            if (res instanceof CompoundTag) return (CompoundTag) res;
            if (res instanceof Optional) {
                Optional<?> opt = (Optional<?>) res;
                if (opt.isPresent() && opt.get() instanceof CompoundTag) {
                    return (CompoundTag) opt.get();
                }
            }
        } catch (Throwable ignored) {}
        return new CompoundTag();
    }

    public static boolean contains(CompoundTag tag, String key) {
        if (tag == null || key == null) return false;
        try {
            Method m = tag.getClass().getMethod("contains", String.class);
            return (Boolean) m.invoke(tag, key);
        } catch (Throwable t) {
            try {
                Method m2 = tag.getClass().getMethod("contains", String.class, int.class);
                return (Boolean) m2.invoke(tag, key, 10);
            } catch (Throwable ignored) {}
        }
        return false;
    }
}
