#!/usr/bin/env bash
# =============================================================
# 脚本名称: pull-base-images.sh
# 功能描述: 通过 daocloud 镜像站预拉全部基础镜像并 retag 为官方名
#           (daemon.json 的 registry-mirrors 对部分层回源无效,Docker Hub 直连极慢;
#            预拉后 buildx/compose 均命中本地缓存,多架构 manifest 同时覆盖本地验证与 amd64 发布)
# 使用方式: bash scripts/pull-base-images.sh
# 依赖环境: Docker Desktop
# =============================================================
set -euo pipefail

MIRROR="docker.m.daocloud.io"

# 官方 library 命名空间的镜像(nginx/mysql/redis/node 需 library/ 前缀,eclipse-temurin 不需要)
images=(
  "eclipse-temurin:25-jre"
  "library/node:22-alpine"
  "library/nginx:1.27-alpine"
  "library/mysql:8.4"
  "library/redis:7-alpine"
)

for img in "${images[@]}"; do
  name="${img#library/}"
  echo "[pull-base] 拉取 ${MIRROR}/${img} ..."
  if docker pull "${MIRROR}/${img}" 2>/dev/null; then
    docker tag "${MIRROR}/${img}" "${name}"
    echo "[pull-base] ${name} 就绪(多架构 manifest,本地验证与 amd64 发布共用)"
  else
    echo "[pull-base] 警告: ${img} 拉取失败(可能被限流),buildx 构建时将回退直连" >&2
  fi
done

echo "[pull-base] 完成。"
