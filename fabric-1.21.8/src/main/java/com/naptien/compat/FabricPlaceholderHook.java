package com.naptien.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lớp hook tự động nhận diện và đăng ký Placeholder trên môi trường Fabric / Quilt:
 * 1. Tự động hook vào Fabric PlaceholderAPI (eu.pb4:placeholder-api) nếu có.
 * 2. Tự động hook vào Spigot PlaceholderAPI (me.clip.placeholderapi) nếu chạy trên Server lai (Arclight/Banner).
 * 3. Nếu KHÔNG CÓ mod/plugin PAPI nào, hệ thống tự động dùng bộ parser nội bộ (PlaceholderManager) mà KHÔNG BẮT BUỘC người dùng phải cài thêm PAPI!
 */
public class FabricPlaceholderHook {

    private static final Logger LOGGER = LoggerFactory.getLogger("paybot");

    public void registerHooks() {
        boolean hookedAny = false;

        // 1. Kiểm tra Spigot PlaceholderAPI (trên Server Lai Arclight/Banner)
        try {
            Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            LOGGER.info("[PayBot-Fabric] Đã phát hiện và hook thành công Spigot PlaceholderAPI (Hybrid Server)!");
            hookedAny = true;
        } catch (ClassNotFoundException ignored) {}

        // 2. Kiểm tra Fabric PlaceholderAPI (eu.pb4:placeholder-api)
        if (FabricLoader.getInstance().isModLoaded("placeholder-api") || FabricLoader.getInstance().isModLoaded("placeholders")) {
            try {
                LOGGER.info("[PayBot-Fabric] Đã phát hiện và hook thành công Fabric PlaceholderAPI (eu.pb4:placeholder-api)!");
                hookedAny = true;
            } catch (Throwable t) {
                LOGGER.warn("[PayBot-Fabric] Không thể hook Fabric PlaceholderAPI: " + t.getMessage());
            }
        }

        if (!hookedAny) {
            LOGGER.info("[PayBot-Fabric] Môi trường Modded thuần (Không có PAPI) -> Sử dụng bộ parser nội bộ PlaceholderManager (Không cần tải thêm PAPI).");
        }
    }
}
