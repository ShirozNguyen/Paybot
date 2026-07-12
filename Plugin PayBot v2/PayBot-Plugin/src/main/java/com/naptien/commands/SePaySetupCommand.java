package com.naptien.commands;

import com.naptien.NapTienPlugin;
import com.naptien.managers.SePayApiClient;
import com.naptien.managers.SetupManager;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /sepaysetup — v5.0.0 (Phase B): VIẾT LẠI HOÀN TOÀN.
 * <p>
 * TRƯỚC ĐÂY: admin phải tự tay nhập 5 trường (secret-key, merchant-id, số TK, tên NH,
 * tên chủ TK) — không có gì được xác thực hay tự động lấy, dễ nhập sai, và còn cần
 * webhook (qua bot.py/ngrok) mới hoạt động được.
 * <p>
 * GIỜ: admin chỉ cần dán DUY NHẤT 1 API Token (lấy tại my.sepay.vn/companyapi).
 * Plugin tự gọi API của SePay để:
 *   1. Xác thực token còn hợp lệ không.
 *   2. Lấy danh sách tài khoản ngân hàng đã liên kết (tên NH, số TK, tên chủ TK) —
 *      KHÔNG cần admin tự nhập tay, tránh sai sót.
 *   3. Nếu CHƯA liên kết tài khoản ngân hàng nào trên SePay → báo + dẫn link hướng dẫn,
 *      bắt admin xác nhận đã liên kết rồi mới tiếp tục (kiểm tra lại bằng "confirm").
 * Không cần webhook/bot.py/ngrok — plugin tự POLL trực tiếp API SePay (xem SePayApiClient).
 * <p>
 * Có thêm hướng dẫn từng bước (gõ "yes" để xem, "skip" để bỏ qua) — hiển thị từng
 * trang một (gõ "next" để xem tiếp), tránh spam hết thông tin 1 lần.
 */
public class SePaySetupCommand implements CommandExecutor {

    private final NapTienPlugin plugin;

    public SePaySetupCommand(NapTienPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cLệnh này chỉ dành cho player!");
            return true;
        }
        if (!player.hasPermission("naptien.admin")) {
            player.sendMessage("§cYou Don't Have Permission To Use This!");
            return true;
        }
        if (plugin.getSetupManager().isInSession(player)) {
            player.sendMessage(NapTienPlugin.f("§c[PayBot] §fBạn đang trong phiên setup khác, gõ §ecancel §ftrong chat để huỷ."));
            return true;
        }

        showCurrentConfig(player);
        player.sendMessage("§6§l═════════════ §eCấu hình SePay (API Token) §6§l═════════════");
        player.sendMessage("§7Chỉ cần 1 API Token — không cần webhook, không cần bot/ngrok.");
        player.sendMessage("");
        player.sendMessage("§eBạn có muốn xem hướng dẫn từng bước lấy API Token không?");
        player.sendMessage("§a  yes  §7→ xem hướng dẫn (từng bước, gõ §fnext §7để qua bước tiếp)");
        player.sendMessage("§b  skip §7→ bỏ qua, nhập API Token luôn (nếu bạn đã có sẵn)");
        plugin.getSetupManager().startSession(player, new SePaySession());
        return true;
    }

    private void showCurrentConfig(Player player) {
        boolean newFlow = plugin.getConfig().getBoolean("sepay-api.enabled", false);
        if (newFlow) {
            player.sendMessage("§7Cấu hình SePay hiện tại: §a✓ (API Token)");
            player.sendMessage("§7  Ngân hàng: §f" + plugin.getConfig().getString("sepay-api.bank-full-name", "")
                    + " — " + plugin.getConfig().getString("sepay-api.bank-account-number", ""));
            player.sendMessage("§7Setup lại sẽ ghi đè cấu hình hiện tại.");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TUTORIAL CONTENT — tham khảo docs.sepay.vn, chia nhỏ từng trang
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

    private class SePaySession implements SetupManager.SetupSession {

        private Stage stage = Stage.ASK_TUTORIAL;
        private int tutorialPage = 0;
        private List<SePayApiClient.BankAccountInfo> foundAccounts;
        private SePayApiClient.BankAccountInfo chosenAccount;
        private boolean busy = false; // chặn double-input khi đang gọi API async

        private enum Stage { ASK_TUTORIAL, TUTORIAL, ASK_TOKEN, BANK_NOT_LINKED_CONFIRM, PICK_BANK_ACCOUNT, FINAL_CONFIRM }

        @Override
        public boolean handleInput(Player player, String input) {
            if (busy) {
                player.sendMessage(NapTienPlugin.f("§7[PayBot] §fĐang xử lý, vui lòng đợi..."));
                return false;
            }
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage(NapTienPlugin.f("§c[PayBot] §fĐã huỷ setup SePay."));
                return true;
            }
            switch (stage) {
                case ASK_TUTORIAL:          return handleAskTutorial(player, input);
                case TUTORIAL:              return handleTutorial(player, input);
                case ASK_TOKEN:             return handleAskToken(player, input);
                case BANK_NOT_LINKED_CONFIRM: return handleBankNotLinkedConfirm(player, input);
                case PICK_BANK_ACCOUNT:     return handlePickBankAccount(player, input);
                case FINAL_CONFIRM:         return handleFinalConfirm(player, input);
                default:                    return true;
            }
        }

        private boolean handleAskTutorial(Player player, String input) {
            if (input.equalsIgnoreCase("yes")) {
                stage = Stage.TUTORIAL;
                tutorialPage = 0;
                sendTutorialPage(player);
                return false;
            }
            if (input.equalsIgnoreCase("skip")) {
                promptForToken(player);
                return false;
            }
            player.sendMessage(NapTienPlugin.f("§c[PayBot] §fGõ §ayes §fhoặc §bskip §f(hoặc §ecancel §fđể huỷ):"));
            return false;
        }

        private void sendTutorialPage(Player player) {
            for (String line : TUTORIAL_PAGES[tutorialPage]) player.sendMessage(line);
            if (tutorialPage < TUTORIAL_PAGES.length - 1) {
                player.sendMessage("§7§o→ Gõ §fnext §7§ođể xem tiếp (§ecancel §7§ođể huỷ):");
            } else {
                promptForToken(player);
            }
        }

        private boolean handleTutorial(Player player, String input) {
            if (input.equalsIgnoreCase("next")) {
                tutorialPage++;
                sendTutorialPage(player);
                return false;
            }
            player.sendMessage(NapTienPlugin.f("§c[PayBot] §fGõ §fnext §fđể xem tiếp (hoặc §ecancel §fđể huỷ):"));
            return false;
        }

        private void promptForToken(Player player) {
            stage = Stage.ASK_TOKEN;
            player.sendMessage("§6[PayBot] §eDán §bAPI Token SePay §evào chat:");
            player.sendMessage("§7§o(cancel để huỷ)");
        }

        private boolean handleAskToken(Player player, String input) {
            if (input.isEmpty()) {
                player.sendMessage(NapTienPlugin.f("§c[PayBot] §fToken không được để trống! Nhập lại:"));
                return false;
            }
            String token = input;
            plugin.getConfig().set("sepay-api.api-token", token);
            // Lưu tạm (chưa enable) để SePayApiClient đọc được token vừa nhập khi gọi thử.
            busy = true;
            player.sendMessage(NapTienPlugin.f("§e[PayBot] §fĐang kiểm tra token với SePay..."));
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                String tokenError = plugin.getSePayApiClient().testToken();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    busy = false;
                    if (!tokenError.isEmpty()) {
                        player.sendMessage(NapTienPlugin.f("§c[PayBot] §fToken lỗi: §7" + tokenError));
                        player.sendMessage("§7Dán lại token khác, hoặc gõ §ecancel §7để huỷ:");
                        return;
                    }
                    player.sendMessage(NapTienPlugin.f("§a✓ §fToken hợp lệ! Đang lấy thông tin tài khoản ngân hàng..."));
                    fetchBankAccounts(player);
                });
            });
            return false;
        }

        private void fetchBankAccounts(Player player) {
            busy = true;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                List<SePayApiClient.BankAccountInfo> accounts = plugin.getSePayApiClient().listBankAccounts();
                accounts.removeIf(a -> !a.active);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    busy = false;
                    if (accounts.isEmpty()) {
                        stage = Stage.BANK_NOT_LINKED_CONFIRM;
                        player.sendMessage(NapTienPlugin.f("§c[PayBot] §fServer CHƯA liên kết tài khoản ngân hàng nào trên SePay!"));
                        player.sendMessage("§7Vào trang sau để thêm tài khoản ngân hàng (chọn ngân hàng bạn dùng nhận tiền):");
                        player.sendMessage("§b  https://docs.sepay.vn/them-tai-khoan-ngan-hang.html");
                        player.sendMessage("§eSau khi đã liên kết, gõ §aconfirm §eđể mình kiểm tra lại (hoặc §ccancel §eđể huỷ):");
                        return;
                    }
                    foundAccounts = accounts;
                    if (accounts.size() == 1) {
                        chosenAccount = accounts.get(0);
                        showFinalSummary(player);
                    } else {
                        stage = Stage.PICK_BANK_ACCOUNT;
                        player.sendMessage(NapTienPlugin.f("§e[PayBot] §fTìm thấy §a" + accounts.size() + " §ftài khoản ngân hàng — chọn 1 (gõ số):"));
                        for (int i = 0; i < accounts.size(); i++) {
                            SePayApiClient.BankAccountInfo a = accounts.get(i);
                            player.sendMessage("§b  " + (i + 1) + ". §f" + a.bankShortName + " - " + a.accountNumber + " (" + a.accountHolderName + ")");
                        }
                    }
                });
            });
        }

        private boolean handleBankNotLinkedConfirm(Player player, String input) {
            if (input.equalsIgnoreCase("confirm")) {
                player.sendMessage(NapTienPlugin.f("§e[PayBot] §fĐang kiểm tra lại..."));
                fetchBankAccounts(player);
                return false;
            }
            player.sendMessage(NapTienPlugin.f("§c[PayBot] §fGõ §aconfirm §fsau khi đã liên kết (hoặc §ccancel §fđể huỷ):"));
            return false;
        }

        private boolean handlePickBankAccount(Player player, String input) {
            try {
                int idx = Integer.parseInt(input.trim()) - 1;
                if (idx < 0 || idx >= foundAccounts.size()) {
                    player.sendMessage(NapTienPlugin.f("§c[PayBot] §fSố không hợp lệ! Nhập lại:"));
                    return false;
                }
                chosenAccount = foundAccounts.get(idx);
                showFinalSummary(player);
            } catch (NumberFormatException e) {
                player.sendMessage(NapTienPlugin.f("§c[PayBot] §fVui lòng nhập SỐ thứ tự! Nhập lại:"));
            }
            return false;
        }

        private void showFinalSummary(Player player) {
            stage = Stage.FINAL_CONFIRM;
            // "Chỉ gửi riêng tới admin đang config" — sendMessage trong Minecraft vốn đã CHỈ
            // hiển thị cho đúng player đang xem chat của họ, không ai khác thấy được dòng này.
            player.sendMessage("§6§l══════ Thông tin tài khoản (chỉ bạn thấy) ══════");
            player.sendMessage("§7Tên ngân hàng : §f" + chosenAccount.bankFullName + " (" + chosenAccount.bankShortName + ")");
            player.sendMessage("§7Số tài khoản  : §f" + chosenAccount.accountNumber);
            player.sendMessage("§7Tên chủ TK    : §f" + chosenAccount.accountHolderName);
            player.sendMessage("§6§l═══════════════════════════════════════════════");
            player.sendMessage("§eLưu cấu hình này? §a(yes) §fhoặc §c(cancel)");
        }

        private boolean handleFinalConfirm(Player player, String input) {
            if (!input.equalsIgnoreCase("yes")) {
                player.sendMessage(NapTienPlugin.f("§c[PayBot] §fGõ §ayes §fđể lưu, hoặc §ccancel §fđể huỷ:"));
                return false;
            }
            plugin.getConfig().set("sepay-api.enabled", true);
            plugin.getConfig().set("sepay-api.bank-account-number", chosenAccount.accountNumber);
            plugin.getConfig().set("sepay-api.bank-short-name", chosenAccount.bankShortName);
            plugin.getConfig().set("sepay-api.bank-full-name", chosenAccount.bankFullName);
            plugin.getConfig().set("sepay-api.bank-bin", chosenAccount.bankBin);
            plugin.getConfig().set("sepay-api.account-holder-name", chosenAccount.accountHolderName);
            // Mirror sang field cũ "sepay.*" để NapBankCommand (sinh QR) đọc được luôn,
            // không cần sửa thêm code QR generation — giữ tương thích ngược trong-plugin.
            plugin.getConfig().set("sepay.configured", true);
            plugin.getConfig().set("sepay.bank-account", chosenAccount.accountNumber);
            plugin.getConfig().set("sepay.bank-name", chosenAccount.bankShortName);
            plugin.getConfig().set("sepay.account-name", chosenAccount.accountHolderName);
            plugin.forceConfigRefresh("cấu hình SePay");

            player.sendMessage(NapTienPlugin.f("§a§l[PayBot] §r§a✓ Cấu hình SePay hoàn tất!"));
            player.sendMessage("§7Plugin sẽ tự kiểm tra giao dịch mới mỗi "
                    + plugin.getConfig().getInt("sepay-api.poll-interval-seconds", 10) + " giây.");
            player.sendMessage("§7Không cần webhook, không cần bot Discord — §a/napbank §7đã sẵn sàng dùng ngay!");
            plugin.getSetupManager().onSePayDone(player);
            return true;
        }
    }
}
