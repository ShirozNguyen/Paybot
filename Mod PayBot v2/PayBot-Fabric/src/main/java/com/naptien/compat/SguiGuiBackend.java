package com.naptien.compat;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

/**
 * SguiGuiBackend — Implementation của GuiBackend sử dụng SGUI 1.x API.
 * Tương thích với Minecraft 1.20.x và 1.21.x.
 */
public class SguiGuiBackend implements GuiBackend {

    private final SimpleGui gui;

    public SguiGuiBackend(ScreenHandlerType<?> type, ServerPlayerEntity player, String title) {
        // SimpleGui(type, player, manipulatePlayerSlots)
        this.gui = new SimpleGui(type, player, false);
        this.gui.setTitle(Text.literal(title));
    }

    @Override
    public void open() {
        gui.open();
    }

    @Override
    public void close() {
        gui.close();
    }

    @Override
    public void setSlot(int slot, ItemStack item, String name, List<String> lore, Runnable onClick) {
        GuiElementBuilder builder = new GuiElementBuilder(item.getItem())
                .setName(Text.literal(name));
        if (lore != null) {
            for (String line : lore) {
                builder.addLoreLine(Text.literal(line));
            }
        }
        if (onClick != null) {
            builder.setCallback((index, type, action, g) -> onClick.run());
        }
        gui.setSlot(slot, builder.build());
    }

    @Override
    public int getSize() {
        return gui.getSize();
    }

    @Override
    public void fillGlass() {
        ItemStack glassItem = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        GuiElementBuilder builder = new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE)
                .setName(Text.literal(" "));
        
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getSlot(i) == null) {
                gui.setSlot(i, builder.build());
            }
        }
    }
}
