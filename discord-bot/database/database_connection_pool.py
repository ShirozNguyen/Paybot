"""
DatabaseConnectionPool — Quản lý kết nối SQLite WAL an toàn cho Bot Discord.
Tuân thủ Rule 17 (Class chuyên trách duy nhất).
"""

import sqlite3
import os
import logging
from typing import Optional

log = logging.getLogger(__name__)


class DatabaseConnectionPool:
    """Class quản lý kết nối và khởi tạo schema SQLite WAL mode."""

    def __init__(self, db_path: str = "naptien.db"):
        self.db_path = db_path
        self.init_database()

    def get_connection(self) -> sqlite3.Connection:
        """Tạo kết nối SQLite mới với cấu hình timeout và WAL mode."""
        conn = sqlite3.connect(self.db_path, timeout=15.0)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA journal_mode = WAL;")
        conn.execute("PRAGMA synchronous = NORMAL;")
        return conn

    def init_database(self) -> None:
        """Khởi tạo toàn bộ các bảng cơ sở dữ liệu nếu chưa tồn tại."""
        try:
            with self.get_connection() as conn:
                cursor = conn.cursor()

                # Bảng lưu giao dịch nạp tiền
                cursor.execute("""
                    CREATE TABLE IF NOT EXISTS transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        guild_id TEXT,
                        player_name TEXT,
                        tx_type TEXT,
                        amount INTEGER,
                        code TEXT,
                        status TEXT,
                        created_at TEXT,
                        completed_at TEXT,
                        extra_data TEXT
                    );
                """)

                # Bảng lưu cấu hình per-guild
                cursor.execute("""
                    CREATE TABLE IF NOT EXISTS guild_settings (
                        guild_id TEXT PRIMARY KEY,
                        notify_channel_id TEXT,
                        admin_role_id TEXT,
                        prefix TEXT DEFAULT '!',
                        sync_enabled INTEGER DEFAULT 1,
                        updated_at TEXT
                    );
                """)

                # Bảng lưu ticket hỗ trợ
                cursor.execute("""
                    CREATE TABLE IF NOT EXISTS tickets (
                        ticket_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        guild_id TEXT,
                        channel_id TEXT,
                        user_id TEXT,
                        player_name TEXT,
                        status TEXT,
                        created_at TEXT
                    );
                """)

                conn.commit()
            log.info(f"[DatabaseConnectionPool] Đã xác thực schema CSDL SQLite tại {self.db_path}")
        except Exception as e:
            log.error(f"[DatabaseConnectionPool] Lỗi khởi tạo SQLite schema: {e}")
