package com.naptien.managers;

import com.google.gson.*;
import com.naptien.PayBotMod;
import com.naptien.log.LogManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
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
    private static final Map<String,String> SUPPORTED_SITES = DirectCardSubmitHandler.getSupportedSites();

    private final PayBotMod mod;
    private final Gson gson = new Gson();

    public StandaloneCardProcessor(PayBotMod mod) { this.mod = mod; }

    public static Map<String,String> getSupportedSites() { return SUPPORTED_SITES; }

    /** Gọi từ /ok command — nộp thẻ lên bot standalone hoặc gửi trực tiếp 5x POST -> GET. */
    public void submitCard(ServerPlayer player, CardManager.PendingCard card) {
        String site   = mod.getConfig().getString("card-api.site","").trim();
        String[] creds = DirectCardSubmitHandler.resolveCredentials(mod, site);
        String pid    = creds[0];
        String pkey   = creds[1];
        boolean configured = mod.getConfig().getBoolean("card-api.configured", false);

        if (!configured || site.isEmpty() || pid.isEmpty() || pkey.isEmpty()) {
            player.sendSystemMessage(com.naptien.utils.ClickableTextHelper.makeSuggestCommand(
                    "§c[PayBot] §fServer chưa cấu hình Card API. Admin dùng §e/cardsetup §fđể cấu hình.",
                    "/cardsetup",
                    "§eClick để tự động nhập lệnh /cardsetup"
            ));
            return;
        }
        player.sendSystemMessage(Component.literal("§a[PayBot] §fĐang gửi thẻ lên hệ thống..."));

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

        // [FIX GỐC — audit v5.5.5 Part 53] TRƯỚC ĐÂY hàm này CHỈ có 1 đường duy nhất: relay
        // qua bot ("if (botUrl.isEmpty()) return;" — không làm gì cả nếu rỗng). NGHĨA LÀ: 1
        // admin standalone THẬT (đã /cardsetup xong nhưng KHÔNG có bot-url — đúng trạng thái
        // universal từ khi tắt bot-connected mode) bấm nạp thẻ thì KHÔNG CÓ GÌ XẢY RA — thẻ
        // không được gửi đi đâu cả, không lỗi, không thông báo, im lặng tuyệt đối.
        // Nguyên nhân: DirectCardSubmitHandler.submitDirectly() (gọi thẳng web thứ 3, không
        // cần bot) đã được viết đầy đủ (retry 5×POST+5×GET có backoff) NHƯNG chưa từng được
        // gọi ở bất kỳ đâu trong toàn bộ codebase (xác nhận qua grep toàn project) — hàm sống
        // duy nhất luôn là nhánh relay-qua-bot. Từ khi bot-connected mode bị tắt vĩnh viễn
        // (xem PayBotMod.isStandaloneMode()), đây KHÔNG còn là edge-case nữa mà là đường DUY
        // NHẤT admin nào cũng đi qua — bắt buộc phải nối vào, không thể để dead code.
        if (botUrl.isEmpty()) {
            if (site.isEmpty() || pid.isEmpty() || pkey.isEmpty()) {
                PayBotMod.LOGGER.warn("[CardProcessor] submitAndPoll: standalone nhưng card-api "
                        + "chưa cấu hình đủ (site/partner-id/partner-key) — dùng /cardsetup trước.");
                mod.getLocalOrderManager().markCardConnectionError(requestId, true);
                return;
            }
            JsonObject result = com.naptien.managers.DirectCardSubmitHandler.submitDirectly(
                    site, pid, pkey, telco, denom, cardCode, cardSerial, requestId);
            if (result == null) {
                // Thất bại toàn bộ sau 5×POST+5×GET (đã retry đầy đủ bên trong submitDirectly).
                mod.getLocalOrderManager().markCardConnectionError(requestId, true);
                mod.notifyAdmins("§c[PayBot] §fGửi thẻ trực tiếp thất bại (hết lượt retry) cho §e"
                        + playerName + "§f, mã thẻ requestId=" + requestId.substring(0, 8) + "...");
                return;
            }
            mod.getLocalOrderManager().markCardConnectionError(requestId, false);
            String status  = result.has("status")  ? result.get("status").getAsString()  : "";
            String message = result.has("message") ? result.get("message").getAsString() : "";
            // [GIỚI HẠN ĐÃ BIẾT — nói rõ, không giấu] Nếu status trả về là CARD_PROCESSING
            // ("99" — web thứ 3 nhận đơn nhưng chưa xử lý xong ngay), luồng relay-qua-bot CŨ
            // có thể còn 1 bước "hỏi lại sau" riêng phía bot.py (không có trong codebase Java
            // này nên KHÔNG THỂ xác minh chính xác tham số/API "check status" — không đoán mò
            // theo yêu cầu). Ở đây xử lý AN TOÀN: báo admin biết để tự /approve khi web thứ 3
            // xử lý xong, KHÔNG tự động resubmit lại thẻ (resubmit thẻ đã nộp có rủi ro bị web
            // thứ 3 trả về "thẻ đã sử dụng" nếu họ không coi lần gọi lại là idempotent).
            if (LocalOrderManager.CARD_PROCESSING.equals(status)) {
                PayBotMod.LOGGER.warn("[CardProcessor] Thẻ requestId=" + requestId.substring(0, 8)
                        + "... đang ở trạng thái PROCESSING từ web thứ 3 — cần admin tự kiểm tra "
                        + "lại sau và /approve thủ công (không tự động resubmit).");
            }
            if (!status.isEmpty()) {
                notifyCardResultFromPush(requestId, status, message);
            }
            return;
        }

        attemptSubmitAndPollViaBot(requestId, playerName, telco, denom, cardCode, cardSerial, botUrl, sid, site, pid, pkey);
    }

    /**
     * [DEAD CODE — Bot-connected mode đã tắt] Đường relay-qua-bot CŨ — GIỮ NGUYÊN nguyên vẹn,
     * chỉ tách ra thành method riêng để submitAndPoll() có thể rẽ nhánh rõ ràng. Chỉ còn chạy
     * được nếu server nào đó CHỦ ĐỘNG set "bot-url" dù guild-id rỗng (edge-case hiếm, không
     * phải luồng chính thức nào của tính năng "kết nối bot" — connect command đã bị chặn hoàn
     * toàn nên guild-id không thể tự có giá trị nữa, nhưng bot-url là field riêng nên về lý
     * thuyết admin vẫn có thể tự tay điền — giữ nhánh này chạy được cho trường hợp đó thay vì
     * chặn cứng, vì đây không phải "liên kết Discord bot" theo đúng nghĩa đã bị tắt).
     */
    private void attemptSubmitAndPollViaBot(String requestId, String playerName, String telco,
                               int denom, String cardCode, String cardSerial,
                               String botUrl, String sid, String site, String pid, String pkey) {

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
            ServerPlayer p = mod.getServer().getPlayerList().getPlayerByName(playerName);
            if (p != null) p.sendSystemMessage(Component.literal("§a[PayBot] §fThẻ đã được gửi! Đang trong quá trình xử lý..."));
            notifyAdminCard(requestId, playerName, denom);
        } catch (Exception e) {
            mod.getLocalOrderManager().markCardConnectionError(requestId, true);
            PayBotMod.LOGGER.warn("[CardProcessor] submit error: " + e.getMessage());
        }
    }

    public void pollPendingCards() {
        // [DEAD CODE — Bot-connected mode đã tắt] Cùng lý do như StandaloneBankPoller.
        // pollPendingOrders() — thêm gate isStandaloneMode() tường minh, không chỉ dựa vào
        // "bot-url" rỗng hay không (server upgrade có thể còn sót bot-url cũ trong config).
        if (mod.isStandaloneMode()) return;
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
                    ServerPlayer p = mod.getServer().getPlayerList().getPlayerByName(playerName);
                    if (p != null) {
                        p.sendSystemMessage(Component.literal("§a[PayBot] §fThẻ §a" + order.telco + " §a"
                                + PayBotMod.formatVnd(order.denom) + " VND §fthành công! Đang chờ admin cấu hình thưởng..."));
                        mod.runRewardEffect(p, order.denom);
                    }
                });
            }
            return;
        }

        // Các trạng thái lỗi: chỉ thông báo
        mod.runOnMainThread(() -> {
            ServerPlayer p = mod.getServer().getPlayerList().getPlayerByName(playerName);
            if (p == null) return;
            switch (status) {
                case LocalOrderManager.CARD_WRONG_DENOM ->
                    p.sendSystemMessage(Component.literal("§c[PayBot] §fSai mệnh giá thẻ! Thẻ " + order.denom/1000 + "k không đúng."));
                case LocalOrderManager.CARD_USED ->
                    p.sendSystemMessage(Component.literal("§c[PayBot] §fThẻ đã được sử dụng trước đó."));
                case LocalOrderManager.CARD_WRONG ->
                    p.sendSystemMessage(Component.literal("§c[PayBot] §fThẻ sai hoặc không hợp lệ."));
                default ->
                    p.sendSystemMessage(Component.literal("§c[PayBot] §fKết quả: " + message));
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
