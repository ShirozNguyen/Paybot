"""
TransactionRepository — Lưu trữ và truy vấn giao dịch nạp tiền.
Tuân thủ Rule 17 (Class chuyên trách duy nhất).
"""

import json
from datetime import datetime, timezone, timedelta
from typing import Optional, Dict, Any, List
from .database_connection_pool import DatabaseConnectionPool


class TransactionRepository:
    """Class phụ trách toàn bộ truy vấn bảng transactions."""

    def __init__(self, pool: DatabaseConnectionPool):
        self.pool = pool

    @staticmethod
    def now_vn() -> str:
        vn_time = datetime.now(timezone(timedelta(hours=7)))
        return vn_time.strftime("%Y-%m-%d %H:%M:%S")

    def create_transaction(self, guild_id: str, player_name: str, tx_type: str,
                           amount: int, code: str, extra: Optional[Dict[str, Any]] = None) -> int:
        """Tạo giao dịch nạp mới."""
        now = self.now_vn()
        extra_json = json.dumps(extra or {}, ensure_ascii=False)

        with self.pool.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO transactions (guild_id, player_name, tx_type, amount, code, status, created_at, extra_data)
                VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?)
            """, (guild_id, player_name, tx_type, amount, code, now, extra_json))
            conn.commit()
            return cursor.lastrowid

    def mark_completed(self, tx_id: int) -> bool:
        """Đánh dấu giao dịch nạp thành công."""
        now = self.now_vn()
        with self.pool.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                UPDATE transactions
                SET status = 'SUCCESS', completed_at = ?
                WHERE id = ? AND status = 'PENDING'
            """, (now, tx_id))
            conn.commit()
            return cursor.rowcount > 0

    def mark_failed(self, tx_id: int, reason: str = "") -> bool:
        """Đánh dấu giao dịch nạp thất bại."""
        now = self.now_vn()
        with self.pool.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                UPDATE transactions
                SET status = 'FAILED', completed_at = ?, extra_data = json_set(COALESCE(extra_data, '{}'), '$.fail_reason', ?)
                WHERE id = ?
            """, (now, reason, tx_id))
            conn.commit()
            return cursor.rowcount > 0

    def get_by_code(self, code: str) -> Optional[Dict[str, Any]]:
        """Tìm giao dịch theo mã code chuyển khoản/thẻ."""
        with self.pool.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM transactions WHERE code = ?", (code,))
            row = cursor.fetchone()
            if row:
                return dict(row)
            return None

    def get_recent(self, limit: int = 10) -> List[Dict[str, Any]]:
        """Lấy danh sách các giao dịch gần đây."""
        with self.pool.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM transactions ORDER BY id DESC LIMIT ?", (limit,))
            return [dict(r) for r in cursor.fetchall()]
