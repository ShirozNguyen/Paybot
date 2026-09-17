// v5.5.5 Part 93: Folia and Folia-forks (Canvas) full audit and thread-safety compliance
package com.paybot.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.concurrent.TimeUnit;

/**
 * Tiện ích lập lịch tác vụ hỗ trợ đa nền tảng (Folia, Canvas, Paper, Purpur, Spigot).
 * Tự động phát hiện Folia/Canvas tại thời điểm chạy và điều hướng các tác vụ lập lịch tương ứng.
 */
public class SchedulerUtils {
    private static boolean isFolia = false;
    private static boolean isPaper = false;
    private static boolean isPurpur = false;

    static {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (ClassNotFoundException ignored) {}

        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
            isPaper = true;
        } catch (ClassNotFoundException e1) {
            try {
                Class.forName("io.papermc.paper.configuration.Configuration");
                isPaper = true;
            } catch (ClassNotFoundException ignored) {}
        }

        try {
            Class.forName("org.purpurmc.purpur.PurpurConfig");
            isPurpur = true;
        } catch (ClassNotFoundException ignored) {}
    }

    public static boolean isFolia() {
        return isFolia;
    }

    public static boolean isPaper() {
        return isPaper;
    }

    public static boolean isPurpur() {
        return isPurpur;
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

    /**
     * Chạy tác vụ trên Global Region Scheduler (Folia) — CHỈ dùng cho việc KHÔNG đụng tới
     * player/entity/world/chunk cụ thể nào (vd: console command dispatch, đọc/ghi config, log).
     * <p>
     * ⚠️ KHÔNG dùng hàm này (hay runSyncLater/runSyncTimer) cho tác vụ đụng inventory,
     * sendMessage, teleport... của 1 player cụ thể — Global Region Scheduler KHÔNG sở hữu
     * region của bất kỳ entity nào, gọi API entity từ đây trên Folia thật sẽ ném lỗi. Dùng
     * {@link #runForPlayer}/{@link #runForPlayerLater} cho các trường hợp đó thay vào đó.
     */
    public static void runSync(Plugin plugin, Runnable task) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** Xem lưu ý ở {@link #runSync} — cùng giới hạn (Global Region Scheduler). */
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

    /**
     * Chạy tác vụ đụng tới 1 player CỤ THỂ (inventory, sendMessage, GUI...) — trên Folia
     * dùng EntityScheduler của chính player đó (đúng region-thread sở hữu player).
     * <p>
     * LƯU Ý (Part 45): nếu player logout trước khi tác vụ kịp chạy, Folia coi entity là
     * "retired" và HUỶ tác vụ (không tự chạy lại khi player login lại — instance Player
     * cũ đã mất). Với tác vụ ngắn (vài tick, vd mở lại GUI) đây là hành vi ĐÚNG mong muốn.
     * Với tác vụ cần chạy dù player có relog hay không (vd hẹn giờ hết hạn dài — xem
     * QRMapManager 30 phút), KHÔNG dùng hàm này/runForPlayerLater cho phần delay — phải
     * đặt delay trên runSyncLater/runAsyncLater (không phụ thuộc entity) rồi mới gọi
     * runForPlayer (không delay) bên trong khi tới giờ, để không phụ thuộc vòng đời entity.
     */
    public static void runForPlayer(Plugin plugin, Player player, Runnable task) {
        if (isFolia && player != null) {
            player.getScheduler().run(plugin, scheduledTask -> task.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Giống {@link #runForPlayer} nhưng có delay — dùng entity scheduler của Folia
     * ({@code EntityScheduler.runDelayed(Plugin, Consumer, Runnable, long)}).
     * Cùng lưu ý vòng đời entity như {@link #runForPlayer} — chỉ dùng cho delay NGẮN,
     * nơi việc tác vụ tự huỷ khi player logout là hành vi chấp nhận được/mong muốn.
     */
    public static void runForPlayerLater(Plugin plugin, Player player, Runnable task, long delayTicks) {
        if (isFolia && player != null) {
            player.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /**
     * Chạy tác vụ gắn liền với 1 Location/Chunk cụ thể trong World (spawn block, particle, firework, v.v.).
     * Trên Folia/Canvas dùng RegionScheduler; trên Paper/Purpur/Spigot dùng BukkitScheduler.
     */
    public static void runAtLocation(Plugin plugin, Location location, Runnable task) {
        if (isFolia && location != null && location.getWorld() != null) {
            Bukkit.getRegionScheduler().execute(plugin, location, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Chạy tác vụ gắn liền với Location kèm độ trễ (delayTicks).
     */
    public static void runAtLocationLater(Plugin plugin, Location location, Runnable task, long delayTicks) {
        if (isFolia && location != null && location.getWorld() != null) {
            Bukkit.getRegionScheduler().runDelayed(plugin, location, scheduledTask -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /**
     * Hủy toàn bộ tác vụ của plugin khi tắt server hoặc reload plugin (dọn sạch tài nguyên triệt để).
     */
    public static void cancelAllTasks(Plugin plugin) {
        if (isFolia) {
            try {
                Bukkit.getAsyncScheduler().cancelTasks(plugin);
            } catch (Exception ignored) {}
            try {
                Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
            } catch (Exception ignored) {}
        } else {
            try {
                Bukkit.getScheduler().cancelTasks(plugin);
            } catch (Exception ignored) {}
        }
    }
}
