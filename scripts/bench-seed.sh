#!/usr/bin/env bash
# =============================================================
# 脚本名称: bench-seed.sh
# 功能描述: login 专项压测灌库(遗留任务 2 修正规模,2026-09-19):
#           user 1000 万(跨 p0/p1/p2 三段) + identity_email 1000 万(KEY 25 区)
#           + login_history 5000 万(跨 2026-04~09 六个月分区) + otp_code 100 万(当月)。
#           翻倍法 = 显式 id 平移(email 用目标 id 保证全局唯一);单事务分片 200 万行。
#           幂等守卫:已达标即跳过对应阶段。
# 使用方式: bash scripts/bench-seed.sh --confirm
# 依赖环境: docker compose mysql 容器 healthy;.env.deploy 注入 MYSQL_ROOT_PASSWORD
# =============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

ENV_FILE=".env.deploy"
[ -f "${ENV_FILE}" ] || { echo "[seed] 缺少 ${ENV_FILE}" >&2; exit 1; }
set -a; source "${ENV_FILE}"; set +a

if [ "${1:-}" != "--confirm" ]; then
  echo "[seed] 灌库将向 dreamy_server 主库写入亿级行,确认请加 --confirm" >&2
  exit 1
fi

TARGET_USERS="${TARGET_USERS:-10000000}"
TARGET_LH="${TARGET_LH:-50000000}"
TARGET_OTP="${TARGET_OTP:-1000000}"
CHUNK=2000000

msql() { docker compose --env-file "${ENV_FILE}" exec -T -e MQ="$1" mysql sh -c \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" dreamy_server -N -e "$MQ"' 2>/dev/null; }

sql() { docker compose --env-file "${ENV_FILE}" exec -T -e MQ="$1" mysql sh -c \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" dreamy_server -e "$MQ"' 2>/dev/null; }

echo "[seed] STEP-1 login_history 历史月分区 p202604~p202608(REORGANIZE,幂等)"
sql "ALTER TABLE login_history REORGANIZE PARTITION p202609 INTO (
  PARTITION p202604 VALUES LESS THAN ('2026-05-01'),
  PARTITION p202605 VALUES LESS THAN ('2026-06-01'),
  PARTITION p202606 VALUES LESS THAN ('2026-07-01'),
  PARTITION p202607 VALUES LESS THAN ('2026-08-01'),
  PARTITION p202608 VALUES LESS THAN ('2026-09-01'),
  PARTITION p202609 VALUES LESS THAN ('2026-10-01'));" \
  || echo "[seed] 分区已存在,跳过"

echo "[seed] STEP-2 user → ${TARGET_USERS}(显式 id 平移翻倍)"
while [ "$(msql "SELECT COALESCE(MAX(id),0) FROM user;")" -lt "${TARGET_USERS}" ]; do
  MAX=$(msql "SELECT COALESCE(MAX(id),0) FROM user;")
  OFFSET=${MAX}
  REMAIN=$(( TARGET_USERS - MAX ))
  # 本轮源行 [1, min(MAX, REMAIN)],分片写入
  SRC=$(( MAX < REMAIN ? MAX : REMAIN ))
  ADDED=0
  S=0
  while [ "${S}" -lt "${SRC}" ]; do
    E=$(( S + CHUNK ))
    [ "${E}" -gt "${SRC}" ] && E=${SRC}
    sql "INSERT INTO user (id,email,email_verified,tier,status,anonymized,version,joined_at)
      SELECT id + ${OFFSET}, CONCAT('load', id + ${OFFSET}, '@load.test'), 1, 1, 1, 0, 0,
             DATE(NOW()) - INTERVAL FLOOR(RAND()*90) DAY
      FROM user WHERE id > ${S} AND id <= ${E};"
    S=${E}
  done
  NOW=$(msql "SELECT COALESCE(MAX(id),0) FROM user;")
  echo "[seed] user → ${NOW}"
done

echo "[seed] STEP-3 identity_email → ${TARGET_USERS}(分批 200 万,IGNORE 防重)"
S=27
while [ "${S}" -le "${TARGET_USERS}" ]; do
  E=$(( S + CHUNK - 1 ))
  [ "${E}" -gt "${TARGET_USERS}" ] && E=${TARGET_USERS}
  CNT=$(msql "SELECT COUNT(*) FROM identity_email WHERE user_id >= ${S} AND user_id <= ${E} AND email LIKE 'load%@load.test';")
  if [ "${CNT:-0}" -lt $(( E - S + 1 )) ]; then
    sql "INSERT IGNORE INTO identity_email (email,user_id)
      SELECT CONCAT('load', id, '@load.test'), id FROM user WHERE id >= ${S} AND id <= ${E};"
  fi
  echo "[seed] identity_email → ≤${E}"
  S=$(( E + 1 ))
done

echo "[seed] STEP-4 login_history → ${TARGET_LH}(跨 2026-04~09 月分区)"
LH_MAX=$(msql "SELECT COALESCE(MAX(id),0) FROM login_history;")
if [ "${LH_MAX}" -lt 1000 ]; then
  sql "INSERT INTO login_history (user_id,email,method,ip,device,result,is_new_device,notified,created_at)
    SELECT id, CONCAT('load', id, '@load.test'), 1, '203.0.113.7', 'Mozilla/5.0 bench', 1, 0, 0,
           TIMESTAMP('2026-04-01') + INTERVAL FLOOR(RAND()*183*24) HOUR
    FROM user WHERE id <= 1000;"
  echo "[seed] login_history 种子 1000 行"
fi
while [ "$(msql "SELECT COALESCE(MAX(id),0) FROM login_history;")" -lt "${TARGET_LH}" ]; do
  MAX=$(msql "SELECT COALESCE(MAX(id),0) FROM login_history;")
  OFFSET=${MAX}
  REMAIN=$(( TARGET_LH - MAX ))
  SRC=$(( MAX < REMAIN ? MAX : REMAIN ))
  S=0
  while [ "${S}" -lt "${SRC}" ]; do
    E=$(( S + CHUNK ))
    [ "${E}" -gt "${SRC}" ] && E=${SRC}
    sql "INSERT INTO login_history (id,user_id,email,method,ip,device,result,is_new_device,notified,created_at)
      SELECT id + ${OFFSET}, 1 + (id % ${TARGET_USERS}), CONCAT('load', 1 + (id % ${TARGET_USERS}), '@load.test'),
             1, '203.0.113.7', 'Mozilla/5.0 bench', 1, 0, 0,
             TIMESTAMP('2026-04-01') + INTERVAL FLOOR(RAND()*183*24) HOUR
      FROM login_history WHERE id > ${S} AND id <= ${E};"
    S=${E}
  done
  NOW=$(msql "SELECT COALESCE(MAX(id),0) FROM login_history;")
  echo "[seed] login_history → ${NOW}"
done

echo "[seed] STEP-5 otp_code → ${TARGET_OTP}(当月,Expired 态不污染 pending 路径)"
OTP_MAX=$(msql "SELECT COALESCE(MAX(id),0) FROM otp_code;")
if [ "${OTP_MAX}" -lt 1000 ]; then
  sql "INSERT INTO otp_code (email,code_hash,length,expires_at,attempts,max_attempts,status,last_sent_at,version,created_at,updated_at)
    SELECT CONCAT('load', id, '@load.test'),
           '\$2b\$12\$bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', 6,
           NOW() - INTERVAL 1 HOUR, 5, 5, 3, NOW() - INTERVAL 2 HOUR, 0,
           NOW() - INTERVAL 2 HOUR, NOW() - INTERVAL 2 HOUR
    FROM user WHERE id <= 1000;"
  echo "[seed] otp_code 种子 1000 行"
fi
while [ "$(msql "SELECT COALESCE(MAX(id),0) FROM otp_code;")" -lt "${TARGET_OTP}" ]; do
  MAX=$(msql "SELECT COALESCE(MAX(id),0) FROM otp_code;")
  OFFSET=${MAX}
  REMAIN=$(( TARGET_OTP - MAX ))
  SRC=$(( MAX < REMAIN ? MAX : REMAIN ))
  S=0
  while [ "${S}" -lt "${SRC}" ]; do
    E=$(( S + CHUNK ))
    [ "${E}" -gt "${SRC}" ] && E=${SRC}
    sql "INSERT INTO otp_code (id,email,code_hash,length,expires_at,attempts,max_attempts,status,last_sent_at,version,created_at,updated_at)
      SELECT id + ${OFFSET}, CONCAT('load', 1 + (id % ${TARGET_USERS}), '@load.test'),
             '\$2b\$12\$bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', 6,
             NOW() - INTERVAL 1 HOUR, 5, 5, 3, NOW() - INTERVAL 2 HOUR, 0,
             NOW() - INTERVAL 2 HOUR, NOW() - INTERVAL 2 HOUR
      FROM otp_code WHERE id > ${S} AND id <= ${E};"
    S=${E}
  done
  NOW=$(msql "SELECT COALESCE(MAX(id),0) FROM otp_code;")
  echo "[seed] otp_code → ${NOW}"
done

echo "[seed] 完成:user $(msql 'SELECT COUNT(*) FROM user;') / email $(msql "SELECT COUNT(*) FROM identity_email WHERE email LIKE 'load%@load.test';") / login_history $(msql 'SELECT COUNT(*) FROM login_history;') / otp_code $(msql 'SELECT COUNT(*) FROM otp_code;')"
