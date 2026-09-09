package com.naptien.gui;

import com.naptien.PayBotMod;
import com.naptien.compat.GuiBackend;
import com.naptien.compat.GuiFactory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * NapTheGui (Fabric) — GUI nạp thẻ cào dùng SGUI (qua GuiBackend).
 *
 * Flow:
 *   openTelcoGui() → player chọn nhà mạng → GuiSession.stage=CARD_DENOM
 *   openDenomGui() → player chọn mệnh giá → đóng GUI → chat SERIAL
 *   Sau khi nhập serial/code → /ok để gửi
 *
 * Changelog:
 *   v4.0.1-fabric — initial
 *   v4.0.4-fabric — fix SGUI 1.6.1: ClickCallback void
 *   v5.2.0        — Refactor sang GuiBackend hỗ trợ đa phiên bản Minecraft.
 */
public class NapTheGui {

    private NapTheGui() {}

    // ─── Bước 1: Chọn nhà mạng ───────────────────────────────────────────────

    public static void openTelcoGui(ServerPlayer player) {
        PayBotMod mod = PayBotMod.getInstance();
        GuiBackend gui = GuiFactory.create(MenuType.GENERIC_9x3, player, "§6§l✦ Nạp Thẻ Cào — Chọn nhà mạng ✦");

        boolean customLoreEnabled = mod.getConfig().getBoolean("custom-lore.enabled", true);
        String[] telcos = GuiUtil.TELCOS;
        for (int i = 0; i < telcos.length; i++) {
            String telco = telcos[i];
            int slot = 9 + i;

            List<String> lore = null;
            if (customLoreEnabled) {
                List<String> rawLore = mod.getConfig().getStringList("custom-lore.telco." + telco);
                if (rawLore != null && !rawLore.isEmpty()) {
                    lore = com.naptien.utils.CustomLoreFormatter.formatLore(player, rawLore, 0, "");
                }
            }
            if (lore == null || lore.isEmpty()) {
                lore = List.of("§7Click để chọn §e" + telco);
            }

            gui.setSlot(slot, new ItemStack(GuiUtil.getTelcoWool(telco)), "§6§l" + telco, lore, () -> {
                GuiSession.get(player.getUUID()).telco = telco;
                GuiSession.get(player.getUUID()).stage = GuiSession.Stage.CARD_DENOM;
                openDenomGui(player, telco);
            });
        }
        
        gui.setSlot(22, GuiUtil.getCloseItem(), "§c§lĐóng", List.of("§7Nhấn để đóng"), () -> gui.close());

        GuiUtil.fillGlass(gui);
        gui.open();
    }

    // ─── Bước 2: Chọn mệnh giá ───────────────────────────────────────────────

    public static void openDenomGui(ServerPlayer player, String telco) {
        PayBotMod mod = PayBotMod.getInstance();
        GuiBackend gui = GuiFactory.create(MenuType.GENERIC_9x3, player, "§6§l✦ Nạp Thẻ — " + telco + " ✦");

        int[] denoms = GuiUtil.DENOMS;
        for (int i = 0; i < denoms.length; i++) {
            int denom = denoms[i];
            boolean enabled = !mod.getConfig().getString(
                    "denom-rewards-card." + denom + ".cmd", "").isEmpty()
                    || !mod.getConfig().getString(
                    "denom-rewards-card." + denom + ".amt", "").isEmpty();
            boolean customLoreEnabled = mod.getConfig().getBoolean("custom-lore.enabled", true);
            boolean customNameEnabled = mod.getConfig().getBoolean("custom-name.enabled", true);
            List<String> loreList = null;
            String customName = customNameEnabled ? mod.getConfig().getString("custom-name.card." + denom, "") : "";
            String coinAmt = mod.getConfig().getString("denom-rewards-card." + denom + ".amt", "");
            String displayName = customName.isEmpty()
                    ? (enabled ? "§a§l" : "§7§l") + GuiUtil.formatVnd(denom) + " VND"
                    : com.naptien.utils.CustomLoreFormatter.formatName(player, customName, denom, coinAmt);

            if (customLoreEnabled) {
                List<String> rawLore = mod.getConfig().getStringList("custom-lore.card." + denom);
                if (rawLore != null && !rawLore.isEmpty()) {
                    loreList = com.naptien.utils.CustomLoreFormatter.formatLore(player, rawLore, denom, coinAmt);
                }
            }
            if (loreList == null || loreList.isEmpty()) {
                loreList = List.of(enabled ? "§eClick để chọn" : "§cMệnh giá này chưa được cấu hình");
            }

            int slot = 9 + i;
            int finalDenom = denom;
            gui.setSlot(slot, new ItemStack(GuiUtil.getDenomItem(denom)), displayName, loreList, () -> {
                if (!enabled) {
                    player.sendSystemMessage(Component.literal("§c[PayBot] §fMệnh giá này chưa được cấu hình!"));
                    return;
                }
                GuiSession s = GuiSession.get(player.getUUID());
                gui.close();

                // v5.0.5 [Part 19]: test mode — giả lập thành công NGAY, bỏ qua hẳn
                // bước nhập mã thẻ/serial thật. Dùng 1 lần rồi tự tắt cờ.
                if (s.testMode) {
                    s.testMode = false;
                    mod.runOnMainThread(() -> TestPaymentGui.fakeApproveCard(player, telco, finalDenom));
                    return;
                }

                s.denom = finalDenom;
                s.stage = GuiSession.Stage.CARD_WAIT_CODE;
                player.sendSystemMessage(Component.literal("§6[PayBot] §fNhập §bmã thẻ §fvào chat:"));
                player.sendSystemMessage(Component.literal("§7(Tin nhắn chỉ bạn mới thấy — gõ §ccancel §7để huỷ)"));
            });
        }

        gui.setSlot(18, new ItemStack(Items.ARROW), "§7◄ Quay lại", List.of("§7Nhấn để quay lại"), () -> openTelcoGui(player));
        gui.setSlot(26, GuiUtil.getCloseItem(), "§c§lĐóng", List.of("§7Nhấn để đóng"), () -> {
            GuiSession.clear(player.getUUID());
            gui.close();
        });

        GuiUtil.fillGlass(gui);
        gui.open();
    }
}