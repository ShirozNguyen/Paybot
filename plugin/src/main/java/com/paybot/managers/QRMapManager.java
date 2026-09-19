// v5.5.5 Part 93: Folia and Folia-forks (Canvas) full audit and thread-safety compliance
package com.paybot.managers;

import com.paybot.PayBotPlugin;
import com.paybot.utils.SchedulerUtils;
import com.paybot.utils.VersionCompat;
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
import java.awt.*;
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
 *   v5.0.0 — Vẽ trực tiếp 6 dòng thông tin (ngân hàng, số TK, tên TK, số tiền,
 *            nội dung CK, note hết hạn) lên map canvas, dưới ảnh QR.
 *            QR thu nhỏ về 84x84 để chừa chỗ cho text overlay.
 *   v5.0.0 (fix) — Thêm bước binarize() ép ảnh QR về thuần đen/trắng trước khi vẽ
 *            (Thứ 1: fix QR bị xám xịt do pixel anti-alias từ ảnh gốc bị MapPalette
 *            quy về màu xám/nâu gần nhất). Toàn bộ log đổi qua NotificationManager.
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
     * v5.0.0 — Ép ảnh QR thô về thuần đen/trắng tuyệt đối (binarize theo luminance
     * threshold). Đây là root-cause fix cho QR bị "xám xịt" (xem giải thích ở deliverQRImage).
     * Mọi pixel có luminance < 140 → đen (0xFF000000), ngược lại → trắng (0xFFFFFFFF).
     * Ngưỡng 140 (trên thang 0-255) được chọn hơi cao hơn 128 vì hầu hết ảnh QR có nền
     * trắng chiếm đa số diện tích — threshold cao giúp giữ module đen đủ đậm mà không
     * làm mất nét viền mảnh.
     */
    private static BufferedImage binarize(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                int luminance = (r * 299 + g * 587 + b * 114) / 1000; // công thức luminance chuẩn
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
     * v5.0.0 — Overload đầy đủ: kèm thông tin ngân hàng để vẽ overlay 6 dòng
     * lên map (ngân hàng, số TK, tên TK, số tiền, nội dung CK, note hết hạn).
     * Nếu bankName == null → bỏ qua overlay, chỉ render ảnh QR như cũ.
     */
    public void giveQRMap(Player player, String imageUrl, String fallbackUrl, String invoiceId,
                           String bankName, String bankAccount, String accountName, int amount) {
        SchedulerUtils.runAsync(plugin, () -> {
            BufferedImage rawImage = null;
            String usedUrl = imageUrl;

            try {
                rawImage = downloadImage(imageUrl);
            } catch (Exception e) {
                NotificationManager.warn(plugin, "qr-create-fail",
                        "[PayBot] QR primary URL thất bại (" + imageUrl + "): "
                        + e.getMessage() + (fallbackUrl != null ? " — thử fallback..." : ""));
            }

            if (rawImage == null && fallbackUrl != null && !fallbackUrl.isEmpty()) {
                try {
                    rawImage = downloadImage(fallbackUrl);
                    usedUrl  = fallbackUrl;
                    if (rawImage != null)
                        NotificationManager.log(plugin, "qr-created", "[PayBot] QR fallback thành công: " + fallbackUrl);
                } catch (Exception e2) {
                    NotificationManager.warn(plugin, "qr-create-fail",
                            "[PayBot] QR fallback cũng thất bại (" + fallbackUrl + "): " + e2.getMessage());
                }
            }

            if (rawImage == null) {
                SchedulerUtils.runForPlayer(plugin, player, () ->
                    player.sendMessage(PayBotPlugin.f("§c[PayBot] §fKhông tải được QR! Vui lòng thử lại sau.")));
                return;
            }

            final BufferedImage finalImage = rawImage;
            final String        finalUrl   = usedUrl;
            SchedulerUtils.runForPlayer(plugin, player, () ->
                deliverQRImage(player, finalImage, invoiceId, finalUrl,
                        bankName, bankAccount, accountName, amount));
        });
    }

    /**
     * Scale ảnh QR + (tuỳ chọn) vẽ overlay 6 dòng text → tạo MapView → give
     * ItemStack → set timer 30 phút. [Part 45] Gọi qua SchedulerUtils.runForPlayer —
     * chạy trên entity-scheduler của player (Folia) hoặc main thread (Bukkit/Paper thường).
     */
    private void deliverQRImage(Player player, BufferedImage rawImage, String invoiceId, String sourceUrl,
                                 String bankName, String bankAccount, String accountName, int amount) {
        try {
            boolean hasOverlay = (bankName != null && !bankName.isEmpty());

            BufferedImage canvas = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = canvas.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_OFF);
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, 128, 128);

            // Sử dụng trực tiếp rawImage để hiển thị màu sắc logo nguyên bản
            BufferedImage qrImage = rawImage;

            if (hasOverlay) {
                // v5.0.0: 72x72 — QR nhỏ + đơn giản hơn, có áp dụng Aspect Ratio chống méo ảnh
                int qrSize = 72;
                int qrX = (128 - qrSize) / 2;
                
                double ratio = (double) qrImage.getWidth() / qrImage.getHeight();
                int drawW = qrSize;
                int drawH = qrSize;
                if (ratio > 1.0) {
                    drawH = (int) (qrSize / ratio);
                } else {
                    drawW = (int) (qrSize * ratio);
                }
                int xOffset = (qrSize - drawW) / 2;
                int yOffset = (qrSize - drawH) / 2;
                
                g2d.drawImage(qrImage, qrX + xOffset, yOffset, drawW, drawH, null);
                drawInfoLines(g2d, qrSize + 2, bankName, bankAccount, accountName, amount, invoiceId);
            } else {
                // Không có thông tin overlay → vẽ full 128x128 giữ nguyên tỷ lệ
                double ratio = (double) qrImage.getWidth() / qrImage.getHeight();
                int drawW = 128;
                int drawH = 128;
                if (ratio > 1.0) {
                    drawH = (int) (128 / ratio);
                } else {
                    drawW = (int) (128 * ratio);
                }
                int xOffset = (128 - drawW) / 2;
                int yOffset = (128 - drawH) / 2;
                
                g2d.drawImage(qrImage, xOffset, yOffset, drawW, drawH, null);
            }
            g2d.dispose();
            final BufferedImage finalImage = canvas;

            SchedulerUtils.runForPlayer(plugin, player, () -> {
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

                    meta.setDisplayName("§aQR Chuyển Khoản");
                    meta.setLore(Arrays.asList(
                        "§7Mã đơn: §f" + invoiceId,
                        "§7Quét bằng app ngân hàng để thanh toán",
                        "§eCầm map để xem QR",
                        "§eHết hạn sau 30 phút, vui lòng giữ yên trong balo, sẽ tự động được xoá"
                    ));

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
            });

        } catch (Exception e) {
            NotificationManager.warn(plugin, "qr-create-fail", "[PayBot] Lỗi tạo QR map item: " + e.getMessage());
            if (player.isOnline())
                player.sendMessage(PayBotPlugin.f("§c[PayBot] §fLỗi tạo QR map! Vui lòng thử lại."));
        }
    }

    /** Vẽ 6 dòng thông tin dưới QR: ngân hàng, STK, tên TK, số tiền, nội dung CK, note hết hạn. */
    private static void drawInfoLines(Graphics2D g2d, int yStart, String bankName, String bankAccount,
                                       String accountName, int amount, String invoiceId) {
        String[] lines = {
            "NH: " + safe(bankName),
            "STK: " + safe(bankAccount),
            safe(accountName),
            formatVnd(amount) + " VND",
            "ND: " + safe(invoiceId),
            "Tu xoa sau 30 phut, giu yen trong balo"
        };
        int available = 128 - yStart - 2;
        int rowH = Math.max(5, available / lines.length);
        g2d.setColor(Color.BLACK);
        for (int i = 0; i < lines.length; i++) {
            drawFitted(g2d, lines[i], 124, yStart + (i + 1) * rowH - 1);
        }
    }

    /** Vẽ 1 dòng, tự giảm font size để fit width, truncate "..." nếu vẫn không đủ. */
    private static void drawFitted(Graphics2D g2d, String text, int maxWidth, int baselineY) {
        if (text == null || text.isEmpty()) return;
        int size = 7;
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, size);
        FontMetrics fm = g2d.getFontMetrics(font);
        while (fm.stringWidth(text) > maxWidth && size > 4) {
            size--;
            font = new Font(Font.SANS_SERIF, Font.PLAIN, size);
            fm = g2d.getFontMetrics(font);
        }
        String draw = text;
        if (fm.stringWidth(draw) > maxWidth) {
            while (draw.length() > 3 && fm.stringWidth(draw + "..") > maxWidth) {
                draw = draw.substring(0, draw.length() - 1);
            }
            draw = draw + "..";
        }
        g2d.setFont(font);
        int x = Math.max(2, (128 - fm.stringWidth(draw)) / 2);
        g2d.drawString(draw, x, baselineY);
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
