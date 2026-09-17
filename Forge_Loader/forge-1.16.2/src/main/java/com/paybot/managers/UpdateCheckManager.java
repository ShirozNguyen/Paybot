// v5.5.5 Part 94: Pure Java self-JAR loader detection and loader-specific update checking
// v5.5.5 Part 85: Sync 1.16.5 Mojang API for forge-1.16.2
package com.paybot.managers;

import com.paybot.utils.JarLoaderDetector;
import com.paybot.utils.JarLoaderDetector.JarLoaderType;
import com.paybot.utils.ModrinthVersionFetcher;
import com.paybot.utils.LoaderSpecificVersionComparator;
import com.paybot.utils.LoaderSpecificVersionComparator.CheckResult;
import com.paybot.utils.LoaderUpdateNotifier;

import com.paybot.PayBotMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.TextComponent;

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

    
    
    private static volatile String downloadUrl = "https://modrinth.com/mod/paybot";

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
            // 1. Tự nhận diện loại Loader của CHÍNH FILE JAR này
            JarLoaderType selfLoader = JarLoaderDetector.detectSelfLoader();
            String currentVersion = getCurrentVersion();
            String userAgent = "PayBot-" + selfLoader.getCode() + "/" + currentVersion + " (update-checker)";

            // 2. Lấy danh sách phiên bản từ Modrinth API
            String jsonRaw = ModrinthVersionFetcher.fetchVersionsJson(selfLoader.getModrinthSlug(), userAgent);
            if (jsonRaw == null || jsonRaw.trim().isEmpty()) {
                return;
            }

            // 3. Lọc và so sánh phiên bản dành riêng cho Loader của chính file JAR này
            CheckResult result = LoaderSpecificVersionComparator.evaluateUpdate(jsonRaw, selfLoader, currentVersion);
            latestVersion = result.getLatestVersion();
            updateAvailable = result.isUpdateAvailable();
            downloadUrl = result.getDownloadUrl();

            // 4. Xuất log ra console server
            LoaderUpdateNotifier.printConsoleLog(result, PayBotMod.LOGGER::info, PayBotMod.LOGGER::warn);

            // 5. Nếu có bản cập nhật mới, gửi thông báo cho các Admin online
            if (updateAvailable) {
                mod.runOnMainThread(() -> {
                    for (ServerPlayer p : mod.getServer().getPlayerList().getPlayers()) {
                        if (p.hasPermissions(2) || mod.getOwnerSessionManager().isOwner(p)) {
                            sendUpdateNotice(p);
                        }
                    }
                });
            }
        } catch (Throwable ignored) {
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
        p.sendMessage(new TextComponent("§6§l[PayBot] ══════════════════════════════════"), ChatType.SYSTEM, net.minecraft.Util.NIL_UUID);
        p.sendMessage(new TextComponent(
                "§a§l✦ Có phiên bản mới! §fv§a" + latestVersion
                + " §7(đang dùng §fv" + getCurrentVersion() + "§7)"), ChatType.SYSTEM, net.minecraft.Util.NIL_UUID);
        p.sendMessage(new TextComponent(
                "§7Tải về: §b§n" + downloadUrl), ChatType.SYSTEM, net.minecraft.Util.NIL_UUID);
        p.sendMessage(new TextComponent("§6§l[PayBot] ══════════════════════════════════"), ChatType.SYSTEM, net.minecraft.Util.NIL_UUID);
    }
}
