package com.naptien.managers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.naptien.PayBotMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

/**
 * DirectCardSubmitHandler (Common / ModLoader) — Lớp chuyên biệt xử lý gửi thẻ cào trực tiếp tới các Web thứ 3.
 * Hỗ trợ tất cả các trang web gạch thẻ chiết khấu (gachthepro.com, card2k.net, thesieure.com, gachthefast.com, gachthe1s.com).
 * 
 * Thực hiện quy trình retry linh hoạt:
 *   - Thử gửi 5 lần riêng biệt bằng phương thức POST.
 *   - Nếu cả 5 lần POST đều không thành công (lỗi kết nối, HTTP error, timeout) -> Chuyển sang phương thức GET (URL query).
 * 
 * Bắt buộc tuân thủ Quy tắc 17: Tách biệt hoàn toàn xử lý nộp thẻ trực tiếp tới Web thứ 3.
 */
public class DirectCardSubmitHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-DirectCardSubmit");
    private static final int TIMEOUT_MS = 10_000;
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
    public static String[] resolveCredentials(PayBotMod mod, String site) {
        String pid = mod.getConfig().getString("card-api.partner-id", "").trim();
        String pkey = mod.getConfig().getString("card-api.partner-key", "").trim();

        if (pid.isEmpty() || pkey.isEmpty()) {
            String domainKey = site.toLowerCase().replace(".com", "").replace(".net", "");
            pid = mod.getConfig().getString("card-api-sites." + domainKey + ".com.partner-id",
                    mod.getConfig().getString("card-api-sites." + domainKey + ".net.partner-id", "")).trim();
            pkey = mod.getConfig().getString("card-api-sites." + domainKey + ".com.partner-key",
                    mod.getConfig().getString("card-api-sites." + domainKey + ".net.partner-key", "")).trim();
        }

        return new String[]{pid, pkey};
    }

    /**
     * Gửi thẻ cào trực tiếp tới Web thứ 3 (5 lần POST -> 5 lần GET Fallback).
     * 
     * @return JsonObject chứa response kết quả từ Web thứ 3 (hoặc null nếu thất bại toàn bộ)
     */
    public static JsonObject submitDirectly(String siteUrl, String partnerId, String partnerKey,
                                           String telco, int amount, String cardCode, String cardSerial, String requestId) {
        String sign = md5(partnerKey + cardCode + cardSerial);

        // 1. Thử 5 lần POST riêng biệt
        for (int attempt = 1; attempt <= 5; attempt++) {
            LOGGER.info("[DirectCardSubmit] Thử nộp thẻ lần {}/5 bằng phương thức POST tới {}...", attempt, siteUrl);
            JsonObject resp = tryPostSubmit(siteUrl, partnerId, partnerKey, telco, amount, cardCode, cardSerial, requestId, sign);
            if (resp != null && resp.has("status")) {
                LOGGER.info("[DirectCardSubmit] 🟢 Gửi POST thành công ở lần thử {}/5! Kết quả status: {}",
                        attempt, resp.get("status").getAsString());
                return resp;
            }
            try {
                Thread.sleep(1000L * Math.min(5, attempt));
            } catch (InterruptedException ignored) {}
        }

        // 2. Cả 5 lần POST đều không thành công -> Chuyển sang 5 lần thử GET Fallback
        LOGGER.warn("[DirectCardSubmit] ⚠️ Cả 5 lần thử POST đều thất bại. Chuyển sang phương thức GET làm Fallback...");
        for (int attempt = 1; attempt <= 5; attempt++) {
            LOGGER.info("[DirectCardSubmit] Thử nộp thẻ lần {}/5 bằng phương thức GET tới {}...", attempt, siteUrl);
            JsonObject getResp = tryGetSubmit(siteUrl, partnerId, partnerKey, telco, amount, cardCode, cardSerial, requestId, sign);
            if (getResp != null && getResp.has("status")) {
                LOGGER.info("[DirectCardSubmit] 🟢 Gửi GET thành công ở lần thử {}/5! Kết quả status: {}", attempt, getResp.get("status").getAsString());
                return getResp;
            }
            try {
                Thread.sleep(1000L * Math.min(5, attempt));
            } catch (InterruptedException ignored) {}
        }

        LOGGER.error("[DirectCardSubmit] ❌ Gửi thẻ thất bại hoàn toàn sau 5 lần POST và 5 lần GET tới {}.", siteUrl);
        return null;
    }

    private static JsonObject tryPostSubmit(String siteUrl, String partnerId, String partnerKey,
                                             String telco, int amount, String cardCode, String cardSerial, String requestId, String sign) {
        try {
            URL url = URI.create(siteUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);

            // Gửi dưới dạng Form URL-encoded chuẩn Web Gạch Thẻ
            String formBody = "request_id=" + URLEncoder.encode(requestId, StandardCharsets.UTF_8)
                    + "&code=" + URLEncoder.encode(cardCode, StandardCharsets.UTF_8)
                    + "&partner_id=" + URLEncoder.encode(partnerId, StandardCharsets.UTF_8)
                    + "&serial=" + URLEncoder.encode(cardSerial, StandardCharsets.UTF_8)
                    + "&telco=" + URLEncoder.encode(telco.toUpperCase(), StandardCharsets.UTF_8)
                    + "&amount=" + amount
                    + "&sign=" + URLEncoder.encode(sign, StandardCharsets.UTF_8)
                    + "&command=submit";

            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("User-Agent", "PayBot-DirectSubmit/5.5.5");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(formBody.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    return GSON.fromJson(br, JsonObject.class);
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("[DirectCardSubmit] POST error: {}", t.getMessage());
        }
        return null;
    }

    private static JsonObject tryGetSubmit(String siteUrl, String partnerId, String partnerKey,
                                            String telco, int amount, String cardCode, String cardSerial, String requestId, String sign) {
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
            conn.setRequestProperty("User-Agent", "PayBot-DirectSubmit/5.5.5");

            int code = conn.getResponseCode();
            if (code == 200) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    return GSON.fromJson(br, JsonObject.class);
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("[DirectCardSubmit] GET error: {}", t.getMessage());
        }
        return null;
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
