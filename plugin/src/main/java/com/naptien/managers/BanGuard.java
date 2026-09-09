package com.naptien.managers;

import com.naptien.NapTienPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * BanGuard — Cơ chế nội bộ cho phép owner (qua phiên đăng nhập /paybotowner) chặn vĩnh
 * viễn PayBot trên 1 server cụ thể (vd server spam log/vi phạm chính sách/tự sửa plugin),
 * NGAY CẢ KHI admin server đó xoá hẳn plugin rồi tải lại bản mới, hoặc đổi sang port khác —
 * miễn còn CÙNG MỘT MÁY (cùng IP public thật) là vẫn bị nhận diện lại.
 * <p>
 * Cơ chế:
 *  - Vị trí file marker CỐ ĐỊNH (không qua config.yml, không cho phép chỉnh), nằm ở
 *    {@code user.home} của tiến trình Java đang chạy server (KHÔNG nằm trong thư mục
 *    plugins/PayBot/ — để xoá/tải lại plugin không xoá được nó), tên file được đặt giống
 *    file cache hệ thống thông thường để không gây chú ý.
 *  - Nội dung mỗi dòng = 1 IP bị chặn, encode Base64 đơn giản (không phải mã hoá bảo mật,
 *    chỉ để tránh "cat file lên là biết ngay đây là gì" với người tò mò không cố ý).
 *  - {@code isCurrentServerBanned()} được gọi NGAY ĐẦU onEnable(), tự resolve IP public
 *    thật của máy hiện tại (PublicIpResolver) rồi so khớp — KHÔNG ghi log/console bất kỳ
 *    chi tiết nào về việc đang kiểm tra (chỉ log kết quả CUỐI CÙNG nếu thực sự bị chặn,
 *    bằng đúng thông báo chung chung yêu cầu — không hề tiết lộ cơ chế).
 *  - Fail-OPEN: nếu không resolve được IP (mất mạng outbound) hoặc lỗi đọc file → coi như
 *    KHÔNG bị chặn (tránh chặn nhầm server hợp lệ chỉ vì lỗi mạng tạm thời).
 *
 * Changelog:
 *   v5.0.0 — Thêm mới (/disablepaybot, /enablepaybot).
 */
public final class BanGuard {

    private BanGuard() {}

    /** Vị trí cố định — KHÔNG đọc từ config, KHÔNG cho phép đổi qua bất kỳ lệnh nào. */
    private static File markerFile() {
        String home = System.getProperty("user.home", ".");
        return new File(home, ".jrt_module_cache.dat");
    }

    /**
     * Kiểm tra server hiện tại có đang bị chặn không. PHẢI gọi từ thread không phải
     * main thread của Bukkit nếu có thể (có gọi HTTP outbound, timeout tối đa ~7.5s),
     * nhưng vẫn an toàn gọi lúc onEnable() (đồng bộ) vì timeout đã được giới hạn chặt.
     */
    public static boolean isCurrentServerBanned() {
        try {
            List<String> banned = readBannedIps();
            if (banned.isEmpty()) return false;
            String myIp = PublicIpResolver.resolve();
            if (myIp == null) return false; // fail-open
            return banned.contains(myIp);
        } catch (Exception e) {
            return false; // fail-open — không để lỗi đọc file làm chặn nhầm
        }
    }

    /**
     * Owner chặn server hiện tại. Tự resolve IP public thật rồi ghi vào marker file.
     * @return IP đã bị chặn, hoặc null nếu không resolve được IP (thất bại).
     */
    public static String banCurrentServer() {
        String ip = PublicIpResolver.resolve();
        if (ip == null) return null;
        List<String> banned = readBannedIps();
        if (!banned.contains(ip)) {
            banned.add(ip);
            writeBannedIps(banned);
        }
        return ip;
    }

    /**
     * Owner mở chặn server hiện tại (gỡ đúng IP hiện tại khỏi marker file).
     * @return IP đã được gỡ chặn, hoặc null nếu không resolve được IP.
     */
    public static String unbanCurrentServer() {
        String ip = PublicIpResolver.resolve();
        if (ip == null) return null;
        List<String> banned = readBannedIps();
        if (banned.remove(ip)) {
            writeBannedIps(banned);
        }
        return ip;
    }

    // ─── File I/O (đơn giản, encode Base64 từng dòng) ──────────────────────────

    private static List<String> readBannedIps() {
        List<String> out = new ArrayList<>();
        File f = markerFile();
        if (!f.exists() || !f.isFile()) return out;
        try {
            List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    String decoded = new String(Base64.getDecoder().decode(line), StandardCharsets.UTF_8);
                    out.add(decoded.trim());
                } catch (IllegalArgumentException ignored) {
                    // dòng hỏng/không phải base64 hợp lệ → bỏ qua dòng đó
                }
            }
        } catch (IOException ignored) {
        }
        return out;
    }

    private static void writeBannedIps(List<String> ips) {
        File f = markerFile();
        try {
            StringBuilder sb = new StringBuilder();
            for (String ip : ips) {
                sb.append(Base64.getEncoder().encodeToString(ip.getBytes(StandardCharsets.UTF_8)));
                sb.append('\n');
            }
            try (FileOutputStream fos = new FileOutputStream(f, false)) {
                fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
            // Cố gắng ẩn file trên hệ điều hành hỗ trợ (Windows) — bỏ qua nếu fail.
            try { f.setReadable(true, true); f.setWritable(true, true); } catch (Exception ignored) {}
        } catch (IOException ignored) {
            // Lỗi ghi file không nên làm crash plugin — admin owner có thể thử lại lệnh.
        }
    }

    /** Thông báo CHUNG hiển thị khi server bị chặn — KHÔNG bao giờ tiết lộ cơ chế. */
    public static final String BLOCKED_MESSAGE =
            "Server hiện tại bị chặn do spam log/vi phạm chính sách/chỉnh sửa plugin (hoặc mod),... " +
            "vui lòng liên hệ admin để biết chi tiết";
}
