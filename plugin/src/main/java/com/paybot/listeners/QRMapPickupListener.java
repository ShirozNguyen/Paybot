// v5.5.5 Part 112: QR Map Pickup Listener — Convert expired QR map to empty map on pickup (Rule 17)
package com.paybot.listeners;

import com.paybot.PayBotPlugin;
import com.paybot.managers.QRMapSessionTracker;
import com.paybot.utils.VersionCompat;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * QRMapPickupListener — Xử lý sự kiện nhặt bản đồ QR từ mặt đất.
 * <p>
 * Khi người chơi nhặt một tấm bản đồ QR mà đơn hàng đã hết thời gian chờ hoặc đã hoàn thành,
 * tấm bản đồ đó sẽ ngay lập tức được biến đổi thành Bản Đồ Trống (EMPTY_MAP / MAP) nguyên bản,
 * dọn dẹp toàn bộ dữ liệu metadata cũ để người chơi không giữ bản đồ chết.
 */
public class QRMapPickupListener implements Listener {

    private final PayBotPlugin plugin;
    private final QRMapSessionTracker sessionTracker;
    private final NamespacedKey invoiceKey;

    public QRMapPickupListener(PayBotPlugin plugin, QRMapSessionTracker sessionTracker) {
        this.plugin = plugin;
        this.sessionTracker = sessionTracker;
        this.invoiceKey = new NamespacedKey(plugin, "invoice_id");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        ItemStack item = event.getItem().getItemStack();
        if (item == null || item.getType() != VersionCompat.getMapMaterial()) return;

        String invoiceId = VersionCompat.getInvoiceId(item, invoiceKey);
        if (invoiceId == null || invoiceId.isEmpty()) return;

        // Nếu đơn hàng QR này đã hết hạn hoặc không còn phiên hợp lệ
        if (sessionTracker.isInvoiceExpiredOrCompleted(invoiceId)) {
            // Chuyển đổi item thành bản đồ trống hoàn toàn sạch sẽ
            ItemStack emptyMap = new ItemStack(VersionCompat.getEmptyMapMaterial(), item.getAmount());
            event.getItem().setItemStack(emptyMap);
            player.sendMessage(PayBotPlugin.f("§7[PayBot] Bản đồ QR này đã hết hạn nên đã được chuyển thành bản đồ trống."));
        }
    }
}
