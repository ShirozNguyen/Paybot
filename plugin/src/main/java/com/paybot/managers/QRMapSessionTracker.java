// v5.5.5 Part 112: QR Map Session Tracker — Independent lifecycle & timer manager (Rule 17)
package com.paybot.managers;

import com.paybot.PayBotPlugin;
import com.paybot.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QRMapSessionTracker — Quản lý vòng đời bộ đếm thời gian của QR Map.
 * <p>
 * Đặc tính cốt lõi (Tối ưu cho Server Hàng Ngàn Người Chơi & Folia/Canvas):
 * 1. Khi player offline: Tạm dừng bộ đếm (Pause) và lưu thời gian còn lại. Không tốn bất kỳ thread/loop nào.
 * 2. Khi player online trở lại: Tiếp tục đếm ngược từ thời gian còn lại (Resume).
 * 3. Đếm ngược định kỳ tập trung (Single Lightweight Async Ticker): Dùng 1 scheduler duy nhất nhịp 5 giây,
 *    không tạo hàng ngàn timer rời rạc gây nghẽn thread pool.
 * 4. Folia & Canvas Thread-Safety: Mọi tương tác inventory hoặc gửi tin nhắn cho player đều dispatch qua
 *    {@link SchedulerUtils#runForPlayer(PayBotPlugin, Player, Runnable)}.
 */
public class QRMapSessionTracker implements Listener {

    public static class QRSession {
        public final UUID playerUuid;
        public final String playerName;
        public final String invoiceId;
        public final int mapId;
        public volatile long remainingSeconds;
        public volatile long lastActiveEpochMs;
        public volatile boolean isPaused;
        public volatile boolean isExpired;

        public QRSession(UUID playerUuid, String playerName, String invoiceId, int mapId, long durationSeconds) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.invoiceId = invoiceId;
            this.mapId = mapId;
            this.remainingSeconds = durationSeconds;
            this.lastActiveEpochMs = System.currentTimeMillis();
            this.isPaused = false;
            this.isExpired = false;
        }
    }

    private final PayBotPlugin plugin;
    private final QRMapManager qrMapManager;
    private final ConcurrentHashMap<UUID, QRSession> activeSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> invoiceToPlayer = new ConcurrentHashMap<>();

    public QRMapSessionTracker(PayBotPlugin plugin, QRMapManager qrMapManager) {
        this.plugin = plugin;
        this.qrMapManager = qrMapManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startCentralTicker();
    }

    /**
     * Đăng ký một phiên QR mới cho người chơi.
     *
     * @param player          Người chơi nhận QR
     * @param invoiceId       Mã đơn hàng
     * @param mapId           ID bản đồ
     * @param durationSeconds Thời gian tồn tại (ví dụ: 1800 giây = 30 phút)
     */
    public void startSession(Player player, String invoiceId, int mapId, long durationSeconds) {
        UUID uuid = player.getUniqueId();
        QRSession session = new QRSession(uuid, player.getName(), invoiceId, mapId, durationSeconds);
        activeSessions.put(uuid, session);
        invoiceToPlayer.put(invoiceId, uuid);
    }

    /**
     * Hủy phiên khi QR đã được xóa hoặc đã thanh toán thành công.
     */
    public void stopSession(String invoiceId) {
        UUID uuid = invoiceToPlayer.remove(invoiceId);
        if (uuid != null) {
            activeSessions.remove(uuid);
        }
    }

    /**
     * Kiểm tra xem đơn hàng này đã hết hạn hoặc không còn hiệu lực hay chưa.
     */
    public boolean isInvoiceExpiredOrCompleted(String invoiceId) {
        UUID uuid = invoiceToPlayer.get(invoiceId);
        if (uuid == null) return true; // Không còn phiên hoạt động -> xem như đã hết hạn / hoàn thành
        QRSession session = activeSessions.get(uuid);
        return session == null || session.isExpired || session.remainingSeconds <= 0;
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        QRSession session = activeSessions.get(uuid);
        if (session != null && !session.isPaused && !session.isExpired) {
            long now = System.currentTimeMillis();
            long elapsedSeconds = (now - session.lastActiveEpochMs) / 1000L;
            session.remainingSeconds = Math.max(0, session.remainingSeconds - elapsedSeconds);
            session.isPaused = true;
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        QRSession session = activeSessions.get(uuid);
        if (session != null) {
            if (session.remainingSeconds <= 0 || session.isExpired) {
                // Đã hết hạn trong lúc offline -> dọn dẹp ngay trên luồng của player
                expireSession(session, player);
            } else {
                // Tiếp tục đếm ngược
                session.lastActiveEpochMs = System.currentTimeMillis();
                session.isPaused = false;
            }
        }
    }

    // ── Ticker trung tâm (Zero Lag) ───────────────────────────────────────────

    private void startCentralTicker() {
        // Chạy async định kỳ mỗi 5 giây (100 ticks)
        SchedulerUtils.runAsyncTimer(plugin, this::tickSessions, 100L, 100L);
    }

    private void tickSessions() {
        if (activeSessions.isEmpty()) return;

        long now = System.currentTimeMillis();
        for (QRSession session : activeSessions.values()) {
            if (session.isPaused || session.isExpired) continue;

            long elapsed = (now - session.lastActiveEpochMs) / 1000L;
            if (elapsed <= 0) continue;

            session.lastActiveEpochMs = now;
            session.remainingSeconds -= elapsed;

            if (session.remainingSeconds <= 0) {
                session.isExpired = true;
                Player player = Bukkit.getPlayer(session.playerUuid);
                if (player != null && player.isOnline()) {
                    expireSession(session, player);
                }
            }
        }
    }

    private void expireSession(QRSession session, Player player) {
        stopSession(session.invoiceId);
        SchedulerUtils.runForPlayer(plugin, player, () -> {
            boolean removed = qrMapManager.removeQRMap(player, session.invoiceId);
            if (removed) {
                player.sendMessage(PayBotPlugin.f("§c[PayBot] §fQR chuyển khoản đã hết hạn (30 phút)! Dùng /napbank lại nếu cần. ⏰"));
                plugin.getLogger().info("QR map hết hạn: player=" + session.playerName + " invoice=" + session.invoiceId);
                SchedulerUtils.runAsync(plugin, () ->
                        plugin.getBotHttpClient().notifyNapBankExpired(session.invoiceId)
                );
            }
        });
    }
}
