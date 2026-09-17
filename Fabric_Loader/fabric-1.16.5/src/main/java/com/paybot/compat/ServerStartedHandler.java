package com.paybot.compat;

import net.minecraft.server.MinecraftServer;
import com.paybot.PayBotMod;

/**
 * ServerStartedHandler — Lớp xử lý sự kiện SERVER_STARTED tường minh.
 */
public class ServerStartedHandler {

    private final PayBotMod mod;

    public ServerStartedHandler(PayBotMod mod) {
        this.mod = mod;
    }

    public void onStart(MinecraftServer srv) {
        mod.onServerStart(srv);
    }
}
