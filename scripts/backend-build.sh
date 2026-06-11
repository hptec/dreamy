#!/usr/bin/env bash
# =============================================================
# 脚本名称: backend-build.sh
# 功能描述: 构建后端项目（Gradle，跳过测试）
# 使用方式: bash scripts/backend-build.sh
# 依赖环境: JDK 25（GraalVM）、Gradle Wrapper
# =============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

if [ -d "/Library/Java/JavaVirtualMachines/graalvm-jdk-25.0.2+10.1/Contents/Home" ]; then
  export JAVA_HOME="/Library/Java/JavaVirtualMachines/graalvm-jdk-25.0.2+10.1/Contents/Home"
fi

cd "${PROJECT_ROOT}/backend"
echo "[backend-build] 构建后端..."
./gradlew build -x test
echo "[backend-build] 完成"
