package com.paybot.managers;

import java.util.EnumSet;
import java.util.Set;

/**
 * OrderStateMachine — v5.5.6 Part 117 (Tuân thủ Rule 17)
 * <p>
 * Quản lý trạng thái và chu trình chuyển đổi trạng thái của đơn hàng thanh toán
 * theo chuẩn Mục 16 của Master Technical Specification:
 * CREATED -> PENDING -> PAYMENT_DETECTED -> VERIFIED -> APPROVED -> REWARDED -> COMPLETED.
 * Các trạng thái ngoại lệ: UNDERPAID, EXPIRED, CANCELLED, FAILED.
 */
public final class OrderStateMachine {

    public enum OrderStatus {
        CREATED,
        PENDING,
        PAYMENT_DETECTED,
        VERIFIED,
        APPROVED,
        REWARDED,
        COMPLETED,
        UNDERPAID,
        EXPIRED,
        CANCELLED,
        FAILED;

        /**
         * Kiểm tra xem trạng thái này có phải là trạng thái kết thúc (Terminal State) không.
         * Các trạng thái kết thúc không thể tự ý chuyển ngược lại PENDING.
         */
        public boolean isTerminal() {
            return this == COMPLETED || this == EXPIRED || this == CANCELLED || this == FAILED;
        }
    }

    private OrderStateMachine() {
        // Utility class
    }

    /**
     * Kiểm tra tính hợp lệ của việc chuyển đổi từ trạng thái hiện tại sang trạng thái tiếp theo.
     */
    public static boolean isValidTransition(OrderStatus current, OrderStatus next) {
        if (current == null || next == null) return false;
        if (current == next) return true;
        if (current.isTerminal()) {
            return false; // Đã kết thúc thì không được chuyển trạng thái nữa
        }

        switch (current) {
            case CREATED:
                return next == OrderStatus.PENDING || next == OrderStatus.CANCELLED;
            case PENDING:
                return next == OrderStatus.PAYMENT_DETECTED || next == OrderStatus.UNDERPAID
                        || next == OrderStatus.APPROVED || next == OrderStatus.EXPIRED || next == OrderStatus.CANCELLED;
            case PAYMENT_DETECTED:
                return next == OrderStatus.VERIFIED || next == OrderStatus.UNDERPAID || next == OrderStatus.FAILED;
            case VERIFIED:
                return next == OrderStatus.APPROVED || next == OrderStatus.FAILED;
            case APPROVED:
                return next == OrderStatus.REWARDED || next == OrderStatus.COMPLETED;
            case REWARDED:
                return next == OrderStatus.COMPLETED;
            case UNDERPAID:
                return next == OrderStatus.APPROVED || next == OrderStatus.CANCELLED; // Admin có thể duyệt tay sau
            default:
                return false;
        }
    }

    /**
     * Parse chuỗi trạng thái thành Enum an toàn, tương thích ngược với các mã trạng thái cũ.
     */
    public static OrderStatus fromString(String str) {
        if (str == null || str.isBlank()) {
            return OrderStatus.PENDING;
        }
        String s = str.trim().toUpperCase();
        switch (s) {
            case "PAID":
            case "1":
                return OrderStatus.APPROVED;
            case "99":
            case "PENDING":
                return OrderStatus.PENDING;
            case "2":
            case "UNDERPAID":
                return OrderStatus.UNDERPAID;
            case "3":
            case "4":
            case "100":
            case "FAILED":
                return OrderStatus.FAILED;
            case "EXPIRED":
                return OrderStatus.EXPIRED;
            case "CANCELLED":
                return OrderStatus.CANCELLED;
            case "COMPLETED":
                return OrderStatus.COMPLETED;
            case "REWARDED":
                return OrderStatus.REWARDED;
            case "VERIFIED":
                return OrderStatus.VERIFIED;
            case "PAYMENT_DETECTED":
                return OrderStatus.PAYMENT_DETECTED;
            case "CREATED":
                return OrderStatus.CREATED;
            default:
                try {
                    return OrderStatus.valueOf(s);
                } catch (IllegalArgumentException e) {
                    return OrderStatus.PENDING;
                }
        }
    }
}
