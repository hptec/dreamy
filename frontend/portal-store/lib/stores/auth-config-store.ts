'use client'

/**
 * authConfigStore（STORE-S02）：getStoreAuthConfig 登录方式开关。
 * 控制 Google/Apple 按钮显隐（FUNC-003/006）。
 * 无客户端缓存：每次 load() 都请求后端，确保 admin 改配置后用户刷新登录页立即生效。
 *
 * 为什么不缓存：authConfig 是 admin 可热更新的运营配置，前端缓存会导致 admin 关闭 Google/Apple
 * 后已打开的登录页继续显示旧按钮（见 oauth-buttons.tsx 的 showGoogle/showApple 逻辑）。
 * 接口本身有后端 JetCache（600s）兜底，每次请求开销极小。
 *
 * 不做降级 fallback：拉取失败时 config 保持 null，登录页按 fail-closed 渲染
 * （所有登录入口隐藏 + 「登录方式暂不可用」提示），让故障可见，而不是静默假装 email 开启。
 */

import { create } from 'zustand'
import type { StoreAuthConfig } from '../api/types'
import { getStoreAuthConfig } from '../api/auth-api'

interface AuthConfigState {
  config: StoreAuthConfig | null
  loadError: boolean
  load: () => Promise<void>
}

export const useAuthConfigStore = create<AuthConfigState>((set) => ({
  config: null,
  loadError: false,

  load: async () => {
    try {
      const config = await getStoreAuthConfig()
      set({ config, loadError: false })
    } catch {
      // 拉取失败：不降级、不装作可用。config 留 null → 登录页 fail-closed 提示。
      set({ config: null, loadError: true })
    }
  }
}))
