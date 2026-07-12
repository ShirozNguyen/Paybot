package com.naptien.managers;

import com.naptien.PayBotMod;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;

/**
 * UpdateCheckManager — Kiểm tra cập nhật qua Modrinth API.
 * Thông báo in-game cho admin khi có bản mới (chat + console).
 *
 * v5.0.0:
 *   - Version string cập nhật lên 5.0.0
 *   - Thông báo in-game cho admin online khi check xong
 *   - notifyAdmin(player): gửi thông báo cho 1 admin cụ thể (dùng khi join)
 *   - Tự ngắt nếu không có mạng (silent catch)
 *
 * v5.0.4:
 *   - Đổi log level từ INFO → WARN cho thông báo update
 *   - Đúng format yêu cầu với === border và thụt lề
 */
public class UpdateCheckManager {

    public static final String CURRENT_VERSION = "5.0.0";

    private static final String MODRINTH_PROJECT = "paybot";
    private static final String MODRINTH_API =
            "https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT
                    + "/version?loaders=%5B%22fabric%22%5D";
    private static final String MODRINTH_URL =
            "https://modrinth.com/plugin/" + MODRINTH_PROJECT;

    // Kết quả check lưu static để dùng khi admin join sau
    private static volatile String latestVersion   = null;
    private static volatile boolean updateAvailable = false;

    private final PayBotMod mod;

    public UpdateCheckManager(PayBotMod mod) { this.mod = mod; }

    // ─── Public API ───────────────────────────────────────────────────────────

    public static boolean isUpdateAvailable()  { return updateAvailable; }
    public static String  getLatestVersion()   { return latestVersion; }

    /** Gọi từ background thread khi server khởi động. */
    public void checkForUpdates() {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(MODRINTH_API).toURL().openConnection();
            conn.setRequestProperty("User-Agent",
                    "PayBot-Fabric/" + CURRENT_VERSION + " (" + MODRINTH_URL + ")");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            if (conn.getResponseCode() != 200) return;

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }

            String resp = sb.toString();
            if (!resp.contains("\"version_number\"")) return;

            int idx = resp.indexOf("\"version_number\"") + 19;
            String found = resp.substring(idx, resp.indexOf("\"", idx));

            latestVersion = found;
            updateAvailable = !CURRENT_VERSION.equals(found);

            if (updateAvailable) {
                PayBotMod.LOGGER.warn("[PayBot] ================================================");
                PayBotMod.LOGGER.warn("[PayBot]  [PayBot] ĐÃ PHÁT HIỆN PHIÊN BẢN MỚI!");
                PayBotMod.LOGGER.warn("[PayBot]  Đã phát hiện phiên bản mới, phiên bản hiện tại");
                PayBotMod.LOGGER.warn("[PayBot]  là " + CURRENT_VERSION + ", vui lòng cập nhật lên phiên bản " + latestVersion);
                PayBotMod.LOGGER.warn("[PayBot]  Tải tại: " + MODRINTH_URL);
                PayBotMod.LOGGER.warn("[PayBot] ================================================");

                // Thông báo cho tất cả admin đang online
                mod.runOnMainThread(() -> {
                    for (ServerPlayerEntity p : mod.getServer().getPlayerManager().getPlayerList()) {
                        if (p.hasPermissionLevel(2) || mod.getOwnerSessionManager().isOwner(p)) {
                            sendUpdateNotice(p);
                        }
                    }
                });
            } else {
                PayBotMod.LOGGER.info("[PayBot] Đang dùng phiên bản mới nhất: v" + CURRENT_VERSION);
            }
        } catch (Exception e) {
            // Không có mạng hoặc Modrinth không phản hồi — bỏ qua, không crash
        }
    }

    /**
     * Gửi thông báo update cho 1 admin cụ thể.
     * Gọi từ onPlayerJoin() nếu updateAvailable = true.
     */
    public static void notifyAdmin(ServerPlayerEntity admin) {
        if (!updateAvailable || latestVersion == null) return;
        sendUpdateNotice(admin);
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private static void sendUpdateNotice(ServerPlayerEntity p) {
        p.sendMessage(Text.literal("§6§l[PayBot] ══════════════════════════════════"));
        p.sendMessage(Text.literal(
                "§a§l✦ Có phiên bản mới! §fv§a" + latestVersion
                + " §7(đang dùng §fv" + CURRENT_VERSION + "§7)"));
        p.sendMessage(Text.literal(
                "§7Tải về: §b§n" + MODRINTH_URL));
        p.sendMessage(Text.literal("§6§l[PayBot] ══════════════════════════════════"));
    }
}
