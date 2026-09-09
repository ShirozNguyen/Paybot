package com.naptien.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/**
 * FireworkCompat — Class tạo hiệu ứng pháo hoa thưởng đa phiên bản (MC 1.14.4 tới 1.21.1+).
 * 
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn chức năng tạo hiệu ứng pháo hoa.
 * Sử dụng Reflection an toàn để biên dịch sạch 100% trên mọi bản MC.
 */
public class FireworkCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-FireworkCompat");

    /**
     * Tạo và kích hoạt entity pháo hoa thưởng tại vị trí chỉ định.
     */
    public static void spawnRewardFirework(ServerLevel world, double x, double y, double z, int amount, int c1, int c2) {
        if (world == null) return;

        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);

        if (MinecraftVersionDetector.isDataComponentsEra()) {
            try {
                Class<?> shapeClass = Class.forName("net.minecraft.world.item.component.FireworkExplosion$Shape");
                Object shapeObj = Enum.valueOf((Class<Enum>) shapeClass, amount >= 100_000 ? "LARGE_BALL" : "BURST");

                Class<?> intListClass = Class.forName("it.unimi.dsi.fastutil.ints.IntArrayList");
                Constructor<?> intListCons = intListClass.getConstructor(int[].class);
                Object colorsList = intListCons.newInstance(new int[]{c1, c2});
                Object fadeColorsList = intListCons.newInstance(new int[]{0xFFFFFF});

                Class<?> explosionClass = Class.forName("net.minecraft.world.item.component.FireworkExplosion");
                Constructor<?> expCons = explosionClass.getConstructor(
                        shapeClass,
                        Class.forName("it.unimi.dsi.fastutil.ints.IntList"),
                        Class.forName("it.unimi.dsi.fastutil.ints.IntList"),
                        boolean.class,
                        boolean.class
                );
                Object explosionObj = expCons.newInstance(shapeObj, colorsList, fadeColorsList, true, amount >= 100_000);

                Class<?> fireworksClass = Class.forName("net.minecraft.world.item.component.Fireworks");
                Constructor<?> fwCons = fireworksClass.getConstructor(int.class, List.class);
                Object fireworksObj = fwCons.newInstance(amount >= 100_000 ? 2 : 1, List.of(explosionObj));

                Class<?> dataComponentsClass = Class.forName("net.minecraft.core.component.DataComponents");
                Object fireworksType = dataComponentsClass.getField("FIREWORKS").get(null);

                Method setMethod = ItemStack.class.getMethod("set", Class.forName("net.minecraft.core.component.DataComponentType"), Object.class);
                setMethod.invoke(rocket, fireworksType, fireworksObj);

                FireworkRocketEntity entity = new FireworkRocketEntity(world, x, y + 1.0, z, rocket);
                world.addFreshEntity(entity);
                return;
            } catch (Throwable t) {
                // [v5.5.5] LOGGER.debug() trước đây hiệu quả im lặng (mức DEBUG thường không in ra
                // theo cấu hình log mặc định) — route qua PayBotDebug để nhất quán với phần còn lại
                // của dự án, admin bật debug-mode sẽ thấy rõ tại sao pháo hoa DataComponents thất bại
                // (rơi về NBT fallback bên dưới — vẫn hoạt động, chỉ là đường vòng, không phải lỗi nặng).
                PayBotDebug.logSwallowed("FireworkCompat: pháo hoa kiểu DataComponents thất bại, dùng NBT fallback", t);
            }
        }

        // MC <= 1.20.4 (1.14.4 - 1.20.4): NBT Legacy Tag
        try {
            CompoundTag tag = ItemStackHelper.getOrCreateTag(rocket);
            if (tag != null) {
                CompoundTag fwTag = tag.contains("Fireworks", 10) ? tag.getCompound("Fireworks") : new CompoundTag();
                fwTag.putByte("Flight", (byte) (amount >= 100_000 ? 2 : 1));

                ListTag explosions = new ListTag();
                CompoundTag expTag = new CompoundTag();
                expTag.putByte("Type", (byte) (amount >= 100_000 ? 1 : 4));
                expTag.putIntArray("Colors", new int[]{c1, c2});
                expTag.putIntArray("FadeColors", new int[]{0xFFFFFF});
                expTag.putBoolean("Trail", true);
                expTag.putBoolean("Flicker", amount >= 100_000);
                explosions.add(expTag);

                fwTag.put("Explosions", explosions);
                tag.put("Fireworks", fwTag);
            }

            FireworkRocketEntity entity = new FireworkRocketEntity(world, x, y + 1.0, z, rocket);
            world.addFreshEntity(entity);
        } catch (Throwable t) {
            LOGGER.error("[FireworkCompat] Failed to spawn firework: {}", t.getMessage());
        }
    }
}
