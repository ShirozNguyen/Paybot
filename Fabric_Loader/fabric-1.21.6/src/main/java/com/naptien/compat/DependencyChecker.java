package com.naptien.compat;

/**
 * DependencyChecker — Kiểm tra sự tồn tại của class bằng Java thuần qua reflection.
 * Đảm bảo không gây nạp class dở dang hoặc crash thô hệ thống khi thiếu thư viện.
 */
public class DependencyChecker {

    /**
     * Kiểm tra xem class theo tên đầy đủ (fully-qualified name) có tồn tại trong ClassLoader không.
     *
     * @param className Tên đầy đủ của class cần kiểm tra (ví dụ: dev.architectury.platform.Platform)
     * @return true nếu class tồn tại và nạp thành công, false nếu không tìm thấy
     */
    public static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, DependencyChecker.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        }
    }
}
