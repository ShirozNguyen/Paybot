package com.naptien.fabric.mixin;

import com.naptien.PayBotMod;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v5.5.5 [audit - BUG FIX]: Fabric 1.18.2 KHONG co ServerMessageEvents.ALLOW_CHAT_MESSAGE
 * (API do chi ra doi cung chat-signing tu 1.19). Dung Mixin inject vao
 * ServerGamePacketListenerImpl.handleChat(ServerboundChatPacket) - CHU Y: day la Mojang
 * mapping (project dung officialMojangMappings() xuyen suot), KHONG PHAI ten Yarn
 * (ServerPlayNetworkHandler/onChatMessage/ChatMessageC2SPacket) - xac nhan truc tiep qua
 * javadoc chinh thuc "forge 1.18.2-40.2.1" (nekoyue.github.io) va mappings.dev 1.16.1-1.16.5.
 * getMessage() (khong phai getChatMessage() - do la ten Yarn) tra ve String noi dung tho.
 *
 * CAN THAN: day la Mixin dau tien trong toan bo project (chua co tien le). Inject vao
 * HEAD, cancellable=true, chi cancel() khi thuc su xu ly duoc - neu co loi runtime
 * (vd sai ten do khac version), catch va KHONG cancel de tranh chan nham chat hop le.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow public ServerPlayer player;

    @Inject(method = "handleChat(Lnet/minecraft/network/protocol/game/ServerboundChatPacket;)V",
            at = @At("HEAD"), cancellable = true)
    private void paybot$handleChat(ServerboundChatPacket packet, CallbackInfo ci) {
        try {
            String text = packet.getMessage();
            ServerPlayer sender = this.player;
            if (com.naptien.gui.GuiSession.isAnyoneWaiting(sender.getUUID())
                    && com.naptien.gui.GuiChatHandler.handle(sender, text)) {
                ci.cancel();
                return;
            }
            PayBotMod mod = PayBotMod.getInstance();
            if (mod != null && mod.getSetupManager() != null
                    && mod.getSetupManager().isInSession(sender)
                    && mod.getSetupManager().handleChat(sender, text)) {
                ci.cancel();
            }
        } catch (Throwable t) {
            // An toan: neu co loi (vd sai ten method do khac version), KHONG cancel -
            // de chat hoat dong binh thuong thay vi lam gian doan trai nghiem nguoi choi.
            PayBotMod.LOGGER.warn("[PayBot] Mixin handleChat loi, bo qua an toan: " + t);
        }
    }
}
