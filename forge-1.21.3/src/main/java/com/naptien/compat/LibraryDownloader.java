package com.naptien.compat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Dynamic Library Downloader / Verifier cho PayBot.
 * Kiểm tra tính sẵn sàng của các thư viện phụ thuộc (SnakeYAML, ZXing, NanoHTTPD).
 * Tương thích 100% trên cả 5 ModLoaders & ServerLoaders: Fabric, Quilt, Forge, NeoForge, Bukkit/Paper.
 * Khi chạy trên ModLoader, các thư viện đã được Shade sẵn vào tệp JAR Mod.
 */
public class LibraryDownloader {

    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-LibDownloader");

    public static void checkAndDownloadLibraries(File configDir) {
        String[][] requiredLibs = {
            {
                "org.yaml.snakeyaml.Yaml",
                "https://repo1.maven.org/maven2/org/yaml/snakeyaml/2.2/snakeyaml-2.2.jar",
                "snakeyaml-2.2.jar"
            },
            {
                "com.google.zxing.MultiFormatWriter",
                "https://repo1.maven.org/maven2/com/google/zxing/core/3.5.3/core-3.5.3.jar",
                "core-3.5.3.jar"
            },
            {
                "com.google.zxing.client.j2se.MatrixToImageWriter",
                "https://repo1.maven.org/maven2/com/google/zxing/javase/3.5.3/javase-3.5.3.jar",
                "javase-3.5.3.jar"
            },
            {
                "fi.iki.elonen.NanoHTTPD",
                "https://repo1.maven.org/maven2/org/nanohttpd/nanohttpd/2.3.1/nanohttpd-2.3.1.jar",
                "nanohttpd-2.3.1.jar"
            }
        };

        File libsDir = new File(configDir, "libs");

        for (String[] libInfo : requiredLibs) {
            String className = libInfo[0];
            String downloadUrl = libInfo[1];
            String fileName = libInfo[2];

            if (isClassPresent(className)) {
                continue;
            }

            if (!libsDir.exists()) {
                libsDir.mkdirs();
            }

            File targetFile = new File(libsDir, fileName);
            if (!targetFile.exists() || targetFile.length() == 0) {
                LOGGER.info("[PayBot] Missing dependency {}, downloading from Maven Central...", fileName);
                downloadFile(downloadUrl, targetFile);
            }
            injectIntoClassLoader(targetFile);
        }
    }

    public static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, LibraryDownloader.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static void downloadFile(String urlStr, File outputFile) {
        try (InputStream in = new URL(urlStr).openStream()) {
            Files.copy(in, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[PayBot] Successfully downloaded {}", outputFile.getName());
        } catch (Exception e) {
            LOGGER.warn("[PayBot] Failed to download {}: {}", outputFile.getName(), e.getMessage());
        }
    }

    private static void injectIntoClassLoader(File jarFile) {
        try {
            ClassLoader classLoader = LibraryDownloader.class.getClassLoader();
            if (classLoader instanceof URLClassLoader) {
                Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                addURL.setAccessible(true);
                addURL.invoke(classLoader, jarFile.toURI().toURL());
            } else {
                LOGGER.info("[PayBot] ClassLoader ({}) is not URLClassLoader; relying on shaded/system dependencies.", classLoader.getClass().getName());
            }
        } catch (Exception e) {
            LOGGER.warn("[PayBot] Could not inject {} into ClassLoader: {}", jarFile.getName(), e.getMessage());
        }
    }
}
