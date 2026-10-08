package com.paybot.telemetry;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ThreadFactory tạo daemon thread cho Forge FastStats Executor.
 * Tuân thủ Quy Tắc 23 (Executor/Thread Management) và Quy Tắc 17 (Class đơn nhiệm).
 */
public final class ForgeFastStatsThreadFactory implements ThreadFactory {

    private final AtomicInteger threadCount = new AtomicInteger(1);

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "faststats-forge-submitter-" + threadCount.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    }
}
