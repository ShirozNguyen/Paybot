package com.naptien.managers;

import com.naptien.NapTienPlugin;
import org.bukkit.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.concurrent.ThreadLocalRandom;

/**
 * RewardEffectManager — hiệu ứng pháo hoa, âm thanh và thông báo action-bar
 * khi player nạp thành công.
 *
 * Config keys:
 *   Reward_Firework:     true/false  — bắn 2 quả pháo hoa (cách nhau 2 giây)
 *   Reward_Sound:        true/false  — âm thanh level-up (<100k) hoặc epic (≥100k)
 *   Reward_Notification: true/false  — thông báo action-bar màu xanh lá
 *
 * Changelog:
 *   v4.1.0 — Tái tạo từ .class (file nguồn gốc không có trong ZIP)
 *   v5.0.0 — Gọi qua NotificationManager (toggle "firework-fail" trong notifications:).
 *            Lưu ý: trigger() chỉ được gọi khi player ĐÃ XÁC NHẬN online (xem RewardDispatcher) —
 *            đơn duyệt lúc player offline sẽ KHÔNG gọi method này (xử lý ở RewardDispatcher).
 */
public class RewardEffectManager {

    private RewardEffectManager() {}

    /**
     * Kích hoạt tất cả hiệu ứng được cấu hình cho player vừa nạp thành công.
     * Phải gọi từ main thread.
     *
     * @param plugin  plugin instance
     * @param player  người chơi nhận thưởng
     * @param amount  số tiền nạp (VND), dùng để chọn hiệu ứng phù hợp
     */
    public static void trigger(NapTienPlugin plugin, Player player, int amount) {
        boolean firework     = plugin.getConfig().getBoolean("Reward_Firework",     true);
        boolean sound        = plugin.getConfig().getBoolean("Reward_Sound",        true);
        boolean notification = plugin.getConfig().getBoolean("Reward_Notification", true);

        if (notification) {
            // Action bar — chỉ player nhìn thấy
            player.sendTitle("", "§a§l✓ Nạp " + formatVnd(amount) + " VND thành công!", 5, 40, 15);
        }

        if (sound) {
            if (amount >= 100_000) {
                // Âm thanh hoành tráng hơn cho nạp lớn
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline())
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.2f);
                }, 10L);
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
        }

        if (firework) {
            spawnFirework(plugin, player);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) spawnFirework(plugin, player);
            }, 40L); // 2 giây sau
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static void spawnFirework(NapTienPlugin plugin, Player player) {
        try {
            Firework fw = player.getWorld().spawn(player.getLocation(), Firework.class);
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .withColor(randomColor(), Color.YELLOW)
                    .withFade(Color.WHITE)
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .trail(true)
                    .flicker(true)
                    .build());
            meta.setPower(1);
            fw.setFireworkMeta(meta);
        } catch (Exception e) {
            NotificationManager.warn(plugin, "firework-fail",
                    "[RewardEffect] Không thể tạo firework/thành tựu epic: " + e.getMessage());
        }
    }

    private static Color randomColor() {
        Color[] colors = {Color.RED, Color.BLUE, Color.GREEN, Color.AQUA,
                          Color.PURPLE, Color.ORANGE, Color.LIME};
        return colors[ThreadLocalRandom.current().nextInt(colors.length)];
    }

    /**
     * v5.0.0 — Hiện thông báo on-screen (sendTitle) NGAY khi plugin xác nhận NHẬN ĐƯỢC
     * thanh toán thật (card/bank), TRƯỚC khi admin duyệt và phần thưởng thực sự được cấp.
     * <p>
     * Trước đây bước này chỉ gửi 1 dòng chat thường — khiến nạp thật "kém cảm giác" hơn
     * hẳn so với /testnapbank, /testnapthe (giả lập đi thẳng tới trigger() ở trên, có
     * popup to ngay). Method này KHÔNG thay thế trigger() (vẫn chạy đầy đủ khi admin
     * duyệt xong — xem RewardDispatcher), chỉ thêm phản hồi tức thì tại thời điểm phát
     * hiện thanh toán, dùng CHUNG toggle "Reward_Notification" để admin có thể tắt nếu
     * không muốn 2 lần popup (lúc nhận + lúc duyệt).
     *
     * @param plugin  plugin instance
     * @param player  người chơi vừa thanh toán (null/offline → bỏ qua an toàn)
     * @param amount  số tiền đã thanh toán (VND)
     */
    public static void notifyPaymentReceived(NapTienPlugin plugin, Player player, int amount) {
        if (player == null || !player.isOnline()) return;
        boolean notification = plugin.getConfig().getBoolean("Reward_Notification", true);
        if (!notification) return;
        player.sendTitle("§a§l✓ Đã nhận thanh toán!", "§7" + formatVnd(amount) + " VND — đang chờ admin duyệt...", 5, 50, 15);
    }

    private static String formatVnd(int amount) {
        StringBuilder sb = new StringBuilder(String.valueOf(amount));
        int len = sb.length();
        for (int i = len - 3; i > 0; i -= 3) sb.insert(i, '.');
        return sb.toString();
    }
}
