package com.paybot.utils;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * QRImageEngine — Chuyên trách vẽ mã QR cục bộ bằng ZXing QRCodeWriter ra BufferedImage.
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn engine dựng hình và xử lý pixel QR thành class độc lập.
 * 
 * Tối ưu hóa hiển thị cho Minecraft Map:
 * - MARGIN = 1 (Tối đa hóa kích thước module, không viền thừa).
 * - ErrorCorrectionLevel.M (15% phục hồi lỗi chuẩn VietQR, module to, sắc nét, dễ bắt nét trên điện thoại).
 * - Màu tương phản tuyệt đối: 0xFF000000 (Đen) và 0xFFFFFFFF (Trắng).
 */
public final class QRImageEngine {

    private QRImageEngine() {}

    /**
     * Dựng ảnh QR kích thước mặc định 128x128 pixel cho bản đồ Minecraft.
     */
    public static BufferedImage renderQR(String content) {
        return renderQR(content, 128);
    }

    /**
     * Dựng ảnh QR với kích thước tùy biến (vuông size x size).
     */
    public static BufferedImage renderQR(String content, int size) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        try {
            Map<com.google.zxing.EncodeHintType, Object> hints = new HashMap<>();
            hints.put(com.google.zxing.EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(com.google.zxing.EncodeHintType.ERROR_CORRECTION,
                    com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M);
            hints.put(com.google.zxing.EncodeHintType.MARGIN, 1);

            com.google.zxing.common.BitMatrix matrix = new com.google.zxing.qrcode.QRCodeWriter()
                    .encode(content, com.google.zxing.BarcodeFormat.QR_CODE, size, size, hints);

            int width = matrix.getWidth();
            int height = matrix.getHeight();
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }

            return image;
        } catch (Throwable t) {
            return null;
        }
    }
}
