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
 * NapBankGui (Fabric) — SGUI chọn mệnh giá nạp ngân hàng (qua GuiBackend).
 *
 * v5.0.0 — BUG FIX CRITICAL: kiểm tra isStandaloneMode() trước khi tạo đơn.
 *   - Connected mode → BotHttpClient.requestNapBank() → nhận qr_url + invoice_id
 *     → QRMapManager.generateQRMap(player, amount, invoiceId, qrUrl)
 *   - Standalone mode → StandaloneBankPoller.createBankOrder() (giữ nguyên)
 *   Trước đây luôn gọi StandaloneBankPoller dù đã connected → tạo đơn standalone,
 *   SePay webhook không match → hàng loạt lỗi.
 *   v5.2.0 — Refactor sang GuiBackend hỗ trợ đa phiên bản Minecraft.
 */
public class NapBankGui {

    public static void open(ServerPlayer player) {
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
        MenuType<?> screenType = GuiUtil.screenType(rows * 9);

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

            boolean customLoreEnabled = mod.getConfig().getBoolean("custom-lore.enabled", true);
            boolean customNameEnabled = mod.getConfig().getBoolean("custom-name.enabled", true);
            List<String> lore = null;
            String customName = customNameEnabled ? mod.getConfig().getString("custom-name.bank." + denom, "") : "";
            String coinAmt = mod.getConfig().getString("denom-rewards-bank." + denom + ".amt", "");
            String displayName = customName.isEmpty()
                    ? (enabled ? "§a§l" : "§7§l") + GuiUtil.formatVnd(denom) + " VND"
                    : com.naptien.utils.CustomLoreFormatter.formatName(player, customName, denom, coinAmt);

            if (customLoreEnabled) {
                List<String> rawLore = mod.getConfig().getStringList("custom-lore.bank." + denom);
                if (rawLore != null && !rawLore.isEmpty()) {
                    lore = com.naptien.utils.CustomLoreFormatter.formatLore(player, rawLore, denom, coinAmt);
                }
            }
            if (lore == null || lore.isEmpty()) {
                lore = List.of(enabled ? "§7Click để nạp" : "§cMệnh giá này chưa cấu hình");
            }

            gui.setSlot(slot, new ItemStack(GuiUtil.getDenomItem(denom)), displayName, lore, () -> {
                if (!enabled) {
                    player.sendSystemMessage(Component.literal("§c[PayBot] §fMệnh giá §e"
                            + GuiUtil.formatVnd(denom) + "§f chưa được cấu hình!"));
                    return;
                }
                gui.close();

                // v5.0.5 [Part 19]: test mode — giả lập thành công NGAY, không tạo
                // đơn/QR thật, không gọi bot/SePay. Dùng 1 lần rồi tự tắt cờ.
                GuiSession ts = GuiSession.get(player.getUUID());
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
        gui.setSlot(closeSlot, GuiUtil.getCloseItem(), "§c§lĐóng", List.of("§7Nhấn để đóng"), () -> gui.close());
        GuiUtil.fillGlass(gui);
        gui.open();
    }
}
