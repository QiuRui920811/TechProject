#!/bin/bash
# TechProject 幫手 Bot 啟動腳本
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# 建立虛擬環境（首次執行）
if [ ! -d ".venv" ]; then
    echo "🔧 建立 Python 虛擬環境…"
    python3 -m venv .venv
fi

source .venv/bin/activate

# 安裝依賴
pip install -q -r requirements.txt

# 檢查 .env
if [ ! -f ".env" ]; then
    echo "⚠️  找不到 .env 設定檔！"
    echo "   請先複製範本：cp .env.example .env"
    echo "   然後填入 DISCORD_TOKEN 與 GEMINI_API_KEY"
    exit 1
fi

echo "🚀 啟動科技幫手 Bot…"
python3 bot.py
