package com.naptien.gui;

import com.naptien.PayBotMod;
import com.naptien.compat.GuiBackend;
import com.naptien.compat.GuiFactory;
import com.naptien.managers.LocalOrderManager;
import com.naptien.managers.RewardEffectManager;
import com.naptien.log.LogManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TopupListGui (Fabric) — GUI danh sách đơn nạp (qua GuiBackend).
 *
 * v5.0.0:
 *   - open()         → TẤT CẢ đơn (bank + thẻ) — /topuplist, /topuplist all
 *   - openBankOnly() → CHỈ đơn bank  — /bankcheck
 *   - openCardOnly() → CHỈ đơn thẻ  — /cardcheck
 *   - openFiltered() → lọc theo player (optional)
 *   Mỗi loại có title riêng biệt; filter không lẫn lộn giữa các lệnh.
 *   v5.2.0 — Refactor sang GuiBackend hỗ trợ đa phiên bản Minecraft.
 */
public class TopupListGui {

    private static final int PAGE_SIZE = 36;
    private static final int PREV_SLOT = 45, NEXT_SLOT = 53, CLOSE_SLOT = 49;
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("dd/MM HH:mm");

    // ─── Public entry points ──────────────────────────────────────────────────

    /** /topuplist [all] — tất cả bank + thẻ */
    public static void open(ServerPlayerEntity player, int page) {
        open(player, page, null, "all");
    }

    /** /bankcheck [player] — chỉ đơn ngân hàng */
    public static void openBankOnly(ServerPlayerEntity player, int page, String playerFilter) {
        open(player, page, playerFilter, "bank");
    }

    /** /cardcheck [player] — chỉ đơn thẻ cào */
    public static void openCardOnly(ServerPlayerEntity player, int page, String playerFilter) {
        open(player, page, playerFilter, "card");
    }

    // ─── Core: unified paged list ─────────────────────────────────────────────

    private static void open(ServerPlayerEntity player, int page,
                              String playerFilter, String mode) {
        PayBotMod mod = PayBotMod.getInstance();

        // ── Gộp dữ liệu theo mode ────────────────────────────────────────────
        List<Object> all = new ArrayList<>();
        if (!"card".equals(mode)) all.addAll(mod.getLocalOrderManager().getAllBankOrders());
        if (!"bank".equals(mode)) all.addAll(mod.getLocalOrderManager().getAllCardOrders());

        // ── Lọc theo player nếu có ───────────────────────────────────────────
        if (playerFilter != null && !playerFilter.isBlank()) {
            String pf = playerFilter.trim().toLowerCase();
            all = all.stream().filter(o -> {
                String pname = o instanceof LocalOrderManager.BankOrder b ? b.playerName
                             : o instanceof LocalOrderManager.CardOrder c  ? c.playerName : "";
                return pname.toLowerCase().contains(pf);
            }).collect(Collectors.toList());
        }

        // ── Sort mới nhất lên đầu ────────────────────────────────────────────
        all.sort((a, b) -> {
            long ta = a instanceof LocalOrderManager.BankOrder bo
                    ? bo.createdAt : ((LocalOrderManager.CardOrder) a).createdAt;
            long tb = b instanceof LocalOrderManager.BankOrder bo
                    ? bo.createdAt : ((LocalOrderManager.CardOrder) b).createdAt;
            return Long.compare(tb, ta);
        });

        int total  = all.size();
        int pages  = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int pg     = Math.max(0, Math.min(page, pages - 1));
        int finalPg = pg;

        // ── Tiêu đề theo mode ────────────────────────────────────────────────
        String modeLabel = switch (mode) {
            case "bank" -> "§6§lĐơn Ngân Hàng";
            case "card" -> "§b§lĐơn Thẻ Cào";
            default     -> "§6§lTất Cả Đơn Nạp";
        };
        String filterSuffix = (playerFilter != null && !playerFilter.isBlank())
                ? " §7(" + playerFilter + ")" : "";
        String pageStr = " §8[" + (pg + 1) + "/" + pages + "]";

        GuiBackend gui = GuiFactory.create(ScreenHandlerType.GENERIC_9X6, player, modeLabel + filterSuffix + pageStr);

        // ── Điền entries ─────────────────────────────────────────────────────
        int start = pg * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, total);
        for (int i = start; i < end; i++) {
            Object o = all.get(i);
            int slot = i - start;
            if (o instanceof LocalOrderManager.BankOrder b)
                buildBankSlot(gui, slot, b, player, pg, mode, playerFilter);
            else if (o instanceof LocalOrderManager.CardOrder c)
                buildCardSlot(gui, slot, c);
        }

        // ── Nav row (45-53) ───────────────────────────────────────────────────
        if (pg > 0) {
            gui.setSlot(PREV_SLOT, new ItemStack(Items.ARROW), "§7◄ Trang trước", null, () -> open(player, finalPg - 1, playerFilter, mode));
        }
        if (pg < pages - 1) {
            gui.setSlot(NEXT_SLOT, new ItemStack(Items.ARROW), "§7Trang tiếp ►", null, () -> open(player, finalPg + 1, playerFilter, mode));
        }

        // Slot tóm tắt
        gui.setSlot(46, new ItemStack(Items.PAPER), "§7Tổng: §f" + total + " đơn", List.of("§7Trang §f" + (pg + 1) + "§7/§f" + pages), null);

        gui.setSlot(CLOSE_SLOT, GuiUtil.getCloseItem(), "§c§lĐóng", List.of("§7Nhấn để đóng"), gui::close);
        GuiUtil.fillGlass(gui);
        gui.open();
    }

    // ─── Bank order slot ──────────────────────────────────────────────────────

    private static void buildBankSlot(GuiBackend gui, int slot,
                                      LocalOrderManager.BankOrder b,
                                      ServerPlayerEntity player, int page,
                                      String mode, String filter) {
        PayBotMod mod = PayBotMod.getInstance();
        boolean isPaid     = LocalOrderManager.BANK_PAID.equals(b.status);
        boolean isApproved = LocalOrderManager.BANK_APPROVED.equals(b.status);
        boolean isExpired  = LocalOrderManager.BANK_EXPIRED.equals(b.status);

        net.minecraft.item.Item icon;
        if (isApproved) icon = Items.LIME_WOOL;
        else if (isPaid) icon = Items.YELLOW_WOOL;
        else if (isExpired) icon = Items.GRAY_WOOL;
        else icon = Items.RED_WOOL;

        String time    = TIME_FMT.format(new Date(b.createdAt));
        String shortId = shorten(b.invoiceId, 12);

        List<String> lore = new ArrayList<>();
        lore.add("§7Player    : §f" + b.playerName);
        lore.add("§7Thời gian : §f" + time);
        lore.add("§7Loại      : §bNạp bank");
        lore.add("§7Số tiền   : §a" + PayBotMod.formatVnd(b.amount) + " VND");
        lore.add("§7Mã đơn    : §f" + shortId);
        lore.add("§7Trạng thái: §f" + LogManager.bankStatusLabel(b.status));

        Runnable onClick = null;
        if (isPaid) {
            lore.add("§e§lClick để duyệt đơn");
            onClick = () -> {
                gui.close();
                mod.runOnMainThread(() -> approveBank(mod, b, player, page, mode, filter));
            };
        }
        
        gui.setSlot(slot, new ItemStack(icon), (isApproved ? "§a§l✓" : "§c§l✗") + " §f" + b.playerName + " §e" + PayBotMod.formatVnd(b.amount) + " VND", lore, onClick);
    }

    private static void approveBank(PayBotMod mod, LocalOrderManager.BankOrder b,
                                    ServerPlayerEntity admin, int page, String mode, String filter) {
        mod.getLocalOrderManager().updateBankStatus(b.invoiceId, LocalOrderManager.BANK_APPROVED);

        // Tìm lệnh thưởng qua fallback chain
        String cmd  = mod.getConfig().getString("denom-rewards-bank." + b.amount + ".cmd", "").trim();
        String rawAmt = mod.getConfig().getString("denom-rewards-bank." + b.amount + ".amt", "").trim();
        if (cmd.isEmpty()) cmd = mod.getConfig().getString("reward-command-bank", "").trim();
        if (cmd.isEmpty()) cmd = mod.getConfig().getString("reward-command", "").trim();

        int mode2 = Math.max(1, mod.getConfig().getInt("Rewards_Bank_Mode", 1));
        int rewardAmt = 0;
        try { if (!rawAmt.isEmpty()) rewardAmt = (int)(Double.parseDouble(rawAmt) * mode2); }
        catch (Exception ignored) {}

        if (!cmd.isEmpty()) {
            final String fcmd = cmd
                    .replace("{player}",    b.playerName)
                    .replace("[playername]", b.playerName)
                    .replace("[amount]",    String.valueOf(rewardAmt));

            // Multi-command support (";;")
            for (String c : fcmd.split(";;")) {
                String cc = c.trim();
                if (cc.isEmpty()) continue;
                try { mod.getServer().getCommandManager().getDispatcher()
                        .execute(cc, mod.getServer().getCommandSource()); }
                catch (Exception e) {
                    if (mod.isNotifEnabled("approve-fail") && mod.getLogFilter().allow("approve-fail"))
                        PayBotMod.LOGGER.error("[TopupList] cmd error: " + e.getMessage());
                }
            }
        }

        admin.sendMessage(Text.literal("§a[PayBot] §fĐã duyệt đơn §e" + shorten(b.invoiceId, 8) + "... §fcho §e" + b.playerName));
        mod.notifyAdmins("§a[PayBot] §fAdmin đã duyệt đơn bank §e" + b.playerName
                + " §f" + PayBotMod.formatVnd(b.amount) + " VND");

        ServerPlayerEntity target = mod.getServer().getPlayerManager().getPlayer(b.playerName);
        if (target != null) {
            RewardEffectManager.trigger(mod, target, b.amount);
            target.sendMessage(Text.literal("§a[PayBot] §fĐơn nạp §e" + PayBotMod.formatVnd(b.amount)
                    + " VND §fđã được duyệt! Cảm ơn ♥"));
        }
        // Mở lại GUI sau khi duyệt
        open(admin, page, filter, mode);
    }

    // ─── Card order slot ──────────────────────────────────────────────────────

    private static void buildCardSlot(GuiBackend gui, int slot, LocalOrderManager.CardOrder c) {
        boolean isApproved = LocalOrderManager.CARD_APPROVED.equals(c.status);
        boolean isFailed   = "2".equals(c.status) || "3".equals(c.status) || "100".equals(c.status);

        net.minecraft.item.Item icon = isApproved ? Items.LIME_WOOL
                                     : isFailed   ? Items.RED_WOOL
                                                  : Items.ORANGE_WOOL;
        String time = TIME_FMT.format(new Date(c.createdAt));

        List<String> lore = new ArrayList<>();
        lore.add("§7Player    : §f" + c.playerName);
        lore.add("§7Thời gian : §f" + time);
        lore.add("§7Loại      : §6Nạp thẻ §7(" + c.telco + ")");
        lore.add("§7Số tiền   : §a" + PayBotMod.formatVnd(c.denom) + " VND");
        lore.add("§7Mã đơn    : §f" + shorten(c.requestId, 12));
        lore.add("§7Trạng thái: §f" + LogManager.cardStatusLabel(c.status));

        gui.setSlot(slot, new ItemStack(icon), (isApproved ? "§a§l✓" : "§c§l✗") + " §f" + c.playerName + " §e" + c.telco + " " + PayBotMod.formatVnd(c.denom) + " VND", lore, null);
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private static String shorten(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
