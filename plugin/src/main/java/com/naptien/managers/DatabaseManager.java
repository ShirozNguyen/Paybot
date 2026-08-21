package com.naptien.managers;

import com.naptien.NapTienPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

/**
 * DatabaseManager — v5.4.1
 * <p>
 * Quản lý SQLite database thay thế các file YAML (bank-orders.yml,
 * card-orders.yml, offline-rewards.yml). Lý do chuyển sang DB:
 * <ul>
 *   <li>YAML bị hỏng nếu server crash giữa lúc ghi (file truncated)</li>
 *   <li>Đọc/ghi YAML chậm khi số đơn lớn (parse lại toàn bộ mỗi lần)</li>
 *   <li>SQLite atomic transaction — an toàn dù server crash đột ngột</li>
 *   <li>MySQL atomic transaction — an toàn dù server crash đột ngột</li>
 *   <li>Dễ query, backup, inspect bằng các công cụ database</li>
 * </ul>
 * <p>
 * Database: MySQL
 * <p>
 * Migration: lần đầu khởi động sau upgrade, tự detect file YAML cũ còn
 * tồn tại và copy dữ liệu sang DB. YAML cũ được đổi tên (không xoá) để
 * admin có thể khôi phục tay nếu cần.
 */
public class DatabaseManager {

    // [Đặc tả bảo mật Phần I] Tên file chia sẻ cấu hình MySQL nội bộ cho PayBotPlusPlus —
    // đọc file theo đường dẫn cụ thể KHÔNG xác thực được người đọc (mọi plugin Bukkit chung
    // 1 JVM, không có sandbox) — chỉ nâng độ khó từ "vô tình đọc được" lên "phải chủ đích
    // viết code nhắm đúng file". Xem writeInternalShareFile() bên dưới + Phần II (giới hạn
    // thiệt hại nếu credential vẫn bị đọc bởi plugin không phải PayBotPlusPlus thật).
    private static final String INTERNAL_SHARE_FILE = ".internal-db-share.json";

    private final NapTienPlugin plugin;
    private Connection conn;        // Connection MySQL (chỉ dùng khi useMySQL=true)
    private Connection bankConn;   // Connection SQLite Bank (chỉ dùng khi useMySQL=false)
    private Connection cardConn;   // Connection SQLite Card (chỉ dùng khi useMySQL=false)
    private Connection rewardConn; // Connection SQLite Rewards (chỉ dùng khi useMySQL=false)
    private boolean useMySQL = false; // true = chỉ dùng MySQL, false = chỉ dùng SQLite

    // [Đặc tả bảo mật Phần I/II — phát hiện khi Shiroz cảnh báo] Host CẤU HÌNH (mysql.host)
    // có thể KHÔNG PHẢI host thực sự dùng để kết nối — tryConnectMySQLDirect() tự động thử
    // fallback qua IP gateway NAT hosting (172.18.0.1...) nếu host cấu hình bị chặn loopback,
    // nhưng TRƯỚC ĐÂY chỉ log, không lưu lại host nào thắng cuộc. Nếu ghi file chia sẻ bằng
    // host cấu hình (sai) thay vì host thật đã kết nối, PayBotPlusPlus sẽ không bao giờ kết
    // nối được dù PayBot đang chạy tốt. Lưu lại ở đây, dùng đúng field này khi ghi file chia
    // sẻ — để PayBotPlusPlus không cần tự lặp lại logic fallback riêng.
    private volatile String actualConnectedHost;

    public DatabaseManager(NapTienPlugin plugin) {
        this.plugin = plugin;
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Thử kết nối cơ sở dữ liệu MySQL dựa trên cấu hình mysql.yml với HARD TIMEOUT 20 GIÂY.
     */
    private synchronized boolean tryConnectMySQL() {
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        java.util.concurrent.Future<Boolean> future = executor.submit(this::tryConnectMySQLDirect);
        try {
            return future.get(20, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean tryConnectMySQLDirect() {
        try {
            if (conn != null && !conn.isClosed() && conn.isValid(2)) {
                return true;
            }
            String rawHost = sanitizeConfigValue(plugin.getConfig().getString("mysql.host", "localhost"));
            int port = plugin.getConfig().getInt("mysql.port", 3306);
            String db = sanitizeConfigValue(plugin.getConfig().getString("mysql.database", "paybot"));
            String user = sanitizeConfigValue(plugin.getConfig().getString("mysql.username", "root"));
            String pass = sanitizeConfigValue(plugin.getConfig().getString("mysql.password", ""));
            boolean useSSL = false;

            if (plugin.getConfig().contains("mysql.useSSL")) {
                useSSL = plugin.getConfig().getBoolean("mysql.useSSL", false);
            } else if (plugin.getConfig().contains("mysql.use-ssl")) {
                useSSL = plugin.getConfig().getBoolean("mysql.use-ssl", false);
            }

            String host = rawHost;
            if (host.contains(":")) {
                String[] parts = host.split(":");
                host = parts[0].trim();
                if (parts.length > 1) {
                    try {
                        port = Integer.parseInt(parts[1].trim());
                    } catch (Exception ignored) {}
                }
            }
            if (host.isEmpty()) {
                host = "localhost";
            }

            try {
                DriverManager.registerDriver(new com.mysql.cj.jdbc.Driver());
            } catch (Throwable ignored) {
                try {
                    Class.forName("com.mysql.cj.jdbc.Driver");
                } catch (Throwable ignored2) {}
            }

            Properties props = new Properties();
            props.setProperty("user", user);
            props.setProperty("password", pass);
            props.setProperty("sslMode", useSSL ? "REQUIRED" : "DISABLED");
            props.setProperty("allowPublicKeyRetrieval", "true");
            props.setProperty("characterEncoding", "utf8");
            props.setProperty("encoding", "UTF-8");
            props.setProperty("useUnicode", "true");
            props.setProperty("serverTimezone", "UTC");
            props.setProperty("connectTimeout", "4000");
            props.setProperty("socketTimeout", "15000");

            List<String> hostsToTry = new ArrayList<>();
            hostsToTry.add(host);
            for (String g : new String[]{"172.18.0.1", "172.17.0.1", "172.19.0.1", "127.0.0.1", "localhost"}) {
                if (!hostsToTry.contains(g)) {
                    hostsToTry.add(g);
                }
            }

            SQLException lastSqlException = null;
            Exception lastException = null;

            for (String tryHost : hostsToTry) {
                try {
                    String url = "jdbc:mysql://" + tryHost + ":" + port + "/" + db;
                    conn = DriverManager.getConnection(url, props);
                    if (conn != null && !conn.isClosed()) {
                        actualConnectedHost = tryHost;
                        if (!tryHost.equalsIgnoreCase(host)) {
                            plugin.getLogger().info("[PayBot] Host '" + host + "' bị chặn NAT loopback bởi hosting. Đã tự động kết nối qua IP gateway hosting: '" + tryHost + "'");
                        }
                        return true;
                    }
                } catch (SQLException e) {
                    lastSqlException = e;
                    if (e.getErrorCode() == 1045 || e.getErrorCode() == 1044 || e.getErrorCode() == 1049 || (e.getMessage() != null && e.getMessage().contains("Unknown database"))) {
                        plugin.getLogger().log(Level.SEVERE, "[PayBot] Lỗi kết nối MySQL (" + tryHost + ":" + port + "): " + e.getMessage());
                        return false;
                    }
                } catch (Exception e) {
                    lastException = e;
                }
            }

            if (lastSqlException != null) {
                plugin.getLogger().log(Level.SEVERE, "[PayBot] Lỗi kết nối MySQL (" + host + ":" + port + "): " + lastSqlException.getMessage());
            } else if (lastException != null) {
                plugin.getLogger().log(Level.SEVERE, "[PayBot] Lỗi không xác định khi kết nối MySQL: " + lastException.getMessage(), lastException);
            }
            return false;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[PayBot] Lỗi không xác định khi kết nối MySQL: " + e.getMessage(), e);
            return false;
        }
    }

    private String sanitizeConfigValue(String val) {
        if (val == null) return "";
        val = val.trim();
        while ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
            if (val.length() >= 2) {
                val = val.substring(1, val.length() - 1).trim();
            } else {
                break;
            }
        }
        return val;
    }

    /**
     * Khởi tạo database theo cấu hình database-type trong config.yml.
     * SQLite: chỉ mở SQLite, không đụng MySQL.
     * MySQL: chỉ kết nối MySQL (synchronous 20s), nếu fail → throw RuntimeException → plugin crash.
     */
    public synchronized void init() {
        String dbType = plugin.getConfig().getString("database-type", "SQLite").trim().toLowerCase();

        if ("mysql".equals(dbType)) {
            // ── Chế độ MySQL ───────────────────────────────────────────────
            useMySQL = true;
            plugin.getLogger().info("[PayBot] Chế độ database: MySQL. Đang kết nối...");
            boolean mysqlSuccess = tryConnectMySQL();
            if (!mysqlSuccess) {
                plugin.getLogger().severe("[PayBot] ═══════════════════════════════════════════════");
                plugin.getLogger().severe("[PayBot] LỖI NGHIÊM TRỌNG: Không thể kết nối MySQL!");
                plugin.getLogger().severe("[PayBot] Kiểm tra lại mục mysql trong file config.yml và thông tin kết nối.");
                plugin.getLogger().severe("[PayBot] Plugin sẽ TỰ TẮT ngay bây giờ.");
                plugin.getLogger().severe("[PayBot] ═══════════════════════════════════════════════");
                throw new RuntimeException("[PayBot] Không thể kết nối MySQL theo cấu hình database-type: mysql");
            }
            try {
                createMySQLTables();
                plugin.getLogger().info("[PayBot] Kết nối MySQL thành công! Plugin hoạt động ở chế độ MySQL.");
                runMigrations();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[PayBot] Lỗi khởi tạo bảng MySQL!", e);
                throw new RuntimeException("[PayBot] Lỗi khởi tạo bảng MySQL: " + e.getMessage());
            }
        } else {
            // ── Chế độ SQLite ──────────────────────────────────────────────
            if (!"sqlite".equals(dbType)) {
                plugin.getLogger().warning("[PayBot] Giá trị database-type không hợp lệ: \"" + dbType + "\". Tự động dùng SQLite.");
            }
            useMySQL = false;
            plugin.getLogger().info("[PayBot] Chế độ database: SQLite.");
            try {
                Class.forName("org.sqlite.JDBC");
                File cardDir = new File(plugin.getDataFolder(), "Card");
                File bankDir = new File(plugin.getDataFolder(), "Bank");
                if (!cardDir.exists()) cardDir.mkdirs();
                if (!bankDir.exists()) bankDir.mkdirs();

                File bankDbFile   = new File(bankDir, "bank_orders.db");
                File cardDbFile   = new File(cardDir, "card_orders.db");
                File rewardDbFile = new File(plugin.getDataFolder(), "offline_rewards.db");

                bankConn   = DriverManager.getConnection("jdbc:sqlite:" + bankDbFile.getAbsolutePath());
                cardConn   = DriverManager.getConnection("jdbc:sqlite:" + cardDbFile.getAbsolutePath());
                rewardConn = DriverManager.getConnection("jdbc:sqlite:" + rewardDbFile.getAbsolutePath());

                createSQLiteTables();
                plugin.getLogger().info("[PayBot] Kết nối SQLite thành công!");
                runMigrations();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "[PayBot] Lỗi nghiêm trọng khi khởi tạo SQLite! Dữ liệu sẽ không được lưu.", e);
            }
        }

        // [Đặc tả bảo mật Phần I] Ghi cấu hình chia sẻ nội bộ SAU cả 2 nhánh (MySQL/SQLite) —
        // chạy đúng 1 lần, cho cả 2 chế độ (SQLite ghi ra file với useMySQL:false, khiến
        // PayBotPlusPlus tự fallback SQLite riêng của nó — không lỗi, không crash).
        writeInternalShareFile();
    }

    /**
     * [Đặc tả bảo mật Phần I] Ghi cấu hình MySQL ra file nội bộ trong data folder của PayBot,
     * để PayBotPlusPlus đọc trực tiếp thay vì qua PlaceholderAPI %paybot_db_config% (kênh
     * broadcast công khai — bất kỳ plugin/lệnh /papi parse nào cũng đọc được nguyên văn mật
     * khẩu MySQL). Không throw, không chặn init() nếu ghi file thất bại.
     * <p>
     * QUAN TRỌNG: getDbConfigJson() chỉ được dùng NỘI BỘ ở đây — KHÔNG được expose qua bất kỳ
     * API public/broadcast nào khác (PlaceholderAPI, HTTP không xác thực...). Đừng nối lại
     * đường lộ cũ trong 1 phiên sửa code sau này.
     */
    private void writeInternalShareFile() {
        try {
            File shareFile = new File(plugin.getDataFolder(), INTERNAL_SHARE_FILE);
            String json = buildShareJsonForAddon();
            java.nio.file.Files.write(shareFile.toPath(), json.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Best-effort giới hạn quyền đọc/ghi chỉ cho owner OS — 1 số OS/filesystem không
            // hỗ trợ setReadable/setWritable theo owner, thất bại thì chỉ log cảnh báo mức
            // thấp, KHÔNG chặn luồng khởi động plugin (đây chỉ là lớp phòng thủ bổ sung, file
            // vẫn bị đọc được bởi plugin khác cùng JVM dù có giới hạn quyền OS hay không).
            boolean permOk = shareFile.setReadable(false, false) && shareFile.setReadable(true, true)
                    && shareFile.setWritable(false, false) && shareFile.setWritable(true, true);
            if (!permOk) {
                plugin.getLogger().info("[PayBot] Lưu ý: không giới hạn được quyền OS cho file "
                        + INTERNAL_SHARE_FILE + " (filesystem không hỗ trợ) — không ảnh hưởng hoạt động.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[PayBot] Lỗi ghi file chia sẻ nội bộ " + INTERNAL_SHARE_FILE
                    + " — PayBotPlusPlus (nếu có) sẽ không dùng được MySQL chung, tự fallback SQLite riêng.", e);
        }
    }

    /**
     * [Đặc tả bảo mật Phần II] Quyết định nội dung file chia sẻ: nếu đang MySQL, thử cấp/tái
     * dùng tài khoản MySQL RIÊNG cho PayBotPlusPlus (least-privilege, chỉ bảng của nó +
     * SELECT bank_orders/card_orders) và dùng credential ĐÃ SCOPED đó thay vì credential admin
     * gốc của PayBot.
     * <p>
     * QUYẾT ĐỊNH THIẾT KẾ (không có trong đặc tả gốc, tự quyết định theo đúng tinh thần
     * least-privilege của Phần II): nếu KHÔNG cấp được scoped user (thường do thiếu quyền
     * GRANT — xem provisionScopedUserForAddon), CHỦ ĐÍCH KHÔNG rơi về ghi credential admin gốc
     * vào file chia sẻ — sẽ phá vỡ toàn bộ mục tiêu của Phần II. Thay vào đó báo
     * useMySQL:false, khiến PayBotPlusPlus tạm fallback SQLite riêng (không lỗi, không crash,
     * đúng luồng fail-safe đã có ở Phần I) cho tới khi admin tự chạy script GRANT đã log.
     */
    private String buildShareJsonForAddon() {
        if (!useMySQL) {
            return getDbConfigJson(); // SQLite: useMySQL=false, không có credential MySQL nào để scope.
        }
        String[] scoped = provisionScopedUserForAddon("paybotpp_scoped", "paybotpp_");
        if (scoped != null) {
            org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
            // Dùng host THẬT đã kết nối thành công (có thể là IP gateway NAT fallback, khác
            // hẳn host cấu hình nếu host cấu hình bị hosting chặn loopback) — xem field
            // actualConnectedHost. Fallback về config chỉ để phòng hờ (không nên xảy ra vì
            // nhánh này chỉ chạy khi useMySQL=true, nghĩa là tryConnectMySQL() đã thành công
            // và phải set field này).
            String host = actualConnectedHost != null ? actualConnectedHost : cfg.getString("mysql.host", "127.0.0.1");
            int port = cfg.getInt("mysql.port", 3306);
            String db = cfg.getString("mysql.database", "paybot");
            boolean ssl = cfg.getBoolean("mysql.useSSL", false);
            return formatDbConfigJson(true, host, port, db, scoped[0], scoped[1], ssl);
        }
        plugin.getLogger().warning("[PayBot] Chưa cấp được tài khoản MySQL riêng cho addon — file chia sẻ "
                + "sẽ báo useMySQL:false để KHÔNG lộ credential admin gốc. PayBotPlusPlus (nếu có) sẽ tạm "
                + "dùng SQLite riêng cho tới khi admin chạy script GRANT ở log phía trên.");
        return "{\"useMySQL\":false}";
    }

    /**
     * [Đặc tả bảo mật Phần II] Cấp 1 tài khoản MySQL RIÊNG cho addon, chỉ quyền trên đúng các
     * bảng khớp {@code tablePrefix} + (nếu tablePrefix bắt đầu "paybotpp") quyền SELECT bổ
     * sung có chủ đích trên bank_orders/card_orders (phục vụ Phần III). KHÔNG dùng ALL
     * PRIVILEGES. Idempotent — an toàn gọi lại mỗi lần khởi động (addon thêm bảng mới ở
     * version sau, lần khởi động kế tiếp của PayBot tự dò + cấp quyền theo).
     * <p>
     * Không dùng được wildcard trong GRANT khi chỉ định TÊN BẢNG cụ thể (chỉ hợp lệ ở cấp
     * database, {@code GRANT ... ON db.*}) — xác nhận qua tài liệu MySQL chính thức trước khi
     * thiết kế, không đoán — nên phải dò danh sách bảng thật (LIKE hợp lệ trong SELECT) rồi
     * GRANT từng bảng theo tên chính xác.
     * <p>
     * GIẢ ĐỊNH (chưa kiểm chứng được bằng MySQL thật trong sandbox): user MySQL riêng luôn
     * cấp {@code @'localhost'} — đúng khi addon và MySQL server chạy cùng 1 máy (đúng mô hình
     * "dùng chung DB" đang bàn). Nếu MySQL server là máy RIÊNG biệt với server Minecraft,
     * cần đổi thành đúng host/IP addon kết nối tới — không tự đoán giá trị đó ở đây.
     *
     * @param scopedUsername tên đầy đủ user MySQL sẽ tạo, vd "paybotpp_scoped"
     * @param tablePrefix    tiền tố bảng của addon để dò + cấp quyền, vd "paybotpp_"
     * @return {username, password} nếu cấp/tái dùng thành công, {@code null} nếu không đủ
     *         quyền GRANT hoặc có lỗi (đã log chi tiết trong cả 2 trường hợp)
     */
    private String[] provisionScopedUserForAddon(String scopedUsername, String tablePrefix) {
        if (conn == null) return null;
        // Sinh/tái dùng password TRƯỚC nhánh rẽ GRANT OPTION — để script log cho admin tự chạy
        // (nhánh thiếu quyền) và password thực thi tự động (nhánh đủ quyền) LUÔN khớp nhau;
        // nếu không, admin chạy script cũ rồi PayBot sau này đủ quyền lại sinh password khác,
        // gây lệch password giữa MySQL thật và file chia sẻ.
        String password = loadOrGenerateScopedPassword(scopedUsername);
        String db = plugin.getConfig().getString("mysql.database", "paybot");

        try {
            if (!currentUserHasGrantOption()) {
                plugin.getLogger().warning("[PayBot] Tài khoản MySQL hiện tại của PayBot không có quyền "
                        + "GRANT OPTION — không tự cấp được tài khoản riêng cho addon. Admin cần tự chạy "
                        + "đoạn SQL sau 1 lần bằng tài khoản có quyền GRANT (vd root):");
                for (String line : buildManualGrantScript(scopedUsername, tablePrefix, password, db)) {
                    plugin.getLogger().warning("[PayBot]   " + line);
                }
                return null;
            }

            try (Statement st = conn.createStatement()) {
                st.execute("CREATE USER IF NOT EXISTS '" + scopedUsername + "'@'localhost' IDENTIFIED BY '" + password + "'");
                st.execute("GRANT CREATE ON `" + db + "`.* TO '" + scopedUsername + "'@'localhost'");
            }

            List<String> tables = findTablesByPrefix(tablePrefix);
            for (String table : tables) {
                grantOnTable(scopedUsername, db, table, "SELECT, INSERT, UPDATE, DELETE, ALTER, INDEX");
            }

            // Ngoại lệ duy nhất, có chủ đích, KHÔNG nằm trong vòng lặp dò-theo-tiền-tố ở trên —
            // chỉ đọc, phục vụ Phần III (xác minh mốc nạp trực tiếp thay vì qua PlaceholderAPI).
            if (tablePrefix.startsWith("paybotpp")) {
                grantOnTable(scopedUsername, db, "bank_orders", "SELECT");
                grantOnTable(scopedUsername, db, "card_orders", "SELECT");
            }

            try (Statement st = conn.createStatement()) {
                st.execute("FLUSH PRIVILEGES");
            }

            plugin.getLogger().info("[PayBot] Đã cấp/cập nhật quyền MySQL cho tài khoản riêng \"" + scopedUsername
                    + "\" (" + tables.size() + " bảng khớp tiền tố \"" + tablePrefix + "\""
                    + (tablePrefix.startsWith("paybotpp") ? " + SELECT trên bank_orders/card_orders" : "") + ").");
            return new String[]{scopedUsername, password};

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[PayBot] Lỗi cấp quyền MySQL cho tài khoản riêng \""
                    + scopedUsername + "\" — addon liên quan sẽ không dùng được MySQL chung cho tới khi sửa.", e);
            return null;
        }
    }

    /** true nếu tài khoản MySQL admin hiện tại của PayBot có quyền GRANT OPTION. */
    private boolean currentUserHasGrantOption() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW GRANTS FOR CURRENT_USER()")) {
            while (rs.next()) {
                String grant = rs.getString(1);
                if (grant != null && grant.toUpperCase(Locale.ROOT).contains("GRANT OPTION")) return true;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[PayBot] Không kiểm tra được quyền GRANT OPTION của tài khoản MySQL hiện tại.", e);
        }
        return false;
    }

    /**
     * Dò danh sách bảng khớp tiền tố qua information_schema (wildcard hợp lệ trong SELECT,
     * KHÔNG hợp lệ trong GRANT theo tên bảng cụ thể — xem Javadoc provisionScopedUserForAddon).
     * Escape "_" (wildcard 1 ký tự trong LIKE) để không khớp nhầm bảng khác có "_" đúng vị trí.
     */
    private List<String> findTablesByPrefix(String tablePrefix) throws SQLException {
        List<String> result = new ArrayList<>();
        String sql = "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tablePrefix.replace("_", "\\_") + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    // Defense-in-depth trước khi nối trực tiếp vào câu GRANT bên dưới (không có
                    // cách tham số hoá tên định danh trong JDBC) — dù tên bảng đến từ chính
                    // information_schema (không phải input bên ngoài), vẫn chặn nếu có ký tự lạ.
                    if (name != null && name.matches("[A-Za-z0-9_]+")) result.add(name);
                }
            }
        }
        return result;
    }

    private void grantOnTable(String username, String db, String table, String privileges) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("GRANT " + privileges + " ON `" + db + "`.`" + table + "` TO '" + username + "'@'localhost'");
        }
    }

    /**
     * Password sinh ngẫu nhiên CHỈ 1 LẦN, lưu ổn định qua các lần khởi động — nếu sinh lại mỗi
     * lần, PayBotPlusPlus đang cầm password cũ (đã lưu trong .internal-db-share.json từ lần
     * trước) sẽ mất kết nối. Chỉ chứa ký tự chữ+số (không dấu nháy/backslash) để không cần
     * escape khi nối vào câu SQL literal bên trên.
     */
    private String loadOrGenerateScopedPassword(String username) {
        File pwFile = new File(plugin.getDataFolder(), ".internal-scoped-pw-" + username + ".txt");
        try {
            if (pwFile.exists()) {
                String existing = new String(java.nio.file.Files.readAllBytes(pwFile.toPath()),
                        java.nio.charset.StandardCharsets.UTF_8).trim();
                if (!existing.isEmpty()) return existing;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[PayBot] Lỗi đọc password đã lưu cho tài khoản MySQL riêng \""
                    + username + "\" — sẽ sinh password mới.", e);
        }

        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        String fresh = sb.toString();

        try {
            java.nio.file.Files.write(pwFile.toPath(), fresh.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            boolean permOk = pwFile.setReadable(false, false) && pwFile.setReadable(true, true)
                    && pwFile.setWritable(false, false) && pwFile.setWritable(true, true);
            if (!permOk) {
                plugin.getLogger().info("[PayBot] Lưu ý: không giới hạn được quyền OS cho file password "
                        + pwFile.getName() + " (filesystem không hỗ trợ) — không ảnh hưởng hoạt động.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[PayBot] KHÔNG lưu được password tài khoản MySQL riêng \""
                    + username + "\" — sẽ sinh password MỚI mỗi lần khởi động, làm addon mất kết nối liên tục "
                    + "cho tới khi sửa được quyền ghi file trong data folder.", e);
        }
        return fresh;
    }

    /** Script SQL để admin tự chạy 1 lần bằng tài khoản có quyền GRANT, khi tài khoản admin
     *  hiện tại của PayBot không đủ quyền tự cấp (currentUserHasGrantOption() == false). */
    private List<String> buildManualGrantScript(String username, String tablePrefix, String password, String db) {
        List<String> lines = new ArrayList<>();
        lines.add("CREATE USER IF NOT EXISTS '" + username + "'@'localhost' IDENTIFIED BY '" + password + "';");
        lines.add("GRANT CREATE ON `" + db + "`.* TO '" + username + "'@'localhost';");
        lines.add("-- Dò bảng khớp tiền tố \"" + tablePrefix + "\", rồi GRANT từng bảng tìm được (không dùng được wildcard trong GRANT theo tên bảng):");
        lines.add("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = '" + db
                + "' AND TABLE_NAME LIKE '" + tablePrefix.replace("_", "\\_") + "%';");
        lines.add("-- Với MỖI bảng tìm được ở trên:");
        lines.add("--   GRANT SELECT, INSERT, UPDATE, DELETE, ALTER, INDEX ON `" + db + "`.`<tên_bảng>` TO '" + username + "'@'localhost';");
        if (tablePrefix.startsWith("paybotpp")) {
            lines.add("GRANT SELECT ON `" + db + "`.`bank_orders` TO '" + username + "'@'localhost';");
            lines.add("GRANT SELECT ON `" + db + "`.`card_orders` TO '" + username + "'@'localhost';");
        }
        lines.add("FLUSH PRIVILEGES;");
        return lines;
    }

    /** Đóng connection khi plugin disable. */
    public synchronized void close() {
        if (conn != null)       { try { conn.close();       } catch (SQLException ignored) {} conn       = null; }
        if (bankConn != null)   { try { bankConn.close();   } catch (SQLException ignored) {} bankConn   = null; }
        if (cardConn != null)   { try { cardConn.close();   } catch (SQLException ignored) {} cardConn   = null; }
        if (rewardConn != null) { try { rewardConn.close(); } catch (SQLException ignored) {} rewardConn = null; }
    }

    // ─── Schema ───────────────────────────────────────────────────────────────

    private void createSQLiteTables() throws SQLException {
        // Tạo bảng bank_orders trên bankConn
        try (Statement st = bankConn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS bank_orders (" +
                    "invoice_id VARCHAR(128) PRIMARY KEY, " +
                    "player_name VARCHAR(128) NOT NULL, " +
                    "amount INT NOT NULL, " +
                    "status VARCHAR(64) NOT NULL DEFAULT 'PENDING', " +
                    "created_at BIGINT NOT NULL, " +
                    "registered_with_bot INT NOT NULL DEFAULT 0" +
                    ")");
            try { st.execute("CREATE INDEX idx_bank_player ON bank_orders(player_name)"); } catch (SQLException ignored) {}
            try { st.execute("CREATE INDEX idx_bank_status ON bank_orders(status)"); } catch (SQLException ignored) {}
        }

        // Tạo bảng card_orders trên cardConn
        try (Statement st = cardConn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS card_orders (" +
                    "request_id VARCHAR(128) PRIMARY KEY, " +
                    "player_name VARCHAR(128) NOT NULL, " +
                    "telco VARCHAR(64) DEFAULT '', " +
                    "denom INT DEFAULT 0, " +
                    "card_code VARCHAR(128) DEFAULT '', " +
                    "card_serial VARCHAR(128) DEFAULT '', " +
                    "status VARCHAR(64) NOT NULL DEFAULT '99', " +
                    "message TEXT DEFAULT NULL, " +
                    "created_at BIGINT NOT NULL, " +
                    "submit_attempts INT DEFAULT 0, " +
                    "connection_error INT DEFAULT 0" +
                    ")");
            try { st.execute("CREATE INDEX idx_card_player ON card_orders(player_name)"); } catch (SQLException ignored) {}
            try { st.execute("CREATE INDEX idx_card_status ON card_orders(status)"); } catch (SQLException ignored) {}
        }

        // Tạo bảng offline_rewards trên rewardConn
        try (Statement st = rewardConn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS offline_rewards (" +
                    "reward_id VARCHAR(128) PRIMARY KEY, " +
                    "player_name VARCHAR(128) NOT NULL, " +
                    "raw_cmd TEXT DEFAULT NULL, " +
                    "reward_amount VARCHAR(128) DEFAULT '0', " +
                    "denom_vnd VARCHAR(128) DEFAULT '', " +
                    "type VARCHAR(64) DEFAULT 'card', " +
                    "invoice_id VARCHAR(128) DEFAULT '', " +
                    "discord_uid VARCHAR(128) DEFAULT '', " +
                    "created_at BIGINT NOT NULL" +
                    ")");
            try { st.execute("CREATE INDEX idx_reward_player ON offline_rewards(player_name)"); } catch (SQLException ignored) {}
        }
    }

    private void createMySQLTables() throws SQLException {
        if (conn == null) return;
        try (Statement st = conn.createStatement()) {
            String suffix = " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

            st.execute("CREATE TABLE IF NOT EXISTS bank_orders (" +
                    "invoice_id VARCHAR(128) PRIMARY KEY, " +
                    "player_name VARCHAR(128) NOT NULL, " +
                    "amount INT NOT NULL, " +
                    "status VARCHAR(64) NOT NULL DEFAULT 'PENDING', " +
                    "created_at BIGINT NOT NULL, " +
                    "registered_with_bot INT NOT NULL DEFAULT 0" +
                    ")" + suffix);

            st.execute("CREATE TABLE IF NOT EXISTS card_orders (" +
                    "request_id VARCHAR(128) PRIMARY KEY, " +
                    "player_name VARCHAR(128) NOT NULL, " +
                    "telco VARCHAR(64) DEFAULT '', " +
                    "denom INT DEFAULT 0, " +
                    "card_code VARCHAR(128) DEFAULT '', " +
                    "card_serial VARCHAR(128) DEFAULT '', " +
                    "status VARCHAR(64) NOT NULL DEFAULT '99', " +
                    "message TEXT DEFAULT NULL, " +
                    "created_at BIGINT NOT NULL, " +
                    "submit_attempts INT DEFAULT 0, " +
                    "connection_error INT DEFAULT 0" +
                    ")" + suffix);

            st.execute("CREATE TABLE IF NOT EXISTS offline_rewards (" +
                    "reward_id VARCHAR(128) PRIMARY KEY, " +
                    "player_name VARCHAR(128) NOT NULL, " +
                    "raw_cmd TEXT DEFAULT NULL, " +
                    "reward_amount VARCHAR(128) DEFAULT '0', " +
                    "denom_vnd VARCHAR(128) DEFAULT '', " +
                    "type VARCHAR(64) DEFAULT 'card', " +
                    "invoice_id VARCHAR(128) DEFAULT '', " +
                    "discord_uid VARCHAR(128) DEFAULT '', " +
                    "created_at BIGINT NOT NULL" +
                    ")" + suffix);

            try { st.execute("CREATE INDEX idx_bank_player ON bank_orders(player_name)"); } catch (SQLException ignored) {}
            try { st.execute("CREATE INDEX idx_bank_status ON bank_orders(status)"); } catch (SQLException ignored) {}
            try { st.execute("CREATE INDEX idx_card_player ON card_orders(player_name)"); } catch (SQLException ignored) {}
            try { st.execute("CREATE INDEX idx_card_status ON card_orders(status)"); } catch (SQLException ignored) {}
            try { st.execute("CREATE INDEX idx_reward_player ON offline_rewards(player_name)"); } catch (SQLException ignored) {}
        }
    }

    private void runMigrations() {
        if (!useMySQL) {
            migrateOldPayBotDb();
        }
        migrateBankOrders();
        migrateCardOrders();
        migrateOfflineRewards();
    }

    @SuppressWarnings("unchecked")
    private void migrateBankOrders() {
        File yaml = new File(plugin.getDataFolder(), "bank-orders.yml");
        if (!yaml.exists()) return;

        plugin.getLogger().info("[PayBot] Migration: đang chuyển bank-orders.yml sang database...");
        int count = 0;
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(yaml);
            if (cfg.isConfigurationSection("orders")) {
                for (String id : cfg.getConfigurationSection("orders").getKeys(false)) {
                    String path = "orders." + id;
                    String playerName = cfg.getString(path + ".playerName", "");
                    int amount       = cfg.getInt(path + ".amount", 0);
                    String status    = cfg.getString(path + ".status", "PENDING");
                    long createdAt   = cfg.getLong(path + ".createdAt", System.currentTimeMillis());
                    boolean regBot   = cfg.getBoolean(path + ".registeredWithBot", false);

                    upsertBankOrder(id, playerName, amount, status, createdAt, regBot ? 1 : 0);
                    count++;
                }
            }
            // Đổi tên file cũ (không xoá để admin có thể khôi phục)
            yaml.renameTo(new File(plugin.getDataFolder(), "bank-orders.yml.migrated"));
            plugin.getLogger().info("[PayBot] Migration bank-orders: " + count + " đơn đã chuyển sang DB.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[PayBot] Migration bank-orders lỗi (dữ liệu trong DB có thể chưa đầy đủ): " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void migrateCardOrders() {
        File yaml = new File(plugin.getDataFolder(), "card-orders.yml");
        if (!yaml.exists()) return;

        plugin.getLogger().info("[PayBot] Migration: đang chuyển card-orders.yml sang database...");
        int count = 0;
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(yaml);
            if (cfg.isConfigurationSection("orders")) {
                for (String id : cfg.getConfigurationSection("orders").getKeys(false)) {
                    String path = "orders." + id;
                    String playerName = cfg.getString(path + ".playerName", "");
                    String telco     = cfg.getString(path + ".telco", "");
                    int denom        = cfg.getInt(path + ".denom", 0);
                    String cardCode  = cfg.getString(path + ".cardCode", "");
                    String cardSerial= cfg.getString(path + ".cardSerial", "");
                    String status    = cfg.getString(path + ".status", "99");
                    String message   = cfg.getString(path + ".message", "");
                    long createdAt   = cfg.getLong(path + ".createdAt", System.currentTimeMillis());
                    int attempts     = cfg.getInt(path + ".submitAttempts", 0);
                    boolean connErr  = cfg.getBoolean(path + ".connectionError", false);

                    upsertCardOrder(id, playerName, telco, denom, cardCode, cardSerial,
                            status, message, createdAt, attempts, connErr ? 1 : 0);
                    count++;
                }
            }
            yaml.renameTo(new File(plugin.getDataFolder(), "card-orders.yml.migrated"));
            plugin.getLogger().info("[PayBot] Migration card-orders: " + count + " đơn đã chuyển sang DB.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[PayBot] Migration card-orders lỗi: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void migrateOfflineRewards() {
        File yaml = new File(plugin.getDataFolder(), "offline-rewards.yml");
        if (!yaml.exists()) return;

        plugin.getLogger().info("[PayBot] Migration: đang chuyển offline-rewards.yml sang database...");
        int count = 0;
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(yaml);
            if (cfg.isConfigurationSection("players")) {
                for (String player : cfg.getConfigurationSection("players").getKeys(false)) {
                    List<?> rawList = cfg.getList("players." + player, Collections.emptyList());
                    for (Object rawItem : rawList) {
                        if (!(rawItem instanceof Map)) continue;
                        Map<?, ?> raw = (Map<?, ?>) rawItem;
                        String rewardId   = str(raw, "rewardId",     UUID.randomUUID().toString());
                        String playerName = str(raw, "playerName",   player);
                        String rawCmd     = str(raw, "rewardCmd",    "");
                        String rewardAmt  = str(raw, "rewardAmount", "0");
                        String denomVnd   = str(raw, "denom",        "");
                        String type       = str(raw, "type",         "card");
                        String invoiceId  = str(raw, "invoiceId",    "");
                        String discordUid = str(raw, "discordUid",   "");
                        long createdAt;
                        try { createdAt = Long.parseLong(str(raw, "createdAt", "0")); }
                        catch (NumberFormatException e2) { createdAt = System.currentTimeMillis(); }

                        insertOfflineReward(rewardId, playerName, rawCmd, rewardAmt,
                                denomVnd, type, invoiceId, discordUid, createdAt);
                        count++;
                    }
                }
            }
            yaml.renameTo(new File(plugin.getDataFolder(), "offline-rewards.yml.migrated"));
            plugin.getLogger().info("[PayBot] Migration offline-rewards: " + count + " reward đã chuyển sang DB.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[PayBot] Migration offline-rewards lỗi: " + e.getMessage(), e);
        }
    }

    private static String str(Map<?, ?> map, String key, String def) {
        Object v = map.get(key);
        return v != null ? String.valueOf(v) : def;
    }

    // ─── Bank Orders CRUD ─────────────────────────────────────────────────────

    /** Insert hoặc update bank order. */
    public synchronized void upsertBankOrder(String invoiceId, String playerName,
                                              int amount, String status,
                                              long createdAt, int registeredWithBot) {
        String sql = "REPLACE INTO bank_orders (invoice_id,player_name,amount,status,created_at,registered_with_bot) VALUES (?,?,?,?,?,?)";
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : bankConn;
        if (c == null) return;
        String tag = useMySQL ? "[MySQL]" : "[SQLite]";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, invoiceId);
            ps.setString(2, playerName);
            ps.setInt(3, amount);
            ps.setString(4, status);
            ps.setLong(5, createdAt);
            ps.setInt(6, registeredWithBot);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning(tag + " upsertBankOrder lỗi: " + e.getMessage());
        }
    }

    /** Cập nhật trạng thái bank order. */
    public synchronized void updateBankStatus(String invoiceId, String status) {
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : bankConn;
        if (c == null) return;
        String tag = useMySQL ? "[MySQL]" : "[SQLite]";
        try (PreparedStatement ps = c.prepareStatement("UPDATE bank_orders SET status=? WHERE invoice_id=?")) {
            ps.setString(1, status);
            ps.setString(2, invoiceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning(tag + " updateBankStatus lỗi: " + e.getMessage());
        }
    }

    /** Đánh dấu bank order đã register với bot (legacy support). */
    public synchronized void markBankRegistered(String invoiceId, boolean registered) {
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : bankConn;
        if (c == null) return;
        String tag = useMySQL ? "[MySQL]" : "[SQLite]";
        try (PreparedStatement ps = c.prepareStatement("UPDATE bank_orders SET registered_with_bot=? WHERE invoice_id=?")) {
            ps.setInt(1, registered ? 1 : 0);
            ps.setString(2, invoiceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning(tag + " markBankRegistered lỗi: " + e.getMessage());
        }
    }

    /** Lấy tất cả bank orders (dùng để khởi tạo in-memory cache). */
    public synchronized List<Map<String, Object>> getAllBankOrders() {
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : bankConn;
        List<Map<String, Object>> list = new ArrayList<>();
        if (c == null) return list;
        String tag = useMySQL ? "[MySQL]" : "[SQLite]";
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM bank_orders ORDER BY created_at ASC")) {
            return parseBankOrders(rs);
        } catch (SQLException e) {
            plugin.getLogger().warning(tag + " getAllBankOrders lỗi: " + e.getMessage());
        }
        return list;
    }

    /** Kiểm tra xem invoice_id đã từng tồn tại trong CSDL chưa (dùng cho chống trùng mã nạp). */
    public synchronized boolean hasBankOrder(String invoiceId) {
        if (invoiceId == null || invoiceId.isEmpty()) return false;
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : bankConn;
        if (c == null) return false;
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM bank_orders WHERE invoice_id=? LIMIT 1")) {
            ps.setString(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private List<Map<String, Object>> parseBankOrders(ResultSet rs) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("invoice_id",          rs.getString("invoice_id"));
            row.put("player_name",          rs.getString("player_name"));
            row.put("amount",               rs.getInt("amount"));
            row.put("status",               rs.getString("status"));
            row.put("created_at",           rs.getLong("created_at"));
            row.put("registered_with_bot",  rs.getInt("registered_with_bot") == 1);
            list.add(row);
        }
        return list;
    }

    // ─── Card Orders CRUD ─────────────────────────────────────────────────────

    /** Insert hoặc update card order. */
    public synchronized void upsertCardOrder(String requestId, String playerName,
                                              String telco, int denom,
                                              String cardCode, String cardSerial,
                                              String status, String message,
                                              long createdAt, int submitAttempts,
                                              int connectionError) {
        String sql = "REPLACE INTO card_orders (request_id,player_name,telco,denom,card_code,card_serial," +
                     "status,message,created_at,submit_attempts,connection_error) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : cardConn;
        if (c == null) return;
        String tag = useMySQL ? "[MySQL]" : "[SQLite]";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, requestId);
            ps.setString(2, playerName);
            ps.setString(3, telco);
            ps.setInt(4, denom);
            ps.setString(5, cardCode);
            ps.setString(6, cardSerial);
            ps.setString(7, status);
            ps.setString(8, message);
            ps.setLong(9, createdAt);
            ps.setInt(10, submitAttempts);
            ps.setInt(11, connectionError);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning(tag + " upsertCardOrder lỗi: " + e.getMessage());
        }
    }

    /** Cập nhật trạng thái + message của card order. */
    public synchronized void updateCardStatus(String requestId, String status, String message) {
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : cardConn;
        if (c == null) return;
        String tag = useMySQL ? "[MySQL]" : "[SQLite]";
        try (PreparedStatement ps = c.prepareStatement("UPDATE card_orders SET status=?, message=? WHERE request_id=?")) {
            ps.setString(1, status);
            ps.setString(2, message != null ? message : "");
            ps.setString(3, requestId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning(tag + " updateCardStatus lỗi: " + e.getMessage());
        }
    }

    /** Cập nhật connection error flag. */
    public synchronized void updateCardConnectionError(String requestId, boolean hasError) {
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : cardConn;
        if (c == null) return;
        String tag = useMySQL ? "[MySQL]" : "[SQLite]";
        try (PreparedStatement ps = c.prepareStatement("UPDATE card_orders SET connection_error=? WHERE request_id=?")) {
            ps.setInt(1, hasError ? 1 : 0);
            ps.setString(2, requestId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning(tag + " updateCardConnectionError lỗi: " + e.getMessage());
        }
    }

    /** Tăng submit_attempts cho card order. */
    public synchronized void incrementCardSubmitAttempts(String requestId) {
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : cardConn;
        if (c == null) return;
        String tag = useMySQL ? "[MySQL]" : "[SQLite]";
        try (PreparedStatement ps = c.prepareStatement("UPDATE card_orders SET submit_attempts=submit_attempts+1 WHERE request_id=?")) {
            ps.setString(1, requestId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning(tag + " incrementCardSubmitAttempts lỗi: " + e.getMessage());
        }
    }

    /** Lấy tất cả card orders. */
    public synchronized List<Map<String, Object>> getAllCardOrders() {
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : cardConn;
        List<Map<String, Object>> list = new ArrayList<>();
        if (c == null) return list;
        String tag = useMySQL ? "[MySQL]" : "[SQLite]";
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM card_orders ORDER BY created_at ASC")) {
            return parseCardOrders(rs);
        } catch (SQLException e) {
            plugin.getLogger().warning(tag + " getAllCardOrders lỗi: " + e.getMessage());
        }
        return list;
    }

    private List<Map<String, Object>> parseCardOrders(ResultSet rs) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("request_id",       rs.getString("request_id"));
            row.put("player_name",      rs.getString("player_name"));
            row.put("telco",            rs.getString("telco"));
            row.put("denom",            rs.getInt("denom"));
            row.put("card_code",        rs.getString("card_code"));
            row.put("card_serial",      rs.getString("card_serial"));
            row.put("status",           rs.getString("status"));
            row.put("message",          rs.getString("message"));
            row.put("created_at",       rs.getLong("created_at"));
            row.put("submit_attempts",  rs.getInt("submit_attempts"));
            row.put("connection_error", rs.getInt("connection_error") == 1);
            list.add(row);
        }
        return list;
    }

    /** Xoá card orders ở trạng thái cuối cũ hơn cutoff. */
    public synchronized int deleteCardOrdersBefore(long cutoffMs, List<String> terminalStatuses) {
        if (terminalStatuses.isEmpty()) return 0;
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : cardConn;
        if (c == null) return 0;
        String tag = useMySQL ? "[MySQL]" : "[SQLite]";
        String placeholders = String.join(",", Collections.nCopies(terminalStatuses.size(), "?"));
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM card_orders WHERE status IN (" + placeholders + ") AND created_at <= ?")) {
            for (int i = 0; i < terminalStatuses.size(); i++) ps.setString(i + 1, terminalStatuses.get(i));
            ps.setLong(terminalStatuses.size() + 1, cutoffMs);
            return ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning(tag + " deleteCardOrdersBefore lỗi: " + e.getMessage());
        }
        return 0;
    }

    // ─── Offline Rewards CRUD ─────────────────────────────────────────────────

    // ─── Offline Rewards CRUD ─────────────────────────────────────────────────

    /** Thêm offline reward mới. */
    public synchronized void insertOfflineReward(String rewardId, String playerName,
                                                  String rawCmd, String rewardAmount,
                                                  String denomVnd, String type,
                                                  String invoiceId, String discordUid,
                                                  long createdAt) {
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : rewardConn;
        if (c == null) return;
        String tag = useMySQL ? "[MySQL]" : "[SQLite]";
        String sql = useMySQL
            ? "INSERT IGNORE INTO offline_rewards (reward_id,player_name,raw_cmd,reward_amount,denom_vnd,type,invoice_id,discord_uid,created_at) VALUES (?,?,?,?,?,?,?,?,?)"
            : "INSERT OR IGNORE INTO offline_rewards (reward_id,player_name,raw_cmd,reward_amount,denom_vnd,type,invoice_id,discord_uid,created_at) VALUES (?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, rewardId);
            ps.setString(2, playerName.toLowerCase());
            ps.setString(3, rawCmd != null ? rawCmd : "");
            ps.setString(4, rewardAmount != null ? rewardAmount : "0");
            ps.setString(5, denomVnd != null ? denomVnd : "");
            ps.setString(6, type != null ? type : "card");
            ps.setString(7, invoiceId != null ? invoiceId : "");
            ps.setString(8, discordUid != null ? discordUid : "");
            ps.setLong(9, createdAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning(tag + " insertOfflineReward lỗi: " + e.getMessage());
        }
    }

    /** Lấy tất cả offline rewards của player. */
    public synchronized List<Map<String, String>> getOfflineRewardsForPlayer(String playerName) {
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : rewardConn;
        List<Map<String, String>> list = new ArrayList<>();
        if (c == null) return list;
        String tag = useMySQL ? "[MySQL]" : "[SQLite]";
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM offline_rewards WHERE player_name=? ORDER BY created_at ASC")) {
            ps.setString(1, playerName.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                return parseOfflineRewards(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning(tag + " getOfflineRewardsForPlayer lỗi: " + e.getMessage());
        }
        return list;
    }

    private List<Map<String, String>> parseOfflineRewards(ResultSet rs) throws SQLException {
        List<Map<String, String>> list = new ArrayList<>();
        while (rs.next()) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("rewardId",     rs.getString("reward_id"));
            row.put("playerName",   rs.getString("player_name"));
            row.put("rewardCmd",    rs.getString("raw_cmd"));
            row.put("rewardAmount", rs.getString("reward_amount"));
            row.put("denom",        rs.getString("denom_vnd"));
            row.put("type",         rs.getString("type"));
            row.put("invoiceId",    rs.getString("invoice_id"));
            row.put("discordUid",   rs.getString("discord_uid"));
            row.put("createdAt",    String.valueOf(rs.getLong("created_at")));
            list.add(row);
        }
        return list;
    }

    /** Xoá một offline reward theo rewardId. */
    public synchronized void deleteOfflineReward(String rewardId) {
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : rewardConn;
        if (c == null) return;
        String tag = useMySQL ? "[MySQL]" : "[SQLite]";
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM offline_rewards WHERE reward_id=?")) {
            ps.setString(1, rewardId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning(tag + " deleteOfflineReward lỗi: " + e.getMessage());
        }
    }

    /** Lấy set tên tất cả player có pending rewards. */
    public synchronized Set<String> getPlayersWithPendingRewards() {
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : rewardConn;
        Set<String> set = new HashSet<>();
        if (c == null) return set;
        String tag = useMySQL ? "[MySQL]" : "[SQLite]";
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT player_name FROM offline_rewards")) {
            while (rs.next()) set.add(rs.getString("player_name"));
        } catch (SQLException e) {
            plugin.getLogger().warning(tag + " getPlayersWithPendingRewards lỗi: " + e.getMessage());
        }
        return set;
    }

    /** Xoá offline rewards cũ hơn cutoff (TTL cleanup). */
    public synchronized int deleteExpiredOfflineRewards(long cutoffMs) {
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : rewardConn;
        if (c == null) return 0;
        String tag = useMySQL ? "[MySQL]" : "[SQLite]";
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM offline_rewards WHERE created_at > 0 AND created_at < ?")) {
            ps.setLong(1, cutoffMs);
            return ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning(tag + " deleteExpiredOfflineRewards lỗi: " + e.getMessage());
        }
        return 0;
    }

    /** Xoá bank orders ở trạng thái cuối (EXPIRED/APPROVED) cũ hơn cutoff. */
    public synchronized int deleteBankOrdersBefore(long cutoffMs, List<String> terminalStatuses) {
        if (terminalStatuses.isEmpty()) return 0;
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : bankConn;
        if (c == null) return 0;
        String tag = useMySQL ? "[MySQL]" : "[SQLite]";
        String placeholders = String.join(",", Collections.nCopies(terminalStatuses.size(), "?"));
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM bank_orders WHERE status IN (" + placeholders + ") AND created_at <= ?")) {
            for (int i = 0; i < terminalStatuses.size(); i++) ps.setString(i + 1, terminalStatuses.get(i));
            ps.setLong(terminalStatuses.size() + 1, cutoffMs);
            return ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning(tag + " deleteBankOrdersBefore lỗi: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Tự động chuyển đổi dữ liệu từ database paybot.db cũ sang 3 file SQLite database mới.
     */
    private void migrateOldPayBotDb() {
        File oldDbFile = new File(plugin.getDataFolder(), "paybot.db");
        if (!oldDbFile.exists()) return;

        plugin.getLogger().info("[PayBot] Phát hiện database paybot.db cũ. Bắt đầu migrate dữ liệu sang cấu trúc mới...");

        String oldUrl = "jdbc:sqlite:" + oldDbFile.getAbsolutePath();
        try (Connection oldConn = DriverManager.getConnection(oldUrl)) {
            // 1. Migrate bank_orders
            try (Statement st = oldConn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM bank_orders")) {
                int count = 0;
                while (rs.next()) {
                    upsertBankOrder(
                        rs.getString("invoice_id"),
                        rs.getString("player_name"),
                        rs.getInt("amount"),
                        rs.getString("status"),
                        rs.getLong("created_at"),
                        rs.getInt("registered_with_bot")
                    );
                    count++;
                }
                plugin.getLogger().info("[PayBot] Đã chuyển " + count + " đơn bank từ database cũ.");
            } catch (SQLException e) {
                plugin.getLogger().warning("[PayBot] Không thể đọc bank_orders từ database cũ: " + e.getMessage());
            }

            // 2. Migrate card_orders
            try (Statement st = oldConn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM card_orders")) {
                int count = 0;
                while (rs.next()) {
                    upsertCardOrder(
                        rs.getString("request_id"),
                        rs.getString("player_name"),
                        rs.getString("telco"),
                        rs.getInt("denom"),
                        rs.getString("card_code"),
                        rs.getString("card_serial"),
                        rs.getString("status"),
                        rs.getString("message"),
                        rs.getLong("created_at"),
                        rs.getInt("submit_attempts"),
                        rs.getInt("connection_error")
                    );
                    count++;
                }
                plugin.getLogger().info("[PayBot] Đã chuyển " + count + " đơn card từ database cũ.");
            } catch (SQLException e) {
                plugin.getLogger().warning("[PayBot] Không thể đọc card_orders từ database cũ: " + e.getMessage());
            }

            // 3. Migrate offline_rewards
            try (Statement st = oldConn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM offline_rewards")) {
                int count = 0;
                while (rs.next()) {
                    insertOfflineReward(
                        rs.getString("reward_id"),
                        rs.getString("player_name"),
                        rs.getString("raw_cmd"),
                        rs.getString("reward_amount"),
                        rs.getString("denom_vnd"),
                        rs.getString("type"),
                        rs.getString("invoice_id"),
                        rs.getString("discord_uid"),
                        rs.getLong("created_at")
                    );
                    count++;
                }
                plugin.getLogger().info("[PayBot] Đã chuyển " + count + " offline rewards từ database cũ.");
            } catch (SQLException e) {
                plugin.getLogger().warning("[PayBot] Không thể đọc offline_rewards từ database cũ: " + e.getMessage());
            }

            // Đóng connection cũ trước khi rename file
            oldConn.close();

            // Đổi tên file database cũ
            File migratedFile = new File(plugin.getDataFolder(), "paybot.db.migrated");
            if (oldDbFile.renameTo(migratedFile)) {
                plugin.getLogger().info("[PayBot] Đã đổi tên database cũ thành paybot.db.migrated.");
            } else {
                plugin.getLogger().warning("[PayBot] Không thể đổi tên file paybot.db cũ. Hãy xoá/đổi tên thủ công.");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[PayBot] Lỗi trong quá trình migrate database cũ: " + e.getMessage(), e);
        }
    }

    // syncMySQLAndSQLite() đã bị xóa — không còn chế độ lưu song song.

    private void syncBankOrders() throws SQLException {
        // Đọc SQLite
        Map<String, Map<String, Object>> sqliteBank = new HashMap<>();
        try (Statement st = bankConn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM bank_orders")) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("invoice_id", rs.getString("invoice_id"));
                row.put("player_name", rs.getString("player_name"));
                row.put("amount", rs.getInt("amount"));
                row.put("status", rs.getString("status"));
                row.put("created_at", rs.getLong("created_at"));
                row.put("registered_with_bot", rs.getInt("registered_with_bot"));
                sqliteBank.put(rs.getString("invoice_id"), row);
            }
        }

        // Đọc MySQL
        Map<String, Map<String, Object>> mysqlBank = new HashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM bank_orders")) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("invoice_id", rs.getString("invoice_id"));
                row.put("player_name", rs.getString("player_name"));
                row.put("amount", rs.getInt("amount"));
                row.put("status", rs.getString("status"));
                row.put("created_at", rs.getLong("created_at"));
                row.put("registered_with_bot", rs.getInt("registered_with_bot"));
                mysqlBank.put(rs.getString("invoice_id"), row);
            }
        }

        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(sqliteBank.keySet());
        allKeys.addAll(mysqlBank.keySet());

        int sqliteUpdated = 0;
        int mysqlUpdated = 0;

        for (String id : allKeys) {
            Map<String, Object> local = sqliteBank.get(id);
            Map<String, Object> remote = mysqlBank.get(id);

            if (local == null) {
                insertBankToSQLite(remote);
                sqliteUpdated++;
            } else if (remote == null) {
                insertBankToMySQL(local);
                mysqlUpdated++;
            } else {
                String localStatus = (String) local.get("status");
                String remoteStatus = (String) remote.get("status");
                if (!localStatus.equals(remoteStatus)) {
                    boolean localIsFinal = localStatus.equals("APPROVED") || localStatus.equals("PAID");
                    if (localIsFinal) {
                        insertBankToMySQL(local);
                        mysqlUpdated++;
                    } else {
                        insertBankToSQLite(remote);
                        sqliteUpdated++;
                    }
                }
            }
        }
        if (sqliteUpdated > 0 || mysqlUpdated > 0) {
            plugin.getLogger().info("[PayBot] Đồng bộ Bank: Cập nhật SQLite local: " + sqliteUpdated + " đơn, MySQL: " + mysqlUpdated + " đơn.");
        }
    }

    private void insertBankToSQLite(Map<String, Object> row) throws SQLException {
        try (PreparedStatement ps = bankConn.prepareStatement(
                "REPLACE INTO bank_orders (invoice_id,player_name,amount,status,created_at,registered_with_bot) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, (String) row.get("invoice_id"));
            ps.setString(2, (String) row.get("player_name"));
            ps.setInt(3, (int) row.get("amount"));
            ps.setString(4, (String) row.get("status"));
            ps.setLong(5, (long) row.get("created_at"));
            ps.setInt(6, (int) row.get("registered_with_bot"));
            ps.executeUpdate();
        }
    }

    private void insertBankToMySQL(Map<String, Object> row) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "REPLACE INTO bank_orders (invoice_id,player_name,amount,status,created_at,registered_with_bot) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, (String) row.get("invoice_id"));
            ps.setString(2, (String) row.get("player_name"));
            ps.setInt(3, (int) row.get("amount"));
            ps.setString(4, (String) row.get("status"));
            ps.setLong(5, (long) row.get("created_at"));
            ps.setInt(6, (int) row.get("registered_with_bot"));
            ps.executeUpdate();
        }
    }

    private void syncCardOrders() throws SQLException {
        // Đọc SQLite
        Map<String, Map<String, Object>> sqliteCard = new HashMap<>();
        try (Statement st = cardConn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM card_orders")) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("request_id", rs.getString("request_id"));
                row.put("player_name", rs.getString("player_name"));
                row.put("telco", rs.getString("telco"));
                row.put("denom", rs.getInt("denom"));
                row.put("card_code", rs.getString("card_code"));
                row.put("card_serial", rs.getString("card_serial"));
                row.put("status", rs.getString("status"));
                row.put("message", rs.getString("message"));
                row.put("created_at", rs.getLong("created_at"));
                row.put("submit_attempts", rs.getInt("submit_attempts"));
                row.put("connection_error", rs.getInt("connection_error"));
                sqliteCard.put(rs.getString("request_id"), row);
            }
        }

        // Đọc MySQL
        Map<String, Map<String, Object>> mysqlCard = new HashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM card_orders")) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("request_id", rs.getString("request_id"));
                row.put("player_name", rs.getString("player_name"));
                row.put("telco", rs.getString("telco"));
                row.put("denom", rs.getInt("denom"));
                row.put("card_code", rs.getString("card_code"));
                row.put("card_serial", rs.getString("card_serial"));
                row.put("status", rs.getString("status"));
                row.put("message", rs.getString("message"));
                row.put("created_at", rs.getLong("created_at"));
                row.put("submit_attempts", rs.getInt("submit_attempts"));
                row.put("connection_error", rs.getInt("connection_error"));
                mysqlCard.put(rs.getString("request_id"), row);
            }
        }

        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(sqliteCard.keySet());
        allKeys.addAll(mysqlCard.keySet());

        int sqliteUpdated = 0;
        int mysqlUpdated = 0;

        for (String id : allKeys) {
            Map<String, Object> local = sqliteCard.get(id);
            Map<String, Object> remote = mysqlCard.get(id);

            if (local == null) {
                insertCardToSQLite(remote);
                sqliteUpdated++;
            } else if (remote == null) {
                insertCardToMySQL(local);
                mysqlUpdated++;
            } else {
                String localStatus = (String) local.get("status");
                String remoteStatus = (String) remote.get("status");
                if (!localStatus.equals(remoteStatus)) {
                    boolean localIsFinal = localStatus.equals("APPROVED") || localStatus.equals("1") || localStatus.equals("2") || localStatus.equals("3") || localStatus.equals("4") || localStatus.equals("100");
                    if (localIsFinal) {
                        insertCardToMySQL(local);
                        mysqlUpdated++;
                    } else {
                        insertCardToSQLite(remote);
                        sqliteUpdated++;
                    }
                }
            }
        }
        if (sqliteUpdated > 0 || mysqlUpdated > 0) {
            plugin.getLogger().info("[PayBot] Đồng bộ Card: Cập nhật SQLite local: " + sqliteUpdated + " đơn, MySQL: " + mysqlUpdated + " đơn.");
        }
    }

    private void insertCardToSQLite(Map<String, Object> row) throws SQLException {
        try (PreparedStatement ps = cardConn.prepareStatement(
                "REPLACE INTO card_orders (request_id,player_name,telco,denom,card_code,card_serial,status,message,created_at,submit_attempts,connection_error) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, (String) row.get("request_id"));
            ps.setString(2, (String) row.get("player_name"));
            ps.setString(3, (String) row.get("telco"));
            ps.setInt(4, (int) row.get("denom"));
            ps.setString(5, (String) row.get("card_code"));
            ps.setString(6, (String) row.get("card_serial"));
            ps.setString(7, (String) row.get("status"));
            ps.setString(8, (String) row.get("message"));
            ps.setLong(9, (long) row.get("created_at"));
            ps.setInt(10, (int) row.get("submit_attempts"));
            ps.setInt(11, (int) row.get("connection_error"));
            ps.executeUpdate();
        }
    }

    private void insertCardToMySQL(Map<String, Object> row) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "REPLACE INTO card_orders (request_id,player_name,telco,denom,card_code,card_serial,status,message,created_at,submit_attempts,connection_error) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, (String) row.get("request_id"));
            ps.setString(2, (String) row.get("player_name"));
            ps.setString(3, (String) row.get("telco"));
            ps.setInt(4, (int) row.get("denom"));
            ps.setString(5, (String) row.get("card_code"));
            ps.setString(6, (String) row.get("card_serial"));
            ps.setString(7, (String) row.get("status"));
            ps.setString(8, (String) row.get("message"));
            ps.setLong(9, (long) row.get("created_at"));
            ps.setInt(10, (int) row.get("submit_attempts"));
            ps.setInt(11, (int) row.get("connection_error"));
            ps.executeUpdate();
        }
    }

    private void syncOfflineRewards() throws SQLException {
        // Đọc SQLite
        Map<String, Map<String, Object>> sqliteRewards = new HashMap<>();
        try (Statement st = rewardConn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM offline_rewards")) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("reward_id", rs.getString("reward_id"));
                row.put("player_name", rs.getString("player_name"));
                row.put("raw_cmd", rs.getString("raw_cmd"));
                row.put("reward_amount", rs.getString("reward_amount"));
                row.put("denom_vnd", rs.getString("denom_vnd"));
                row.put("type", rs.getString("type"));
                row.put("invoice_id", rs.getString("invoice_id"));
                row.put("discord_uid", rs.getString("discord_uid"));
                row.put("created_at", rs.getLong("created_at"));
                sqliteRewards.put(rs.getString("reward_id"), row);
            }
        }

        // Đọc MySQL
        Map<String, Map<String, Object>> mysqlRewards = new HashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM offline_rewards")) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("reward_id", rs.getString("reward_id"));
                row.put("player_name", rs.getString("player_name"));
                row.put("raw_cmd", rs.getString("raw_cmd"));
                row.put("reward_amount", rs.getString("reward_amount"));
                row.put("denom_vnd", rs.getString("denom_vnd"));
                row.put("type", rs.getString("type"));
                row.put("invoice_id", rs.getString("invoice_id"));
                row.put("discord_uid", rs.getString("discord_uid"));
                row.put("created_at", rs.getLong("created_at"));
                mysqlRewards.put(rs.getString("reward_id"), row);
            }
        }

        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(sqliteRewards.keySet());
        allKeys.addAll(mysqlRewards.keySet());

        int sqliteUpdated = 0;
        int mysqlUpdated = 0;

        for (String id : allKeys) {
            Map<String, Object> local = sqliteRewards.get(id);
            Map<String, Object> remote = mysqlRewards.get(id);

            if (local == null) {
                insertRewardToSQLite(remote);
                sqliteUpdated++;
            } else if (remote == null) {
                insertRewardToMySQL(local);
                mysqlUpdated++;
            }
        }
        if (sqliteUpdated > 0 || mysqlUpdated > 0) {
            plugin.getLogger().info("[PayBot] Đồng bộ Reward: Cập nhật SQLite local: " + sqliteUpdated + " rewards, MySQL: " + mysqlUpdated + " rewards.");
        }
    }

    private void insertRewardToSQLite(Map<String, Object> row) throws SQLException {
        try (PreparedStatement ps = rewardConn.prepareStatement(
                "INSERT OR IGNORE INTO offline_rewards (reward_id,player_name,raw_cmd,reward_amount,denom_vnd,type,invoice_id,discord_uid,created_at) VALUES (?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, (String) row.get("reward_id"));
            ps.setString(2, (String) row.get("player_name"));
            ps.setString(3, (String) row.get("raw_cmd"));
            ps.setString(4, (String) row.get("reward_amount"));
            ps.setString(5, (String) row.get("denom_vnd"));
            ps.setString(6, (String) row.get("type"));
            ps.setString(7, (String) row.get("invoice_id"));
            ps.setString(8, (String) row.get("discord_uid"));
            ps.setLong(9, (long) row.get("created_at"));
            ps.executeUpdate();
        }
    }

    private void insertRewardToMySQL(Map<String, Object> row) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT IGNORE INTO offline_rewards (reward_id,player_name,raw_cmd,reward_amount,denom_vnd,type,invoice_id,discord_uid,created_at) VALUES (?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, (String) row.get("reward_id"));
            ps.setString(2, (String) row.get("player_name"));
            ps.setString(3, (String) row.get("raw_cmd"));
            ps.setString(4, (String) row.get("reward_amount"));
            ps.setString(5, (String) row.get("denom_vnd"));
            ps.setString(6, (String) row.get("type"));
            ps.setString(7, (String) row.get("invoice_id"));
            ps.setString(8, (String) row.get("discord_uid"));
            ps.setLong(9, (long) row.get("created_at"));
            ps.executeUpdate();
        }
    }

    public String getDbStatus() {
        if (useMySQL && conn != null) {
            try {
                if (!conn.isClosed() && conn.isValid(2)) {
                    return "MySQL (Connected)";
                }
            } catch (Exception ignored) {}
            return "MySQL (Disconnected)";
        }
        return "SQLite (Local)";
    }

    public String getDbConfigJson() {
        try {
            org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
            boolean use = cfg.getBoolean("mysql.use", cfg.getBoolean("mysql.enable", false));
            // Ưu tiên host THẬT đã kết nối thành công (có thể là IP gateway NAT fallback) —
            // xem ghi chú đầy đủ ở field actualConnectedHost. Chỉ rơi về config nếu chưa từng
            // kết nối MySQL thành công (vd đang SQLite, hoặc MySQL lỗi hoàn toàn).
            String host = actualConnectedHost != null ? actualConnectedHost : cfg.getString("mysql.host", "127.0.0.1");
            int port = cfg.getInt("mysql.port", 3306);
            String db = cfg.getString("mysql.database", "paybot");
            String user = cfg.getString("mysql.username", "root");
            String pass = cfg.getString("mysql.password", "");
            boolean ssl = cfg.getBoolean("mysql.useSSL", false);
            return formatDbConfigJson(use, host, port, db, user, pass, ssl);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** Tách riêng phần format JSON để {@link #buildShareJsonForAddon()} (Phần II) tái dùng được
     *  với credential ĐÃ SCOPED thay vì credential admin gốc, không lặp lại chuỗi format. */
    private String formatDbConfigJson(boolean use, String host, int port, String db, String user, String pass, boolean ssl) {
        return String.format(Locale.ROOT,
            "{\"useMySQL\":%b,\"host\":\"%s\",\"port\":%d,\"database\":\"%s\",\"username\":\"%s\",\"password\":\"%s\",\"useSSL\":%b}",
            use, host, port, db, user, pass, ssl);
    }
}
