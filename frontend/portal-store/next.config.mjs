/** @type {import('next').NextConfig} */
const nextConfig = {
  // 决策 22：去 output:'export' 静态导出 → Node standalone 运行时
  // （ISR/revalidatePath 失效链可用；docker 容器部署 + CDN 前置，页面 Cache-Control: s-maxage 由 Next ISR 默认头承载）
  output: 'standalone',
  reactStrictMode: true,
  // MF-L4S-002：隐藏 X-Powered-By，减少指纹暴露
  poweredByHeader: false,
  images: {
    // 商品/内容图为 API 下发的绝对 URL，页面统一使用 <img>（unoptimized 保持既有行为）
    unoptimized: true
  },
  // MF-L4S-002：HTTP 安全响应头基线（全路由）。
  // CSP connect-src 中的 http://localhost:8080 为 dev 后端域名，上线时由环境变量/部署层替换为生产 API 域名。
  async headers() {
    // dev 下 Next.js（react-refresh/eval-source-map）依赖 'unsafe-eval'，生产不放行
    const devScript = process.env.NODE_ENV !== 'production' ? " 'unsafe-eval'" : ''
    const csp = [
      "default-src 'self'",
      `script-src 'self' 'unsafe-inline'${devScript} https://www.googletagmanager.com https://js.stripe.com`,
      "connect-src 'self' http://localhost:8080 https://www.google-analytics.com https://*.google-analytics.com https://api.stripe.com",
      "frame-src https://js.stripe.com",
      "img-src 'self' data: blob: https: http:",
      "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
      "font-src 'self' data: https://fonts.gstatic.com"
    ].join('; ')
    return [
      {
        source: '/(.*)',
        headers: [
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'X-Frame-Options', value: 'DENY' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
          { key: 'Permissions-Policy', value: 'camera=(), microphone=(), geolocation=()' },
          { key: 'Content-Security-Policy', value: csp }
        ]
      }
    ]
  }
}

export default nextConfig
