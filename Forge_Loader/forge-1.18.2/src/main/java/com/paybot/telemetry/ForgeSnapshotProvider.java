package com.paybot.telemetry;

import com.paybot.PayBotMod;
import net.minecraft.server.MinecraftServer;

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
            modVersion = net.minecraftforge.fml.ModList.get().getModContainerById("paybot")
                    .map(m -> m.getModInfo().getVersion().toString())
                    .orElse("6.0.0");
        } catch (Throwable ignored) {
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
