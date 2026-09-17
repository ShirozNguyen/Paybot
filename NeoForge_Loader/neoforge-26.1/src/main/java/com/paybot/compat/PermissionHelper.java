package com.paybot.compat;

import java.lang.reflect.Method;

/**
 * PermissionHelper — Class riêng biệt kiểm tra quyền hạn an toàn đa phiên bản (Quy tắc 17).
 * Hỗ trợ ServerPlayer, Player, CommandSourceStack, và CommandSource qua reflection.
 */
public class PermissionHelper {

    public static boolean hasPermissions(Object target, int level) {
        if (target == null) return false;

        // 1. Thử method hasPermissions(int)
        try {
            Method m = target.getClass().getMethod("hasPermissions", int.class);
            return (Boolean) m.invoke(target, level);
        } catch (Throwable ignored) {}

        // 2. Thử method hasPermission(int)
        try {
            Method m = target.getClass().getMethod("hasPermission", int.class);
            return (Boolean) m.invoke(target, level);
        } catch (Throwable ignored) {}

        // 3. Thử getPermissionLevel()
        try {
            Method m = target.getClass().getMethod("getPermissionLevel");
            Object res = m.invoke(target);
            if (res instanceof Number) {
                return ((Number) res).intValue() >= level;
            }
        } catch (Throwable ignored) {}

        // 4. Thử getServer() / isOp qua PlayerList
        try {
            Object server = null;
            try {
                Method m = target.getClass().getMethod("getServer");
                server = m.invoke(target);
            } catch (Throwable ignored) {
                try {
                    Method m = target.getClass().getMethod("server");
                    server = m.invoke(target);
                } catch (Throwable ignored2) {}
            }
            if (server != null) {
                Method mPl = server.getClass().getMethod("getPlayerList");
                Object pl = mPl.invoke(server);
                if (pl != null) {
                    Object p = target;
                    try {
                        Method mGetPlayer = target.getClass().getMethod("getPlayer");
                        Object pRes = mGetPlayer.invoke(target);
                        if (pRes != null) p = pRes;
                    } catch (Throwable ignored) {}

                    try {
                        Method mIsOp = pl.getClass().getMethod("isOp", p.getClass());
                        return (Boolean) mIsOp.invoke(pl, p);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }
}
