package com.naptien.gui;

import com.naptien.PayBotMod;
import com.naptien.managers.CardManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

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
            player.sendSystemMessage(Component.literal("§7[PayBot] Đã huỷ."));
            return true;
        }
        PayBotMod mod = PayBotMod.getInstance();
        switch (s.stage) {
            // ── Nạp thẻ ──────────────────────────────────────────────────────
            // Hỏi mã thẻ TRƯỚC, serial SAU (dễ nhập hơn cho người dùng)
            case CARD_WAIT_CODE -> {
                s.code  = input.trim();
                s.stage = GuiSession.Stage.CARD_WAIT_SERIAL;
                player.sendSystemMessage(Component.literal("§a[PayBot] §fĐã nhận mã thẻ: §e[" + s.code.length() + " ký tự — ẩn]"));
                player.sendSystemMessage(Component.literal("§6[PayBot] §fNhập §bserial thẻ §fvào chat:  §7§o(cancel để huỷ)"));
            }
            case CARD_WAIT_SERIAL -> {
                String serial = input.trim();
                String telco = s.telco; int denom = s.denom; String code = s.code;
                GuiSession.clear(player.getUUID());
                player.sendSystemMessage(Component.literal("§7[PayBot] Đã nhận serial: §e[" + serial.length() + " ký tự — ẩn]"));
                player.sendSystemMessage(Component.literal("§e[PayBot] §fXem lại trước khi xác nhận:"));
                player.sendSystemMessage(Component.literal("§7Nhà mạng: §f" + telco + "  §7Mệnh giá: §f" + PayBotMod.formatVnd(denom) + " VND"));
                player.sendSystemMessage(Component.literal("§7Mã thẻ  : §f" + code));
                player.sendSystemMessage(Component.literal("§7Serial  : §f" + serial));
                player.sendSystemMessage(Component.literal("§aDùng §e/ok §ađể xác nhận. §7§o(Chỉ bạn mới thấy thông tin này)"));
                mod.getCardManager().setPending(player.getName().getString(),
                        new CardManager.PendingCard(player.getName().getString(), telco, denom, code, serial));
            }
            // ── Edit denom: CMD trước, AMT sau ───────────────────────────────
            case EDIT_WAIT_CMD_CARD, EDIT_WAIT_CMD_BANK -> {
                s.editCmd = input.trim();
                s.stage   = s.stage == GuiSession.Stage.EDIT_WAIT_CMD_CARD
                        ? GuiSession.Stage.EDIT_WAIT_AMT_CARD
                        : GuiSession.Stage.EDIT_WAIT_AMT_BANK;
                player.sendSystemMessage(Component.literal("§7[PayBot] Lệnh đã nhận: §a" + s.editCmd));
                player.sendSystemMessage(Component.literal("§6[PayBot] §eNhập §bsố lượng item thưởng khi nạp §e(số nguyên):  §7§o(cancel để huỷ)"));
            }
            case EDIT_WAIT_AMT_CARD, EDIT_WAIT_AMT_BANK -> {
                try { Integer.parseInt(input.trim()); } catch (NumberFormatException e) {
                    player.sendSystemMessage(Component.literal("§c[PayBot] Phải nhập số nguyên! Nhập lại:")); return true;
                }
                String type = s.editType; int denom = s.editDenom; String cmd = s.editCmd; String amt = input.trim();
                GuiSession.clear(player.getUUID());
                String section = "card".equals(type) ? "denom-rewards-card" : "denom-rewards-bank";
                mod.getConfig().set(section + "." + denom + ".cmd", cmd);
                mod.getConfig().set(section + "." + denom + ".amt", amt);
                mod.getConfig().save();
                if (!mod.isStandaloneMode()) mod.runAsync(() -> mod.getBotHttpClient().pushRewardConfig());
                player.sendSystemMessage(Component.literal("§a[PayBot] §fĐã lưu §e" + GuiUtil.formatDenom(denom)
                        + " §f(" + type + "): lệnh §a" + cmd + " §f| thưởng §a" + amt));
                mod.runOnMainThread(() -> ChinhSuaGui.open(player));
            }
            // v5.0.2: API_WAIT_* cases đã xoá — xem SetupManager.handleChat() thay thế.
            default -> GuiSession.clear(player.getUUID());
        }
        return true;
    }
}