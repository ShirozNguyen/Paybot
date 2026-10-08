package com.paybot.telemetry;

import com.paybot.PayBotMod;
import dev.faststats.Config;
import dev.faststats.Metrics;
import dev.faststats.SimpleContext;
import dev.faststats.config.SimpleConfig;
import dev.faststats.internal.PlatformLoggerFactory;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Custom SimpleContext implementation cho Minecraft Forge.
 * Tuân thủ Quy Tắc 10 (Forge Context Responsibilities) và Quy Tắc 17 (Class đơn nhiệm).
 */
public final class ForgeFastStatsContext extends SimpleContext {

    private final String projectName;
    private final ScheduledExecutorService executor;
    private final Set<Future<?>> tasks = ConcurrentHashMap.newKeySet();
    private final ForgeSnapshotProvider snapshotProvider;

    public static class Factory extends SimpleContext.Factory<ForgeFastStatsContext, Factory> {
        private final String projectName;
        private final String token;
        private final ForgeSnapshotProvider snapshotProvider;

        public Factory(String projectName, String token, ForgeSnapshotProvider snapshotProvider) {
            this.projectName = projectName;
            this.token = token;
            this.snapshotProvider = snapshotProvider;
        }

        @Override
        public ForgeFastStatsContext create() {
            PlatformLoggerFactory loggerFactory = new PlatformLoggerFactory((level, message, throwable) -> {
                if (throwable != null) {
                    PayBotMod.LOGGER.warn("[FastStats] " + message, throwable);
                } else {
                    PayBotMod.LOGGER.info("[FastStats] " + message);
                }
            });

            Path configPath = FMLPaths.CONFIGDIR.get().resolve("faststats");
            Config config = SimpleConfig.read(configPath, loggerFactory);

            ForgeFastStatsContext context = new ForgeFastStatsContext(
                    this,
                    loggerFactory,
                    config,
                    projectName,
                    token,
                    snapshotProvider
            );
            context.initializeServices(this);
            return context;
        }
    }

    private ForgeFastStatsContext(Factory factory,
                                  PlatformLoggerFactory loggerFactory,
                                  Config config,
                                  String projectName,
                                  String token,
                                  ForgeSnapshotProvider snapshotProvider) {
        super(factory, loggerFactory, config, projectName, token);
        this.projectName = projectName;
        this.snapshotProvider = snapshotProvider;
        this.executor = Executors.newSingleThreadScheduledExecutor(new ForgeFastStatsThreadFactory());
    }

    @Override
    protected boolean preSubmissionStart() {
        return getConfig().preSubmissionStart(this);
    }

    @Override
    public String getProjectName() {
        return projectName;
    }

    @Override
    protected Metrics.Factory metricsFactory() {
        return factory -> new ForgeFastStatsMetrics(factory, snapshotProvider);
    }

    @Override
    public void scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        if (executor.isShutdown()) {
            return;
        }
        Future<?> task = executor.scheduleAtFixedRate(command, initialDelay, period, unit);
        tasks.add(task);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        for (Future<?> task : tasks) {
            try {
                task.cancel(false);
            } catch (Throwable ignored) {
            }
        }
        tasks.clear();
        executor.shutdown();
    }
}
