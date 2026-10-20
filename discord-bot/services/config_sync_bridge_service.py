"""
ConfigSyncBridgeService — Cầu nối đồng bộ cấu hình 2 chiều thời gian thực giữa Minecraft Server và Discord Bot.
Tuân thủ Rule 17 (Class chuyên trách duy nhất).
"""

import logging
from typing import Dict, Any, Optional

log = logging.getLogger(__name__)


class ConfigSyncBridgeService:
    """Class điều phối đồng bộ trạng thái cấu hình giữa Minecraft Plugin/Mod và Bot Discord."""

    def __init__(self, config_manager, sepay_service, card_service):
        self.config_manager = config_manager
        self.sepay_service = sepay_service
        self.card_service = card_service

    def apply_sync_from_game(self, payload: Dict[str, Any]) -> bool:
        """Nhận và cập nhật cấu hình mới từ Minecraft Server (Local IPC)."""
        try:
            settings = payload.get("settings", {})
            log.info(f"[ConfigSyncBridgeService] Nhận tín hiệu đồng bộ từ Minecraft: {list(settings.keys())}")

            # Đồng bộ cấu hình SePay
            if "sepay_api_token" in settings:
                self.config_manager.set("sepay", "api_token", settings["sepay_api_token"])
                self.sepay_service.set_token(settings["sepay_api_token"])

            if "sepay_account_number" in settings:
                self.config_manager.set("sepay", "account_number", settings["sepay_account_number"])

            if "sepay_bank_name" in settings:
                self.config_manager.set("sepay", "bank_name", settings["sepay_bank_name"])

            # Đồng bộ cấu hình Thẻ cào
            if "card_provider" in settings:
                self.config_manager.set("card", "provider", settings["card_provider"])

            if "card_partner_id" in settings:
                self.config_manager.set("card", "partner_id", settings["card_partner_id"])

            if "card_partner_key" in settings:
                self.config_manager.set("card", "partner_key", settings["card_partner_key"])
                self.card_service.set_credentials(
                    self.config_manager.get("card", "provider", "thesieure"),
                    self.config_manager.get("card", "partner_id", ""),
                    settings["card_partner_key"]
                )

            # Đồng bộ ID kênh thông báo nếu có
            if "notify_channel_id" in settings:
                self.config_manager.set("discord", "notify_channel_id", str(settings["notify_channel_id"]))

            self.config_manager.save()
            log.info("[ConfigSyncBridgeService] Đã lưu cấu hình mới thành công vào RAM và ổ đĩa.")
            return True
        except Exception as e:
            log.error(f"[ConfigSyncBridgeService] Lỗi khi xử lý đồng bộ từ Minecraft: {e}")
            return False
