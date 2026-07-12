package com.naptien.managers;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.naptien.PayBotMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OwnerSessionManager (Fabric) — xác thực owner qua mã một lần từ Discord.
 * Không dùng Bukkit PermissionAttachment — dùng Set<UUID> in-memory.
 * Session tồn tại 30 phút, expire tự động qua tickExpiry().
 *
 * Flow: Discord /ownerlogin → mã 100 ký tự → /paybotowner <mã> trong MC
 *       → POST /api/owner-verify → ok=true → grantSession() → OP 4
 *
 * v5.0.0 (fix theo yêu cầu):
 *   - Deop hoạt động được CẢ KHI PLAYER ĐÃ OFFLINE (không chỉ lúc hết hạn mà họ vẫn online).
 *     Cách làm AN TOÀN: cache lại GameProfile của player NGAY LÚC grantSession() (lúc đó
 *     chắc chắn họ đang online) — lúc cần deop chỉ cần dùng lại GameProfile đã cache, KHÔNG
 *     cần tra cứu UserCache theo UUID (tránh phụ thuộc API có thể khác giữa các version).
 *   - KHÔNG OP/deop thật ra (vô tình) của admin đã là OP từ trước khi login owner (lưu lại
 *     trạng thái OP gốc, chỉ revoke nếu họ KHÔNG phải OP thật từ trước).
 *   - KHÔNG log console/file gì về việc cấp/gỡ OP này — gọi addToOperators/removeFromOperators
 *     TRỰC TIẾP (không qua lệnh /op /deop) nên vốn đã không broadcast cho ai khác; code ở
 *     đây cũng không tự thêm log nào để không lộ thêm cơ chế.
 *   - isOwner() lúc tự phát hiện hết hạn (gọi giữa 2 lần tick) cũng phải revoke đúng cách —
 *     trước đây chỉ xoá khỏi map theo dõi mà KHÔNG hề deop, khiến OP bị "treo" vĩnh viễn
 *     nếu isOwner() vô tình dọn session trước khi tickExpiry() kịp chạy.
 *
 * Changelog: v4.1.0-fabric
 */
public class OwnerSessionManager {

    private static final int SESSION_MINUTES = 30;
    private static final int TIMEOUT_MS      = 8_000;

    private final PayBotMod mod;
    private final Map<UUID, Long> sessions = new ConcurrentHashMap<>();

    /** Trạng thái cần để revoke ĐÚNG CÁCH, dù player đang online hay đã offline. */
    private record GrantInfo(boolean wasOp, GameProfile profile) {}
    private final Map<UUID, GrantInfo> grants = new ConcurrentHashMap<>();

    private final Gson gson = new Gson();

    public OwnerSessionManager(PayBotMod mod) { this.mod = mod; }

    // ─── Verify ───────────────────────────────────────────────────────────────

    public enum VerifyResult {
        SUCCESS, ALREADY_LOGGED_IN, WRONG_CODE, CODE_EXPIRED,
        NO_ACTIVE_CODE, BOT_UNREACHABLE, NO_BOT_URL
    }

    public VerifyResult verifyWithBot(ServerPlayerEntity player, String code) {
        String botUrl = mod.getConfig().getString("bot-url", "").trim();
        if (botUrl.isEmpty()) return VerifyResult.NO_BOT_URL;
        if (isOwner(player)) return VerifyResult.ALREADY_LOGGED_IN;

        try {
            JsonObject body = new JsonObject();
            body.addProperty("code",        code);
            body.addProperty("player_name", player.getName().getString());
            body.addProperty("player_uuid", player.getUuidAsString());
            body.addProperty("server_id",   mod.getConfig().getString("server-id", ""));

            byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);
            HttpURLConnection conn = (HttpURLConnection) new URL(botUrl + "/api/owner-verify").openConnection();
            conn.setDoOutput(true); conn.setDoInput(true); conn.setUseCaches(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type",   "application/json; charset=UTF-8");
            conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
            BotHttpClient.applyApiKey(conn, mod); // v5.0.0: bắt buộc — bot.py check X-API-Key
            conn.setConnectTimeout(TIMEOUT_MS); conn.setReadTimeout(TIMEOUT_MS);
            try (OutputStream os = conn.getOutputStream()) { os.write(bodyBytes); os.flush(); }

            int httpCode = conn.getResponseCode();
            InputStream is = (httpCode >= 200 && httpCode < 300)
                    ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) return VerifyResult.BOT_UNREACHABLE;

            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder(); String ln;
                while ((ln = br.readLine()) != null) sb.append(ln);
                JsonElement elem = gson.fromJson(sb.toString(), JsonElement.class);
                if (elem == null || !elem.isJsonObject()) return VerifyResult.BOT_UNREACHABLE;
                JsonObject resp = elem.getAsJsonObject();
                boolean ok = resp.has("ok") && resp.get("ok").getAsBoolean();
                if (ok) return VerifyResult.SUCCESS;
                String reason = resp.has("reason") ? resp.get("reason").getAsString() : "";
                if (reason.contains("hết hạn"))   return VerifyResult.CODE_EXPIRED;
                if (reason.contains("hoạt động")) return VerifyResult.NO_ACTIVE_CODE;
                return VerifyResult.WRONG_CODE;
            }
        } catch (Exception e) {
            PayBotMod.LOGGER.warn("[OwnerSession] verifyWithBot: " + e.getMessage());
            return VerifyResult.BOT_UNREACHABLE;
        }
    }

    // ─── Session management ───────────────────────────────────────────────────

    public void grantSession(ServerPlayerEntity player) {
        sessions.put(player.getUuid(), System.currentTimeMillis() + SESSION_MINUTES * 60_000L);
        boolean wasOp = mod.getServer().getPlayerManager().isOperator(player.getGameProfile());
        // v5.0.0: cache GameProfile NGAY LÚC NÀY (chắc chắn đang online) để revoke sau này
        // dùng lại được — không cần tra cứu gì thêm dù lúc đó player đã offline.
        grants.put(player.getUuid(), new GrantInfo(wasOp, player.getGameProfile()));
        if (!wasOp) mod.getServer().getPlayerManager().addToOperators(player.getGameProfile());
        mod.getServer().getPlayerManager().sendCommandTree(player);
        // v5.0.0 (theo yêu cầu): KHÔNG log console/file gì về việc cấp OP này.
    }

    public void revokeSession(ServerPlayerEntity player) {
        sessions.remove(player.getUuid());
        revokeOp(player.getUuid(), player);
    }

    /**
     * v5.0.0 — Gỡ OP (nếu cần) dùng GrantInfo đã cache — hoạt động ĐÚNG dù
     * {@code onlinePlayerOrNull} là null (player đã offline). KHÔNG log/console gì.
     */
    private void revokeOp(UUID uuid, ServerPlayerEntity onlinePlayerOrNull) {
        GrantInfo info = grants.remove(uuid);
        if (info == null) return; // không có gì để revoke (vd chưa từng grant qua session này)
        if (!info.wasOp()) {
            // removeFromOperators() chỉ cần GameProfile — KHÔNG cần player đang online.
            mod.getServer().getPlayerManager().removeFromOperators(info.profile());
        }
        if (onlinePlayerOrNull != null) {
            mod.getServer().getPlayerManager().sendCommandTree(onlinePlayerOrNull);
        }
    }

    public boolean isOwner(ServerPlayerEntity player) {
        Long exp = sessions.get(player.getUuid());
        if (exp == null) return false;
        if (System.currentTimeMillis() > exp) {
            // v5.0.0 (fix): PHẢI revoke đúng cách ở đây luôn (không chỉ xoá khỏi map) —
            // nếu không, OP cấp tạm sẽ bị "treo" vĩnh viễn vì tickExpiry() sẽ không còn
            // thấy entry này trong sessions nữa (đã bị xoá ở đây) để tự xử lý.
            sessions.remove(player.getUuid());
            revokeOp(player.getUuid(), player);
            return false;
        }
        return true;
    }

    public long remainingMinutes(ServerPlayerEntity player) {
        Long exp = sessions.get(player.getUuid());
        if (exp == null) return -1;
        long ms = exp - System.currentTimeMillis();
        return ms > 0 ? ms / 60_000 : -1;
    }

    /**
     * Gọi mỗi phút để expire session cũ — bắt CẢ trường hợp player đã offline (deop vẫn
     * áp dụng được nhờ GameProfile đã cache từ lúc grantSession(), xem revokeOp()).
     */
    public void tickExpiry(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Long>> it = sessions.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            if (now > e.getValue()) {
                it.remove();
                UUID uuid = e.getKey();
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid); // null nếu offline — vẫn OK
                revokeOp(uuid, p);
                if (p != null) {
                    p.sendMessage(Text.literal("§e[PayBot] §fOwner session đã hết hạn."));
                }
                // v5.0.0 (theo yêu cầu): KHÔNG log console gì về việc hết hạn/deop này.
            }
        }
    }

    /**
     * v5.0.0: KHÔNG còn xoá session khi player quit nữa — owner session phải tiếp tục
     * tính thời gian (và OP phải tiếp tục được gỡ đúng hạn dù họ offline) thay vì mất
     * tracking ngay khi họ rời server. Việc gỡ OP khi disconnect bất kể session còn hạn
     * hay không là hành vi RIÊNG — nếu cần, gọi revokeSession() rõ ràng ở chỗ khác.
     */
    public void onPlayerQuit(ServerPlayerEntity player) { /* no-op — xem javadoc trên */ }

    public boolean handleChat(ServerPlayerEntity player, String text) { return false; }
}

