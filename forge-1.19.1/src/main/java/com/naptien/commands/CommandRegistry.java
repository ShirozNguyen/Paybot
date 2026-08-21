package com.naptien.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.naptien.PayBotMod;
import com.naptien.gui.*;
import com.naptien.managers.*;
import com.naptien.managers.BanManager;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * CommandRegistry — đăng ký tất cả lệnh PayBot bằng Brigadier.
 *
 * v5.0.0:
 *   - /topuplist [all] → ALL orders (bank + thẻ)
 *   - /bankcheck [player] → GUI CHỈ đơn bank
 *   - /cardcheck [player] → GUI CHỈ đơn thẻ
 *   - /paybotleaderboard → PayBotPlaceholderGui (mọi player) [v5.0.3: đổi tên từ /paybotplaceholder]
 *   - /testnapbank → TestPaymentGui bank (OP only)
 *   - /testnapthe  → TestPaymentGui thẻ (OP only)
 *   - Fix [playername] + [amount] → resolveRewardCmd()
 *
 * + /disablepaybot (owner session only) — vô hiệu hóa PayBot trên server này
 * + /enablepaybot (owner session only)  — hủy vô hiệu hóa
 * + /approve: thêm sendSuccessTitle() khi duyệt đơn thành công
 */
public class CommandRegistry {

    public static void registerAll(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerNapThe(dispatcher);
        registerNapBank(dispatcher);
        registerOk(dispatcher);
        registerChinhSua(dispatcher);
        registerTopupList(dispatcher);
        registerCardSetup(dispatcher);
        registerSePaySetup(dispatcher);
        registerPayBotSetup(dispatcher);
        registerConnect(dispatcher);
        registerDisconnect(dispatcher);
        registerConfirm(dispatcher);
        registerBankCheck(dispatcher);
        registerCardCheck(dispatcher);
        registerApprove(dispatcher);
        registerNapTienId(dispatcher);
        registerMuaPayBot(dispatcher);
        registerPayBotOwner(dispatcher);
        registerPayBotPlaceholder(dispatcher);
        registerTestNapBank(dispatcher);
        registerTestNapThe(dispatcher);
        registerDisablePayBot(dispatcher);
        registerEnablePayBot(dispatcher);
        registerPayBotReload(dispatcher);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static boolean isAdmin(CommandSourceStack src) {
        if (src.hasPermission(2)) return true;
        try { return PayBotMod.getInstance().getOwnerSessionManager().isOwner(src.getPlayer()); }
        catch (Exception e) { return false; }
    }

    private static void send(CommandSourceStack src, String msg) {
        src.sendSystemMessage(Component.literal(msg));
    }

    /**
     * Resolve lệnh thưởng: fallback chain + replace [playername]/[amount] + multiplier.
     * Fix v5.0.0: thay đủ {player}, [playername], [amount].
     */
    private static String resolveRewardCmd(PayBotMod mod, String denomSection,
                                           String modeKey, String playerName, int denomAmount) {
        String cmd    = mod.getConfig().getString(denomSection + "." + denomAmount + ".cmd", "").trim();
        String rawAmt = mod.getConfig().getString(denomSection + "." + denomAmount + ".amt", "").trim();

        if (cmd.isEmpty()) {
            String fallback = denomSection.contains("card") ? "reward-command-card" : "reward-command-bank";
            cmd = mod.getConfig().getString(fallback, "").trim();
        }
        if (cmd.isEmpty()) cmd = mod.getConfig().getString("reward-command", "").trim();

        int mode      = Math.max(1, mod.getConfig().getInt(modeKey, 1));
        int rewardAmt = 0;
        try { if (!rawAmt.isEmpty()) rewardAmt = (int)(Double.parseDouble(rawAmt) * mode); }
        catch (NumberFormatException ignored) {}

        return cmd
                .replace("{player}",    playerName)
                .replace("[playername]", playerName)
                .replace("[amount]",    String.valueOf(rewardAmt));
    }

    // ─── /napthe ──────────────────────────────────────────────────────────────
    private static void registerNapThe(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("napthe")
            .requires(src -> src.isPlayer())
            .executes(ctx -> { NapTheGui.openTelcoGui(ctx.getSource().getPlayer()); return 1; }));
    }

    // ─── /napbank [amount] ────────────────────────────────────────────────────
    private static void registerNapBank(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("napbank")
            .requires(src -> src.isPlayer())
            .executes(ctx -> { NapBankGui.open(ctx.getSource().getPlayer()); return 1; })
            .then(Commands.argument("amount", StringArgumentType.word())
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    String amtStr = StringArgumentType.getString(ctx, "amount").toLowerCase()
                            .replace("k","000").replace("m","000000");
                    try {
                        int amount = Integer.parseInt(amtStr);
                        if (amount < 10000) {
                            send(ctx.getSource(), "§c[PayBot] §fSố tiền tối thiểu 10.000 VND");
                            return 0;
                        }
                        PayBotMod mod = PayBotMod.getInstance();
                        // v5.1.0: Luôn tạo QR trực tiếp (không phụ thuộc bot).
                        // Nếu bot-connected: bot nhận thông báo sau khi SePay poll phát hiện thanh toán.
                        mod.runAsync(() -> mod.getStandaloneBankPoller().createBankOrder(p, amount));
                    } catch (NumberFormatException e) {
                        NapBankGui.open(p);
                    }
                    return 1;
                })));
    }

    // ─── /ok ──────────────────────────────────────────────────────────────────
    private static void registerOk(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("ok")
            .requires(src -> src.isPlayer())
            .executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayer();
                PayBotMod mod = PayBotMod.getInstance();
                CardManager.PendingCard card = mod.getCardManager().getPending(p.getName().getString());
                if (card == null) {
                    send(ctx.getSource(), "§c[PayBot] §fKhông có thẻ nào đang chờ. Dùng §e/napthe §fđể bắt đầu.");
                    return 0;
                }
                if (!mod.getConfig().getBoolean("card-api.configured", false)) {
                    send(ctx.getSource(), "§c[PayBot] §fServer chưa cấu hình Card API. Admin dùng §e/cardsetup§f.");
                    return 0;
                }
                send(ctx.getSource(), "§a[PayBot] §fĐang gửi thẻ lên hệ thống...");
                // v5.1.0: Luôn gửi thẻ qua StandaloneCardProcessor trực tiếp (không phụ thuộc bot).
                // Nếu bot-connected: bot nhận thông báo sau khi card API trả kết quả.
                mod.runAsync(() -> mod.getStandaloneCardProcessor().submitCard(p, card));
                return 1;
            }));
    }

    // ─── /chinhsuamenhgianap ──────────────────────────────────────────────────
    private static void registerChinhSua(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("chinhsuamenhgianap")
            .requires(src -> src.isPlayer() && isAdmin(src))
            .executes(ctx -> { ChinhSuaGui.open(ctx.getSource().getPlayer()); return 1; }));
    }

    // ─── /topuplist [all|page] ────────────────────────────────────────────────
    // Hiện TẤT CẢ đơn (bank + thẻ). /bankcheck và /cardcheck mở GUI riêng.
    private static void registerTopupList(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("topuplist")
            .requires(src -> src.isPlayer() && isAdmin(src))
            // /topuplist          → trang 1, all
            .executes(ctx -> { TopupListGui.open(ctx.getSource().getPlayer(), 0); return 1; })
            // /topuplist all      → trang 1, all (alias rõ ràng)
            .then(Commands.literal("all")
                .executes(ctx -> { TopupListGui.open(ctx.getSource().getPlayer(), 0); return 1; }))
            // /topuplist <page>   → trang N, all
            .then(Commands.argument("page", IntegerArgumentType.integer(1))
                .executes(ctx -> {
                    TopupListGui.open(ctx.getSource().getPlayer(),
                            IntegerArgumentType.getInteger(ctx, "page") - 1);
                    return 1;
                })));
    }

    // ─── /cardsetup [site] ────────────────────────────────────────────────────
    // v5.0.2: bỏ CardApiSetupGui (GUI click), dùng chat-based flow (SetupManager)
    // giống plugin. Gộp bước channel-id vào luôn (không cần /cardapisetup riêng nữa).
    private static void registerCardSetup(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("cardsetup")
            .requires(src -> src.isPlayer() && isAdmin(src))
            .executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayer();
                PayBotMod mod = PayBotMod.getInstance();
                SetupManager.Session s = mod.getSetupManager().startSession(p);
                s.step = SetupManager.Step.CARD_SITE;
                String sites = String.join(", ", com.naptien.managers.StandaloneCardProcessor.getSupportedSites().keySet());
                p.sendSystemMessage(Component.literal("§6§l═══ Cấu hình Card API ═══"));
                p.sendSystemMessage(Component.literal("§e[1/4] §fNhập §bsite §fgạch thẻ:"));
                p.sendSystemMessage(Component.literal("§7Có thể dùng: §f" + sites));
                p.sendSystemMessage(Component.literal("§7(cancel để huỷ)"));
                return 1;
            })
            .then(Commands.argument("site", StringArgumentType.word())
                // v5.0.3: tab-complete danh sách site hỗ trợ (Brigadier suggests — API ổn
                // định, KHÔNG dùng ClickEvent để tránh rủi ro như ghi chú ở /connect phía
                // trên). Admin gõ "/cardsetup " rồi Tab là ra danh sách bấm chọn được.
                .suggests((ctx, builder) -> {
                    for (String s : com.naptien.managers.StandaloneCardProcessor.getSupportedSites().keySet()) {
                        if (s.startsWith(builder.getRemaining().toLowerCase())) builder.suggest(s);
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    String site = StringArgumentType.getString(ctx, "site").toLowerCase().trim();
                    if (!com.naptien.managers.StandaloneCardProcessor.getSupportedSites().containsKey(site)) {
                        send(ctx.getSource(), "§c[PayBot] §fSite không hợp lệ. Dùng /cardsetup để xem danh sách.");
                        return 0;
                    }
                    PayBotMod mod = PayBotMod.getInstance();
                    SetupManager.Session s = mod.getSetupManager().startSession(p);
                    s.apiSite = site;
                    s.step    = SetupManager.Step.CARD_PARTNER_ID;
                    p.sendSystemMessage(Component.literal("§a[PayBot] §eSite: §f" + site));
                    p.sendSystemMessage(Component.literal("§e[2/4] §fNhập §bPartner ID §f(lấy từ " + site + "):"));
                    return 1;
                })));
    }

    // ─── /sepaysetup ──────────────────────────────────────────────────────────
    private static void registerSePaySetup(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("sepaysetup")
            .requires(src -> src.isPlayer() && isAdmin(src))
            .executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayer();
                PayBotMod mod = PayBotMod.getInstance();
                SetupManager.Session s = mod.getSetupManager().startSession(p);
                s.step = SetupManager.Step.SEPAY_ASK_TUTORIAL;
                p.sendSystemMessage(Component.literal("§6§l═══ Cấu hình SePay API ═══"));
                p.sendSystemMessage(Component.literal("§7Chỉ cần 1 API Token — không cần webhook, không cần bot/ngrok."));
                p.sendSystemMessage(Component.literal(""));
                p.sendSystemMessage(Component.literal("§eBạn có muốn xem hướng dẫn từng bước lấy API Token không?"));
                p.sendSystemMessage(Component.literal("§a  yes  §7→ xem hướng dẫn (từng bước, gõ §fnext §7để qua bước tiếp)"));
                p.sendSystemMessage(Component.literal("§b  skip §7→ bỏ qua, nhập API Token luôn (nếu bạn đã có sẵn)"));
                return 1;
            }));
    }

    // ─── /paybotsetup ─────────────────────────────────────────────────────────
    private static void registerPayBotSetup(CommandDispatcher<CommandSourceStack> d) {
        // v5.0.5 [Part 21] FIX: literal TRƯỚC ĐÂY là "PayBotSetup" (có chữ hoa) — khác
        // với MỌI lệnh khác trong file này đều toàn chữ thường. Brigadier match literal
        // theo kiểu case-SENSITIVE, nên gõ "/paybotsetup" (tự nhiên như mọi lệnh khác)
        // sẽ báo "Unknown command" — phải gõ đúng "/PayBotSetup" hoa thường chính xác
        // mới chạy được, cực kỳ dễ gây nhầm lẫn cho admin. Đổi về chữ thường cho đúng.
        d.register(Commands.literal("paybotsetup")
            .requires(src -> isAdmin(src))
            .executes(ctx -> {
                PayBotMod mod = PayBotMod.getInstance();
                boolean sepay = mod.getConfig().getBoolean("sepay.configured", false);
                boolean card  = mod.getConfig().getBoolean("card-api.configured", false);
                String botUrl = mod.getConfig().getString("bot-url", "").trim();
                boolean standalone = mod.isStandaloneMode();
                // v5.0.5: fix default port sai (25565 TRÙNG port Minecraft mặc định —
                // xem comment trong config-template.yml, default đúng phải là 25580).
                int port = mod.getConfig().getInt("plugin-port", 25580);
                send(ctx.getSource(), "§8§m────────────────────────────────────");
                send(ctx.getSource(), "§6§l  PayBot Forge §r§7— Setup Status §7(v" + PayBotMod.getModVersion() + ")");
                send(ctx.getSource(), "§8§m────────────────────────────────────");
                send(ctx.getSource(), "§7Chế độ  : " + (standalone ? "§a[Standalone]" : "§b[Bot-connected] §7— các cấu hình sau do bot quản lý"));
                if (!standalone) {
                    // v5.0.5 [Part 21]: thêm status bot-url/cert/auto-sync — trước đây
                    // không có, dù đây là phần quan trọng nhất của Bot-connected mode
                    // (đã bỏ ngrok, giờ HTTPS self-signed + TOFU cert-pinning).
                    String fp = mod.getConfig().getString("bot-cert-fingerprint", "").trim();
                    send(ctx.getSource(), "§7Bot URL : " + (botUrl.isEmpty()
                            ? "§c✗ Chưa có §7— dùng §e/connect §7để kết nối"
                            : "§a✓ " + botUrl));
                    if (!botUrl.isEmpty()) {
                        send(ctx.getSource(), "§7Cert    : " + (fp.isEmpty()
                                ? "§e⏳ Chưa xác thực lần nào (sẽ tự lưu ở request đầu tiên)"
                                : "§a✓ Đã pin fingerprint (TOFU)"));
                    }
                }
                send(ctx.getSource(), "§7Auto-sync config §7: " + (mod.getConfig().getBoolean("auto-config-sync", true)
                        ? "§a✓ Bật §7(tự thêm/xoá key mỗi 10s)" : "§c✗ Tắt"));
                send(ctx.getSource(), "§7SePay   : " + (sepay ? "§a✓ " + mod.getConfig().getString("sepay.bank-name","?") : "§c✗ Chưa — /sepaysetup"));
                send(ctx.getSource(), "§7Card API: " + (card ? "§a✓ " + mod.getConfig().getString("card-api.site","?") : "§c✗ Chưa — /cardsetup"));
                send(ctx.getSource(), "§7HTTP port (nhận lệnh từ bot) §7: §e" + port);
                send(ctx.getSource(), "§8§m────────────────────────────────────");
                return 1;
            }));
    }

    // ─── /connect discord <guild_id> ──────────────────────────────────────────
    private static void registerConnect(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("connect")
            .requires(src -> isAdmin(src))
            // v5.0.0 (theo yêu cầu): /connect KHÔNG kèm gì cả → gợi ý add bot. Hiện URL
            // dạng text thường (KHÔNG dùng ClickEvent) — client Minecraft (vanilla/Fabric)
            // tự nhận diện URL trong chat và tự làm thành link bấm được sẵn, không cần
            // styling thủ công (tránh rủi ro API Text/ClickEvent đổi giữa các bản nhỏ).
            .executes(ctx -> {
                send(ctx.getSource(), "§e[PayBot] §fCách dùng: §e/connect discord <guild_id>");
                send(ctx.getSource(), "§eNếu bạn muốn có hệ thống tự động thì vui lòng add bot tại "
                        + "https://discord.gg/QdE5uNYqrV §evà làm theo hướng dẫn");
                return 1;
            })
            .then(Commands.literal("discord")
                .then(Commands.argument("guild_id", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        String raw = StringArgumentType.getString(ctx, "guild_id").trim();
                        String[] parts = raw.split("\\s+");
                        String guildId = parts[0];
                        PayBotMod mod = PayBotMod.getInstance();

                        if (parts.length >= 2) {
                            String newBotUrl = parts[1].trim();
                            mod.getConfig().set("bot-url", newBotUrl);
                            mod.getConfig().save();
                            ctx.getSource().sendSystemMessage(Component.literal("§a[PayBot] §7Đã cập nhật bot-url thành: §e" + newBotUrl));
                        }

                        String existing = mod.getConfig().getString("guild-id", "").trim();
                        if (!existing.isEmpty()) {
                            send(ctx.getSource(), "§c[PayBot] §fPlugin đã kết nối với §e" + existing + "§f! Dùng /disconnect trước.");
                            return 0;
                        }
                        send(ctx.getSource(), "§7[PayBot] Đang kết nối với guild §e" + guildId + "§7...");
                        mod.runAsync(() -> {
                            String sid = mod.getBotHttpClient().connectToGuild(guildId);
                            if (sid == null) {
                                ctx.getSource().sendSystemMessage(Component.literal("§c[PayBot] §fKết nối thất bại!"));
                                return;
                            }
                            mod.getConfig().set("guild-id", guildId);
                            mod.getConfig().set("server-id", sid);
                            mod.getConfig().save();
                            ctx.getSource().sendSystemMessage(Component.literal("§a[PayBot] §fĐã kết nối với guild §e" + guildId));
                        });
                        return 1;
                    }))));
    }

    // ─── /disconnect [--force] ────────────────────────────────────────────────
    private static void registerDisconnect(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("disconnect")
            .requires(src -> isAdmin(src))
            .executes(ctx -> disconnectLogic(ctx.getSource(), false))
            .then(Commands.literal("--force")
                .executes(ctx -> disconnectLogic(ctx.getSource(), true))));
    }
    private static int disconnectLogic(CommandSourceStack src, boolean force) {
        PayBotMod mod = PayBotMod.getInstance();
        String guildId = mod.getConfig().getString("guild-id", "").trim();
        if (guildId.isEmpty()) { send(src, "§c[PayBot] §fPlugin chưa kết nối."); return 0; }
        if (force) { clearBotConfig(mod); send(src, "§a[PayBot] §fĐã ngắt kết nối cục bộ (force)."); return 1; }
        send(src, "§7[PayBot] Đang gửi yêu cầu ngắt kết nối...");
        mod.runAsync(() -> {
            boolean ok = mod.getBotHttpClient().requestDisconnect();
            if (ok) src.sendSystemMessage(Component.literal("§a[PayBot] §fYêu cầu gửi! Dùng §e/confirm §fđể xác nhận."));
            else    src.sendSystemMessage(Component.literal("§c[PayBot] §fGửi thất bại! Dùng §e/disconnect --force§f."));
        });
        return 1;
    }

    // ─── /confirm ─────────────────────────────────────────────────────────────
    private static void registerConfirm(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("confirm")
            .requires(src -> isAdmin(src))
            .executes(ctx -> {
                PayBotMod mod = PayBotMod.getInstance();
                if (mod.getConfig().getString("guild-id","").trim().isEmpty()) {
                    send(ctx.getSource(), "§c[PayBot] §fPlugin chưa kết nối!"); return 0; }
                mod.runAsync(() -> {
                    boolean ok = mod.getBotHttpClient().confirmDisconnect();
                    if (ok) { clearBotConfig(mod); ctx.getSource().sendSystemMessage(Component.literal("§a[PayBot] §fĐã ngắt kết nối.")); }
                    else    ctx.getSource().sendSystemMessage(Component.literal("§c[PayBot] §fXác nhận thất bại!"));
                });
                return 1;
            }));
    }

    private static void clearBotConfig(PayBotMod mod) {
        mod.getConfig().set("guild-id",  "");
        mod.getConfig().set("server-id", "");
        mod.getConfig().save();
    }

    // ─── /bankcheck [player] → GUI CHỈ đơn bank ───────────────────────────────
    private static void registerBankCheck(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("bankcheck")
            .requires(src -> src.isPlayer() && isAdmin(src))
            .executes(ctx -> {
                TopupListGui.openBankOnly(ctx.getSource().getPlayer(), 0, null);
                return 1;
            })
            .then(Commands.argument("player", StringArgumentType.word())
                .executes(ctx -> {
                    TopupListGui.openBankOnly(ctx.getSource().getPlayer(), 0,
                            StringArgumentType.getString(ctx, "player"));
                    return 1;
                })));
    }

    // ─── /cardcheck [player] → GUI CHỈ đơn thẻ ───────────────────────────────
    private static void registerCardCheck(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("cardcheck")
            .requires(src -> src.isPlayer() && isAdmin(src))
            .executes(ctx -> {
                TopupListGui.openCardOnly(ctx.getSource().getPlayer(), 0, null);
                return 1;
            })
            .then(Commands.argument("player", StringArgumentType.word())
                .executes(ctx -> {
                    TopupListGui.openCardOnly(ctx.getSource().getPlayer(), 0,
                            StringArgumentType.getString(ctx, "player"));
                    return 1;
                })));
    }

    // ─── /approve <id> ────────────────────────────────────────────────────────
    private static void registerApprove(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("approve")
            .requires(src -> isAdmin(src))
            .then(Commands.argument("id", StringArgumentType.word())
                .executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    PayBotMod mod = PayBotMod.getInstance();

                    // Bank
                    LocalOrderManager.BankOrder bank = mod.getLocalOrderManager().getBankOrder(id);
                    if (bank != null && (LocalOrderManager.BANK_PAID.equals(bank.status)
                            || LocalOrderManager.BANK_PENDING.equals(bank.status))) {
                        String cmd = resolveRewardCmd(mod, "denom-rewards-bank", "Rewards_Bank_Mode",
                                bank.playerName, bank.amount);
                        mod.getLocalOrderManager().updateBankStatus(bank.invoiceId, LocalOrderManager.BANK_APPROVED);
                        if (!cmd.isEmpty()) {
                            final String fc = cmd;
                            mod.runOnMainThread(() -> {
                                for (String c : fc.split(";;")) {
                                    String cc = c.trim();
                                    if (cc.isEmpty()) continue;
                                    try { mod.getServer().getCommands().getDispatcher()
                                            .execute(cc, mod.getServer().createCommandSourceStack()); }
                                    catch (Exception e) {
                                        if (mod.isNotifEnabled("approve-fail"))
                                            PayBotMod.LOGGER.error("[approve] error: " + e.getMessage());
                                    }
                                }
                            });
                        }
                        send(ctx.getSource(), "§a[PayBot] §fĐã duyệt bank đơn §e"
                                + bank.invoiceId.substring(0, 8) + "... §fcho §e" + bank.playerName);
                        mod.notifyAdmins("§a[PayBot] §fAdmin duyệt đơn bank §e" + bank.playerName
                                + " §f" + PayBotMod.formatVnd(bank.amount) + " VND");
                        ServerPlayer tp = mod.getServer().getPlayerList().getPlayerByName(bank.playerName);
                        if (tp != null) {
                            RewardEffectManager.trigger(mod, tp, bank.amount);
                            RewardEffectManager.sendSuccessTitle(tp, bank.amount);
                            tp.sendSystemMessage(Component.literal("§a[PayBot] §fĐơn nạp §e"
                                    + PayBotMod.formatVnd(bank.amount) + " VND §fđã duyệt! ♥"));
                        }
                        return 1;
                    }

                    // Card
                    LocalOrderManager.CardOrder card = mod.getLocalOrderManager().getCardOrder(id);
                    if (card != null && (LocalOrderManager.CARD_SUCCESS.equals(card.status)
                            || "1".equals(card.status))) {
                        String cmd = resolveRewardCmd(mod, "denom-rewards-card", "Rewards_Card_Mode",
                                card.playerName, card.denom);
                        mod.getLocalOrderManager().updateCardStatus(card.requestId, LocalOrderManager.CARD_APPROVED, "Admin approved");
                        if (!cmd.isEmpty()) {
                            final String fc = cmd;
                            mod.runOnMainThread(() -> {
                                for (String c : fc.split(";;")) {
                                    String cc = c.trim();
                                    if (cc.isEmpty()) continue;
                                    try { mod.getServer().getCommands().getDispatcher()
                                            .execute(cc, mod.getServer().createCommandSourceStack()); }
                                    catch (Exception e) {
                                        if (mod.isNotifEnabled("approve-fail"))
                                            PayBotMod.LOGGER.error("[approve] error: " + e.getMessage());
                                    }
                                }
                            });
                        }
                        send(ctx.getSource(), "§a[PayBot] §fĐã duyệt card đơn cho §e" + card.playerName);
                        ServerPlayer tp = mod.getServer().getPlayerList().getPlayerByName(card.playerName);
                        if (tp != null) {
                            RewardEffectManager.trigger(mod, tp, card.denom);
                            RewardEffectManager.sendSuccessTitle(tp, card.denom);
                            tp.sendSystemMessage(Component.literal("§a[PayBot] §fThẻ §e" + card.telco + " "
                                    + PayBotMod.formatVnd(card.denom) + " VND §fđã duyệt! ♥"));
                        }
                        return 1;
                    }

                    send(ctx.getSource(), "§c[PayBot] §fKhông tìm thấy đơn §e" + id + "§f hoặc đã xử lý.");
                    return 0;
                })));
    }

    // ─── /naptienid ───────────────────────────────────────────────────────────
    private static void registerNapTienId(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("naptienid")
            .requires(src -> isAdmin(src))
            .executes(ctx -> {
                PayBotMod mod = PayBotMod.getInstance();
                send(ctx.getSource(), "§8§m────────────────────────────────");
                send(ctx.getSource(), "§6§l PayBot Forge v" + PayBotMod.getModVersion() + " — Info");
                send(ctx.getSource(), "§8§m────────────────────────────────");
                send(ctx.getSource(), "§7Server ID : §e" + mod.getConfig().getString("server-id","(chưa có)"));
                send(ctx.getSource(), "§7Guild ID  : §e" + mod.getConfig().getString("guild-id","(chưa kết nối)"));
                send(ctx.getSource(), "§7Bot URL   : §e" + mod.getConfig().getString("bot-url","(chưa đặt)"));
                send(ctx.getSource(), "§7Chế độ   : " + (mod.isStandaloneMode() ? "§a[Standalone]" : "§b[Bot-connected]"));
                send(ctx.getSource(), "§8§m────────────────────────────────");
                return 1;
            }));
    }

    // ─── /muapaybot ───────────────────────────────────────────────────────────
    private static void registerMuaPayBot(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("muapaybot")
            .executes(ctx -> {
                send(ctx.getSource(), "§6§l=== PayBot v" + PayBotMod.getModVersion() + " ===");
                send(ctx.getSource(), "§7Hệ thống nạp thẻ cào + ngân hàng cho Minecraft server.");
                send(ctx.getSource(), "§7Tác giả  : §eTheRealShiroz");
                send(ctx.getSource(), "§7Profile  : §bhttps://guns.lol/therealshiroz");
                send(ctx.getSource(), "§7Discord  : §bhttps://discord.gg/ETXaFxhH3d");
                send(ctx.getSource(), "§7Modrinth : §bhttps://modrinth.com/mod/paybot");
                if (UpdateCheckManager.isUpdateAvailable()) {
                    send(ctx.getSource(), "§a✦ Bản mới: v" + UpdateCheckManager.getLatestVersion()
                            + " §7(đang dùng v" + UpdateCheckManager.getCurrentVersion() + ")");
                }
                return 1;
            }));
    }

    // ─── /paybotowner ────────────────────────────────────────────────────────
    private static void registerPayBotOwner(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("paybotowner")
            .requires(src -> src.isPlayer())
            .then(Commands.literal("logout").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayer();
                PayBotMod mod = PayBotMod.getInstance();
                if (!mod.getOwnerSessionManager().isOwner(p)) { send(ctx.getSource(),"§7[PayBot] Bạn chưa đăng nhập."); return 0; }
                mod.getOwnerSessionManager().revokeSession(p);
                send(ctx.getSource(),"§a[PayBot] §fĐã đăng xuất quyền owner."); return 1;
            }))
            .then(Commands.literal("status").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayer();
                PayBotMod mod = PayBotMod.getInstance();
                long mins = mod.getOwnerSessionManager().remainingMinutes(p);
                if (mins < 0) send(ctx.getSource(),"§7[PayBot] Bạn không có owner session.");
                else send(ctx.getSource(),"§a[PayBot] §fOwner session còn §e"+mins+" phút.");
                return 1;
            }))
            .then(Commands.argument("code", StringArgumentType.greedyString()).executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayer();
                String code = StringArgumentType.getString(ctx,"code").trim();
                PayBotMod mod = PayBotMod.getInstance();
                send(ctx.getSource(),"§7[PayBot] Đang xác thực...");
                mod.runAsync(() -> {
                    OwnerSessionManager.VerifyResult result = mod.getOwnerSessionManager().verifyWithBot(p, code);
                    mod.runOnMainThread(() -> {
                        switch (result) {
                            case SUCCESS        -> { mod.getOwnerSessionManager().grantSession(p); p.sendSystemMessage(Component.literal("§a[PayBot] §fĐăng nhập thành công!")); }
                            case ALREADY_LOGGED_IN -> p.sendSystemMessage(Component.literal("§e[PayBot] §fBạn đã có owner session rồi."));
                            case WRONG_CODE        -> p.sendSystemMessage(Component.literal("§c[PayBot] §fMã không đúng."));
                            case CODE_EXPIRED      -> p.sendSystemMessage(Component.literal("§c[PayBot] §fMã đã hết hạn."));
                            case NO_ACTIVE_CODE    -> p.sendSystemMessage(Component.literal("§c[PayBot] §fChưa có mã. Dùng /ownerlogin trong Discord."));
                            case BOT_UNREACHABLE   -> p.sendSystemMessage(Component.literal("§c[PayBot] §fKhông thể kết nối bot."));
                            case NO_BOT_URL        -> p.sendSystemMessage(Component.literal("§c[PayBot] §fBot URL chưa cấu hình."));
                        }
                    });
                });
                return 1;
            })));
    }

    // ─── /paybotleaderboard — mọi player (v5.0.3: đổi tên từ /paybotplaceholder) ──
    private static void registerPayBotPlaceholder(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("paybotleaderboard")
            .requires(src -> src.isPlayer())
            .executes(ctx -> {
                PayBotPlaceholderGui.openMain(ctx.getSource().getPlayer());
                return 1;
            }));
    }

    // ─── /testnapbank — OP only (v5.0.5: mở ĐÚNG NapBankGui thật, đồng bộ plugin) ──
    private static void registerTestNapBank(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("testnapbank")
            .requires(src -> src.isPlayer() && src.hasPermission(4))
            .executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayer();
                p.sendSystemMessage(Component.literal("§e§l[TEST MODE] §r§eChọn 1 mệnh giá — đơn sẽ được giả lập "
                        + "THÀNH CÔNG ngay lập tức (không tạo QR/giao dịch thật)."));
                GuiSession.get(p.getUUID()).testMode = true;
                NapBankGui.open(p);
                return 1;
            }));
    }

    // ─── /testnapthe — OP only (v5.0.5: mở ĐÚNG NapTheGui thật, đồng bộ plugin) ────
    private static void registerTestNapThe(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("testnapthe")
            .requires(src -> src.isPlayer() && src.hasPermission(4))
            .executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayer();
                p.sendSystemMessage(Component.literal("§e§l[TEST MODE] §r§eChọn nhà mạng + mệnh giá — §a§lTHÀNH CÔNG "
                        + "§r§engay lập tức (không gửi thẻ thật lên hệ thống)."));
                GuiSession.get(p.getUUID()).testMode = true;
                NapTheGui.openTelcoGui(p);
                return 1;
            }));
    }

    // ─── /disablepaybot — chỉ dùng được trong owner session ──────────────────
    /**
     * Vô hiệu hóa PayBot trên server hiện tại dựa theo public IP.
     * Lưu vào file bí mật chỉ PayBot biết vị trí.
     * Kể cả xóa và cài lại mod/config, server vẫn bị chặn khi cùng IP.
     * Yêu cầu: đang trong owner session (/paybotowner <code>).
     */
    private static void registerDisablePayBot(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("disablepaybot")
            .requires(src -> {
                if (!src.isPlayer()) return false;
                try {
                    return PayBotMod.getInstance().getOwnerSessionManager().isOwner(src.getPlayer());
                } catch (Exception e) { return false; }
            })
            .executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayer();
                PayBotMod mod = PayBotMod.getInstance();

                // Kiểm tra owner session nghiêm ngặt
                if (!mod.getOwnerSessionManager().isOwner(p)) {
                    send(ctx.getSource(), "§c[PayBot] §fLệnh này chỉ dùng được trong owner session!");
                    send(ctx.getSource(), "§7Dùng §e/paybotowner <code> §fđể đăng nhập trước.");
                    return 0;
                }

                send(ctx.getSource(), "§6[PayBot] §fĐang vô hiệu hóa PayBot trên server này...");
                send(ctx.getSource(), "§7(Phát hiện public IP...)");

                mod.runAsync(() -> {
                    boolean ok = BanManager.banCurrentServer();
                    String ip  = BanManager.getCurrentPublicIp();

                    mod.runOnMainThread(() -> {
                        if (!ok) {
                            p.sendSystemMessage(Component.literal(
                                "§c[PayBot] §fKhông phát hiện được public IP! " +
                                "Kiểm tra kết nối mạng."));
                            return;
                        }
                        p.sendSystemMessage(Component.literal("§a[PayBot] §fĐã vô hiệu hóa PayBot."));
                        p.sendSystemMessage(Component.literal("§7Public IP bị chặn: §e" + ip));
                        p.sendSystemMessage(Component.literal(
                            "§7Server này sẽ không thể chạy PayBot kể cả khi " +
                            "xóa/cài lại mod hoặc đổi config."));
                        p.sendSystemMessage(Component.literal(
                            "§7Dùng §e/enablepaybot §7(trong owner session) để bỏ chặn."));
                        PayBotMod.LOGGER.warn("[PayBot] Server bị vô hiệu hóa bởi owner: " + ip);
                        // v5.0.5 [Part 24]: báo IP lên bot để owner quản lý tập trung qua
                        // Discord (/viewblockedips, /removeip) — fire-and-forget.
                        mod.runAsync(() -> mod.getBotHttpClient().reportBlockedIp());
                    });
                });
                return 1;
            }));
    }

    // ─── /enablepaybot — chỉ dùng được trong owner session ───────────────────
    /**
     * Hủy vô hiệu hóa PayBot trên server hiện tại.
     * Xóa public IP khỏi file ban list.
     * Yêu cầu: đang trong owner session.
     */
    private static void registerEnablePayBot(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("enablepaybot")
            .requires(src -> {
                if (!src.isPlayer()) return false;
                try {
                    return PayBotMod.getInstance().getOwnerSessionManager().isOwner(src.getPlayer());
                } catch (Exception e) { return false; }
            })
            .executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayer();
                PayBotMod mod = PayBotMod.getInstance();

                if (!mod.getOwnerSessionManager().isOwner(p)) {
                    send(ctx.getSource(), "§c[PayBot] §fLệnh này chỉ dùng được trong owner session!");
                    return 0;
                }

                send(ctx.getSource(), "§6[PayBot] §fĐang bỏ chặn PayBot trên server này...");

                mod.runAsync(() -> {
                    boolean removed = BanManager.unbanCurrentServer();
                    String ip       = BanManager.getCurrentPublicIp();

                    mod.runOnMainThread(() -> {
                        if (ip == null || ip.isEmpty()) {
                            p.sendSystemMessage(Component.literal(
                                "§c[PayBot] §fKhông phát hiện được public IP."));
                            return;
                        }
                        if (removed) {
                            p.sendSystemMessage(Component.literal("§a[PayBot] §fĐã bỏ chặn PayBot thành công."));
                            p.sendSystemMessage(Component.literal("§7IP §e" + ip + " §7đã được xóa khỏi danh sách chặn."));
                            p.sendSystemMessage(Component.literal("§7Khởi động lại server để PayBot hoạt động lại."));
                        } else {
                            p.sendSystemMessage(Component.literal("§e[PayBot] §fIP §e" + ip
                                + " §fkhông có trong danh sách bị chặn."));
                        }
                    });
                });
                return 1;
            }));
    }

    // ─── /paybotreload — Nạp lại & Apply config ──────────────────────────────
    private static void registerPayBotReload(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("paybotreload")
            .requires(src -> isAdmin(src))
            .executes(ctx -> {
                PayBotMod mod = PayBotMod.getInstance();
                send(ctx.getSource(), "§e[PayBot] §fĐang nạp lại và áp dụng (apply) cấu hình...");
                try {
                    mod.getConfig().load();
                    send(ctx.getSource(), "§a[PayBot] §f✓ Đã nạp lại và áp dụng §a(apply) §ftoàn bộ cấu hình mới từ config.yml thành công!");
                } catch (Exception e) {
                    send(ctx.getSource(), "§c[PayBot] §fLỗi khi reload: " + e.getMessage());
                }
                return 1;
            }));
    }

}