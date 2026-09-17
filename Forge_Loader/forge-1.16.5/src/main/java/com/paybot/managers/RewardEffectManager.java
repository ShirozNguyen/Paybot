// v5.5.5 Part 85: Sync 1.16.5 Mojang API for forge-1.16.5
// v5.5.5 Part 73: Fix actionbar displayClientMessage for 1.16.5
// v5.5.5 Part 72: Fix player.getLevel() for 1.16.5
package com.paybot.managers;

import com.paybot.PayBotMod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.network.protocol.game.ClientboundSetTitlesPacket;

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
            player.displayClientMessage(
                    new TextComponent("§a§l✓ §fNạp §a§l" + PayBotMod.formatVnd(amount) + " VND §a§lthành công!"), true);
        }

        // Âm thanh
        if (sound) {
            ServerLevel world = (ServerLevel) player.getLevel();
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
                    1f, amount >= 100_000 ? 0.85f : 1f);

            if (amount >= 100_000) {
                mod.getScheduler().schedule(() -> mod.runOnMainThread(() -> {
                    if (mod.getServer().getPlayerList().getPlayer(player.getUUID()) == null) return;
                    try {
                        ServerLevel w = (ServerLevel) player.getLevel();
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
                player.connection.send(new ClientboundSetTitlesPacket());
                player.connection.send(new ClientboundSetTitlesPacket(10, 60, 20));
                player.connection.send(new ClientboundSetTitlesPacket(
                        ClientboundSetTitlesPacket.Type.TITLE,
                        new TextComponent("§a§l✓ Nạp " + PayBotMod.formatVnd(amount) + " VND thành công!")));
                player.connection.send(new ClientboundSetTitlesPacket(
                        ClientboundSetTitlesPacket.Type.SUBTITLE,
                        new TextComponent("§7Cảm ơn bạn đã ủng hộ server!")));
            }
        } catch (Exception ignored) {
        }
    }

    private static void spawnFirework(ServerPlayer player, int amount) {
        ServerLevel world = (ServerLevel) player.getLevel();

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

        com.paybot.utils.FireworkCompat.spawnRewardFirework(world, player.getX(), player.getY() + 0.5, player.getZ(), amount, c1, c2);
    }
}
