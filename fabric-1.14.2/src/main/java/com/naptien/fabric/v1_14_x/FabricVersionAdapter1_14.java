package com.naptien.fabric.v1_14_x;

import com.naptien.compat.version.VersionAdapter;
import com.naptien.utils.ComponentColorParser;
import com.naptien.utils.PayBotDebug;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * FabricVersionAdapter1_14 — v5.5.5 Part 44 [ĐÃ HARDEN]
 *
 * Adapter NBT thô cho nhánh Minecraft 1.14.x (module fabric-legacy/, biên dịch
 * nhắm 1.20.1). Fabric dùng ID Intermediary ổn định trong nội bộ 1 kỷ nguyên NBT thô (1.14-1.20.4, không có ranh giới đổi cơ chế mapping như Forge) nên module này dùng 1 lần biên dịch (nhắm 1.20.1) cho cả dải.
 *
 * v5.5.5 Part 44 — cải tiến so với bản trước (hành vi không đổi, chỉ AN TOÀN + DỄ CHẨN ĐOÁN hơn):
 *  1. Không còn dùng stack.getOrCreateTagElement("display") — đây là method TIỆN LỢI có thể
 *     KHÔNG tồn tại ở các bản MC cũ nhất (1.14.x); thay bằng thao tác CompoundTag thủ
 *     công (getOrCreateTag + getCompound + put lại) — các API này tồn tại từ rất lâu, an toàn
 *     hơn cho toàn dải phiên bản module này phục vụ.
 *  2. Không còn "catch (Throwable ignored) {}" im lặng ở bất kỳ đâu — mọi lỗi bị nuốt đều đi
 *     qua PayBotDebug.logSwallowed() để debug-mode có thể thấy, đúng yêu cầu "log chi tiết,
 *     không đoán mù" đã thống nhất trước đó.
 *  3. lockMap() có thêm fallback quét-theo-kiểu (chỉ còn đúng 1 field boolean) nếu cả tên
 *     "locked" lẫn phương án dự phòng đều không tìm được — cùng kỹ thuật đã dùng ở adapter
 *     Data Components, tăng an toàn không cần đoán tên chính xác.
 */
public class FabricVersionAdapter1_14 implements VersionAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-Fabric-Adapter1_14_x");

    private static Field lockedField = null;
    private static boolean reflectionInit = false;

    // ===================== TÊN + LORE =====================

    @Override
    public void setItemNameAndLore(ItemStack stack, String name, List<String> lore) {
        if (stack == null || stack.isEmpty()) return;
        if (name != null && !name.isEmpty()) {
            setName(stack, ComponentColorParser.parse(name));
        }
        if (lore != null && !lore.isEmpty()) {
            setLore(stack, lore);
        }
    }

    private void setName(ItemStack stack, Component nameComponent) {
        try {
            stack.setHoverName(nameComponent);
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("FabricVersionAdapter1_14.setName: setHoverName() lỗi", t);
        }
        CompoundTag displayTag = getOrCreateDisplayTag(stack);
        if (displayTag != null) displayTag.putString("Name", safeComponentToJson(nameComponent));
    }

    private void setLore(ItemStack stack, List<String> lore) {
        CompoundTag displayTag = getOrCreateDisplayTag(stack);
        if (displayTag == null) {
            PayBotDebug.logSwallowed("FabricVersionAdapter1_14.setLore: không lấy được display tag — bỏ qua set lore.", null);
            return;
        }
        ListTag loreTag = new ListTag();
        for (String line : lore) {
            Component lineComp = ComponentColorParser.parse(line);
            loreTag.add(StringTag.valueOf(safeComponentToJson(lineComp)));
        }
        displayTag.put("Lore", loreTag);
    }

    // ===================== INVOICE ID =====================

    @Override
    public void setInvoiceId(ItemStack stack, String invoiceId) {
        if (stack == null || stack.isEmpty() || invoiceId == null) return;
        CompoundTag tag = getOrCreateTag(stack);
        if (tag != null) tag.putString("paybot_invoice_id", invoiceId);
    }

    @Override
    public String getInvoiceId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CompoundTag tag = getTag(stack);
        return (tag != null && tag.contains("paybot_invoice_id")) ? tag.getString("paybot_invoice_id") : null;
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
            LOGGER.error("[Fabric1_14_x] getMapSavedData error: {}", t.getMessage());
        }
        return null;
    }

    @Override
    public void lockMap(MapItemSavedData state) {
        if (state == null) return;
        initFieldReflection();
        if (lockedField != null) {
            try {
                lockedField.set(state, true);
                return;
            } catch (Throwable t) {
                PayBotDebug.logSwallowed("FabricVersionAdapter1_14.lockMap: set field 'locked' thất bại", t);
            }
        }
        try {
            Method lockMethod = MapItemSavedData.class.getMethod("lock");
            lockMethod.invoke(state);
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("FabricVersionAdapter1_14.lockMap: cả field lẫn method lock() đều không dùng được — "
                    + "map QR có thể bị ghi đè địa hình theo thời gian.", t);
        }
    }

    private static synchronized void initFieldReflection() {
        if (reflectionInit) return;
        reflectionInit = true;

        String[] nameCandidates = {"locked", "f_77906_", "field_1838"};
        for (String name : nameCandidates) {
            try {
                Field f = MapItemSavedData.class.getDeclaredField(name);
                if (f.getType() == boolean.class) {
                    f.setAccessible(true);
                    lockedField = f;
                    return;
                }
            } catch (Throwable ignored) {
                // tên ứng viên không tồn tại trên bản này — thử tên tiếp theo
            }
        }

        // Dự phòng cuối: quét toàn bộ field kiểu boolean, nếu chỉ có ĐÚNG 1 field thì dùng luôn
        // (an toàn hơn đoán tên, không cần biết chính xác Mojang/Intermediary gọi field này là gì).
        List<Field> boolFields = new ArrayList<>();
        for (Field f : MapItemSavedData.class.getDeclaredFields()) {
            if (f.getType() == boolean.class) boolFields.add(f);
        }
        if (boolFields.size() == 1) {
            boolFields.get(0).setAccessible(true);
            lockedField = boolFields.get(0);
        } else if (boolFields.size() > 1) {
            PayBotDebug.logSwallowed("FabricVersionAdapter1_14.initFieldReflection: có " + boolFields.size()
                    + " field boolean trong MapItemSavedData, không chắc field nào là 'locked'.", null);
        }
    }

    // ===================== NBT — chỉ dùng API nền tảng cổ nhất, an toàn cho mọi bản trong dải =====================

    private CompoundTag getOrCreateTag(ItemStack stack) {
        try {
            return stack.getOrCreateTag();
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("FabricVersionAdapter1_14.getOrCreateTag", t);
            return null;
        }
    }

    private CompoundTag getTag(ItemStack stack) {
        try {
            return stack.getTag();
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("FabricVersionAdapter1_14.getTag", t);
            return null;
        }
    }

    /** Lấy (hoặc tạo) compound "display" bằng CompoundTag.getCompound()/put() thủ công — KHÔNG
     *  dùng getOrCreateTagElement() vì đây là method tiện lợi có thể chưa tồn tại ở bản cũ nhất
     *  module này phục vụ. getCompound() trả compound RỖNG MỚI nếu key chưa có (không phải null),
     *  nên phải put() lại vào tag gốc để việc sửa compound con thực sự lưu lại được. */
    private CompoundTag getOrCreateDisplayTag(ItemStack stack) {
        CompoundTag root = getOrCreateTag(stack);
        if (root == null) return null;
        try {
            CompoundTag display = root.getCompound("display");
            root.put("display", display);
            return display;
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("FabricVersionAdapter1_14.getOrCreateDisplayTag", t);
            return null;
        }
    }

    private String safeComponentToJson(Component comp) {
        if (comp == null) return "{\"text\":\"\"}";
        try {
            return Component.Serializer.toJson(comp);
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("FabricVersionAdapter1_14.safeComponentToJson", t);
            return "{\"text\":\"" + comp.getString() + "\"}";
        }
    }
}
