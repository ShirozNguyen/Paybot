package com.naptien.managers;

import com.naptien.PayBotMod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * RewardEffectManager — Pháo hoa, âm thanh, thông báo khi nạp thành công.
 * Mojang Official Mappings (MC 1.20.1).
 */
public class RewardEffectManager {

    private RewardEffectManager() {}

    public static void trigger(PayBotMod mod, ServerPlayer player, int amount) {
        if (player == null || mod == null) return;

        boolean firework     = mod.isNotifEnabled("firework");
        boolean sound        = mod.isNotifEnabled("sound");
        boolean notification = mod.isNotifEnabled("notification");

        // Action bar thông báo
        if (notification) {
            player.sendSystemMessage(
                    Component.literal("§a§l✓ §fNạp §a§l" + PayBotMod.formatVnd(amount) + " VND §a§lthành công!"),
                    true);
        }

        // Âm thanh
        if (sound) {
            ServerLevel world = (ServerLevel) player.level();
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
                    1f, amount >= 100_000 ? 0.85f : 1f);

            if (amount >= 100_000) {
                mod.getScheduler().schedule(() -> mod.runOnMainThread(() -> {
                    if (mod.getServer().getPlayerList().getPlayer(player.getUUID()) == null) return;
                    try {
                        ServerLevel w = (ServerLevel) player.level();
                        w.playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                                SoundSource.PLAYERS, 0.65f, 1.0f);
                    } catch (Exception e) {
                        if (mod.isNotifEnabled("firework-fail") && mod.getLogFilter().allow("firework-fail"))
                            PayBotMod.LOGGER.warn("[RewardEffect] Sound epic error: " + e.getMessage());
                    }
                }), 500, TimeUnit.MILLISECONDS);
            }
        }

        // Pháo hoa
        if (firework) {
            try {
                spawnFirework(player, amount);
            } catch (Exception e) {
                if (mod.isNotifEnabled("firework-fail") && mod.getLogFilter().allow("firework-fail"))
                    PayBotMod.LOGGER.warn("[RewardEffect] Firework 1 error: " + e.getMessage());
            }

            if (amount >= 100_000) {
                mod.getScheduler().schedule(() -> mod.runOnMainThread(() -> {
                    if (mod.getServer().getPlayerList().getPlayer(player.getUUID()) == null) return;
                    try {
                        spawnFirework(player, amount);
                    } catch (Exception e) {
                        if (mod.isNotifEnabled("firework-fail") && mod.getLogFilter().allow("firework-fail"))
                            PayBotMod.LOGGER.warn("[RewardEffect] Firework 2 error: " + e.getMessage());
                    }
                }), 2, TimeUnit.SECONDS);
            }
        }
    }

    public static void sendSuccessTitle(ServerPlayer player, int amount) {
        try {
            if (player.connection != null) {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
                player.connection.send(new ClientboundClearTitlesPacket(false));
                player.connection.send(new ClientboundSetTitleTextPacket(
                        Component.literal("§a§l✓ Nạp " + PayBotMod.formatVnd(amount) + " VND thành công!")));
                player.connection.send(new ClientboundSetSubtitleTextPacket(
                        Component.literal("§7Cảm ơn bạn đã ủng hộ server!")));
            }
        } catch (Exception ignored) {
        }
    }

    private static void spawnFirework(ServerPlayer player, int amount) {
        ServerLevel world = (ServerLevel) player.level();

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

        com.naptien.utils.FireworkCompat.spawnRewardFirework(world, player.getX(), player.getY() + 0.5, player.getZ(), amount, c1, c2);
    }
}
