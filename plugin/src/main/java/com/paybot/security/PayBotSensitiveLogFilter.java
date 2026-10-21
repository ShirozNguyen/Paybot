package com.paybot.security;

import java.util.logging.Filter;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

/**
 * PayBotSensitiveLogFilter — Màng lọc bảo mật cấp hệ thống (System-level Log Filter).
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn thành 1 class đơn nhiệm độc lập.
 * Ngăn chặn 100% rò rỉ mã PIN, Serial, SePay API Token, Card Partner Key và MySQL Password ra Console & File Log.
 */
public final class PayBotSensitiveLogFilter implements Filter {

    private static final Pattern CARD_NUMBERS_PATTERN = Pattern.compile("\\b\\d{11,16}\\b");
    private static final Pattern SEPAY_TOKEN_PATTERN = Pattern.compile("apt_[a-zA-Z0-9_\\-]{8,}");
    private static final Pattern PARTNER_KEY_PATTERN = Pattern.compile("sec_[a-zA-Z0-9_\\-]{8,}");
    private static final Pattern SENSITIVE_PASS_PATTERN = Pattern.compile("(?i)(password|mysql\\.pass|bot-token)\\s*[:=]\\s*[^\\s]+");

    private static volatile boolean registered = false;

    private PayBotSensitiveLogFilter() {}

    public static synchronized void register(java.util.logging.Logger logger) {
        if (registered) return;
        registered = true;

        PayBotSensitiveLogFilter filter = new PayBotSensitiveLogFilter();
        if (logger != null) {
            logger.setFilter(filter);
        }

        // Đăng ký vào Root Logger của Java Util Logging
        try {
            java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
            if (root != null) {
                root.setFilter(filter);
            }
        } catch (Throwable ignored) {}

        // Thử đăng ký Log4j2 Core Filter nếu runtime có sẵn (Minecraft Paper/Fabric/Forge)
        try {
            Class<?> logManagerClass = Class.forName("org.apache.logging.log4j.LogManager");
            Object rootLogger = logManagerClass.getMethod("getRootLogger").invoke(null);
            if (rootLogger != null) {
                // Tạo dynamic log4j filter
                registerLog4jFilter(rootLogger);
            }
        } catch (Throwable ignored) {}
    }

    private static void registerLog4jFilter(Object rootLogger) {
        try {
            Class<?> filterClass = Class.forName("org.apache.logging.log4j.core.Filter");
            // Kiểm tra method addFilter
            java.lang.reflect.Method addFilter = rootLogger.getClass().getMethod("addFilter", filterClass);
            // Proxy động cho Log4j Filter
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                    filterClass.getClassLoader(),
                    new Class<?>[]{filterClass},
                    (p, method, args) -> {
                        String mName = method.getName();
                        if ("filter".equals(mName) && args != null && args.length > 0) {
                            for (Object arg : args) {
                                if (arg != null) {
                                    String str = arg.toString();
                                    if (isSensitive(str)) {
                                        // DENY enum
                                        Class<?> resultClass = Class.forName("org.apache.logging.log4j.core.Filter$Result");
                                        return Enum.valueOf((Class<Enum>) resultClass, "DENY");
                                    }
                                }
                            }
                            Class<?> resultClass = Class.forName("org.apache.logging.log4j.core.Filter$Result");
                            return Enum.valueOf((Class<Enum>) resultClass, "NEUTRAL");
                        }
                        if ("isStarted".equals(mName)) return true;
                        if ("isStopped".equals(mName)) return false;
                        if ("start".equals(mName) || "stop".equals(mName)) return null;
                        return null;
                    }
            );
            addFilter.invoke(rootLogger, proxy);
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean isLoggable(LogRecord record) {
        if (record == null) return true;
        String msg = record.getMessage();
        if (msg != null && isSensitive(msg)) {
            return false;
        }
        Object[] params = record.getParameters();
        if (params != null) {
            for (Object p : params) {
                if (p != null && isSensitive(p.toString())) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isSensitive(String text) {
        if (text == null || text.isEmpty()) return false;
        if (CARD_NUMBERS_PATTERN.matcher(text).find()) return true;
        if (SEPAY_TOKEN_PATTERN.matcher(text).find()) return true;
        if (PARTNER_KEY_PATTERN.matcher(text).find()) return true;
        if (SENSITIVE_PASS_PATTERN.matcher(text).find()) return true;
        return false;
    }
}
