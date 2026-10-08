package com.paybot.telemetry;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Đọc cấu hình và token FastStats cho nền tảng Minecraft Forge.
 * Tuân thủ Quy Tắc 8 (Không Hardcode) và Quy Tắc 17 (Class đơn nhiệm).
 */
public final class ForgeFastStatsConfigReader {

    public ForgeFastStatsConfigReader() {}

    /**
     * Lấy token FastStats từ cấu hình game config directory.
     * Trả về null nếu không cấu hình hoặc token không hợp lệ.
     */
    public String resolveToken() {
        try {
            Path configDir = FMLPaths.CONFIGDIR.get();

            // 1. Kiểm tra config/faststats/config.properties
            File faststatsProp = configDir.resolve("faststats").resolve("config.properties").toFile();
            if (faststatsProp.exists() && faststatsProp.isFile()) {
                Properties props = new Properties();
                try (InputStream in = new FileInputStream(faststatsProp)) {
                    props.load(in);
                    String token = props.getProperty("token", "").trim();
                    if (FastStatsDataSanitizer.isValidToken(token)) {
                        return token;
                    }
                }
            }

            // 2. Kiểm tra config/paybot/config.properties
            File paybotProp = configDir.resolve("paybot").resolve("config.properties").toFile();
            if (paybotProp.exists() && paybotProp.isFile()) {
                Properties props = new Properties();
                try (InputStream in = new FileInputStream(paybotProp)) {
                    props.load(in);
                    String token = props.getProperty("faststats.token", "").trim();
                    if (FastStatsDataSanitizer.isValidToken(token)) {
                        return token;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Kiểm tra telemetry có được bật hay không.
     */
    public boolean isTelemetryEnabled() {
        try {
            Path configDir = FMLPaths.CONFIGDIR.get();
            File faststatsProp = configDir.resolve("faststats").resolve("config.properties").toFile();
            if (faststatsProp.exists() && faststatsProp.isFile()) {
                Properties props = new Properties();
                try (InputStream in = new FileInputStream(faststatsProp)) {
                    props.load(in);
                    String enabled = props.getProperty("enabled", "true").trim();
                    return !"false".equalsIgnoreCase(enabled);
                }
            }
        } catch (Throwable ignored) {
        }
        return true;
    }
}
