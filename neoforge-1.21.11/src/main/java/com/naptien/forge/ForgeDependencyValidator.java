package com.naptien.forge;

import com.naptien.compat.DependencyChecker;
import net.neoforged.fml.ModList;

/**
 * ForgeDependencyValidator — Kiểm tra các dependency phụ thuộc bắt buộc riêng trên môi trường Forge/NeoForge.
 * Giúp báo lỗi có kiểm soát và hiển thị thông tin hướng dẫn tiếng Việt rõ ràng khi người dùng quên chưa cài Architectury API.
 */
public class ForgeDependencyValidator {

    public static void validate() {
        boolean hasMod = false;
        try {
            hasMod = ModList.get() != null && ModList.get().isLoaded("architectury");
        } catch (Throwable ignored) {
            hasMod = DependencyChecker.isClassPresent("dev.architectury.platform.Platform");
        }
        boolean hasClass = DependencyChecker.isClassPresent("dev.architectury.platform.Platform");

        if (!hasMod || !hasClass) {
            String errorMsg = "\n"
                    + "================================================================================\n"
                    + "[PayBot Forge/NeoForge] THIẾU MOD BẮT BUỘC (MISSING DEPENDENCY):\n"
                    + "  Mod PayBot yêu cầu mod 'Architectury API' (modid: architectury) để khởi chạy.\n"
                    + "  Vui lòng tải mod 'Architectury API' (bản Forge/NeoForge tương ứng với Minecraft) từ:\n"
                    + "    • CurseForge: https://www.curseforge.com/minecraft/mc-mods/architectury-api\n"
                    + "    • Modrinth:   https://modrinth.com/mod/architectury-api\n"
                    + "  sau đó copy file .jar vào thư mục 'mods/' của server/client rồi thử lại.\n"
                    + "================================================================================\n";
            System.err.println(errorMsg);
            throw new RuntimeException("[PayBot Forge/NeoForge] THIẾU MOD BẮT BUỘC: Vui lòng cài đặt mod Architectury API (Forge/NeoForge) vào thư mục mods!");
        }
    }
}
