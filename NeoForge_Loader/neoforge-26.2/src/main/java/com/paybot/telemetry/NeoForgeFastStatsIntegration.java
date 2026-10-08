package com.paybot.telemetry;

import com.paybot.PayBotMod;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Quản trị vòng đời tích hợp FastStats Telemetry trên nền tảng NeoForge.
 * Sử dụng NeoForge Custom Adapter (FastStats Core + Config).
 * Cơ chế Fail-Safe tuyệt đối: Lỗi telemetry KHÔNG BAO GIỜ làm crash hay gián đoạn PayBot.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm điều phối vòng đời telemetry NeoForge.
 */
public final class NeoForgeFastStatsIntegration {

    private static final AtomicReference<NeoForgeFastStatsIntegration> INSTANCE = new AtomicReference<>();

    private final FastStatsState state;
    private final NeoForgeFastStatsContext context;
    private final NeoForgeSnapshotProvider snapshotProvider;

    private NeoForgeFastStatsIntegration(NeoForgeFastStatsContext context, NeoForgeSnapshotProvider snapshotProvider, FastStatsState state) {
        this.context = context;
        this.snapshotProvider = snapshotProvider;
        this.state = state;
    }

    public static synchronized void initialize() {
        NeoForgeFastStatsIntegration existing = INSTANCE.get();
        if (existing != null && existing.state != FastStatsState.SHUTDOWN) {
            return;
        }

        try {
            NeoForgeFastStatsConfigReader configReader = new NeoForgeFastStatsConfigReader();
            if (!configReader.isTelemetryEnabled()) {
                INSTANCE.set(new NeoForgeFastStatsIntegration(null, null, FastStatsState.DISABLED));
                PayBotMod.LOGGER.info("[FastStats] Telemetry bi tat boi cau hinh.");
                return;
            }

            String token = configReader.resolveToken();
            if (token == null || !FastStatsDataSanitizer.isValidToken(token)) {
                INSTANCE.set(new NeoForgeFastStatsIntegration(null, null, FastStatsState.DISABLED));
                PayBotMod.LOGGER.info("[FastStats] Khong tim thay token hop le, bo qua telemetry.");
                return;
            }

            NeoForgeSnapshotProvider snapshotProvider = new NeoForgeSnapshotProvider();

            NeoForgeFastStatsContext context = new NeoForgeFastStatsContext.Factory(
                    PayBotMod.MOD_ID,
                    token,
                    snapshotProvider
            ).create();

            NeoForgeFastStatsIntegration integration = new NeoForgeFastStatsIntegration(
                    context,
                    snapshotProvider,
                    FastStatsState.READY
            );
            INSTANCE.set(integration);

            PayBotMod.LOGGER.info("[FastStats] Telemetry khoi tao thanh cong cho NeoForge.");

        } catch (Throwable t) {
            INSTANCE.set(new NeoForgeFastStatsIntegration(null, null, FastStatsState.DISABLED));
            try {
                PayBotMod.LOGGER.warn("[FastStats] Khong the khoi tao telemetry (Best Effort): {}", t.getMessage());
            } catch (Throwable ignored) {
            }
        }
    }

    public static void onServerStarted(MinecraftServer server) {
        NeoForgeFastStatsIntegration current = INSTANCE.get();
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
        NeoForgeFastStatsIntegration current = INSTANCE.getAndSet(null);
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
