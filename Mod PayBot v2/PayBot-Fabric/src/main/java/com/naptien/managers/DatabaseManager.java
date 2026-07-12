package com.naptien.managers;

import com.naptien.PayBotMod;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.util.*;

/**
 * DatabaseManager — v5.1.0 (Fabric)
 */
public class DatabaseManager {

    private static final String DB_FILE = "paybot.db";

    private final PayBotMod mod;
    private Connection conn;
    private boolean isSQLite = false;

    public DatabaseManager(PayBotMod mod) {
        this.mod = mod;
    }

    public synchronized void init() {
        try {
            File mysqlFile = new File(mod.getDataDir().toFile(), "mysql.yml");
            if (!mysqlFile.exists()) {
                mod.getDataDir().toFile().mkdirs();
                try (java.io.InputStream is = DatabaseManager.class.getResourceAsStream("/mysql.yml")) {
                    if (is != null) {
                        java.nio.file.Files.copy(is, mysqlFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            Map<String, Object> mysqlData = new HashMap<>();
            if (mysqlFile.exists()) {
                try (java.io.Reader r = new java.io.InputStreamReader(java.nio.file.Files.newInputStream(mysqlFile.toPath()), java.nio.charset.StandardCharsets.UTF_8)) {
                    org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
                    Object loaded = yaml.load(r);
                    if (loaded instanceof Map) {
                        mysqlData = (Map<String, Object>) loaded;
                    }
                }
            }

            String host = String.valueOf(mysqlData.getOrDefault("host", "localhost"));
            int port = mysqlData.get("port") instanceof Number num ? num.intValue() : 3306;
            String db = String.valueOf(mysqlData.getOrDefault("database", "paybot"));
            String user = String.valueOf(mysqlData.getOrDefault("username", "root"));
            String pass = String.valueOf(mysqlData.getOrDefault("password", ""));
            boolean useSSL = mysqlData.get("use-ssl") instanceof Boolean b ? b : false;

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
                PayBotMod.LOGGER.info("[PayBot] Kết nối cơ sở dữ liệu MySQL thành công tới: " + host + ":" + port + "/" + db);
            } catch (Exception mysqlEx) {
                PayBotMod.LOGGER.warn("[PayBot] Không thể kết nối tới cơ sở dữ liệu MySQL (" + mysqlEx.getMessage() + "), tự động chuyển sang cơ chế dự phòng SQLite local...");
                // Fallback về SQLite
                Class.forName("org.sqlite.JDBC");
                File dbFile = new File(mod.getDataDir().toFile(), "paybot.db");
                if (!dbFile.getParentFile().exists()) {
                    dbFile.getParentFile().mkdirs();
                }
                String sqliteUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                conn = DriverManager.getConnection(sqliteUrl);
                isSQLite = true;
                PayBotMod.LOGGER.info("[PayBot] Khởi tạo cơ sở dữ liệu SQLite dự phòng thành công tại: " + dbFile.getName());
            }

            createTables();
            runMigrations();

        } catch (Exception e) {
            PayBotMod.LOGGER.error("[PayBot] Không thể khởi tạo bất kỳ kết nối cơ sở dữ liệu nào (MySQL & SQLite fallback đều lỗi)! Dữ liệu sẽ không được lưu.", e);
        }
    }

    public synchronized void close() {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
            conn = null;
        }
    }

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

    private void runMigrations() {
        migrateBankOrders();
        migrateCardOrders();
        migrateOfflineRewards();
    }

    @SuppressWarnings("unchecked")
    private void migrateBankOrders() {
        File yamlFile = new File(mod.getDataDir().toFile(), "bank-orders.yml");
        if (!yamlFile.exists()) return;

        PayBotMod.LOGGER.info("[PayBot] Migration: đang chuyển bank-orders.yml sang database...");
        int count = 0;
        try {
            Yaml yamlParser = new Yaml();
            Map<String, Object> cfg;
            try (Reader r = new InputStreamReader(Files.newInputStream(yamlFile.toPath()), StandardCharsets.UTF_8)) {
                cfg = yamlParser.load(r);
            }
            if (cfg != null && cfg.containsKey("orders")) {
                Map<String, Object> orders = (Map<String, Object>) cfg.get("orders");
                for (Map.Entry<String, Object> entry : orders.entrySet()) {
                    String id = entry.getKey();
                    Map<String, Object> row = (Map<String, Object>) entry.getValue();
                    String playerName = str(row, "playerName", "");
                    int amount       = num(row, "amount");
                    String status    = str(row, "status", "PENDING");
                    long createdAt   = numLong(row, "createdAt");
                    boolean regBot   = bool(row, "registeredWithBot");

                    upsertBankOrder(id, playerName, amount, status, createdAt, regBot ? 1 : 0);
                    count++;
                }
            }
            yamlFile.renameTo(new File(mod.getDataDir().toFile(), "bank-orders.yml.migrated"));
            PayBotMod.LOGGER.info("[PayBot] Migration bank-orders: " + count + " đơn đã chuyển sang DB.");
        } catch (Exception e) {
            PayBotMod.LOGGER.warn("[PayBot] Migration bank-orders lỗi: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void migrateCardOrders() {
        File yamlFile = new File(mod.getDataDir().toFile(), "card-orders.yml");
        if (!yamlFile.exists()) return;

        PayBotMod.LOGGER.info("[PayBot] Migration: đang chuyển card-orders.yml sang database...");
        int count = 0;
        try {
            Yaml yamlParser = new Yaml();
            Map<String, Object> cfg;
            try (Reader r = new InputStreamReader(Files.newInputStream(yamlFile.toPath()), StandardCharsets.UTF_8)) {
                cfg = yamlParser.load(r);
            }
            if (cfg != null && cfg.containsKey("orders")) {
                Map<String, Object> orders = (Map<String, Object>) cfg.get("orders");
                for (Map.Entry<String, Object> entry : orders.entrySet()) {
                    String id = entry.getKey();
                    Map<String, Object> row = (Map<String, Object>) entry.getValue();
                    String playerName = str(row, "playerName", "");
                    String telco     = str(row, "telco", "");
                    int denom        = num(row, "denom");
                    String cardCode  = str(row, "cardCode", "");
                    String cardSerial= str(row, "cardSerial", "");
                    String status    = str(row, "status", "99");
                    String message   = str(row, "message", "");
                    long createdAt   = numLong(row, "createdAt");
                    int attempts     = num(row, "submitAttempts");
                    boolean connErr  = bool(row, "connectionError");

                    upsertCardOrder(id, playerName, telco, denom, cardCode, cardSerial,
                            status, message, createdAt, attempts, connErr ? 1 : 0);
                    count++;
                }
            }
            yamlFile.renameTo(new File(mod.getDataDir().toFile(), "card-orders.yml.migrated"));
            PayBotMod.LOGGER.info("[PayBot] Migration card-orders: " + count + " đơn đã chuyển sang DB.");
        } catch (Exception e) {
            PayBotMod.LOGGER.warn("[PayBot] Migration card-orders lỗi: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void migrateOfflineRewards() {
        File yamlFile = new File(mod.getDataDir().toFile(), "offline-rewards.yml");
        if (!yamlFile.exists()) return;

        PayBotMod.LOGGER.info("[PayBot] Migration: đang chuyển offline-rewards.yml sang database...");
        int count = 0;
        try {
            Yaml yamlParser = new Yaml();
            Map<String, Object> cfg;
            try (Reader r = new InputStreamReader(Files.newInputStream(yamlFile.toPath()), StandardCharsets.UTF_8)) {
                cfg = yamlParser.load(r);
            }
            if (cfg != null && cfg.containsKey("players")) {
                Map<String, Object> players = (Map<String, Object>) cfg.get("players");
                for (Map.Entry<String, Object> entry : players.entrySet()) {
                    String player = entry.getKey();
                    List<?> rawList = (List<?>) entry.getValue();
                    if (rawList == null) continue;
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
            yamlFile.renameTo(new File(mod.getDataDir().toFile(), "offline-rewards.yml.migrated"));
            PayBotMod.LOGGER.info("[PayBot] Migration offline-rewards: " + count + " reward đã chuyển sang DB.");
        } catch (Exception e) {
            PayBotMod.LOGGER.warn("[PayBot] Migration offline-rewards lỗi: " + e.getMessage());
        }
    }

    private static String str(Map<?, ?> map, String key, String def) {
        Object v = map.get(key);
        return v != null ? String.valueOf(v) : def;
    }
    
    private int num(Map<?, ?> m, String k) { Object v = m.get(k); return v instanceof Number ? ((Number)v).intValue() : 0; }
    private long numLong(Map<?, ?> m, String k) { Object v = m.get(k); return v instanceof Number ? ((Number)v).longValue() : System.currentTimeMillis(); }
    private boolean bool(Map<?, ?> m, String k) { Object v = m.get(k); return v instanceof Boolean && (Boolean)v; }

    public synchronized void upsertBankOrder(String invoiceId, String playerName, int amount, String status, long createdAt, int registeredWithBot) {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement("REPLACE INTO bank_orders (invoice_id,player_name,amount,status,created_at,registered_with_bot) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, invoiceId); ps.setString(2, playerName); ps.setInt(3, amount);
            ps.setString(4, status); ps.setLong(5, createdAt); ps.setInt(6, registeredWithBot);
            ps.executeUpdate();
        } catch (SQLException e) {}
    }

    public synchronized void updateBankStatus(String invoiceId, String status) {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement("UPDATE bank_orders SET status=? WHERE invoice_id=?")) {
            ps.setString(1, status); ps.setString(2, invoiceId); ps.executeUpdate();
        } catch (SQLException e) {}
    }

    public synchronized void markBankRegistered(String invoiceId, boolean registered) {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement("UPDATE bank_orders SET registered_with_bot=? WHERE invoice_id=?")) {
            ps.setInt(1, registered ? 1 : 0); ps.setString(2, invoiceId); ps.executeUpdate();
        } catch (SQLException e) {}
    }

    public synchronized List<Map<String, Object>> getAllBankOrders() {
        List<Map<String, Object>> list = new ArrayList<>();
        if (conn == null) return list;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM bank_orders ORDER BY created_at ASC")) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("invoice_id", rs.getString("invoice_id"));
                row.put("player_name", rs.getString("player_name"));
                row.put("amount", rs.getInt("amount"));
                row.put("status", rs.getString("status"));
                row.put("created_at", rs.getLong("created_at"));
                row.put("registered_with_bot", rs.getInt("registered_with_bot") == 1);
                list.add(row);
            }
        } catch (SQLException e) {}
        return list;
    }

    public synchronized int deleteBankOrdersBefore(long cutoffMs, List<String> terminalStatuses) {
        if (conn == null || terminalStatuses.isEmpty()) return 0;
        String placeholders = String.join(",", Collections.nCopies(terminalStatuses.size(), "?"));
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM bank_orders WHERE status IN (" + placeholders + ") AND created_at <= ?")) {
            for (int i = 0; i < terminalStatuses.size(); i++) ps.setString(i + 1, terminalStatuses.get(i));
            ps.setLong(terminalStatuses.size() + 1, cutoffMs);
            return ps.executeUpdate();
        } catch (SQLException e) { return 0; }
    }

    public synchronized void upsertCardOrder(String requestId, String playerName, String telco, int denom, String cardCode, String cardSerial, String status, String message, long createdAt, int submitAttempts, int connectionError) {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement("REPLACE INTO card_orders (request_id,player_name,telco,denom,card_code,card_serial,status,message,created_at,submit_attempts,connection_error) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, requestId); ps.setString(2, playerName); ps.setString(3, telco);
            ps.setInt(4, denom); ps.setString(5, cardCode); ps.setString(6, cardSerial);
            ps.setString(7, status); ps.setString(8, message); ps.setLong(9, createdAt);
            ps.setInt(10, submitAttempts); ps.setInt(11, connectionError); ps.executeUpdate();
        } catch (SQLException e) {}
    }

    public synchronized void updateCardStatus(String requestId, String status, String message) {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement("UPDATE card_orders SET status=?, message=? WHERE request_id=?")) {
            ps.setString(1, status); ps.setString(2, message != null ? message : "");
            ps.setString(3, requestId); ps.executeUpdate();
        } catch (SQLException e) {}
    }

    public synchronized void updateCardConnectionError(String requestId, boolean hasError) {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement("UPDATE card_orders SET connection_error=? WHERE request_id=?")) {
            ps.setInt(1, hasError ? 1 : 0); ps.setString(2, requestId); ps.executeUpdate();
        } catch (SQLException e) {}
    }

    public synchronized void incrementCardSubmitAttempts(String requestId) {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement("UPDATE card_orders SET submit_attempts=submit_attempts+1 WHERE request_id=?")) {
            ps.setString(1, requestId); ps.executeUpdate();
        } catch (SQLException e) {}
    }

    public synchronized List<Map<String, Object>> getAllCardOrders() {
        List<Map<String, Object>> list = new ArrayList<>();
        if (conn == null) return list;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM card_orders ORDER BY created_at ASC")) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
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
                row.put("connection_error", rs.getInt("connection_error") == 1);
                list.add(row);
            }
        } catch (SQLException e) {}
        return list;
    }

    public synchronized int deleteCardOrdersBefore(long cutoffMs, List<String> terminalStatuses) {
        if (conn == null || terminalStatuses.isEmpty()) return 0;
        String placeholders = String.join(",", Collections.nCopies(terminalStatuses.size(), "?"));
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM card_orders WHERE status IN (" + placeholders + ") AND created_at <= ?")) {
            for (int i = 0; i < terminalStatuses.size(); i++) ps.setString(i + 1, terminalStatuses.get(i));
            ps.setLong(terminalStatuses.size() + 1, cutoffMs);
            return ps.executeUpdate();
        } catch (SQLException e) { return 0; }
    }

    public synchronized void insertOfflineReward(String rewardId, String playerName, String rawCmd, String rewardAmount, String denomVnd, String type, String invoiceId, String discordUid, long createdAt) {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement("INSERT IGNORE INTO offline_rewards (reward_id,player_name,raw_cmd,reward_amount,denom_vnd,type,invoice_id,discord_uid,created_at) VALUES (?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, rewardId); ps.setString(2, playerName.toLowerCase());
            ps.setString(3, rawCmd != null ? rawCmd : ""); ps.setString(4, rewardAmount != null ? rewardAmount : "0");
            ps.setString(5, denomVnd != null ? denomVnd : ""); ps.setString(6, type != null ? type : "card");
            ps.setString(7, invoiceId != null ? invoiceId : ""); ps.setString(8, discordUid != null ? discordUid : "");
            ps.setLong(9, createdAt); ps.executeUpdate();
        } catch (SQLException e) {}
    }

    public synchronized List<Map<String, String>> getOfflineRewardsForPlayer(String playerName) {
        List<Map<String, String>> list = new ArrayList<>();
        if (conn == null) return list;
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM offline_rewards WHERE player_name=? ORDER BY created_at ASC")) {
            ps.setString(1, playerName.toLowerCase());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("rewardId", rs.getString("reward_id"));
                row.put("playerName", rs.getString("player_name"));
                row.put("rewardCmd", rs.getString("raw_cmd"));
                row.put("rewardAmount", rs.getString("reward_amount"));
                row.put("denom", rs.getString("denom_vnd"));
                row.put("type", rs.getString("type"));
                row.put("invoiceId", rs.getString("invoice_id"));
                row.put("discordUid", rs.getString("discord_uid"));
                row.put("createdAt", String.valueOf(rs.getLong("created_at")));
                list.add(row);
            }
        } catch (SQLException e) {}
        return list;
    }

    public synchronized boolean deleteOfflineReward(String rewardId) {
        if (conn == null) return false;
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM offline_rewards WHERE reward_id=?")) {
            ps.setString(1, rewardId); return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public synchronized Set<String> getPlayersWithPendingRewards() {
        Set<String> set = new HashSet<>();
        if (conn == null) return set;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT DISTINCT player_name FROM offline_rewards")) {
            while (rs.next()) set.add(rs.getString("player_name"));
        } catch (SQLException e) {}
        return set;
    }

    public synchronized int deleteExpiredOfflineRewards(long cutoffMs) {
        if (conn == null) return 0;
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM offline_rewards WHERE created_at > 0 AND created_at < ?")) {
            ps.setLong(1, cutoffMs);
            return ps.executeUpdate();
        } catch (SQLException e) { return 0; }
    }
}
