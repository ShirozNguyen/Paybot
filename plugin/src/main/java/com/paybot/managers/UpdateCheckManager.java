// v5.5.5 Part 94: Pure Java self-JAR loader detection and loader-specific update checking
package com.paybot.managers;

import com.paybot.PayBotPlugin;
import com.paybot.utils.JarLoaderDetector;
import com.paybot.utils.JarLoaderDetector.JarLoaderType;
import com.paybot.utils.LoaderSpecificVersionComparator;
import com.paybot.utils.LoaderSpecificVersionComparator.CheckResult;
import com.paybot.utils.LoaderUpdateNotifier;
import com.paybot.utils.ModrinthVersionFetcher;
import com.paybot.utils.SchedulerUtils;

/**
 * UpdateCheckManager — Tự động kiểm tra phiên bản mới nhất từ Modrinth API
 * theo đúng Loader của chính file JAR đang chạy trên server.
 * <p>
 * Changelog:
 * - v4.0.1: Thêm mới UpdateCheckManager (tự động kiểm tra phiên bản từ Modrinth)
 * - v5.0.0: Cảnh báo khi phiên bản khác Modrinth
 * - v5.5.5 Part 94: Tự nhận diện Loader của CHÍNH FILE JAR đang chạy bằng Java thuần,
 *   chỉ đối chiếu phiên bản mới nhất dành riêng cho Loader đó trên Modrinth.
 * </p>
 */
public class UpdateCheckManager {

    private final PayBotPlugin plugin;
    private static volatile CheckResult lastCheckResult = null;

    public UpdateCheckManager(PayBotPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean isUpdateAvailable() {
        return lastCheckResult != null && lastCheckResult.isUpdateAvailable();
    }

    public static String getLatestVersion() {
        return lastCheckResult != null ? lastCheckResult.getLatestVersion() : null;
    }

    public static CheckResult getLastCheckResult() {
        return lastCheckResult;
    }

    /**
     * Gọi trong onEnable() để kiểm tra phiên bản bất đồng bộ, không block main thread.
     */
    public void checkForUpdates() {
        SchedulerUtils.runAsync(plugin, () -> {
            try {
                // 1. Tự nhận diện loại Loader của CHÍNH FILE JAR này
                JarLoaderType selfLoader = JarLoaderDetector.detectSelfLoader();
                String currentVersion = plugin.getDescription().getVersion().trim();

                // 2. Tạo User-Agent chuẩn cho Loader của JAR
                String userAgent = "PayBot-" + selfLoader.getCode() + "/" + currentVersion + " (update-checker)";

                // 3. Lấy dữ liệu phiên bản từ Modrinth API
                String jsonRaw = ModrinthVersionFetcher.fetchVersionsJson(selfLoader.getModrinthSlug(), userAgent);
                if (jsonRaw == null || jsonRaw.trim().isEmpty()) {
                    NotificationManager.warn(plugin, "update-available",
                            "[UpdateCheck] Không thể kết nối tới Modrinth API để kiểm tra cập nhật cho " + selfLoader.getDisplayName() + ".");
                    return;
                }

                // 4. Lọc và so sánh phiên bản dành riêng cho Loader của JAR này
                CheckResult result = LoaderSpecificVersionComparator.evaluateUpdate(jsonRaw, selfLoader, currentVersion);
                lastCheckResult = result;

                // 5. Xuất thông báo ra console server
                LoaderUpdateNotifier.printConsoleLog(
                        result,
                        msg -> NotificationManager.log(plugin, "update-available", msg),
                        msg -> NotificationManager.warn(plugin, "update-available", msg)
                );

            } catch (Throwable t) {
                NotificationManager.warn(plugin, "update-available",
                        "[UpdateCheck] Lỗi khi kiểm tra cập nhật: " + t.getMessage());
            }
        });
    }
}
