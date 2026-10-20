package com.paybot.managers;

import com.paybot.PayBotPlugin;
import com.paybot.utils.SchedulerUtils;
import org.json.simple.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

/**
 * DiscordIpcClient — v7.0.0 (Part 161)
 * TUÂN THỦ RULE 17: Class độc lập phụ trách giao tiếp IPC cục bộ giữa Minecraft Server và Bot Discord.
 * Không phụ thuộc thư viện ngoài, tương thích 100% Bukkit/Spigot/Paper/Purpur/Folia.
 */
public class DiscordIpcClient {

    private final PayBotPlugin plugin;

    public DiscordIpcClient(PayBotPlugin plugin) {
        this.plugin = plugin;
    }

    private String getBaseUrl() {
        int port = plugin.getConfig().getInt("discord.ipc-port", 24733);
        return "http://127.0.0.1:" + port + "/internal";
    }

    /**
     * Kiểm tra bất đồng bộ xem Bot Discord có đang chạy trên localhost không.
     */
    public void pingAsync(Consumer<Boolean> callback) {
        SchedulerUtils.runAsync(plugin, () -> {
            boolean online = false;
            try {
                URL url = new URL(getBaseUrl() + "/status");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(2500);
                conn.setReadTimeout(2500);
                int code = conn.getResponseCode();
                online = (code == 200);
                conn.disconnect();
            } catch (Exception ignored) {
                online = false;
            }
            final boolean result = online;
            if (callback != null) {
                SchedulerUtils.runSync(plugin, () -> callback.accept(result));
            }
        });
    }

    /**
     * Gửi thông báo nạp tiền thành công tới Bot Discord để in bill vào kênh Discord.
     */
    public void notifyPaymentAsync(String playerName, String txType, int amount, String code) {
        if (!plugin.getConfig().getBoolean("discord.enabled", false)) return;

        SchedulerUtils.runAsync(plugin, () -> {
            try {
                URL url = new URL(getBaseUrl() + "/notify-payment");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                conn.setDoOutput(true);

                JSONObject payload = new JSONObject();
                payload.put("player_name", playerName);
                payload.put("tx_type", txType);
                payload.put("amount", amount);
                payload.put("code", code);

                byte[] out = payload.toJSONString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(out);
                }

                int respCode = conn.getResponseCode();
                conn.disconnect();
                if (respCode == 200 && plugin.getConfig().getBoolean("debug-mode", false)) {
                    plugin.getLogger().info("[DiscordIpcClient] Đã gửi thông báo giao dịch " + code + " sang Bot Discord thành công.");
                }
            } catch (Exception e) {
                if (plugin.getConfig().getBoolean("debug-mode", false)) {
                    plugin.getLogger().warning("[DiscordIpcClient] Không thể kết nối tới Bot Discord: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Đồng bộ cấu hình mới sang Bot Discord thời gian thực.
     */
    public void syncConfigAsync(Map<String, Object> settings) {
        if (!plugin.getConfig().getBoolean("discord.enabled", false)) return;

        SchedulerUtils.runAsync(plugin, () -> {
            try {
                URL url = new URL(getBaseUrl() + "/sync-from-game");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                conn.setDoOutput(true);

                JSONObject payload = new JSONObject();
                payload.put("source", "MINECRAFT");
                JSONObject settingsObj = new JSONObject();
                if (settings != null) {
                    settingsObj.putAll(settings);
                }
                payload.put("settings", settingsObj);

                byte[] out = payload.toJSONString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(out);
                }

                int respCode = conn.getResponseCode();
                conn.disconnect();
                if (respCode == 200 && plugin.getConfig().getBoolean("debug-mode", false)) {
                    plugin.getLogger().info("[DiscordIpcClient] Đã đồng bộ cấu hình sang Bot Discord thành công.");
                }
            } catch (Exception e) {
                if (plugin.getConfig().getBoolean("debug-mode", false)) {
                    plugin.getLogger().warning("[DiscordIpcClient] Không thể đồng bộ cấu hình sang Bot Discord: " + e.getMessage());
                }
            }
        });
    }
}
