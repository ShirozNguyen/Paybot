package com.paybot.managers;

import com.paybot.PayBotPlugin;
import org.bukkit.Bukkit;

import java.util.logging.Level;

/**
 * EmergencyShutdownHandler — v6.1.0 (Part 160)
 * TUÂN THỦ RULE 17: Chức năng xử lý khẩn cấp khi Cơ sở dữ liệu (MySQL / SQLite)
 * không thể kết nối hoặc gặp sự cố nghiêm trọng.
 * <p>
 * YÊU CẦU BẢO VỆ GIAO DỊCH NẠP TIỀN:
 * Nếu CSDL không dùng được (dù bất kỳ trường hợp nào: sai host, sai pass, mất mạng, timeout, hỏng file),
 * hệ thống:
 * 1. Ghi log cảnh báo nghiêm trọng (FATAL) ra Console.
 * 2. Vô hiệu hóa (disable) plugin để ngăn chặn nhận thêm bất kỳ lệnh nạp nào.
 * 3. Tắt toàn bộ Server (Bukkit.shutdown()) để tránh trường hợp người chơi nạp tiền mà CSDL không ghi nhận!
 * 4. TUYỆT ĐỐI KHÔNG FALLBACK tự tiện giữa MySQL và SQLite.
 */
public final class EmergencyShutdownHandler {

    private EmergencyShutdownHandler() {}

    public static void shutdownServer(PayBotPlugin plugin, String reason, Throwable cause) {
        if (plugin != null) {
            plugin.getLogger().severe("═══════════════════════════════════════════════════════════════════════");
            plugin.getLogger().severe("[PayBot FATAL] CƠ SỞ DỮ LIỆU GẶP SỰ CỐ NGHIÊM TRỌNG!");
            plugin.getLogger().severe("[PayBot FATAL] Lý do: " + reason);
            if (cause != null) {
                plugin.getLogger().log(Level.SEVERE, "[PayBot FATAL] Chi tiết ngoại lệ: " + cause.getMessage(), cause);
            }
            plugin.getLogger().severe("[PayBot FATAL] ĐỂ BẢO VỆ AN TOÀN TUYỆT ĐỐI CHO CÁC GIAO DỊCH NẠP TIỀN:");
            plugin.getLogger().severe("[PayBot FATAL] Hệ thống sẽ tự động TẮT TOÀN BỘ SERVER ngay lập tức!");
            plugin.getLogger().severe("[PayBot FATAL] Vui lòng kiểm tra lại cấu hình CSDL và khởi động lại server.");
            plugin.getLogger().severe("═══════════════════════════════════════════════════════════════════════");

            try {
                // Disable plugin ngay
                plugin.getServer().getPluginManager().disablePlugin(plugin);
            } catch (Throwable ignored) {}

            try {
                // Shutdown toan bo Server an toan
                plugin.getServer().shutdown();
            } catch (Throwable t) {
                // Neu running async hoac Folia, goi System.exit fallback
                try {
                    Bukkit.shutdown();
                } catch (Throwable ignored) {
                    System.exit(1);
                }
            }
        }
    }
}
