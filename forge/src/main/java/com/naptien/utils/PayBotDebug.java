package com.naptien.utils;

import com.naptien.PayBotMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PayBotDebug — Class chuyên biệt xử lý log debug cho Forge module khi debug-mode bật (Rule 17).
 */
public final class PayBotDebug {
    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-Debug");

    private PayBotDebug() {}

    public static boolean isDebugEnabled(PayBotMod mod) {
        return mod != null && mod.getConfig().getBoolean("debug-mode", false);
    }

    public static void log(PayBotMod mod, String message) {
        if (isDebugEnabled(mod)) {
            LOGGER.info("[DEBUG] {}", message);
        }
    }

    public static void warn(PayBotMod mod, String message, Throwable t) {
        if (isDebugEnabled(mod)) {
            LOGGER.warn("[DEBUG-WARN] " + message, t);
        }
    }
}
