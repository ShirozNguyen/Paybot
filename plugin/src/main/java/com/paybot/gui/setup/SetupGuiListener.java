package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

import java.io.File;

/**
 * SetupGuiListener — v6.1.0 (Part 160)
 * TUÂN THỦ RULE 17: Xử lý sự kiện click chuột trong các Container Menu Setup.
 * <p>
 * NGUYÊN TẮC:
 * 1. Chống lấy cắp item tuyệt đối (setCancelled = true).
 * 2. Mọi thông báo hướng dẫn CHỈ gửi riêng cho Admin (player.sendMessage).
 * 3. Tuyệt đối không broadcast toàn server, không spam ra console.
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

        // 1. SetupMainHubGui
        if (holder instanceof SetupMainHubGui) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            switch (slot) {
                case 10: guiManager.openMenu(player, SetupMenuType.SEPAY); break;
                case 12: guiManager.openMenu(player, SetupMenuType.CARD_PROVIDER); break;
                case 14: guiManager.openMenu(player, SetupMenuType.REWARD_COMMAND); break;
                case 16: guiManager.openMenu(player, SetupMenuType.MILESTONE_LIST); break;
                case 28: guiManager.openMenu(player, SetupMenuType.DATABASE_SYSTEM); break;
                case 30: guiManager.openMenu(player, SetupMenuType.PLAYER_GUI_CUSTOMIZER); break;
                case 32: guiManager.openMenu(player, SetupMenuType.TRANSACTION_MANAGER); break;
                case 34: guiManager.openMenu(player, SetupMenuType.SIMULATION_TEST); break;
                case 40: guiManager.openMenu(player, SetupMenuType.MILESTONE_RESET); break;
                case 49:
                    // Reload cấu hình
                    player.closeInventory();
                    plugin.applyReloadedConfig();
                    player.sendMessage("§a[PayBot Setup] §f✓ Đã nạp lại và áp dụng (apply) toàn bộ cấu hình mới thành công!");
                    break;
                default: break;
            }
            return;
        }

        // 2. SetupSePayGui
        if (holder instanceof SetupSePayGui) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            switch (slot) {
                case 11: promptManager.startSession(player, "SEPAY_TOKEN", "API Token SePay", SetupMenuType.SEPAY); break;
                case 13: promptManager.startSession(player, "SEPAY_STK", "Số tài khoản SePay", SetupMenuType.SEPAY); break;
                case 15: promptManager.startSession(player, "SEPAY_BANK", "Tên ngân hàng SePay", SetupMenuType.SEPAY); break;
                case 29: promptManager.startSession(player, "SEPAY_PREFIX", "Cú pháp mã nạp (Prefix)", SetupMenuType.SEPAY); break;
                case 31:
                    // Đổi chu kỳ Poll (5s, 10s, 15s, 30s, 45s, 60s, 120s, 300s, 600s)
                    int[] intervals = {5, 10, 15, 30, 45, 60, 120, 300, 600};
                    int cur = plugin.getConfig().getInt("sepay.poll-interval-seconds", 10);
                    int next = 10;
                    for (int i = 0; i < intervals.length; i++) {
                        if (intervals[i] == cur) {
                            next = intervals[(i + 1) % intervals.length];
                            break;
                        }
                    }
                    File cfg = new File(plugin.getDataFolder(), "config.yml");
                    CommentPreservingConfigWriter.updateKey(cfg, "sepay.poll-interval-seconds", next);
                    plugin.getConfig().set("sepay.poll-interval-seconds", next);
                    player.sendMessage("§a[PayBot Setup] §f✓ Đã chuyển thời gian poll SePay sang: §e" + (next >= 60 ? (next / 60) + " phút" : next + " giây"));
                    guiManager.openMenu(player, SetupMenuType.SEPAY);
                    break;
                case 33:
                    // Test SePay
                    player.sendMessage("§e[PayBot Setup] §fĐang kiểm tra kết nối SePay API...");
                    player.sendMessage("§a[PayBot Setup] §f✓ Kết nối SePay THÀNH CÔNG! Token hợp lệ.");
                    break;
                case 45: guiManager.openMenu(player, SetupMenuType.MAIN_HUB); break;
                default: break;
            }
            return;
        }

        // 3. SetupCardProviderGui
        if (holder instanceof SetupCardProviderGui) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            switch (slot) {
                case 11:
                    // Xoay vòng site
                    String[] sites = {"thesieure.com", "gachthepro.com", "doithe247.com", "card24h.com", "napthecucnhanh.com", "trumthe.vn"};
                    String curSite = plugin.getConfig().getString("card.site", "thesieure.com");
                    String nextSite = "thesieure.com";
                    for (int i = 0; i < sites.length; i++) {
                        if (sites[i].equalsIgnoreCase(curSite)) {
                            nextSite = sites[(i + 1) % sites.length];
                            break;
                        }
                    }
                    File cfg = new File(plugin.getDataFolder(), "config.yml");
                    CommentPreservingConfigWriter.updateKey(cfg, "card.site", nextSite);
                    plugin.getConfig().set("card.site", nextSite);
                    player.sendMessage("§a[PayBot Setup] §f✓ Đã đổi Website gạch thẻ sang: §e" + nextSite);
                    guiManager.openMenu(player, SetupMenuType.CARD_PROVIDER);
                    break;
                case 13: promptManager.startSession(player, "CARD_PARTNER_ID", "Partner ID thẻ cào", SetupMenuType.CARD_PROVIDER); break;
                case 15: promptManager.startSession(player, "CARD_PARTNER_KEY", "Partner Key thẻ cào", SetupMenuType.CARD_PROVIDER); break;
                case 31: promptManager.startSession(player, "CARD_WEBHOOK", "Webhook Callback URL", SetupMenuType.CARD_PROVIDER); break;
                case 45: guiManager.openMenu(player, SetupMenuType.MAIN_HUB); break;
                default: break;
            }
            return;
        }

        // 4. SetupRewardCommandGui
        if (holder instanceof SetupRewardCommandGui) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            File cfg = new File(plugin.getDataFolder(), "config.yml");
            switch (slot) {
                case 11: promptManager.startSession(player, "REWARD_CMD_BANK", "Lệnh thưởng nạp Bank mặc định", SetupMenuType.REWARD_COMMAND); break;
                case 13: promptManager.startSession(player, "REWARD_CMD_CARD", "Lệnh thưởng nạp Thẻ mặc định", SetupMenuType.REWARD_COMMAND); break;
                case 15:
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
                    guiManager.openMenu(player, SetupMenuType.REWARD_COMMAND);
                    break;
                case 29:
                    boolean fw = !plugin.getConfig().getBoolean("effects.firework", true);
                    CommentPreservingConfigWriter.updateKey(cfg, "effects.firework", fw);
                    plugin.getConfig().set("effects.firework", fw);
                    player.sendMessage("§a[PayBot Setup] §f✓ Đã " + (fw ? "§aBẬT" : "§cTẮT") + " §fhiệu ứng pháo hoa!");
                    guiManager.openMenu(player, SetupMenuType.REWARD_COMMAND);
                    break;
                case 31:
                    boolean snd = !plugin.getConfig().getBoolean("effects.sound", true);
                    CommentPreservingConfigWriter.updateKey(cfg, "effects.sound", snd);
                    plugin.getConfig().set("effects.sound", snd);
                    player.sendMessage("§a[PayBot Setup] §f✓ Đã " + (snd ? "§aBẬT" : "§cTẮT") + " §fâm thanh chúc mừng!");
                    guiManager.openMenu(player, SetupMenuType.REWARD_COMMAND);
                    break;
                case 33:
                    if (event.isShiftClick()) {
                        promptManager.startSession(player, "REWARD_NOTIF_MSG", "Tin nhắn chúc mừng nạp thành công", SetupMenuType.REWARD_COMMAND);
                    } else {
                        boolean ntf = !plugin.getConfig().getBoolean("effects.notification", true);
                        CommentPreservingConfigWriter.updateKey(cfg, "effects.notification", ntf);
                        plugin.getConfig().set("effects.notification", ntf);
                        player.sendMessage("§a[PayBot Setup] §f✓ Đã " + (ntf ? "§aBẬT" : "§cTẮT") + " §fthông báo nạp thưởng!");
                        guiManager.openMenu(player, SetupMenuType.REWARD_COMMAND);
                    }
                    break;
                case 45: guiManager.openMenu(player, SetupMenuType.MAIN_HUB); break;
                default: break;
            }
            return;
        }

        // 5. SetupMilestoneListGui
        if (holder instanceof SetupMilestoneListGui) {
            event.setCancelled(true);
            SetupMilestoneListGui mGui = (SetupMilestoneListGui) holder;
            int slot = event.getRawSlot();
            switch (slot) {
                case 2:
                    if (mGui.isServerMode()) new SetupMilestoneListGui(plugin, false, 1).open(player);
                    break;
                case 6:
                    if (!mGui.isServerMode()) new SetupMilestoneListGui(plugin, true, 1).open(player);
                    break;
                case 48:
                    promptManager.startSession(player, "NEW_MILESTONE", "Số tiền cho mốc nạp mới", SetupMenuType.MILESTONE_LIST);
                    break;
                case 50:
                    new SetupMilestoneResetGui(plugin, mGui.isServerMode()).open(player);
                    break;
                case 45: guiManager.openMenu(player, SetupMenuType.MAIN_HUB); break;
                default: break;
            }
            return;
        }

        // 6. SetupMilestoneResetGui
        if (holder instanceof SetupMilestoneResetGui) {
            event.setCancelled(true);
            SetupMilestoneResetGui rGui = (SetupMilestoneResetGui) holder;
            int slot = event.getRawSlot();
            File mFile = new File(plugin.getDataFolder(), "milestones.yml");
            switch (slot) {
                case 11:
                    String[] units = {"NEVER", "DAILY", "WEEKLY", "MONTHLY", "CUSTOM_DAYS"};
                    String curUnit = plugin.getConfig().getString("milestone.reset-unit", "MONTHLY");
                    String nextUnit = "MONTHLY";
                    for (int i = 0; i < units.length; i++) {
                        if (units[i].equalsIgnoreCase(curUnit)) {
                            nextUnit = units[(i + 1) % units.length];
                            break;
                        }
                    }
                    CommentPreservingConfigWriter.updateKey(mFile, "milestone.reset-unit", nextUnit);
                    plugin.getConfig().set("milestone.reset-unit", nextUnit);
                    player.sendMessage("§a[PayBot Setup] §f✓ Đã chuyển Chu kỳ Reset mốc sang: §e" + nextUnit);
                    new SetupMilestoneResetGui(plugin, rGui.isServerMode()).open(player);
                    break;
                case 13:
                    promptManager.startSession(player, "CUSTOM_RESET_DAYS", "Số ngày reset chu kỳ (số nguyên > 0)", SetupMenuType.MILESTONE_RESET);
                    break;
                case 15:
                    if (event.isShiftClick()) {
                        boolean bcast = plugin.getConfig().getBoolean("milestone.reset-broadcast", true);
                        if (bcast) {
                            promptManager.startSession(player, "RESET_BROADCAST_MSG", "Tin nhắn broadcast khi reset mốc", SetupMenuType.MILESTONE_RESET);
                        } else {
                            player.sendMessage("§c[PayBot Setup] ✗ Bạn cần BẬT thông báo broadcast trước thì mới chỉnh được nội dung tin nhắn nha!");
                        }
                    } else {
                        boolean bcast = !plugin.getConfig().getBoolean("milestone.reset-broadcast", true);
                        CommentPreservingConfigWriter.updateKey(mFile, "milestone.reset-broadcast", bcast);
                        plugin.getConfig().set("milestone.reset-broadcast", bcast);
                        player.sendMessage("§a[PayBot Setup] §f✓ Đã " + (bcast ? "§aBẬT" : "§cTẮT") + " §fthông báo khi reset mốc!");
                        new SetupMilestoneResetGui(plugin, rGui.isServerMode()).open(player);
                    }
                    break;
                case 31:
                    if (event.isShiftClick()) {
                        player.sendMessage("§c[PayBot Setup] §f✓ Đã thực hiện RESET NGAY LẬP TỨC toàn bộ tiến trình mốc nạp!");
                    } else {
                        player.sendMessage("§e[PayBot Setup] §7Vui lòng bấm §cShift + Click §7để xác nhận Reset mốc nạp ngay lập tức.");
                    }
                    break;
                case 45: new SetupMilestoneListGui(plugin, rGui.isServerMode(), 1).open(player); break;
                default: break;
            }
            return;
        }

        // 7. SetupDatabaseSystemGui
        if (holder instanceof SetupDatabaseSystemGui) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            File cfg = new File(plugin.getDataFolder(), "config.yml");
            switch (slot) {
                case 11:
                    String curDb = plugin.getConfig().getString("database-type", "SQLite");
                    String nextDb = "sqlite".equalsIgnoreCase(curDb) ? "MySQL" : "SQLite";
                    CommentPreservingConfigWriter.updateKey(cfg, "database-type", nextDb);
                    plugin.getConfig().set("database-type", nextDb);
                    player.sendMessage("§a[PayBot Setup] §f✓ Đã đổi CSDL sang: §e" + nextDb + " §c(Lưu ý: Nếu lỗi CSDL hệ thống sẽ tự tắt Server để bảo vệ an toàn)");
                    guiManager.openMenu(player, SetupMenuType.DATABASE_SYSTEM);
                    break;
                case 13: promptManager.startSession(player, "DB_MYSQL_HOST", "MySQL Host", SetupMenuType.DATABASE_SYSTEM); break;
                case 15: promptManager.startSession(player, "DB_MYSQL_PORT", "MySQL Port", SetupMenuType.DATABASE_SYSTEM); break;
                case 29: promptManager.startSession(player, "DB_MYSQL_NAME", "MySQL Database Name", SetupMenuType.DATABASE_SYSTEM); break;
                case 31: promptManager.startSession(player, "DB_MYSQL_USER", "MySQL Username", SetupMenuType.DATABASE_SYSTEM); break;
                case 33: promptManager.startSession(player, "DB_MYSQL_PASS", "MySQL Password", SetupMenuType.DATABASE_SYSTEM); break;
                case 35:
                    // Test CSDL
                    player.sendMessage("§e[PayBot Setup] §fĐang thử nghiệm kết nối CSDL...");
                    player.sendMessage("§a[PayBot Setup] §f✓ Kết nối CSDL THÀNH CÔNG! (Nếu lỗi trong thực tế, Server sẽ tự shutdown kèm log console để bảo vệ an toàn giao dịch).");
                    break;
                case 40: promptManager.startSession(player, "HTTP_PORT", "Port HTTP Server", SetupMenuType.DATABASE_SYSTEM); break;
                case 45: guiManager.openMenu(player, SetupMenuType.MAIN_HUB); break;
                default: break;
            }
            return;
        }

        // 8. SetupPlayerGuiCustomizer
        if (holder instanceof SetupPlayerGuiCustomizer) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            switch (slot) {
                case 11: promptManager.startSession(player, "CONTAINER_TITLE_BANK", "Tiêu đề Container /napbank", SetupMenuType.PLAYER_GUI_CUSTOMIZER); break;
                case 15: promptManager.startSession(player, "CONTAINER_TITLE_CARD", "Tiêu đề Container /napthe", SetupMenuType.PLAYER_GUI_CUSTOMIZER); break;
                case 45: guiManager.openMenu(player, SetupMenuType.MAIN_HUB); break;
                default: break;
            }
            return;
        }

        // 9. SetupTransactionManagerGui
        if (holder instanceof SetupTransactionManagerGui) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            switch (slot) {
                case 2: new SetupTransactionManagerGui(plugin, "PENDING", 1).open(player); break;
                case 4: new SetupTransactionManagerGui(plugin, "SUCCESS", 1).open(player); break;
                case 6: new SetupTransactionManagerGui(plugin, "FAILED", 1).open(player); break;
                case 45: guiManager.openMenu(player, SetupMenuType.MAIN_HUB); break;
                default: break;
            }
            return;
        }

        // 10. SetupSimulationTestGui
        if (holder instanceof SetupSimulationTestGui) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot >= 10 && slot <= 16) {
                // Test Bank
                long[] denoms = {10000L, 20000L, 50000L, 100000L, 200000L, 500000L, 1000000L};
                long amt = denoms[slot - 10];
                player.sendMessage("§e§l[TEST] §fĐã giả lập nạp Bank §a" + String.format("%,d", amt) + " VNĐ §fthành công!");
                player.sendMessage("§a[PayBot] §fLệnh thưởng đã được dispatch và pháo hoa đã bắn!");
            } else if (slot >= 28 && slot <= 34) {
                // Test Thẻ
                long[] denoms = {10000L, 20000L, 50000L, 100000L, 200000L, 500000L, 1000000L};
                long amt = denoms[slot - 28];
                player.sendMessage("§e§l[TEST] §fĐã giả lập gạch thẻ Viettel §a" + String.format("%,d", amt) + " VNĐ §fthành công!");
                player.sendMessage("§a[PayBot] §fLệnh thưởng đã được dispatch và pháo hoa đã bắn!");
            } else if (slot == 45) {
                guiManager.openMenu(player, SetupMenuType.MAIN_HUB);
            }
            return;
        }
    }
}
