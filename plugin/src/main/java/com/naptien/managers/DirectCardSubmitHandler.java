package com.naptien.managers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.naptien.NapTienPlugin;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * DirectCardSubmitHandler (Plugin / ServerLoader) — Lớp chuyên biệt xử lý gửi thẻ cào trực tiếp tới các Web thứ 3.
 * Hỗ trợ tất cả các trang web gạch thẻ chiết khấu (gachthepro.com, card2k.net, thesieure.com, gachthefast.com, gachthe1s.com).
 *
 * Thực hiện quy trình retry linh hoạt (v5.5.5 — đổi từ 5×POST→1×GET sang 5×POST→5×GET theo yêu cầu):
 *   - Thử gửi 5 lần riêng biệt bằng phương thức POST, có backoff tăng dần (1s/2s/4s/8s/8s).
 *   - Nếu cả 5 lần POST đều không thành công (lỗi kết nối, HTTP error, timeout) -> Chuyển sang phương thức GET
 *     (URL query), cũng thử 5 lần với backoff tăng dần riêng.
 *   - Toàn bộ lỗi (trước đây bị nuốt im lặng qua catch(Throwable ignored)) giờ được log chi tiết khi
 *     config "debug-mode: true" — xem PayBotDebug.
 *
 * Bắt buộc tuân thủ Quy tắc 17: Tách biệt hoàn toàn xử lý nộp thẻ trực tiếp tới Web thứ 3.
 */
public class DirectCardSubmitHandler {

    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_ATTEMPTS_PER_METHOD = 5;
    /** Backoff (ms) theo thứ tự lần thử 1..5 — tăng dần rồi giữ trần ở 8s để không dồn quá lâu. */
    private static final int[] BACKOFF_MS = {1000, 2000, 4000, 8000, 8000};
    private static final Gson GSON = new Gson();

    private static final Map<String, String> SUPPORTED_SITES = new LinkedHashMap<>();
    static {
        SUPPORTED_SITES.put("thesieure.com",   "https://thesieure.com/chargingws/v2");
        SUPPORTED_SITES.put("gachthepro.com",  "https://gachthepro.com/chargingws/v2");
        SUPPORTED_SITES.put("gachthefast.com", "https://gachthefast.com/chargingws/v2");
        SUPPORTED_SITES.put("gachthe1s.com",   "https://gachthe1s.com/chargingws/v2");
        SUPPORTED_SITES.put("card2k.net",      "https://card2k.net/chargingws/v2");
    }

    private DirectCardSubmitHandler() {}

    public static Map<String, String> getSupportedSites() {
        return SUPPORTED_SITES;
    }

    /**
     * Tra cứu thông tin partner-id và partner-key linh hoạt từ config.
     */
    public static String[] resolveCredentials(NapTienPlugin plugin, String site) {
        String pid = plugin.getConfig().getString("card-api.partner-id", "").trim();
        String pkey = plugin.getConfig().getString("card-api.partner-key", "").trim();

        if (pid.isEmpty() || pkey.isEmpty()) {
            String domainKey = site.toLowerCase().replace(".com", "").replace(".net", "");
            pid = plugin.getConfig().getString("card-api-sites." + domainKey + ".com.partner-id",
                    plugin.getConfig().getString("card-api-sites." + domainKey + ".net.partner-id", "")).trim();
            pkey = plugin.getConfig().getString("card-api-sites." + domainKey + ".com.partner-key",
                    plugin.getConfig().getString("card-api-sites." + domainKey + ".net.partner-key", "")).trim();
        }

        return new String[]{pid, pkey};
    }

    /**
     * Gửi thẻ cào trực tiếp tới Web thứ 3 (5 lần POST -> 5 lần GET Fallback, có backoff).
     */
    public static JsonObject submitDirectly(NapTienPlugin plugin, String siteUrl, String partnerId, String partnerKey,
                                           String telco, int amount, String cardCode, String cardSerial, String requestId) {
        String sign = md5(partnerKey + cardCode + cardSerial);
        String ua = "PayBot-DirectSubmit/" + plugin.getDescription().getVersion();

        // 1. Thử 5 lần POST riêng biệt, có backoff
        String lastFailReason = "";
        for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_METHOD; attempt++) {
            plugin.getLogger().info("[DirectCardSubmit] Thử nộp thẻ lần " + attempt + "/" + MAX_ATTEMPTS_PER_METHOD
                    + " bằng phương thức POST tới " + siteUrl + "...");
            AttemptResult r = tryPostSubmit(plugin, siteUrl, partnerId, partnerKey, telco, amount, cardCode, cardSerial, requestId, sign, ua);
            if (r.body != null && r.body.has("status")) {
                plugin.getLogger().info("[DirectCardSubmit] 🟢 Gửi POST thành công ở lần thử " + attempt + "/"
                        + MAX_ATTEMPTS_PER_METHOD + "! Status: " + r.body.get("status").getAsString());
                return r.body;
            }
            lastFailReason = r.failReason;
            if (attempt < MAX_ATTEMPTS_PER_METHOD) sleepBackoff(attempt);
        }

        // 2. Cả 5 lần POST đều không thành công -> Chuyển sang phương thức GET, cũng thử 5 lần
        plugin.getLogger().warning("[DirectCardSubmit] ⚠️ Cả " + MAX_ATTEMPTS_PER_METHOD
                + " lần thử POST đều thất bại (lần cuối: " + lastFailReason + "). Chuyển sang phương thức GET...");
        for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_METHOD; attempt++) {
            plugin.getLogger().info("[DirectCardSubmit] Thử nộp thẻ lần " + attempt + "/" + MAX_ATTEMPTS_PER_METHOD
                    + " bằng phương thức GET tới " + siteUrl + "...");
            AttemptResult r = tryGetSubmit(plugin, siteUrl, partnerId, partnerKey, telco, amount, cardCode, cardSerial, requestId, sign, ua);
            if (r.body != null && r.body.has("status")) {
                plugin.getLogger().info("[DirectCardSubmit] 🟢 Gửi GET thành công ở lần thử " + attempt + "/"
                        + MAX_ATTEMPTS_PER_METHOD + "! Status: " + r.body.get("status").getAsString());
                return r.body;
            }
            lastFailReason = r.failReason;
            if (attempt < MAX_ATTEMPTS_PER_METHOD) sleepBackoff(attempt);
        }

        plugin.getLogger().severe("[DirectCardSubmit] ❌ Gửi thẻ thất bại hoàn toàn sau " + MAX_ATTEMPTS_PER_METHOD
                + " lần POST và " + MAX_ATTEMPTS_PER_METHOD + " lần GET tới " + siteUrl + ". Lỗi cuối cùng: " + lastFailReason
                + (com.naptien.utils.PayBotDebug.isEnabled() ? "" : " (bật \"debug-mode: true\" trong config.yml để xem chi tiết từng lần thử)"));
        return null;
    }

    private static void sleepBackoff(int attemptJustFinished) {
        int idx = Math.min(attemptJustFinished - 1, BACKOFF_MS.length - 1);
        try {
            Thread.sleep(BACKOFF_MS[idx]);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Kết quả 1 lần thử — body=null nếu thất bại, kèm lý do cụ thể để log/debug thay vì nuốt im lặng. */
    private static final class AttemptResult {
        final JsonObject body;
        final String failReason;
        AttemptResult(JsonObject body, String failReason) { this.body = body; this.failReason = failReason; }
    }

    private static AttemptResult tryPostSubmit(NapTienPlugin plugin, String siteUrl, String partnerId, String partnerKey,
                                             String telco, int amount, String cardCode, String cardSerial, String requestId,
                                             String sign, String userAgent) {
        try {
            URL url = URI.create(siteUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);

            String formBody = "request_id=" + URLEncoder.encode(requestId, StandardCharsets.UTF_8)
                    + "&code=" + URLEncoder.encode(cardCode, StandardCharsets.UTF_8)
                    + "&partner_id=" + URLEncoder.encode(partnerId, StandardCharsets.UTF_8)
                    + "&serial=" + URLEncoder.encode(cardSerial, StandardCharsets.UTF_8)
                    + "&telco=" + URLEncoder.encode(telco.toUpperCase(), StandardCharsets.UTF_8)
                    + "&amount=" + amount
                    + "&sign=" + URLEncoder.encode(sign, StandardCharsets.UTF_8)
                    + "&command=submit";

            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("User-Agent", userAgent);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(formBody.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    return new AttemptResult(GSON.fromJson(br, JsonObject.class), null);
                }
            }
            // v5.5.5: đọc body kể cả khi status lỗi — nhiều API (bao gồm các site thẻ cào này) trả
            // chi tiết lỗi trong body dù HTTP status != 200, trước đây bị bỏ qua hoàn toàn.
            String errBody = readErrorBodySafely(conn);
            String reason = "HTTP " + code + (errBody.isEmpty() ? "" : " — " + errBody);
            com.naptien.utils.PayBotDebug.logSwallowed(plugin, "DirectCardSubmit POST tới " + siteUrl + ": " + reason, null);
            return new AttemptResult(null, reason);
        } catch (Throwable t) {
            com.naptien.utils.PayBotDebug.logSwallowed(plugin, "DirectCardSubmit POST tới " + siteUrl + " ném exception", t);
            return new AttemptResult(null, t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static AttemptResult tryGetSubmit(NapTienPlugin plugin, String siteUrl, String partnerId, String partnerKey,
                                            String telco, int amount, String cardCode, String cardSerial, String requestId,
                                            String sign, String userAgent) {
        try {
            String queryUrl = siteUrl + "?request_id=" + URLEncoder.encode(requestId, StandardCharsets.UTF_8)
                    + "&code=" + URLEncoder.encode(cardCode, StandardCharsets.UTF_8)
                    + "&partner_id=" + URLEncoder.encode(partnerId, StandardCharsets.UTF_8)
                    + "&serial=" + URLEncoder.encode(cardSerial, StandardCharsets.UTF_8)
                    + "&telco=" + URLEncoder.encode(telco.toUpperCase(), StandardCharsets.UTF_8)
                    + "&amount=" + amount
                    + "&sign=" + URLEncoder.encode(sign, StandardCharsets.UTF_8)
                    + "&command=submit";

            URL url = URI.create(queryUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", userAgent);

            int code = conn.getResponseCode();
            if (code == 200) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    return new AttemptResult(GSON.fromJson(br, JsonObject.class), null);
                }
            }
            String errBody = readErrorBodySafely(conn);
            String reason = "HTTP " + code + (errBody.isEmpty() ? "" : " — " + errBody);
            com.naptien.utils.PayBotDebug.logSwallowed(plugin, "DirectCardSubmit GET tới " + siteUrl + ": " + reason, null);
            return new AttemptResult(null, reason);
        } catch (Throwable t) {
            com.naptien.utils.PayBotDebug.logSwallowed(plugin, "DirectCardSubmit GET tới " + siteUrl + " ném exception", t);
            return new AttemptResult(null, t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    /** Đọc error stream an toàn (không ném exception thêm) để đưa vào log debug-mode, giới hạn 500 ký tự tránh log quá to. */
    private static String readErrorBodySafely(HttpURLConnection conn) {
        try (java.io.InputStream es = conn.getErrorStream()) {
            if (es == null) return "";
            try (BufferedReader br = new BufferedReader(new InputStreamReader(es, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null && sb.length() < 500) sb.append(line);
                return sb.toString();
            }
        } catch (Throwable ignored) {
            return ""; // best-effort — không để lỗi đọc error-body làm rối thêm nguyên nhân lỗi chính
        }
    }

    public static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] array = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : array) {
                sb.append(Integer.toHexString((b & 0xFF) | 0x100), 1, 3);
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }
}
