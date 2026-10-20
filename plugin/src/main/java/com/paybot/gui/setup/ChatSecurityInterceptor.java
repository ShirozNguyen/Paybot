package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * ChatSecurityInterceptor — v6.1.0 (Part 160)
 * TUÂN THỦ RULE 17: Bắt chặn sự kiện Chat để bảo mật thông tin cài đặt (Token, Password, STK).
 * <p>
 * NGUYÊN TẮC BẢO MẬT:
 * 1. Khi Admin đang nhập dữ liệu setup qua chat: HỦY SỰ KIỆN CHAT NGAY LẬP TỨC.
 * 2. Tuyệt đối không để tin nhắn phát tán ra toàn server.
 * 3. Tuyệt đối không log nội dung nhạy cảm ra console.
 * 4. Mọi phản hồi, thông báo hướng dẫn CHỈ gửi riêng tới Admin đang thao tác (player.sendMessage).
 */
public class ChatSecurityInterceptor implements Listener {

    private final PayBotPlugin plugin;
    private final ChatInputPromptManager promptManager;

    public ChatSecurityInterceptor(PayBotPlugin plugin, ChatInputPromptManager promptManager) {
        this.plugin = plugin;
        this.promptManager = promptManager;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        if (promptManager.isAwaitingInput(player.getUniqueId())) {
            // Hủy sự kiện phát tán chat toàn server
            event.setCancelled(true);
            String message = event.getMessage();

            // Chuyển cho PromptManager xử lý trên Main Thread (hoặc Folia player thread)
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                promptManager.handleInput(player, message);
            });
        }
    }
}
