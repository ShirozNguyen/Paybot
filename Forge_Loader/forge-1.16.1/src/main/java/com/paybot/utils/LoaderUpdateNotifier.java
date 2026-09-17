// v5.5.5 Part 94: Pure Java self-JAR loader detection and loader-specific update checking
package com.paybot.utils;

import com.paybot.utils.LoaderSpecificVersionComparator.CheckResult;

import java.util.function.Consumer;

/**
 * LoaderUpdateNotifier — Định dạng và xuất thông báo kiểm tra cập nhật ra Console của Minecraft Server.
 */
public class LoaderUpdateNotifier {

    /**
     * Xuất log kiểm tra phiên bản ra console server Minecraft.
     *
     * @param result     Kết quả kiểm tra đối chiếu phiên bản theo Loader
     * @param infoLogger Consumer ghi nhận log cấp độ INFO
     * @param warnLogger Consumer ghi nhận log cấp độ WARN
     */
    public static void printConsoleLog(CheckResult result, Consumer<String> infoLogger, Consumer<String> warnLogger) {
        if (result == null) return;

        String loaderName = result.getJarLoaderType().getDisplayName();
        String currentVer = result.getCurrentVersion();
        String latestVer  = result.getLatestVersion();

        if (result.isUpdateAvailable()) {
            if (warnLogger != null) {
                warnLogger.accept("================================================================");
                warnLogger.accept(String.format(" [PayBot] ⚠️ ĐÃ PHÁT HIỆN BẢN CẬP NHẬT MỚI CHO [%s]!", loaderName));
                warnLogger.accept(String.format(" [PayBot] • Loader của file JAR: %s", loaderName));
                warnLogger.accept(String.format(" [PayBot] • Phiên bản JAR hiện tại: %s", currentVer));
                warnLogger.accept(String.format(" [PayBot] • Phiên bản mới nhất trên Modrinth: %s", latestVer));
                warnLogger.accept(String.format(" [PayBot] • Trang tải về: %s", result.getDownloadUrl()));
                warnLogger.accept("================================================================");
            }
        } else {
            if (infoLogger != null) {
                infoLogger.accept(String.format("[PayBot] File JAR (%s) đang sử dụng phiên bản mới nhất (%s).",
                        loaderName, currentVer));
            }
        }
    }
}
