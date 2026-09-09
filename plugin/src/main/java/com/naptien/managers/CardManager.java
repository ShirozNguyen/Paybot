package com.naptien.managers;

import com.naptien.NapTienPlugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CardManager — Lưu trữ PendingCard per-player (in-memory, không persist).
 * Player nhập thẻ qua GUI hoặc text mode → setPending()
 * Sau 2 phút không /ok → isExpired() = true → tự động xoá.
 *
 * Changelog:
 *   v4.1.0 — Tái tạo từ .class (file nguồn gốc không có trong ZIP)
 */
public class CardManager {

    /** Thời gian hết hạn pending: 2 phút. */
    private static final long EXPIRY_MS = 2 * 60 * 1000L;

    private final NapTienPlugin plugin;
    private final Map<String, PendingCard> pending = new ConcurrentHashMap<>();

    public CardManager(NapTienPlugin plugin) {
        this.plugin = plugin;
    }

    // ─── PendingCard ──────────────────────────────────────────────────────────

    public static class PendingCard {
        public final String playerName;
        public final String telco;
        public final int    denom;
        public final String cardCode;
        public final String cardSerial;
        private final long  createdAt = System.currentTimeMillis();

        public PendingCard(String playerName, String telco, int denom,
                           String cardCode, String cardSerial) {
            this.playerName = playerName;
            this.telco      = telco;
            this.denom      = denom;
            this.cardCode   = cardCode;
            this.cardSerial = cardSerial;
        }

        /** Hết hạn sau {@value #EXPIRY_MS} ms kể từ lúc tạo. */
        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > EXPIRY_MS;
        }
    }

    // ─── API ──────────────────────────────────────────────────────────────────

    public void setPending(String playerName, PendingCard card) {
        pending.put(playerName.toLowerCase(), card);
    }

    /** @return pending card nếu còn hạn, null nếu không có hoặc đã hết hạn. */
    public PendingCard getPending(String playerName) {
        PendingCard card = pending.get(playerName.toLowerCase());
        if (card == null) return null;
        if (card.isExpired()) {
            pending.remove(playerName.toLowerCase());
            return null;
        }
        return card;
    }

    public void clearPending(String playerName) {
        pending.remove(playerName.toLowerCase());
    }
}
