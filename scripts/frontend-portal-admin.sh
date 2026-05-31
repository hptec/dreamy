#!/usr/bin/env bash
# frontend-portal-admin.sh — 启动管理后台（Vue3 + Vite），内置 restart
# 规范：exec 前台运行 / 无 .log .pid / 端口检测 / pnpm + npmmirror
set -euo pipefail

PORT="${1:-5174}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_DIR="$ROOT/frontend/portal-admin"

if lsof -ti:"$PORT" >/dev/null 2>&1; then
  echo "[portal-admin] 端口 $PORT 被占用，清理旧进程…"
  lsof -ti:"$PORT" | xargs kill -9 2>/dev/null || true
  sleep 1
fi

if [ ! -d "$APP_DIR" ]; then
  echo "[portal-admin] 未找到 $APP_DIR，请先执行 /pd:apply 生成后台工程"
  exit 1
fi

cd "$APP_DIR"
export npm_config_registry="https://registry.npmmirror.com"
if [ ! -d node_modules ]; then
  echo "[portal-admin] 安装依赖（pnpm）…"
  pnpm install
fi
echo "[portal-admin] 启动 Vite dev on :$PORT …"
exec pnpm dev --port "$PORT"
