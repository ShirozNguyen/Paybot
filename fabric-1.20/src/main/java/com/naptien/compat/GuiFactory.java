package com.naptien.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import com.naptien.utils.ComponentColorParser;

/**
 * GuiFactory — Tạo đối tượng GuiBackend tương thích 100% với tất cả các phiên bản Minecraft (1.14.4+).
 * Sử dụng Vanilla Minecraft Container Menu (ChestMenu & SimpleContainer) zero external dependency.
 */
public final class GuiFactory {

    private GuiFactory() {}

    public static GuiBackend create(MenuType<?> type, ServerPlayer player, String title) {
        int slots = 54;
        if (type == MenuType.GENERIC_9x1) slots = 9;
        else if (type == MenuType.GENERIC_9x2) slots = 18;
        else if (type == MenuType.GENERIC_9x3) slots = 27;
        else if (type == MenuType.GENERIC_9x4) slots = 36;
        else if (type == MenuType.GENERIC_9x5) slots = 45;
        
        // Dùng ComponentColorParser.parse() để title GUI hiện màu đúng (Component.literal() không parse mã §)
        return new VanillaGuiBackend(player, ComponentColorParser.parse(title), slots);
    }
}
