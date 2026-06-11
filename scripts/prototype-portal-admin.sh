#!/usr/bin/env bash
# prototype-portal-admin.sh — 启动 Dreamy 运营管理后台原型（portal-admin · Vue3 + Vite）
set -euo pipefail
ADMIN_DIR="hhspec/prototype/portal-admin"
cd "$(git rev-parse --show-toplevel)"
if [[ ! -d "$ADMIN_DIR" ]]; then
  echo "错误：后台目录 $ADMIN_DIR 不存在" >&2; exit 1
fi
command -v pnpm &>/dev/null || { echo "错误：pnpm 未安装，请先执行 npm install -g pnpm" >&2; exit 1; }
# 后台依赖装在 prototype workspace 根
if [[ ! -d "hhspec/prototype/node_modules" ]]; then
  echo "首次启动，正在安装依赖..."
  (cd hhspec/prototype && pnpm install --prefer-offline) || {
    echo "错误：依赖安装失败，请手动执行: cd hhspec/prototype && pnpm install" >&2
    exit 1
  }
fi
echo "启动 Dreamy 管理后台 → http://localhost:5174"
cd "$ADMIN_DIR" && pnpm dev
