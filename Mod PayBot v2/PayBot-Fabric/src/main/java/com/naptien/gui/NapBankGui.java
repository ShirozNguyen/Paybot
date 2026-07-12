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
 * NapBankGui (Fabric) — SGUI chọn mệnh giá nạp ngân hàng (qua GuiBackend).
 *
 * v5.0.0 — BUG FIX CRITICAL: kiểm tra isStandaloneMode() trước khi tạo đơn.
 *   - Connected mode → BotHttpClient.requestNapBank() → nhận qr_url + invoice_id
 *     → QRMapManager.createAndGiveQR(player, amount, invoiceId, qrUrl)
 *   - Standalone mode → StandaloneBankPoller.createBankOrder() (giữ nguyên)
 *   Trước đây luôn gọi StandaloneBankPoller dù đã connected → tạo đơn standalone,
 *   SePay webhook không match → hàng loạt lỗi.
 *   v5.2.0 — Refactor sang GuiBackend hỗ trợ đa phiên bản Minecraft.
 */
public class NapBankGui {

    public static void open(ServerPlayerEntity player) {
        PayBotMod mod = PayBotMod.getInstance();

        List<String> raw = mod.getConfig().getStringList("quick-amounts");
        int[] denoms;
        if (!raw.isEmpty()) {
            denoms = raw.stream()
                    .mapToInt(s -> { try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; } })
                    .filter(a -> a > 0)
                    .toArray();
        } else {
            denoms = GuiUtil.DENOMS;
        }

        int itemRows = Math.max(1, (int) Math.ceil(denoms.length / 9.0));
        int rows = Math.min(itemRows + 2, 6);
        ScreenHandlerType<?> screenType = GuiUtil.screenType(rows * 9);

        GuiBackend gui = GuiFactory.create(screenType, player, "§b§l✦ Nạp Ngân Hàng — Chọn mệnh giá ✦");

        gui.setSlot(4, new ItemStack(Items.LIGHT_BLUE_STAINED_GLASS_PANE), "§b§l✦ Nạp Ngân Hàng ✦", null, null);

        int itemsPerRow = Math.min(denoms.length, 9);
        int offset = (9 - itemsPerRow) / 2;

        for (int i = 0; i < Math.min(denoms.length, itemRows * 9); i++) {
            int denom = denoms[i];
            int row   = i / 9;
            int col   = i % 9;
            int slot  = 9 + row * 9 + col + offset;

            boolean enabled = !mod.getConfig()
                    .getString("denom-rewards-bank." + denom + ".cmd", "").isEmpty()
                    || !mod.getConfig()
                    .getString("denom-rewards-bank." + denom + ".amt", "").isEmpty();

            gui.setSlot(slot, new ItemStack(GuiUtil.getDenomItem(denom)), (enabled ? "§a§l" : "§7§l") + GuiUtil.formatVnd(denom) + " VND", List.of(enabled ? "§7Click để nạp" : "§cMệnh giá này chưa cấu hình"), () -> {
                if (!enabled) {
                    player.sendMessage(Text.literal("§c[PayBot] §fMệnh giá §e"
                            + GuiUtil.formatVnd(denom) + "§f chưa được cấu hình!"));
                    return;
                }
                gui.close();

                // v5.0.5 [Part 19]: test mode — giả lập thành công NGAY, không tạo
                // đơn/QR thật, không gọi bot/SePay. Dùng 1 lần rồi tự tắt cờ.
                GuiSession ts = GuiSession.get(player.getUuid());
                if (ts.testMode) {
                    ts.testMode = false;
                    mod.runOnMainThread(() -> TestPaymentGui.fakeApproveBank(player, denom));
                    return;
                }

                mod.runAsync(() -> {
                    // v5.1.0: Luôn tạo QR trực tiếp (không phụ thuộc bot).
                    // Nếu bot-connected: bot nhận thông báo sau khi SePay API
                    // poll phát hiện thanh toán (trong pollSePayApiTransactions).
                    mod.getStandaloneBankPoller().createBankOrder(player, denom);
                });
            });
        }

        int closeSlot = (rows - 1) * 9 + 4;
        gui.setSlot(closeSlot, GuiUtil.getCloseItem(), "§c§lĐóng", List.of("§7Nhấn để đóng"), gui::close);
        GuiUtil.fillGlass(gui);
        gui.open();
    }
}
