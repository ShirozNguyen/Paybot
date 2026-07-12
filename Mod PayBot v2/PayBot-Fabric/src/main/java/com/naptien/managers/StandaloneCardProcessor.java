package com.naptien.managers;

import com.google.gson.*;
import com.naptien.PayBotMod;
import com.naptien.log.LogManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * StandaloneCardProcessor (Fabric) — nộp thẻ cào qua bot standalone API.
 * retryConnectionErrors() chạy mỗi 30s vô hạn lần.
 * Changelog: v4.1.0-fabric
 */
public class StandaloneCardProcessor {

    private static final int TIMEOUT_MS = 8_000;
    private static final Map<String,String> SUPPORTED_SITES = new LinkedHashMap<>();
    static {
        SUPPORTED_SITES.put("thesieure.com",   "https://thesieure.com/chargingws/v2");
        SUPPORTED_SITES.put("gachthepro.com",  "https://gachthepro.com/chargingws/v2");
        SUPPORTED_SITES.put("gachthefast.com", "https://gachthefast.com/chargingws/v2");
        SUPPORTED_SITES.put("gachthe1s.com",   "https://gachthe1s.com/chargingws/v2");
        // gachthefast2.com KHÔNG được hỗ trợ
    }

    private final PayBotMod mod;
    private final Gson gson = new Gson();

    public StandaloneCardProcessor(PayBotMod mod) { this.mod = mod; }

    public static Map<String,String> getSupportedSites() { return SUPPORTED_SITES; }

    /** Gọi từ /ok command — nộp thẻ lên bot standalone. */
    public void submitCard(ServerPlayerEntity player, CardManager.PendingCard card) {
        String site   = mod.getConfig().getString("card-api.site","").trim();
        String pid    = mod.getConfig().getString("card-api.partner-id","").trim();
        String pkey   = mod.getConfig().getString("card-api.partner-key","").trim();
        boolean configured = mod.getConfig().getBoolean("card-api.configured", false);

        if (!configured || site.isEmpty() || pid.isEmpty() || pkey.isEmpty()) {
            player.sendMessage(Text.literal("§c[PayBot] §fServer chưa cấu hình Card API. Admin dùng §e/cardsetup §fđể cấu hình."));
            return;
        }
        player.sendMessage(Text.literal("§a[PayBot] §fĐang gửi thẻ lên hệ thống..."));

        String requestId = UUID.randomUUID().toString();
        mod.getLocalOrderManager().createCardOrder(requestId, player.getName().getString(),
                card.telco, card.denom, card.cardCode, card.cardSerial);
        mod.getCardManager().clearPending(player.getName().getString());

        mod.runAsync(() -> submitAndPoll(requestId, player.getName().getString(),
                card.telco, card.denom, card.cardCode, card.cardSerial));
    }

    public void submitAndPoll(String requestId, String playerName, String telco,
                               int denom, String cardCode, String cardSerial) {
        String botUrl = mod.getConfig().getString("bot-url","").trim();
        String sid    = mod.getConfig().getString("server-id","");
        String site   = mod.getConfig().getString("card-api.site","");
        String pid    = mod.getConfig().getString("card-api.partner-id","");
        String pkey   = mod.getConfig().getString("card-api.partner-key","");
        if (botUrl.isEmpty()) return;

        JsonObject body = new JsonObject();
        body.addProperty("server_id",   sid);
        body.addProperty("request_id",  requestId);
        body.addProperty("player_name", playerName);
        body.addProperty("telco",       telco);
        body.addProperty("denom",       denom);
        body.addProperty("card_code",   cardCode);
        body.addProperty("card_serial", cardSerial);
        body.addProperty("api_site",    site);
        body.addProperty("partner_id",  pid);
        body.addProperty("partner_key", pkey);

        try {
            JsonObject resp = postJson(botUrl + "/api/standalone-card-submit", body);
            if (resp == null) {
                mod.getLocalOrderManager().markCardConnectionError(requestId, true);
                return;
            }
            mod.getLocalOrderManager().incrementCardSubmitAttempts(requestId);
            mod.getLocalOrderManager().markCardConnectionError(requestId, false);
            ServerPlayerEntity p = mod.getServer().getPlayerManager().getPlayer(playerName);
            if (p != null) p.sendMessage(Text.literal("§a[PayBot] §fThẻ đã được gửi! Đang trong quá trình xử lý..."));
            notifyAdminCard(requestId, playerName, denom);
        } catch (Exception e) {
            mod.getLocalOrderManager().markCardConnectionError(requestId, true);
            PayBotMod.LOGGER.warn("[CardProcessor] submit error: " + e.getMessage());
        }
    }

    public void pollPendingCards() {
        String botUrl = mod.getConfig().getString("bot-url","").trim();
        if (botUrl.isEmpty()) return;
        List<LocalOrderManager.CardOrder> processing = mod.getLocalOrderManager().getProcessingCardOrders();
        if (processing.isEmpty()) return;

        String sid = mod.getConfig().getString("server-id","");
        JsonObject body = new JsonObject();
        body.addProperty("server_id", sid);
        JsonArray ids = new JsonArray();
        for (LocalOrderManager.CardOrder o : processing) ids.add(o.requestId);
        body.add("request_ids", ids);

        try {
            JsonObject resp = postJson(botUrl + "/api/standalone-card-status", body);
            if (resp == null || !resp.has("results")) return;
            JsonObject results = resp.getAsJsonObject("results");
            for (Map.Entry<String,JsonElement> e : results.entrySet()) {
                if (e.getValue().isJsonNull()) continue;
                JsonObject r = e.getValue().isJsonObject() ? e.getValue().getAsJsonObject() : null;
                if (r == null) continue;
                String status  = r.has("status")  ? r.get("status").getAsString()  : "";
                String message = r.has("message") ? r.get("message").getAsString() : "";
                if (!status.isEmpty() && !LocalOrderManager.CARD_PROCESSING.equals(status)) {
                    notifyCardResult(e.getKey(), status, message);
                }
            }
        } catch (Exception e) {
            PayBotMod.LOGGER.debug("[CardProcessor] pollPendingCards: " + e.getMessage());
        }
    }

    private void notifyCardResult(String requestId, String status, String message) {
        notifyCardResultFromPush(requestId, status, message);
    }

    /**
     * Xử lý kết quả thẻ từ bot — gọi từ PluginHttpServer /api/card-result.
     * Bot push về thay vì plugin poll /api/standalone-card-status (v5.0.1 anti-spam).
     */
    public void notifyCardResultFromPush(String requestId, String status, String message) {
        LocalOrderManager.CardOrder order = mod.getLocalOrderManager().getCardOrder(requestId);
        if (order == null) return;
        mod.getLocalOrderManager().updateCardStatus(requestId, status, message);
        String playerName = order.playerName;

        if (LocalOrderManager.CARD_SUCCESS.equals(status)) {
            // v5.0.2 FIX: Auto-dispatch reward ngay — không cần admin /approve thủ công.
            List<String> rewardCmds = mod.resolveRewardCmds(order.denom, "card");
            if (!rewardCmds.isEmpty()) {
                String rewardAmt = mod.computeRewardAmt(order.denom, "card");
                // Update APPROVED trước khi dispatch (tránh double-approve nếu ai đó gọi /approve ngay)
                mod.getLocalOrderManager().updateCardStatus(requestId, LocalOrderManager.CARD_APPROVED, message);
                mod.runOnMainThread(() -> {
                    boolean wasOnline = mod.dispatchOrQueueReward(
                            requestId, playerName, rewardCmds, rewardAmt,
                            String.valueOf(order.denom), "card");
                    mod.notifyAdmins("§a[PayBot] §e" + playerName + " §fnạp thẻ §a" + order.telco
                            + " §a" + PayBotMod.formatVnd(order.denom) + "VND §f— thưởng tự giao"
                            + (wasOnline ? "" : " §7(offline → nhận khi join lại)") + "§f.");
                    // v5.1.0: nếu bot-connected → notify Discord
                    if (!mod.isStandaloneMode()) {
                        mod.runAsync(() -> mod.getBotHttpClient().notifyCardResult(
                                requestId, playerName, order.telco, order.denom, true, ""));
                    }
                });
            } else {
                // Chưa cấu hình lệnh thưởng → báo admin /approve sau khi cấu hình
                mod.notifyAdmins("§a[PayBot] §e" + playerName + " §fnạp thẻ §a" + order.telco
                        + " §a" + PayBotMod.formatVnd(order.denom) + "VND §fthành công! "
                        + "§7Chưa cấu hình lệnh thưởng — dùng §e/approve " + requestId.substring(0, 8)
                        + "... §7sau khi cấu hình /chinhsuamenhgianap.");
                mod.runOnMainThread(() -> {
                    ServerPlayerEntity p = mod.getServer().getPlayerManager().getPlayer(playerName);
                    if (p != null) {
                        p.sendMessage(Text.literal("§a[PayBot] §fThẻ §a" + order.telco + " §a"
                                + PayBotMod.formatVnd(order.denom) + " VND §fthành công! Đang chờ admin cấu hình thưởng..."));
                        mod.runRewardEffect(p, order.denom);
                    }
                });
            }
            return;
        }

        // Các trạng thái lỗi: chỉ thông báo
        mod.runOnMainThread(() -> {
            ServerPlayerEntity p = mod.getServer().getPlayerManager().getPlayer(playerName);
            if (p == null) return;
            switch (status) {
                case LocalOrderManager.CARD_WRONG_DENOM ->
                    p.sendMessage(Text.literal("§c[PayBot] §fSai mệnh giá thẻ! Thẻ " + order.denom/1000 + "k không đúng."));
                case LocalOrderManager.CARD_USED ->
                    p.sendMessage(Text.literal("§c[PayBot] §fThẻ đã được sử dụng trước đó."));
                case LocalOrderManager.CARD_WRONG ->
                    p.sendMessage(Text.literal("§c[PayBot] §fThẻ sai hoặc không hợp lệ."));
                default ->
                    p.sendMessage(Text.literal("§c[PayBot] §fKết quả: " + message));
            }
        });
    }

    private void notifyAdminCard(String requestId, String playerName, int denom) {
        mod.notifyAdmins("§7[PayBot] Thẻ §e" + playerName + " §7mệnh giá §e" + PayBotMod.formatVnd(denom)
            + " VND §7đang chờ API xử lý. (id=" + requestId.substring(0,8) + "...)");
    }

    public void retryConnectionErrors() {
        List<LocalOrderManager.CardOrder> errors = mod.getLocalOrderManager().getConnectionErrorCardOrders();
        if (errors.isEmpty()) return;
        PayBotMod.LOGGER.info("[CardProcessor] Retry " + errors.size() + " đơn lỗi mạng...");
        for (LocalOrderManager.CardOrder o : errors) {
            mod.getLocalOrderManager().markCardConnectionError(o.requestId, false);
            mod.runAsync(() -> submitAndPoll(o.requestId, o.playerName, o.telco, o.denom, o.cardCode, o.cardSerial));
        }
    }

    public void recoverUnsubmitted() {
        PayBotMod.LOGGER.info("[CardProcessor] Startup recovery: kiểm tra card orders...");
        retryConnectionErrors();
    }

    private JsonObject postJson(String urlStr, JsonObject body) throws Exception {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setDoOutput(true); conn.setDoInput(true); conn.setUseCaches(false);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type",   "application/json; charset=UTF-8");
        conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
        BotHttpClient.applyApiKey(conn, mod); // v5.0.0: bắt buộc — bot.py check X-API-Key
        conn.setConnectTimeout(TIMEOUT_MS); conn.setReadTimeout(TIMEOUT_MS);
        try (OutputStream os = conn.getOutputStream()) { os.write(bytes); os.flush(); }
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder(); String ln;
            while ((ln = br.readLine()) != null) sb.append(ln);
            try {
                JsonElement e = gson.fromJson(sb.toString().trim(), JsonElement.class);
                return (e != null && !e.isJsonNull() && e.isJsonObject()) ? e.getAsJsonObject() : null;
            } catch (JsonSyntaxException e) { return null; }
        }
    }
}
