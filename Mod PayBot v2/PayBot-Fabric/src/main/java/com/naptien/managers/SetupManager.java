package com.naptien.managers;

import com.naptien.PayBotMod;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SetupManager (Fabric) — quản lý setup wizard qua chat.
 * Dùng ServerMessageEvents.ALLOW_CHAT_MESSAGE.
 *
 * v5.0.2 FIX:
 *   - SePay setup: ĐỔI từ Merchant ID + Secret Key → SePay API Token (giống plugin).
 *     Sau khi nhập token, mod TỰ GỌI SePay API để lấy thông tin ngân hàng (STK, tên
 *     ngân hàng, chủ TK) — admin không cần nhập thủ công từng trường nữa.
 *   - Card setup: gộp bước hỏi channel-id (trước đây chỉ có ở /cardapisetup cũ).
 *   - Bỏ field apiSite/partnerId thừa (state được quản lý trong Session như cũ nhưng
 *     qua biến riêng).
 */
public class SetupManager {

    // ═══════════════════════════════════════════════════════════════════════════
    // v5.0.5 [Part 20]: TUTORIAL CONTENT — port nguyên từ SePaySetupCommand.java
    // (plugin) để 2 bên hiện đúng y hệt nội dung hướng dẫn, tham khảo docs.sepay.vn.
    // ═══════════════════════════════════════════════════════════════════════════
    private static final String[][] TUTORIAL_PAGES = {
        {
            "§6§l[Hướng dẫn 1/4] §eSePay là gì?",
            "§7SePay là dịch vụ giúp tự động nhận diện khi có người chuyển khoản ngân hàng",
            "§7vào tài khoản của bạn — dùng để xác nhận đơn nạp bank tự động, không cần",
            "§7admin check tay từng giao dịch.",
            "§7Nếu CHƯA có tài khoản SePay, đăng ký miễn phí tại: §bsepay.vn",
        },
        {
            "§6§l[Hướng dẫn 2/4] §eBước 1 — Liên kết tài khoản ngân hàng",
            "§7Nếu tài khoản SePay của bạn CHƯA liên kết ngân hàng nào, vào trang sau",
            "§7để thêm (chọn đúng ngân hàng bạn dùng để nhận tiền nạp):",
            "§b  https://docs.sepay.vn/them-tai-khoan-ngan-hang.html",
            "§7§o(Nếu đã liên kết rồi thì bỏ qua bước này.)",
        },
        {
            "§6§l[Hướng dẫn 3/4] §eBước 2 — Tạo API Token",
            "§71. Vào §bmy.sepay.vn/companyapi",
            "§72. Bấm §e+ Thêm API §7(góc trên bên phải)",
            "§73. Đặt tên bất kỳ (vd: PayBot), Trạng thái chọn §aHoạt động",
            "§74. Bấm §eThêm §7→ API Token sẽ hiện trong danh sách API Access",
            "§7Chi tiết: §bhttps://docs.sepay.vn/tao-api-token.html",
        },
        {
            "§6§l[Hướng dẫn 4/4] §eBước 3 — Dán Token vào đây",
            "§7Copy API Token vừa tạo (dạng chuỗi ký tự dài), rồi dán vào chat khi",
            "§7được hỏi ở bước tiếp theo.",
            "§7§oToken chỉ hiển thị 1 lần lúc tạo — nếu mất, vào lại my.sepay.vn/companyapi",
            "§7§ođể tạo token mới.",
        },
    };

    private static void sendTutorialPage(ServerPlayerEntity player, Session s) {
        for (String line : TUTORIAL_PAGES[s.tutorialPage]) player.sendMessage(Text.literal(line));
        if (s.tutorialPage < TUTORIAL_PAGES.length - 1) {
            player.sendMessage(Text.literal("§7§o→ Gõ §fnext §7§ođể xem tiếp (§ecancel §7§ođể huỷ):"));
        } else {
            s.step = Step.SEPAY_API_TOKEN;
            player.sendMessage(Text.literal("§6[PayBot] §eDán §bAPI Token SePay §evào chat:"));
            player.sendMessage(Text.literal("§7(cancel để huỷ)"));
        }
    }

    public enum Step {
        NONE,
        // SePay API Token setup (v5.0.2 — thay cho MERCHANT_ID/SECRET_KEY)
        // v5.0.5 [Part 20]: thêm 2 bước hỏi/hiện tutorial — đồng bộ với plugin
        // (SePaySetupCommand.java) vốn đã có sẵn hướng dẫn 4 trang chi tiết, Fabric
        // trước đây chỉ có 1 dòng gợi ý ngắn gọn, thiếu hẳn phần hướng dẫn từng bước.
        SEPAY_ASK_TUTORIAL, SEPAY_TUTORIAL,
        SEPAY_API_TOKEN, SEPAY_CHOOSE_ACCOUNT,
        // Card API setup (bao gồm channel-id)
        CARD_SITE, CARD_PARTNER_ID, CARD_PARTNER_KEY, CARD_CHANNEL_ID,
        // Reward setup
        REWARD_CMD_CARD, REWARD_CMD_BANK,
        // Denom edit
        DENOM_AMT_CARD, DENOM_AMT_BANK, DENOM_CMD_CARD, DENOM_CMD_BANK
    }

    public static class Session {
        public Step   step       = Step.NONE;
        public String apiSite    = "";  // card setup: site đang cấu hình
        public String partnerId  = "";  // card setup: partner ID tạm thời
        public String editDenom  = "";
        public String editType   = "";
        // SePay setup state
        public List<SePayApiClient.BankAccountInfo> bankAccounts = null; // danh sách tài khoản từ API
        public int    tutorialPage = 0; // v5.0.5 [Part 20]: trang tutorial SePay hiện tại
        public long   startedAt  = System.currentTimeMillis();

        public boolean isExpired() { return System.currentTimeMillis() - startedAt > 10 * 60_000L; }
        public boolean waitingForChat() { return step != Step.NONE; }
    }

    private final PayBotMod mod;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public SetupManager(PayBotMod mod) { this.mod = mod; }

    public Session startSession(ServerPlayerEntity player) {
        Session s = new Session();
        sessions.put(player.getUuid(), s);
        return s;
    }

    public void clearSession(ServerPlayerEntity player) { sessions.remove(player.getUuid()); }
    public Session getSession(ServerPlayerEntity player) { return sessions.get(player.getUuid()); }

    public boolean isInSession(ServerPlayerEntity player) {
        Session s = sessions.get(player.getUuid());
        return s != null && s.waitingForChat() && !s.isExpired();
    }

    public boolean handleChat(ServerPlayerEntity player, String input) {
        Session s = sessions.get(player.getUuid());
        if (s == null || !s.waitingForChat()) return false;
        if (s.isExpired()) { sessions.remove(player.getUuid()); return false; }
        if ("cancel".equalsIgnoreCase(input.trim())) {
            sessions.remove(player.getUuid());
            player.sendMessage(Text.literal("§7[PayBot] Đã huỷ."));
            return true;
        }
        processInput(player, s, input.trim());
        return true;
    }

    private void processInput(ServerPlayerEntity player, Session s, String input) {
        switch (s.step) {

            // ── v5.0.5 [Part 20]: hỏi có muốn xem tutorial không ────────────────
            case SEPAY_ASK_TUTORIAL -> {
                if (input.equalsIgnoreCase("yes")) {
                    s.step = Step.SEPAY_TUTORIAL;
                    s.tutorialPage = 0;
                    sendTutorialPage(player, s);
                } else if (input.equalsIgnoreCase("skip")) {
                    s.step = Step.SEPAY_API_TOKEN;
                    player.sendMessage(Text.literal("§6[PayBot] §eDán §bAPI Token SePay §evào chat:"));
                    player.sendMessage(Text.literal("§7(cancel để huỷ)"));
                } else {
                    player.sendMessage(Text.literal("§c[PayBot] §fGõ §ayes §fhoặc §bskip §f(hoặc §ecancel §fđể huỷ):"));
                }
            }
            case SEPAY_TUTORIAL -> {
                if (input.equalsIgnoreCase("next")) {
                    s.tutorialPage++;
                    sendTutorialPage(player, s);
                } else {
                    player.sendMessage(Text.literal("§c[PayBot] §fGõ §fnext §fđể xem tiếp (hoặc §ecancel §fđể huỷ):"));
                }
            }

            // ── SePay API Token ──────────────────────────────────────────────
            case SEPAY_API_TOKEN -> {
                // Lưu token tạm vào config, thử verify và lấy danh sách tài khoản
                String token = input.trim();
                mod.getConfig().set("sepay-api.api-token", token);
                mod.getConfig().save();
                player.sendMessage(Text.literal("§7[PayBot] Đang xác minh token và lấy danh sách ngân hàng..."));
                mod.runAsync(() -> {
                    SePayApiClient client = new SePayApiClient(mod);
                    String err = client.testToken();
                    if (err != null) {
                        mod.runOnMainThread(() -> {
                            player.sendMessage(Text.literal("§c[PayBot] §fToken không hợp lệ: " + err));
                            player.sendMessage(Text.literal("§7Lấy token tại: My.SePay.vn → Cấu hình Công ty → API Access"));
                            player.sendMessage(Text.literal("§7Nhập lại token hoặc §ccancel §7để huỷ:"));
                        });
                        return;
                    }
                    List<SePayApiClient.BankAccountInfo> accounts = client.listBankAccounts();
                    if (accounts.isEmpty()) {
                        mod.runOnMainThread(() -> {
                            player.sendMessage(Text.literal("§c[PayBot] §fToken hợp lệ nhưng không tìm thấy tài khoản ngân hàng nào liên kết với SePay!"));
                            player.sendMessage(Text.literal("§7Vui lòng liên kết tài khoản ngân hàng tại my.sepay.vn rồi thử lại."));
                            sessions.remove(player.getUuid());
                        });
                        return;
                    }
                    if (accounts.size() == 1) {
                        // Chỉ 1 tài khoản → dùng luôn, không hỏi thêm
                        saveSePayAccount(accounts.get(0));
                        mod.runOnMainThread(() -> showSePayDone(player, accounts.get(0)));
                        sessions.remove(player.getUuid());
                    } else {
                        // Nhiều tài khoản → hỏi chọn
                        s.bankAccounts = accounts;
                        s.step = Step.SEPAY_CHOOSE_ACCOUNT;
                        mod.runOnMainThread(() -> {
                            player.sendMessage(Text.literal("§6[PayBot] §fTìm thấy §e" + accounts.size() + " §ftài khoản ngân hàng:"));
                            for (int i = 0; i < accounts.size(); i++) {
                                SePayApiClient.BankAccountInfo a = accounts.get(i);
                                player.sendMessage(Text.literal("§7[" + (i + 1) + "] §f" + a.bankShortName
                                        + " — §e" + a.accountNumber + " §7(§f" + a.accountHolderName + "§7)"));
                            }
                            player.sendMessage(Text.literal("§7Nhập số §e1-" + accounts.size() + " §7để chọn tài khoản nhận tiền:"));
                        });
                    }
                });
            }

            case SEPAY_CHOOSE_ACCOUNT -> {
                List<SePayApiClient.BankAccountInfo> accounts = s.bankAccounts;
                if (accounts == null || accounts.isEmpty()) {
                    sessions.remove(player.getUuid());
                    return;
                }
                int idx;
                try { idx = Integer.parseInt(input) - 1; } catch (NumberFormatException e) {
                    player.sendMessage(Text.literal("§c[PayBot] §fVui lòng nhập số từ 1 đến " + accounts.size() + "."));
                    return;
                }
                if (idx < 0 || idx >= accounts.size()) {
                    player.sendMessage(Text.literal("§c[PayBot] §fSố không hợp lệ (1-" + accounts.size() + ")."));
                    return;
                }
                SePayApiClient.BankAccountInfo chosen = accounts.get(idx);
                saveSePayAccount(chosen);
                sessions.remove(player.getUuid());
                showSePayDone(player, chosen);
            }

            // ── Card API ─────────────────────────────────────────────────────
            // v5.0.3 FIX: case CARD_SITE bị thiếu hoàn toàn — nhập site ở bước [1/4]
            // trước đây rơi vào default -> huỷ session. Logic validate mirror đúng
            // theo nhánh /cardsetup <site> (argument) đã có sẵn trong CommandRegistry.
            case CARD_SITE -> {
                String site = input.toLowerCase().trim();
                if (!com.naptien.managers.StandaloneCardProcessor.getSupportedSites().containsKey(site)) {
                    String sites = String.join(", ", com.naptien.managers.StandaloneCardProcessor.getSupportedSites().keySet());
                    player.sendMessage(Text.literal("§c[PayBot] §fSite không hợp lệ. Dùng: §f" + sites));
                    player.sendMessage(Text.literal("§7(cancel để huỷ)"));
                    return; // giữ nguyên step CARD_SITE, cho nhập lại thay vì huỷ session
                }
                s.apiSite = site;
                s.step = Step.CARD_PARTNER_ID;
                player.sendMessage(Text.literal("§a[PayBot] §eSite: §f" + site));
                player.sendMessage(Text.literal("§e[2/4] §fNhập §bPartner ID §f(lấy từ " + site + "):"));
            }
            case CARD_PARTNER_ID -> {
                s.partnerId = input;
                mod.getConfig().set("card-api.partner-id", input); mod.getConfig().save();
                s.step = Step.CARD_PARTNER_KEY;
                player.sendMessage(Text.literal("§a[PayBot] [3/4] §fNhập §bPartner Key§f: (cancel để huỷ)"));
            }
            case CARD_PARTNER_KEY -> {
                String site = s.apiSite;
                mod.getConfig().set("card-api.partner-key",               input);
                mod.getConfig().set("card-api-sites." + site + ".partner-id",  s.partnerId);
                mod.getConfig().set("card-api-sites." + site + ".partner-key", input);
                mod.getConfig().set("card-api.site",        site);
                mod.getConfig().set("card-api.configured",  !s.partnerId.isEmpty() && !input.isEmpty());
                mod.getConfig().save();
                s.step = Step.CARD_CHANNEL_ID;
                player.sendMessage(Text.literal("§a[PayBot] §a✓ Đã lưu Partner Key!"));
                player.sendMessage(Text.literal("§e[4/4] §fNhập §bID kênh Discord §fđể nhận thông báo:"));
                player.sendMessage(Text.literal("§7§o(Chỉ có tác dụng khi đã connect bot — gõ §cskip §7§ohoặc §ccancel §7§ođể bỏ qua)"));
            }
            case CARD_CHANNEL_ID -> {
                if (!"skip".equalsIgnoreCase(input)) {
                    mod.getConfig().set("card-api-sites." + s.apiSite + ".channel-id", input);
                    mod.getConfig().save();
                    if (!mod.isStandaloneMode())
                        mod.runAsync(() -> mod.getBotHttpClient().updateNotificationChannel(input));
                }
                sessions.remove(player.getUuid());
                showCardDone(player, s.apiSite, s.partnerId, input);
            }

            // ── Denom edit ───────────────────────────────────────────────────
            case DENOM_AMT_CARD, DENOM_AMT_BANK -> {
                String section = s.step == Step.DENOM_AMT_CARD ? "denom-rewards-card" : "denom-rewards-bank";
                mod.getConfig().set(section + "." + s.editDenom + ".amt", input);
                mod.getConfig().save();
                s.step = s.step == Step.DENOM_AMT_CARD ? Step.DENOM_CMD_CARD : Step.DENOM_CMD_BANK;
                player.sendMessage(Text.literal("§a[PayBot] §fNhập §blệnh thưởng §f({player}=[tên], [amount]=[số]):"));
            }
            case DENOM_CMD_CARD, DENOM_CMD_BANK -> {
                String section = s.step == Step.DENOM_CMD_CARD ? "denom-rewards-card" : "denom-rewards-bank";
                mod.getConfig().set(section + "." + s.editDenom + ".cmd", input);
                mod.getConfig().save();
                sessions.remove(player.getUuid());
                player.sendMessage(Text.literal("§a[PayBot] §fĐã lưu cấu hình mệnh giá §e" + s.editDenom + "§f."));
            }
            default -> sessions.remove(player.getUuid());
        }
    }

    /** Lưu thông tin tài khoản SePay vào config. */
    private void saveSePayAccount(SePayApiClient.BankAccountInfo a) {
        mod.getConfig().set("sepay-api.bank-account-number", a.accountNumber);
        mod.getConfig().set("sepay-api.bank-short-name",     a.bankShortName);
        mod.getConfig().set("sepay-api.bank-full-name",      a.bankFullName);
        mod.getConfig().set("sepay-api.bank-bin",            a.bankBin);
        mod.getConfig().set("sepay-api.account-holder-name", a.accountHolderName);
        // Cũng lưu vào sepay.* cũ cho QR generation tương thích ngược
        mod.getConfig().set("sepay.bank-account",  a.accountNumber);
        mod.getConfig().set("sepay.bank-name",     a.bankShortName);
        mod.getConfig().set("sepay.account-name",  a.accountHolderName);
        mod.getConfig().set("sepay.configured",    true);
        mod.getConfig().save();
        PayBotMod.LOGGER.info("[PayBot] SePay configured: bank=" + a.bankShortName
                + " account=" + a.accountNumber + " holder=" + a.accountHolderName);
    }

    private void showSePayDone(ServerPlayerEntity player, SePayApiClient.BankAccountInfo a) {
        player.sendMessage(Text.literal("§a§l[PayBot] §r§aCấu hình SePay API thành công!"));
        player.sendMessage(Text.literal("§7Ngân hàng : §f" + a.bankFullName + " (§e" + a.bankShortName + "§f)"));
        player.sendMessage(Text.literal("§7Số TK     : §f" + a.accountNumber));
        player.sendMessage(Text.literal("§7Chủ TK    : §f" + a.accountHolderName));
        player.sendMessage(Text.literal("§7Mod sẽ tự poll SePay API mỗi §e"
                + mod.getConfig().getInt("sepay-api.poll-interval-seconds", 10) + "s §7để phát hiện giao dịch."));
        // v5.0.5: XOÁ dòng hiển thị "webhook (tuỳ chọn)" — trước đây còn show URL
        // ngrok cũ cho admin (dù optional) dù đã chuyển hoàn toàn tự động (self-poll),
        // không còn lý do gì cần webhook nữa, giữ lại chỉ gây rối/nhầm lẫn cho admin.
        player.sendMessage(Text.literal("§7Không cần webhook, không cần bot Discord — /napbank đã sẵn sàng dùng ngay!"));
    }

    private void showCardDone(ServerPlayerEntity player, String site, String pid, String channel) {
        player.sendMessage(Text.literal("§6§l═══════════════════════════════════════"));
        player.sendMessage(Text.literal("§a§l✓ Cấu hình Card API hoàn tất!"));
        player.sendMessage(Text.literal("§7Site       : §f" + site));
        player.sendMessage(Text.literal("§7Partner ID : §f" + pid));
        player.sendMessage(Text.literal("§7Channel ID : §f" + ("skip".equalsIgnoreCase(channel) ? "§8(bỏ qua)" : channel)));
        player.sendMessage(Text.literal("§7Tiếp theo: /chinhsuamenhgianap để cấu hình lệnh thưởng."));
        player.sendMessage(Text.literal("§6§l═══════════════════════════════════════"));
    }
}
