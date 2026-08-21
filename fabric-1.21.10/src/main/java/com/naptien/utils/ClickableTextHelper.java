package com.naptien.utils;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * ClickableTextHelper (Common / ModLoader) — Lớp trợ giúp tạo tin nhắn Chat
 * có thể CLICK BẤM vào để tự động nhập lệnh (SUGGEST_COMMAND) hoặc gửi lệnh (RUN_COMMAND)
 * cho 4 ModLoader (Fabric, Forge, NeoForge, Quilt).
 * 
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
        
        ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, commandSuggest);
        comp.withStyle(style -> style.withClickEvent(clickEvent));

        if (hoverTooltip != null && !hoverTooltip.isEmpty()) {
            HoverEvent hoverEvent = new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    ComponentColorParser.parse(hoverTooltip)
            );
            comp.withStyle(style -> style.withHoverEvent(hoverEvent));
        }

        return comp;
    }

    /**
     * Tạo Component có thể click bấm để thực thi thẳng lệnh.
     */
    public static Component makeRunCommand(String text, String commandRun, String hoverTooltip) {
        MutableComponent comp = (MutableComponent) ComponentColorParser.parse(text);
        
        ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.RUN_COMMAND, commandRun);
        comp.withStyle(style -> style.withClickEvent(clickEvent));

        if (hoverTooltip != null && !hoverTooltip.isEmpty()) {
            HoverEvent hoverEvent = new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    ComponentColorParser.parse(hoverTooltip)
            );
            comp.withStyle(style -> style.withHoverEvent(hoverEvent));
        }

        return comp;
    }
}
