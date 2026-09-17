#!/usr/bin/env bash
# =============================================================
# 脚本名称: migrate-identity.sh(v2.2)
# 功能描述: 一次性数据迁移——identity 库(v1 Java)→ dreamy_server 库(v2.2 分区):
#           路由表从旧 user.email + user_identity 生成,主档/时序直拷。
#
# 安全细则(codex 评审通过条件 + v2.2 §7):
#   1. 默认拒绝执行:必须显式 --confirm(维护窗口内、backend/server 已停写)
#   2. 预检断言:源库无外键/触发器/存储过程;无活跃写入连接
#   3. 目标表非空拒绝(除非 --force:重建目标表,源库不受影响)
#   4. 无断点续传:任一表失败即中止,重跑必须 --force
#   5. 逐表校验 COUNT + 全列 checksum;路由表行数 vs 源表分组计数
#   6. 复制后修正 AUTO_INCREMENT;user/user_identity 分区覆盖源 MAX(id)
#
# 使用方式: bash scripts/migrate-identity.sh --confirm [--force]
# 依赖环境: MySQL compose 容器可达;schema/identity.sql 为 v2.2
# 注意:     operation_log/email_template 的收编见 scripts/migrate-shared-tables.sh
#           (v2.3:两表迁 dreamy_server,Java 业务侧改经 gRPC)
# =============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

ENV_FILE=".env.deploy"
[ -f "${ENV_FILE}" ] || { echo "[migrate] 缺少 ${ENV_FILE}" >&2; exit 1; }
set -a; source "${ENV_FILE}"; set +a

SOURCE_DB="identity"
TARGET_DB="dreamy_server"
CONFIRM=0
FORCE=0
while [ $# -gt 0 ]; do
  case "$1" in
    --confirm) CONFIRM=1 ;;
    --force)   FORCE=1 ;;
    *) echo "[migrate] 未知参数: $1" >&2; exit 1 ;;
  esac
  shift
done

TABLES=(admin_session admin_user auth_config login_history otp_code permission role role_permission user user_identity user_session)

MYSQL="docker compose --env-file ${ENV_FILE} exec -T mysql mysql -uroot -p${MYSQL_ROOT_PASSWORD} --batch --raw"
sql() {
  local err; err="$(mktemp)"
  if ! echo "$1" | ${MYSQL} 2>"${err}"; then
    grep -v "insecure" "${err}" >&2 || true
    rm -f "${err}"; return 1
  fi
  rm -f "${err}"
}

if [ "${CONFIRM}" -ne 1 ]; then
  echo "[migrate] 拒绝执行:一次性迁移需显式 --confirm(维护窗口内、backend/server 已停写)" >&2
  echo "[migrate] 影响范围:仅创建/写入 ${TARGET_DB}(源库 ${SOURCE_DB} 全程只读)" >&2
  exit 1
fi

echo "[migrate] 预检 1/4:活跃写入者..."
ACTIVE="$(sql "SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB='${SOURCE_DB}' AND COMMAND NOT IN ('Sleep','Daemon','Binlog Dump') AND ID<>CONNECTION_ID();" | tail -1)"
if [ "${ACTIVE}" != "0" ]; then
  echo "[migrate] 错误:检测到 ${ACTIVE} 个活跃连接,请先停 backend/server" >&2
  exit 1
fi

echo "[migrate] 预检 2/4:外键/触发器/存储过程断言..."
FK="$(sql "SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE REFERENCED_TABLE_NAME IS NOT NULL AND TABLE_SCHEMA='${SOURCE_DB}';" | tail -1)"
TRG="$(sql "SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA='${SOURCE_DB}';" | tail -1)"
RTN="$(sql "SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA='${SOURCE_DB}';" | tail -1)"
if [ "${FK}" != "0" ] || [ "${TRG}" != "0" ] || [ "${RTN}" != "0" ]; then
  echo "[migrate] 错误:源库存在外键(${FK})/触发器(${TRG})/存储过程(${RTN}),与假设不符" >&2
  exit 1
fi

echo "[migrate] 预检 3/4:建 v2.2 schema(CREATE IF NOT EXISTS,幂等)..."
docker compose --env-file "${ENV_FILE}" exec -T mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" "${TARGET_DB}" < server/schema/identity.sql 2>/dev/null \
  || { echo "[migrate] 错误:schema 应用失败(检查 server/schema/identity.sql)" >&2; exit 1; }

echo "[migrate] 预检 4/4:目标表非空检查..."
NONEMPTY=""
for t in "${TABLES[@]}" identity_email identity_google identity_apple; do
  CNT="$(sql "SELECT COUNT(*) FROM \`${TARGET_DB}\`.\`${t}\`;" | tail -1)"
  if [ "${CNT}" != "0" ]; then NONEMPTY="${NONEMPTY} ${t}(${CNT})"; fi
done
if [ -n "${NONEMPTY}" ]; then
  if [ "${FORCE}" -ne 1 ]; then
    echo "[migrate] 错误:目标库已有数据的表:${NONEMPTY}" >&2
    echo "[migrate]   重放场景加 --force(TRUNCATE 目标表后重拷,源库不受影响)" >&2
    exit 1
  fi
  echo "[migrate] --force:清空目标表 ${NONEMPTY}"
  for t in "${TABLES[@]}" identity_email identity_google identity_apple; do
    sql "TRUNCATE TABLE \`${TARGET_DB}\`.\`${t}\`;"
  done
fi

# 分区覆盖预扩:源 MAX(id) 超出 p2 边界(1200 万)时逐段 REORGANIZE pmax
MAX_SRC_ID="$(sql "SELECT COALESCE(MAX(id),0) FROM \`${SOURCE_DB}\`.user;" | tail -1)"
ensure_partitions() { # table col
  local table="$1" col="$2" boundary seg
  boundary="$(sql "SELECT MAX(CAST(PARTITION_DESCRIPTION AS UNSIGNED)) FROM information_schema.PARTITIONS WHERE TABLE_SCHEMA='${TARGET_DB}' AND TABLE_NAME='${table}' AND PARTITION_DESCRIPTION <> 'MAXVALUE';" | tail -1)"
  while [ "${boundary}" -le "${MAX_SRC_ID}" ]; do
    seg=$((boundary / 4000000))
    sql "ALTER TABLE \`${TARGET_DB}\`.\`${table}\` REORGANIZE PARTITION pmax INTO (PARTITION p${seg} VALUES LESS THAN ($((boundary + 4000000))), PARTITION pmax VALUES LESS THAN (MAXVALUE));" \
      || { echo "[migrate] 错误:${table} 扩段失败" >&2; exit 1; }
    boundary=$((boundary + 4000000))
    echo "  ${table} 扩段至 ${boundary}"
  done
}
ensure_partitions user id
ensure_partitions user_identity user_id

echo "[migrate] 主档/时序/admin 直拷(11 表)..."
FAILED=0
copy_table() { # table
  local t="$1"
  echo "  → ${t}"
  local COLS="$(sql "SELECT GROUP_CONCAT(CONCAT('\`', COLUMN_NAME, '\`') ORDER BY ORDINAL_POSITION SEPARATOR ',') FROM information_schema.COLUMNS c WHERE TABLE_SCHEMA='${SOURCE_DB}' AND TABLE_NAME='${t}' AND EXISTS (SELECT 1 FROM information_schema.COLUMNS c2 WHERE c2.TABLE_SCHEMA='${TARGET_DB}' AND c2.TABLE_NAME='${t}' AND c2.COLUMN_NAME=c.COLUMN_NAME);" | tail -1)"
  sql "INSERT INTO \`${TARGET_DB}\`.\`${t}\` (${COLS}) SELECT ${COLS} FROM \`${SOURCE_DB}\`.\`${t}\`;" \
    || { echo "[migrate] 错误:复制 ${t} 失败(无断点续传,重跑需 --force)" >&2; FAILED=1; }
  # AUTO_INCREMENT 修正(有自增列的表)
  if [ "${t}" != "identity_email" ]; then
    local maxid
    maxid="$(sql "SELECT COALESCE(MAX(id),0) FROM \`${TARGET_DB}\`.\`${t}\`;" | tail -1)"
    [ "${maxid}" != "0" ] && sql "ALTER TABLE \`${TARGET_DB}\`.\`${t}\` AUTO_INCREMENT = $((maxid + 1));" || true
  fi
}
for t in "${TABLES[@]}"; do copy_table "${t}"; done
[ "${FAILED}" -eq 0 ] || exit 1

echo "[migrate] 路由表生成(v2.2 核心:从旧表推导)..."
# identity_email:全部有 email 的用户(含已删——与 v1 uk_user_email 语义一致;匿名化用户 email 已 NULL 天然排除)
sql "INSERT INTO \`${TARGET_DB}\`.identity_email (email, user_id, created_at) SELECT email, id, COALESCE(created_at, NOW()) FROM \`${SOURCE_DB}\`.user WHERE email IS NOT NULL;" \
  || { echo "[migrate] 错误:identity_email 生成失败" >&2; exit 1; }
# identity_google / identity_apple:connected=1 且未匿名化(provider_uid 不含 anon: 前缀)
sql "INSERT INTO \`${TARGET_DB}\`.identity_google (google_sub, user_id, created_at) SELECT provider_uid, user_id, COALESCE(bound_at, NOW()) FROM \`${SOURCE_DB}\`.user_identity WHERE provider=2 AND connected=1 AND provider_uid NOT LIKE 'anon:%';" \
  || { echo "[migrate] 错误:identity_google 生成失败" >&2; exit 1; }
sql "INSERT INTO \`${TARGET_DB}\`.identity_apple (apple_sub, user_id, relay_email, created_at) SELECT provider_uid, user_id, relay_email, COALESCE(bound_at, NOW()) FROM \`${SOURCE_DB}\`.user_identity WHERE provider=3 AND connected=1 AND provider_uid NOT LIKE 'anon:%';" \
  || { echo "[migrate] 错误:identity_apple 生成失败" >&2; exit 1; }

echo "[migrate] 校验:COUNT + checksum + 路由分组计数..."
FAIL=0
printf "%-18s %10s %12s %12s\n" "表" "源行数" "目标行数" "checksum"
printf "%-18s %10s %12s %12s\n" "----" "------" "--------" "--------"
verify_table() { # table [where_clause]
  local t="$1" extra="${2:-}"
  local SC TC SS TS COLS SUM_EXPR
  SC="$(sql "SELECT COUNT(*) FROM \`${SOURCE_DB}\`.\`${t}\` ${extra};" | tail -1)"
  TC="$(sql "SELECT COUNT(*) FROM \`${TARGET_DB}\`.\`${t}\`;" | tail -1)"
  COLS="$(sql "SELECT GROUP_CONCAT(CONCAT('IFNULL(\`', COLUMN_NAME, '\`, ''∅'')') ORDER BY ORDINAL_POSITION SEPARATOR ',') FROM information_schema.COLUMNS c WHERE TABLE_SCHEMA='${SOURCE_DB}' AND TABLE_NAME='${t}' AND EXISTS (SELECT 1 FROM information_schema.COLUMNS c2 WHERE c2.TABLE_SCHEMA='${TARGET_DB}' AND c2.TABLE_NAME='${t}' AND c2.COLUMN_NAME=c.COLUMN_NAME);" | tail -1)"
  SUM_EXPR="BIT_XOR(CRC32(CONCAT_WS('#', ${COLS})))"
  SS="$(sql "SELECT COALESCE(${SUM_EXPR},0) FROM \`${SOURCE_DB}\`.\`${t}\` ${extra};" | tail -1)"
  TS="$(sql "SELECT COALESCE(${SUM_EXPR},0) FROM \`${TARGET_DB}\`.\`${t}\`;" | tail -1)"
  printf "%-18s %10s %12s %12s\n" "${t}" "${SC}" "${TC}" "${SS}"
  if [ "${SC}" != "${TC}" ] || [ "${SS}" != "${TS}" ]; then
    echo "[migrate] 校验失败:${t} 行数(${SC}/${TC})或 checksum(${SS}/${TS})不一致" >&2
    FAIL=1
  fi
}
for t in "${TABLES[@]}"; do verify_table "${t}"; done

# 路由表分组计数(源侧推导条件 vs 目标行数)
verify_route() { # label target_table source_condition
  local label="$1" tt="$2" cond="$3" SC TC
  SC="$(sql "SELECT COUNT(*) FROM \`${SOURCE_DB}\`.user_identity WHERE ${cond};" | tail -1)"
  TC="$(sql "SELECT COUNT(*) FROM \`${TARGET_DB}\`.\`${tt}\`;" | tail -1)"
  printf "%-18s %10s %12s %12s\n" "${tt}" "${SC}" "${TC}" "-"
  if [ "${SC}" != "${TC}" ]; then
    echo "[migrate] 路由校验失败:${label} 源分组 ${SC} ≠ 目标 ${TC}" >&2
    FAIL=1
  fi
}
SC="$(sql "SELECT COUNT(*) FROM \`${SOURCE_DB}\`.user WHERE email IS NOT NULL;" | tail -1)"
TC="$(sql "SELECT COUNT(*) FROM \`${TARGET_DB}\`.identity_email;" | tail -1)"
printf "%-18s %10s %12s %12s\n" "identity_email" "${SC}" "${TC}" "-"
if [ "${SC}" != "${TC}" ]; then echo "[migrate] 路由校验失败:identity_email ${SC}≠${TC}" >&2; FAIL=1; fi
verify_route google identity_google "provider=2 AND connected=1 AND provider_uid NOT LIKE 'anon:%'"
verify_route apple identity_apple "provider=3 AND connected=1 AND provider_uid NOT LIKE 'anon:%'"

[ "${FAIL}" -eq 0 ] || { echo "[migrate] 迁移校验未通过——目标表保留供排查,重跑需 --force" >&2; exit 1; }

echo "[migrate] 完成:11 表直拷 + 3 路由表生成,双重校验全绿(源库零写操作)"
echo "[migrate] 回滚:TRUNCATE ${TARGET_DB} 各表 + 重启旧 Java 链路"
