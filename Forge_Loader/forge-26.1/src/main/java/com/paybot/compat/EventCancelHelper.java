package com.paybot.compat;

import java.lang.reflect.Method;

/**
 * EventCancelHelper — Hỗ trợ hủy event an toàn qua dynamic reflection (Rule 17).
 */
public class EventCancelHelper {

    public static void cancel(Object event) {
        if (event == null) return;
        try {
            // 1. Thử setCanceled(boolean)
            try {
                Method m = event.getClass().getMethod("setCanceled", boolean.class);
                m.invoke(event, true);
                return;
            } catch (NoSuchMethodException ignored) {}

            // 2. Thử setCancelled(boolean)
            try {
                Method m = event.getClass().getMethod("setCancelled", boolean.class);
                m.invoke(event, true);
                return;
            } catch (NoSuchMethodException ignored) {}

            // 3. Thử cancel()
            try {
                Method m = event.getClass().getMethod("cancel");
                m.invoke(event);
                return;
            } catch (NoSuchMethodException ignored) {}
        } catch (Throwable ignored) {}
    }
}
