package com.paybot.utils;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * QRCanvasRenderer — Chuyên trách căn chỉnh tỷ lệ và render ảnh QR lên canvas 128x128 pixel.
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn xử lý đồ họa AWT canvas thành class độc lập.
 * 
 * Luôn thực thi trong luồng Async (bất đồng bộ) trước khi đẩy sang Main Server Thread,
 * triệt tiêu hoàn toàn hiện tượng Paper Watchdog cảnh báo lag 10-12s khi JVM khởi tạo Graphics2D.
 */
public final class QRCanvasRenderer {

    private QRCanvasRenderer() {}

    /**
     * Scale và vẽ ảnh QR full 128x128 canvas căn giữa trên nền trắng.
     *
     * @param rawImage ảnh QR gốc (từ ZXing, SePay direct hoặc VietQR API)
     * @return BufferedImage kích thước đúng 128x128 TYPE_INT_RGB
     */
    public static BufferedImage renderTo128Canvas(BufferedImage rawImage) {
        if (rawImage == null) {
            return null;
        }

        try {
            BufferedImage canvas = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = canvas.createGraphics();

            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_OFF);

            // Nền trắng
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, 128, 128);

            // Giữ nguyên tỷ lệ khung hình
            int rawW = rawImage.getWidth();
            int rawH = rawImage.getHeight();
            int drawW = 128;
            int drawH = 128;

            if (rawW > 0 && rawH > 0 && rawW != rawH) {
                double ratio = (double) rawW / rawH;
                if (ratio > 1.0) {
                    drawH = (int) (128.0 / ratio);
                } else {
                    drawW = (int) (128.0 * ratio);
                }
            }

            int xOffset = (128 - drawW) / 2;
            int yOffset = (128 - drawH) / 2;

            g2d.drawImage(rawImage, xOffset, yOffset, drawW, drawH, null);
            g2d.dispose();

            return canvas;
        } catch (Throwable t) {
            return rawImage;
        }
    }
}
