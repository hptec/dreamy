'use client'

/**
 * authConfigStore（STORE-S02）：缓存 getStoreAuthConfig 登录方式开关。
 * 控制 Google/Apple 按钮显隐（FUNC-003/006）；缺省全开，加载失败时降级为仅 email（DG-001 思路）。
 */

import { create } from 'zustand'
import type { StoreAuthConfig } from '../api/types'
import { getStoreAuthConfig } from '../api/auth-api'

interface AuthConfigState {
  config: StoreAuthConfig | null
  loading: boolean
  loaded: boolean
  load: () => Promise<void>
}

const FALLBACK: StoreAuthConfig = {
  emailEnabled: true,
  googleEnabled: false,
  appleEnabled: false,
  otpLength: 6
}

export const useAuthConfigStore = create<AuthConfigState>((set, get) => ({
  config: null,
  loading: false,
  loaded: false,

  load: async () => {
    if (get().loading || get().loaded) return
    set({ loading: true })
    try {
      const config = await getStoreAuthConfig()
      set({ config, loading: false, loaded: true })
    } catch {
      // 配置拉取失败：降级为仅 email 登录（隐藏 OAuth 按钮），不阻断登录页
      set({ config: FALLBACK, loading: false, loaded: true })
    }
  }
}))
