#!/usr/bin/env bash
# prototype-export.sh — 将 Dreamy 原型编译为静态 HTML 文件
set -euo pipefail
PROTOTYPE_DIR="${1:-hhspec/prototype}"
cd "$(git rev-parse --show-toplevel)"
command -v pnpm &>/dev/null || { echo "错误：pnpm 未安装" >&2; exit 1; }
if [[ ! -d "$PROTOTYPE_DIR/node_modules" ]]; then
  echo "安装依赖..."
  (cd "$PROTOTYPE_DIR" && pnpm install --prefer-offline)
fi
echo "构建静态文件..."
(cd "$PROTOTYPE_DIR" && pnpm build)
echo "修复 file:// 路径..."
node "$(git rev-parse --show-toplevel)/scripts/prototype-fix-static-paths.js"
echo ""
echo "✓ 静态文件已输出到 $PROTOTYPE_DIR/out/"
echo ""
echo "直接打开（file:// 协议）："
echo "  open $PROTOTYPE_DIR/out/index.html"
echo ""
echo "或本地服务器预览："
echo "  npx serve $PROTOTYPE_DIR/out -p 4173"
