package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import com.paybot.gui.setup.TransactionQueryService.NormalizedStatus;
import com.paybot.gui.setup.TransactionQueryService.OrderType;
import com.paybot.gui.setup.TransactionQueryService.TransactionQueryResult;
import com.paybot.gui.setup.TransactionQueryService.UnifiedOrder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * SetupTransactionManagerGui — v7.0.2 (Part 186)
 * TUÂN THỦ RULE 17: Container Menu 54 ô Quản lý & Lọc Đơn Nạp từ CSDL/Runtime Thật.
 * <p>
 * ĐẶC TẢ:
 * 1. 3 Bộ lọc trạng thái: ⏳ Chờ duyệt (PENDING), ✅ Thành công (SUCCESS), ❌ Thất bại (FAILED).
 * 2. Phân trang mượt mà (21 items / trang).
 * 3. Kết nối dữ liệu sống từ DatabaseManager & LocalOrderManager.
 * 4. Cho phép Shift+Click vào đơn PENDING để mở TransactionApproveConfirmGui duyệt thủ công.
 */
public class SetupTransactionManagerGui implements InventoryHolder {

    public static final String TITLE = "§e§lQuản Lý Đơn Nạp §8(v7.0.2)";
    public static final int[] ORDER_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final PayBotPlugin plugin;
    private final Inventory inventory;
    private final String statusFilter; // ALL, PENDING, SUCCESS, FAILED
    private final int page;
    private final Map<Integer, UnifiedOrder> slotToOrderMap = new HashMap<>();

    public SetupTransactionManagerGui(PayBotPlugin plugin, String statusFilter, int page) {
        this.plugin = plugin;
        this.statusFilter = statusFilter != null ? statusFilter : "ALL";
        this.page = Math.max(0, page);
        this.inventory = Bukkit.createInventory(this, 54, TITLE + " [" + this.statusFilter + "]");
        buildLayout();
    }

    private void buildLayout() {
        ItemStack border = VanillaItemRegistry.createBorderPane();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, border);
        }

        // Slot 2: Bộ lọc Chờ Duyệt
        Material clock = VanillaItemRegistry.resolveMaterial("CLOCK", "WATCH");
        inventory.setItem(2, VanillaItemRegistry.createItem(clock,
                ("PENDING".equals(statusFilter) ? "§a§l▶ " : "§7") + "§e§l⏳ Đơn Chờ Duyệt (PENDING)",
                "§7Lọc các đơn đang chờ đối soát hoặc xác nhận thẻ.",
                "",
                "PENDING".equals(statusFilter) ? "§a✔ Đang lọc theo trạng thái này" : "§e▶ [Click] để lọc đơn Chờ duyệt"));

        // Slot 4: Bộ lọc Thành Công
        Material emerald = VanillaItemRegistry.resolveMaterial("EMERALD", "EMERALD_BLOCK");
        inventory.setItem(4, VanillaItemRegistry.createItem(emerald,
                ("SUCCESS".equals(statusFilter) ? "§a§l▶ " : "§7") + "§a§l✅ Đơn Thành Công (SUCCESS)",
                "§7Lọc các đơn nạp tiền đã hoàn tất và giao thưởng.",
                "",
                "SUCCESS".equals(statusFilter) ? "§a✔ Đang lọc theo trạng thái này" : "§e▶ [Click] để lọc đơn Thành công"));

        // Slot 6: Bộ lọc Thất Bại
        Material redstone = VanillaItemRegistry.resolveMaterial("REDSTONE", "REDSTONE_BLOCK");
        inventory.setItem(6, VanillaItemRegistry.createItem(redstone,
                ("FAILED".equals(statusFilter) ? "§a§l▶ " : "§7") + "§c§l❌ Đơn Thất Bại (FAILED)",
                "§7Lọc các đơn bị hủy, thẻ sai hoặc hết hạn giao dịch.",
                "",
                "FAILED".equals(statusFilter) ? "§a✔ Đang lọc theo trạng thái này" : "§e▶ [Click] để lọc đơn Thất bại"));

        // Truy vấn dữ liệu đơn thật
        TransactionQueryResult result = TransactionQueryService.queryOrders(plugin, statusFilter, page, ORDER_SLOTS.length);
        List<UnifiedOrder> orders = result.getOrders();

        if (orders.isEmpty()) {
            Material barrier = VanillaItemRegistry.resolveMaterial("BARRIER", "GLASS");
            inventory.setItem(22, VanillaItemRegistry.createItem(barrier,
                    "§c§lKhông Có Đơn Nạp Nào",
                    "§7Không tìm thấy giao dịch nào phù hợp với bộ lọc: §e" + statusFilter,
                    "",
                    "§7Hãy thử chọn bộ lọc trạng thái khác ở phía trên."));
        } else {
            for (int i = 0; i < orders.size() && i < ORDER_SLOTS.length; i++) {
                int slot = ORDER_SLOTS[i];
                UnifiedOrder order = orders.get(i);
                slotToOrderMap.put(slot, order);

                Material itemMat;
                String statusTag;
                if (order.getNormalizedStatus() == NormalizedStatus.SUCCESS) {
                    itemMat = VanillaItemRegistry.resolveMaterial("EMERALD", "LIME_WOOL", "PAPER");
                    statusTag = "§a✅ THÀNH CÔNG";
                } else if (order.getNormalizedStatus() == NormalizedStatus.FAILED) {
                    itemMat = VanillaItemRegistry.resolveMaterial("REDSTONE", "RED_WOOL", "PAPER");
                    statusTag = "§c❌ THẤT BẠI";
                } else {
                    itemMat = VanillaItemRegistry.resolveMaterial("GOLD_INGOT", "YELLOW_WOOL", "PAPER");
                    statusTag = "§e⏳ CHỜ DUYỆT";
                }

                List<String> lore = new ArrayList<>();
                lore.add("§7Người nạp: §a" + order.getPlayerName());
                lore.add("§7Số tiền: §6" + String.format("%,d", order.getAmount()) + " VNĐ");
                lore.add("§7Hình thức: §b" + (order.getType() == OrderType.BANK ? "Ngân Hàng" : "Thẻ Cào"));
                lore.add("§7Chi tiết: §7" + order.getDetail());
                lore.add("§7Thời gian: §f" + order.getFormattedDate());
                lore.add("§7Trạng thái: " + statusTag + " §8(" + order.getRawStatus() + ")");
                lore.add("");
                if (order.getNormalizedStatus() == NormalizedStatus.PENDING) {
                    lore.add("§a▶ [Shift + Click] để DUYỆT ĐƠN thủ công");
                } else {
                    lore.add("§7Đơn này đã ở trạng thái cuối.");
                }

                inventory.setItem(slot, VanillaItemRegistry.createItem(itemMat,
                        "§e§lĐơn: §f" + order.getOrderId(),
                        lore.toArray(new String[0])));
            }
        }

        // Slot 45: Quay lại Hub chính
        Material door = VanillaItemRegistry.resolveMaterial("OAK_DOOR", "WOODEN_DOOR", "ARROW");
        inventory.setItem(45, VanillaItemRegistry.createItem(door, "§c§l◀ Quay Lại Menu Chính",
                "§7Trở về Master Setup Hub."));

        // Slot 47: Trang trước
        if (result.getCurrentPage() > 0) {
            Material prev = VanillaItemRegistry.resolveMaterial("ARROW", "FEATHER");
            inventory.setItem(47, VanillaItemRegistry.createItem(prev,
                    "§e§l◀ Trang Trước (" + result.getCurrentPage() + ")",
                    "§7Quay về trang danh sách trước."));
        }

        // Slot 49: Hiển thị thông tin trang
        Material paper = VanillaItemRegistry.resolveMaterial("PAPER", "MAP", "BOOK");
        inventory.setItem(49, VanillaItemRegistry.createItem(paper,
                "§e§lThông Tin Danh Sách",
                "§7Tổng số đơn: §f" + result.getTotalOrders(),
                "§7Trang hiện tại: §a" + (result.getCurrentPage() + 1) + " / " + Math.max(1, result.getTotalPages()),
                "§7Bộ lọc: §b" + statusFilter));

        // Slot 51: Trang sau
        if (result.getCurrentPage() + 1 < result.getTotalPages()) {
            Material next = VanillaItemRegistry.resolveMaterial("ARROW", "FEATHER");
            inventory.setItem(51, VanillaItemRegistry.createItem(next,
                    "§e§lTrang Sau (" + (result.getCurrentPage() + 2) + ") ▶",
                    "§7Xem các đơn ở trang kế tiếp."));
        }
    }

    public UnifiedOrder getOrderBySlot(int slot) {
        return slotToOrderMap.get(slot);
    }

    public String getStatusFilter() {
        return statusFilter;
    }

    public int getPage() {
        return page;
    }

    public void open(Player player) {
        if (player != null) player.openInventory(this.inventory);
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }
}
