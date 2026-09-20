// v5.5.5 Part 94: Pure Java self-JAR loader detection and loader-specific update checking
package com.paybot.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.paybot.utils.JarLoaderDetector.JarLoaderType;

import java.util.ArrayList;
import java.util.List;

/**
 * LoaderSpecificVersionComparator — Lọc và so sánh phiên bản từ Modrinth theo đúng Loader của chính file JAR.
 * <p>
 * Tuyệt đối không so sánh phiên bản của các Loader khác.
 * Ví dụ: JAR đang chạy là Forge, nếu Modrinth có bản Fabric 5.5.6 nhưng Forge chỉ có 5.5.5 thì KHÔNG coi là có cập nhật.
 * </p>
 */
public class LoaderSpecificVersionComparator {

    public static class CheckResult {
        private final boolean updateAvailable;
        private final String latestVersion;
        private final String currentVersion;
        private final JarLoaderType jarLoaderType;
        private final String downloadUrl;

        public CheckResult(boolean updateAvailable, String latestVersion, String currentVersion,
                           JarLoaderType jarLoaderType, String downloadUrl) {
            this.updateAvailable = updateAvailable;
            this.latestVersion = latestVersion;
            this.currentVersion = currentVersion;
            this.jarLoaderType = jarLoaderType;
            this.downloadUrl = downloadUrl;
        }

        public boolean isUpdateAvailable() {
            return updateAvailable;
        }

        public String getLatestVersion() {
            return latestVersion;
        }

        public String getCurrentVersion() {
            return currentVersion;
        }

        public JarLoaderType getJarLoaderType() {
            return jarLoaderType;
        }

        public String getDownloadUrl() {
            return downloadUrl;
        }
    }

    /**
     * Phân tích JSON từ Modrinth và tìm bản cập nhật mới nhất dành riêng cho Loader của file JAR này.
     *
     * @param jsonRaw          Dữ liệu JSON thô trả về từ Modrinth API
     * @param jarLoaderType    Loại Loader của chính file JAR này
     * @param currentVersion   Phiên bản hiện tại của file JAR
     * @param currentMcVersion Phiên bản Minecraft của server (ví dụ 1.21.1), null nếu không lọc theo MC
     * @return CheckResult chứa thông tin so sánh
     */
    public static CheckResult evaluateUpdate(String jsonRaw, JarLoaderType jarLoaderType, String currentVersion, String currentMcVersion) {
        String cleanCurrent = extractCleanVersion(currentVersion);
        String downloadUrl = "https://modrinth.com/"
                + (jarLoaderType == JarLoaderType.PLUGIN ? "plugin" : "mod")
                + "/" + jarLoaderType.getModrinthSlug();

        if (jsonRaw == null || jsonRaw.trim().isEmpty()) {
            return new CheckResult(false, cleanCurrent, cleanCurrent, jarLoaderType, downloadUrl);
        }

        try {
            JsonElement parsed = new JsonParser().parse(jsonRaw);
            if (!parsed.isJsonArray()) {
                return new CheckResult(false, cleanCurrent, cleanCurrent, jarLoaderType, downloadUrl);
            }

            JsonArray versions = parsed.getAsJsonArray();
            if (versions.size() == 0) {
                return new CheckResult(false, cleanCurrent, cleanCurrent, jarLoaderType, downloadUrl);
            }

            String latestMatchingVersion = null;
            String latestMatchingCleanVersion = null;

            // Modrinth API trả về mảng các version đã được sắp xếp từ mới nhất đến cũ nhất
            for (JsonElement item : versions) {
                if (!item.isJsonObject()) continue;
                JsonObject verObj = item.getAsJsonObject();

                // 1. Kiểm tra xem version này có hỗ trợ đúng Loader của chính JAR này hay không
                List<String> loaders = extractLoaders(verObj);
                boolean isCompatibleLoader = false;
                for (String l : loaders) {
                    if (jarLoaderType.isCompatibleWithModrinth(l)) {
                        isCompatibleLoader = true;
                        break;
                    }
                }
                if (!isCompatibleLoader) continue;

                // 2. Kiểm tra xem version này có hỗ trợ đúng phiên bản Minecraft của server hiện tại không
                if (currentMcVersion != null && !currentMcVersion.trim().isEmpty()) {
                    List<String> gameVersions = extractGameVersions(verObj);
                    if (!gameVersions.isEmpty() && !gameVersions.contains(currentMcVersion.trim())) {
                        continue; // Bỏ qua nếu release này không dành cho phiên bản Minecraft hiện tại của server
                    }
                }

                if (verObj.has("version_number")) {
                    latestMatchingVersion = verObj.get("version_number").getAsString().trim();
                    latestMatchingCleanVersion = extractCleanVersion(latestMatchingVersion);
                    break; // Tìm thấy version mới nhất hợp lệ -> dừng duyệt
                }
            }

            if (latestMatchingVersion == null) {
                return new CheckResult(false, cleanCurrent, cleanCurrent, jarLoaderType, downloadUrl);
            }

            // So sánh số học phiên bản ngữ nghĩa (SemVer) trên Clean Version (bỏ qua build metadata sau dấu +)
            boolean hasNewer = compareVersions(latestMatchingCleanVersion, cleanCurrent) > 0;
            return new CheckResult(hasNewer, latestMatchingCleanVersion, cleanCurrent, jarLoaderType, downloadUrl);

        } catch (Throwable t) {
            return new CheckResult(false, cleanCurrent, cleanCurrent, jarLoaderType, downloadUrl);
        }
    }

    public static CheckResult evaluateUpdate(String jsonRaw, JarLoaderType jarLoaderType, String currentVersion) {
        return evaluateUpdate(jsonRaw, jarLoaderType, currentVersion, null);
    }

    /**
     * Tách bỏ phần build metadata sau dấu '+' theo đặc tả SemVer 2.0.0.
     * Ví dụ: "5.5.8+fabric.26.2" -> "5.5.8"
     */
    public static String extractCleanVersion(String version) {
        if (version == null) return "0.0.0";
        String v = version.trim();
        int plusIdx = v.indexOf('+');
        if (plusIdx >= 0) {
            v = v.substring(0, plusIdx);
        }
        return v.trim();
    }

    private static List<String> extractLoaders(JsonObject verObj) {
        List<String> result = new ArrayList<>();
        if (verObj.has("loaders") && verObj.get("loaders").isJsonArray()) {
            JsonArray arr = verObj.getAsJsonArray("loaders");
            for (JsonElement el : arr) {
                if (el.isJsonPrimitive()) {
                    result.add(el.getAsString().trim());
                }
            }
        }
        return result;
    }

    private static List<String> extractGameVersions(JsonObject verObj) {
        List<String> result = new ArrayList<>();
        if (verObj.has("game_versions") && verObj.get("game_versions").isJsonArray()) {
            JsonArray arr = verObj.getAsJsonArray("game_versions");
            for (JsonElement el : arr) {
                if (el.isJsonPrimitive()) {
                    result.add(el.getAsString().trim());
                }
            }
        }
        return result;
    }

    /**
     * So sánh 2 chuỗi version theo từng phần số học (Semantic Version).
     *
     * @return > 0 nếu a > b; < 0 nếu a < b; 0 nếu a == b
     */
    public static int compareVersions(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;

        String[] pa = a.trim().split("\\.");
        String[] pb = b.trim().split("\\.");
        int len = Math.max(pa.length, pb.length);

        for (int i = 0; i < len; i++) {
            int va = i < pa.length ? leadingInt(pa[i]) : 0;
            int vb = i < pb.length ? leadingInt(pb[i]) : 0;
            if (va != vb) {
                return Integer.compare(va, vb);
            }
        }
        return 0;
    }

    private static int leadingInt(String s) {
        if (s == null) return 0;
        StringBuilder digits = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            } else {
                break;
            }
        }
        return digits.length() == 0 ? 0 : Integer.parseInt(digits.toString());
    }
}
