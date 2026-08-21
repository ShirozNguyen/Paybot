package com.naptien;

import com.naptien.commands.CommandRegistry;
import com.naptien.config.PayBotConfig;
import com.naptien.log.LogManager;
import com.naptien.log.LogSpamFilter;
import com.naptien.managers.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

public class PayBotMod implements ModInitializer {

    public static final String MOD_ID = "paybot";

    public static final String DEFAULT_BOT_URL = "https://YOUR_VPS_IP:24733";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static PayBotMod INSTANCE;

    private MinecraftServer server;
    private Path dataDir;
    private boolean isBanned = false;

    private PayBotConfig            config;
    private LogManager              logManager;
    private LogSpamFilter           logFilter;
    private BotHttpClient           botHttpClient;
    private CardManager             cardManager;
    private LocalOrderManager       localOrderManager;
    private OfflineRewardManager    offlineRewardManager;
    private DatabaseManager         databaseManager;
    private OwnerSessionManager     ownerSessionManager;
    private SetupManager            setupManager;
    private StandaloneCardProcessor standaloneCardProcessor;
    private StandaloneBankPoller    standaloneBankPoller;
    private PluginHttpServer        pluginHttpServer;
    private QRMapManager            qrMapManager;
    private UpdateCheckManager      updateCheckManager;
    private PlaceholderManager      placeholderManager;

    private ScheduledExecutorService scheduler;

    private final Set<String> lastOnlinePlayers = new HashSet<>();

    @Override
    public void onInitialize() {
        init();
    }

    public static void init() {
        try {
            com.naptien.compat.LibraryDownloader.checkAndDownloadLibraries(FabricLoader.getInstance().getConfigDir().toFile());
        } catch (Exception ignored) {}
        INSTANCE = new PayBotMod();
        INSTANCE.setupEvents();
    }

    private void setupEvents() {
        com.naptien.utils.MinecraftVersionDetector.init();

        ServerLifecycleEvents.SERVER_STARTED.register(srv -> onServerStart(srv));
        ServerLifecycleEvents.SERVER_STOPPING.register(srv -> onServerStop());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            CommandRegistry.registerAll(dispatcher);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onPlayerJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onPlayerQuit(handler.getPlayer()));

        LOGGER.info("[PayBot] Fabric Mod registered — waiting for server start…");
    }

    public static String getModVersion() {
        try {
            var mod = FabricLoader.getInstance().getModContainer(MOD_ID);
            return mod.isPresent() ? mod.get().getMetadata().getVersion().getFriendlyString() : "5.5.5";
        } catch (Exception e) {
            return "5.5.5";
        }
    }

    public static String getLoaderVersion() {
        return "Fabric " + FabricLoader.getInstance().getModContainer("fabricloader").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("0.16.0");
    }

    /** Version Minecraft server đang chạy thực tế (không phải bản mod target lúc build). */
    public String getMcVersion() {
        try {
            return server != null ? server.getServerVersion() : "?";
        } catch (Exception e) { return "?"; }
    }

    /** Java runtime version đang chạy server này. */
    public static String getJavaVersion() {
        return System.getProperty("java.version", "?");
    }

    // ─── Server start ─────────────────────────────────────────────────────────

    public void onServerStart(net.minecraft.server.MinecraftServer srv) {
        this.server = srv;
        onServerStart();
    }

    private void onServerStart() {
        checkEnforceSecureProfile();

        if (BanManager.isCurrentServerBanned()) {
            isBanned = true;
            LOGGER.warn("[PayBot] ╔══════════════════════════════════════════════════╗");
            LOGGER.warn("[PayBot] ║        PAYBOT ĐÃ BỊ VÔ HIỆU HÓA TRÊN SERVER    ║");
            LOGGER.warn("[PayBot] ╚══════════════════════════════════════════════════╝");
            LOGGER.warn("[PayBot] Server hiện tại bị chặn do spam log / vi phạm chính sách");
            LOGGER.warn("[PayBot] / chỉnh sửa plugin (hoặc mod),... vui lòng liên hệ");
            LOGGER.warn("[PayBot] admin để biết chi tiết.");
            return;
        }

        dataDir = FabricLoader.getInstance().getGameDir().resolve("PayBot");
        dataDir.toFile().mkdirs();
        dataDir.resolve("Card").toFile().mkdirs();
        dataDir.resolve("Bank").toFile().mkdirs();

        // Config: load + auto-merge missing keys
        config = new PayBotConfig(dataDir.resolve("config.yml"));
        config.load();
        // v5.0.0: validate transfer-content.code-length NGAY — fail-fast, không để lỗi
        // âm thầm lúc tạo đơn thật. Cố ý KHÔNG bắt exception — để nó propagate ra ngoài,
        // Fabric sẽ tự log lỗi + ngừng khởi tạo mod với lý do rõ ràng.
        com.naptien.managers.TransferContentGenerator.validateConfig(this);
        ensureServerId();

        scheduler = Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "PayBot-Scheduler");
            t.setDaemon(true);
            return t;
        });

        // Khởi tạo managers theo thứ tự dependency
        logFilter               = new LogSpamFilter();
        logManager              = new LogManager(dataDir);
        botHttpClient           = new BotHttpClient(this);
        cardManager             = new CardManager();
        databaseManager         = new DatabaseManager(this);
        databaseManager.init();
        localOrderManager       = new LocalOrderManager(this);
        offlineRewardManager    = new OfflineRewardManager(this);
        ownerSessionManager     = new OwnerSessionManager(this);
        setupManager            = new SetupManager(this);
        standaloneCardProcessor = new StandaloneCardProcessor(this);
        standaloneBankPoller    = new StandaloneBankPoller(this);
        qrMapManager            = new QRMapManager(this);
        placeholderManager      = new PlaceholderManager(this);
        updateCheckManager      = new UpdateCheckManager(this);

        pluginHttpServer = new PluginHttpServer(this);
        try { pluginHttpServer.start(); }
        catch (java.io.IOException e) {
            LOGGER.error("[PayBot] Không khởi động được HTTP server: " + e.getMessage());
        }

        startTasks();

        LOGGER.info("[PayBot] ════════════════════════════════════════");
        LOGGER.info("[PayBot]   PayBot Fabric v" + getModVersion() + " — Sẵn sàng!");
        LOGGER.info("[PayBot]   Loader: " + getLoaderVersion() + " | MC: " + getMcVersion() + " | Java: " + getJavaVersion());
        LOGGER.info("[PayBot]   Chế độ: " + (isStandaloneMode()
                ? "[Standalone]"
                : "[Bot-connected] guild=" + config.getString("guild-id", "")));
        LOGGER.info("[PayBot]   Max_Notifications: " + config.getMaxNotificationsPerMinute()
                + (config.getMaxNotificationsPerMinute() < 0 ? " (unlimited)" : "/phút"));
        // v5.0.0 (theo yêu cầu): cảnh báo riêng nếu CHƯA setup BẤT CỨ THỨ GÌ (chưa
        // /connect, chưa /sepaysetup, chưa /cardsetup) — đồng bộ với bản Bukkit.
        boolean sepayDone = config.getBoolean("sepay.configured", false);
        boolean cardDone  = config.getBoolean("card-api.configured", false);
        if (isStandaloneMode() && !sepayDone && !cardDone) {
            LOGGER.warn("[PayBot]   ⚠ CHƯA CẤU HÌNH GÌ CẢ — admin cần làm 1 trong các bước sau:");
            LOGGER.warn("[PayBot]     • /connect discord <guild_id>  (hệ thống auto qua Discord Bot)");
            LOGGER.warn("[PayBot]     • /sepaysetup                  (nạp ngân hàng standalone)");
            LOGGER.warn("[PayBot]     • /cardsetup                   (nạp thẻ cào standalone)");
            LOGGER.warn("[PayBot]     Mod sẽ KHÔNG xử lý được giao dịch nào cho tới khi hoàn tất 1 trong 3.");
        }
        LOGGER.info("[PayBot] ════════════════════════════════════════");
    }

    // ─── Server stop ──────────────────────────────────────────────────────────

    private void onServerStop() {
        LOGGER.info("[PayBot] Đang dừng…");
        if (scheduler        != null) scheduler.shutdownNow();
        if (pluginHttpServer != null) pluginHttpServer.stop();
        if (databaseManager  != null) databaseManager.close();
        LOGGER.info("[PayBot] Đã dừng.");
    }

    // ─── Background tasks ─────────────────────────────────────────────────────
    /**
     * [Anti-spam] Chiến lược mới:
     *
     * TRƯỚC: Plugin poll bot định kỳ mỗi 60s cho cả 3 endpoint, ngay cả khi không có gì mới.
     *
     * SAU:
     *   - /api/update-online-players → chỉ push khi player JOIN hoặc QUIT (event-driven)
     *   - /api/pending-rewards       → bot push về /api/reward-push (xem PluginHttpServer)
     *   - /api/standalone-bank-status / card-status → bot push về /api/bank-paid / /api/card-result
     *   - Fallback poll 10 phút/lần: chỉ khi đang có đơn PENDING chưa xử lý (an toàn)
     *   - retryUnregistered / retryConnectionErrors: giảm xuống 5 phút (trước là 30s)
     */
    private void startTasks() {
        // Recover pending orders sau 5s
        scheduler.schedule(() -> {
            standaloneBankPoller.recoverOnStartup();
            standaloneCardProcessor.recoverUnsubmitted();
        }, 5, TimeUnit.SECONDS);

        if (!isStandaloneMode()) {
            // Config: lấy 1 lần khi start, fallback mỗi 30 phút
            scheduler.schedule(() -> botHttpClient.fetchAndApplyConfig(), 10, TimeUnit.SECONDS);
            scheduler.scheduleAtFixedRate(() -> botHttpClient.fetchAndApplyConfig(),
                    600, 1800, TimeUnit.SECONDS);

            // v5.0.5 [Part 22]: đổi interval fallback poll từ 10 PHÚT → 10 GIÂY theo yêu
            // cầu — vẫn chỉ là lưới an toàn (bình thường bot push /api/reward-push ngay),
            // 10s giúp phát hiện reward bị lỡ NHANH hơn nhiều nếu push thất bại (server
            // offline lúc bot gửi, lỗi mạng thoáng qua, v.v.) mà không tốn kém gì nhiều —
            // query rỗng hầu hết các lần (return sớm nếu rewards.isEmpty(), không log).
            scheduler.scheduleAtFixedRate(() -> this.pollBotRewardsFallback(),
                    10, 10, TimeUnit.SECONDS);

            // KHÔNG còn pushOnlinePlayers định kỳ — event-driven qua onPlayerJoin/Quit
            // KHÔNG còn pollBotRewards mỗi 60s

        } else {
            // Standalone: safety fallback poll mỗi 10 phút
            // Bình thường bot sẽ push về /api/bank-paid và /api/card-result
            scheduler.scheduleAtFixedRate(() -> standaloneCardProcessor.pollPendingCards(),
                    600, 600, TimeUnit.SECONDS);
            scheduler.scheduleAtFixedRate(() -> standaloneBankPoller.pollPendingOrders(),
                    600, 600, TimeUnit.SECONDS);
        }

        // v5.0.2 FIX: SePay API self-poll (không cần webhook/bot).
        // Chỉ chạy nếu admin đã đặt api-token trong config.
        // interval đọc động từ config mỗi lần schedule (mặc định 10s).
        scheduler.scheduleAtFixedRate(() -> {
            try {
                String token = getConfig().getString("sepay-api.api-token", "").trim();
                if (!token.isEmpty()) standaloneBankPoller.pollSePayApiTransactions();
            } catch (Exception e) {
                LOGGER.debug("[PayBot] SePay API poll lỗi: " + e.getMessage());
            }
        }, 15, Math.max(5, getConfig().getInt("sepay-api.poll-interval-seconds", 10)), TimeUnit.SECONDS);

        // v5.0.5 [Part 18]: watcher tự phát hiện config.yml thay đổi (do push từ bot,
        // do admin tự sửa tay, hay do upgrade version) → tự reload + tự thêm key/comment
        // thiếu + tự xoá key thừa không còn tác dụng. Mặc định BẬT — tắt qua
        // "auto-config-sync: false" nếu admin không muốn (VD: đang tự debug config thủ công).
        final long[] lastConfigMtime = {getConfig().getConfigPath().toFile().lastModified()};
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (!getConfig().getBoolean("auto-config-sync", true)) return;
                long mtime = getConfig().getConfigPath().toFile().lastModified();
                if (mtime == lastConfigMtime[0]) return; // chưa đổi gì, khỏi làm gì cả
                lastConfigMtime[0] = mtime;
                int[] result = getConfig().sync();
                int added = result[0], removed = result[1];
                if (added > 0 || removed > 0) {
                    LOGGER.info("[PayBot] 🔄 Config đã thay đổi — tự reload xong. "
                            + "(+" + added + " key mới, -" + removed + " key thừa) "
                            + "[PayBot v" + getModVersion() + "]");
                }
            } catch (Exception e) {
                LOGGER.debug("[PayBot] Config watcher lỗi: " + e.getMessage());
            }
        }, 10, 10, TimeUnit.SECONDS);

        // Retry đơn chưa đăng ký (tăng lên 5 phút thay vì 30s)
        scheduler.scheduleAtFixedRate(() -> {
            try { standaloneBankPoller.retryUnregistered(); } catch (Exception ignored) {}
        }, 60, 300, TimeUnit.SECONDS);

        scheduler.scheduleAtFixedRate(() -> {
            try { standaloneCardProcessor.retryConnectionErrors(); } catch (Exception ignored) {}
        }, 65, 300, TimeUnit.SECONDS);

        // v5.0.0 (theo yêu cầu): check LOCAL (RAM, không qua mạng) mỗi 30s cho TỪNG
        // player đang online — bắt case reward được thêm vào lúc họ ĐÃ ONLINE rồi (vd
        // push tới /api/reward-push thất bại tạm thời, rơi vào offline-queue, nhưng họ
        // chưa hề rời server để có join-event mới kích hoạt check). Hoàn toàn local, KHÔNG
        // gọi bot — giữ nguyên pollBotRewardsFallback() ở cadence 10 phút/lần như cũ (đó
        // mới là request qua ngrok, tài nguyên CHUNG nhiều server khác cùng dùng).
        scheduler.scheduleAtFixedRate(() -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (offlineRewardManager.hasPendingRewards(p.getName().getString())) {
                    server.execute(() -> processOfflineRewards(p));
                }
            }
        }, 30, 30, TimeUnit.SECONDS);

        scheduler.scheduleAtFixedRate(() ->
                offlineRewardManager.checkAndExpireOldRewards(), 3600, 3600, TimeUnit.SECONDS);

        // v5.0.0 FIX: bankOrders/cardOrders (LocalOrderManager) trước đây chỉ được thêm,
        // không bao giờ xoá — phình vô hạn theo thời gian (cùng bug đã fix ở bản Bukkit).
        scheduler.scheduleAtFixedRate(() -> {
            try {
                int bankDays = getConfig().getInt("order-retention.bank-days", -1);
                int cardDays = getConfig().getInt("order-retention.card-days", -1);
                int removed  = localOrderManager.pruneOldOrders(bankDays, cardDays);
                if (removed > 0) {
                    PayBotMod.LOGGER.info("[PayBot] Đã dọn " + removed + " đơn cũ (đã xử lý xong, quá hạn lưu trữ) khỏi bộ nhớ.");
                }
            } catch (Exception ignored) {}
        }, 3600, 3600, TimeUnit.SECONDS);

        scheduler.scheduleAtFixedRate(() ->
                server.execute(() -> ownerSessionManager.tickExpiry(server)), 60, 60, TimeUnit.SECONDS);

        // Update check sau 30s
        scheduler.schedule(() -> updateCheckManager.checkForUpdates(), 30, TimeUnit.SECONDS);

        // Report IP thật lên bot sau 15s (sau khi BanManager đã cache IP)
        if (!isStandaloneMode()) {
            scheduler.schedule(() -> this.reportPublicIpToBot(), 15, TimeUnit.SECONDS);
            
            // v5.1.0: Tự động ping bot định kỳ mỗi 60 giây ở chế độ background để tự đồng bộ cổng khi bot thay đổi port
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    botHttpClient.pingBot();
                } catch (Exception ignored) {}
            }, 40, 60, TimeUnit.SECONDS);
        }

        // v5.3.3: Delayed check & warn nếu bật cả include-date-prefix và check-duplicate-orders sau khi server khởi động xong
        scheduler.schedule(() -> {
            boolean datePrefix = getConfig().getBoolean("transfer-content.include-date-prefix", false);
            boolean checkDup   = getConfig().getBoolean("transfer-content.check-duplicate-orders", true);
            if (datePrefix && checkDup) {
                LOGGER.warn("[PayBot] ⚠️ CẢNH BÁO TỐI ƯU HIỆU NĂNG: Đang bật ĐỒNG THỜI 'include-date-prefix: true' và 'check-duplicate-orders: true' trong transfer-content!");
                LOGGER.warn("[PayBot] Khi đã chèn ngày hiện tại (include-date-prefix), tỉ lệ trùng mã nạp ở mốc code-length >= 12 là CỰC KỲ THẤP (~0%).");
                LOGGER.warn("[PayBot] 📌 KHUYẾN CÁO HIỆU NĂNG:");
                LOGGER.warn("[PayBot]   • Dưới 50.000 đơn lưu trữ trong CSDL: Server xử lý mượt mà, bạn hoàn toàn có thể an tâm sử dụng.");
                LOGGER.warn("[PayBot]   • Trên 50.000 đơn lưu trữ trong CSDL: Nên đặt 'transfer-content.check-duplicate-orders: false' trong config.yml để tránh tốn tài nguyên truy vấn CSDL và tối ưu hiệu năng tối đa.");
            }
        }, 10, TimeUnit.SECONDS);
    }

    // ─── Bot reward polling (fallback an toàn) ────────────────────────────────

    /**
     * Fallback poll rewards từ bot mỗi 10 phút.
     * Bình thường bot sẽ push về /api/reward-push trong PluginHttpServer.
     * Fallback này chỉ chạy khi có kết nối và có thể có reward chưa push.
     */
    private void pollBotRewardsFallback() {
        List<com.google.gson.JsonObject> rewards = botHttpClient.fetchPendingRewards();
        if (rewards.isEmpty()) return;
        LOGGER.info("[PayBot] [Fallback] Nhận " + rewards.size() + " reward từ poll (bot chưa push).");
        for (com.google.gson.JsonObject r : rewards) {
            server.execute(() -> {
                String rewardId   = r.has("reward_id")     ? r.get("reward_id").getAsString()     : "";
                String playerName = r.has("player_name")   ? r.get("player_name").getAsString()   : "";
                String rawCmd     = r.has("reward_cmd")    ? r.get("reward_cmd").getAsString()     : "";
                if (playerName.isEmpty() || rawCmd.isEmpty()) return;

                int amt = 0;
                try {
                    if (r.has("reward_amount"))
                        amt = (int) Double.parseDouble(r.get("reward_amount").getAsString());
                } catch (Exception ignored) {}

                ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);
                if (player != null) {
                    executeReward(player, rawCmd, rewardId, false, amt);
                } else {
                    String type = r.has("type") ? r.get("type").getAsString() : "bank";
                    offlineRewardManager.addReward(rewardId, playerName, rawCmd,
                            String.valueOf(amt), "", type, "", "");
                }
            });
        }
    }

    // ─── Online players — event-driven, chỉ push khi thay đổi ────────────────

    /**
     * [Anti-spam] Push danh sách player lên bot KHI VÀ CHỈ KHI danh sách thay đổi.
     * Gọi từ onPlayerJoin() và onPlayerQuit() thay vì schedule mỗi 60s.
     */
    private void pushOnlinePlayersIfChanged() {
        if (isStandaloneMode()) return;
        Set<String> current = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            current.add(p.getName().getString());

        if (current.equals(lastOnlinePlayers)) return; // Không thay đổi → không push
        lastOnlinePlayers.clear();
        lastOnlinePlayers.addAll(current);

        final List<String> names = new ArrayList<>(current);
        runAsync(() -> botHttpClient.updateOnlinePlayers(names));
    }

    // ─── Report public IP lên bot ─────────────────────────────────────────────

    /**
     * [IP detection] Report public IP thật lên bot thay vì 0.0.0.0.
     * BanManager.detectPublicIp() gọi api.ipify.org / checkip.amazonaws.com / icanhazip.com.
     */
    private void reportPublicIpToBot() {
        String ip = BanManager.getCurrentPublicIp();
        if (ip == null || ip.isEmpty()) {
            LOGGER.warn("[PayBot] Không phát hiện được public IP để report.");
            return;
        }
        int mcPort = server.getPort();
        botHttpClient.reportServerIp(ip, mcPort);
        LOGGER.info("[PayBot] Đã report public IP: " + ip + ":" + mcPort);
    }

    // ─── executeReward ────────────────────────────────────────────────────────
    /**
     * Thực thi lệnh thưởng và hiện thông báo cho player.
     *
     * v5.0.0: replace {player}, [playername], [amount]; multi-command ;;
     * + Hiện title lớn + hiệu ứng pháo hoa khi amount > 0 (giống test mode)
     */
    public void executeReward(ServerPlayer player, String rawCmd,
                               String rewardId, boolean isOffline, int amount) {
        if (isNotifEnabled("reward-dispatch") && logFilter.allow("reward-dispatch"))
            LOGGER.info("[PayBot] Executing reward: player=" + player.getName().getString()
                    + " amount=" + amount + " cmd=" + rawCmd.substring(0, Math.min(40, rawCmd.length())));

        String[] cmds = rawCmd.split("(?:;;|§§|;|\\n)");
        for (String raw : cmds) {
            String cmd = raw.trim()
                    .replace("{player}",     player.getName().getString())
                    .replace("[playername]", player.getName().getString())
                    .replace("[amount]",     String.valueOf(amount));
            if (cmd.isEmpty()) continue;
            try {
                server.getCommands().getDispatcher().execute(cmd, server.createCommandSourceStack());
            } catch (Exception e) {
                if (isNotifEnabled("reward-invalid") && logFilter.allow("reward-invalid"))
                    LOGGER.warn("[PayBot] Lỗi lệnh thưởng '" + cmd.substring(0, Math.min(50, cmd.length()))
                            + "': " + e.getMessage());
            }
        }

        // Hiện title lớn + hiệu ứng pháo hoa khi nạp thật (amount > 0)
        if (amount > 0) {
            final int finalAmt = amount;
            runOnMainThread(() -> {
                ServerPlayer p = server.getPlayerList().getPlayer(player.getUUID());
                if (p == null) return;
                RewardEffectManager.trigger(this, p, finalAmt);
                RewardEffectManager.sendSuccessTitle(p, finalAmt);
            });
        }

        // Confirm reward với bot
        if (!isStandaloneMode()) {
            if (isOffline) scheduler.execute(() -> botHttpClient.confirmOfflineReward(rewardId));
            else           scheduler.execute(() -> botHttpClient.confirmReward(rewardId));
        }
    }

    /** Overload tương thích ngược — amount = 0, không có title/hiệu ứng. */
    public void executeReward(ServerPlayer player, String rawCmd,
                               String rewardId, boolean isOffline) {
        executeReward(player, rawCmd, rewardId, isOffline, 0);
    }

    // ─── Reward helpers (v5.0.2) ──────────────────────────────────────────────

    /**
     * Resolve danh sách lệnh thưởng cho amount + type ("bank"/"card").
     * Ưu tiên: denom-rewards-{type}.{amount}.cmd → reward-command-{type} → reward-command.
     * Trả về list rỗng nếu chưa cấu hình.
     */
    public int parseRewardMode(String type) {
        String key = "Rewards_" + ("bank".equalsIgnoreCase(type) ? "Bank" : "Card") + "_Mode";
        Object raw = getConfig().get(key);
        if (raw == null) return 1;
        String rawStr = String.valueOf(raw).trim().replace(',', '.');
        if (rawStr.endsWith("x") || rawStr.endsWith("X")) {
            rawStr = rawStr.substring(0, rawStr.length() - 1).trim();
        }
        try {
            double val = Double.parseDouble(rawStr);
            int mode = (int) Math.round(val);
            if (mode < 1) mode = 1;
            if (Math.abs(val - mode) > 0.001) {
                LOGGER.warn("[PayBot] CẢNH BÁO: " + key + " có giá trị lẻ '" + raw + "', đã tự động làm tròn thành " + mode + ".");
            }
            return mode;
        } catch (Exception e) {
            return 1;
        }
    }

    public List<String> resolveRewardCmds(int amount, String type) {
        // 1. Thử denom-rewards-{type}.{amount}.cmd (multi-command ngăn cách bằng ;;)
        String denomKey = "denom-rewards-" + type + "." + amount + ".cmd";
        String denomCmd = getConfig().getString(denomKey, "").trim();
        if (!denomCmd.isEmpty()) {
            List<String> cmds = new java.util.ArrayList<>();
            for (String c : denomCmd.split("(?:;;|§§|;|\\n)")) { String t = c.trim(); if (!t.isEmpty()) cmds.add(t); }
            if (!cmds.isEmpty()) return cmds;
        }
        // 2. Fallback: reward-command-{type}
        String typeCmd = getConfig().getString("reward-command-" + type, "").trim();
        if (!typeCmd.isEmpty()) {
            List<String> cmds = new java.util.ArrayList<>();
            for (String c : typeCmd.split("(?:;;|§§|;|\\n)")) { String t = c.trim(); if (!t.isEmpty()) cmds.add(t); }
            if (!cmds.isEmpty()) return cmds;
        }
        // 3. Fallback cuối: reward-command (chung)
        String globalCmd = getConfig().getString("reward-command", "").trim();
        if (!globalCmd.isEmpty()) {
            List<String> cmds = new java.util.ArrayList<>();
            for (String c : globalCmd.split("(?:;;|§§|;|\\n)")) { String t = c.trim(); if (!t.isEmpty()) cmds.add(t); }
            return cmds;
        }
        return java.util.Collections.emptyList();
    }

    /**
     * Tính số lượng thưởng (đã nhân reward mode) cho amount + type.
     * Trả về chuỗi số hoặc "" nếu chưa cấu hình amt.
     */
    public String computeRewardAmt(int amount, String type) {
        String amtRaw = getConfig().getString("denom-rewards-" + type + "." + amount + ".amt", "").trim();
        if (amtRaw.isEmpty()) return String.valueOf(amount);
        try {
            int mode = getConfig().getInt("Rewards_" + (type.equals("bank") ? "Bank" : "Card") + "_Mode", 1);
            long base = Long.parseLong(amtRaw);
            return String.valueOf(base * Math.max(1, mode));
        } catch (NumberFormatException e) { return amtRaw; }
    }

    /**
     * Dispatch reward cho player (nếu online) hoặc queue vào OfflineRewardManager (nếu offline).
     * @return true nếu player online và đã dispatch ngay
     */
    public boolean dispatchOrQueueReward(String rewardId, String playerName, List<String> rewardCmds,
                                          String rewardAmt, String rawAmount, String type) {
        int mode = parseRewardMode("Rewards_" + (type.equals("bank") ? "Bank" : "Card") + "_Mode");
        List<String> modeCmds = new java.util.ArrayList<>();
        for (int m = 0; m < mode; m++) {
            modeCmds.addAll(rewardCmds);
        }
        String combinedCmd = String.join(";;", modeCmds);
        ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);
        if (player != null) {
            // Online — dispatch ngay
            int amtInt = 0;
            try { amtInt = Integer.parseInt(rawAmount); } catch (NumberFormatException ignored) {}
            final int finalAmt = amtInt;
            final ServerPlayer fp = player;
            server.execute(() -> executeReward(fp, combinedCmd, rewardId, false, finalAmt));
            return true;
        } else {
            // Offline — queue vào OfflineRewardManager
            offlineRewardManager.addReward(rewardId, playerName, combinedCmd, rewardAmt, rawAmount, type, rewardId, "");
            return false;
        }
    }

    /** Kích hoạt hiệu ứng pháo hoa/âm thanh/title khi nạp thành công — gọi từ standalone. */
    public void runRewardEffect(ServerPlayer player, int amount) {
        RewardEffectManager.trigger(this, player, amount);
        RewardEffectManager.sendSuccessTitle(player, amount);
    }

    // ─── Player join / quit ───────────────────────────────────────────────────

    private void onPlayerJoin(ServerPlayer player) {
        // v5.0.2: cảnh báo enforce-secure-profile cho admin/OP khi join
        if (player.hasPermissions(2) || ownerSessionManager.isOwner(player)) {
            scheduler.schedule(() -> checkEnforceSecureProfileWarn(player), 3, TimeUnit.SECONDS);
        }

        // v5.0.2 FIX: quét + xoá QR map hết hạn (>30 phút từ lúc tạo đơn).
        // Cơ chế cũ dùng scheduler.schedule() 1 lần — nếu player offline trước khi hết hạn,
        // task chạy nhưng không làm gì (player offline), và không tự chạy lại → QR kẹt mãi.
        // Fix: quét khi player JOIN (lúc này chắc chắn có thể sửa inventory).
        server.execute(() -> cleanExpiredQRMapsOnJoin(player));

        // Thông báo update cho admin khi có phiên bản mới
        if ((player.hasPermissions(2) || ownerSessionManager.isOwner(player))
                && UpdateCheckManager.isUpdateAvailable()) {
            scheduler.schedule(() ->
                    server.execute(() -> UpdateCheckManager.notifyAdmin(player)),
                    2, TimeUnit.SECONDS);
        }

        // Xử lý reward offline
        if (offlineRewardManager.hasPendingRewards(player.getName().getString())) {
            scheduler.schedule(() -> processOfflineRewards(player), 3, TimeUnit.SECONDS);
        }

        // Tự động kiểm tra đơn nạp standalone đang chờ duyệt khi player join
        if (isStandaloneMode()) {
            scheduler.schedule(() -> checkAndRewardPendingLocalOrders(player), 4, TimeUnit.SECONDS);
        }

        // [Anti-spam] Push player list chỉ khi có thay đổi
        server.execute(() -> this.pushOnlinePlayersIfChanged());

        // Cập nhật command tree
        server.getPlayerList().sendPlayerPermissionLevel(player);
    }

    private void processOfflineRewards(ServerPlayer player) {
        List<Map<String, String>> rewards =
                offlineRewardManager.getRewardsForPlayer(player.getName().getString());
        for (Map<String, String> r : rewards) {
            String rewardId = r.getOrDefault("rewardId", "");
            String rawCmd   = r.getOrDefault("rewardCmd", "");
            String amtStr   = r.getOrDefault("rewardAmount", "0");
            if (rawCmd.isEmpty()) continue;

            int amt = 0;
            try { amt = Integer.parseInt(amtStr); } catch (Exception ignored) {}
            final int finalAmt = amt;

            // v5.0.0 (fix): "claim" (xoá khỏi storage) TRƯỚC, chỉ thực thi lệnh reward
            // NẾU thực sự claim được — removeReward() giờ synchronized + trả boolean,
            // đảm bảo atomic dù processOfflineRewards() được gọi từ nhiều nguồn gần như
            // đồng thời (lúc join VÀ lúc auto-poll 30s — xem startAutoRewardPoll()).
            // Claim ở ĐÂY (thread gọi method này, có thể không phải main thread) — an
            // toàn vì offlineRewardManager hoàn toàn không đụng Bukkit/Fabric API.
            boolean claimed = offlineRewardManager.removeReward(player.getName().getString(), rewardId);
            if (!claimed) continue; // nguồn khác đã giao rồi — bỏ qua, không giao trùng

            server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayer(player.getUUID());
                if (p == null) return;
                // executeReward() đã tự trigger effects nếu amount > 0
                executeReward(p, rawCmd, rewardId, true, finalAmt);

                if (isNotifEnabled("reward-queued-offline") && logFilter.allow("reward-queued-offline"))
                    LOGGER.info("[PayBot] Offline reward đã giao cho " + p.getName().getString()
                            + " — amount=" + finalAmt);
            });
        }
    }

    public void checkAndRewardPendingLocalOrders(ServerPlayer player) {
        if (!isStandaloneMode()) return;
        String name = player.getName().getString();

        // 1. Bank orders
        for (LocalOrderManager.BankOrder order : localOrderManager.getAllBankOrders()) {
            if (name.equalsIgnoreCase(order.playerName) && LocalOrderManager.BANK_PAID.equals(order.status)) {
                List<String> rewardCmds = resolveRewardCmds(order.amount, "bank");
                if (!rewardCmds.isEmpty()) {
                    int mode = parseRewardMode("Rewards_Bank_Mode");
                    List<String> modeCmds = new java.util.ArrayList<>();
                    for (int m = 0; m < mode; m++) modeCmds.addAll(rewardCmds);
                    String combinedCmd = String.join(";;", modeCmds);
                    localOrderManager.updateBankStatus(order.invoiceId, LocalOrderManager.BANK_APPROVED);

                    server.execute(() -> {
                        ServerPlayer p = server.getPlayerList().getPlayer(player.getUUID());
                        if (p == null) return;
                        executeReward(p, combinedCmd, order.invoiceId, false, order.amount);

                        notifyAdmins("§a[PayBot] §e" + order.playerName + " §fnạp bank §a"
                                + PayBotMod.formatVnd(order.amount) + " VND §f— thưởng tự giao khi join (standalone)§f.");
                    });
                }
            }
        }

        // 2. Card orders
        for (LocalOrderManager.CardOrder order : localOrderManager.getAllCardOrders()) {
            if (name.equalsIgnoreCase(order.playerName) && (LocalOrderManager.CARD_SUCCESS.equals(order.status) || "1".equals(order.status))) {
                List<String> rewardCmds = resolveRewardCmds(order.denom, "card");
                if (!rewardCmds.isEmpty()) {
                    int mode = parseRewardMode("Rewards_Card_Mode");
                    List<String> modeCmds = new java.util.ArrayList<>();
                    for (int m = 0; m < mode; m++) modeCmds.addAll(rewardCmds);
                    String combinedCmd = String.join(";;", modeCmds);
                    localOrderManager.updateCardStatus(order.requestId, LocalOrderManager.CARD_APPROVED, order.message);

                    server.execute(() -> {
                        ServerPlayer p = server.getPlayerList().getPlayer(player.getUUID());
                        if (p == null) return;
                        executeReward(p, combinedCmd, order.requestId, false, order.denom);

                        String denomStr = order.telco + " " + PayBotMod.formatVnd(order.denom);
                        notifyAdmins("§a[PayBot] §fThẻ §e" + order.playerName + " §f" + denomStr
                                + " §athành công ✓ — thưởng tự giao khi join (standalone)§f.");
                    });
                }
            }
        }
    }

    private void onPlayerQuit(ServerPlayer player) {
        com.naptien.gui.GuiSession.remove(player.getUUID());
        setupManager.clearSession(player);
        ownerSessionManager.onPlayerQuit(player);

        // [Anti-spam] Push player list sau khi player thoát (delay nhỏ để player đã out)
        if (scheduler != null && !scheduler.isShutdown()) {
            try {
                scheduler.schedule(() -> {
                    if (server != null) {
                        server.execute(() -> this.pushOnlinePlayersIfChanged());
                    }
                }, 1, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        }
    }

    // ─── enforce-secure-profile + online-mode — WARN, không crash ─────────────

    private void checkEnforceSecureProfile() {
        try {
            Path propsPath = FabricLoader.getInstance().getGameDir().resolve("server.properties");
            java.util.Properties props = new java.util.Properties();
            try (FileInputStream fis = new FileInputStream(propsPath.toFile())) { props.load(fis); }
            boolean secureProfile = !"false".equalsIgnoreCase(props.getProperty("enforce-secure-profile", "true").trim());
            boolean onlineMode    = !"false".equalsIgnoreCase(props.getProperty("online-mode", "true").trim());
            if (secureProfile && onlineMode) {
                LOGGER.warn("[PayBot] ─────────────────────────────────────────────");
                LOGGER.warn("[PayBot] CẢNH BÁO: Server đang bật online-mode + enforce-secure-profile,");
                LOGGER.warn("[PayBot] vui lòng tắt 1 trong 2 để plugin/mod chạy đúng.");
                LOGGER.warn("[PayBot] (online-mode=true bắt buộc xác thực Mojang/Microsoft + ký chat —");
                LOGGER.warn("[PayBot]  kết hợp enforce-secure-profile=true có thể xung đột với mod");
                LOGGER.warn("[PayBot]  khác/proxy chat-relay đang forward/sửa tin nhắn đã ký số.)");
                LOGGER.warn("[PayBot] ─────────────────────────────────────────────");
            } else if (secureProfile) {
                LOGGER.warn("[PayBot] ─────────────────────────────────────────────");
                LOGGER.warn("[PayBot] KHUYẾN NGHỊ: trong server.properties, đặt:");
                LOGGER.warn("[PayBot]   enforce-secure-profile=false");
                LOGGER.warn("[PayBot] (ngăn server log chat có chữ ký khi player");
                LOGGER.warn("[PayBot]  nhập mã thẻ/nội dung chuyển khoản)");
                LOGGER.warn("[PayBot] Mod VẪN chạy bình thường — không bắt buộc.");
                LOGGER.warn("[PayBot] ─────────────────────────────────────────────");
            }
        } catch (Exception e) {
            LOGGER.warn("[PayBot] Không đọc được server.properties: " + e.getMessage());
        }
    }

    /** Cooldown cảnh báo enforce-secure-profile per-player (10 phút/player, không spam). */
    private final java.util.concurrent.ConcurrentHashMap<java.util.UUID, Long>
            secureProfileWarnCooldown = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * v5.0.2 — Cảnh báo trực tiếp vào chat OP/admin khi join nếu enforce-secure-profile=true.
     * Cooldown 10 phút/player để không spam mỗi lần reconnect.
     */
    private void checkEnforceSecureProfileWarn(ServerPlayer player) {
        if (!player.isAlive()) return; // đã offline trước khi task chạy
        long now  = System.currentTimeMillis();
        long last = secureProfileWarnCooldown.getOrDefault(player.getUUID(), 0L);
        if (now - last < 10 * 60_000L) return;
        try {
            Path propsPath = FabricLoader.getInstance().getGameDir().resolve("server.properties");
            java.util.Properties props = new java.util.Properties();
            try (FileInputStream fis = new FileInputStream(propsPath.toFile())) { props.load(fis); }
            boolean secureProfile = !"false".equalsIgnoreCase(props.getProperty("enforce-secure-profile", "true").trim());
            boolean onlineMode    = !"false".equalsIgnoreCase(props.getProperty("online-mode", "true").trim());
            if (!secureProfile) return; // không cần cảnh báo
            secureProfileWarnCooldown.put(player.getUUID(), now);
            server.execute(() -> {
                if (!player.isAlive()) return;
                if (secureProfile && onlineMode) {
                    player.sendSystemMessage(Component.literal("§c§l[PayBot] §r§cCẢNH BÁO: §fenforce-secure-profile=true + online-mode=true đang bật!"));
                    player.sendSystemMessage(Component.literal("§7Kết hợp này có thể gây xung đột với PayBot (middleware chat-relay, chữ ký chat)."));
                    player.sendSystemMessage(Component.literal("§7→ Vào server.properties và đặt §fenforce-secure-profile=§cfalse §7(khuyến nghị)"));
                    player.sendSystemMessage(Component.literal("§7hoặc tắt §fonline-mode §7(nếu server offline chủ ý)."));
                } else {
                    player.sendSystemMessage(Component.literal("§e[PayBot] §7Gợi ý: §fenforce-secure-profile=true §7đang bật. Khuyến nghị đặt §bfalse §ftrong server.properties §7để tránh rủi ro khi cài thêm proxy/mod chat."));
                }
            });
        } catch (Exception ignored) {}
    }

    /**
     * v5.0.2 FIX — Quét + xoá QR map hết hạn (>30 phút từ createdAt của đơn) khỏi
     * inventory khi player join. Cơ chế cũ (scheduler.schedule 1 lần) mất tác dụng nếu
     * player logout trước hạn hoặc server restart giữa chừng. Dùng BankOrder.createdAt
     * làm nguồn sự thật duy nhất — không cần map/cache riêng.
     *
     * Phải gọi từ main thread (thao tác inventory).
     */
    private void cleanExpiredQRMapsOnJoin(ServerPlayer player) {
        long now   = System.currentTimeMillis();
        long ttlMs = 30 * 60 * 1000L;
        var  inv   = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            String invId = com.naptien.utils.ItemTagCompat.getInvoiceId(stack);
            if (invId == null || invId.isEmpty()) continue;
            LocalOrderManager.BankOrder order = localOrderManager.getBankOrder(invId);
            if (order == null) continue; // đơn đã bị pruneOldOrders — bỏ qua
            if (now - order.createdAt >= ttlMs) {
                inv.removeItemNoUpdate(i);
                player.sendSystemMessage(Component.literal("§c[PayBot] §fQR chuyển khoản §e"
                        + invId.substring(0, Math.min(10, invId.length()))
                        + "...§f đã hết hạn (>30 phút), tự động xoá khỏi balo."));
                LOGGER.info("[PayBot] QR map hết hạn đã xoá lúc join: player="
                        + player.getName().getString() + " invoice=" + invId);
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    public boolean isNotifEnabled(String key) {
        return config.getBoolean("notifications." + key, true);
    }

    private void ensureServerId() {
        String id = config.getString("server-id", "").trim();
        if (id.isEmpty()) {
            id = java.util.UUID.randomUUID().toString();
            config.set("server-id", id);
            config.save();
            LOGGER.info("[PayBot] Auto-generated server-id: " + id);
        }
    }

    public boolean isStandaloneMode() {
        return config.getString("guild-id", "").trim().isEmpty();
    }

    /** @return true nếu PayBot bị ban và không hoạt động */
    public boolean isBanned() { return isBanned; }

    public void runOnMainThread(Runnable r) {
        if (server != null) server.execute(r);
    }

    public void runAsync(Runnable r) {
        if (scheduler != null && !scheduler.isShutdown()) scheduler.execute(r);
        else new Thread(r).start();
    }

    public void notifyAdmins(String legacyMsg) {
        Component text = Component.literal(legacyMsg);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.hasPermissions(2) || ownerSessionManager.isOwner(p))
                p.sendSystemMessage(text);
        }
        LOGGER.info(legacyMsg.replaceAll("§.", ""));
    }

    public static String formatVnd(int amount) {
        StringBuilder sb = new StringBuilder(String.valueOf(Math.abs(amount)));
        int len = sb.length();
        for (int i = len - 3; i > 0; i -= 3) sb.insert(i, '.');
        return (amount < 0 ? "-" : "") + sb;
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public static PayBotMod              getInstance()                { return INSTANCE; }
    public MinecraftServer               getServer()                  { return server; }
    public Path                          getDataDir()                 { return dataDir; }
    public PayBotConfig                  getConfig()                  { return config; }
    public LogManager                    getLogManager()              { return logManager; }
    public LogSpamFilter                 getLogFilter()               { return logFilter; }
    public BotHttpClient                 getBotHttpClient()           { return botHttpClient; }
    public CardManager                   getCardManager()             { return cardManager; }
    public DatabaseManager               getDatabaseManager()         { return databaseManager; }
    public LocalOrderManager             getLocalOrderManager()       { return localOrderManager; }
    public OfflineRewardManager          getOfflineRewardManager()    { return offlineRewardManager; }
    public OwnerSessionManager           getOwnerSessionManager()     { return ownerSessionManager; }
    public SetupManager                  getSetupManager()            { return setupManager; }
    public StandaloneCardProcessor       getStandaloneCardProcessor() { return standaloneCardProcessor; }
    public StandaloneBankPoller          getStandaloneBankPoller()    { return standaloneBankPoller; }
    public QRMapManager                  getQRMapManager()            { return qrMapManager; }
    public PlaceholderManager            getPlaceholderManager()      { return placeholderManager; }
    public PluginHttpServer              getPluginHttpServer()        { return pluginHttpServer; }
    public ScheduledExecutorService      getScheduler()               { return scheduler; }
    public UpdateCheckManager            getUpdateCheckManager()      { return updateCheckManager; }
}
