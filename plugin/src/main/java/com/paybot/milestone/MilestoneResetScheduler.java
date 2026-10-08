package com.paybot.milestone;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Bộ lập lịch quản lý tác vụ tự động reset mốc nạp theo chu kỳ thời gian (Scheduled Reset).
 * Chạy hoàn toàn trên luồng ngầm bất đồng bộ, không ảnh hưởng đến CPU tick của server.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm quản lý lịch trình reset mốc nạp.
 */
public final class MilestoneResetScheduler {

    private final MilestoneConfig config;
    private final MilestoneDatabaseService dbService;
    private final Logger logger;
    private final Consumer<String> broadcastConsumer;

    private ScheduledExecutorService executor;

    public MilestoneResetScheduler(MilestoneConfig config,
                                   MilestoneDatabaseService dbService,
                                   Logger logger,
                                   Consumer<String> broadcastConsumer) {
        this.config = config;
        this.dbService = dbService;
        this.logger = logger;
        this.broadcastConsumer = broadcastConsumer;
    }

    public synchronized void start() {
        stop();
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PayBot-Milestone-Scheduler");
            t.setDaemon(true);
            return t;
        });

        // Kiểm tra mỗi 60 giây một lần
        this.executor.scheduleAtFixedRate(this::checkResetSchedule, 10, 60, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void checkResetSchedule() {
        if (!config.isEnabled() || !config.isScheduledResetEnabled()) {
            return;
        }

        try {
            long now = System.currentTimeMillis();
            long lastReset = dbService.getLastResetTime();
            MilestoneResetUnit unit = config.getResetUnit();
            int interval = config.getResetInterval();

            if (MilestoneResetCalculator.shouldReset(now, lastReset, unit, interval)) {
                dbService.incrementCycleId();
                dbService.setLastResetTime(now);

                int newCycle = dbService.getCurrentCycleId();
                logger.info("[PayBot] [SCHEDULED-RESET] Đã tự động reset toàn bộ mốc nạp server (Chu kỳ mới: #" + newCycle + ", định kỳ: " + interval + " " + unit + ")!");

                if (config.isBroadcastOnReset() && broadcastConsumer != null) {
                    broadcastConsumer.accept(config.getResetBroadcastMessage());
                }
            }
        } catch (Throwable t) {
            logger.warning("[PayBot] Lỗi kiểm tra lịch reset mốc nạp: " + t.getMessage());
        }
    }
}
