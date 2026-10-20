package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChatInputPromptManager — v6.1.0 (Part 160)
 * TUÂN THỦ RULE 17: Quản lý phiên nhập liệu qua Chat cho Admin khi tùy chỉnh cài đặt qua GUI.
 * <p>
 * ĐẶC TẢ NGHIỆP VỤ:
 * 1. Ngôn ngữ giao tiếp tự nhiên, gần gũi, thân thiện ("Bạn ơi, hãy nhập...").
 * 2. Tin nhắn CHỈ gửi riêng tới Admin (player.sendMessage), không gửi toàn server, không gửi console.
 * 3. Hỗ trợ gõ 'huy' hoặc 'cancel' để hủy thao tác an toàn.
 * 4. Tự động mở lại đúng Container GUI tương ứng sau khi nhập xong hoặc khi hủy.
 */
public class ChatInputPromptManager {

    private final PayBotPlugin plugin;
    private final SetupGuiManager guiManager;
    private final Map<UUID, PromptSession> activeSessions = new ConcurrentHashMap<>();

    public ChatInputPromptManager(PayBotPlugin plugin, SetupGuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    public boolean isAwaitingInput(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    public void startSession(Player player, String promptKey, String fieldName, SetupMenuType returnMenu) {
        if (player == null) return;
        activeSessions.put(player.getUniqueId(), new PromptSession(promptKey, fieldName, returnMenu));
        player.closeInventory();

        player.sendMessage("§e[PayBot Setup] §fBạn ơi, hãy nhập §a" + fieldName + " §fvào chat nha!");
        player.sendMessage("§7(Gõ §c'huy' §7hoặc §c'cancel' §7để quay lại menu cài đặt nè).");
    }

    public void handleInput(Player player, String rawInput) {
        if (player == null) return;
        PromptSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) return;

        String input = rawInput != null ? rawInput.trim() : "";

        // Kiểm tra lệnh hủy
        if ("huy".equalsIgnoreCase(input) || "cancel".equalsIgnoreCase(input)) {
            player.sendMessage("§e[PayBot Setup] §7Đã hủy thao tác nhập. Đang mở lại menu cho bạn nè!");
            guiManager.openMenu(player, session.returnMenu);
            return;
        }

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        File milestoneFile = new File(plugin.getDataFolder(), "milestones.yml");
        File mysqlFile = new File(plugin.getDataFolder(), "mysql.yml");

        // Xử lý từng promptKey cụ thể
        switch (session.promptKey) {
            case "SEPAY_TOKEN":
                CommentPreservingConfigWriter.updateKey(configFile, "sepay.api-token", input);
                plugin.getConfig().set("sepay.api-token", input);
                player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật API Token SePay thành công!");
                break;

            case "SEPAY_STK":
                CommentPreservingConfigWriter.updateKey(configFile, "sepay.account-number", input);
                plugin.getConfig().set("sepay.account-number", input);
                player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật Số tài khoản SePay: §e" + input);
                break;

            case "SEPAY_BANK":
                CommentPreservingConfigWriter.updateKey(configFile, "sepay.bank-name", input);
                plugin.getConfig().set("sepay.bank-name", input);
                player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật Ngân hàng SePay: §e" + input);
                break;

            case "SEPAY_PREFIX":
                BankPrefixValidator.ValidationResult valRes = BankPrefixValidator.validate(input);
                if (!valRes.isValid()) {
                    player.sendMessage("§c[PayBot Setup] ✗ Lỗi: " + valRes.getErrorMessage());
                    // Cho phép nhập lại
                    activeSessions.put(player.getUniqueId(), session);
                    return;
                }
                CommentPreservingConfigWriter.updateKey(configFile, "sepay.prefix", input);
                plugin.getConfig().set("sepay.prefix", input);
                player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật Cú pháp mã nạp thành công: §e" + input);
                break;

            case "CARD_SITE":
                CommentPreservingConfigWriter.updateKey(configFile, "card.site", input);
                plugin.getConfig().set("card.site", input);
                player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật Web gạch thẻ: §e" + input);
                break;

            case "CARD_PARTNER_ID":
                CommentPreservingConfigWriter.updateKey(configFile, "card.partner-id", input);
                plugin.getConfig().set("card.partner-id", input);
                player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật Partner ID gạch thẻ thành công!");
                break;

            case "CARD_PARTNER_KEY":
                CommentPreservingConfigWriter.updateKey(configFile, "card.partner-key", input);
                plugin.getConfig().set("card.partner-key", input);
                player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật Partner Key gạch thẻ thành công!");
                break;

            case "DB_MYSQL_HOST":
                CommentPreservingConfigWriter.updateKey(mysqlFile, "mysql.host", input);
                player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật MySQL Host: §e" + input);
                break;

            case "DB_MYSQL_USER":
                CommentPreservingConfigWriter.updateKey(mysqlFile, "mysql.user", input);
                player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật MySQL User: §e" + input);
                break;

            case "DB_MYSQL_PASS":
                CommentPreservingConfigWriter.updateKey(mysqlFile, "mysql.password", input);
                player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật MySQL Password thành công!");
                break;

            case "DB_MYSQL_NAME":
                CommentPreservingConfigWriter.updateKey(mysqlFile, "mysql.database", input);
                player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật MySQL Database: §e" + input);
                break;

            case "CONTAINER_TITLE_BANK":
                CommentPreservingConfigWriter.updateKey(configFile, "gui.title-bank", input);
                plugin.getConfig().set("gui.title-bank", input);
                player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật Tiêu đề GUI Nạp Bank: " + input);
                break;

            case "CONTAINER_TITLE_CARD":
                CommentPreservingConfigWriter.updateKey(configFile, "gui.title-card", input);
                plugin.getConfig().set("gui.title-card", input);
                player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật Tiêu đề GUI Nạp Thẻ: " + input);
                break;

            case "REWARD_CMD_BANK":
                CommentPreservingConfigWriter.updateKey(configFile, "reward-command-bank", input);
                plugin.getConfig().set("reward-command-bank", input);
                player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật Lệnh thưởng nạp Bank mặc định: §e" + input);
                break;

            case "REWARD_CMD_CARD":
                CommentPreservingConfigWriter.updateKey(configFile, "reward-command-card", input);
                plugin.getConfig().set("reward-command-card", input);
                player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật Lệnh thưởng nạp Thẻ mặc định: §e" + input);
                break;

            case "CUSTOM_RESET_DAYS":
                try {
                    int days = Integer.parseInt(input);
                    if (days <= 0) {
                        player.sendMessage("§c[PayBot Setup] ✗ Số ngày reset chu kỳ phải là số nguyên dương lớn hơn 0!");
                        activeSessions.put(player.getUniqueId(), session);
                        return;
                    }
                    CommentPreservingConfigWriter.updateKey(milestoneFile, "reset-cycle.custom-days", days);
                    player.sendMessage("§a[PayBot Setup] §f✓ Đã đặt chu kỳ reset mốc nạp sau mỗi §e" + days + " ngày§f!");
                } catch (NumberFormatException e) {
                    player.sendMessage("§c[PayBot Setup] ✗ Bạn vui lòng chỉ nhập số nguyên dương nha!");
                    activeSessions.put(player.getUniqueId(), session);
                    return;
                }
                break;

            default:
                player.sendMessage("§e[PayBot Setup] §fĐã ghi nhận giá trị: §a" + input);
                break;
        }

        // Tự động mở lại đúng GUI sau khi hoàn thành
        guiManager.openMenu(player, session.returnMenu);
    }

    public static class PromptSession {
        final String promptKey;
        final String fieldName;
        final SetupMenuType returnMenu;

        public PromptSession(String promptKey, String fieldName, SetupMenuType returnMenu) {
            this.promptKey = promptKey;
            this.fieldName = fieldName;
            this.returnMenu = returnMenu;
        }
    }
}
