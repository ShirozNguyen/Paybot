package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import com.paybot.gui.setup.TransactionQueryService.OrderType;
import com.paybot.gui.setup.TransactionQueryService.UnifiedOrder;
import com.paybot.managers.LocalOrderManager;
import com.paybot.managers.RewardDispatcher;
import com.paybot.milestone.MilestoneManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * TransactionApproveConfirmGui — v7.0.2 (Part 186)
 * TUÂN THỦ RULE 17: Container Menu 27 ô Xác nhận Duyệt đơn nạp tiền thủ công cho Admin.
 * <p>
 * Bố cục:
 * - Slot 11: Khối Ngọc Lục Bảo (Xác nhận duyệt & phát thưởng)
 * - Slot 13: Quyển Sách (Thông tin chi tiết đơn nạp)
 * - Slot 15: Khối Đá Đỏ (Hủy bỏ và quay lại danh sách đơn)
 */
public class TransactionApproveConfirmGui implements InventoryHolder {

    public static final String TITLE = "§e§lXác Nhận Duyệt Đơn Nạp";

    private final PayBotPlugin plugin;
    private final Inventory inventory;
    private final UnifiedOrder order;
    private final String returnFilter;
    private final int returnPage;

    public TransactionApproveConfirmGui(PayBotPlugin plugin, UnifiedOrder order, String returnFilter, int returnPage) {
        this.plugin = plugin;
        this.order = order;
        this.returnFilter = returnFilter != null ? returnFilter : "ALL";
        this.returnPage = returnPage;
        this.inventory = Bukkit.createInventory(this, 27, TITLE);
        buildLayout();
    }

    private void buildLayout() {
        ItemStack border = VanillaItemRegistry.createBorderPane();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, border);
        }

        // Slot 11: Nút Duyệt Đơn (Emerald)
        Material emerald = VanillaItemRegistry.resolveMaterial("EMERALD_BLOCK", "EMERALD");
        inventory.setItem(11, VanillaItemRegistry.createItem(emerald,
                "§a§l✔ XÁC NHẬN DUYỆT ĐƠN NÀY",
                "§7Thực hiện trao thưởng ngay cho người chơi:",
                "§f• Player: §a" + order.getPlayerName(),
                "§f• Số tiền: §e" + String.format("%,d", order.getAmount()) + " VNĐ",
                "§f• Hình thức: §b" + order.getType(),
                "",
                "§a▶ [Click] để DUYỆT NGAY & GIAO THƯỞNG"));

        // Slot 13: Chi tiết đơn hàng (Book / Paper)
        Material book = VanillaItemRegistry.resolveMaterial("BOOK", "PAPER");
        inventory.setItem(13, VanillaItemRegistry.createItem(book,
                "§e§lThông Tin Đơn Nạp: §f" + order.getOrderId(),
                "§7Người nạp: §a" + order.getPlayerName(),
                "§7Mệnh giá: §6" + String.format("%,d", order.getAmount()) + " VNĐ",
                "§7Hình thức: §b" + (order.getType() == OrderType.BANK ? "Ngân Hàng (Bank)" : "Thẻ Cào (Card)"),
                "§7Chi tiết: §7" + order.getDetail(),
                "§7Thời gian: §f" + order.getFormattedDate(),
                "§7Trạng thái hiện tại: §e" + order.getRawStatus()));

        // Slot 15: Nút Hủy Bỏ (Redstone)
        Material redstone = VanillaItemRegistry.resolveMaterial("REDSTONE_BLOCK", "REDSTONE");
        inventory.setItem(15, VanillaItemRegistry.createItem(redstone,
                "§c§l✖ HỦY BỎ / QUAY LẠI",
                "§7Không duyệt đơn này và quay lại danh sách.",
                "",
                "§c▶ [Click] để đóng menu xác nhận"));
    }

    public void executeApproval(Player admin) {
        if (order == null || plugin == null) return;

        try {
            String typeStr = (order.getType() == OrderType.BANK) ? "bank" : "card";
            List<String> rawCmds = RewardDispatcher.resolveRewardCmds(plugin, order.getAmount(), typeStr);
            String rewardAmt = RewardDispatcher.computeRewardAmt(plugin, order.getAmount(), typeStr);

            // 1. Thực thi lệnh thưởng hoặc đưa vào hàng chờ offline
            boolean deliveredNow = RewardDispatcher.dispatchOrQueue(
                    plugin,
                    UUID.randomUUID().toString(),
                    order.getPlayerName(),
                    rawCmds,
                    rewardAmt,
                    String.valueOf(order.getAmount()),
                    typeStr,
                    order.getOrderId(),
                    ""
            );

            // 2. Cập nhật trạng thái đơn sang APPROVED
            if (order.getType() == OrderType.BANK) {
                plugin.getLocalOrderManager().updateBankStatus(order.getOrderId(), LocalOrderManager.BANK_APPROVED);
            } else {
                plugin.getLocalOrderManager().updateCardStatus(order.getOrderId(), LocalOrderManager.CARD_APPROVED, "Admin duyệt thủ công");
            }

            // 3. Kích hoạt tính mốc nạp tích lũy
            if (MilestoneManager.getInstance() != null) {
                MilestoneManager.getInstance().onPaymentApproved(order.getPlayerName(), order.getAmount());
            }

            admin.sendMessage("§a[PayBot Setup] §f✓ Đã DUYỆT THÀNH CÔNG đơn §e" + order.getOrderId()
                    + " §fcho người chơi §a" + order.getPlayerName() + " §7(Số tiền: §e"
                    + String.format("%,d", order.getAmount()) + " VNĐ§7)!");

            if (deliveredNow) {
                admin.sendMessage("§a[PayBot Setup] §f✓ Người chơi đang online — phần thưởng đã được trao ngay lập tức!");
            } else {
                admin.sendMessage("§e[PayBot Setup] §fℹ Người chơi đang offline — phần thưởng đã được lưu vào hàng chờ tự nhận khi vào lại!");
            }

            plugin.getLogger().info("[PayBot] Admin " + admin.getName() + " đã duyệt thủ công đơn " + order.getOrderId()
                    + " (" + order.getPlayerName() + ", " + order.getAmount() + " VND).");

        } catch (Throwable t) {
            admin.sendMessage("§c[PayBot Setup] ✗ Lỗi khi duyệt đơn: " + t.getMessage());
            plugin.getLogger().warning("[PayBot] Lỗi duyệt đơn " + order.getOrderId() + ": " + t.getMessage());
        }

        // Mở lại Transaction Manager
        new SetupTransactionManagerGui(plugin, returnFilter, returnPage).open(admin);
    }

    public UnifiedOrder getOrder() {
        return order;
    }

    public String getReturnFilter() {
        return returnFilter;
    }

    public int getReturnPage() {
        return returnPage;
    }

    public void open(Player player) {
        if (player != null) player.openInventory(this.inventory);
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }
}
