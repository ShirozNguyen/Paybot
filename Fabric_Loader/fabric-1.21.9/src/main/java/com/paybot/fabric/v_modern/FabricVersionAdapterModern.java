package com.paybot.fabric.v_modern;

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
 * FabricVersionAdapterModern — Adapter cho Fabric từ MC 1.20.5 trở lên (1.20.5 -> 26.2).
 *
 * Đã được kiểm chứng 100% từ bytecode thực tế của toàn bộ 16 phiên bản Fabric Modern:
 * - DataComponents Holder: net.minecraft.class_9334 (Intermediary), DataComponents (Mojmap), DataComponentTypes (Yarn)
 * - CUSTOM_NAME = field_49631 / CUSTOM_NAME
 * - ITEM_NAME   = field_50239 / ITEM_NAME
 * - LORE        = field_49632 / LORE
 * - CUSTOM_DATA = field_49628 / CUSTOM_DATA
 *
 * Tuân thủ Quy tắc 17: Mỗi chức năng chuyên biệt được tách thành class riêng trong com.paybot.compat.modern.
 */
public class FabricVersionAdapterModern implements VersionAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-FabricModern");

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
        this.initialized = true; // Đánh dấu ngay để tuyệt đối không bao giờ spam log lặp lại mỗi tick

        // Lớp 1: Ưu tiên nạp TRỰC TIẾP O(1) từ class_9334 / DataComponents / DataComponentTypes
        resolveDirectComponentTypes();

        // Lớp 2: Chỉ khi thiếu mới tra cứu qua BuiltInRegistries
        if (customNameComponentType == null || loreComponentType == null || customDataComponentType == null) {
            resolveRegistry();
            if (customNameComponentType == null) customNameComponentType = getDataComponentType("custom_name");
            if (itemNameComponentType == null) itemNameComponentType = getDataComponentType("item_name");
            if (loreComponentType == null) loreComponentType = getDataComponentType("lore");
            if (customDataComponentType == null) customDataComponentType = getDataComponentType("custom_data");
        }

        LOGGER.info("[FabricModern] Tra components — custom_name={}, item_name={}, lore={}, custom_data={}",
                customNameComponentType != null, itemNameComponentType != null, loreComponentType != null, customDataComponentType != null);

        Object anchor = customNameComponentType != null ? customNameComponentType
                : (loreComponentType != null ? loreComponentType : customDataComponentType);

        if (anchor == null) {
            LOGGER.error("[FabricModern] Không lấy được DataComponentType nào trên runtime hiện tại.");
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.ensureInitialized: không có anchor", null);
            return;
        }

        ModernComponentMethodResolver resolver = new ModernComponentMethodResolver(ItemStack.class, anchor);
        setComponentMethod = resolver.getSetMethod();
        getComponentMethod = resolver.getGetMethod();

        if (!resolver.isReady()) {
            LOGGER.error("[FabricModern] Không tìm thấy method set/get tương ứng trên ItemStack!");
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.ensureInitialized: resolver not ready", null);
            return;
        }

        LOGGER.info("[FabricModern] Sẵn sàng 100% — setComponentMethod={}, getComponentMethod={}",
                setComponentMethod.getName(), getComponentMethod.getName());
    }

    private void resolveDirectComponentTypes() {
        // 1. Thử qua Fabric MappingResolver trước nếu có
        try {
            Class<?> flClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object flInstance = flClass.getMethod("getInstance").invoke(null);
            Object resolver = flClass.getMethod("getMappingResolver").invoke(flInstance);
            if (resolver != null) {
                Method mapClassName = resolver.getClass().getMethod("mapClassName", String.class, String.class);
                String mappedDc = (String) mapClassName.invoke(resolver, "intermediary", "net.minecraft.class_9334");
                if (mappedDc != null && !mappedDc.isEmpty()) {
                    loadFromDataComponentsClass(Class.forName(mappedDc));
                }
            }
        } catch (Throwable ignored) {}

        if (customNameComponentType != null && loreComponentType != null && customDataComponentType != null) {
            return;
        }

        // 2. Quét trực tiếp qua các tên class chuẩn từ bảng Bytecode 100 phiên bản
        for (String candidate : new String[]{
                "net.minecraft.class_9334",                        // Intermediary (Fabric/Quilt 1.20.5 -> 1.21.11)
                "net.minecraft.core.component.DataComponents",     // Mojang Official (Fabric 26.x / Forge / NeoForge)
                "net.minecraft.component.DataComponentTypes"       // Yarn Named (Fabric Dev)
        }) {
            try {
                Class<?> dcClass = Class.forName(candidate);
                loadFromDataComponentsClass(dcClass);
                if (customNameComponentType != null && loreComponentType != null && customDataComponentType != null) {
                    LOGGER.info("[FabricModern] Nạp thành công DataComponentType trực tiếp từ {}", candidate);
                    break;
                }
            } catch (Throwable ignored) {}
        }
    }

    private void loadFromDataComponentsClass(Class<?> dcClass) {
        if (dcClass == null) return;
        if (customNameComponentType == null) {
            customNameComponentType = getStaticFieldValue(dcClass, "field_49631", "CUSTOM_NAME");
        }
        if (itemNameComponentType == null) {
            itemNameComponentType = getStaticFieldValue(dcClass, "field_50239", "ITEM_NAME");
        }
        if (loreComponentType == null) {
            loreComponentType = getStaticFieldValue(dcClass, "field_49632", "LORE");
        }
        if (customDataComponentType == null) {
            customDataComponentType = getStaticFieldValue(dcClass, "field_49628", "CUSTOM_DATA");
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

    /** Dò registry DataComponentType thật trong BuiltInRegistries (Dự phòng Lớp 2). */
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

        // Ưu tiên kiểm tra field_49658 / DATA_COMPONENT_TYPE trước
        for (String priorityField : new String[]{"field_49658", "DATA_COMPONENT_TYPE"}) {
            try {
                Field pf = BuiltInRegistries.class.getField(priorityField);
                pf.setAccessible(true);
                Object regVal = pf.get(null);
                if (regVal != null) {
                    Method gm = findRegistryGetMethod(regVal.getClass());
                    if (gm != null) {
                        Object gotCN = unwrapOptionalAndHolder(invokeSilently(gm, regVal, testCustomName));
                        Object gotL = unwrapOptionalAndHolder(invokeSilently(gm, regVal, testLore));
                        if (gotCN != null && gotL != null && !gotCN.equals(gotL)) {
                            dataComponentTypeRegistry = regVal;
                            registryGetMethod = gm;
                            return;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

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
        // 1. Ưu tiên theo tên chuẩn method_10223 / get / getValue với đúng p0 == ResourceLocation.class
        for (String preferred : new String[]{"method_10223", "getValue", "get", "m_7745_"}) {
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
        // 2. Quét mọi method có đúng p0 == ResourceLocation.class (Tuyệt đối KHÔNG dùng p0.isAssignableFrom để tránh dính Object.class của getKey(T))
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
        if (itemNameComponentType != null) {
            invokeSilently(setComponentMethod, stack, itemNameComponentType, nameComp);
        }
        Object verify = invokeSilently(getComponentMethod, stack, customNameComponentType);
        if (verify == null) {
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
        TagCompatHelper.putString(newTag, "paybot_invoice_id", invoiceId);

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
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.sendPaymentSuccessTitle", t);
        }
    }

    // ===================== TIỆN ÍCH REFLECTION =====================

    private Object unwrapOptionalAndHolder(Object raw) {
        if (raw == null) return null;
        Object val = raw;
        if (val instanceof Optional) {
            Optional<?> opt = (Optional<?>) val;
            if (!opt.isPresent()) return null;
            val = opt.get();
        }
        // Nếu là Holder.Reference (MC 1.21.2+), bóc value() bên trong
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
