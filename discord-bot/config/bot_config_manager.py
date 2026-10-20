"""
BotConfigManager — Quản lý cấu hình tập trung cho PayBot Discord Companion.
Tuân thủ Rule 17 (mỗi function/chức năng là 1 class riêng biệt).
Tuân thủ nguyên tắc bảo mật: KHÔNG lưu trữ token, IP hay secret key cá nhân trong code.
"""

import os
import json
import logging
from typing import Dict, Any

log = logging.getLogger(__name__)


class BotConfigManager:
    """Class độc lập phụ trách nạp, thẩm định và lưu trữ cấu hình Bot Discord."""

    DEFAULT_CONFIG_PATH = "config.json"

    # Link giới thiệu đối tác SePay chính thức của tác giả (Công khai)
    DEFAULT_SEPAY_REFERRAL_URL = "https://my.sepay.vn/register?gcid=vrewlwez"
    DEFAULT_SEPAY_HOME_URL = "https://sepay.vn?gcid=vrewlwez"

    def __init__(self, config_path: str = DEFAULT_CONFIG_PATH):
        self.config_path = config_path
        self._data: Dict[str, Any] = {}
        self.load()

    def get_default_config(self) -> Dict[str, Any]:
        """Tạo khung cấu hình mặc định sạch (Zero Sensitive Data)."""
        return {
            "discord": {
                "bot_token": os.getenv("DISCORD_TOKEN", ""),
                "guild_id": os.getenv("DISCORD_GUILD_ID", ""),
                "notify_channel_id": os.getenv("DISCORD_CHANNEL_ID", ""),
                "owner_id": 0
            },
            "ipc": {
                "host": "127.0.0.1",
                "port": int(os.getenv("PAYBOT_IPC_PORT", "24733")),
                "secret_key": ""
            },
            "sepay": {
                "api_token": "",
                "account_number": "",
                "bank_name": "",
                "referral_url": self.DEFAULT_SEPAY_REFERRAL_URL,
                "home_url": self.DEFAULT_SEPAY_HOME_URL
            },
            "card": {
                "provider": "thesieure",
                "partner_id": "",
                "partner_key": ""
            },
            "database": {
                "path": "naptien.db"
            }
        }

    def load(self) -> None:
        """Đọc file cấu hình từ ổ đĩa hoặc tạo mới nếu chưa tồn tại."""
        if not os.path.exists(self.config_path):
            self._data = self.get_default_config()
            self.save()
            log.info(f"[BotConfigManager] Đã khởi tạo file cấu hình mẫu tại: {self.config_path}")
            return

        try:
            with open(self.config_path, "r", encoding="utf-8") as f:
                loaded = json.load(f)
                defaults = self.get_default_config()
                # Merge sâu để tránh thiếu key
                for section, vals in defaults.items():
                    if section not in loaded:
                        loaded[section] = vals
                    elif isinstance(vals, dict):
                        for k, v in vals.items():
                            if k not in loaded[section]:
                                loaded[section][k] = v
                self._data = loaded
        except Exception as e:
            log.error(f"[BotConfigManager] Lỗi khi nạp cấu hình từ {self.config_path}: {e}")
            self._data = self.get_default_config()

    def save(self) -> bool:
        """Lưu cấu hình an toàn xuống ổ đĩa."""
        try:
            with open(self.config_path, "w", encoding="utf-8") as f:
                json.dump(self._data, f, indent=4, ensure_ascii=False)
            return True
        except Exception as e:
            log.error(f"[BotConfigManager] Không thể ghi file {self.config_path}: {e}")
            return False

    def get(self, section: str, key: str, default: Any = None) -> Any:
        return self._data.get(section, {}).get(key, default)

    def set(self, section: str, key: str, value: Any) -> None:
        if section not in self._data:
            self._data[section] = {}
        self._data[section][key] = value

    @property
    def bot_token(self) -> str:
        return self.get("discord", "bot_token", "")

    @property
    def ipc_port(self) -> int:
        return int(self.get("ipc", "port", 24733))

    @property
    def ipc_host(self) -> str:
        return self.get("ipc", "host", "127.0.0.1")

    @property
    def sepay_referral_url(self) -> str:
        return self.get("sepay", "referral_url", self.DEFAULT_SEPAY_REFERRAL_URL)
