package com.naptien.compat.version;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.List;

/**
 * VersionAdapter — Interface chuẩn cho từng phiên bản Minecraft.
 * Tuân thủ Quy tắc 17: Mỗi chức năng/phiên bản một Adapter riêng biệt.
 */
public interface VersionAdapter {

    void setItemNameAndLore(ItemStack stack, String name, List<String> lore);

    void setInvoiceId(ItemStack stack, String invoiceId);

    String getInvoiceId(ItemStack stack);

    MapItemSavedData getMapSavedData(ItemStack mapItem, ServerLevel world);

    void lockMap(MapItemSavedData state);
}
