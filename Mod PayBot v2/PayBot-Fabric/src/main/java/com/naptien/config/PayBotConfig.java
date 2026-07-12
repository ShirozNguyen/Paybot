package com.naptien.config;

import com.naptien.PayBotMod;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * PayBotConfig — YAML config wrapper dùng SnakeYAML.
 * Hỗ trợ nested paths (dấu chấm): "sepay-api.api-token", "notifications.qr-created"
 *
 * v5.0.0:
 *   - Smart auto-merge: tự phát hiện key thiếu → thêm default + apply ngay, KHÔNG đè giá trị cũ
 *   - Max_Notifications hỗ trợ number hoặc false (tắt giới hạn)
 *   - Notifications section đầy đủ khớp config template
 *   - sepay-api section (Phase B direct polling)
 */
public class PayBotConfig {

    public static final String HARDCODED_CALLBACK_URL =
            "https://unwarrantably-horrent-jacki.ngrok-free.dev/api/sepay-ipn";

    private final Path configPath;
    private Map<String, Object> data = new LinkedHashMap<>();
    private final Yaml yaml;

    /** v5.0.5 [Part 18]: getter cho watcher 10s ở PayBotMod dùng check mtime file. */
    public Path getConfigPath() {
        return configPath;
    }

    public PayBotConfig(Path configPath) {
        this.configPath = configPath;
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setIndent(2);
        opts.setPrettyFlow(true);
        this.yaml = new Yaml(opts);
    }

    // ─── Load / Save ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public void load() {
        try {
            Files.createDirectories(configPath.getParent());
            if (Files.exists(configPath)) {
                try (Reader r = new InputStreamReader(Files.newInputStream(configPath), StandardCharsets.UTF_8)) {
                    Object loaded = yaml.load(r);
                    data = (loaded instanceof Map<?,?> m) ? deepNormalizeMap(m) : new LinkedHashMap<>();
                }
            } else {
                // Config mới → tạo từ template hoặc default
                if (!copyTemplate()) {
                    data = defaultConfig();
                    save();
                    return;
                }
                try (Reader r = new InputStreamReader(Files.newInputStream(configPath), StandardCharsets.UTF_8)) {
                    Object loaded = yaml.load(r);
                    data = (loaded instanceof Map<?,?> m) ? deepNormalizeMap(m) : defaultConfig();
                }
            }
            // Chạy auto-merge cho cả config mới và cũ
            autoMergeAndMigrate();
        } catch (Exception e) {
            PayBotMod.LOGGER.error("[PayBot] Lỗi load config: " + e.getMessage());
            data = defaultConfig();
        }
    }

    private boolean copyTemplate() {
        try (InputStream is = PayBotConfig.class.getResourceAsStream("/config-template.yml")) {
            if (is == null) return false;
            Files.copy(is, configPath, StandardCopyOption.REPLACE_EXISTING);
            PayBotMod.LOGGER.info("[PayBot] Đã tạo config.yml từ template.");
            return true;
        } catch (IOException e) {
            PayBotMod.LOGGER.warn("[PayBot] Không copy được template: " + e.getMessage());
            return false;
        }
    }

    /**
     * Auto-merge: so sánh config hiện tại với defaultConfig(), thêm key còn thiếu.
     * Không bao giờ ghi đè giá trị đã được admin tuỳ chỉnh.
     * Cũng chạy migrate() cho các migration cố định.
     */
    private void autoMergeAndMigrate() {
        boolean changed = mergeMissingDefaults(data, defaultConfig(), "");

        // Migration cố định (không thể tự động): force plugin-callback-url
        String currentCb = getString("plugin-callback-url", "").trim();
        if (!HARDCODED_CALLBACK_URL.equals(currentCb)) {
            set("plugin-callback-url", HARDCODED_CALLBACK_URL);
            changed = true;
        }

        // v5.0.0 FIX: tự sửa 1 LẦN nếu plugin-port vẫn còn đúng giá trị lỗi cũ (25565)
        if (getInt("plugin-port", -1) == 25565) {
            set("plugin-port", 25580);
            changed = true;
            PayBotMod.LOGGER.warn("[PayBot] Tự sửa plugin-port: 25565 → 25580 (25565 trùng port chơi "
                    + "Minecraft mặc định, khiến HTTP server của mod không bind được).");
        }

        // v5.0.2 FIX: xoá "bot-api-key" khỏi config nếu còn sót từ bản cũ
        // (key này đã HARDCODE trong code — admin không cần và không nên tự đổi)
        if (getString("bot-api-key", null) != null) {
            set("bot-api-key", null);
            changed = true;
            PayBotMod.LOGGER.info("[PayBot] Đã xoá \"bot-api-key\" khỏi config.yml (key này giờ hardcode "
                    + "trong code, không còn là tuỳ chọn nữa).");
        }

        // v5.0.2 FIX: transfer-content.code-mode = -1 (cũ) → 0 (chỉ số, an toàn cho SePay webhook)
        // Chỉ tự sửa nếu ĐÚNG giá trị lỗi cũ = -1, không đụng nếu admin đã tự đổi sang giá trị khác
        if (getInt("transfer-content.code-mode", 0) == -1) {
            set("transfer-content.code-mode", 0);
            changed = true;
            PayBotMod.LOGGER.warn("[PayBot] Tự sửa transfer-content.code-mode: -1 → 0 (chỉ số). "
                    + "Giá trị -1 (lẫn chữ A-F) có thể khiến SePay ÂM THẦM không gửi webhook.");
        }

        // v5.0.2 FIX: prefix "PB" → "NT" (1 lần duy nhất, đánh dấu bằng cờ nội bộ)
        if (!getBoolean("_migrated-prefix-5-0-2", false)) {
            if ("PB".equalsIgnoreCase(getString("transfer-content.prefix", "").trim())) {
                set("transfer-content.prefix", "NT");
                PayBotMod.LOGGER.info("[PayBot] Tự sửa transfer-content.prefix: \"PB\" → \"NT\" (1 lần duy nhất).");
            }
            set("_migrated-prefix-5-0-2", true);
            changed = true;
        }

        if (changed) {
            save();
            PayBotMod.LOGGER.info("[PayBot] Config đã được tự động cập nhật với các key mới/thiếu.");
        }

        // v5.0.2: Sau khi merge value, append block còn thiếu KÈM COMMENT từ template
        // vào file vật lý (SmartConfigMerge). Reload in-memory sau nếu có thay đổi.
        if (appendMissingBlocksFromTemplate()) {
            load(); // reload để in-memory khớp với file vật lý vừa được append
        }
    }

    /**
     * Đệ quy so sánh target với defaults, thêm key còn thiếu vào target.
     * @return true nếu có thay đổi
     */
    @SuppressWarnings("unchecked")
    private boolean mergeMissingDefaults(Map<String, Object> target,
                                         Map<String, Object> defaults,
                                         String pathPrefix) {
        boolean changed = false;
        for (Map.Entry<String, Object> e : defaults.entrySet()) {
            String key      = e.getKey();
            Object defVal   = e.getValue();
            String fullPath = pathPrefix.isEmpty() ? key : pathPrefix + "." + key;

            if (!target.containsKey(key)) {
                // Key hoàn toàn chưa có → thêm vào với giá trị default
                target.put(key, defVal instanceof Map<?,?> m ? deepCopyMap(deepNormalizeMap(m)) : defVal);
                PayBotMod.LOGGER.info("[PayBot] Config: tự thêm key thiếu '" + fullPath + "'");
                changed = true;
            } else if (defVal instanceof Map<?,?> defMap) {
                // Cả target và default đều là section → đệ quy
                Object existing = target.get(key);
                if (existing instanceof Map<?,?>) {
                    Map<String, Object> targetSection = (Map<String, Object>) existing;
                    if (mergeMissingDefaults(targetSection, deepNormalizeMap(defMap), fullPath))
                        changed = true;
                }
                // Nếu existing không phải Map → giữ nguyên giá trị admin đã set
            }
            // Key đã có + không phải section → không đụng vào
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopyMap(Map<String, Object> src) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : src.entrySet())
            copy.put(e.getKey(), e.getValue() instanceof Map<?,?> m
                    ? deepCopyMap((Map<String, Object>) m) : e.getValue());
        return copy;
    }

    public synchronized void save() {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer w = new OutputStreamWriter(Files.newOutputStream(configPath), StandardCharsets.UTF_8)) {
                yaml.dump(data, w);
            }
        } catch (Exception e) {
            PayBotMod.LOGGER.error("[PayBot] Lỗi lưu config: " + e.getMessage());
        }
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public String getString(String path, String def) {
        Object v = get(path);
        return v != null ? v.toString() : def;
    }

    public int getInt(String path, int def) {
        Object v = get(path);
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return def; }
    }

    public long getLong(String path, long def) {
        Object v = get(path);
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return def; }
    }

    public boolean getBoolean(String path, boolean def) {
        Object v = get(path);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return def;
    }

    @SuppressWarnings("unchecked")
    public List<String> getStringList(String path) {
        Object v = get(path);
        if (v instanceof List<?> l) {
            List<String> out = new ArrayList<>();
            for (Object o : l) out.add(String.valueOf(o));
            return out;
        }
        return Collections.emptyList();
    }

    /**
     * Lấy Max_Notifications từ config:
     *   - Trả về -1 nếu = false (vô giới hạn)
     *   - Trả về N nếu = N (giới hạn N/phút)
     *   - Trả về 20 (default) nếu không hợp lệ
     */
    public int getMaxNotificationsPerMinute() {
        Object v = get("Max_Notifications");
        if (v instanceof Boolean b && !b) return -1; // false = unlimited
        if (v instanceof Number n) return Math.max(1, n.intValue());
        try { return Math.max(1, Integer.parseInt(String.valueOf(v))); }
        catch (Exception e) { return 20; }
    }

    public boolean isConfigurationSection(String path) { return get(path) instanceof Map; }

    @SuppressWarnings("unchecked")
    public Set<String> getSectionKeys(String path) {
        Object v = get(path);
        if (v instanceof Map<?,?> m) return ((Map<String,Object>) m).keySet();
        return Collections.emptySet();
    }

    // ─── Setter ───────────────────────────────────────────────────────────────

    public synchronized void set(String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> node = data;
        for (int i = 0; i < parts.length - 1; i++) {
            Object child = node.get(parts[i]);
            if (!(child instanceof Map)) {
                Map<String, Object> newMap = new LinkedHashMap<>();
                node.put(parts[i], newMap);
                node = newMap;
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> childMap = (Map<String, Object>) child;
                node = childMap;
            }
        }
        if (value == null) node.remove(parts[parts.length - 1]);
        else node.put(parts[parts.length - 1], value);
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    public Object get(String path) {
        String[] parts = path.split("\\.");
        Object node = data;
        for (String part : parts) {
            if (!(node instanceof Map)) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> nodeMap = (Map<String, Object>) node;
            node = nodeMap.get(part);
        }
        return node;
    }

    private Map<String, Object> deepNormalizeMap(Map<?,?> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?,?> e : m.entrySet()) {
            String key = String.valueOf(e.getKey());
            Object val = e.getValue();
            out.put(key, val instanceof Map<?,?> nested ? deepNormalizeMap(nested) : val);
        }
        return out;
    }

    // ─── Default config (TOÀN BỘ các key — dùng làm fallback khi không đọc được template) ───

    public static Map<String, Object> defaultConfig() {
        Map<String, Object> cfg = new LinkedHashMap<>();

        // ── Connection ──────────────────────────────────────────────────────
        cfg.put("server-id",           "");
        cfg.put("guild-id",            "");
        cfg.put("bot-url",             "");
        // v5.0.2 FIX: "bot-api-key" đã HARDCODE trong code (BotHttpClient) —
        // không còn là config tuỳ chỉnh nữa. Xoá khỏi defaultConfig() để autoMergeAndMigrate()
        // không tự thêm lại cho những ai upgrade từ bản cũ có key này.
        cfg.put("plugin-callback-url", HARDCODED_CALLBACK_URL);
        cfg.put("plugin-port",         25580);

        // ── Nội dung chuyển khoản — v5.0.2 FIX ────────────────────────────
        // prefix: PB→NT, code-mode: -1→0 (xem CHANGELOG / TransferContentGenerator)
        Map<String, Object> transferContent = new LinkedHashMap<>();
        transferContent.put("prefix",             "NT");
        transferContent.put("require-underscore", false);
        transferContent.put("code-length",        12);
        transferContent.put("code-mode",          0);
        cfg.put("transfer-content", transferContent);

        // ── Lưu trữ đơn nạp cũ ─────────────────────────────────────────────
        Map<String, Object> orderRetention = new LinkedHashMap<>();
        orderRetention.put("bank-days", -1);
        orderRetention.put("card-days", -1);
        cfg.put("order-retention", orderRetention);

        // ── Reward effects ───────────────────────────────────────────────────
        cfg.put("Reward_Firework",     true);
        cfg.put("Reward_Sound",        true);
        cfg.put("Reward_Notification", true);

        // ── Reward multiplier ────────────────────────────────────────────────
        cfg.put("Rewards_Card_Mode", 1);
        cfg.put("Rewards_Bank_Mode", 1);

        // ── SePay — legacy (backward-compat) ────────────────────────────────
        Map<String, Object> sepay = new LinkedHashMap<>();
        sepay.put("secret-key",   ""); sepay.put("merchant-id", "");
        sepay.put("bank-account", ""); sepay.put("bank-name",   "");
        sepay.put("account-name","");  sepay.put("configured",  false);
        cfg.put("sepay", sepay);

        // ── Card API ─────────────────────────────────────────────────────────
        Map<String, Object> cardApi = new LinkedHashMap<>();
        cardApi.put("site",""); cardApi.put("partner-id",""); cardApi.put("partner-key",""); cardApi.put("configured",false);
        cfg.put("card-api", cardApi);

        // ── Card API Sites ────────────────────────────────────────────────────
        Map<String, Object> cardApiSites = new LinkedHashMap<>();
        for (String site : new String[]{"thesieure.com","gachthepro.com","gachthefast.com","gachthe1s.com"}) {
            Map<String, Object> siteMap = new LinkedHashMap<>();
            siteMap.put("partner-id", ""); siteMap.put("partner-key", ""); siteMap.put("channel-id", "");
            String[] parts = site.split("\\.", 2);
            @SuppressWarnings("unchecked")
            Map<String, Object> domainMap = (Map<String, Object>) cardApiSites.computeIfAbsent(parts[0], k -> new LinkedHashMap<>());
            domainMap.put(parts.length > 1 ? parts[1] : "com", siteMap);
        }
        cfg.put("card-api-sites", cardApiSites);

        // ── Denom rewards ────────────────────────────────────────────────────
        int[] cardDenoms = {10000,20000,30000,50000,100000,200000,300000,500000,1000000};
        int[] bankDenoms = {10000,20000,50000,100000,200000,500000,1000000};
        Map<String, Object> denomCard = new LinkedHashMap<>();
        Map<String, Object> denomBank = new LinkedHashMap<>();
        for (int d : cardDenoms) { Map<String,Object> e = new LinkedHashMap<>(); e.put("amt",""); e.put("cmd",""); denomCard.put(String.valueOf(d), e); }
        for (int d : bankDenoms) { Map<String,Object> e = new LinkedHashMap<>(); e.put("amt",""); e.put("cmd",""); denomBank.put(String.valueOf(d), e); }
        cfg.put("denom-rewards-card", denomCard);
        cfg.put("denom-rewards-bank", denomBank);

        // ── Quick amounts ────────────────────────────────────────────────────
        cfg.put("quick-amounts", List.of(10000, 20000, 50000, 100000, 200000, 500000, 1000000));

        // ── Fallback reward commands ─────────────────────────────────────────
        cfg.put("reward-command",      "");
        cfg.put("reward-command-card", "");
        cfg.put("reward-command-bank", "");

        // ── Max_Notifications + notification flags ───────────────────────────
        cfg.put("Max_Notifications", 20);
        Map<String, Object> notif = new LinkedHashMap<>();
        notif.put("qr-created", true); notif.put("qr-create-fail", true);
        notif.put("card-submitted", true); notif.put("card-submit-fail", true);
        notif.put("card-api-error", true); notif.put("sepay-error", true);
        notif.put("bank-payment-received", true); notif.put("approve-fail", true);
        notif.put("reward-invalid", true); notif.put("reward-dispatch", true);
        notif.put("reward-queued-offline", true); notif.put("firework-fail", true);
        notif.put("order-expired", true); notif.put("bot-disconnect", true);
        notif.put("http-error", true);
        cfg.put("notifications", notif);

        // ── SePay API (self-poll — không cần webhook) ── v5.0.2 FULL ─────────
        Map<String, Object> sepayApi = new LinkedHashMap<>();
        sepayApi.put("api-token",             "");
        sepayApi.put("bank-account-number",   "");
        sepayApi.put("bank-short-name",       "");
        sepayApi.put("bank-full-name",        "");
        sepayApi.put("bank-bin",              "");
        sepayApi.put("account-holder-name",   "");
        sepayApi.put("last-transaction-id",   0L);
        sepayApi.put("poll-interval-seconds", 10);
        cfg.put("sepay-api", sepayApi);

        return cfg;
    }

    // ─── Template-based comment-preserving merge ──────────────────────────────

    /**
     * v5.0.2 — Đọc config-template.yml từ JAR resources, tìm top-level key còn thiếu
     * trong file config.yml thực tế của admin, append nguyên khối text (kèm comment)
     * vào cuối file. Tương đương SmartConfigMerger bên plugin.
     *
     * Chỉ xét top-level key (indent 0). Sub-key của section đã có được merge bằng
     * mergeMissingDefaults() bình thường (không cần comment riêng cho sub-key).
     *
     * @return true nếu có thay đổi (đã append ít nhất 1 block)
     */
    /**
     * v5.0.5 [Part 18]: Chạy CẢ add lẫn remove trong 1 lần — dùng cho watcher 10s
     * (PayBotMod đăng ký scheduler.scheduleAtFixedRate) để tự đồng bộ config.yml
     * định kỳ, không chỉ lúc khởi động. Trả int[]{added, removed}.
     *
     * Remove: block nào trong file live có key KHÔNG còn tồn tại trong template hiện
     * tại (config-template.yml đóng gói sẵn trong jar bản NÀY) → coi là key thừa từ
     * bản cũ, không còn tác dụng gì với mod nữa → xoá cả key lẫn comment đi kèm. Log
     * rõ nội dung trước khi xoá để admin xem lại log nếu cần khôi phục tay. KHÔNG
     * đụng key nào còn trong template — an toàn cho mọi giá trị admin tự chỉnh.
     */
    public synchronized int[] sync() {
        try (InputStream tplStream = PayBotConfig.class.getResourceAsStream("/config-template.yml")) {
            if (tplStream == null || !Files.exists(configPath)) return new int[]{0, 0};

            List<List<String>> tplBlocks = new ArrayList<>();
            Set<String> tplKeys = new LinkedHashSet<>();
            parseIntoBlocks(new java.io.BufferedReader(
                    new java.io.InputStreamReader(tplStream, StandardCharsets.UTF_8)), tplBlocks, tplKeys);

            List<List<String>> liveBlocks = new ArrayList<>();
            Set<String> liveKeys = new LinkedHashSet<>();
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(Files.newInputStream(configPath), StandardCharsets.UTF_8))) {
                parseIntoBlocks(br, liveBlocks, liveKeys);
            }

            // ── REMOVE: block live có key không còn trong template ─────────────
            int removed = 0;
            List<String> keptLines = new ArrayList<>();
            for (List<String> block : liveBlocks) {
                String key = keyOf(block);
                if (key != null && !tplKeys.contains(key)) {
                    PayBotMod.LOGGER.info("[PayBot] SmartConfigMerge: xoá key thừa '" + key
                            + "' (không còn trong template bản hiện tại): "
                            + String.join(" | ", block).trim());
                    removed++;
                    continue;
                }
                keptLines.addAll(block);
            }

            // ── ADD: template có key mà live thiếu ──────────────────────────────
            int added = 0;
            List<String> toAppend = new ArrayList<>();
            for (List<String> block : tplBlocks) {
                String key = keyOf(block);
                if (key == null || liveKeys.contains(key)) continue;
                toAppend.add("");
                toAppend.addAll(block);
                added++;
                PayBotMod.LOGGER.info("[PayBot] SmartConfigMerge: thêm key mới '" + key
                        + "' (kèm comment) vào config.yml.");
            }

            if (added > 0 || removed > 0) {
                keptLines.addAll(toAppend);
                try (java.io.PrintWriter pw = new java.io.PrintWriter(
                        new java.io.FileWriter(configPath.toFile(), false))) {
                    for (String line : keptLines) pw.println(line);
                }
                load(); // reload lại in-memory sau khi ghi file
            }
            return new int[]{added, removed};
        } catch (Exception e) {
            PayBotMod.LOGGER.warn("[PayBot] PayBotConfig.sync lỗi: " + e.getMessage());
            return new int[]{0, 0};
        }
    }

    /** Helper dùng chung bởi sync(): đọc 1 nguồn (template hoặc live) thành list block. */
    private static void parseIntoBlocks(java.io.BufferedReader br, List<List<String>> blocks,
                                         Set<String> keys) throws IOException {
        List<String> pending  = new ArrayList<>();
        List<String> curBlock = null;
        String line;
        while ((line = br.readLine()) != null) {
            boolean blank    = line.isBlank();
            boolean comment  = line.startsWith("#");
            boolean indented = !blank && !comment && line.startsWith(" ");
            boolean isTopKey = !blank && !comment && !indented && !line.trim().startsWith("-") && line.contains(":");

            if (isTopKey) {
                curBlock = new ArrayList<>(pending);
                curBlock.add(line);
                pending.clear();
                blocks.add(curBlock);
                String key = keyOf(curBlock);
                if (key != null) keys.add(key);
            } else if (indented && curBlock != null) {
                curBlock.add(line);
            } else {
                pending.add(line);
                if (curBlock != null && !indented) curBlock = null;
            }
        }
    }

    /** Lấy top-level key của 1 block (dòng đầu tiên không phải comment/blank). */
    private static String keyOf(List<String> block) {
        for (String l : block) {
            if (l.isBlank() || l.startsWith("#")) continue;
            int colon = l.indexOf(':');
            return colon > 0 ? l.substring(0, colon).trim() : l.trim();
        }
        return null;
    }

    private boolean appendMissingBlocksFromTemplate() {
        try (InputStream tplStream = PayBotConfig.class.getResourceAsStream("/config-template.yml")) {
            if (tplStream == null) return false;

            // ── Đọc tất cả dòng của template ─────────────────────────────────
            List<String> tplLines;
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(tplStream, StandardCharsets.UTF_8))) {
                tplLines = new ArrayList<>();
                String line;
                while ((line = br.readLine()) != null) tplLines.add(line);
            }

            // ── Lấy set top-level key đang có trong file live ─────────────────
            Set<String> existingKeys = new LinkedHashSet<>();
            if (Files.exists(configPath)) {
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(Files.newInputStream(configPath), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.isBlank() || line.startsWith("#") || line.startsWith(" ") || line.trim().startsWith("-")) continue;
                        int colon = line.indexOf(':');
                        if (colon > 0) existingKeys.add(line.substring(0, colon).trim());
                    }
                }
            }

            // ── Parse template thành blocks ────────────────────────────────────
            // Block = [pending comment/blank lines] + [top-level key line] + [indented child lines]
            List<List<String>> missingBlocks = new ArrayList<>();
            List<String> pending  = new ArrayList<>();
            List<String> curBlock = null;
            String       curKey   = null;

            for (String line : tplLines) {
                boolean blank     = line.isBlank();
                boolean comment   = line.startsWith("#");
                boolean indented  = !blank && !comment && line.startsWith(" ");
                boolean isTopKey  = !blank && !comment && !indented && !line.trim().startsWith("-") && line.contains(":");

                if (isTopKey) {
                    // Kết thúc block cũ (nếu có) và bắt đầu block mới
                    int colon = line.indexOf(':');
                    curKey   = colon > 0 ? line.substring(0, colon).trim() : line.trim();
                    curBlock = new ArrayList<>(pending);
                    curBlock.add(line);
                    pending.clear();
                    if (!existingKeys.contains(curKey)) {
                        missingBlocks.add(curBlock); // sẽ append nếu key thiếu
                        existingKeys.add(curKey);    // tránh dup
                    } else {
                        curBlock = null; // key đã có → bỏ qua block này
                    }
                } else if (indented && curBlock != null) {
                    curBlock.add(line);
                } else {
                    // blank hoặc comment → pending cho block tiếp theo
                    pending.add(line);
                    if (curBlock != null && !indented) curBlock = null; // kết thúc block cũ
                }
            }

            if (missingBlocks.isEmpty()) return false;

            // ── Append vào cuối file ─────────────────────────────────────────
            try (java.io.PrintWriter pw = new java.io.PrintWriter(
                    new java.io.FileWriter(configPath.toFile(), true))) {
                pw.println(); // đảm bảo xuống dòng sau nội dung cũ
                for (List<String> block : missingBlocks) {
                    for (String line : block) pw.println(line);
                    pw.println(); // blank line giữa các block
                    PayBotMod.LOGGER.info("[PayBot] SmartConfigMerge: thêm key mới '"
                            + (block.stream().filter(l -> !l.startsWith("#") && !l.isBlank()).findFirst().orElse("?")
                               .split(":")[0].trim())
                            + "' (kèm comment) vào config.yml.");
                }
            }
            return true;
        } catch (Exception e) {
            PayBotMod.LOGGER.warn("[PayBot] appendMissingBlocksFromTemplate lỗi: " + e.getMessage());
            return false;
        }
    }

}
