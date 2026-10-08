package com.paybot.telemetry;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Đọc cấu hình và token FastStats cho nền tảng Bukkit.
 * Tuân thủ Quy Tắc 8 (Không Hardcode) và Quy Tắc 17 (Class đơn nhiệm).
 */
public final class BukkitFastStatsConfigReader {

    private final Plugin plugin;

    public BukkitFastStatsConfigReader(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Lấy token FastStats từ cấu hình plugin hoặc file cấu hình chung của FastStats.
     * Trả về null nếu không cấu hình hoặc token không hợp lệ.
     */
    public String resolveToken() {
        try {
            // 1. Kiểm tra trong config.yml của plugin
            FileConfiguration config = plugin.getConfig();
            if (config != null) {
                if (!config.getBoolean("telemetry.enabled", true)) {
                    return null;
                }
                String cfgToken = config.getString("telemetry.faststats-token", "").trim();
                if (FastStatsDataSanitizer.isValidToken(cfgToken)) {
                    return cfgToken;
                }
            }

            // 2. Kiểm tra trong plugins/faststats/config.properties
            File faststatsDir = new File(plugin.getDataFolder().getParentFile(), "faststats");
            File propFile = new File(faststatsDir, "config.properties");
            if (propFile.exists() && propFile.isFile()) {
                Properties props = new Properties();
                try (InputStream in = new FileInputStream(propFile)) {
                    props.load(in);
                    String propToken = props.getProperty("token", "").trim();
                    if (FastStatsDataSanitizer.isValidToken(propToken)) {
                        return propToken;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Kiểm tra telemetry có được bật bởi quản trị viên hay không.
     */
    public boolean isTelemetryEnabled() {
        try {
            FileConfiguration config = plugin.getConfig();
            if (config != null) {
                return config.getBoolean("telemetry.enabled", true);
            }
        } catch (Throwable ignored) {
        }
        return true;
    }
}
