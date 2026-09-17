package com.naptien.log;

import com.naptien.PayBotMod;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LogSpamFilter — Ngăn spam log cùng loại quá N lần/phút.
 * Thread-safe. Giới hạn đọc từ config.Max_Notifications mỗi lần kiểm tra.
 *
 * v5.0.0:
 *   - MAX_PER_MINUTE nay là động, đọc từ PayBotConfig.getMaxNotificationsPerMinute()
 *   - Max_Notifications: 20   → 20 lần/phút
 *   - Max_Notifications: false → unlimited (allow() luôn trả true)
 *   - Max_Notifications: 0    → block tất cả
 */
public class LogSpamFilter {

    private static final long WINDOW_MS = 60_000L;

    private final ConcurrentHashMap<String, LinkedList<Long>> times = new ConcurrentHashMap<>();

    /**
     * Kiểm tra xem category này có được phép log không.
     * @return true nếu còn trong giới hạn, false nếu đã spam quá mức
     */
    public boolean allow(String category) {
        int maxPerMin = resolveMax();
        if (maxPerMin < 0) return true;  // false trong config → unlimited
        if (maxPerMin == 0) return false; // 0 → block tất cả

        long now = System.currentTimeMillis();
        LinkedList<Long> q = times.computeIfAbsent(category, k -> new LinkedList<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peek() > WINDOW_MS) q.poll();
            if (q.size() < maxPerMin) { q.offer(now); return true; }
            return false;
        }
    }

    /** Reset counter cho 1 category (dùng khi muốn force log lại). */
    public void reset(String category) {
        LinkedList<Long> q = times.get(category);
        if (q != null) synchronized (q) { q.clear(); }
    }

    /** Reset toàn bộ counter. */
    public void resetAll() { times.clear(); }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private static int resolveMax() {
        try {
            PayBotMod mod = PayBotMod.getInstance();
            if (mod == null || mod.getConfig() == null) return 20;
            return mod.getConfig().getMaxNotificationsPerMinute();
        } catch (Exception e) {
            return 20; // safe default
        }
    }
}
