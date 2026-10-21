package com.paybot.gui.security;

import com.paybot.gui.setup.SetupMainHubGui;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * GuiItemTheftProtectionListener — Chuyên trách chống triệt để mọi hành vi "ăn cắp" và dupe item từ ALL GUIs của PayBot.
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn thành 1 class Listener đơn nhiệm độc lập.
 * Chặn đứng 12 vector tấn công (Hotbar swap 1-9, phím 'F', double-click gom đồ, shift-click quick move, throw Q, drag paint...).
 */
public class GuiItemTheftProtectionListener implements Listener {

    public static boolean isPayBotInventory(Inventory inv) {
        if (inv == null) return false;
        InventoryHolder holder = inv.getHolder();
        if (holder != null) {
            String name = holder.getClass().getName();
            if (name.startsWith("com.paybot.gui.")) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory topInv = event.getView().getTopInventory();
        if (!isPayBotInventory(topInv)) return;

        ClickType clickType = event.getClick();
        InventoryAction action = event.getAction();
        int rawSlot = event.getRawSlot();
        int topSize = topInv.getSize();

        // 1. Chặn SWAP (Phím số 1-9 và Phím F đổi tay phụ)
        if (clickType == ClickType.NUMBER_KEY || clickType.name().equals("SWAP_OFFHAND")) {
            event.setCancelled(true);
            player.updateInventory();
            return;
        }

        // 2. Chặn DOUBLE_CLICK gom item (Collect to cursor)
        if (clickType == ClickType.DOUBLE_CLICK || action == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            player.updateInventory();
            return;
        }

        // 3. Chặn THROW / DROP (Phím Q và Ctrl+Q ném item)
        if (clickType == ClickType.DROP || clickType == ClickType.CONTROL_DROP ||
            action == InventoryAction.DROP_ALL_SLOT || action == InventoryAction.DROP_ONE_SLOT) {
            if (rawSlot >= 0 && rawSlot < topSize) {
                event.setCancelled(true);
                player.updateInventory();
                return;
            }
        }

        // 4. Chặn CLONE (Chuột giữa trong Creative)
        if (clickType == ClickType.MIDDLE || action == InventoryAction.CLONE_STACK) {
            event.setCancelled(true);
            player.updateInventory();
            return;
        }

        // 5. Chặn thao tác trực tiếp trên TOP GUI của PayBot
        if (rawSlot >= 0 && rawSlot < topSize) {
            // Không cho nhấc item, đổi item, chia item trên GUI PayBot
            if (action == InventoryAction.PICKUP_ALL || action == InventoryAction.PICKUP_HALF ||
                action == InventoryAction.PICKUP_ONE || action == InventoryAction.PICKUP_SOME ||
                action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_ONE ||
                action == InventoryAction.PLACE_SOME || action == InventoryAction.SWAP_WITH_CURSOR) {
                event.setCancelled(true);
                player.updateInventory();
                return;
            }
        }

        // 6. Chặn QUICK_MOVE (Shift-Click)
        if (clickType.isShiftClick() || action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            // Chặn chuyển từ Top GUI vào balo và từ balo vào Top GUI
            event.setCancelled(true);
            player.updateInventory();
            return;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory topInv = event.getView().getTopInventory();
        if (!isPayBotInventory(topInv)) return;

        int topSize = topInv.getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                // Chặn kéo chuột đè vào các slot thuộc Top GUI PayBot
                event.setCancelled(true);
                player.updateInventory();
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Inventory topInv = event.getView().getTopInventory();
        if (!isPayBotInventory(topInv)) return;

        // Tiêu hủy ngay lập tức item GUI dính trên con trỏ chuột khi đóng cửa sổ
        ItemStack cursor = event.getView().getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            event.getView().setCursor(new ItemStack(Material.AIR));
            player.updateInventory();
        }
    }
}
