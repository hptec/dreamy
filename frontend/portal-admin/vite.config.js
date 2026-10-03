import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';
// portal-admin（端口 5174，中文）。dev 代理 /api → Rust server:18082，避免本地 CORS 摩擦；
// （Java backend 18081 已于 Phase B 退役，全部 /api 由 Rust 承载）
// 生产构建仍走 VITE_API_BASE_URL（默认相对路径，由反代统一前缀 /api）。
// MF-L4S-002：dev/preview 本地下发安全响应头；生产托管层（反代/CDN）需同步配置同等响应头。
// 注意：本文件为 vite.config.ts 的编译产物且解析优先级更高，两者需保持同步。
const securityHeaders = {
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'DENY'
};
export default defineConfig({
    plugins: [vue()],
    // 单端口网关部署:/admin/ 子路径托管(生产构建经 release.sh 注入 ADMIN_BASE=/admin/;dev 不设走根路径)
    base: process.env.ADMIN_BASE ?? '/',
    resolve: {
        alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
    },
    server: {
        port: 5174,
        headers: securityHeaders,
        // dev 代理分流:全部 /api → Rust server(18082)。
        // 身份域六条显式规则保留(历史可读性),其余 /api 兜底同指 18082——18081 已随 Java 退役失效。
        // 对象键按序匹配,长前缀在前,兜底 '/api' 殿后。
        proxy: {
            '/api/admin/auth': {
                target: 'http://localhost:18082',
                changeOrigin: true
            },
            '/api/admin/admins': {
                target: 'http://localhost:18082',
                changeOrigin: true
            },
            '/api/admin/roles': {
                target: 'http://localhost:18082',
                changeOrigin: true
            },
            '/api/admin/permissions': {
                target: 'http://localhost:18082',
                changeOrigin: true
            },
            '/api/admin/users': {
                target: 'http://localhost:18082',
                changeOrigin: true
            },
            '/api/admin/operation-logs': {
                target: 'http://localhost:18082',
                changeOrigin: true
            },
            '/api': {
                target: 'http://localhost:18082',
                changeOrigin: true
            }
        }
    },
    preview: {
        headers: securityHeaders
    }
});
