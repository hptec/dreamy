#!/usr/bin/env bash
# prototype.sh — 启动 Maison Eden 原型开发服务器（pnpm + Vite + Vue 3）
set -euo pipefail
PROTOTYPE_DIR="${1:-hhspec/prototype}"

# 切到仓库根（非 git 仓库时回退到脚本上级目录）
ROOT="$(git rev-parse --show-toplevel 2>/dev/null || cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ ! -d "$PROTOTYPE_DIR" ]]; then
  echo "错误：原型目录 $PROTOTYPE_DIR 不存在" >&2
  exit 1
fi

command -v pnpm &>/dev/null || { echo "错误：pnpm 未安装，请先执行 npm install -g pnpm" >&2; exit 1; }

if [[ ! -d "$PROTOTYPE_DIR/node_modules" ]]; then
  echo "首次启动，正在安装依赖（使用本地缓存 + npmmirror 镜像）..."
  (cd "$PROTOTYPE_DIR" && npm_config_registry=https://registry.npmmirror.com pnpm install --prefer-offline) || {
    echo "错误：依赖安装失败，请手动执行: cd $PROTOTYPE_DIR && pnpm install" >&2
    exit 1
  }
fi

echo "启动 Maison Eden 原型开发服务器..."
echo "访问： http://localhost:5173"
cd "$PROTOTYPE_DIR" && pnpm dev
