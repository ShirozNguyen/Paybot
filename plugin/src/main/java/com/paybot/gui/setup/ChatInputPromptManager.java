package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChatInputPromptManager — v7.0.0 (Part 161)
 * TUÂN THỦ RULE 17: Quản lý phiên nhập liệu qua Chat cho Admin khi tùy chỉnh cài đặt qua GUI.
 * <p>
 * ĐẶC TẢ NGHIỆP VỤ:
 * 1. Ngôn ngữ giao tiếp tự nhiên, gần gũi, thân thiện ("Bạn ơi, hãy nhập...").
 * 2. Tin nhắn CHỈ gửi riêng tới Admin (player.sendMessage), không gửi toàn server, không gửi console.
 * 3. Hỗ trợ gõ 'huy' hoặc 'cancel' để hủy thao tác an toàn.
 * 4. Tự động mở lại đúng Container GUI tương ứng sau khi nhập xong hoặc khi hủy.
 * 5. Tại bước nhập API Token SePay: Đính kèm clickable text "Chưa có tài khoản SePay? Tạo tài khoản tại đây"
 *    với chữ "tại đây" màu vàng gạch chân, click chuột mở thẳng link đăng ký đối tác chính thức.
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
        if ("SEPAY_TOKEN".equals(promptKey)) {
            sendSepayPartnerLink(player);
        }
        player.sendMessage("§7(Gõ §c'huy' §7hoặc §c'cancel' §7để quay lại menu cài đặt nè).");
    }

    private void sendSepayPartnerLink(Player player) {
        String partnerUrl = "https://my.sepay.vn/register?gcid=vrewlwez";
        try {
            net.kyori.adventure.text.Component msg = net.kyori.adventure.text.Component.text("§fChưa có tài khoản SePay? Tạo tài khoản ")
                    .append(net.kyori.adventure.text.Component.text("tại đây")
                            .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                            .decorate(net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(partnerUrl))
                            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                    net.kyori.adventure.text.Component.text("§eBấm để mở trang đăng ký SePay đối tác"))))
                    .append(net.kyori.adventure.text.Component.text("§f."));
            player.sendMessage(msg);
        } catch (Throwable t) {
            try {
                net.md_5.bungee.api.chat.TextComponent base = new net.md_5.bungee.api.chat.TextComponent("§fChưa có tài khoản SePay? Tạo tài khoản ");
                net.md_5.bungee.api.chat.TextComponent link = new net.md_5.bungee.api.chat.TextComponent("tại đây");
                link.setColor(net.md_5.bungee.api.ChatColor.YELLOW);
                link.setUnderlined(true);
                link.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, partnerUrl));
                link.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                        new net.md_5.bungee.api.chat.ComponentBuilder("§eBấm để mở trang đăng ký SePay đối tác").create()));
                base.addExtra(link);
                base.addExtra(new net.md_5.bungee.api.chat.TextComponent("§f."));
                player.spigot().sendMessage(base);
            } catch (Throwable t2) {
                player.sendMessage("§fChưa có tài khoản SePay? Tạo tài khoản §e§ntại đây§r: §b" + partnerUrl);
            }
        }
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
                CommentPreservingConfigWriter.updateKey(configFile, "sepay-api.api-token", input);
                CommentPreservingConfigWriter.updateKey(configFile, "sepay-api.enabled", true);
                CommentPreservingConfigWriter.updateKey(configFile, "sepay.configured", true);
                plugin.getConfig().set("sepay.api-token", input);
                plugin.getConfig().set("sepay-api.api-token", input);
                plugin.getConfig().set("sepay-api.enabled", true);
                plugin.getConfig().set("sepay.configured", true);
                player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật API Token SePay thành công!");
                break;

            case "DISCORD_TOKEN":
                CommentPreservingConfigWriter.updateKey(configFile, "discord.bot-token", input);
                plugin.getConfig().set("discord.bot-token", input);
                player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật Bot Token Discord thành công!");
                break;

            case "DISCORD_GUILD_ID":
                CommentPreservingConfigWriter.updateKey(configFile, "discord.guild-id", input);
                plugin.getConfig().set("discord.guild-id", input);
                player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật Guild ID Discord: §e" + input);
                break;

            case "DISCORD_CHANNEL_ID":
                CommentPreservingConfigWriter.updateKey(configFile, "discord.notify-channel-id", input);
                plugin.getConfig().set("discord.notify-channel-id", input);
                player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật Kênh thông báo Discord: §e" + input);
                break;

            case "DISCORD_IPC_PORT":
                try {
                    int port = Integer.parseInt(input);
                    if (port < 1024 || port > 65535) {
                        player.sendMessage("§c[PayBot Setup] ✗ Port IPC phải nằm trong khoảng 1024 - 65535!");
                        activeSessions.put(player.getUniqueId(), session);
                        return;
                    }
                    CommentPreservingConfigWriter.updateKey(configFile, "discord.ipc-port", port);
                    plugin.getConfig().set("discord.ipc-port", port);
                    player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật Port IPC: §e" + port);
                } catch (NumberFormatException e) {
                    player.sendMessage("§c[PayBot Setup] ✗ Bạn vui lòng chỉ nhập số nguyên cho Port nha!");
                    activeSessions.put(player.getUniqueId(), session);
                    return;
                }
                break;

            case "SEPAY_STK":
                CommentPreservingConfigWriter.updateKey(configFile, "sepay.account-number", input);
                CommentPreservingConfigWriter.updateKey(configFile, "sepay.bank-account", input);
                CommentPreservingConfigWriter.updateKey(configFile, "sepay-api.bank-account-number", input);
                plugin.getConfig().set("sepay.account-number", input);
                plugin.getConfig().set("sepay.bank-account", input);
                plugin.getConfig().set("sepay-api.bank-account-number", input);
                player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật Số tài khoản SePay: §e" + input);
                break;

            case "SEPAY_BANK":
                CommentPreservingConfigWriter.updateKey(configFile, "sepay.bank-name", input);
                CommentPreservingConfigWriter.updateKey(configFile, "sepay-api.bank-short-name", input);
                plugin.getConfig().set("sepay.bank-name", input);
                plugin.getConfig().set("sepay-api.bank-short-name", input);
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
                CommentPreservingConfigWriter.updateKey(configFile, "card.partner-url", input);
                CommentPreservingConfigWriter.updateKey(configFile, "card-api.site", input);
                plugin.getConfig().set("card.site", input);
                plugin.getConfig().set("card.partner-url", input);
                plugin.getConfig().set("card-api.site", input);
                player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật Web gạch thẻ: §e" + input);
                break;

            case "CARD_PARTNER_ID":
                CommentPreservingConfigWriter.updateKey(configFile, "card.partner-id", input);
                CommentPreservingConfigWriter.updateKey(configFile, "card-api.partner-id", input);
                CommentPreservingConfigWriter.updateKey(configFile, "card-api.configured", true);
                plugin.getConfig().set("card.partner-id", input);
                plugin.getConfig().set("card-api.partner-id", input);
                plugin.getConfig().set("card-api.configured", true);
                player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật Partner ID gạch thẻ thành công!");
                break;

            case "CARD_PARTNER_KEY":
                CommentPreservingConfigWriter.updateKey(configFile, "card.partner-key", input);
                CommentPreservingConfigWriter.updateKey(configFile, "card-api.partner-key", input);
                CommentPreservingConfigWriter.updateKey(configFile, "card-api.configured", true);
                plugin.getConfig().set("card.partner-key", input);
                plugin.getConfig().set("card-api.partner-key", input);
                plugin.getConfig().set("card-api.configured", true);
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

            case "COOLDOWN_QR_CUSTOM":
                try {
                    int sec = Integer.parseInt(input);
                    if (sec <= 0) {
                        player.sendMessage("§c[PayBot Setup] ✗ Số giây Cooldown phải là số nguyên dương > 0!");
                        activeSessions.put(player.getUniqueId(), session);
                        return;
                    }
                    com.paybot.managers.PlayerCooldownManager.getInstance().setQrMapSeconds(sec);
                    CommentPreservingConfigWriter.updateKey(configFile, "cooldown.qr-map-seconds", sec);
                    plugin.getConfig().set("cooldown.qr-map-seconds", sec);
                    player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật Cooldown tạo QR Map: §e" + sec + " giây");
                } catch (NumberFormatException e) {
                    player.sendMessage("§c[PayBot Setup] ✗ Bạn vui lòng chỉ nhập số nguyên dương nha!");
                    activeSessions.put(player.getUniqueId(), session);
                    return;
                }
                break;

            case "COOLDOWN_CARD_CUSTOM":
                try {
                    int sec = Integer.parseInt(input);
                    if (sec <= 0) {
                        player.sendMessage("§c[PayBot Setup] ✗ Số giây Cooldown phải là số nguyên dương > 0!");
                        activeSessions.put(player.getUniqueId(), session);
                        return;
                    }
                    com.paybot.managers.PlayerCooldownManager.getInstance().setCardSubmitSeconds(sec);
                    CommentPreservingConfigWriter.updateKey(configFile, "cooldown.card-submit-seconds", sec);
                    plugin.getConfig().set("cooldown.card-submit-seconds", sec);
                    player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật Cooldown gửi thẻ cào: §e" + sec + " giây");
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

        if (session.promptKey.startsWith("REWARD_DENOM_BANK_")) {
            String denomStr = session.promptKey.substring("REWARD_DENOM_BANK_".length());
            String key = "denom-rewards-bank." + denomStr + ".cmd";
            CommentPreservingConfigWriter.updateKey(configFile, key, input);
            plugin.getConfig().set(key, input);
            player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật Lệnh thưởng Bank cho mệnh giá §e" + denomStr + " VNĐ§f: §b" + input);
        } else if (session.promptKey.startsWith("REWARD_DENOM_CARD_")) {
            String denomStr = session.promptKey.substring("REWARD_DENOM_CARD_".length());
            String key = "denom-rewards-card." + denomStr + ".cmd";
            CommentPreservingConfigWriter.updateKey(configFile, key, input);
            plugin.getConfig().set(key, input);
            player.sendMessage("§a[PayBot Setup] §f✓ Đã cập nhật Lệnh thưởng Thẻ Cào cho mệnh giá §e" + denomStr + " VNĐ§f: §b" + input);
        }

        // Tự động mở lại đúng GUI sau khi hoàn thành
        com.paybot.utils.SchedulerUtils.runSync(plugin, () -> guiManager.openMenu(player, session.returnMenu));
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
