package com.naptien.compat;

import net.minecraft.server.MinecraftServer;
import com.naptien.PayBotMod;

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
