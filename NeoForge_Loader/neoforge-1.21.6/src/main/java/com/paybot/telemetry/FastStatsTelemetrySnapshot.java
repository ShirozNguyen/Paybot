package com.paybot.telemetry;

/**
 * Snapshot dữ liệu bất biến (immutable) phục vụ FastStats Telemetry trên NeoForge.
 * Đảm bảo O(1) thread-safe read, không truy cập trực tiếp Minecraft state từ async thread.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm biểu diễn dữ liệu an toàn.
 */
public final class FastStatsTelemetrySnapshot {

    private final int playerCount;
    private final boolean onlineMode;
    private final String minecraftVersion;
    private final String pluginVersion;
    private final String serverType;
    private final long timestampMillis;

    public FastStatsTelemetrySnapshot(int playerCount, boolean onlineMode, String minecraftVersion,
                                      String pluginVersion, String serverType, long timestampMillis) {
        this.playerCount = Math.max(0, playerCount);
        this.onlineMode = onlineMode;
        this.minecraftVersion = minecraftVersion != null ? minecraftVersion : "unknown";
        this.pluginVersion = pluginVersion != null ? pluginVersion : "unknown";
        this.serverType = serverType != null ? serverType : "unknown";
        this.timestampMillis = timestampMillis;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public boolean isOnlineMode() {
        return onlineMode;
    }

    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    public String getPluginVersion() {
        return pluginVersion;
    }

    public String getServerType() {
        return serverType;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    @Override
    public String toString() {
        return "FastStatsTelemetrySnapshot{" +
                "playerCount=" + playerCount +
                ", onlineMode=" + onlineMode +
                ", minecraftVersion='" + minecraftVersion + '\'' +
                ", pluginVersion='" + pluginVersion + '\'' +
                ", serverType='" + serverType + '\'' +
                ", timestampMillis=" + timestampMillis +
                '}';
    }
}
