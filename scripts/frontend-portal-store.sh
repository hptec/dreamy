#!/usr/bin/env bash
# frontend-portal-store.sh — 启动消费端（Next.js 15），内置 restart
# 规范：exec 前台运行 / 无 .log .pid / 端口检测 / pnpm + npmmirror
set -euo pipefail

PORT="${1:-5173}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_DIR="$ROOT/frontend/portal-store"

if lsof -ti:"$PORT" >/dev/null 2>&1; then
  echo "[portal-store] 端口 $PORT 被占用，清理旧进程…"
  lsof -ti:"$PORT" | xargs kill -9 2>/dev/null || true
  sleep 1
fi

if [ ! -d "$APP_DIR" ]; then
  echo "[portal-store] 未找到 $APP_DIR，请先执行 /pd:apply 生成消费端工程"
  exit 1
fi

cd "$APP_DIR"
export npm_config_registry="https://registry.npmmirror.com"
if [ ! -d node_modules ]; then
  echo "[portal-store] 安装依赖（pnpm）…"
  pnpm install
fi
echo "[portal-store] 启动 Next.js dev on :$PORT …"
exec pnpm dev --port "$PORT"
