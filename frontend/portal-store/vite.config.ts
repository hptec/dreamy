import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// portal-store（端口 5173）。dev 代理 /api → backend:18081，避免本地 CORS 摩擦；
// 生产构建仍走 VITE_API_BASE_URL（默认相对路径，由反代统一前缀 /api）。
// MF-L4S-002：dev/preview 本地下发安全响应头；生产托管层（反代/CDN）需同步配置同等响应头。
const securityHeaders = {
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'DENY'
}

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    port: 5173,
    host: 'localhost',
    hmr: {
      host: 'localhost'
    },
    headers: securityHeaders,
    proxy: {
      '/api': {
        target: 'http://localhost:18081',
        changeOrigin: true
      }
    }
  },
  preview: {
    headers: securityHeaders
  }
})
