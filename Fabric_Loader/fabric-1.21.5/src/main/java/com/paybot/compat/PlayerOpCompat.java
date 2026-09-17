package com.paybot.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import java.lang.reflect.Method;

/**
 * PlayerOpCompat — Class riêng biệt phụ trách kiểm tra và cấp/gỡ OP an toàn đa phiên bản.
 * 
 * Tuân thủ Quy tắc 17: Xử lý an toàn cả GameProfile (MC <= 1.21.8) và NameAndId (MC 1.21.9+).
 */
public class PlayerOpCompat {

    public static boolean isOp(PlayerList playerList, ServerPlayer player, Object profile) {
        if (playerList == null) return false;
        // 1. Thử gọi trực tiếp qua player nếu có
        try {
            Method m = playerList.getClass().getMethod("isOp", ServerPlayer.class);
            return (Boolean) m.invoke(playerList, player);
        } catch (Throwable ignored) {}

        // 2. Thử gọi qua đối tượng profile / NameAndId
        for (Method m : playerList.getClass().getMethods()) {
            if (m.getName().equals("isOp") && m.getParameterCount() == 1) {
                Class<?> paramType = m.getParameterTypes()[0];
                if (profile != null && paramType.isAssignableFrom(profile.getClass())) {
                    try {
                        return (Boolean) m.invoke(playerList, profile);
                    } catch (Throwable ignored) {}
                }
                // Thử lấy nameAndId từ player
                if (player != null && paramType.getSimpleName().contains("NameAndId")) {
                    try {
                        Method mNameAndId = player.getClass().getMethod("nameAndId");
                        Object nameAndId = mNameAndId.invoke(player);
                        return (Boolean) m.invoke(playerList, nameAndId);
                    } catch (Throwable ignored) {}
                }
            }
        }
        return false;
    }

    public static void op(PlayerList playerList, ServerPlayer player, Object profile) {
        if (playerList == null) return;
        try {
            Method m = playerList.getClass().getMethod("op", ServerPlayer.class);
            m.invoke(playerList, player);
            return;
        } catch (Throwable ignored) {}

        for (Method m : playerList.getClass().getMethods()) {
            if (m.getName().equals("op") && m.getParameterCount() == 1) {
                Class<?> paramType = m.getParameterTypes()[0];
                if (profile != null && paramType.isAssignableFrom(profile.getClass())) {
                    try {
                        m.invoke(playerList, profile);
                        return;
                    } catch (Throwable ignored) {}
                }
                if (player != null && paramType.getSimpleName().contains("NameAndId")) {
                    try {
                        Method mNameAndId = player.getClass().getMethod("nameAndId");
                        Object nameAndId = mNameAndId.invoke(player);
                        m.invoke(playerList, nameAndId);
                        return;
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    public static void deop(PlayerList playerList, Object profile) {
        if (playerList == null || profile == null) return;
        for (Method m : playerList.getClass().getMethods()) {
            if (m.getName().equals("deop") && m.getParameterCount() == 1) {
                Class<?> paramType = m.getParameterTypes()[0];
                if (paramType.isAssignableFrom(profile.getClass())) {
                    try {
                        m.invoke(playerList, profile);
                        return;
                    } catch (Throwable ignored) {}
                }
            }
        }
    }
}
