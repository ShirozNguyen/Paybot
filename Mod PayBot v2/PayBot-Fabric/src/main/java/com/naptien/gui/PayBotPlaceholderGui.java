package com.naptien.gui;

import com.naptien.PayBotMod;
import com.naptien.compat.GuiBackend;
import com.naptien.compat.GuiFactory;
import com.naptien.managers.LocalOrderManager;
import com.naptien.managers.PlaceholderManager;
import com.naptien.managers.PlaceholderManager.PlayerStat;
import com.naptien.managers.PlaceholderManager.ServerStat;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * PayBotPlaceholderGui (Fabric) — Menu thống kê nạp tiền (qua GuiBackend).
 *
 * Cách mở: lệnh /paybotleaderboard (mọi player).
 *
 * v5.2.0 — Refactor sang GuiBackend hỗ trợ đa phiên bản Minecraft.
 */
public class PayBotPlaceholderGui {

    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("dd/MM HH:mm");

    // ─── Slot constants — Main Menu (45 slots) ────────────────────────────────
    private static final int MM_TOP_BANK     = 10;
    private static final int MM_TOP_CARD     = 13;
    private static final int MM_TOP_COMBINED = 16;
    private static final int MM_SERVER       = 28;
    private static final int MM_PERSONAL     = 31;
    private static final int MM_HISTORY      = 34;
    private static final int MM_CLOSE        = 40;

    // ─── Slot constants — Sub-pages (54 slots) ────────────────────────────────
    private static final int[] RANK_SLOTS = { 10, 13, 16, 19, 22, 25, 28, 31, 34, 40 };
    private static final int SP_STAT_SLOT = 4;
    private static final int SP_BACK      = 45;
    private static final int SP_CLOSE     = 49;

    // ─── Main menu ────────────────────────────────────────────────────────────

    public static void openMain(ServerPlayerEntity player) {
        PayBotMod mod = PayBotMod.getInstance();
        PlaceholderManager ph = mod.getPlaceholderManager();

        GuiBackend gui = GuiFactory.create(ScreenHandlerType.GENERIC_9X5, player, "§6§l✦ PayBot — Thống Kê Nạp Tiền ✦");

        // ── Top Nạp Bank ──────────────────────────────────────────────────────
        gui.setSlot(MM_TOP_BANK, new ItemStack(Items.GOLD_INGOT), "§6§l🏦 Top Nạp Ngân Hàng", List.of("§7Xem bảng xếp hạng nạp bank.", "§7", "§e▶ Nhấn để xem"), () -> openTopBank(player));

        // ── Top Nạp Thẻ ───────────────────────────────────────────────────────
        gui.setSlot(MM_TOP_CARD, new ItemStack(Items.PAPER), "§b§l📱 Top Nạp Thẻ Cào", List.of("§7Xem bảng xếp hạng nạp thẻ.", "§7", "§e▶ Nhấn để xem"), () -> openTopCard(player));

        // ── Top Tổng Hợp ──────────────────────────────────────────────────────
        gui.setSlot(MM_TOP_COMBINED, new ItemStack(Items.EMERALD), "§a§l🌐 Top Tổng Hợp", List.of("§7Xếp hạng theo tổng bank + thẻ.", "§7", "§e▶ Nhấn để xem"), () -> openTopCombined(player));

        // ── Thống Kê Server ───────────────────────────────────────────────────
        ServerStat total = ph.getServerTotal();
        gui.setSlot(MM_SERVER, new ItemStack(Items.NETHER_STAR), "§d§l📊 Thống Kê Toàn Server", List.of(
                "§7Bank : §a" + PayBotMod.formatVnd((int) total.bankTotal()) + " VND §7(" + total.bankCount() + " đơn)",
                "§7Thẻ  : §a" + PayBotMod.formatVnd((int) total.cardTotal()) + " VND §7(" + total.cardCount() + " đơn)",
                "§7Tổng : §e§l" + PayBotMod.formatVnd((int) total.total()) + " VND",
                "§7",
                "§e▶ Nhấn để xem chi tiết"
        ), () -> openServerStats(player));

        // ── Thống Kê Cá Nhân ─────────────────────────────────────────────────
        PlayerStat myStat = ph.getPlayerStat(player.getName().getString());
        int myBankRank    = ph.getPlayerBankRank(player.getName().getString());
        int myCombRank    = ph.getPlayerCombinedRank(player.getName().getString());
        gui.setSlot(MM_PERSONAL, new ItemStack(Items.PLAYER_HEAD), "§e§l👤 Thống Kê Của Tôi", List.of(
                "§7Bank  : §a" + PayBotMod.formatVnd((int) myStat.bankTotal()) + " VND",
                "§7Thẻ   : §a" + PayBotMod.formatVnd((int) myStat.cardTotal()) + " VND",
                "§7Tổng  : §e§l" + PayBotMod.formatVnd((int) myStat.total()) + " VND",
                "§7Hạng bank  : §6" + (myBankRank < 0 ? "Chưa xếp hạng" : "#" + myBankRank),
                "§7Hạng tổng  : §6" + (myCombRank < 0 ? "Chưa xếp hạng" : "#" + myCombRank),
                "§7",
                "§e▶ Nhấn để xem lịch sử của bạn"
        ), () -> openPersonal(player));

        // ── Lịch Sử Gần Đây ──────────────────────────────────────────────────
        List<Map<String, Object>> recent = ph.getRecentOrders(3);
        List<String> histLore = new ArrayList<>();
        histLore.add("§7Các giao dịch gần nhất trên server:");
        if (recent.isEmpty()) {
            histLore.add("§8(chưa có giao dịch nào)");
        } else {
            for (Map<String, Object> r : recent) {
                String type    = (String) r.get("type");
                String pname   = (String) r.get("playerName");
                long   amount  = (long)   r.get("amount");
                String timeStr = TIME_FMT.format(new Date((long) r.get("createdAt")));
                String typeTag = "bank".equals(type) ? "§6[Bank]" : "§b[Thẻ]";
                histLore.add(typeTag + " §f" + pname + " §a" + PayBotMod.formatVnd((int) amount) + "đ §8" + timeStr);
            }
        }
        histLore.add("§7");
        histLore.add("§e▶ Nhấn để xem tất cả");
        
        gui.setSlot(MM_HISTORY, new ItemStack(Items.BOOK), "§c§l📜 Lịch Sử Gần Đây", histLore, () -> openHistory(player, 0));

        // ── Close button ──────────────────────────────────────────────────────
        gui.setSlot(MM_CLOSE, GuiUtil.getCloseItem(), "§c§lĐóng", List.of("§7Nhấn để đóng"), gui::close);

        GuiUtil.fillGlass(gui);
        gui.open();
    }

    // ─── Sub-page: Top Bank ───────────────────────────────────────────────────

    public static void openTopBank(ServerPlayerEntity player) {
        PayBotMod mod = PayBotMod.getInstance();
        List<PlayerStat> top = mod.getPlaceholderManager().getTopByBank(10);
        ServerStat total = mod.getPlaceholderManager().getServerTotal();

        GuiBackend gui = GuiFactory.create(ScreenHandlerType.GENERIC_9X6, player, "§6§l🏦 Bảng Xếp Hạng Nạp Ngân Hàng");

        gui.setSlot(SP_STAT_SLOT, new ItemStack(Items.GOLD_BLOCK), "§6§lTổng Nạp Bank Toàn Server", List.of(
                "§a" + PayBotMod.formatVnd((int) total.bankTotal()) + " VND",
                "§7" + total.bankCount() + " giao dịch đã duyệt"
        ), null);

        // Rankings
        String playerName = player.getName().getString();
        for (int idx = 0; idx < Math.min(top.size(), 10); idx++) {
            PlayerStat ps = top.get(idx);
            int rank      = idx + 1;
            int slot      = RANK_SLOTS[idx];
            boolean isMe  = ps.name().equalsIgnoreCase(playerName);

            gui.setSlot(slot, new ItemStack(getRankItem(rank)), getRankColor(rank) + "§l#" + rank + " §f" + ps.name() + (isMe ? " §e◀ Bạn" : ""), List.of(
                    "§7Tổng Bank: §a" + PayBotMod.formatVnd((int) ps.bankTotal()) + " VND",
                    "§7Số đơn   : §f" + ps.bankCount() + " đơn"
            ), null);
        }

        if (top.isEmpty()) {
            gui.setSlot(22, new ItemStack(Items.BARRIER), "§7(Chưa có dữ liệu)", null, null);
        }

        setNavigation(gui, player);
        gui.open();
    }

    // ─── Sub-page: Top Card ───────────────────────────────────────────────────

    public static void openTopCard(ServerPlayerEntity player) {
        PayBotMod mod = PayBotMod.getInstance();
        List<PlayerStat> top = mod.getPlaceholderManager().getTopByCard(10);
        ServerStat total = mod.getPlaceholderManager().getServerTotal();

        GuiBackend gui = GuiFactory.create(ScreenHandlerType.GENERIC_9X6, player, "§b§l📱 Bảng Xếp Hạng Nạp Thẻ Cào");

        gui.setSlot(SP_STAT_SLOT, new ItemStack(Items.CYAN_WOOL), "§b§lTổng Nạp Thẻ Toàn Server", List.of(
                "§a" + PayBotMod.formatVnd((int) total.cardTotal()) + " VND",
                "§7" + total.cardCount() + " giao dịch đã duyệt"
        ), null);

        String playerName = player.getName().getString();
        for (int idx = 0; idx < Math.min(top.size(), 10); idx++) {
            PlayerStat ps = top.get(idx);
            int rank = idx + 1;
            int slot = RANK_SLOTS[idx];
            boolean isMe = ps.name().equalsIgnoreCase(playerName);

            gui.setSlot(slot, new ItemStack(getRankItem(rank)), getRankColor(rank) + "§l#" + rank + " §f" + ps.name() + (isMe ? " §e◀ Bạn" : ""), List.of(
                    "§7Tổng Thẻ: §a" + PayBotMod.formatVnd((int) ps.cardTotal()) + " VND",
                    "§7Số đơn  : §f" + ps.cardCount() + " đơn"
            ), null);
        }

        if (top.isEmpty()) {
            gui.setSlot(22, new ItemStack(Items.BARRIER), "§7(Chưa có dữ liệu)", null, null);
        }

        setNavigation(gui, player);
        gui.open();
    }

    // ─── Sub-page: Top Combined ───────────────────────────────────────────────

    public static void openTopCombined(ServerPlayerEntity player) {
        PayBotMod mod = PayBotMod.getInstance();
        List<PlayerStat> top = mod.getPlaceholderManager().getTopCombined(10);
        ServerStat total = mod.getPlaceholderManager().getServerTotal();

        GuiBackend gui = GuiFactory.create(ScreenHandlerType.GENERIC_9X6, player, "§a§l🌐 Bảng Xếp Hạng Tổng Hợp");

        gui.setSlot(SP_STAT_SLOT, new ItemStack(Items.EMERALD_BLOCK), "§a§lTổng Nạp Toàn Server (Bank + Thẻ)", List.of(
                "§7Bank  : §a" + PayBotMod.formatVnd((int) total.bankTotal()) + " VND",
                "§7Thẻ   : §a" + PayBotMod.formatVnd((int) total.cardTotal()) + " VND",
                "§e§l→ Tổng : " + PayBotMod.formatVnd((int) total.total()) + " VND"
        ), null);

        String playerName = player.getName().getString();
        for (int idx = 0; idx < Math.min(top.size(), 10); idx++) {
            PlayerStat ps = top.get(idx);
            int rank = idx + 1;
            int slot = RANK_SLOTS[idx];
            boolean isMe = ps.name().equalsIgnoreCase(playerName);

            gui.setSlot(slot, new ItemStack(getRankItem(rank)), getRankColor(rank) + "§l#" + rank + " §f" + ps.name() + (isMe ? " §e◀ Bạn" : ""), List.of(
                    "§7Tổng : §e§l" + PayBotMod.formatVnd((int) ps.total()) + " VND",
                    "§7Bank  : §a" + PayBotMod.formatVnd((int) ps.bankTotal()) + " VND §7(" + ps.bankCount() + "đ)",
                    "§7Thẻ   : §a" + PayBotMod.formatVnd((int) ps.cardTotal()) + " VND §7(" + ps.cardCount() + "đ)"
            ), null);
        }

        if (top.isEmpty()) {
            gui.setSlot(22, new ItemStack(Items.BARRIER), "§7(Chưa có dữ liệu)", null, null);
        }

        setNavigation(gui, player);
        gui.open();
    }

    // ─── Sub-page: Server Stats ───────────────────────────────────────────────

    public static void openServerStats(ServerPlayerEntity player) {
        PayBotMod mod = PayBotMod.getInstance();
        PlaceholderManager ph = mod.getPlaceholderManager();
        ServerStat total = ph.getServerTotal();
        ServerStat today = ph.getTodayStat();

        GuiBackend gui = GuiFactory.create(ScreenHandlerType.GENERIC_9X5, player, "§d§l📊 Thống Kê Toàn Server");

        gui.setSlot(4, new ItemStack(Items.NETHER_STAR), "§d§lTổng Toàn Thời Gian", List.of("§7Tất cả đơn đã duyệt từ trước đến nay"), null);

        gui.setSlot(10, new ItemStack(Items.GOLD_INGOT), "§6§lNạp Ngân Hàng", List.of(
                "§7Tổng: §a§l" + PayBotMod.formatVnd((int) total.bankTotal()) + " VND",
                "§7Đơn : §f" + total.bankCount() + " giao dịch",
                "§7Trung bình/đơn: §e" + (total.bankCount() > 0 ? PayBotMod.formatVnd((int) (total.bankTotal() / total.bankCount())) : "0") + " VND"
        ), null);

        gui.setSlot(13, new ItemStack(Items.PAPER), "§b§lNạp Thẻ Cào", List.of(
                "§7Tổng: §a§l" + PayBotMod.formatVnd((int) total.cardTotal()) + " VND",
                "§7Đơn : §f" + total.cardCount() + " giao dịch",
                "§7Trung bình/đơn: §e" + (total.cardCount() > 0 ? PayBotMod.formatVnd((int) (total.cardTotal() / total.cardCount())) : "0") + " VND"
        ), null);

        gui.setSlot(16, new ItemStack(Items.EMERALD), "§a§lTổng Hợp (Bank + Thẻ)", List.of(
                "§7Tổng  : §e§l" + PayBotMod.formatVnd((int) total.total()) + " VND",
                "§7Đơn   : §f" + total.totalCount() + " giao dịch",
                "§7Players: §f" + ph.getTopCombined(Integer.MAX_VALUE).size()
        ), null);

        gui.setSlot(22, new ItemStack(Items.CLOCK), "§7§lHôm Nay", List.of("§7Giao dịch trong ngày hôm nay"), null);

        gui.setSlot(28, new ItemStack(Items.GOLD_NUGGET), "§6Bank Hôm Nay", List.of(
                "§7Tổng: §a" + PayBotMod.formatVnd((int) today.bankTotal()) + " VND",
                "§7Đơn : §f" + today.bankCount()
        ), null);

        gui.setSlot(31, new ItemStack(Items.STRING), "§bThẻ Hôm Nay", List.of(
                "§7Tổng: §a" + PayBotMod.formatVnd((int) today.cardTotal()) + " VND",
                "§7Đơn : §f" + today.cardCount()
        ), null);

        gui.setSlot(34, new ItemStack(Items.GLOWSTONE_DUST), "§aHôm Nay Tổng", List.of(
                "§7Tổng  : §e§l" + PayBotMod.formatVnd((int) today.total()) + " VND",
                "§7Đơn   : §f" + today.totalCount()
        ), null);

        gui.setSlot(40, new ItemStack(Items.ARROW), "§7◄ Quay lại", List.of("§8Về trang chính"), () -> {
            gui.close();
            openMain(player);
        });
        
        GuiUtil.fillGlass(gui);
        gui.open();
    }

    // ─── Sub-page: Personal Stats ─────────────────────────────────────────────

    public static void openPersonal(ServerPlayerEntity player) {
        PayBotMod mod = PayBotMod.getInstance();
        PlaceholderManager ph = mod.getPlaceholderManager();
        String name     = player.getName().getString();
        PlayerStat ps   = ph.getPlayerStat(name);
        int bankRank    = ph.getPlayerBankRank(name);
        int cardRank    = ph.getPlayerCardRank(name);
        int combRank    = ph.getPlayerCombinedRank(name);

        GuiBackend gui = GuiFactory.create(ScreenHandlerType.GENERIC_9X5, player, "§e§l👤 Thống Kê Của " + name);

        gui.setSlot(4, new ItemStack(Items.PLAYER_HEAD), "§e§l" + name, List.of("§7Thống kê nạp tiền cá nhân"), null);

        gui.setSlot(10, new ItemStack(Items.GOLD_INGOT), "§6§lNạp Ngân Hàng", List.of(
                "§7Tổng  : §a" + PayBotMod.formatVnd((int) ps.bankTotal()) + " VND",
                "§7Đơn   : §f" + ps.bankCount(),
                "§7Hạng  : §e" + (bankRank < 0 ? "Chưa xếp hạng" : "#" + bankRank)
        ), null);

        gui.setSlot(13, new ItemStack(Items.PAPER), "§b§lNạp Thẻ Cào", List.of(
                "§7Tổng  : §a" + PayBotMod.formatVnd((int) ps.cardTotal()) + " VND",
                "§7Đơn   : §f" + ps.cardCount(),
                "§7Hạng  : §e" + (cardRank < 0 ? "Chưa xếp hạng" : "#" + cardRank)
        ), null);

        gui.setSlot(16, new ItemStack(Items.EMERALD), "§a§lTổng Hợp", List.of(
                "§7Tổng  : §e§l" + PayBotMod.formatVnd((int) ps.total()) + " VND",
                "§7Đơn   : §f" + ps.totalCount(),
                "§7Hạng  : §6" + (combRank < 0 ? "Chưa xếp hạng" : "#" + combRank)
        ), null);

        List<Map<String, Object>> myOrders = ph.getRecentOrdersForPlayer(name, 3);
        String recentText = myOrders.isEmpty()
                ? "§8(chưa có)"
                : "§7" + TIME_FMT.format(new Date((long) myOrders.get(0).get("createdAt"))) + " §a" + PayBotMod.formatVnd((int)(long) myOrders.get(0).get("amount")) + "đ";
        
        gui.setSlot(28, new ItemStack(Items.CLOCK), "§c§lLần Nạp Gần Nhất", List.of(recentText), null);

        long totalAll = ps.total();
        String tier = totalAll >= 5_000_000 ? "§5§l★ VIP Vàng"
                    : totalAll >= 1_000_000 ? "§6§l★ VIP Bạc"
                    : totalAll >= 500_000   ? "§b§l◆ Thường"
                    :                         "§7◆ Mới";
        gui.setSlot(31, new ItemStack(Items.DIAMOND), "§b§lHạng Thành Viên", List.of(
                "§7Hạng hiện tại: " + tier,
                "§7Tổng nạp: §e" + PayBotMod.formatVnd((int) totalAll) + " VND"
        ), null);

        gui.setSlot(34, new ItemStack(Items.BOOK), "§c§l📜 Lịch Sử Của Tôi", List.of(
                "§7Xem " + (myOrders.size()) + " giao dịch gần nhất",
                "§e▶ Nhấn để xem tất cả"
        ), () -> openHistory(player, 0));

        gui.setSlot(40, new ItemStack(Items.ARROW), "§7◄ Quay lại", List.of("§8Về trang chính"), () -> {
            gui.close();
            openMain(player);
        });
        
        GuiUtil.fillGlass(gui);
        gui.open();
    }

    // ─── Sub-page: History (all orders, paginated) ────────────────────────────

    private static final int HIST_PAGE_SIZE = 36;

    public static void openHistory(ServerPlayerEntity player, int page) {
        PayBotMod mod = PayBotMod.getInstance();
        boolean isAdmin = player.hasPermissionLevel(2)
                || mod.getOwnerSessionManager().isOwner(player);

        List<Map<String, Object>> all = isAdmin
                ? mod.getPlaceholderManager().getRecentOrders(Integer.MAX_VALUE)
                : mod.getPlaceholderManager().getRecentOrdersForPlayer(player.getName().getString(), Integer.MAX_VALUE);

        int total = all.size();
        int pages = Math.max(1, (total + HIST_PAGE_SIZE - 1) / HIST_PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        int finalPage = page;

        GuiBackend gui = GuiFactory.create(ScreenHandlerType.GENERIC_9X6, player, "§c§l📜 Lịch Sử Giao Dịch §7(Trang " + (page + 1) + "/" + pages + ")");

        int start = page * HIST_PAGE_SIZE;
        int end   = Math.min(start + HIST_PAGE_SIZE, total);
        for (int i = start; i < end; i++) {
            Map<String, Object> r = all.get(i);
            int slot = i - start;
            buildHistorySlot(gui, slot, r);
        }

        gui.setSlot(45, new ItemStack(Items.ARROW), "§7◄ Quay lại", List.of("§8Về trang chính"), () -> {
            gui.close();
            openMain(player);
        });
        
        if (page > 0) {
            gui.setSlot(46, new ItemStack(Items.ARROW), "§7◄ Trang trước", null, () -> openHistory(player, finalPage - 1));
        }
        gui.setSlot(49, GuiUtil.getCloseItem(), "§c§lĐóng", List.of("§7Nhấn để đóng"), gui::close);
        
        if (page < pages - 1) {
            gui.setSlot(52, new ItemStack(Items.ARROW), "§7Trang tiếp ►", null, () -> openHistory(player, finalPage + 1));
        }
        gui.setSlot(53, new ItemStack(Items.PAPER), "§7Tổng: §f" + total + " đơn", null, null);

        GuiUtil.fillGlass(gui);
        gui.open();
    }

    private static void buildHistorySlot(GuiBackend gui, int slot, Map<String, Object> r) {
        String type      = (String) r.get("type");
        String pname     = (String) r.get("playerName");
        long   amount    = (long)   r.get("amount");
        String status    = (String) r.get("status");
        String id        = (String) r.get("id");
        String timeStr   = TIME_FMT.format(new Date((long) r.get("createdAt")));
        boolean approved = LocalOrderManager.BANK_APPROVED.equals(status)
                        || LocalOrderManager.CARD_APPROVED.equals(status);
        boolean failed   = "100".equals(status) || "2".equals(status) || "3".equals(status)
                        || LocalOrderManager.BANK_EXPIRED.equals(status);

        net.minecraft.item.Item icon = approved ? Items.LIME_WOOL
                                     : failed   ? Items.RED_WOOL
                                                : Items.YELLOW_WOOL;

        List<String> lore = new ArrayList<>();
        lore.add("§7Player  : §f" + pname);
        lore.add("§7Loại    : " + ("bank".equals(type) ? "§6Ngân hàng" : "§bThẻ cào"));
        if ("card".equals(type) && r.containsKey("telco"))
            lore.add("§7Nhà mạng: §f" + r.get("telco"));
        lore.add("§7Số tiền : §a" + PayBotMod.formatVnd((int) amount) + " VND");
        lore.add("§7Mã đơn  : §8" + (id.length() > 12 ? id.substring(0, 12) + "..." : id));
        lore.add("§7Thời gian: §f" + timeStr);
        lore.add("§7Trạng thái: " + statusColor(status, type));

        gui.setSlot(slot, new ItemStack(icon), "§f" + pname + " §e" + PayBotMod.formatVnd((int) amount) + "đ", lore, null);
    }

    private static String statusColor(String status, String type) {
        if (LocalOrderManager.BANK_APPROVED.equals(status) || LocalOrderManager.CARD_APPROVED.equals(status))
            return "§a✓ Đã duyệt";
        if (LocalOrderManager.BANK_PAID.equals(status))
            return "§e⏳ Chờ duyệt";
        if (LocalOrderManager.BANK_PENDING.equals(status))
            return "§7⏸ Đang chờ thanh toán";
        if (LocalOrderManager.BANK_EXPIRED.equals(status))
            return "§8✗ Hết hạn";
        if ("99".equals(status))
            return "§7⚙ Đang xử lý";
        if ("1".equals(status))
            return "§e⏳ Chờ duyệt";
        if ("2".equals(status))
            return "§c✗ Sai mệnh giá";
        if ("3".equals(status))
            return "§c✗ Thẻ đã dùng";
        if ("100".equals(status))
            return "§c✗ Thẻ sai";
        return "§7" + status;
    }

    // ─── Shared helpers ───────────────────────────────────────────────────────

    private static void setNavigation(GuiBackend gui, ServerPlayerEntity player) {
        gui.setSlot(SP_BACK, new ItemStack(Items.ARROW), "§7◄ Quay lại", List.of("§8Về trang chính"), () -> {
            gui.close();
            openMain(player);
        });
        gui.setSlot(SP_CLOSE, GuiUtil.getCloseItem(), "§c§lĐóng", List.of("§7Nhấn để đóng"), gui::close);
        GuiUtil.fillGlass(gui);
    }

    private static net.minecraft.item.Item getRankItem(int rank) {
        return switch (rank) {
            case 1  -> Items.GOLD_INGOT;
            case 2  -> Items.IRON_INGOT;
            case 3  -> Items.COPPER_INGOT;
            case 4  -> Items.AMETHYST_SHARD;
            case 5  -> Items.PRISMARINE_SHARD;
            default -> Items.FLINT;
        };
    }

    private static String getRankColor(int rank) {
        return switch (rank) {
            case 1  -> "§6";
            case 2  -> "§7";
            case 3  -> "§c";
            default -> "§f";
        };
    }
}
