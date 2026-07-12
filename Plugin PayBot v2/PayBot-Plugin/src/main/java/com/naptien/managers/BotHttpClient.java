package com.naptien.managers;

import com.google.gson.*;
import com.naptien.NapTienPlugin;
import org.bukkit.entity.Player;

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
import java.util.logging.Level;

/**
 * BotHttpClient — Giao tiếp HTTP giữa plugin và Discord bot.
 *
 * Tất cả endpoints đều dùng POST và server_id để xác thực.
 * Endpoints đúng theo botbackup.py (build_http_app).
 *
 * Changelog:
 *   v4.0.1 — Tạo ban đầu
 *   v4.1.0 — Fix tên endpoints (disconnect, config, rewards, online-players, napbank);
 *            fix GET → POST cho get-config, pending-rewards, restore-offline-rewards
 */
public class BotHttpClient {

    private static final int TIMEOUT_MS = 8_000;

    /**
     * v5.0.0 — Key xác thực request lên bot.py (middleware auth_middleware trong
     * build_http_app() check header "X-API-Key"). PHẢI khớp tuyệt đối với hằng số
     * BOT_API_KEY hardcode trong bot.py, nếu không MỌI request (trừ /api/sepay-ipn)
     * sẽ bị bot trả 403 Forbidden.
     *
     * v5.0.2 FIX: TRƯỚC đây đọc từ config "bot-api-key" để admin "tự đổi được không
     * cần build lại jar" — nhưng đây là khoá bí mật nội bộ giữa plugin/mod và bot.py,
     * KHÔNG phải thứ admin server nên tự ý sửa (đổi 1 bên mà quên đổi bên kia là tự
     * khoá API của chính mình, 403 toàn bộ, rất khó debug). Giờ hardcode cứng trong
     * code (khớp 3 nơi: plugin, mod, bot.py) — bỏ hẳn khỏi config.yml.
     */
    public static final String DEFAULT_API_KEY =
            "TheRealShirozOnTopAndIMadeThisAPIKeyForSecretThingYouShouldntBypassThisAPIKeyYay";

    /** Gắn header X-API-Key vào connection — gọi SAU openConnection(), TRƯỚC getOutputStream(). */
    public static void applyApiKey(HttpURLConnection conn, NapTienPlugin plugin) {
        conn.setRequestProperty("X-API-Key", DEFAULT_API_KEY);
    }

    private final NapTienPlugin plugin;
    private final Gson gson = new Gson();

    public BotHttpClient(NapTienPlugin plugin) {
        this.plugin = plugin;
    }

    // ─── Connect / Disconnect ─────────────────────────────────────────────────

    /**
     * Kết nối plugin với Discord guild.
     * POST /api/connect  {guild_id, callback_url, server_id?}
     * @return server_id mới từ bot, hoặc null nếu thất bại
     */
    public String connectToGuild(String guildId) {
        String existingServerId = plugin.getConfig().getString("server-id", "").trim();
        // callback_url: URL của HTTP server plugin (nếu bot cần push về).
        // Trong pull-based mode, có thể để trống.
        String callbackUrl = plugin.getConfig().getString("plugin-callback-url", "").trim();

        JsonObject body = new JsonObject();
        body.addProperty("guild_id",     guildId);
        body.addProperty("callback_url", callbackUrl);
        if (!existingServerId.isEmpty()) {
            body.addProperty("server_id", existingServerId); // reconnect
        }
        try {
            JsonObject resp = postJson("/api/connect", body);
            if (resp != null && resp.has("ok") && resp.get("ok").getAsBoolean()) {
                if (resp.has("bot_url") && !resp.get("bot_url").getAsString().isBlank()) {
                    plugin.getConfig().set("bot-url", resp.get("bot_url").getAsString().trim());
                    plugin.forceConfigRefresh("auto-saved bot-url");
                    plugin.getLogger().info("[Bot] Auto-saved bot-url: " + resp.get("bot_url").getAsString());
                }
                return resp.has("server_id")
                        ? resp.get("server_id").getAsString()
                        : existingServerId;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Bot] connectToGuild: " + e.getMessage());
        }
        return null;
    }

    /**
     * Yêu cầu ngắt kết nối.
     * POST /api/disconnect  {server_id, guild_id}
     */
    public boolean requestDisconnect() {
        JsonObject body = new JsonObject();
        body.addProperty("server_id", getServerId());
        body.addProperty("guild_id",  getGuildId());
        try {
            JsonObject resp = postJson("/api/disconnect", body);
            return resp != null && resp.has("ok") && resp.get("ok").getAsBoolean();
        } catch (Exception e) {
            plugin.getLogger().warning("[Bot] requestDisconnect: " + e.getMessage());
            return false;
        }
    }

    /**
     * Xác nhận ngắt kết nối.
     * POST /api/confirm-disconnect  {server_id, guild_id}
     */
    public boolean confirmDisconnect() {
        JsonObject body = new JsonObject();
        body.addProperty("server_id", getServerId());
        body.addProperty("guild_id",  getGuildId());
        try {
            JsonObject resp = postJson("/api/confirm-disconnect", body);
            return resp != null && resp.has("ok") && resp.get("ok").getAsBoolean();
        } catch (Exception e) {
            plugin.getLogger().warning("[Bot] confirmDisconnect: " + e.getMessage());
            return false;
        }
    }

    // ─── Config ───────────────────────────────────────────────────────────────

    /**
     * Lấy config từ bot và apply (pull-based).
     * POST /api/get-config  {server_id}
     */
    public void fetchAndApplyConfig() {
        String serverId = getServerId();
        if (serverId.isEmpty()) return;

        JsonObject body = new JsonObject();
        body.addProperty("server_id", serverId);
        try {
            JsonObject resp = postJson("/api/get-config", body);
            if (resp == null || !resp.has("ok") || !resp.get("ok").getAsBoolean()) return;

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (resp.has("card_rewards")) applyRewards(resp.getAsJsonObject("card_rewards"), "denom-rewards-card");
                if (resp.has("bank_rewards")) applyRewards(resp.getAsJsonObject("bank_rewards"), "denom-rewards-bank");
                plugin.forceConfigRefresh("nhận config từ bot lúc khởi động");
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINER, "[Bot] fetchAndApplyConfig: " + e.getMessage());
        }
    }

    private void applyRewards(JsonObject rewards, String section) {
        for (Map.Entry<String, JsonElement> entry : rewards.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject info = entry.getValue().getAsJsonObject();
            String key = entry.getKey();
            if (info.has("amt")) plugin.getConfig().set(section + "." + key + ".amt", info.get("amt").getAsString());
            if (info.has("cmd")) plugin.getConfig().set(section + "." + key + ".cmd", info.get("cmd").getAsString());
        }
    }

    // ─── Rewards ──────────────────────────────────────────────────────────────

    /**
     * Lấy danh sách phần thưởng đang chờ xử lý.
     * POST /api/pending-rewards  {server_id}
     */
    public List<JsonObject> fetchPendingRewards() {
        String serverId = getServerId();
        if (serverId.isEmpty()) return Collections.emptyList();

        JsonObject body = new JsonObject();
        body.addProperty("server_id", serverId);
        try {
            JsonObject resp = postJson("/api/pending-rewards", body);
            if (resp == null || !resp.has("rewards")) return Collections.emptyList();
            List<JsonObject> list = new ArrayList<>();
            for (JsonElement e : resp.getAsJsonArray("rewards")) {
                if (e.isJsonObject()) list.add(e.getAsJsonObject());
            }
            return list;
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINER, "[Bot] fetchPendingRewards: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * v5.0.3 [Part 24]: báo IP vừa bị /disablepaybot chặn cục bộ (BanGuard) lên bot —
     * để owner xem/quản lý tập trung qua Discord (/viewblockedips, /removeip). Không
     * gửi "ip" tự khai báo — bot tự lấy IP THẬT từ chính request (xem bot.py Part 15).
     * Fire-and-forget: ban cục bộ ĐÃ THÀNH CÔNG dù báo bot có lỗi hay không.
     */
    public void reportBlockedIp(String serverId) {
        JsonObject body = new JsonObject();
        body.addProperty("server_id", serverId);
        try {
            postJson("/api/report-blocked-ip", body);
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINER, "[Bot] reportBlockedIp: " + e.getMessage());
        }
    }

    /**
     * Xác nhận đã xử lý xong reward.
     * POST /api/confirm-reward  {server_id, reward_id}
     */
    public void confirmReward(String rewardId) {
        JsonObject body = new JsonObject();
        body.addProperty("server_id", getServerId());
        body.addProperty("reward_id", rewardId);
        try { postJson("/api/confirm-reward", body); }
        catch (Exception e) { plugin.getLogger().log(Level.FINER, "[Bot] confirmReward: " + e.getMessage()); }
    }

    // ─── Offline rewards ──────────────────────────────────────────────────────

    /**
     * Khôi phục offline rewards từ bot (gọi sau khi plugin restart).
     * POST /api/restore-offline-rewards  {server_id}
     */
    public List<Map<String, String>> fetchOfflineRewardsFromBot() {
        String serverId = getServerId();
        if (serverId.isEmpty()) return Collections.emptyList();

        JsonObject body = new JsonObject();
        body.addProperty("server_id", serverId);
        try {
            JsonObject resp = postJson("/api/restore-offline-rewards", body);
            if (resp == null || !resp.has("rewards")) return Collections.emptyList();
            List<Map<String, String>> list = new ArrayList<>();
            for (JsonElement e : resp.getAsJsonArray("rewards")) {
                if (!e.isJsonObject()) continue;
                Map<String, String> map = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> entry : e.getAsJsonObject().entrySet()) {
                    map.put(entry.getKey(), entry.getValue().isJsonNull()
                            ? "" : entry.getValue().getAsString());
                }
                list.add(map);
            }
            return list;
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINER, "[Bot] fetchOfflineRewardsFromBot: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Xác nhận đã xử lý xong offline reward.
     * POST /api/confirm-offline-reward  {server_id, reward_id}
     */
    public void confirmOfflineReward(String rewardId) {
        JsonObject body = new JsonObject();
        body.addProperty("server_id", getServerId());
        body.addProperty("reward_id", rewardId);
        try { postJson("/api/confirm-offline-reward", body); }
        catch (Exception e) { plugin.getLogger().log(Level.FINER, "[Bot] confirmOfflineReward: " + e.getMessage()); }
    }

    // ─── Online players ───────────────────────────────────────────────────────

    /**
     * Gửi danh sách player online lên bot.
     * POST /api/update-online-players  {server_id, players: [...]}
     * <p>
     * v5.0.0: KHÔNG còn được gọi tự động ở đâu nữa (đã bỏ hẳn poll/heartbeat/event-push) —
     * bot giờ tự hỏi on-demand qua /check-online (xem PluginHttpServer.handleCheckOnline)
     * đúng lúc cần, plugin không cần đẩy gì cả. Giữ lại method này (không xoá) để không
     * phá tương thích nếu có code khác/Fabric mod còn gọi tới, và để admin có thể tự gọi
     * thủ công nếu thực sự cần đồng bộ ngay.
     */
    public void updateOnlinePlayers(List<String> playerNames) {
        String serverId = getServerId();
        if (serverId.isEmpty()) return;
        JsonObject body = new JsonObject();
        body.addProperty("server_id", serverId);
        JsonArray players = new JsonArray();
        for (String name : playerNames) players.add(name);
        body.add("players", players);
        try { postJson("/api/update-online-players", body); }
        catch (Exception e) { plugin.getLogger().log(Level.FINER, "[Bot] updateOnlinePlayers: " + e.getMessage()); }
    }

    // ─── Card & Bank (bot-connected mode) ─────────────────────────────────────

    /**
     * Gửi thẻ lên bot (bot-connected mode).
     * POST /api/submit-card  {server_id, player_name, telco, denom, card_code, card_serial}
     * @return request_id, hoặc null nếu thất bại
     */
    public String submitCard(String playerName, String telco, int denom,
                              String cardCode, String cardSerial) {
        JsonObject body = new JsonObject();
        body.addProperty("server_id",   getServerId());
        body.addProperty("player_name", playerName);
        body.addProperty("telco",       telco);
        body.addProperty("denom",       denom);
        body.addProperty("card_code",   cardCode);
        body.addProperty("card_serial", cardSerial);
        try {
            JsonObject resp = postJson("/api/submit-card", body);
            if (resp != null && resp.has("request_id")) return resp.get("request_id").getAsString();
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINER, "[Bot] submitCard: " + e.getMessage());
        }
        return null;
    }

    /**
     * Yêu cầu tạo QR nạp bank (bot-connected mode).
     * POST /api/request-napbank  {server_id, player_name, amount}
     *
     * v5.0.0 BUG FIX: trước đây gọi postJson() nhưng KHÔNG đọc response —
     * nghĩa là bot tạo invoice + trả qr_url thành công nhưng player KHÔNG
     * BAO GIỜ nhận được map QR (chỉ đứng im, không có gì xảy ra). Giờ đọc
     * đúng qr_url/qr_fallback_url/invoice_id rồi gọi QRMapManager để give map,
     * giống hệt luồng standalone.
     */
    public void requestNapBank(Player player, int amount) {
        JsonObject body = new JsonObject();
        body.addProperty("server_id",   getServerId());
        body.addProperty("player_name", player.getName());
        body.addProperty("amount",      amount);
        try {
            JsonObject resp = postJson("/api/request-napbank", body);
            if (resp == null || !resp.has("ok") || !resp.get("ok").getAsBoolean()) {
                String msg = (resp != null && resp.has("msg")) ? resp.get("msg").getAsString() : "Không có response từ bot";
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.sendMessage(NapTienPlugin.f("§c[PayBot] §fLỗi tạo QR: " + msg)));
                return;
            }
            String qrUrl       = resp.has("qr_url")          ? resp.get("qr_url").getAsString()          : "";
            String qrFbUrl     = resp.has("qr_fallback_url") ? resp.get("qr_fallback_url").getAsString() : "";
            String invoiceId   = resp.has("invoice_id")      ? resp.get("invoice_id").getAsString()      : "";
            if (qrUrl.isEmpty() || invoiceId.isEmpty()) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.sendMessage(NapTienPlugin.f("§c[PayBot] §fBot không trả về QR hợp lệ!")));
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getQRMapManager().giveQRMap(player, qrUrl, qrFbUrl, invoiceId));
        } catch (Exception e) {
            plugin.getLogger().warning("[Bot] requestNapBank: " + e.getMessage());
            plugin.getServer().getScheduler().runTask(plugin, () ->
                player.sendMessage(NapTienPlugin.f("§c[PayBot] §fKhông kết nối được tới bot!")));
        }
    }

    /**
     * Thông báo QR nạp bank đã hết hạn.
     * POST /api/napbank-expired  {server_id, invoice_id}
     */
    public void notifyNapBankExpired(String invoiceId) {
        JsonObject body = new JsonObject();
        body.addProperty("server_id",  getServerId());
        body.addProperty("invoice_id", invoiceId);
        try { postJson("/api/napbank-expired", body); }
        catch (Exception e) { plugin.getLogger().log(Level.FINER, "[Bot] napbankExpired: " + e.getMessage()); }
    }

    /**
     * v5.0.0 (Phase B): báo cho bot khi PLUGIN tự phát hiện 1 đơn bank đã thanh toán
     * qua flow SePay-API-mới (poll trực tiếp, không qua bot). Bot không tham gia vào
     * việc NHẬN DIỆN thanh toán (plugin tự làm hết), nhưng nếu server đang connect bot
     * (guild-id có) thì vẫn cần báo qua để bot ghi log/relay thông báo lên Discord —
     * giữ đúng yêu cầu "vẫn phải báo lên Discord nếu connect, note status như cũ".
     * POST /api/standalone-bank-paid {server_id, invoice_id, player_name, amount}
     */
    public void notifyBankPaidViaApi(String invoiceId, String playerName, int amount) {
        String serverId = getServerId();
        if (serverId.isEmpty()) return; // không connect bot → không có gì để báo
        JsonObject body = new JsonObject();
        body.addProperty("server_id",   serverId);
        body.addProperty("invoice_id",  invoiceId);
        body.addProperty("player_name", playerName);
        body.addProperty("amount",      amount);
        try { postJson("/api/standalone-bank-paid", body); }
        catch (Exception e) { plugin.getLogger().log(Level.FINER, "[Bot] notifyBankPaidViaApi: " + e.getMessage()); }
    }

    // ─── Config push ──────────────────────────────────────────────────────────

    /**
     * Đẩy cấu hình thưởng lên bot.
     * POST /api/push-reward-config  {server_id, card_rewards, bank_rewards}
     */
    public void pushRewardConfig() {
        String serverId = getServerId();
        if (serverId.isEmpty()) return;
        JsonObject body = new JsonObject();
        body.addProperty("server_id", serverId);
        body.add("card_rewards", buildRewardJson("denom-rewards-card"));
        body.add("bank_rewards", buildRewardJson("denom-rewards-bank"));
        try { postJson("/api/push-reward-config", body); }
        catch (Exception e) { plugin.getLogger().log(Level.FINER, "[Bot] pushRewardConfig: " + e.getMessage()); }
    }

    private JsonObject buildRewardJson(String section) {
        JsonObject obj = new JsonObject();
        if (!plugin.getConfig().isConfigurationSection(section)) return obj;
        for (String key : plugin.getConfig().getConfigurationSection(section).getKeys(false)) {
            JsonObject entry = new JsonObject();
            entry.addProperty("amt", plugin.getConfig().getString(section + "." + key + ".amt", ""));
            entry.addProperty("cmd", plugin.getConfig().getString(section + "." + key + ".cmd", ""));
            obj.add(key, entry);
        }
        return obj;
    }

    /**
     * Cập nhật channel Discord nhận thông báo.
     * POST /api/update-notification-channel  {server_id, channel_id}
     */
    public void updateNotificationChannel(String channelId) {
        JsonObject body = new JsonObject();
        body.addProperty("server_id",  getServerId());
        body.addProperty("channel_id", channelId);
        try { postJson("/api/update-notification-channel", body); }
        catch (Exception e) { plugin.getLogger().log(Level.FINER, "[Bot] updateNotificationChannel: " + e.getMessage()); }
    }

    /**
     * Đẩy config card API lên bot.
     * POST /api/update-card-api-config  {guild_id, site, partner_id, partner_key}
     */
    public void updateCardApiConfig(String site, String partnerId, String partnerKey) {
        JsonObject body = new JsonObject();
        body.addProperty("guild_id",    getGuildId());
        body.addProperty("site",        site);
        body.addProperty("partner_id",  partnerId);
        body.addProperty("partner_key", partnerKey);
        try { postJson("/api/update-card-api-config", body); }
        catch (Exception e) { plugin.getLogger().log(Level.FINER, "[Bot] updateCardApiConfig: " + e.getMessage()); }
    }

    // ─── HTTP helpers ─────────────────────────────────────────────────────────

    /**
     * v5.0.3 — TOFU (Trust On First Use) cho cert self-signed của bot.py (bot.py giờ
     * tự host HTTPS trên IP thật của VPS thay vì ngrok, dùng cert self-signed vì
     * Let's Encrypt không cấp được cho địa chỉ IP trần).
     *
     * Lần đầu kết nối (chưa lưu fingerprint nào) → tự tin cert đầu tiên nhận được,
     * lưu fingerprint SHA-256 vào config "bot-cert-fingerprint". Lần sau, nếu cert
     * đổi mà fingerprint không khớp: mặc định (bot-cert-strict-pin=false) chỉ CẢNH
     * BÁO (khả năng: bot restart trên VPS khác — tự cập nhật fingerprint mới luôn,
     * không tự khoá kết nối gây gián đoạn); nếu admin bật bot-cert-strict-pin=true
     * thì TỪ CHỐI kết nối (an toàn hơn, phòng MITM, nhưng phải tự xoá fingerprint cũ
     * trong config nếu đổi VPS thật).
     */
    private static SSLSocketFactory buildPinnedSocketFactory(NapTienPlugin plugin) throws Exception {
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

                String saved = plugin.getConfig().getString("bot-cert-fingerprint", "").trim();
                if (saved.isEmpty()) {
                    plugin.getConfig().set("bot-cert-fingerprint", actual);
                    plugin.saveConfig();
                    plugin.getLogger().info("[Bot] TOFU: lưu fingerprint cert lần đầu: " + actual);
                    return;
                }
                if (!saved.equalsIgnoreCase(actual)) {
                    boolean strict = plugin.getConfig().getBoolean("bot-cert-strict-pin", false);
                    String msg = "[Bot] ⚠️ Cert fingerprint của bot ĐÃ ĐỔI (" + saved + " → " + actual
                            + ") — có thể do bot đổi VPS (bình thường) HOẶC bị MITM (bất thường).";
                    if (strict) {
                        plugin.getLogger().severe(msg + " bot-cert-strict-pin=true → TỪ CHỐI kết nối. "
                                + "Nếu chắc chắn bot đổi VPS hợp lệ, xoá key bot-cert-fingerprint trong config.yml.");
                        throw new CertificateException("Cert fingerprint không khớp (strict-pin bật).");
                    }
                    plugin.getLogger().warning(msg + " bot-cert-strict-pin=false → vẫn cho qua, tự cập nhật "
                            + "fingerprint mới. Đặt bot-cert-strict-pin=true trong config.yml nếu muốn chặn cứng.");
                    plugin.getConfig().set("bot-cert-fingerprint", actual);
                    plugin.saveConfig();
                }
            }

            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }};
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tm, new SecureRandom());
        return ctx.getSocketFactory();
    }

    private static final int[] CANDIDATE_PORTS = {24733, 25585, 28491, 39281, 48293};

    private JsonObject postJson(String path, JsonObject body) throws Exception {
        return postJson(path, body, false);
    }

    private JsonObject postJson(String path, JsonObject body, boolean silent) throws Exception {
        if (plugin.isStandaloneMode() && !path.equals("/api/connect-plugin") && !path.equals("/api/connect")) {
            return null;
        }

        String botUrl = plugin.getConfig().getString("bot-url", "").trim();
        if (botUrl.isEmpty()) return null;

        java.net.URL url;
        try {
            url = new java.net.URL(botUrl);
        } catch (java.net.MalformedURLException e) {
            return doPostJson(botUrl, path, body);
        }

        String protocol = url.getProtocol();
        String host = url.getHost();
        int currentPort = url.getPort();
        if (currentPort == -1) {
            currentPort = protocol.equalsIgnoreCase("https") ? 443 : 80;
        }

        try {
            return doPostJson(botUrl, path, body);
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
                plugin.getLogger().warning("[PayBot] Cổng " + currentPort + " không kết nối được, tự động quét các cổng dự phòng...");
            }
            for (int altPort : CANDIDATE_PORTS) {
                if (altPort == currentPort) continue;
                String altUrl = protocol + "://" + host + ":" + altPort;
                try {
                    JsonObject resp = doPostJsonWithTimeout(altUrl, path, body, 2000);
                    if (resp != null) {
                        plugin.getConfig().set("bot-url", altUrl);
                        plugin.saveConfig();
                        plugin.reloadConfig();
                        plugin.getLogger().info("[PayBot] Tự động tìm thấy bot hoạt động tại cổng mới: " + altPort);
                        return resp;
                    }
                } catch (java.io.IOException ignored) {
                }
            }
            throw e;
        }
    }

    private JsonObject doPostJson(String botUrl, String path, JsonObject body) throws Exception {
        return doPostJsonWithTimeout(botUrl, path, body, TIMEOUT_MS);
    }

    private JsonObject doPostJsonWithTimeout(String botUrl, String path, JsonObject body, int timeoutMs) throws Exception {
        // FIX body rỗng: pre-compute bytes → Content-Length explicit → flush
        byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);

        URL url = new URL(botUrl + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (conn instanceof HttpsURLConnection https) {
            https.setSSLSocketFactory(buildPinnedSocketFactory(plugin));
        }
        conn.setDoOutput(true);              // phải đặt TRƯỚC setRequestMethod
        conn.setDoInput(true);
        conn.setUseCaches(false);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type",   "application/json; charset=UTF-8");
        conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
        applyApiKey(conn, plugin); // v5.0.0: bắt buộc — bot.py giờ check X-API-Key, thiếu là bị 403
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bodyBytes);
            os.flush();   // flush đảm bảo bytes được gửi trước khi đọc response
        }
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300)
                ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            // FIX v4.0.9: parse JsonElement trước, check isJsonObject() sau
            try {
                JsonElement elem = gson.fromJson(sb.toString(), JsonElement.class);
                if (elem == null || elem.isJsonNull() || !elem.isJsonObject()) return null;
                return elem.getAsJsonObject();
            } catch (JsonSyntaxException e) {
                return null;
            }
        }
    }

    /**
     * Báo cáo IP + port server lên bot khi khởi động (v4.0.12).
     * v5.0.0: IP truyền vào giờ LÀ IP public thật (PublicIpResolver), không còn là
     * bind-address getServer().getIp() (luôn "0.0.0.0", vô dụng để block server spam).
     * Thêm http_port (port HTTP server CỦA PLUGIN, khác port chơi) để bot có thể thử
     * PUSH thẳng reward/config tới plugin (bỏ qua ngrok hoàn toàn) khi port này mở công khai.
     * POST /api/report-server-ip {server_id, guild_id, ip, port, http_port, address, mode, plugin_version}
     */
    public void reportServerIp(String ip, int port, int httpPort) {
        JsonObject body = new JsonObject();
        body.addProperty("server_id",      getServerId());
        body.addProperty("guild_id",       getGuildId());
        body.addProperty("ip",             ip);
        body.addProperty("port",           port);
        body.addProperty("http_port",      httpPort);
        body.addProperty("address",        ip + ":" + port);
        body.addProperty("mode",           plugin.isStandaloneMode() ? "standalone" : "connected");
        body.addProperty("plugin_version", plugin.getDescription().getVersion());
        try { postJson("/api/report-server-ip", body); }
        catch (Exception e) { plugin.getLogger().log(Level.FINER, "[Bot] reportServerIp: " + e.getMessage()); }
    }

    public void pingBot() {
        String sid = getServerId();
        if (sid.isEmpty()) return;
        JsonObject body = new JsonObject();
        body.addProperty("server_id", sid);
        try {
            postJson("/api/pending-rewards", body, true);
        } catch (Exception ignored) {
        }
    }

    // ─── Config helpers ───────────────────────────────────────────────────────

    private String getServerId() {
        return plugin.getConfig().getString("server-id", "").trim();
    }

    private String getGuildId() {
        return plugin.getConfig().getString("guild-id", "").trim();
    }
}
