#!/usr/bin/env bash
# =============================================================
# 脚本名称: release.sh
# 功能描述: 本地编译三镜像并推送阿里云 ACR(香港服务器 x86_64 → linux/amd64)
#           backend: 本地 Gradle 编译 JAR(JVM 字节码跨架构)→ 单阶段打包
#           admin:   本地 Vite 构建静态产物(跨架构)→ nginx 单阶段打包
#           store:   buildx 容器内全量构建(standalone 含平台 SWC 二进制,必须目标平台构建)
# 使用方式: bash scripts/release.sh          # 构建并推送 ACR(双 tag: latest + <时间戳>-<短SHA>)
#           bash scripts/release.sh --load   # 仅构建并导入本地(不推送,平台跟随本机,用于本地验证)
# 依赖环境: Docker Desktop(buildx)、GraalVM JDK25、pnpm、已 docker login ACR
# 配置来源: 仓库根目录 .env.deploy(见 .env.deploy.example)
# =============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

LOAD_ONLY=0
[ "${1:-}" = "--load" ] && LOAD_ONLY=1

ENV_FILE="${PROJECT_ROOT}/.env.deploy"
if [ ! -f "${ENV_FILE}" ]; then
  echo "[release] 未找到 ${ENV_FILE}" >&2
  echo "[release] 请先执行: cp .env.deploy.example .env.deploy 并填写(--load 模式 ACR_NAMESPACE 可填任意值)" >&2
  exit 1
fi
set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

# ── 公网地址派生:只在 PUBLIC_STORE_URL/PUBLIC_ADMIN_URL 填一次,构建期内联变量默认取同值 ──
NEXT_PUBLIC_SITE_URL="${NEXT_PUBLIC_SITE_URL:-${PUBLIC_STORE_URL:-}}"
VITE_STORE_BASE_URL="${VITE_STORE_BASE_URL:-${PUBLIC_STORE_URL:-}}"

# ── 前置校验 ─────────────────────────────────────────────
missing=()
for v in ACR_REGISTRY ACR_NAMESPACE; do
  if [ -z "$(eval "echo \${${v}:-}")" ]; then missing+=("${v}"); fi
done
if [ "${#missing[@]}" -gt 0 ]; then
  echo "[release] 错误: .env.deploy 缺少必填变量: ${missing[*]}" >&2
  exit 1
fi

if ! docker buildx version >/dev/null 2>&1; then
  echo "[release] 错误: docker buildx 不可用,请确认 Docker Desktop 已启动" >&2
  exit 1
fi

if [ -n "$(git -C "${PROJECT_ROOT}" status --porcelain --untracked-files=no)" ]; then
  echo "[release] 警告: 存在未提交改动,镜像内容可能与提交版本不一致" >&2
fi

# ── 平台与 tag ───────────────────────────────────────────
SHORT_SHA="$(git -C "${PROJECT_ROOT}" rev-parse --short HEAD)"
RELEASE_TAG="$(date +%Y%m%d%H%M)-${SHORT_SHA}"
PUSH_FLAG="--push"
PLATFORM="${PLATFORM:-linux/amd64}"
if [ "${LOAD_ONLY}" = "1" ]; then
  PUSH_FLAG="--load"
  # 本地验证用本机平台:原生运行最快且不需要模拟
  case "$(uname -m)" in
    arm64)  PLATFORM="linux/arm64" ;;
    x86_64) PLATFORM="linux/amd64" ;;
  esac
  echo "[release] --load 模式:平台 ${PLATFORM},不推送(镜像名与 compose 一致,可直接 up)"
fi

# ── ① 后端:本地编译 bootJar ─────────────────────────────
echo "[release] 编译后端 identity-app.jar ..."
if [ -d "/Library/Java/JavaVirtualMachines/graalvm-jdk-25.0.2+10.1/Contents/Home" ]; then
  export JAVA_HOME="/Library/Java/JavaVirtualMachines/graalvm-jdk-25.0.2+10.1/Contents/Home"
fi
(
  cd "${PROJECT_ROOT}/backend"
  # --no-build-cache:一次性发布构建不依赖增量缓存,规避外置卷上 cache 打包 stat 异常
  ./gradlew bootJar --no-daemon --no-build-cache -q
)
if [ ! -f "${PROJECT_ROOT}/backend/build/libs/identity-app.jar" ]; then
  echo "[release] 错误: bootJar 产物缺失" >&2
  exit 1
fi

# ── ② admin:本地构建静态产物(同源模式,VITE_API_BASE_URL 置空;/admin/ 子路径部署)──
echo "[release] 构建 portal-admin 静态产物 ..."
export npm_config_registry="https://registry.npmmirror.com"
(
  cd "${PROJECT_ROOT}/frontend/portal-admin"
  if [ ! -d node_modules ]; then pnpm install --frozen-lockfile; fi
  VITE_API_BASE_URL="" VITE_STORE_BASE_URL="${VITE_STORE_BASE_URL:-}" ADMIN_BASE="/admin/" pnpm build
)

# ── ③ 三镜像 buildx 构建(双 tag:latest + git-<sha>)──────
build_image() {
  local name="$1" dir="$2"; shift 2
  local repo="${ACR_REGISTRY}/${ACR_NAMESPACE}/dreamy-${name}"
  echo "[release] 构建镜像 ${repo} (platform: ${PLATFORM}, tags: latest / ${RELEASE_TAG}) ..."
  # shellcheck disable=SC2086
  docker buildx build \
    --platform "${PLATFORM}" \
    -t "${repo}:latest" \
    -t "${repo}:${RELEASE_TAG}" \
    "$@" \
    ${PUSH_FLAG} \
    "${dir}"
}

build_image backend "${PROJECT_ROOT}/backend"
build_image admin "${PROJECT_ROOT}/frontend/portal-admin"
build_image store "${PROJECT_ROOT}/frontend/portal-store" \
  --build-arg NEXT_PUBLIC_SITE_URL="${NEXT_PUBLIC_SITE_URL:-}" \
  --build-arg NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY="${NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY:-}" \
  --build-arg NEXT_PUBLIC_GA4_ID="${NEXT_PUBLIC_GA4_ID:-}" \
  --build-arg ADMIN_ORIGIN="${ADMIN_ORIGIN:-${PUBLIC_STORE_URL:-}}"

echo "[release] 完成。"
if [ "${LOAD_ONLY}" = "0" ]; then
  echo "[release] 已推送 ${ACR_REGISTRY}/${ACR_NAMESPACE}/dreamy-{backend,store,admin}:{latest,${RELEASE_TAG}}"
  echo "[release] 服务器执行: bash scripts/deploy.sh"
else
  echo "[release] 本地验证: docker compose --env-file .env.deploy up -d"
fi
