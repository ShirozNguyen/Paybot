package com.paybot.telemetry;

import dev.faststats.bukkit.BukkitContext;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Quản trị vòng đời tích hợp FastStats Telemetry trên nền tảng Bukkit / Paper / Purpur / Folia.
 * Cơ chế Fail-Safe tuyệt đối: Lỗi telemetry KHÔNG BAO GIỜ làm crash hay gián đoạn PayBot.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm điều phối vòng đời telemetry Bukkit.
 */
public final class BukkitFastStatsIntegration {

    private static final AtomicReference<BukkitFastStatsIntegration> INSTANCE = new AtomicReference<>();

    private final Plugin plugin;
    private final FastStatsState state;
    private final BukkitContext context;
    private final BukkitSnapshotProvider snapshotProvider;

    private BukkitFastStatsIntegration(Plugin plugin, BukkitContext context, BukkitSnapshotProvider snapshotProvider, FastStatsState state) {
        this.plugin = plugin;
        this.context = context;
        this.snapshotProvider = snapshotProvider;
        this.state = state;
    }

    /**
     * Khởi tạo FastStats Telemetry cho Plugin Bukkit.
     */
    public static synchronized void initialize(Plugin plugin) {
        if (plugin == null) {
            return;
        }

        BukkitFastStatsIntegration existing = INSTANCE.get();
        if (existing != null && existing.state != FastStatsState.SHUTDOWN) {
            return; // Đã khởi tạo, không tạo lần hai
        }

        try {
            BukkitFastStatsConfigReader configReader = new BukkitFastStatsConfigReader(plugin);
            if (!configReader.isTelemetryEnabled()) {
                INSTANCE.set(new BukkitFastStatsIntegration(plugin, null, null, FastStatsState.DISABLED));
                plugin.getLogger().info("[FastStats] Telemetry bi tat boi cau hinh.");
                return;
            }

            String token = configReader.resolveToken();
            if (token == null || !FastStatsDataSanitizer.isValidToken(token)) {
                INSTANCE.set(new BukkitFastStatsIntegration(plugin, null, null, FastStatsState.DISABLED));
                plugin.getLogger().info("[FastStats] Khong tim thay token hop le, bo qua telemetry.");
                return;
            }

            BukkitSnapshotProvider snapshotProvider = new BukkitSnapshotProvider(plugin);
            BukkitFastStatsMetricsRegistry metricsRegistry = new BukkitFastStatsMetricsRegistry(snapshotProvider);

            BukkitContext context = new BukkitContext.Factory(plugin, token)
                    .metrics(factory -> metricsRegistry.registerMetrics(factory).create())
                    .create();

            context.ready();

            BukkitFastStatsIntegration integration = new BukkitFastStatsIntegration(
                    plugin,
                    context,
                    snapshotProvider,
                    FastStatsState.READY
            );
            INSTANCE.set(integration);
            plugin.getLogger().info("[FastStats] Telemetry khoi tao thanh cong.");

        } catch (Throwable t) {
            // BEST EFFORT: Tuyệt đối không ném ngoại lệ làm gián đoạn plugin PayBot
            INSTANCE.set(new BukkitFastStatsIntegration(plugin, null, null, FastStatsState.DISABLED));
            try {
                plugin.getLogger().warning("[FastStats] Khong the khoi tao telemetry (Best Effort): " + t.getMessage());
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Dừng telemetry và giải phóng tài nguyên khi plugin bị disable.
     */
    public static synchronized void shutdown() {
        BukkitFastStatsIntegration current = INSTANCE.getAndSet(null);
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
