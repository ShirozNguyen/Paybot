package com.naptien.gui;

import com.naptien.PayBotMod;
import com.naptien.compat.GuiBackend;
import com.naptien.compat.GuiFactory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

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

    public static void openTelcoGui(ServerPlayerEntity player) {
        GuiBackend gui = GuiFactory.create(ScreenHandlerType.GENERIC_9X3, player, "§6§l✦ Nạp Thẻ Cào — Chọn nhà mạng ✦");

        String[] telcos = GuiUtil.TELCOS;
        for (int i = 0; i < telcos.length; i++) {
            String telco = telcos[i];
            int slot = 9 + i;
            gui.setSlot(slot, new ItemStack(GuiUtil.getTelcoWool(telco)), "§6§l" + telco, List.of("§7Click để chọn §e" + telco), () -> {
                GuiSession.get(player.getUuid()).telco = telco;
                GuiSession.get(player.getUuid()).stage = GuiSession.Stage.CARD_DENOM;
                openDenomGui(player, telco);
            });
        }
        
        gui.setSlot(22, GuiUtil.getCloseItem(), "§c§lĐóng", List.of("§7Nhấn để đóng"), gui::close);

        GuiUtil.fillGlass(gui);
        gui.open();
    }

    // ─── Bước 2: Chọn mệnh giá ───────────────────────────────────────────────

    public static void openDenomGui(ServerPlayerEntity player, String telco) {
        PayBotMod mod = PayBotMod.getInstance();
        GuiBackend gui = GuiFactory.create(ScreenHandlerType.GENERIC_9X3, player, "§6§l✦ Nạp Thẻ — " + telco + " ✦");

        int[] denoms = GuiUtil.DENOMS;
        for (int i = 0; i < denoms.length; i++) {
            int denom = denoms[i];
            boolean enabled = !mod.getConfig().getString(
                    "denom-rewards-card." + denom + ".cmd", "").isEmpty()
                    || !mod.getConfig().getString(
                    "denom-rewards-card." + denom + ".amt", "").isEmpty();

            String lore = enabled ? "§eClick để chọn" : "§cMệnh giá này chưa được cấu hình";

            int slot = 9 + i;
            int finalDenom = denom;
            gui.setSlot(slot, new ItemStack(GuiUtil.getDenomItem(denom)), (enabled ? "§a§l" : "§7§l") + GuiUtil.formatVnd(denom) + " VND", List.of(lore), () -> {
                if (!enabled) {
                    player.sendMessage(Text.literal("§c[PayBot] §fMệnh giá này chưa được cấu hình!"));
                    return;
                }
                GuiSession s = GuiSession.get(player.getUuid());
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
                player.sendMessage(Text.literal("§6[PayBot] §fNhập §bmã thẻ §fvào chat:"));
                player.sendMessage(Text.literal("§7(Tin nhắn chỉ bạn mới thấy — gõ §ccancel §7để huỷ)"));
            });
        }

        gui.setSlot(18, new ItemStack(Items.ARROW), "§7◄ Quay lại", List.of("§7Nhấn để quay lại"), () -> openTelcoGui(player));
        gui.setSlot(26, GuiUtil.getCloseItem(), "§c§lĐóng", List.of("§7Nhấn để đóng"), () -> {
            GuiSession.clear(player.getUuid());
            gui.close();
        });

        GuiUtil.fillGlass(gui);
        gui.open();
    }
}