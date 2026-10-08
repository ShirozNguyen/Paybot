package com.paybot.telemetry;

import com.paybot.PayBotMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Quản trị vòng đời tích hợp FastStats Telemetry trên Fabric Loader & Quilt Loader.
 * BẮT BUỘC: Fabric và Quilt dùng CHUNG 100% implementation này (FastStats Core + Config).
 * Cơ chế Fail-Safe tuyệt đối: Lỗi telemetry KHÔNG BAO GIỜ làm crash hay gián đoạn PayBot.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm điều phối vòng đời telemetry Fabric & Quilt.
 */
public final class FabricFastStatsIntegration {

    private static final AtomicReference<FabricFastStatsIntegration> INSTANCE = new AtomicReference<>();

    private final FastStatsState state;
    private final FabricFastStatsContext context;
    private final FabricSnapshotProvider snapshotProvider;

    private FabricFastStatsIntegration(FabricFastStatsContext context, FabricSnapshotProvider snapshotProvider, FastStatsState state) {
        this.context = context;
        this.snapshotProvider = snapshotProvider;
        this.state = state;
    }

    public static synchronized void initialize() {
        FabricFastStatsIntegration existing = INSTANCE.get();
        if (existing != null && existing.state != FastStatsState.SHUTDOWN) {
            return;
        }

        try {
            FabricFastStatsConfigReader configReader = new FabricFastStatsConfigReader();
            if (!configReader.isTelemetryEnabled()) {
                INSTANCE.set(new FabricFastStatsIntegration(null, null, FastStatsState.DISABLED));
                PayBotMod.LOGGER.info("[FastStats] Telemetry bi tat boi cau hinh.");
                return;
            }

            String token = configReader.resolveToken();
            if (token == null || !FastStatsDataSanitizer.isValidToken(token)) {
                INSTANCE.set(new FabricFastStatsIntegration(null, null, FastStatsState.DISABLED));
                PayBotMod.LOGGER.info("[FastStats] Khong tim thay token hop le, bo qua telemetry.");
                return;
            }

            FabricSnapshotProvider snapshotProvider = new FabricSnapshotProvider();

            FabricFastStatsContext context = new FabricFastStatsContext.Factory(
                    PayBotMod.MOD_ID,
                    token,
                    snapshotProvider
            ).create();

            FabricFastStatsIntegration integration = new FabricFastStatsIntegration(
                    context,
                    snapshotProvider,
                    FastStatsState.READY
            );
            INSTANCE.set(integration);

            try {
                ServerLifecycleEvents.SERVER_STARTED.register(FabricFastStatsIntegration::onServerStarted);
                ServerLifecycleEvents.SERVER_STOPPING.register(server -> onServerStopping());
            } catch (Throwable ignored) {
            }

            PayBotMod.LOGGER.info("[FastStats] Telemetry khoi tao thanh cong cho Fabric/Quilt.");

        } catch (Throwable t) {
            INSTANCE.set(new FabricFastStatsIntegration(null, null, FastStatsState.DISABLED));
            try {
                PayBotMod.LOGGER.warn("[FastStats] Khong the khoi tao telemetry (Best Effort): {}", t.getMessage());
            } catch (Throwable ignored) {
            }
        }
    }

    public static void onServerStarted(MinecraftServer server) {
        FabricFastStatsIntegration current = INSTANCE.get();
        if (current != null && current.context != null && current.snapshotProvider != null) {
            try {
                current.snapshotProvider.updateServer(server);
                current.context.ready();
            } catch (Throwable ignored) {
            }
        }
    }

    public static void onServerStopping() {
        shutdown();
    }

    public static synchronized void shutdown() {
        FabricFastStatsIntegration current = INSTANCE.getAndSet(null);
        if (current == null || current.context == null) {
            return;
        }

        try {
            current.context.shutdown();
        } catch (Throwable ignored) {
        }
    }

    public FastStatsState getState() {
        return state;
    }
}
