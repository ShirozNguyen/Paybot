package com.naptien.managers;

import com.naptien.NapTienPlugin;
import com.naptien.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * RewardDispatcher — Nơi DUY NHẤT build + thực thi lệnh thưởng cho toàn plugin.
 * <p>
 * Trước v5.0.0 (đợt fix này), logic build placeholder + dispatch command + gửi tin nhắn
 * bị copy-paste riêng ở 5 nơi khác nhau (GuiListener, NapTienPlugin x2, ApproveCommand,
 * PluginHttpServer) và mỗi nơi có 1 bug khác nhau (thiếu {player}, thiếu pháo hoa, thiếu
 * fallback offline,...). Class này gộp lại 1 chỗ duy nhất để sửa 1 lần là sửa hết.
 * <p>
 * Quy tắc bắn hiệu ứng (pháo hoa / sound / action-bar) — theo yêu cầu:
 *   - Đơn được DUYỆT (admin GUI, /approve, hoặc bot tự duyệt) MÀ player đang ONLINE
 *     tại đúng thời điểm đó → bắn hiệu ứng ngay (tier theo mệnh giá nạp).
 *   - Đơn được duyệt nhưng player OFFLINE → chỉ lưu vào hàng chờ (OfflineRewardManager),
 *     KHÔNG bắn hiệu ứng, KHÔNG spam thông báo/log (admin đã biết là duyệt rồi).
 *   - Khi player join lại và reward được giao trễ → bắn hiệu ứng tại thời điểm giao
 *     (vì lúc này player chắc chắn đang online để thấy).
 * <p>
 * Hỗ trợ NHIỀU lệnh thưởng / mệnh giá (tối đa 10, xem ChinhSuaGui + GuiListener chat-flow):
 * mỗi đơn có thể chạy 1..10 lệnh, nhưng hiệu ứng/thông báo chỉ bắn 1 LẦN cho toàn bộ đơn
 * (không bắn lại theo từng lệnh).
 *
 * Changelog:
 *   v5.0.0 — Thêm mới, gộp logic từ 5 nơi cũ + hỗ trợ thêm placeholder {player}/{amount}
 *            (Thứ 6) + offline-queue cho mọi đường duyệt (GUI/approve/webhook) (firework
 *            condition) + hook NotificationManager cho toàn bộ log/thông báo lỗi.
 *   v5.0.0 (fix 2) — Hỗ trợ multi-command (tối đa 10 lệnh/mệnh giá), đọc cả field mới
 *            "cmds" (list) và field cũ "cmd" (string) để tương thích ngược.
 */
public final class RewardDispatcher {

    private RewardDispatcher() {}

    /** Số lệnh tối đa cho phép cấu hình trên 1 mệnh giá. */
    public static final int MAX_CMDS = 30;

    /**
     * Dùng làm dấu phân tách khi LƯU TẠM nhiều lệnh thành 1 chuỗi đơn (vd. khi đưa vào
     * OfflineRewardManager — file đó đang lưu field "rewardCmd" dạng String đơn, không phải
     * List, nên ta nối nhiều lệnh lại bằng dấu này rồi tách ra lúc đọc, để KHÔNG phải đổi
     * format file offline-rewards.yml đã có từ trước (giữ tương thích ngược dữ liệu cũ)).
     */
    private static final String CMD_SEP = "\n";

    public static String joinCmds(List<String> cmds) {
        return String.join(CMD_SEP, cmds);
    }

    public static List<String> splitCmds(String joined) {
        List<String> out = new ArrayList<>();
        if (joined == null || joined.isEmpty()) return out;
        for (String s : joined.split(CMD_SEP)) {
            if (!s.isBlank()) out.add(s);
        }
        return out;
    }

    /**
     * Đọc danh sách lệnh thưởng đã cấu hình cho 1 mệnh giá + loại (card/bank).
     * Ưu tiên field mới {@code <section>.<denom>.cmds} (list, tối đa 10 lệnh — cấu hình
     * qua /chinhsuamenhgianap). Nếu không có, fallback field cũ {@code <section>.<denom>.cmd}
     * (string đơn — tương thích config cũ / config được bot Discord push xuống qua
     * receive-config, bot hiện chỉ hỗ trợ 1 lệnh/mệnh giá). Cuối cùng fallback
     * {@code reward-command} (lệnh chung toàn server).
     */
    /**
     * Parse hệ số thưởng (Rewards_Bank_Mode / Rewards_Card_Mode):
     * - Hỗ trợ "2x", 2, 2.2, "2,2" -> tự làm tròn với Math.round()
     * - Cảnh báo WARNING lên log nếu giá trị là số lẻ.
     * - Đảm bảo giá trị >= 1.
     */
    public static int parseRewardMode(NapTienPlugin plugin, String type) {
        String key = "bank".equalsIgnoreCase(type) ? "Rewards_Bank_Mode" : "Rewards_Card_Mode";
        Object raw = plugin.getConfig().get(key);
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
                plugin.getLogger().warning("[PayBot] CẢNH BÁO: " + key + " có giá trị lẻ '" + raw + "', đã tự động làm tròn thành " + mode + ".");
            }
            return mode;
        } catch (Exception e) {
            return 1;
        }
    }

    /**
     * Đọc danh sách lệnh thưởng đã cấu hình cho 1 mệnh giá + loại (card/bank).
     * Hỗ trợ tách nhiều lệnh trong 1 chuỗi bằng dấu ; hoặc \n hoặc ;; hoặc §§.
     */
    public static List<String> resolveRewardCmds(NapTienPlugin plugin, int amount, String type) {
        String section = "bank".equalsIgnoreCase(type) ? "denom-rewards-bank" : "denom-rewards-card";
        List<String> list = new ArrayList<>();

        List<?> rawList = plugin.getConfig().getList(section + "." + amount + ".cmds");
        if (rawList != null) {
            for (Object o : rawList) {
                if (o == null) continue;
                String s = String.valueOf(o).trim();
                if (!s.isEmpty()) {
                    for (String part : s.split("(?:;;|§§|;|\n)")) {
                        String t = part.trim();
                        if (!t.isEmpty()) list.add(t);
                    }
                }
            }
        }
        if (list.isEmpty()) {
            String single = plugin.getConfig().getString(section + "." + amount + ".cmd", "").trim();
            if (!single.isEmpty()) {
                for (String part : single.split("(?:;;|§§|;|\n)")) {
                    String t = part.trim();
                    if (!t.isEmpty()) list.add(t);
                }
            }
        }
        if (list.isEmpty()) {
            String fallback = plugin.getConfig().getString("reward-command", "").trim();
            if (!fallback.isEmpty()) {
                for (String part : fallback.split("(?:;;|§§|;|\n)")) {
                    String t = part.trim();
                    if (!t.isEmpty()) list.add(t);
                }
            }
        }
        if (list.size() > MAX_CMDS) list = list.subList(0, MAX_CMDS);
        return list;
    }

    /**
     * Build lệnh cuối cùng từ template thô.
     */
    public static String buildFinalCmd(String rawCmdTemplate, String playerName, String rewardAmt) {
        String cmd = rawCmdTemplate.replaceAll("\"([^\"]*)\"", "$1").trim();
        cmd = cmd.replace("[playername]", playerName)   // placeholder chuẩn (plugin)
                 .replace("{player}",     playerName)    // placeholder chuẩn (bot Discord)
                 .replace("[amount]",     rewardAmt)      // placeholder chuẩn
                 .replace("{amount}",     rewardAmt)      // alias bot Discord
                 .replace("[So luong]",   rewardAmt)      // backward compat
                 .replace("[Số lượng]",   rewardAmt)      // backward compat
                 .replace("playername",   playerName)     // backward compat (bare)
                 .replace("amount",       rewardAmt);      // backward compat (bare)
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        return cmd;
    }

    /** Tính số lượng thưởng hiển thị cho 1 mệnh giá. */
    public static String computeRewardAmt(NapTienPlugin plugin, int amount, String type) {
        String section = "bank".equalsIgnoreCase(type) ? "denom-rewards-bank" : "denom-rewards-card";
        return plugin.getConfig().getString(section + "." + amount + ".amt", String.valueOf(amount)).trim();
    }

    // ─── Multi-command entry points ───────────────────────────────────────────

    public static boolean dispatchOrQueue(NapTienPlugin plugin, String rewardId, String playerName,
                                           List<String> rawCmds, String rewardAmt, String denomVnd,
                                           String type, String invoiceId, String discordUid) {
        Player target = Bukkit.getPlayerExact(playerName);
        int mode = parseRewardMode(plugin, type);
        List<String> modeCmds = new ArrayList<>();
        for (int m = 0; m < mode; m++) {
            modeCmds.addAll(rawCmds);
        }

        if (target == null || !target.isOnline()) {
            plugin.getOfflineRewardManager().addReward(rewardId, playerName, joinCmds(modeCmds), rewardAmt,
                    denomVnd != null ? denomVnd : "", type, invoiceId, discordUid);
            return false;
        }
        deliverNow(plugin, target, modeCmds, rewardAmt, denomVnd, type, invoiceId, false);
        return true;
    }

    public static boolean dispatchOrQueue(NapTienPlugin plugin, String rewardId, String playerName,
                                           String singleRawCmd, String rewardAmt, String denomVnd,
                                           String type, String invoiceId, String discordUid) {
        List<String> cmds = new ArrayList<>();
        if (singleRawCmd != null && !singleRawCmd.isBlank()) {
            for (String part : singleRawCmd.split("(?:;;|§§|;|\n)")) {
                String t = part.trim();
                if (!t.isEmpty()) cmds.add(t);
            }
        }
        return dispatchOrQueue(plugin, rewardId, playerName, cmds,
                rewardAmt, denomVnd, type, invoiceId, discordUid);
    }

    /**
     * [Part 45] Đụng rất nhiều tới target (dispatchCommand hướng tới player, sendMessage,
     * QRMapManager.removeQRMap, RewardEffectManager.trigger — toàn bộ entity-specific).
     * TỰ dispatch qua entity-scheduler của target ở đây — KHÔNG giả định caller đã ở đúng
     * thread (audit phát hiện nhiều nơi gọi qua SchedulerUtils.runSync/Global — SAI ngữ
     * cảnh vì đó là Global Region Scheduler, không sở hữu region của target).
     */
    public static void deliverNow(NapTienPlugin plugin, Player target, List<String> rawCmds, String rewardAmt,
                                   String denomVnd, String type, String invoiceId, boolean isLate) {
        SchedulerUtils.runForPlayer(plugin, target, () ->
            deliverNowSync(plugin, target, rawCmds, rewardAmt, denomVnd, type, invoiceId, isLate));
    }

    private static void deliverNowSync(NapTienPlugin plugin, Player target, List<String> rawCmds, String rewardAmt,
                                   String denomVnd, String type, String invoiceId, boolean isLate) {
        boolean isBank = "bank".equalsIgnoreCase(type);
        boolean anyFail = false;

        for (String rawCmd : rawCmds) {
            if (rawCmd == null || rawCmd.isBlank()) continue;
            String finalCmd = buildFinalCmd(rawCmd, target.getName(), rewardAmt);
            boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
            NotificationManager.log(plugin, "reward-dispatch",
                    "[PayBot] Reward: /" + finalCmd + " -> " + ok + " (player=" + target.getName() + ")");
            if (!ok) {
                anyFail = true;
                NotificationManager.warn(plugin, "reward-invalid",
                        "[PayBot] Reward command THẤT BẠI cho " + target.getName() + ": /" + finalCmd);
            }
        }
        if (anyFail) {
            NotificationManager.notifyAdmins(plugin, "reward-invalid",
                    "§c[PayBot] §fCó §e1+ §flệnh reward lỗi cho §e" + target.getName()
                            + "§f — kiểm tra console!");
        }

        if (target.isOnline()) {
            String lateSuffix = isLate ? " (Xử lý trễ)" : "";
            if (isBank) {
                target.sendMessage(NapTienPlugin.f("§a§l[PayBot] §r§aThanh toán thành công!" + lateSuffix));
                if (denomVnd != null && !denomVnd.isEmpty()) {
                    try {
                        target.sendMessage("§7Số tiền: §f" + formatVnd(Integer.parseInt(denomVnd)) + " VND");
                    } catch (NumberFormatException ignored) {}
                }
                if (invoiceId != null && !invoiceId.isEmpty()) {
                    plugin.getQRMapManager().removeQRMap(target, invoiceId);
                }
            } else {
                target.sendMessage(NapTienPlugin.f("§a[PayBot] §fThẻ của bạn đã được xử lý thành công!" + lateSuffix));
            }
        }

        // Bắn pháo hoa / sound / action-bar — CHỈ tại đây (1 lần / đơn, không lặp theo lệnh),
        // vì tới đây player CHẮC CHẮN đang online (online ngay khi duyệt, hoặc vừa join lại
        // để nhận reward trễ). Tier (pháo thường / thành tựu epic) tính theo MỆNH GIÁ NẠP
        // (denomVnd), không phải số lượng reward (rewardAmt) — 2 giá trị này khác nhau.
        int tierAmount = parseAmountForTier(denomVnd, rewardAmt);
        RewardEffectManager.trigger(plugin, target, tierAmount);

        // v5.0.0 (Phase B): ghi nhận thống kê tổng nạp cho PlaceholderAPI và bắn PayBotTopupEvent
        if (denomVnd != null && !denomVnd.isEmpty()) {
            try {
                long parsedVnd = Long.parseLong(denomVnd.trim());
                plugin.getTopupStatsManager().recordTopup(target.getName(), parsedVnd, type != null ? type.toUpperCase() : "BANK");
                Bukkit.getPluginManager().callEvent(new com.naptien.events.PayBotTopupEvent(
                        target.getName(), parsedVnd, type != null ? type.toUpperCase() : "BANK", invoiceId
                ));
            } catch (NumberFormatException ignored) {}
        }
    }

    /** Overload tiện lợi cho nơi chỉ có 1 lệnh đơn. */
    public static void deliverNow(NapTienPlugin plugin, Player target, String singleRawCmd, String rewardAmt,
                                   String denomVnd, String type, String invoiceId, boolean isLate) {
        deliverNow(plugin, target, List.of(singleRawCmd), rewardAmt, denomVnd, type, invoiceId, isLate);
    }

    private static int parseAmountForTier(String denomVnd, String rewardAmt) {
        try {
            if (denomVnd != null && !denomVnd.isEmpty()) return Integer.parseInt(denomVnd.trim());
        } catch (NumberFormatException ignored) {}
        try {
            return Integer.parseInt(rewardAmt.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String formatVnd(int amount) {
        StringBuilder sb = new StringBuilder(String.valueOf(amount));
        for (int i = sb.length() - 3; i > 0; i -= 3) sb.insert(i, '.');
        return sb.toString();
    }
}
