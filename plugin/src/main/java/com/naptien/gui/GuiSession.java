package com.naptien.gui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lưu trạng thái UI/chat per-player dùng chung cho tất cả GUI.
 *
 * Changelog:
 *   v4.1.0 — Tái tạo từ .class (file nguồn gốc không có trong ZIP)
 */
public class GuiSession {

    // ─── Stage enum ───────────────────────────────────────────────────────────
    public enum Stage {
        NONE               (false),
        CARD_TELCO         (false),
        CARD_DENOM         (false),
        CARD_WAIT_SERIAL   (true),   // chờ serial từ chat
        CARD_WAIT_CODE     (true),   // chờ mã thẻ từ chat
        BANK_DENOM         (false),
        TOPUP_LIST         (false),
        EDIT_WAIT_AMT_CARD (true),   // chờ số thưởng (card) từ chat
        EDIT_WAIT_AMT_BANK (true),   // chờ số thưởng (bank) từ chat
        EDIT_WAIT_CMD_CARD (true),   // chờ lệnh thưởng (card) từ chat
        EDIT_WAIT_CMD_BANK (true);   // chờ lệnh thưởng (bank) từ chat
        // v5.0.2: API_WAIT_PARTNER_ID / API_WAIT_PARTNER_KEY / API_WAIT_CHANNEL đã xoá —
        // flow GUI Card API Setup không còn (gộp vào /cardsetup, xem CardSetupCommand).

        /** true → listener chat cần intercept và cancel sự kiện. */
        public final boolean waitForChat;
        Stage(boolean waitForChat) { this.waitForChat = waitForChat; }
    }

    // ─── Session data ─────────────────────────────────────────────────────────
    public Stage  stage     = Stage.NONE;
    public String telco     = "";
    public int    denom     = 0;
    public String serial    = "";
    public String cardCode  = "";  // v4.3.0: mã thẻ (hỏi TRƯỚC serial)
    public int    editDenom = 0;   // dùng cho: denom đang chỉnh (ChinhSuaGui) / page (TopupListGui)
    public String topupListFilter = null; // v5.0.0 (fix): "card"/"bank"/null(=all) — nhớ filter khi phân trang TopupListGui
    public String editType  = "";  // "card" | "bank"
    public String editCmd   = "";  // (deprecated v5.0.0, giữ lại tránh lỗi tham chiếu cũ — dùng editCmdList)
    public java.util.List<String> editCmdList = new java.util.ArrayList<>(); // v5.0.0: tối đa 10 lệnh thưởng/mệnh giá
    public String editAmt   = "";  // số lượng thưởng (hỏi SAU)
    public boolean testMode = false; // v5.0.0: /testnapbank, /testnapthe — giả lập nạp thành công để test reward+hiệu ứng

    // ─── Static session store ─────────────────────────────────────────────────
    private static final Map<UUID, GuiSession> SESSIONS = new ConcurrentHashMap<>();

    /** Lấy session của player (tạo mới nếu chưa có). */
    public static GuiSession get(UUID uuid) {
        return SESSIONS.computeIfAbsent(uuid, k -> new GuiSession());
    }

    /** Reset stage về NONE, giữ nguyên UUID trong map (để tái dùng). */
    public static void clear(UUID uuid) {
        GuiSession s = SESSIONS.get(uuid);
        if (s != null) {
            s.stage     = Stage.NONE;
            s.telco     = "";
            s.denom     = 0;
            s.serial    = "";
            s.cardCode  = "";   // v4.3.0: clear mã thẻ tạm
            s.editDenom = 0;
            s.topupListFilter = null;
            s.editType  = "";
            s.editCmd   = "";   // v4.3.0: BUG FIX — trước đây không clear, lệnh cũ bị giữ lại
            s.editCmdList.clear(); // v5.0.0: clear danh sách lệnh thưởng tạm (multi-cmd)
            s.editAmt   = "";
            s.testMode  = false; // v5.0.0: clear cờ test-mode (/testnapbank, /testnapthe)
        }
    }

    /** Xoá hoàn toàn session khỏi map (khi player quit). */
    public static void remove(UUID uuid) {
        SESSIONS.remove(uuid);
    }

    /** @return true nếu đang chờ input từ chat. */
    public boolean isWaitingForChat() {
        return stage.waitForChat;
    }
}
