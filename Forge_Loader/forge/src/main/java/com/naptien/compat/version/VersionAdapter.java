package com.naptien.compat.version;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import java.util.List;

public interface VersionAdapter {
    void setItemNameAndLore(ItemStack stack, String name, List<String> lore);
    void setInvoiceId(ItemStack stack, String invoiceId);
    String getInvoiceId(ItemStack stack);
    void lockMap(MapItemSavedData state);
    MapItemSavedData getMapSavedData(ItemStack mapItem, ServerLevel world);
}
