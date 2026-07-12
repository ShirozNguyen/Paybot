package com.naptien.managers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.naptien.NapTienPlugin;
import com.naptien.managers.RewardDispatcher;
import fi.iki.elonen.NanoHTTPD;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.logging.Level;

public class PluginHttpServer extends NanoHTTPD {

    private final NapTienPlugin plugin;
    private final Gson gson = new Gson();

    public PluginHttpServer(NapTienPlugin plugin) {
        super(plugin.getConfig().getInt("plugin-port", 25580));
        this.plugin = plugin;
    }

    public void start() throws IOException {
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        plugin.getLogger().info("[PayBot] HTTP server running on port: " + getListeningPort());
    }

    public void stop() {
        super.stop();
        plugin.getLogger().info("[PayBot] HTTP server stopped.");
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri    = session.getUri();
        Method method = session.getMethod();

        try {
            // v5.0.3: TRƯỚC ĐÂY chiều bot → plugin CHỈ xác thực bằng server_id khớp
            // (isValidRequest) — ai biết được server_id (có thể đoán/rò rỉ) đều giả
            // mạo được request từ "bot" (fake reward-config, fake bot-url trỏ sang
            // server độc hại, v.v.). Thêm check X-API-Key — CÙNG hằng số 2 chiều đã
            // dùng sẵn (chiều plugin→bot vốn đã check key này từ v5.0.0). Trừ
            // /api/sepay-ipn (SePay gọi vào, không biết key nội bộ này).
            if (!uri.equals("/api/sepay-ipn")) {
                String expected = plugin.getConfig().getString("bot-api-key", BotHttpClient.DEFAULT_API_KEY).trim();
                String received = session.getHeaders().getOrDefault("x-api-key", "").trim();
                if (received.isEmpty() || !received.equals(expected)) {
                    plugin.getLogger().warning("[PayBot] Request tới " + uri + " thiếu/sai X-API-Key, từ chối.");
                    return jsonResponse(Response.Status.FORBIDDEN, "{\"ok\":false,\"msg\":\"Invalid API key\"}");
                }
            }
            switch (uri) {
                case "/execute-reward":  return handleExecuteReward(session, method);
                case "/bot-disconnect":  return handleBotDisconnect(session, method);
                case "/receive-config":  return handleReceiveConfig(session, method);
                case "/check-online":    return handleCheckOnline(session, method); // v5.0.0
                case "/api/sepay-ipn":   return handleSepayIpn(session, method);
                default:                 return jsonResponse(Response.Status.NOT_FOUND, "{\"ok\":false}");
            }
        } catch (Exception e) {
            NotificationManager.warn(plugin, "http-error", "[PayBot] HTTP handler error: " + e.getMessage(), e);
            return jsonResponse(Response.Status.INTERNAL_ERROR, "{\"ok\":false}");
        }
    }

    // ─── /execute-reward ──────────────────────────────────────────────────────

    private Response handleExecuteReward(IHTTPSession session, Method method) throws Exception {
        if (method != Method.POST) return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, "{\"ok\":false}");
        JsonObject data = readBody(session);
        if (!isValidRequest(data)) {
            NotificationManager.warn(plugin, "http-error", "[PayBot] execute-reward: server_id mismatch, ignored.");
            return jsonResponse(Response.Status.FORBIDDEN, "{\"ok\":false,\"msg\":\"invalid server_id\"}");
        }
        String playerName = data.get("player_name").getAsString();
        String rewardAmt  = data.get("reward_amount").getAsString();
        String denomVnd   = data.has("denom")      ? data.get("denom").getAsString()      : null;
        String invoiceId  = data.has("invoice_id") ? data.get("invoice_id").getAsString() : null;
        String type       = data.has("type")       ? data.get("type").getAsString()        : "card";
        String rewardId    = data.has("reward_id") ? data.get("reward_id").getAsString()
                : (invoiceId != null ? invoiceId : java.util.UUID.randomUUID().toString());
        String discordUid  = data.has("discord_user_id") && !data.get("discord_user_id").isJsonNull()
                ? data.get("discord_user_id").getAsString() : "";
        boolean isBank    = "bank".equalsIgnoreCase(type);

        String rawCmd = "";
        if (data.has("reward_cmd") && !data.get("reward_cmd").isJsonNull())
            rawCmd = data.get("reward_cmd").getAsString().trim();

        java.util.List<String> rawCmds;
        if (!rawCmd.isEmpty()) {
            rawCmds = java.util.List.of(rawCmd);
        } else if (denomVnd != null && !denomVnd.isEmpty()) {
            // v5.0.0: hỗ trợ multi-cmd (tối đa 10) đọc từ config local nếu bot không gửi sẵn
            try {
                rawCmds = RewardDispatcher.resolveRewardCmds(plugin, Integer.parseInt(denomVnd.trim()), type);
            } catch (NumberFormatException e) {
                rawCmds = java.util.List.of();
            }
        } else {
            String fb = plugin.getConfig().getString(isBank ? "reward-command-bank" : "reward-command-card", "").trim();
            if (fb.isEmpty()) fb = plugin.getConfig().getString("reward-command", "").trim();
            rawCmds = fb.isEmpty() ? java.util.List.of() : java.util.List.of(fb);
        }

        if (rawCmds.isEmpty()) {
            NotificationManager.warn(plugin, "reward-invalid",
                    "[PayBot] No reward-command configured for type=" + type);
            return jsonResponse(Response.Status.BAD_REQUEST, "{\"ok\":false,\"msg\":\"reward-command not configured\"}");
        }

        final java.util.List<String> fCmds = rawCmds;
        Bukkit.getScheduler().runTask(plugin, () ->
                RewardDispatcher.dispatchOrQueue(plugin, rewardId, playerName, fCmds, rewardAmt,
                        denomVnd, type, invoiceId, discordUid));
        return jsonResponse(Response.Status.OK, "{\"ok\":true}");
    }

    // ─── /receive-config ──────────────────────────────────────────────────────

    private Response handleReceiveConfig(IHTTPSession session, Method method) throws Exception {
        if (method != Method.POST) return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, "{\"ok\":false}");
        JsonObject data = readBody(session);
        if (!isValidRequest(data)) {
            NotificationManager.warn(plugin, "http-error", "[PayBot] receive-config: server_id mismatch, ignored.");
            return jsonResponse(Response.Status.FORBIDDEN, "{\"ok\":false,\"msg\":\"invalid server_id\"}");
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            int count = 0;
            // v5.0.4: bot_url — bot tự gửi địa chỉ HTTPS mới sau mỗi lần restart
            // (thay ngrok), plugin lưu và áp dụng NGAY, không cần admin sửa tay.
            if (data.has("bot_url") && !data.get("bot_url").getAsString().isBlank()) {
                String newUrl = data.get("bot_url").getAsString().trim();
                plugin.getConfig().set("bot-url", newUrl);
                count++;
                plugin.getLogger().info("[PayBot] bot-url cập nhật: " + newUrl);
            }
            if (data.has("card_rewards") && data.get("card_rewards").isJsonObject())
                count += applyRewards(data.getAsJsonObject("card_rewards"), "denom-rewards-card");
            if (data.has("bank_rewards") && data.get("bank_rewards").isJsonObject())
                count += applyRewards(data.getAsJsonObject("bank_rewards"), "denom-rewards-bank");
            plugin.forceConfigRefresh("nhận config mới từ bot");
            plugin.getLogger().info("[PayBot] Config received and applied (" + count + " entries).");
        });
        return jsonResponse(Response.Status.OK, "{\"ok\":true}");
    }

    private int applyRewards(JsonObject rewards, String section) {
        int count = 0;
        for (String key : rewards.keySet()) {
            try {
                Integer.parseInt(key);
                JsonObject entry = rewards.getAsJsonObject(key);
                String amt = entry.has("amt") ? entry.get("amt").getAsString() : "";
                String cmd = entry.has("cmd") ? entry.get("cmd").getAsString() : "";
                if (!amt.isEmpty()) plugin.getConfig().set(section + "." + key + ".amt", amt);
                if (!cmd.isEmpty()) plugin.getConfig().set(section + "." + key + ".cmd", cmd);
                count++;
            } catch (Exception ignored) {}
        }
        return count;
    }

    // ─── /bot-disconnect ──────────────────────────────────────────────────────

    private Response handleBotDisconnect(IHTTPSession session, Method method) throws Exception {
        if (method != Method.POST) return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, "{\"ok\":false}");
        JsonObject data = readBody(session);
        if (!isValidRequest(data)) {
            NotificationManager.warn(plugin, "http-error", "[PayBot] bot-disconnect: server_id mismatch, ignored.");
            return jsonResponse(Response.Status.FORBIDDEN, "{\"ok\":false,\"msg\":\"invalid server_id\"}");
        }
        NotificationManager.log(plugin, "bot-disconnect", "[PayBot] Bot requested disconnect.");
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getConfig().set("guild-id",            "");
            plugin.getConfig().set("server-id",           "");
            plugin.getConfig().set("reward-command",      "");
            plugin.getConfig().set("reward-command-card", "");
            plugin.getConfig().set("reward-command-bank", "");
            plugin.saveConfig();
            NotificationManager.broadcast(plugin, "bot-disconnect", "§c[PayBot] §fHệ thống tự động đã ngắt kết nối.");
        });
        return jsonResponse(Response.Status.OK, "{\"ok\":true}");
    }

    // ─── /api/sepay-ipn ───────────────────────────────────────────────────────

    private Response handleSepayIpn(IHTTPSession session, Method method) throws Exception {
        if (method != Method.POST) return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, "{\"success\":false}");
        JsonObject data = readBody(session);
        plugin.getLogger().info("[PayBot] SePay webhook received: " + data);

        if (!"in".equals(data.has("transferType") ? data.get("transferType").getAsString() : ""))
            return jsonResponse(Response.Status.OK, "{\"success\":true}");

        int transferAmount;
        try { transferAmount = (int) Double.parseDouble(data.get("transferAmount").getAsString()); }
        catch (Exception e) { return jsonResponse(Response.Status.OK, "{\"success\":true}"); }
        if (transferAmount <= 0) return jsonResponse(Response.Status.OK, "{\"success\":true}");

        String content = (data.has("content") ? data.get("content").getAsString() : "").toUpperCase();
        String code    = (data.has("code")    ? data.get("code").getAsString()    : "").toUpperCase();

        String configuredKey  = plugin.getConfig().getString("sepay.secret-key", "").trim();
        boolean forwardedByBot = "true".equalsIgnoreCase(
                session.getHeaders().getOrDefault("x-forwarded-by-paybot", ""));

        if (!configuredKey.isEmpty() && !forwardedByBot) {
            String authHeader  = session.getHeaders().getOrDefault("authorization", "");
            String receivedKey = "";
            if (authHeader.toLowerCase().startsWith("apikey "))  receivedKey = authHeader.substring(7).trim();
            else if (authHeader.toLowerCase().startsWith("bearer ")) receivedKey = authHeader.substring(7).trim();
            if (receivedKey.isEmpty()) receivedKey = session.getHeaders().getOrDefault("x-api-key", "").trim();
            if (!receivedKey.equals(configuredKey)) {
                NotificationManager.warn(plugin, "sepay-error", "[PayBot] SePay IPN: auth failed.");
                return jsonResponse(Response.Status.UNAUTHORIZED, "{\"success\":false,\"error\":\"Unauthorized\"}");
            }
        }

        final String fContent = content, fCode = code;
        final int    fAmount  = transferAmount;
        boolean[] matchedFlag = {false};
        Bukkit.getScheduler().runTask(plugin, () -> {
            matchedFlag[0] = processSePayWebhook(fContent, fCode, fAmount);
        });
        String matchedStr = forwardedByBot ? ",\"matched\":true" : "";
        return jsonResponse(Response.Status.OK, "{\"success\":true" + matchedStr + "}");
    }

    private boolean processSePayWebhook(String content, String code, int transferAmount) {
        LocalOrderManager lom = plugin.getLocalOrderManager();
        LocalOrderManager.BankOrder matched = lom.matchPendingByContent(content, code);
        if (matched == null) {
            NotificationManager.warn(plugin, "sepay-error", "[PayBot] SePay IPN: no matching order for content='" + content + "'");
            return false;
        }
        if (transferAmount < matched.amount) {
            NotificationManager.warn(plugin, "sepay-error", "[PayBot] SePay IPN: underpaid! expected=" + matched.amount + " got=" + transferAmount);
            return false;
        }
        plugin.getLogger().info("[PayBot] SePay IPN PAID: invoice=" + matched.invoiceId + " player=" + matched.playerName);

        // v5.0.2 FIX: Auto-dispatch reward ngay — không cần admin /topuplist để duyệt.
        java.util.List<String> rewardCmds = RewardDispatcher.resolveRewardCmds(plugin, matched.amount, "bank");
        if (!rewardCmds.isEmpty()) {
            String rewardAmt = RewardDispatcher.computeRewardAmt(plugin, matched.amount, "bank");
            lom.updateBankStatus(matched.invoiceId, LocalOrderManager.BANK_APPROVED);
            boolean wasOnline = RewardDispatcher.dispatchOrQueue(plugin, matched.invoiceId, matched.playerName,
                    rewardCmds, rewardAmt, String.valueOf(matched.amount), "bank", matched.invoiceId, "");
            NotificationManager.notifyAdmins(plugin, "bank-payment-received",
                    "§a[PayBot] §fĐơn §b#" + matched.invoiceId + " §fcủa §e" + matched.playerName
                    + " §fthanh toán §f" + formatVnd(matched.amount) + " VND — §athưởng đã tự giao"
                    + (wasOnline ? "" : " §7(offline → nhận khi join lại)") + "§f. §7(SePay IPN)");
        } else {
            // Chưa cấu hình lệnh thưởng → vẫn set PAID để admin tự /approve sau
            lom.updateBankStatus(matched.invoiceId, LocalOrderManager.BANK_PAID);
            NotificationManager.notifyAdmins(plugin, "bank-payment-received",
                    "§a[PayBot] §fĐơn §b#" + matched.invoiceId + " §fcủa §e" + matched.playerName
                    + " §fđã §athanh toán §f" + formatVnd(matched.amount)
                    + " VND§f. §7Chưa cấu hình lệnh thưởng — dùng §e/approve " + matched.invoiceId
                    + " §7sau khi cấu hình /chinhsuamenhgianap.");
            Player p = Bukkit.getPlayerExact(matched.playerName);
            if (p != null && p.isOnline()) {
                p.sendMessage(NapTienPlugin.f("§a§l[PayBot] §r§aĐã nhận thanh toán " + formatVnd(matched.amount) + " VND! Đang chờ admin cấu hình thưởng..."));
                RewardEffectManager.notifyPaymentReceived(plugin, p, matched.amount);
            }
        }
        return true;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private boolean isValidRequest(JsonObject data) {
        String storedServerId = plugin.getConfig().getString("server-id", "").trim();
        String storedGuildId  = plugin.getConfig().getString("guild-id",  "").trim();
        if (!storedServerId.isEmpty()) {
            String incoming = data.has("server_id") ? data.get("server_id").getAsString().trim() : "";
            return storedServerId.equals(incoming);
        }
        if (!storedGuildId.isEmpty()) {
            String incoming = data.has("guild_id") ? data.get("guild_id").getAsString().trim() : "";
            return storedGuildId.equals(incoming);
        }
        return false;
    }

    // ─── /check-online (v5.0.0) ─────────────────────────────────────────────
    // Thay cho việc plugin liên tục PUSH danh sách online lên bot "để dự phòng lúc cần"
    // (tốn request dù phần lớn thời gian bot chẳng dùng tới) — giờ bot chỉ hỏi đúng lúc
    // THỰC SỰ cần biết: ngay khi player chạy /napbank hoặc /napthe TỪ DISCORD (bot cần
    // biết player có đang online không + đang ở server nào để route reward đúng chỗ).
    // Đây mới là model "gọi khi cần" đúng nghĩa — không hỏi thì plugin không cần trả lời.
    private Response handleCheckOnline(IHTTPSession session, Method method) throws Exception {
        if (method != Method.POST) return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, "{\"ok\":false}");
        JsonObject data = readBody(session);
        if (!isValidRequest(data)) {
            return jsonResponse(Response.Status.FORBIDDEN, "{\"ok\":false,\"msg\":\"invalid server_id\"}");
        }
        if (!data.has("player_name")) {
            return jsonResponse(Response.Status.BAD_REQUEST, "{\"ok\":false,\"msg\":\"missing player_name\"}");
        }
        String playerName = data.get("player_name").getAsString();

        // Bukkit API (getPlayerExact, isOnline) KHÔNG an toàn gọi trực tiếp từ thread của
        // NanoHTTPD — phải hỏi qua main thread rồi đợi kết quả (timeout 2s để không treo
        // request mãi nếu main thread đang kẹt vì lý do khác).
        java.util.concurrent.CompletableFuture<Boolean> future = new java.util.concurrent.CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayerExact(playerName);
            future.complete(p != null && p.isOnline());
        });
        boolean online;
        try {
            online = future.get(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            return jsonResponse(Response.Status.INTERNAL_ERROR, "{\"ok\":false,\"msg\":\"timeout\"}");
        }
        return jsonResponse(Response.Status.OK, "{\"ok\":true,\"online\":" + online + "}");
    }

    private JsonObject readBody(IHTTPSession session) throws Exception {
        int contentLength;
        try {
            contentLength = Integer.parseInt(session.getHeaders().getOrDefault("content-length", "0"));
        } catch (NumberFormatException e) {
            NotificationManager.warn(plugin, "http-error", "[PayBot] readBody: Content-Length header không hợp lệ: "
                    + session.getHeaders().get("content-length"));
            NotificationManager.notifyAdmins(plugin, "http-error", "§c[PayBot] Lỗi HTTP: Content-Length không hợp lệ từ "
                    + session.getRemoteIpAddress());
            return new JsonObject();
        }
        if (contentLength <= 0) return new JsonObject();
        byte[] buf = new byte[contentLength];
        int total = 0;
        try {
            while (total < contentLength) {
                int read = session.getInputStream().read(buf, total, contentLength - total);
                if (read == -1) break;
                total += read;
            }
        } catch (IOException e) {
            NotificationManager.warn(plugin, "http-error", "[PayBot] readBody: Lỗi đọc dữ liệu sau " + total
                    + "/" + contentLength + " bytes: " + e.getMessage());
            NotificationManager.notifyAdmins(plugin, "http-error", "§c[PayBot] Lỗi đọc HTTP body (" + total + "/" + contentLength
                    + " bytes). Xem console để biết thêm.");
            throw e;
        }
        if (total < contentLength) {
            NotificationManager.warn(plugin, "http-error", "[PayBot] readBody: Đọc thiếu dữ liệu! Nhận được "
                    + total + "/" + contentLength + " bytes. Có thể mất dữ liệu webhook.");
            NotificationManager.notifyAdmins(plugin, "http-error", "§c[PayBot] §fCảnh báo: Nhận được webhook nhưng thiếu dữ liệu ("
                    + total + "/" + contentLength + " bytes). Kiểm tra console!");
        }
        try {
            return gson.fromJson(new String(buf, 0, total, java.nio.charset.StandardCharsets.UTF_8),
                    JsonObject.class);
        } catch (Exception e) {
            NotificationManager.warn(plugin, "http-error", "[PayBot] readBody: Không parse được JSON: " + e.getMessage()
                    + " | Raw: " + new String(buf, 0, Math.min(total, 200),
                            java.nio.charset.StandardCharsets.UTF_8));
            NotificationManager.notifyAdmins(plugin, "http-error", "§c[PayBot] §fLỗi parse JSON từ webhook. Xem console để biết thêm.");
            throw e;
        }
    }

    private Response jsonResponse(Response.Status status, String body) {
        return newFixedLengthResponse(status, "application/json", body);
    }

    private String formatVnd(int amount) {
        StringBuilder sb = new StringBuilder(String.valueOf(amount));
        int len = sb.length();
        for (int i = len - 3; i > 0; i -= 3) sb.insert(i, '.');
        return sb.toString();
    }
}