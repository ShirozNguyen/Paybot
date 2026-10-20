"""
TicketRepository — Quản lý dữ liệu hệ thống Ticket hỗ trợ người chơi.
Tuân thủ Rule 17 (Class chuyên trách duy nhất).
"""

from datetime import datetime, timezone, timedelta
from typing import Optional, Dict, Any, List
from .database_connection_pool import DatabaseConnectionPool


class TicketRepository:
    """Class phụ trách lưu trữ và truy vấn bảng tickets."""

    def __init__(self, pool: DatabaseConnectionPool):
        self.pool = pool

    @staticmethod
    def now_vn() -> str:
        vn_time = datetime.now(timezone(timedelta(hours=7)))
        return vn_time.strftime("%Y-%m-%d %H:%M:%S")

    def create_ticket(self, guild_id: str, channel_id: str, user_id: str, player_name: str) -> int:
        """Tạo một ticket mới."""
        now = self.now_vn()
        with self.pool.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO tickets (guild_id, channel_id, user_id, player_name, status, created_at)
                VALUES (?, ?, ?, ?, 'OPEN', ?)
            """, (str(guild_id), str(channel_id), str(user_id), player_name, now))
            conn.commit()
            return cursor.lastrowid

    def close_ticket(self, channel_id: str) -> bool:
        """Đóng ticket dựa trên channel_id."""
        with self.pool.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                UPDATE tickets SET status = 'CLOSED' WHERE channel_id = ?
            """, (str(channel_id),))
            conn.commit()
            return cursor.rowcount > 0

    def get_open_ticket(self, channel_id: str) -> Optional[Dict[str, Any]]:
        """Lấy thông tin ticket đang mở."""
        with self.pool.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                SELECT * FROM tickets WHERE channel_id = ? AND status = 'OPEN'
            """, (str(channel_id),))
            row = cursor.fetchone()
            if row:
                return dict(row)
            return None
