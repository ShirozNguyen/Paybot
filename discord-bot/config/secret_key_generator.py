"""
SecretKeyGenerator — Tạo mã khóa bí mật ngẫu nhiên cho kết nối IPC nội bộ giữa Minecraft và Bot Discord.
Tuân thủ Rule 17 (Class chuyên trách duy nhất).
"""

import secrets
import string
import logging

log = logging.getLogger(__name__)


class SecretKeyGenerator:
    """Class sinh chuỗi bảo mật ngẫu nhiên cho kết nối IPC."""

    @staticmethod
    def generate_token(length: int = 32) -> str:
        """Tạo chuỗi token bảo mật ngẫu nhiên dùng bảng chữ cái và số an toàn."""
        alphabet = string.ascii_letters + string.digits
        return "".join(secrets.choice(alphabet) for _ in range(length))

    @staticmethod
    def ensure_secret_key(config_manager) -> str:
        """Kiểm tra và sinh khóa bí mật nếu chưa có trong cấu hình."""
        existing = config_manager.get("ipc", "secret_key", "").strip()
        if not existing:
            new_key = SecretKeyGenerator.generate_token(32)
            config_manager.set("ipc", "secret_key", new_key)
            config_manager.save()
            log.info("[SecretKeyGenerator] Đã tạo mã bí mật IPC ngẫu nhiên mới.")
            return new_key
        return existing
