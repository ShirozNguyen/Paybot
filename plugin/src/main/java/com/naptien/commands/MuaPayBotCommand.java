package com.naptien.commands;

import com.naptien.utils.TextHelper;
import org.bukkit.command.*;

/**
 * /muapaybot — Hiển thị thông tin mua PayBot.
 *
 * 1.16+ Paper : link clickable (Adventure, qua TextHelper → ModernTextHelper).
 * 1.12–1.15   : link plain text (qua TextHelper → LegacyTextHelper).
 */
public class MuaPayBotCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("§cYou Don't Have Permission To Use This!");
            return true;
        }
        TextHelper.sendMuaPayBotInfo(sender);
        return true;
    }
}
