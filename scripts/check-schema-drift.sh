#!/usr/bin/env bash
# =============================================================
# 脚本名称: check-schema-drift.sh
# 功能描述: schema 冻结门禁——从 live MySQL 重新 dump identity 库 11 张身份域
#           回滚快照表 DDL,与入库基准 server/schema/identity-reference-13.sql
#           (历史文件名,现管 11 张)逐字节比对,漂移即失败(防止 huihao auto-DDL/
#           手工变更悄悄改掉实体假设)。生成方式与基准文件完全同构
#           (mysqldump --no-data --compact --skip-add-drop-table + 剥 AUTO_INCREMENT 当前值)。
#           注意:operation_log/email_template 已收编 dreamy_server(Rust 主库),
#           权威 DDL = server/schema/identity.sql(自举),不在本门禁范围;
#           identity 库同名旧表为迁移遗留死表,保留作回滚快照。
# 使用方式: bash scripts/check-schema-drift.sh
# 失败处置: 若为有意变更——更新基准(重跑 dump 覆盖)并同步 SeaORM 实体
# =============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

ENV_FILE=".env.deploy"
[ -f "${ENV_FILE}" ] || { echo "[drift] 缺少 ${ENV_FILE}" >&2; exit 1; }
set -a; source "${ENV_FILE}"; set +a

BASELINE="server/schema/identity-reference-13.sql"
[ -f "${BASELINE}" ] || { echo "[drift] 缺少基准 ${BASELINE}" >&2; exit 1; }

LIVE="$(mktemp)"
trap 'rm -f "${LIVE}"' EXIT

docker compose --env-file "${ENV_FILE}" exec -T mysql mysqldump \
  -uroot -p"${MYSQL_ROOT_PASSWORD}" --no-data --compact --skip-add-drop-table \
  identity user user_identity user_session otp_code auth_config login_history \
  admin_user admin_session role permission role_permission \
  2>/dev/null | sed -e 's/^CREATE TABLE `/CREATE TABLE IF NOT EXISTS `/g' \
                    -e '/^\/\*!/d' -e '/^SET /d' -e 's/ AUTO_INCREMENT=[0-9]*//' > "${LIVE}"

# 双端剥注释行(基准文件含文档头注释,live dump 无注释;比对只看 DDL 本体)
if diff -u <(sed '/^--/d' "${BASELINE}") <(sed '/^--/d' "${LIVE}") > /dev/null; then
  echo "[drift] 一致:live schema 与入库基准逐字节相同(11 张表)"
else
  echo "[drift] 错误:检测到 schema 漂移!" >&2
  diff -u <(sed '/^--/d' "${BASELINE}") <(sed '/^--/d' "${LIVE}") | head -60 >&2
  echo "[drift] 处置:有意变更则更新基准与 SeaORM 实体;无意变更回查 huihao auto-DDL" >&2
  exit 1
fi
