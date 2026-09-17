package com.naptien.compat;

import net.minecraftforge.fml.ModList;
import net.minecraft.resources.ResourceLocation;

public final class McVersionHelper {

    public static final String GROUP_1_20 = "1.20.x";
    public static final String GROUP_1_21 = "1.21.x";
    public static final String GROUP_26X  = "26.x+";

    private static final String DETECTED_VERSION = detectRawVersion();

    private McVersionHelper() {}

    public static String getMinecraftVersion() {
        return DETECTED_VERSION;
    }

    public static String getVersionGroup() {
        if (DETECTED_VERSION.startsWith("1.20")) return GROUP_1_20;
        if (DETECTED_VERSION.startsWith("1.21")) return GROUP_1_21;
        return GROUP_26X;
    }

    public static boolean isAtLeast(String targetVersion) {
        return compareVersions(DETECTED_VERSION, targetVersion) >= 0;
    }

    public static boolean is1_20() { return getVersionGroup().equals(GROUP_1_20); }
    public static boolean is1_21() { return getVersionGroup().equals(GROUP_1_21); }

    public static ResourceLocation id(String namespace, String path) {
        try {
            java.lang.reflect.Method m = ResourceLocation.class.getMethod("fromNamespaceAndPath", String.class, String.class);
            return (ResourceLocation) m.invoke(null, namespace, path);
        } catch (Throwable ignored) {
            return new ResourceLocation(namespace, path);
        }
    }

    public static ResourceLocation paybot(String path) { return id("paybot", path); }
    public static ResourceLocation mc(String path)     { return id("minecraft", path); }

    private static String detectRawVersion() {
        try {
            var mc = ModList.get().getModContainerById("minecraft");
            if (mc.isPresent())
                return mc.get().getModInfo().getVersion().toString();
        } catch (Exception ignored) {}
        return "1.20.1";
    }

    private static int compareVersions(String v1, String v2) {
        String[] a1 = v1.split("\\.");
        String[] a2 = v2.split("\\.");
        int len = Math.max(a1.length, a2.length);
        for (int i = 0; i < len; i++) {
            int n1 = i < a1.length ? parseOrZero(a1[i]) : 0;
            int n2 = i < a2.length ? parseOrZero(a2[i]) : 0;
            if (n1 != n2) return Integer.compare(n1, n2);
        }
        return 0;
    }

    private static int parseOrZero(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; }
    }
}
