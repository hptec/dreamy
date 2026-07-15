import { fileURLToPath } from 'node:url'
import { dirname } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))

/** @type {import('next').NextConfig} */
const nextConfig = {
  // 决策 22：去 output:'export' 静态导出 → Node standalone 运行时
  // 开发阶段页面与接口统一实时读取，不启用 ISR/revalidatePath 发布链。
  output: 'standalone',
  // 锁定 workspace root 为本工程目录：根目录存在空壳 pnpm-lock.yaml 会令 Next 误判 root，
  // 导致 dev 下 public/ 静态资源定位错误（competitor-refs 图片 404）。
  outputFileTracingRoot: __dirname,
  reactStrictMode: true,
  // MF-L4S-002：隐藏 X-Powered-By，减少指纹暴露
  poweredByHeader: false,
  images: {
    // 商品/内容图为 API 下发的绝对 URL，页面统一使用 <img>（unoptimized 保持既有行为）
    unoptimized: true
  },
  // MF-L4S-002：HTTP 安全响应头基线（全路由）。
  // CSP connect-src 中的 http://localhost:18081 为 dev 后端域名，上线时由环境变量/部署层替换为生产 API 域名。
  async headers() {
    // dev 下 Next.js（react-refresh/eval-source-map）依赖 'unsafe-eval'，生产不放行
    const devScript = process.env.NODE_ENV !== 'production' ? " 'unsafe-eval'" : ''
    const adminOrigin = process.env.ADMIN_ORIGIN || process.env.NEXT_PUBLIC_ADMIN_ORIGIN || 'http://localhost:5174'
    const csp = [
      "default-src 'self'",
      `script-src 'self' 'unsafe-inline'${devScript} https://www.googletagmanager.com https://js.stripe.com`,
      "connect-src 'self' http://localhost:18081 https://www.google-analytics.com https://*.google-analytics.com https://api.stripe.com",
      "frame-src https://js.stripe.com",
      "img-src 'self' data: blob: https: http:",
      "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
      "font-src 'self' data: https://fonts.gstatic.com",
      `frame-ancestors 'self' ${adminOrigin}`
    ].join('; ')
    return [
      {
        source: '/(.*)',
        headers: [
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
          { key: 'Permissions-Policy', value: 'camera=(), microphone=(), geolocation=()' },
          { key: 'Content-Security-Policy', value: csp }
        ]
      }
    ]
  }
}

export default nextConfig
