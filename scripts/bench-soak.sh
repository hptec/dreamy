#!/usr/bin/env bash
# =============================================================
# 脚本名称: bench-soak.sh(遗留任务 3:内存泄漏 soak 证据)
# 流程:50 并发混合流量 30 分钟(soak.rs 驱动)+ 每 30s 采样
#       server 容器 RSS/CPU、Redis 内存、MySQL 连接水位 → data/soak-samples.csv
#       结束后生成 data/soak-evidence.md(采样表 + 曲线图 + 结论)
#                data/soak-evidence.html(自包含内联 SVG,可打印 PDF)
#                data/charts/soak-{rss,cpu,db}.svg(独立曲线图)
# 使用方式: bash scripts/bench-soak.sh
# =============================================================
set -euo pipefail
cd "$(dirname "$0")/.."

ENV_FILE=".env.deploy"
set -a; source "${ENV_FILE}"; set +a
DB_PASSWORD_LINE=$(grep -o 'MYSQL_ROOT_PASSWORD=.*' "${ENV_FILE}" | cut -d= -f2)

OUT_DIR="data"
SAMPLES="${OUT_DIR}/soak-samples.csv"
EVIDENCE="${OUT_DIR}/soak-evidence.md"
SOAK_SECS="${SOAK_SECS:-1800}"
INTERVAL=30
mkdir -p "${OUT_DIR}"

echo "epoch,rss_mb,cpu_pct,redis_used_mb,mysql_threads_running,mysql_connections" > "${SAMPLES}"

# 后台启动 soak 流量驱动(直连 server 宿主映射口 18082,与压测同口径)
cd server
DB_PASSWORD="${DB_PASSWORD_LINE}" SERVER_DB_HOST=127.0.0.1 SERVER_DB_PORT=3307 \
  IDENTITY_SOAK=1 SERVER_LOAD_ADDR="http://127.0.0.1:18082" \
  cargo test -p identity --test soak --release -- --nocapture &
SOAK_PID=$!
cd ..

SAMPLE_N=0
echo "[soak] 采样开始(每 ${INTERVAL}s,共 ${SOAK_SECS}s)…"
for _ in $(seq 1 $(( SOAK_SECS / INTERVAL ))); do
  sleep "${INTERVAL}"
  STATS=$(docker stats --no-stream --format '{{.MemUsage}}|{{.CPUPerc}}' dreamy-server-1 2>/dev/null | head -1)
  RSS_MB=$(echo "${STATS}" | awk -F'|' '{gsub(/[[:space:]]/,"",$1); split($1,a,"/"); u=a[1]; if (u ~ /GiB/) {gsub(/GiB/,"",u); printf "%.0f", u*1024} else {gsub(/MiB/,"",u); printf "%.0f", u}}')
  CPU=$(echo "${STATS}" | awk -F'|' '{gsub(/%/,"",$2); print $2}')
  REDIS_MB=$(docker compose --env-file "${ENV_FILE}" exec -T redis redis-cli info memory 2>/dev/null \
    | awk -F: '/used_memory:/ {printf "%.0f", $2/1048576}')
  MYSQL_ST=$(docker compose --env-file "${ENV_FILE}" exec -T -e Q="SHOW GLOBAL STATUS WHERE Variable_name IN ('Threads_running','Threads_connected');" mysql sh -c \
    'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "$Q"' 2>/dev/null | awk -F'\t' '{print $2}' | paste -sd, -)
  echo "$(date +%s),${RSS_MB:-0},${CPU:-0},${REDIS_MB:-0},${MYSQL_ST:-0,0}" >> "${SAMPLES}"
  SAMPLE_N=$((SAMPLE_N+1))
  echo "[soak] 采样 ${SAMPLE_N}: RSS=${RSS_MB}MB CPU=${CPU}% Redis=${REDIS_MB}MB"
done

wait ${SOAK_PID} 2>/dev/null || true

# 证据报告:采样表 + 曲线图 + 收敛判定(判定逻辑见 scripts/soak-chart.py)
# 口径:剔除末尾 cpu=0 的空载采样点后统计(压测驱动退出后 RSS 回落会虚高 PASS)
python3 scripts/soak-chart.py \
  --csv "${SAMPLES}" \
  --md "${EVIDENCE}" \
  --html "${OUT_DIR}/soak-evidence.html" \
  --svg-dir "${OUT_DIR}/charts"
