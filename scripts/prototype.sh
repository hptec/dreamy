#!/usr/bin/env bash
# prototype.sh — 启动 Dreamy 原型开发服务器（Next.js + Vite-like dev）
set -euo pipefail
PROTOTYPE_DIR="${1:-hhspec/prototype}"
cd "$(git rev-parse --show-toplevel)"
if [[ ! -d "$PROTOTYPE_DIR" ]]; then
  echo "错误：原型目录 $PROTOTYPE_DIR 不存在" >&2; exit 1
fi
command -v pnpm &>/dev/null || { echo "错误：pnpm 未安装，请先全局安装 pnpm（corepack enable pnpm）" >&2; exit 1; }
if [[ ! -d "$PROTOTYPE_DIR/node_modules" ]]; then
  echo "首次启动，正在安装依赖..."
  (cd "$PROTOTYPE_DIR" && pnpm install --prefer-offline) || {
    echo "错误：依赖安装失败，请手动执行: cd $PROTOTYPE_DIR && pnpm install" >&2
    exit 1
  }
fi
echo "启动 Dreamy 原型开发服务器 → http://localhost:5173"
cd "$PROTOTYPE_DIR" && pnpm dev
