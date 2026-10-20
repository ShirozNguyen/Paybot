"""
RewardNotificationService — Tạo và gửi tin nhắn Embed thông báo nạp thành công vào kênh Discord.
Tuân thủ Rule 17 (Class chuyên trách duy nhất).
"""

import discord
import logging
from typing import Optional

log = logging.getLogger(__name__)


class RewardNotificationService:
    """Class phụ trách tạo và phát sóng Embed thông báo giao dịch vào Discord Channel."""

    def __init__(self, bot_client, config_manager):
        self.bot_client = bot_client
        self.config_manager = config_manager

    @staticmethod
    def format_vnd(amount: int) -> str:
        return f"{amount:,.0f} VNĐ".replace(",", ".")

    async def send_payment_notification(self, player_name: str, tx_type: str, amount: int,
                                        code: str, channel_id: Optional[str] = None) -> bool:
        """Gửi tin nhắn Embed chúc mừng và xác nhận nạp tiền."""
        target_channel_id = channel_id or self.config_manager.get("discord", "notify_channel_id", "")
        if not target_channel_id:
            log.warning("[RewardNotificationService] Chưa cấu hình kênh thông báo Discord.")
            return False

        try:
            channel = self.bot_client.get_channel(int(target_channel_id))
            if not channel:
                channel = await self.bot_client.fetch_channel(int(target_channel_id))

            embed = discord.Embed(
                title="🎉 THANH TOÁN THÀNH CÔNG!",
                description=f"Hệ thống đã ghi nhận giao dịch nạp tiền của người chơi **{player_name}**.",
                color=discord.Color.green()
            )
            embed.add_field(name="👤 Người chơi", value=f"`{player_name}`", inline=True)
            embed.add_field(name="💰 Số tiền", value=f"**{self.format_vnd(amount)}**", inline=True)
            embed.add_field(name="💳 Hình thức", value=f"**{tx_type.upper()}**", inline=True)
            embed.add_field(name="🔖 Mã giao dịch", value=f"`{code}`", inline=False)
            embed.set_footer(text="PayBot Ecosystem • Tự động xử lý 100%")

            await channel.send(embed=embed)
            log.info(f"[RewardNotificationService] Đã gửi thông báo nạp {amount} VNĐ của {player_name} vào kênh {target_channel_id}")
            return True
        except Exception as e:
            log.error(f"[RewardNotificationService] Lỗi gửi thông báo Discord: {e}")
            return False
