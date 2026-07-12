package com.naptien.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized compatibility layer for Minecraft 1.12 – latest.
 * Supports all loaders: Bukkit, Spigot, Paper, Purpur (including 26.x+ versioning).
 *
 * <pre>
 * PRE_FLAT      : Legacy material names (WOOL data, STAINED_GLASS_PANE, MAP)      — true nếu RED_WOOL không tồn tại
 * PRE_PDC       : No PersistentDataContainer, no MapMeta.setMapView()             — true nếu thiếu class PersistentDataContainer
 * PRE_ADVENTURE : Paper Adventure API not bundled                                 — true nếu thiếu net.kyori.adventure.text.Component
 * IS_LEGACY     : General "legacy" path flag (≈ PRE_ADVENTURE, chỉ ảnh hưởng material denom GUI)
 * MINOR         : best-effort, CHỈ dùng hiển thị/log — KHÔNG quyết định logic gì cả
 * </pre>
 *
 * Tất cả flag trên được tính bằng FEATURE-DETECTION (try Material.valueOf/Class.forName
 * thật, bắt exception) — KHÔNG parse chuỗi version nữa. Xem changelog v5.0.0 (fix) để
 * biết lý do (parse string đã crash 2 lần qua các bản Purpur khác nhau).
 *
 * All Bukkit API calls that differ between versions are routed through this
 * class to keep every other file clean.
 *
 * Changelog:
 *   v4.2.0 — Fix version detection cho Purpur 26.x (getBukkitVersion() trả "26.1.2-..." → MINOR=1, crash)
 *             → ưu tiên parse "(MC: X.Y)" từ getServer().getVersion() — đúng trên mọi loader
 *   v5.0.0 (fix) — BUG TÁI PHÁT: build Purpur mới hơn (26.1.2 build 2591) lại đổi format
 *             chuỗi version KHÁC NỮA, khiến cách parse "(MC: X.Y)" ở trên cũng fail y hệt
 *             bug gốc → crash STAINED_GLASS_PANE/WOOL lại lần thứ 2.
 *             → BỎ HẲN việc parse string version (đã 2 lần chứng minh không bền vững,
 *             mỗi fork/build có thể tự đổi format bất cứ lúc nào). Thay bằng
 *             FEATURE-DETECTION trực tiếp: try Material.valueOf("RED_WOOL") thật, nếu
 *             lỗi thì mới coi là legacy — hỏi thẳng API thật có gì thay vì đoán qua text.
 *             Cách này KHÔNG THỂ sai vì không phụ thuộc bất kỳ chuỗi text nào server tự
 *             xưng — chỉ phụ thuộc Material enum runtime THẬT có gì.
 */
public final class VersionCompat {

    private VersionCompat() {}

    // ── Version flags ─────────────────────────────────────────────────────────

    /** Server minor version number — CHỈ để hiển thị/log, KHÔNG dùng để quyết định logic
     *  (mọi nhánh quan trọng đã chuyển qua feature-detection, xem static block dưới). */
    public static final int MINOR;

    /** &lt; 1.13 — legacy enum names: WOOL, STAINED_GLASS_PANE, MAP. */
    public static final boolean PRE_FLAT;

    /** &lt; 1.14 — no PersistentDataContainer, no MapMeta.setMapView(). */
    public static final boolean PRE_PDC;

    /** &lt; 1.16 — Paper Adventure API (net.kyori.adventure.*) not bundled. */
    public static final boolean PRE_ADVENTURE;

    /** &lt; 1.18 — general legacy flag (chỉ ảnh hưởng chọn material "đẹp" cho denom GUI). */
    public static final boolean IS_LEGACY;

    static {
        // ── PRE_FLAT: thử thẳng material 1.13+ thật có tồn tại không ─────────
        // RED_WOOL chỉ xuất hiện từ 1.13 (flattening). Nếu Material.valueOf() báo
        // không có enum constant này → chắc chắn đang ở < 1.13 (WOOL kiểu cũ).
        boolean preFlat;
        try {
            Material.valueOf("RED_WOOL");
            preFlat = false;
        } catch (IllegalArgumentException e) {
            preFlat = true;
        }
        PRE_FLAT = preFlat;

        // ── PRE_PDC: class PersistentDataContainer chỉ có từ 1.14 ─────────────
        boolean prePdc;
        try {
            Class.forName("org.bukkit.persistence.PersistentDataContainer");
            prePdc = false;
        } catch (ClassNotFoundException e) {
            prePdc = true;
        }
        PRE_PDC = prePdc;

        // ── PRE_ADVENTURE: Paper bundle Adventure API từ 1.16.5+ ──────────────
        boolean preAdventure;
        try {
            Class.forName("net.kyori.adventure.text.Component");
            preAdventure = false;
        } catch (ClassNotFoundException e) {
            preAdventure = true;
        }
        PRE_ADVENTURE = preAdventure;

        // IS_LEGACY: dùng chung mốc Adventure (≈1.16) cho đơn giản — chỉ ảnh hưởng
        // chọn material "đẹp" (NETHERITE_INGOT,...) hay len màu cho GUI denom;
        // getDenomMaterial() đã có safeMatOf() tự fallback nên an toàn dù lệch mốc thật.
        IS_LEGACY = preAdventure;

        // MINOR: best-effort, CHỈ để log/hiển thị — không còn ai dùng để rẽ nhánh logic
        // quan trọng nữa nên dù parse sai cũng không gây crash được như trước.
        int minor = 21;
        try {
            String sv = Bukkit.getServer().getVersion();
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\\(MC:\\s*(\\d+)\\.(\\d+)").matcher(sv);
            if (m.find()) minor = Integer.parseInt(m.group(2));
        } catch (Exception ignored) {}
        MINOR = minor;
    }

    // ── Adventure availability ────────────────────────────────────────────────

    /**
     * Returns {@code true} if {@code net.kyori.adventure.text.Component}
     * is on the classpath at runtime. Đơn giản là phần đảo của {@link #PRE_ADVENTURE}
     * (đã tính 1 lần duy nhất bằng feature-detection trong static block ở trên).
     */
    public static boolean hasAdventure() {
        return !PRE_ADVENTURE;
    }

    // ── Material helpers ──────────────────────────────────────────────────────

    /**
     * Wool data index → 1.13+ flat name mapping.
     * Indices match the 1.12 wool damage values (0 = white … 15 = black).
     */
    private static final String[] FLAT_WOOL = {
        "WHITE_WOOL", "ORANGE_WOOL",    "MAGENTA_WOOL",    "LIGHT_BLUE_WOOL",
        "YELLOW_WOOL", "LIME_WOOL",     "PINK_WOOL",       "GRAY_WOOL",
        "LIGHT_GRAY_WOOL", "CYAN_WOOL", "PURPLE_WOOL",     "BLUE_WOOL",
        "BROWN_WOOL",  "GREEN_WOOL",    "RED_WOOL",        "BLACK_WOOL"
    };

    /**
     * Creates a wool {@link ItemStack} using the 1.12 data index (0–15).
     * <ul>
     *   <li>1.13+ → flat material name (e.g. {@code RED_WOOL})
     *   <li>1.12  → {@code WOOL} with durability/data = {@code dataIndex}
     * </ul>
     */
    @SuppressWarnings("deprecation")
    public static ItemStack makeWoolItem(int dataIndex, String displayName, List<String> lore) {
        int idx = dataIndex & 0xF;
        ItemStack item;
        if (PRE_FLAT) {
            item = new ItemStack(Material.valueOf("WOOL"), 1, (short) idx);
        } else {
            item = new ItemStack(Material.valueOf(FLAT_WOOL[idx]));
        }
        applyMeta(item, displayName, lore);
        return item;
    }

    /**
     * Convenience wrapper: green (dark-green = data 13) or red (data 14) wool.
     * Used in config/setup GUIs to indicate configured/unconfigured state.
     */
    public static ItemStack makeStatusWool(boolean green, String displayName, List<String> lore) {
        // data 13 = GREEN_WOOL (dark green, same as the original Material.GREEN_WOOL)
        // data 14 = RED_WOOL
        return makeWoolItem(green ? 13 : 14, displayName, lore);
    }

    /**
     * Gray glass pane filler:
     * <ul>
     *   <li>1.13+ → {@code GRAY_STAINED_GLASS_PANE}
     *   <li>1.12  → {@code STAINED_GLASS_PANE} data 7
     * </ul>
     */
    @SuppressWarnings("deprecation")
    public static ItemStack makeGlassPaneItem() {
        ItemStack item;
        if (PRE_FLAT) {
            item = new ItemStack(Material.valueOf("STAINED_GLASS_PANE"), 1, (short) 7);
        } else {
            item = new ItemStack(Material.valueOf("GRAY_STAINED_GLASS_PANE"));
        }
        applyMeta(item, " ", null);
        return item;
    }

    /**
     * Denomination → display material (1.18+ only).
     * On servers below 1.18, call {@link #makeDenomItem} instead which returns
     * a coloured wool item appropriate for the version.
     */
    public static Material getDenomMaterial(int denom) {
        if (denom == 10_000)    return Material.COAL;
        if (denom == 20_000)    return Material.IRON_INGOT;
        if (denom == 30_000)    return Material.GOLD_INGOT;
        if (denom == 50_000)    return Material.DIAMOND;
        if (denom == 100_000)   return Material.PRISMARINE_SHARD;
        if (denom == 200_000)   return safeMatOf("AMETHYST_SHARD",  "QUARTZ");
        if (denom == 300_000)   return safeMatOf("ECHO_SHARD",      "NETHER_STAR");
        if (denom == 500_000)   return safeMatOf("NETHERITE_SCRAP", "IRON_NUGGET");
        if (denom == 1_000_000) return safeMatOf("NETHERITE_INGOT", "GOLD_NUGGET");
        return Material.GOLD_INGOT;
    }

    /**
     * Wool data index (0–15) for each denomination when running below 1.18.
     * Ordered to match the DENOMS LinkedHashMap: 10k, 20k, 30k, 50k, 100k, 200k, 300k, 500k, 1000k.
     * Colors form a rough value gradient (light → dark).
     */
    private static final int[] DENOM_WOOL = {
         0,  // 10k   → WHITE
         4,  // 20k   → YELLOW
         1,  // 30k   → ORANGE
         9,  // 50k   → CYAN
         3,  // 100k  → LIGHT_BLUE
        11,  // 200k  → BLUE
        10,  // 300k  → PURPLE
        14,  // 500k  → RED
        13   // 1000k → GREEN (dark)
    };

    /** Ordered denom list matching DENOM_WOOL — must stay in sync with GuiUtil.DENOMS. */
    private static final int[] DENOM_VALUES =
        {10_000, 20_000, 30_000, 50_000, 100_000, 200_000, 300_000, 500_000, 1_000_000};

    /**
     * Creates the display ItemStack for a denomination slot.
     * <ul>
     *   <li>1.18+ → original materials (COAL, DIAMOND, NETHERITE_INGOT…)
     *   <li>1.12–1.17 → coloured wool (same as telco selection GUI)
     * </ul>
     * Use this method instead of {@code makeItem(getDenomMaterial(denom), …)} everywhere.
     */
    public static ItemStack makeDenomItem(int denom, String displayName, List<String> lore) {
        if (!IS_LEGACY) {
            // 1.18+: dùng vật liệu hiện tại (NETHERITE_INGOT, DIAMOND, v.v.)
            return makeSimpleItem(getDenomMaterial(denom), displayName, lore);
        }
        // < 1.18: dùng len màu cho nhất quán và tránh vật liệu thiếu
        int woolData = 4; // yellow fallback
        for (int i = 0; i < DENOM_VALUES.length; i++) {
            if (DENOM_VALUES[i] == denom) { woolData = DENOM_WOOL[i]; break; }
        }
        return makeWoolItem(woolData, displayName, lore);
    }

    /** Simple ItemStack builder (does NOT apply data values — for 1.13+ flat materials). */
    private static ItemStack makeSimpleItem(Material mat, String displayName, List<String> lore) {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(mat);
        applyMeta(item, displayName, lore);
        return item;
    }

    /** Try {@code preferred}, fall back to {@code fallback}, finally GOLD_INGOT. */
    public static Material safeMatOf(String preferred, String fallback) {
        try { return Material.valueOf(preferred); }
        catch (Exception i1) {
            try { return Material.valueOf(fallback); }
            catch (Exception i2) { return Material.GOLD_INGOT; }
        }
    }

    // ── QR Map helpers ────────────────────────────────────────────────────────

    /**
     * Calls {@code MapView.setTrackingPosition(false)} safely.
     * The method was added in Bukkit 1.14; on 1.12–1.13 it simply doesn't exist
     * and must be skipped to avoid a {@link NoSuchMethodError}.
     */
    public static void setTrackingPositionSafe(MapView view, boolean track) {
        if (PRE_PDC) return; // < 1.14: method does not exist, skip silently
        try {
            view.setTrackingPosition(track);
        } catch (Exception | Error ignored) {
            // Extra safety net for any edge-case server builds
        }
    }

    /**
     * Map item material:
     * <ul>
     *   <li>1.13+ → {@code FILLED_MAP}
     *   <li>1.12  → {@code MAP}
     * </ul>
     */
    public static Material getMapMaterial() {
        return PRE_FLAT ? Material.valueOf("MAP") : Material.valueOf("FILLED_MAP");
    }

    /**
     * In-memory invoice store used on 1.12–1.13 (no PDC).
     * Key: MapView ID, Value: invoice ID.
     * Also used on 1.14+ as a fast first-lookup before PDC.
     */
    private static final ConcurrentHashMap<Integer, String> MAP_INVOICE_CACHE =
            new ConcurrentHashMap<>();

    /**
     * Stores an invoice ID on the map item.
     * <ul>
     *   <li>1.14+ → PersistentDataContainer (survives restarts)
     *   <li>all versions → in-memory cache keyed by MapView ID
     * </ul>
     * Must be called BEFORE {@link #applyMapToItem} so the meta already
     * has PDC data when it is applied to the ItemStack.
     */
    public static void storeInvoiceId(MapMeta meta, MapView view,
                                       String invoiceId, NamespacedKey key) {
        // In-memory cache — always written regardless of version
        MAP_INVOICE_CACHE.put(view.getId(), invoiceId);

        // PDC write (1.14+ only)
        if (!PRE_PDC) {
            meta.getPersistentDataContainer()
                .set(key, PersistentDataType.STRING, invoiceId);
        }
    }

    /**
     * Retrieves the invoice ID associated with a map item.
     * Returns {@code null} if the item is not a QR map or has no stored ID.
     *
     * <p>Lookup order:
     * <ol>
     *   <li>In-memory cache (fast, all versions)
     *   <li>PersistentDataContainer (1.14+, survives restarts)
     *   <li>Item lore extraction as last-resort fallback
     * </ol>
     */
    public static String getInvoiceId(ItemStack item, NamespacedKey key) {
        if (item == null || item.getType() != getMapMaterial()) return null;
        ItemMeta raw = item.getItemMeta();
        if (!(raw instanceof MapMeta meta)) return null;

        // 1. In-memory (fast path, works on all versions)
        int mapId = getMapViewId(item, meta);
        if (mapId >= 0) {
            String cached = MAP_INVOICE_CACHE.get(mapId);
            if (cached != null) return cached;
        }

        // 2. PDC (1.14+, persists across restarts)
        if (!PRE_PDC) {
            try {
                String id = meta.getPersistentDataContainer()
                               .get(key, PersistentDataType.STRING);
                if (id != null) {
                    if (mapId >= 0) MAP_INVOICE_CACHE.put(mapId, id); // warm cache
                    return id;
                }
            } catch (Exception ignored) {}
        }

        // 3. Lore fallback — extract "§7Mã đơn: §f<id>"
        if (meta.hasLore() && meta.getLore() != null && !meta.getLore().isEmpty()) {
            String first = meta.getLore().get(0);
            String prefix = "§7Mã đơn: §f";
            if (first.startsWith(prefix)) {
                String id = first.substring(prefix.length()).trim();
                if (!id.isEmpty()) {
                    if (mapId >= 0) MAP_INVOICE_CACHE.put(mapId, id);
                    return id;
                }
            }
        }
        return null;
    }

    /** Clear the in-memory cache entry when a QR map expires or is collected. */
    public static void clearInvoiceCache(int mapViewId) {
        MAP_INVOICE_CACHE.remove(mapViewId);
    }

    /**
     * Applies a {@link MapView} to a map ItemStack across all versions.
     * <p>
     * v5.0.0 (fix): trước đây rẽ nhánh theo so sánh {@code MINOR} (lấy từ parse string
     * version — đã chứng minh không bền vững). Giờ THỬ TRỰC TIẾP API mới nhất trước
     * (try/catch), tự rơi xuống cách cũ hơn nếu API đó không tồn tại trên server thật —
     * không còn phụ thuộc việc tính MINOR có đúng hay không.
     * <ul>
     *   <li>1.14+ → {@code MapMeta.setMapView(view)}
     *   <li>1.13  → {@code MapMeta.setMapId(int)} via reflection
     *   <li>1.12  → {@code ItemStack.setDurability((short) mapId)} (deprecated but functional)
     * </ul>
     * Always calls {@code mapItem.setItemMeta(meta)} internally.
     *
     * @return the same {@code mapItem} instance (for chaining)
     */
    @SuppressWarnings("deprecation")
    public static ItemStack applyMapToItem(ItemStack mapItem, MapMeta meta, MapView view) {
        try {
            meta.setMapView(view); // 1.14+: API chuẩn
            mapItem.setItemMeta(meta);
            return mapItem;
        } catch (Throwable ignored) {
            // Method không tồn tại / không hoạt động trên server này → thử cách cũ hơn
        }
        try {
            mapItem.setItemMeta(meta);
            meta.getClass().getMethod("setMapId", int.class).invoke(meta, view.getId());
            mapItem.setItemMeta(meta);
            return mapItem;
        } catch (Throwable ignored) {
            // setMapId() cũng không có → server quá cũ (1.12) hoặc quá mới đã đổi API khác
        }
        // Absolute fallback: durability lưu map ID (hoạt động trên mọi version có ItemStack)
        mapItem.setItemMeta(meta);
        mapItem.setDurability((short) view.getId());
        return mapItem;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Apply display name and lore to an ItemStack's meta. */
    private static void applyMeta(ItemStack item, String displayName, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (displayName != null) meta.setDisplayName(displayName);
        if (lore        != null) meta.setLore(lore);
        item.setItemMeta(meta);
    }

    /**
     * Retrieves the MapView ID from a map ItemStack.
     * Returns -1 if unable to determine.
     * <p>
     * v5.0.0 (fix): thử thẳng {@code getMapId()} bằng reflection trước (1.13+), tự rơi
     * xuống durability (1.12) nếu method không tồn tại — không còn so sánh MINOR.
     */
    @SuppressWarnings("deprecation")
    private static int getMapViewId(ItemStack item, MapMeta meta) {
        try {
            Object id = meta.getClass().getMethod("getMapId").invoke(meta);
            if (id instanceof Integer i) return i;
        } catch (Throwable ignored) {
            // Method không tồn tại trên server này → fallback durability
        }
        return item.getDurability();
    }
}
