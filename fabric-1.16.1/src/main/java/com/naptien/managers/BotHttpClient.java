package com.naptien.managers;

import com.google.gson.*;
import com.naptien.PayBotMod;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * BotHttpClient (Fabric) — HTTP client giao tiếp với Discord bot.
 * Không dùng Bukkit API, dùng java.net thuần.
 *
 * v5.0.0 — requestNapBank() trả về JsonObject thay vì void
 *          để NapBankGui lấy qr_url + invoice_id dùng tạo QR Map in-game.
 *
 * + reportServerIp() dùng BanManager.getCurrentPublicIp() thay vì 0.0.0.0 (v5.0.0+)
 */
public class BotHttpClient {

    private static final int TIMEOUT_MS = 8_000;

    /**
     * v5.0.0 — Key xác thực request lên bot.py (middleware auth_middleware trong
     * build_http_app() check header "X-API-Key"). PHẢI khớp với BOT_API_KEY (env var)
     * hoặc default trong bot.py, nếu không MỌI request (trừ /api/sepay-ipn) sẽ bị bot
     * trả 403 Forbidden. Đọc từ config "bot-api-key" để admin đổi được không cần build
     * lại mod — fallback về default khớp với default trong bot.py nếu admin chưa đổi.
     */
    public static final String DEFAULT_API_KEY =
            "TheRealShirozOnTopAndIMadeThisAPIKeyForSecretThingYouShouldntBypassThisAPIKeyYay";

    /** Gắn header X-API-Key vào connection — gọi SAU openConnection(), TRƯỚC getOutputStream(). */
    public static void applyApiKey(HttpURLConnection conn, PayBotMod mod) {
        String key = mod.getConfig().getString("bot-api-key", DEFAULT_API_KEY).trim();
        if (!key.isEmpty()) conn.setRequestProperty("X-API-Key", key);
    }

    private final PayBotMod mod;
    private final Gson gson = new Gson();

    public BotHttpClient(PayBotMod mod) { this.mod = mod; }

    // ─── Connect / Disconnect ─────────────────────────────────────────────────

    public String connectToGuild(String guildId) {
        String sid = serverId();
        String callbackUrl = mod.getConfig().getString("plugin-callback-url", "").trim();
        JsonObject body = new JsonObject();
        body.addProperty("guild_id", guildId);
        body.addProperty("callback_url", callbackUrl);
        if (!sid.isEmpty()) body.addProperty("server_id", sid);
        try {
            JsonObject r = post("/api/connect", body);
            if (r == null || !ok(r)) return null;
            if (r.has("bot_url") && !r.get("bot_url").getAsString().isBlank()) {
                mod.getConfig().set("bot-url", r.get("bot_url").getAsString().trim());
                mod.getConfig().save();
                PayBotMod.LOGGER.info("[Bot] Auto-saved bot-url: " + r.get("bot_url").getAsString());
            }
            return r.has("server_id") ? r.get("server_id").getAsString() : null;
        } catch (Exception e) { warn("connectToGuild", e); return null; }
    }

    public boolean requestDisconnect() {
        try { JsonObject r = post("/api/disconnect", ids()); return ok(r); }
        catch (Exception e) { warn("requestDisconnect", e); return false; }
    }

    public boolean confirmDisconnect() {
        try { JsonObject r = post("/api/confirm-disconnect", ids()); return ok(r); }
        catch (Exception e) { warn("confirmDisconnect", e); return false; }
    }

    // ─── Config ───────────────────────────────────────────────────────────────

    public void fetchAndApplyConfig() {
        try {
            JsonObject r = post("/api/get-config", sid());
            if (r == null || !ok(r)) return;
            if (r.has("card_rewards")) applyRewards(r.getAsJsonObject("card_rewards"), "denom-rewards-card");
            if (r.has("bank_rewards")) applyRewards(r.getAsJsonObject("bank_rewards"), "denom-rewards-bank");
            mod.getConfig().save();
        } catch (Exception e) { warn("fetchAndApplyConfig", e); }
    }

    private void applyRewards(JsonObject rw, String section) {
        for (Map.Entry<String, JsonElement> e : rw.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject info = e.getValue().getAsJsonObject();
            String k = e.getKey();
            if (info.has("amt")) mod.getConfig().set(section + "." + k + ".amt", info.get("amt").getAsString());
            if (info.has("cmd")) mod.getConfig().set(section + "." + k + ".cmd", info.get("cmd").getAsString());
        }
    }

    // ─── Rewards ──────────────────────────────────────────────────────────────

    public List<JsonObject> fetchPendingRewards() {
        try {
            JsonObject r = post("/api/pending-rewards", sid());
            if (r == null || !r.has("rewards")) return List.of();
            List<JsonObject> list = new ArrayList<>();
            for (JsonElement e : r.getAsJsonArray("rewards")) if (e.isJsonObject()) list.add(e.getAsJsonObject());
            return list;
        } catch (Exception e) { warn("fetchPendingRewards", e); return List.of(); }
    }

    /**
     * v5.0.5 [Part 24]: báo IP vừa bị /disablepaybot chặn cục bộ (BanManager) lên bot
     * — owner xem/quản lý tập trung qua Discord (/viewblockedips, /removeip). Không gửi
     * "ip" tự khai báo — bot tự lấy IP thật từ chính request. Fire-and-forget.
     */
    public void reportBlockedIp() {
        try {
            post("/api/report-blocked-ip", sid());
        } catch (Exception e) { warn("reportBlockedIp", e); }
    }

    public void confirmReward(String rewardId) {
        JsonObject b = sid(); b.addProperty("reward_id", rewardId);
        try { post("/api/confirm-reward", b); } catch (Exception e) { warn("confirmReward", e); }
    }

    public List<Map<String,String>> fetchOfflineRewardsFromBot() {
        try {
            JsonObject r = post("/api/restore-offline-rewards", sid());
            if (r == null || !r.has("rewards")) return List.of();
            List<Map<String,String>> list = new ArrayList<>();
            for (JsonElement e : r.getAsJsonArray("rewards")) {
                if (!e.isJsonObject()) continue;
                Map<String,String> m = new LinkedHashMap<>();
                for (var entry : e.getAsJsonObject().entrySet())
                    m.put(entry.getKey(), entry.getValue().isJsonNull() ? "" : entry.getValue().getAsString());
                list.add(m);
            }
            return list;
        } catch (Exception e) { warn("fetchOfflineRewards", e); return List.of(); }
    }

    public void confirmOfflineReward(String rewardId) {
        JsonObject b = sid(); b.addProperty("reward_id", rewardId);
        try { post("/api/confirm-offline-reward", b); } catch (Exception e) { warn("confirmOfflineReward", e); }
    }

    // ─── Online players ───────────────────────────────────────────────────────

    public void updateOnlinePlayers(List<String> names) {
        JsonObject b = sid(); JsonArray arr = new JsonArray();
        for (String n : names) arr.add(n); b.add("players", arr);
        try { post("/api/update-online-players", b); } catch (Exception e) { warn("updateOnlinePlayers", e); }
    }

    // ─── Card / Bank (bot-connected mode) ─────────────────────────────────────

    public String submitCard(String player, String telco, int denom, String code, String serial) {
        JsonObject b = sid();
        b.addProperty("player_name", player); b.addProperty("telco", telco);
        b.addProperty("denom", denom); b.addProperty("card_code", code); b.addProperty("card_serial", serial);
        try {
            JsonObject r = post("/api/submit-card", b);
            return (r != null && r.has("request_id")) ? r.get("request_id").getAsString() : null;
        } catch (Exception e) { warn("submitCard", e); return null; }
    }

    /**
     * v5.0.0 — trả về JsonObject để caller lấy qr_url + invoice_id.
     * Bot trả về: {"ok": true, "qr_url": "...", "invoice_id": "..."}
     * Trả về null nếu lỗi mạng; trả về JsonObject với ok=false nếu bot báo lỗi.
     */
    public JsonObject requestNapBank(String playerName, int amount) {
        JsonObject b = sid();
        b.addProperty("player_name", playerName);
        b.addProperty("amount", amount);
        try {
            return post("/api/request-napbank", b);
        } catch (Exception e) {
            warn("requestNapBank", e);
            return null;
        }
    }

    public void notifyNapBankExpired(String invoiceId) {
        JsonObject b = sid(); b.addProperty("invoice_id", invoiceId);
        try { post("/api/napbank-expired", b); } catch (Exception e) { warn("napbankExpired", e); }
    }

    /**
     * v5.1.0 — Notify bot kết quả gạch thẻ (standalone gạch trực tiếp, bot chỉ relay Discord).
     * Fire-and-forget. POST /api/standalone-card-result
     */
    public void notifyCardResult(String requestId, String playerName,
                                  String telco, int denom, boolean success, String message) {
        String sid = serverId();
        if (sid.isEmpty()) return; // không connect bot → không báo
        JsonObject b = new JsonObject();
        b.addProperty("server_id",   sid);
        b.addProperty("request_id", requestId);
        b.addProperty("player_name", playerName);
        b.addProperty("telco",       telco);
        b.addProperty("denom",       denom);
        b.addProperty("success",     success);
        if (!message.isEmpty()) b.addProperty("message", message);
        try { post("/api/standalone-card-result", b); }
        catch (Exception e) { PayBotMod.LOGGER.debug("[Bot] notifyCardResult: " + e.getMessage()); }
    }

    /**
     * v5.1.0 — Notify bot giao dịch bank đã được thanh toán (poll trực tiếp SePay API).
     * Fire-and-forget. POST /api/standalone-bank-paid
     */
    public void notifyBankPaidViaApi(String invoiceId, String playerName, int amount) {
        String sid = serverId();
        if (sid.isEmpty()) return; // không connect bot → không báo
        JsonObject b = new JsonObject();
        b.addProperty("server_id",   sid);
        b.addProperty("invoice_id",  invoiceId);
        b.addProperty("player_name", playerName);
        b.addProperty("amount",      amount);
        try { post("/api/standalone-bank-paid", b); }
        catch (Exception e) { PayBotMod.LOGGER.debug("[Bot] notifyBankPaidViaApi: " + e.getMessage()); }
    }

    // ─── Config push ──────────────────────────────────────────────────────────

    public void pushRewardConfig() {
        JsonObject b = sid();
        b.add("card_rewards", buildRewardJson("denom-rewards-card"));
        b.add("bank_rewards", buildRewardJson("denom-rewards-bank"));
        try { post("/api/push-reward-config", b); } catch (Exception e) { warn("pushRewardConfig", e); }
    }

    private JsonObject buildRewardJson(String section) {
        JsonObject obj = new JsonObject();
        for (String key : mod.getConfig().getSectionKeys(section)) {
            JsonObject e = new JsonObject();
            e.addProperty("amt", mod.getConfig().getString(section + "." + key + ".amt", ""));
            e.addProperty("cmd", mod.getConfig().getString(section + "." + key + ".cmd", ""));
            obj.add(key, e);
        }
        return obj;
    }

    public void updateNotificationChannel(String channelId) {
        JsonObject b = sid(); b.addProperty("channel_id", channelId);
        try { post("/api/update-notification-channel", b); } catch (Exception e) { warn("updateNotificationChannel", e); }
    }

    public void updateCardApiConfig(String site, String pid, String pkey) {
        JsonObject b = new JsonObject();
        b.addProperty("guild_id", guildId()); b.addProperty("site", site);
        b.addProperty("partner_id", pid); b.addProperty("partner_key", pkey);
        try { post("/api/update-card-api-config", b); } catch (Exception e) { warn("updateCardApiConfig", e); }
    }

    /**
     * Report public IP thật của server lên bot.
     * Gọi từ PayBotMod.reportPublicIpToBot() sau khi BanManager phát hiện IP.
     *
     * Tham số ip lấy từ BanManager.getCurrentPublicIp(), KHÔNG dùng bind address 0.0.0.0.
     * Bot dùng thông tin này để nhận diện server và hỗ trợ block khi cần.
     */
    public void reportServerIp(String ip, int mcPort) {
        JsonObject b = ids();
        b.addProperty("public_ip",      ip);
        b.addProperty("ip",             ip);  // tương thích ngược
        b.addProperty("port",           mcPort);
        b.addProperty("mc_port",        mcPort);
        b.addProperty("address",        ip + ":" + mcPort);
        b.addProperty("mode",           mod.isStandaloneMode() ? "standalone" : "connected");
        b.addProperty("plugin_version", "5.0.0");
        b.addProperty("plugin_port",    mod.getConfig().getInt("plugin-port", 25580));
        try { post("/api/report-server-ip", b); }
        catch (Exception e) { PayBotMod.LOGGER.debug("[Bot] reportServerIp: " + e.getMessage()); }
    }

    // ─── HTTP core ────────────────────────────────────────────────────────────

    /** v5.0.3 — mirror y hệt cơ chế TOFU bên plugin (BotHttpClient.java Bukkit),
     * xem comment đầy đủ ở đó. Dùng chung PayBotConfig thay Bukkit FileConfiguration. */
    private static SSLSocketFactory buildPinnedSocketFactory(PayBotMod mod) throws Exception {
        TrustManager[] tm = new TrustManager[] { new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}

            public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                if (chain == null || chain.length == 0)
                    throw new CertificateException("Bot không gửi certificate nào.");
                String actual;
                try {
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    byte[] digest = md.digest(chain[0].getEncoded());
                    StringBuilder sb = new StringBuilder();
                    for (byte b : digest) sb.append(String.format("%02x", b));
                    actual = sb.toString();
                } catch (Exception e) {
                    throw new CertificateException(e);
                }

                String saved = mod.getConfig().getString("bot-cert-fingerprint", "").trim();
                if (saved.isEmpty()) {
                    mod.getConfig().set("bot-cert-fingerprint", actual);
                    mod.getConfig().save();
                    PayBotMod.LOGGER.info("[Bot] TOFU: lưu fingerprint cert lần đầu: " + actual);
                    return;
                }
                if (!saved.equalsIgnoreCase(actual)) {
                    boolean strict = mod.getConfig().getBoolean("bot-cert-strict-pin", false);
                    String msg = "[Bot] ⚠️ Cert fingerprint của bot ĐÃ ĐỔI (" + saved + " → " + actual
                            + ") — có thể do bot đổi VPS (bình thường) HOẶC bị MITM (bất thường).";
                    if (strict) {
                        PayBotMod.LOGGER.error(msg + " bot-cert-strict-pin=true → TỪ CHỐI kết nối. "
                                + "Nếu chắc chắn bot đổi VPS hợp lệ, xoá key bot-cert-fingerprint trong config.yml.");
                        throw new CertificateException("Cert fingerprint không khớp (strict-pin bật).");
                    }
                    PayBotMod.LOGGER.warn(msg + " bot-cert-strict-pin=false → vẫn cho qua, tự cập nhật "
                            + "fingerprint mới. Đặt bot-cert-strict-pin=true trong config.yml nếu muốn chặn cứng.");
                    mod.getConfig().set("bot-cert-fingerprint", actual);
                    mod.getConfig().save();
                }
            }

            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }};
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tm, new SecureRandom());
        return ctx.getSocketFactory();
    }

    private static final int[] CANDIDATE_PORTS = {24733, 25585, 28491, 39281, 48293};

    private JsonObject post(String path, JsonObject body) throws Exception {
        return post(path, body, false);
    }

    private JsonObject post(String path, JsonObject body, boolean silent) throws Exception {
        if (mod.isStandaloneMode() && !path.equals("/api/connect") && !path.equals("/api/connect-plugin")) {
            return null;
        }

        String botUrl = mod.getConfig().getString("bot-url", "").trim();
        if (botUrl.isEmpty()) return null;

        java.net.URL url;
        try {
            url = new java.net.URL(botUrl);
        } catch (java.net.MalformedURLException e) {
            return doPost(botUrl, path, body);
        }

        String protocol = url.getProtocol();
        String host = url.getHost();
        int currentPort = url.getPort();
        if (currentPort == -1) {
            currentPort = protocol.equalsIgnoreCase("https") ? 443 : 80;
        }

        try {
            return doPost(botUrl, path, body);
        } catch (java.io.IOException e) {
            boolean isCandidate = false;
            for (int p : CANDIDATE_PORTS) {
                if (p == currentPort) {
                    isCandidate = true;
                    break;
                }
            }
            if (!isCandidate && currentPort != 24733) {
                throw e;
            }

            if (!silent) {
                PayBotMod.LOGGER.warn("[Bot] Cổng " + currentPort + " không kết nối được, tự động quét các cổng dự phòng...");
            }
            for (int altPort : CANDIDATE_PORTS) {
                if (altPort == currentPort) continue;
                String altUrl = protocol + "://" + host + ":" + altPort;
                try {
                    JsonObject resp = doPostWithTimeout(altUrl, path, body, 2000);
                    if (resp != null) {
                        mod.getConfig().set("bot-url", altUrl);
                        mod.getConfig().save();
                        PayBotMod.LOGGER.info("[Bot] Tự động tìm thấy bot hoạt động tại cổng mới: " + altPort);
                        return resp;
                    }
                } catch (java.io.IOException ignored) {
                }
            }
            throw e;
        }
    }

    private JsonObject doPost(String botUrl, String path, JsonObject body) throws Exception {
        return doPostWithTimeout(botUrl, path, body, TIMEOUT_MS);
    }

    private JsonObject doPostWithTimeout(String botUrl, String path, JsonObject body, int timeoutMs) throws Exception {
        byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection conn = (HttpURLConnection) new URL(botUrl + path).openConnection();
        if (conn instanceof HttpsURLConnection https) {
            https.setSSLSocketFactory(buildPinnedSocketFactory(mod));
        }
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setUseCaches(false);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type",   "application/json; charset=UTF-8");
        conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
        applyApiKey(conn, mod); // v5.0.0: bắt buộc — bot.py giờ check X-API-Key, thiếu là bị 403
        conn.setConnectTimeout(timeoutMs); conn.setReadTimeout(timeoutMs);
        try (OutputStream os = conn.getOutputStream()) { os.write(bodyBytes); os.flush(); }
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = br.readLine()) != null) sb.append(line);
            String raw = sb.toString().trim();
            if (raw.isEmpty()) return null;
            try {
                JsonElement elem = gson.fromJson(raw, JsonElement.class);
                if (elem == null || elem.isJsonNull() || !elem.isJsonObject()) {
                    PayBotMod.LOGGER.debug("[Bot] postJson non-object: " + raw.substring(0, Math.min(80, raw.length())));
                    return null;
                }
                return elem.getAsJsonObject();
            } catch (JsonSyntaxException e) {
                PayBotMod.LOGGER.debug("[Bot] postJson invalid JSON: " + raw.substring(0, Math.min(80, raw.length())));
                return null;
            }
        }
    }

    public void pingBot() {
        String sid = serverId();
        if (sid.isEmpty()) return;
        JsonObject body = new JsonObject();
        body.addProperty("server_id", sid);
        try {
            post("/api/pending-rewards", body, true);
        } catch (Exception ignored) {
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private boolean ok(JsonObject r) { return r != null && r.has("ok") && r.get("ok").getAsBoolean(); }
    private String serverId()  { return mod.getConfig().getString("server-id", "").trim(); }
    private String guildId()   { return mod.getConfig().getString("guild-id",  "").trim(); }
    private JsonObject sid()   { JsonObject b = new JsonObject(); b.addProperty("server_id", serverId()); return b; }
    private JsonObject ids()   { JsonObject b = sid(); b.addProperty("guild_id", guildId()); return b; }
    private void warn(String m, Exception e) { PayBotMod.LOGGER.debug("[Bot] " + m + ": " + e.getMessage()); }
}
