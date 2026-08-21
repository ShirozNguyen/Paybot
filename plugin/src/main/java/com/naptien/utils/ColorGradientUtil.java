package com.naptien.utils;

import org.bukkit.ChatColor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ColorGradientUtil — Chức năng chuyên biệt xử lý tô màu Hex, Gradient và ChatColor legacy.
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn thành 1 class riêng biệt.
 */
public final class ColorGradientUtil {

    private ColorGradientUtil() {}

    private static final Pattern GRADIENT_PATTERN = Pattern.compile("<gradient:(#[A-Fa-f0-9]{6}(?::#[A-Fa-f0-9]{6})+)>([^<]+)</gradient>");
    private static final Pattern AMP_HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern HASH_HEX_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final Pattern PLAIN_HEX_PATTERN = Pattern.compile("#([A-Fa-f0-9]{6})");

    /**
     * Parse tô màu cho 1 chuỗi text (Hỗ trợ Gradient, Hex &#RRGGBB, <#RRGGBB>, #RRGGBB và màu &a-f).
     */
    public static String colorize(String text) {
        if (text == null || text.isEmpty()) return "";

        // 1. Xử lý Gradient tags <gradient:#HEX1:#HEX2>Text</gradient>
        text = processGradient(text);

        // 2. Xử lý Hex &#RRGGBB
        Matcher matcher = AMP_HEX_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(sb, toSpigotHex(hex));
        }
        matcher.appendTail(sb);
        text = sb.toString();

        // 3. Xử lý Hex <#RRGGBB>
        matcher = HASH_HEX_PATTERN.matcher(text);
        sb = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(sb, toSpigotHex(hex));
        }
        matcher.appendTail(sb);
        text = sb.toString();

        // 4. Color codes &a - &f, &0 - &9, &k - &r
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Process <gradient:#HEX1:#HEX2:...>text</gradient>
     */
    private static String processGradient(String input) {
        Matcher matcher = GRADIENT_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String[] hexColors = matcher.group(1).split(":");
            String content = matcher.group(2);

            List<Color> colors = new ArrayList<>();
            for (String hex : hexColors) {
                try {
                    colors.add(Color.decode(hex));
                } catch (Exception ignored) {}
            }

            if (colors.size() < 2 || content.isEmpty()) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(content));
                continue;
            }

            String gradientText = applyGradient(content, colors);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(gradientText));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String applyGradient(String text, List<Color> colors) {
        StringBuilder sb = new StringBuilder();
        int length = text.length();
        if (length == 0) return "";

        int numSections = colors.size() - 1;
        double charsPerSection = (double) length / numSections;

        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            int section = Math.min((int) (i / charsPerSection), numSections - 1);
            double ratio = (i - (section * charsPerSection)) / charsPerSection;

            Color c1 = colors.get(section);
            Color c2 = colors.get(section + 1);

            int r = (int) (c1.getRed() + ratio * (c2.getRed() - c1.getRed()));
            int g = (int) (c1.getGreen() + ratio * (c2.getGreen() - c1.getGreen()));
            int b = (int) (c1.getBlue() + ratio * (c2.getBlue() - c1.getBlue()));

            String hex = String.format("%02X%02X%02X", r, g, b);
            sb.append(toSpigotHex(hex)).append(c);
        }

        return sb.toString();
    }

    /**
     * Chuyển RRGGBB thành §x§R§R§G§G§B§B (mã màu Hex tương thích Spigot 1.16+ và BungeeChat).
     */
    private static String toSpigotHex(String hex) {
        StringBuilder sb = new StringBuilder("§x");
        for (char c : hex.toCharArray()) {
            sb.append('§').append(c);
        }
        return sb.toString();
    }
}
