package com.naptien.forge.v_modern;

import com.naptien.compat.version.VersionAdapter;
import com.naptien.utils.ComponentColorParser;
import com.naptien.utils.PayBotDebug;

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
 * ForgeVersionAdapterModern — v5.5.5 Part 44 [MODULE MỚI, thay ForgeVersionAdapter1_21 cũ]
 *
 * Phục vụ TOÀN BỘ MC 1.20.2 - 1.21.11 trên Forge (module forge-modern/, biên dịch nhắm 1.21.1
 * nhưng KHÔNG hardcode theo bản này — xem chiến lược bên dưới). Tự nhận diện lúc chạy để dùng
 * đúng API: NBT thô (1.20.2-1.20.4) hay Data Components (1.20.5+).
 *
 * ================= KHÁC BIỆT SO VỚI FABRIC =================
 *
 * Forge/NeoForge dùng tên Mojang trực tiếp CẢ lúc dev LẪN lúc chạy thật (kể từ MC 1.17 —
 * "obfuscation was effectively invisible in NeoForge development environments", theo tài liệu
 * NeoForged) — khác Fabric (chạy bằng ID Intermediary, cần MappingResolver dịch qua lại). Vì
 * vậy adapter này KHÔNG cần MappingResolver: Class.forName với tên Mojang chuỗi ("net.minecraft...")
 * hoạt động trực tiếp trên runtime thật, kể cả class MỚI xuất hiện từ 1.20.5 (DataComponentTypes,
 * ItemLore, CustomData) mà module này không thể import thẳng lúc biên dịch (vì compile chung 1
 * lần, không tách theo minor version như thiết kế gốc).
 *
 * VẪN áp dụng cùng nguyên tắc như Fabric: KHÔNG gọi thẳng bất kỳ method nào có khả năng đổi
 * bytecode giữa các bản (vd stack.setHoverName() bên trong Data Components có thể đổi cách cài
 * đặt dù Mojang vẫn giữ tên) — dùng registry thật + so khớp method theo CẤU TRÚC + xác minh đọc
 * lại sau khi set, để 1 module vẫn dùng tốt cho nhiều bản khác nhau trong dải 1.20.2-1.21.11.
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

        // Data Components chỉ tồn tại từ 1.20.5 — dò bằng sự hiện diện thật của class, không
        // đoán theo số version (module này không tự biết chính xác đang chạy bản nào).
        dataComponentsEra = classExists("net.minecraft.core.component.DataComponentType");
        LOGGER.info("[ForgeModern] Phát hiện kiến trúc runtime: Data Components = {}", dataComponentsEra);

        if (!dataComponentsEra) return; // 1.20.2-1.20.4: dùng nhánh NBT thô, không cần registry

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

        resolveSetGetMethods(anchor);

        if (setComponentMethod == null || getComponentMethod == null) {
            LOGGER.error("[ForgeModern] Có DataComponentType nhưng KHÔNG tìm được method set/get tương ứng.");
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.ensureInitialized: setComponentMethod="
                    + (setComponentMethod != null) + ", getComponentMethod=" + (getComponentMethod != null), null);
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
            LOGGER.info("[ForgeModern] Xác định registry DataComponentType qua field '{}' — đã kiểm tra {} ứng viên.",
                    f.getName(), checked);
            return;
        }

        LOGGER.error("[ForgeModern] Quét hết {} field ứng viên mà không tìm được registry khớp.", checked);
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

    private void resolveSetGetMethods(Object anchor) {
        Method bestSet = null, bestGet = null;
        int setCandidates = 0, getCandidates = 0;
        for (Method m : ItemStack.class.getMethods()) {
            int pc = m.getParameterCount();
            if (pc == 2 && m.getParameterTypes()[0].isInstance(anchor)) {
                setCandidates++;
                if (bestSet == null || m.getParameterTypes()[1] == Object.class) bestSet = m;
            } else if (pc == 1 && m.getParameterTypes()[0].isInstance(anchor)
                    && m.getReturnType() != void.class && m.getReturnType() != boolean.class) {
                getCandidates++;
                if (bestGet == null || m.getReturnType() == Object.class) bestGet = m;
            }
        }
        setComponentMethod = bestSet;
        getComponentMethod = bestGet;
        if (setCandidates != 1 || getCandidates != 1) {
            LOGGER.warn("[ForgeModern] Số method khớp cấu trúc không rõ ràng 1-1 (set: {}, get: {}).",
                    setCandidates, getCandidates);
        }
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
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.setNameModern: thiếu component type/method, fallback setHoverName", null);
            trySetHoverNameFallback(stack, nameComp);
            return;
        }
        invokeSilently(setComponentMethod, stack, customNameComponentType, nameComp);
        if (invokeSilently(getComponentMethod, stack, customNameComponentType) == null) {
            LOGGER.warn("[ForgeModern] Set tên qua registry+reflection nhưng đọc lại ra null — thử fallback setHoverName().");
            trySetHoverNameFallback(stack, nameComp);
        }
    }

    private void trySetHoverNameFallback(ItemStack stack, Component nameComp) {
        try {
            stack.setHoverName(nameComp);
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.trySetHoverNameFallback", t);
        }
    }

    /** Nhánh 1.20.2-1.20.4: chưa có Data Components, vẫn dùng NBT display.Name như bản legacy. */
    private void setNameLegacyNbt(ItemStack stack, Component nameComp) {
        try {
            stack.setHoverName(nameComp);
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.setNameLegacyNbt", t);
        }
    }

    private void setLoreModern(ItemStack stack, List<Component> componentList) {
        if (loreComponentType == null || setComponentMethod == null || getComponentMethod == null) {
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.setLoreModern: thiếu component type/method — bỏ qua.", null);
            return;
        }
        if (attemptLoreValue(stack, componentList, "List<Component> trực tiếp")) return;

        Class<?> itemLoreClass = classForNameOrNull("net.minecraft.world.item.component.ItemLore");
        if (itemLoreClass != null) {
            Object wrapper = buildListWrapperInstance(itemLoreClass, componentList);
            if (wrapper != null && attemptLoreValue(stack, wrapper, "wrapper " + itemLoreClass.getName())) return;
        }
        LOGGER.warn("[ForgeModern] KHÔNG set được lore bằng bất kỳ cách nào đã thử.");
    }

    private boolean attemptLoreValue(ItemStack stack, Object value, String description) {
        try {
            invokeSilently(setComponentMethod, stack, loreComponentType, value);
            Object verify = invokeSilently(getComponentMethod, stack, loreComponentType);
            if (verify != null) {
                LOGGER.info("[ForgeModern] Set lore THÀNH CÔNG bằng phương án: {} — đọc lại: {}", description, safeToString(verify));
                return true;
            }
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.attemptLoreValue: '" + description + "'", t);
        }
        return false;
    }

    /** Nhánh 1.20.2-1.20.4: lore vẫn là NBT List<String> JSON trong display.Lore, giống bản legacy. */
    private void setLoreLegacyNbt(ItemStack stack, List<Component> componentList) {
        try {
            CompoundTag display = stack.getOrCreateTagElement("display");
            net.minecraft.nbt.ListTag loreList = new net.minecraft.nbt.ListTag();
            for (Component c : componentList) {
                loreList.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(c)));
            }
            display.put("Lore", loreList);
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.setLoreLegacyNbt", t);
        }
    }

    /** v5.5.5 Part 44b: đã xác minh qua Javadoc chính thức (NeoForge 1.21.1-21.1.216 + CraftTweaker
     *  docs) — net.minecraft.world.item.component.ItemLore là RECORD 2 field (lines, styledLines),
     *  CÓ static factory ItemLore.of(List)/ItemLore.of(List,List). Ưu tiên static factory trước
     *  (khả năng tự suy ra styledLines đúng cách hơn truyền cùng 1 list 2 lần cho constructor),
     *  constructor record chỉ còn là dự phòng. */
    private Object buildListWrapperInstance(Class<?> wrapperClass, List<Component> componentList) {
        try {
            for (Method m : wrapperClass.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers())) continue;
                if (!wrapperClass.isAssignableFrom(m.getReturnType())) continue;
                Class<?>[] pTypes = m.getParameterTypes();
                m.setAccessible(true);
                try {
                    if (pTypes.length == 1 && pTypes[0].isAssignableFrom(List.class)) return m.invoke(null, componentList);
                    if (pTypes.length == 2 && pTypes[0].isAssignableFrom(List.class) && pTypes[1].isAssignableFrom(List.class))
                        return m.invoke(null, componentList, componentList);
                } catch (Throwable ignoredPerMethod) {
                    // thử static factory tiếp theo
                }
            }
        } catch (Throwable ignored) {
            // không có static factory phù hợp
        }
        try {
            for (Constructor<?> ctor : wrapperClass.getDeclaredConstructors()) {
                ctor.setAccessible(true);
                Class<?>[] pTypes = ctor.getParameterTypes();
                try {
                    if (pTypes.length == 1 && pTypes[0].isAssignableFrom(List.class)) return ctor.newInstance(componentList);
                    if (pTypes.length == 2 && pTypes[0].isAssignableFrom(List.class) && pTypes[1].isAssignableFrom(List.class))
                        return ctor.newInstance(componentList, componentList);
                } catch (Throwable ignoredPerCtor) {
                    // thử constructor tiếp theo
                }
            }
        } catch (Throwable ignored) {
            // không có constructor phù hợp
        }
        return null;
    }

    // ===================== INVOICE ID (CustomData / NBT trực tiếp) =====================

    @Override
    public void setInvoiceId(ItemStack stack, String invoiceId) {
        if (stack == null || stack.isEmpty() || invoiceId == null) return;
        ensureInitialized();

        if (!dataComponentsEra) {
            try {
                stack.getOrCreateTag().putString("paybot_invoice_id", invoiceId);
            } catch (Throwable t) {
                PayBotDebug.logSwallowed("ForgeVersionAdapterModern.setInvoiceId (legacy NBT)", t);
            }
            return;
        }

        if (customDataComponentType == null || setComponentMethod == null || getComponentMethod == null) {
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.setInvoiceId: thiếu component type/method", null);
            return;
        }

        CompoundTag newTag = new CompoundTag();
        CompoundTag existingTag = extractCompoundTag(invokeSilently(getComponentMethod, stack, customDataComponentType));
        if (existingTag != null) newTag = existingTag.copy();
        newTag.putString("paybot_invoice_id", invoiceId);

        if (attemptCustomDataValue(stack, newTag, "CompoundTag trực tiếp")) return;

        Class<?> customDataClass = classForNameOrNull("net.minecraft.world.item.component.CustomData");
        if (customDataClass != null) {
            Object wrapper = buildTagWrapperInstance(customDataClass, newTag);
            if (wrapper != null && attemptCustomDataValue(stack, wrapper, "wrapper " + customDataClass.getName())) return;
        }
        PayBotDebug.logSwallowed("ForgeVersionAdapterModern.setInvoiceId: không set được bằng bất kỳ cách nào", null);
    }

    private boolean attemptCustomDataValue(ItemStack stack, Object value, String description) {
        try {
            invokeSilently(setComponentMethod, stack, customDataComponentType, value);
            CompoundTag verifyTag = extractCompoundTag(invokeSilently(getComponentMethod, stack, customDataComponentType));
            if (verifyTag != null && verifyTag.contains("paybot_invoice_id")) {
                LOGGER.info("[ForgeModern] Set CustomData (invoice id) THÀNH CÔNG bằng phương án: {}", description);
                return true;
            }
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.attemptCustomDataValue: '" + description + "'", t);
        }
        return false;
    }

    private Object buildTagWrapperInstance(Class<?> wrapperClass, CompoundTag tag) {
        try {
            for (Constructor<?> ctor : wrapperClass.getDeclaredConstructors()) {
                ctor.setAccessible(true);
                Class<?>[] pTypes = ctor.getParameterTypes();
                if (pTypes.length == 1 && pTypes[0].isAssignableFrom(CompoundTag.class)) {
                    try {
                        return ctor.newInstance(tag);
                    } catch (Throwable ignored) {
                        // thử tiếp
                    }
                }
            }
        } catch (Throwable ignored) {
            // không có constructor phù hợp
        }
        try {
            for (Method m : wrapperClass.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers())
                        && m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(CompoundTag.class)
                        && wrapperClass.isAssignableFrom(m.getReturnType())) {
                    m.setAccessible(true);
                    try {
                        return m.invoke(null, tag);
                    } catch (Throwable ignored) {
                        // thử tiếp
                    }
                }
            }
        } catch (Throwable ignored) {
            // không có static factory phù hợp
        }
        return null;
    }

    private CompoundTag extractCompoundTag(Object customDataObj) {
        if (customDataObj == null) return null;
        if (customDataObj instanceof CompoundTag) return (CompoundTag) customDataObj;
        try {
            for (Method m : customDataObj.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && CompoundTag.class.isAssignableFrom(m.getReturnType())) {
                    Object result = invokeSilently(m, customDataObj);
                    if (result instanceof CompoundTag) return (CompoundTag) result;
                }
            }
        } catch (Throwable ignored) {
            // không tìm được method trích CompoundTag
        }
        return null;
    }

    @Override
    public String getInvoiceId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ensureInitialized();

        if (!dataComponentsEra) {
            try {
                CompoundTag tag = stack.getTag();
                if (tag != null && tag.contains("paybot_invoice_id")) return tag.getString("paybot_invoice_id");
            } catch (Throwable t) {
                PayBotDebug.logSwallowed("ForgeVersionAdapterModern.getInvoiceId (legacy NBT)", t);
            }
            return null;
        }

        if (customDataComponentType == null || getComponentMethod == null) return null;
        try {
            CompoundTag tag = extractCompoundTag(invokeSilently(getComponentMethod, stack, customDataComponentType));
            if (tag != null && tag.contains("paybot_invoice_id")) return tag.getString("paybot_invoice_id");
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.getInvoiceId", t);
        }
        return null;
    }

    // ===================== MAP (QR code) =====================

    @Override
    public MapItemSavedData getMapSavedData(ItemStack mapItem, ServerLevel world) {
        if (mapItem == null || world == null) return null;
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
        Field lockedField = findLockedField();
        if (lockedField == null) {
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.lockMap: không tìm được field 'locked'.", null);
            return;
        }
        try {
            lockedField.setAccessible(true);
            lockedField.set(state, true);
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.lockMap: set field thất bại", t);
        }
    }

    private Field findLockedField() {
        try {
            return MapItemSavedData.class.getDeclaredField("locked");
        } catch (Throwable ignored) {
            // Mojang có thể đổi tên field ở bản khác — thử fallback type-scan
        }
        List<Field> boolFields = new java.util.ArrayList<>();
        for (Field f : MapItemSavedData.class.getDeclaredFields()) {
            if (f.getType() == boolean.class) boolFields.add(f);
        }
        if (boolFields.size() == 1) return boolFields.get(0);
        if (boolFields.size() > 1) {
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.findLockedField: có " + boolFields.size()
                    + " field boolean, không chắc field nào là 'locked'.", null);
        }
        return null;
    }

    // ===================== TIỆN ÍCH DÙNG CHUNG =====================

    private Object unwrapOptional(Object value) {
        if (!(value instanceof Optional)) return value;
        Optional<?> opt = (Optional<?>) value;
        return opt.isPresent() ? opt.get() : null;
    }

    private Object invokeSilently(Method m, Object target, Object... args) {
        try {
            m.setAccessible(true);
        } catch (Throwable ignored) {
            // môi trường có thể chặn setAccessible, vẫn thử invoke bình thường
        }
        try {
            return m.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Class<?> classForNameOrNull(String mojangName) {
        try {
            return Class.forName(mojangName);
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.classForNameOrNull: " + mojangName, t);
            return null;
        }
    }

    private ResourceLocation createResourceLocation(String namespace, String path) {
        try {
            return new ResourceLocation(namespace, path);
        } catch (Throwable t1) {
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.createResourceLocation: constructor trực tiếp lỗi", t1);
        }
        try {
            for (Constructor<?> ctor : ResourceLocation.class.getDeclaredConstructors()) {
                Class<?>[] p = ctor.getParameterTypes();
                if (p.length == 2 && p[0] == String.class && p[1] == String.class) {
                    ctor.setAccessible(true);
                    return (ResourceLocation) ctor.newInstance(namespace, path);
                }
            }
        } catch (Throwable t2) {
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.createResourceLocation: fallback constructor lỗi", t2);
        }
        try {
            for (Method m : ResourceLocation.class.getMethods()) {
                if (Modifier.isStatic(m.getModifiers())
                        && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class
                        && ResourceLocation.class.isAssignableFrom(m.getReturnType())) {
                    Object result = m.invoke(null, namespace + ":" + path);
                    if (result != null) return (ResourceLocation) result;
                }
            }
        } catch (Throwable t3) {
            PayBotDebug.logSwallowed("ForgeVersionAdapterModern.createResourceLocation: fallback static factory lỗi", t3);
        }
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
