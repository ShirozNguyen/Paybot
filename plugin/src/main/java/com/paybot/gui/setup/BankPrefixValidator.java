package com.paybot.gui.setup;

import java.util.regex.Pattern;

/**
 * BankPrefixValidator — v6.1.0 (Part 160)
 * TUÂN THỦ RULE 17: Chức năng kiểm tra tính hợp lệ của Cú pháp mã nạp (Transfer Prefix).
 * <p>
 * QUY TẮC NGÂN HÀNG & SEPAY:
 * 1. Giữ nguyên chữ hoa và chữ thường theo đúng ý Admin nhập (không tự ép toUpperCase()).
 * 2. Ký tự hợp lệ DUY NHẤT: A-Z, a-z, 0-9 và dấu gạch dưới '_'.
 * 3. Nếu chứa ký tự đặc biệt khác hoặc dấu cách, từ chối kèm lý do chi tiết để thông báo trên chat riêng.
 */
public final class BankPrefixValidator {

    private static final Pattern VALID_PREFIX_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    private BankPrefixValidator() {}

    public static ValidationResult validate(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return new ValidationResult(false, "Cú pháp mã nạp không được để trống!");
        }

        String trimmed = prefix.trim();
        if (trimmed.length() < 2 || trimmed.length() > 16) {
            return new ValidationResult(false, "Độ dài mã nạp phải từ 2 đến 16 ký tự!");
        }

        if (trimmed.contains(" ")) {
            return new ValidationResult(false, "Mã nạp không được chứa khoảng trắng (dấu cách)!");
        }

        if (!VALID_PREFIX_PATTERN.matcher(trimmed).matches()) {
            return new ValidationResult(false, "Mã nạp chỉ được chứa ký tự A-Z, a-z, 0-9 và dấu gạch dưới '_'. Ngân hàng sẽ từ chối các ký tự đặc biệt khác!");
        }

        return new ValidationResult(true, null);
    }

    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        public ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
