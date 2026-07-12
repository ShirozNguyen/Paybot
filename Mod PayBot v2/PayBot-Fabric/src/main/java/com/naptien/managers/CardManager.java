package com.naptien.managers;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/** CardManager Fabric — giống hệt plugin, không cần Bukkit. v4.1.0-fabric */
public class CardManager {
    private static final long EXPIRY_MS = 2 * 60 * 1000L;
    private final Map<String, PendingCard> pending = new ConcurrentHashMap<>();
    public static class PendingCard {
        public final String playerName, telco, cardCode, cardSerial;
        public final int denom;
        private final long createdAt = System.currentTimeMillis();
        public PendingCard(String p, String t, int d, String c, String s) {
            playerName=p; telco=t; denom=d; cardCode=c; cardSerial=s; }
        public boolean isExpired() { return System.currentTimeMillis()-createdAt>EXPIRY_MS; }
    }
    public void setPending(String name, PendingCard c) { pending.put(name.toLowerCase(), c); }
    public PendingCard getPending(String name) {
        PendingCard c = pending.get(name.toLowerCase());
        if (c == null) return null;
        if (c.isExpired()) { pending.remove(name.toLowerCase()); return null; }
        return c;
    }
    public void clearPending(String name) { pending.remove(name.toLowerCase()); }
}
