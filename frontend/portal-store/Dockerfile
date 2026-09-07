# portal-store 完整多阶段构建:Next.js standalone 产物内含平台相关 SWC 二进制,
# 必须在目标平台环境内构建 —— release.sh 统一以 docker buildx --platform linux/amd64 构建。
# (不用 # syntax 指令:标准语法足够,且避免 BuildKit 拉取 frontend 镜像的网络依赖)
FROM node:22-alpine AS builder
WORKDIR /app

# corepack 按 package.json 的 packageManager 字段(pnpm@10.32.1)激活
RUN corepack enable

# 依赖层缓存:仅 lock 文件变化时才重装
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .

# 构建期内联的公开变量(改值需重新构建镜像)
ARG NEXT_PUBLIC_SITE_URL=""
ARG NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY=""
ARG NEXT_PUBLIC_GA4_ID=""
# CSP frame-ancestors 的管理端地址(headers() 在 build 时固化,需随构建注入)
ARG ADMIN_ORIGIN=""
ENV NEXT_PUBLIC_SITE_URL=$NEXT_PUBLIC_SITE_URL \
    NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY=$NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY \
    NEXT_PUBLIC_GA4_ID=$NEXT_PUBLIC_GA4_ID \
    ADMIN_ORIGIN=$ADMIN_ORIGIN \
    NEXT_TELEMETRY_DISABLED=1

RUN pnpm build

FROM node:22-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    HOSTNAME=0.0.0.0 \
    PORT=3000

# standalone 产物不含 .next/static 与 public,必须手动补齐
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
COPY --from=builder /app/public ./public

EXPOSE 3000

HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=5 \
  CMD wget -q -O /dev/null http://127.0.0.1:3000/ || exit 1

CMD ["node", "server.js"]
