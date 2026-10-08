package com.paybot.telemetry;

import com.google.gson.JsonObject;
import dev.faststats.Metrics;
import dev.faststats.SimpleContext;
import dev.faststats.SimpleMetrics;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Custom SimpleMetrics implementation cho Fabric Loader & Quilt Loader.
 * Kế thừa FastStats Core SimpleMetrics, tự động đính kèm metadata hệ thống chuẩn.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm thu thập metrics Fabric & Quilt.
 */
public final class FabricFastStatsMetrics extends SimpleMetrics {

    private final FabricSnapshotProvider snapshotProvider;

    public FabricFastStatsMetrics(SimpleMetrics.Factory factory, FabricSnapshotProvider snapshotProvider) {
        super(factory);
        this.snapshotProvider = snapshotProvider;
    }

    public static class Factory extends SimpleMetrics.Factory {
        private final FabricSnapshotProvider snapshotProvider;

        public Factory(SimpleContext context, FabricSnapshotProvider snapshotProvider) {
            super(context);
            this.snapshotProvider = snapshotProvider;
        }

        @Override
        public Metrics create() {
            return new FabricFastStatsMetrics(this, snapshotProvider);
        }
    }

    @Override
    protected String serverType() {
        return FabricLoader.getInstance().isModLoaded("quilt_loader") ? "Quilt" : "Fabric";
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
        data.addProperty("server_type", serverType());
        data.addProperty("online_mode", snapshot.isOnlineMode());
        data.addProperty("player_count", snapshot.getPlayerCount());
    }
}
