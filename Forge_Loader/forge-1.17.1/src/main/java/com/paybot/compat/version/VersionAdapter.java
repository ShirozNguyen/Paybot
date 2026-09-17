// v5.5.5 Part 89: Tuong thich Minecraft 1.17.1 cho Forge
package com.paybot.compat.version;

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
