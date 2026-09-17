package com.naptien.utils;

import com.naptien.compat.version.VersionAdapterFactory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * ItemTagCompat — Facade ủy quyền xử lý Item Name, Lore và NBT Data sang VersionAdapter tương ứng.
 * 
 * Tuân thủ Quy tắc 17 & Kiến trúc Version Package.
 */
public class ItemTagCompat {

    public static void setItemNameAndLore(ItemStack stack, String name, List<String> lore) {
        VersionAdapterFactory.getAdapter().setItemNameAndLore(stack, name, lore);
    }

    public static void setInvoiceId(ItemStack stack, String invoiceId) {
        VersionAdapterFactory.getAdapter().setInvoiceId(stack, invoiceId);
    }

    public static String getInvoiceId(ItemStack stack) {
        return VersionAdapterFactory.getAdapter().getInvoiceId(stack);
    }
}
