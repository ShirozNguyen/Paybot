package com.paybot.managers;

import com.paybot.PayBotPlugin;
import com.paybot.utils.SchedulerUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SetupManager implements Listener {

    private final PayBotPlugin plugin;
    private final Map<UUID, SetupSession> activeSessions = new ConcurrentHashMap<>();

    private volatile boolean setupInitiated = false;
    private volatile boolean sePayDone      = false;
    private volatile boolean cardDone       = false;

    public SetupManager(PayBotPlugin plugin) {
        this.plugin = plugin;
        sePayDone = isSePayConfigured();
        cardDone  = isCardApiConfigured();
    }

    // ─── Session management ───────────────────────────────────────────────────

    public interface SetupSession {
        boolean handleInput(Player player, String input);
    }

    public boolean isInSession(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    public void startSession(Player player, SetupSession session) {
        activeSessions.put(player.getUniqueId(), session);
    }

    public void endSession(Player player) {
        activeSessions.remove(player.getUniqueId());
    }

    // ─── State flags ──────────────────────────────────────────────────────────

    public boolean isSetupInitiated() { return setupInitiated; }
    public void    markSetupInitiated() { setupInitiated = true; }

    public boolean isSePayDone() { return sePayDone; }
    public boolean isCardDone()  { return cardDone; }

    public void onSePayDone(Player player) {
        sePayDone = true;
        plugin.getConfig().set("sepay.configured", true);
        plugin.forceConfigRefresh("cấu hình SePay (hoàn tất wizard)");
        if (cardDone) showBothDoneMessage(player);
        else player.sendMessage("§7Tiếp theo dùng §e/Cardsetup §7để cấu hình API gạch thẻ.");
    }

    public void onCardDone(Player player) {
        cardDone = true;
        plugin.getConfig().set("card-api.configured", true);
        plugin.forceConfigRefresh("cấu hình web đổi thẻ (hoàn tất wizard)");
        if (sePayDone) showBothDoneMessage(player);
        else player.sendMessage("§7Tiếp theo dùng §e/SePaysetup §7để cấu hình SePay.");
    }

    private void showBothDoneMessage(Player player) {
        player.sendMessage("§6§l══════════════════════════════════════");
        player.sendMessage("§a§l✓ §fĐã setup xong! Bạn có thể dùng:");
        player.sendMessage("§e  /bankcheck §7— xem danh sách đơn nạp ngân hàng");
        player.sendMessage("§e  /cardcheck §7— xem danh sách đơn nạp thẻ");
        player.sendMessage("§e  /approve <id> §7— xác nhận và thực thi đơn nạp");
        player.sendMessage("§e  /bankcheck <id> §7hoặc §e/cardcheck <id> §7— chi tiết đơn");
        player.sendMessage("§7Nếu muốn setup hệ thống tự động giao dịch, vui lòng");
        player.sendMessage("§7quay lại trang bạn tải plugin này và inbox admin.");
        player.sendMessage("§6§l══════════════════════════════════════");
    }

    // ─── Config helpers ───────────────────────────────────────────────────────

    public boolean isSePayConfigured() {
        // v7.0.1: Hỗ trợ cả key mới v7.0 (sepay.*) và key v5/v6 (sepay-api.*, sepay.configured)
        String token = plugin.getConfig().getString("sepay.api-token", "").trim();
        if (token.isEmpty()) token = plugin.getConfig().getString("sepay-api.api-token", "").trim();

        String stk = plugin.getConfig().getString("sepay.account-number", "").trim();
        if (stk.isEmpty() || "(Chưa đặt)".equals(stk)) stk = plugin.getConfig().getString("sepay.bank-account", "").trim();
        if (stk.isEmpty()) stk = plugin.getConfig().getString("sepay-api.bank-account-number", "").trim();

        String bank = plugin.getConfig().getString("sepay.bank-name", "").trim();
        if (bank.isEmpty()) bank = plugin.getConfig().getString("sepay-api.bank-short-name", "").trim();

        boolean enabled = plugin.getConfig().getBoolean("sepay.enabled", plugin.getConfig().getBoolean("sepay-api.enabled", true));

        // Đã cấu hình nếu có API token HOẶC có đầy đủ STK + Tên ngân hàng nhận tiền
        if (!token.isEmpty() && enabled) return true;
        return (!stk.isEmpty() && !bank.isEmpty() && enabled);
    }

    public boolean isCardApiConfigured() {
        // v7.0.1: Hỗ trợ cả key mới (card.*) và key cũ (card-api.*)
        String site = plugin.getConfig().getString("card.site", "").trim();
        if (site.isEmpty()) site = plugin.getConfig().getString("card.partner-url", "").trim();
        if (site.isEmpty()) site = plugin.getConfig().getString("card-api.site", "").trim();

        String partnerId = plugin.getConfig().getString("card.partner-id", "").trim();
        if (partnerId.isEmpty() || "(Chưa đặt)".equals(partnerId)) partnerId = plugin.getConfig().getString("card-api.partner-id", "").trim();

        String partnerKey = plugin.getConfig().getString("card.partner-key", "").trim();
        if (partnerKey.isEmpty() || "(Chưa đặt)".equals(partnerKey)) partnerKey = plugin.getConfig().getString("card-api.partner-key", "").trim();

        boolean enabled = plugin.getConfig().getBoolean("card.enabled", plugin.getConfig().getBoolean("card-api.configured", true));

        return enabled && !site.isEmpty() && !partnerId.isEmpty() && !partnerKey.isEmpty()
                && !"(Chưa đặt)".equals(partnerId) && !"(Chưa đặt)".equals(partnerKey);
    }

    // ─── Chat listener ────────────────────────────────────────────────────────

    /**
     * Intercepts chat input during an active setup session.
     *
     * Uses {@link AsyncPlayerChatEvent} (Bukkit, works 1.12+) instead of
     * Paper-only {@code AsyncChatEvent} — deprecated on Paper 1.19+ but still
     * functional on all versions including modern Paper servers.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        SetupSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;
        event.setCancelled(true);
        // event.getMessage() works on all versions; no Adventure dependency needed
        String input = event.getMessage().trim();
        SchedulerUtils.runForPlayer(plugin, player, () -> {
            boolean done = session.handleInput(player, input);
            if (done) activeSessions.remove(player.getUniqueId());
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        activeSessions.remove(event.getPlayer().getUniqueId());
    }
}
