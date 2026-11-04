package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import com.paybot.managers.LocalOrderManager;
import com.paybot.managers.LocalOrderManager.BankOrder;
import com.paybot.managers.LocalOrderManager.CardOrder;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * TransactionQueryService — v7.0.2 (Part 186)
 * TUÂN THỦ RULE 17: Class đơn nhiệm phụ trách truy vấn, lọc và chuẩn hóa dữ liệu đơn nạp (Bank & Card).
 * <p>
 * Tính năng:
 * 1. Hợp nhất dữ liệu đơn nạp Bank và Card từ LocalOrderManager & DatabaseManager.
 * 2. Chuẩn hóa trạng thái về 3 nhóm: PENDING (Chờ duyệt), SUCCESS (Thành công), FAILED (Thất bại).
 * 3. Sắp xếp đơn mới nhất lên đầu (createdAt giảm dần).
 * 4. Hỗ trợ phân trang mượt mà (21 items / trang).
 */
public final class TransactionQueryService {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private TransactionQueryService() {}

    public enum NormalizedStatus {
        PENDING,
        SUCCESS,
        FAILED
    }

    public enum OrderType {
        BANK,
        CARD
    }

    public static class UnifiedOrder {
        private final String orderId;
        private final String playerName;
        private final OrderType type;
        private final int amount;
        private final NormalizedStatus normalizedStatus;
        private final String rawStatus;
        private final long createdAt;
        private final String detail;

        public UnifiedOrder(String orderId, String playerName, OrderType type, int amount,
                            NormalizedStatus normalizedStatus, String rawStatus, long createdAt, String detail) {
            this.orderId = orderId != null ? orderId : "";
            this.playerName = playerName != null ? playerName : "Unknown";
            this.type = type;
            this.amount = amount;
            this.normalizedStatus = normalizedStatus;
            this.rawStatus = rawStatus != null ? rawStatus : "";
            this.createdAt = createdAt;
            this.detail = detail != null ? detail : "";
        }

        public String getOrderId() { return orderId; }
        public String getPlayerName() { return playerName; }
        public OrderType getType() { return type; }
        public int getAmount() { return amount; }
        public NormalizedStatus getNormalizedStatus() { return normalizedStatus; }
        public String getRawStatus() { return rawStatus; }
        public long getCreatedAt() { return createdAt; }
        public String getDetail() { return detail; }

        public String getFormattedDate() {
            synchronized (DATE_FORMAT) {
                return DATE_FORMAT.format(new Date(createdAt));
            }
        }
    }

    public static class TransactionQueryResult {
        private final List<UnifiedOrder> orders;
        private final int totalOrders;
        private final int totalPages;
        private final int currentPage;

        public TransactionQueryResult(List<UnifiedOrder> orders, int totalOrders, int totalPages, int currentPage) {
            this.orders = orders != null ? orders : Collections.emptyList();
            this.totalOrders = totalOrders;
            this.totalPages = totalPages;
            this.currentPage = currentPage;
        }

        public List<UnifiedOrder> getOrders() { return orders; }
        public int getTotalOrders() { return totalOrders; }
        public int getTotalPages() { return totalPages; }
        public int getCurrentPage() { return currentPage; }
    }

    /**
     * Truy vấn và lọc danh sách đơn hàng hợp nhất.
     *
     * @param plugin       instance plugin
     * @param statusFilter ALL, PENDING, SUCCESS, FAILED
     * @param page         trang hiện tại (0-indexed)
     * @param pageSize     số lượng đơn mỗi trang (thường là 21)
     * @return TransactionQueryResult
     */
    public static TransactionQueryResult queryOrders(PayBotPlugin plugin, String statusFilter, int page, int pageSize) {
        if (plugin == null || plugin.getLocalOrderManager() == null) {
            return new TransactionQueryResult(Collections.emptyList(), 0, 0, 0);
        }

        LocalOrderManager lom = plugin.getLocalOrderManager();
        List<UnifiedOrder> all = new ArrayList<>();

        // 1. Thu thập Bank Orders
        for (BankOrder bo : lom.getAllBankOrders()) {
            NormalizedStatus norm = normalizeBankStatus(bo.status);
            if (matchesFilter(norm, statusFilter)) {
                all.add(new UnifiedOrder(
                        bo.invoiceId,
                        bo.playerName,
                        OrderType.BANK,
                        bo.amount,
                        norm,
                        bo.status,
                        bo.createdAt,
                        "Nạp Ngân Hàng (VietQR)"
                ));
            }
        }

        // 2. Thu thập Card Orders
        for (CardOrder co : lom.getAllCardOrders()) {
            NormalizedStatus norm = normalizeCardStatus(co.status);
            if (matchesFilter(norm, statusFilter)) {
                String detail = (co.telco != null ? co.telco : "Thẻ") + " | Mã: " + maskSensitive(co.cardCode) + " | Seri: " + maskSensitive(co.cardSerial);
                all.add(new UnifiedOrder(
                        co.requestId,
                        co.playerName,
                        OrderType.CARD,
                        co.denom,
                        norm,
                        co.status,
                        co.createdAt,
                        detail
                ));
            }
        }

        // Sắp xếp đơn mới nhất lên đầu
        all.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));

        int totalOrders = all.size();
        int safePageSize = Math.max(1, pageSize);
        int totalPages = (int) Math.ceil((double) totalOrders / safePageSize);
        int safePage = Math.max(0, Math.min(page, Math.max(0, totalPages - 1)));

        int startIndex = safePage * safePageSize;
        int endIndex = Math.min(startIndex + safePageSize, totalOrders);

        List<UnifiedOrder> pageOrders = (startIndex < totalOrders) ? all.subList(startIndex, endIndex) : Collections.emptyList();

        return new TransactionQueryResult(new ArrayList<>(pageOrders), totalOrders, totalPages, safePage);
    }

    public static NormalizedStatus normalizeBankStatus(String rawStatus) {
        if (rawStatus == null) return NormalizedStatus.PENDING;
        String s = rawStatus.toUpperCase();
        if (s.contains("APPROVED") || s.contains("DONE")) {
            return NormalizedStatus.SUCCESS;
        }
        if (s.contains("EXPIRED") || s.contains("UNDERPAID") || s.contains("FAILED")) {
            return NormalizedStatus.FAILED;
        }
        return NormalizedStatus.PENDING;
    }

    public static NormalizedStatus normalizeCardStatus(String rawStatus) {
        if (rawStatus == null) return NormalizedStatus.PENDING;
        String s = rawStatus.trim();
        if ("1".equals(s) || "APPROVED".equalsIgnoreCase(s) || "DONE".equalsIgnoreCase(s)) {
            return NormalizedStatus.SUCCESS;
        }
        if ("2".equals(s) || "3".equals(s) || "4".equals(s) || "100".equals(s) || "FAILED".equalsIgnoreCase(s)) {
            return NormalizedStatus.FAILED;
        }
        return NormalizedStatus.PENDING;
    }

    private static boolean matchesFilter(NormalizedStatus status, String filter) {
        if (filter == null || "ALL".equalsIgnoreCase(filter)) return true;
        return status.name().equalsIgnoreCase(filter);
    }

    private static String maskSensitive(String input) {
        if (input == null || input.isEmpty()) return "***";
        if (input.length() <= 4) return "****";
        return input.substring(0, 2) + "****" + input.substring(input.length() - 2);
    }
}
