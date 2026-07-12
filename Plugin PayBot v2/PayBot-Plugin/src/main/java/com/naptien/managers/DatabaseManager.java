package com.naptien.managers;

import com.naptien.NapTienPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

/**
 * DatabaseManager — v5.1.0
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

    private final NapTienPlugin plugin;
    private Connection conn;
    private boolean isSQLite = false;

    public DatabaseManager(NapTienPlugin plugin) {
        this.plugin = plugin;
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Khởi tạo database: mở connection MySQL, tạo bảng nếu chưa có, chạy migration.
     */
    public synchronized void init() {
        try {
            File mysqlFile = new File(plugin.getDataFolder(), "mysql.yml");
            if (!mysqlFile.exists()) {
                plugin.saveResource("mysql.yml", false);
            }
            YamlConfiguration mysqlCfg = YamlConfiguration.loadConfiguration(mysqlFile);

            String host = mysqlCfg.getString("host", "localhost");
            int port = mysqlCfg.getInt("port", 3306);
            String db = mysqlCfg.getString("database", "paybot");
            String user = mysqlCfg.getString("username", "root");
            String pass = mysqlCfg.getString("password", "");
            boolean useSSL = mysqlCfg.getBoolean("use-ssl", false);

            try {
                // Thử kết nối MySQL
                Class.forName("com.mysql.cj.jdbc.Driver");
                String baseUrl = "jdbc:mysql://" + host + ":" + port + "/?useSSL=" + useSSL + "&allowPublicKeyRetrieval=true&autoReconnect=true&characterEncoding=UTF-8";
                try (Connection tempConn = DriverManager.getConnection(baseUrl, user, pass);
                     Statement st = tempConn.createStatement()) {
                    st.execute("CREATE DATABASE IF NOT EXISTS `" + db + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                }
                String url = "jdbc:mysql://" + host + ":" + port + "/" + db 
                           + "?useSSL=" + useSSL + "&allowPublicKeyRetrieval=true&autoReconnect=true&characterEncoding=UTF-8";
                conn = DriverManager.getConnection(url, user, pass);
                isSQLite = false;
                plugin.getLogger().info("[PayBot] Kết nối cơ sở dữ liệu MySQL thành công tới: " + host + ":" + port + "/" + db);
            } catch (Exception mysqlEx) {
                plugin.getLogger().warning("[PayBot] Không thể kết nối tới cơ sở dữ liệu MySQL (" + mysqlEx.getMessage() + "), tự động chuyển sang cơ chế dự phòng SQLite local...");
                // Fallback về SQLite
                Class.forName("org.sqlite.JDBC");
                File dbFile = new File(plugin.getDataFolder(), "paybot.db");
                if (!dbFile.getParentFile().exists()) {
                    dbFile.getParentFile().mkdirs();
                }
                String sqliteUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                conn = DriverManager.getConnection(sqliteUrl);
                isSQLite = true;
                plugin.getLogger().info("[PayBot] Khởi tạo cơ sở dữ liệu SQLite dự phòng thành công tại: " + dbFile.getName());
            }

            createTables();
            runMigrations();

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[PayBot] Không thể khởi tạo bất kỳ kết nối cơ sở dữ liệu nào (MySQL & SQLite fallback đều lỗi)! Dữ liệu sẽ không được lưu.", e);
        }
    }

    /** Đóng connection khi plugin disable. */
    public synchronized void close() {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
            conn = null;
        }
    }

    // ─── Schema ───────────────────────────────────────────────────────────────

    private void createTables() throws SQLException {
        try (Statement st = conn.createStatement()) {
            String suffix = isSQLite ? "" : " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

            // Bank orders (nạp ngân hàng)
            st.execute("CREATE TABLE IF NOT EXISTS bank_orders (" +
                    "invoice_id VARCHAR(128) PRIMARY KEY, " +
                    "player_name VARCHAR(128) NOT NULL, " +
                    "amount INT NOT NULL, " +
                    "status VARCHAR(64) NOT NULL DEFAULT 'PENDING', " +
                    "created_at BIGINT NOT NULL, " +
                    "registered_with_bot INT NOT NULL DEFAULT 0" +
                    ")" + suffix);

            // Card orders (nạp thẻ cào)
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

            // Offline rewards (phần thưởng chờ player online)
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

            // Indexes để tăng tốc query thường dùng (MySQL bắt ngoại lệ nếu đã tồn tại)
            try { st.execute("CREATE INDEX idx_bank_player ON bank_orders(player_name)"); } catch (SQLException ignored) {}
            try { st.execute("CREATE INDEX idx_bank_status ON bank_orders(status)"); } catch (SQLException ignored) {}
            try { st.execute("CREATE INDEX idx_card_player ON card_orders(player_name)"); } catch (SQLException ignored) {}
            try { st.execute("CREATE INDEX idx_card_status ON card_orders(status)"); } catch (SQLException ignored) {}
            try { st.execute("CREATE INDEX idx_reward_player ON offline_rewards(player_name)"); } catch (SQLException ignored) {}
        }
    }

    // ─── Migration từ YAML ────────────────────────────────────────────────────

    /**
     * Tự động detect và migrate dữ liệu từ YAML cũ.
     * Chỉ chạy nếu file YAML cũ còn tồn tại (chưa migrate).
     */
    private void runMigrations() {
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
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "REPLACE INTO bank_orders " +
                "(invoice_id,player_name,amount,status,created_at,registered_with_bot) " +
                "VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, invoiceId);
            ps.setString(2, playerName);
            ps.setInt(3, amount);
            ps.setString(4, status);
            ps.setLong(5, createdAt);
            ps.setInt(6, registeredWithBot);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[DB] upsertBankOrder lỗi: " + e.getMessage());
        }
    }

    /** Cập nhật trạng thái bank order. */
    public synchronized void updateBankStatus(String invoiceId, String status) {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE bank_orders SET status=? WHERE invoice_id=?")) {
            ps.setString(1, status);
            ps.setString(2, invoiceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[DB] updateBankStatus lỗi: " + e.getMessage());
        }
    }

    /** Đánh dấu bank order đã register với bot (legacy support). */
    public synchronized void markBankRegistered(String invoiceId, boolean registered) {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE bank_orders SET registered_with_bot=? WHERE invoice_id=?")) {
            ps.setInt(1, registered ? 1 : 0);
            ps.setString(2, invoiceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[DB] markBankRegistered lỗi: " + e.getMessage());
        }
    }

    /** Lấy tất cả bank orders (dùng để khởi tạo in-memory cache). */
    public synchronized List<Map<String, Object>> getAllBankOrders() {
        List<Map<String, Object>> list = new ArrayList<>();
        if (conn == null) return list;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM bank_orders ORDER BY created_at ASC")) {
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
        } catch (SQLException e) {
            plugin.getLogger().warning("[DB] getAllBankOrders lỗi: " + e.getMessage());
        }
        return list;
    }

    /** Xoá bank orders ở trạng thái cuối (EXPIRED/APPROVED) cũ hơn cutoff. */
    public synchronized int deleteBankOrdersBefore(long cutoffMs, List<String> terminalStatuses) {
        if (conn == null || terminalStatuses.isEmpty()) return 0;
        String placeholders = String.join(",", Collections.nCopies(terminalStatuses.size(), "?"));
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM bank_orders WHERE status IN (" + placeholders + ") AND created_at <= ?")) {
            for (int i = 0; i < terminalStatuses.size(); i++) ps.setString(i + 1, terminalStatuses.get(i));
            ps.setLong(terminalStatuses.size() + 1, cutoffMs);
            return ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[DB] deleteBankOrdersBefore lỗi: " + e.getMessage());
            return 0;
        }
    }

    // ─── Card Orders CRUD ─────────────────────────────────────────────────────

    /** Insert hoặc update card order. */
    public synchronized void upsertCardOrder(String requestId, String playerName,
                                              String telco, int denom,
                                              String cardCode, String cardSerial,
                                              String status, String message,
                                              long createdAt, int submitAttempts,
                                              int connectionError) {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "REPLACE INTO card_orders " +
                "(request_id,player_name,telco,denom,card_code,card_serial," +
                "status,message,created_at,submit_attempts,connection_error) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
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
            plugin.getLogger().warning("[DB] upsertCardOrder lỗi: " + e.getMessage());
        }
    }

    /** Cập nhật trạng thái + message của card order. */
    public synchronized void updateCardStatus(String requestId, String status, String message) {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE card_orders SET status=?, message=? WHERE request_id=?")) {
            ps.setString(1, status);
            ps.setString(2, message != null ? message : "");
            ps.setString(3, requestId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[DB] updateCardStatus lỗi: " + e.getMessage());
        }
    }

    /** Cập nhật connection error flag. */
    public synchronized void updateCardConnectionError(String requestId, boolean hasError) {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE card_orders SET connection_error=? WHERE request_id=?")) {
            ps.setInt(1, hasError ? 1 : 0);
            ps.setString(2, requestId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[DB] updateCardConnectionError lỗi: " + e.getMessage());
        }
    }

    /** Tăng submit_attempts cho card order. */
    public synchronized void incrementCardSubmitAttempts(String requestId) {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE card_orders SET submit_attempts=submit_attempts+1 WHERE request_id=?")) {
            ps.setString(1, requestId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[DB] incrementCardSubmitAttempts lỗi: " + e.getMessage());
        }
    }

    /** Lấy tất cả card orders. */
    public synchronized List<Map<String, Object>> getAllCardOrders() {
        List<Map<String, Object>> list = new ArrayList<>();
        if (conn == null) return list;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM card_orders ORDER BY created_at ASC")) {
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
        } catch (SQLException e) {
            plugin.getLogger().warning("[DB] getAllCardOrders lỗi: " + e.getMessage());
        }
        return list;
    }

    /** Xoá card orders ở trạng thái cuối cũ hơn cutoff. */
    public synchronized int deleteCardOrdersBefore(long cutoffMs, List<String> terminalStatuses) {
        if (conn == null || terminalStatuses.isEmpty()) return 0;
        String placeholders = String.join(",", Collections.nCopies(terminalStatuses.size(), "?"));
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM card_orders WHERE status IN (" + placeholders + ") AND created_at <= ?")) {
            for (int i = 0; i < terminalStatuses.size(); i++) ps.setString(i + 1, terminalStatuses.get(i));
            ps.setLong(terminalStatuses.size() + 1, cutoffMs);
            return ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[DB] deleteCardOrdersBefore lỗi: " + e.getMessage());
            return 0;
        }
    }

    // ─── Offline Rewards CRUD ─────────────────────────────────────────────────

    /** Thêm offline reward mới. */
    public synchronized void insertOfflineReward(String rewardId, String playerName,
                                                  String rawCmd, String rewardAmount,
                                                  String denomVnd, String type,
                                                  String invoiceId, String discordUid,
                                                  long createdAt) {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT IGNORE INTO offline_rewards " +
                "(reward_id,player_name,raw_cmd,reward_amount,denom_vnd,type,invoice_id,discord_uid,created_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?)")) {
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
            plugin.getLogger().warning("[DB] insertOfflineReward lỗi: " + e.getMessage());
        }
    }

    /** Lấy tất cả offline rewards của player. */
    public synchronized List<Map<String, String>> getOfflineRewardsForPlayer(String playerName) {
        List<Map<String, String>> list = new ArrayList<>();
        if (conn == null) return list;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM offline_rewards WHERE player_name=? ORDER BY created_at ASC")) {
            ps.setString(1, playerName.toLowerCase());
            ResultSet rs = ps.executeQuery();
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
        } catch (SQLException e) {
            plugin.getLogger().warning("[DB] getOfflineRewardsForPlayer lỗi: " + e.getMessage());
        }
        return list;
    }

    /** Xoá một offline reward theo rewardId. */
    public synchronized void deleteOfflineReward(String rewardId) {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM offline_rewards WHERE reward_id=?")) {
            ps.setString(1, rewardId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[DB] deleteOfflineReward lỗi: " + e.getMessage());
        }
    }

    /** Lấy set tên tất cả player có pending rewards. */
    public synchronized Set<String> getPlayersWithPendingRewards() {
        Set<String> set = new HashSet<>();
        if (conn == null) return set;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT player_name FROM offline_rewards")) {
            while (rs.next()) set.add(rs.getString("player_name"));
        } catch (SQLException e) {
            plugin.getLogger().warning("[DB] getPlayersWithPendingRewards lỗi: " + e.getMessage());
        }
        return set;
    }

    /** Xoá offline rewards cũ hơn cutoff (TTL cleanup). */
    public synchronized int deleteExpiredOfflineRewards(long cutoffMs) {
        if (conn == null) return 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM offline_rewards WHERE created_at > 0 AND created_at < ?")) {
            ps.setLong(1, cutoffMs);
            return ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[DB] deleteExpiredOfflineRewards lỗi: " + e.getMessage());
            return 0;
        }
    }
}
