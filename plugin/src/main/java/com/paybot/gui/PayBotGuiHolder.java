package com.paybot.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * PayBotGuiHolder — v5.5.7 (Rule 17 Compliance)
 * <p>
 * Định danh duy nhất và an toàn cho tất cả Inventory thuộc quyền quản lý của PayBot.
 * Khắc phục hoàn toàn lỗ hổng bypass / desync dựa trên raw title string trong Bukkit/Paper/Purpur.
 */
public class PayBotGuiHolder implements InventoryHolder {

    public enum GuiType {
        NAP_BANK,
        NAP_THE_TELCO,
        NAP_THE_DENOM,
        CHINH_SUA,
        TOPUP_LIST,
        PLACEHOLDER_MAIN,
        PLACEHOLDER_LEADERBOARD,
        OTHER
    }

    private final GuiType type;
    private final String extraData; // Telco name, invoiceId, hoặc trang số
    private Inventory inventory;

    public PayBotGuiHolder(GuiType type) {
        this(type, "");
    }

    public PayBotGuiHolder(GuiType type, String extraData) {
        this.type = type != null ? type : GuiType.OTHER;
        this.extraData = extraData != null ? extraData : "";
    }

    public GuiType getType() {
        return type;
    }

    public String getExtraData() {
        return extraData;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
