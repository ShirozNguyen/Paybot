package com.naptien.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.concurrent.TimeUnit;

/**
 * Tiện ích lập lịch tác vụ hỗ trợ đa nền tảng (Folia và Bukkit/Spigot/Paper truyền thống).
 * Tự động phát hiện Folia tại thời điểm chạy và điều hướng các tác vụ lập lịch tương ứng.
 */
public class SchedulerUtils {
    private static boolean isFolia = false;

    static {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (ClassNotFoundException ignored) {}
    }

    public static boolean isFolia() {
        return isFolia;
    }

    /**
     * Interface bọc tác vụ lập lịch để hỗ trợ hủy (cancel) một cách thống nhất.
     */
    public interface WrappedTask {
        void cancel();
    }

    public static void runAsync(Plugin plugin, Runnable task) {
        if (isFolia) {
            Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    public static void runAsyncLater(Plugin plugin, Runnable task, long delayTicks) {
        if (isFolia) {
            Bukkit.getAsyncScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayTicks * 50L, TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
        }
    }

    public static WrappedTask runAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (isFolia) {
            io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask = Bukkit.getAsyncScheduler()
                    .runAtFixedRate(plugin, st -> task.run(), delayTicks * 50L, periodTicks * 50L, TimeUnit.MILLISECONDS);
            return scheduledTask::cancel;
        } else {
            org.bukkit.scheduler.BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
            return bukkitTask::cancel;
        }
    }

    public static void runSync(Plugin plugin, Runnable task) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void runSyncLater(Plugin plugin, Runnable task, long delayTicks) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    public static WrappedTask runSyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (isFolia) {
            io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask = Bukkit.getGlobalRegionScheduler()
                    .runAtFixedRate(plugin, st -> task.run(), delayTicks, periodTicks);
            return scheduledTask::cancel;
        } else {
            org.bukkit.scheduler.BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
            return bukkitTask::cancel;
        }
    }

    public static void runForPlayer(Plugin plugin, Player player, Runnable task) {
        if (isFolia && player != null) {
            player.getScheduler().run(plugin, scheduledTask -> task.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
