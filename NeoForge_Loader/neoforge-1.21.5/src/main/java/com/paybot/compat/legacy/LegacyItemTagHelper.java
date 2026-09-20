package com.paybot.compat.legacy;

import com.google.gson.JsonObject;
import com.paybot.utils.ComponentColorParser;
import com.paybot.utils.PayBotDebug;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.List;

/**
 * LegacyItemTagHelper — Quản lý an toàn Item Name, Lore và NBT Data cho Minecraft < 1.20.5 (NBT Era).
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn thành 1 class đơn nhiệm độc lập.
 */
public final class LegacyItemTagHelper {

    private LegacyItemTagHelper() {}

    public static void setItemNameAndLore(ItemStack stack, String name, List<String> lore) {
        if (stack == null || stack.isEmpty()) return;
        if (name != null && !name.isEmpty()) {
            setName(stack, ComponentColorParser.parse(name));
        }
        if (lore != null && !lore.isEmpty()) {
            setLoreStrings(stack, lore);
        }
    }

    public static void setName(ItemStack stack, Component nameComponent) {
        if (stack == null || stack.isEmpty() || nameComponent == null) return;
        try {
            stack.setHoverName(nameComponent);
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("LegacyItemTagHelper.setName: setHoverName() lỗi", t);
        }
        try {
            CompoundTag displayTag = getOrCreateDisplayTag(stack);
            if (displayTag != null) {
                displayTag.putString("Name", safeComponentToJson(nameComponent));
            }
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("LegacyItemTagHelper.setName: set display.Name lỗi", t);
        }
    }

    public static void setLoreStrings(ItemStack stack, List<String> lore) {
        if (stack == null || stack.isEmpty() || lore == null || lore.isEmpty()) return;
        CompoundTag displayTag = getOrCreateDisplayTag(stack);
        if (displayTag == null) return;
        try {
            ListTag loreTag = new ListTag();
            for (String line : lore) {
                if (line == null) continue;
                Component lineComp = ComponentColorParser.parse(line);
                loreTag.add(StringTag.valueOf(safeComponentToJson(lineComp)));
            }
            displayTag.put("Lore", loreTag);
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("LegacyItemTagHelper.setLoreStrings: lỗi ghi Lore NBT", t);
        }
    }

    public static void setLoreComponents(ItemStack stack, List<Component> componentList) {
        if (stack == null || stack.isEmpty() || componentList == null || componentList.isEmpty()) return;
        CompoundTag displayTag = getOrCreateDisplayTag(stack);
        if (displayTag == null) return;
        try {
            ListTag loreTag = new ListTag();
            for (Component comp : componentList) {
                if (comp == null) continue;
                loreTag.add(StringTag.valueOf(safeComponentToJson(comp)));
            }
            displayTag.put("Lore", loreTag);
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("LegacyItemTagHelper.setLoreComponents: lỗi ghi Lore NBT", t);
        }
    }

    public static void setInvoiceId(ItemStack stack, String invoiceId) {
        if (stack == null || stack.isEmpty() || invoiceId == null) return;
        try {
            CompoundTag tag = getOrCreateTag(stack);
            if (tag != null) tag.putString("paybot_invoice_id", invoiceId);
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("LegacyItemTagHelper.setInvoiceId lỗi", t);
        }
    }

    public static String getInvoiceId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        try {
            CompoundTag tag = getTag(stack);
            if (tag != null && com.paybot.utils.TagCompatHelper.contains(tag, "paybot_invoice_id")) {
                return com.paybot.utils.TagCompatHelper.getString(tag, "paybot_invoice_id");
            }
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("LegacyItemTagHelper.getInvoiceId lỗi", t);
        }
        return null;
    }

    private static CompoundTag getOrCreateTag(ItemStack stack) {
        try {
            Method m = stack.getClass().getMethod("getOrCreateTag");
            return (CompoundTag) m.invoke(stack);
        } catch (Throwable ignored) {}
        try {
            return stack.getOrCreateTag();
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("LegacyItemTagHelper.getOrCreateTag", t);
            return null;
        }
    }

    private static CompoundTag getTag(ItemStack stack) {
        try {
            Method m = stack.getClass().getMethod("getTag");
            return (CompoundTag) m.invoke(stack);
        } catch (Throwable ignored) {}
        try {
            return stack.getTag();
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("LegacyItemTagHelper.getTag", t);
            return null;
        }
    }

    public static CompoundTag getOrCreateDisplayTag(ItemStack stack) {
        CompoundTag root = getOrCreateTag(stack);
        if (root == null) return null;
        try {
            CompoundTag display = null;
            try {
                display = (CompoundTag) stack.getClass().getMethod("getOrCreateTagElement", String.class).invoke(stack, "display");
            } catch (Throwable ignored) {}
            if (display == null) {
                display = root.getCompound("display");
                root.put("display", display);
            }
            return display;
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("LegacyItemTagHelper.getOrCreateDisplayTag", t);
            return null;
        }
    }

    public static String safeComponentToJson(Component comp) {
        if (comp == null) return "{\"text\":\"\"}";
        try {
            return Component.Serializer.toJson(comp);
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("LegacyItemTagHelper.safeComponentToJson", t);
            JsonObject obj = new JsonObject();
            obj.addProperty("text", comp.getString());
            return obj.toString();
        }
    }
}
