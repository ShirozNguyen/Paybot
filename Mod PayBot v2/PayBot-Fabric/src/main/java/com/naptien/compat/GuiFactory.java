package com.naptien.compat;

import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * GuiFactory — Tạo đối tượng GuiBackend tương thích với phiên bản Minecraft đang chạy.
 */
public final class GuiFactory {

    private GuiFactory() {}

    /**
     * Khởi tạo GuiBackend động dựa trên phiên bản Minecraft đang chạy.
     */
    public static GuiBackend create(ScreenHandlerType<?> type, ServerPlayerEntity player, String title) {
        // Trong tương lai, nếu có SguiGuiBackendV2 (SGUI 2.x cho MC 26.x), 
        // chúng ta sẽ kiểm tra McVersionHelper.usesSguiV2() và khởi tạo class đó qua Reflection.
        // Hiện tại, tất cả các bản từ 1.20.1 đến 1.21.11 đều sử dụng SGUI 1.x (SguiGuiBackend).
        
        if (McVersionHelper.usesSguiV2()) {
            try {
                // Sử dụng Reflection để load SguiGuiBackendV2 động khi chạy trên MC 26.x.
                // Việc này tránh lỗi NoClassDefFoundError khi chạy trên 1.20/1.21 nếu class V2 import thư viện SGUI 2.x
                return (GuiBackend) Class.forName("com.naptien.compat.SguiGuiBackendV2")
                        .getConstructor(ScreenHandlerType.class, ServerPlayerEntity.class, String.class)
                        .newInstance(type, player, title);
            } catch (Exception e) {
                // Cảnh báo và fallback về SguiGuiBackend mặc định
                System.err.println("[PayBot] Không thể load SguiGuiBackendV2 cho MC 26.x, fallback về V1: " + e.getMessage());
            }
        }
        
        return new SguiGuiBackend(type, player, title);
    }
}
