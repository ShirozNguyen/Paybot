package com.naptien.utils;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinecraftVersionDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-VersionDetector");

    private static String rawVersion = null;
    private static int majorVersion = 1;
    private static int minorVersion = 20;
    private static int patchVersion = 1;
    private static boolean dataComponentsSupported = false;
    private static boolean legacyNbtSupported = false;
    private static boolean initialized = false;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        rawVersion = detectRawVersion();
        parseVersionString(rawVersion);
        detectCapabilities();

        LOGGER.info("[PayBot] Phát hiện môi trường Fabric runtime: Minecraft {} (Loader: {})",
                rawVersion, getLoaderName());
        LOGGER.info("[PayBot] Khả năng tương thích: DataComponents Era = {}, Legacy NBT Era = {}",
                dataComponentsSupported, legacyNbtSupported);
    }

    private static String detectRawVersion() {
        try {
            String v = SharedConstants.getCurrentVersion().getName();
            if (v != null && !v.isEmpty()) {
                return v;
            }
        } catch (Throwable ignored) {}

        try {
            var mcMod = FabricLoader.getInstance().getModContainer("minecraft");
            if (mcMod.isPresent()) {
                return mcMod.get().getMetadata().getVersion().getFriendlyString();
            }
        } catch (Throwable ignored) {}

        return "1.20.1";
    }

    private static void parseVersionString(String ver) {
        try {
            String cleaned = ver.replaceAll("[^0-9.]", "");
            String[] parts = cleaned.split("\\.");
            if (parts.length >= 1) majorVersion = Integer.parseInt(parts[0]);
            if (parts.length >= 2) minorVersion = Integer.parseInt(parts[1]);
            if (parts.length >= 3) patchVersion = Integer.parseInt(parts[2]);
        } catch (Throwable ignored) {
            majorVersion = 1;
            minorVersion = 20;
            patchVersion = 1;
        }
    }

    private static void detectCapabilities() {
        // Kiểm tra sự tồn tại của class DataComponentTypes (MC >= 1.20.5)
        try {
            Class.forName("net.minecraft.core.component.DataComponentTypes");
            dataComponentsSupported = true;
        } catch (Throwable ignored) {
            dataComponentsSupported = false;
        }

        // Kiểm tra xem MC >= 1.20.5 dựa vào version number
        if (minorVersion > 20 || (minorVersion == 20 && patchVersion >= 5)) {
            dataComponentsSupported = true;
        }

        // Kiểm tra sự tồn tại của method getOrCreateTag trên ItemStack (MC <= 1.20.4)
        try {
            boolean hasGetOrCreateTag = false;
            for (var m : net.minecraft.world.item.ItemStack.class.getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType().equals(net.minecraft.nbt.CompoundTag.class)) {
                    hasGetOrCreateTag = true;
                    break;
                }
            }
            legacyNbtSupported = hasGetOrCreateTag;
        } catch (Throwable ignored) {
            legacyNbtSupported = !dataComponentsSupported;
        }
    }

    public static String getRawVersion() {
        init();
        return rawVersion;
    }

    public static int getMinorVersion() {
        init();
        return minorVersion;
    }

    public static int getPatchVersion() {
        init();
        return patchVersion;
    }

    public static boolean isDataComponentsEra() {
        init();
        return dataComponentsSupported;
    }

    public static boolean isLegacyNbtEra() {
        init();
        return legacyNbtSupported;
    }

    public static String getLoaderName() {
        return "Fabric/Quilt";
    }
}
