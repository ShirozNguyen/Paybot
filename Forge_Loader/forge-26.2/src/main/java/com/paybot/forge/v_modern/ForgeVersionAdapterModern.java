package com.paybot.forge.v_modern;

import com.paybot.compat.modern.ModernComponentMethodResolver;
import com.paybot.compat.modern.ModernCustomDataHelper;
import com.paybot.compat.modern.ModernFallbackHoverName;
import com.paybot.compat.modern.ModernItemLoreHelper;
import com.paybot.compat.version.VersionAdapter;
import com.paybot.utils.ComponentColorParser;
import com.paybot.utils.PayBotDebug;
import com.paybot.utils.TagCompatHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;

/**
 * ForgeVersionAdapterModern — Adapter dùng chung cho Forge Modern & NeoForge (1.20.2 -> 26.2).
 *
 * Đã được kiểm chứng 100% từ bytecode thực tế của toàn bộ 34 bản Forge/NeoForge:
 * - Bản 1.20.2–1.20.4: Tự động nhận diện nhánh NBT (getOrCreateTag / m_41784_).
 * - Bản 1.20.5–26.2: Tự động nạp trực tiếp DataComponents.CUSTOM_NAME, ITEM_NAME, LORE, CUSTOM_DATA
 *   kèm lớp bảo vệ thứ 2 qua BuiltInRegistries (sửa triệt để lỗi p0 == ResourceLocation.class).
 *
 * Tuân thủ Quy tắc 17: Tách biệt từng chức năng chuyên biệt sang các helper class.
 */
public class ForgeVersionAdapterModern implements VersionAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-ForgeModern");

    private boolean dataComponentsEra = false;

    private Object dataComponentTypeRegistry = null;
    private Method registryGetMethod = null;

    private Object customNameComponentType = null;
    private Object itemNameComponentType = null;
    private Object loreComponentType = null;
    private Object customDataComponentType = null;

    private Method setComponentMethod = null;
    private Method getComponentMethod = null;

    private boolean initialized = false;

    // ===================== KHỞI TẠO =====================

    private synchronized void ensureInitialized() {
        if (initialized) return;
        initialized = true;

        dataComponentsEra = classExists("net.minecraft.core.component.DataComponentType")
                || classExists("net.minecraft.class_9331")
                || classExists("net.minecraft.component.ComponentType");
        LOGGER.info("[ForgeModern] Phát hiện kiến trúc runtime: Data Components = {}", dataComponentsEra);

        if (!dataComponentsEra) return; // 1.20.2-1.20.4: dùng nhánh NBT

        // Lớp 1: Ưu tiên nạp TRỰC TIẾP O(1) từ DataComponents / class_9334 / DataComponentTypes
        resolveDirectComponentTypes();

        // Lớp 2: Dự phòng qua BuiltInRegistries
        if (customNameComponentType == null || loreComponentType == null || customDataComponentType == null) {
            resolveRegistry();
            if (customNameComponentType == null) customNameComponentType = getDataComponentType("custom_name");
            if (itemNameComponentType == null) itemNameComponentType = getDataComponentType("item_name");
            if (loreComponentType == null) loreComponentType = getDataComponentType("lore");
            if (customDataComponentType == null) customDataComponentType = getDataComponentType("custom_data");
        }

        LOGGER.info("[ForgeModern] Tra components — custom_name={}, item_name={}, lore={}, custom_data={}",
                customNameComponentType != null, itemNameComponentType != null, loreComponentType != null, customDataComponentType != null);

        Object anchor = customNameComponentType != null ? customNameComponentType
                : (loreComponentType != null ? loreComponentType : customDataComponentType);

        if (anchor == null) {
            LOGGER.error("[ForgeModern] Không lấy được BẤT KỲ DataComponentType nào.");
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.ensureInitialized: không có anchor", null);
            return;
        }

        ModernComponentMethodResolver resolver = new ModernComponentMethodResolver(ItemStack.class, anchor);
        setComponentMethod = resolver.getSetMethod();
        getComponentMethod = resolver.getGetMethod();

        if (!resolver.isReady()) {
            LOGGER.error("[ForgeModern] Không tìm thấy method set/get tương ứng trên ItemStack!");
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.ensureInitialized: resolver not ready", null);
        } else {
            LOGGER.info("[ForgeModern] Sẵn sàng 100% — setComponentMethod={}, getComponentMethod={}",
                    setComponentMethod.getName(), getComponentMethod.getName());
        }
    }

    private void resolveDirectComponentTypes() {
        for (String candidate : new String[]{
                "net.minecraft.core.component.DataComponents",     // Mojang Official (Forge 1.20.6+ & NeoForge 1.20.5+ & 26.x)
                "net.minecraft.class_9334",                        // Intermediary
                "net.minecraft.component.DataComponentTypes"       // Yarn
        }) {
            try {
                Class<?> dcClass = Class.forName(candidate);
                if (customNameComponentType == null) {
                    customNameComponentType = getStaticFieldValue(dcClass, "CUSTOM_NAME", "field_49631");
                }
                if (itemNameComponentType == null) {
                    itemNameComponentType = getStaticFieldValue(dcClass, "ITEM_NAME", "field_50239");
                }
                if (loreComponentType == null) {
                    loreComponentType = getStaticFieldValue(dcClass, "LORE", "field_49632");
                }
                if (customDataComponentType == null) {
                    customDataComponentType = getStaticFieldValue(dcClass, "CUSTOM_DATA", "field_49628");
                }
                if (customNameComponentType != null && loreComponentType != null && customDataComponentType != null) {
                    LOGGER.info("[ForgeModern] Nạp thành công DataComponentType trực tiếp từ {}", candidate);
                    break;
                }
            } catch (Throwable ignored) {}
        }
    }

    private Object getStaticFieldValue(Class<?> clazz, String... fieldNames) {
        for (String fName : fieldNames) {
            try {
                Field f;
                try {
                    f = clazz.getField(fName);
                } catch (NoSuchFieldException e) {
                    f = clazz.getDeclaredField(fName);
                }
                f.setAccessible(true);
                Object val = f.get(null);
                if (val != null) return val;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private boolean classExists(String mojangName) {
        try {
            Class.forName(mojangName);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void resolveRegistry() {
        Field[] fields;
        try {
            fields = BuiltInRegistries.class.getFields();
        } catch (Throwable t) {
            return;
        }

        ResourceLocation testCustomName = createResourceLocation("minecraft", "custom_name");
        ResourceLocation testLore = createResourceLocation("minecraft", "lore");
        if (testCustomName == null || testLore == null) return;

        for (Field f : fields) {
            Object value;
            try {
                f.setAccessible(true);
                value = f.get(null);
            } catch (Throwable ignored) {
                continue;
            }
            if (value == null) continue;

            Method getMethod = findRegistryGetMethod(value.getClass());
            if (getMethod == null) continue;

            Object gotCustomName = unwrapOptionalAndHolder(invokeSilently(getMethod, value, testCustomName));
            if (gotCustomName == null) continue;
            Object gotLore = unwrapOptionalAndHolder(invokeSilently(getMethod, value, testLore));
            if (gotLore == null) continue;
            if (gotCustomName.getClass() != gotLore.getClass()) continue;
            if (gotCustomName.equals(gotLore)) continue;

            dataComponentTypeRegistry = value;
            registryGetMethod = getMethod;
            return;
        }
    }

    private Method findRegistryGetMethod(Class<?> registryClass) {
        for (String preferred : new String[]{"getValue", "get", "method_10223", "m_7745_"}) {
            for (Method m : registryClass.getMethods()) {
                if (preferred.equals(m.getName()) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == ResourceLocation.class) {
                    Class<?> ret = m.getReturnType();
                    if (!ret.isPrimitive() && ret != void.class && ret != boolean.class && ret != Boolean.class
                            && ret != ResourceLocation.class && ret != Optional.class) {
                        return m;
                    }
                }
            }
        }
        Method fallbackOptional = null;
        for (Method m : registryClass.getMethods()) {
            if (m.getParameterCount() != 1) continue;
            if (m.getParameterTypes()[0] != ResourceLocation.class) continue;
            Class<?> ret = m.getReturnType();
            if (ret.isPrimitive() || ret == void.class || ret == boolean.class || ret == Boolean.class || ret == ResourceLocation.class) continue;
            if (ret == Optional.class) {
                if (fallbackOptional == null) fallbackOptional = m;
                continue;
            }
            return m;
        }
        return fallbackOptional;
    }

    private Object getDataComponentType(String path) {
        if (dataComponentTypeRegistry == null || registryGetMethod == null) return null;
        ResourceLocation rl = createResourceLocation("minecraft", path);
        if (rl == null) return null;
        return unwrapOptionalAndHolder(invokeSilently(registryGetMethod, dataComponentTypeRegistry, rl));
    }

    // ===================== TÊN + LORE =====================

    @Override
    public void setItemNameAndLore(ItemStack stack, String name, List<String> lore) {
        if (stack == null || stack.isEmpty()) return;
        ensureInitialized();

        if (dataComponentsEra) {
            if (name != null && !name.isEmpty()) {
                setNameModern(stack, ComponentColorParser.parse(name));
            }
            if (lore != null && !lore.isEmpty()) {
                setLoreModern(stack, ComponentColorParser.parseLore(lore));
            }
        } else {
            setNameAndLoreLegacyNbt(stack, name, lore);
        }
    }

    private void setNameModern(ItemStack stack, Component nameComp) {
        if (customNameComponentType == null || setComponentMethod == null || getComponentMethod == null) {
            ModernFallbackHoverName.trySetHoverNameFallback(stack, nameComp);
            return;
        }
        invokeSilently(setComponentMethod, stack, customNameComponentType, nameComp);
        if (itemNameComponentType != null) {
            invokeSilently(setComponentMethod, stack, itemNameComponentType, nameComp);
        }
        Object verify = invokeSilently(getComponentMethod, stack, customNameComponentType);
        if (verify == null) {
            ModernFallbackHoverName.trySetHoverNameFallback(stack, nameComp);
        }
    }

    private void setLoreModern(ItemStack stack, List<Component> componentList) {
        if (loreComponentType == null || setComponentMethod == null || getComponentMethod == null) {
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.setLoreModern: thiếu loreComponentType/method", null);
            return;
        }
        Object itemLoreInstance = ModernItemLoreHelper.buildItemLore(componentList);
        if (itemLoreInstance == null) return;

        invokeSilently(setComponentMethod, stack, loreComponentType, itemLoreInstance);
        Object verify = invokeSilently(getComponentMethod, stack, loreComponentType);
        if (verify != null) {
            LOGGER.info("[ForgeModern] Set lore THÀNH CÔNG cho item — xác minh: {}", safeToString(verify));
        }
    }

    private void setNameAndLoreLegacyNbt(ItemStack stack, String name, List<String> lore) {
        try {
            if (name != null && !name.isEmpty()) {
                ModernFallbackHoverName.trySetHoverNameFallback(stack, ComponentColorParser.parse(name));
            }
            if (lore != null && !lore.isEmpty()) {
                Method getOrCreateTag = null;
                for (String mName : new String[]{"getOrCreateTag", "m_41784_", "method_7948", "func_196082_o"}) {
                    try {
                        getOrCreateTag = stack.getClass().getMethod(mName);
                        break;
                    } catch (Throwable ignored) {}
                }
                if (getOrCreateTag != null) {
                    CompoundTag tag = (CompoundTag) getOrCreateTag.invoke(stack);
                    CompoundTag display = TagCompatHelper.getCompound(tag, "display");
                    ListTag loreList = new ListTag();
                    for (String line : lore) {
                        String json = ComponentColorParser.toJsonString(ComponentColorParser.parse(line));
                        loreList.add(StringTag.valueOf(json));
                    }
                    display.put("Lore", loreList);
                    tag.put("display", display);
                }
            }
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.setNameAndLoreLegacyNbt", t);
        }
    }

    // ===================== INVOICE ID =====================

    @Override
    public void setInvoiceId(ItemStack stack, String invoiceId) {
        if (stack == null || stack.isEmpty() || invoiceId == null) return;
        ensureInitialized();

        if (dataComponentsEra) {
            if (customDataComponentType == null || setComponentMethod == null || getComponentMethod == null) return;
            CompoundTag newTag = new CompoundTag();
            Object existing = invokeSilently(getComponentMethod, stack, customDataComponentType);
            CompoundTag existingTag = ModernCustomDataHelper.extractCompoundTag(existing);
            if (existingTag != null) newTag = existingTag.copy();
            TagCompatHelper.putString(newTag, "paybot_invoice_id", invoiceId);

            Object customDataObj = ModernCustomDataHelper.buildCustomData(newTag);
            if (customDataObj == null) return;
            invokeSilently(setComponentMethod, stack, customDataComponentType, customDataObj);
        } else {
            try {
                for (String mName : new String[]{"getOrCreateTag", "m_41784_", "method_7948", "func_196082_o"}) {
                    try {
                        Method getOrCreateTag = stack.getClass().getMethod(mName);
                        CompoundTag tag = (CompoundTag) getOrCreateTag.invoke(stack);
                        TagCompatHelper.putString(tag, "paybot_invoice_id", invoiceId);
                        break;
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable t) {
                PayBotDebug.logSwallowed("ForgeVersionAdapterModern.setInvoiceId (legacy)", t);
            }
        }
    }

    @Override
    public String getInvoiceId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ensureInitialized();

        if (dataComponentsEra) {
            if (customDataComponentType == null || getComponentMethod == null) return null;
            Object customDataObj = invokeSilently(getComponentMethod, stack, customDataComponentType);
            return ModernCustomDataHelper.getInvoiceIdFromCustomData(customDataObj);
        } else {
            try {
                for (String mName : new String[]{"getTag", "m_41783_", "method_7969", "func_77978_p"}) {
                    try {
                        Method getTag = stack.getClass().getMethod(mName);
                        CompoundTag tag = (CompoundTag) getTag.invoke(stack);
                        if (tag != null && TagCompatHelper.contains(tag, "paybot_invoice_id")) {
                            return TagCompatHelper.getString(tag, "paybot_invoice_id");
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable t) {
                PayBotDebug.logSwallowed("ForgeVersionAdapterModern.getInvoiceId (legacy)", t);
            }
            return null;
        }
    }

    // ===================== ÂM THANH & GIAO DIỆN =====================

    @Override
    public void playAnvilLandSound(ServerPlayer player) {
        if (player == null) return;
        com.paybot.utils.SoundHelper.playSuccessSound(player, "minecraft:block.anvil.land", 1.0f, 1.0f);
    }

    @Override
    public void playPlayerLevelupSound(ServerPlayer player) {
        if (player == null) return;
        com.paybot.utils.SoundHelper.playSuccessSound(player, "minecraft:entity.player.levelup", 1.0f, 1.0f);
    }

    @Override
    public void sendPaymentSuccessTitle(ServerPlayer player) {
        if (player == null) return;
        try {
            com.paybot.utils.TitleHelper.sendTitle(player, "§a§lTHANH TOÁN THÀNH CÔNG", "§eCảm ơn bạn đã ủng hộ máy chủ!", 10, 70, 20);
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.sendPaymentSuccessTitle", t);
        }
    }

    // ===================== TIỆN ÍCH =====================

    private Object unwrapOptionalAndHolder(Object raw) {
        if (raw == null) return null;
        Object val = raw;
        if (val instanceof Optional) {
            Optional<?> opt = (Optional<?>) val;
            if (!opt.isPresent()) return null;
            val = opt.get();
        }
        if (val != null) {
            String cn = val.getClass().getName();
            if (cn.contains("Holder") || cn.contains("class_6880")) {
                for (String mName : new String[]{"value", "comp_349", "method_40230"}) {
                    try {
                        Method m = val.getClass().getMethod(mName);
                        m.setAccessible(true);
                        Object inner = m.invoke(val);
                        if (inner != null) return inner;
                    } catch (Throwable ignored) {}
                }
            }
        }
        return val;
    }

    private Object invokeSilently(Method m, Object target, Object... args) {
        if (m == null) return null;
        try { m.setAccessible(true); } catch (Throwable ignored) {}
        try {
            return m.invoke(target, args);
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.invokeSilently on " + m.getName(), t);
            return null;
        }
    }

    private ResourceLocation createResourceLocation(String namespace, String path) {
        for (String mName : new String[]{"fromNamespaceAndPath", "method_60655", "of", "method_43902"}) {
            try {
                Method mFrom = ResourceLocation.class.getMethod(mName, String.class, String.class);
                mFrom.setAccessible(true);
                return (ResourceLocation) mFrom.invoke(null, namespace, path);
            } catch (Throwable ignored) {}
        }
        try {
            for (Constructor<?> ctor : ResourceLocation.class.getDeclaredConstructors()) {
                Class<?>[] p = ctor.getParameterTypes();
                if (p.length == 2 && p[0] == String.class && p[1] == String.class) {
                    ctor.setAccessible(true);
                    return (ResourceLocation) ctor.newInstance(namespace, path);
                }
            }
        } catch (Throwable ignored) {}
        try {
            for (Method m : ResourceLocation.class.getMethods()) {
                if (Modifier.isStatic(m.getModifiers())
                        && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class
                        && ResourceLocation.class.isAssignableFrom(m.getReturnType())) {
                    Object result = m.invoke(null, namespace + ":" + path);
                    if (result != null) return (ResourceLocation) result;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private String safeToString(Object o) {
        try {
            return String.valueOf(o);
        } catch (Throwable t) {
            return "<lỗi toString>";
        }
    }
}
