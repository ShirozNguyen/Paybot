// v5.5.5 Part 94: Pure Java self-JAR loader detection and loader-specific update checking
package com.paybot.utils;

import java.io.File;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarFile;

/**
 * JarLoaderDetector — Nhận diện loại Loader của CHÍNH FILE JAR đang thực thi trên server.
 * <p>
 * Lưu ý quan trọng:
 * Không nhận diện theo môi trường server máy chủ (như Arclight, Mohist, Magma, Banner - nơi
 * server vừa chạy Forge/Fabric vừa chạy Bukkit Plugin), mà nhận diện bản chất của chính file JAR
 * đang chứa class này.
 * </p>
 */
public class JarLoaderDetector {

    public enum JarLoaderType {
        PLUGIN("plugin", "Plugin (Paper/Folia/Purpur/Spigot/Bukkit)", "paybot",
                Arrays.asList("paper", "purpur", "folia", "spigot", "bukkit")),
        NEOFORGE("neoforge", "NeoForge Mod", "paybot",
                Collections.singletonList("neoforge")),
        FORGE("forge", "Forge Mod", "paybot",
                Collections.singletonList("forge")),
        QUILT("quilt", "Quilt Mod", "paybot",
                Arrays.asList("quilt", "fabric")),
        FABRIC("fabric", "Fabric Mod", "paybot",
                Collections.singletonList("fabric")),
        UNKNOWN("unknown", "Unknown Loader", "paybot",
                Collections.emptyList());

        private final String code;
        private final String displayName;
        private final String modrinthSlug;
        private final List<String> supportedModrinthLoaders;

        JarLoaderType(String code, String displayName, String modrinthSlug, List<String> supportedModrinthLoaders) {
            this.code = code;
            this.displayName = displayName;
            this.modrinthSlug = modrinthSlug;
            this.supportedModrinthLoaders = supportedModrinthLoaders;
        }

        public String getCode() {
            return code;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getModrinthSlug() {
            return modrinthSlug;
        }

        public List<String> getSupportedModrinthLoaders() {
            return supportedModrinthLoaders;
        }

        public boolean isCompatibleWithModrinth(String loaderName) {
            if (loaderName == null) return false;
            String lower = loaderName.trim().toLowerCase(Locale.ROOT);
            for (String supported : supportedModrinthLoaders) {
                if (supported.equalsIgnoreCase(lower)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final JarLoaderType MODULE_FALLBACK = JarLoaderType.FORGE;
    private static volatile JarLoaderType cachedType = null;

    /**
     * Tự động nhận diện Loader của CHÍNH FILE JAR đang chạy.
     * Sử dụng 100% Java thuần và chỉ kiểm tra nội tại của chính file JAR chứa class này.
     *
     * @return JarLoaderType tương ứng của file JAR.
     */
    public static JarLoaderType detectSelfLoader() {
        if (cachedType != null) {
            return cachedType;
        }

        synchronized (JarLoaderDetector.class) {
            if (cachedType != null) {
                return cachedType;
            }

            // 1. Kiểm tra qua JarURLConnection của chính class JarLoaderDetector
            try {
                URL classUrl = JarLoaderDetector.class.getResource("JarLoaderDetector.class");
                if (classUrl != null) {
                    URLConnection urlConn = classUrl.openConnection();
                    if (urlConn instanceof JarURLConnection) {
                        JarURLConnection jarConn = (JarURLConnection) urlConn;
                        JarFile jar = jarConn.getJarFile();
                        if (jar != null) {
                            JarLoaderType detected = inspectJarEntries(jar);
                            if (detected != JarLoaderType.UNKNOWN) {
                                cachedType = detected;
                                return cachedType;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }

            // 2. Kiểm tra qua CodeSource location của chính file JAR
            try {
                CodeSource cs = JarLoaderDetector.class.getProtectionDomain().getCodeSource();
                if (cs != null && cs.getLocation() != null) {
                    URL loc = cs.getLocation();
                    File file = new File(loc.toURI());
                    if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                        try (JarFile jar = new JarFile(file)) {
                            JarLoaderType detected = inspectJarEntries(jar);
                            if (detected != JarLoaderType.UNKNOWN) {
                                cachedType = detected;
                                return cachedType;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }

            // 3. Fallback: Kiểm tra resource stream nội tại của classloader
            try {
                if (JarLoaderDetector.class.getResource("/plugin.yml") != null
                        || JarLoaderDetector.class.getResource("/paper-plugin.yml") != null) {
                    cachedType = JarLoaderType.PLUGIN;
                    return cachedType;
                }
                if (JarLoaderDetector.class.getResource("/META-INF/neoforge.mods.toml") != null) {
                    cachedType = JarLoaderType.NEOFORGE;
                    return cachedType;
                }
                if (JarLoaderDetector.class.getResource("/META-INF/mods.toml") != null) {
                    cachedType = JarLoaderType.FORGE;
                    return cachedType;
                }                if (JarLoaderDetector.class.getResource("/fabric.mod.json") != null) {
                    cachedType = JarLoaderType.FABRIC;
                    return cachedType;
                }
                if (JarLoaderDetector.class.getResource("/quilt.mod.json") != null) {
                    cachedType = JarLoaderType.QUILT;
                    return cachedType;
                }
            } catch (Throwable ignored) {
            }

            // Fallback an toàn theo module
            cachedType = MODULE_FALLBACK;
            return cachedType;
        }
    }

    /**
     * Soi trực tiếp các entry bên trong file JAR để nhận diện loại Loader.
     */
    private static JarLoaderType inspectJarEntries(JarFile jar) {
        if (jar == null) return JarLoaderType.UNKNOWN;

        // Ưu tiên 1: Bukkit/Paper Plugin JAR có plugin.yml hoặc paper-plugin.yml
        if (jar.getJarEntry("plugin.yml") != null || jar.getJarEntry("paper-plugin.yml") != null) {
            return JarLoaderType.PLUGIN;
        }

        // Ưu tiên 2: NeoForge mod JAR có META-INF/neoforge.mods.toml
        if (jar.getJarEntry("META-INF/neoforge.mods.toml") != null) {
            return JarLoaderType.NEOFORGE;
        }

        // Ưu tiên 3: Forge mod JAR có META-INF/mods.toml
        if (jar.getJarEntry("META-INF/mods.toml") != null) {
            return JarLoaderType.FORGE;
        }        // Ưu tiên 4: Fabric mod JAR có fabric.mod.json (kể cả có thêm quilt.mod.json)
        if (jar.getJarEntry("fabric.mod.json") != null) {
            return JarLoaderType.FABRIC;
        }

        // Ưu tiên 5: Quilt mod JAR chỉ có quilt.mod.json thuần
        if (jar.getJarEntry("quilt.mod.json") != null) {
            return JarLoaderType.QUILT;
        }

        return JarLoaderType.UNKNOWN;
    }
}
