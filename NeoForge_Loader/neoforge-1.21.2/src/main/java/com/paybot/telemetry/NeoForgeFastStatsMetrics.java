package com.paybot.telemetry;

import com.google.gson.JsonObject;
import dev.faststats.Metrics;
import dev.faststats.SimpleContext;
import dev.faststats.SimpleMetrics;

/**
 * Custom SimpleMetrics implementation cho NeoForge.
 * Kế thừa FastStats Core SimpleMetrics, tự động đính kèm metadata hệ thống chuẩn.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm thu thập metrics NeoForge.
 */
public final class NeoForgeFastStatsMetrics extends SimpleMetrics {

    private final NeoForgeSnapshotProvider snapshotProvider;

    public NeoForgeFastStatsMetrics(SimpleMetrics.Factory factory, NeoForgeSnapshotProvider snapshotProvider) {
        super(factory);
        this.snapshotProvider = snapshotProvider;
    }

    public static class Factory extends SimpleMetrics.Factory {
        private final NeoForgeSnapshotProvider snapshotProvider;

        public Factory(SimpleContext context, NeoForgeSnapshotProvider snapshotProvider) {
            super(context);
            this.snapshotProvider = snapshotProvider;
        }

        @Override
        public Metrics create() {
            return new NeoForgeFastStatsMetrics(this, snapshotProvider);
        }
    }

    @Override
    protected String serverType() {
        return "NeoForge";
    }

    @Override
    protected void appendDefaultData(JsonObject data) {
        if (data == null) {
            return;
        }

        FastStatsTelemetrySnapshot snapshot = snapshotProvider.getSnapshot();

        data.addProperty("game_version", snapshot.getMinecraftVersion());
        data.addProperty("plugin_version", snapshot.getPluginVersion());
        data.addProperty("platform_version", snapshot.getMinecraftVersion());
        data.addProperty("server_type", "NeoForge");
        data.addProperty("online_mode", snapshot.isOnlineMode());
        data.addProperty("player_count", snapshot.getPlayerCount());
    }
}
