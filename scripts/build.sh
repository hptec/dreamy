#!/usr/bin/env bash
# =============================================================
# 脚本名称: build.sh
# 功能描述: 构建前后端项目
# 使用方式: bash scripts/build.sh
# 依赖环境: Java 17+、Gradle 8+、Node 18+、pnpm
# =============================================================
set -euo pipefail

echo "[build] 开始构建项目..."

# 构建后端
if [ -d "backend" ]; then
  echo "[build] 构建后端..."
  cd backend
  if [ -f "gradlew" ]; then
    ./gradlew build -x test
  else
    gradle build -x test
  fi
  cd ..
fi

# 构建前端
FRONTEND_DIR=""
if [ -d "frontend" ]; then
  FRONTEND_DIR="frontend"
elif [ -d "web" ]; then
  FRONTEND_DIR="web"
fi

if [ -n "${FRONTEND_DIR}" ]; then
  echo "[build] 构建前端 (${FRONTEND_DIR})..."
  cd "${FRONTEND_DIR}"
  if [ ! -d "node_modules" ]; then
    echo "[build] 未找到 node_modules，正在执行 pnpm install..."
    pnpm install
  fi
  pnpm build
  cd ..
fi

echo "[build] 构建完成"
