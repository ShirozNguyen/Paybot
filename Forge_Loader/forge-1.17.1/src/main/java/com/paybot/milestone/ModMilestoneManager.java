package com.paybot.milestone;

import com.paybot.PayBotMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Facade trung tâm điều phối hệ thống mốc nạp (Milestones & Scheduled Reset) trên Mod Loader.
 * Hoạt động 100% Async trên Worker pool, không block main tick của MinecraftServer.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm điều phối mốc nạp Mod.
 */
public final class ModMilestoneManager {

    private static final AtomicReference<ModMilestoneManager> INSTANCE = new AtomicReference<>();

    private final MilestoneConfig config;
    private final MilestoneDatabaseService dbService;
    private final MilestoneEvaluationEngine evaluationEngine;
    private final MilestoneResetScheduler resetScheduler;
    private final ExecutorService workerPool;

    private volatile MinecraftServer server;

    private ModMilestoneManager(File dataFolder) {
        this.config = new MilestoneConfig(dataFolder);
        this.dbService = new MilestoneDatabaseService(dataFolder);
        this.dbService.init();

        this.evaluationEngine = new MilestoneEvaluationEngine(this.config, this.dbService);

        this.workerPool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "PayBot-Mod-Milestone-Worker");
            t.setDaemon(true);
            return t;
        });

        this.resetScheduler = new MilestoneResetScheduler(
                this.config,
                this.dbService,
                this::broadcastMessage
        );
        this.resetScheduler.start();
    }

    public static synchronized void initialize(File dataFolder) {
        if (INSTANCE.get() == null) {
            INSTANCE.set(new ModMilestoneManager(dataFolder));
        }
    }

    public static ModMilestoneManager getInstance() {
        return INSTANCE.get();
    }

    public void updateServer(MinecraftServer server) {
        this.server = server;
    }

    public synchronized void reload() {
        config.load();
        resetScheduler.start();
        PayBotMod.LOGGER.info("[PayBot] Đã reload thành công hệ thống milestones.yml!");
    }

    public void onPaymentApproved(String playerName, long amount) {
        if (!config.isEnabled() || playerName == null || playerName.isBlank()) {
            return;
        }

        workerPool.submit(() -> {
            try {
                int cycleId = dbService.getCurrentCycleId();
                dbService.recordTopup(playerName, amount, cycleId);

                long playerTotal = dbService.getPlayerCycleTotal(playerName, cycleId);
                long serverTotal = dbService.getServerCycleTotal(cycleId);

                evaluationEngine.evaluatePlayerMilestones(
                        playerName,
                        playerTotal,
                        serverTotal,
                        this::dispatchConsoleCommand
                );

                Set<String> onlineNames = new HashSet<>();
                MinecraftServer srv = this.server;
                if (srv != null && srv.getPlayerList() != null) {
                    for (ServerPlayer sp : srv.getPlayerList().getPlayers()) {
                        if (sp != null && sp.getName() != null) {
                            onlineNames.add(sp.getName().getString());
                        }
                    }
                }

                evaluationEngine.evaluateGlobalMilestones(
                        serverTotal,
                        onlineNames,
                        onlineNames, // Fallback online set cho mod
                        this::dispatchConsoleCommand,
                        (p, cmd) -> dbService.addOfflineReward(p, cmd, 0, cycleId)
                );

            } catch (Throwable t) {
                PayBotMod.LOGGER.warn("[PayBot] Lỗi xử lý mốc nạp cho {}: {}", playerName, t.getMessage());
            }
        });
    }

    public void onPlayerJoin(String playerName) {
        if (!config.isEnabled() || playerName == null || playerName.isBlank()) {
            return;
        }

        workerPool.submit(() -> {
            try {
                int cycleId = dbService.getCurrentCycleId();
                List<String> pending = dbService.pollOfflineRewards(playerName, cycleId);
                if (!pending.isEmpty()) {
                    PayBotMod.LOGGER.info("[PayBot] Đang trao bù {} phần thưởng mốc offline cho: {}", pending.size(), playerName);
                    for (String cmd : pending) {
                        dispatchConsoleCommand(cmd);
                    }
                }
            } catch (Throwable t) {
                PayBotMod.LOGGER.warn("[PayBot] Lỗi trao quà mốc offline cho {}: {}", playerName, t.getMessage());
            }
        });
    }

    private void dispatchConsoleCommand(String command) {
        if (command == null || command.isBlank()) return;
        MinecraftServer srv = this.server;
        if (srv == null) return;

        srv.execute(() -> {
            try {
                srv.getCommands().getDispatcher().execute(command, srv.createCommandSourceStack());
            } catch (Throwable t) {
                PayBotMod.LOGGER.warn("[PayBot] Lỗi thực thi lệnh trao thưởng mốc [{}]: {}", command, t.getMessage());
            }
        });
    }

    private void broadcastMessage(String message) {
        if (message == null || message.isBlank()) return;
        MinecraftServer srv = this.server;
        if (srv == null) return;

        srv.execute(() -> {
            try {
                srv.getCommands().getDispatcher().execute(
                        "say " + message.replace("&", "§"),
                        srv.createCommandSourceStack()
                );
            } catch (Throwable t) {
                PayBotMod.LOGGER.debug("[PayBot] Broadcast message error: {}", t.getMessage());
            }
        });
    }

    private long queryPlayerTotalTopup(String playerName) {
        return dbService != null ? dbService.getPlayerCycleTotal(playerName, dbService.getCurrentCycleId()) : 0L;
    }

    private long queryServerTotalTopup() {
        return dbService != null ? dbService.getServerCycleTotal(dbService.getCurrentCycleId()) : 0L;
    }

    public synchronized void shutdown() {
        if (resetScheduler != null) resetScheduler.stop();
        if (workerPool != null && !workerPool.isShutdown()) workerPool.shutdownNow();
        if (dbService != null) dbService.close();
    }
}
