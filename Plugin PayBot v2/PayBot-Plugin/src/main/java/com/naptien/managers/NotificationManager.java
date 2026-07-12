package com.naptien.managers;

import com.naptien.NapTienPlugin;
import org.bukkit.Bukkit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * NotificationManager — Cổng trung tâm cho TOÀN BỘ thông báo/log có thể bật-tắt của plugin.
 * <p>
 * Mọi điểm log.warning/log.severe/sendMessage liên quan tới lỗi hoặc sự kiện đáng chú ý
 * (tạo QR, gửi thẻ, lỗi SePay, lỗi web gạch thẻ, duyệt đơn fail, reward sai, pháo hoa fail,...)
 * nên đi qua đây thay vì gọi trực tiếp plugin.getLogger() / Bukkit.broadcastMessage, để admin
 * có thể tắt/mở từng loại riêng lẻ trong config.yml (nhóm "notifications:").
 * <p>
 * Mỗi "key" tương ứng 1 dòng config tại {@code notifications.<key>} (boolean, mặc định true).
 * <p>
 * v5.0.0 (fix 3): RATE-LIMIT giờ đọc từ config {@code Max_Notifications} (trước hardcode 5)
 * — tối đa N thông báo CÙNG KEY / 60 giây (tính dồn qua mọi method log/warn/notifyAdmins/
 * broadcast). Set {@code Max_Notifications: false} trong config.yml để TẮT hẳn rate-limit
 * (hiện toàn bộ thông báo, không giới hạn). Mặc định 20. Vượt mức → âm thầm bỏ qua (không
 * spam console/chat), tự reset sau khi qua cửa sổ 60s.
 *
 * Changelog:
 *   v5.0.0 — Thêm mới.
 *   v5.0.0 (fix 2) — Thêm rate-limit chống spam (hardcode 5 lần/loại/phút).
 *   v5.0.0 (fix 3) — Đổi rate-limit sang đọc config "Max_Notifications" (số hoặc false=tắt).
 */
public final class NotificationManager {

    private NotificationManager() {}

    private static final int  DEFAULT_MAX_PER_MINUTE = 20;
    private static final long WINDOW_MS              = 60_000L;

    /** key -> [mốc bắt đầu cửa sổ (ms), số lần đã bắn trong cửa sổ] */
    private static final ConcurrentHashMap<String, long[]> RATE_WINDOW = new ConcurrentHashMap<>();

    /**
     * Đọc giới hạn từ config "Max_Notifications":
     *   - không có / lỗi đọc → mặc định {@value #DEFAULT_MAX_PER_MINUTE}.
     *   - {@code false} → trả về -1 (KHÔNG giới hạn, hiện toàn bộ thông báo).
     *   - số nguyên (vd 10, 50) → dùng đúng số đó làm giới hạn/phút.
     *   - số ≤ 0 (admin lỡ điền 0/âm) → coi như không giới hạn (an toàn, tránh tự câm hết log).
     */
    private static int getMaxPerMinute(NapTienPlugin plugin) {
        Object val = plugin.getConfig().get("Max_Notifications", DEFAULT_MAX_PER_MINUTE);
        if (val instanceof Boolean b) {
            return b ? DEFAULT_MAX_PER_MINUTE : -1; // false = tắt giới hạn
        }
        if (val instanceof Number n) {
            int i = n.intValue();
            return i <= 0 ? -1 : i;
        }
        return DEFAULT_MAX_PER_MINUTE;
    }

    /** true nếu còn được phép bắn thông báo cho key này (chưa vượt giới hạn Max_Notifications/60s). */
    private static boolean allowRate(NapTienPlugin plugin, String key) {
        int max = getMaxPerMinute(plugin);
        if (max < 0) return true; // Max_Notifications: false → không giới hạn

        long now = System.currentTimeMillis();
        long[] state = RATE_WINDOW.computeIfAbsent(key, k -> new long[]{now, 0});
        synchronized (state) {
            if (now - state[0] > WINDOW_MS) {
                state[0] = now;
                state[1] = 0;
            }
            if (state[1] >= max) return false;
            state[1]++;
            return true;
        }
    }

    /** Đọc trạng thái bật/tắt của 1 loại thông báo. Mặc định true nếu chưa cấu hình. */
    public static boolean enabled(NapTienPlugin plugin, String key) {
        return plugin.getConfig().getBoolean("notifications." + key, true);
    }

    /** Log mức INFO ra console, chỉ khi key đang bật VÀ chưa vượt rate-limit. */
    public static void log(NapTienPlugin plugin, String key, String message) {
        if (enabled(plugin, key) && allowRate(plugin, key)) plugin.getLogger().info(message);
    }

    /** Log mức WARNING ra console, chỉ khi key đang bật VÀ chưa vượt rate-limit. */
    public static void warn(NapTienPlugin plugin, String key, String message) {
        if (enabled(plugin, key) && allowRate(plugin, key)) plugin.getLogger().warning(message);
    }

    /** Log mức WARNING kèm Throwable, chỉ khi key đang bật VÀ chưa vượt rate-limit. */
    public static void warn(NapTienPlugin plugin, String key, String message, Throwable t) {
        if (enabled(plugin, key) && allowRate(plugin, key)) plugin.getLogger().log(Level.WARNING, message, t);
    }

    /**
     * Gửi tin nhắn tới mọi admin online (permission naptien.admin), chỉ khi key đang bật
     * VÀ chưa vượt rate-limit. Dùng CHUNG bộ đếm rate-limit với log()/warn() — nếu console
     * đã bắn đủ Max_Notifications lần/phút cho key này thì notifyAdmins() cũng không bắn
     * thêm (tránh spam chat).
     */
    public static void notifyAdmins(NapTienPlugin plugin, String key, String message) {
        if (!enabled(plugin, key) || !allowRate(plugin, key)) return;
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("naptien.admin"))
                .forEach(p -> p.sendMessage(message));
    }

    /** Broadcast toàn server, chỉ khi key đang bật VÀ chưa vượt rate-limit. */
    public static void broadcast(NapTienPlugin plugin, String key, String message) {
        if (enabled(plugin, key) && allowRate(plugin, key)) Bukkit.broadcastMessage(message);
    }
}
