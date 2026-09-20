package com.paybot.forge.v_modern;

import com.paybot.compat.modern.ModernComponentMethodResolver;
import com.paybot.compat.modern.ModernCustomDataHelper;
import com.paybot.compat.modern.ModernFallbackHoverName;
import com.paybot.compat.modern.ModernItemLoreHelper;
import com.paybot.compat.modern.ModernMapLockHelper;
import com.paybot.compat.legacy.LegacyItemTagHelper;
import com.paybot.compat.legacy.LegacyMapLockHelper;
import com.paybot.compat.version.VersionAdapter;
import com.paybot.utils.ComponentColorParser;
import com.paybot.utils.PayBotDebug;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;

/**
 * ForgeVersionAdapterModern — v5.5.9 Part 125 [TÁI CẤU TRÚC THEO RULE 17]
 *
 * Phục vụ kỷ nguyên MC 1.20.2 - 1.21.11, 26.x trên forge.
 * Đã giải quyết triệt để lỗi 3 tháng qua:
 *  1. Dùng ModernComponentMethodResolver phân biệt chính xác method set() và getOrDefault().
 *  2. Dùng ModernItemLoreHelper đóng gói đúng ItemLore record wrapper.
 *  3. Dùng ModernCustomDataHelper xử lý CustomData component lưu trữ invoice id.
 *  4. Dùng ModernMapLockHelper khóa cứng bản đồ QR code.
 */
public class ForgeVersionAdapterModern implements VersionAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-Forge-Adapter-Modern");

    private boolean dataComponentsEra = false;

    private Object dataComponentTypeRegistry = null;
    private Method registryGetMethod = null;

    private Object customNameComponentType = null;
    private Object loreComponentType = null;
    private Object customDataComponentType = null;

    private Method setComponentMethod = null;
    private Method getComponentMethod = null;

    private boolean initialized = false;

    // ===================== KHỞI TẠO =====================

    private synchronized void ensureInitialized() {
        if (initialized) return;
        initialized = true;

        dataComponentsEra = classExists("net.minecraft.core.component.DataComponentType");
        LOGGER.info("[ForgeModern] Phát hiện kiến trúc runtime: Data Components = {}", dataComponentsEra);

        if (!dataComponentsEra) return; // 1.20.2-1.20.4: dùng nhánh NBT thô

        resolveRegistry();

        customNameComponentType = getDataComponentType("custom_name");
        loreComponentType = getDataComponentType("lore");
        customDataComponentType = getDataComponentType("custom_data");

        LOGGER.info("[ForgeModern] Tra registry — custom_name={}, lore={}, custom_data={}",
                customNameComponentType != null, loreComponentType != null, customDataComponentType != null);

        Object anchor = customNameComponentType != null ? customNameComponentType
                : (loreComponentType != null ? loreComponentType : customDataComponentType);

        if (anchor == null) {
            LOGGER.error("[ForgeModern] Không lấy được BẤT KỲ DataComponentType nào qua registry.");
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
            LOGGER.info("[ForgeModern] Sẵn sàng — setComponentMethod={}, getComponentMethod={}",
                    setComponentMethod.getName(), getComponentMethod.getName());
        }
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
            LOGGER.error("[ForgeModern] Không đọc được field của BuiltInRegistries: {}", t.getMessage());
            return;
        }

        ResourceLocation testCustomName = createResourceLocation("minecraft", "custom_name");
        ResourceLocation testLore = createResourceLocation("minecraft", "lore");
        if (testCustomName == null || testLore == null) {
            LOGGER.error("[ForgeModern] Không dựng được ResourceLocation để test registry.");
            return;
        }

        int checked = 0;
        for (Field f : fields) {
            Object value;
            try {
                value = f.get(null);
            } catch (Throwable ignored) {
                continue;
            }
            if (value == null) continue;

            Method getMethod = findRegistryGetMethod(value.getClass());
            if (getMethod == null) continue;
            checked++;

            Object gotCustomName = unwrapOptional(invokeSilently(getMethod, value, testCustomName));
            if (gotCustomName == null) continue;
            Object gotLore = unwrapOptional(invokeSilently(getMethod, value, testLore));
            if (gotLore == null) continue;
            if (gotCustomName.getClass() != gotLore.getClass()) continue;
            if (gotCustomName.equals(gotLore)) continue;

            dataComponentTypeRegistry = value;
            registryGetMethod = getMethod;
            LOGGER.info("[ForgeModern] Xác định registry DataComponentType qua field '{}' (kiểu {}) — đã kiểm tra {} field ứng viên.",
                    f.getName(), value.getClass().getName(), checked);
            return;
        }

        LOGGER.error("[ForgeModern] Quét hết {} field ứng viên mà không tìm được registry DataComponentType.", checked);
    }

    private Method findRegistryGetMethod(Class<?> registryClass) {
        Method fallbackOptional = null;
        for (Method m : registryClass.getMethods()) {
            if (m.getParameterCount() != 1) continue;
            Class<?> p0 = m.getParameterTypes()[0];
            if (!p0.isAssignableFrom(ResourceLocation.class)) continue;
            Class<?> ret = m.getReturnType();
            if (ret == void.class || ret == boolean.class || ret == Boolean.class) continue;
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
        return unwrapOptional(invokeSilently(registryGetMethod, dataComponentTypeRegistry, rl));
    }

    // ===================== TÊN + LORE =====================

    @Override
    public void setItemNameAndLore(ItemStack stack, String name, List<String> lore) {
        if (stack == null || stack.isEmpty()) return;
        ensureInitialized();

        if (name != null && !name.isEmpty()) {
            Component nameComp = ComponentColorParser.parse(name);
            if (dataComponentsEra) setNameModern(stack, nameComp);
            else setNameLegacyNbt(stack, nameComp);
        }
        if (lore != null && !lore.isEmpty()) {
            List<Component> componentList = ComponentColorParser.parseLore(lore);
            if (dataComponentsEra) setLoreModern(stack, componentList);
            else setLoreLegacyNbt(stack, componentList);
        }
    }

    private void setNameModern(ItemStack stack, Component nameComp) {
        if (customNameComponentType == null || setComponentMethod == null || getComponentMethod == null) {
            ModernFallbackHoverName.trySetHoverNameFallback(stack, nameComp);
            return;
        }
        invokeSilently(setComponentMethod, stack, customNameComponentType, nameComp);
        Object verify = invokeSilently(getComponentMethod, stack, customNameComponentType);
        if (verify == null) {
            LOGGER.warn("[ForgeModern] Set tên qua reflection chưa xác minh được — thử fallback.");
            ModernFallbackHoverName.trySetHoverNameFallback(stack, nameComp);
        }
    }

    private void setNameLegacyNbt(ItemStack stack, Component nameComp) {
        LegacyItemTagHelper.setName(stack, nameComp);
    }

    private void setLoreModern(ItemStack stack, List<Component> componentList) {
        if (loreComponentType == null || setComponentMethod == null || getComponentMethod == null) {
            setLoreLegacyNbt(stack, componentList);
            return;
        }
        Object wrapper = ModernItemLoreHelper.buildItemLore(componentList);
        if (wrapper == null) {
            setLoreLegacyNbt(stack, componentList);
            return;
        }
        invokeSilently(setComponentMethod, stack, loreComponentType, wrapper);
        Object verify = invokeSilently(getComponentMethod, stack, loreComponentType);
        if (verify != null) {
            LOGGER.info("[ForgeModern] Set lore THÀNH CÔNG cho item.");
        } else {
            setLoreLegacyNbt(stack, componentList);
        }
    }

    private void setLoreLegacyNbt(ItemStack stack, List<Component> componentList) {
        LegacyItemTagHelper.setLoreComponents(stack, componentList);
    }

    // ===================== INVOICE ID =====================

    @Override
    public void setInvoiceId(ItemStack stack, String invoiceId) {
        if (stack == null || stack.isEmpty() || invoiceId == null) return;
        ensureInitialized();
        if (dataComponentsEra) {
            if (customDataComponentType != null && setComponentMethod != null && getComponentMethod != null) {
                CompoundTag newTag = new CompoundTag();
                Object existing = invokeSilently(getComponentMethod, stack, customDataComponentType);
                CompoundTag existingTag = ModernCustomDataHelper.extractCompoundTag(existing);
                if (existingTag != null) newTag = existingTag.copy();
                newTag.putString("paybot_invoice_id", invoiceId);

                Object customDataObj = ModernCustomDataHelper.buildCustomData(newTag);
                if (customDataObj != null) {
                    invokeSilently(setComponentMethod, stack, customDataComponentType, customDataObj);
                    return;
                }
            }
        }
        // Legacy NBT
        try {
            CompoundTag tag = (CompoundTag) stack.getClass().getMethod("getOrCreateTag").invoke(stack);
            if (tag != null) tag.putString("paybot_invoice_id", invoiceId);
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.setInvoiceId legacy", t);
        }
    }

    @Override
    public String getInvoiceId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ensureInitialized();
        if (dataComponentsEra && customDataComponentType != null && getComponentMethod != null) {
            try {
                Object customDataObj = invokeSilently(getComponentMethod, stack, customDataComponentType);
                String id = ModernCustomDataHelper.getInvoiceIdFromCustomData(customDataObj);
                if (id != null) return id;
            } catch (Throwable t) {
                PayBotDebug.logSwallowed("ForgeVersionAdapterModern.getInvoiceId", t);
            }
        }
        try {
            CompoundTag tag = (CompoundTag) stack.getClass().getMethod("getTag").invoke(stack);
            if (tag != null && com.paybot.utils.TagCompatHelper.contains(tag, "paybot_invoice_id")) {
                return com.paybot.utils.TagCompatHelper.getString(tag, "paybot_invoice_id");
            }
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.getInvoiceId legacy", t);
        }
        return null;
    }

    // ===================== MAP (QR code) =====================

    @Override
    public MapItemSavedData getMapSavedData(ItemStack mapItem, ServerLevel world) {
        if (mapItem == null || world == null) return null;
        ensureInitialized();
        try {
            for (Method m : MapItem.class.getMethods()) {
                if (m.getParameterCount() == 2
                        && m.getParameterTypes()[0] == ItemStack.class
                        && net.minecraft.world.level.Level.class.isAssignableFrom(m.getParameterTypes()[1])
                        && MapItemSavedData.class.isAssignableFrom(m.getReturnType())) {
                    Object res = m.invoke(null, mapItem, world);
                    if (res instanceof MapItemSavedData) return (MapItemSavedData) res;
                }
            }
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.getMapSavedData", t);
        }
        return null;
    }

    @Override
    public void lockMap(MapItemSavedData state) {
        if (state == null) return;
        if (dataComponentsEra) {
            ModernMapLockHelper.lockMap(state);
        } else {
            LegacyMapLockHelper.lock(state);
        }
    }

    // ===================== TIỆN ÍCH DÙNG CHUNG =====================

    private Object unwrapOptional(Object value) {
        if (!(value instanceof Optional)) return value;
        Optional<?> opt = (Optional<?>) value;
        return opt.isPresent() ? opt.get() : null;
    }

    private Object invokeSilently(Method m, Object target, Object... args) {
        if (m == null) return null;
        try { m.setAccessible(true); } catch (Throwable ignored) {}
        try { return m.invoke(target, args); } catch (Throwable t) {
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.invokeSilently on " + m.getName(), t);
            return null;
        }
    }

    private ResourceLocation createResourceLocation(String namespace, String path) {
        try {
            Method mFrom = ResourceLocation.class.getMethod("fromNamespaceAndPath", String.class, String.class);
            return (ResourceLocation) mFrom.invoke(null, namespace, path);
        } catch (Throwable ignored) {}
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
        try { return String.valueOf(o); } catch (Throwable t) { return "<lỗi toString>"; }
    }
}
