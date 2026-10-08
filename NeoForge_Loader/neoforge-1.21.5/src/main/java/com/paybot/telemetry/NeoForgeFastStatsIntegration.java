package com.paybot.telemetry;

import com.paybot.PayBotMod;
import dev.faststats.Metrics;
import dev.faststats.neoforge.NeoForgeContext;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Quản trị vòng đời tích hợp FastStats Telemetry trên nền tảng NeoForge.
 * Cơ chế Fail-Safe tuyệt đối: Lỗi telemetry KHÔNG BAO GIỜ làm crash hay gián đoạn PayBot.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm điều phối vòng đời telemetry NeoForge.
 */
public final class NeoForgeFastStatsIntegration {

    private static final AtomicReference<NeoForgeFastStatsIntegration> INSTANCE = new AtomicReference<>();

    private final FastStatsState state;
    private final NeoForgeContext context;
    private final NeoForgeSnapshotProvider snapshotProvider;

    private NeoForgeFastStatsIntegration(NeoForgeContext context, NeoForgeSnapshotProvider snapshotProvider, FastStatsState state) {
        this.context = context;
        this.snapshotProvider = snapshotProvider;
        this.state = state;
    }

    /**
     * Khởi tạo FastStats Telemetry cho NeoForge.
     */
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
            NeoForgeFastStatsMetricsRegistry metricsRegistry = new NeoForgeFastStatsMetricsRegistry(snapshotProvider);

            NeoForgeContext context = new NeoForgeContext.Factory(PayBotMod.MOD_ID, token)
                    .metrics(factory -> metricsRegistry.registerMetrics(factory).create())
                    .create();

            NeoForgeFastStatsIntegration integration = new NeoForgeFastStatsIntegration(
                    context,
                    snapshotProvider,
                    FastStatsState.READY
            );
            INSTANCE.set(integration);

            // Đăng ký lifecycle hooks trên NeoForge Event Bus
            NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
                snapshotProvider.updateServer(event.getServer());
            });

            PayBotMod.LOGGER.info("[FastStats] Telemetry khoi tao thanh cong cho NeoForge.");

        } catch (Throwable t) {
            // BEST EFFORT: Tuyệt đối không ném ngoại lệ làm gián đoạn mod PayBot
            INSTANCE.set(new NeoForgeFastStatsIntegration(null, null, FastStatsState.DISABLED));
            try {
                PayBotMod.LOGGER.warn("[FastStats] Khong the khoi tao telemetry (Best Effort): {}", t.getMessage());
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Dừng telemetry và giải phóng tài nguyên khi server dừng.
     */
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
