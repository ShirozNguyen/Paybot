package com.naptien.managers;

import com.naptien.NapTienPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * StandaloneBankPoller — v5.1.0
 * <p>
 * Từ v5.1.0, plugin KHÔNG CÒN phụ thuộc vào Discord Bot để xử lý nạp ngân hàng.
 * Việc register order lên bot và poll trạng thái từ bot ĐÃ BỊ XOÁ BỎ.
 * Thay vào đó, NapTienPlugin (thông qua SePayApiClient) tự động poll trực tiếp
 * giao dịch từ API của SePay.
 * <p>
 * Class này được giữ lại dưới dạng một bộ khung trống để tương thích ngược 
 * với các object đang được khởi tạo, nhưng mọi logic bot HTTP request đã bị gỡ bỏ.
 */
public class StandaloneBankPoller {

    private final NapTienPlugin plugin;

    public StandaloneBankPoller(NapTienPlugin plugin) {
        this.plugin = plugin;
    }

    // ─── Bot Dependency Removed ──────────────────────────────────────────────

    public void retryUnregistered() {
        // v5.1.0: Không còn khái niệm đăng ký với bot nữa.
    }

    public void recoverOnStartup() {
        // v5.1.0: Không cần re-register với bot.
    }

    public void registerOrder(String invoiceId, int amount, String playerName) {
        // v5.1.0: Không đăng ký lên bot nữa. Giao dịch sẽ được match tự động 
        // nhờ SePayApiClient poll định kỳ trong NapTienPlugin.
    }

    public void pollPendingOrders() {
        // v5.1.0: Đã chuyển sang tự poll trực tiếp SePay API trong NapTienPlugin 
        // (xem pollSePayApiTransactions).
    }
}
