package com.paybot.managers;

import com.paybot.PayBotPlugin;
import com.paybot.utils.SchedulerUtils;

/**
 * StandaloneCardPoller — Kênh 2: Quét chủ động tra cứu trạng thái thẻ cào đang xử lý (Status 99).
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn chức năng Polling định kỳ thành 1 class đơn nhiệm độc lập.
 * Cứu cánh 100% cho các server chạy trên Hosting / Sau NAT không thể nhận Webhook Callback từ Internet.
 */
public final class StandaloneCardPoller {

    private final PayBotPlugin plugin;
    private volatile boolean running = false;

    public StandaloneCardPoller(PayBotPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void start() {
        if (running) return;
        running = true;

        // Chạy định kỳ mỗi 15 giây một lần trong luồng bất đồng bộ (20 ticks * 15 = 300 ticks)
        SchedulerUtils.runRepeatingAsync(plugin, () -> {
            if (!running) return;
            try {
                if (plugin.getStandaloneCardProcessor() != null) {
                    plugin.getStandaloneCardProcessor().pollPendingCards();
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[PayBot] StandaloneCardPoller lỗi ngoại lệ: " + t.getMessage());
            }
        }, 300L, 300L);

        plugin.getLogger().info("[PayBot] StandaloneCardPoller (Kênh 2 tra cứu thẻ chủ động) đã khởi chạy: 15s/lần.");
    }

    public synchronized void stop() {
        running = false;
    }
}
