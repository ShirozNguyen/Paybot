package com.paybot.commands;

import com.paybot.PayBotPlugin;

/**
 * DynamicPaymentCommandRegistry — Quản lý bật/tắt động trong runtime O(1) của các lệnh thanh toán /napbank và /napthe.
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn chức năng điều phối trạng thái lệnh thành 1 class đơn nhiệm độc lập.
 */
public final class DynamicPaymentCommandRegistry {

    private static final DynamicPaymentCommandRegistry INSTANCE = new DynamicPaymentCommandRegistry();

    private volatile boolean bankEnabled = true;
    private volatile boolean cardEnabled = true;

    private DynamicPaymentCommandRegistry() {}

    public static DynamicPaymentCommandRegistry getInstance() {
        return INSTANCE;
    }

    public void init(PayBotPlugin plugin) {
        if (plugin != null && plugin.getConfig() != null) {
            this.bankEnabled = plugin.getConfig().getBoolean("sepay.enabled", true);
            this.cardEnabled = plugin.getConfig().getBoolean("card.enabled", true);
        }
    }

    public boolean isBankEnabled() {
        return bankEnabled;
    }

    public void setBankEnabled(boolean bankEnabled) {
        this.bankEnabled = bankEnabled;
    }

    public boolean isCardEnabled() {
        return cardEnabled;
    }

    public void setCardEnabled(boolean cardEnabled) {
        this.cardEnabled = cardEnabled;
    }
}
