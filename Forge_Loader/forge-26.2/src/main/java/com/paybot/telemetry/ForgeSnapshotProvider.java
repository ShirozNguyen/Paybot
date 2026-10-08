package com.paybot.telemetry;

import net.minecraft.server.MinecraftServer;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Trích xuất và cập nhật snapshot dữ liệu an toàn trên nền tảng Minecraft Forge.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm phụ trách trích xuất snapshot dữ liệu Forge.
 */
public final class ForgeSnapshotProvider {

    private final AtomicReference<FastStatsTelemetrySnapshot> currentSnapshot;

    public ForgeSnapshotProvider() {
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

        String modVersion = "6.0.0";
        try {
            Class<?> modListClass = Class.forName("net.minecraftforge.fml.ModList");
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
                "Forge",
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
