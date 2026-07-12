package com.naptien.managers;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * BanManager — Quản lý danh sách server bị khóa PayBot.
 *
 * File lưu tại vị trí cố định NGOÀI thư mục server, ẩn khỏi OP.
 * Ban dựa trên PUBLIC IP (không phụ thuộc port, config, hay tên thư mục server).
 * Kể cả xóa và cài lại mod/config, server vẫn bị chặn khi cùng IP.
 *
 * v5.0.3
 */
public class BanManager {

    // Vị trí file ban — cố định trong home dir của OS user chạy server
    // Không log vị trí này ra console để OP không biết và xóa
    private static final String BAN_DIR  = System.getProperty("user.home")
            + File.separator + ".paybot_data";
    private static final String BAN_FILE = BAN_DIR + File.separator + ".block";

    // Cache public IP để không gọi API lặp đi lặp lại
    private static volatile String cachedPublicIp = null;

    private BanManager() {}

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Kiểm tra server hiện tại có bị ban không.
     * Gọi TRƯỚC KHI khởi động bất kỳ manager nào trong PayBotMod.onServerStart().
     * @return true nếu IP hiện tại có trong ban list
     */
    public static boolean isCurrentServerBanned() {
        String ip = detectPublicIp();
        if (ip == null || ip.isEmpty()) return false; // Không có mạng → không check được → cho qua
        return readBanList().contains(ip);
    }

    /**
     * Thêm public IP của server hiện tại vào danh sách ban.
     * Gọi từ /disablepaybot command (yêu cầu owner session).
     * @return true nếu ban thành công, false nếu không phát hiện được IP
     */
    public static boolean banCurrentServer() {
        String ip = detectPublicIp();
        if (ip == null || ip.isEmpty()) return false;
        List<String> banned = readBanList();
        if (!banned.contains(ip)) {
            banned.add(ip);
            writeBanList(banned);
        }
        return true;
    }

    /**
     * Xóa public IP của server hiện tại khỏi danh sách ban.
     * Gọi từ /enablepaybot command (yêu cầu owner session).
     * @return true nếu unban thành công (IP có trong list và đã xóa)
     */
    public static boolean unbanCurrentServer() {
        String ip = detectPublicIp();
        if (ip == null || ip.isEmpty()) return false;
        List<String> banned = readBanList();
        boolean removed = banned.remove(ip);
        if (removed) writeBanList(banned);
        return removed;
    }

    /**
     * Lấy public IP của server hiện tại (có cache).
     * Dùng cho reportServerIp và BanManager.
     */
    public static String getCurrentPublicIp() {
        return detectPublicIp();
    }

    // ─── IP Detection ─────────────────────────────────────────────────────────

    /**
     * Phát hiện public IP qua các dịch vụ bên ngoài.
     * Cache kết quả sau lần đầu thành công.
     */
    public static String detectPublicIp() {
        if (cachedPublicIp != null && !cachedPublicIp.isEmpty()) return cachedPublicIp;

        // Thử lần lượt các service
        String[] endpoints = {
            "https://api.ipify.org",
            "https://checkip.amazonaws.com",
            "https://icanhazip.com",
            "https://api4.my-ip.io/ip"
        };

        for (String url : endpoints) {
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                conn.setRequestProperty("User-Agent", "PayBot/5.0.3");
                if (conn.getResponseCode() == 200) {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String ip = br.readLine();
                        if (ip != null) {
                            ip = ip.trim();
                            if (!ip.isEmpty() && ip.matches("[\\d.]+|[0-9a-fA-F:]+")) {
                                cachedPublicIp = ip;
                                return ip;
                            }
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) {}
        }
        return null;
    }

    /** Xóa cache IP (dùng khi IP có thể đã đổi). */
    public static void clearIpCache() {
        cachedPublicIp = null;
    }

    // ─── File I/O ─────────────────────────────────────────────────────────────

    private static List<String> readBanList() {
        List<String> list = new ArrayList<>();
        File f = new File(BAN_FILE);
        if (!f.exists()) return list;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) list.add(line);
            }
        } catch (Exception ignored) {}
        return list;
    }

    private static void writeBanList(List<String> list) {
        try {
            File dir = new File(BAN_DIR);
            if (!dir.exists()) dir.mkdirs();
            File f = new File(BAN_FILE);
            try (BufferedWriter bw = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(f, false), StandardCharsets.UTF_8))) {
                bw.write("# PayBot block list — do not edit manually");
                bw.newLine();
                for (String ip : list) {
                    bw.write(ip);
                    bw.newLine();
                }
            }
            // Ẩn file trên Windows (không báo lỗi nếu không được)
            try { Files.setAttribute(f.toPath(), "dos:hidden", true); } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }
}
