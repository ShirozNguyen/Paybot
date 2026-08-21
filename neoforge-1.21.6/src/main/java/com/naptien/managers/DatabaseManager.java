package com.naptien.managers;

import com.naptien.PayBotMod;
import com.naptien.config.PayBotConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.util.*;

/**
 * DatabaseManager — v5.4.1 (Fabric/Forge)
 * Quản lý lưu song song SQLite local và MySQL cho Fabric/Forge Mod.
 */
public class DatabaseManager {

    private final PayBotMod mod;
    private Connection conn;        // Connection MySQL (chỉ dùng khi useMySQL=true)
    private Connection bankConn;   // Connection SQLite Bank (chỉ dùng khi useMySQL=false)
    private Connection cardConn;   // Connection SQLite Card (chỉ dùng khi useMySQL=false)
    private Connection rewardConn; // Connection SQLite Rewards (chỉ dùng khi useMySQL=false)
    private boolean useMySQL = false; // true = chỉ dùng MySQL, false = chỉ dùng SQLite

    public DatabaseManager(PayBotMod mod) {
        this.mod = mod;
    }

    private synchronized boolean tryConnectMySQL() {
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        java.util.concurrent.Future<Boolean> future = executor.submit(() -> this.tryConnectMySQLDirect());
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
            PayBotConfig cfg = mod.getConfig();
            String rawHost = cfg != null ? sanitizeConfigValue(cfg.getString("mysql.host", "localhost")) : "localhost";
            int port = cfg != null ? cfg.getInt("mysql.port", 3306) : 3306;
            String db = cfg != null ? sanitizeConfigValue(cfg.getString("mysql.database", "paybot")) : "paybot";
            String user = cfg != null ? sanitizeConfigValue(cfg.getString("mysql.username", "root")) : "root";
            String pass = cfg != null ? sanitizeConfigValue(cfg.getString("mysql.password", "")) : "";
            boolean useSSL = cfg != null && (cfg.getBoolean("mysql.useSSL", false) || cfg.getBoolean("mysql.use-ssl", false));

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
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (Throwable ignored) {}

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
                        if (!tryHost.equalsIgnoreCase(host)) {
                            PayBotMod.LOGGER.info("[PayBot] Host '{}:{}' bị chặn NAT loopback bởi hosting. Đã tự động kết nối qua IP gateway hosting: '{}'", host, port, tryHost);
                        }
                        return true;
                    }
                } catch (SQLException e) {
                    lastSqlException = e;
                    if (e.getErrorCode() == 1045 || e.getErrorCode() == 1044 || e.getErrorCode() == 1049 || (e.getMessage() != null && e.getMessage().contains("Unknown database"))) {
                        PayBotMod.LOGGER.error("[PayBot] Lỗi kết nối MySQL ({}:{}): {}", tryHost, port, e.getMessage());
                        return false;
                    }
                } catch (Exception e) {
                    lastException = e;
                }
            }

            if (lastSqlException != null) {
                PayBotMod.LOGGER.error("[PayBot] Lỗi kết nối MySQL ({}:{}): {}", host, port, lastSqlException.getMessage());
            } else if (lastException != null) {
                PayBotMod.LOGGER.error("[PayBot] Lỗi không xác định khi kết nối MySQL: {}", lastException.getMessage(), lastException);
            }
            return false;
        } catch (Exception e) {
            PayBotMod.LOGGER.error("[PayBot] Lỗi không xác định khi kết nối MySQL: {}", e.getMessage(), e);
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

    public synchronized void init() {
        String dbType = mod.getConfig() != null ? mod.getConfig().getString("database-type", "SQLite").trim().toLowerCase() : "sqlite";

        if ("mysql".equals(dbType)) {
            useMySQL = true;
            PayBotMod.LOGGER.info("[PayBot] Chế độ database: MySQL. Đang kết nối...");
            boolean mysqlSuccess = tryConnectMySQL();
            if (!mysqlSuccess) {
                PayBotMod.LOGGER.error("[PayBot] ═══════════════════════════════════════════════");
                PayBotMod.LOGGER.error("[PayBot] LỖI NGHIÊM TRỌNG: Không thể kết nối MySQL!");
                PayBotMod.LOGGER.error("[PayBot] Kiểm tra lại mục mysql trong file config.yml và thông tin kết nối.");
                PayBotMod.LOGGER.error("[PayBot] Mod sẽ dừng khởi tạo DB MySQL.");
                PayBotMod.LOGGER.error("[PayBot] ═══════════════════════════════════════════════");
                PayBotMod.LOGGER.error("[PayBot] Kiểm tra lại file mysql.yml và thông tin kết nối.");
                PayBotMod.LOGGER.error("[PayBot] Mod sẽ DỪNG/TẮT ngay bây giờ.");
                PayBotMod.LOGGER.error("[PayBot] ═══════════════════════════════════════════════");
                throw new RuntimeException("[PayBot] Không thể kết nối MySQL theo cấu hình database-type: mysql");
            }
            try {
                createMySQLTables();
                PayBotMod.LOGGER.info("[PayBot] Kết nối MySQL thành công! Mod hoạt động ở chế độ MySQL.");
                runMigrations();
            } catch (Exception e) {
                PayBotMod.LOGGER.error("[PayBot] Lỗi khởi tạo bảng MySQL!", e);
                throw new RuntimeException("[PayBot] Lỗi khởi tạo bảng MySQL: " + e.getMessage());
            }
        } else {
            if (!"sqlite".equals(dbType)) {
                PayBotMod.LOGGER.warn("[PayBot] Giá trị database-type không hợp lệ: \"" + dbType + "\". Tự động dùng SQLite.");
            }
            useMySQL = false;
            PayBotMod.LOGGER.info("[PayBot] Chế độ database: SQLite.");
            try {
                Class.forName("org.sqlite.JDBC");
                File cardDir = new File(mod.getDataDir().toFile(), "Card");
                File bankDir = new File(mod.getDataDir().toFile(), "Bank");
                if (!cardDir.exists()) cardDir.mkdirs();
                if (!bankDir.exists()) bankDir.mkdirs();
                
                File bankDbFile   = new File(bankDir, "bank_orders.db");
                File cardDbFile   = new File(cardDir, "card_orders.db");
                File rewardDbFile = new File(mod.getDataDir().toFile(), "offline_rewards.db");
                
                bankConn   = DriverManager.getConnection("jdbc:sqlite:" + bankDbFile.getAbsolutePath());
                cardConn   = DriverManager.getConnection("jdbc:sqlite:" + cardDbFile.getAbsolutePath());
                rewardConn = DriverManager.getConnection("jdbc:sqlite:" + rewardDbFile.getAbsolutePath());
                
                createSQLiteTables();
                PayBotMod.LOGGER.info("[PayBot] Kết nối SQLite thành công!");
                runMigrations();
            } catch (Exception e) {
                PayBotMod.LOGGER.error("[PayBot] Lỗi nghiêm trọng khi khởi tạo cơ sở dữ liệu! Dữ liệu sẽ không được lưu.", e);
            }
        }
    }

    public synchronized void close() {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
            conn = null;
        }
        if (bankConn != null) {
            try { bankConn.close(); } catch (SQLException ignored) {}
            bankConn = null;
        }
        if (cardConn != null) {
            try { cardConn.close(); } catch (SQLException ignored) {}
            cardConn = null;
        }
        if (rewardConn != null) {
            try { rewardConn.close(); } catch (SQLException ignored) {}
            rewardConn = null;
        }
    }

    private void createSQLiteTables() throws SQLException {
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

    private void migrateOldPayBotDb() {
        File oldDbFile = new File(mod.getDataDir().toFile(), "paybot.db");
        if (!oldDbFile.exists()) return;

        PayBotMod.LOGGER.info("[PayBot] Phát hiện database paybot.db cũ. Bắt đầu migrate dữ liệu sang cấu trúc mới...");

        String oldUrl = "jdbc:sqlite:" + oldDbFile.getAbsolutePath();
        try (Connection oldConn = DriverManager.getConnection(oldUrl)) {
            // 1. Bank
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
                PayBotMod.LOGGER.info("[PayBot] Đã chuyển " + count + " đơn bank từ database cũ.");
            } catch (SQLException e) {
                PayBotMod.LOGGER.warn("[PayBot] Không thể đọc bank_orders từ database cũ: " + e.getMessage());
            }

            // 2. Card
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
                PayBotMod.LOGGER.info("[PayBot] Đã chuyển " + count + " đơn card từ database cũ.");
            } catch (SQLException e) {
                PayBotMod.LOGGER.warn("[PayBot] Không thể đọc card_orders từ database cũ: " + e.getMessage());
            }

            // 3. Reward
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
                PayBotMod.LOGGER.info("[PayBot] Đã chuyển " + count + " offline rewards từ database cũ.");
            } catch (SQLException e) {
                PayBotMod.LOGGER.warn("[PayBot] Không thể đọc offline_rewards từ database cũ: " + e.getMessage());
            }

            oldConn.close();

            File migratedFile = new File(mod.getDataDir().toFile(), "paybot.db.migrated");
            if (oldDbFile.renameTo(migratedFile)) {
                PayBotMod.LOGGER.info("[PayBot] Đã đổi tên database cũ thành paybot.db.migrated.");
            } else {
                PayBotMod.LOGGER.warn("[PayBot] Không thể đổi tên file paybot.db cũ. Hãy xoá/đổi tên thủ công.");
            }

        } catch (Exception e) {
            PayBotMod.LOGGER.error("[PayBot] Lỗi trong quá trình migrate database cũ: " + e.getMessage(), e);
        }
    }

    private void syncMySQLAndSQLite() {
        if (conn == null || bankConn == null || cardConn == null || rewardConn == null) return;
        PayBotMod.LOGGER.info("[PayBot] Bắt đầu đồng bộ song phương dữ liệu giữa MySQL và SQLite local...");
        
        try {
            syncBankOrders();
            syncCardOrders();
            syncOfflineRewards();
            PayBotMod.LOGGER.info("[PayBot] Đồng bộ song phương dữ liệu hoàn tất!");
        } catch (Exception e) {
            PayBotMod.LOGGER.warn("[PayBot] Lỗi khi đồng bộ song phương: " + e.getMessage());
        }
    }

    private void syncBankOrders() throws SQLException {
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
            PayBotMod.LOGGER.info("[PayBot] Đồng bộ Bank: Cập nhật SQLite local: " + sqliteUpdated + " đơn, MySQL: " + mysqlUpdated + " đơn.");
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

        Map<String, Map<String, Object>> mysqlCard = new HashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM card_orders ORDER BY created_at ASC")) {
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
            PayBotMod.LOGGER.info("[PayBot] Đồng bộ Card: Cập nhật SQLite local: " + sqliteUpdated + " đơn, MySQL: " + mysqlUpdated + " đơn.");
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
            PayBotMod.LOGGER.info("[PayBot] Đồng bộ Reward: Cập nhật SQLite local: " + sqliteUpdated + " rewards, MySQL: " + mysqlUpdated + " rewards.");
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

    @SuppressWarnings("unchecked")
    private void migrateBankOrders() {
        File yaml = new File(mod.getDataDir().toFile(), "bank-orders.yml");
        if (!yaml.exists()) return;

        PayBotMod.LOGGER.info("[PayBot] Migration: đang chuyển bank-orders.yml sang database...");
        int count = 0;
        try {
            Map<String, Object> data = new HashMap<>();
            try (java.io.Reader r = Files.newBufferedReader(yaml.toPath(), StandardCharsets.UTF_8)) {
                Yaml yamlParser = new Yaml();
                Object loaded = yamlParser.load(r);
                if (loaded instanceof Map) data = (Map<String, Object>) loaded;
            }
            Object ordersObj = data.get("orders");
            if (ordersObj instanceof Map) {
                Map<?, ?> orders = (Map<?, ?>) ordersObj;
                for (Map.Entry<?, ?> entry : orders.entrySet()) {
                    String id = String.valueOf(entry.getKey());
                    if (entry.getValue() instanceof Map) {
                        Map<?, ?> path = (Map<?, ?>) entry.getValue();
                        Object pNameObj = path.get("playerName");
                        String playerName = pNameObj != null ? String.valueOf(pNameObj) : "";
                        int amount = path.get("amount") instanceof Number num ? num.intValue() : 0;
                        Object statusObj = path.get("status");
                        String status = statusObj != null ? String.valueOf(statusObj) : "PENDING";
                        long createdAt = path.get("createdAt") instanceof Number num ? num.longValue() : System.currentTimeMillis();
                        boolean regBot = path.get("registeredWithBot") instanceof Boolean b ? b : false;

                        upsertBankOrder(id, playerName, amount, status, createdAt, regBot ? 1 : 0);
                        count++;
                    }
                }
            }
            yaml.renameTo(new File(mod.getDataDir().toFile(), "bank-orders.yml.migrated"));
            PayBotMod.LOGGER.info("[PayBot] Migration bank-orders: " + count + " đơn đã chuyển sang DB.");
        } catch (Exception e) {
            PayBotMod.LOGGER.error("[PayBot] Migration bank-orders lỗi: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void migrateCardOrders() {
        File yaml = new File(mod.getDataDir().toFile(), "card-orders.yml");
        if (!yaml.exists()) return;

        PayBotMod.LOGGER.info("[PayBot] Migration: đang chuyển card-orders.yml sang database...");
        int count = 0;
        try {
            Map<String, Object> data = new HashMap<>();
            try (java.io.Reader r = Files.newBufferedReader(yaml.toPath(), StandardCharsets.UTF_8)) {
                Yaml yamlParser = new Yaml();
                Object loaded = yamlParser.load(r);
                if (loaded instanceof Map) data = (Map<String, Object>) loaded;
            }
            Object ordersObj = data.get("orders");
            if (ordersObj instanceof Map) {
                Map<?, ?> orders = (Map<?, ?>) ordersObj;
                for (Map.Entry<?, ?> entry : orders.entrySet()) {
                    String id = String.valueOf(entry.getKey());
                    if (entry.getValue() instanceof Map) {
                        Map<?, ?> path = (Map<?, ?>) entry.getValue();
                        Object pNameObj = path.get("playerName");
                        String playerName = pNameObj != null ? String.valueOf(pNameObj) : "";
                        Object telcoObj = path.get("telco");
                        String telco = telcoObj != null ? String.valueOf(telcoObj) : "";
                        int denom = path.get("denom") instanceof Number num ? num.intValue() : 0;
                        Object codeObj = path.get("cardCode");
                        String cardCode = codeObj != null ? String.valueOf(codeObj) : "";
                        Object serialObj = path.get("cardSerial");
                        String cardSerial = serialObj != null ? String.valueOf(serialObj) : "";
                        Object statusObj = path.get("status");
                        String status = statusObj != null ? String.valueOf(statusObj) : "99";
                        Object msgObj = path.get("message");
                        String message = msgObj != null ? String.valueOf(msgObj) : "";
                        long createdAt = path.get("createdAt") instanceof Number num ? num.longValue() : System.currentTimeMillis();
                        int attempts = path.get("submitAttempts") instanceof Number num ? num.intValue() : 0;
                        boolean connErr = path.get("connectionError") instanceof Boolean b ? b : false;

                        upsertCardOrder(id, playerName, telco, denom, cardCode, cardSerial,
                                status, message, createdAt, attempts, connErr ? 1 : 0);
                        count++;
                    }
                }
            }
            yaml.renameTo(new File(mod.getDataDir().toFile(), "card-orders.yml.migrated"));
            PayBotMod.LOGGER.info("[PayBot] Migration card-orders: " + count + " đơn đã chuyển sang DB.");
        } catch (Exception e) {
            PayBotMod.LOGGER.error("[PayBot] Migration card-orders lỗi: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void migrateOfflineRewards() {
        File yaml = new File(mod.getDataDir().toFile(), "offline-rewards.yml");
        if (!yaml.exists()) return;

        PayBotMod.LOGGER.info("[PayBot] Migration: đang chuyển offline-rewards.yml sang database...");
        int count = 0;
        try {
            Map<String, Object> data = new HashMap<>();
            try (java.io.Reader r = Files.newBufferedReader(yaml.toPath(), StandardCharsets.UTF_8)) {
                Yaml yamlParser = new Yaml();
                Object loaded = yamlParser.load(r);
                if (loaded instanceof Map) data = (Map<String, Object>) loaded;
            }
            Object playersObj = data.get("players");
            if (playersObj instanceof Map) {
                Map<?, ?> players = (Map<?, ?>) playersObj;
                for (Map.Entry<?, ?> entry : players.entrySet()) {
                    String player = String.valueOf(entry.getKey());
                    if (entry.getValue() instanceof List) {
                        List<?> rawList = (List<?>) entry.getValue();
                        for (Object rawItem : rawList) {
                            if (!(rawItem instanceof Map)) continue;
                            Map<?, ?> raw = (Map<?, ?>) rawItem;
                            String rewardId = str(raw, "rewardId", UUID.randomUUID().toString());
                            String playerName = str(raw, "playerName", player);
                            String rawCmd = str(raw, "rewardCmd", "");
                            String rewardAmt = str(raw, "rewardAmount", "0");
                            String denomVnd = str(raw, "denom", "");
                            String type = str(raw, "type", "card");
                            String invoiceId = str(raw, "invoiceId", "");
                            String discordUid = str(raw, "discordUid", "");
                            long createdAt;
                            try {
                                createdAt = Long.parseLong(str(raw, "createdAt", "0"));
                            } catch (NumberFormatException e2) {
                                createdAt = System.currentTimeMillis();
                            }

                            insertOfflineReward(rewardId, playerName, rawCmd, rewardAmt,
                                    denomVnd, type, invoiceId, discordUid, createdAt);
                            count++;
                        }
                    }
                }
            }
            yaml.renameTo(new File(mod.getDataDir().toFile(), "offline-rewards.yml.migrated"));
            PayBotMod.LOGGER.info("[PayBot] Migration offline-rewards: " + count + " reward đã chuyển sang DB.");
        } catch (Exception e) {
            PayBotMod.LOGGER.error("[PayBot] Migration offline-rewards lỗi: " + e.getMessage(), e);
        }
    }

    private static String str(Map<?, ?> map, String key, String def) {
        Object v = map.get(key);
        return v != null ? String.valueOf(v) : def;
    }

    // ─── Bank Orders CRUD ─────────────────────────────────────────────────────

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
            PayBotMod.LOGGER.warn(tag + " upsertBankOrder lỗi: " + e.getMessage());
        }
    }

    public synchronized void updateBankStatus(String invoiceId, String status) {
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : bankConn;
        if (c == null) return;
        String tag = useMySQL ? "[MySQL]" : "[SQLite]";
        try (PreparedStatement ps = c.prepareStatement("UPDATE bank_orders SET status=? WHERE invoice_id=?")) {
            ps.setString(1, status);
            ps.setString(2, invoiceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            PayBotMod.LOGGER.warn(tag + " updateBankStatus lỗi: " + e.getMessage());
        }
    }

    public synchronized void markBankRegistered(String invoiceId, boolean registered) {
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : bankConn;
        if (c == null) return;
        String tag = useMySQL ? "[MySQL]" : "[SQLite]";
        try (PreparedStatement ps = c.prepareStatement("UPDATE bank_orders SET registered_with_bot=? WHERE invoice_id=?")) {
            ps.setInt(1, registered ? 1 : 0);
            ps.setString(2, invoiceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            PayBotMod.LOGGER.warn(tag + " markBankRegistered lỗi: " + e.getMessage());
        }
    }

    public synchronized List<Map<String, Object>> getAllBankOrders() {
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : bankConn;
        List<Map<String, Object>> list = new ArrayList<>();
        if (c == null) return list;
        String tag = useMySQL ? "[MySQL]" : "[SQLite]";
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM bank_orders ORDER BY created_at ASC")) {
            return parseBankOrders(rs);
        } catch (SQLException e) {
            PayBotMod.LOGGER.warn(tag + " getAllBankOrders lỗi: " + e.getMessage());
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
            row.put("invoice_id", rs.getString("invoice_id"));
            row.put("player_name", rs.getString("player_name"));
            row.put("amount", rs.getInt("amount"));
            row.put("status", rs.getString("status"));
            row.put("created_at", rs.getLong("created_at"));
            row.put("registered_with_bot", rs.getInt("registered_with_bot") == 1);
            list.add(row);
        }
        return list;
    }

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
            PayBotMod.LOGGER.warn(tag + " deleteBankOrdersBefore lỗi: " + e.getMessage());
        }
        return 0;
    }

    // ─── Card Orders CRUD ─────────────────────────────────────────────────────

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
            PayBotMod.LOGGER.warn(tag + " upsertCardOrder lỗi: " + e.getMessage());
        }
    }

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
            PayBotMod.LOGGER.warn(tag + " updateCardStatus lỗi: " + e.getMessage());
        }
    }

    public synchronized void updateCardConnectionError(String requestId, boolean hasError) {
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : cardConn;
        if (c == null) return;
        String tag = useMySQL ? "[MySQL]" : "[SQLite]";
        try (PreparedStatement ps = c.prepareStatement("UPDATE card_orders SET connection_error=? WHERE request_id=?")) {
            ps.setInt(1, hasError ? 1 : 0);
            ps.setString(2, requestId);
            ps.executeUpdate();
        } catch (SQLException e) {
            PayBotMod.LOGGER.warn(tag + " updateCardConnectionError lỗi: " + e.getMessage());
        }
    }

    public synchronized void incrementCardSubmitAttempts(String requestId) {
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : cardConn;
        if (c == null) return;
        String tag = useMySQL ? "[MySQL]" : "[SQLite]";
        try (PreparedStatement ps = c.prepareStatement("UPDATE card_orders SET submit_attempts=submit_attempts+1 WHERE request_id=?")) {
            ps.setString(1, requestId);
            ps.executeUpdate();
        } catch (SQLException e) {
            PayBotMod.LOGGER.warn(tag + " incrementCardSubmitAttempts lỗi: " + e.getMessage());
        }
    }

    public synchronized List<Map<String, Object>> getAllCardOrders() {
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : cardConn;
        List<Map<String, Object>> list = new ArrayList<>();
        if (c == null) return list;
        String tag = useMySQL ? "[MySQL]" : "[SQLite]";
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM card_orders ORDER BY created_at ASC")) {
            return parseCardOrders(rs);
        } catch (SQLException e) {
            PayBotMod.LOGGER.warn(tag + " getAllCardOrders lỗi: " + e.getMessage());
        }
        return list;
    }

    private List<Map<String, Object>> parseCardOrders(ResultSet rs) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
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
        return list;
    }

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
            PayBotMod.LOGGER.warn(tag + " deleteCardOrdersBefore lỗi: " + e.getMessage());
        }
        return 0;
    }

    // ─── Offline Rewards CRUD ─────────────────────────────────────────────────

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
            PayBotMod.LOGGER.warn(tag + " insertOfflineReward lỗi: " + e.getMessage());
        }
    }

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
            PayBotMod.LOGGER.warn(tag + " getOfflineRewardsForPlayer lỗi: " + e.getMessage());
        }
        return list;
    }

    private List<Map<String, String>> parseOfflineRewards(ResultSet rs) throws SQLException {
        List<Map<String, String>> list = new ArrayList<>();
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
        return list;
    }

    public synchronized void deleteOfflineReward(String rewardId) {
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : rewardConn;
        if (c == null) return;
        String tag = useMySQL ? "[MySQL]" : "[SQLite]";
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM offline_rewards WHERE reward_id=?")) {
            ps.setString(1, rewardId);
            ps.executeUpdate();
        } catch (SQLException e) {
            PayBotMod.LOGGER.warn(tag + " deleteOfflineReward lỗi: " + e.getMessage());
        }
    }

    public synchronized Set<String> getPlayersWithPendingRewards() {
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : rewardConn;
        Set<String> set = new HashSet<>();
        if (c == null) return set;
        String tag = useMySQL ? "[MySQL]" : "[SQLite]";
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT player_name FROM offline_rewards")) {
            while (rs.next()) set.add(rs.getString("player_name"));
        } catch (SQLException e) {
            PayBotMod.LOGGER.warn(tag + " getPlayersWithPendingRewards lỗi: " + e.getMessage());
        }
        return set;
    }

    public synchronized int deleteExpiredOfflineRewards(long cutoffMs) {
        Connection c = useMySQL ? (tryConnectMySQL() ? conn : null) : rewardConn;
        if (c == null) return 0;
        String tag = useMySQL ? "[MySQL]" : "[SQLite]";
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM offline_rewards WHERE created_at > 0 AND created_at < ?")) {
            ps.setLong(1, cutoffMs);
            return ps.executeUpdate();
        } catch (SQLException e) {
            PayBotMod.LOGGER.warn(tag + " deleteExpiredOfflineRewards lỗi: " + e.getMessage());
        }
        return 0;
    }
}
