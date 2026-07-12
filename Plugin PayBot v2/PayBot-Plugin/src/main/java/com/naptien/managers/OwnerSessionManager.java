package com.naptien.managers;

import java.util.ArrayList;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;

import com.naptien.NapTienPlugin;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OwnerSessionManager — Xác thực owner qua mã một lần từ Discord.
 *
 * Flow:
 *  1. Owner dùng /ownerlogin trên Discord → bot tạo mã 100 ký tự ngẫu nhiên
 *     chỉ hiển thị cho owner (ephemeral), không lưu DB, không log
 *  2. Owner copy mã → /paybotowner <mã> trong Minecraft
 *  3. Plugin gửi mã lên bot (POST /api/owner-verify)
 *  4. Bot kiểm tra: đúng mã + chưa hết hạn? → trả về ok
 *  5. Plugin cấp PermissionAttachment "naptien.admin" cho player
 *  6. Session tự hết hạn sau 30 phút
 */
public class OwnerSessionManager {

    private static final long SESSION_MS  = 30L * 60 * 1000;  // 30 phút
    private static final int  HTTP_TIMEOUT = 6_000;

    private final NapTienPlugin plugin;
    private final Gson gson = new Gson();

    /** UUID → PermissionAttachment đang active */
    private final Map<UUID, PermissionAttachment> attachments  = new ConcurrentHashMap<>();
    /** UUID → thời điểm hết hạn (ms epoch) */
    private final Map<UUID, Long>                 sessionExpiry = new ConcurrentHashMap<>();
    /**
     * v5.0.0 — UUID → trạng thái OP TRƯỚC khi grant session, để khi revoke (logout/hết
     * hạn/disconnect) trả lại ĐÚNG trạng thái cũ (không vô tình giữ OP mãi nếu player gốc
     * không phải OP, và không vô tình GỠ OP nếu player gốc đã là OP từ trước).
     */
    private final Map<UUID, Boolean>               previousOpState = new ConcurrentHashMap<>();

    private BukkitTask expiryTask;

    public OwnerSessionManager(NapTienPlugin plugin) {
        this.plugin = plugin;
        startExpiryTask();
    }

    // ─── Login ────────────────────────────────────────────────────────────────

    /**
     * Gửi mã lên bot để xác minh, trả về kết quả.
     * Gọi từ async thread.
     */
    public VerifyResult verifyWithBot(Player player, String code) {
        String botUrl = plugin.getConfig().getString("bot-url", "").trim();
        if (botUrl.isEmpty()) return VerifyResult.NO_BOT_URL;

        String serverId = plugin.getConfig().getString("server-id", "").trim();

        try {
            JsonObject body = new JsonObject();
            body.addProperty("server_id",   serverId);
            body.addProperty("player_name", player.getName());
            body.addProperty("player_uuid", player.getUniqueId().toString());
            body.addProperty("code",        code);

            JsonObject resp = postJson(botUrl + "/api/owner-verify", body);
            if (resp == null) return VerifyResult.BOT_UNREACHABLE;

            boolean ok = resp.has("ok") && resp.get("ok").getAsBoolean();
            if (!ok) {
                String reason = resp.has("reason") ? resp.get("reason").getAsString() : "unknown";
                plugin.getLogger().warning("[OwnerAuth] Xác minh thất bại: " + reason);
                return VerifyResult.fromReason(reason);
            }
            return VerifyResult.SUCCESS;
        } catch (Exception e) {
            plugin.getLogger().warning("[OwnerAuth] Lỗi kết nối bot: " + e.getMessage());
            return VerifyResult.BOT_UNREACHABLE;
        }
    }

    /**
     * Cấp session owner cho player (gọi trên main thread sau khi verifyWithBot thành công).
     */
    public void grantSession(Player player) {
        // Thu hồi session cũ nếu có
        revokeSession(player.getUniqueId());

        PermissionAttachment att = player.addAttachment(plugin);
        att.setPermission("naptien.admin", true);
        att.setPermission("naptien.op",    true);

        attachments.put(player.getUniqueId(), att);
        sessionExpiry.put(player.getUniqueId(), System.currentTimeMillis() + SESSION_MS);

        // v5.0.0: cấp quyền OP THẬT trong lúc đang login owner — lưu lại trạng thái OP
        // GỐC trước đó để revokeSession() trả về đúng (không tự nhiên thêm/gỡ OP ngoài ý).
        previousOpState.put(player.getUniqueId(), player.isOp());
        if (!player.isOp()) player.setOp(true);

        // Refresh tab-complete để client thấy ngay các lệnh admin mới
        player.updateCommands();

        // v5.0.0 (theo yêu cầu): KHÔNG log console/file gì về việc cấp OP này — chỉ
        // player tự thấy tin nhắn của riêng họ (không broadcast cho admin/op khác).
        // Hiện danh sách lệnh admin có thể dùng
        player.sendMessage("§6§l══════ Lệnh Owner ══════");
        player.sendMessage("§e/topuplist all        §7— xem danh sách đơn nạp");
        player.sendMessage("§e/approve <id>         §7— duyệt và cấp thưởng");
        player.sendMessage("§e/bankcheck            §7— kiểm tra đơn ngân hàng");
        player.sendMessage("§e/cardcheck            §7— kiểm tra đơn thẻ");
        player.sendMessage("§e/sepaysetup           §7— cấu hình SePay");
        player.sendMessage("§e/cardsetup            §7— cấu hình card API");
        player.sendMessage("§e/PayBotSetup          §7— xem trạng thái cấu hình");
        player.sendMessage("§e/disablepaybot        §7— chặn vĩnh viễn PayBot trên server này");
        player.sendMessage("§e/paybotowner logout   §7— đăng xuất owner");
        player.sendMessage("§6§l══════════════════════");
    }

    // ─── Logout / Revoke ──────────────────────────────────────────────────────

    public void revokeSession(UUID uuid) {
        PermissionAttachment att = attachments.remove(uuid);
        if (att != null) {
            try { att.getPermissible().removeAttachment(att); } catch (Exception ignored) {}
        }
        sessionExpiry.remove(uuid);

        // v5.0.0 (fix theo yêu cầu): trả lại ĐÚNG trạng thái OP gốc — dùng OfflinePlayer
        // (KHÔNG dùng Bukkit.getServer().getPlayer(), chỉ trả về player ĐANG ONLINE) để
        // deop được CẢ KHI PLAYER ĐÃ OFFLINE — đây chính là cách /deop console vẫn hoạt
        // động được dù player không có mặt. KHÔNG log/console gì về việc gỡ OP này (gọi
        // setOp() trực tiếp, không qua lệnh /deop nên vốn không broadcast cho ai khác —
        // chỉ đảm bảo code mình cũng không tự thêm log nào tiết lộ thêm).
        Boolean wasOp = previousOpState.remove(uuid);
        if (wasOp != null) {
            org.bukkit.OfflinePlayer offline = plugin.getServer().getOfflinePlayer(uuid);
            if (offline.isOp() != wasOp) offline.setOp(wasOp);
        }

        // Refresh tab-complete để xóa lệnh admin khỏi gợi ý (chỉ có tác dụng nếu đang online)
        org.bukkit.entity.Player p = plugin.getServer().getPlayer(uuid);
        if (p != null && p.isOnline()) p.updateCommands();
    }

    public void revokeSession(Player player) {
        revokeSession(player.getUniqueId());
    }

    // ─── Check ────────────────────────────────────────────────────────────────

    public boolean isOwner(Player player) {
        Long expiry = sessionExpiry.get(player.getUniqueId());
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            revokeSession(player.getUniqueId());
            return false;
        }
        return true;
    }

    /** Thời gian còn lại của session (phút), -1 nếu không active. */
    public long remainingMinutes(Player player) {
        Long expiry = sessionExpiry.get(player.getUniqueId());
        if (expiry == null) return -1;
        long remaining = expiry - System.currentTimeMillis();
        return remaining > 0 ? remaining / 60_000 : -1;
    }

    // ─── Background expiry checker ────────────────────────────────────────────

    private void startExpiryTask() {
        expiryTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Long> entry : sessionExpiry.entrySet()) {
                if (now > entry.getValue()) {
                    UUID uuid = entry.getKey();
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        revokeSession(uuid);
                        // Notify nếu player đang online (chỉ riêng họ thấy, không broadcast)
                        org.bukkit.entity.Player p = plugin.getServer().getPlayer(uuid);
                        if (p != null && p.isOnline()) {
                            p.sendMessage(NapTienPlugin.f("§c[PayBot] §fOwner session đã hết hạn. Dùng §e/paybotowner <mã> §fđể đăng nhập lại."));
                        }
                        // v5.0.0 (theo yêu cầu): KHÔNG log console gì về việc hết hạn/deop này.
                    });
                }
            }
        }, 1200L, 1200L); // check mỗi 60 giây
    }

    public void shutdown() {
        if (expiryTask != null) expiryTask.cancel();
        // Thu hồi tất cả session khi plugin stop
        for (UUID uuid : attachments.keySet()) revokeSession(uuid);
    }

    // ─── HTTP helper ──────────────────────────────────────────────────────────

    private JsonObject postJson(String urlStr, JsonObject body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        BotHttpClient.applyApiKey(conn, plugin); // v5.0.0: bắt buộc — bot.py check X-API-Key
        conn.setConnectTimeout(HTTP_TIMEOUT);
        conn.setReadTimeout(HTTP_TIMEOUT);
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            // FIX v4.0.9
            try {
                JsonElement elem = gson.fromJson(sb.toString(), JsonElement.class);
                if (elem == null || elem.isJsonNull() || !elem.isJsonObject()) return null;
                return elem.getAsJsonObject();
            } catch (JsonSyntaxException e) { return null; }
        }
    }

    // ─── Result enum ──────────────────────────────────────────────────────────

    public enum VerifyResult {
        SUCCESS,
        ALREADY_LOGGED_IN,
        WRONG_CODE,
        CODE_EXPIRED,
        NO_ACTIVE_CODE,
        BOT_UNREACHABLE,
        NO_BOT_URL;

        static VerifyResult fromReason(String reason) {
            if (reason == null) return WRONG_CODE;
            String r = reason.toLowerCase();
            if (r.contains("hết hạn") || r.contains("expired"))     return CODE_EXPIRED;
            if (r.contains("không có mã") || r.contains("no code")) return NO_ACTIVE_CODE;
            return WRONG_CODE;
        }
    }
}