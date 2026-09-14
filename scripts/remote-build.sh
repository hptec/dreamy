#!/usr/bin/env bash
# =============================================================
# 脚本名称: remote-build.sh
# 功能描述: 在香港服务器(x86_64 原生 amd64)上构建三镜像并装载进本机 docker daemon
#           (本地 tag: latest + <时间戳>-<短SHA>,不推送任何外部镜像仓库),
#           由本地 release.sh 远程模式触发(JAR/dist 已 scp 就位),也可服务器手动执行。
#           背景:本地 Mac 上 buildx 模拟拉取基础镜像回源慢且 Rosetta 版本敏感,
#           服务器原生构建 + Docker Hub 直连最稳;镜像仅在本机流转,无公网传输。
# 使用方式: bash scripts/remote-build.sh          # 在服务器 /opt/dreamy 下执行
# 依赖环境: 服务器已装 docker/compose、已 clone 仓库、.env.deploy 已填写
# 日志:     本脚本幂等,日志直接输出(stdout 供 release.sh 采集)
# =============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

ENV_FILE=".env.deploy"
if [ ! -f "${ENV_FILE}" ]; then
  echo "[remote-build] 未找到 ${ENV_FILE}" >&2
  exit 1
fi
set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

TAG="$(date +%Y%m%d%H%M)-$(git rev-parse --short HEAD)"

# 前置产物校验(JAR/dist 由 release.sh scp 上来,或服务器手动放置)
if [ ! -f backend/build/libs/identity-app.jar ]; then
  echo "[remote-build] 错误: backend/build/libs/identity-app.jar 缺失(应由 release.sh 传输)" >&2
  exit 1
fi
if [ ! -f frontend/portal-admin/dist/index.html ]; then
  echo "[remote-build] 错误: frontend/portal-admin/dist 缺失" >&2
  exit 1
fi

build_image() {
  local name="$1" dir="$2"; shift 2
  echo "[remote-build] ${name} 构建开始 $(date +%T)"
  # --provenance/--sbom off:OCI attestation manifest 在部分工具链不受支持,保持关闭
  # --load:镜像直接进本机 daemon,deploy.sh 凭本地 tag 启动,无需镜像仓库
  docker buildx build --platform linux/amd64 --provenance=false --sbom=false \
    -t "dreamy-${name}:latest" -t "dreamy-${name}:${TAG}" \
    --load "$@" "${dir}"
  echo "[remote-build] ${name} 构建完成 $(date +%T)"
}

build_image backend backend/
build_image admin frontend/portal-admin/
build_image store frontend/portal-store/ \
  --build-arg NEXT_PUBLIC_SITE_URL="${PUBLIC_STORE_URL:-}" \
  --build-arg NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY="${NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY:-}" \
  --build-arg NEXT_PUBLIC_GA4_ID="${NEXT_PUBLIC_GA4_ID:-}" \
  --build-arg ADMIN_ORIGIN="${ADMIN_ORIGIN:-${PUBLIC_STORE_URL:-}}"

echo "[remote-build] ALL DONE TAG=${TAG}"
