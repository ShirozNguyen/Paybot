package com.naptien.managers;

import com.naptien.PayBotMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;

/**
 * UpdateCheckManager — Tự động kiểm tra và so sánh phiên bản từ Modrinth API với phiên bản mod hiện tại.
 * 
 * Lấy danh sách phiên bản mới nhất từ Modrinth, so sánh với PayBotMod.getModVersion()
 * để phát hiện xem có phiên bản mới hơn hay không.
 */
public class UpdateCheckManager {

    private static final String MODRINTH_PROJECT = "paybotmod";
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

    public static String getCurrentVersion() {
        return PayBotMod.getModVersion();
    }

    /**
     * So sánh 2 phiên bản (X.Y.Z) theo thứ tự số học.
     * @return >0 nếu a > b, <0 nếu a < b, 0 nếu a == b
     */
    public static int compareVersions(String a, String b) {
        if (a == null || b == null) return 0;
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int va = i < pa.length ? leadingInt(pa[i]) : 0;
            int vb = i < pb.length ? leadingInt(pb[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private static int leadingInt(String s) {
        StringBuilder digits = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c)) digits.append(c); else break;
        }
        return digits.length() == 0 ? 0 : Integer.parseInt(digits.toString());
    }

    /** Gọi từ background thread khi server khởi động. */
    public void checkForUpdates() {
        try {
            String currentVersion = getCurrentVersion();
            HttpURLConnection conn = (HttpURLConnection) URI.create(MODRINTH_API).toURL().openConnection();
            conn.setRequestProperty("User-Agent",
                    "PayBot-Fabric/" + currentVersion + " (" + MODRINTH_URL + ")");
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
            String found = resp.substring(idx, resp.indexOf("\"", idx)).trim();

            latestVersion = found;
            // Tự động lấy bản mới nhất từ Modrinth rồi so sánh với phiên bản đang chạy hiện tại
            updateAvailable = compareVersions(latestVersion, currentVersion) > 0;

            if (updateAvailable) {
                PayBotMod.LOGGER.warn("[PayBot] ================================================");
                PayBotMod.LOGGER.warn("[PayBot]  [PayBot] ĐÃ PHÁT HIỆN PHIÊN BẢN MỚI TRÊN MODRINTH!");
                PayBotMod.LOGGER.warn("[PayBot]  Phiên bản hiện tại là " + currentVersion + ", phiên bản mới nhất là " + latestVersion);
                PayBotMod.LOGGER.warn("[PayBot]  Tải tại: " + MODRINTH_URL);
                PayBotMod.LOGGER.warn("[PayBot] ================================================");

                // Thông báo cho tất cả admin đang online
                mod.runOnMainThread(() -> {
                    for (ServerPlayer p : mod.getServer().getPlayerList().getPlayers()) {
                        if (p.hasPermissions(2) || mod.getOwnerSessionManager().isOwner(p)) {
                            sendUpdateNotice(p);
                        }
                    }
                });
            } else {
                PayBotMod.LOGGER.info("[PayBot] Đang dùng phiên bản mới nhất: v" + currentVersion);
            }
        } catch (Exception e) {
            // Không có mạng hoặc Modrinth không phản hồi — bỏ qua, không crash
        }
    }

    /**
     * Gửi thông báo update cho 1 admin cụ thể.
     * Gọi từ onPlayerJoin() nếu updateAvailable = true.
     */
    public static void notifyAdmin(ServerPlayer admin) {
        if (!updateAvailable || latestVersion == null) return;
        sendUpdateNotice(admin);
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private static void sendUpdateNotice(ServerPlayer p) {
        p.sendSystemMessage(Component.literal("§6§l[PayBot] ══════════════════════════════════"));
        p.sendSystemMessage(Component.literal(
                "§a§l✦ Có phiên bản mới! §fv§a" + latestVersion
                + " §7(đang dùng §fv" + getCurrentVersion() + "§7)"));
        p.sendSystemMessage(Component.literal(
                "§7Tải về: §b§n" + MODRINTH_URL));
        p.sendSystemMessage(Component.literal("§6§l[PayBot] ══════════════════════════════════"));
    }
}
