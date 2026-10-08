package com.paybot.telemetry;

import dev.faststats.bukkit.BukkitMetrics;
import dev.faststats.data.Metric;

/**
 * Đăng ký các metrics PayBot an toàn vào FastStats Bukkit factory.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm đăng ký metrics.
 */
public final class BukkitFastStatsMetricsRegistry {

    private final BukkitSnapshotProvider snapshotProvider;

    public BukkitFastStatsMetricsRegistry(BukkitSnapshotProvider snapshotProvider) {
        this.snapshotProvider = snapshotProvider;
    }

    /**
     * Đăng ký các metrics phần mềm an toàn vào Metrics Factory.
     */
    public BukkitMetrics.Factory registerMetrics(BukkitMetrics.Factory factory) {
        if (factory == null) {
            return null;
        }

        try {
            factory.addMetric(Metric.string("paybot_version", () ->
                    snapshotProvider.getSnapshot().getPluginVersion()
            ));

            factory.addMetric(Metric.string("server_brand", () ->
                    snapshotProvider.getSnapshot().getServerType()
            ));
        } catch (Throwable ignored) {
        }

        return factory;
    }
}
