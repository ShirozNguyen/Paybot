"""
AdminSetupCog — Lệnh quản trị Bot Discord và Đăng ký đối tác SePay Affiliate Funnel.
Tuân thủ Rule 17 (Class chuyên trách duy nhất).
Tích hợp Link Đối Tác SePay chính thức của tác giả: https://my.sepay.vn/register?gcid=vrewlwez
"""

import discord
from discord import app_commands
from discord.ext import commands
import logging

log = logging.getLogger(__name__)


class SePayPartnerView(discord.ui.View):
    """View chứa nút bấm mở link đăng ký đối tác SePay trực tiếp."""

    def __init__(self, partner_url: str):
        super().__init__(timeout=None)
        self.add_item(discord.ui.Button(
            label="🔗 Đăng Ký Tài Khoản SePay Đối Tác",
            url=partner_url,
            style=discord.ButtonStyle.link,
            emoji="💎"
        ))


class AdminSetupCog(commands.Cog):
    """Cog xử lý các lệnh quản trị: /sepay, /status, /setchannel."""

    def __init__(self, bot, config_manager, sepay_service, guild_repo):
        self.bot = bot
        self.config_manager = config_manager
        self.sepay_service = sepay_service
        self.guild_repo = guild_repo

    @app_commands.command(name="sepay", description="Xem trạng thái SePay và đăng ký tài khoản Đối tác nhận ưu đãi")
    async def sepay_command(self, interaction: discord.Interaction):
        """Lệnh xem thông tin kết nối SePay và liên kết đăng ký đối tác chính thức."""
        partner_url = self.config_manager.sepay_referral_url

        embed = discord.Embed(
            title="💎 CỔNG THANH TOÁN SEPAY & CHƯƠNG TRÌNH ĐỐI TÁC",
            description=(
                "SePay là cổng tự động hóa thanh toán Ngân hàng và VietQR hàng đầu cho máy chủ game.\n\n"
                "✨ **Đặc quyền khi đăng ký qua Link Đối Tác PayBot:**\n"
                "• Tích hợp trực tiếp 100% tự động vào Minecraft Server & Bot Discord.\n"
                "• Miễn phí khởi tạo, không cần thủ tục kinh doanh phức tạp.\n"
                "• Hỗ trợ toàn diện từ đội ngũ phát triển PayBot Ecosystem."
            ),
            color=discord.Color.gold()
        )

        has_token = bool(self.config_manager.get("sepay", "api_token", "").strip())
        status_text = "🟢 Đã kết nối API" if has_token else "🔴 Chưa cấu hình API Token"
        embed.add_field(name="Trạng thái SePay Bot", value=status_text, inline=True)
        embed.add_field(name="Phiên bản", value="PayBot v7.0.0", inline=True)
        embed.set_footer(text="Nhấn nút bên dưới để đăng ký tài khoản SePay nhanh chóng")

        view = SePayPartnerView(partner_url)
        await interaction.response.send_message(embed=embed, view=view, ephemeral=True)

    @app_commands.command(name="setnotifychannel", description="Cài đặt kênh thông báo giao dịch nạp tiền thành công")
    @app_commands.default_permissions(administrator=True)
    async def set_notify_channel_command(self, interaction: discord.Interaction, channel: discord.TextChannel):
        """Đặt kênh nhận thông báo bill nạp tiền."""
        self.config_manager.set("discord", "notify_channel_id", str(channel.id))
        self.config_manager.save()
        if interaction.guild_id:
            self.guild_repo.upsert_notify_channel(str(interaction.guild_id), str(channel.id))

        await interaction.response.send_message(
            f"✅ Đã đặt kênh thông báo nạp tiền thành công tại: {channel.mention}",
            ephemeral=True
        )

    @app_commands.command(name="status", description="Kiểm tra trạng thái hệ sinh thái PayBot Companion")
    async def status_command(self, interaction: discord.Interaction):
        """Kiểm tra tình trạng hoạt động của Bot và kết nối nội bộ."""
        embed = discord.Embed(
            title="📊 TRẠNG THÁI HỆ THỐNG PAYBOT COMPANION",
            color=discord.Color.blue()
        )
        embed.add_field(name="Bot Status", value="🟢 Hoạt động", inline=True)
        embed.add_field(name="IPC Server", value=f"`127.0.0.1:{self.config_manager.ipc_port}`", inline=True)
        embed.add_field(name="Two-Way Sync", value="🟢 Bật", inline=True)
        embed.set_footer(text="PayBot v7.0.0 • Local Companion Engine")
        await interaction.response.send_message(embed=embed, ephemeral=True)
