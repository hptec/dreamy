import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// portal-admin（端口 5174，中文）。dev 代理 /api → backend:8080，避免本地 CORS 摩擦；
// 生产构建仍走 VITE_API_BASE_URL（默认相对路径，由反代统一前缀 /api）。
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    port: 5174,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
