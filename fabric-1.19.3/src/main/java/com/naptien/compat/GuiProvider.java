package com.naptien.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * GuiProvider — Cung cấp GuiBackend thuần Vanilla Minecraft (MC 1.14.4+).
 */
public final class GuiProvider {

    private GuiProvider() {}

    public static GuiBackend create(ServerPlayer player, Component title, int size) {
        return new VanillaGuiBackend(player, title, size);
    }
}
