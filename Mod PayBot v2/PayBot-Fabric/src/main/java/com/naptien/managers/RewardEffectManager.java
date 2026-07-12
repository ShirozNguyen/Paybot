package com.naptien.managers;

import com.naptien.PayBotMod;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * RewardEffectManager (Fabric) — Pháo hoa, âm thanh, thông báo khi nạp thành công.
 *
 * v5.0.0:
 *   - CHỈ chạy khi player ĐANG ONLINE (kiểm tra mỗi lần schedule)
 *   - < 100k  : 2 quả pháo hoa cách nhau 2s + sound levelup
 *   - ≥ 100k  : 2 quả + sound levelup + Epic Achievement sound (0.5s sau)
 *   - Config toggles: Reward_Firework, Reward_Sound, Reward_Notification
 *   - Notification keys: "firework-fail", "reward-dispatch" (khớp config template)
 *
 * v5.0.5:
 *   - Thêm sendSuccessTitle() để hiện title lớn khi nạp thật (giống test mode)
 *   - Gọi từ PayBotMod.executeReward() và CommandRegistry./approve
 */
public class RewardEffectManager {

    private RewardEffectManager() {}

    /**
     * Kích hoạt hiệu ứng. PHẢI gọi trên main server thread.
     * @param amount mệnh giá thực tế nạp (VND)
     */
    public static void trigger(PayBotMod mod, ServerPlayerEntity player, int amount) {
        // Guard: player phải còn online tại thời điểm gọi
        if (mod.getServer().getPlayerManager().getPlayer(player.getUuid()) == null) return;

        boolean firework     = mod.getConfig().getBoolean("Reward_Firework",     true);
        boolean sound        = mod.getConfig().getBoolean("Reward_Sound",        true);
        boolean notification = mod.getConfig().getBoolean("Reward_Notification", true);

        // ── Action bar thông báo ───────────────────────────────────────────────
        if (notification) {
            player.sendMessage(
                    Text.literal("§a§l✓ §fNạp §a§l" + PayBotMod.formatVnd(amount) + " VND §a§lthành công!"),
                    true); // true = action bar
        }

        // ── Âm thanh ──────────────────────────────────────────────────────────
        if (sound) {
            ServerWorld world = (ServerWorld) player.getWorld();
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS,
                    1f, amount >= 100_000 ? 0.85f : 1f);

            if (amount >= 100_000) {
                // Epic Achievement: delay 0.5s để không bị chồng âm
                mod.getScheduler().schedule(() -> mod.runOnMainThread(() -> {
                    if (mod.getServer().getPlayerManager().getPlayer(player.getUuid()) == null) return;
                    try {
                        ServerWorld w = (ServerWorld) player.getWorld();
                        w.playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                                SoundCategory.PLAYERS, 0.65f, 1.0f);
                    } catch (Exception e) {
                        if (mod.isNotifEnabled("firework-fail") && mod.getLogFilter().allow("firework-fail"))
                            PayBotMod.LOGGER.warn("[RewardEffect] Sound epic error: " + e.getMessage());
                    }
                }), 500, TimeUnit.MILLISECONDS);
            }
        }

        // ── Pháo hoa — 2 quả cách 2s ──────────────────────────────────────────
        if (firework) {
            // Quả 1: ngay lập tức
            try {
                spawnFirework(player, amount);
            } catch (Exception e) {
                if (mod.isNotifEnabled("firework-fail") && mod.getLogFilter().allow("firework-fail"))
                    PayBotMod.LOGGER.warn("[RewardEffect] Firework 1 error: " + e.getMessage());
            }

            // Quả 2: sau 2 giây, chỉ nếu player vẫn online
            mod.getScheduler().schedule(() -> mod.runOnMainThread(() -> {
                if (mod.getServer().getPlayerManager().getPlayer(player.getUuid()) == null) return;
                try {
                    spawnFirework(player, amount);
                } catch (Exception e) {
                    if (mod.isNotifEnabled("firework-fail") && mod.getLogFilter().allow("firework-fail"))
                        PayBotMod.LOGGER.warn("[RewardEffect] Firework 2 error: " + e.getMessage());
                }
            }), 2000, TimeUnit.MILLISECONDS);
        }
    }

    // ─── Success title (v5.0.5) ──────────────────────────────────────────────

    /**
     * Hiện title lớn "✓ Nạp X VND thành công!" trên màn hình player.
     * Gọi từ PayBotMod.executeReward() cho giao dịch THẬT (không phải test).
     * @param player  người chơi nhận thông báo
     * @param amount  số tiền (VND)
     */
    public static void sendSuccessTitle(ServerPlayerEntity player, int amount) {
        try {
            player.networkHandler.sendPacket(
                    new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(10, 60, 20));
            player.networkHandler.sendPacket(
                    new net.minecraft.network.packet.s2c.play.ClearTitleS2CPacket(false));
            player.networkHandler.sendPacket(
                    new net.minecraft.network.packet.s2c.play.TitleS2CPacket(
                            Text.literal("\u00a7a\u00a7l\u2713 N\u1ea1p "
                                    + PayBotMod.formatVnd(amount) + " VND th\u00e0nh c\u00f4ng!")));
            player.networkHandler.sendPacket(
                    new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(
                            Text.literal("\u00a77C\u1ea3m \u01a1n b\u1ea1n \u0111\u00e3 \u1ee7ng h\u1ed9 server!")));
        } catch (Exception ignored) {
            // Title không critical — bỏ qua nếu lỗi
        }
    }

    // ─── Spawn firework ───────────────────────────────────────────────────────

    private static void spawnFirework(ServerPlayerEntity player, int amount) {
        ServerWorld world = (ServerWorld) player.getWorld();

        // Màu theo mệnh giá
        int[] colors;
        if (amount >= 1_000_000) {
            colors = new int[]{0xFFD700, 0xFFFFFF, 0xFFA500, 0xFF69B4, 0xADD8E6};
        } else if (amount >= 100_000) {
            colors = new int[]{0xFF69B4, 0x00BFFF, 0xFF8C00, 0x9400D3, 0x00FF7F};
        } else {
            colors = new int[]{0xFF0000, 0xFF8C00, 0xFFFF00, 0x00FF00, 0x00BFFF, 0x9400D3};
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int c1 = colors[rng.nextInt(colors.length)];
        int c2 = colors[rng.nextInt(colors.length)];

        FireworkExplosionComponent explosion = new FireworkExplosionComponent(
                amount >= 100_000
                        ? FireworkExplosionComponent.Type.LARGE_BALL
                        : FireworkExplosionComponent.Type.BURST,
                new IntArrayList(new int[]{c1, c2}),
                new IntArrayList(new int[]{0xFFFFFF}),
                true,              // trail
                amount >= 100_000  // twinkle chỉ khi ≥ 100k
        );

        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponentTypes.FIREWORKS,
                new FireworksComponent(amount >= 100_000 ? 2 : 1, List.of(explosion)));

        FireworkRocketEntity entity = new FireworkRocketEntity(world,
                player.getX(), player.getY() + 0.5, player.getZ(), rocket);
        world.spawnEntity(entity);
    }
}
