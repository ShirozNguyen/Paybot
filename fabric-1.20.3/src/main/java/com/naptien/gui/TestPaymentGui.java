package com.naptien.gui;

import com.naptien.PayBotMod;
import com.naptien.managers.RewardEffectManager;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

/**
 * TestPaymentGui (Fabric) — Logic giả lập reward cho /testnapbank /testnapthe.
 *
 * v5.0.0: Ban đầu tự vẽ GUI RIÊNG (không phải NapBankGui/NapTheGui thật) để chọn
 *         mệnh giá giả lập — khác hành vi với plugin (plugin mở ĐÚNG NapBankGui/
 *         NapTheGui thật, chỉ gắn cờ testMode lên session).
 *
 * v5.0.5 [Part 19] FIX: bỏ hẳn 3 hàm tự vẽ GUI (openBankTest/openCardTest/
 *         openCardAmountTest) — giờ CommandRegistry.registerTestNapBank/TestNapThe
 *         set GuiSession.testMode=true rồi gọi thẳng NapBankGui.open()/
 *         NapTheGui.openTelcoGui() (GUI THẬT — admin thấy đúng y hệt trải nghiệm
 *         người chơi thật). NapBankGui/NapTheGui tự check cờ testMode ở bước chọn
 *         mệnh giá để gọi fakeApproveBank/fakeApproveCard bên dưới thay vì tạo đơn
 *         thật — đồng bộ HOÀN TOÀN với cách plugin làm (GuiListener.simulate*Success
 *         được gọi từ NapBankGui/NapTheGui thật của Bukkit).
 *         2 hàm dưới đây đổi từ private → public để NapBankGui/NapTheGui gọi được.
 */
public class TestPaymentGui {

    // ─── Fake approve logic (được gọi từ NapBankGui/NapTheGui thật khi testMode) ──

    public static void fakeApproveBank(ServerPlayer player, int amount) {
        PayBotMod mod = PayBotMod.getInstance();
        String playerName = player.getName().getString();

        // Thực thi reward command (có thật)
        String cmd    = mod.getConfig().getString("denom-rewards-bank." + amount + ".cmd", "").trim();
        String rawAmt = mod.getConfig().getString("denom-rewards-bank." + amount + ".amt", "").trim();
        if (cmd.isEmpty()) cmd = mod.getConfig().getString("reward-command-bank", "").trim();
        if (cmd.isEmpty()) cmd = mod.getConfig().getString("reward-command", "").trim();

        int modeMulti = Math.max(1, mod.getConfig().getInt("Rewards_Bank_Mode", 1));
        int rewardAmt = 0;
        try { if (!rawAmt.isEmpty()) rewardAmt = (int)(Double.parseDouble(rawAmt) * modeMulti); }
        catch (Exception ignored) {}

        if (!cmd.isEmpty()) {
            final String finalCmd = cmd
                    .replace("{player}",    playerName)
                    .replace("[playername]", playerName)
                    .replace("[amount]",    String.valueOf(rewardAmt));
            for (String c : finalCmd.split(";;")) {
                String cc = c.trim();
                if (cc.isEmpty()) continue;
                try { mod.getServer().getCommands().getDispatcher()
                        .execute(cc, mod.getServer().createCommandSourceStack()); }
                catch (Exception e) { PayBotMod.LOGGER.warn("[TEST] cmd error: " + e.getMessage()); }
            }
        }

        // Chat messages (giống screenshot)
        player.sendSystemMessage(Component.literal("§e§l[TEST] §fĐã giả lập đơn nạp bank §a"
                + PayBotMod.formatVnd(amount) + " VND §fthành công cho §e" + playerName + "§f!"));
        player.sendSystemMessage(Component.literal("§7§o(Không tạo QR/đơn thật, không lưu vào /topuplist — chỉ test reward + hiệu ứng)"));
        player.sendSystemMessage(Component.literal("§a§l[PayBot] §fThanh toán thành công!"));
        player.sendSystemMessage(Component.literal("§7Số tiền: §e" + PayBotMod.formatVnd(amount) + " VND"));

        // Title lớn "✓ Nạp X VND thành công!" (giống screenshot)
        sendSuccessTitle(player, PayBotMod.formatVnd(amount) + " VND");

        // Hiệu ứng pháo hoa
        RewardEffectManager.trigger(mod, player, amount);
    }

    public static void fakeApproveCard(ServerPlayer player, String telco, int denom) {
        PayBotMod mod = PayBotMod.getInstance();
        String playerName = player.getName().getString();

        String cmd    = mod.getConfig().getString("denom-rewards-card." + denom + ".cmd", "").trim();
        String rawAmt = mod.getConfig().getString("denom-rewards-card." + denom + ".amt", "").trim();
        if (cmd.isEmpty()) cmd = mod.getConfig().getString("reward-command-card", "").trim();
        if (cmd.isEmpty()) cmd = mod.getConfig().getString("reward-command", "").trim();

        int modeMulti = Math.max(1, mod.getConfig().getInt("Rewards_Card_Mode", 1));
        int rewardAmt = 0;
        try { if (!rawAmt.isEmpty()) rewardAmt = (int)(Double.parseDouble(rawAmt) * modeMulti); }
        catch (Exception ignored) {}

        if (!cmd.isEmpty()) {
            final String finalCmd = cmd
                    .replace("{player}",    playerName)
                    .replace("[playername]", playerName)
                    .replace("[amount]",    String.valueOf(rewardAmt));
            for (String c : finalCmd.split(";;")) {
                String cc = c.trim();
                if (cc.isEmpty()) continue;
                try { mod.getServer().getCommands().getDispatcher()
                        .execute(cc, mod.getServer().createCommandSourceStack()); }
                catch (Exception e) { PayBotMod.LOGGER.warn("[TEST] cmd error: " + e.getMessage()); }
            }
        }

        player.sendSystemMessage(Component.literal("§b§l[TEST] §fĐã giả lập nạp thẻ §e" + telco + " "
                + PayBotMod.formatVnd(denom) + " VND §fthành công cho §e" + playerName + "§f!"));
        player.sendSystemMessage(Component.literal("§7§o(Không gửi thẻ thật lên hệ thống — chỉ test reward + hiệu ứng)"));
        player.sendSystemMessage(Component.literal("§a§l[PayBot] §fXử lý thẻ thành công!"));
        player.sendSystemMessage(Component.literal("§7Nhà mạng: §e" + telco + " §7| §7Mệnh giá: §e" + PayBotMod.formatVnd(denom) + " VND"));

        sendSuccessTitle(player, PayBotMod.formatVnd(denom) + " VND");
        RewardEffectManager.trigger(mod, player, denom);
    }

    // ─── Title helper ─────────────────────────────────────────────────────────

    /** Hiện title lớn màu xanh "✓ Nạp X VND thành công!" như ảnh. */
    private static void sendSuccessTitle(ServerPlayer player, String amountStr) {
        try {
            if (player.connection != null) {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
                player.connection.send(new ClientboundClearTitlesPacket(false));
                player.connection.send(new ClientboundSetTitleTextPacket(
                        Component.literal("§a§l✓ Nạp " + amountStr + " thành công!")));
                player.connection.send(new ClientboundSetSubtitleTextPacket(
                        Component.literal("§7(chế độ test — không giao dịch thật)")));
            }
        } catch (Exception e) {
            // Title không critical — bỏ qua nếu lỗi
        }
    }
}
