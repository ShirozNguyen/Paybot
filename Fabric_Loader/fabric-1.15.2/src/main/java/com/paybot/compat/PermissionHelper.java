package com.paybot.compat;

import net.minecraft.server.level.ServerPlayer;
import java.lang.reflect.Method;

/**
 * PermissionHelper — Class riêng biệt kiểm tra quyền hạn (OP / Permission Level) an toàn đa phiên bản.
 * 
 * Tuân thủ Quy tắc 17: Tránh gọi trực tiếp method hasPermissions(int) có thể thay đổi hoặc vắng mặt trên các bản Minecraft mới/khác loader.
 */
public class PermissionHelper {

    public static boolean hasPermissions(ServerPlayer player, int level) {
        if (player == null) return false;
        try {
            Method m = player.getClass().getMethod("hasPermissions", int.class);
            return (Boolean) m.invoke(player, level);
        } catch (Throwable ignored) {}

        try {
            Method m = player.getClass().getMethod("getPermissionLevel");
            Object res = m.invoke(player);
            if (res instanceof Number) {
                return ((Number) res).intValue() >= level;
            }
        } catch (Throwable ignored) {}

        try {
            Object server = null;
            try {
                java.lang.reflect.Method m = player.getClass().getMethod("getServer");
                server = m.invoke(player);
            } catch (Throwable ignored) {
                try {
                    java.lang.reflect.Method m = player.getClass().getMethod("server");
                    server = m.invoke(player);
                } catch (Throwable ignored2) {}
            }
            if (server != null) {
                Method mPl = server.getClass().getMethod("getPlayerList");
                Object pl = mPl.invoke(server);
                if (pl != null) {
                    Method mIsOp = pl.getClass().getMethod("isOp", player.getClass());
                    return (Boolean) mIsOp.invoke(pl, player);
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }
}
