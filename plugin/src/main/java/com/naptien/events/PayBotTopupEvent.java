package com.naptien.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Event bắn ra khi người chơi nạp tiền thành công (thẻ cào hoặc ngân hàng).
 * Hỗ trợ cho các plugin khác (như PayBot++) hook trực tiếp nhận dữ liệu nạp.
 */
public class PayBotTopupEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String playerName;
    private final long amountVnd;
    private final String type; // "CARD" hoặc "BANK"
    private final String invoiceId;

    public PayBotTopupEvent(String playerName, long amountVnd, String type, String invoiceId) {
        this.playerName = playerName;
        this.amountVnd = amountVnd;
        this.type = type;
        this.invoiceId = invoiceId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public long getAmountVnd() {
        return amountVnd;
    }

    public String getType() {
        return type;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
