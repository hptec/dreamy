// Vitest 最小配置（工程原无测试基建——本变更建立；测试位于 tests/，不进 vue-tsc 构建图）
// plugin-vue：启用 SFC 编译以支持 SSR 字符串渲染断言（TC-ALIGN-032，零新依赖——复用构建链已有 devDependency）
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  test: {
    environment: 'node',
    include: ['tests/**/*.spec.ts'],
  },
})
