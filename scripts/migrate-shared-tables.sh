#!/usr/bin/env bash
# =============================================================
# 脚本名称: migrate-shared-tables.sh(v2.3)
# 功能描述: 一次性数据迁移——operation_log/email_template 两张表
#           identity 库(共享过渡期)→ dreamy_server 库(Rust 主库,权威),
#           并对存量 auth_config 补 admin 登录失败锁定两列(自举只建表不加列)。
#
# 幂等设计(与 migrate-identity.sh 的 --force 语义不同):
#   - 按 PK(operation_log.id) / 唯一键(email_template.code+locale)
#     NOT EXISTS 增量补插,中断重跑安全;已收编行不重复不覆盖
#   - auth_config 加列前先查 information_schema 列存在性
#
# 安全细则:
#   1. 默认拒绝执行:必须显式 --confirm(维护窗口内、backend/server 已停写)
#   2. 预检断言:无活跃写入连接
#   3. 源库全程只读;目标库两表结构由 server/schema/identity.sql 自举
#   4. 搬迁后行数 + checksum 校验
#
# 使用方式: bash scripts/migrate-shared-tables.sh --confirm
# 回滚口径: identity 库旧表保留(死表快照);如需彻底回退 Rust→次库直连,
#           需代码级 revert(见 git 历史此提交前一版)
# =============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

ENV_FILE=".env.deploy"
[ -f "${ENV_FILE}" ] || { echo "[migrate2] 缺少 ${ENV_FILE}" >&2; exit 1; }
set -a; source "${ENV_FILE}"; set +a

SOURCE_DB="identity"
TARGET_DB="dreamy_server"
CONFIRM=0
while [ $# -gt 0 ]; do
  case "$1" in
    --confirm) CONFIRM=1 ;;
    *) echo "[migrate2] 未知参数: $1" >&2; exit 1 ;;
  esac
  shift
done

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
  echo "[migrate2] 拒绝执行:一次性迁移需显式 --confirm(维护窗口内、backend/server 已停写)" >&2
  echo "[migrate2] 影响范围:仅写入 ${TARGET_DB}(源库 ${SOURCE_DB} 全程只读)" >&2
  exit 1
fi

echo "[migrate2] 预检 1/3:活跃写入者..."
ACTIVE="$(sql "SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB='${SOURCE_DB}' AND COMMAND NOT IN ('Sleep','Daemon','Binlog Dump') AND ID<>CONNECTION_ID();" | tail -1)"
if [ "${ACTIVE}" != "0" ]; then
  echo "[migrate2] 错误:检测到 ${ACTIVE} 个活跃连接,请先停 backend/server" >&2
  exit 1
fi

echo "[migrate2] 预检 2/3:目标表结构(由 server/schema/identity.sql 自举,幂等)..."
docker compose --env-file "${ENV_FILE}" exec -T mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" "${TARGET_DB}" < server/schema/identity.sql 2>/dev/null \
  || { echo "[migrate2] 错误:schema 应用失败(检查 server/schema/identity.sql)" >&2; exit 1; }

echo "[migrate2] 预检 3/3:auth_config 补列(admin 登录失败锁定)..."
for col in admin_login_max_attempts admin_login_lock_minutes; do
  EXISTS="$(sql "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='${TARGET_DB}' AND TABLE_NAME='auth_config' AND COLUMN_NAME='${col}';" | tail -1)"
  if [ "${EXISTS}" == "0" ]; then
    case "${col}" in
      admin_login_max_attempts) DEF="INT NOT NULL DEFAULT 5 COMMENT 'admin 登录失败锁定阈值 3-10'" ;;
      admin_login_lock_minutes) DEF="INT NOT NULL DEFAULT 15 COMMENT 'admin 登录锁定时长 5-60 分钟'" ;;
    esac
    sql "ALTER TABLE \`${TARGET_DB}\`.auth_config ADD COLUMN \`${col}\` ${DEF};" \
      || { echo "[migrate2] 错误:auth_config 加列 ${col} 失败" >&2; exit 1; }
    echo "  + auth_config.${col}"
  else
    echo "  = auth_config.${col} 已存在"
  fi
done

echo "[migrate2] 搬迁 operation_log(按 id 幂等增量)..."
sql "INSERT INTO \`${TARGET_DB}\`.operation_log (id, operator_id, operator_name, action, target, ip, user_agent, changes, created_at, updated_at)
     SELECT s.id, s.operator_id, s.operator_name, s.action, s.target, s.ip, s.user_agent, s.changes, s.created_at, s.updated_at
     FROM \`${SOURCE_DB}\`.operation_log s
     WHERE NOT EXISTS (SELECT 1 FROM \`${TARGET_DB}\`.operation_log t WHERE t.id = s.id);" \
  || { echo "[migrate2] 错误:operation_log 搬迁失败(幂等,可直接重跑)" >&2; exit 1; }
MAXID="$(sql "SELECT COALESCE(MAX(id),0) FROM \`${TARGET_DB}\`.operation_log;" | tail -1)"
[ "${MAXID}" != "0" ] && sql "ALTER TABLE \`${TARGET_DB}\`.operation_log AUTO_INCREMENT = $((MAXID + 1));" || true

echo "[migrate2] 搬迁 email_template(按 code+locale 幂等增量,不覆盖 Rust 种子)..."
sql "INSERT INTO \`${TARGET_DB}\`.email_template (code, locale, subject, body, created_at, updated_at)
     SELECT s.code, s.locale, s.subject, s.body, s.created_at, s.updated_at
     FROM \`${SOURCE_DB}\`.email_template s
     WHERE NOT EXISTS (SELECT 1 FROM \`${TARGET_DB}\`.email_template t WHERE t.code = s.code AND t.locale = s.locale);" \
  || { echo "[migrate2] 错误:email_template 搬迁失败(幂等,可直接重跑)" >&2; exit 1; }

echo "[migrate2] 校验:行数与 checksum..."
FAIL=0
# 行覆盖:源表全部行都应已在目标库(NOT EXISTS 增量语义)
# checksum:仅 operation_log(append-only,id 主键,无重合)——email_template 目标为权威
# (种子/既有行优先,源行仅补缺),内容比对不适用,只验行覆盖
verify_rows() { # table key_expr
  local t="$1" key="$2" SC TC
  SC="$(sql "SELECT COUNT(*) FROM \`${SOURCE_DB}\`.\`${t}\`;" | tail -1)"
  TC="$(sql "SELECT COUNT(*) FROM \`${SOURCE_DB}\`.\`${t}\` s WHERE NOT EXISTS (SELECT 1 FROM \`${TARGET_DB}\`.\`${t}\` t WHERE ${key});" | tail -1)"
  printf "  %-16s 源行数=%-8s 未收编=%-6s\n" "${t}" "${SC}" "${TC}"
  if [ "${TC}" != "0" ]; then
    echo "[migrate2] 校验失败:${t} 未收编 ${TC} 行" >&2
    FAIL=1
  fi
}
verify_rows operation_log "t.id = s.id"
verify_rows email_template "t.code = s.code AND t.locale = s.locale"
# checksum 只比对「源自迁入行」的内容一致性(限源 id 集合)——目标库可合法含源外新行
# (收编后新写入/Rust 种子),全表双向相等在此语义下必误报
SS="$(sql "SELECT COALESCE(BIT_XOR(CRC32(CONCAT_WS('#', s.id, s.operator_id, s.action, s.target, s.created_at, s.updated_at))),0) FROM \`${SOURCE_DB}\`.operation_log s;" | tail -1)"
TS="$(sql "SELECT COALESCE(BIT_XOR(CRC32(CONCAT_WS('#', t.id, t.operator_id, t.action, t.target, t.created_at, t.updated_at))),0) FROM \`${TARGET_DB}\`.operation_log t WHERE EXISTS (SELECT 1 FROM \`${SOURCE_DB}\`.operation_log s WHERE s.id = t.id);" | tail -1)"
printf "  %-16s checksum(源自迁行)=%s/%s\n" "operation_log" "${SS}" "${TS}"
if [ "${SS}" != "${TS}" ]; then
  echo "[migrate2] 校验失败:operation_log 自迁行 checksum 不一致(内容损坏)" >&2
  FAIL=1
fi

[ "${FAIL}" -eq 0 ] || { echo "[migrate2] 迁移校验未通过——可修复后直接重跑(幂等增量)" >&2; exit 1; }

echo "[migrate2] 完成:两表收编 + auth_config 补列,校验全绿(源库零写操作)"
echo "[migrate2] 提示:identity 库同名旧表保留为死表快照;确认稳定后可手工 DROP"
