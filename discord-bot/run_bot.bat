@echo off
chcp 65001 >nul
title PayBot Discord Companion v7.0.0
echo =======================================================
echo   PAYBOT DISCORD COMPANION v7.0.0 (LOCAL ENGINE)
echo =======================================================
echo.

cd /d "%~dp0"

echo [1/2] Kiem tra thu vien can thiet...
py -3 -m pip install -r requirements.txt -q

echo.
echo [2/2] Khoi dong PayBot Discord Companion...
py -3 main.py

pause
