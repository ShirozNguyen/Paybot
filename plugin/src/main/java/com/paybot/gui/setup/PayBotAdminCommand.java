package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * PayBotAdminCommand — v7.0.0 (Part 160)
 * TUÂN THỦ RULE 17: Command Executor duy nhất cho lệnh /paybot (setup & reload).
 * <p>
 * THAY THẾ TOÀN BỘ 9 LỆNH PHÂN MẢNH CŨ.
 */
public class PayBotAdminCommand implements CommandExecutor, TabCompleter {

    private final PayBotPlugin plugin;
    private final SetupGuiManager guiManager;

    public PayBotAdminCommand(PayBotPlugin plugin, SetupGuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("naptien.admin")) {
            sender.sendMessage("§c[PayBot] Bạn không có quyền sử dụng lệnh này (cần quyền: naptien.admin).");
            return true;
        }

        if (args.length == 0 || "setup".equalsIgnoreCase(args[0]) || "menu".equalsIgnoreCase(args[0]) || "gui".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§c[PayBot] Console không thể mở GUI Container. Vui lòng vào game hoặc dùng lệnh: /paybot reload");
                return true;
            }
            Player player = (Player) sender;
            guiManager.openMenu(player, SetupMenuType.MAIN_HUB);
            return true;
        }

        if ("reload".equalsIgnoreCase(args[0])) {
            plugin.applyReloadedConfig();
            sender.sendMessage("§a[PayBot] §f✓ Đã nạp lại và áp dụng (apply) toàn bộ cấu hình mới thành công!");
            return true;
        }

        sender.sendMessage("§e[PayBot] §fCú pháp lệnh quản trị: §b/paybot [setup|reload]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("naptien.admin")) return Collections.emptyList();
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            for (String sub : Arrays.asList("setup", "reload")) {
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
            return completions;
        }
        return Collections.emptyList();
    }
}
