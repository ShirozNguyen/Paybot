package com.naptien.managers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * PublicIpResolver — xác định IP public THẬT của server (IP mà player dùng để join được),
 * KHÔNG dùng {@code Bukkit.getServer().getIp()} vì đó chỉ là bind-address cấu hình trong
 * server.properties (key "server-ip") — admin hầu như luôn để trống → Bukkit trả về
 * "" hoặc "0.0.0.0", không phải IP public thật. Đây chính là lý do report-server-ip
 * trước đây luôn hiện "0.0.0.0:port" (vô dụng, không dùng để block được).
 * <p>
 * Cách lấy IP THẬT đáng tin cậy nhất: tự hỏi 1 dịch vụ "what is my IP" bên ngoài
 * (outbound luôn hoạt động dù server ở sau NAT/firewall, không cần mở port gì thêm) —
 * giống cách rất nhiều tool mạng khác làm (curl ifconfig.me,...).
 * <p>
 * Có 3 provider dự phòng (thử lần lượt, timeout ngắn 2.5s/provider) để tránh phụ thuộc
 * 1 dịch vụ duy nhất bị sập/chặn.
 *
 * Changelog:
 *   v5.0.0 — Thêm mới (fix báo cáo IP "0.0.0.0:port" không dùng được để block server spam).
 */
public final class PublicIpResolver {

    private PublicIpResolver() {}

    private static final String[] PROVIDERS = {
            "https://api.ipify.org",
            "https://checkip.amazonaws.com",
            "https://icanhazip.com",
    };

    private static final int TIMEOUT_MS = 2_500;

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}" +
            "(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");

    /**
     * Trả về IP public thật (vd "203.0.113.45"), hoặc {@code null} nếu cả 3 provider
     * đều thất bại (mất mạng / bị chặn outbound — hiếm). PHẢI gọi từ thread async/khác
     * main thread (đây là HTTP call có thể block tới ~2.5s mỗi lần thử).
     */
    public static String resolve() {
        for (String provider : PROVIDERS) {
            try {
                String ip = fetch(provider);
                if (ip != null && IPV4_PATTERN.matcher(ip).matches()) return ip;
            } catch (Exception ignored) {
                // thử provider tiếp theo
            }
        }
        return null;
    }

    private static String fetch(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "PayBot-Plugin");
        if (conn.getResponseCode() != 200) return null;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line = br.readLine();
            return line == null ? null : line.trim();
        }
    }
}
