package com.naptien.gui;

import com.naptien.NapTienPlugin;
import com.naptien.commands.NapBankCommand;
import com.naptien.managers.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;

import java.util.Arrays;
import java.util.List;

import static com.naptien.gui.GuiUtil.*;

@SuppressWarnings("deprecation")
public final class GuiListener implements Listener {

    private final NapTienPlugin plugin;

    public GuiListener(NapTienPlugin plugin) {
        this.plugin = plugin;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPER: kiểm tra title có phải GUI của plugin không
    // ══════════════════════════════════════════════════════════════════════════
    private static boolean isOurGui(String title) {
        if (title == null) return false;
        return title.equals("§6§lChọn nhà mạng")
            || title.equals("§6§lNạp tiền ngân hàng")
            || title.equals("§6§lChỉnh sửa mệnh giá nạp")
            || title.equals(PayBotPlaceholderGui.MAIN_TITLE)
            || title.equals(PayBotPlaceholderGui.LEADERBOARD_TITLE)
            || title.startsWith("§6§lChọn mệnh giá - §e")
            || title.startsWith("§6§lDanh sách đơn");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INVENTORY DRAG — block dragging items into/out of our GUIs
    // ══════════════════════════════════════════════════════════════════════════
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (isOurGui(title)) { event.setCancelled(true); return; }
        GuiSession s = GuiSession.get(player.getUniqueId());
        if (s.stage == GuiSession.Stage.NONE) return;
        int topSize = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) { event.setCancelled(true); return; }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INVENTORY CLICK
    // ══════════════════════════════════════════════════════════════════════════
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title   = event.getView().getTitle();
        boolean ourGui = isOurGui(title);

        GuiSession s = GuiSession.get(player.getUniqueId());
        // Không phải GUI của plugin (cả title lẫn session) → bỏ qua
        if (!ourGui && s.stage == GuiSession.Stage.NONE) return;

        // Cancel TRƯỚC mọi thứ — bảo vệ item dù session state có thế nào
        event.setCancelled(true);

        // Chỉ xử lý click trong top inventory (GUI của plugin), không phải player inventory bên dưới
        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getCurrentItem() == null) return;

        int slot = event.getSlot();

        // ── NapThe: Telco ──────────────────────────────────────────────────────
        if (title.equals("§6§lChọn nhà mạng")) {
            NapTheGui.TelcoEntry t = NapTheGui.getTelcoBySlot(slot);
            if (t != null) {
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
                NapTheGui.openDenomGui(player, t.name());
            }
            return;
        }

        // ── NapThe: Denom ─────────────────────────────────────────────────────
        if (title.startsWith("§6§lChọn mệnh giá - §e")) {
            if (slot == 49) { // Back
                NapTheGui.openTelcoGui(player);
                return;
            }
            java.util.Map.Entry<String, Integer> denom = NapTheGui.getDenomBySlot(slot);
            if (denom != null) {
                if (s.testMode) {
                    // v5.0.0: /testnapthe — giả lập thành công ngay, KHÔNG hỏi mã thẻ/serial
                    String telcoName = s.telco;
                    int    denomAmt  = denom.getValue();
                    player.closeInventory();
                    GuiSession.clear(player.getUniqueId());
                    simulateCardSuccess(plugin, player, telcoName, denomAmt);
                    return;
                }
                s.denom  = denom.getValue();
                s.stage  = GuiSession.Stage.CARD_WAIT_CODE;   // v4.3.0: mã thẻ TRƯỚC, serial SAU
                player.closeInventory();
                player.sendMessage(NapTienPlugin.f("§6[PayBot] §eNhập §bmã thẻ §evào chat:"));
                player.sendMessage("§7§o(Tin nhắn chỉ bạn mới thấy — gõ §ccancel §7§ođể huỷ)");
            }
            return;
        }

        // ── NapBank ────────────────────────────────────────────────────────────
        if (title.equals("§6§lNạp tiền ngân hàng")) {
            if (slot == NapBankGui.getCloseSlot()) {
                player.closeInventory();
                GuiSession.clear(player.getUniqueId());
                return;
            }
            List<Integer> amounts = new java.util.ArrayList<>(parseAmountList(
                    plugin.getConfig().getList("quick-amounts",
                            Arrays.asList(10000, 20000, 50000, 100000, 200000, 500000, 1000000))));
            if (!amounts.contains(1_000_000)) { amounts.add(1_000_000); java.util.Collections.sort(amounts); }
            int amt = NapBankGui.getAmountBySlot(slot, amounts);
            if (amt > 0) {
                boolean testMode = s.testMode; // đọc trước khi clear session
                player.closeInventory();
                GuiSession.clear(player.getUniqueId());
                if (testMode) {
                    // v5.0.0: /testnapbank — giả lập thành công ngay, KHÔNG tạo QR/đơn thật
                    simulateBankSuccess(plugin, player, amt);
                } else {
                    new NapBankCommand(plugin).requestAndGiveQR(player, amt);
                }
            }
            return;
        }

        // ── ChinhSua ──────────────────────────────────────────────────────────
        if (title.equals("§6§lChỉnh sửa mệnh giá nạp")) {
            if (slot == 49) { player.closeInventory(); return; }
            ChinhSuaGui.SlotInfo info = ChinhSuaGui.getSlotInfo(slot);
            if (info != null) {
                s.editDenom = info.denom();
                s.editType  = info.type();
                s.editCmdList.clear(); // v5.0.0: reset danh sách lệnh trước khi bắt đầu nhập lại
                // FIX v4.1.0: Hỏi CMD trước (lệnh thưởng), sau đó mới hỏi AMT (số lượng)
                s.stage = "card".equals(info.type())
                        ? GuiSession.Stage.EDIT_WAIT_CMD_CARD
                        : GuiSession.Stage.EDIT_WAIT_CMD_BANK;
                player.closeInventory();
                player.sendMessage(NapTienPlugin.f("§6[PayBot] §eCấu hình mệnh giá §b"
                        + formatDenom(info.denom()) + " §e(" + info.type() + ")"));
                player.sendMessage(NapTienPlugin.f("§6Nhập §blệnh thưởng #1 §e(không cần dấu /):  §7§o(cancel để huỷ)"));
                player.sendMessage("§7Biến dùng được trong lệnh:");
                player.sendMessage("§b[playername] §7→ tên người chơi nạp");
                player.sendMessage("§b[amount]     §7→ số lượng thưởng");
                player.sendMessage("§7Ví dụ: §feco give [playername] [amount]");
                player.sendMessage("§7Ví dụ: §fgive [playername] diamond [amount]");
                player.sendMessage("§7§oSau lệnh #1, plugin sẽ hỏi tiếp lệnh #2 (tối đa 10 lệnh/mệnh giá) —");
                player.sendMessage("§7§ogõ §cskip §7§okhi không muốn thêm lệnh nữa.");
            }
            return;
        }

        // ── TopupList ─────────────────────────────────────────────────────────
        // ── PayBotPlaceholder: Main menu ────────────────────────────────────────
        if (title.equals(PayBotPlaceholderGui.MAIN_TITLE)) {
            if (slot == PayBotPlaceholderGui.getCloseSlot()) { player.closeInventory(); return; }
            if (slot == PayBotPlaceholderGui.getLeaderboardSlot()) {
                PayBotPlaceholderGui.openLeaderboard(player, plugin);
            }
            return;
        }

        // ── PayBotPlaceholder: Leaderboard ──────────────────────────────────────
        if (title.equals(PayBotPlaceholderGui.LEADERBOARD_TITLE)) {
            if (slot == PayBotPlaceholderGui.getCloseSlot()) { player.closeInventory(); return; }
            if (slot == PayBotPlaceholderGui.getBackSlot()) {
                PayBotPlaceholderGui.openMain(player, plugin);
            }
            return;
        }

        if (title.startsWith("§6§lDanh sách đơn")) {
            int page = s.editDenom; // reused for page
            String filter = s.topupListFilter; // v5.0.0 (fix): nhớ filter card/bank/null khi phân trang
            if (slot == 49) { player.closeInventory(); return; }
            if (slot == 45 && page > 0) { // Prev page
                TopupListGui.open(player, plugin, page - 1, filter);
                return;
            }
            if (slot == 53) { // Next page
                TopupListGui.open(player, plugin, page + 1, filter);
                return;
            }
            // Approve order
            TopupListGui.OrderEntry order = TopupListGui.getOrderBySlot(slot, plugin, page, filter);
            if (order != null) {
                handleApproveOrder(player, order);
                // Refresh GUI
                Bukkit.getScheduler().runTaskLater(plugin, () -> TopupListGui.open(player, plugin, page, filter), 2L);
            }
            return;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INVENTORY CLOSE — clear GUI stages (not chat-wait stages)
    // ══════════════════════════════════════════════════════════════════════════
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        GuiSession s = GuiSession.get(player.getUniqueId());
        if (!s.isWaitingForChat()) {
            s.stage = GuiSession.Stage.NONE;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CHAT — private input handling
    // ══════════════════════════════════════════════════════════════════════════
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        GuiSession s  = GuiSession.get(player.getUniqueId());
        if (!s.isWaitingForChat()) return;

        event.setCancelled(true); // ← Other players CANNOT see this message
        String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("cancel")) {
            GuiSession.clear(player.getUniqueId());
            player.sendMessage(NapTienPlugin.f("§c[PayBot] §fĐã huỷ."));
            return;
        }

        switch (s.stage) {

            // ── Card: mã thẻ (bước 1) ──────────────────────────────────────────
            case CARD_WAIT_CODE -> {
                if (!input.matches("[0-9]+")) {
                    player.sendMessage(NapTienPlugin.f("§c[PayBot] §fMã thẻ chỉ chứa số! Nhập lại:")); return;
                }
                s.cardCode = input;   // v4.3.0: lưu mã thẻ, chuyển sang hỏi serial
                s.stage    = GuiSession.Stage.CARD_WAIT_SERIAL;
                player.sendMessage(NapTienPlugin.f("§7[PayBot] §fĐã nhận mã thẻ: §b" + maskFull(input)));
                player.sendMessage(NapTienPlugin.f("§6[PayBot] §eNhập §bserial §ethẻ vào chat:  §7§o(cancel để huỷ)"));
            }

            // ── Card: serial (bước 2) ───────────────────────────────────────────
            case CARD_WAIT_SERIAL -> {
                if (!input.matches("[0-9]+")) {
                    player.sendMessage(NapTienPlugin.f("§c[PayBot] §fSerial chỉ chứa số! Nhập lại:")); return;
                }
                String telco  = s.telco;
                int    denom  = s.denom;
                String code   = s.cardCode;   // v4.3.0: lấy mã thẻ đã nhận ở bước 1
                String serial = input;
                GuiSession.clear(player.getUniqueId());
                // Echo masked
                player.sendMessage(NapTienPlugin.f("§7[PayBot] §fĐã nhận serial: §b" + maskPartial(serial)));
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.getCardManager().setPending(player.getName(),
                            new com.naptien.managers.CardManager.PendingCard(
                                    player.getName(), telco, denom, code, serial));
                    player.sendMessage(NapTienPlugin.f("§e[PayBot] §fXem lại trước khi xác nhận:"));
                    player.sendMessage("§7Nhà mạng: §f" + telco + "  §7Mệnh giá: §f" + formatVnd(denom) + " VND");
                    player.sendMessage("§7Mã thẻ  : §a" + code);
                    player.sendMessage("§7Serial  : §a" + serial);
                    player.sendMessage("§aDùng §e/ok §ađể xác nhận. §7§o(Chỉ bạn mới thấy thông tin này)");
                });
            }

            // ── Edit denom: command (bước 1 — hỏi CMD trước, tối đa 10 lệnh) ─────
            case EDIT_WAIT_CMD_CARD, EDIT_WAIT_CMD_BANK -> {
                boolean isFirst = s.editCmdList.isEmpty();
                if (!isFirst && input.equalsIgnoreCase("skip")) {
                    s.stage = (s.stage == GuiSession.Stage.EDIT_WAIT_CMD_CARD)
                            ? GuiSession.Stage.EDIT_WAIT_AMT_CARD
                            : GuiSession.Stage.EDIT_WAIT_AMT_BANK;
                    player.sendMessage(NapTienPlugin.f("§7[PayBot] §fĐã dừng — tổng §a"
                            + s.editCmdList.size() + " §flệnh thưởng."));
                    player.sendMessage(NapTienPlugin.f("§6[PayBot] §eNhập §bsố lượng item thưởng khi nạp §e(số nguyên):  §7§o(cancel để huỷ)"));
                    return;
                }
                s.editCmdList.add(input);
                player.sendMessage(NapTienPlugin.f("§7[PayBot] §fLệnh #" + s.editCmdList.size() + " đã nhận: §a" + input));
                if (s.editCmdList.size() >= com.naptien.managers.RewardDispatcher.MAX_CMDS) {
                    s.stage = (s.stage == GuiSession.Stage.EDIT_WAIT_CMD_CARD)
                            ? GuiSession.Stage.EDIT_WAIT_AMT_CARD
                            : GuiSession.Stage.EDIT_WAIT_AMT_BANK;
                    player.sendMessage(NapTienPlugin.f("§7[PayBot] §fĐã đạt tối đa §e"
                            + com.naptien.managers.RewardDispatcher.MAX_CMDS + " §flệnh."));
                    player.sendMessage(NapTienPlugin.f("§6[PayBot] §eNhập §bsố lượng item thưởng khi nạp §e(số nguyên):  §7§o(cancel để huỷ)"));
                } else {
                    player.sendMessage(NapTienPlugin.f("§6[PayBot] §eNhập §blệnh thưởng #" + (s.editCmdList.size() + 1)
                            + " §e(hoặc gõ §cskip §eđể dừng):  §7§o(cancel để huỷ)"));
                }
            }

            // ── Edit denom: amount (bước 2 — hỏi AMT sau) ────────────────────────
            case EDIT_WAIT_AMT_CARD, EDIT_WAIT_AMT_BANK -> {
                try { Integer.parseInt(input); }
                catch (NumberFormatException e) {
                    player.sendMessage(NapTienPlugin.f("§c[PayBot] §fPhải nhập số nguyên! Nhập lại:")); return;
                }
                String type    = s.editType;
                int    denom   = s.editDenom;
                List<String> cmds = new java.util.ArrayList<>(s.editCmdList);
                String amt     = input;
                GuiSession.clear(player.getUniqueId());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    String section = "card".equals(type) ? "denom-rewards-card" : "denom-rewards-bank";
                    plugin.getConfig().set(section + "." + denom + ".cmds", cmds); // v5.0.0: list (tối đa 10)
                    plugin.getConfig().set(section + "." + denom + ".cmd",  cmds.get(0)); // backward-compat: lệnh #1
                    plugin.getConfig().set(section + "." + denom + ".amt", amt);
                    plugin.forceConfigRefresh("chỉnh mệnh giá nạp (" + type + " " + denom + ")");
                    if (!plugin.isStandaloneMode()) {
                        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                                plugin.getBotHttpClient().pushRewardConfig());
                    }
                    StringBuilder summary = new StringBuilder();
                    for (int i = 0; i < cmds.size(); i++) {
                        summary.append("\n  §7#").append(i + 1).append(": §a").append(cmds.get(i));
                    }
                    player.sendMessage(NapTienPlugin.f("§a[PayBot] §fĐã lưu §e" + formatDenom(denom)
                            + " §f(" + type + "): §a" + cmds.size() + " lệnh §f| thưởng §a" + amt + summary));
                    ChinhSuaGui.open(player, plugin);
                });
            }

            default -> {}
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PLAYER QUIT
    // ══════════════════════════════════════════════════════════════════════════
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        GuiSession.remove(event.getPlayer().getUniqueId());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // APPROVE ORDER LOGIC
    // ══════════════════════════════════════════════════════════════════════════
    private void handleApproveOrder(Player admin, TopupListGui.OrderEntry order) {
        try {
            handleApproveOrderInternal(admin, order);
        } catch (Exception e) {
            NotificationManager.warn(plugin, "approve-fail",
                    "[PayBot] Duyệt đơn lỗi: " + order.id() + " — " + e.getMessage(), e);
            admin.sendMessage(NapTienPlugin.f("§c[PayBot] §fDuyệt đơn lỗi! Xem console để biết thêm."));
        }
    }

    private void handleApproveOrderInternal(Player admin, TopupListGui.OrderEntry order) {
        LocalOrderManager lom = plugin.getLocalOrderManager();

        if ("card".equals(order.type())) {
            if (LocalOrderManager.CARD_APPROVED.equals(order.status())) {
                admin.sendMessage(NapTienPlugin.f("§c[PayBot] §fĐơn này đã được duyệt rồi.")); return;
            }
            // v5.0.0 (fix Thứ 3 — guard an toàn): TopupListGui giờ hiện TẤT CẢ đơn (kể cả
            // fail/đang xử lý), nên phải chặn duyệt nếu đơn KHÔNG ở trạng thái CARD_SUCCESS —
            // tránh admin bấm nhầm vào đơn thẻ sai/lỗi mà vẫn được cấp thưởng.
            if (!LocalOrderManager.CARD_SUCCESS.equals(order.status())) {
                admin.sendMessage(NapTienPlugin.f("§c[PayBot] §fĐơn này không ở trạng thái có thể duyệt "
                        + "(hiện tại: §e" + com.naptien.log.LogManager.cardStatusLabel(order.status()) + "§f)."));
                return;
            }
            List<String> rewardCmds = RewardDispatcher.resolveRewardCmds(plugin, order.amount(), "card");
            if (rewardCmds.isEmpty()) {
                admin.sendMessage(NapTienPlugin.f("§c[PayBot] §fChưa cấu hình lệnh thưởng cho mệnh giá §e"
                        + formatVnd(order.amount()) + " §f(card). Dùng §e/chinhsuamenhgianap§f."));
                return;
            }
            String rewardAmt = RewardDispatcher.computeRewardAmt(plugin, order.amount(), "card");
            lom.updateCardStatus(order.id(), LocalOrderManager.CARD_APPROVED, "admin approved");
            boolean wasOnline = RewardDispatcher.dispatchOrQueue(plugin, order.id(), order.playerName(),
                    rewardCmds, rewardAmt, String.valueOf(order.amount()), "card", null, "");
            admin.sendMessage(NapTienPlugin.f("§a[PayBot] §fĐã duyệt đơn thẻ §e" + order.playerName()
                    + " §f- §b" + formatVnd(order.amount()) + " VND"
                    + (wasOnline ? "" : " §7(player offline — sẽ nhận khi vào lại)")));

        } else { // bank
            if (LocalOrderManager.BANK_APPROVED.equals(order.status())) {
                admin.sendMessage(NapTienPlugin.f("§c[PayBot] §fĐơn này đã được duyệt rồi.")); return;
            }
            // v5.0.0 (fix Thứ 3 — guard an toàn): tương tự, chỉ cho duyệt đơn BANK_PAID.
            if (!LocalOrderManager.BANK_PAID.equals(order.status())) {
                admin.sendMessage(NapTienPlugin.f("§c[PayBot] §fĐơn này không ở trạng thái có thể duyệt "
                        + "(hiện tại: §e" + com.naptien.log.LogManager.bankStatusLabel(order.status()) + "§f)."));
                return;
            }
            List<String> rewardCmds = RewardDispatcher.resolveRewardCmds(plugin, order.amount(), "bank");
            if (rewardCmds.isEmpty()) {
                admin.sendMessage(NapTienPlugin.f("§c[PayBot] §fChưa cấu hình lệnh thưởng cho mệnh giá §e"
                        + formatVnd(order.amount()) + " §f(bank). Dùng §e/chinhsuamenhgianap§f."));
                return;
            }
            String rewardAmt = RewardDispatcher.computeRewardAmt(plugin, order.amount(), "bank");
            lom.updateBankStatus(order.id(), LocalOrderManager.BANK_APPROVED);
            boolean wasOnline = RewardDispatcher.dispatchOrQueue(plugin, order.id(), order.playerName(),
                    rewardCmds, rewardAmt, String.valueOf(order.amount()), "bank", order.id(), "");
            admin.sendMessage(NapTienPlugin.f("§a[PayBot] §fĐã duyệt đơn bank §e" + order.playerName()
                    + " §f- §b" + formatVnd(order.amount()) + " VND"
                    + (wasOnline ? "" : " §7(player offline — sẽ nhận khi vào lại)")));
        }
    }

    /** Tính số lượng thưởng cuối cùng (đã áp dụng hệ số Rewards_Card_Mode/Rewards_Bank_Mode). */
    // (computeRewardAmt đã gộp vào RewardDispatcher.computeRewardAmt — dùng chung với ApproveCommand)

    // ══════════════════════════════════════════════════════════════════════════
    // TEST COMMANDS — /testnapbank, /testnapthe (v5.0.0)
    // Giả lập đơn nạp THÀNH CÔNG để admin tự kiểm tra chuỗi reward + hiệu ứng
    // (pháo hoa/sound/thông báo) mà KHÔNG cần SePay/thẻ thật, KHÔNG lưu vào lịch sử đơn.
    // ══════════════════════════════════════════════════════════════════════════
    public static void simulateBankSuccess(NapTienPlugin plugin, Player player, int amount) {
        List<String> cmds = RewardDispatcher.resolveRewardCmds(plugin, amount, "bank");
        if (cmds.isEmpty()) {
            player.sendMessage(NapTienPlugin.f("§c[TEST] §fChưa cấu hình lệnh thưởng cho mệnh giá §e"
                    + formatVnd(amount) + " §f(bank). Dùng §e/chinhsuamenhgianap §fđể cấu hình trước."));
            return;
        }
        String rewardAmt = RewardDispatcher.computeRewardAmt(plugin, amount, "bank");
        String testId    = "TEST-BANK-" + System.currentTimeMillis();
        player.sendMessage(NapTienPlugin.f("§e§l[TEST] §r§eĐã giả lập đơn nạp bank §b" + formatVnd(amount)
                + " VND §ethành công cho §b" + player.getName() + "§e!"));
        player.sendMessage("§7§o(Không tạo QR/đơn thật, không lưu vào /topuplist — chỉ test reward + hiệu ứng)");
        RewardDispatcher.dispatchOrQueue(plugin, testId, player.getName(), cmds, rewardAmt,
                String.valueOf(amount), "bank", null, "");
    }

    public static void simulateCardSuccess(NapTienPlugin plugin, Player player, String telco, int amount) {
        List<String> cmds = RewardDispatcher.resolveRewardCmds(plugin, amount, "card");
        if (cmds.isEmpty()) {
            player.sendMessage(NapTienPlugin.f("§c[TEST] §fChưa cấu hình lệnh thưởng cho mệnh giá §e"
                    + formatVnd(amount) + " §f(card). Dùng §e/chinhsuamenhgianap §fđể cấu hình trước."));
            return;
        }
        String rewardAmt = RewardDispatcher.computeRewardAmt(plugin, amount, "card");
        String testId    = "TEST-CARD-" + System.currentTimeMillis();
        player.sendMessage(NapTienPlugin.f("§e§l[TEST] §r§eĐã giả lập thẻ §b" + telco + " " + formatVnd(amount)
                + " VND §ethành công cho §b" + player.getName() + "§e!"));
        player.sendMessage("§7§o(Không gửi thẻ/API thật, không lưu vào /topuplist — chỉ test reward + hiệu ứng)");
        RewardDispatcher.dispatchOrQueue(plugin, testId, player.getName(), cmds, rewardAmt,
                String.valueOf(amount), "card", null, "");
    }

    // ─── Masking helpers ──────────────────────────────────────────────────────
    /** Show first 2 + last 2 chars, rest as * */
    private static String maskPartial(String s) {
        if (s.length() <= 4) return "****";
        return s.substring(0, 2) + "*".repeat(s.length() - 4) + s.substring(s.length() - 2);
    }

    /** Fully mask — only show length */
    private static String maskFull(String s) {
        return "§8[" + s.length() + " ký tự — ẩn]";
    }
}
