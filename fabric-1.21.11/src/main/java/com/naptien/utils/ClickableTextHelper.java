package com.naptien.utils;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.net.URI;

/**
 * ClickableTextHelper (Common / ModLoader) — Lớp trợ giúp tạo tin nhắn Chat
 * có thể CLICK BẤM vào để tự động nhập lệnh (SUGGEST_COMMAND), gửi lệnh (RUN_COMMAND),
 * hoặc mở URL (OPEN_URL) cho 4 ModLoader (Fabric, Forge, NeoForge, Quilt).
 *
 * [FIX — audit v5.5.5 Part 53, xác minh qua Yarn docs chính thức (maven.fabricmc.net)]
 * File CŨ dùng {@code new ClickEvent(ClickEvent.Action, String)} và {@code new HoverEvent
 * (HoverEvent.Action, Object)} — ĐÚNG cho Minecraft ≤ 1.21.4 (Yarn 1.21.4+build.8 xác nhận
 * ClickEvent vẫn là class thường với đúng constructor 2 tham số này), NHƯNG Mojang đã tái
 * cấu trúc CẢ ClickEvent LẪN HoverEvent thành sealed interface + record con kể từ 1.21.5
 * (Yarn 1.21.5+build.1 xác nhận {@code ClickEvent.RunCommand}/{@code SuggestCommand}/
 * {@code OpenUrl} là record riêng, KHÔNG còn constructor (Action,String) chung nữa — tương tự
 * {@code HoverEvent.ShowText(Component)}) — bản build cho Minecraft 1.21.5 trở lên (bao gồm
 * chính version này) PHẢI dùng cú pháp record mới, nếu không sẽ không compile được.
 * <p>
 * File này CHỈ áp dụng cho các thư mục version ≥ 1.21.5 — thư mục ≤ 1.21.4 giữ nguyên bản cũ
 * dùng constructor (Action,String), đó là code ĐÚNG cho version của nó, không đụng tới.
 * <p>
 * Bắt buộc tuân thủ Quy tắc 17: Tách biệt hoàn toàn chức năng tạo tin nhắn Chat bấm được.
 */
public class ClickableTextHelper {

    private ClickableTextHelper() {}

    /**
     * Tạo Component có thể click bấm để điền lệnh gợi ý vào ô chat.
     *
     * @param text text hiển thị trên chat
     * @param commandSuggest lệnh sẽ được tự động điền khi click
     * @param hoverTooltip text hiển thị khi di chuột vào (nullable)
     */
    public static Component makeSuggestCommand(String text, String commandSuggest, String hoverTooltip) {
        MutableComponent comp = (MutableComponent) ComponentColorParser.parse(text);

        ClickEvent clickEvent = new ClickEvent.SuggestCommand(commandSuggest);
        comp.withStyle(style -> style.withClickEvent(clickEvent));

        if (hoverTooltip != null && !hoverTooltip.isEmpty()) {
            HoverEvent hoverEvent = new HoverEvent.ShowText(ComponentColorParser.parse(hoverTooltip));
            comp.withStyle(style -> style.withHoverEvent(hoverEvent));
        }

        return comp;
    }

    /**
     * Tạo Component có thể click bấm để thực thi thẳng lệnh.
     */
    public static Component makeRunCommand(String text, String commandRun, String hoverTooltip) {
        MutableComponent comp = (MutableComponent) ComponentColorParser.parse(text);

        ClickEvent clickEvent = new ClickEvent.RunCommand(commandRun);
        comp.withStyle(style -> style.withClickEvent(clickEvent));

        if (hoverTooltip != null && !hoverTooltip.isEmpty()) {
            HoverEvent hoverEvent = new HoverEvent.ShowText(ComponentColorParser.parse(hoverTooltip));
            comp.withStyle(style -> style.withHoverEvent(hoverEvent));
        }

        return comp;
    }

    /**
     * [MỚI — v5.5.5 Part 53] Tạo Component có thể click bấm để MỞ 1 URL trong trình duyệt.
     * {@code ClickEvent.OpenUrl} (record, xác nhận qua mappings.dev) nhận {@link URI}, không
     * phải String — dùng {@link URI#create(String)} nên url truyền vào phải là URL hợp lệ.
     *
     * @param text text hiển thị trên chat
     * @param url URL sẽ mở khi click (vd "https://...")
     * @param hoverTooltip text hiển thị khi di chuột vào (nullable)
     */
    public static Component makeOpenUrl(String text, String url, String hoverTooltip) {
        MutableComponent comp = (MutableComponent) ComponentColorParser.parse(text);

        ClickEvent clickEvent = new ClickEvent.OpenUrl(URI.create(url));
        comp.withStyle(style -> style.withClickEvent(clickEvent));

        if (hoverTooltip != null && !hoverTooltip.isEmpty()) {
            HoverEvent hoverEvent = new HoverEvent.ShowText(ComponentColorParser.parse(hoverTooltip));
            comp.withStyle(style -> style.withHoverEvent(hoverEvent));
        }

        return comp;
    }
}
