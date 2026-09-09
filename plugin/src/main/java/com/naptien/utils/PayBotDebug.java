package com.naptien.utils;

import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * PayBotDebug — v5.5.5 (Paper)
 *
 * Trung tâm log lỗi/warning chi tiết khi config "debug-mode: true".
 * Thay cho các khối `catch (Throwable ignored) {}` rải rác toàn project (xem
 * BÁO CÁO PHÂN TÍCH Mục 5.6 — 359 vị trí, phân loại Nhóm A/B).
 *
 * Nguyên tắc:
 *   - Mặc định (debug-mode=false): hành vi giống hệt trước khi có class này —
 *     hoàn toàn im lặng, KHÔNG log thêm gì, không ảnh hưởng hiệu năng/log file.
 *   - Khi bật: log ở mức WARNING kèm context rõ ràng + stacktrace nếu có, để
 *     admin/dev tra được nguyên nhân thật thay vì đoán mò.
 *   - CHỈ áp dụng cho Nhóm B (reflection version-adapter, lỗi mạng/API bên thứ 3,
 *     lỗi parse config...) — Nhóm A (đóng connection, CREATE INDEX...) không cần
 *     route qua đây vì log sẽ tạo noise không hữu ích.
 *
 * Độc lập hoàn toàn với PayBotDebug bên fabric/forge — không share code, đúng tinh
 * thần "Rule 17" (mỗi module tự chứa, không phụ thuộc chéo) đã áp dụng xuyên suốt dự án.
 */
public final class PayBotDebug {

    private static volatile boolean enabled = false;
    private static volatile Plugin  ownerPlugin;

    private PayBotDebug() {}

    /**
     * Gọi trong {@code onEnable()} và mỗi khi config được reload (lệnh /paybot reload,
     * hoặc lệnh riêng /paybot debug on|off nếu có) để đồng bộ giá trị mới nhất từ config.yml.
     */
    public static void setEnabled(Plugin plugin, boolean value) {
        ownerPlugin = plugin;
        boolean was = enabled;
        enabled = value;
        if (plugin != null && was != value) {
            plugin.getLogger().info("[PayBot] debug-mode = " + value
                    + (value ? " — từ giờ mọi lỗi/warning bị nuốt trước đây sẽ được log chi tiết."
                             : " — trở lại im lặng như mặc định."));
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Dùng thay cho {@code catch (Throwable ignored) {}} ở các vị trí Nhóm B.
     * An toàn khi debug-mode tắt: return ngay, không tốn chi phí ngoài 1 lần đọc boolean.
     *
     * @param plugin  instance plugin hiện tại; có thể truyền {@code null} để dùng lại
     *                instance đã lưu từ lần {@link #setEnabled} gần nhất.
     * @param context Mô tả ngắn, đủ rõ nơi lỗi xảy ra (class/method/thao tác cụ thể —
     *                ví dụ "DirectCardSubmit POST tới thesieure.com lần 3/5") để admin
     *                tra ra ngay không cần đọc lại source.
     * @param t       Throwable gốc bị nuốt trước đây, hoặc {@code null} nếu chỉ là 1
     *                tình huống bất thường không gắn với exception cụ thể (ví dụ:
     *                "request bị từ chối vì thiếu cấu hình").
     */
    public static void logSwallowed(Plugin plugin, String context, Throwable t) {
        if (!enabled) return;
        Plugin p = plugin != null ? plugin : ownerPlugin;
        if (p == null) return; // chưa init xong (quá sớm trong onEnable) — bỏ qua an toàn thay vì NPE
        if (t != null) {
            p.getLogger().log(Level.WARNING,
                    "[PayBot-Debug] " + context + " — " + t.getClass().getSimpleName() + ": " + t.getMessage(), t);
        } else {
            p.getLogger().warning("[PayBot-Debug] " + context);
        }
    }

    /** Overload tiện dùng khi không có sẵn biến plugin trong scope hiện tại. */
    public static void logSwallowed(String context, Throwable t) {
        logSwallowed(null, context, t);
    }
}
