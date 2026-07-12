package com.naptien.gui;

import com.naptien.NapTienPlugin;
import com.naptien.managers.TopupStatsManager;
import com.naptien.utils.VersionCompat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.Map;

import static com.naptien.gui.GuiUtil.*;

/**
 * GUI cho /paybotleaderboard (v5.0.3: đổi tên từ /paybotplaceholder) — xem các chỉ số thống kê nạp (tương đương các
 * placeholder %paybot_...%) TRỰC TIẾP trong game, KHÔNG cần cài PlaceholderAPI.
 * Đọc trực tiếp từ {@link TopupStatsManager} — tích hợp sẵn trong PayBot.
 *
 * Changelog:
 *   v5.0.0 (Phase B) — Thêm mới.
 */
public final class PayBotPlaceholderGui {

    private PayBotPlaceholderGui() {}

    public static final String MAIN_TITLE        = "§6§lThống Kê Nạp";
    public static final String LEADERBOARD_TITLE = "§6§lBảng Xếp Hạng Nạp";

    private static final int SLOT_SERVER_TOTAL = 11;
    private static final int SLOT_PLAYER_TOTAL = 13;
    private static final int SLOT_LEADERBOARD  = 15;
    private static final int SLOT_CLOSE        = 49;
    private static final int SLOT_BACK         = 45;

    public static void openMain(Player player, NapTienPlugin plugin) {
        TopupStatsManager stats = plugin.getTopupStatsManager();

        Inventory inv = Bukkit.createInventory(null, 54, MAIN_TITLE);
        fillGlass(inv);

        inv.setItem(SLOT_SERVER_TOTAL, makeItem(Material.GOLD_INGOT,
                "§6§lTổng nạp toàn server",
                List.of(
                        "§7%paybot_total_topup%",
                        "§e" + formatVnd(stats.getServerTotal()) + " VND",
                        "§7Số người đã từng nạp: §f" + stats.getTotalPlayerCount()
                )));

        long mine = stats.getPlayerTotal(player.getName());
        inv.setItem(SLOT_PLAYER_TOTAL, makeItem(Material.EMERALD,
                "§a§lTổng nạp của bạn",
                List.of(
                        "§7%paybot_player_topup%",
                        "§a" + formatVnd(mine) + " VND"
                )));

        inv.setItem(SLOT_LEADERBOARD, makeItem(Material.NETHER_STAR,
                "§d§lBảng xếp hạng Top nạp",
                List.of(
                        "§7%paybot_top1_name% .. %paybot_top10_name%",
                        "§eClick để xem Top 10 người nạp nhiều nhất"
                )));

        inv.setItem(SLOT_CLOSE, makeItem(Material.BARRIER, "§c§lĐóng", List.of("§7Đóng giao diện")));
        player.openInventory(inv);
    }

    public static void openLeaderboard(Player player, NapTienPlugin plugin) {
        TopupStatsManager stats = plugin.getTopupStatsManager();
        List<Map.Entry<String, Long>> top = stats.getTopPlayers(10);

        Inventory inv = Bukkit.createInventory(null, 54, LEADERBOARD_TITLE);
        fillGlass(inv);

        // 2 hàng x 5, canh giữa từng hàng (centerStart) — hạng 1-5 hàng trên, 6-10 hàng dưới
        int[] rowStarts = { 19, 28 };
        for (int i = 0; i < 10; i++) {
            int row      = i / 5;
            int col      = i % 5;
            int rowStart = centerStart(rowStarts[row], 5);
            int slot     = rowStart + col;

            if (i < top.size()) {
                Map.Entry<String, Long> e = top.get(i);
                inv.setItem(slot, VersionCompat.makeWoolItem(woolColorForRank(i),
                        rankPrefix(i) + " §f" + e.getKey(),
                        List.of("§7Đã nạp: §e" + formatVnd(e.getValue()) + " VND")));
            } else {
                inv.setItem(slot, VersionCompat.makeWoolItem(7,
                        "§7#" + (i + 1) + " — chưa có",
                        List.of("§8Chưa có ai ở hạng này")));
            }
        }

        inv.setItem(SLOT_BACK, makeItem(Material.ARROW, "§eQuay lại", List.of("§7Về menu chính")));
        inv.setItem(SLOT_CLOSE, makeItem(Material.BARRIER, "§c§lĐóng", List.of("§7Đóng giao diện")));
        player.openInventory(inv);
    }

    private static String rankPrefix(int idx) {
        return switch (idx) {
            case 0  -> "§6§l#1";
            case 1  -> "§7§l#2";
            case 2  -> "§c§l#3";
            default -> "§f#" + (idx + 1);
        };
    }

    /** Wool color index (0-15): #1 vàng, #2 xám bạc, #3 cam (đồng), còn lại cyan. */
    private static int woolColorForRank(int idx) {
        return switch (idx) {
            case 0  -> 4;
            case 1  -> 8;
            case 2  -> 1;
            default -> 9;
        };
    }

    public static int getCloseSlot()       { return SLOT_CLOSE; }
    public static int getBackSlot()        { return SLOT_BACK; }
    public static int getLeaderboardSlot() { return SLOT_LEADERBOARD; }
}
