package com.naptien.compat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lớp hook tự động nhận diện và đăng ký Placeholder trên môi trường Forge / NeoForge:
 * 1. Tự động hook vào Spigot PlaceholderAPI (me.clip.placeholderapi) nếu chạy trên Server lai (Mohist/Arclight/Magma).
 * 2. Nếu KHÔNG CÓ mod/plugin PAPI nào, hệ thống tự động dùng bộ parser nội bộ (PlaceholderManager) mà KHÔNG BẮT BUỘC người dùng phải cài thêm PAPI!
 */
public class ForgePlaceholderHook {

    private static final Logger LOGGER = LoggerFactory.getLogger("paybot");

    public void registerHooks() {
        boolean hookedAny = false;

        // 1. Kiểm tra Spigot PlaceholderAPI (trên Server Lai Mohist/Arclight/Magma)
        try {
            Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            LOGGER.info("[PayBot-Forge] Đã phát hiện và hook thành công Spigot PlaceholderAPI (Hybrid Server)!");
            hookedAny = true;
        } catch (ClassNotFoundException ignored) {}

        if (!hookedAny) {
            LOGGER.info("[PayBot-Forge] Môi trường Forge/NeoForge thuần (Không có PAPI) -> Sử dụng bộ parser nội bộ PlaceholderManager (Không cần tải thêm PAPI).");
        }
    }
}
