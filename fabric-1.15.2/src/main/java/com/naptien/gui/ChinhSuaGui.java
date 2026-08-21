package com.naptien.gui;

import com.naptien.PayBotMod;
import com.naptien.compat.GuiBackend;
import com.naptien.compat.GuiFactory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * ChinhSuaGui (Fabric) — SGUI chỉnh sửa mệnh giá thưởng (qua GuiBackend).
 *
 * Changelog:
 *   v4.0.1-fabric — initial
 *   v4.0.4-fabric — fix SGUI 1.6.1: ClickCallback void
 *   v5.2.0        — Refactor sang GuiBackend hỗ trợ đa phiên bản Minecraft.
 */
public class ChinhSuaGui {
    public record SlotInfo(int denom, String type) {}
    private static final int[] DENOMS = GuiUtil.DENOMS;
    private static final int CARD_START=9, BANK_START=27, CLOSE_SLOT=49;

    public static SlotInfo getSlotInfo(int slot) {
        if (slot>=CARD_START && slot<CARD_START+DENOMS.length) return new SlotInfo(DENOMS[slot-CARD_START],"card");
        if (slot>=BANK_START && slot<BANK_START+DENOMS.length) return new SlotInfo(DENOMS[slot-BANK_START],"bank");
        return null;
    }

    public static void open(ServerPlayer player) {
        PayBotMod mod = PayBotMod.getInstance();
        GuiBackend gui = GuiFactory.create(MenuType.GENERIC_9x6, player, "§6§lChỉnh sửa mệnh giá nạp");
        gui.setSlot(4, new ItemStack(Items.YELLOW_STAINED_GLASS_PANE), "§6§l✦ Nạp Thẻ Cào ✦", null, null);
        gui.setSlot(22, new ItemStack(Items.LIGHT_BLUE_STAINED_GLASS_PANE), "§b§l✦ Nạp Ngân Hàng ✦", null, null);
        for (int i=0;i<DENOMS.length;i++) addDenomSlot(gui, player, mod, DENOMS[i], CARD_START+i, "card");
        for (int i=0;i<DENOMS.length;i++) addDenomSlot(gui, player, mod, DENOMS[i], BANK_START+i, "bank");
        gui.setSlot(CLOSE_SLOT, GuiUtil.getCloseItem(), "§c§lĐóng", List.of("§7Nhấn để đóng"), () -> gui.close());
        GuiUtil.fillGlass(gui);
        gui.open();
    }

    private static void addDenomSlot(GuiBackend gui, ServerPlayer player, PayBotMod mod, int denom, int slot, String type) {
        String section = "card".equals(type) ? "denom-rewards-card" : "denom-rewards-bank";
        String cmd = mod.getConfig().getString(section+"."+denom+".cmd","").trim();
        String amt = mod.getConfig().getString(section+"."+denom+".amt","").trim();
        boolean configured = !cmd.isEmpty();
        String label = ("card".equals(type)?"§6[Thẻ] ":"§b[Bank] ")+"§l"+GuiUtil.formatVnd(denom)+" VND";
        
        List<String> lore = new ArrayList<>();
        if (configured) { 
            lore.add("§7Lệnh: §a"+cmd); 
            lore.add("§7Số thưởng: §a"+(amt.isEmpty()?"(chưa đặt)":amt)); 
        } else {
            lore.add("§cChưa cấu hình — Click để thiết lập");
        }
        lore.add("§eClick để chỉnh sửa");

        gui.setSlot(slot, new ItemStack(configured?Items.GREEN_WOOL:Items.RED_WOOL), label, lore, () -> {
            GuiSession s = GuiSession.get(player.getUUID());
            s.editDenom = denom; s.editType = type;
            s.stage = "card".equals(type) ? GuiSession.Stage.EDIT_WAIT_CMD_CARD : GuiSession.Stage.EDIT_WAIT_CMD_BANK;
            gui.close();
            player.sendSystemMessage(Component.literal("§6[PayBot] §eCấu hình §b"+GuiUtil.formatDenom(denom)+" §e("+type+")"));
            player.sendSystemMessage(Component.literal("§6Nhập §blệnh thưởng §e(không cần /):  §7§o(cancel để huỷ)"));
            player.sendSystemMessage(Component.literal("§7Biến: §b[playername] §7→ tên player | §b[amount] §7→ số lượng"));
            player.sendSystemMessage(Component.literal("§7VD: §feco give [playername] [amount]"));
        });
    }
}
