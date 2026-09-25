package com.paybot.fabric.v_modern;

import com.paybot.compat.modern.ModernComponentMethodResolver;
import com.paybot.compat.modern.ModernCustomDataHelper;
import com.paybot.compat.modern.ModernFallbackHoverName;
import com.paybot.compat.modern.ModernItemLoreHelper;
import com.paybot.compat.modern.ModernMapLockHelper;
import com.paybot.compat.version.VersionAdapter;
import com.paybot.utils.ComponentColorParser;
import com.paybot.utils.PayBotDebug;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
// ResourceLocation loaded dynamically — class removed/moved in MC 1.21.11+
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
 * FabricVersionAdapterModern — v5.5.9 Part 125 [TÁI CẤU TRÚC THEO RULE 17]
 *
 * Phục vụ kỷ nguyên Data Components trên Fabric & Quilt: MC 1.20.5 → 1.21.11, 26.x.
 * Đã giải quyết triệt để lỗi 3 tháng qua:
 *  1. Dùng ModernComponentMethodResolver phân biệt chính xác method set() (method_57379)
 *     và getOrDefault() (method_57825).
 *  2. Dùng ModernItemLoreHelper đóng gói đúng ItemLore record wrapper.
 *  3. Dùng ModernCustomDataHelper xử lý CustomData component lưu trữ invoice id.
 *  4. Dùng ModernFallbackHoverName đa mapping chống ClassNotFoundException.
 *  5. Dùng ModernMapLockHelper khóa cứng bản đồ QR code.
 */
public class FabricVersionAdapterModern implements VersionAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-Fabric-Adapter-Modern");

    // Dynamic ResourceLocation class — may not exist in MC 1.21.11+ (class removed/moved or Identifier in yarn)
    private static final Class<?> RESOURCE_LOCATION_CLASS;
    static {
        Class<?> _rlCls = null;
        for (String _rlName : new String[]{
                "net.minecraft.resources.ResourceLocation",
                "net.minecraft.core.ResourceLocation",
                "net.minecraft.util.ResourceLocation",
                "net.minecraft.resources.Identifier",
                "net.minecraft.util.Identifier"}) {
            try { _rlCls = Class.forName(_rlName); break; } catch (Throwable ignored) {}
        }
        RESOURCE_LOCATION_CLASS = _rlCls;
    }

    private Object dataComponentTypeRegistry = null;
    private Method registryGetMethod = null;

    private Object customNameComponentType = null;
    private Object loreComponentType = null;
    private Object customDataComponentType = null;

    private Method setComponentMethod = null; // ItemStack.set(DataComponentType<T>, T)
    private Method getComponentMethod = null; // ItemStack.get(DataComponentType<T>)

    private boolean initialized = false;

    // ===================== KHỞI TẠO =====================

    private synchronized void ensureInitialized() {
        if (initialized) return;
        initialized = true;

        resolveRegistry();

        customNameComponentType = getDataComponentType("custom_name");
        loreComponentType = getDataComponentType("lore");
        customDataComponentType = getDataComponentType("custom_data");

        LOGGER.info("[FabricModern] Tra registry — custom_name={}, lore={}, custom_data={}",
                customNameComponentType != null, loreComponentType != null, customDataComponentType != null);

        Object anchor = customNameComponentType != null ? customNameComponentType
                : (loreComponentType != null ? loreComponentType : customDataComponentType);

        if (anchor == null) {
            LOGGER.error("[FabricModern] Không lấy được BẤT KỲ DataComponentType nào qua registry.");
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.ensureInitialized: không có anchor", null);
            return;
        }

        ModernComponentMethodResolver resolver = new ModernComponentMethodResolver(ItemStack.class, anchor);
        setComponentMethod = resolver.getSetMethod();
        getComponentMethod = resolver.getGetMethod();

        if (!resolver.isReady()) {
            LOGGER.error("[FabricModern] Không tìm thấy method set/get tương ứng trên ItemStack!");
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.ensureInitialized: resolver not ready", null);
        } else {
            LOGGER.info("[FabricModern] Sẵn sàng — setComponentMethod={}, getComponentMethod={}",
                    setComponentMethod.getName(), getComponentMethod.getName());
        }
    }

    /** Dò registry DataComponentType thật trong BuiltInRegistries. */
    private void resolveRegistry() {
        Field[] fields;
        try {
            fields = BuiltInRegistries.class.getFields();
        } catch (Throwable t) {
            LOGGER.error("[FabricModern] Không đọc được field của BuiltInRegistries: {}", t.getMessage());
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.resolveRegistry: BuiltInRegistries.class.getFields()", t);
            return;
        }

        Object testCustomName = createResourceLocation("minecraft", "custom_name");
        Object testLore = createResourceLocation("minecraft", "lore");
        if (testCustomName == null || testLore == null) {
            LOGGER.error("[FabricModern] Không dựng được ResourceLocation để test registry.");
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
            LOGGER.info("[FabricModern] Xác định registry DataComponentType qua field '{}' (kiểu {}) — đã kiểm tra {} field ứng viên.",
                    f.getName(), value.getClass().getName(), checked);
            return;
        }

        LOGGER.error("[FabricModern] Quét hết {} field ứng viên của BuiltInRegistries mà không tìm được registry DataComponentType.", checked);
        PayBotDebug.logSwallowed("FabricVersionAdapterModern.resolveRegistry: không tìm thấy registry", null);
    }

    private Method findRegistryGetMethod(Class<?> registryClass) {
        Method fallbackOptional = null;
        for (Method m : registryClass.getMethods()) {
            if (m.getParameterCount() != 1) continue;
            Class<?> p0 = m.getParameterTypes()[0];
            if (RESOURCE_LOCATION_CLASS == null || !(p0 == RESOURCE_LOCATION_CLASS)) continue;
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
        Object rl = createResourceLocation("minecraft", path);
        if (rl == null) return null;
        return unwrapOptional(invokeSilently(registryGetMethod, dataComponentTypeRegistry, rl));
    }

    // ===================== TÊN + LORE =====================

    @Override
    public void setItemNameAndLore(ItemStack stack, String name, List<String> lore) {
        if (stack == null || stack.isEmpty()) return;
        ensureInitialized();

        if (name != null && !name.isEmpty()) {
            setName(stack, ComponentColorParser.parse(name));
        }
        if (lore != null && !lore.isEmpty()) {
            setLore(stack, ComponentColorParser.parseLore(lore));
        }
    }

    private void setName(ItemStack stack, Component nameComp) {
        if (customNameComponentType == null || setComponentMethod == null || getComponentMethod == null) {
            ModernFallbackHoverName.trySetHoverNameFallback(stack, nameComp);
            return;
        }
        invokeSilently(setComponentMethod, stack, customNameComponentType, nameComp);
        Object verify = invokeSilently(getComponentMethod, stack, customNameComponentType);
        if (verify == null) {
            LOGGER.warn("[FabricModern] Set tên qua reflection chưa xác minh được — thử fallback an toàn.");
            ModernFallbackHoverName.trySetHoverNameFallback(stack, nameComp);
        }
    }

    private void setLore(ItemStack stack, List<Component> componentList) {
        if (loreComponentType == null || setComponentMethod == null || getComponentMethod == null) {
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.setLore: thiếu loreComponentType/set-get method", null);
            return;
        }

        Object itemLoreInstance = ModernItemLoreHelper.buildItemLore(componentList);
        if (itemLoreInstance == null) {
            LOGGER.error("[FabricModern] Không tạo được ItemLore wrapper instance — bỏ qua set lore.");
            return;
        }

        invokeSilently(setComponentMethod, stack, loreComponentType, itemLoreInstance);
        Object verify = invokeSilently(getComponentMethod, stack, loreComponentType);
        if (verify != null) {
            LOGGER.info("[FabricModern] Set lore THÀNH CÔNG cho item — xác minh: {}", safeToString(verify));
        } else {
            LOGGER.warn("[FabricModern] Set lore xong nhưng đọc lại ra null — kiểm tra debug-mode.");
        }
    }

    // ===================== INVOICE ID (CustomData) =====================

    @Override
    public void setInvoiceId(ItemStack stack, String invoiceId) {
        if (stack == null || stack.isEmpty() || invoiceId == null) return;
        ensureInitialized();
        if (customDataComponentType == null || setComponentMethod == null || getComponentMethod == null) {
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.setInvoiceId: thiếu component type/method", null);
            return;
        }

        CompoundTag newTag = new CompoundTag();
        Object existing = invokeSilently(getComponentMethod, stack, customDataComponentType);
        CompoundTag existingTag = ModernCustomDataHelper.extractCompoundTag(existing);
        if (existingTag != null) newTag = existingTag.copy();
        newTag.putString("paybot_invoice_id", invoiceId);

        Object customDataObj = ModernCustomDataHelper.buildCustomData(newTag);
        if (customDataObj == null) {
            LOGGER.error("[FabricModern] Không tạo được CustomData wrapper instance cho invoice id.");
            return;
        }

        invokeSilently(setComponentMethod, stack, customDataComponentType, customDataObj);
        Object verify = invokeSilently(getComponentMethod, stack, customDataComponentType);
        String savedId = ModernCustomDataHelper.getInvoiceIdFromCustomData(verify);
        if (invoiceId.equals(savedId)) {
            LOGGER.info("[FabricModern] Set CustomData (invoice id: {}) THÀNH CÔNG!", invoiceId);
        } else {
            LOGGER.warn("[FabricModern] Set CustomData xong nhưng xác minh invoice id thất bại.");
        }
    }

    @Override
    public String getInvoiceId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ensureInitialized();
        if (customDataComponentType == null || getComponentMethod == null) return null;
        try {
            Object customDataObj = invokeSilently(getComponentMethod, stack, customDataComponentType);
            return ModernCustomDataHelper.getInvoiceIdFromCustomData(customDataObj);
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.getInvoiceId", t);
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
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.getMapSavedData", t);
        }
        return null;
    }

    @Override
    public void lockMap(MapItemSavedData state) {
        ModernMapLockHelper.lockMap(state);
    }

    // ===================== TIỆN ÍCH DÙNG CHUNG =====================

    private Object unwrapOptional(Object value) {
        if (!(value instanceof Optional)) return value;
        Optional<?> opt = (Optional<?>) value;
        return opt.isPresent() ? opt.get() : null;
    }

    private Object invokeSilently(Method m, Object target, Object... args) {
        if (m == null) return null;
        try {
            m.setAccessible(true);
        } catch (Throwable ignored) {}
        try {
            return m.invoke(target, args);
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.invokeSilently on " + m.getName(), t);
            return null;
        }
    }

    // Returns Object to compile on any MC version
    private Object createResourceLocation(String namespace, String path) {
        if (RESOURCE_LOCATION_CLASS == null) return null;
        try {
            Method mFrom = RESOURCE_LOCATION_CLASS.getMethod("fromNamespaceAndPath", String.class, String.class);
            return mFrom.invoke(null, namespace, path);
        } catch (Throwable ignored) {}
        try {
            for (Constructor<?> ctor : RESOURCE_LOCATION_CLASS.getDeclaredConstructors()) {
                Class<?>[] p = ctor.getParameterTypes();
                if (p.length == 2 && p[0] == String.class && p[1] == String.class) {
                    ctor.setAccessible(true);
                    return ctor.newInstance(namespace, path);
                }
            }
        } catch (Throwable ignored) {}
        try {
            for (Method m : RESOURCE_LOCATION_CLASS.getMethods()) {
                if (Modifier.isStatic(m.getModifiers())
                        && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class
                        && RESOURCE_LOCATION_CLASS.isAssignableFrom(m.getReturnType())) {
                    Object result = m.invoke(null, namespace + ":" + path);
                    if (result != null) return result;
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
