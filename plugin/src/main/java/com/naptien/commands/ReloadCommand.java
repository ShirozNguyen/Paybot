package com.naptien.commands;

import com.naptien.NapTienPlugin;
import com.naptien.managers.TransferContentGenerator;
import com.naptien.managers.SmartConfigMerger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.io.File;

/**
 * ReloadCommand — Phụ trách riêng độc lập lệnh /paybot reload (hoặc /naptien reload, /paybotreload).
 * Thực hiện reload file config.yml từ đĩa và TỰ ĐỘNG ÁP DỤNG (APPLY) ngay lập tức tới tất cả Manager trong Runtime.
 *
 * Rule 17: Mãi là 1 class độc lập hoàn toàn, không gộp nhiều chức năng vào 1 class.
 *
 * Changelog:
 *   v5.4.6 — Thêm mới lệnh reload độc lập và apply tự động.
 */
public final class ReloadCommand implements CommandExecutor {

    private final NapTienPlugin plugin;

    public ReloadCommand(final NapTienPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (!sender.hasPermission("naptien.admin")) {
            sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fBạn không có quyền thực hiện lệnh này (cần §enaptien.admin§f)."));
            return true;
        }

        sender.sendMessage(NapTienPlugin.f("§e[PayBot] §fĐang tiến hành nạp lại và áp dụng (apply) cấu hình..."));

        try {
            // 1. Tự đồng bộ template comment nếu có nâng cấp
            final File configFile = new File(plugin.getDataFolder(), "config.yml");
            SmartConfigMerger.sync(plugin, configFile);

            // 2. Reload file config.yml từ đĩa vào Memory
            plugin.reloadConfig();

            // 3. Validate transfer-content config
            TransferContentGenerator.validateConfig(plugin);

            // 4. Áp dụng (Apply) tới tất cả Runtime Managers
            plugin.applyReloadedConfig();

            sender.sendMessage(NapTienPlugin.f("§a[PayBot] §f✓ Đã nạp lại và áp dụng §a(apply) §ftoàn bộ cấu hình mới từ config.yml thành công!"));
            plugin.getLogger().info("[PayBot] Admin " + sender.getName() + " đã thực hiện /paybot reload thành công.");
        } catch (final Exception e) {
            sender.sendMessage(NapTienPlugin.f("§c[PayBot] §fLỗi khi reload cấu hình: " + e.getMessage()));
            plugin.getLogger().severe("[PayBot] Lỗi khi reload config: " + e.getMessage());
            e.printStackTrace();
        }
        return true;
    }
}
