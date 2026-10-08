package com.paybot.telemetry;

import dev.faststats.Metrics;
import dev.faststats.data.Metric;

/**
 * Đăng ký các metrics PayBot an toàn vào FastStats NeoForge factory.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm đăng ký metrics.
 */
public final class NeoForgeFastStatsMetricsRegistry {

    private final NeoForgeSnapshotProvider snapshotProvider;

    public NeoForgeFastStatsMetricsRegistry(NeoForgeSnapshotProvider snapshotProvider) {
        this.snapshotProvider = snapshotProvider;
    }

    /**
     * Đăng ký các metrics phần mềm an toàn vào Metrics Factory.
     */
    public Metrics.Factory registerMetrics(Metrics.Factory factory) {
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
