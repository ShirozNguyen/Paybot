"""
LocalIpcServer — Khởi tạo máy chủ FastAPI micro-service cho giao tiếp IPC nội bộ.
Tuân thủ Rule 17 (Class chuyên trách duy nhất).
"""

from fastapi import FastAPI
import uvicorn
import logging

log = logging.getLogger(__name__)


class LocalIpcServer:
    """Class đóng gói ứng dụng FastAPI và cấu hình chạy ngầm trên Localhost."""

    def __init__(self, config_manager, sync_endpoints):
        self.config_manager = config_manager
        self.sync_endpoints = sync_endpoints
        self.app = FastAPI(title="PayBot Discord Companion IPC", docs_url=None, redoc_url=None)
        self._setup_app()

    def _setup_app(self) -> None:
        self.app.include_router(self.sync_endpoints.router)

    def create_server(self) -> uvicorn.Server:
        """Tạo uvicorn.Server cấu hình chạy async cùng event loop của Bot Discord."""
        host = self.config_manager.ipc_host
        port = self.config_manager.ipc_port

        config = uvicorn.Config(
            app=self.app,
            host=host,
            port=port,
            log_level="warning",
            access_log=False
        )
        log.info(f"[LocalIpcServer] IPC Server chuẩn bị lắng nghe trên http://{host}:{port}")
        return uvicorn.Server(config)
