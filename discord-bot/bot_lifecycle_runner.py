"""
BotLifecycleRunner — Điều phối vòng đời khởi động và chạy song song Bot Discord + FastAPI IPC Server.
Tuân thủ Rule 17 (Class chuyên trách duy nhất).
"""

import asyncio
import discord
from discord.ext import commands
import logging
import sys

from config.bot_config_manager import BotConfigManager
from config.secret_key_generator import SecretKeyGenerator
from database.database_connection_pool import DatabaseConnectionPool
from database.transaction_repository import TransactionRepository
from database.guild_settings_repository import GuildSettingsRepository
from database.ticket_repository import TicketRepository
from services.sepay_api_service import SePayApiService
from services.card_exchange_service import CardExchangeService
from services.config_sync_bridge_service import ConfigSyncBridgeService
from services.reward_notification_service import RewardNotificationService
from ipc.ipc_sync_endpoints import IpcSyncEndpoints
from ipc.local_ipc_server import LocalIpcServer
from cogs.admin_setup_cog import AdminSetupCog
from cogs.topup_slash_cog import TopupSlashCog
from cogs.ticket_system_cog import TicketSystemCog
from cogs.translator_cog import TranslatorCog

log = logging.getLogger(__name__)


class BotLifecycleRunner:
    """Class điều phối toàn bộ vòng đời khởi động của PayBot Companion."""

    def __init__(self, config_path: str = "config.json"):
        # 1. Cấu hình & Bảo mật
        self.config_manager = BotConfigManager(config_path)
        SecretKeyGenerator.ensure_secret_key(self.config_manager)

        # 2. Database
        db_path = self.config_manager.get("database", "path", "naptien.db")
        self.db_pool = DatabaseConnectionPool(db_path)
        self.tx_repo = TransactionRepository(self.db_pool)
        self.guild_repo = GuildSettingsRepository(self.db_pool)
        self.ticket_repo = TicketRepository(self.db_pool)

        # 3. Dịch vụ bên ngoài
        sepay_token = self.config_manager.get("sepay", "api_token", "")
        self.sepay_service = SePayApiService(sepay_token)

        card_provider = self.config_manager.get("card", "provider", "thesieure")
        partner_id = self.config_manager.get("card", "partner_id", "")
        partner_key = self.config_manager.get("card", "partner_key", "")
        self.card_service = CardExchangeService(card_provider, partner_id, partner_key)

        self.sync_bridge = ConfigSyncBridgeService(
            self.config_manager, self.sepay_service, self.card_service
        )

        # 4. Discord Bot Client
        intents = discord.Intents.default()
        intents.message_content = True
        self.bot = commands.Bot(command_prefix="!", intents=intents)
        self.reward_notifier = RewardNotificationService(self.bot, self.config_manager)

        # 5. Localhost IPC Server (FastAPI)
        self.sync_endpoints = IpcSyncEndpoints(
            self.config_manager, self.sync_bridge, self.reward_notifier
        )
        self.ipc_server = LocalIpcServer(self.config_manager, self.sync_endpoints)

    async def _setup_discord_events(self) -> None:
        @self.bot.event
        async def on_ready():
            log.info(f"╔═════════════════════════════════════════════════════════════╗")
            log.info(f"║ PAYBOT DISCORD COMPANION v7.0.0 — ONLINE                     ║")
            log.info(f"║ Logged in as: {self.bot.user} (ID: {self.bot.user.id})")
            log.info(f"║ Local IPC Port: {self.config_manager.ipc_port}               ║")
            log.info(f"╚═════════════════════════════════════════════════════════════╝")
            try:
                synced = await self.bot.tree.sync()
                log.info(f"[BotLifecycleRunner] Đã đồng bộ {len(synced)} lệnh Slash Commands thành công.")
            except Exception as e:
                log.error(f"[BotLifecycleRunner] Lỗi sync slash commands: {e}")

    async def _register_cogs(self) -> None:
        await self.bot.add_cog(AdminSetupCog(self.bot, self.config_manager, self.sepay_service, self.guild_repo))
        await self.bot.add_cog(TopupSlashCog(self.bot, self.config_manager, self.sepay_service, self.card_service, self.tx_repo))
        await self.bot.add_cog(TicketSystemCog(self.bot, self.ticket_repo))
        await self.bot.add_cog(TranslatorCog(self.bot))

    async def start(self) -> None:
        """Khởi động đồng thời cả Bot Discord và Local IPC Server."""
        await self._setup_discord_events()
        await self._register_cogs()

        server = self.ipc_server.create_server()
        bot_token = self.config_manager.bot_token.strip()

        tasks = [asyncio.create_task(server.serve())]

        if bot_token:
            log.info("[BotLifecycleRunner] Đang đăng nhập Discord...")
            tasks.append(asyncio.create_task(self.bot.start(bot_token)))
        else:
            log.warning("⚠️ [BotLifecycleRunner] Chưa cấu hình 'bot_token' trong config.json! Tiến trình Discord tạm dừng.")
            log.info("ℹ️ Local IPC Server vẫn đang hoạt động để nhận cấu hình từ Minecraft Server.")

        await asyncio.gather(*tasks)
