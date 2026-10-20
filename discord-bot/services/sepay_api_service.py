"""
SePayApiService — Dịch vụ gọi API SePay và sinh mã VietQR thanh toán.
Tuân thủ Rule 17 (Class chuyên trách duy nhất).
Tích hợp Link Đối Tác SePay chính thức của tác giả (Affiliate Referral URL).
"""

import httpx
import logging
from typing import Optional, Dict, Any, List
from urllib.parse import quote

log = logging.getLogger(__name__)


class SePayApiService:
    """Class phụ trách toàn bộ tương tác với cổng thanh toán SePay."""

    BASE_URL = "https://my.sepay.vn/userapi"

    # Link đối tác SePay chính thức của tác giả (Hoa hồng 20% thuê bao, 10% loa)
    PARTNER_REFERRAL_URL = "https://my.sepay.vn/register?gcid=vrewlwez"
    PARTNER_HOME_URL = "https://sepay.vn?gcid=vrewlwez"

    def __init__(self, api_token: str = ""):
        self.api_token = api_token

    def set_token(self, token: str) -> None:
        self.api_token = token.strip()

    def get_headers(self) -> Dict[str, str]:
        return {
            "Authorization": f"Bearer {self.api_token}",
            "Content-Type": "application/json"
        }

    async def get_bank_accounts(self) -> List[Dict[str, Any]]:
        """Lấy danh sách tài khoản ngân hàng đã liên kết trên SePay."""
        if not self.api_token:
            return []
        url = f"{self.BASE_URL}/bankaccounts/list"
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                resp = await client.get(url, headers=self.get_headers())
                if resp.status_code == 200:
                    data = resp.json()
                    return data.get("bankaccounts", [])
                log.warning(f"[SePayApiService] Lỗi lấy danh sách bank ({resp.status_code}): {resp.text}")
        except Exception as e:
            log.error(f"[SePayApiService] Lỗi kết nối SePay: {e}")
        return []

    async def get_recent_transactions(self, limit: int = 20) -> List[Dict[str, Any]]:
        """Lấy danh sách giao dịch gần nhất từ SePay."""
        if not self.api_token:
            return []
        url = f"{self.BASE_URL}/transactions/list?limit={limit}"
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                resp = await client.get(url, headers=self.get_headers())
                if resp.status_code == 200:
                    data = resp.json()
                    return data.get("transactions", [])
        except Exception as e:
            log.error(f"[SePayApiService] Lỗi lấy giao dịch SePay: {e}")
        return []

    @staticmethod
    def generate_vietqr_url(bank_name: str, account_number: str, amount: int, memo: str) -> str:
        """Sinh URL ảnh VietQR chuẩn NAPAS tự động điền số tiền và nội dung chuyển khoản."""
        encoded_memo = quote(memo)
        return f"https://qr.sepay.vn/img?bank={bank_name}&acc={account_number}&template=compact&amount={amount}&des={encoded_memo}"
