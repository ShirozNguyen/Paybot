// v5.5.5 Part 94: Pure Java self-JAR loader detection and loader-specific update checking
package com.naptien.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.naptien.utils.JarLoaderDetector.JarLoaderType;

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
     * @param jsonRaw        Dữ liệu JSON thô trả về từ Modrinth API
     * @param jarLoaderType  Loại Loader của chính file JAR này
     * @param currentVersion Phiên bản hiện tại của file JAR
     * @return CheckResult chứa thông tin so sánh
     */
    public static CheckResult evaluateUpdate(String jsonRaw, JarLoaderType jarLoaderType, String currentVersion) {
        String cleanCurrent = currentVersion != null ? currentVersion.trim() : "0.0.0";
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

            // Modrinth API trả về mảng các version đã được sắp xếp từ mới nhất đến cũ nhất
            for (JsonElement item : versions) {
                if (!item.isJsonObject()) continue;
                JsonObject verObj = item.getAsJsonObject();

                // Lấy danh sách loader mà phiên bản này hỗ trợ
                List<String> loaders = extractLoaders(verObj);

                // Kiểm tra xem version này có hỗ trợ đúng Loader của chính JAR này hay không
                boolean isCompatible = false;
                for (String l : loaders) {
                    if (jarLoaderType.isCompatibleWithModrinth(l)) {
                        isCompatible = true;
                        break;
                    }
                }

                if (isCompatible && verObj.has("version_number")) {
                    latestMatchingVersion = verObj.get("version_number").getAsString().trim();
                    break; // Tìm thấy version mới nhất hỗ trợ đúng Loader này -> dừng duyệt
                }
            }

            if (latestMatchingVersion == null) {
                // Không tìm thấy version nào trên Modrinth dành cho Loader này
                return new CheckResult(false, cleanCurrent, cleanCurrent, jarLoaderType, downloadUrl);
            }

            // So sánh số học phiên bản ngữ nghĩa (Semantic Versioning)
            boolean hasNewer = compareVersions(latestMatchingVersion, cleanCurrent) > 0;
            return new CheckResult(hasNewer, latestMatchingVersion, cleanCurrent, jarLoaderType, downloadUrl);

        } catch (Throwable t) {
            return new CheckResult(false, cleanCurrent, cleanCurrent, jarLoaderType, downloadUrl);
        }
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
