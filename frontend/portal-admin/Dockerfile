# portal-admin 运行镜像:dist 已由 scripts/release.sh 在本地构建(纯静态产物跨架构)。
# release.sh 以 docker buildx --platform linux/amd64 构建本文件。
FROM nginx:1.27-alpine

COPY dist/ /usr/share/nginx/html/
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
  CMD wget -q -O /dev/null http://127.0.0.1:80/ || exit 1
