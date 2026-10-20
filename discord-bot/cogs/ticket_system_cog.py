"""
TicketSystemCog — Quản lý hệ thống Ticket khiếu nại và hỗ trợ nạp tiền trên Discord.
Tuân thủ Rule 17 (Class chuyên trách duy nhất).
"""

import discord
from discord import app_commands
from discord.ext import commands
import logging

log = logging.getLogger(__name__)


class TicketCloseButton(discord.ui.View):
    """Nút bấm đóng ticket hỗ trợ."""

    def __init__(self, ticket_repo):
        super().__init__(timeout=None)
        self.ticket_repo = ticket_repo

    @discord.ui.button(label="🔒 Đóng Ticket", style=discord.ButtonStyle.danger)
    async def close_ticket_button(self, interaction: discord.Interaction, button: discord.ui.Button):
        self.ticket_repo.close_ticket(str(interaction.channel_id))
        await interaction.response.send_message("🔒 Ticket này đã được đóng và sẽ bị xóa sau 5 giây...")
        await discord.utils.sleep_until(discord.utils.utcnow() + discord.utils.timedelta(seconds=5))
        try:
            await interaction.channel.delete()
        except Exception as e:
            log.error(f"[TicketCloseButton] Lỗi xoá channel ticket: {e}")


class TicketSystemCog(commands.Cog):
    """Cog xử lý tạo và quản lý Ticket hỗ trợ."""

    def __init__(self, bot, ticket_repo):
        self.bot = bot
        self.ticket_repo = ticket_repo

    @app_commands.command(name="createticket", description="Tạo một kênh ticket hỗ trợ riêng tư về giao dịch nạp tiền")
    @app_commands.describe(player_name="Tên trong Minecraft của bạn")
    async def create_ticket_command(self, interaction: discord.Interaction, player_name: str):
        guild = interaction.guild
        if not guild:
            await interaction.response.send_message("Lệnh này chỉ dùng được trong máy chủ.", ephemeral=True)
            return

        # Tạo text channel riêng tư
        overwrites = {
            guild.default_role: discord.PermissionOverwrite(read_messages=False),
            interaction.user: discord.PermissionOverwrite(read_messages=True, send_messages=True),
            guild.me: discord.PermissionOverwrite(read_messages=True, send_messages=True)
        }

        channel_name = f"ticket-{player_name.lower()[:10]}"
        try:
            channel = await guild.create_text_channel(name=channel_name, overwrites=overwrites)
            self.ticket_repo.create_ticket(str(guild.id), str(channel.id), str(interaction.user.id), player_name)

            embed = discord.Embed(
                title=f"🎫 TICKET HỖ TRỢ NẠP TIỀN • {player_name}",
                description=(
                    f"Xin chào {interaction.user.mention}!\n\n"
                    "Vui lòng gửi bill chuyển khoản hoặc thông tin mã thẻ, thời gian nạp tại đây.\n"
                    "Quản trị viên sẽ kiểm tra và giải quyết cho bạn sớm nhất có thể."
                ),
                color=discord.Color.teal()
            )
            view = TicketCloseButton(self.ticket_repo)
            await channel.send(embed=embed, view=view)

            await interaction.response.send_message(f"✅ Đã tạo ticket tại {channel.mention}", ephemeral=True)
        except Exception as e:
            log.error(f"[TicketSystemCog] Lỗi tạo ticket: {e}")
            await interaction.response.send_message(f"❌ Không thể tạo channel ticket: {e}", ephemeral=True)
