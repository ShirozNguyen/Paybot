package com.paybot.telemetry;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Trích xuất và cập nhật snapshot dữ liệu an toàn trên nền tảng Bukkit / Paper / Purpur / Folia.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm phụ trách trích xuất snapshot dữ liệu Bukkit.
 */
public final class BukkitSnapshotProvider {

    private final Plugin plugin;
    private final AtomicReference<FastStatsTelemetrySnapshot> currentSnapshot;

    public BukkitSnapshotProvider(Plugin plugin) {
        this.plugin = plugin;
        this.currentSnapshot = new AtomicReference<>(captureSnapshot());
    }

    /**
     * Chụp snapshot dữ liệu từ trạng thái hiện tại của máy chủ.
     * Đảm bảo an toàn không gọi mutable API từ thread lạ.
     */
    public FastStatsTelemetrySnapshot captureSnapshot() {
        int players = 0;
        try {
            players = Bukkit.getOnlinePlayers().size();
        } catch (Throwable ignored) {
            // Trường hợp chạy sớm trước khi server sẵn sàng hoặc trên Folia thread isolation
        }

        boolean onlineMode = false;
        try {
            onlineMode = Bukkit.getOnlineMode();
        } catch (Throwable ignored) {
        }

        String mcVersion = "unknown";
        try {
            mcVersion = Bukkit.getBukkitVersion();
        } catch (Throwable ignored) {
        }

        String pluginVersion = "unknown";
        try {
            pluginVersion = plugin.getDescription().getVersion();
        } catch (Throwable ignored) {
        }

        String serverType = detectServerBrand();

        FastStatsTelemetrySnapshot snapshot = new FastStatsTelemetrySnapshot(
                players,
                onlineMode,
                mcVersion,
                pluginVersion,
                serverType,
                System.currentTimeMillis()
        );

        return snapshot;
    }

    /**
     * Nhận diện thương hiệu máy chủ: Folia, Purpur, Paper hoặc Spigot/Bukkit.
     */
    private String detectServerBrand() {
        try {
            String name = Bukkit.getName();
            if (name != null) {
                return name;
            }
        } catch (Throwable ignored) {
        }
        return "Bukkit";
    }

    public FastStatsTelemetrySnapshot getSnapshot() {
        FastStatsTelemetrySnapshot snap = currentSnapshot.get();
        if (snap == null) {
            snap = captureSnapshot();
            currentSnapshot.set(snap);
        }
        return snap;
    }

    public void refresh() {
        currentSnapshot.set(captureSnapshot());
    }
}
