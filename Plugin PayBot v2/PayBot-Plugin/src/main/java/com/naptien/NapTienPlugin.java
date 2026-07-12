package com.naptien;

import com.google.gson.JsonObject;
import com.naptien.commands.*;
import com.naptien.gui.GuiListener;
import com.naptien.log.LogManager;
import com.naptien.managers.RewardEffectManager;
import com.naptien.managers.*;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class NapTienPlugin extends JavaPlugin implements Listener {

    /**
     * v5.0.0 — Địa chỉ public CỐ ĐỊNH của bot.py (ngrok static domain).
     * Dùng cho:
     *   - Hiển thị URL webhook SePay cho admin paste vào my.sepay.vn (LUÔN là bot.py,
     *     KHÔNG BAO GIỜ là plugin-callback-url hay IP local server Minecraft).
     * Đây là hạ tầng của BOT (1 instance Discord bot dùng chung cho mọi server),
     * không phải config riêng từng server, nên hardcode ở đây là an toàn — KHÔNG
     * giống các giá trị per-guild (sepay account, partner key,...) vốn phải luôn động.
     */
    // v5.0.3: BỎ HOÀN TOÀN ngrok — bot.py giờ tự host HTTPS trên IP thật của VPS
    // (xem CHANGELOG bot.py v5.0.4). ⚠️ THAY "YOUR_VPS_IP" bằng IP thật + đúng port
    // (mặc định 24733, HTTP_PORT_SCAN_MAX trong bot.py có thể đổi port thực tế nếu
    // bị chiếm — xem log bot.py lúc khởi động để biết chính xác). Đây CHỈ là giá trị
    // fallback dùng cho lần /connect ĐẦU TIÊN của mỗi server — sau đó plugin tự nhận
    // "bot-url" mới nhất qua cơ chế push/tự cập nhật, không cần build lại jar mỗi khi
    // bot đổi VPS/port (chỉ cần build lại nếu server MỚI chưa từng connect lần nào).
    public static final String BOT_PUBLIC_URL = "https://YOUR_VPS_IP:24733";

    private static NapTienPlugin instance;

    private BotHttpClient           botHttpClient;
    private CardManager             cardManager;
    private PluginHttpServer        pluginHttpServer;
    private QRMapManager            qrMapManager;
    private OfflineRewardManager    offlineRewardManager;
    private TopupStatsManager       topupStatsManager;
    private SetupManager            setupManager;
    private SePayApiClient          sePayApiClient; // v5.0.0 Phase B
    private LocalOrderManager       localOrderManager;
    private StandaloneCardProcessor standaloneCardProcessor;
    private StandaloneBankPoller    standaloneBankPoller;
    private OwnerSessionManager     ownerSessionManager;
    // v4.1.0 — thêm mới
    private LogManager              logManager;
    private UpdateCheckManager      updateCheckManager;
    // v5.1.0 — SQLite database (thế YAML cho orders/rewards)
    private DatabaseManager         databaseManager;

    /**
     * v5.0.0 — true nếu server hiện tại đang bị owner chặn (BanGuard, xem /disablepaybot).
     * Khi true: registerCommands() tự guard mọi lệnh trừ /paybotowner + /enablepaybot +
     * /disablepaybot; KHÔNG khởi động HTTP server / task nào khác (xem activateFull()).
     */
    private volatile boolean bannedByOwner = false;

    private BukkitTask offlineCheckTask;
    private BukkitTask autoRewardPollTask;
    private BukkitTask offlineTtlTask;
    private BukkitTask standaloneCardPollTask;
    private BukkitTask standaloneBankExpireTask;
    private BukkitTask standaloneBankPollTask;
    private BukkitTask sePayApiPollTask; // v5.0.0 Phase B
    private BukkitTask configWatcherTask; // v5.0.3 [Part 18]: auto-reload config.yml mỗi 10s
    private long configLastModified = 0L;

    @Override
    public void onEnable() {
        System.setProperty("java.awt.headless", "true");
        instance = this;

        // ── v5.0.0 (fix Thứ 5): Kiểm tra enforce-secure-profile TRƯỚC MỌI THỨ ───
        // Chỉ LOG thông tin (không shutdown server nữa) — xem chi tiết lý do tại
        // javadoc của checkEnforceSecureProfile() ở dưới.
        checkEnforceSecureProfile();

        saveDefaultConfig();
        migrateConfig(); // v5.0.0 (fix): tự bổ sung config key mới, giữ nguyên giá trị admin đã chỉnh
        // v5.0.2: SmartConfigMerger bổ sung BLOCK KÈM COMMENT cho key mới thay vì chỉ thêm value trơn.
        // Chạy SAU migrateConfig() (để Bukkit FileConfiguration đã biết key mới), sau đó reload
        // để đọc lại file vật lý vừa được SmartConfigMerger append thêm vào.
        int appended = SmartConfigMerger.mergeWithComments(this, new java.io.File(getDataFolder(), "config.yml"));
        if (appended > 0) {
            reloadConfig(); // đồng bộ in-memory với file vật lý vừa được append
            getLogger().info("[PayBot] Đã thêm " + appended + " key mới (kèm comment) vào config.yml.");
        }
        // v5.0.0: validate transfer-content.code-length NGAY — fail-fast, không để lỗi
        // âm thầm lúc tạo đơn thật. Cố ý KHÔNG bắt exception ở đây — để nó propagate ra
        // ngoài onEnable(), Bukkit sẽ tự log SEVERE + disable plugin với lý do rõ ràng.
        TransferContentGenerator.validateConfig(this);
        ensureServerId();

        // ── v5.0.0: Các manager TỐI THIỂU — LUÔN cần kể cả khi bị BanGuard chặn,
        // vì /paybotowner (login) và /enablepaybot vẫn phải hoạt động được để owner
        // tự mở chặn lại từ xa (không cần SSH/console vào máy). ─────────────────
        botHttpClient       = new BotHttpClient(this);
        ownerSessionManager = new OwnerSessionManager(this);
        logManager          = new LogManager(this);
        // v5.1.0: khởi tạo Database ĐÂY — trước mọi manager khác vì
        // LocalOrderManager và OfflineRewardManager đều đọc DB khi construct.
        databaseManager     = new DatabaseManager(this);
        databaseManager.init();

        // ── v5.0.0: Kiểm tra BanGuard (im lặng, không log chi tiết cơ chế) ──────
        // Chạy ĐỒNG BỘ ở đây (chấp nhận delay khởi động vài giây nếu cần resolve IP
        // qua mạng) để CHẮC CHẮN không khởi động bất kỳ task/HTTP server nào trước
        // khi biết kết quả — đúng yêu cầu "mặc định bật paybot sẽ tự đọc file đó trước".
        bannedByOwner = BanGuard.isCurrentServerBanned();

        registerCommands(); // tự guard toàn bộ lệnh (trừ paybotowner/enablepaybot/disablepaybot) nếu bị chặn

        if (bannedByOwner) {
            getLogger().severe("==============================================");
            getLogger().severe(BanGuard.BLOCKED_MESSAGE);
            getLogger().severe("==============================================");
            return; // KHÔNG khởi động HTTP server / task nào khác
        }

        activateFull();
    }

    /**
     * v5.0.0 — Toàn bộ phần khởi động "đầy đủ" (HTTP server, tasks, kết nối bot,...),
     * tách riêng khỏi onEnable() để /enablepaybot có thể gọi LẠI ngay sau khi owner mở
     * chặn — KHÔNG cần admin phải tự /reload hoặc restart cả server Minecraft.
     */
    public void activateFull() {
        cardManager             = new CardManager(this);
        qrMapManager            = new QRMapManager(this);
        offlineRewardManager    = new OfflineRewardManager(this);
        topupStatsManager       = new TopupStatsManager(this); // v5.0.0 Phase B — PlaceholderAPI stats
        setupManager            = new SetupManager(this);
        sePayApiClient          = new SePayApiClient(this); // v5.0.0 Phase B
        localOrderManager       = new LocalOrderManager(this);
        standaloneCardProcessor = new StandaloneCardProcessor(this);
        standaloneBankPoller    = new StandaloneBankPoller(this);
        pluginHttpServer        = new PluginHttpServer(this);

        try {
            pluginHttpServer.start();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Cannot start HTTP server: " + e.getMessage(), e);
        }

        // v5.0.3: bỏ ngrok auto-detect, thay bằng tự dò IP:port thật (chạy sau 2 giây)
        if (getConfig().getString("plugin-callback-url", "").trim().isEmpty()) {
            getServer().getScheduler().runTaskLaterAsynchronously(
                    this, this::detectAndSaveCallbackUrl, 40L);
        }

        // v5.0.0 (Phase B): PlaceholderAPI là soft-depend — chỉ đăng ký nếu server có cài.
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.naptien.placeholder.PayBotPlaceholders(this).register();
            getLogger().info("[PayBot] Đã đăng ký PlaceholderAPI expansion (%paybot_...%).");
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(setupManager, this);
        Bukkit.getPluginManager().registerEvents(new GuiListener(this), this);

        String guildId  = getConfig().getString("guild-id",  "");
        String serverId = getConfig().getString("server-id", "");

        if (guildId != null && !guildId.isEmpty() && serverId != null && !serverId.isEmpty()) {
            getServer().getScheduler().runTaskAsynchronously(this,
                    () -> botHttpClient.fetchAndApplyConfig());
            getServer().getScheduler().runTaskLaterAsynchronously(this, () -> {
                List<Map<String, String>> restored = botHttpClient.fetchOfflineRewardsFromBot();
                if (restored != null && !restored.isEmpty()) {
                    offlineRewardManager.restoreFromBot(restored);
                    getLogger().info("[PayBot] Restored " + restored.size() + " offline reward(s).");
                }
            }, 60L);
        }

        startTasks();
        scheduleStartupRecovery();

        // v4.1.0: Kiểm tra cập nhật sau 30 giây (không block main thread)
        updateCheckManager = new UpdateCheckManager(this);
        getServer().getScheduler().runTaskLaterAsynchronously(this,
                () -> updateCheckManager.checkForUpdates(), 600L);

        // v4.0.12: báo IP server lên bot sau 10 giây.
        // v5.0.0 FIX: getServer().getIp() chỉ là bind-address (server.properties "server-ip",
        // admin hầu như luôn để trống) → luôn trả về "0.0.0.0", KHÔNG phải IP public thật mà
        // player dùng để join. Giờ dùng PublicIpResolver (tự hỏi dịch vụ ngoài) để lấy IP
        // public thật, kèm http_port (port HTTP server CỦA PLUGIN — khác port chơi) để bot
        // có thể PUSH thẳng reward/config tới plugin (xem PluginHttpServer), không cần đợi
        // plugin tự poll qua ngrok.
        getServer().getScheduler().runTaskLaterAsynchronously(this, () -> {
            if (isStandaloneMode()) return;
            String ip = PublicIpResolver.resolve();
            if (ip == null) {
                getLogger().log(Level.FINER, "[PayBot] reportServerIp: không resolve được IP public thật, bỏ qua lần này.");
                return;
            }
            int port     = getServer().getPort();
            int httpPort = pluginHttpServer != null ? pluginHttpServer.getListeningPort() : 0;
            getBotHttpClient().reportServerIp(ip, port, httpPort);
        }, 200L);

        // v4.1.0: Periodic retry vô hạn cho standalone mode
        // Retry bank orders chưa register được với bot (mỗi 30s)
        getServer().getScheduler().runTaskTimerAsynchronously(this,
                () -> getStandaloneBankPoller().retryUnregistered(), 600L, 600L); // 30s
        // Retry card orders bị connectionError (mỗi 30s)
        getServer().getScheduler().runTaskTimerAsynchronously(this,
                () -> getStandaloneCardProcessor().retryConnectionErrors(), 700L, 600L);

        // v5.1.0: Tự động ping bot định kỳ mỗi 60 giây ở chế độ background để tự đồng bộ cổng khi bot thay đổi port
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (!isStandaloneMode()) {
                getBotHttpClient().pingBot();
            }
        }, 800L, 1200L); // Delay 40 giây lúc bật, định kỳ 60 giây chạy một lần

        printStartupBanner(guildId, serverId);
    }

    /**
     * v5.0.2 — Banner khởi động thống nhất (đồng bộ style từ bản Fabric, theo yêu cầu
     * admin): 1 khối duy nhất hiển thị version, MC/Java đang chạy THẬT (không hardcode —
     * xem getMcVersionString()/getJavaVersionString()), chế độ (Standalone/Connected),
     * và đã setup hay chưa — TRƯỚC đây các dòng log này rời rạc, in ở nhiều chỗ khác
     * nhau theo nhánh if/else if/else, không có cái nhìn tổng quan ngay 1 chỗ.
     */
    private void printStartupBanner(String guildId, String serverId) {
        boolean connected  = guildId != null && !guildId.isEmpty() && serverId != null && !serverId.isEmpty();
        boolean sepayDone  = setupManager.isSePayConfigured();
        boolean cardDone   = setupManager.isCardApiConfigured();

        getLogger().info("[PayBot] ════════════════════════════════════════");
        getLogger().info("[PayBot]   PayBot Plugin v" + getDescription().getVersion() + " — Sẵn sàng!");
        getLogger().info("[PayBot]   Server: " + getServer().getName() + " " + getServer().getBukkitVersion()
                + " | MC: " + getMcVersionString() + " | Java: " + getJavaVersionString());
        if (connected) {
            getLogger().info("[PayBot]   Chế độ: [Bot-connected] guild=" + guildId + " server=" + serverId);
        } else if (guildId != null && !guildId.isEmpty()) {
            getLogger().info("[PayBot]   Chế độ: [Bot-connected] guild=" + guildId + " — Server ID chưa có, dùng /disconnect rồi /connect lại.");
        } else {
            getLogger().info("[PayBot]   Chế độ: [Standalone]");
        }
        getLogger().info("[PayBot]   Setup: SePay=" + (sepayDone ? "✓" : "✗ chưa") + "  Card API=" + (cardDone ? "✓" : "✗ chưa"));
        if (!connected && !sepayDone && !cardDone) {
            getLogger().warning("[PayBot]   ⚠ CHƯA CẤU HÌNH GÌ CẢ — admin cần làm 1 trong các bước sau:");
            getLogger().warning("[PayBot]     • /connect discord <guild_id>  (hệ thống auto qua Discord Bot)");
            getLogger().warning("[PayBot]     • /sepaysetup                  (nạp ngân hàng standalone)");
            getLogger().warning("[PayBot]     • /cardsetup                   (nạp thẻ cào standalone)");
            getLogger().warning("[PayBot]     Plugin sẽ KHÔNG xử lý được giao dịch nào cho tới khi hoàn tất 1 trong 3.");
        }
        getLogger().info("[PayBot] ════════════════════════════════════════");
    }

    /** Version Minecraft server đang chạy THẬT (không phải bản target lúc build) — đọc động qua Bukkit API. */
    public String getMcVersionString() {
        try {
            String bv = getServer().getBukkitVersion(); // vd "1.21.1-R0.1-SNAPSHOT"
            int dash = bv.indexOf('-');
            return dash > 0 ? bv.substring(0, dash) : bv;
        } catch (Exception e) { return "?"; }
    }

    /** Java runtime version đang chạy server này — đọc động, không hardcode. */
    public static String getJavaVersionString() {
        return System.getProperty("java.version", "?");
    }

    /**
     * v5.0.0 — Ngược lại với activateFull(): dừng hết task/HTTP server đang chạy NGAY
     * (không cần restart server) — dùng khi /disablepaybot được gọi giữa lúc plugin đang
     * hoạt động bình thường (server đó KHÔNG bị banned từ lúc khởi động, mà bị owner chặn
     * thủ công ngay trong phiên hiện tại).
     */
    public void deactivateFull() {
        cancelTask(offlineCheckTask);
        cancelTask(autoRewardPollTask);
        cancelTask(offlineTtlTask);
        cancelTask(standaloneCardPollTask);
        cancelTask(standaloneBankExpireTask);
        cancelTask(standaloneBankPollTask);
        cancelTask(sePayApiPollTask);
        cancelTask(configWatcherTask);
        if (pluginHttpServer != null) {
            pluginHttpServer.stop();
            pluginHttpServer = null;
        }
    }

    /**
     * Sau khi plugin khởi động xong, chờ 5–10 giây rồi chạy recovery.
     *
     * Recovery bank  (5s) : re-register các PENDING bank order chưa gửi được lên bot
     *                        (do crash / mất mạng trước khi register xong).
     *
     * Recovery card (10s) : retry các card order PROCESSING bị lỗi mạng
     *                        (connectionError=true), phân biệt với thẻ sai thật sự.
     */
    private void scheduleStartupRecovery() {
        // Bank recovery — 5 giây sau khi plugin enable (100 ticks)
        getServer().getScheduler().runTaskLaterAsynchronously(this, () -> {
            if (isStandaloneMode()) {
                getLogger().info("[PayBot] Startup recovery: bắt đầu kiểm tra bank orders...");
                standaloneBankPoller.recoverOnStartup();
            }
        }, 100L);

        // Card recovery — 10 giây sau khi plugin enable (200 ticks)
        getServer().getScheduler().runTaskLaterAsynchronously(this, () -> {
            if (isStandaloneMode() && setupManager.isCardApiConfigured()) {
                getLogger().info("[PayBot] Startup recovery: bắt đầu kiểm tra card orders...");
                standaloneCardProcessor.recoverUnsubmitted();
            }
        }, 200L);
    }

    private void registerCommands() {
        BankCheckCommand bankCheckCmd = new BankCheckCommand(this);
        CardCheckCommand cardCheckCmd = new CardCheckCommand(this, bankCheckCmd);
        ApproveCommand   approveCmd   = new ApproveCommand(this);

        reg("napthe",             new NapTheCommand(this));
        reg("ok",                 new OkCommand(this));
        reg("napbank",            new NapBankCommand(this));
        reg("connect",            new ConnectCommand(this));
        reg("disconnect",         new DisconnectCommand(this));
        reg("confirm",            new ConfirmCommand(this));
        reg("naptienid",          new IdCommand(this));
        reg("chinhsuamenhgianap", new ChinhSuaCommand(this));
        reg("PayBotSetup",        new PayBotSetupCommand(this));
        reg("sepaysetup",         new SePaySetupCommand(this));
        reg("cardsetup",          new CardSetupCommand(this));
        // v4.0.11: cardapisetup đã được gộp vào cardsetup → không còn dùng riêng nữa
        reg("bankcheck",          bankCheckCmd);
        reg("cardcheck",          cardCheckCmd);
        reg("approve",            approveCmd);
        reg("topuplist",          new com.naptien.commands.TopupListCommand(this));
        reg("muapaybot",          new com.naptien.commands.MuaPayBotCommand());
        reg("paybotowner",        new com.naptien.commands.OwnerLoginCommand(this));
        reg("testnapbank",        new com.naptien.commands.TestNapBankCommand(this)); // v5.0.0
        reg("testnapthe",         new com.naptien.commands.TestNapTheCommand(this));  // v5.0.0
        reg("rewardclaim",        new com.naptien.commands.RewardClaimCommand(this)); // v5.0.0
        reg("paybotleaderboard",  new com.naptien.commands.PayBotPlaceholderCommand(this)); // v5.0.3: đổi tên từ paybotplaceholder
        reg("disablepaybot",      new com.naptien.commands.DisablePaybotCommand(this)); // v5.0.0
        reg("enablepaybot",       new com.naptien.commands.EnablePaybotCommand(this));  // v5.0.0
    }

    /**
     * v5.0.0 — Lệnh KHÔNG bị BanGuard chặn, LUÔN phải dùng được kể cả khi
     * {@code bannedByOwner=true} (để owner còn đường mở chặn lại từ xa).
     */
    private static final Set<String> ALWAYS_ALLOWED_WHEN_BANNED =
            Set.of("paybotowner", "enablepaybot", "disablepaybot");

    private void reg(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand c = getCommand(name);
        if (c == null) {
            getLogger().warning("[PayBot] Command not found in plugin.yml: " + name);
            return;
        }
        if (bannedByOwner && !ALWAYS_ALLOWED_WHEN_BANNED.contains(name.toLowerCase())) {
            // v5.0.0: server đang bị BanGuard chặn → mọi lệnh khác chỉ trả về thông
            // báo chung, KHÔNG thực thi logic gốc (kể cả khi admin tự sửa config/jar).
            c.setExecutor((sender, cmd, label, args) -> {
                sender.sendMessage(NapTienPlugin.f("§c[PayBot] §f" + BanGuard.BLOCKED_MESSAGE));
                return true;
            });
        } else {
            c.setExecutor(executor);
        }
    }

    private void startTasks() {
        // v5.0.0 FIX (theo yêu cầu — bỏ HẲN polling tự động, không "tự gửi" gì cả):
        // ĐÃ XOÁ 3 timer tự động trước đây gọi bot.py vô điều kiện theo giờ:
        //   - rewardPollTask   (/api/pending-rewards, cũ 60s → 4 phút/lần)
        //   - configPollTask   (/api/get-config,       cũ 60s → 10 phút/lần)
        //   - onlinePushTask   (/api/update-online-players, heartbeat 5 phút/lần)
        // Cả 3 giờ chỉ gọi khi THỰC SỰ có lý do cụ thể (xem từng nơi):
        //   - pending-rewards: gọi 1 lần lúc player JOIN (xem onPlayerJoin) + lúc player
        //     tự chạy /RewardClaim (xem RewardClaimCommand) — đúng lúc có thể giao được,
        //     không hỏi "phòng hờ" lúc chẳng ai cần.
        //   - get-config: chỉ gọi 1 lần lúc plugin KHỞI ĐỘNG (activateFull(), không phải
        //     polling — chỉ là "tôi vừa start, cần config để chạy") + lúc bot PUSH xuống
        //     khi admin chỉnh trên Discord (push_config_to_plugin → /receive-config).
        //   - online-players: KHÔNG còn gửi gì nữa — bot tự hỏi /check-online (PluginHttpServer)
        //     đúng lúc cần (/napbank, /napthe từ Discord), plugin không tự đẩy gì cả.
        // server không mở port cho bot push được thì coi đây là giới hạn đã biết — xem
        // CHANGELOG.md để biết chi tiết trade-off (không còn safety-net polling nữa).

        // v5.0.0 (theo yêu cầu — tự động giao reward, không bắt /RewardClaim nữa):
        // mỗi 30 GIÂY: (1) check LOCAL (rẻ — chỉ đọc map trong RAM) cho TỪNG player
        // đang online xem có reward nào đang chờ chưa giao không (bắt case reward được
        // thêm vào lúc họ ĐÃ ONLINE rồi — vd push từ bot trong lúc /RewardClaim cũ đã bỏ);
        // (2) hỏi bot CÓ ĐIỀU KIỆN — CHỈ 1 LẦN GỌI DUY NHẤT cho TOÀN SERVER (không phải
        // 1 lần/player), tự bot trả rỗng nếu chẳng có gì mới. Đây không phải kiểu "spam"
        // đã bị xoá trước đó (poll-mọi-player-mọi-30s-vô-điều-kiện) — chỉ là 1 request nhẹ
        // mỗi 30s tối đa, có server-id mới gọi (standalone/chưa connect thì khỏi gọi).
        autoRewardPollTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            getServer().getScheduler().runTask(this, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (offlineRewardManager.hasPendingRewards(p.getName())) {
                        processOfflineRewards(p);
                    }
                }
            });
        }, 600L, 600L);
        // v5.0.3 [Part 22]: TÁCH RIÊNG remote-poll ra 10s (trước đây gộp chung vào task
        // trên, giãn ra 5 phút/lần với lý do "qua ngrok, tài nguyên chung nhiều server" —
        // lý do đó giờ lỗi thời (đã bỏ ngrok, bot tự host trực tiếp). 10s vẫn là fallback
        // an toàn thôi — bình thường bot đã PUSH thẳng qua /execute-reward ngay khi có
        // reward, poll này chỉ bắt lại phần lỡ (server offline lúc bot push, lỗi mạng).
        getServer().getScheduler().runTaskTimerAsynchronously(this,
                this::checkPendingRewardsFromBot, 200L, 200L);

        long pollPeriodTicks = Math.max(5, getConfig().getLong("sepay-api.poll-interval-seconds", 10)) * 20L;
        sePayApiPollTask = getServer().getScheduler().runTaskTimerAsynchronously(this,
                this::pollSePayApiTransactions, 100L, pollPeriodTicks);

        // v5.0.3 [Part 18]: watcher tự phát hiện config.yml thay đổi (do push từ bot,
        // do admin tự sửa tay, hay do upgrade version) → tự reload + tự thêm key/comment
        // thiếu + tự xoá key thừa không còn tác dụng. Mặc định BẬT — tắt qua
        // "auto-config-sync: false" nếu admin không muốn (VD: đang tự debug config thủ công).
        if (getConfig().getBoolean("auto-config-sync", true)) {
            File cfgFile = new File(getDataFolder(), "config.yml");
            configLastModified = cfgFile.lastModified();
            configWatcherTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                long mtime = cfgFile.lastModified();
                if (mtime == configLastModified) return; // chưa đổi gì, khỏi làm gì cả
                configLastModified = mtime;
                int[] result = SmartConfigMerger.sync(this, cfgFile);
                int added = result[0], removed = result[1];
                if (added > 0 || removed > 0) {
                    reloadConfig();
                    getLogger().info("[PayBot] 🔄 Config đã thay đổi — tự reload xong. "
                            + "(+" + added + " key mới, -" + removed + " key thừa) "
                            + "[PayBot v" + getDescription().getVersion() + "]");
                }
            }, 200L, 200L); // 200 tick = 10 giây
        }

        // v5.0.0: BỎ task lặp định kỳ tự nhắc lại reward đang chờ (cũ: cứ 5 phút lại nhắn
        // 1 lần cho player còn pending — bị xem là spam). Giờ /RewardClaim hoạt động độc
        // lập, không cần thông báo nhắc nhở lặp lại — chỉ báo 1 lần lúc join (onPlayerJoin).
        // TTL dọn dẹp reward quá 7 ngày vẫn giữ nguyên ở offlineTtlTask dưới đây.

        offlineTtlTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            String sid = getConfig().getString("server-id", "").trim();
            if (!sid.isEmpty()) offlineRewardManager.checkAndExpireOldRewards();
            // v5.0.0 FIX: bankOrders/cardOrders (LocalOrderManager) trước đây CHỈ ĐƯỢC
            // THÊM, không bao giờ xoá — đơn EXPIRED/APPROVED/đã xử lý xong vẫn nằm mãi
            // trong bank-orders.yml/card-orders.yml, phình vô hạn theo thời gian. Giờ
            // CHO ADMIN TỰ CHỌN qua config (xem migrateConfig() để biết default/giải
            // thích đầy đủ): order-retention.bank-days / order-retention.card-days
            // (-1 = giữ vĩnh viễn [mặc định], 0 = xoá ngay, >0 = xoá sau N ngày).
            int bankDays = getConfig().getInt("order-retention.bank-days", -1);
            int cardDays = getConfig().getInt("order-retention.card-days", -1);
            int removed  = localOrderManager.pruneOldOrders(bankDays, cardDays);
            if (removed > 0) {
                getLogger().info("[PayBot] Đã dọn " + removed + " đơn cũ (đã xử lý xong, quá hạn lưu trữ) khỏi bộ nhớ.");
            }
        }, 72000L, 72000L);

        standaloneCardPollTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (isStandaloneMode() && setupManager.isCardApiConfigured()) {
                standaloneCardProcessor.pollPendingCards();
            }
        }, 200L, 200L);

        standaloneBankExpireTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (!isStandaloneMode()) return;
            long cutoff = System.currentTimeMillis() - (30L * 60 * 1000);
            for (LocalOrderManager.BankOrder o : localOrderManager.getPendingBankOrders()) {
                if (LocalOrderManager.BANK_PENDING.equals(o.status) && o.createdAt <= cutoff) {
                    localOrderManager.updateBankStatus(o.invoiceId, LocalOrderManager.BANK_EXPIRED);
                    NotificationManager.log(this, "order-expired", "[Standalone] Bank order expired: " + o.invoiceId);
                    Player p = Bukkit.getPlayerExact(o.playerName);
                    if (p != null && p.isOnline()) {
                        p.sendMessage(NapTienPlugin.f("§c[PayBot] §fQR nạp tiền §e#" + o.invoiceId
                                + " §fđã hết hạn (30 phút). Dùng /napbank để tạo mới."));
                        qrMapManager.removeQRMap(p, o.invoiceId);
                    }
                }
            }
        }, 600L, 600L);

        // Poll bot xem bank order nào đã được thanh toán (60 giây/lần)
        standaloneBankPollTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (isStandaloneMode()) {
                standaloneBankPoller.pollPendingOrders();
            }
        }, 600L, 1200L);
    }

    @Override
    public void onDisable() {
        cancelTask(offlineCheckTask);
        cancelTask(autoRewardPollTask);
        cancelTask(offlineTtlTask);
        cancelTask(standaloneCardPollTask);
        cancelTask(standaloneBankExpireTask);
        cancelTask(standaloneBankPollTask);
        cancelTask(sePayApiPollTask); // v5.0.0 Phase B
        cancelTask(configWatcherTask);
        if (pluginHttpServer != null) pluginHttpServer.stop();
        if (ownerSessionManager != null) ownerSessionManager.shutdown();
        // v5.1.0: đóng DB sạch sẽ trước khi plugin dừng
        if (databaseManager != null) databaseManager.close();
        getLogger().info("[PayBot] Plugin stopped.");
    }

    private void cancelTask(BukkitTask t) { if (t != null) t.cancel(); }

    // ─── Player join ──────────────────────────────────────────────────────────

    /** Cooldown (playerUUID → lastWarnMs) cho cảnh báo enforce-secure-profile — không spam mỗi lần join. */
    private final java.util.Map<java.util.UUID, Long> secureProfileWarnCooldown = new java.util.concurrent.ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // v5.0.2: Cảnh báo enforce-secure-profile trực tiếp tới admin/OP khi join.
        // Cooldown 10 phút/player để không spam mỗi lần reconnect.
        if (player.hasPermission("naptien.admin") || player.isOp()) {
            getServer().getScheduler().runTaskLaterAsynchronously(this, () -> {
                long now = System.currentTimeMillis();
                long last = secureProfileWarnCooldown.getOrDefault(player.getUniqueId(), 0L);
                if (now - last < 10 * 60_000L) return;
                java.io.File serverProps = new java.io.File("server.properties");
                if (!serverProps.exists()) return;
                try {
                    java.util.Properties props = new java.util.Properties();
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(serverProps)) { props.load(fis); }
                    boolean secureProfile = "true".equalsIgnoreCase(props.getProperty("enforce-secure-profile", "false"));
                    boolean onlineMode    = "true".equalsIgnoreCase(props.getProperty("online-mode", "true"));
                    if (!secureProfile) return;
                    secureProfileWarnCooldown.put(player.getUniqueId(), now);
                    Bukkit.getScheduler().runTask(this, () -> {
                        if (!player.isOnline()) return;
                        if (secureProfile && onlineMode) {
                            player.sendMessage(f("§c§l[PayBot] §r§cCẢNH BÁO: §fenforce-secure-profile=true + online-mode=true đang bật!"));
                            player.sendMessage(f("§7Kết hợp này có thể gây xung đột với PayBot (middleware chat-relay, chữ ký chat). Vui lòng tắt 1 trong 2 trong server.properties:"));
                            player.sendMessage(f("  §fenforce-secure-profile=§cfalse  §7(khuyến nghị — plugin/mod ẩn mã thẻ bằng cancel event, không rebroadcast)"));
                            player.sendMessage(f("§7hoặc tắt online-mode=false (nếu chủ ý offline server)."));
                        } else {
                            player.sendMessage(f("§e[PayBot] §fGợi ý: §fenforce-secure-profile=true đang bật. Plugin/mod vẫn hoạt động đúng, nhưng khuyến nghị đặt §bfalse §fđể tránh rủi ro nếu sau này bật online-mode hoặc cài thêm proxy/plugin xử lý chat."));
                        }
                    });
                } catch (Exception ignored) {}
            }, 60L); // delay 3 giây sau khi join để player load xong màn hình
        }

        // v5.0.2 FIX: quét + xoá QR map hết hạn (an toàn cho trường hợp player logout
        // trước khi đủ 30 phút hoặc server restart giữa lúc đang chờ — xem javadoc
        // cleanExpiredQRMapsOnJoin()).
        getServer().getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) cleanExpiredQRMapsOnJoin(player);
        }, 20L);

        // Auto-deliver reward đang chờ (queue từ trước khi player offline)
        if (offlineRewardManager.hasPendingRewards(player.getName())) {
            getServer().getScheduler().runTaskLaterAsynchronously(this,
                    () -> processOfflineRewards(player), 40L);
        }
        // v5.0.0: hỏi bot ĐÚNG LÚC player vào (thay cho poll định kỳ cũ)
        checkPendingRewardsFromBot();
    }

    /**
     * v5.0.0 — Hỏi bot "có reward nào đang chờ giao cho server này không" — ĐÚNG LÚC CẦN
     * (player vừa join, hoặc tự /RewardClaim), không còn polling theo giờ vô điều kiện.
     * An toàn gọi nhiều lần liên tiếp (vd 2 người join gần nhau): fetchPendingRewards()
     * trả rỗng nếu bot không có gì mới, processReward() tự confirmReward() ngay sau khi
     * dispatch nên reward không bị giao trùng lần thứ 2.
     */
    public void checkPendingRewardsFromBot() {
        String sid = getConfig().getString("server-id", "").trim();
        if (sid.isEmpty()) return; // standalone hoặc chưa connect — không có gì để hỏi
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            List<JsonObject> rewards = botHttpClient.fetchPendingRewards();
            for (JsonObject reward : rewards) processReward(reward);
        });
    }

    // v5.0.0 (fix theo góp ý): ĐÃ BỎ HẲN việc đẩy danh sách online lên bot — kể cả kiểu
    // poll cứng 30s ban đầu, hay throttle event-driven sau đó (join/quit + heartbeat).
    // Lý do bỏ hẳn: dữ liệu này CHỈ được bot dùng đúng 1 lúc — khi player chạy /napbank
    // hoặc /napthe TỪ DISCORD (cần biết "có đang online không" + "đang ở server nào" để
    // route reward đúng chỗ). Việc đẩy liên tục "phòng hờ" cho 1 nhu cầu xảy ra RẤT THƯA
    // (đa số player nạp ngay trong game, không qua Discord) là lãng phí — bot giờ tự hỏi
    // THẲNG qua endpoint /check-online (xem PluginHttpServer) ĐÚNG lúc cần, không hỏi thì
    // plugin không cần trả lời gì cả. Server nào không mở port cho bot hỏi được thì bot
    // tự coi như "không xác định" và xử lý đúng như hành vi gốc đã có từ trước (cho phép
    // nạp tiếp, không chặn — xem is_player_online_in_guild() trong bot.py).

    /** Thông báo NGẮN 1 dòng (không giao reward) — player tự dùng /RewardClaim để nhận. */
    /**
     * v5.0.0 (theo yêu cầu — bỏ bắt buộc /RewardClaim, tự động giao luôn): TỰ ĐỘNG giao
     * hết reward đang chờ ngay khi player online (lúc join, hoặc lúc auto-poll 30s phát
     * hiện họ vừa online — xem startAutoRewardPoll()). KHÔNG giao trùng: snapshot danh
     * sách trước khi lặp, mỗi reward giao xong được {@code dispatchOfflineReward()} tự
     * {@code removeReward()} khỏi storage NGAY (đồng bộ, trước khi xử lý item kế tiếp) —
     * gọi lại method này nhiều lần (vd join + auto-poll trùng thời điểm) tuyệt đối an
     * toàn, lần gọi sau chỉ thấy danh sách rỗng hoặc phần còn lại CHƯA bị lần trước xử lý.
     */
    public void processOfflineRewards(Player player) {
        List<Map<String, String>> pending = offlineRewardManager.getRewardsForPlayer(player.getName());
        if (pending.isEmpty()) return;
        Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline()) return;
            // Snapshot NGAY tại đây (trên main thread) — tránh giao trùng nếu có nguồn
            // khác cũng đang gọi method này gần như đồng thời (xem javadoc trên).
            List<Map<String, String>> snapshot = new ArrayList<>(offlineRewardManager.getRewardsForPlayer(player.getName()));
            if (snapshot.isEmpty()) return;
            if (snapshot.size() > 1) {
                player.sendMessage(NapTienPlugin.f("§e[PayBot] §fĐang giao §a" + snapshot.size()
                        + " §fphần thưởng đang chờ..."));
            }
            for (Map<String, String> r : snapshot) {
                if (!player.isOnline()) break; // phòng trường hợp player out giữa lúc đang giao nhiều cái
                dispatchOfflineReward(player, r); // tự removeReward() + confirm bot ngay sau khi giao
            }
        });
    }

    /** Lấy reward CŨ NHẤT (FIFO) đang chờ của player, hoặc null nếu không có. */
    public Map<String, String> peekOldestOfflineReward(Player player) {
        List<Map<String, String>> rewards = offlineRewardManager.getRewardsForPlayer(player.getName());
        return rewards.isEmpty() ? null : rewards.get(0);
    }

    /** Số reward đang chờ của player. */
    public int countOfflineRewards(Player player) {
        return offlineRewardManager.getRewardsForPlayer(player.getName()).size();
    }

    /** v5.0.0: public — dùng cho /RewardClaim (giao 1 reward cụ thể, player CHẮC CHẮN online vì tự chạy lệnh). */
    public void claimOfflineReward(Player player, Map<String, String> r) {
        dispatchOfflineReward(player, r);
    }

    /**
     * v5.0.0 (Phase B): poll trực tiếp API SePay (my.sepay.vn/my.dev.sepay.vn) bằng
     * Bearer Token — KHÔNG cần webhook, KHÔNG cần bot.py/ngrok. Chạy trên thread async
     * (HTTP call), chỉ phần đụng tới Bukkit API (player.sendMessage,...) mới đẩy về main thread.
     */
    private void pollSePayApiTransactions() {
        if (!getConfig().getBoolean("sepay-api.enabled", false)) return;
        String token = getConfig().getString("sepay-api.api-token", "").trim();
        if (token.isEmpty()) return;

        long lastId = getConfig().getLong("sepay-api.last-transaction-id", 0);
        List<SePayApiClient.TransactionInfo> txs = sePayApiClient.pollNewTransactions(lastId);
        if (txs.isEmpty()) return;

        long maxId = lastId;
        for (SePayApiClient.TransactionInfo tx : txs) {
            maxId = Math.max(maxId, tx.id);
            if (tx.amountIn <= 0) continue; // chỉ quan tâm tiền VÀO (bỏ qua giao dịch tiền ra)

            LocalOrderManager.BankOrder matched = localOrderManager.matchPendingByContent(tx.content, null);
            if (matched == null) continue;
            if (tx.amountIn < matched.amount) {
                NotificationManager.warn(this, "sepay-error", "[PayBot] SePay API: thiếu tiền! đơn #"
                        + matched.invoiceId + " cần " + matched.amount + " nhận " + tx.amountIn);
                continue;
            }

            // v5.0.2 FIX: Auto-dispatch reward — không cần admin duyệt thủ công.
            List<String> rewardCmds = RewardDispatcher.resolveRewardCmds(this, matched.amount, "bank");
            final LocalOrderManager.BankOrder fm = matched;
            if (!rewardCmds.isEmpty()) {
                String rewardAmt = RewardDispatcher.computeRewardAmt(this, matched.amount, "bank");
                localOrderManager.updateBankStatus(fm.invoiceId, LocalOrderManager.BANK_APPROVED);
                // v5.0.0 (Phase B): báo bot relay Discord nếu connect mode
                if (!isStandaloneMode()) {
                    getServer().getScheduler().runTaskAsynchronously(this,
                            () -> botHttpClient.notifyBankPaidViaApi(fm.invoiceId, fm.playerName, fm.amount));
                }
                Bukkit.getScheduler().runTask(this, () -> {
                    boolean wasOnline = RewardDispatcher.dispatchOrQueue(this, fm.invoiceId, fm.playerName,
                            rewardCmds, rewardAmt, String.valueOf(fm.amount), "bank", fm.invoiceId, "");
                    NotificationManager.notifyAdmins(this, "bank-payment-received",
                            "§a[PayBot] §fĐơn §b#" + fm.invoiceId + " §fcủa §e" + fm.playerName
                            + " §fthanh toán §f" + formatVnd(fm.amount) + " VND — §athưởng đã tự giao"
                            + (wasOnline ? "" : " §7(offline → nhận khi join lại)") + "§f. §7(SePay API)");
                });
            } else {
                // Chưa cấu hình lệnh thưởng → set PAID, chờ admin cấu hình rồi /approve
                localOrderManager.updateBankStatus(fm.invoiceId, LocalOrderManager.BANK_PAID);
                if (!isStandaloneMode()) {
                    getServer().getScheduler().runTaskAsynchronously(this,
                            () -> botHttpClient.notifyBankPaidViaApi(fm.invoiceId, fm.playerName, fm.amount));
                }
                Bukkit.getScheduler().runTask(this, () -> {
                    NotificationManager.notifyAdmins(this, "bank-payment-received",
                            "§a[PayBot] §fĐơn §b#" + fm.invoiceId + " §fcủa §e" + fm.playerName
                            + " §fthanh toán §f" + formatVnd(fm.amount) + " VND §7(SePay API)"
                            + "\n§7Chưa cấu hình lệnh thưởng — dùng §e/approve " + fm.invoiceId
                            + " §7sau khi cấu hình /chinhsuamenhgianap.");
                    Player p = Bukkit.getPlayerExact(fm.playerName);
                    if (p != null && p.isOnline()) {
                        p.sendMessage(NapTienPlugin.f("§a§l[PayBot] §r§aĐã nhận thanh toán " + formatVnd(fm.amount)
                                + " VND! Đang chờ admin cấu hình thưởng..."));
                        RewardEffectManager.notifyPaymentReceived(this, p, fm.amount);
                    }
                });
            }
        }

        if (maxId != lastId) {
            getConfig().set("sepay-api.last-transaction-id", maxId);
            saveConfig();
        }
    }

    private void dispatchOfflineReward(Player player, Map<String, String> r) {
        String rewardId   = r.getOrDefault("rewardId",     "");
        String playerName = r.getOrDefault("playerName",   player.getName());
        String rewardAmt  = r.getOrDefault("rewardAmount", "0");
        String denomVnd   = r.getOrDefault("denom",        "");
        String invoiceId  = r.getOrDefault("invoiceId",    "");
        String type       = r.getOrDefault("type",         "card");
        String rawCmd     = r.getOrDefault("rewardCmd",    "");

        if (rawCmd.isEmpty()) {
            offlineRewardManager.removeReward(playerName, rewardId);
            // v5.1.0: chỉ confirm với bot nếu đang kết nối (standalone không có bot queue)
            if (!isStandaloneMode()) {
                getServer().getScheduler().runTaskAsynchronously(this,
                        () -> botHttpClient.confirmOfflineReward(rewardId));
            }
            return;
        }

        // v5.0.0: method này được gọi từ /RewardClaim (RewardClaimCommand) VÀ từ
        // processOfflineRewards()/auto-poll (tự động giao lúc join hoặc auto-poll phát
        // hiện online) — cả 2 nguồn đều đảm bảo đang ở main thread trước khi gọi tới đây
        // (lệnh Bukkit vốn luôn ở main thread; processOfflineRewards() tự bọc runTask())
        // → an toàn bỏ Bukkit.getScheduler().runTask() bọc thừa ở đây, remove khỏi storage
        // diễn ra NGAY, tránh giao trùng nếu 2 nguồn gọi gần như đồng thời.
        java.util.List<String> cmdList = RewardDispatcher.splitCmds(rawCmd);
        // Player CHẮC CHẮN online tại đây (đã check ở caller) → deliverNow sẽ bắn pháo
        // hoa/sound/thông báo (đơn đã được duyệt từ trước lúc player offline, giờ là lúc
        // giao thưởng — player đang ở đây để thấy nên vẫn bắn hiệu ứng).
        RewardDispatcher.deliverNow(this, player, cmdList, rewardAmt, denomVnd, type, invoiceId, true);
        offlineRewardManager.removeReward(playerName, rewardId);
        // v5.1.0: chỉ confirm với bot nếu đang kết nối
        if (!isStandaloneMode()) {
            getServer().getScheduler().runTaskAsynchronously(this,
                    () -> botHttpClient.confirmOfflineReward(rewardId));
        }
    }

    private void processReward(JsonObject reward) {
        String rewardId   = reward.has("reward_id")     ? reward.get("reward_id").getAsString()     : "";
        String playerName = reward.has("player_name")   ? reward.get("player_name").getAsString()   : "";
        String rewardAmt  = reward.has("reward_amount") ? reward.get("reward_amount").getAsString() : "0";
        String denomVnd   = reward.has("denom")         ? reward.get("denom").getAsString()         : null;
        String invoiceId  = reward.has("invoice_id")    ? reward.get("invoice_id").getAsString()    : null;
        String type       = reward.has("type")          ? reward.get("type").getAsString()          : "card";
        String discordUid = reward.has("discord_user_id") && !reward.get("discord_user_id").isJsonNull()
                ? reward.get("discord_user_id").getAsString() : "";
        boolean isBank    = "bank".equalsIgnoreCase(type);

        String rawCmd = "";
        if (reward.has("reward_cmd") && !reward.get("reward_cmd").isJsonNull())
            rawCmd = reward.get("reward_cmd").getAsString().trim();

        java.util.List<String> rawCmds;
        if (!rawCmd.isEmpty()) {
            // Bot tự cung cấp reward_cmd trực tiếp (1 lệnh — protocol bot.py hiện chỉ hỗ trợ 1)
            rawCmds = java.util.List.of(rawCmd);
        } else if (denomVnd != null && !denomVnd.isEmpty()) {
            // v5.0.0: fallback đọc config local — dùng resolveRewardCmds để lấy ĐỦ tối đa
            // 10 lệnh đã cấu hình qua /chinhsuamenhgianap, không chỉ field "cmd" đơn cũ.
            try {
                rawCmds = RewardDispatcher.resolveRewardCmds(this, Integer.parseInt(denomVnd.trim()), type);
            } catch (NumberFormatException e) {
                rawCmds = java.util.List.of();
            }
        } else {
            String fallback = getConfig().getString(isBank ? "reward-command-bank" : "reward-command-card", "").trim();
            if (fallback.isEmpty()) fallback = getConfig().getString("reward-command", "").trim();
            rawCmds = fallback.isEmpty() ? java.util.List.of() : java.util.List.of(fallback);
        }

        if (rawCmds.isEmpty()) {
            NotificationManager.warn(this, "reward-invalid",
                    "[PayBot] No reward-command for type=" + type + " reward_id=" + rewardId);
            final String rid = rewardId;
            if (!isStandaloneMode()) {
                getServer().getScheduler().runTaskAsynchronously(this, () -> botHttpClient.confirmReward(rid));
            }
            return;
        }

        // v5.0.0 (fix Thứ 2): dùng RewardDispatcher để vừa tự rẽ nhánh online/offline,
        // vừa bắn pháo hoa/sound/thông báo khi player đang online tại thời điểm này —
        // đây là luồng TỰ ĐỘNG CHÍNH khi bot xác nhận thẻ/bank thành công, trước đây
        // KHÔNG hề bắn hiệu ứng (chỉ GUI duyệt tay mới có).
        // Lưu ý: processReward() được gọi từ thread ASYNC (checkPendingRewardsFromBot(),
        // chạy on-demand lúc join/RewardClaim — không còn polling task định kỳ nữa) →
        // phải đẩy dispatchOrQueue() về main thread vì nó gọi Bukkit API (dispatchCommand,
        // getPlayerExact,...) không thread-safe.
        final String rid = rewardId;
        final java.util.List<String> fRawCmds = rawCmds;
        final String fPlayerName = playerName, fRewardAmt = rewardAmt,
                     fDenom = denomVnd, fType = type, fInvoice = invoiceId, fDiscordUid = discordUid;
        Bukkit.getScheduler().runTask(this, () -> {
            RewardDispatcher.dispatchOrQueue(this, rid, fPlayerName, fRawCmds, fRewardAmt,
                    fDenom, fType, fInvoice, fDiscordUid);
            // confirmReward CHỈ gọi khi bot-connected (processReward được gọi từ bot)
            if (!isStandaloneMode()) {
                getServer().getScheduler().runTaskAsynchronously(this, () -> botHttpClient.confirmReward(rid));
            }
        });
    }

    private String formatVnd(int amount) {
        StringBuilder sb = new StringBuilder(String.valueOf(amount));
        int len = sb.length();
        for (int i = len - 3; i > 0; i -= 3) sb.insert(i, '.');
        return sb.toString();
    }

    /**
     * Khi đã kết nối bot (mua bot), tự động gỡ tag §x[PayBot] khỏi tin nhắn gửi tới player.
     * Standalone mode: giữ nguyên [PayBot] để nhận diện nguồn.
     * <p>
     * Dùng ở mọi chỗ sendMessage() gửi tới player/admin thay vì hard-code string.
     *
     * @param msg tin nhắn gốc có thể chứa §x[PayBot]
     * @return tin nhắn đã xử lý
     */
    public static String f(String msg) {
        if (instance != null && !instance.isStandaloneMode()) {
            // Xoá mọi dạng §color[PayBot] (kể cả §a§l[PayBot]) + khoảng trắng theo sau
            return msg.replaceAll("(?:§[0-9a-fA-FklmnorKLMNOR])+\\[PayBot\\]\\s*", "");
        }
        return msg;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    public boolean isStandaloneMode() {
        String guildId = getConfig().getString("guild-id", "").trim();
        return guildId.isEmpty();
    }

    private void ensureServerId() {
        String serverId = getConfig().getString("server-id", "").trim();
        if (serverId.isEmpty()) {
            serverId = UUID.randomUUID().toString();
            getConfig().set("server-id", serverId);
            saveConfig();
            getLogger().info("[PayBot] Auto-generated server-id: " + serverId);
        }
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public static NapTienPlugin getInstance()                              { return instance; }
    public DatabaseManager           getDatabaseManager()                  { return databaseManager; }
    public BotHttpClient             getBotHttpClient()                    { return botHttpClient; }
    public CardManager               getCardManager()                      { return cardManager; }
    public PluginHttpServer          getPluginHttpServer()                 { return pluginHttpServer; }
    public QRMapManager              getQRMapManager()                     { return qrMapManager; }
    public OfflineRewardManager      getOfflineRewardManager()             { return offlineRewardManager; }
    public TopupStatsManager         getTopupStatsManager()                { return topupStatsManager; }
    public SetupManager              getSetupManager()                     { return setupManager; }
    public SePayApiClient            getSePayApiClient()                   { return sePayApiClient; }
    public LocalOrderManager         getLocalOrderManager()                { return localOrderManager; }
    public StandaloneCardProcessor   getStandaloneCardProcessor()          { return standaloneCardProcessor; }
    public StandaloneBankPoller      getStandaloneBankPoller()             { return standaloneBankPoller; }
    public OwnerSessionManager       getOwnerSessionManager()              { return ownerSessionManager; }
    public LogManager                getLogManager()                       { return logManager; }
    public UpdateCheckManager        getUpdateCheckManager()               { return updateCheckManager; }

    /** v5.0.0 — true nếu server hiện tại đang bị owner chặn qua BanGuard (/disablepaybot). */
    public boolean isBannedByOwner() { return bannedByOwner; }

    // ─── QR map expiry tracking (v5.0.2 FIX) ────────────────────────────────
    // TRƯỚC: QRMapManager chỉ hẹn giờ runTaskLater 1 LẦN DUY NHẤT lúc tạo QR — nếu
    // player logout TRƯỚC khi đủ 30 phút, lúc task chạy code chỉ return (không làm gì)
    // và KHÔNG tự lên lịch lại → QR kẹt vĩnh viễn trong balo. Ngoài ra task kiểu này
    // còn MẤT HẲN nếu server restart giữa lúc đang chờ (Bukkit scheduler không persist
    // task qua restart) → mất luôn timer, QR cũng kẹt lại.
    // FIX: dùng trực tiếp BankOrder.createdAt (đã có sẵn, persist trên đĩa qua restart)
    // làm nguồn sự thật duy nhất — quét + xoá QR hết hạn mỗi lúc player JOIN (lúc này
    // chắc chắn có thể sửa inventory), KHÔNG cần map riêng nào khác để đồng bộ.

    /** Gọi từ onPlayerJoin — quét QR map hết hạn (>30 phút từ lúc tạo đơn) trong balo, xoá nếu cần. */
    private void cleanExpiredQRMapsOnJoin(Player player) {
        long now = System.currentTimeMillis();
        long ttlMs = 30 * 60 * 1000L;
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(this, "invoice_id");
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            org.bukkit.inventory.ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() != com.naptien.utils.VersionCompat.getMapMaterial()) continue;
            String invId = com.naptien.utils.VersionCompat.getInvoiceId(item, key);
            if (invId == null) continue;
            LocalOrderManager.BankOrder order = localOrderManager.getBankOrder(invId);
            if (order == null) continue; // không tìm thấy đơn (đã bị dọn theo order-retention) — bỏ qua, không đoán mò
            if (now - order.createdAt >= ttlMs) {
                player.getInventory().setItem(i, null);
                player.sendMessage(f("§c[PayBot] §fQR chuyển khoản §e" + invId.substring(0, Math.min(10, invId.length()))
                        + "...§f đã hết hạn (>30 phút), tự động xoá khỏi balo."));
                getLogger().info("[PayBot] QR map hết hạn đã xoá lúc join: player=" + player.getName() + " invoice=" + invId);
            }
        }
    }


    public void setBannedByOwner(boolean banned) {
        this.bannedByOwner = banned;
        registerCommands();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // v4.3.0 — Startup guards & auto-config
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Kiểm tra server.properties: enforce-secure-profile + online-mode.
     * <p>
     * v5.0.0 (fix Thứ 5): TRƯỚC ĐÂY method này shutdown cả server nếu enforce-secure-profile=true,
     * với lý do "chat đã ký số thì plugin không thể ẩn serial/mã thẻ". Lý do đó SAI về kỹ thuật:
     * {@code AsyncPlayerChatEvent#setCancelled(true)} (cách GuiListener đang dùng để ẩn input mã
     * thẻ/serial khỏi chat công khai) chặn tin nhắn HOÀN TOÀN, không phụ thuộc secure-profile.
     * Secure-profile chỉ ảnh hưởng khi plugin SỬA nội dung rồi REBROADCAST lại (vì chữ ký số sẽ
     * không còn khớp) — plugin này không làm vậy ở bất kỳ đâu, chỉ cancel rồi xử lý nội bộ.
     * → Toàn bộ logic ẩn thông tin nhạy cảm vẫn chạy đúng dù enforce-secure-profile=true,
     * nên KHÔNG ép tắt hay shutdown server — chỉ LOG cảnh báo/khuyến nghị để admin tự quyết.
     * <p>
     * v5.0.0 (theo yêu cầu): thêm kiểm tra kết hợp với online-mode —
     *   - CẢ HAI true → cảnh báo NGHIÊM TRỌNG (nguy cơ thật: server online-mode xác thực
     *     qua Mojang/Microsoft + bắt buộc ký chat, vài middleware/proxy chat-relay hoặc
     *     plugin khác có thể xung đột khi cố sửa/forward tin nhắn đã ký) — khuyên tắt 1
     *     trong 2 để plugin/mod chạy đúng.
     *   - CHỈ enforce-secure-profile=true (online-mode false hoặc không set) → không có
     *     nguy cơ xung đột như trên (vì online-mode=false thì server không thực sự bắt
     *     buộc ký chat dù config nói true), nhưng vẫn khuyến nghị tắt để tránh rủi ro khó
     *     đoán nếu admin BẬT LẠI online-mode sau này mà quên xét lại config này.
     */
    private void checkEnforceSecureProfile() {
        java.io.File serverProps = new java.io.File("server.properties");
        if (!serverProps.exists()) return;
        try {
            java.util.Properties props = new java.util.Properties();
            try (java.io.FileInputStream fis = new java.io.FileInputStream(serverProps)) {
                props.load(fis);
            }
            boolean secureProfile = "true".equalsIgnoreCase(props.getProperty("enforce-secure-profile", "false"));
            boolean onlineMode    = "true".equalsIgnoreCase(props.getProperty("online-mode", "true"));

            if (secureProfile && onlineMode) {
                getLogger().warning("[PayBot] ─────────────────────────────────────────────");
                getLogger().warning("[PayBot] CẢNH BÁO: Server đang bật online-mode + enforce-secure-profile,");
                getLogger().warning("[PayBot] vui lòng tắt 1 trong 2 để plugin/mod chạy đúng.");
                getLogger().warning("[PayBot] (online-mode=true bắt buộc xác thực Mojang/Microsoft + ký chat —");
                getLogger().warning("[PayBot]  kết hợp enforce-secure-profile=true có thể xung đột với plugin");
                getLogger().warning("[PayBot]  khác/proxy chat-relay đang forward/sửa tin nhắn đã ký số.)");
                getLogger().warning("[PayBot] ─────────────────────────────────────────────");
            } else if (secureProfile) {
                getLogger().info("[PayBot] ─────────────────────────────────────────────");
                getLogger().info("[PayBot] KHUYẾN NGHỊ: enforce-secure-profile=true được phát hiện.");
                getLogger().info("[PayBot] Plugin/mod vẫn ẩn được serial/mã thẻ khỏi chat công khai bình");
                getLogger().info("[PayBot] thường (event được cancel hoàn toàn, không rebroadcast nên không");
                getLogger().info("[PayBot] đụng tới chữ ký chat) — KHÔNG bắt buộc phải tắt.");
                getLogger().info("[PayBot] Vẫn khuyến nghị đặt enforce-secure-profile=false để tránh rủi ro");
                getLogger().info("[PayBot] khó đoán nếu sau này bật thêm online-mode hoặc dùng kèm");
                getLogger().info("[PayBot] proxy/plugin khác có xử lý chat.");
                getLogger().info("[PayBot] ─────────────────────────────────────────────");
            }
        } catch (Exception e) {
            getLogger().warning("[PayBot] Không thể đọc server.properties: " + e.getMessage());
        }
    }

    /**
     * v5.0.0 (fix): Tự động bổ sung các config key MỚI (so với bản config.yml đóng gói
     * sẵn trong JAR đang chạy) vào file config.yml THẬT của admin trên đĩa — và tự áp
     * dụng ngay (admin không cần set lại tay). KHÔNG đụng tới bất kỳ giá trị nào admin
     * đã tự chỉnh (true/false/số/chuỗi gì cũng giữ nguyên y vậy) — chỉ thêm key nào
     * ĐANG THIẾU hoàn toàn trong file của admin.
     * <p>
     * Hoạt động cho MỌI lần update sau này, không chỉ riêng đợt này — vì nó so sánh
     * trực tiếp với config.yml đóng gói trong JAR ở thời điểm chạy (không hardcode danh
     * sách key cụ thể), nên bất kỳ key mới nào được thêm vào config.yml mẫu ở các bản
     * sau cũng sẽ tự được phát hiện + bổ sung + apply theo đúng cơ chế này.
     */
    /**
     * v5.0.0 — Buộc lưu + nạp lại config NGAY khi có bất kỳ thay đổi nào cần áp dụng tức
     * thì, dùng CHUNG cho mọi nguồn thay đổi: nhận config từ bot (`/receive-config`),
     * chỉnh mệnh giá nạp (`/chinhsuamenhgianap`), cấu hình SePay (`/sepaysetup`), cấu hình
     * web đổi thẻ (`/cardsetup`).
     * <p>
     * Về kỹ thuật: mọi nơi đọc config trong plugin (RewardDispatcher, SePayApiClient,
     * StandaloneCardProcessor,...) đều gọi {@code plugin.getConfig().get...()} TRỰC TIẾP
     * mỗi lần cần — KHÔNG có class nào cache riêng 1 bản {@code FileConfiguration} —
     * nên về lý thuyết thay đổi đã "sống" ngay khi {@code set()} chạy, không cần đợi gì
     * thêm. Method này vẫn được gọi tường minh ở MỌI nơi đổi config (thay cho rải
     * {@code saveConfig()} rời rạc từng chỗ) để: (1) đảm bảo LUÔN ghi xuống đĩa ngay, không
     * phụ thuộc admin nhớ lưu; (2) nạp lại từ đĩa để đồng bộ tuyệt đối nếu sau này có thêm
     * class nào lỡ cache config (an toàn trước các thay đổi code sau này); (3) có 1 chỗ
     * log thống nhất để dễ debug khi nghi config không áp dụng.
     */
    public void forceConfigRefresh(String reason) {
        saveConfig();
        reloadConfig();
        getLogger().info("[PayBot] Config đã được làm mới (" + reason + ").");
    }

    private void migrateConfig() {
        try (java.io.InputStream defStream = getResource("config.yml")) {
            if (defStream == null) return;
            org.bukkit.configuration.file.YamlConfiguration defaults =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                            new java.io.InputStreamReader(defStream, java.nio.charset.StandardCharsets.UTF_8));

            org.bukkit.configuration.file.FileConfiguration live = getConfig();
            int added = 0;
            for (String key : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(key)) continue; // chỉ xét leaf value, bỏ qua section header
                if (!live.isSet(key)) {
                    live.set(key, defaults.get(key));
                    added++;
                }
            }

            // v5.0.0 FIX: bản config.yml mẫu CŨ từng đặt "plugin-port: 25565" — TRÙNG với
            // port chơi mặc định của Minecraft → PluginHttpServer (NanoHTTPD) bind THẤT BẠI
            // ngay từ đầu trên mọi server chưa từng tự đổi port này (SePay IPN/push reward/
            // push config từ bot ĐỀU không hoạt động được, dù không có cảnh báo rõ ràng cho
            // admin biết tại sao). Tự sửa 1 LẦN DUY NHẤT nếu vẫn còn đúng giá trị lỗi cũ —
            // không đụng nếu admin đã tự đổi sang giá trị khác (dù vô tình giống port chơi).
            if (live.getInt("plugin-port", -1) == 25565) {
                live.set("plugin-port", 25580);
                added++;
                getLogger().warning("[PayBot] Tự sửa plugin-port: 25565 → 25580 (25565 trùng port chơi "
                        + "Minecraft mặc định, khiến HTTP server của plugin không bind được).");
            }

            // v5.0.2 FIX: "bot-api-key" giờ đã HARDCODE trong code (BotHttpClient.DEFAULT_API_KEY),
            // không còn đọc từ config nữa. Tự xoá dòng cũ khỏi config.yml nếu admin đang upgrade
            // từ bản trước (tránh để lại 1 key chết, dễ gây hiểu lầm là vẫn chỉnh được).
            if (live.isSet("bot-api-key")) {
                live.set("bot-api-key", null);
                added++;
                getLogger().info("[PayBot] Đã xoá \"bot-api-key\" khỏi config.yml (key này giờ hardcode "
                        + "trong code, không còn là tuỳ chọn nữa — admin không cần và không nên tự đổi).");
            }

            // v5.0.2 FIX: default cũ "transfer-content.code-mode: -1" (lẫn chữ A-F) tái lập
            // ĐÚNG bug đã từng fix ở bot.py new_invoice_id() — SePay tự bỏ qua (không gửi
            // webhook) giao dịch có mã không khớp pattern PREFIX+SỐ nếu dashboard SePay bật
            // skip_if_no_code (mặc định). Admin không hề biết vì plugin không nhận được gì
            // để mà báo lỗi (im lặng hoàn toàn). Tự sửa 1 LẦN nếu vẫn còn ĐÚNG giá trị lỗi cũ
            // (-1) — không đụng nếu admin đã tự đổi sang giá trị khác.
            if (live.getInt("transfer-content.code-mode", 0) == -1) {
                live.set("transfer-content.code-mode", 0);
                added++;
                getLogger().warning("[PayBot] Tự sửa transfer-content.code-mode: -1 → 0 (chỉ số). "
                        + "Giá trị -1 (lẫn chữ A-F) có thể khiến SePay ÂM THẦM không gửi webhook cho "
                        + "giao dịch nếu dashboard SePay đang bật \"skip_if_no_code\" — xem chi tiết "
                        + "trong comment transfer-content trong config.yml.");
            }
            // v5.0.2: prefix mặc định cũ "PB" (kế thừa từ format cố định trước v5.0.0) đổi
            // thành "NT". Vì "PB" cũng có thể là lựa chọn CHỦ Ý của admin khác (không riêng
            // gì giá trị lỗi như code-mode ở trên) nên chỉ tự sửa ĐÚNG 1 LẦN DUY NHẤT (đánh
            // dấu bằng cờ nội bộ "_migrated-prefix-5-0-2", admin không cần biết/đụng vào cờ
            // này) — sau lần đó nếu admin tự gõ lại "PB" sẽ KHÔNG bị ép đổi nữa.
            if (!live.getBoolean("_migrated-prefix-5-0-2", false)) {
                if ("PB".equalsIgnoreCase(live.getString("transfer-content.prefix", "").trim())) {
                    live.set("transfer-content.prefix", "NT");
                    getLogger().info("[PayBot] Tự sửa transfer-content.prefix: \"PB\" → \"NT\" (prefix mặc định mới, "
                            + "chỉ áp dụng 1 lần — nếu sau này bạn tự đổi lại \"PB\" sẽ được giữ nguyên).");
                }
                live.set("_migrated-prefix-5-0-2", true);
                added++;
            }

            if (added > 0) {
                saveConfig();
                getLogger().info("[PayBot] Đã tự bổ sung " + added + " config key mới vào config.yml "
                        + "(giữ nguyên mọi giá trị bạn đã chỉnh trước đó).");
            }
        } catch (Exception e) {
            getLogger().warning("[PayBot] Lỗi khi tự bổ sung config: " + e.getMessage());
        }
    }

    /**
     * v5.0.3: THAY cơ chế cũ (query ngrok local API :4040 — chỉ hoạt động nếu admin
     * tự chạy ngrok riêng cho callback URL của họ) bằng tự dò IP public thật của
     * chính server này (PublicIpResolver — cùng nguồn với reportServerIp(), 3
     * provider dự phòng lẫn nhau) kết hợp "plugin-port" (cổng NanoHTTPD nội bộ).
     * Vẫn CHỈ set khi "plugin-callback-url" đang trống — admin đã tự điền tay
     * (VD: domain riêng, hoặc IP khác do NAT/port-forward) thì không ghi đè.
     * Lưu ý: đây là chiều NGƯỢC (bot → plugin), vẫn dùng http vì PluginHttpServer
     * (NanoHTTPD) chưa có HTTPS trong phạm vi đợt sửa này — chỉ chiều plugin/mod →
     * bot.py mới bắt buộc HTTPS (xem BotHttpClient.java).
     */
    private void detectAndSaveCallbackUrl() {
        try {
            String ip = PublicIpResolver.resolve();
            if (ip == null || ip.isEmpty()) return;
            int port = getConfig().getInt("plugin-port", 25580);
            final String finalUrl = "http://" + ip + ":" + port;
            getServer().getScheduler().runTask(this, () -> {
                getConfig().set("plugin-callback-url", finalUrl);
                saveConfig();
                getLogger().info("[PayBot] ✓ Tự dò IP thật — plugin-callback-url: " + finalUrl);
            });
        } catch (Exception ignored) {
            // Không dò được IP (mất mạng, cả 3 provider đều lỗi) → bỏ qua, admin tự set thủ công
        }
    }
}
