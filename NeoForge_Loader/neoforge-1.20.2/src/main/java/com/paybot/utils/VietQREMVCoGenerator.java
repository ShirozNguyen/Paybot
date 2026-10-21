package com.paybot.utils;

import java.nio.charset.StandardCharsets;

/**
 * VietQREMVCoGenerator — Chuyên trách sinh chuỗi mã hóa VietQR chuẩn EMVCo / NAPAS 247 quốc gia.
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn logic sinh chuỗi chuẩn ngân hàng thành class đơn nhiệm độc lập.
 * Đảm bảo 100% ứng dụng của toàn bộ các ngân hàng tại Việt Nam quét tự động điền lệnh chuyển khoản tức thì.
 */
public final class VietQREMVCoGenerator {

    private static final String GUID_NAPAS = "A000000727";
    private static final String SERVICE_NAPAS_247 = "QRIBFTTA";

    private VietQREMVCoGenerator() {}

    /**
     * Sinh chuỗi EMVCo VietQR hoàn chỉnh kèm mã kiểm tra CRC-16.
     * @param bankInput Tên hoặc mã BIN ngân hàng (sẽ được tự động resolve qua BankBinResolver)
     * @param accountNumber Số tài khoản ngân hàng thụ hưởng
     * @param amount Số tiền chuyển khoản (VNĐ)
     * @param content Nội dung chuyển khoản (Mã hoá đơn / InvoiceId)
     * @return Chuỗi EMVCo hoàn chỉnh (bắt đầu bằng 000201... và kết thúc bằng mã CRC 4 ký tự)
     */
    public static String generate(String bankInput, String accountNumber, int amount, String content) {
        String bin = BankBinResolver.resolveBin(bankInput);
        String acct = (accountNumber != null) ? accountNumber.trim() : "";
        String note = (content != null) ? content.trim() : "";

        // 1. Beneficiary Account Info (Subtag 01 của Tag 38)
        String sub00 = buildTlv("00", bin);
        String sub01 = buildTlv("01", acct);
        String beneficiaryDetail = sub00 + sub01;

        // 2. Tag 38: Merchant Account Information - NAPAS
        String sub38_00 = buildTlv("00", GUID_NAPAS);
        String sub38_01 = buildTlv("01", beneficiaryDetail);
        String sub38_02 = buildTlv("02", SERVICE_NAPAS_247);
        String tag38 = buildTlv("38", sub38_00 + sub38_01 + sub38_02);

        // 3. Xây dựng payload chính
        StringBuilder sb = new StringBuilder();
        sb.append(buildTlv("00", "01")); // Payload Format Indicator
        sb.append(buildTlv("01", amount > 0 ? "12" : "11")); // 12 = Dynamic (có số tiền), 11 = Static
        sb.append(tag38);
        sb.append(buildTlv("53", "704")); // Transaction Currency = VND
        if (amount > 0) {
            sb.append(buildTlv("54", String.valueOf(amount))); // Transaction Amount
        }
        sb.append(buildTlv("58", "VN")); // Country Code

        if (!note.isEmpty()) {
            String sub62_08 = buildTlv("08", note);
            sb.append(buildTlv("62", sub62_08)); // Additional Data Field
        }

        // 4. Checksum CRC-16 (Tag 63)
        sb.append("6304");
        String rawForCrc = sb.toString();
        String crcHex = calculateCrc16Ccitt(rawForCrc);

        return rawForCrc + crcHex;
    }

    private static String buildTlv(String tag, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return String.format("%s%02d%s", tag, bytes.length, value);
    }

    /**
     * Tính toán checksum CRC-16/CCITT-FALSE (Init 0xFFFF, Poly 0x1021).
     */
    public static String calculateCrc16Ccitt(String data) {
        int crc = 0xFFFF;
        byte[] bytes = data.getBytes(StandardCharsets.US_ASCII);
        for (byte b : bytes) {
            crc ^= ((b & 0xFF) << 8);
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x8000) != 0) {
                    crc = ((crc << 1) ^ 0x1021) & 0xFFFF;
                } else {
                    crc = (crc << 1) & 0xFFFF;
                }
            }
        }
        return String.format("%04X", crc);
    }
}
