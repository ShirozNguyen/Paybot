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
import com.paybot.utils.PayBotDebug;

import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VanillaGuiBackend (26.x) — Implementation GUI bằng Menu / Container gốc của Vanilla Minecraft.
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

    private static Object extractClickType(Object containerInput) {
        if (containerInput == null) return null;
        try {
            for (RecordComponent rc : containerInput.getClass().getRecordComponents()) {
                if (rc.getType().getSimpleName().contains("ClickType") || rc.getType().getSimpleName().contains("SlotActionType")) {
                    return rc.getAccessor().invoke(containerInput);
                }
            }
        } catch (Throwable t) {
            PayBotDebug.logSwallowed("VanillaGuiBackend: reflection đọc ContainerInput thất bại", t);
        }
        return null;
    }

    private static boolean isRestrictedClickType(Object containerInput) {
        Object ct = extractClickType(containerInput);
        if (ct == null) return true; // fail-safe
        String name = ct.toString();
        return name.contains("QUICK_MOVE") || name.contains("PICKUP_ALL") || name.contains("SWAP") || name.contains("CLONE") || name.contains("THROW");
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
                            if (isRestrictedClickType(containerInput)) {
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
                                        com.paybot.PayBotMod.LOGGER.error("[VanillaGuiBackend] Lỗi xử lý click slot {}: {}", slotId, t.getMessage());
                                    }
                                }
                                if (player instanceof ServerPlayer sp) {
                                    sp.containerMenu.sendAllDataToRemote();
                                }
                                return;
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
        ItemStack glass = com.paybot.gui.ModernItemProvider.createStack("gray_stained_glass_pane");
        ItemTagCompat.setItemNameAndLore(glass, " ", null);

        for (int i = 0; i < size; i++) {
            if (container.getItem(i).isEmpty()) {
                container.setItem(i, glass.copy());
            }
        }
    }
}
