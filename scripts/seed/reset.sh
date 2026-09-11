#!/usr/bin/env bash
# =============================================================
# 脚本名称: reset.sh(数据重置)
# 功能描述: 清空香港服务器的开发/演示数据(见 reset.sql),关闭 demo seed,
#           flush Redis 并重启 backend(同时清进程内 caffeine 缓存)。
#           清库后网站为空站,随后用 seed.mjs 灌入生产数据。
# 使用方式: bash scripts/seed/reset.sh
# 依赖环境: SSH 免密到服务器;服务器 mysql 容器运行中
# =============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_SSH="${DEPLOY_SSH:-root@47.238.216.69}"
REMOTE_DIR="${DEPLOY_DIR:-/opt/dreamy}"

if ! ssh -o ConnectTimeout=10 -o BatchMode=yes "${DEPLOY_SSH}" true 2>/dev/null; then
  echo "[reset] 错误: 无法 SSH 连接 ${DEPLOY_SSH}" >&2
  exit 1
fi

echo "[reset] 1/4 关闭 demo seed(防止重启后灌回演示数据)..."
ssh "${DEPLOY_SSH}" "cd ${REMOTE_DIR} && sed -i 's|^DEMO_SEED_ENABLED=.*|DEMO_SEED_ENABLED=false|' .env.deploy && grep '^DEMO_SEED_ENABLED=' .env.deploy"

echo "[reset] 2/4 truncate 业务表..."
PW_CMD='grep "^MYSQL_ROOT_PASSWORD=" /opt/dreamy/.env.deploy | cut -d= -f2'
cat "${SCRIPT_DIR}/reset.sql" | ssh "${DEPLOY_SSH}" "docker exec -i dreamy-mysql-1 mysql -uroot -p\$(eval ${PW_CMD}) identity" && echo "  truncate 完成"

echo "[reset] 3/4 flush Redis..."
ssh "${DEPLOY_SSH}" 'docker exec dreamy-redis-1 redis-cli flushall'

echo "[reset] 4/4 重启 backend(应用 env + 清本地缓存)..."
ssh "${DEPLOY_SSH}" "cd ${REMOTE_DIR} && docker compose --env-file .env.deploy up -d backend 2>&1 | tail -2"

echo "[reset] 完成。空站就绪,执行 seed: bash scripts/seed/seed.mjs(或 node scripts/seed/seed.mjs)"
