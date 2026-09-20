package com.paybot.forge;

/**
 * ForgeDependencyValidator — v5.5.9 Part 125.
 * PayBot 100% Native, hoàn toàn không phụ thuộc vào mod Architectury API.
 * validate() là no-op để đảm bảo tương thích ngược.
 */
public class ForgeDependencyValidator {
    public static void validate() {
        // No-op: PayBot chạy thuần native Forge/NeoForge, không bắt buộc cài mod ngoài.
    }
}
