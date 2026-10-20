"""
PayBot Discord Companion — Điểm khởi động chính thức.
Phiên bản 7.0.0 (Part 161).
"""

import asyncio
import logging
import sys
from bot_lifecycle_runner import BotLifecycleRunner

_LOG_FMT = "%(asctime)s │ %(levelname)-7s │ %(message)s"
_DATE_FMT = "%d/%m %H:%M:%S"
logging.basicConfig(level=logging.INFO, format=_LOG_FMT, datefmt=_DATE_FMT)

# Giảm spam log từ uvicorn
logging.getLogger("uvicorn.access").setLevel(logging.WARNING)


def main():
    runner = BotLifecycleRunner("config.json")
    try:
        asyncio.run(runner.start())
    except KeyboardInterrupt:
        print("\n[PayBot Companion] Đang dừng tiến trình an toàn...")
        sys.exit(0)


if __name__ == "__main__":
    main()
