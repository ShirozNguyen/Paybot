package com.paybot.telemetry;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Trích xuất và cập nhật snapshot dữ liệu an toàn trên nền tảng Fabric & Quilt.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm phụ trách trích xuất snapshot dữ liệu Fabric/Quilt.
 */
public final class FabricSnapshotProvider {

    private final AtomicReference<FastStatsTelemetrySnapshot> currentSnapshot;

    public FabricSnapshotProvider() {
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
        try {
            mcVersion = FabricLoader.getInstance().getModContainer("minecraft")
                    .map(m -> m.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
        } catch (Throwable ignored) {
        }

        String modVersion = "unknown";
        try {
            modVersion = FabricLoader.getInstance().getModContainer("paybot")
                    .map(m -> m.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
        } catch (Throwable ignored) {
        }

        String serverType = detectLoaderType();

        return new FastStatsTelemetrySnapshot(
                players,
                onlineMode,
                mcVersion,
                modVersion,
                serverType,
                System.currentTimeMillis()
        );
    }

    /**
     * Nhận diện nền tảng: Quilt Loader (khi có quilt_loader) hoặc Fabric.
     */
    private String detectLoaderType() {
        try {
            if (FabricLoader.getInstance().isModLoaded("quilt_loader")) {
                return "Quilt";
            }
        } catch (Throwable ignored) {
        }
        return "Fabric";
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
