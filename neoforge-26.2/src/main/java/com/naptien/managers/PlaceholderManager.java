package com.naptien.managers;

import com.naptien.PayBotMod;

import java.time.*;
import java.util.*;
import java.util.stream.*;

/**
 * PlaceholderManager (Fabric) — Tính toán và cung cấp dữ liệu thống kê nạp tiền.
 *
 * Các placeholder được hỗ trợ:
 *   %paybot_top_bank_N%          — tên player xếp hạng N bank (1-10)
 *   %paybot_top_bank_amount_N%   — tổng nạp bank của player xếp hạng N
 *   %paybot_top_card_N%          — tên player xếp hạng N thẻ cào
 *   %paybot_top_card_amount_N%   — tổng nạp thẻ của player xếp hạng N
 *   %paybot_top_total_N%         — tên player xếp hạng N tổng hợp
 *   %paybot_top_total_amount_N%  — tổng nạp của player xếp hạng N
 *   %paybot_server_bank_total%   — tổng nạp bank toàn server (VND)
 *   %paybot_server_card_total%   — tổng nạp thẻ toàn server (VND)
 *   %paybot_server_total%        — tổng tất cả toàn server (VND)
 *   %paybot_server_bank_count%   — số đơn bank đã duyệt
 *   %paybot_server_card_count%   — số đơn thẻ đã duyệt
 *   %paybot_player_bank%         — tổng nạp bank của player hiện tại
 *   %paybot_player_card%         — tổng nạp thẻ của player hiện tại
 *   %paybot_player_total%        — tổng nạp của player hiện tại
 *   %paybot_player_bank_rank%    — hạng nạp bank của player hiện tại
 *   %paybot_player_card_rank%    — hạng nạp thẻ của player hiện tại
 *   %paybot_player_total_rank%   — hạng tổng hợp của player hiện tại
 *   %paybot_today_bank%          — tổng nạp bank hôm nay
 *   %paybot_today_card%          — tổng nạp thẻ hôm nay
 *   %paybot_today_total%         — tổng tất cả hôm nay
 *
 * v5.0.0 — initial release
 */
public class PlaceholderManager {

    // ─── Records / Data classes ───────────────────────────────────────────────

    public record PlayerStat(
            String name,
            long bankTotal,
            long cardTotal,
            int  bankCount,
            int  cardCount
    ) {
        public long total()      { return bankTotal + cardTotal; }
        public int  totalCount() { return bankCount + cardCount; }
    }

    public record ServerStat(
            long bankTotal,
            long cardTotal,
            int  bankCount,
            int  cardCount
    ) {
        public long total()      { return bankTotal + cardTotal; }
        public int  totalCount() { return bankCount + cardCount; }
    }

    // ─── Fields ───────────────────────────────────────────────────────────────

    private final PayBotMod mod;

    public PlaceholderManager(PayBotMod mod) {
        this.mod = mod;
    }

    // ─── Top rankings ─────────────────────────────────────────────────────────

    /** Top N player theo tổng bank đã APPROVED. */
    public List<PlayerStat> getTopByBank(int limit) {
        Map<String, long[]> map = new TreeMap<>();
        for (LocalOrderManager.BankOrder o : mod.getLocalOrderManager().getAllBankOrders()) {
            if (!LocalOrderManager.BANK_APPROVED.equals(o.status)) continue;
            long[] arr = map.computeIfAbsent(o.playerName, k -> new long[2]);
            arr[0] += o.amount;
            arr[1]++;
        }
        return map.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(limit)
                .map(e -> new PlayerStat(e.getKey(), e.getValue()[0], 0L,
                        (int) e.getValue()[1], 0))
                .collect(Collectors.toList());
    }

    /** Top N player theo tổng thẻ đã APPROVED. */
    public List<PlayerStat> getTopByCard(int limit) {
        Map<String, long[]> map = new TreeMap<>();
        for (LocalOrderManager.CardOrder o : mod.getLocalOrderManager().getAllCardOrders()) {
            if (!LocalOrderManager.CARD_APPROVED.equals(o.status)) continue;
            long[] arr = map.computeIfAbsent(o.playerName, k -> new long[2]);
            arr[0] += o.denom;
            arr[1]++;
        }
        return map.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(limit)
                .map(e -> new PlayerStat(e.getKey(), 0L, e.getValue()[0],
                        0, (int) e.getValue()[1]))
                .collect(Collectors.toList());
    }

    /** Top N player theo tổng hợp (bank + thẻ) đã APPROVED. */
    public List<PlayerStat> getTopCombined(int limit) {
        // arr = [bankTotal, cardTotal, bankCount, cardCount]
        Map<String, long[]> map = new TreeMap<>();
        for (LocalOrderManager.BankOrder o : mod.getLocalOrderManager().getAllBankOrders()) {
            if (!LocalOrderManager.BANK_APPROVED.equals(o.status)) continue;
            long[] arr = map.computeIfAbsent(o.playerName, k -> new long[4]);
            arr[0] += o.amount;
            arr[2]++;
        }
        for (LocalOrderManager.CardOrder o : mod.getLocalOrderManager().getAllCardOrders()) {
            if (!LocalOrderManager.CARD_APPROVED.equals(o.status)) continue;
            long[] arr = map.computeIfAbsent(o.playerName, k -> new long[4]);
            arr[1] += o.denom;
            arr[3]++;
        }
        return map.entrySet().stream()
                .sorted((a, b) -> Long.compare(
                        b.getValue()[0] + b.getValue()[1],
                        a.getValue()[0] + a.getValue()[1]))
                .limit(limit)
                .map(e -> new PlayerStat(e.getKey(),
                        e.getValue()[0], e.getValue()[1],
                        (int) e.getValue()[2], (int) e.getValue()[3]))
                .collect(Collectors.toList());
    }

    // ─── Server stats ─────────────────────────────────────────────────────────

    /** Thống kê tổng toàn server (chỉ đơn APPROVED). */
    public ServerStat getServerTotal() {
        long bTotal = 0L; int bCount = 0;
        for (LocalOrderManager.BankOrder o : mod.getLocalOrderManager().getAllBankOrders()) {
            if (LocalOrderManager.BANK_APPROVED.equals(o.status)) {
                bTotal += o.amount; bCount++;
            }
        }
        long cTotal = 0L; int cCount = 0;
        for (LocalOrderManager.CardOrder o : mod.getLocalOrderManager().getAllCardOrders()) {
            if (LocalOrderManager.CARD_APPROVED.equals(o.status)) {
                cTotal += o.denom; cCount++;
            }
        }
        return new ServerStat(bTotal, cTotal, bCount, cCount);
    }

    /** Thống kê tổng hôm nay (chỉ đơn APPROVED). */
    public ServerStat getTodayStat() {
        long startOfDay = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli();
        long bTotal = 0L; int bCount = 0;
        for (LocalOrderManager.BankOrder o : mod.getLocalOrderManager().getAllBankOrders()) {
            if (LocalOrderManager.BANK_APPROVED.equals(o.status) && o.createdAt >= startOfDay) {
                bTotal += o.amount; bCount++;
            }
        }
        long cTotal = 0L; int cCount = 0;
        for (LocalOrderManager.CardOrder o : mod.getLocalOrderManager().getAllCardOrders()) {
            if (LocalOrderManager.CARD_APPROVED.equals(o.status) && o.createdAt >= startOfDay) {
                cTotal += o.denom; cCount++;
            }
        }
        return new ServerStat(bTotal, cTotal, bCount, cCount);
    }

    // ─── Player-specific stats ────────────────────────────────────────────────

    /** Thống kê của 1 player cụ thể (chỉ đơn APPROVED). */
    public PlayerStat getPlayerStat(String playerName) {
        long bTotal = 0L; int bCount = 0;
        for (LocalOrderManager.BankOrder o : mod.getLocalOrderManager().getAllBankOrders()) {
            if (LocalOrderManager.BANK_APPROVED.equals(o.status)
                    && playerName.equalsIgnoreCase(o.playerName)) {
                bTotal += o.amount; bCount++;
            }
        }
        long cTotal = 0L; int cCount = 0;
        for (LocalOrderManager.CardOrder o : mod.getLocalOrderManager().getAllCardOrders()) {
            if (LocalOrderManager.CARD_APPROVED.equals(o.status)
                    && playerName.equalsIgnoreCase(o.playerName)) {
                cTotal += o.denom; cCount++;
            }
        }
        return new PlayerStat(playerName, bTotal, cTotal, bCount, cCount);
    }

    // ─── Ranking positions ────────────────────────────────────────────────────

    /** Hạng nạp bank của player (1-based, -1 nếu chưa nạp). */
    public int getPlayerBankRank(String name) {
        return findRank(name, getTopByBank(Integer.MAX_VALUE));
    }

    /** Hạng nạp thẻ của player (1-based, -1 nếu chưa nạp). */
    public int getPlayerCardRank(String name) {
        return findRank(name, getTopByCard(Integer.MAX_VALUE));
    }

    /** Hạng tổng hợp của player (1-based, -1 nếu chưa nạp). */
    public int getPlayerCombinedRank(String name) {
        return findRank(name, getTopCombined(Integer.MAX_VALUE));
    }

    private static int findRank(String name, List<PlayerStat> list) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name().equalsIgnoreCase(name)) return i + 1;
        }
        return -1;
    }

    // ─── Recent orders (combined bank + card) ─────────────────────────────────

    /**
     * Lấy N đơn gần nhất (bank + thẻ, tất cả trạng thái, sort mới nhất trước).
     * Trả về Map với các key: type, playerName, amount, status, id, createdAt.
     */
    public List<Map<String, Object>> getRecentOrders(int limit) {
        List<Map<String, Object>> all = new ArrayList<>();

        for (LocalOrderManager.BankOrder o : mod.getLocalOrderManager().getAllBankOrders()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type",       "bank");
            m.put("playerName", o.playerName);
            m.put("amount",     (long) o.amount);
            m.put("status",     o.status);
            m.put("id",         o.invoiceId);
            m.put("createdAt",  o.createdAt);
            all.add(m);
        }

        for (LocalOrderManager.CardOrder o : mod.getLocalOrderManager().getAllCardOrders()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type",       "card");
            m.put("playerName", o.playerName);
            m.put("amount",     (long) o.denom);
            m.put("status",     o.status);
            m.put("telco",      o.telco);
            m.put("id",         o.requestId);
            m.put("createdAt",  o.createdAt);
            all.add(m);
        }

        all.sort((a, b) -> Long.compare((long) b.get("createdAt"), (long) a.get("createdAt")));
        return all.stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * Recent orders chỉ của 1 player cụ thể.
     */
    public List<Map<String, Object>> getRecentOrdersForPlayer(String playerName, int limit) {
        return getRecentOrders(Integer.MAX_VALUE).stream()
                .filter(m -> playerName.equalsIgnoreCase((String) m.get("playerName")))
                .limit(limit)
                .collect(Collectors.toList());
    }

    // ─── Placeholder text resolver (dùng cho PlaceholderAPI-style) ───────────

    /**
     * Resolve placeholder string dạng %paybot_xxx%.
     * @param playerName tên player hiện tại (dùng cho %paybot_player_xxx%)
     */
    public String resolve(String playerName, String key) {
        return switch (key.toLowerCase()) {
            case "server_bank_total"  -> PayBotMod.formatVnd((int) getServerTotal().bankTotal()) + " VND";
            case "server_card_total"  -> PayBotMod.formatVnd((int) getServerTotal().cardTotal()) + " VND";
            case "server_total"       -> PayBotMod.formatVnd((int) getServerTotal().total())     + " VND";
            case "server_bank_count"  -> String.valueOf(getServerTotal().bankCount());
            case "server_card_count"  -> String.valueOf(getServerTotal().cardCount());
            case "player_bank"        -> { PlayerStat ps = getPlayerStat(playerName); yield PayBotMod.formatVnd((int) ps.bankTotal()) + " VND"; }
            case "player_card"        -> { PlayerStat ps = getPlayerStat(playerName); yield PayBotMod.formatVnd((int) ps.cardTotal()) + " VND"; }
            case "player_total"       -> { PlayerStat ps = getPlayerStat(playerName); yield PayBotMod.formatVnd((int) ps.total())     + " VND"; }
            case "player_bank_rank"   -> { int r = getPlayerBankRank(playerName);    yield r < 0 ? "Chưa xếp hạng" : "#" + r; }
            case "player_card_rank"   -> { int r = getPlayerCardRank(playerName);    yield r < 0 ? "Chưa xếp hạng" : "#" + r; }
            case "player_total_rank"  -> { int r = getPlayerCombinedRank(playerName);yield r < 0 ? "Chưa xếp hạng" : "#" + r; }
            case "today_bank"         -> PayBotMod.formatVnd((int) getTodayStat().bankTotal()) + " VND";
            case "today_card"         -> PayBotMod.formatVnd((int) getTodayStat().cardTotal()) + " VND";
            case "today_total"        -> PayBotMod.formatVnd((int) getTodayStat().total())     + " VND";
            default                   -> resolveTopKey(key);
        };
    }

    private String resolveTopKey(String key) {
        // top_bank_N, top_bank_amount_N, top_card_N, top_card_amount_N, top_total_N, top_total_amount_N
        if (key.startsWith("top_bank_amount_")) {
            int n = parseN(key.substring("top_bank_amount_".length()));
            List<PlayerStat> top = getTopByBank(n);
            if (top.size() < n) return "N/A";
            return PayBotMod.formatVnd((int) top.get(n - 1).bankTotal()) + " VND";
        }
        if (key.startsWith("top_bank_")) {
            int n = parseN(key.substring("top_bank_".length()));
            List<PlayerStat> top = getTopByBank(n);
            return top.size() < n ? "N/A" : top.get(n - 1).name();
        }
        if (key.startsWith("top_card_amount_")) {
            int n = parseN(key.substring("top_card_amount_".length()));
            List<PlayerStat> top = getTopByCard(n);
            if (top.size() < n) return "N/A";
            return PayBotMod.formatVnd((int) top.get(n - 1).cardTotal()) + " VND";
        }
        if (key.startsWith("top_card_")) {
            int n = parseN(key.substring("top_card_".length()));
            List<PlayerStat> top = getTopByCard(n);
            return top.size() < n ? "N/A" : top.get(n - 1).name();
        }
        if (key.startsWith("top_total_amount_")) {
            int n = parseN(key.substring("top_total_amount_".length()));
            List<PlayerStat> top = getTopCombined(n);
            if (top.size() < n) return "N/A";
            return PayBotMod.formatVnd((int) top.get(n - 1).total()) + " VND";
        }
        if (key.startsWith("top_total_")) {
            int n = parseN(key.substring("top_total_".length()));
            List<PlayerStat> top = getTopCombined(n);
            return top.size() < n ? "N/A" : top.get(n - 1).name();
        }
        return "?";
    }

    private static int parseN(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 1; }
    }
}
