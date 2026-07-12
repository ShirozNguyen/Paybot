package com.naptien.managers;

import com.google.gson.*;
import com.naptien.PayBotMod;
import fi.iki.elonen.NanoHTTPD;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * PluginHttpServer (Fabric) — NanoHTTPD server.
 *
 * Nhận:
 *   1. SePay IPN webhook (/api/sepay-ipn) — standalone mode
 *   2. Push reward từ bot (/api/reward-push) — THAY THẾ polling /api/pending-rewards
 *   3. Xác nhận ngân hàng từ bot (/api/bank-paid) — THAY THẾ polling /api/standalone-bank-status
 *   4. Kết quả thẻ từ bot (/api/card-result) — THAY THẾ polling /api/standalone-card-status
 *   5. Ping (/api/ping) — health check nhẹ từ bot (không cần response body phức tạp)
 *
 * Thay đổi v5.0.1 (anti-spam):
 *   - Thêm /api/reward-push, /api/bank-paid, /api/card-result
 *   - Bot chủ động đẩy dữ liệu về plugin thay vì plugin poll liên tục
 *   - Log IP thật của requester (X-Forwarded-For nếu qua ngrok)
 *
 * Thay đổi v5.0.2 (IP detection):
 *   - getClientIp() đọc X-Forwarded-For để lấy IP thật (không phải ngrok relay IP)
 *   - Log real IP khi có request đáng ngờ
 */
public class PluginHttpServer extends NanoHTTPD {

    private final PayBotMod mod;
    private final Gson gson = new Gson();

    public PluginHttpServer(PayBotMod mod) {
        super(mod.getConfig().getInt("plugin-port", 25580));
        this.mod = mod;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri    = session.getUri();
        String method = session.getMethod().name();

        // ── GET /api/ping — health check nhẹ ─────────────────────────────────
        if ("GET".equals(method) && "/api/ping".equals(uri)) {
            return ok("{\"ok\":true,\"version\":\"5.0.6\"}");
        }

        if (!"POST".equals(method)) return ok("{}");

        // ── Parse body ────────────────────────────────────────────────────────
        JsonObject data;
        try {
            Map<String, String> body = new java.util.HashMap<>();
            session.parseBody(body);
            String raw = body.getOrDefault("postData", "");
            if (raw.isEmpty()) raw = readBody(session);
            JsonElement elem = gson.fromJson(raw, JsonElement.class);
            if (elem == null || !elem.isJsonObject()) return err("Invalid JSON");
            data = elem.getAsJsonObject();
        } catch (Exception e) {
            PayBotMod.LOGGER.warn("[PluginHttpServer] Parse error at " + uri + ": " + e.getMessage());
            return err("Parse error");
        }

        // v5.0.5: TRƯỚC ĐÂY chiều bot → mod CHỈ xác thực bằng server_id khớp
        // (verifyServerId) — ai biết được server_id đều giả mạo được request từ
        // "bot". Thêm check X-API-Key — cùng hằng số 2 chiều (chiều mod→bot vốn đã
        // check key này từ v5.0.0). Trừ /api/sepay-ipn (SePay gọi vào trực tiếp).
        if (!uri.equals("/api/sepay-ipn")) {
            String expected = mod.getConfig().getString("bot-api-key", BotHttpClient.DEFAULT_API_KEY).trim();
            String received = session.getHeaders().getOrDefault("x-api-key", "").trim();
            if (received.isEmpty() || !received.equals(expected)) {
                PayBotMod.LOGGER.warn("[PluginHttpServer] Request tới " + uri + " thiếu/sai X-API-Key, từ chối.");
                return err("Invalid API key");
            }
        }

        // ── Routing ───────────────────────────────────────────────────────────
        try {
            return switch (uri) {
                case "/api/sepay-ipn"    -> handleSePay(session, data);
                // v5.0.0: thêm alias "/execute-reward" — bot.py gọi tên này (đồng bộ style Bukkit)
                case "/api/reward-push", "/execute-reward" -> handleRewardPush(session, data);
                case "/api/bank-paid"    -> handleBankPaid(session, data);
                case "/api/card-result"  -> handleCardResult(session, data);
                // v5.0.4 FIX: "/receive-config" TRƯỚC ĐÂY không có case nào — rơi vào
                // default trả ok(true) GIẢ, khiến bot.py tưởng push thành công nhưng
                // KHÔNG CÓ GÌ được áp dụng (silent no-op). Thêm handler thật, đồng bộ
                // đúng behaviour với PluginHttpServer.java bên Bukkit plugin.
                case "/receive-config"   -> handleReceiveConfig(session, data);
                default -> ok("{\"ok\":true}");
            };
        } catch (Exception e) {
            PayBotMod.LOGGER.warn("[PluginHttpServer] " + uri + " error: " + e.getMessage());
            return err("Server error");
        }
    }

    // ─── SePay IPN ────────────────────────────────────────────────────────────

    private Response handleSePay(IHTTPSession session, JsonObject data) {
        String clientIp = getClientIp(session);
        String content  = data.has("content")
                ? data.get("content").getAsString().toUpperCase().trim() : "";
        int amount = data.has("transferAmount") ? data.get("transferAmount").getAsInt() : 0;

        if (content.isEmpty() || amount <= 0) {
            PayBotMod.LOGGER.warn("[SePay] Payload không hợp lệ từ IP " + clientIp
                    + " — content='" + content + "' amount=" + amount);
            return err("Invalid payload");
        }

        PayBotMod.LOGGER.info("[SePay] IPN nhận được: content=" + content
                + " amount=" + amount + " ip=" + clientIp);

        mod.runOnMainThread(() -> {
            java.util.List<LocalOrderManager.BankOrder> pending =
                    mod.getLocalOrderManager().getPendingBankOrders();
            for (LocalOrderManager.BankOrder order : pending) {
                if (content.contains(order.invoiceId.toUpperCase())
                        || order.invoiceId.equalsIgnoreCase(content)) {
                    if (Math.abs(order.amount - amount) < 100) {
                        mod.getLocalOrderManager().updateBankStatus(
                                order.invoiceId, LocalOrderManager.BANK_PAID);
                        PayBotMod.LOGGER.info("[SePay] MATCHED: invoice=" + order.invoiceId
                                + " player=" + order.playerName + " amount=" + amount);
                        mod.notifyAdmins("§a[PayBot] §eSePay §fxác nhận: §e" + order.playerName
                            + " §fđã chuyển §a" + PayBotMod.formatVnd(amount)
                            + " VND§f. /approve để duyệt.");
                        return;
                    }
                }
            }
            PayBotMod.LOGGER.warn("[SePay] NO MATCH: content=" + content
                    + " amount=" + amount + " ip=" + clientIp);
        });
        return ok("{\"success\":true}");
    }

    // ─── Reward push (thay thế /api/pending-rewards polling) ─────────────────
    // Bot gọi endpoint này để đẩy reward về plugin khi có giao dịch thành công.
    // Payload: { server_id, reward_id, player_name, reward_cmd, reward_amount, type }

    private Response handleRewardPush(IHTTPSession session, JsonObject data) {
        if (!verifyServerId(data)) {
            PayBotMod.LOGGER.warn("[RewardPush] server_id không khớp từ IP " + getClientIp(session));
            return err("Wrong server_id");
        }

        String rewardId   = getString(data, "reward_id");
        String playerName = getString(data, "player_name");
        String rawCmd     = getString(data, "reward_cmd");
        int amount = 0;
        try {
            if (data.has("reward_amount"))
                amount = (int) Double.parseDouble(data.get("reward_amount").getAsString());
        } catch (Exception ignored) {}

        if (playerName.isEmpty() || rawCmd.isEmpty()) return err("Missing fields");

        final String fPlayer = playerName;
        final String fCmd    = rawCmd;
        final String fId     = rewardId;
        final int    fAmt    = amount;
        final String fType   = getString(data, "type");

        PayBotMod.LOGGER.info("[RewardPush] Nhận reward: player=" + fPlayer
                + " amount=" + fAmt + " id=" + fId.substring(0, Math.min(8, fId.length())) + "...");

        mod.runOnMainThread(() -> {
            ServerPlayerEntity player = mod.getServer().getPlayerManager().getPlayer(fPlayer);
            if (player != null) {
                mod.executeReward(player, fCmd, fId, false, fAmt);
            } else {
                // Player offline — lưu vào queue
                mod.getOfflineRewardManager().addReward(
                        fId, fPlayer, fCmd, String.valueOf(fAmt),
                        "", fType.isEmpty() ? "bank" : fType, "", "");
                PayBotMod.LOGGER.info("[RewardPush] Player " + fPlayer
                        + " offline — lưu offline queue.");
            }
        });

        return ok("{\"ok\":true}");
    }

    // ─── Receive config push từ bot (v5.0.4 — TRƯỚC ĐÂY KHÔNG TỒN TẠI) ─────────
    // 1. bot_url: bot tự gửi địa chỉ HTTPS mới của nó sau mỗi lần restart (thay ngrok)
    //    — mod lưu và áp dụng NGAY, không cần đợi admin tự vào config sửa tay.
    // 2. card_rewards/bank_rewards: admin sửa lệnh thưởng trên Discord → bot push
    //    thẳng xuống thay vì mod phải tự poll (đồng bộ đúng hành vi Bukkit plugin).
    // Payload: { server_id, bot_url?, card_rewards?, bank_rewards? }
    private Response handleReceiveConfig(IHTTPSession session, JsonObject data) {
        if (!verifyServerId(data)) {
            PayBotMod.LOGGER.warn("[ReceiveConfig] server_id không khớp từ IP " + getClientIp(session));
            return err("Wrong server_id");
        }

        mod.runOnMainThread(() -> {
            int count = 0;
            if (data.has("bot_url") && !data.get("bot_url").getAsString().isBlank()) {
                String newUrl = data.get("bot_url").getAsString().trim();
                mod.getConfig().set("bot-url", newUrl);
                count++;
                PayBotMod.LOGGER.info("[ReceiveConfig] bot-url cập nhật: " + newUrl);
            }
            if (data.has("card_rewards") && data.get("card_rewards").isJsonObject())
                count += applyRewards(data.getAsJsonObject("card_rewards"), "denom-rewards-card");
            if (data.has("bank_rewards") && data.get("bank_rewards").isJsonObject())
                count += applyRewards(data.getAsJsonObject("bank_rewards"), "denom-rewards-bank");
            if (count > 0) {
                mod.getConfig().save();
                PayBotMod.LOGGER.info("[ReceiveConfig] Đã nhận và áp dụng " + count + " thay đổi từ bot.");
            }
        });
        return ok("{\"ok\":true}");
    }

    private int applyRewards(JsonObject rewards, String section) {
        int count = 0;
        for (String key : rewards.keySet()) {
            try {
                Integer.parseInt(key);
                JsonObject entry = rewards.getAsJsonObject(key);
                String amt = entry.has("amt") ? entry.get("amt").getAsString() : "";
                String cmd = entry.has("cmd") ? entry.get("cmd").getAsString() : "";
                if (!amt.isEmpty()) mod.getConfig().set(section + "." + key + ".amt", amt);
                if (!cmd.isEmpty()) mod.getConfig().set(section + "." + key + ".cmd", cmd);
                count++;
            } catch (Exception ignored) {}
        }
        return count;
    }
    // Bot gọi endpoint này khi xác nhận thanh toán ngân hàng.
    // Payload: { server_id, invoice_id }

    private Response handleBankPaid(IHTTPSession session, JsonObject data) {
        if (!verifyServerId(data)) {
            PayBotMod.LOGGER.warn("[BankPaid] server_id không khớp từ IP " + getClientIp(session));
            return err("Wrong server_id");
        }

        String invoiceId = getString(data, "invoice_id");
        if (invoiceId.isEmpty()) return err("Missing invoice_id");

        PayBotMod.LOGGER.info("[BankPaid] Xác nhận từ bot: invoice="
                + invoiceId.substring(0, Math.min(8, invoiceId.length())) + "...");

        mod.runOnMainThread(() ->
                mod.getStandaloneBankPoller().handlePaidFromPush(invoiceId));

        return ok("{\"ok\":true}");
    }

    // ─── Card result push (thay thế /api/standalone-card-status polling) ──────
    // Bot gọi endpoint này khi có kết quả thẻ cào.
    // Payload: { server_id, request_id, status, message }

    private Response handleCardResult(IHTTPSession session, JsonObject data) {
        if (!verifyServerId(data)) {
            PayBotMod.LOGGER.warn("[CardResult] server_id không khớp từ IP " + getClientIp(session));
            return err("Wrong server_id");
        }

        String requestId = getString(data, "request_id");
        String status    = getString(data, "status");
        String message   = getString(data, "message");

        if (requestId.isEmpty() || status.isEmpty()) return err("Missing fields");

        PayBotMod.LOGGER.info("[CardResult] Kết quả từ bot: id="
                + requestId.substring(0, Math.min(8, requestId.length()))
                + "... status=" + status);

        mod.runAsync(() ->
                mod.getStandaloneCardProcessor().notifyCardResultFromPush(requestId, status, message));

        return ok("{\"ok\":true}");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Lấy IP thật của requester.
     * Nếu qua ngrok/reverse proxy, đọc X-Forwarded-For.
     * Không bao giờ trả về chuỗi rỗng — fallback "unknown".
     */
    private String getClientIp(IHTTPSession session) {
        // X-Forwarded-For (ngrok đặt header này)
        String xff = session.getHeaders().get("x-forwarded-for");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim(); // IP gốc là phần tử đầu tiên
        }
        // X-Real-IP
        String xri = session.getHeaders().get("x-real-ip");
        if (xri != null && !xri.isEmpty()) return xri.trim();
        // Direct connection
        String remote = session.getRemoteIpAddress();
        return (remote != null && !remote.isEmpty()) ? remote : "unknown";
    }

    private boolean verifyServerId(JsonObject data) {
        String incomingSid = getString(data, "server_id");
        String ownSid      = mod.getConfig().getString("server-id", "").trim();
        return !ownSid.isEmpty() && ownSid.equals(incomingSid);
    }

    private String getString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull()
                ? obj.get(key).getAsString().trim() : "";
    }

    private Response ok(String json) {
        return newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }

    private Response err(String msg) {
        return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json",
                "{\"success\":false,\"error\":\"" + msg + "\"}");
    }

    private String readBody(IHTTPSession s) throws IOException {
        int len = 0;
        try { len = Integer.parseInt(s.getHeaders().getOrDefault("content-length", "0")); }
        catch (NumberFormatException ignored) {}
        if (len <= 0) return "";
        byte[] buf = new byte[Math.min(len, 1024 * 512)]; // max 512KB
        int read = 0;
        try (InputStream is = s.getInputStream()) {
            while (read < buf.length) {
                int n = is.read(buf, read, buf.length - read);
                if (n < 0) break;
                read += n;
            }
        }
        return new String(buf, 0, read, StandardCharsets.UTF_8);
    }
}
