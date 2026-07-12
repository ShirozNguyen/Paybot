package com.naptien.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.util.Identifier;

import java.util.Optional;

/**
 * McVersionHelper — Cung cấp thông tin và utility cho đa phiên bản Minecraft.
 *
 * Hỗ trợ từ MC 1.20.1 đến 26.x trong 1 JAR duy nhất.
 *
 * Changelog:
 *   v5.2.0 [Part 1] — Khởi tạo. Detect MC version, Identifier compat wrapper,
 *                      version grouping (GROUP_1_20, GROUP_1_21, GROUP_26X).
 */
public final class McVersionHelper {

    // --- Version Groups -------------------------------------------------------
    /** MC 1.20.x (1.20.0 – 1.20.6) */
    public static final int GROUP_1_20    = 1;
    /** MC 1.21.x (1.21.0 – 1.21.11+) */
    public static final int GROUP_1_21    = 2;
    /** MC 26.x (26.1, 26.2, ...) */
    public static final int GROUP_26X     = 3;
    /** Không xác định — fallback an toàn về API cũ nhất */
    public static final int GROUP_UNKNOWN = 0;

    private static String _rawVersion = null;
    private static int    _group      = -1;

    private McVersionHelper() {}

    // --- Public API -----------------------------------------------------------

    /** Trả về version MC đang chạy, ví dụ "1.21.1" hoặc "26.2". */
    public static String getRawVersion() {
        if (_rawVersion == null) _rawVersion = detectRawVersion();
        return _rawVersion;
    }

    /** Trả về nhóm version. */
    public static int getGroup() {
        if (_group < 0) _group = detectGroup(getRawVersion());
        return _group;
    }

    public static boolean is1_20x()  { return getGroup() == GROUP_1_20; }
    public static boolean is1_21x()  { return getGroup() == GROUP_1_21; }
    public static boolean is26x()    { return getGroup() == GROUP_26X;  }

    /** SGUI 1.x tương thích với 1.20.x và 1.21.x */
    public static boolean usesSguiV1() { return getGroup() <= GROUP_1_21 && getGroup() != GROUP_UNKNOWN; }
    /** SGUI 2.x tương thích với 26.x */
    public static boolean usesSguiV2() { return getGroup() == GROUP_26X; }

    /**
     * Tạo Identifier an toàn trên mọi phiên bản.
     * Identifier.of() được thêm từ 1.21 — với 1.20.x dùng constructor cũ qua fallback.
     */
    @SuppressWarnings("deprecation")
    public static Identifier id(String namespace, String path) {
        try {
            // Identifier.of() tồn tại từ 1.21+
            return Identifier.of(namespace, path);
        } catch (NoSuchMethodError | Exception e) {
            // Fallback bằng Reflection cho MC 1.20.x (nơi Identifier.of chưa tồn tại
            // và constructor Identifier(String, String) vẫn là public)
            try {
                return Identifier.class.getConstructor(String.class, String.class)
                        .newInstance(namespace, path);
            } catch (Exception ex) {
                // Fallback cuối cùng
                throw new RuntimeException("Không thể tạo Identifier: " + namespace + ":" + path, ex);
            }
        }
    }

    public static Identifier paybot(String path) { return id("paybot", path); }
    public static Identifier mc(String path)     { return id("minecraft", path); }

    // --- Internal ------------------------------------------------------------

    private static String detectRawVersion() {
        try {
            Optional<ModContainer> mc = FabricLoader.getInstance()
                    .getModContainer("minecraft");
            if (mc.isPresent())
                return mc.get().getMetadata().getVersion().getFriendlyString();
        } catch (Exception ignored) {}
        return "unknown";
    }

    private static int detectGroup(String raw) {
        if (raw == null || raw.isEmpty() || raw.equals("unknown")) return GROUP_UNKNOWN;
        if (raw.startsWith("26."))  return GROUP_26X;
        if (raw.startsWith("1.21")) return GROUP_1_21;
        if (raw.startsWith("1.20")) return GROUP_1_20;
        return GROUP_UNKNOWN;
    }
}
