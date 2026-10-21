package com.paybot.managers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlayerCooldownManager — Quản lý giãn cách thao tác (rate limiting) per-player.
 * Tuân thủ Quy tắc 17: Tách biệt hoàn toàn thành 1 class đơn nhiệm độc lập.
 * - Cooldown tạo QR Map: Mặc định 10 giây (chống spam packet tạo map tràn RAM).
 * - Cooldown gửi thẻ cào: Mặc định 30 giây (chống spam request Cloudflare 429).
 */
public final class PlayerCooldownManager {

    private static final PlayerCooldownManager INSTANCE = new PlayerCooldownManager();

    private final Map<UUID, Long> qrMapCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cardSubmitCooldowns = new ConcurrentHashMap<>();

    private volatile int qrMapSeconds = 10;
    private volatile int cardSubmitSeconds = 30;

    private PlayerCooldownManager() {}

    public static PlayerCooldownManager getInstance() {
        return INSTANCE;
    }

    public static final class CooldownCheckResult {
        private final boolean allowed;
        private final int remainingSeconds;

        public CooldownCheckResult(boolean allowed, int remainingSeconds) {
            this.allowed = allowed;
            this.remainingSeconds = remainingSeconds;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public int getRemainingSeconds() {
            return remainingSeconds;
        }
    }

    /**
     * Kiểm tra cooldown tạo QR Map.
     */
    public CooldownCheckResult checkQrMapCooldown(UUID uuid) {
        if (uuid == null) return new CooldownCheckResult(true, 0);
        long now = System.currentTimeMillis();
        Long lastTime = qrMapCooldowns.get(uuid);
        if (lastTime == null) {
            return new CooldownCheckResult(true, 0);
        }
        long elapsedMillis = now - lastTime;
        long requiredMillis = qrMapSeconds * 1000L;
        if (elapsedMillis < requiredMillis) {
            int remain = (int) Math.ceil((requiredMillis - elapsedMillis) / 1000.0);
            return new CooldownCheckResult(false, Math.max(1, remain));
        }
        return new CooldownCheckResult(true, 0);
    }

    /**
     * Ghi nhận thời điểm tạo QR Map.
     */
    public void recordQrMapCreation(UUID uuid) {
        if (uuid != null) {
            qrMapCooldowns.put(uuid, System.currentTimeMillis());
        }
    }

    /**
     * Kiểm tra cooldown gửi thẻ cào.
     */
    public CooldownCheckResult checkCardSubmitCooldown(UUID uuid) {
        if (uuid == null) return new CooldownCheckResult(true, 0);
        long now = System.currentTimeMillis();
        Long lastTime = cardSubmitCooldowns.get(uuid);
        if (lastTime == null) {
            return new CooldownCheckResult(true, 0);
        }
        long elapsedMillis = now - lastTime;
        long requiredMillis = cardSubmitSeconds * 1000L;
        if (elapsedMillis < requiredMillis) {
            int remain = (int) Math.ceil((requiredMillis - elapsedMillis) / 1000.0);
            return new CooldownCheckResult(false, Math.max(1, remain));
        }
        return new CooldownCheckResult(true, 0);
    }

    /**
     * Ghi nhận thời điểm gửi thẻ cào.
     */
    public void recordCardSubmit(UUID uuid) {
        if (uuid != null) {
            cardSubmitCooldowns.put(uuid, System.currentTimeMillis());
        }
    }

    public int getQrMapSeconds() {
        return qrMapSeconds;
    }

    public void setQrMapSeconds(int seconds) {
        if (seconds > 0) {
            this.qrMapSeconds = seconds;
        }
    }

    public int getCardSubmitSeconds() {
        return cardSubmitSeconds;
    }

    public void setCardSubmitSeconds(int seconds) {
        if (seconds > 0) {
            this.cardSubmitSeconds = seconds;
        }
    }

    public void clearPlayer(UUID uuid) {
        if (uuid != null) {
            qrMapCooldowns.remove(uuid);
            cardSubmitCooldowns.remove(uuid);
        }
    }

    public void clearAll() {
        qrMapCooldowns.clear();
        cardSubmitCooldowns.clear();
    }
}
