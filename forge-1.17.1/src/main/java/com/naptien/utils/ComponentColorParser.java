package com.naptien.utils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ComponentColorParser — Lớp chuyên biệt xử lý parse chuỗi văn bản chứa mã màu Legacy (§ và &),
 * Hex kiểu Bukkit/Spigot (§x§R§R§G§G§B§B) và Hex thô (&#RRGGBB / #RRGGBB) thành Minecraft Vanilla
 * Component (net.minecraft.network.chat.Component).
 *
 * [v5.5.5 FIX] TRƯỚC ĐÂY class này chỉ nhận diện mã màu legacy 1 ký tự (0-9a-fk-or), trong khi
 * ColorGradientUtil.colorize() (dùng cho Hex &#RRGGBB và Gradient <gradient:...> trong custom-lore
 * GUI nạp bank/thẻ) sinh ra định dạng Hex 14-ký-tự kiểu Bukkit "§x§R§R§G§G§B§B". Định dạng này
 * KHÔNG được nhận diện → 'x' bị chèn thành ký tự thừa, 6 cặp §<hex> phía sau bị hiểu nhầm thành
 * 6 mã màu vanilla rời rạc (vì chữ số hex trùng với ký tự mã màu hợp lệ) → tên/lore hiển thị sai/
 * garbled trên Fabric & Forge, trong khi Paper (dùng ItemMeta.setDisplayName của Bukkit, hiểu sẵn
 * định dạng §x) vẫn hiển thị đúng. Đây là nguyên nhân gốc chính của bug "lore/tên không hiện đúng"
 * với các dòng cấu hình dùng Hex/Gradient (ví dụ mệnh giá 100k/500k/1M trong config.yml mặc định).
 *
 * Bắt buộc tuân thủ Quy tắc 17: Tách biệt hoàn toàn chức năng parse màu sắc sang Component.
 */
public class ComponentColorParser {

    private ComponentColorParser() {}

    // Hex thô dạng &#RRGGBB hoặc #RRGGBB (KHÔNG đi qua ColorGradientUtil trước) — hỗ trợ phòng hờ
    // cho các caller nào đó (nếu có, hiện tại chưa phát hiện trong project) gọi thẳng parse() mà
    // không tiền xử lý qua ColorGradientUtil.colorize() trước. Không khớp gradient tag (xử lý riêng
    // bởi ColorGradientUtil trước khi tới đây — tới lúc này gradient đã được nó "trải" thành nhiều
    // đoạn Hex §x liên tiếp, nên KHÔNG cần ComponentColorParser tự hiểu cú pháp <gradient:...>).
    private static final Pattern RAW_HEX_PATTERN = Pattern.compile("&?#([0-9a-fA-F]{6})");

    /**
     * Chuyển đổi chuỗi văn bản chứa mã màu legacy (§a, &b,...), Hex Bukkit (§x§h..) hoặc
     * Hex thô (&#RRGGBB) thành Component chính xác màu sắc.
     */
    public static Component parse(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        // Standardize & to §, rồi chuẩn hoá Hex thô (&#RRGGBB / #RRGGBB) về cùng định dạng
        // §x§h§h§h§h§h§h mà vòng lặp chính bên dưới đã hiểu, để không phải viết 2 lần logic parse hex.
        String formatted = text.replace('&', '§');
        formatted = normalizeRawHexToSpigotHex(formatted);

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
                char code = Character.toLowerCase(formatted.charAt(i + 1));

                // [v5.5.5] Hex kiểu Bukkit/Spigot: §x§R§R§G§G§B§B (14 ký tự tính từ vị trí i)
                if (code == 'x') {
                    String hex = tryParseSpigotHex(formatted, i);
                    if (hex != null) {
                        if (currentText.length() > 0) {
                            root.append(Component.literal(currentText.toString()).withStyle(currentStyle));
                            currentText.setLength(0);
                        }
                        // Dùng TextColor.fromRgb(int) thay vì parseColor(String) — signature đơn giản
                        // (nhận int RGB, trả về TextColor trực tiếp), ổn định từ khi Minecraft hỗ trợ
                        // hex color (1.16), tránh phụ thuộc vào kiểu trả về DataResult/Optional có thể
                        // khác nhau giữa các phiên bản.
                        TextColor tc;
                        try {
                            tc = TextColor.fromRgb(Integer.parseInt(hex, 16));
                        } catch (NumberFormatException nfe) {
                            tc = null;
                        }
                        currentStyle = (tc != null) ? currentStyle.withColor(tc) : Style.EMPTY;
                        i += 13; // "§x" + 6×"§h" = 14 ký tự; for-loop tự +1 nên cộng 13 ở đây
                        continue;
                    }
                    // Không khớp đúng 14 ký tự (chuỗi bị cắt cụt) → rơi xuống dưới, xử lý như mã màu thường
                }

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

    /** Thử parse chuỗi bắt đầu tại vị trí i là "§x§h§h§h§h§h§h". Trả về 6 ký tự hex nếu khớp
     *  đúng định dạng, null nếu không (an toàn — không throw, để caller tự fallback). */
    private static String tryParseSpigotHex(String s, int i) {
        if (i + 13 >= s.length()) return null; // không đủ 14 ký tự còn lại
        if (s.charAt(i) != '§' || Character.toLowerCase(s.charAt(i + 1)) != 'x') return null;
        StringBuilder hex = new StringBuilder(6);
        for (int k = 0; k < 6; k++) {
            int base = i + 2 + k * 2;
            if (s.charAt(base) != '§') return null;
            char h = s.charAt(base + 1);
            if (Character.digit(h, 16) < 0) return null;
            hex.append(h);
        }
        return hex.toString();
    }

    /** Chuyển &#RRGGBB / #RRGGBB (đã thành §#RRGGBB sau bước replace &→§) về §x§h§h§h§h§h§h
     *  để vòng lặp chính xử lý thống nhất 1 định dạng duy nhất. */
    private static String normalizeRawHexToSpigotHex(String formatted) {
        // Sau bước replace('&','§') ở trên, "&#RRGGBB" đã thành "§#RRGGBB" — quét cả 2 dạng
        // ("§#" lẫn "#" độc lập) để phòng trường hợp text gốc chỉ dùng "#RRGGBB" không có "&".
        if (formatted.indexOf('#') < 0) return formatted;
        Pattern p = Pattern.compile("§?#([0-9a-fA-F]{6})");
        Matcher m = p.matcher(formatted);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String hex = m.group(1);
            StringBuilder spigot = new StringBuilder("§x");
            for (char h : hex.toCharArray()) spigot.append('§').append(h);
            m.appendReplacement(sb, Matcher.quoteReplacement(spigot.toString()));
        }
        m.appendTail(sb);
        return sb.toString();
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
