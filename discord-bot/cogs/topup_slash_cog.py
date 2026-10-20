"""
TopupSlashCog — Lệnh /napbank và /napthe cùng các Modal nhập liệu cho người chơi.
Tuân thủ Rule 17 (Class chuyên trách duy nhất).
"""

import discord
from discord import app_commands
from discord.ext import commands
import logging
from uuid import uuid4

log = logging.getLogger(__name__)


class NapTheModal(discord.ui.Modal, title="Nạp Thẻ Cào"):
    """Modal điền mã PIN và số Seri thẻ cào."""
    player_name = discord.ui.TextInput(label="Tên nhân vật Minecraft", placeholder="VD: Steve, Alex...", required=True)
    pin = discord.ui.TextInput(label="Mã thẻ (PIN)", placeholder="Nhập mã cào dưới lớp tráng bạc", required=True)
    serial = discord.ui.TextInput(label="Số Seri", placeholder="Nhập số seri in trên thẻ", required=True)

    def __init__(self, telco: str, amount: int, card_service, tx_repo):
        super().__init__()
        self.telco = telco
        self.amount = amount
        self.card_service = card_service
        self.tx_repo = tx_repo

    async def on_submit(self, interaction: discord.Interaction):
        await interaction.response.defer(ephemeral=True)
        request_id = str(uuid4())[:16]

        # Lưu giao dịch vào SQLite
        tx_id = self.tx_repo.create_transaction(
            guild_id=str(interaction.guild_id or 0),
            player_name=self.player_name.value.strip(),
            tx_type="CARD",
            amount=self.amount,
            code=request_id,
            extra={"telco": self.telco, "serial": self.serial.value.strip()}
        )

        res = await self.card_service.submit_card(
            telco=self.telco,
            pin=self.pin.value.strip(),
            serial=self.serial.value.strip(),
            amount=self.amount,
            request_id=request_id
        )

        status = res.get("status", "")
        if status in [99, "99", "pending", "success"]:
            await interaction.followup.send(
                f"✅ Đã gửi thẻ **{self.telco}** mệnh giá **{self.amount:,.0f} VNĐ** thành công!\n"
                f"🔖 Mã tra cứu: `{request_id}`. Hệ thống sẽ duyệt tự động trong 30-60 giây.",
                ephemeral=True
            )
        else:
            msg = res.get("message", "Thẻ không hợp lệ hoặc lỗi cổng gạch.")
            self.tx_repo.mark_failed(tx_id, reason=msg)
            await interaction.followup.send(f"❌ Nạp thẻ thất bại: {msg}", ephemeral=True)


class TopupSlashCog(commands.Cog):
    """Cog xử lý lệnh /napbank và /napthe."""

    def __init__(self, bot, config_manager, sepay_service, card_service, tx_repo):
        self.bot = bot
        self.config_manager = config_manager
        self.sepay_service = sepay_service
        self.card_service = card_service
        self.tx_repo = tx_repo

    @app_commands.command(name="napbank", description="Tạo mã QR chuyển khoản ngân hàng tự động (VietQR SePay)")
    @app_commands.describe(player_name="Tên trong Minecraft của bạn", amount="Số tiền muốn nạp (VNĐ)")
    async def napbank_command(self, interaction: discord.Interaction, player_name: str, amount: int):
        """Lệnh nạp tiền qua ngân hàng tạo QR động."""
        if amount < 1000:
            await interaction.response.send_message("❌ Số tiền tối thiểu mỗi lần nạp là 1.000 VNĐ.", ephemeral=True)
            return

        bank_name = self.config_manager.get("sepay", "bank_name", "")
        acc_num = self.config_manager.get("sepay", "account_number", "")
        if not bank_name or not acc_num:
            await interaction.response.send_message("❌ Máy chủ chưa cấu hình thông tin ngân hàng SePay.", ephemeral=True)
            return

        memo_code = f"PB {player_name.strip()}"
        qr_url = self.sepay_service.generate_vietqr_url(bank_name, acc_num, amount, memo_code)

        # Lưu đơn nạp
        self.tx_repo.create_transaction(
            guild_id=str(interaction.guild_id or 0),
            player_name=player_name.strip(),
            tx_type="BANK",
            amount=amount,
            code=memo_code
        )

        embed = discord.Embed(
            title="💳 MÃ QR NẠP TIỀN NGÂN HÀNG (VIETQR)",
            description=f"Quét mã bên dưới để thanh toán tự động cho người chơi **{player_name}**.",
            color=discord.Color.blue()
        )
        embed.add_field(name="Ngân hàng", value=f"`{bank_name}`", inline=True)
        embed.add_field(name="Số tài khoản", value=f"`{acc_num}`", inline=True)
        embed.add_field(name="Số tiền", value=f"**{amount:,.0f} VNĐ**", inline=True)
        embed.add_field(name="Nội dung chuyển khoản (Bắt buộc)", value=f"`{memo_code}`", inline=False)
        embed.set_image(url=qr_url)
        embed.set_footer(text="Hệ thống duyệt tự động trong vòng 5-10 giây sau khi chuyển tiền")

        await interaction.response.send_message(embed=embed, ephemeral=True)

    @app_commands.command(name="napthe", description="Nạp thẻ cào điện thoại (Viettel, Mobifone, Vinaphone)")
    @app_commands.describe(telco="Loại thẻ cào", amount="Mệnh giá thẻ (VNĐ)")
    @app_commands.choices(telco=[
        app_commands.Choice(name="Viettel", value="VIETTEL"),
        app_commands.Choice(name="Mobifone", value="MOBIFONE"),
        app_commands.Choice(name="Vinaphone", value="VINAPHONE"),
        app_commands.Choice(name="Zing", value="ZING")
    ])
    async def napthe_command(self, interaction: discord.Interaction, telco: app_commands.Choice[str], amount: int):
        """Mở Modal nạp thẻ cào điện thoại."""
        modal = NapTheModal(telco.value, amount, self.card_service, self.tx_repo)
        await interaction.response.send_modal(modal)
