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
 * CardApiSetupGui (Fabric) — SGUI chọn site card API (qua GuiBackend).
 *
 * Changelog:
 *   v4.0.1-fabric — initial
 *   v4.0.4-fabric — fix SGUI 1.6.1: ClickCallback là void
 *   v5.2.0        — Refactor sang GuiBackend hỗ trợ đa phiên bản Minecraft.
 */
public class CardApiSetupGui {
    // Đồng bộ với bot.py SUPPORTED_SITES — không có gachthefast2.com
    public static final String[] SITES = {"thesieure.com","gachthepro.com","gachthefast.com","gachthe1s.com","card2k.net"};
    private static final int CLOSE_SLOT = 22;

    public static int getSiteBySlot(int slot) {
        int idx = slot - 10;
        return (idx >= 0 && idx < SITES.length) ? idx : -1;
    }
    public static String maskId(String s) {
        if (s==null||s.length()<=6) return "***";
        return s.substring(0,3)+"***"+s.substring(s.length()-3);
    }

    public static void openSiteGui(ServerPlayer player) {
        PayBotMod mod = PayBotMod.getInstance();
        GuiBackend gui = GuiFactory.create(MenuType.GENERIC_9x3, player, "§6§lCấu hình API Gạch Thẻ");
        for (int i=0;i<SITES.length;i++) {
            String site = SITES[i];
            String[] creds = com.naptien.managers.DirectCardSubmitHandler.resolveCredentials(mod, site);
            String pid  = creds[0];
            String pkey = creds[1];
            boolean active     = site.equals(mod.getConfig().getString("card-api.site",""));
            boolean configured = !pid.isEmpty() && !pkey.isEmpty();
            
            ItemStack item = new ItemStack(active?Items.LIME_STAINED_GLASS_PANE:configured?Items.YELLOW_STAINED_GLASS_PANE:Items.RED_STAINED_GLASS_PANE);
            String label = (active?"§a★ ":"§7")+"§l"+site;
            
            List<String> lore = new ArrayList<>();
            if (active)         lore.add("§a§l● Đang sử dụng");
            else if(configured) lore.add("§e● Đã cấu hình");
            else                lore.add("§c● Chưa cấu hình");
            if (!pid.isEmpty()) lore.add("§7ID: §b"+maskId(pid));
            lore.add("§eClick để chọn và cấu hình");
            
            String finalSite = site;
            String finalPid = pid;
            String finalPkey = pkey;
            gui.setSlot(10+i, item, label, lore, () -> {
                if (!finalPid.isEmpty() && !finalPkey.isEmpty()) {
                    mod.getConfig().set("card-api.site", finalSite);
                    mod.getConfig().set("card-api.partner-id", finalPid);
                    mod.getConfig().set("card-api.partner-key", finalPkey);
                    mod.getConfig().set("card-api.configured", true);
                    mod.getConfig().save();
                    gui.close();
                    player.sendSystemMessage(Component.literal("§a[PayBot] §fĐã chuyển sang dùng API §b" + finalSite + " §ftự động từ thông tin lưu sẵn!"));
                    return;
                }
                GuiSession s = GuiSession.get(player.getUUID());
                s.apiSite = finalSite; s.stage = GuiSession.Stage.API_WAIT_PARTNER_ID;
                gui.close();
                player.sendSystemMessage(Component.literal("§a[PayBot] §fCấu hình §b"+finalSite));
                player.sendSystemMessage(Component.literal("§6Nhập §bPartner ID§f:  §7(cancel để huỷ)"));
            });
        }
        gui.setSlot(CLOSE_SLOT, GuiUtil.getCloseItem(), "§c§lĐóng", List.of("§7Nhấn để đóng"), () -> gui.close());
        GuiUtil.fillGlass(gui);
        gui.open();
    }
}
