package com.naptien.gui;

import com.naptien.NapTienPlugin;
import com.naptien.log.LogManager;
import com.naptien.managers.LocalOrderManager;
import com.naptien.utils.VersionCompat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.*;

import static com.naptien.gui.GuiUtil.*;

/**
 * GUI for /topuplist all (hiện tất cả), /cardcheck (chỉ thẻ), /bankcheck (chỉ bank).
 * <p>
 * v5.0.0 (fix Thứ 3): TRƯỚC ĐÂY chỉ hiện đơn card status=CARD_SUCCESS và bank status=BANK_PAID
 * (tức chỉ đơn ĐANG chờ duyệt) — mọi đơn fail (sai mệnh giá, thẻ đã dùng, bảo trì, hết hạn,...)
 * hoặc đã duyệt rồi đều biến mất khỏi danh sách, admin không có cách nào xem lại lịch sử đầy đủ.
 * Giờ hiện TẤT CẢ đơn có trong data (getAllCardOrders/getAllBankOrders), phân màu theo trạng thái:
 *   - Đỏ   = đang chờ duyệt (CARD_SUCCESS / BANK_PAID) — click được để duyệt.
 *   - Xanh = đã duyệt (APPROVED) — chỉ xem, không click được.
 *   - Vàng = đang xử lý / đang chờ thanh toán (CARD_PROCESSING / BANK_PENDING).
 *   - Xám  = fail/hết hạn (sai mệnh giá, thẻ đã dùng, bảo trì, thẻ sai, hết hạn) — chỉ xem.
 * Đơn không ở trạng thái chờ duyệt sẽ KHÔNG cho click duyệt (xem guard trong GuiListener),
 * tránh admin bấm nhầm vào đơn fail mà vẫn cấp thưởng.
 * <p>
 * v5.0.0 (fix): TRƯỚC ĐÂY /cardcheck và /bankcheck (không args, mở GUI) đều gọi
 * {@code open(player, plugin, page)} — tức mở CHUNG danh sách cả thẻ + bank, sai ý nghĩa
 * lệnh (mỗi lệnh chỉ nên hiện đúng loại của nó; chỉ /topuplist all mới hiện chung).
 * Giờ thêm tham số {@code filter} ("card"/"bank"/null=tất cả) để 3 lệnh trên dùng đúng GUI
 * theo đúng loại của mình.
 */
public final class TopupListGui {

    private TopupListGui() {}

    private static final int PAGE_SIZE = 45; // slots 0-44
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd/MM HH:mm");

    // ─── Combined order wrapper ────────────────────────────────────────────────
    public record OrderEntry(String id, String playerName, String type,
                              int amount, String status, long createdAt,
                              // card fields
                              String telco, String cardCode, String cardSerial,
                              // bank fields
                              String invoiceId) {}

    /** Mở GUI hiện TẤT CẢ đơn (card + bank) — dùng cho /topuplist all. */
    public static void open(Player player, NapTienPlugin plugin, int page) {
        open(player, plugin, page, null);
    }

    /**
     * @param filter "card" → chỉ đơn thẻ, "bank" → chỉ đơn bank, null/"all" → tất cả.
     */
    public static void open(Player player, NapTienPlugin plugin, int page, String filter) {
        List<OrderEntry> orders = collectOrders(plugin, filter);
        int totalPages = Math.max(1, (int) Math.ceil(orders.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        String suffix = "card".equals(filter) ? " §7- §eThẻ"
                : "bank".equals(filter) ? " §7- §6Bank"
                : "";
        Inventory inv = Bukkit.createInventory(null, 54,
                "§6§lDanh sách đơn" + suffix + " §7(Trang " + (page + 1) + "/" + totalPages + ")");
        fillGlass(inv);

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && (start + i) < orders.size(); i++) {
            inv.setItem(i, buildOrderItem(orders.get(start + i)));
        }

        // Navigation
        if (page > 0) {
            inv.setItem(45, makeItem(org.bukkit.Material.ARROW, "§eTrang trước", null));
        }
        inv.setItem(49, makeItem(org.bukkit.Material.BARRIER, "§c§lĐóng", List.of("§7Đóng UI")));
        if (page < totalPages - 1) {
            inv.setItem(53, makeItem(org.bukkit.Material.ARROW, "§eTrang sau", null));
        }
        // Store page + filter in session (để phân trang giữ đúng filter)
        GuiSession s = GuiSession.get(player.getUniqueId());
        s.stage = GuiSession.Stage.TOPUP_LIST;
        s.editDenom = page; // reuse field for page number
        s.topupListFilter = ("card".equals(filter) || "bank".equals(filter)) ? filter : null;
        player.openInventory(inv);
    }

    /** v5.0.0 (fix Thứ 3): lấy đơn trong data theo filter, mới nhất hiện trước. */
    private static List<OrderEntry> collectOrders(NapTienPlugin plugin, String filter) {
        LocalOrderManager lom = plugin.getLocalOrderManager();
        List<OrderEntry> list = new ArrayList<>();

        if (!"bank".equals(filter)) {
            for (LocalOrderManager.CardOrder c : lom.getAllCardOrders()) {
                list.add(new OrderEntry(
                        c.requestId, c.playerName, "card",
                        c.denom, c.status, c.createdAt,
                        c.telco, c.cardCode, c.cardSerial, ""
                ));
            }
        }
        if (!"card".equals(filter)) {
            for (LocalOrderManager.BankOrder b : lom.getAllBankOrders()) {
                list.add(new OrderEntry(
                        b.invoiceId, b.playerName, "bank",
                        b.amount, b.status, b.createdAt,
                        "", "", "", b.invoiceId
                ));
            }
        }
        // Mới nhất hiện trước (dễ thấy đơn vừa tạo, không phải lội xuống cuối danh sách dài)
        list.sort(Comparator.comparingLong(OrderEntry::createdAt).reversed());
        return list;
    }

    /** true nếu đơn đang ở trạng thái có thể duyệt (CARD_SUCCESS / BANK_PAID). */
    public static boolean isApprovable(OrderEntry e) {
        if ("card".equals(e.type())) return LocalOrderManager.CARD_SUCCESS.equals(e.status());
        return LocalOrderManager.BANK_PAID.equals(e.status());
    }

    /** true nếu đơn đã được duyệt rồi (APPROVED). */
    private static boolean isApproved(OrderEntry e) {
        return LocalOrderManager.CARD_APPROVED.equals(e.status())
                || LocalOrderManager.BANK_APPROVED.equals(e.status());
    }

    /** Wool data index theo trạng thái: 13=xanh(đã duyệt) 14=đỏ(chờ duyệt) 4=vàng(đang xử lý) 8=xám(fail/hết hạn). */
    private static int woolColorFor(OrderEntry e) {
        if (isApproved(e)) return 13;       // xanh
        if (isApprovable(e)) return 14;     // đỏ — chờ duyệt
        if (LocalOrderManager.CARD_PROCESSING.equals(e.status())
                || LocalOrderManager.BANK_PENDING.equals(e.status())) return 4; // vàng
        return 8; // xám — fail / hết hạn / trạng thái khác
    }

    private static String statusLabel(OrderEntry e) {
        return "card".equals(e.type())
                ? LogManager.cardStatusLabel(e.status())
                : LogManager.bankStatusLabel(e.status());
    }

    private static ItemStack buildOrderItem(OrderEntry e) {
        String timeStr = DATE_FMT.format(new Date(e.createdAt));
        boolean approvable = isApprovable(e);

        List<String> lore = new ArrayList<>();
        lore.add("§7Player: §f" + e.playerName());
        lore.add("§7Thời gian: §f" + timeStr);
        if ("card".equals(e.type())) {
            lore.add("§7Loại: §eNạp thẻ");
            lore.add("§7Nhà mạng: §f" + e.telco());
            lore.add("§7Mệnh giá: §f" + formatVnd(e.amount()) + " VND");
            lore.add("§7Serial: §f" + e.cardSerial());
            lore.add("§7Mã thẻ: §f" + e.cardCode());
            lore.add("§7ID: §8" + e.id().substring(0, Math.min(8, e.id().length())) + "...");
        } else {
            lore.add("§7Loại: §6Nạp bank");
            lore.add("§7Số tiền: §f" + formatVnd(e.amount()) + " VND");
            lore.add("§7Mã đơn: §f" + e.invoiceId());
        }
        lore.add("§7Trạng thái: §f" + statusLabel(e));
        lore.add("");
        if (approvable) {
            lore.add("§aClick để duyệt đơn");
        } else if (isApproved(e)) {
            lore.add("§7Đã duyệt — chỉ xem");
        } else {
            lore.add("§8Không thể duyệt — chỉ xem");
        }

        String icon = approvable ? "§c§l⏳ " : isApproved(e) ? "§a§l✓ " : "§8§l✗ ";
        String displayName = icon + e.playerName() + " §f" + formatVnd(e.amount()) + " VND";
        return VersionCompat.makeWoolItem(woolColorFor(e), displayName, lore);
    }

    /** Get order by inventory slot from the current page (theo filter — xem GuiSession.topupListFilter). */
    public static OrderEntry getOrderBySlot(int slot, NapTienPlugin plugin, int page, String filter) {
        if (slot >= PAGE_SIZE) return null;
        List<OrderEntry> orders = collectOrders(plugin, filter);
        int idx = page * PAGE_SIZE + slot;
        return idx < orders.size() ? orders.get(idx) : null;
    }
}
