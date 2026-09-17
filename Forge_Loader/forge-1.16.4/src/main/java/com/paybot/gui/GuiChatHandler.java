// v5.5.5 Part 85: Sync 1.16.5 Mojang API for forge-1.16.4
package com.paybot.gui;

import com.paybot.PayBotMod;
import com.paybot.managers.CardManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.TextComponent;

/**
 * GuiChatHandler (Fabric) — xử lý chat input cho các GuiSession stage.
 * Được gọi từ PayBotMod.ALLOW_CHAT_MESSAGE sau SetupManager/OwnerSessionManager.
 * Changelog: v4.1.0-fabric
 */
public class GuiChatHandler {

    public static boolean handle(ServerPlayer player, String input) {
        GuiSession s = GuiSession.get(player.getUUID());
        if (!s.isWaitingForChat()) return false;
        if ("cancel".equalsIgnoreCase(input.trim())) {
            GuiSession.clear(player.getUUID());
            player.sendMessage(new TextComponent("§7[PayBot] Đã huỷ."), ChatType.SYSTEM, net.minecraft.Util.NIL_UUID);
            return true;
        }
        PayBotMod mod = PayBotMod.getInstance();
        switch (s.stage) {
            // ── Nạp thẻ ──────────────────────────────────────────────────────
            // Hỏi mã thẻ TRƯỚC, serial SAU (dễ nhập hơn cho người dùng)
            case CARD_WAIT_CODE -> {
                s.code  = input.trim();
                s.stage = GuiSession.Stage.CARD_WAIT_SERIAL;
                player.sendMessage(new TextComponent("§a[PayBot] §fĐã nhận mã thẻ: §e[" + s.code.length() + " ký tự — ẩn]"), ChatType.SYSTEM, net.minecraft.Util.NIL_UUID);
                player.sendMessage(new TextComponent("§6[PayBot] §fNhập §bserial thẻ §fvào chat:  §7§o(cancel để huỷ)"), ChatType.SYSTEM, net.minecraft.Util.NIL_UUID);
            }
            case CARD_WAIT_SERIAL -> {
                String serial = input.trim();
                String telco = s.telco; int denom = s.denom; String code = s.code;
                GuiSession.clear(player.getUUID());
                player.sendMessage(new TextComponent("§7[PayBot] Đã nhận serial: §e[" + serial.length() + " ký tự — ẩn]"), ChatType.SYSTEM, net.minecraft.Util.NIL_UUID);
                player.sendMessage(new TextComponent("§e[PayBot] §fXem lại trước khi xác nhận:"), ChatType.SYSTEM, net.minecraft.Util.NIL_UUID);
                player.sendMessage(new TextComponent("§7Nhà mạng: §f" + telco + "  §7Mệnh giá: §f" + PayBotMod.formatVnd(denom) + " VND"), ChatType.SYSTEM, net.minecraft.Util.NIL_UUID);
                player.sendMessage(new TextComponent("§7Mã thẻ  : §f" + code), ChatType.SYSTEM, net.minecraft.Util.NIL_UUID);
                player.sendMessage(new TextComponent("§7Serial  : §f" + serial), ChatType.SYSTEM, net.minecraft.Util.NIL_UUID);
                player.sendMessage(new TextComponent("§aDùng §e/ok §ađể xác nhận. §7§o(Chỉ bạn mới thấy thông tin này)"), ChatType.SYSTEM, net.minecraft.Util.NIL_UUID);
                mod.getCardManager().setPending(player.getName().getString(),
                        new CardManager.PendingCard(player.getName().getString(), telco, denom, code, serial));
            }
            // ── Edit denom: CMD trước, AMT sau ───────────────────────────────
            case EDIT_WAIT_CMD_CARD, EDIT_WAIT_CMD_BANK -> {
                s.editCmd = input.trim();
                s.stage   = s.stage == GuiSession.Stage.EDIT_WAIT_CMD_CARD
                        ? GuiSession.Stage.EDIT_WAIT_AMT_CARD
                        : GuiSession.Stage.EDIT_WAIT_AMT_BANK;
                player.sendMessage(new TextComponent("§7[PayBot] Lệnh đã nhận: §a" + s.editCmd), ChatType.SYSTEM, net.minecraft.Util.NIL_UUID);
                player.sendMessage(new TextComponent("§6[PayBot] §eNhập §bsố lượng item thưởng khi nạp §e(số nguyên):  §7§o(cancel để huỷ)"), ChatType.SYSTEM, net.minecraft.Util.NIL_UUID);
            }
            case EDIT_WAIT_AMT_CARD, EDIT_WAIT_AMT_BANK -> {
                try { Integer.parseInt(input.trim()); } catch (NumberFormatException e) {
                    player.sendMessage(new TextComponent("§c[PayBot] Phải nhập số nguyên! Nhập lại:"), ChatType.SYSTEM, net.minecraft.Util.NIL_UUID); return true;
                }
                String type = s.editType; int denom = s.editDenom; String cmd = s.editCmd; String amt = input.trim();
                GuiSession.clear(player.getUUID());
                String section = "card".equals(type) ? "denom-rewards-card" : "denom-rewards-bank";
                mod.getConfig().set(section + "." + denom + ".cmd", cmd);
                mod.getConfig().set(section + "." + denom + ".amt", amt);
                mod.getConfig().save();
                if (!mod.isStandaloneMode()) mod.runAsync(() -> mod.getBotHttpClient().pushRewardConfig());
                player.sendMessage(new TextComponent("§a[PayBot] §fĐã lưu §e" + GuiUtil.formatDenom(denom)
                        + " §f(" + type + "): lệnh §a" + cmd + " §f| thưởng §a" + amt), ChatType.SYSTEM, net.minecraft.Util.NIL_UUID);
                mod.runOnMainThread(() -> ChinhSuaGui.open(player));
            }
            // v5.0.2: API_WAIT_* cases đã xoá — xem SetupManager.handleChat() thay thế.
            default -> GuiSession.clear(player.getUUID());
        }
        return true;
    }
}