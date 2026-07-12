package com.naptien.commands;

import com.naptien.NapTienPlugin;
import com.naptien.managers.SetupManager;
import com.naptien.managers.StandaloneCardProcessor;
import com.naptien.utils.TextHelper;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class CardSetupCommand implements CommandExecutor {

    private final NapTienPlugin plugin;

    public CardSetupCommand(NapTienPlugin plugin) { this.plugin = plugin; }

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
            player.sendMessage(NapTienPlugin.f("§c[PayBot] §fBạn đang trong phiên setup khác, gõ §e/cancelpaysetup §fđể huỷ."));
            return true;
        }

        showCurrentConfig(player);
        player.sendMessage("§6§l══════════ §eCấu hình API Gạch Thẻ §6§l══════════");
        player.sendMessage("§7Chọn trang API hoặc gõ tên:");
        sendSiteButtons(player);
        plugin.getSetupManager().startSession(player, new CardSession());
        return true;
    }

    private void showCurrentConfig(Player player) {
        String site = plugin.getConfig().getString("card-api.site", "");
        String pid  = plugin.getConfig().getString("card-api.partner-id", "");
        if (!site.isEmpty()) {
            String channel = plugin.getConfig().getString("card-api-sites." + site + ".channel-id", "");
            player.sendMessage("§7Cấu hình Card API hiện tại: §a✓");
            player.sendMessage("§7  Site      : §f" + site);
            player.sendMessage("§7  Partner ID: §f" + pid);
            player.sendMessage("§7  Channel ID: §f" + (channel.isEmpty() ? "§8(chưa đặt)" : channel));
            player.sendMessage("§7Nhập thông tin mới để cập nhật:");
        }
    }

    private void sendSiteButtons(Player player) {
        List<String> sites = new ArrayList<>(StandaloneCardProcessor.getSupportedSites().keySet());
        TextHelper.sendSiteButtons(player, sites);
    }

    private class CardSession implements SetupManager.SetupSession {
        // v5.0.2: gộp thêm bước CHANNEL_ID — trước đây phải dùng riêng /cardapisetup
        // (lệnh đó không làm gì khác hơn /cardsetup, CHỈ khác đúng bước hỏi channel-id
        // này) để hỏi channel Discord nhận thông báo. Giờ /cardapisetup đã bị xoá, gộp
        // hẳn bước này vào cuối flow /cardsetup luôn cho gọn — không còn 2 lệnh làm
        // cùng 1 việc với UX khác nhau (1 cái GUI-click, 1 cái chat-only) gây nhầm lẫn.
        private enum Step { SITE, PARTNER_ID, PARTNER_KEY, CHANNEL_ID }
        private Step step = Step.SITE;

        @Override
        public boolean handleInput(Player player, String input) {
            if (input.isEmpty()) {
                player.sendMessage(NapTienPlugin.f("§c[PayBot] §fGiá trị không được để trống! Nhập lại:"));
                return false;
            }
            switch (step) {
                case SITE: {
                    String normalized = input.toLowerCase().trim();
                    if (!StandaloneCardProcessor.getSupportedSites().containsKey(normalized)) {
                        player.sendMessage(NapTienPlugin.f("§c[PayBot] §fTrang không hợp lệ! Hợp lệ: "
                                + String.join(", ", StandaloneCardProcessor.getSupportedSites().keySet())));
                        return false;
                    }
                    plugin.getConfig().set("card-api.site", normalized);
                    step = Step.PARTNER_ID;
                    player.sendMessage("§a✓ Site: §f" + normalized);
                    player.sendMessage("§e[2/4] §fNhập §bPartner ID §f(lấy từ trang " + normalized + "):");
                    return false;
                }
                case PARTNER_ID: {
                    plugin.getConfig().set("card-api.partner-id", input);
                    step = Step.PARTNER_KEY;
                    player.sendMessage("§a✓ Đã lưu Partner ID!");
                    player.sendMessage("§e[3/4] §fNhập §bPartner Key:");
                    return false;
                }
                case PARTNER_KEY: {
                    String site = plugin.getConfig().getString("card-api.site", "");
                    String pid  = plugin.getConfig().getString("card-api.partner-id", "");
                    // BUG FIX v4.0.8: Lưu vào card-api-sites.{site} VÀ đặt configured = true
                    // Trước đây chỉ lưu card-api.partner-key → không set configured → vẫn báo "chưa cấu hình"
                    plugin.getConfig().set("card-api.partner-key",               input);
                    plugin.getConfig().set("card-api-sites." + site + ".partner-id",  pid);
                    plugin.getConfig().set("card-api-sites." + site + ".partner-key", input);
                    plugin.getConfig().set("card-api.configured",  !pid.isEmpty() && !input.isEmpty());
                    plugin.forceConfigRefresh("cấu hình web đổi thẻ");
                    step = Step.CHANNEL_ID;
                    player.sendMessage("§a✓ Đã lưu Partner Key!");
                    player.sendMessage("§e[4/4] §fNhập §bID kênh Discord §fđể nhận thông báo:");
                    player.sendMessage("§7§o(Chỉ có tác dụng khi đã connect bot — gõ §cskip §7§ohoặc §ccancel §7§ođể bỏ qua)");
                    return false;
                }
                case CHANNEL_ID: {
                    String site    = plugin.getConfig().getString("card-api.site", "");
                    String channel = input.equalsIgnoreCase("skip") ? "" : input;
                    if (!channel.isEmpty()) {
                        plugin.getConfig().set("card-api-sites." + site + ".channel-id", channel);
                        plugin.forceConfigRefresh("cấu hình web đổi thẻ (channel-id)");
                        if (!plugin.isStandaloneMode()) {
                            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                                    plugin.getBotHttpClient().updateNotificationChannel(channel));
                        }
                    }
                    showSummary(player, channel);
                    plugin.getSetupManager().onCardDone(player);
                    return true;
                }
            }
            return false;
        }

        private void showSummary(Player player, String channel) {
            player.sendMessage("§6§l═══════════════════════════════════════");
            player.sendMessage("§a§l✓ Cấu hình Card API hoàn tất!");
            player.sendMessage("§7Site       : §f" + plugin.getConfig().getString("card-api.site", ""));
            player.sendMessage("§7Partner ID : §f" + plugin.getConfig().getString("card-api.partner-id", ""));
            player.sendMessage("§7Partner Key: §f" + plugin.getConfig().getString("card-api.partner-key", ""));
            player.sendMessage("§7Channel ID : §f" + (channel.isEmpty() ? "§8(bỏ qua)" : channel));
            player.sendMessage("§7Tiếp theo dùng §e/chinhsuamenhgianap §7để cấu hình lệnh thưởng theo mệnh giá.");
            player.sendMessage("§6§l═══════════════════════════════════════");
        }
    }
}