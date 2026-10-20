"""
IpcSyncEndpoints — Các API endpoints nội bộ nhận lệnh từ Minecraft Plugin / Mod.
Tuân thủ Rule 17 (Class chuyên trách duy nhất).
"""

from fastapi import APIRouter, Request, Header, HTTPException
from fastapi.responses import JSONResponse
import logging

log = logging.getLogger(__name__)


class IpcSyncEndpoints:
    """Class định nghĩa và gắn các router xử lý IPC nội bộ từ Minecraft."""

    def __init__(self, config_manager, sync_bridge, reward_notifier):
        self.config_manager = config_manager
        self.sync_bridge = sync_bridge
        self.reward_notifier = reward_notifier
        self.router = APIRouter(prefix="/internal")
        self._register_routes()

    def _register_routes(self) -> None:
        @self.router.get("/status")
        async def handle_status():
            """Endpoint kiểm tra trạng thái hoạt động của Bot (Ping)."""
            return {
                "status": "online",
                "companion": "PayBot Discord Companion",
                "version": "7.0.0"
            }

        @self.router.post("/sync-from-game")
        async def handle_sync_from_game(request: Request, x_secret_key: str = Header(None)):
            """Nhận dữ liệu cấu hình đồng bộ thời gian thực từ Minecraft Server."""
            expected_key = self.config_manager.get("ipc", "secret_key", "").strip()
            if expected_key and x_secret_key != expected_key:
                raise HTTPException(status_code=403, detail="Forbidden: Sai mã khóa bảo mật IPC.")

            try:
                payload = await request.json()
                success = self.sync_bridge.apply_sync_from_game(payload)
                return {"success": success, "message": "Synced successfully"}
            except Exception as e:
                log.error(f"[IpcSyncEndpoints] Lỗi đồng bộ: {e}")
                return JSONResponse(status_code=400, content={"success": False, "error": str(e)})

        @self.router.post("/notify-payment")
        async def handle_notify_payment(request: Request, x_secret_key: str = Header(None)):
            """Nhận tín hiệu nạp thành công từ Minecraft Server để phát sóng vào Discord Channel."""
            expected_key = self.config_manager.get("ipc", "secret_key", "").strip()
            if expected_key and x_secret_key != expected_key:
                raise HTTPException(status_code=403, detail="Forbidden: Sai mã khóa bảo mật IPC.")

            try:
                data = await request.json()
                player_name = data.get("player_name", "Unknown")
                tx_type = data.get("tx_type", "BANK")
                amount = int(data.get("amount", 0))
                code = data.get("code", "")

                success = await self.reward_notifier.send_payment_notification(player_name, tx_type, amount, code)
                return {"success": success}
            except Exception as e:
                log.error(f"[IpcSyncEndpoints] Lỗi gửi thông báo nạp: {e}")
                return JSONResponse(status_code=400, content={"success": False, "error": str(e)})
