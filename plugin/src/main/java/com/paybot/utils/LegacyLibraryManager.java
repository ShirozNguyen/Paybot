package com.paybot.utils;

import com.paybot.PayBotPlugin;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * LegacyLibraryManager — Tự động kiểm tra và nạp các thư viện bắt buộc cho PayBot
 * trên các phiên bản máy chủ Minecraft không hỗ trợ cơ chế tự tải thư viện (Paper/Spigot &lt; 1.16.5).
 * <p>
 * Tuân thủ Rule 17: Tách biệt hoàn toàn thành 1 class đơn nhiệm phụ trách quản lý thư viện runtime.
 * </p>
 */
public final class LegacyLibraryManager {

    private LegacyLibraryManager() {}

    public static class RequiredLibrary {
        private final String name;
        private final String mainClass;
        private final String fileName;
        private final String downloadUrl;

        public RequiredLibrary(String name, String mainClass, String fileName, String downloadUrl) {
            this.name = name;
            this.mainClass = mainClass;
            this.fileName = fileName;
            this.downloadUrl = downloadUrl;
        }

        public String getName() {
            return name;
        }

        public String getMainClass() {
            return mainClass;
        }

        public String getFileName() {
            return fileName;
        }

        public String getDownloadUrl() {
            return downloadUrl;
        }
    }

    private static final List<RequiredLibrary> REQUIRED_LIBRARIES = new ArrayList<>();

    static {
        REQUIRED_LIBRARIES.add(new RequiredLibrary(
                "SQLite JDBC Driver",
                "org.sqlite.JDBC",
                "sqlite-jdbc-3.45.3.0.jar",
                "https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.45.3.0/sqlite-jdbc-3.45.3.0.jar"
        ));
        REQUIRED_LIBRARIES.add(new RequiredLibrary(
                "HikariCP Connection Pool",
                "com.zaxxer.hikari.HikariDataSource",
                "HikariCP-5.1.0.jar",
                "https://repo1.maven.org/maven2/com/zaxxer/HikariCP/5.1.0/HikariCP-5.1.0.jar"
        ));
        REQUIRED_LIBRARIES.add(new RequiredLibrary(
                "NanoHTTPD Web Server",
                "fi.iki.elonen.NanoHTTPD",
                "nanohttpd-2.3.1.jar",
                "https://repo1.maven.org/maven2/org/nanohttpd/nanohttpd/2.3.1/nanohttpd-2.3.1.jar"
        ));
        REQUIRED_LIBRARIES.add(new RequiredLibrary(
                "ZXing Core (QR Code Generator)",
                "com.google.zxing.qrcode.QRCodeWriter",
                "core-3.5.3.jar",
                "https://repo1.maven.org/maven2/com/google/zxing/core/3.5.3/core-3.5.3.jar"
        ));
        REQUIRED_LIBRARIES.add(new RequiredLibrary(
                "ZXing JavaSE (Image Writer)",
                "com.google.zxing.client.j2se.MatrixToImageWriter",
                "javase-3.5.3.jar",
                "https://repo1.maven.org/maven2/com/google/zxing/javase/3.5.3/javase-3.5.3.jar"
        ));
        REQUIRED_LIBRARIES.add(new RequiredLibrary(
                "MySQL Connector/J",
                "com.mysql.cj.jdbc.Driver",
                "mysql-connector-j-8.4.0.jar",
                "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar"
        ));
    }

    /**
     * Kiểm tra và nạp thư viện cho plugin.
     *
     * @param plugin Instance của PayBotPlugin
     * @return true nếu tất cả thư viện đã đầy đủ và sẵn sàng; false nếu thiếu thư viện và plugin đã bị vô hiệu hóa.
     */
    public static boolean ensureLibrariesLoaded(PayBotPlugin plugin) {
        // 1. Thử nạp động các file JAR có trong thư mục plugins/PayBot/libs/
        loadLocalJarFiles(plugin);

        // 2. Kiểm tra sự hiện diện của từng thư viện
        ClassLoader loader = plugin.getClass().getClassLoader();
        List<RequiredLibrary> missingLibraries = new ArrayList<>();

        for (RequiredLibrary lib : REQUIRED_LIBRARIES) {
            if (!isClassPresent(lib.getMainClass(), loader)) {
                missingLibraries.add(lib);
            }
        }

        // 3. Nếu đã đủ 100% thư viện -> Im lặng chạy tiếp, không in gì cả
        if (missingLibraries.isEmpty()) {
            return true;
        }

        // 4. Nếu thiếu thư viện -> In thông báo hướng dẫn chi tiết và tắt plugin
        printMissingLibrariesWarning(plugin, missingLibraries);
        plugin.getServer().getPluginManager().disablePlugin(plugin);
        return false;
    }

    /**
     * Quét và nạp động tất cả các file .jar nằm trong thư mục plugins/PayBot/libs/.
     */
    private static void loadLocalJarFiles(PayBotPlugin plugin) {
        try {
            File libsDir = new File(plugin.getDataFolder(), "libs");
            if (!libsDir.exists()) {
                libsDir.mkdirs();
                return;
            }

            File[] jarFiles = libsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
            if (jarFiles == null || jarFiles.length == 0) {
                return;
            }

            ClassLoader classLoader = plugin.getClass().getClassLoader();
            if (classLoader instanceof URLClassLoader urlLoader) {
                Method addUrlMethod = null;
                try {
                    addUrlMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                    addUrlMethod.setAccessible(true);
                } catch (Throwable ignored) {}

                if (addUrlMethod != null) {
                    for (File jarFile : jarFiles) {
                        try {
                            addUrlMethod.invoke(urlLoader, jarFile.toURI().toURL());
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static boolean isClassPresent(String className, ClassLoader loader) {
        try {
            Class.forName(className, false, loader);
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        }
    }

    private static void printMissingLibrariesWarning(PayBotPlugin plugin, List<RequiredLibrary> missing) {
        String border = "========================================================================================";
        plugin.getLogger().severe(border);
        plugin.getLogger().severe("[PayBot] PHÁT HIỆN THIẾU THƯ VIỆN BẮT BUỘC TRÊN MÁY CHỦ MINECRAFT CŨ!");
        plugin.getLogger().severe("Máy chủ của bạn đang chạy phiên bản không hỗ trợ cơ chế tự động tải thư viện (libraries:).");
        plugin.getLogger().severe("Để PayBot hoạt động được, bạn vui lòng tải các file JAR sau và đặt vào thư mục:");
        plugin.getLogger().severe("  ==> " + plugin.getDataFolder().getPath() + File.separator + "libs" + File.separator);
        plugin.getLogger().severe("");
        plugin.getLogger().severe("DANH SÁCH " + missing.size() + " THƯ VIỆN CÒN THIẾU KÈM LINK TẢI CHÍNH THỨC:");

        for (RequiredLibrary lib : missing) {
            plugin.getLogger().severe(" • " + lib.getName() + " (" + lib.getFileName() + "):");
            plugin.getLogger().severe("   Link: " + lib.getDownloadUrl());
        }

        plugin.getLogger().severe("");
        plugin.getLogger().severe("HƯỚNG DẪN CÀI ĐẶT:");
        plugin.getLogger().severe(" 1. Tải các file .jar ở các đường link trên.");
        plugin.getLogger().severe(" 2. Chép toàn bộ file vào thư mục: plugins" + File.separator + "PayBot" + File.separator + "libs" + File.separator);
        plugin.getLogger().severe(" 3. Khởi động lại máy chủ Minecraft.");
        plugin.getLogger().severe("");
        plugin.getLogger().severe("PayBot sẽ tạm thời tự động TẮT để bảo vệ dữ liệu máy chủ!");
        plugin.getLogger().severe(border);
    }
}
