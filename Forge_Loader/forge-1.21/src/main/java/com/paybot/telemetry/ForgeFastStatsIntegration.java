package com.paybot.telemetry;

import com.paybot.PayBotMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Quản trị vòng đời tích hợp FastStats Telemetry trên nền tảng Minecraft Forge.
 * Sử dụng Forge Custom Adapter (FastStats Core + Config).
 * Cơ chế Fail-Safe tuyệt đối: Lỗi telemetry KHÔNG BAO GIỜ làm crash hay gián đoạn PayBot.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm điều phối vòng đời telemetry Forge.
 */
public final class ForgeFastStatsIntegration {

    private static final AtomicReference<ForgeFastStatsIntegration> INSTANCE = new AtomicReference<>();

    private final FastStatsState state;
    private final ForgeFastStatsContext context;
    private final ForgeSnapshotProvider snapshotProvider;

    private ForgeFastStatsIntegration(ForgeFastStatsContext context, ForgeSnapshotProvider snapshotProvider, FastStatsState state) {
        this.context = context;
        this.snapshotProvider = snapshotProvider;
        this.state = state;
    }

    /**
     * Khởi tạo FastStats Telemetry cho Forge.
     */
    public static synchronized void initialize() {
        ForgeFastStatsIntegration existing = INSTANCE.get();
        if (existing != null && existing.state != FastStatsState.SHUTDOWN) {
            return;
        }

        try {
            ForgeFastStatsConfigReader configReader = new ForgeFastStatsConfigReader();
            if (!configReader.isTelemetryEnabled()) {
                INSTANCE.set(new ForgeFastStatsIntegration(null, null, FastStatsState.DISABLED));
                PayBotMod.LOGGER.info("[FastStats] Telemetry bi tat boi cau hinh.");
                return;
            }

            String token = configReader.resolveToken();
            if (token == null || !FastStatsDataSanitizer.isValidToken(token)) {
                INSTANCE.set(new ForgeFastStatsIntegration(null, null, FastStatsState.DISABLED));
                PayBotMod.LOGGER.info("[FastStats] Khong tim thay token hop le, bo qua telemetry.");
                return;
            }

            ForgeSnapshotProvider snapshotProvider = new ForgeSnapshotProvider();

            ForgeFastStatsContext context = new ForgeFastStatsContext.Factory(
                    PayBotMod.MOD_ID,
                    token,
                    snapshotProvider
            ).create();

            ForgeFastStatsIntegration integration = new ForgeFastStatsIntegration(
                    context,
                    snapshotProvider,
                    FastStatsState.READY
            );
            INSTANCE.set(integration);

            // Đăng ký lifecycle hooks trên Forge Event Bus
            MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
                try {
                    snapshotProvider.updateServer(event.getServer());
                    context.ready();
                } catch (Throwable ignored) {
                }
            });

            MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> {
                shutdown();
            });

            PayBotMod.LOGGER.info("[FastStats] Telemetry khoi tao thanh cong cho Forge.");

        } catch (Throwable t) {
            // BEST EFFORT: Tuyệt đối không ném ngoại lệ làm gián đoạn mod PayBot
            INSTANCE.set(new ForgeFastStatsIntegration(null, null, FastStatsState.DISABLED));
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
        ForgeFastStatsIntegration current = INSTANCE.getAndSet(null);
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
