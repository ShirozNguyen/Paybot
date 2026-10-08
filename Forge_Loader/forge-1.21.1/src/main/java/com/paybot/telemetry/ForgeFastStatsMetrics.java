package com.paybot.telemetry;

import com.google.gson.JsonObject;
import dev.faststats.Metrics;
import dev.faststats.SimpleContext;
import dev.faststats.SimpleMetrics;

/**
 * Custom SimpleMetrics implementation cho Minecraft Forge.
 * Kế thừa FastStats Core SimpleMetrics, tự động đính kèm metadata hệ thống chuẩn.
 * Tuân thủ Quy Tắc 10 (Forge Integration) và Quy Tắc 17 (Class đơn nhiệm).
 */
public final class ForgeFastStatsMetrics extends SimpleMetrics {

    private final ForgeSnapshotProvider snapshotProvider;

    public ForgeFastStatsMetrics(SimpleMetrics.Factory factory, ForgeSnapshotProvider snapshotProvider) {
        super(factory);
        this.snapshotProvider = snapshotProvider;
    }

    public static class Factory extends SimpleMetrics.Factory {
        private final ForgeSnapshotProvider snapshotProvider;

        public Factory(SimpleContext context, ForgeSnapshotProvider snapshotProvider) {
            super(context);
            this.snapshotProvider = snapshotProvider;
        }

        @Override
        public Metrics create() {
            return new ForgeFastStatsMetrics(this, snapshotProvider);
        }
    }

    @Override
    protected String serverType() {
        return "Forge";
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
        data.addProperty("server_type", "Forge");
        data.addProperty("online_mode", snapshot.isOnlineMode());
        data.addProperty("player_count", snapshot.getPlayerCount());
    }
}
