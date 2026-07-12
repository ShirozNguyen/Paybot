package com.naptien.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.naptien.NapTienPlugin;
import org.bukkit.Bukkit;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * UpdateCheckManager — Tự động kiểm tra phiên bản mới nhất từ Modrinth API.
 * <p>
 * Chạy bất đồng bộ (async) một lần khi plugin khởi động.
 * Nếu phát hiện phiên bản mới hơn, in cảnh báo rõ ràng ra console.
 * </p>
 *
 * Changelog:
 * - v4.0.1: Thêm mới UpdateCheckManager (tự động kiểm tra phiên bản từ Modrinth)
 * - v5.0.0: Theo yêu cầu — chỉ cần phát hiện phiên bản hiện tại KHÁC bản trên Modrinth
 *   là báo (không cần phân biệt mới hơn/cũ hơn). Method {@link #compareVersions} vẫn
 *   giữ lại trong file (không xoá) phòng sau này cần dùng lại, nhưng hiện KHÔNG được
 *   gọi ở logic chính nữa.
 */
public class UpdateCheckManager {

    // Modrinth API trả về mảng version, phần tử [0] là mới nhất
    // include_changelog=false giảm kích thước response, Modrinth khuyến nghị dùng khi không cần changelog
    private static final String MODRINTH_VERSIONS_URL =
            "https://api.modrinth.com/v2/project/paybot/version?include_changelog=false";

    private static final String MODRINTH_PAGE_URL =
            "https://modrinth.com/plugin/paybot";

    private final NapTienPlugin plugin;

    public UpdateCheckManager(NapTienPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * So sánh 2 chuỗi version dạng "X.Y.Z..." theo TỪNG PHẦN SỐ (không so chuỗi thô —
     * so chuỗi thô sẽ sai, vd "10.0.0" so chuỗi nhỏ hơn "9.0.0" vì ký tự '1' < '9').
     * Phần không phải số ở cuối mỗi đoạn (vd "5.0.0-beta") bị cắt bỏ, chỉ lấy phần số đầu.
     *
     * @return số dương nếu a &gt; b, số âm nếu a &lt; b, 0 nếu bằng nhau.
     */
    static int compareVersions(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int va = i < pa.length ? leadingInt(pa[i]) : 0;
            int vb = i < pb.length ? leadingInt(pb[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private static int leadingInt(String s) {
        StringBuilder digits = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c)) digits.append(c); else break;
        }
        return digits.length() == 0 ? 0 : Integer.parseInt(digits.toString());
    }

    /**
     * Gọi method này trong {@code onEnable()} để kiểm tra phiên bản bất đồng bộ.
     * Không block main thread.
     */
    public void checkForUpdates() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection conn =
                        (HttpURLConnection) new URL(MODRINTH_VERSIONS_URL).openConnection();
                conn.setRequestMethod("GET");
                // Modrinth yêu cầu User-Agent hợp lệ
                conn.setRequestProperty("User-Agent",
                        "PayBot-Plugin/" + plugin.getDescription().getVersion()
                        + " (update-checker)");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setDoInput(true);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    NotificationManager.warn(plugin, "update-available",
                            "[UpdateCheck] Không thể kiểm tra cập nhật — Modrinth trả về HTTP "
                            + responseCode + ".");
                    return;
                }

                JsonArray versions = JsonParser
                        .parseReader(new InputStreamReader(conn.getInputStream()))
                        .getAsJsonArray();

                if (versions == null || versions.isEmpty()) {
                    NotificationManager.warn(plugin, "update-available",
                            "[UpdateCheck] Modrinth không trả về dữ liệu phiên bản.");
                    return;
                }

                // Phần tử đầu tiên = phiên bản mới nhất (Modrinth sắp xếp mới → cũ)
                JsonObject latest        = versions.get(0).getAsJsonObject();
                String     latestVersion = latest.get("version_number").getAsString().trim();
                String     currentVersion = plugin.getDescription().getVersion().trim();

                // Theo yêu cầu: chỉ cần phát hiện KHÁC NHAU là báo, không cần phân biệt
                // mới hơn/cũ hơn (compareVersions() vẫn giữ lại trong file, không dùng nữa).
                if (latestVersion.equalsIgnoreCase(currentVersion)) {
                    NotificationManager.log(plugin, "update-available",
                            "[PayBot] Plugin đang dùng phiên bản giống Modrinth (" + currentVersion + ").");
                } else {
                    NotificationManager.warn(plugin, "update-available", "================================================");
                    NotificationManager.warn(plugin, "update-available", " [PayBot] ĐÃ PHÁT HIỆN PHIÊN BẢN MỚI!");
                    NotificationManager.warn(plugin, "update-available", " Đã phát hiện phiên bản mới, phiên bản hiện tại");
                    NotificationManager.warn(plugin, "update-available", " là " + currentVersion
                            + ", vui lòng cập nhật lên phiên bản " + latestVersion);
                    NotificationManager.warn(plugin, "update-available", " Tải tại: " + MODRINTH_PAGE_URL);
                    NotificationManager.warn(plugin, "update-available", "================================================");
                }

            } catch (java.net.SocketTimeoutException e) {
                NotificationManager.warn(plugin, "update-available",
                        "[UpdateCheck] Hết thời gian kết nối khi kiểm tra cập nhật.");
            } catch (Exception e) {
                NotificationManager.warn(plugin, "update-available",
                        "[UpdateCheck] Lỗi khi kiểm tra cập nhật: " + e.getMessage());
            }
        });
    }
}
