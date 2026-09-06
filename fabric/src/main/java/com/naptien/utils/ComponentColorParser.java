package com.naptien.utils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;

/**
 * ComponentColorParser — Lớp chuyên biệt xử lý parse chuỗi văn bản chứa mã màu Legacy (§ và &)
 * thành Minecraft Vanilla Component (net.minecraft.network.chat.Component).
 * 
 * Bắt buộc tuân thủ Quy tắc 17: Tách biệt hoàn toàn chức năng parse màu sắc sang Component.
 */
public class ComponentColorParser {

    private ComponentColorParser() {}

    /**
     * Chuyển đổi chuỗi văn bản chứa mã màu legacy (§a, &b,...) thành Component chính xác màu sắc.
     */
    public static Component parse(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        // Standardize & to §
        String formatted = text.replace('&', '§');
        if (!formatted.contains("§")) {
            return Component.literal(formatted);
        }

        MutableComponent root = Component.empty();
        StringBuilder currentText = new StringBuilder();
        Style currentStyle = Style.EMPTY;

        int len = formatted.length();
        for (int i = 0; i < len; i++) {
            char c = formatted.charAt(i);
            if (c == '§' && i + 1 < len) {
                // 1. Spigot/Bukkit Hex format: §x§R§R§G§G§B§B
                if (i + 13 < len && Character.toLowerCase(formatted.charAt(i + 1)) == 'x') {
                    boolean isSpigotHex = true;
                    StringBuilder hexSb = new StringBuilder();
                    for (int k = 0; k < 6; k++) {
                        if (formatted.charAt(i + 2 + k * 2) != '§') {
                            isSpigotHex = false;
                            break;
                        }
                        char hexChar = formatted.charAt(i + 3 + k * 2);
                        if (Character.digit(hexChar, 16) == -1) {
                            isSpigotHex = false;
                            break;
                        }
                        hexSb.append(hexChar);
                    }
                    if (isSpigotHex) {
                        if (currentText.length() > 0) {
                            root.append(Component.literal(currentText.toString()).withStyle(currentStyle));
                            currentText.setLength(0);
                        }
                        int rgb = Integer.parseInt(hexSb.toString(), 16);
                        currentStyle = currentStyle.withColor(net.minecraft.network.chat.TextColor.fromRgb(rgb));
                        i += 13;
                        continue;
                    }
                }

                // 2. Direct Hex format: §#RRGGBB
                if (i + 7 < len && formatted.charAt(i + 1) == '#') {
                    String hexCandidate = formatted.substring(i + 2, i + 8);
                    try {
                        int rgb = Integer.parseInt(hexCandidate, 16);
                        if (currentText.length() > 0) {
                            root.append(Component.literal(currentText.toString()).withStyle(currentStyle));
                            currentText.setLength(0);
                        }
                        currentStyle = currentStyle.withColor(net.minecraft.network.chat.TextColor.fromRgb(rgb));
                        i += 7;
                        continue;
                    } catch (NumberFormatException ignored) {}
                }

                // 3. Legacy single-character color code (§0-9, §a-f, §k-o, §r)
                char code = Character.toLowerCase(formatted.charAt(i + 1));
                ChatFormatting format = getByCode(code);
                if (format != null) {
                    if (currentText.length() > 0) {
                        root.append(Component.literal(currentText.toString()).withStyle(currentStyle));
                        currentText.setLength(0);
                    }
                    if (format.isFormat()) {
                        currentStyle = applyFormat(currentStyle, format);
                    } else if (format == ChatFormatting.RESET) {
                        currentStyle = Style.EMPTY;
                    } else {
                        // Reset formatting upon new color code
                        currentStyle = Style.EMPTY.withColor(format);
                    }
                    i++; // Skip color code char
                    continue;
                }
            }
            currentText.append(c);
        }

        if (currentText.length() > 0) {
            root.append(Component.literal(currentText.toString()).withStyle(currentStyle));
        }

        return root;
    }

    /**
     * Chuyển danh sách String (Lore) thành danh sách Component màu sắc.
     */
    public static List<Component> parseLore(List<String> lore) {
        if (lore == null) return List.of();
        List<Component> result = new ArrayList<>();
        for (String line : lore) {
            result.add(parse(line));
        }
        return result;
    }

    private static ChatFormatting getByCode(char code) {
        for (ChatFormatting cf : ChatFormatting.values()) {
            if (cf.getChar() == code) {
                return cf;
            }
        }
        return null;
    }

    private static Style applyFormat(Style style, ChatFormatting format) {
        return switch (format) {
            case BOLD -> style.withBold(true);
            case ITALIC -> style.withItalic(true);
            case UNDERLINE -> style.withUnderlined(true);
            case STRIKETHROUGH -> style.withStrikethrough(true);
            case OBFUSCATED -> style.withObfuscated(true);
            default -> style;
        };
    }
}
