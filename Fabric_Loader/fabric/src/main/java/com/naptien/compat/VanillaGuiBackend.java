package com.naptien.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import com.naptien.utils.ItemTagCompat;
import com.naptien.utils.PayBotDebug;

import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VanillaGuiBackend (26.x) — Implementation GUI bằng Menu / Container gốc của Vanilla Minecraft.
 *
 * 100% Zero External Dependencies (Không dùng SGUI hay thư viện ngoài).
 *
 * [FIX — audit v5.5.5 Part 58, xác minh qua primer chính thức docs.neoforged.net/primer/docs/26.1/
 * ("AbstractContainerMenu#clicked now takes in a ContainerInput instead of a ClickType") và 1 mod
 * thật đã port (takusan.negitoro.dev, so sánh trực tiếp source 1.21.11 → 26.1)]
 * Kể từ MC 26.1, chữ ký {@code AbstractContainerMenu.clicked(int, int, ClickType, Player)} đã đổi
 * thành {@code clicked(int, int, ContainerInput, Player)} — {@code ContainerInput} là kiểu MỚI bọc
 * quanh {@code ClickType} gốc. Không tìm được tài liệu chính thức liệt kê chính xác tên accessor
 * bên trong {@code ContainerInput} (record) dù đã tra nhiều nguồn — thay vì đoán tên, dùng
 * reflection tổng quát qua {@code getRecordComponents()} (an toàn tuyệt đối vì đây LÀ record,
 * luôn có metadata component chuẩn của Java, không phụ thuộc tên cụ thể) để tìm component nào
 * mang kiểu {@code ClickType} và lấy giá trị đó ra, tái sử dụng nguyên vẹn logic chặn click nguy
 * hiểm đã có. Nếu vì lý do bất khả kháng KHÔNG tìm được component ClickType nào (cấu trúc
 * ContainerInput đổi khác hẳn so với mọi nguồn đã tra) — CHẶN LẠI theo hướng an toàn (fail-safe,
 * không fail-open) thay vì cho qua, vì đây là GUI thanh toán, thà chặn nhầm còn hơn để lọt dupe-item.
 */
public class VanillaGuiBackend implements GuiBackend {

    private final ServerPlayer player;
    private final Component title;
    private final int size;
    private final SimpleContainer container;
    private final Map<Integer, Runnable> clickHandlers = new HashMap<>();

    public VanillaGuiBackend(ServerPlayer player, Component title, int size) {
        this.player = player;
        this.title = title;
        this.size = size;
        this.container = new SimpleContainer(size);
    }

    /**
     * Trích ClickType ra khỏi ContainerInput bằng reflection tổng quát (không giả định tên field).
     * Trả về null nếu không tìm được — gọi nơi dùng PHẢI coi null là "chặn lại" (fail-safe).
     */
    private static ClickType extractClickType(Object containerInput) {
        if (containerInput == null) return null;
        try {
            for (RecordComponent rc : containerInput.getClass().getRecordComponents()) {
                if (rc.getType().equals(ClickType.class)) {
                    Object value = rc.getAccessor().invoke(containerInput);
                    if (value instanceof ClickType ct) {
                        return ct;
                    }
                }
            }
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("VanillaGuiBackend: reflection đọc ContainerInput thất bại", t);
        }
        return null;
    }

    @Override
    public void open() {
        MenuType<?> menuType = switch (size) {
            case 9  -> MenuType.GENERIC_9x1;
            case 18 -> MenuType.GENERIC_9x2;
            case 27 -> MenuType.GENERIC_9x3;
            case 36 -> MenuType.GENERIC_9x4;
            case 45 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };

        player.openMenu(new SimpleMenuProvider(
                (containerId, playerInventory, p) -> {
                    ChestMenu menu = new ChestMenu(menuType, containerId, playerInventory, container, size / 9) {
                        @Override
                        public void clicked(int slotId, int button, net.minecraft.world.inventory.ContainerInput containerInput, net.minecraft.world.entity.player.Player player) {
                            ClickType clickType = extractClickType(containerInput);

                            // clickType == null nghĩa là không trích được từ ContainerInput — chặn
                            // an toàn (fail-safe) thay vì cho qua, vì đây là GUI thanh toán.
                            if (clickType == null
                                    || clickType == ClickType.QUICK_MOVE
                                    || clickType == ClickType.PICKUP_ALL
                                    || clickType == ClickType.SWAP
                                    || clickType == ClickType.CLONE
                                    || clickType == ClickType.THROW) {
                                if (player instanceof ServerPlayer sp) {
                                    sp.containerMenu.sendAllDataToRemote();
                                }
                                return;
                            }

                            // Chặn tất cả các click trực tiếp vào slot thuộc GUI container
                            if (slotId >= 0 && slotId < size) {
                                Runnable handler = clickHandlers.get(slotId);
                                if (handler != null) {
                                    try {
                                        handler.run();
                                    } catch (Throwable t) {
                                        com.naptien.PayBotMod.LOGGER.error("[VanillaGuiBackend] Lỗi xử lý click slot {}: {}", slotId, t.getMessage());
                                    }
                                }
                                if (player instanceof ServerPlayer sp) {
                                    sp.containerMenu.sendAllDataToRemote();
                                }
                                return; // Hủy hoàn toàn xử lý Vanilla đối với GUI item
                            }

                            // Với slot túi đồ cá nhân bên dưới (slotId >= size), cho phép tương tác bình thường nhưng đồng bộ dữ liệu
                            super.clicked(slotId, button, containerInput, player);
                            if (player instanceof ServerPlayer sp) {
                                sp.containerMenu.sendAllDataToRemote();
                            }
                        }
                    };
                    return menu;
                },
                title
        ));

        // Ép đồng bộ dữ liệu GUI (item names, lore, components) tức thời về client ngay sau khi mở màn hình
        if (player.containerMenu != null) {
            player.containerMenu.sendAllDataToRemote();
        }
    }

    @Override
    public void close() {
        player.closeContainer();
    }

    @Override
    public void setSlot(int slot, ItemStack item, String name, List<String> lore, Runnable onClick) {
        if (slot < 0 || slot >= size) return;
        
        ItemStack stack = item.copy();
        ItemTagCompat.setItemNameAndLore(stack, name, lore);
        container.setItem(slot, stack);

        if (onClick != null) {
            clickHandlers.put(slot, onClick);
        } else {
            clickHandlers.remove(slot);
        }

        // Ép đồng bộ tức thì slot GUI mới gán Tên + Lore về Client
        if (player != null && player.containerMenu != null) {
            try {
                player.containerMenu.sendAllDataToRemote();
                player.containerMenu.broadcastChanges();
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public void fillGlass() {
        ItemStack glass = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        ItemTagCompat.setItemNameAndLore(glass, " ", null);

        for (int i = 0; i < size; i++) {
            if (container.getItem(i).isEmpty()) {
                container.setItem(i, glass.copy());
            }
        }
    }
}
