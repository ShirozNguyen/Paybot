package com.paybot.telemetry;

import com.paybot.PayBotMod;
import dev.faststats.Metrics;
import dev.faststats.fabric.FabricContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Quản trị vòng đời tích hợp FastStats Telemetry trên Fabric Loader & Quilt Loader.
 * BẮT BUỘC: Fabric và Quilt dùng CHUNG 100% implementation này.
 * Cơ chế Fail-Safe tuyệt đối: Lỗi telemetry KHÔNG BAO GIỜ làm crash hay gián đoạn PayBot.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm điều phối vòng đời telemetry Fabric & Quilt.
 */
public final class FabricFastStatsIntegration {

    private static final AtomicReference<FabricFastStatsIntegration> INSTANCE = new AtomicReference<>();

    private final FastStatsState state;
    private final FabricContext context;
    private final FabricSnapshotProvider snapshotProvider;

    private FabricFastStatsIntegration(FabricContext context, FabricSnapshotProvider snapshotProvider, FastStatsState state) {
        this.context = context;
        this.snapshotProvider = snapshotProvider;
        this.state = state;
    }

    /**
     * Khởi tạo FastStats Telemetry cho Fabric & Quilt.
     */
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
            FabricFastStatsMetricsRegistry metricsRegistry = new FabricFastStatsMetricsRegistry(snapshotProvider);

            FabricContext context = new FabricContext.Factory(PayBotMod.MOD_ID, token)
                    .metrics(factory -> metricsRegistry.registerMetrics(Metrics.Factory.create()))
                    .create();

            // Đăng ký cập nhật server snapshot khi server khởi động
            ServerLifecycleEvents.SERVER_STARTED.register(snapshotProvider::updateServer);

            FabricFastStatsIntegration integration = new FabricFastStatsIntegration(
                    context,
                    snapshotProvider,
                    FastStatsState.READY
            );
            INSTANCE.set(integration);
            PayBotMod.LOGGER.info("[FastStats] Telemetry khoi tao thanh cong cho Fabric/Quilt.");

        } catch (Throwable t) {
            // BEST EFFORT: Tuyệt đối không ném ngoại lệ làm gián đoạn mod PayBot
            INSTANCE.set(new FabricFastStatsIntegration(null, null, FastStatsState.DISABLED));
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
