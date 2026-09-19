package com.paybot.managers;

import java.util.EnumSet;
import java.util.Set;

/**
 * OrderStateMachine — v5.5.8 Part 122 (Tuân thủ Rule 17)
 * <p>
 * Quản lý trạng thái và chu trình chuyển đổi trạng thái của đơn hàng thanh toán
 * theo chuẩn Mục 18 & 19 của Master Technical Specification:
 * PENDING -> PAYMENT_CONFIRMED -> REWARD_PENDING -> REWARD_PROCESSING -> REWARD_DONE.
 * Các trạng thái ngoại lệ: UNDERPAID, EXPIRED, CANCELLED, REWARD_FAILED, FAILED.
 */
public final class OrderStateMachine {

    public enum OrderStatus {
        CREATED,
        PENDING,
        PAYMENT_DETECTED,
        VERIFIED,
        PAYMENT_CONFIRMED,
        REWARD_PENDING,
        REWARD_PROCESSING,
        REWARD_DONE,
        REWARD_FAILED,
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
            return this == COMPLETED || this == REWARD_DONE || this == EXPIRED || this == CANCELLED || this == FAILED;
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
                return next == OrderStatus.PAYMENT_DETECTED || next == OrderStatus.PAYMENT_CONFIRMED
                        || next == OrderStatus.UNDERPAID || next == OrderStatus.APPROVED
                        || next == OrderStatus.EXPIRED || next == OrderStatus.CANCELLED;
            case PAYMENT_DETECTED:
                return next == OrderStatus.VERIFIED || next == OrderStatus.PAYMENT_CONFIRMED
                        || next == OrderStatus.UNDERPAID || next == OrderStatus.FAILED;
            case VERIFIED:
                return next == OrderStatus.PAYMENT_CONFIRMED || next == OrderStatus.APPROVED
                        || next == OrderStatus.FAILED;
            case PAYMENT_CONFIRMED:
                return next == OrderStatus.REWARD_PENDING || next == OrderStatus.REWARD_PROCESSING
                        || next == OrderStatus.APPROVED || next == OrderStatus.FAILED;
            case REWARD_PENDING:
                return next == OrderStatus.REWARD_PROCESSING || next == OrderStatus.REWARD_FAILED;
            case REWARD_PROCESSING:
                return next == OrderStatus.REWARD_DONE || next == OrderStatus.REWARDED
                        || next == OrderStatus.COMPLETED || next == OrderStatus.APPROVED
                        || next == OrderStatus.REWARD_FAILED || next == OrderStatus.REWARD_PENDING;
            case REWARD_FAILED:
                return next == OrderStatus.REWARD_PENDING || next == OrderStatus.REWARD_PROCESSING
                        || next == OrderStatus.CANCELLED || next == OrderStatus.FAILED;
            case APPROVED:
                return next == OrderStatus.REWARD_PENDING || next == OrderStatus.REWARD_PROCESSING
                        || next == OrderStatus.REWARDED || next == OrderStatus.COMPLETED || next == OrderStatus.REWARD_DONE;
            case REWARDED:
            case REWARD_DONE:
                return next == OrderStatus.COMPLETED;
            case UNDERPAID:
                return next == OrderStatus.PAYMENT_CONFIRMED || next == OrderStatus.APPROVED || next == OrderStatus.CANCELLED;
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
                return OrderStatus.PAYMENT_CONFIRMED;
            case "APPROVED":
                return OrderStatus.APPROVED;
            case "REWARD_DONE":
                return OrderStatus.REWARD_DONE;
            case "REWARD_PROCESSING":
                return OrderStatus.REWARD_PROCESSING;
            case "REWARD_PENDING":
                return OrderStatus.REWARD_PENDING;
            case "REWARD_FAILED":
                return OrderStatus.REWARD_FAILED;
            case "PAYMENT_CONFIRMED":
                return OrderStatus.PAYMENT_CONFIRMED;
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
