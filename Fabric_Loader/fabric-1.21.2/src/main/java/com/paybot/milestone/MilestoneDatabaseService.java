package com.paybot.milestone;

import com.paybot.PayBotMod;

import java.io.File;
import java.sql.*;
import java.util.*;

/**
 * Quản trị CSDL cục bộ an toàn (SQLite) cho hệ thống mốc nạp trên Mod Loader.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm quản lý lưu trữ CSDL mốc nạp.
 */
public final class MilestoneDatabaseService {

    private final File dbFile;
    private Connection connection;

    public MilestoneDatabaseService(File dataFolder) {
        this.dbFile = new File(dataFolder, "paybot_milestones.db");
    }

    public synchronized void init() {
        try {
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            try (Statement st = connection.createStatement()) {
                st.execute("""
                    CREATE TABLE IF NOT EXISTS paybot_milestones_claimed (
                        player_name TEXT NOT NULL,
                        milestone INTEGER NOT NULL,
                        claimed_at INTEGER NOT NULL,
                        cycle_id INTEGER NOT NULL DEFAULT 1,
                        PRIMARY KEY(player_name, milestone, cycle_id)
                    );
                """);

                st.execute("""
                    CREATE TABLE IF NOT EXISTS paybot_milestones_global (
                        milestone INTEGER NOT NULL,
                        reached_at INTEGER NOT NULL,
                        cycle_id INTEGER NOT NULL DEFAULT 1,
                        PRIMARY KEY(milestone, cycle_id)
                    );
                """);

                st.execute("""
                    CREATE TABLE IF NOT EXISTS paybot_milestones_offline (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_name TEXT NOT NULL,
                        command_str TEXT NOT NULL,
                        milestone INTEGER NOT NULL,
                        cycle_id INTEGER NOT NULL DEFAULT 1
                    );
                """);

                st.execute("""
                    CREATE TABLE IF NOT EXISTS paybot_milestones_meta (
                        meta_key TEXT PRIMARY KEY,
                        meta_value TEXT NOT NULL
                    );
                """);

                st.execute("""
                    CREATE TABLE IF NOT EXISTS paybot_milestones_cycle_totals (
                        key_name TEXT NOT NULL,
                        cycle_id INTEGER NOT NULL,
                        total_amount INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(key_name, cycle_id)
                    );
                """);
            }

            if (getMeta("cycle_id") == null) {
                setMeta("cycle_id", "1");
            }
            if (getMeta("last_reset_time") == null) {
                setMeta("last_reset_time", String.valueOf(System.currentTimeMillis()));
            }

        } catch (Throwable t) {
            PayBotMod.LOGGER.error("[PayBot] Không thể khởi tạo CSDL mốc nạp SQLite: {}", t.getMessage());
        }
    }

    public synchronized int getCurrentCycleId() {
        String val = getMeta("cycle_id");
        try {
            return val != null ? Integer.parseInt(val) : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public synchronized void incrementCycleId() {
        int next = getCurrentCycleId() + 1;
        setMeta("cycle_id", String.valueOf(next));
    }

    public synchronized long getLastResetTime() {
        String val = getMeta("last_reset_time");
        try {
            return val != null ? Long.parseLong(val) : System.currentTimeMillis();
        } catch (NumberFormatException e) {
            return System.currentTimeMillis();
        }
    }

    public synchronized void setLastResetTime(long time) {
        setMeta("last_reset_time", String.valueOf(time));
    }

    private synchronized String getMeta(String key) {
        String sql = "SELECT meta_value FROM paybot_milestones_meta WHERE meta_key = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("meta_value");
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private synchronized void setMeta(String key, String value) {
        String sql = "INSERT INTO paybot_milestones_meta(meta_key, meta_value) VALUES(?, ?) " +
                     "ON CONFLICT(meta_key) DO UPDATE SET meta_value = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.setString(3, value);
            ps.executeUpdate();
        } catch (Throwable ignored) {
        }
    }

    public synchronized Set<Long> getClaimedSingleMilestones(String playerName, int cycleId) {
        Set<Long> result = new HashSet<>();
        String sql = "SELECT milestone FROM paybot_milestones_claimed WHERE player_name = ? AND cycle_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerName.toLowerCase(Locale.ROOT));
            ps.setInt(2, cycleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getLong("milestone"));
                }
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    public synchronized void recordClaimedSingleMilestone(String playerName, long milestone, int cycleId) {
        String sql = "INSERT OR IGNORE INTO paybot_milestones_claimed(player_name, milestone, claimed_at, cycle_id) VALUES(?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerName.toLowerCase(Locale.ROOT));
            ps.setLong(2, milestone);
            ps.setLong(3, System.currentTimeMillis());
            ps.setInt(4, cycleId);
            ps.executeUpdate();
        } catch (Throwable ignored) {
        }
    }

    public synchronized Set<Long> getReachedGlobalMilestones(int cycleId) {
        Set<Long> result = new HashSet<>();
        String sql = "SELECT milestone FROM paybot_milestones_global WHERE cycle_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, cycleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getLong("milestone"));
                }
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    public synchronized boolean recordReachedGlobalMilestone(long milestone, int cycleId) {
        String sql = "INSERT OR IGNORE INTO paybot_milestones_global(milestone, reached_at, cycle_id) VALUES(?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, milestone);
            ps.setLong(2, System.currentTimeMillis());
            ps.setInt(3, cycleId);
            return ps.executeUpdate() > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public synchronized void addOfflineReward(String playerName, String command, long milestone, int cycleId) {
        String sql = "INSERT INTO paybot_milestones_offline(player_name, command_str, milestone, cycle_id) VALUES(?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerName.toLowerCase(Locale.ROOT));
            ps.setString(2, command);
            ps.setLong(3, milestone);
            ps.setInt(4, cycleId);
            ps.executeUpdate();
        } catch (Throwable ignored) {
        }
    }

    public synchronized List<String> pollOfflineRewards(String playerName, int cycleId) {
        List<String> commands = new ArrayList<>();
        String query = "SELECT id, command_str FROM paybot_milestones_offline WHERE player_name = ? AND cycle_id = ?";
        List<Integer> ids = new ArrayList<>();

        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, playerName.toLowerCase(Locale.ROOT));
            ps.setInt(2, cycleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("id"));
                    commands.add(rs.getString("command_str"));
                }
            }
        } catch (Throwable ignored) {
        }

        if (!ids.isEmpty()) {
            String delete = "DELETE FROM paybot_milestones_offline WHERE id = ?";
            try (PreparedStatement ps = connection.prepareStatement(delete)) {
                for (int id : ids) {
                    ps.setInt(1, id);
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (Throwable ignored) {
            }
        }

        return commands;
    }

    public synchronized void recordTopup(String playerName, long amount, int cycleId) {
        if (amount <= 0 || playerName == null || playerName.isBlank()) return;
        String playerKey = "player:" + playerName.toLowerCase(Locale.ROOT);
        String serverKey = "server_total";

        String sql = "INSERT INTO paybot_milestones_cycle_totals(key_name, cycle_id, total_amount) VALUES(?, ?, ?) " +
                     "ON CONFLICT(key_name, cycle_id) DO UPDATE SET total_amount = total_amount + excluded.total_amount";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            // Chu kỳ hiện tại
            ps.setString(1, playerKey);
            ps.setInt(2, cycleId);
            ps.setLong(3, amount);
            ps.executeUpdate();

            // All-time (cycle_id = 0)
            ps.setString(1, playerKey);
            ps.setInt(2, 0);
            ps.setLong(3, amount);
            ps.executeUpdate();

            // Server hiện tại
            ps.setString(1, serverKey);
            ps.setInt(2, cycleId);
            ps.setLong(3, amount);
            ps.executeUpdate();

            // Server all-time (cycle_id = 0)
            ps.setString(1, serverKey);
            ps.setInt(2, 0);
            ps.setLong(3, amount);
            ps.executeUpdate();
        } catch (Throwable t) {
            PayBotMod.LOGGER.warn("[PayBot] Lỗi ghi nhận nạp tiền vào CSDL mốc nạp: {}", t.getMessage());
        }
    }

    public synchronized long getPlayerCycleTotal(String playerName, int cycleId) {
        if (playerName == null || playerName.isBlank()) return 0L;
        String sql = "SELECT total_amount FROM paybot_milestones_cycle_totals WHERE key_name = ? AND cycle_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, "player:" + playerName.toLowerCase(Locale.ROOT));
            ps.setInt(2, cycleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("total_amount");
            }
        } catch (Throwable ignored) {}
        return 0L;
    }

    public synchronized long getServerCycleTotal(int cycleId) {
        String sql = "SELECT total_amount FROM paybot_milestones_cycle_totals WHERE key_name = 'server_total' AND cycle_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, cycleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("total_amount");
            }
        } catch (Throwable ignored) {}
        return 0L;
    }

    public synchronized long getPlayerAllTimeTotal(String playerName) {
        return getPlayerCycleTotal(playerName, 0);
    }

    public synchronized long getServerAllTimeTotal() {
        return getServerCycleTotal(0);
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (Throwable ignored) {
        }
    }
}
