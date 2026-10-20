package com.paybot.gui.setup;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * CommentPreservingConfigWriter — v6.1.0 (Part 160)
 * TUÂN THỦ RULE 17: Chức năng cập nhật giá trị trong file cấu hình YAML
 * mà BẢO TOÀN 100% CẤU TRÚC, THỤT LỀ VÀ TOÀN BỘ CÁC DÒNG COMMENT (# ...).
 * <p>
 * Tuyệt đối không dùng YamlConfiguration.save() vì parser mặc định của Bukkit
 * sẽ xóa sạch toàn bộ comment hướng dẫn của admin.
 */
public final class CommentPreservingConfigWriter {

    private CommentPreservingConfigWriter() {}

    /**
     * Cập nhật một key đơn giản (top-level hoặc nested key theo đường dẫn path) trong file YAML.
     *
     * @param file     File YAML vật lý cần chỉnh sửa
     * @param keyPath  Đường dẫn key, ví dụ "database-type" hoặc "sepay.api-token"
     * @param newValue Giá trị mới cần gán (chuỗi, số, boolean)
     * @return true nếu tìm thấy và cập nhật thành công, false nếu không tìm thấy key
     */
    public static synchronized boolean updateKey(File file, String keyPath, Object newValue) {
        if (file == null || !file.exists() || keyPath == null || keyPath.trim().isEmpty()) {
            return false;
        }

        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            List<String> newLines = new ArrayList<>(lines.size());
            String[] parts = keyPath.split("\\.");
            int currentDepth = 0;
            boolean updated = false;

            for (String line : lines) {
                String trimmed = line.trim();
                // Bỏ qua dòng comment hoặc dòng trống
                if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                    newLines.add(line);
                    continue;
                }

                // Tính toán độ thụt lề (indent)
                int indent = getIndent(line);
                int expectedIndent = currentDepth * 2;

                if (parts.length == 1) {
                    // Top-level key
                    if (indent == 0 && trimmed.startsWith(parts[0] + ":")) {
                        String formattedValue = formatValue(newValue);
                        newLines.add(parts[0] + ": " + formattedValue);
                        updated = true;
                        continue;
                    }
                } else {
                    // Nested key logic
                    if (currentDepth < parts.length - 1) {
                        if (indent == expectedIndent && trimmed.startsWith(parts[currentDepth] + ":")) {
                            currentDepth++;
                        }
                    } else if (currentDepth == parts.length - 1) {
                        String targetKey = parts[parts.length - 1];
                        if (indent == expectedIndent && trimmed.startsWith(targetKey + ":")) {
                            String indentSpaces = " ".repeat(indent);
                            String formattedValue = formatValue(newValue);
                            newLines.add(indentSpaces + targetKey + ": " + formattedValue);
                            updated = true;
                            currentDepth = 0; // reset
                            continue;
                        }
                    }
                }

                newLines.add(line);
            }

            if (updated) {
                Files.write(file.toPath(), newLines, StandardCharsets.UTF_8);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static int getIndent(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') count++;
            else break;
        }
        return count;
    }

    private static String formatValue(Object val) {
        if (val == null) return "''";
        if (val instanceof Boolean || val instanceof Number) {
            return String.valueOf(val);
        }
        String s = String.valueOf(val);
        // Bao chuỗi trong nháy đơn hoặc nháy kép nếu chứa ký tự đặc biệt
        if (s.contains(":") || s.contains("#") || s.contains("'") || s.contains("\"") || s.contains("&") || s.contains("§")) {
            return "\"" + s.replace("\"", "\\\"") + "\"";
        }
        return "\"" + s + "\"";
    }
}
