// v5.5.5 Part 93: Folia and Folia-forks (Canvas) full audit and thread-safety compliance
package com.paybot.managers;

import com.paybot.PayBotPlugin;
import com.paybot.utils.BankBinResolver;
import com.paybot.utils.QRCanvasRenderer;
import com.paybot.utils.QRImageEngine;
import com.paybot.utils.SchedulerUtils;
import com.paybot.utils.SePayQrUrlBuilder;
import com.paybot.utils.VersionCompat;
import com.paybot.utils.VietQREMVCoGenerator;
import com.paybot.utils.VietQrApiClient;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;

/**
 * Quản lý QR Map.
 *
 * Expiry:
 *  - 30 phút sau khi give map → xoá map + notify bot qua /api/napbank-expired
 *  - Bot có task riêng check_expired_bank_orders làm backup (atomic, không double-process)
 *
 * Changelog:
 *   v7.0.0 (Part 182) — Ủy thác render canvas AWT 128x128 cho QRCanvasRenderer thực thi 100%
 *            trên luồng Async, triệt tiêu hoàn toàn Paper Watchdog lag/freeze 12s.
 */
public class QRMapManager implements Listener {

    private final PayBotPlugin plugin;
    private final NamespacedKey invoiceKey;

    public QRMapManager(PayBotPlugin plugin) {
        this.plugin     = plugin;
        this.invoiceKey = new NamespacedKey(plugin, "invoice_id");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ── Events ────────────────────────────────────────────────────────────────

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isQRMap(ItemStack item) {
        if (item == null || item.getType() != VersionCompat.getMapMaterial()) return false;
        return VersionCompat.getInvoiceId(item, invoiceKey) != null;
    }

    private boolean hasAnyQRMap(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isQRMap(item)) return true;
        }
        return false;
    }

    private static String formatVnd(int amount) {
        StringBuilder sb = new StringBuilder(String.valueOf(amount));
        int len = sb.length();
        for (int i = len - 3; i > 0; i -= 3) sb.insert(i, '.');
        return sb.toString();
    }

    private static String safe(String s) { return s == null ? "" : s; }

    /**
     * v5.0.0 — Ép ảnh QR thô về thuần đen/trắng tuyệt đối (binarize theo luminance threshold).
     */
    private static BufferedImage binarize(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                int luminance = (r * 299 + g * 587 + b * 114) / 1000;
                out.setRGB(x, y, luminance < 140 ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return out;
    }

    // ── giveQRMap: simple overload (image-only, không có text overlay) ─────────

    public void giveQRMap(Player player, String imageUrl, String invoiceId) {
        giveQRMap(player, imageUrl, null, invoiceId, null, null, null, 0);
    }

    /** Overload với fallbackUrl, không có bank info → render ảnh QR thuần. */
    public void giveQRMap(Player player, String imageUrl, String fallbackUrl, String invoiceId) {
        giveQRMap(player, imageUrl, fallbackUrl, invoiceId, null, null, null, 0);
    }

    /**
     * v7.0.0 Part 181/182: Kèm thông tin ngân hàng để gán vào Lore/Display Name của bản đồ.
     */
    public void giveQRMap(Player player, String imageUrl, String fallbackUrl, String invoiceId,
                           String bankName, String bankAccount, String accountName, int amount) {
        if (player != null) {
            PlayerCooldownManager.CooldownCheckResult cd = PlayerCooldownManager.getInstance().checkQrMapCooldown(player.getUniqueId());
            if (!cd.isAllowed()) {
                player.sendMessage("§c[PayBot] Vui lòng chờ " + cd.getRemainingSeconds() + " giây nữa để tạo mã QR mới!");
                return;
            }
            PlayerCooldownManager.getInstance().recordQrMapCreation(player.getUniqueId());
        }

        SchedulerUtils.runAsync(plugin, () -> {
            BufferedImage rawImage = null;
            String usedSource = "LOCAL_VIETQR_NAPAS";

            String bName = (bankName != null && !bankName.isEmpty()) ? bankName :
                    plugin.getConfig().getString("sepay-api.bank-short-name",
                    plugin.getConfig().getString("sepay.bank-name", "")).trim();
            String bAcct = (bankAccount != null && !bankAccount.isEmpty()) ? bankAccount :
                    plugin.getConfig().getString("sepay-api.bank-account-number",
                    plugin.getConfig().getString("sepay.bank-account", "")).trim();
            String aName = (accountName != null && !accountName.isEmpty()) ? accountName :
                    plugin.getConfig().getString("sepay-api.account-holder-name",
                    plugin.getConfig().getString("sepay.account-name", "")).trim();

            // ── LỚP 1: LOCAL VIETQR EMVCO ENGINE (0ms - Siêu nét, 100% App Ngân Hàng Quét Được) ──
            if (!bName.isEmpty() && !bAcct.isEmpty()) {
                try {
                    String emvco = VietQREMVCoGenerator.generate(bName, bAcct, amount, invoiceId);
                    if (emvco != null && !emvco.isEmpty()) {
                        rawImage = QRImageEngine.renderQR(emvco);
                        if (rawImage != null) {
                            usedSource = "LOCAL_VIETQR_EMVCO";
                        }
                    }
                } catch (Throwable t) {
                    NotificationManager.warn(plugin, "qr-create-fail",
                            "[PayBot] Lớp 1 (Local VietQR EMVCo) gặp lỗi: " + t.getMessage() + " — kích hoạt Lớp 2...");
                }
            }

            // ── LỚP 2: SEPAY DIRECT PNG API (Fallback 1) ──
            if (rawImage == null && !bName.isEmpty() && !bAcct.isEmpty()) {
                try {
                    String sepayUrl = SePayQrUrlBuilder.buildUrl(bName, bAcct, amount, invoiceId);
                    rawImage = downloadImage(sepayUrl);
                    if (rawImage != null) {
                        usedSource = sepayUrl;
                        NotificationManager.log(plugin, "qr-created", "[PayBot] Lớp 2 (SePay Direct PNG) thành công: " + sepayUrl);
                    }
                } catch (Throwable t) {
                    NotificationManager.warn(plugin, "qr-create-fail",
                            "[PayBot] Lớp 2 (SePay Direct PNG) gặp lỗi: " + t.getMessage() + " — kích hoạt Lớp 3...");
                }
            }

            // ── LỚP 3: VIETQR OFFICIAL API V2 (Fallback 2) ──
            if (rawImage == null && !bName.isEmpty() && !bAcct.isEmpty()) {
                try {
                    String bin = BankBinResolver.resolveBin(bName);
                    String emvcoApi = VietQrApiClient.fetchEmvcoString(bin, bAcct, aName, amount, invoiceId);
                    if (emvcoApi != null && !emvcoApi.isEmpty()) {
                        rawImage = QRImageEngine.renderQR(emvcoApi);
                        if (rawImage != null) {
                            usedSource = "VIETQR_API_V2";
                            NotificationManager.log(plugin, "qr-created", "[PayBot] Lớp 3 (VietQR Official API v2) thành công.");
                        }
                    }
                } catch (Throwable t) {
                    NotificationManager.warn(plugin, "qr-create-fail",
                            "[PayBot] Lớp 3 (VietQR Official API v2) gặp lỗi: " + t.getMessage());
                }
            }

            // Fallback cuối cùng: tải URL từ tham số nếu các lớp trên chưa có ảnh
            if (rawImage == null && imageUrl != null && !imageUrl.isEmpty()) {
                try {
                    rawImage = downloadImage(imageUrl);
                    if (rawImage != null) usedSource = imageUrl;
                } catch (Exception ignored) {}
            }
            if (rawImage == null && fallbackUrl != null && !fallbackUrl.isEmpty()) {
                try {
                    rawImage = downloadImage(fallbackUrl);
                    if (rawImage != null) usedSource = fallbackUrl;
                } catch (Exception ignored) {}
            }

            if (rawImage == null) {
                SchedulerUtils.runForPlayer(plugin, player, () ->
                    player.sendMessage(PayBotPlugin.f("§c[PayBot] §fKhông tạo được mã QR! Vui lòng liên hệ Admin.")));
                return;
            }

            // [Rule 17] Render canvas AWT 128x128 hoàn toàn trên Async Thread qua QRCanvasRenderer
            // triệt tiêu 100% lag/hang Watchdog server main thread do nạp AWT WToolkit/GDI
            final BufferedImage renderedCanvas = QRCanvasRenderer.renderTo128Canvas(rawImage);
            final String        finalSource = usedSource;
            SchedulerUtils.runForPlayer(plugin, player, () ->
                deliverQRImage(player, renderedCanvas, invoiceId, finalSource,
                        bName, bAcct, aName, amount));
        });
    }

    /**
     * Tạo MapView → give ItemStack bản đồ QR cho người chơi (Lore giữ toàn bộ thông tin CK).
     * [Part 182] Chạy trên main/entity thread nhưng nhận canvas đã vẽ xong từ Async Thread qua QRCanvasRenderer (< 1ms).
     */
    private void deliverQRImage(Player player, BufferedImage finalImage, String invoiceId, String sourceUrl,
                                 String bankName, String bankAccount, String accountName, int amount) {
        if (!player.isOnline()) {
            plugin.getLogger().info("Player " + player.getName() + " đã offline, bỏ qua give QR map.");
            return;
        }
        try {
            MapView mapView = Bukkit.createMap(player.getWorld());
            // Defensive: copy list trước khi remove để tránh ConcurrentModificationException
            // nếu phiên bản Bukkit nào đó trả về live-list cho getRenderers().
            for (MapRenderer r : new java.util.ArrayList<>(mapView.getRenderers())) {
                mapView.removeRenderer(r);
            }
            mapView.addRenderer(new QRMapRenderer(finalImage));
            VersionCompat.setLockedSafe(mapView, true);
            mapView.setScale(MapView.Scale.NORMAL);

            ItemStack mapItem = new ItemStack(VersionCompat.getMapMaterial());
            MapMeta   meta    = (MapMeta) mapItem.getItemMeta();
            if (meta == null) throw new Exception("MapMeta là null");

            if (amount > 0) {
                meta.setDisplayName("§6✦ §aQR Nạp §e" + formatVnd(amount) + " VND §6✦");
            } else {
                meta.setDisplayName("§aQR Chuyển Khoản");
            }

            java.util.List<String> lore = new java.util.ArrayList<>();
            if (bankName != null && !bankName.isEmpty()) {
                lore.add("§7Ngân hàng  : §f" + bankName);
                lore.add("§7Số TK      : §e" + (bankAccount != null && !bankAccount.isEmpty() ? bankAccount : "?"));
                lore.add("§7Tên TK     : §f" + (accountName != null && !accountName.isEmpty() ? accountName : "?"));
                lore.add("§7Số tiền    : §a" + formatVnd(amount) + " VND");
                lore.add("§7Nội dung CK: §e§l" + invoiceId);
                lore.add("§c§oQR tự xóa sau 30 phút.");
            } else {
                lore.add("§7Mã đơn: §f" + invoiceId);
                lore.add("§7Quét bằng app ngân hàng để thanh toán");
                lore.add("§eCầm map để xem QR");
                lore.add("§c§oQR tự xóa sau 30 phút.");
            }
            meta.setLore(lore);

            VersionCompat.storeInvoiceId(meta, mapView, invoiceId, invoiceKey);
            mapItem = VersionCompat.applyMapToItem(mapItem, meta, mapView);

            Map<Integer, ItemStack> leftover = player.getInventory().addItem(mapItem);
            if (leftover != null && !leftover.isEmpty()) {
                for (ItemStack dropItem : leftover.values()) {
                    if (dropItem != null) {
                        player.getWorld().dropItemNaturally(player.getLocation(), dropItem);
                    }
                }
                player.sendMessage(PayBotPlugin.f("§e[PayBot] §fTúi đồ của bạn đã đầy! Bản đồ QR thanh toán đã rơi xuống chân bạn 💳"));
            } else {
                player.sendMessage(PayBotPlugin.f("§a[PayBot] §fQR chuyển khoản đã vào balo! Vui lòng kiểm tra balo để nạp 💳"));
            }
            NotificationManager.log(plugin, "qr-created",
                    "[PayBot] Đã tạo QR cho " + player.getName() + " (invoice=" + invoiceId + ")");

            final String fInvoiceId = invoiceId;
            // v5.5.5 Part 112: Chuyển giao quản lý vòng đời bộ đếm sang QRMapSessionTracker (Rule 17)
            // Hỗ trợ tạm dừng (pause) khi player offline và tiếp tục (resume) khi join lại.
            // Đếm ngược qua 1 task nhẹ tập trung, không gây lag trên server lớn (Folia/Canvas thread-safe).
            if (plugin.getQrMapSessionTracker() != null) {
                plugin.getQrMapSessionTracker().startSession(player, fInvoiceId, mapView.getId(), 1800L);
            }

        } catch (Exception e) {
            NotificationManager.warn(plugin, "qr-create-fail", "[PayBot] Lỗi tạo QR map item: " + e.getMessage());
            if (player.isOnline())
                player.sendMessage(PayBotPlugin.f("§c[PayBot] §fLỗi tạo QR map! Vui lòng thử lại."));
        }
    }

    /** Xoá QR map khỏi inventory. Phải gọi trên main thread. */
    public boolean removeQRMap(Player player, String invoiceId) {
        PlayerInventory inv     = player.getInventory();
        boolean         removed = false;

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() != VersionCompat.getMapMaterial()) continue;
            String stored = VersionCompat.getInvoiceId(item, invoiceKey);
            if (invoiceId.equals(stored)) {
                var itemMeta = item.getItemMeta();
                if (itemMeta instanceof MapMeta mm) {
                    try {
                        Object id = mm.getClass().getMethod("getMapId").invoke(mm);
                        if (id instanceof Integer mapId) VersionCompat.clearInvoiceCache(mapId);
                    } catch (Exception ignored) {
                        VersionCompat.clearInvoiceCache(item.getDurability());
                    }
                }
                inv.setItem(i, null);
                removed = true;
                if (plugin.getQrMapSessionTracker() != null) {
                    plugin.getQrMapSessionTracker().stopSession(invoiceId);
                }
                break;
            }
        }

        return removed;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private BufferedImage downloadImage(String imageUrl) throws Exception {
        URL url = new URL(imageUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        conn.setRequestProperty("User-Agent", "PayBotPlugin/1.0");
        conn.connect();
        if (conn.getResponseCode() != 200)
            throw new Exception("HTTP " + conn.getResponseCode() + " khi tải ảnh QR");
        return ImageIO.read(conn.getInputStream());
    }

    // ── QRMapRenderer ─────────────────────────────────────────────────────────

    public static class QRMapRenderer extends MapRenderer {
        private final BufferedImage image;
        private byte[] paletteBuffer = null;

        public QRMapRenderer(BufferedImage image) {
            super(false);
            this.image = image;
            initPalette();
        }

        @SuppressWarnings("deprecation")
        private void initPalette() {
            try {
                byte[] buf = new byte[128 * 128];
                for (int x = 0; x < 128; x++) {
                    for (int y = 0; y < 128; y++) {
                        buf[y * 128 + x] = org.bukkit.map.MapPalette.matchColor(new Color(image.getRGB(x, y), true));
                    }
                }
                this.paletteBuffer = buf;
            } catch (Throwable t) {
                this.paletteBuffer = null;
            }
        }

        @Override
        public void render(MapView view, MapCanvas canvas, Player player) {
            // Luôn luôn phủ đè toàn bộ pixel QR lên canvas để ngăn chặn địa hình vanilla quét đè
            if (paletteBuffer != null) {
                for (int x = 0; x < 128; x++) {
                    for (int y = 0; y < 128; y++) {
                        canvas.setPixel(x, y, paletteBuffer[y * 128 + x]);
                    }
                }
            } else {
                canvas.drawImage(0, 0, image);
            }
        }
    }
}
