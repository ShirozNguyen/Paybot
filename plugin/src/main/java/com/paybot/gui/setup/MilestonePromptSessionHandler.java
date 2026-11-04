package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import com.paybot.milestone.MilestoneConfig;
import com.paybot.milestone.MilestoneManager;
import com.paybot.milestone.MilestoneModel;
import com.paybot.utils.SchedulerUtils;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MilestonePromptSessionHandler — v7.0.2 (Part 186)
 * TUÂN THỦ QUY TẮC 17: Class đơn nhiệm quản lý các phiên chat nhập liệu cho Mốc Nạp (Milestone).
 * <p>
 * Hỗ trợ:
 * 1. Nhập số tiền mốc mới / đổi số tiền mốc hiện tại.
 * 2. Cấu hình danh sách lệnh thưởng mốc: hỗ trợ cả nhập 1 dòng có dấu chấm phẩy (;)
 *    lẫn nhập nhiều dòng liên tiếp cho đến khi gõ 'done' / 'xong' / 'save'.
 * 3. Hỗ trợ gõ 'huy' / 'cancel' để hủy an toàn và mở lại menu.
 */
public final class MilestonePromptSessionHandler {

    private static final Map<UUID, MilestoneDraftSession> draftSessions = new ConcurrentHashMap<>();

    private MilestonePromptSessionHandler() {}

    public static boolean isHandling(UUID playerId) {
        return draftSessions.containsKey(playerId);
    }

    /**
     * Bắt đầu phiên thêm mốc nạp mới (nhập số tiền).
     */
    public static void startAddMilestone(Player player, boolean isServerMode) {
        if (player == null) return;
        MilestoneDraftSession session = new MilestoneDraftSession(
                isServerMode,
                0L,
                DraftMode.INPUT_AMOUNT,
                new ArrayList<>()
        );
        draftSessions.put(player.getUniqueId(), session);
        player.closeInventory();
        player.sendMessage("§e[PayBot Mốc Nạp] §fBạn ơi, hãy nhập §aSố tiền mốc nạp mới §7(VNĐ, ví dụ: §e100000§7) vào chat:");
        player.sendMessage("§7(Gõ §c'huy' §7hoặc §c'cancel' §7để hủy thao tác nè).");
    }

    /**
     * Bắt đầu phiên sửa số tiền mốc hiện có.
     */
    public static void startEditAmount(Player player, boolean isServerMode, long oldAmount) {
        if (player == null) return;
        MilestoneConfig config = getMilestoneConfig();
        List<String> existingCmds = new ArrayList<>();
        if (config != null) {
            Map<Long, MilestoneModel> map = isServerMode ? config.getGlobalMilestones() : config.getSingleMilestones();
            MilestoneModel model = map.get(oldAmount);
            if (model != null && model.getCommands() != null) {
                existingCmds.addAll(model.getCommands());
            }
        }
        MilestoneDraftSession session = new MilestoneDraftSession(
                isServerMode,
                oldAmount,
                DraftMode.INPUT_AMOUNT,
                existingCmds
        );
        draftSessions.put(player.getUniqueId(), session);
        player.closeInventory();
        player.sendMessage("§e[PayBot Mốc Nạp] §fMốc hiện tại: §6" + String.format("%,d", oldAmount) + " VNĐ§f. Hãy nhập §aSố tiền mốc mới §fvào chat:");
        player.sendMessage("§7(Gõ §c'huy' §7hoặc §c'cancel' §7để hủy).");
    }

    /**
     * Bắt đầu phiên sửa lệnh thưởng cho mốc nạp.
     */
    public static void startEditCommands(Player player, boolean isServerMode, long amount) {
        if (player == null) return;
        MilestoneConfig config = getMilestoneConfig();
        List<String> existingCmds = new ArrayList<>();
        if (config != null) {
            Map<Long, MilestoneModel> map = isServerMode ? config.getGlobalMilestones() : config.getSingleMilestones();
            MilestoneModel model = map.get(amount);
            if (model != null && model.getCommands() != null) {
                existingCmds.addAll(model.getCommands());
            }
        }
        MilestoneDraftSession session = new MilestoneDraftSession(
                isServerMode,
                amount,
                DraftMode.INPUT_COMMANDS,
                existingCmds
        );
        draftSessions.put(player.getUniqueId(), session);
        player.closeInventory();
        player.sendMessage("§e[PayBot Mốc Nạp] §fĐang chỉnh sửa lệnh thưởng cho mốc: §6" + String.format("%,d", amount) + " VNĐ");
        if (!existingCmds.isEmpty()) {
            player.sendMessage("§7Các lệnh hiện có (" + existingCmds.size() + " lệnh):");
            for (int i = 0; i < existingCmds.size(); i++) {
                player.sendMessage("  §8" + (i + 1) + ". §b" + existingCmds.get(i));
            }
        }
        player.sendMessage("§a▶ Nhập lệnh thưởng mới vào chat §7(Dùng {player}, %amount%...).");
        player.sendMessage("§7• Bạn có thể nhập nhiều lệnh cách nhau bằng dấu chấm phẩy (§e;§7)");
        player.sendMessage("§7• Hoặc nhập từng dòng. Khi xong gõ §a'done'§7, §a'xong' §7hoặc §a'save'§7.");
        player.sendMessage("§7• Gõ §c'huy' §7để hủy thao tác.");
    }

    /**
     * Xử lý tin nhắn chat từ Admin trong phiên nhập liệu.
     */
    public static boolean handleChatInput(PayBotPlugin plugin, Player player, String rawInput) {
        if (player == null) return false;
        MilestoneDraftSession session = draftSessions.get(player.getUniqueId());
        if (session == null) return false;

        String input = rawInput != null ? rawInput.trim() : "";

        // Hủy thao tác
        if ("huy".equalsIgnoreCase(input) || "cancel".equalsIgnoreCase(input)) {
            draftSessions.remove(player.getUniqueId());
            player.sendMessage("§e[PayBot Mốc Nạp] §7Đã hủy thao tác. Đang mở lại danh sách mốc nạp!");
            reopenGui(plugin, player, session.isServerMode);
            return true;
        }

        MilestoneConfig config = getMilestoneConfig();
        if (config == null) {
            draftSessions.remove(player.getUniqueId());
            player.sendMessage("§c[PayBot Mốc Nạp] Hệ thống mốc nạp chưa sẵn sàng!");
            return true;
        }

        // Nhập số tiền mốc
        if (session.mode == DraftMode.INPUT_AMOUNT) {
            try {
                String cleanNumber = input.replace(".", "").replace(",", "").replace("_", "").trim();
                long amount = Long.parseLong(cleanNumber);
                if (amount <= 0) {
                    player.sendMessage("§c[PayBot Mốc Nạp] ✗ Số tiền mốc phải là số nguyên dương > 0!");
                    return true;
                }

                if (session.oldAmount == 0L) {
                    // Thêm mốc mới: chuyển sang nhập lệnh thưởng
                    session.oldAmount = amount;
                    session.mode = DraftMode.INPUT_COMMANDS;
                    if (session.commands.isEmpty()) {
                        session.commands.add("give [playername] diamond 1");
                    }
                    player.sendMessage("§a[PayBot Mốc Nạp] §f✓ Đã đặt số tiền mốc: §6" + String.format("%,d", amount) + " VNĐ!");
                    player.sendMessage("§a▶ Bây giờ hãy nhập Lệnh thưởng cho mốc này (hoặc gõ §e'done' §ađể dùng lệnh mặc định):");
                    player.sendMessage("§7(Gõ các lệnh cách nhau bằng §e; §7hoặc nhập từng dòng, xong gõ §a'done'§7)");
                    return true;
                } else {
                    // Đổi số tiền mốc hiện tại
                    long oldAmt = session.oldAmount;
                    draftSessions.remove(player.getUniqueId());
                    if (session.isServerMode) {
                        config.addOrUpdateGlobalMilestone(oldAmt, amount, session.commands);
                    } else {
                        config.addOrUpdateSingleMilestone(oldAmt, amount, session.commands);
                    }
                    player.sendMessage("§a[PayBot Mốc Nạp] §f✓ Đã đổi mốc từ §6" + String.format("%,d", oldAmt)
                            + " VNĐ §fsang §6" + String.format("%,d", amount) + " VNĐ §fthành công!");
                    reopenGui(plugin, player, session.isServerMode);
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§c[PayBot Mốc Nạp] ✗ Số tiền không hợp lệ! Vui lòng chỉ nhập số (ví dụ: §e200000§c).");
                return true;
            }
        }

        // Nhập lệnh thưởng
        if (session.mode == DraftMode.INPUT_COMMANDS) {
            if ("done".equalsIgnoreCase(input) || "xong".equalsIgnoreCase(input) || "save".equalsIgnoreCase(input)) {
                draftSessions.remove(player.getUniqueId());
                long targetAmt = session.oldAmount;
                if (session.commands.isEmpty()) {
                    session.commands.add("give [playername] diamond 1");
                }
                if (session.isServerMode) {
                    config.addOrUpdateGlobalMilestone(targetAmt, targetAmt, session.commands);
                } else {
                    config.addOrUpdateSingleMilestone(targetAmt, targetAmt, session.commands);
                }
                player.sendMessage("§a[PayBot Mốc Nạp] §f✓ Đã lưu thành công §e" + session.commands.size()
                        + " lệnh thưởng §fcho mốc §6" + String.format("%,d", targetAmt) + " VNĐ!");
                reopenGui(plugin, player, session.isServerMode);
                return true;
            }

            // Tách lệnh nếu có dấu ; hoặc ;;
            String[] parts = input.split("(?:;;|§§|;)");
            for (String part : parts) {
                String cmd = part.trim();
                if (!cmd.isEmpty()) {
                    if (cmd.startsWith("/")) cmd = cmd.substring(1);
                    session.commands.add(cmd);
                    player.sendMessage("§a[PayBot Mốc Nạp] §f+ Đã thêm lệnh: §b" + cmd);
                }
            }
            player.sendMessage("§7(Nhập thêm lệnh nữa hoặc gõ §a'done' §7để hoàn tất lưu lại nha).");
            return true;
        }

        return false;
    }

    private static MilestoneConfig getMilestoneConfig() {
        if (MilestoneManager.getInstance() != null) {
            return MilestoneManager.getInstance().getConfig();
        }
        return null;
    }

    private static void reopenGui(PayBotPlugin plugin, Player player, boolean isServerMode) {
        SchedulerUtils.runSync(plugin, () -> {
            if (player.isOnline()) {
                SetupMilestoneListGui gui = new SetupMilestoneListGui(plugin, isServerMode, 0);
                gui.open(player);
            }
        });
    }

    private enum DraftMode {
        INPUT_AMOUNT,
        INPUT_COMMANDS
    }

    private static class MilestoneDraftSession {
        final boolean isServerMode;
        long oldAmount;
        DraftMode mode;
        final List<String> commands;

        MilestoneDraftSession(boolean isServerMode, long oldAmount, DraftMode mode, List<String> commands) {
            this.isServerMode = isServerMode;
            this.oldAmount = oldAmount;
            this.mode = mode;
            this.commands = commands != null ? new ArrayList<>(commands) : new ArrayList<>();
        }
    }
}
