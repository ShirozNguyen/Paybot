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
            boolean _shnCalled = false;
            for (java.lang.reflect.Method _shnM : stack.getClass().getMethods()) {
                String _shnN = _shnM.getName();
                if ((_shnN.equals("setHoverName") || _shnN.equals("m_41794_") || _shnN.equals("func_200292_b"))
                        && _shnM.getParameterCount() == 1) {
                    _shnM.invoke(stack, nameComponent);
                    _shnCalled = true;
                    break;
                }
            }
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

    // Pure reflection — no direct fallback (direct call fails compile on MC 1.20.5+)
    private static CompoundTag getOrCreateTag(ItemStack stack) {
        try {
            Method m = stack.getClass().getMethod("getOrCreateTag");
            Object result = m.invoke(stack);
            if (result instanceof CompoundTag) return (CompoundTag) result;
            if (result != null) return unwrapOptionalCompoundTag(result);
        } catch (Throwable ignored) {}
        try {
            CompoundTag tag = new CompoundTag();
            setTagReflect(stack, tag);
            return tag;
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("LegacyItemTagHelper.getOrCreateTag fallback", t);
            return null;
        }
    }

    // Pure reflection — no direct fallback (direct call fails compile on MC 1.20.5+)
    private static CompoundTag getTag(ItemStack stack) {
        try {
            Method m = stack.getClass().getMethod("getTag");
            Object result = m.invoke(stack);
            if (result instanceof CompoundTag) return (CompoundTag) result;
            if (result != null) return unwrapOptionalCompoundTag(result);
        } catch (Throwable ignored) {}
        return null;
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
                display = getCompoundTagSafe(root, "display");
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
            // Reflection — Component.Serializer.toJson signature changed in MC 1.21.x+
            Class<?> _serCls = Class.forName("net.minecraft.network.chat.Component$Serializer");
            for (java.lang.reflect.Method _serM : _serCls.getMethods()) {
                if (_serM.getName().equals("toJson")
                        && java.lang.reflect.Modifier.isStatic(_serM.getModifiers())
                        && _serM.getParameterCount() == 1
                        && _serM.getParameterTypes()[0].isAssignableFrom(comp.getClass())) {
                    Object _serR = _serM.invoke(null, comp);
                    if (_serR != null) return _serR.toString();
                }
            }
            throw new ReflectiveOperationException("Component.Serializer.toJson(Component) not found");
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("LegacyItemTagHelper.safeComponentToJson", t);
            JsonObject obj = new JsonObject();
            obj.addProperty("text", comp.getString());
            return obj.toString();
        }
    }
    // ─── Pure-reflection helpers (compile-safe on any MC version) ─────────────────

    /** Unwrap Optional<CompoundTag> — MC 26.x changed getTag/getCompound return type */
    private static CompoundTag unwrapOptionalCompoundTag(Object optionalObj) {
        try {
            Method orElse = optionalObj.getClass().getMethod("orElse", Object.class);
            Object val = orElse.invoke(optionalObj, (Object) null);
            if (val instanceof CompoundTag) return (CompoundTag) val;
        } catch (Throwable ignored) {}
        return null;
    }

    /** setTag(CompoundTag) via reflection — removed in MC 1.20.5+ DataComponents era */
    private static void setTagReflect(ItemStack stack, CompoundTag tag) {
        try {
            for (Method m : stack.getClass().getMethods()) {
                String n = m.getName();
                if ((n.equals("setTag") || n.equals("m_41751_") || n.equals("func_77982_d"))
                        && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isAssignableFrom(CompoundTag.class)) {
                    m.invoke(stack, tag);
                    return;
                }
            }
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("LegacyItemTagHelper.setTagReflect", t);
        }
    }

    /** Get CompoundTag subtag by key — handles Optional<CompoundTag> return in MC 26.x+ */
    private static CompoundTag getCompoundTagSafe(CompoundTag parent, String key) {
        try {
            Method m = parent.getClass().getMethod("getCompound", String.class);
            Object result = m.invoke(parent, key);
            if (result instanceof CompoundTag) return (CompoundTag) result;
            if (result != null) {
                CompoundTag unwrapped = unwrapOptionalCompoundTag(result);
                if (unwrapped != null) return unwrapped;
            }
        } catch (Throwable ignored) {}
        return new CompoundTag();
    }

}
