package com.paybot.milestone;

import com.paybot.PayBotPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Facade trung tâm điều phối toàn bộ hệ thống mốc nạp (Milestones & Scheduled Reset).
 * Thiết kế Zero Main-Thread Lag: Toàn bộ tính toán và truy vấn CSDL chạy 100% trên worker pool ngầm.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm quản trị điều phối hệ thống mốc nạp.
 */
public final class MilestoneManager {

    private static final AtomicReference<MilestoneManager> INSTANCE = new AtomicReference<>();

    private final PayBotPlugin plugin;
    private final MilestoneConfig config;
    private final MilestoneDatabaseService dbService;
    private final MilestoneEvaluationEngine evaluationEngine;
    private final MilestoneResetScheduler resetScheduler;
    private final ExecutorService workerPool;

    private MilestoneManager(PayBotPlugin plugin) {
        this.plugin = plugin;
        this.config = new MilestoneConfig(plugin.getDataFolder(), plugin.getLogger());
        this.dbService = new MilestoneDatabaseService(plugin.getDataFolder(), plugin.getLogger());
        this.dbService.init();

        this.evaluationEngine = new MilestoneEvaluationEngine(this.config, this.dbService, plugin.getLogger());

        this.workerPool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "PayBot-Milestone-Worker");
            t.setDaemon(true);
            return t;
        });

        this.resetScheduler = new MilestoneResetScheduler(
                this.config,
                this.dbService,
                plugin.getLogger(),
                this::broadcastMessage
        );
        this.resetScheduler.start();
    }

    public static synchronized void initialize(PayBotPlugin plugin) {
        if (INSTANCE.get() == null) {
            INSTANCE.set(new MilestoneManager(plugin));
        }
    }

    public static MilestoneManager getInstance() {
        return INSTANCE.get();
    }

    /**
     * Tải lại toàn bộ file milestones.yml và làm mới bộ lập lịch reset.
     */
    public synchronized void reload() {
        config.load();
        resetScheduler.start();
        plugin.getLogger().info("[PayBot] Đã reload thành công hệ thống milestones.yml!");
    }

    /**
     * Kích hoạt bất đồng bộ khi một đơn nạp tiền (Bank hoặc Card) được duyệt thành công.
     */
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

                if (cycleId == 1) {
                    long legacyPlayer = queryPlayerTotalTopup(playerName);
                    if (legacyPlayer > playerTotal) playerTotal = legacyPlayer;
                    long legacyServer = queryServerTotalTopup();
                    if (legacyServer > serverTotal) serverTotal = legacyServer;
                }

                // 1. Thẩm định mốc cá nhân
                evaluationEngine.evaluatePlayerMilestones(
                        playerName,
                        playerTotal,
                        serverTotal,
                        this::dispatchConsoleCommand
                );

                // 2. Thẩm định mốc toàn server
                Set<String> onlineNames = new HashSet<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p != null && p.getName() != null) onlineNames.add(p.getName());
                }

                Set<String> allNames = new HashSet<>(onlineNames);
                for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                    if (op != null && op.getName() != null) allNames.add(op.getName());
                }
                allNames.addAll(onlineNames);

                evaluationEngine.evaluateGlobalMilestones(
                        serverTotal,
                        onlineNames,
                        allNames,
                        this::dispatchConsoleCommand,
                        (p, cmd) -> dbService.addOfflineReward(p, cmd, 0, cycleId)
                );

            } catch (Throwable t) {
                plugin.getLogger().warning("[PayBot] Lỗi xử lý mốc nạp cho " + playerName + ": " + t.getMessage());
            }
        });
    }

    /**
     * Kích hoạt bất đồng bộ khi người chơi tham gia server để trao thưởng offline nếu có.
     */
    public void onPlayerJoin(String playerName) {
        if (!config.isEnabled() || playerName == null || playerName.isBlank()) {
            return;
        }

        workerPool.submit(() -> {
            try {
                int cycleId = dbService.getCurrentCycleId();
                List<String> pendingCommands = dbService.pollOfflineRewards(playerName, cycleId);
                if (!pendingCommands.isEmpty()) {
                    plugin.getLogger().info("[PayBot] Đang trao bù " + pendingCommands.size() + " phần thưởng mốc offline cho: " + playerName);
                    for (String cmd : pendingCommands) {
                        dispatchConsoleCommand(cmd);
                    }
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[PayBot] Lỗi trao quà mốc offline cho " + playerName + ": " + t.getMessage());
            }
        });
    }

    private void dispatchConsoleCommand(String command) {
        if (command == null || command.isBlank()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (Throwable t) {
                plugin.getLogger().warning("[PayBot] Lỗi thực thi lệnh trao thưởng mốc [" + command + "]: " + t.getMessage());
            }
        });
    }

    private void broadcastMessage(String message) {
        if (message == null || message.isBlank()) return;
        String colored = ChatColor.translateAlternateColorCodes('&', message);
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcastMessage(colored));
    }

    private long queryPlayerTotalTopup(String playerName) {
        try {
            if (plugin.getTopupStatsManager() != null) {
                return plugin.getTopupStatsManager().getPlayerTotal(playerName);
            }
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    private long queryServerTotalTopup() {
        try {
            if (plugin.getTopupStatsManager() != null) {
                return plugin.getTopupStatsManager().getServerTotal();
            }
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    public synchronized void shutdown() {
        if (resetScheduler != null) resetScheduler.stop();
        if (workerPool != null && !workerPool.isShutdown()) workerPool.shutdownNow();
        if (dbService != null) dbService.close();
    }
}
