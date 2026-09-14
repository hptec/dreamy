#!/usr/bin/env bash
# =============================================================
# 脚本名称: migrate-identity.sh
# 功能描述: 一次性数据迁移——同实例跨库把 identity 库 11 张身份域表
#           复制到 dreamy_server 库(源库全程只读,回滚=清理目标表,零数据损失)。
#
# 安全细则(codex 评审通过条件):
#   1. 默认拒绝执行:必须显式 --confirm(维护窗口内、backend/server 已停写)
#   2. 预检断言:11 表无外键/触发器/存储过程引用(存在即中止报告)
#   3. 目标表非空拒绝(除非 --force:DROP 目标表重建,破坏性操作将大声记录)
#   4. 无断点续传:任一表失败即中止,重跑必须 --force 清理重建
#   5. 逐表校验 COUNT + 全列 BIT_XOR(CRC32(...)) checksum(NULL 哨兵确定性拼接)
#   6. 复制后修正 AUTO_INCREMENT(CREATE TABLE LIKE 不携带自增计数)
#
# 使用方式: bash scripts/migrate-identity.sh --confirm [--force]
#           [--source identity] [--target dreamy_server]
# 依赖环境: MySQL compose 容器可达(脚本经 docker compose exec 执行 SQL,
#           与发布链同构,无宿主 mysql 客户端依赖)
# 注意:     共享过渡表 operation_log/email_template 留 identity 库,不在迁移范围
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
    --source)  SOURCE_DB="$2"; shift ;;
    --target)  TARGET_DB="$2"; shift ;;
    *) echo "[migrate] 未知参数: $1" >&2; exit 1 ;;
  esac
  shift
done

# 11 张迁移表(FK 不存在,顺序无关;按字母序列出保持输出稳定)
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

echo "[migrate] 预检 1/3:活跃写入者(非 Sleep 的 identity 连接)..."
ACTIVE="$(sql "SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB='${SOURCE_DB}' AND COMMAND NOT IN ('Sleep','Daemon','Binlog Dump') AND ID<>CONNECTION_ID();" | tail -1)"
if [ "${ACTIVE}" != "0" ]; then
  echo "[migrate] 错误:检测到 ${ACTIVE} 个活跃写入连接,请先停 backend/server 再迁移" >&2
  sql "SELECT ID,USER,COMMAND,TIME,INFO FROM information_schema.PROCESSLIST WHERE DB='${SOURCE_DB}' AND COMMAND NOT IN ('Sleep','Daemon','Binlog Dump');" >&2
  exit 1
fi

echo "[migrate] 预检 2/3:外键/触发器/存储过程断言(本项目 schema 约定为无)..."
FK="$(sql "SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE REFERENCED_TABLE_NAME IS NOT NULL AND TABLE_SCHEMA='${SOURCE_DB}';" | tail -1)"
TRG="$(sql "SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA='${SOURCE_DB}';" | tail -1)"
RTN="$(sql "SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA='${SOURCE_DB}';" | tail -1)"
if [ "${FK}" != "0" ] || [ "${TRG}" != "0" ] || [ "${RTN}" != "0" ]; then
  echo "[migrate] 错误:源库存在外键(${FK})/触发器(${TRG})/存储过程(${RTN}),与本脚本假设不符——请先人工处置后再迁移" >&2
  exit 1
fi

echo "[migrate] 预检 3/3:目标表非空检查..."
NONEMPTY=""
for t in "${TABLES[@]}"; do
  EXISTS="$(sql "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='${TARGET_DB}' AND TABLE_NAME='${t}';" | tail -1)"
  if [ "${EXISTS}" = "1" ]; then
    CNT="$(sql "SELECT COUNT(*) FROM \`${TARGET_DB}\`.\`${t}\`;" | tail -1)"
    if [ "${CNT}" != "0" ]; then NONEMPTY="${NONEMPTY} ${t}(${CNT})"; fi
  fi
done
if [ -n "${NONEMPTY}" ]; then
  if [ "${FORCE}" -ne 1 ]; then
    echo "[migrate] 错误:目标库已有数据的表:${NONEMPTY}" >&2
    echo "[migrate]   重放/重建场景加 --force(将 DROP 这些目标表后从源库重拷,源库不受影响)" >&2
    exit 1
  fi
  echo "[migrate] --force:DROP 目标表 ${NONEMPTY} 重建"
  for t in "${TABLES[@]}"; do
    sql "DROP TABLE IF EXISTS \`${TARGET_DB}\`.\`${t}\`;"
  done
fi

echo "[migrate] 开始逐表复制(单条 INSERT 语句原子性;任一失败即中止,无断点续传)..."
FAILED=0
for t in "${TABLES[@]}"; do
  echo "  → ${t}"
  sql "CREATE TABLE IF NOT EXISTS \`${TARGET_DB}\`.\`${t}\` LIKE \`${SOURCE_DB}\`.\`${t}\`;" || { echo "[migrate] 错误:建表 ${t} 失败" >&2; FAILED=1; break; }
  sql "INSERT INTO \`${TARGET_DB}\`.\`${t}\` SELECT * FROM \`${SOURCE_DB}\`.\`${t}\`;" || { echo "[migrate] 错误:复制 ${t} 失败(无断点续传,重跑需 --force)" >&2; FAILED=1; break; }
  # 自增计数修正:CREATE TABLE LIKE 不携带 AUTO_INCREMENT 当前值
  MAXID="$(sql "SELECT COALESCE(MAX(id),0) FROM \`${TARGET_DB}\`.\`${t}\`;" | tail -1)"
  sql "ALTER TABLE \`${TARGET_DB}\`.\`${t}\` AUTO_INCREMENT = $((MAXID + 1));" || { echo "[migrate] 错误:自增修正 ${t} 失败" >&2; FAILED=1; break; }
done
[ "${FAILED}" -eq 0 ] || exit 1

echo "[migrate] 校验:逐表 COUNT + 全列 checksum(NULL→哨兵串,确定性拼接)..."
FAIL=0
printf "%-18s %10s %12s %12s\n" "表" "源行数" "目标行数" "checksum"
printf "%-18s %10s %12s %12s\n" "----" "------" "--------" "--------"
for t in "${TABLES[@]}"; do
  SC="$(sql "SELECT COUNT(*) FROM \`${SOURCE_DB}\`.\`${t}\`;" | tail -1)"
  TC="$(sql "SELECT COUNT(*) FROM \`${TARGET_DB}\`.\`${t}\`;" | tail -1)"
  # 动态列清单 → IFNULL(列,'∅') 拼接 → CRC32 → BIT_XOR(对行序不敏感的全表指纹)
  COLS="$(sql "SELECT GROUP_CONCAT(CONCAT('IFNULL(\`', COLUMN_NAME, '\`, ''∅'')') ORDER BY ORDINAL_POSITION SEPARATOR ',') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='${SOURCE_DB}' AND TABLE_NAME='${t}';" | tail -1)"
  SUM_EXPR="BIT_XOR(CRC32(CONCAT_WS('#', ${COLS})))"
  SS="$(sql "SELECT COALESCE(${SUM_EXPR},0) FROM \`${SOURCE_DB}\`.\`${t}\`;" | tail -1)"
  TS="$(sql "SELECT COALESCE(${SUM_EXPR},0) FROM \`${TARGET_DB}\`.\`${t}\`;" | tail -1)"
  printf "%-18s %10s %12s %12s\n" "${t}" "${SC}" "${TC}" "${SS}"
  if [ "${SC}" != "${TC}" ] || [ "${SS}" != "${TS}" ]; then
    echo "[migrate] 校验失败:${t} 行数(${SC}/${TC})或 checksum(${SS}/${TS})不一致" >&2
    FAIL=1
  fi
done
[ "${FAIL}" -eq 0 ] || { echo "[migrate] 迁移校验未通过——目标表保留供排查,重跑需 --force 重建" >&2; exit 1; }

echo "[migrate] 完成:${#TABLES[@]} 张表已复制至 ${TARGET_DB} 并通过双重校验(源库未做任何写操作)"
echo "[migrate] 回滚方式:DROP ${TARGET_DB} 中上述表 + 重启旧 Java 链路"
