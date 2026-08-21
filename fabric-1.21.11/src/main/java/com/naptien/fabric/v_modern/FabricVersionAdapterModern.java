package com.naptien.fabric.v_modern;

import com.naptien.compat.version.VersionAdapter;
import com.naptien.utils.ComponentColorParser;
import com.naptien.utils.PayBotDebug;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
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
 * FabricVersionAdapterModern — v5.5.5 Part 44 [VIẾT LẠI TOÀN BỘ, đổi chiến lược gốc]
 *
 * Phục vụ TOÀN BỘ kỷ nguyên Data Components trên Fabric: MC 1.20.5 → 1.21.11 (module
 * fabric-modern/ biên dịch nhắm 1.21.1, nhưng code KHÔNG hardcode theo bản này — xem giải
 * thích chiến lược bên dưới). Bản 26.1+ (Mojang bỏ obfuscation hoàn toàn) dùng module riêng
 * fabric-26x/, chưa làm ở Part 44.
 *
 * ================= BỐI CẢNH — VÌ SAO PHẢI VIẾT LẠI TOÀN BỘ =================
 *
 * Bản trước (FabricVersionAdapter1_21, xem LOG.md Part 39-43) dùng 2 cách đều SAI, đã xác
 * nhận bằng log crash thật Shiroz gửi (server Fabric 1.21.1, debug-mode bật):
 *
 * 1) Gọi thẳng stack.setHoverName(nameComp) — code biên dịch trong module này bị Architectury
 *    Loom remap từ tên Mojang sang Intermediary DỰA TRÊN mapping của MC 1.20.1 (minecraft_version
 *    của module lúc đó dùng chung 1.20.1 cho mọi adapter). Data Components (1.20.5) viết lại
 *    hẳn cách lưu tên/lore item khiến ID Intermediary của setHoverName ĐỔI ở các bản sau —
 *    log thật: NoSuchMethodError 'method_7977' không tồn tại trên runtime 1.21.1 thật.
 *
 * 2) Fallback reflection đoán số hiệu class (class_9331 cho DataComponentTypes) — SAI, class
 *    đó có thật trên 1.21.1 nhưng không phải DataComponentTypes (chỉ 4 field, không phải ~80+).
 *
 * 3) PHÁT HIỆN THÊM (chưa từng nêu trước đây): fallback đó còn tìm method set/get trên
 *    ItemStack bằng SO TÊN ("set"/"get") — nhưng trên Fabric production, MỌI tên method nội
 *    bộ Minecraft đều là method_XXXXX (Intermediary), không method nào tên thật là "set"/"get"
 *    → setComponentMethod/getComponentMethod LUÔN null, nhánh dự phòng vô dụng dù (2) có đúng.
 *
 * ================= CHIẾN LƯỢC MỚI =================
 *
 * fabric-modern/ giờ là module RIÊNG, biên dịch nhắm 1.21.1 — nhưng vẫn KHÔNG gọi thẳng bất kỳ
 * method nào có khả năng đổi bytecode giữa các bản trong dải 1.20.5-1.21.11 (an toàn hơn, và
 * quan trọng hơn: để 1 module vẫn dùng tốt cho nhiều bản DataComponents khác nhau, không riêng
 * 1.21.1). Chỉ dựa vào 3 thứ ổn định thật sự:
 *
 *  1. Class nền tảng tồn tại từ trước 1.20.1 (BuiltInRegistries, ResourceLocation, ItemStack,
 *     Component, CompoundTag...) — import trực tiếp, KHÔNG qua reflection tên chuỗi.
 *  2. Registry THẬT của game + khoá chuỗi ("minecraft:custom_name", "minecraft:lore",
 *     "minecraft:custom_data") — đây là DỮ LIỆU GAME do Mojang đảm bảo ổn định giữa các bản,
 *     khác hẳn tên định danh trong code (class_XXXX/method_XXXX) vốn có thể đổi mỗi bản.
 *  3. So khớp method theo CẤU TRÚC (kiểu tham số/kiểu trả về) — không so theo TÊN, vì tên thật
 *     trên runtime luôn là method_XXXXX bất kể Mojang gọi nó là gì.
 *
 * Sau MỌI lần set (tên, lore, invoice id), code ĐỌC LẠI để tự xác minh — không tin mù việc
 * "tìm được 1 class có constructor khớp" như bản trước (đó chính là lý do bản trước tưởng đã
 * xong nhưng thực ra vẫn fail âm thầm ở bước setComponentMethod).
 *
 * v5.5.5 Part 44b: đã tra Javadoc chính thức (NeoForge 1.21.1-21.1.216, CraftTweaker docs)
 * xác nhận đúng tên + cấu trúc net.minecraft.world.item.component.ItemLore (record 2 field
 * lines/styledLines, có static factory ItemLore.of()) và CustomData (field CompoundTag riêng,
 * static factory CustomData.of()/update()/set()) — không còn đoán mù tên class 2 chỗ này nữa,
 * chỉ còn phụ thuộc duy nhất vào việc các tên/cấu trúc này KHÔNG đổi tiếp ở các bản MC khác
 * trong dải 1.20.5-1.21.11 mà module này phục vụ (rủi ro thấp, đây là API tương đối mới/ổn định).
 */
public class FabricVersionAdapterModern implements VersionAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-Fabric-Adapter-Modern");

    private Object dataComponentTypeRegistry = null;
    private Method registryGetMethod = null;

    private Object customNameComponentType = null;
    private Object loreComponentType = null;
    private Object customDataComponentType = null;

    private Method setComponentMethod = null; // ItemStack.set(DataComponentType<T>, T) — tìm theo cấu trúc
    private Method getComponentMethod = null; // ItemStack.get(DataComponentType<T>) — tìm theo cấu trúc

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
            LOGGER.error("[FabricModern] Không lấy được BẤT KỲ DataComponentType nào qua registry — "
                    + "tên/lore/invoice-id sẽ KHÔNG hoạt động trên bản MC này. Bật debug-mode để xem chi tiết.");
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.ensureInitialized: không có anchor để dò set/get method", null);
            return;
        }

        resolveSetGetMethods(anchor);

        if (setComponentMethod == null || getComponentMethod == null) {
            LOGGER.error("[FabricModern] Có DataComponentType nhưng KHÔNG tìm được method set/get tương ứng "
                    + "trên ItemStack bằng so khớp cấu trúc — tình huống bất thường, cần log debug-mode.");
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.ensureInitialized: setComponentMethod="
                    + (setComponentMethod != null) + ", getComponentMethod=" + (getComponentMethod != null), null);
        } else {
            LOGGER.info("[FabricModern] Sẵn sàng — setComponentMethod={}, getComponentMethod={}",
                    setComponentMethod.getName(), getComponentMethod.getName());
        }
    }

    /** Dò registry DataComponentType thật trong BuiltInRegistries — không đoán tên field. */
    private void resolveRegistry() {
        Field[] fields;
        try {
            fields = BuiltInRegistries.class.getFields();
        } catch (Throwable t) {
            LOGGER.error("[FabricModern] Không đọc được field của BuiltInRegistries: {}", t.getMessage());
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.resolveRegistry: BuiltInRegistries.class.getFields()", t);
            return;
        }

        ResourceLocation testCustomName = createResourceLocation("minecraft", "custom_name");
        ResourceLocation testLore = createResourceLocation("minecraft", "lore");
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

            // Cả 2 khoá đặc trưng riêng của registry data_component_type đều khớp trên CÙNG 1
            // field, và trả về 2 đối tượng khác nhau cùng kiểu — gần như chắc chắn đúng registry,
            // khó trùng ngẫu nhiên với registry khác (item/block/... không có key "custom_name").
            if (gotCustomName.getClass() != gotLore.getClass()) continue;
            if (gotCustomName.equals(gotLore)) continue;

            dataComponentTypeRegistry = value;
            registryGetMethod = getMethod;
            LOGGER.info("[FabricModern] Xác định registry DataComponentType qua field '{}' (kiểu {}) — "
                    + "đã kiểm tra {} field ứng viên.", f.getName(), value.getClass().getName(), checked);
            return;
        }

        LOGGER.error("[FabricModern] Quét hết {} field ứng viên của BuiltInRegistries mà không tìm được registry "
                + "nào khớp cả 'minecraft:custom_name' lẫn 'minecraft:lore'.", checked);
        PayBotDebug.logSwallowed("FabricVersionAdapterModern.resolveRegistry: quét " + checked + " ứng viên, không khớp", null);
    }

    /** Tìm method "get theo ResourceLocation" trên 1 registry — so khớp cấu trúc, không so tên. */
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
            return m; // ưu tiên method trả trực tiếp (không bọc Optional)
        }
        return fallbackOptional;
    }

    /** Lấy 1 DataComponentType thật từ registry đã xác định, theo đường dẫn "minecraft:<path>". */
    private Object getDataComponentType(String path) {
        if (dataComponentTypeRegistry == null || registryGetMethod == null) return null;
        ResourceLocation rl = createResourceLocation("minecraft", path);
        if (rl == null) return null;
        return unwrapOptional(invokeSilently(registryGetMethod, dataComponentTypeRegistry, rl));
    }

    /** Dò method set(2 tham số)/get(1 tham số) trên ItemStack theo CẤU TRÚC, dùng anchor làm mẫu kiểu. */
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
            LOGGER.warn("[FabricModern] Số method khớp cấu trúc không rõ ràng 1-1 (set: {} ứng viên, get: {} "
                    + "ứng viên) — đã chọn ứng viên hợp lý nhất, kiểm tra debug-mode nếu vẫn lỗi.",
                    setCandidates, getCandidates);
        }
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
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.setName: thiếu customNameComponentType/set-get method, dùng fallback setHoverName trực tiếp", null);
            trySetHoverNameFallback(stack, nameComp);
            return;
        }
        invokeSilently(setComponentMethod, stack, customNameComponentType, nameComp);
        Object verify = invokeSilently(getComponentMethod, stack, customNameComponentType);
        if (verify == null) {
            LOGGER.warn("[FabricModern] Set tên qua registry+reflection nhưng đọc lại ra null — thử fallback setHoverName().");
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.setName: set qua reflection không xác minh được", null);
            trySetHoverNameFallback(stack, nameComp);
        }
    }

    /** Fallback CHỈ dùng khi đường chính (registry+reflection) thất bại — rủi ro NoSuchMethodError
     *  nếu bytecode setHoverName() đổi giữa bản biên dịch (1.21.1) và bản đang chạy thật. */
    private void trySetHoverNameFallback(ItemStack stack, Component nameComp) {
        try {
            stack.setHoverName(nameComp);
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.trySetHoverNameFallback: setHoverName() trực tiếp cũng lỗi", t);
        }
    }

    private void setLore(ItemStack stack, List<Component> componentList) {
        if (loreComponentType == null || setComponentMethod == null || getComponentMethod == null) {
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.setLore: thiếu loreComponentType/set-get method — bỏ qua set lore.", null);
            return;
        }

        // Phương án 1: truyền thẳng List<Component> — rẻ, thử trước dù khả năng thành công thấp.
        if (attemptLoreValue(stack, componentList, "List<Component> trực tiếp")) return;

        // Phương án 2: dựng wrapper qua reflection từ tên Mojang khả dĩ nhất.
        Class<?> itemLoreClass = resolveClassEitherWay("net.minecraft.world.item.component.ItemLore");
        if (itemLoreClass != null) {
            Object wrapper = buildListWrapperInstance(itemLoreClass, componentList);
            if (wrapper != null && attemptLoreValue(stack, wrapper, "wrapper " + itemLoreClass.getName())) return;
        }

        LOGGER.warn("[FabricModern] KHÔNG set được lore bằng bất kỳ cách nào đã thử — xem log debug-mode phía trên.");
    }

    private boolean attemptLoreValue(ItemStack stack, Object value, String description) {
        try {
            invokeSilently(setComponentMethod, stack, loreComponentType, value);
            Object verify = invokeSilently(getComponentMethod, stack, loreComponentType);
            if (verify != null) {
                LOGGER.info("[FabricModern] Set lore THÀNH CÔNG bằng phương án: {} — đọc lại xác minh: {}", description, safeToString(verify));
                return true;
            }
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.attemptLoreValue: phương án '" + description + "' set xong nhưng đọc lại ra null", null);
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.attemptLoreValue: phương án '" + description + "' lỗi", t);
        }
        return false;
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

    // ===================== INVOICE ID (CustomData) =====================

    @Override
    public void setInvoiceId(ItemStack stack, String invoiceId) {
        if (stack == null || stack.isEmpty() || invoiceId == null) return;
        ensureInitialized();
        if (customDataComponentType == null || setComponentMethod == null || getComponentMethod == null) {
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.setInvoiceId: thiếu customDataComponentType/set-get method", null);
            return;
        }

        CompoundTag newTag = new CompoundTag();
        Object existing = invokeSilently(getComponentMethod, stack, customDataComponentType);
        CompoundTag existingTag = extractCompoundTag(existing);
        if (existingTag != null) newTag = existingTag.copy();
        newTag.putString("paybot_invoice_id", invoiceId);

        if (attemptCustomDataValue(stack, newTag, "CompoundTag trực tiếp")) return;

        Class<?> customDataClass = resolveClassEitherWay("net.minecraft.world.item.component.CustomData");
        if (customDataClass != null) {
            Object wrapper = buildTagWrapperInstance(customDataClass, newTag);
            if (wrapper != null && attemptCustomDataValue(stack, wrapper, "wrapper " + customDataClass.getName())) return;
        }

        PayBotDebug.logSwallowed("FabricVersionAdapterModern.setInvoiceId: không set được bằng bất kỳ cách nào đã thử", null);
    }

    private boolean attemptCustomDataValue(ItemStack stack, Object value, String description) {
        try {
            invokeSilently(setComponentMethod, stack, customDataComponentType, value);
            Object verify = invokeSilently(getComponentMethod, stack, customDataComponentType);
            CompoundTag verifyTag = extractCompoundTag(verify);
            if (verifyTag != null && verifyTag.contains("paybot_invoice_id")) {
                LOGGER.info("[FabricModern] Set CustomData (invoice id) THÀNH CÔNG bằng phương án: {}", description);
                return true;
            }
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.attemptCustomDataValue: phương án '" + description + "' không xác minh được sau khi set", null);
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.attemptCustomDataValue: phương án '" + description + "' lỗi", t);
        }
        return false;
    }

    private Object buildTagWrapperInstance(Class<?> wrapperClass, CompoundTag tag) {
        // Thử constructor nhận CompoundTag trước.
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
        // Thử static factory method nhận CompoundTag, trả về đúng kiểu wrapperClass (vd CustomData.of(tag)).
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
        if (customDataComponentType == null || getComponentMethod == null) return null;
        try {
            Object customDataObj = invokeSilently(getComponentMethod, stack, customDataComponentType);
            CompoundTag tag = extractCompoundTag(customDataObj);
            if (tag != null && tag.contains("paybot_invoice_id")) return tag.getString("paybot_invoice_id");
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.getInvoiceId", t);
        }
        return null;
    }

    // ===================== MAP (QR code) — đã an toàn từ trước, giữ nguyên logic =====================

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
        if (state == null) return;
        Field lockedField = findLockedField();
        if (lockedField == null) {
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.lockMap: không tìm được field 'locked' bằng bất kỳ cách nào — "
                    + "map QR có thể bị ghi đè địa hình theo thời gian trên phiên bản MC này.", null);
            return;
        }
        try {
            lockedField.setAccessible(true);
            lockedField.set(state, true);
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.lockMap: set field thất bại", t);
        }
    }

    private Field findLockedField() {
        try {
            return MapItemSavedData.class.getDeclaredField("locked");
        } catch (Throwable ignored) {
            // không có field tên "locked" thật (production Fabric) — thử tiếp
        }
        try {
            MappingResolver resolver = FabricLoader.getInstance().getMappingResolver();
            String runtimeName = resolver.mapFieldName("intermediary",
                    "net.minecraft.world.level.saveddata.maps.MapItemSavedData", "field_1838", "Z");
            return MapItemSavedData.class.getDeclaredField(runtimeName);
        } catch (Throwable ignored) {
            // ID field_1838 chỉ là best-effort, có thể không đúng bản này — thử tiếp
        }
        List<Field> boolFields = new java.util.ArrayList<>();
        for (Field f : MapItemSavedData.class.getDeclaredFields()) {
            if (f.getType() == boolean.class) boolFields.add(f);
        }
        if (boolFields.size() == 1) {
            LOGGER.info("[FabricModern] lockMap: dùng fallback type-scan, tìm thấy đúng 1 field boolean: {}", boolFields.get(0).getName());
            return boolFields.get(0);
        }
        if (boolFields.size() > 1) {
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.findLockedField: có " + boolFields.size()
                    + " field boolean trong MapItemSavedData, không chắc field nào là 'locked'.", null);
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

    private ResourceLocation createResourceLocation(String namespace, String path) {
        try {
            return new ResourceLocation(namespace, path);
        } catch (Throwable t1) {
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.createResourceLocation: constructor (namespace,path) trực tiếp lỗi", t1);
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
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.createResourceLocation: fallback constructor reflection lỗi", t2);
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
            PayBotDebug.logSwallowed("FabricVersionAdapterModern.createResourceLocation: fallback static factory lỗi", t3);
        }
        return null;
    }

    /** Thử Class.forName trực tiếp (chỉ hoạt động ở dev-env), rồi qua MappingResolver intermediary
     *  (chỉ hoạt động nếu tên truyền vào ĐÃ LÀ intermediary hợp lệ) — giữ như 1 phương án bổ sung
     *  trong chuỗi "thử nhiều phương án + xác minh đọc lại", không còn là điểm phụ thuộc duy nhất. */
    private Class<?> resolveClassEitherWay(String nameOrIntermediary) {
        try {
            return Class.forName(nameOrIntermediary);
        } catch (Throwable ignored) {
            // môi trường production Fabric sẽ luôn lỗi bước này (tên Mojang không load được) — dự kiến
        }
        try {
            MappingResolver resolver = FabricLoader.getInstance().getMappingResolver();
            String runtimeName = resolver.mapClassName("intermediary", nameOrIntermediary);
            return Class.forName(runtimeName);
        } catch (Throwable ignored) {
            // dự kiến lỗi nếu nameOrIntermediary không thực sự là 1 ID intermediary hợp lệ
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
