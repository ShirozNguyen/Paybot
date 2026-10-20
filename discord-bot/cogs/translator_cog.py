"""
TranslatorCog — Tính năng dịch nhanh tin nhắn đa ngôn ngữ trên Discord.
Tuân thủ Rule 17 (Class chuyên trách duy nhất).
"""

import discord
from discord import app_commands
from discord.ext import commands
import logging
from urllib.parse import quote
import httpx

log = logging.getLogger(__name__)


class TranslatorCog(commands.Cog):
    """Cog xử lý dịch ngôn ngữ nhanh hỗ trợ người chơi quốc tế."""

    def __init__(self, bot):
        self.bot = bot

    @app_commands.command(name="translate", description="Dịch tin nhắn nhanh sang tiếng Việt hoặc tiếng Anh")
    @app_commands.describe(text="Nội dung cần dịch", to_lang="Ngôn ngữ đích (vi hoặc en)")
    async def translate_command(self, interaction: discord.Interaction, text: str, to_lang: str = "vi"):
        await interaction.response.defer(ephemeral=True)
        url = f"https://api.mymemory.translated.net/get?q={quote(text)}&langpair=autodetect|{to_lang}"

        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                resp = await client.get(url)
                if resp.status_code == 200:
                    data = resp.json()
                    translated = data.get("responseData", {}).get("translatedText", text)
                    embed = discord.Embed(
                        title="🌐 KẾT QUẢ DỊCH NGÔN NGỮ",
                        color=discord.Color.purple()
                    )
                    embed.add_field(name="Gốc", value=f"```{text}```", inline=False)
                    embed.add_field(name="Bản dịch", value=f"```{translated}```", inline=False)
                    await interaction.followup.send(embed=embed, ephemeral=True)
                    return
        except Exception as e:
            log.error(f"[TranslatorCog] Lỗi dịch: {e}")

        await interaction.followup.send(f"❌ Không thể dịch văn bản lúc này.", ephemeral=True)
