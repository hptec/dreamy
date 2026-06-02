'use client'

/**
 * authStore（STORE-S01）：{ tokens, user, isAuthenticated, login(), logout(), refresh() }。
 * - login(): verifyOtp/oidc 成功后存 token + user。
 * - logout(): 撤销当前会话 + 清 token。
 * - refresh(): 用 refresh token 续期（FLOW-04）；失败 → 跳登录由调用方处理。
 * - hydrate(): 应用启动用 refresh 续期并拉取 profile，重建会话。
 */

import { create } from 'zustand'
import type { LoginResponse, TokenPair, UserProfile } from '../api/types'
import { getProfile } from '../api/auth-api'
import { ApiError, refreshTokens } from '../api/client'
import {
  clearTokens,
  getRefreshToken,
  hydrateFromStorage,
  saveTokens
} from '../api/token-store'

type AuthStatus = 'idle' | 'loading' | 'authenticated' | 'unauthenticated'

interface AuthState {
  user: UserProfile | null
  isAuthenticated: boolean
  status: AuthStatus
  hydrated: boolean
  login: (result: LoginResponse) => void
  setUser: (user: UserProfile) => void
  logout: () => void
  refresh: () => Promise<TokenPair>
  hydrate: () => Promise<void>
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  isAuthenticated: false,
  status: 'idle',
  hydrated: false,

  login: (result: LoginResponse) => {
    saveTokens(result.tokens)
    set({ user: result.user, isAuthenticated: true, status: 'authenticated' })
  },

  setUser: (user: UserProfile) => set({ user }),

  logout: () => {
    clearTokens()
    set({ user: null, isAuthenticated: false, status: 'unauthenticated' })
  },

  refresh: async () => {
    const tokens = await refreshTokens()
    set({ isAuthenticated: true })
    return tokens
  },

  // 启动恢复：内存态 access 丢失时用持久化 refresh 续期 + 拉 profile
  hydrate: async () => {
    if (get().hydrated) return
    hydrateFromStorage()
    if (!getRefreshToken()) {
      set({ hydrated: true, status: 'unauthenticated' })
      return
    }
    set({ status: 'loading' })
    try {
      await refreshTokens()
      const user = await getProfile()
      set({ user, isAuthenticated: true, status: 'authenticated', hydrated: true })
    } catch (err) {
      clearTokens()
      set({
        user: null,
        isAuthenticated: false,
        status: 'unauthenticated',
        hydrated: true
      })
      if (!(err instanceof ApiError)) {
        // 网络/未知错误同样落到未认证态，由页面引导重新登录
      }
    }
  }
}))
