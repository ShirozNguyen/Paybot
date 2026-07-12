package com.naptien.managers;

import com.google.gson.*;
import com.naptien.PayBotMod;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.*;
import java.net.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * StandaloneBankPoller (Fabric) — Quản lý đơn nạp ngân hàng ở standalone mode.
 * Tạo QR SePay, đăng ký với bot, poll trạng thái và cấp thưởng.
 *
 * v5.0.2 FIX:
 *   - Thêm pollSePayApiTransactions() — poll trực tiếp SePay API (không cần webhook/bot)
 *     giống bản plugin, sử dụng SePayApiClient mới port sang Fabric.
 *   - Auto-dispatch reward khi BANK_PAID (không cần admin /approve thủ công nữa):
 *     cả 2 path (bot push + SePay API poll) đều tự giao thưởng ngay.
 *
 * v5.0.1 (anti-spam):
 *   - Thêm handlePaidFromPush() để PluginHttpServer gọi khi bot push /api/bank-paid
 *   - pollPendingOrders() giữ lại làm fallback an toàn
 */
public class StandaloneBankPoller {

    private static final int    TIMEOUT_MS           = 8_000;
    private static final int    MAX_REGISTER_RETRIES = 3;
    private static final long   ORDER_TTL_MS         = 30 * 60 * 1000L; // 30 phút

    private final PayBotMod      mod;
    private final Gson           gson          = new Gson();
    private final SePayApiClient sePayApiClient;

    public StandaloneBankPoller(PayBotMod mod) {
        this.mod           = mod;
        this.sePayApiClient = new SePayApiClient(mod);
    }

    // ─── Tạo QR + đăng ký với bot ────────────────────────────────────────────

    /**
     * Tạo đơn ngân hàng: sinh invoice_id, tạo QR, đăng ký bot, lưu local.
     * Gọi từ /napbank command (async thread).
     */
    public void createBankOrder(ServerPlayerEntity player, int amount) {
        String invoiceId  = TransferContentGenerator.generate(mod);
        String playerName = player.getName().getString();

        mod.getLocalOrderManager().createBankOrder(invoiceId, playerName, amount);

        mod.runOnMainThread(() -> mod.getQRMapManager().createAndGiveQR(player, amount, invoiceId));

        player.sendMessage(Text.literal("§a[PayBot] §fĐang tạo QR chuyển khoản..."));
        player.sendMessage(Text.literal("§7Số tiền: §f" + PayBotMod.formatVnd(amount) + " VND"));
        player.sendMessage(Text.literal("§7Nội dung CK: §e" + invoiceId));
        player.sendMessage(Text.literal("§7QR hết hạn sau §e30 phút§7."));

        mod.getScheduler().schedule(() -> expireOrder(invoiceId, playerName), ORDER_TTL_MS, TimeUnit.MILLISECONDS);

        // v5.1.0: chỉ đăng ký với bot khi đang kết nối bot (bot quản lý webhook nhận tiền)
        // Standalone mode: tự poll SePay API trực tiếp, không cần "đăng ký" với bot
        if (!mod.isStandaloneMode()) {
            boolean registered = doRegisterWithRetry(invoiceId, amount, playerName);
            if (registered) {
                mod.getLocalOrderManager().markBankRegistered(invoiceId);
            }
        }
    }

    // ─── SePay API self-poll (v5.0.2 FIX) ────────────────────────────────────

    /**
     * Poll trực tiếp SePay API để phát hiện giao dịch mới — KHÔNG cần webhook, không cần bot.
     * Gọi định kỳ từ PayBotMod.startTasks() theo poll-interval-seconds (default 10s).
     *
     * Khi phát hiện giao dịch khớp đơn PENDING: auto-dispatch reward ngay (không cần /approve).
     */
    public void pollSePayApiTransactions() {
        String token = mod.getConfig().getString("sepay-api.api-token", "").trim();
        if (token.isEmpty()) return;

        long lastId = mod.getConfig().getLong("sepay-api.last-transaction-id", 0L);
        List<SePayApiClient.TransactionInfo> txs = sePayApiClient.pollNewTransactions(lastId);
        if (txs.isEmpty()) return;

        long maxId = lastId;
        for (SePayApiClient.TransactionInfo tx : txs) {
            maxId = Math.max(maxId, tx.id);
            if (tx.amountIn <= 0) continue; // bỏ qua giao dịch tiền ra

            LocalOrderManager.BankOrder matched = mod.getLocalOrderManager().matchPendingByContent(tx.content, null);
            if (matched == null) continue;
            if (tx.amountIn < matched.amount) {
                PayBotMod.LOGGER.warn("[PayBot] SePay API: thiếu tiền! đơn #" + matched.invoiceId
                        + " cần " + matched.amount + " nhận " + tx.amountIn);
                continue;
            }

            PayBotMod.LOGGER.info("[PayBot] SePay API PAID: invoice=" + matched.invoiceId
                    + " player=" + matched.playerName);
            // v5.0.2 FIX: auto-dispatch reward
            autoDispatchReward(matched, "SePay API poll");
        }

        if (maxId != lastId) {
            mod.getConfig().set("sepay-api.last-transaction-id", maxId);
            mod.getConfig().save();
        }
    }

    // ─── Bot push + polling fallback ─────────────────────────────────────────

    public void pollPendingOrders() {
        String botUrl = mod.getConfig().getString("bot-url", "").trim();
        if (botUrl.isEmpty()) return;
        List<LocalOrderManager.BankOrder> pending = mod.getLocalOrderManager().getPendingBankOrders();
        if (pending.isEmpty()) return;

        JsonObject body = new JsonObject();
        body.addProperty("server_id", mod.getConfig().getString("server-id", ""));
        JsonArray ids = new JsonArray();
        for (LocalOrderManager.BankOrder o : pending) ids.add(o.invoiceId);
        body.add("invoice_ids", ids);

        try {
            JsonObject resp = postJson(botUrl + "/api/standalone-bank-status", body);
            if (resp == null || !resp.has("results")) return;
            JsonObject results = resp.getAsJsonObject("results");
            for (Map.Entry<String, JsonElement> e : results.entrySet()) {
                String status = e.getValue().isJsonNull() ? "" : e.getValue().getAsString();
                if ("PAID".equalsIgnoreCase(status)) {
                    LocalOrderManager.BankOrder order = mod.getLocalOrderManager().getBankOrder(e.getKey());
                    if (order != null) autoDispatchReward(order, "bot poll fallback");
                }
            }
        } catch (Exception e) {
            PayBotMod.LOGGER.debug("[BankPoller] pollPendingOrders: " + e.getMessage());
        }
    }

    /**
     * v5.0.2 FIX: handlePaidFromPush — cũ chỉ set BANK_PAID và nhắc admin dùng /approve.
     * Giờ auto-dispatch reward ngay (không cần /approve thủ công).
     */
    public void handlePaidFromPush(String invoiceId) {
        LocalOrderManager.BankOrder order = mod.getLocalOrderManager().getBankOrder(invoiceId);
        if (order == null || !LocalOrderManager.BANK_PENDING.equals(order.status)) return;
        PayBotMod.LOGGER.info("[BankPoller] PAID (bot push): invoice=" + invoiceId + " player=" + order.playerName);
        autoDispatchReward(order, "bot push");
    }

    /**
     * v5.0.2 — Core auto-reward logic dùng chung cho mọi path (SePay API / bot push / bot poll).
     * Nếu chưa cấu hình lệnh thưởng → set BANK_PAID, báo admin /approve thủ công.
     * Nếu đã cấu hình → set BANK_APPROVED, dispatch ngay (player online) hoặc queue (offline).
     */
    private void autoDispatchReward(LocalOrderManager.BankOrder order, String source) {
        List<String> rewardCmds = mod.resolveRewardCmds(order.amount, "bank");
        if (!rewardCmds.isEmpty()) {
            String rewardAmt = mod.computeRewardAmt(order.amount, "bank");
            mod.getLocalOrderManager().updateBankStatus(order.invoiceId, LocalOrderManager.BANK_APPROVED);
            mod.runOnMainThread(() -> {
                boolean wasOnline = mod.dispatchOrQueueReward(
                        order.invoiceId, order.playerName, rewardCmds, rewardAmt,
                        String.valueOf(order.amount), "bank");
                mod.notifyAdmins("§a[PayBot] §e" + order.playerName + " §fnạp bank §a"
                        + PayBotMod.formatVnd(order.amount) + " VND §f— thưởng tự giao"
                        + (wasOnline ? "" : " §7(offline → nhận khi join lại)")
                        + " §7(" + source + ")");
                // v5.1.0: nếu bot-connected → notify Discord
                if (!mod.isStandaloneMode()) {
                    mod.runAsync(() -> mod.getBotHttpClient().notifyBankPaidViaApi(
                            order.invoiceId, order.playerName, order.amount));
                }
            });
        } else {
            // Chưa cấu hình lệnh thưởng — vẫn set PAID, chờ admin /approve sau khi cấu hình
            mod.getLocalOrderManager().updateBankStatus(order.invoiceId, LocalOrderManager.BANK_PAID);
            mod.notifyAdmins("§a[PayBot] §e" + order.playerName + " §fchuyển khoản §a"
                    + PayBotMod.formatVnd(order.amount) + " VND§f. §7(" + source + ")"
                    + "\n§7Chưa cấu hình lệnh thưởng — dùng §e/approve " + order.invoiceId.substring(0, 8)
                    + "... §7sau khi cấu hình /chinhsuamenhgianap.");
            mod.runOnMainThread(() -> {
                ServerPlayerEntity p = mod.getServer().getPlayerManager().getPlayer(order.playerName);
                if (p != null) {
                    p.sendMessage(Text.literal("§a§l[PayBot] §r§aĐã nhận thanh toán "
                            + PayBotMod.formatVnd(order.amount) + " VND! Đang chờ admin cấu hình thưởng..."));
                    mod.runRewardEffect(p, order.amount);
                }
            });
        }
    }

    // ─── Retry vô hạn ────────────────────────────────────────────────────────

    public void retryUnregistered() {
        if (mod.isStandaloneMode()) return;
        String botUrl = mod.getConfig().getString("bot-url", "").trim();
        if (botUrl.isEmpty()) return;
        List<LocalOrderManager.BankOrder> list = mod.getLocalOrderManager().getUnregisteredBankOrders();
        if (list.isEmpty()) return;
        PayBotMod.LOGGER.info("[BankPoller] Retry " + list.size() + " đơn chưa đăng ký...");
        for (LocalOrderManager.BankOrder o : list) {
            boolean ok = doRegisterWithRetry(o.invoiceId, o.amount, o.playerName);
            if (ok) mod.getLocalOrderManager().markBankRegistered(o.invoiceId);
        }
    }

    public void recoverOnStartup() {
        PayBotMod.LOGGER.info("[BankPoller] Startup recovery: kiểm tra bank orders...");
        retryUnregistered();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private boolean doRegisterWithRetry(String invoiceId, int amount, String playerName) {
        String botUrl = mod.getConfig().getString("bot-url", "").trim();
        if (botUrl.isEmpty()) return false;
        String callbackUrl    = mod.getConfig().getString("plugin-callback-url", "").trim();
        String sepayApiToken  = mod.getConfig().getString("sepay-api.api-token", "").trim();
        for (int attempt = 1; attempt <= MAX_REGISTER_RETRIES; attempt++) {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("invoice_id",  invoiceId);
                body.addProperty("server_id",   mod.getConfig().getString("server-id", ""));
                body.addProperty("amount",      amount);
                body.addProperty("player_name", playerName);
                if (!callbackUrl.isEmpty())   body.addProperty("callback_url", callbackUrl);
                if (!sepayApiToken.isEmpty()) body.addProperty("api_token", sepayApiToken);
                JsonObject resp = postJson(botUrl + "/api/standalone-bank-order", body);
                if (resp != null && resp.has("ok") && resp.get("ok").getAsBoolean()) {
                    PayBotMod.LOGGER.info("[BankPoller] Registered: invoice=" + invoiceId
                            + " (lần " + attempt + "/" + MAX_REGISTER_RETRIES + ")");
                    return true;
                }
            } catch (Exception e) {
                PayBotMod.LOGGER.warn("[BankPoller] Register attempt " + attempt + ": " + e.getMessage());
            }
            if (attempt < MAX_REGISTER_RETRIES) {
                try { Thread.sleep(1500); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
        }
        PayBotMod.LOGGER.warn("[BankPoller] Không đăng ký được invoice=" + invoiceId + " — sẽ retry sau.");
        return false;
    }

    private void expireOrder(String invoiceId, String playerName) {
        LocalOrderManager.BankOrder o = mod.getLocalOrderManager().getBankOrder(invoiceId);
        if (o == null || !LocalOrderManager.BANK_PENDING.equals(o.status)) return;
        mod.getLocalOrderManager().updateBankStatus(invoiceId, LocalOrderManager.BANK_EXPIRED);
        // v5.1.0: chỉ notify bot khi đang kết nối
        if (!mod.isStandaloneMode()) {
            mod.runAsync(() -> mod.getBotHttpClient().notifyNapBankExpired(invoiceId));
        }
        ServerPlayerEntity p = mod.getServer().getPlayerManager().getPlayer(playerName);
        if (p != null) p.sendMessage(Text.literal("§c[PayBot] §fQR chuyển khoản đã hết hạn!"));
    }

    // ─── postJson ────────────────────────────────────────────────────────────

    private JsonObject postJson(String urlStr, JsonObject body) throws Exception {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        conn.setDoOutput(true); conn.setDoInput(true); conn.setUseCaches(false);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type",   "application/json; charset=UTF-8");
        conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
        BotHttpClient.applyApiKey(conn, mod);
        conn.setConnectTimeout(TIMEOUT_MS); conn.setReadTimeout(TIMEOUT_MS);
        try (OutputStream os = conn.getOutputStream()) { os.write(bytes); os.flush(); }
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder(); String ln;
            while ((ln = br.readLine()) != null) sb.append(ln);
            String raw = sb.toString().trim();
            if (raw.isEmpty()) return null;
            try {
                JsonElement elem = gson.fromJson(raw, JsonElement.class);
                return (elem != null && !elem.isJsonNull() && elem.isJsonObject())
                        ? elem.getAsJsonObject() : null;
            } catch (JsonSyntaxException e) { return null; }
        }
    }
}
