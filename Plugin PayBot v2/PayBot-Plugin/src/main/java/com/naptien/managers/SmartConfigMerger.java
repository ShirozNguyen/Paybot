package com.naptien.managers;

import com.naptien.NapTienPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;

/**
 * SmartConfigMerger — v5.0.2
 * <p>
 * Bukkit's built-in {@code YamlConfiguration.saveConfig()} STRIPS tất cả comment khi ghi file.
 * Điều đó có nghĩa: khi admin upgrade plugin lên bản mới có thêm key, migrateConfig() thêm được
 * value nhưng KHÔNG có comment đi kèm — admin không biết key đó để làm gì, không có hướng dẫn.
 * <p>
 * SmartConfigMerger giải quyết bằng cách:
 * <ol>
 *   <li>Đọc template config.yml TỪNG DÒNG từ resources JAR (giữ nguyên comment và format)</li>
 *   <li>Parse thành danh sách "block": mỗi block = [dòng comment + blank] + [dòng key-value đầu tiên
 *       ở indent-0 + tất cả dòng con indent > 0 ngay sau]</li>
 *   <li>Với mỗi block có top-level key CHƯA CÓ trong file live của admin → append nguyên khối block
 *       vào cuối file live (giữ comment, giữ format, giữ indent)</li>
 *   <li>Không bao giờ đụng vào dòng đã có trong file live — giá trị admin tự chỉnh luôn được giữ</li>
 * </ol>
 * Cơ chế này bổ sung cho {@code migrateConfig()} (vẫn chạy trước để thêm key vào bộ nhớ): sau khi
 * migrateConfig() thêm value, SmartConfigMerger thêm BLOCK KÈM COMMENT vào file vật lý.
 * <p>
 * <b>Quan trọng:</b> SmartConfigMerger chỉ xét top-level key (indent 0). Các sub-key của 1 section
 * đã có được merge qua migrateConfig() bình thường (Bukkit YamlConfiguration đệ quy). Điều này
 * đủ vì trong thực tế thêm một hẳn top-level section mới mới cần comment hướng dẫn — thêm sub-key
 * nhỏ vào section cũ thì chỉ cần value là đủ.
 */
public class SmartConfigMerger {

    private SmartConfigMerger() {}

    /**
     * Chạy merge: đọc template từ JAR, tìm top-level key còn thiếu trong liveFile,
     * append block (kèm comment) vào cuối liveFile.
     *
     * @param plugin    instance plugin (để đọc resource + log)
     * @param liveFile  file config.yml thực tế của server này
     * @return số top-level block đã được thêm mới (0 = không có gì thay đổi)
     */
    public static int mergeWithComments(NapTienPlugin plugin, File liveFile) {
        try (InputStream tplStream = plugin.getResource("config.yml")) {
            if (tplStream == null) return 0;

            // ── Đọc template thành list<Block> ───────────────────────────────────
            List<Block> templateBlocks = parseBlocks(tplStream);

            // ── Đọc live file: tập hợp top-level key hiện có ────────────────────
            Set<String> existingTopKeys = readTopLevelKeys(liveFile);

            // ── Tìm block thiếu + append ─────────────────────────────────────────
            int added = 0;
            List<String> toAppend = new ArrayList<>();
            for (Block block : templateBlocks) {
                if (block.topKey == null) continue; // header-only block (comment đầu file)
                if (existingTopKeys.contains(block.topKey)) continue; // đã có rồi

                if (toAppend.isEmpty()) toAppend.add(""); // blank line trước section đầu tiên được append
                toAppend.addAll(block.lines);
                toAppend.add(""); // blank line sau mỗi block

                existingTopKeys.add(block.topKey); // tránh dup nếu template có key 2 lần
                added++;
                plugin.getLogger().info("[PayBot] SmartConfigMerger: thêm key mới '" + block.topKey
                        + "' (kèm comment) vào config.yml.");
            }

            if (!toAppend.isEmpty()) {
                // Append vào cuối file, đảm bảo file kết thúc bằng newline
                try (PrintWriter pw = new PrintWriter(new FileWriter(liveFile, true))) {
                    for (String line : toAppend) pw.println(line);
                }
            }
            return added;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[PayBot] SmartConfigMerger lỗi: " + e.getMessage(), e);
            return 0;
        }
    }

    /**
     * v5.0.3 [Part 18]: Chạy CẢ add lẫn remove trong 1 lần — dùng cho watcher 10s
     * (NapTienPlugin.startConfigWatcher()) để tự đồng bộ config.yml định kỳ, không
     * chỉ lúc khởi động. Trả int[]{added, removed}.
     *
     * Remove: block nào trong file live có topKey KHÔNG còn tồn tại trong template
     * hiện tại (config.yml đóng gói sẵn trong jar bản NÀY) → coi là key thừa từ bản
     * cũ, không còn tác dụng gì với plugin nữa → xoá cả key lẫn comment đi kèm.
     * Log rõ nội dung trước khi xoá (key + toàn bộ dòng) để admin xem lại log nếu
     * cần khôi phục tay. KHÔNG đụng vào key nào còn nằm trong template — an toàn
     * cho mọi giá trị admin tự chỉnh (chỉ đổi VALUE, không đổi KEY thì không sao).
     */
    public static int[] sync(NapTienPlugin plugin, File liveFile) {
        try (InputStream tplStream = plugin.getResource("config.yml")) {
            if (tplStream == null) return new int[]{0, 0};

            List<Block> templateBlocks = parseBlocks(tplStream);
            Set<String> templateTopKeys = new LinkedHashSet<>();
            for (Block b : templateBlocks) if (b.topKey != null) templateTopKeys.add(b.topKey);

            List<Block> liveBlocks;
            try (InputStream liveStream = new FileInputStream(liveFile)) {
                liveBlocks = parseBlocks(liveStream);
            }
            Set<String> existingTopKeys = new LinkedHashSet<>();
            for (Block b : liveBlocks) if (b.topKey != null) existingTopKeys.add(b.topKey);

            // ── REMOVE: block live có topKey không còn trong template ──────────
            int removed = 0;
            List<String> keptLines = new ArrayList<>();
            for (Block b : liveBlocks) {
                if (b.topKey != null && !templateTopKeys.contains(b.topKey)) {
                    plugin.getLogger().info("[PayBot] SmartConfigMerger: xoá key thừa '" + b.topKey
                            + "' (không còn trong template bản hiện tại): "
                            + String.join(" | ", b.lines).trim());
                    removed++;
                    continue; // bỏ block này — không ghi vào keptLines
                }
                keptLines.addAll(b.lines);
            }

            // ── ADD: template có key mà live thiếu ──────────────────────────────
            int added = 0;
            List<String> toAppend = new ArrayList<>();
            for (Block block : templateBlocks) {
                if (block.topKey == null) continue;
                if (existingTopKeys.contains(block.topKey)) continue;
                if (toAppend.isEmpty()) toAppend.add("");
                toAppend.addAll(block.lines);
                toAppend.add("");
                added++;
                plugin.getLogger().info("[PayBot] SmartConfigMerger: thêm key mới '" + block.topKey
                        + "' (kèm comment) vào config.yml.");
            }

            if (added > 0 || removed > 0) {
                keptLines.addAll(toAppend);
                try (PrintWriter pw = new PrintWriter(new FileWriter(liveFile, false))) {
                    for (String line : keptLines) pw.println(line);
                }
            }
            return new int[]{added, removed};

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[PayBot] SmartConfigMerger.sync lỗi: " + e.getMessage(), e);
            return new int[]{0, 0};
        }
    }

    // ─── Parsing ──────────────────────────────────────────────────────────────

    /** Một "block" trong config.yml template = các dòng comment/blank phía trước + dòng key + các dòng con. */
    private static class Block {
        String       topKey; // top-level key (không có indent, không có #), null nếu chỉ comment header
        List<String> lines = new ArrayList<>(); // TẤT CẢ dòng tạo nên block này (comment + key + children)
    }

    /**
     * Đọc InputStream của template, trả về list Block.
     * Logic:
     *   - Dòng bắt đầu bằng #, khoảng trắng, hoặc blank → pending lines (có thể là comment cho block tiếp theo)
     *   - Dòng bắt đầu bằng ký tự non-space, non-# → top-level key → bắt đầu block mới
     *   - Dòng có indent > 0 (bắt đầu bằng space) ngay sau key → thuộc block hiện tại
     */
    private static List<Block> parseBlocks(InputStream tplStream) throws IOException {
        List<String> allLines;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(tplStream, StandardCharsets.UTF_8))) {
            allLines = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) allLines.add(line);
        }

        List<Block> blocks = new ArrayList<>();
        Block current = null;
        List<String> pending = new ArrayList<>(); // comment/blank dòng chờ gắn vào block tiếp theo

        for (String line : allLines) {
            boolean isBlank   = line.isBlank();
            boolean isComment = line.startsWith("#");
            boolean isIndented = !isBlank && !isComment && line.startsWith(" ");
            boolean isTopKey  = !isBlank && !isComment && !isIndented
                    && !line.trim().startsWith("-") && line.contains(":");

            if (isTopKey) {
                // Bắt đầu block mới
                current = new Block();
                current.topKey = extractKey(line);
                current.lines.addAll(pending);
                current.lines.add(line);
                pending.clear();
                blocks.add(current);
            } else if (isIndented && current != null) {
                // Dòng con thuộc block đang mở
                current.lines.add(line);
            } else {
                // blank hoặc comment → pending (chờ xem có block tiếp theo không)
                if (current != null && !isBlank) {
                    // Comment ngay sau indented lines → thuộc block hiện tại thực ra là comment cho block MỚI
                    // Ta không gắn vào block cũ, để nó vào pending
                }
                pending.add(line);
            }
        }
        return blocks;
    }

    /** Lấy tên key từ dòng YAML top-level ("key: value" → "key", "key:" → "key"). */
    private static String extractKey(String line) {
        int colon = line.indexOf(':');
        return colon > 0 ? line.substring(0, colon).trim() : line.trim();
    }

    /** Đọc file live config.yml, trả về set tất cả top-level key đang có. */
    private static Set<String> readTopLevelKeys(File liveFile) throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        if (!liveFile.exists()) return keys;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(liveFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#") || line.startsWith(" ")
                        || line.trim().startsWith("-")) continue;
                String key = extractKey(line);
                if (!key.isEmpty()) keys.add(key);
            }
        }
        return keys;
    }
}
