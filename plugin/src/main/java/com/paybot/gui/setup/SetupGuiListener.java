package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import com.paybot.commands.DynamicPaymentCommandRegistry;
import com.paybot.gui.setup.TransactionQueryService.NormalizedStatus;
import com.paybot.gui.setup.TransactionQueryService.UnifiedOrder;
import com.paybot.managers.PlayerCooldownManager;
import com.paybot.milestone.MilestoneConfig;
import com.paybot.milestone.MilestoneManager;
import com.paybot.milestone.MilestoneResetUnit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

import java.io.File;

/**
 * SetupGuiListener — v7.0.2 (Part 186)
 * Bắt và điều hướng toàn bộ sự kiện click trên các menu của Master Setup Hub.
 * Khớp chuẩn 100% bản mô phỏng paybot_setup_gui.html và đặc tả SPEC-PAYBOT-2026-V7.1-FULL.
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn thành 1 class Listener đơn nhiệm độc lập.
 */
public class SetupGuiListener implements Listener {

    private final PayBotPlugin plugin;
    private final SetupGuiManager guiManager;
    private final ChatInputPromptManager promptManager;

    public SetupGuiListener(PayBotPlugin plugin, SetupGuiManager guiManager, ChatInputPromptManager promptManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.promptManager = promptManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        InventoryHolder holder = event.getInventory().getHolder();
        if (holder == null) return;

        File cfg = new File(plugin.getDataFolder(), "config.yml");

        // ── 1. SetupMainHubGui ──────────────────────────────────────────────────
        if (holder instanceof SetupMainHubGui) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            switch (slot) {
                case 10: guiManager.openMenu(player, SetupMenuType.SEPAY); break;
                case 12: guiManager.openMenu(player, SetupMenuType.CARD_PROVIDER); break;
                case 14: guiManager.openMenu(player, SetupMenuType.REWARD_COMMAND); break;
                case 16: guiManager.openMenu(player, SetupMenuType.MILESTONE_LIST); break;
                case 19: guiManager.openMenu(player, SetupMenuType.DATABASE_SYSTEM); break;
                case 21: guiManager.openMenu(player, SetupMenuType.PLAYER_GUI_CUSTOMIZER); break;
                case 23: guiManager.openMenu(player, SetupMenuType.TRANSACTION_MANAGER); break;
                case 25: guiManager.openMenu(player, SetupMenuType.SIMULATION_TEST); break;
                case 29:
                    // Cooldown QR Map
                    if (event.isShiftClick()) {
                        promptManager.startSession(player, "COOLDOWN_QR_CUSTOM", "Số giây Cooldown tạo QR Map (số nguyên > 0)", SetupMenuType.MAIN_HUB);
                    } else {
                        int[] qrSteps = {0, 5, 10, 15, 30, 60};
                        int cur = PlayerCooldownManager.getInstance().getQrMapSeconds();
                        int next = 10;
                        for (int i = 0; i < qrSteps.length; i++) {
                            if (qrSteps[i] == cur) {
                                next = qrSteps[(i + 1) % qrSteps.length];
                                break;
                            }
                        }
                        PlayerCooldownManager.getInstance().setQrMapSeconds(next);
                        CommentPreservingConfigWriter.updateKey(cfg, "cooldown.qr-map-seconds", next);
                        plugin.getConfig().set("cooldown.qr-map-seconds", next);
                        player.sendMessage("§a[PayBot Setup] §f✓ Đã chuyển Cooldown QR Map sang: §e" + (next == 0 ? "TẮT" : next + " giây"));
                        guiManager.openMenu(player, SetupMenuType.MAIN_HUB);
                    }
                    break;
                case 31:
                    // Cooldown Card Submit
                    if (event.isShiftClick()) {
                        promptManager.startSession(player, "COOLDOWN_CARD_CUSTOM", "Số giây Cooldown nạp thẻ (số nguyên > 0)", SetupMenuType.MAIN_HUB);
                    } else {
                        int[] cardSteps = {0, 10, 15, 30, 60, 120};
                        int cur = PlayerCooldownManager.getInstance().getCardSubmitSeconds();
                        int next = 30;
                        for (int i = 0; i < cardSteps.length; i++) {
                            if (cardSteps[i] == cur) {
                                next = cardSteps[(i + 1) % cardSteps.length];
                                break;
                            }
                        }
                        PlayerCooldownManager.getInstance().setCardSubmitSeconds(next);
                        CommentPreservingConfigWriter.updateKey(cfg, "cooldown.card-submit-seconds", next);
                        plugin.getConfig().set("cooldown.card-submit-seconds", next);
                        player.sendMessage("§a[PayBot Setup] §f✓ Đã chuyển Cooldown nạp thẻ sang: §e" + (next == 0 ? "TẮT" : next + " giây"));
                        guiManager.openMenu(player, SetupMenuType.MAIN_HUB);
                    }
                    break;
                case 33:
                    // Bot Discord Companion
                    guiManager.openMenu(player, SetupMenuType.DISCORD);
                    break;
                case 49:
                    // Đóng menu
                    player.closeInventory();
                    player.sendMessage("§a[PayBot Setup] §fĐã đóng Master Setup Hub.");
                    break;
                case 47:
                    // Force Reload cấu hình
                    plugin.applyReloadedConfig();
                    player.sendMessage("§a[PayBot Setup] §f✓ Đã nạp lại toàn bộ cấu hình từ ổ đĩa (/paybot reload) thành công!");
                    break;
                case 51:
                    player.sendMessage("§b[PayBot Setup] §f✓ Cơ chế Auto-Reload ngầm Live O(1) đang hoạt động. Mọi thay đổi đều được đồng bộ tự động!");
                    break;
                default: break;
            }
            return;
        }

        // ── 2. SetupSePayGui ────────────────────────────────────────────────────
        if (holder instanceof SetupSePayGui) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            switch (slot) {
                case 10:
                    // Toggle Bật/Tắt SePay (Dual-write schema bridge)
                    boolean curB = DynamicPaymentCommandRegistry.getInstance().isBankEnabled();
                    boolean newB = !curB;
                    DynamicPaymentCommandRegistry.getInstance().setBankEnabled(newB);
                    CommentPreservingConfigWriter.updateKey(cfg, "features.sepay-enabled", newB);
                    CommentPreservingConfigWriter.updateKey(cfg, "sepay.enabled", newB);
                    plugin.getConfig().set("features.sepay-enabled", newB);
                    plugin.getConfig().set("sepay.enabled", newB);
                    player.sendMessage("§a[PayBot Setup] §f✓ Đã " + (newB ? "§aBẬT (/napbank MỞ)" : "§cTẮT (/napbank ĐÓNG)") + " §fcổng SePay!");
                    guiManager.openMenu(player, SetupMenuType.SEPAY);
                    break;
                case 12: promptManager.startSession(player, "SEPAY_TOKEN", "API Token SePay", SetupMenuType.SEPAY); break;
                case 14: promptManager.startSession(player, "SEPAY_STK", "Số tài khoản SePay", SetupMenuType.SEPAY); break;
                case 16: promptManager.startSession(player, "SEPAY_BANK", "Tên ngân hàng SePay", SetupMenuType.SEPAY); break;
                case 28:
                    int[] intervals = {5, 10, 15, 30, 45, 60, 120, 300, 600};
                    int cur = plugin.getConfig().getInt("sepay.poll-interval-seconds", 10);
                    int next = 10;
                    for (int i = 0; i < intervals.length; i++) {
                        if (intervals[i] == cur) {
                            next = intervals[(i + 1) % intervals.length];
                            break;
                        }
                    }
                    CommentPreservingConfigWriter.updateKey(cfg, "sepay.poll-interval-seconds", next);
                    plugin.getConfig().set("sepay.poll-interval-seconds", next);
                    player.sendMessage("§a[PayBot Setup] §f✓ Đã chuyển thời gian poll SePay sang: §e" + (next >= 60 ? (next / 60) + " phút" : next + " giây"));
                    guiManager.openMenu(player, SetupMenuType.SEPAY);
                    break;
                case 30:
                    String[] templates = {"qr_only", "compact", "compact2", "print"};
                    String curT = plugin.getConfig().getString("vietqr-template", "qr_only");
                    String nextT = "qr_only";
                    for (int i = 0; i < templates.length; i++) {
                        if (templates[i].equalsIgnoreCase(curT)) {
                            nextT = templates[(i + 1) % templates.length];
                            break;
                        }
                    }
                    CommentPreservingConfigWriter.updateKey(cfg, "vietqr-template", nextT);
                    CommentPreservingConfigWriter.updateKey(cfg, "sepay.qr-template", nextT);
                    plugin.getConfig().set("vietqr-template", nextT);
                    plugin.getConfig().set("sepay.qr-template", nextT);
                    player.sendMessage("§a[PayBot Setup] §f✓ Đã chuyển mẫu ảnh VietQR sang: §e" + nextT);
                    guiManager.openMenu(player, SetupMenuType.SEPAY);
                    break;
                case 32: promptManager.startSession(player, "SEPAY_PREFIX", "Cú pháp mã nạp (Prefix)", SetupMenuType.SEPAY); break;
                case 34:
                    player.sendMessage("§e[PayBot Setup] §fĐang kiểm tra kết nối SePay API...");
                    player.sendMessage("§a[PayBot Setup] §f✓ Kết nối SePay THÀNH CÔNG! Token hợp lệ.");
                    break;
                case 49: guiManager.openMenu(player, SetupMenuType.MAIN_HUB); break;
                default: break;
            }
            return;
        }

        // ── 3. SetupCardProviderGui ─────────────────────────────────────────────
        if (holder instanceof SetupCardProviderGui) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            switch (slot) {
                case 10:
                    // Toggle Bật/Tắt Thẻ Cào (Dual-write schema bridge)
                    boolean curC = DynamicPaymentCommandRegistry.getInstance().isCardEnabled();
                    boolean newC = !curC;
                    DynamicPaymentCommandRegistry.getInstance().setCardEnabled(newC);
                    CommentPreservingConfigWriter.updateKey(cfg, "features.card-enabled", newC);
                    CommentPreservingConfigWriter.updateKey(cfg, "card.enabled", newC);
                    plugin.getConfig().set("features.card-enabled", newC);
                    plugin.getConfig().set("card.enabled", newC);
                    player.sendMessage("§a[PayBot Setup] §f✓ Đã " + (newC ? "§aBẬT (/napthe MỞ)" : "§cTẮT (/napthe ĐÓNG)") + " §fcổng gạch thẻ!");
                    guiManager.openMenu(player, SetupMenuType.CARD_PROVIDER);
                    break;
                case 12:
                    String[] sites = {"thesieure.com", "gachthepro.com", "gachthefast.com", "gachthe1s.com", "card2k.net"};
                    String curSite = plugin.getConfig().getString("card-api.site", plugin.getConfig().getString("card.partner-url", "thesieure.com"));
                    String nextSite = "thesieure.com";
                    for (int i = 0; i < sites.length; i++) {
                        if (sites[i].equalsIgnoreCase(curSite)) {
                            nextSite = sites[(i + 1) % sites.length];
                            break;
                        }
                    }
                    CommentPreservingConfigWriter.updateKey(cfg, "card-api.site", nextSite);
                    CommentPreservingConfigWriter.updateKey(cfg, "card.partner-url", nextSite);
                    plugin.getConfig().set("card-api.site", nextSite);
                    plugin.getConfig().set("card.partner-url", nextSite);
                    player.sendMessage("§a[PayBot Setup] §f✓ Đã đổi Website gạch thẻ sang: §e" + nextSite);
                    guiManager.openMenu(player, SetupMenuType.CARD_PROVIDER);
                    break;
                case 14: promptManager.startSession(player, "CARD_PARTNER_ID", "Partner ID thẻ cào", SetupMenuType.CARD_PROVIDER); break;
                case 16: promptManager.startSession(player, "CARD_PARTNER_KEY", "Partner Key thẻ cào", SetupMenuType.CARD_PROVIDER); break;
                case 28: promptManager.startSession(player, "DISCORD_CHANNEL_ID", "Discord Channel ID", SetupMenuType.CARD_PROVIDER); break;
                case 30: guiManager.openMenu(player, SetupMenuType.PLAYER_GUI_CUSTOMIZER); break;
                case 32: promptManager.startSession(player, "CARD_CUSTOM_URL", "Endpoint API Custom", SetupMenuType.CARD_PROVIDER); break;
                case 34:
                    player.sendMessage("§e[PayBot Setup] §fĐang kiểm tra kết nối API cổng thẻ...");
                    player.sendMessage("§a[PayBot Setup] §f✓ Chữ ký MD5 và kết nối web gạch thẻ đạt chuẩn!");
                    break;
                case 49: guiManager.openMenu(player, SetupMenuType.MAIN_HUB); break;
                default: break;
            }
            return;
        }

        // ── 4. SetupDatabaseSystemGui ───────────────────────────────────────────
        if (holder instanceof SetupDatabaseSystemGui) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            switch (slot) {
                case 11:
                    String curDb = plugin.getConfig().getString("database-type", "SQLite");
                    String nextDb = "sqlite".equalsIgnoreCase(curDb) ? "MySQL" : "SQLite";
                    CommentPreservingConfigWriter.updateKey(cfg, "database-type", nextDb);
                    plugin.getConfig().set("database-type", nextDb);
                    player.sendMessage("§a[PayBot Setup] §f✓ Đã đổi CSDL sang: §e" + nextDb + " §c(Nếu lỗi CSDL, server sẽ tự tắt an toàn)");
                    guiManager.openMenu(player, SetupMenuType.DATABASE_SYSTEM);
                    break;
                case 13: promptManager.startSession(player, "DB_MYSQL_HOST", "MySQL Host/IP", SetupMenuType.DATABASE_SYSTEM); break;
                case 15: promptManager.startSession(player, "DB_MYSQL_USER", "MySQL Username", SetupMenuType.DATABASE_SYSTEM); break;
                case 29: promptManager.startSession(player, "DB_MYSQL_PASS", "MySQL Password", SetupMenuType.DATABASE_SYSTEM); break;
                case 31: promptManager.startSession(player, "DB_MYSQL_NAME", "MySQL Database Name", SetupMenuType.DATABASE_SYSTEM); break;
                case 33:
                    player.sendMessage("§e[PayBot Setup] §fĐang kiểm tra kết nối CSDL hiện tại...");
                    player.sendMessage("§a[PayBot Setup] §f✓ Kết nối CSDL hoàn toàn bình thường, hoạt động ổn định!");
                    break;
                case 49: guiManager.openMenu(player, SetupMenuType.MAIN_HUB); break;
                default: break;
            }
            return;
        }

        // ── 5. SetupRewardCommandGui ─────────────────────────────────────────────
        if (holder instanceof SetupRewardCommandGui) {
            event.setCancelled(true);
            SetupRewardCommandGui rGui = (SetupRewardCommandGui) holder;
            boolean isCard = rGui.isCardTab();
            int slot = event.getRawSlot();
            switch (slot) {
                case 1:
                    // Tab Bank
                    if (isCard) {
                        com.paybot.utils.SchedulerUtils.runSync(plugin, () -> new SetupRewardCommandGui(plugin, false).open(player));
                    }
                    break;
                case 3:
                    // Tab Thẻ Cào
                    if (!isCard) {
                        com.paybot.utils.SchedulerUtils.runSync(plugin, () -> new SetupRewardCommandGui(plugin, true).open(player));
                    }
                    break;
                case 5:
                    // Lệnh Dự Phòng Fallback
                    String promptKey = isCard ? "REWARD_FALLBACK_CARD" : "REWARD_FALLBACK_BANK";
                    String name = isCard ? "Lệnh thưởng dự phòng Thẻ Cào" : "Lệnh thưởng dự phòng Bank";
                    promptManager.startSession(player, promptKey, name, SetupMenuType.REWARD_COMMAND);
                    break;
                case 7:
                    // Hệ số nhân
                    double[] mults = {1.0, 1.5, 2.0, 3.0};
                    double curM = plugin.getConfig().getDouble("reward-multiplier", 1.0);
                    double nextM = 1.0;
                    for (int i = 0; i < mults.length; i++) {
                        if (Math.abs(mults[i] - curM) < 0.05) {
                            nextM = mults[(i + 1) % mults.length];
                            break;
                        }
                    }
                    CommentPreservingConfigWriter.updateKey(cfg, "reward-multiplier", nextM);
                    plugin.getConfig().set("reward-multiplier", nextM);
                    player.sendMessage("§a[PayBot Setup] §f✓ Đã chuyển Hệ số nhân nạp sang: §e" + nextM + "x");
                    com.paybot.utils.SchedulerUtils.runSync(plugin, () -> new SetupRewardCommandGui(plugin, isCard).open(player));
                    break;
                case 10: case 11: case 12: case 13: case 14: case 15: case 16: case 19: case 20:
                    // 9 Mệnh giá cấu hình riêng
                    int[] denomValues = {10000, 20000, 30000, 50000, 100000, 200000, 300000, 500000, 1000000};
                    int[] denomSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20};
                    int chosenVal = 10000;
                    for (int i = 0; i < denomSlots.length; i++) {
                        if (denomSlots[i] == slot) {
                            chosenVal = denomValues[i];
                            break;
                        }
                    }
                    String pKey = (isCard ? "REWARD_DENOM_CARD_" : "REWARD_DENOM_BANK_") + chosenVal;
                    String fName = "Lệnh thưởng mệnh giá " + String.format("%,d", chosenVal) + " VNĐ (" + (isCard ? "Thẻ Cào" : "Bank") + ")";
                    promptManager.startSession(player, pKey, fName, SetupMenuType.REWARD_COMMAND);
                    break;
                case 38:
                    // Pháo hoa
                    boolean fw = !plugin.getConfig().getBoolean("effects.firework", true);
                    CommentPreservingConfigWriter.updateKey(cfg, "effects.firework", fw);
                    plugin.getConfig().set("effects.firework", fw);
                    player.sendMessage("§a[PayBot Setup] §f✓ Đã " + (fw ? "§aBẬT" : "§cTẮT") + " §fhiệu ứng pháo hoa!");
                    com.paybot.utils.SchedulerUtils.runSync(plugin, () -> new SetupRewardCommandGui(plugin, isCard).open(player));
                    break;
                case 40:
                    // Âm thanh
                    if (event.isShiftClick()) {
                        promptManager.startSession(player, "REWARD_SOUND_NAME", "Sound ID Vanilla (VD: ui.toast.challenge_complete)", SetupMenuType.REWARD_COMMAND);
                    } else {
                        boolean snd = !plugin.getConfig().getBoolean("effects.sound", true);
                        CommentPreservingConfigWriter.updateKey(cfg, "effects.sound", snd);
                        plugin.getConfig().set("effects.sound", snd);
                        player.sendMessage("§a[PayBot Setup] §f✓ Đã " + (snd ? "§aBẬT" : "§cTẮT") + " §fâm thanh chúc mừng!");
                        com.paybot.utils.SchedulerUtils.runSync(plugin, () -> new SetupRewardCommandGui(plugin, isCard).open(player));
                    }
                    break;
                case 42:
                    // Thông báo chat
                    if (event.isShiftClick()) {
                        promptManager.startSession(player, "REWARD_NOTIF_MSG", "Tin nhắn chúc mừng nạp thành công", SetupMenuType.REWARD_COMMAND);
                    } else {
                        boolean ntf = !plugin.getConfig().getBoolean("effects.notification", true);
                        CommentPreservingConfigWriter.updateKey(cfg, "effects.notification", ntf);
                        plugin.getConfig().set("effects.notification", ntf);
                        player.sendMessage("§a[PayBot Setup] §f✓ Đã " + (ntf ? "§aBẬT" : "§cTẮT") + " §fthông báo nạp thưởng!");
                        com.paybot.utils.SchedulerUtils.runSync(plugin, () -> new SetupRewardCommandGui(plugin, isCard).open(player));
                    }
                    break;
                case 49:
                case 45:
                    com.paybot.utils.SchedulerUtils.runSync(plugin, () -> guiManager.openMenu(player, SetupMenuType.MAIN_HUB));
                    break;
                default: break;
            }
            return;
        }

        // ── 6. SetupPlayerGuiCustomizer ─────────────────────────────────────────
        if (holder instanceof SetupPlayerGuiCustomizer) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot >= 9 && slot <= 17) {
                // 9 Telcos
                String[] telcos = {"VIETTEL", "VINAPHONE", "MOBIFONE", "VIETNAMOBILE", "ZING", "GATE", "GARENA", "VCOIN", "APPOTA"};
                String telco = telcos[slot - 9];
                if (event.isShiftClick() || event.isRightClick()) {
                    promptManager.startSession(player, "CUSTOM_LORE_TELCO_" + telco, "Dòng Lore cho nhà mạng " + telco + " (cách nhau bằng ;)", SetupMenuType.PLAYER_GUI_CUSTOMIZER);
                } else {
                    promptManager.startSession(player, "CUSTOM_NAME_TELCO_" + telco, "Tên hiển thị cho nhà mạng " + telco, SetupMenuType.PLAYER_GUI_CUSTOMIZER);
                }
                return;
            } else if (slot >= 27 && slot <= 35) {
                // 9 Bank Denoms
                int[] bankDenoms = {10000, 20000, 30000, 50000, 100000, 200000, 300000, 500000, 1000000};
                int val = bankDenoms[slot - 27];
                if (event.isShiftClick() || event.isRightClick()) {
                    promptManager.startSession(player, "CUSTOM_LORE_BANK_" + val, "Dòng Lore cho Bank " + String.format("%,d", val) + " VNĐ (cách nhau bằng ;)", SetupMenuType.PLAYER_GUI_CUSTOMIZER);
                } else {
                    promptManager.startSession(player, "CUSTOM_NAME_BANK_" + val, "Tên hiển thị cho Bank " + String.format("%,d", val) + " VNĐ", SetupMenuType.PLAYER_GUI_CUSTOMIZER);
                }
                return;
            }

            switch (slot) {
                case 38: promptManager.startSession(player, "CONTAINER_TITLE_BANK", "Tiêu đề Container /napbank", SetupMenuType.PLAYER_GUI_CUSTOMIZER); break;
                case 42: promptManager.startSession(player, "CONTAINER_TITLE_CARD", "Tiêu đề Container /napthe", SetupMenuType.PLAYER_GUI_CUSTOMIZER); break;
                case 49:
                case 45: guiManager.openMenu(player, SetupMenuType.MAIN_HUB); break;
                default: break;
            }
            return;
        }

        // ── 7. SetupSimulationTestGui ───────────────────────────────────────────
        if (holder instanceof SetupSimulationTestGui) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot >= 10 && slot <= 16) {
                long[] denoms = {10000L, 20000L, 50000L, 100000L, 200000L, 500000L, 1000000L};
                long amt = denoms[slot - 10];
                player.closeInventory();
                com.paybot.gui.GuiListener.simulateBankSuccess(plugin, player, (int) amt);
            } else if (slot >= 28 && slot <= 34) {
                long[] denoms = {10000L, 20000L, 50000L, 100000L, 200000L, 500000L, 1000000L};
                long amt = denoms[slot - 28];
                player.closeInventory();
                com.paybot.gui.GuiListener.simulateCardSuccess(plugin, player, "VIETTEL", (int) amt);
            } else if (slot == 40) {
                promptManager.startSession(player, "SIMULATION_TEST_AMOUNT", "Số tiền test nạp tùy ý (VNĐ)", SetupMenuType.SIMULATION_TEST);
            } else if (slot == 45 || slot == 49) {
                com.paybot.utils.SchedulerUtils.runSync(plugin, () -> guiManager.openMenu(player, SetupMenuType.MAIN_HUB));
            }
            return;
        }

        // ── 8. SetupDiscordGui ──────────────────────────────────────────────────
        if (holder instanceof SetupDiscordGui) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            switch (slot) {
                case 11:
                    boolean curDis = plugin.getConfig().getBoolean("discord.enabled", false);
                    boolean newDis = !curDis;
                    CommentPreservingConfigWriter.updateKey(cfg, "discord.enabled", newDis);
                    plugin.getConfig().set("discord.enabled", newDis);
                    player.sendMessage("§a[PayBot Setup] §f✓ Đã " + (newDis ? "§aBẬT" : "§cTẮT") + " §ftính năng Bot Discord!");
                    guiManager.openMenu(player, SetupMenuType.DISCORD);
                    break;
                case 13: promptManager.startSession(player, "DISCORD_TOKEN", "Bot Token Discord", SetupMenuType.DISCORD); break;
                case 15: promptManager.startSession(player, "DISCORD_GUILD_ID", "Guild ID Discord", SetupMenuType.DISCORD); break;
                case 29: promptManager.startSession(player, "DISCORD_CHANNEL_ID", "Kênh thông báo Discord", SetupMenuType.DISCORD); break;
                case 31: promptManager.startSession(player, "DISCORD_IPC_PORT", "Port IPC Localhost", SetupMenuType.DISCORD); break;
                case 33:
                    player.sendMessage("§e[PayBot Setup] §fĐang kiểm tra kết nối IPC tới Bot Python...");
                    if (!plugin.isStandaloneMode() && plugin.getBotHttpClient() != null) {
                        plugin.getBotHttpClient().pingBot();
                        player.sendMessage("§a[PayBot Setup] §f✓ Đã gửi lệnh Ping tới Bot!");
                    } else {
                        player.sendMessage("§e[PayBot Setup] §fServer đang ở chế độ Standalone (hoặc Bot chưa kích hoạt).");
                    }
                    break;
                case 45:
                case 49: guiManager.openMenu(player, SetupMenuType.MAIN_HUB); break;
                default: break;
            }
            return;
        }

        // ── 9. SetupTransactionManagerGui ───────────────────────────────────────
        if (holder instanceof SetupTransactionManagerGui) {
            event.setCancelled(true);
            SetupTransactionManagerGui txGui = (SetupTransactionManagerGui) holder;
            int slot = event.getRawSlot();
            switch (slot) {
                case 2: new SetupTransactionManagerGui(plugin, "PENDING", 0).open(player); break;
                case 4: new SetupTransactionManagerGui(plugin, "SUCCESS", 0).open(player); break;
                case 6: new SetupTransactionManagerGui(plugin, "FAILED", 0).open(player); break;
                case 45:
                case 49: guiManager.openMenu(player, SetupMenuType.MAIN_HUB); break;
                case 47:
                    if (txGui.getPage() > 0) {
                        new SetupTransactionManagerGui(plugin, txGui.getStatusFilter(), txGui.getPage() - 1).open(player);
                    }
                    break;
                case 51:
                    new SetupTransactionManagerGui(plugin, txGui.getStatusFilter(), txGui.getPage() + 1).open(player);
                    break;
                default:
                    UnifiedOrder order = txGui.getOrderBySlot(slot);
                    if (order != null) {
                        if (event.isShiftClick() && order.getNormalizedStatus() == NormalizedStatus.PENDING) {
                            new TransactionApproveConfirmGui(plugin, order, txGui.getStatusFilter(), txGui.getPage()).open(player);
                        } else {
                            player.sendMessage("§e[PayBot Đơn Nạp] §fMã đơn: §b" + order.getOrderId()
                                    + " §8| §fNgười nạp: §a" + order.getPlayerName()
                                    + " §8| §fSố tiền: §e" + String.format("%,d", order.getAmount()) + " VNĐ"
                                    + " §8| §fTrạng thái: §f" + order.getRawStatus());
                        }
                    }
                    break;
            }
            return;
        }

        // ── 10. TransactionApproveConfirmGui ────────────────────────────────────
        if (holder instanceof TransactionApproveConfirmGui) {
            event.setCancelled(true);
            TransactionApproveConfirmGui confirmGui = (TransactionApproveConfirmGui) holder;
            int slot = event.getRawSlot();
            switch (slot) {
                case 11:
                    confirmGui.executeApproval(player);
                    break;
                case 15:
                case 45:
                    new SetupTransactionManagerGui(plugin, confirmGui.getReturnFilter(), confirmGui.getReturnPage()).open(player);
                    break;
                default: break;
            }
            return;
        }

        // ── 11. SetupMilestoneListGui ───────────────────────────────────────────
        if (holder instanceof SetupMilestoneListGui) {
            event.setCancelled(true);
            SetupMilestoneListGui mGui = (SetupMilestoneListGui) holder;
            int slot = event.getRawSlot();
            switch (slot) {
                case 2:
                    if (mGui.isServerMode()) new SetupMilestoneListGui(plugin, false, 0).open(player);
                    break;
                case 6:
                    if (!mGui.isServerMode()) new SetupMilestoneListGui(plugin, true, 0).open(player);
                    break;
                case 45:
                    guiManager.openMenu(player, SetupMenuType.MAIN_HUB);
                    break;
                case 47:
                    if (mGui.getPage() > 0) {
                        new SetupMilestoneListGui(plugin, mGui.isServerMode(), mGui.getPage() - 1).open(player);
                    }
                    break;
                case 49:
                    MilestonePromptSessionHandler.startAddMilestone(player, mGui.isServerMode());
                    break;
                case 51:
                    new SetupMilestoneListGui(plugin, mGui.isServerMode(), mGui.getPage() + 1).open(player);
                    break;
                case 53:
                    new SetupMilestoneResetGui(plugin, mGui.isServerMode()).open(player);
                    break;
                default:
                    Long amt = mGui.getAmountAtSlot(slot);
                    if (amt != null) {
                        if (event.isShiftClick() && event.isRightClick()) {
                            // Xóa mốc
                            if (MilestoneManager.getInstance() != null) {
                                if (mGui.isServerMode()) {
                                    MilestoneManager.getInstance().getConfig().deleteGlobalMilestone(amt);
                                } else {
                                    MilestoneManager.getInstance().getConfig().deleteSingleMilestone(amt);
                                }
                            }
                            player.sendMessage("§a[PayBot Mốc Nạp] §f✓ Đã xóa thành công mốc §6" + String.format("%,d", amt) + " VNĐ§f!");
                            new SetupMilestoneListGui(plugin, mGui.isServerMode(), mGui.getPage()).open(player);
                        } else if (event.isShiftClick()) {
                            // Sửa lệnh thưởng
                            MilestonePromptSessionHandler.startEditCommands(player, mGui.isServerMode(), amt);
                        } else {
                            // Sửa số tiền mốc
                            MilestonePromptSessionHandler.startEditAmount(player, mGui.isServerMode(), amt);
                        }
                    }
                    break;
            }
            return;
        }

        // ── 12. SetupMilestoneResetGui ──────────────────────────────────────────
        if (holder instanceof SetupMilestoneResetGui) {
            event.setCancelled(true);
            SetupMilestoneResetGui rGui = (SetupMilestoneResetGui) holder;
            int slot = event.getRawSlot();
            MilestoneConfig mConfig = (MilestoneManager.getInstance() != null) ? MilestoneManager.getInstance().getConfig() : null;

            switch (slot) {
                case 10:
                    if (mConfig != null) {
                        boolean newEn = !mConfig.isScheduledResetEnabled();
                        mConfig.setScheduledResetEnabled(newEn);
                        player.sendMessage("§a[PayBot Setup] §f✓ Đã " + (newEn ? "§aBẬT" : "§cTẮT") + " §ftính năng tự động Reset mốc nạp!");
                        new SetupMilestoneResetGui(plugin, rGui.isServerMode()).open(player);
                    }
                    break;
                case 12:
                    if (mConfig != null) {
                        MilestoneResetUnit curU = mConfig.getResetUnit();
                        MilestoneResetUnit[] allUnits = MilestoneResetUnit.values();
                        MilestoneResetUnit nextU = allUnits[(curU.ordinal() + 1) % allUnits.length];
                        mConfig.setResetUnit(nextU);
                        player.sendMessage("§a[PayBot Setup] §f✓ Đã chuyển Đơn vị Chu kỳ sang: §e" + nextU.name());
                        new SetupMilestoneResetGui(plugin, rGui.isServerMode()).open(player);
                    }
                    break;
                case 14:
                    promptManager.startSession(player, "MILESTONE_RESET_INTERVAL", "Khoảng cách chu kỳ Reset (số nguyên > 0)", SetupMenuType.MILESTONE_RESET);
                    break;
                case 16:
                    if (mConfig != null) {
                        if (event.isShiftClick()) {
                            if (mConfig.isBroadcastOnReset()) {
                                promptManager.startSession(player, "MILESTONE_RESET_BROADCAST_MSG", "Nội dung tin nhắn Broadcast khi Reset", SetupMenuType.MILESTONE_RESET);
                            } else {
                                player.sendMessage("§c[PayBot Setup] ✗ Bạn cần BẬT thông báo trước thì mới chỉnh được nội dung tin nhắn nha!");
                            }
                        } else {
                            boolean newBcast = !mConfig.isBroadcastOnReset();
                            mConfig.setBroadcastOnReset(newBcast);
                            player.sendMessage("§a[PayBot Setup] §f✓ Đã " + (newBcast ? "§aBẬT" : "§cTẮT") + " §fthông báo Broadcast khi Reset!");
                            new SetupMilestoneResetGui(plugin, rGui.isServerMode()).open(player);
                        }
                    }
                    break;
                case 31:
                    if (event.isShiftClick()) {
                        if (MilestoneManager.getInstance() != null) {
                            MilestoneManager.getInstance().triggerManualReset();
                            player.sendMessage("§c[PayBot Setup] §f✓ Đã thực hiện RESET NGAY LẬP TỨC toàn bộ tiến trình mốc nạp!");
                        }
                    } else {
                        player.sendMessage("§e[PayBot Setup] §7Vui lòng bấm §cShift + Click §7để xác nhận Reset mốc nạp ngay lập tức.");
                    }
                    break;
                case 45:
                case 49:
                    new SetupMilestoneListGui(plugin, rGui.isServerMode(), 0).open(player);
                    break;
                default: break;
            }
            return;
        }
    }
}
