"""
GuildSettingsRepository — Quản lý cấu hình per-guild trong SQLite.
Tuân thủ Rule 17 (Class chuyên trách duy nhất).
"""

from datetime import datetime, timezone, timedelta
from typing import Optional, Dict, Any
from .database_connection_pool import DatabaseConnectionPool


class GuildSettingsRepository:
    """Class phụ trách lưu trữ và truy vấn cấu hình từng Guild Discord."""

    def __init__(self, pool: DatabaseConnectionPool):
        self.pool = pool

    @staticmethod
    def now_vn() -> str:
        vn_time = datetime.now(timezone(timedelta(hours=7)))
        return vn_time.strftime("%Y-%m-%d %H:%M:%S")

    def get_settings(self, guild_id: str) -> Optional[Dict[str, Any]]:
        """Lấy cấu hình của một guild."""
        with self.pool.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM guild_settings WHERE guild_id = ?", (str(guild_id),))
            row = cursor.fetchone()
            if row:
                return dict(row)
            return None

    def upsert_notify_channel(self, guild_id: str, channel_id: str) -> None:
        """Cập nhật ID kênh thông báo nạp tiền."""
        now = self.now_vn()
        with self.pool.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO guild_settings (guild_id, notify_channel_id, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(guild_id) DO UPDATE SET
                    notify_channel_id = excluded.notify_channel_id,
                    updated_at = excluded.updated_at
            """, (str(guild_id), str(channel_id), now))
            conn.commit()

    def set_sync_status(self, guild_id: str, enabled: bool) -> None:
        """Bật/tắt cờ đồng bộ 2 chiều per-guild."""
        now = self.now_vn()
        with self.pool.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO guild_settings (guild_id, sync_enabled, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(guild_id) DO UPDATE SET
                    sync_enabled = excluded.sync_enabled,
                    updated_at = excluded.updated_at
            """, (str(guild_id), 1 if enabled else 0, now))
            conn.commit()
