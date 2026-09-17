// v5.5.5 Part 89: Dung ItemStack return va broadcastChanges cho 1.16.x
// v5.5.5 Part 80: Return ItemStack in clicked() for MC 1.16.5
// v5.5.5 Part 79: Remove sendAllDataToRemote in fabric-1.16.5 VanillaGuiBackend
// v5.5.5 Part 74: Fix clicked return paths for 1.16.5
// v5.5.5 Part 73: Fix clicked return type and container sync for 1.16.5
package com.paybot.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import com.paybot.utils.ItemTagCompat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VanillaGuiBackend — Implementation GUI bằng Menu / Container gốc của Vanilla Minecraft (MC 1.14.4+).
 * 
 * 100% Zero External Dependencies (Không dùng SGUI hay thư viện ngoài).
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
                        public net.minecraft.world.item.ItemStack clicked(int slotId, int button, net.minecraft.world.inventory.ClickType clickType, net.minecraft.world.entity.player.Player player) {
                            // Chặn triệt để tất cả các loại click nguy hiểm có thể di chuyển / rút / thu gom / vứt item
                            if (clickType == net.minecraft.world.inventory.ClickType.QUICK_MOVE
                                    || clickType == net.minecraft.world.inventory.ClickType.PICKUP_ALL
                                    || clickType == net.minecraft.world.inventory.ClickType.SWAP
                                    || clickType == net.minecraft.world.inventory.ClickType.CLONE
                                    || clickType == net.minecraft.world.inventory.ClickType.THROW) {
                                if (player instanceof ServerPlayer sp) {
                                    sp.containerMenu.broadcastChanges();
                                }
                                return net.minecraft.world.item.ItemStack.EMPTY;
                            }

                            // Chặn tất cả các click trực tiếp vào slot thuộc GUI container
                            if (slotId >= 0 && slotId < size) {
                                Runnable handler = clickHandlers.get(slotId);
                                if (handler != null) {
                                    try {
                                        handler.run();
                                    } catch (Throwable t) {
                                        com.paybot.PayBotMod.LOGGER.error("[VanillaGuiBackend] Lỗi xử lý click slot {}: {}", slotId, t.getMessage());
                                    }
                                }
                                if (player instanceof ServerPlayer sp) {
                                    sp.containerMenu.broadcastChanges();
                                }
                                return net.minecraft.world.item.ItemStack.EMPTY;
                            }

                            // Với slot túi đồ cá nhân bên dưới (slotId >= size), cho phép tương tác bình thường nhưng đồng bộ dữ liệu
                            ItemStack res = super.clicked(slotId, button, clickType, player);
                            if (player instanceof ServerPlayer sp) {
                                sp.containerMenu.broadcastChanges();
                            }
                            return res != null ? res : net.minecraft.world.item.ItemStack.EMPTY;
                        }
                    };
                    return menu;
                },
                title
        ));

        // Ép đồng bộ dữ liệu GUI (item names, lore, components) tức thời về client ngay sau khi mở màn hình
        if (player.containerMenu != null) {
            player.containerMenu.broadcastChanges();
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
