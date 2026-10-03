// v5.5.16 Part 138: Fix safeComponentToJson and hoverName reflection on production runtime
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
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;

/**
 * LegacyItemTagHelper — Quản lý an toàn Item Name, Lore và NBT Data cho Minecraft < 1.20.5 (NBT Era).
 * Hỗ trợ đa hệ thống mapping: Mojmap, Fabric Intermediary, Fabric Yarn, Forge SRG, Forge MCP.
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn thành 1 class đơn nhiệm độc lập.
 */
public final class LegacyItemTagHelper {

    private static final Set<String> HOVER_NAME_METHODS = Set.of(
            "setHoverName", "method_7977", "setCustomName",
            "m_41714_", "m_41784_", "m_41794_", "func_200302_a", "func_200292_b"
    );

    private static final Set<String> GET_OR_CREATE_TAG_METHODS = Set.of(
            "getOrCreateTag", "method_7948", "getOrCreateNbt", "m_41784_", "func_196082_o"
    );

    private static final Set<String> GET_TAG_METHODS = Set.of(
            "getTag", "method_7969", "getNbt", "m_41783_", "func_77978_p"
    );

    private static final Set<String> SET_TAG_METHODS = Set.of(
            "setTag", "method_7980", "setNbt", "m_41751_", "func_77982_d"
    );

    private static final Set<String> GET_SUB_TAG_METHODS = Set.of(
            "getOrCreateTagElement", "method_7967", "getOrCreateSubNbt", "m_41737_", "func_196084_a"
    );

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
        boolean setHoverNameSuccess = false;
        try {
            for (Method m : stack.getClass().getMethods()) {
                if (HOVER_NAME_METHODS.contains(m.getName()) && m.getParameterCount() == 1) {
                    m.invoke(stack, nameComponent);
                    setHoverNameSuccess = true;
                    break;
                }
            }
            if (!setHoverNameSuccess) {
                // Fallback duyệt theo kiểu tham số Component
                for (Method m : stack.getClass().getMethods()) {
                    if (m.getParameterCount() == 1
                            && m.getParameterTypes()[0].isAssignableFrom(nameComponent.getClass())) {
                        m.invoke(stack, nameComponent);
                        setHoverNameSuccess = true;
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("LegacyItemTagHelper.setName: setHoverName() lỗi", t);
        }
        if (!setHoverNameSuccess) {
            try {
                CompoundTag displayTag = getOrCreateDisplayTag(stack);
                if (displayTag != null) {
                    displayTag.putString("Name", safeComponentToJson(nameComponent));
                }
            } catch (Throwable t) {
                PayBotDebug.logSwallowed("LegacyItemTagHelper.setName: set display.Name lỗi", t);
            }
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

    // Pure reflection — hỗ trợ đầy đủ 5 hệ mapping
    private static CompoundTag getOrCreateTag(ItemStack stack) {
        try {
            for (Method m : stack.getClass().getMethods()) {
                if (GET_OR_CREATE_TAG_METHODS.contains(m.getName()) && m.getParameterCount() == 0) {
                    Object result = m.invoke(stack);
                    if (result instanceof CompoundTag) return (CompoundTag) result;
                    if (result != null) return unwrapOptionalCompoundTag(result);
                }
            }
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

    // Pure reflection — hỗ trợ đầy đủ 5 hệ mapping
    private static CompoundTag getTag(ItemStack stack) {
        try {
            for (Method m : stack.getClass().getMethods()) {
                if (GET_TAG_METHODS.contains(m.getName()) && m.getParameterCount() == 0) {
                    Object result = m.invoke(stack);
                    if (result instanceof CompoundTag) return (CompoundTag) result;
                    if (result != null) return unwrapOptionalCompoundTag(result);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static CompoundTag getOrCreateDisplayTag(ItemStack stack) {
        CompoundTag root = getOrCreateTag(stack);
        if (root == null) return null;
        try {
            CompoundTag display = null;
            try {
                for (Method m : stack.getClass().getMethods()) {
                    if (GET_SUB_TAG_METHODS.contains(m.getName())
                            && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == String.class) {
                        display = (CompoundTag) m.invoke(stack, "display");
                        break;
                    }
                }
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
            // 1. Thử duyệt inner classes của Component.class để tìm method toJson(Component)
            for (Class<?> inner : Component.class.getClasses()) {
                String res = invokeToJsonMethod(inner, comp);
                if (res != null) return res;
            }
            for (Class<?> inner : Component.class.getDeclaredClasses()) {
                String res = invokeToJsonMethod(inner, comp);
                if (res != null) return res;
            }

            // 2. Thử các candidate class names cho từng loader/mapping
            String[] candidateClassNames = {
                "net.minecraft.network.chat.Component$Serializer",
                "net.minecraft.class_2561$class_2562",
                "net.minecraft.class_2561$class_2563",
                "net.minecraft.util.text.ITextComponent$Serializer"
            };
            for (String clsName : candidateClassNames) {
                try {
                    Class<?> cls = Class.forName(clsName);
                    String res = invokeToJsonMethod(cls, comp);
                    if (res != null) return res;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("LegacyItemTagHelper.safeComponentToJson", t);
        }

        // 3. Fallback JsonObject an toàn
        JsonObject obj = new JsonObject();
        obj.addProperty("text", comp.getString());
        return obj.toString();
    }

    private static final Set<String> TO_JSON_METHODS = Set.of(
            "toJson", "method_10867", "func_150696_a", "m_130703_"
    );

    private static String invokeToJsonMethod(Class<?> cls, Component comp) {
        if (cls == null || comp == null) return null;
        for (Method m : cls.getMethods()) {
            if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1) {
                boolean nameMatch = TO_JSON_METHODS.contains(m.getName()) || m.getName().toLowerCase().contains("json");
                boolean typeMatch = m.getReturnType().equals(String.class);
                if (nameMatch || typeMatch) {
                    Class<?> p0 = m.getParameterTypes()[0];
                    if (Component.class.isAssignableFrom(p0)
                            || p0.isAssignableFrom(comp.getClass())
                            || p0.getName().contains("class_2561")
                            || p0.getName().contains("ITextComponent")
                            || p0.getName().contains("Component")) {
                        try {
                            m.setAccessible(true);
                            Object res = m.invoke(null, comp);
                            if (res != null && !res.toString().isEmpty()) return res.toString();
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }
        return null;
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
                if (SET_TAG_METHODS.contains(m.getName())
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
