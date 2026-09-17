// v5.5.5 Part 89: Tuong thich Minecraft 1.17.1 cho Forge
package com.paybot.compat;

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
