package com.naptien.compat;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.List;

/**
 * GuiBackend — Interface trừu tượng để quản lý GUI độc lập với phiên bản SGUI.
 * 
 * Giúp cô lập code GUI (như NapTheGui, NapBankGui...) khỏi việc import trực tiếp
 * các class của SGUI, cho phép hoán đổi implementation SGUI 1.x và SGUI 2.x tại runtime.
 */
public interface GuiBackend {

    /**
     * Mở GUI cho người chơi.
     */
    void open();

    /**
     * Đóng GUI của người chơi.
     */
    void close();

    /**
     * Thiết lập item và callback khi click tại slot chỉ định.
     * 
     * @param slot Vị trí slot (0-indexed)
     * @param item ItemStack hiển thị
     * @param name Tên hiển thị (đã dịch/format màu)
     * @param lore Dòng mô tả của item
     * @param onClick Hành động thực thi khi click vào slot này
     */
    void setSlot(int slot, ItemStack item, String name, List<String> lore, Runnable onClick);

    /**
     * Lấy kích thước của GUI (số lượng slot).
     */
    int getSize();

    /**
     * Điền kính xám (GRAY_STAINED_GLASS_PANE) vào tất cả các slot còn trống.
     */
    void fillGlass();

    /**
     * Interface callback nhận sự kiện click.
     */
    interface GuiSlotHandler {
        void onClick(int slot);
    }
}
