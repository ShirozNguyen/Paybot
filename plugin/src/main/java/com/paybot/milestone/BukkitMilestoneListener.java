package com.paybot.milestone;

import com.paybot.events.PayBotTopupEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Lắng nghe sự kiện nạp tiền PayBotTopupEvent và sự kiện người chơi đăng nhập PlayerJoinEvent.
 * Kích hoạt luồng xử lý mốc nạp 100% Async không gây độ trễ server.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm lắng nghe sự kiện Bukkit cho mốc nạp.
 */
public final class BukkitMilestoneListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTopup(PayBotTopupEvent event) {
        if (event == null || event.getPlayerName() == null) return;
        MilestoneManager manager = MilestoneManager.getInstance();
        if (manager != null) {
            manager.onPaymentApproved(event.getPlayerName(), event.getAmountVnd());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (event == null || event.getPlayer() == null) return;
        MilestoneManager manager = MilestoneManager.getInstance();
        if (manager != null) {
            manager.onPlayerJoin(event.getPlayer().getName());
        }
    }
}
