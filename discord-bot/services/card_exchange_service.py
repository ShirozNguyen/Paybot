"""
CardExchangeService — Dịch vụ gạch thẻ cào đa nhà mạng (TheSieuRe, CardVIP, Web2M).
Tuân thủ Rule 17 (Class chuyên trách duy nhất).
"""

import hashlib
import httpx
import logging
from typing import Optional, Dict, Any

log = logging.getLogger(__name__)


class CardExchangeService:
    """Class phụ trách gửi và kiểm tra trạng thái thẻ cào viễn thông."""

    PROVIDERS = {
        "thesieure": "https://thesieure.com/chargingws/v2",
        "cardvip": "https://partner.cardvip.vn/api/create-exchange",
        "web2m": "https://api.web2m.com/api/card"
    }

    def __init__(self, provider: str = "thesieure", partner_id: str = "", partner_key: str = ""):
        self.provider = provider.lower()
        self.partner_id = partner_id
        self.partner_key = partner_key

    def set_credentials(self, provider: str, partner_id: str, partner_key: str) -> None:
        self.provider = provider.lower()
        self.partner_id = partner_id.strip()
        self.partner_key = partner_key.strip()

    def generate_sign(self, pin: str, serial: str) -> str:
        """Tạo mã MD5 chữ ký bảo mật cho request gạch thẻ."""
        raw = f"{self.partner_key}{pin}{serial}"
        return hashlib.md5(raw.encode("utf-8")).hexdigest()

    async def submit_card(self, telco: str, pin: str, serial: str, amount: int, request_id: str) -> Dict[str, Any]:
        """Gửi thông tin thẻ cào lên cổng đối tác."""
        if not self.partner_id or not self.partner_key:
            return {"status": "error", "message": "Cổng gạch thẻ chưa được cấu hình Partner ID / Key."}

        url = self.PROVIDERS.get(self.provider, self.PROVIDERS["thesieure"])
        sign = self.generate_sign(pin, serial)

        payload = {
            "telco": telco.upper(),
            "code": pin,
            "serial": serial,
            "amount": amount,
            "request_id": request_id,
            "partner_id": self.partner_id,
            "sign": sign,
            "command": "charging"
        }

        try:
            async with httpx.AsyncClient(timeout=15.0) as client:
                resp = await client.post(url, json=payload)
                if resp.status_code == 200:
                    return resp.json()
                return {"status": "error", "message": f"Cổng thẻ trả về HTTP {resp.status_code}"}
        except Exception as e:
            log.error(f"[CardExchangeService] Lỗi khi gửi thẻ: {e}")
            return {"status": "error", "message": str(e)}
