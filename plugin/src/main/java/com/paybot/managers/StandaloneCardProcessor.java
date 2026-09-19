package com.paybot.managers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.paybot.PayBotPlugin;
import com.paybot.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.logging.Level;

import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;

/**
 * StandaloneCardProcessor — Xử lý nạp thẻ qua card API trực tiếp (standalone mode).
 *
 * Resilience:
 *  - callSubmitApi() retry tối đa MAX_SUBMIT_RETRIES lần (2s backoff)
 *  - Phân biệt lỗi mạng (connectionError=true) với thẻ sai thật sự
 *  - recoverUnsubmitted(): khi plugin restart, retry các order PROCESSING
 *    bị đánh dấu connectionError (mạng lỗi, chưa gửi được)
 *  - callCheckApi() retry tối đa MAX_CHECK_RETRIES lần
 */
public class StandaloneCardProcessor {

    private static final Map<String, String> SUPPORTED_SITES = DirectCardSubmitHandler.getSupportedSites();

    public static Map<String, String> getSupportedSites() { return SUPPORTED_SITES; }

    // Số lần retry tối đa
    private static final int  MAX_SUBMIT_RETRIES = 3;
    private static final int  MAX_CHECK_RETRIES  = 2;
    private static final long RETRY_DELAY_MS     = 2_000L;

    private final PayBotPlugin plugin;
    private final Gson gson = new Gson();
    private final java.util.concurrent.atomic.AtomicBoolean isChecking = new java.util.concurrent.atomic.AtomicBoolean(false);

    public StandaloneCardProcessor(PayBotPlugin plugin) {
        this.plugin = plugin;
    }

    // ─── Recovery khi plugin restart ─────────────────────────────────────────

    /**
     * Gọi sau khi plugin khởi động xong.
     * Retry tất cả card order PROCESSING bị đánh dấu connectionError=true
     * (mạng bị ngắt trước khi gửi được lên card API).
     *
     * LƯU Ý: Chỉ retry lỗi KẾT NỐI (connectionError=true).
     * Thẻ sai / đã dùng KHÔNG retry (đó là lỗi thực sự của thẻ).
     */
    /**
     * Retry vô hạn lần cho card orders bị connectionError.
     * Gọi định kỳ mỗi 30s từ PayBotPlugin (thay vì chỉ khi restart).
     * v4.1.0: Bỏ giới hạn số lần → retry vô hạn đến khi thành công hoặc có kết quả.
     */
    public void retryConnectionErrors() {
        List<LocalOrderManager.CardOrder> errorOrders =
                plugin.getLocalOrderManager().getConnectionErrorCardOrders();
        if (errorOrders.isEmpty()) return;
        plugin.getLogger().info("[Card] Periodic retry: " + errorOrders.size() + " đơn lỗi mạng...");
        for (LocalOrderManager.CardOrder order : errorOrders) {
            plugin.getLocalOrderManager().markCardConnectionError(order.requestId, false);
            JsonObject result = callSubmitApiWithRetry(
                    order.requestId,
                    order.telco,
                    order.denom,
                    order.cardCode,
                    order.cardSerial
            );

            if (result == null) {
                plugin.getLocalOrderManager().markCardConnectionError(order.requestId, true);
                continue;
            }

            String status = result.has("status")
                    ? result.get("status").getAsString()
                    : "100";

            String message = result.has("message")
                    ? result.get("message").getAsString()
                    : "";

            plugin.getLocalOrderManager().updateCardStatus(
                    order.requestId,
                    status,
                    message
            );

            if (!LocalOrderManager.CARD_PROCESSING.equals(status)) {
                notifyCardResult(
                        order.requestId,
                        order.playerName,
                        order.denom,
                        status,
                        message
                );
            }
        }
    }

    public void recoverUnsubmitted() {
        if (!isCardApiConfigured()) return;

        List<LocalOrderManager.CardOrder> failed =
                plugin.getLocalOrderManager().getConnectionErrorCardOrders();
        if (failed.isEmpty()) {
            return;
        }

        plugin.getLogger().info("[Standalone] Card recovery: phát hiện " + failed.size()
                + " đơn thẻ bị lỗi mạng → đang retry...");

        SchedulerUtils.runAsync(plugin, () -> {
            for (LocalOrderManager.CardOrder order : failed) {
                plugin.getLogger().info("[Standalone] Card recovery: retry requestId=" + order.requestId
                        + " player=" + order.playerName + " (attempts so far=" + order.submitAttempts + ")");

                // Reset connectionError trước khi retry
                plugin.getLocalOrderManager().markCardConnectionError(order.requestId, false);

                JsonObject result = callSubmitApiWithRetry(
                        order.requestId, order.telco, order.denom, order.cardCode, order.cardSerial);

                if (result == null) {
                    // Vẫn lỗi mạng → đánh dấu lại
                    plugin.getLocalOrderManager().markCardConnectionError(order.requestId, true);
                    NotificationManager.warn(plugin, "card-api-error", "[PayBot] Card recovery: vẫn lỗi mạng requestId="
                            + order.requestId + " — sẽ thử lại lần sau.");
                    continue;
                }

                String status  = result.has("status")  ? result.get("status").getAsString()  : "100";
                String message = result.has("message") ? result.get("message").getAsString() : "";
                plugin.getLocalOrderManager().updateCardStatus(order.requestId, status, message);

                plugin.getLogger().info("[Standalone] Card recovery kết quả: requestId=" + order.requestId
                        + " status=" + status);

                if (!LocalOrderManager.CARD_PROCESSING.equals(status)) {
                    notifyCardResult(order.requestId, order.playerName, order.denom, status, message);
                }
            }
        });
    }

    // ─── Submit thẻ mới (async) ───────────────────────────────────────────────

    public void submitCard(Player player, String telco, int denom, String cardCode, String cardSerial) {
        String requestId = UUID.randomUUID().toString();

        // Tạo order local TRƯỚC khi gọi API (đảm bảo không mất dữ liệu ngay cả khi crash)
        plugin.getLocalOrderManager().createCardOrder(
                requestId, player.getName(), telco, denom, cardCode, cardSerial);

        player.sendMessage(PayBotPlugin.f("§e[PayBot] §fThẻ đã được gửi! Đang trong quá trình xử lý..."));
        player.sendMessage("§7Admin dùng §e/cardcheck §7để theo dõi và §e/approve "
                + requestId.substring(0, 8) + "... §7để xác nhận.");
        NotificationManager.log(plugin, "card-submitted",
                "[PayBot] " + player.getName() + " gửi thẻ: " + telco + " " + denom + " VND (standalone)");

        SchedulerUtils.runAsync(plugin, () -> {
            plugin.getLocalOrderManager().incrementCardSubmitAttempts(requestId);

            JsonObject result = callSubmitApiWithRetry(requestId, telco, denom, cardCode, cardSerial);

            if (result == null) {
                // Lỗi mạng hoàn toàn → đánh dấu để retry sau
                plugin.getLocalOrderManager().markCardConnectionError(requestId, true);
                NotificationManager.warn(plugin, "card-api-error", "[PayBot] Card submit lỗi mạng, requestId="
                        + requestId + " — sẽ retry khi mạng phục hồi.");
                notifyOps("§c[PayBot] §fLỗi mạng khi gửi thẻ §e" + player.getName()
                        + " §f— hệ thống sẽ tự retry sau khi mạng phục hồi."
                        + " §7(requestId=" + requestId + ")");
                return;
            }

            String status  = result.has("status")  ? result.get("status").getAsString()  : "100";
            String message = result.has("message") ? result.get("message").getAsString() : "";
            plugin.getLocalOrderManager().updateCardStatus(requestId, status, message);

            plugin.getLogger().info("[Standalone] Card submitted requestId=" + requestId
                    + " status=" + status);

            if (LocalOrderManager.CARD_PROCESSING.equals(status)) {
                notifyOps("§e[PayBot] §fThẻ §e" + player.getName()
                        + " §fmệnh giá §b" + formatVnd(denom)
                        + " §fđang chờ API xử lý. §7(id=" + requestId + ")");
            } else {
                notifyCardResult(requestId, player.getName(), denom, status, message);
            }
        });
    }

    // ─── Poll định kỳ cho PROCESSING cards ───────────────────────────────────

    public void pollPendingCards() {
        if (!isChecking.compareAndSet(false, true)) return;
        SchedulerUtils.runAsync(plugin, () -> {
            try {
                List<LocalOrderManager.CardOrder> pending =
                        plugin.getLocalOrderManager().getProcessingCardOrders();
                if (pending.isEmpty()) return;

                for (LocalOrderManager.CardOrder order : pending) {
                    try {
                        JsonObject result = callCheckApiWithRetry(
                                order.requestId, order.telco, order.denom, order.cardCode, order.cardSerial);
                        if (result != null) {
                            String newStatus  = result.has("status")  ? result.get("status").getAsString()  : "";
                            String newMessage = result.has("message") ? result.get("message").getAsString() : "";
                            if (!newStatus.isEmpty() && !newStatus.equals(order.status)) {
                                plugin.getLocalOrderManager().updateCardStatus(order.requestId, newStatus, newMessage);
                                if (!LocalOrderManager.CARD_PROCESSING.equals(newStatus)) {
                                    notifyCardResult(order.requestId, order.playerName, order.denom, newStatus, newMessage);
                                }
                            }
                        }
                        // Pacing 300ms giữa mỗi thẻ để tránh spam API đối tác và không nghẽn thread
                        Thread.sleep(300L);
                    } catch (Throwable ignored) {}
                }
            } finally {
                isChecking.set(false);
            }
        });
    }

    // ─── Card API calls với retry ─────────────────────────────────────────────

    /**
     * Submit thẻ lên card API trực tiếp thông qua DirectCardSubmitHandler (5x POST + 5x GET fallback).
     * @return JsonObject kết quả, hoặc null nếu lỗi mạng hoàn toàn
     */
    private JsonObject callSubmitApiWithRetry(String requestId, String telco, int denom,
                                               String cardCode, String cardSerial) {
        String site       = plugin.getConfig().getString("card-api.site",        "").trim();
        String partnerId  = plugin.getConfig().getString("card-api.partner-id",  "").trim();
        String partnerKey = plugin.getConfig().getString("card-api.partner-key", "").trim();
        String apiUrl     = SUPPORTED_SITES.get(site);
        if (apiUrl == null || partnerId.isEmpty() || partnerKey.isEmpty()) {
            String[] creds = DirectCardSubmitHandler.resolveCredentials(plugin, site);
            if (creds != null && !creds[0].isEmpty() && !creds[1].isEmpty()) {
                partnerId = creds[0];
                partnerKey = creds[1];
            } else {
                return null;
            }
        }

        return DirectCardSubmitHandler.submitDirectly(
                plugin, apiUrl, partnerId, partnerKey, telco, denom, cardCode, cardSerial, requestId);
    }

    /**
     * Check trạng thái thẻ đang PROCESSING với retry.
     * @return JsonObject kết quả, hoặc null nếu mạng lỗi (bỏ qua, thử lần sau)
     */
    private JsonObject callCheckApiWithRetry(String requestId, String telco, int denom,
                                              String cardCode, String cardSerial) {
        String site       = plugin.getConfig().getString("card-api.site",        "");
        String partnerId  = plugin.getConfig().getString("card-api.partner-id",  "");
        String partnerKey = plugin.getConfig().getString("card-api.partner-key", "");
        String apiUrl     = SUPPORTED_SITES.get(site);
        if (apiUrl == null || partnerId.isEmpty() || partnerKey.isEmpty()) return null;

        String sign  = md5(partnerKey + requestId);
        String query = "partner_id=" + enc(partnerId)
                + "&request_id=" + enc(requestId)
                + "&sign="       + enc(sign)
                + "&command=check"
                + "&telco="      + enc(telco)
                + "&code="       + enc(cardCode)
                + "&serial="     + enc(cardSerial)
                + "&amount="     + denom;

        for (int attempt = 1; attempt <= MAX_CHECK_RETRIES; attempt++) {
            try {
                JsonObject result = getJson(apiUrl + "?" + query);
                if (result != null) return result;
            } catch (Exception e) {
                NotificationManager.warn(plugin, "card-api-error",
                        "[PayBot] Lỗi check thẻ với web gạch thẻ §e" + site + "§7 (attempt " + attempt + "/"
                        + MAX_CHECK_RETRIES + "): " + e.getMessage());
            }
            if (attempt < MAX_CHECK_RETRIES) {
                try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ignored) {}
            }
        }
        return null;
    }

    // ─── Notify helpers ───────────────────────────────────────────────────────

    /**
     * v5.0.2 — Khi thẻ CARD_SUCCESS: auto-dispatch reward ngay (không cần admin /approve).
     * Nếu chưa cấu hình lệnh thưởng → vẫn báo ops nhưng không dispatch, tránh mất reward.
     * Nếu player offline → đưa vào OfflineRewardManager, giao khi join lại.
     */
    private void notifyCardResult(String requestId, String playerName, int denom,
                                   String status, String message) {
        String denomStr = formatDenom(denom);

        if (LocalOrderManager.CARD_SUCCESS.equals(status)) {
            // v5.0.2 FIX: Auto-dispatch reward — không cần /approve thủ công nữa.
            java.util.List<String> rewardCmds = RewardDispatcher.resolveRewardCmds(plugin, denom, "card");
            if (!rewardCmds.isEmpty()) {
                String rewardAmt = RewardDispatcher.computeRewardAmt(plugin, denom, "card");
                String rewardHash = RewardDeliveryLedger.computeRewardHash(rewardCmds, rewardAmt);

                // v5.5.8 Part 122: Idempotency check: Tránh duplicate reward
                if (plugin.getRewardDeliveryLedger() != null && plugin.getRewardDeliveryLedger().hasDelivered("card", requestId, rewardHash)) {
                    plugin.getLocalOrderManager().updateCardStatus(requestId, LocalOrderManager.CARD_APPROVED, message);
                    return;
                }

                // Cập nhật PAYMENT_CONFIRMED và claim atomic REWARD_PROCESSING
                plugin.getLocalOrderManager().updateCardStatus(requestId, LocalOrderManager.CARD_CONFIRMED, message);
                plugin.getLocalOrderManager().claimCardOrderForReward(requestId);

                SchedulerUtils.runSync(plugin, () -> {
                    boolean wasOnline = RewardDispatcher.dispatchOrQueue(
                            plugin, requestId, playerName, rewardCmds, rewardAmt,
                            String.valueOf(denom), "card", null, "");
                    plugin.getLocalOrderManager().updateCardStatus(requestId, LocalOrderManager.CARD_APPROVED, message);
                    if (plugin.getRewardDeliveryLedger() != null) {
                        plugin.getRewardDeliveryLedger().recordDelivery("card", requestId, playerName, rewardHash, "DONE");
                    }
                    String suffix = wasOnline ? "§7(đã giao thưởng)" : "§7(player offline — sẽ nhận khi vào lại)";
                    notifyOps("§a[PayBot] §fThẻ §e" + playerName + " §f" + denomStr
                            + " §athành công ✓ — " + suffix + "\n§7ID: §f" + requestId);
                    // v5.1.0: nếu bot-connected → notify Discord
                    if (!plugin.isStandaloneMode()) {
                        SchedulerUtils.runAsync(plugin,
                                () -> plugin.getBotHttpClient().notifyBankPaidViaApi(
                                        requestId, playerName, denom));
                    }
                });
            } else {
                // Chưa cấu hình thưởng → báo ops dùng /approve sau khi cấu hình
                notifyOps("§a[PayBot] §fThẻ §e" + playerName + " §f" + denomStr
                        + " §athành công ✓ §7— Chưa cấu hình lệnh thưởng, dùng §e/approve "
                        + requestId + " §7sau khi đã cấu hình /chinhsuamenhgianap.");
                SchedulerUtils.runSync(plugin, () -> {
                    Player p = Bukkit.getPlayerExact(playerName);
                    if (p != null && p.isOnline()) {
                        RewardEffectManager.notifyPaymentReceived(plugin, p, denom);
                    }
                });
            }
            return;
        }

        // Các trạng thái lỗi: chỉ thông báo, không dispatch
        String statusLabel;
        switch (status) {
            case LocalOrderManager.CARD_WRONG_DENOM:  statusLabel = "§cSai mệnh giá ✗"; break;
            case LocalOrderManager.CARD_USED:          statusLabel = "§cThẻ đã sử dụng ✗"; break;
            case LocalOrderManager.CARD_MAINTENANCE:   statusLabel = "§eHệ thống bảo trì ⚠"; break;
            default:                                   statusLabel = "§cThẻ sai/đã dùng ✗"; break;
        }
        String msg = "§6[PayBot] §fKết quả thẻ §e" + playerName + " §f" + denomStr + ": " + statusLabel
                + (!message.isEmpty() ? " §7(" + message + ")" : "")
                + "\n§7ID: §f" + requestId;
        notifyOps(msg);
        SchedulerUtils.runSync(plugin, () -> {
            Player p = Bukkit.getPlayerExact(playerName);
            if (p != null && p.isOnline()) {
                SchedulerUtils.runForPlayer(plugin, p, () ->
                    p.sendMessage(PayBotPlugin.f("§c[PayBot] §fThẻ của bạn không được xử lý: " + statusLabel)));
            }
        });
    }

    void notifyOps(String message) {
        // [Part 45] getOnlinePlayers() KHÔNG an toàn gọi từ thread async trần (Javadoc
        // Bukkit chính thức) — đọc trên runSync, dispatch riêng từng player qua entity-
        // scheduler của họ (broadcastMessage không đảm bảo tới hết mọi player trên Folia —
        // GitHub issue #382, PaperMC/Folia).
        SchedulerUtils.runSync(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("naptien.admin")) {
                    SchedulerUtils.runForPlayer(plugin, p, () -> p.sendMessage(message));
                }
            }
        });
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private boolean isCardApiConfigured() {
        String site      = plugin.getConfig().getString("card-api.site",        "");
        String partnerId = plugin.getConfig().getString("card-api.partner-id",  "");
        String partnerKey= plugin.getConfig().getString("card-api.partner-key", "");
        return !site.isEmpty() && !partnerId.isEmpty() && !partnerKey.isEmpty()
                && SUPPORTED_SITES.containsKey(site);
    }

    private JsonObject postForm(String urlStr, Map<String, String> params) {
        try {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (sb.length() > 0) sb.append('&');
                sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
            }
            byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
            URL url = new URL(urlStr);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            con.setDoOutput(true);
            con.setConnectTimeout(15_000);
            con.setReadTimeout(15_000);
            try (OutputStream os = con.getOutputStream()) { os.write(body); }
            return readResponse(con);
        } catch (Exception e) {
            NotificationManager.warn(plugin, "card-api-error",
                    "[PayBot] Lỗi gửi yêu cầu (POST) tới web gạch thẻ: " + e.getMessage());
            return null;
        }
    }

    private JsonObject getJson(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(15_000);
            con.setReadTimeout(15_000);
            return readResponse(con);
        } catch (Exception e) {
            NotificationManager.warn(plugin, "card-api-error",
                    "[PayBot] Lỗi gửi yêu cầu (GET) tới web gạch thẻ: " + e.getMessage());
            return null;
        }
    }

    private JsonObject readResponse(HttpURLConnection con) throws Exception {
        int code = con.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? con.getInputStream() : con.getErrorStream();
        if (is == null) return null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            String raw = sb.toString().trim();
            if (raw.startsWith("{")) {
                // FIX v4.0.9: dùng JsonElement để tránh ClassCastException
                try {
                    JsonElement elem = gson.fromJson(raw, JsonElement.class);
                    if (elem != null && !elem.isJsonNull() && elem.isJsonObject())
                        return elem.getAsJsonObject();
                } catch (JsonSyntaxException e) { /* fall through */ }
            }
            JsonObject obj = new JsonObject();
            for (String part : raw.split("&")) {
                int idx = part.indexOf('=');
                if (idx > 0) obj.addProperty(part.substring(0, idx), part.substring(idx + 1));
            }
            return obj;
        }
    }

    private String enc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private String formatVnd(int amount) {
        StringBuilder sb = new StringBuilder(String.valueOf(amount));
        int len = sb.length();
        for (int i = len - 3; i > 0; i -= 3) sb.insert(i, '.');
        return sb + " VND";
    }

    private String formatDenom(int denom) {
        if (denom >= 1_000_000 && denom % 1_000_000 == 0) return (denom / 1_000_000) + "tr";
        if (denom >= 1_000    && denom % 1_000    == 0) return (denom / 1_000) + "k";
        return String.valueOf(denom);
    }
}
