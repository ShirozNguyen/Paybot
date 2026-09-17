// v5.5.5 Part 94: Pure Java self-JAR loader detection and loader-specific update checking
package com.naptien.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * ModrinthVersionFetcher — Thực hiện truy vấn HTTP lấy dữ liệu phiên bản từ Modrinth API bằng Java thuần.
 */
public class ModrinthVersionFetcher {

    private static final String MODRINTH_API_TEMPLATE =
            "https://api.modrinth.com/v2/project/%s/version?include_changelog=false";

    private static final int TIMEOUT_MS = 7000;

    /**
     * Gửi request lấy toàn bộ danh sách phiên bản của project từ Modrinth API dưới dạng chuỗi JSON thô.
     *
     * @param projectSlug Slug của project trên Modrinth (ví dụ: "paybot" hoặc "paybotmod")
     * @param userAgent   User-Agent header tuân thủ quy chuẩn Modrinth API
     * @return Chuỗi JSON trả về từ Modrinth, hoặc null nếu lỗi kết nối / không tìm thấy
     */
    public static String fetchVersionsJson(String projectSlug, String userAgent) {
        if (projectSlug == null || projectSlug.isEmpty()) {
            return null;
        }

        HttpURLConnection conn = null;
        try {
            String urlStr = String.format(MODRINTH_API_TEMPLATE, projectSlug);
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", userAgent != null ? userAgent : "PayBot-UpdateChecker/5.5.5");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoInput(true);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                return null;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            return sb.toString();

        } catch (Throwable t) {
            return null;
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
