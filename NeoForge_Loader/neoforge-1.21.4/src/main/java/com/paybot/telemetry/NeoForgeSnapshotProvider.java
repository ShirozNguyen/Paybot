package com.paybot.telemetry;

import net.minecraft.server.MinecraftServer;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Trích xuất và cập nhật snapshot dữ liệu an toàn trên nền tảng NeoForge.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm phụ trách trích xuất snapshot dữ liệu NeoForge.
 */
public final class NeoForgeSnapshotProvider {

    private final AtomicReference<FastStatsTelemetrySnapshot> currentSnapshot;

    public NeoForgeSnapshotProvider() {
        this.currentSnapshot = new AtomicReference<>(captureSnapshot(null));
    }

    /**
     * Chụp snapshot dữ liệu từ trạng thái hiện tại của máy chủ.
     */
    public FastStatsTelemetrySnapshot captureSnapshot(MinecraftServer server) {
        int players = 0;
        boolean onlineMode = false;

        if (server != null) {
            try {
                players = server.getPlayerCount();
            } catch (Throwable ignored) {
            }
            try {
                onlineMode = server.usesAuthentication();
            } catch (Throwable ignored) {
            }
        }

        String mcVersion = "unknown";
        if (server != null) {
            try {
                mcVersion = server.getServerVersion();
            } catch (Throwable ignored) {
            }
        }
        if ("unknown".equals(mcVersion)) {
            try {
                Class<?> fmlLoaderClass = Class.forName("net.neoforged.fml.loading.FMLLoader");
                Object versionInfo = fmlLoaderClass.getMethod("versionInfo").invoke(null);
                Object mcVer = versionInfo.getClass().getMethod("mcVersion").invoke(versionInfo);
                if (mcVer != null) {
                    mcVersion = mcVer.toString();
                }
            } catch (Throwable ignored) {
            }
        }

        String modVersion = "6.0.0";
        try {
            Class<?> modListClass = Class.forName("net.neoforged.fml.ModList");
            Object modList = modListClass.getMethod("get").invoke(null);
            Object optContainer = modListClass.getMethod("getModContainerById", String.class).invoke(modList, "paybot");
            if (optContainer instanceof Optional<?> opt && opt.isPresent()) {
                Object container = opt.get();
                Object modInfo = container.getClass().getMethod("getModInfo").invoke(container);
                Object versionObj = modInfo.getClass().getMethod("getVersion").invoke(modInfo);
                if (versionObj != null) {
                    modVersion = versionObj.toString();
                }
            }
        } catch (Throwable ignored) {
            modVersion = "6.0.0";
        }

        return new FastStatsTelemetrySnapshot(
                players,
                onlineMode,
                mcVersion,
                modVersion,
                "NeoForge",
                System.currentTimeMillis()
        );
    }

    public FastStatsTelemetrySnapshot getSnapshot() {
        FastStatsTelemetrySnapshot snap = currentSnapshot.get();
        if (snap == null) {
            snap = captureSnapshot(null);
            currentSnapshot.set(snap);
        }
        return snap;
    }

    public void updateServer(MinecraftServer server) {
        currentSnapshot.set(captureSnapshot(server));
    }
}
