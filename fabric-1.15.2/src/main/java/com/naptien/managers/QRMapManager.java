package com.naptien.managers;

import com.naptien.PayBotMod;
import com.naptien.utils.ItemStackHelper;
import com.naptien.utils.ItemTagCompat;
import com.naptien.utils.MapItemCompat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.nbt.CompoundTag;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * QRMapManager — Tạo Map Item hiển thị QR chuyển khoản trực tiếp trong game.
 * Mojang Official Mappings (MC 1.20.1).
 */
public class QRMapManager {

    private final PayBotMod mod;
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(2);
    private final ScheduledExecutorService deleteScheduler = Executors.newSingleThreadScheduledExecutor();

    private final ConcurrentHashMap<UUID, Integer> pendingQRs = new ConcurrentHashMap<>();

    public QRMapManager(PayBotMod mod) {
        this.mod = mod;
    }

    public void generateQRMap(ServerPlayer player, int amount, String invoiceId) {
        if (player == null) return;

        downloadExecutor.submit(() -> {
            String bankCode = mod.getConfig().getString("sepay-api.bank-short-name", "").trim();
            if (bankCode.isEmpty()) bankCode = mod.getConfig().getString("sepay.bank-name", "").trim();
            if (bankCode.isEmpty()) bankCode = mod.getConfig().getString("bank-code", "").trim();
            if (bankCode.isEmpty()) bankCode = mod.getConfig().getString("bank-name", "").trim();

            String bankAcct = mod.getConfig().getString("sepay-api.bank-account-number", "").trim();
            if (bankAcct.isEmpty()) bankAcct = mod.getConfig().getString("sepay.bank-account", "").trim();
            if (bankAcct.isEmpty()) bankAcct = mod.getConfig().getString("bank-acct", "").trim();
            if (bankAcct.isEmpty()) bankAcct = mod.getConfig().getString("bank-account", "").trim();

            String acctName = mod.getConfig().getString("sepay-api.account-holder-name", "").trim();
            if (acctName.isEmpty()) acctName = mod.getConfig().getString("sepay.account-name", "").trim();
            if (acctName.isEmpty()) acctName = mod.getConfig().getString("acct-name", "").trim();
            if (acctName.isEmpty()) acctName = mod.getConfig().getString("account-name", "").trim();

            String qrUrl = "https://img.vietqr.io/image/" + bankCode + "-" + bankAcct
                    + "-compact2.png?amount=" + amount
                    + "&addInfo=" + invoiceId
                    + "&accountName=" + URI.create(acctName).toASCIIString();

            BufferedImage qrImg = downloadImage(qrUrl);
            if (qrImg == null) {
                String sePayContent = mod.getConfig().getString("sepay-content-pattern", "{ORDER_ID}").replace("{ORDER_ID}", invoiceId);
                String qrContent = "STK:" + bankAcct + "|NH:" + bankCode + "|ND:" + sePayContent + "|ST:" + amount;
                qrImg = generateLocalQR(qrContent);
            }

            final String finalBankCode = bankCode;
            final String finalBankAcct = bankAcct;
            final String finalAcctName = acctName;
            final BufferedImage finalImg = qrImg;

            mod.runOnMainThread(() -> {
                if (mod.getServer().getPlayerList().getPlayer(player.getUUID()) == null) return;
                try {
                    createMapItemOnMainThread(player, amount, invoiceId, finalBankCode, finalBankAcct, finalAcctName, finalImg);
                } catch (Exception e) {
                    PayBotMod.LOGGER.error("[QRMap] Lỗi tạo Map: " + e.getMessage(), e);
                }
            });
        });
    }

    private void createMapItemOnMainThread(ServerPlayer player, int amount, String invoiceId,
                                           String bankName, String bankAcct, String acctName,
                                           BufferedImage qrImg) {
        ServerLevel world = (ServerLevel) player.level();

        // Tạo MapSavedData mới
        ItemStack mapItem = MapItem.create(world, player.getBlockX(), player.getBlockZ(), (byte) 0, false, false);
        MapItemSavedData state = MapItemCompat.getSavedData(mapItem, world);
        int mapIdInt = 0;
        try {
            Integer id = MapItem.getMapId(mapItem);
            if (id != null) mapIdInt = id;
        } catch (Throwable ignored) {}

        if (state != null && qrImg != null) {
            BufferedImage scaled = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 128, 128);

            int drawW = 128, drawH = 128, xOffset = 0, yOffset = 0;
            if (qrImg.getWidth() != qrImg.getHeight()) {
                if (qrImg.getWidth() > qrImg.getHeight()) {
                    drawH = (int) (128.0 * qrImg.getHeight() / qrImg.getWidth());
                    yOffset = (128 - drawH) / 2;
                } else {
                    drawW = (int) (128.0 * qrImg.getWidth() / qrImg.getHeight());
                    xOffset = (128 - drawW) / 2;
                }
            }
            g.drawImage(qrImg, xOffset, yOffset, drawW, drawH, null);
            g.dispose();

            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    int rgb = scaled.getRGB(x, y);
                    // FIX: dùng MapItemCompat.setColor() thay vì truy cập state.colors[] trực tiếp
                    // (tên field thay đổi sau remap Intermediary/SRG — truy cập thẳng sẽ crash runtime)
                    MapItemCompat.setColor(state, y * 128 + x, findClosestMapColor(rgb));
                }
            }
            // FIX CRITICAL: Lock map để ngăn inventoryTick() ghi đè QR bằng terrain mỗi tick
            MapItemCompat.lockMap(state);
            state.setDirty();
        }

        ItemTagCompat.setInvoiceId(mapItem, invoiceId);

        List<String> lore = List.of(
                "§7Ngân hàng  : §f" + (bankName.isEmpty() ? "?" : bankName),
                "§7Số TK      : §e" + (bankAcct.isEmpty() ? "?" : bankAcct),
                "§7Tên TK     : §f" + (acctName.isEmpty() ? "?" : acctName),
                "§7Số tiền    : §a" + PayBotMod.formatVnd(amount) + " VND",
                "§7Nội dung CK: §e§l" + invoiceId,
                "§c§oQR tự xóa sau 30 phút."
        );
        String name = "§6✦ §aQR Nạp §e" + PayBotMod.formatVnd(amount) + " VND §6✦";
        ItemTagCompat.setItemNameAndLore(mapItem, name, lore);

        player.getInventory().add(mapItem);
        sendBankInfo(player, amount, invoiceId, bankName, bankAcct, acctName);

        UUID playerUuid = player.getUUID();
        String playerName = player.getName().getString();
        final int finalMapIdInt = mapIdInt;
        pendingQRs.put(playerUuid, finalMapIdInt);

        deleteScheduler.schedule(() ->
                mod.runOnMainThread(() -> deleteQRMap(playerUuid, playerName, finalMapIdInt, invoiceId)),
                30, TimeUnit.MINUTES);

        if (mod.isNotifEnabled("qr-created") && mod.getLogFilter().allow("qr-created")) {
            PayBotMod.LOGGER.info("[QRMap] QR tạo thành công: player=" + playerName
                    + " mapId=" + finalMapIdInt + " invoice=" + invoiceId);
        }
        mod.notifyAdmins("§7[PayBot] §f" + playerName + " §7vừa tạo QR §enạp bank §e"
                + PayBotMod.formatVnd(amount) + " VND");
    }

    private void deleteQRMap(UUID playerUuid, String playerName, int mapIntId, String invoiceId) {
        pendingQRs.remove(playerUuid);
        ServerPlayer player = mod.getServer().getPlayerList().getPlayer(playerUuid);
        if (player == null) return;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == Items.FILLED_MAP) {
                String tagInvoice = ItemTagCompat.getInvoiceId(stack);
                if (invoiceId.equals(tagInvoice)) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                    player.sendSystemMessage(Component.literal("§c[PayBot] QR nạp (" + invoiceId + ") đã hết hạn và tự xóa!"));
                    if (mod.isNotifEnabled("order-expired") && mod.getLogFilter().allow("order-expired"))
                        PayBotMod.LOGGER.info("[QRMap] QR hết hạn: player=" + playerName + " mapId=" + mapIntId);
                    return;
                }
            }
        }
    }

    private static void sendBankInfo(ServerPlayer player, int amount, String invoiceId,
                                     String bankName, String bankAcct, String acctName) {
        player.sendSystemMessage(Component.literal("§6§l══════ Thông tin chuyển khoản ══════"));
        player.sendSystemMessage(Component.literal("§7Ngân hàng  : §f" + (bankName.isEmpty() ? "?" : bankName)));
        player.sendSystemMessage(Component.literal("§7Số TK      : §e" + (bankAcct.isEmpty() ? "?" : bankAcct)));
        player.sendSystemMessage(Component.literal("§7Tên TK     : §f" + (acctName.isEmpty() ? "?" : acctName)));
        player.sendSystemMessage(Component.literal("§7Số tiền    : §a" + PayBotMod.formatVnd(amount) + " VND"));
        player.sendSystemMessage(Component.literal("§7Nội dung CK: §e§l" + invoiceId));
        player.sendSystemMessage(Component.literal("§c⚠ Ghi §eđúng nội dung§c để tự nhận phần thưởng!"));
        player.sendSystemMessage(Component.literal("§7QR trong balo tự xóa sau §e30 phút§7."));
        player.sendSystemMessage(Component.literal("§6§l══════════════════════════════════════"));
    }

    private static BufferedImage downloadImage(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
            conn.setConnectTimeout(8000); conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "PayBot/5.0.0");
            try (InputStream is = conn.getInputStream()) { return ImageIO.read(is); }
        } catch (Exception e) { return null; }
    }

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

    private static byte findClosestMapColor(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (byte) (r < 128 && g < 128 && b < 128 ? 119 : 34);
    }

    public void shutdown() {
        downloadExecutor.shutdownNow();
        deleteScheduler.shutdownNow();
    }
}
