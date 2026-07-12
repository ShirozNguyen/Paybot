package com.naptien.gui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GuiSession (Fabric) — trạng thái GUI per-player.
 * Giống hệt plugin version, không cần Bukkit API.
 * Chat interception qua ServerMessageEvents đăng ký trong PayBotMod.
 *
 * Changelog:
 *   v4.0.1-fabric — initial
 *   v4.0.2-fabric — thêm field editCmd (thiếu so với GuiChatHandler)
 */
public class GuiSession {

    public enum Stage {
        NONE               (false),
        CARD_TELCO         (false),
        CARD_DENOM         (false),
        CARD_WAIT_SERIAL   (true),
        CARD_WAIT_CODE     (true),
        BANK_DENOM         (false),
        TOPUP_LIST         (false),
        EDIT_WAIT_AMT_CARD (true),
        EDIT_WAIT_AMT_BANK (true),
        EDIT_WAIT_CMD_CARD (true),
        EDIT_WAIT_CMD_BANK (true),
        API_WAIT_PARTNER_ID  (true),  // v5.0.2: deprecated — xem SetupManager.Step.CARD_PARTNER_ID
        API_WAIT_PARTNER_KEY (true),  // v5.0.2: deprecated
        API_WAIT_CHANNEL     (true);  // v5.0.2: deprecated

        public final boolean waitForChat;
        Stage(boolean w) { waitForChat = w; }
    }

    public Stage  stage     = Stage.NONE;
    public String telco     = "";
    public int    denom     = 0;
    public String code      = "";   // mã thẻ (hỏi trước serial)
    public String serial    = "";
    public int    editDenom = 0;
    public String editType  = "";
    public String editAmt   = "";
    public String editCmd   = "";   // v4.0.2: thêm field này — cần thiết cho EDIT_WAIT_CMD_*
    public String apiSite   = "";
    public String partnerId = "";
    // v5.0.5 [Part 19]: /testnapbank /testnapthe giờ mở ĐÚNG NapBankGui/NapTheGui thật
    // (đồng bộ với plugin) thay vì GUI riêng giả — set true trước khi open(), NapBankGui/
    // NapTheGui tự check cờ này ở bước chọn mệnh giá để giả lập thành công thay vì gọi
    // API/tạo đơn thật. Tự reset về false ngay sau khi dùng (dùng 1 lần).
    public boolean testMode  = false;

    private static final Map<UUID, GuiSession> SESSIONS = new ConcurrentHashMap<>();

    public static GuiSession get(UUID uuid) {
        return SESSIONS.computeIfAbsent(uuid, k -> new GuiSession());
    }

    public static void clear(UUID uuid) {
        GuiSession s = SESSIONS.get(uuid);
        if (s == null) return;
        s.stage = Stage.NONE; s.telco = ""; s.denom = 0; s.code = ""; s.serial = "";
        s.editDenom = 0; s.editType = ""; s.editAmt = ""; s.editCmd = "";
        s.apiSite = ""; s.partnerId = "";
    }

    public static void remove(UUID uuid) { SESSIONS.remove(uuid); }

    public boolean isWaitingForChat() { return stage.waitForChat; }

    /**
     * Gọi từ PayBotMod.ALLOW_CHAT_MESSAGE. Return true → cancel (không broadcast).
     * Logic xử lý chat được delegate sang GuiChatHandler.
     */
    public static boolean isAnyoneWaiting(UUID uuid) {
        GuiSession s = SESSIONS.get(uuid);
        return s != null && s.isWaitingForChat();
    }
}