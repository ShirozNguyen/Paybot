package com.paybot.telemetry;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ThreadFactory daemon an toàn cho FastStats telemetry trên Fabric & Quilt.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm tạo thread.
 */
public final class FabricFastStatsThreadFactory implements ThreadFactory {
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r, "PayBot-FastStats-Fabric-" + threadNumber.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    }
}
