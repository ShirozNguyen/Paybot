package com.naptien.managers;

import com.naptien.PayBotMod;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * QRMapManager (Fabric) — Tải QR từ VietQR, vẽ lên Minecraft map, giao player.
 *
 * v5.0.0:
 *   - Primary  : VietQR.io (qr_only template)
 *   - Fallback : ZXing local — sinh TRỰC TIẾP 128×128, không scale thêm
 *   - Scale dùng NEAREST_NEIGHBOR (không blur) → QR nét, đen trắng thuần
 *   - Binary threshold: lum > 127 → trắng, ≤ 127 → đen
 *   - Notification keys khớp với config template (notifications.X)
 *   - QR tự xóa sau 30 phút
 */
public class QRMapManager {

    private final PayBotMod mod;

    private static final ScheduledExecutorService deleteScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "PayBot-QRDelete");
                t.setDaemon(true);
                return t;
            });

    private static final ConcurrentHashMap<UUID, Integer> pendingQRs = new ConcurrentHashMap<>();

    public QRMapManager(PayBotMod mod) { this.mod = mod; }

    // ─── Entry points ─────────────────────────────────────────────────────────

    /** Standalone mode. */
    public void createAndGiveQR(ServerPlayerEntity player, int amount, String invoiceId) {
        createAndGiveQR(player, amount, invoiceId, "");
    }

    /**
     * Connected mode — nhận primaryUrl từ bot.
     * Fallback chain: bot URL → VietQR.io → ZXing local.
     */
    public void createAndGiveQR(ServerPlayerEntity player, int amount, String invoiceId,
                                String primaryUrl) {
        mod.runAsync(() -> {
            try {
                String bankName  = mod.getConfig().getString("sepay.bank-name",    "").trim();
                String bankAcct  = mod.getConfig().getString("sepay.bank-account", "").trim();
                String acctName  = mod.getConfig().getString("sepay.account-name", "").trim();

                // Ưu tiên sepay-api nếu đã cấu hình
                if (mod.getConfig().getBoolean("sepay-api.enabled", false)) {
                    String apiBank = mod.getConfig().getString("sepay-api.bank-short-name", "").trim();
                    String apiAcct = mod.getConfig().getString("sepay-api.bank-account-number", "").trim();
                    String apiName = mod.getConfig().getString("sepay-api.account-holder-name", "").trim();
                    if (!apiBank.isEmpty() && !apiAcct.isEmpty()) {
                        bankName = apiBank; bankAcct = apiAcct;
                        if (!apiName.isEmpty()) acctName = apiName;
                    }
                }

                BufferedImage img = null;

                // 1. URL từ bot
                if (!primaryUrl.isEmpty()) {
                    img = downloadImage(primaryUrl);
                    if (img != null) PayBotMod.LOGGER.debug("[QRMap] URL bot OK");
                }

                // 2. VietQR.io qr_only
                if (img == null && !bankAcct.isEmpty() && !bankName.isEmpty()) {
                    img = downloadImage(buildVietQRUrl(bankName, bankAcct, acctName, amount, invoiceId));
                    if (img != null) {
                        PayBotMod.LOGGER.debug("[QRMap] VietQR.io OK");
                    } else {
                        if (mod.isNotifEnabled("qr-create-fail") && mod.getLogFilter().allow("qr-create-fail"))
                            PayBotMod.LOGGER.warn("[QRMap] VietQR.io thất bại → fallback ZXing");
                    }
                }

                // 3. ZXing local
                if (img == null) {
                    img = generateLocalQR(buildVietQRString(bankName, bankAcct, amount, invoiceId));
                    if (img == null && mod.isNotifEnabled("qr-create-fail") && mod.getLogFilter().allow("qr-create-fail"))
                        PayBotMod.LOGGER.error("[QRMap] ZXing cũng thất bại — không có QR!");
                }

                final BufferedImage finalImg      = img;
                final String        finalBankName = bankName;
                final String        finalBankAcct = bankAcct;
                final String        finalAcctName = acctName;
                mod.runOnMainThread(() -> {
                    try {
                        giveMapToPlayer(
                                player,
                                finalImg,
                                amount,
                                invoiceId,
                                finalBankName,
                                finalBankAcct,
                                finalAcctName
                        );
                    } catch (Exception e) {
                        if (mod.isNotifEnabled("qr-create-fail") && mod.getLogFilter().allow("qr-create-fail"))
                            PayBotMod.LOGGER.error("[QRMap] giveMap error: " + e.getMessage());
                        sendBankInfo(
                                player,
                                amount,
                                invoiceId,
                                finalBankName,
                                finalBankAcct,
                                finalAcctName
                        );
                    }
                });
            } catch (Exception e) {
                if (mod.getLogFilter().allow("qr-error"))
                    PayBotMod.LOGGER.error("[QRMap] Lỗi không xác định: " + e.getMessage());
            }
        });
    }

    // ─── VietQR URL ───────────────────────────────────────────────────────────

    private static String buildVietQRUrl(String bank, String acct, String name, int amt, String desc) {
        StringBuilder url = new StringBuilder("https://img.vietqr.io/image/")
                .append(bank).append("-").append(acct).append("-qr_only.png")
                .append("?amount=").append(amt)
                .append("&addInfo=").append(desc);
        if (!name.isEmpty()) {
            try {
                url.append("&accountName=")
                   .append(java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
        }
        return url.toString();
    }

    // ─── Give map ─────────────────────────────────────────────────────────────

    private void giveMapToPlayer(ServerPlayerEntity player, BufferedImage img,
                                 int amount, String invoiceId,
                                 String bankName, String bankAcct, String acctName) {
        ServerWorld world  = (ServerWorld) player.getWorld();
        MapIdComponent mapId = world.increaseAndGetMapId();

        net.minecraft.item.map.MapState state =
                net.minecraft.item.map.MapState.of((byte) 0, true, world.getRegistryKey());

        byte mapWhite = findClosestMapColor(0xFFFFFF);
        byte mapBlack = findClosestMapColor(0x000000);
        Arrays.fill(state.colors, mapWhite);

        if (img != null) {
            // Scale NEAREST_NEIGHBOR — không tạo gray pixel ở biên module QR
            BufferedImage scaled = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 128, 128);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_SPEED);
            g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_SPEED);
            g.drawImage(img, 0, 0, 128, 128, null);
            g.dispose();

            // Binary threshold: không gray, chỉ đen/trắng
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    int rgb = scaled.getRGB(x, y);
                    int lum = ((rgb >> 16 & 0xFF) * 299 + (rgb >> 8 & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
                    state.colors[y * 128 + x] = lum > 127 ? mapWhite : mapBlack;
                }
            }
        }

        state.locked = true;
        state.markDirty();
        world.putMapState(mapId, state);

        // Tạo filled_map item
        ItemStack mapItem = new ItemStack(Items.FILLED_MAP);
        mapItem.set(DataComponentTypes.MAP_ID, mapId);
        mapItem.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("§6✦ §aQR Nạp §e" + PayBotMod.formatVnd(amount) + " VND §6✦"));
        
        net.minecraft.nbt.NbtCompound customNbt = new net.minecraft.nbt.NbtCompound();
        customNbt.putString("paybot_invoice_id", invoiceId);
        mapItem.set(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.of(customNbt));

        List<Text> lore = new ArrayList<>();
        lore.add(Text.literal("§7Ngân hàng  : §f" + (bankName.isEmpty() ? "?" : bankName)));
        lore.add(Text.literal("§7Số TK      : §e" + (bankAcct.isEmpty() ? "?" : bankAcct)));
        lore.add(Text.literal("§7Tên TK     : §f" + (acctName.isEmpty() ? "?" : acctName)));
        lore.add(Text.literal("§7Số tiền    : §a" + PayBotMod.formatVnd(amount) + " VND"));
        lore.add(Text.literal("§7Nội dung CK: §e§l" + invoiceId));
        lore.add(Text.literal("§c§oQR tự xóa sau 30 phút."));
        mapItem.set(DataComponentTypes.LORE, new LoreComponent(lore));

        player.getInventory().offerOrDrop(mapItem);
        sendBankInfo(player, amount, invoiceId, bankName, bankAcct, acctName);

        UUID playerUuid = player.getUuid();
        String playerName = player.getName().getString();
        int mapIntId = mapId.id();
        pendingQRs.put(playerUuid, mapIntId);

        deleteScheduler.schedule(() ->
                mod.runOnMainThread(() -> deleteQRMap(playerUuid, playerName, mapIntId, invoiceId)),
                30, TimeUnit.MINUTES);

        if (mod.isNotifEnabled("qr-created") && mod.getLogFilter().allow("qr-created")) {
            PayBotMod.LOGGER.info("[QRMap] QR tạo thành công: player=" + playerName
                    + " mapId=" + mapIntId + " invoice=" + invoiceId);
        }
        mod.notifyAdmins("§7[PayBot] §f" + playerName + " §7vừa tạo QR §enạp bank §e"
                + PayBotMod.formatVnd(amount) + " VND");
    }

    // ─── Delete QR ────────────────────────────────────────────────────────────

    private void deleteQRMap(UUID playerUuid, String playerName, int mapIntId, String invoiceId) {
        pendingQRs.remove(playerUuid);
        ServerPlayerEntity player = mod.getServer().getPlayerManager().getPlayer(playerUuid);
        if (player == null) return;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty() || !stack.isOf(Items.FILLED_MAP)) continue;
            MapIdComponent id = stack.get(DataComponentTypes.MAP_ID);
            if (id != null && id.id() == mapIntId) {
                player.getInventory().removeStack(i);
                player.sendMessage(Text.literal("§7[PayBot] §oQR §e"
                        + invoiceId.substring(0, Math.min(10, invoiceId.length()))
                        + "...§7§o đã hết hạn 30 phút, tự động xóa."));
                if (mod.isNotifEnabled("order-expired") && mod.getLogFilter().allow("order-expired"))
                    PayBotMod.LOGGER.info("[QRMap] QR hết hạn: player=" + playerName + " mapId=" + mapIntId);
                return;
            }
        }
    }

    // ─── Bank info chat ───────────────────────────────────────────────────────

    private static void sendBankInfo(ServerPlayerEntity player, int amount, String invoiceId,
                                     String bankName, String bankAcct, String acctName) {
        player.sendMessage(Text.literal("§6§l══════ Thông tin chuyển khoản ══════"));
        player.sendMessage(Text.literal("§7Ngân hàng  : §f" + (bankName.isEmpty() ? "?" : bankName)));
        player.sendMessage(Text.literal("§7Số TK      : §e" + (bankAcct.isEmpty() ? "?" : bankAcct)));
        player.sendMessage(Text.literal("§7Tên TK     : §f" + (acctName.isEmpty() ? "?" : acctName)));
        player.sendMessage(Text.literal("§7Số tiền    : §a" + PayBotMod.formatVnd(amount) + " VND"));
        player.sendMessage(Text.literal("§7Nội dung CK: §e§l" + invoiceId));
        player.sendMessage(Text.literal("§c⚠ Ghi §eđúng nội dung§c để tự nhận phần thưởng!"));
        player.sendMessage(Text.literal("§7QR trong balo tự xóa sau §e30 phút§7."));
        player.sendMessage(Text.literal("§6§l══════════════════════════════════════"));
    }

    // ─── Download ─────────────────────────────────────────────────────────────

    private static BufferedImage downloadImage(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
            conn.setConnectTimeout(8000); conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "PayBot/5.0.0");
            try (InputStream is = conn.getInputStream()) { return ImageIO.read(is); }
        } catch (Exception e) { return null; }
    }

    // ─── ZXing local — sinh 128×128 trực tiếp, không scale thêm ──────────────

    private static BufferedImage generateLocalQR(String content) {
        try {
            var hints = new java.util.HashMap<com.google.zxing.EncodeHintType, Object>();
            hints.put(com.google.zxing.EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(com.google.zxing.EncodeHintType.ERROR_CORRECTION,
                    com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.L);
            hints.put(com.google.zxing.EncodeHintType.MARGIN, 2);
            var matrix = new com.google.zxing.qrcode.QRCodeWriter()
                    .encode(content, com.google.zxing.BarcodeFormat.QR_CODE, 128, 128, hints);
            BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 128; y++)
                for (int x = 0; x < 128; x++)
                    img.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
            return img;
        } catch (Exception e) {
            PayBotMod.LOGGER.warn("[QRMap] ZXing error: " + e.getMessage());
            return null;
        }
    }

    // ─── EMVCo VietQR string ──────────────────────────────────────────────────

    private static String buildVietQRString(String bank, String acct, int amount, String desc) {
        String bin  = getBankBin(bank);
        if (bin.isEmpty()) return "BANK:" + bank + "|ACC:" + acct + "|AMT:" + amount + "|MSG:" + desc;
        String mai  = tlv("00","A000000727") + tlv("01", acct) + tlv("02", bin);
        String p62  = tlv("62", tlv("08", desc.length() > 25 ? desc.substring(0, 25) : desc));
        String body = tlv("00","01") + tlv("01","12") + tlv("38", mai)
                + tlv("52","0000") + tlv("53","704")
                + (amount > 0 ? tlv("54", String.valueOf(amount)) : "")
                + tlv("58","VN") + p62 + "6304";
        return body + crc16(body);
    }

    private static String tlv(String id, String v) { return id + String.format("%02d", v.length()) + v; }

    private static String crc16(String str) {
        int crc = 0xFFFF;
        for (byte b : str.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int j = 0; j < 8; j++) crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
        }
        return String.format("%04X", crc & 0xFFFF);
    }

    private static String getBankBin(String name) {
        String k = name.toLowerCase().replaceAll("[\\s_\\-]","");
        Map<String,String> m = new java.util.LinkedHashMap<>();
        m.put("mbbank","970422"); m.put("mb","970422");
        m.put("vcb","970436");    m.put("vietcombank","970436");
        m.put("vietinbank","970415"); m.put("icb","970415");
        m.put("bidv","970418");   m.put("agribank","970405");
        m.put("techcombank","970407"); m.put("tcb","970407");
        m.put("tpbank","970423"); m.put("vpbank","970432");
        m.put("acb","970416");    m.put("sacombank","970403"); m.put("stb","970403");
        m.put("hdbank","970437"); m.put("ocb","970448");
        m.put("msb","970426");    m.put("seabank","970440");
        m.put("shinhanbank","970424"); m.put("shinhan","970424");
        m.put("vib","970441");    m.put("eximbank","970431");
        m.put("abbank","970425"); m.put("ncb","970419");
        m.put("scb","970429");    m.put("lpb","970449");
        m.put("baovietbank","970438"); m.put("vietbank","970433");
        m.put("bacabank","970409"); m.put("coopbank","970446");
        return m.getOrDefault(k, "");
    }

    // ─── Map color: trắng nhất (variant 2) và đen nhất (variant 3) ──────────

    private static byte findClosestMapColor(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        byte best = 0; double bestDist = Double.MAX_VALUE;
        float[] mults = {180f/255, 220f/255, 1f, 135f/255};
        for (int id = 1; id < 64; id++) {
            net.minecraft.block.MapColor mc = net.minecraft.block.MapColor.get(id);
            if (mc == null || mc.color == 0) continue;
            int baseR = (mc.color >> 16) & 0xFF, baseG = (mc.color >> 8) & 0xFF, baseB = mc.color & 0xFF;
            for (int i = 0; i < 4; i++) {
                int mr = Math.min(255,(int)(baseR*mults[i])), mg = Math.min(255,(int)(baseG*mults[i])), mb = Math.min(255,(int)(baseB*mults[i]));
                double d = (r-mr)*(double)(r-mr)+(g-mg)*(double)(g-mg)+(b-mb)*(double)(b-mb);
                if (d < bestDist) { bestDist = d; best = (byte)((id << 2) | i); }
            }
        }
        return best;
    }

}
