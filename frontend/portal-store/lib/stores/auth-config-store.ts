'use client'

/**
 * authConfigStore（STORE-S02）：getStoreAuthConfig 登录方式开关。
 * 控制 Google/Apple 按钮显隐（FUNC-003/006）。
 * 无客户端缓存：每次 load() 都请求后端，确保 admin 改配置后用户刷新登录页立即生效。
 * 加载失败时降级为仅 email（DG-001 思路）。
 *
 * 为什么不缓存：authConfig 是 admin 可热更新的运营配置，前端缓存会导致 admin 关闭 Google/Apple
 * 后已打开的登录页继续显示旧按钮（见 oauth-buttons.tsx 的 showGoogle/showApple 逻辑）。
 * 接口本身有后端 JetCache（600s）兜底，每次请求开销极小。
 */

import { create } from 'zustand'
import type { StoreAuthConfig } from '../api/types'
import { getStoreAuthConfig } from '../api/auth-api'

interface AuthConfigState {
  config: StoreAuthConfig | null
  load: () => Promise<void>
}

const FALLBACK: StoreAuthConfig = {
  emailEnabled: true,
  googleEnabled: false,
  appleEnabled: false,
  otpLength: 6
}

export const useAuthConfigStore = create<AuthConfigState>((set) => ({
  config: null,

  load: async () => {
    try {
      const config = await getStoreAuthConfig()
      set({ config })
    } catch {
      // 配置拉取失败：降级为仅 email 登录（隐藏 OAuth 按钮），不阻断登录页
      set({ config: FALLBACK })
    }
  }
}))
