'use client'

/**
 * showroomListStore（STORE-SHR-S01）+ showroomDetailStore（STORE-SHR-S02）。
 * 列表：我的 Showroom 摘要（创建 unshift / 删除剔除）；详情：双态身份（owner/guest）+
 * 投票乐观覆盖 / 留言 push / 指派与提醒行覆盖 / 邀请重置。
 * 婚期带入结算 selector：getDefaultWeddingDate（FORM-SHR-S10，交叉引用 trading COMP-TRD-S02）。
 */

import { create } from 'zustand'
import * as showroomApi from '../api/showroom-api'
import { ApiError } from '../api/client'
import type { ShowroomDetail, ShowroomSummary } from '../api/store-types'
import {
  clearGuestSession,
  isGuestSessionExpired,
  loadGuestSession,
  rejoinGuestSession,
  saveGuestSession,
  type StoredGuestSession
} from './guest-session-store'
import { useAuthStore } from './auth-store'

// ===== 列表 =====

interface ShowroomListState {
  items: ShowroomSummary[]
  loading: boolean
  fetched: boolean
  fetch: () => Promise<void>
  create: (name: string, weddingDate?: string) => Promise<ShowroomDetail>
  remove: (id: number) => Promise<void>
}

export const useShowroomListStore = create<ShowroomListState>((set, get) => ({
  items: [],
  loading: false,
  fetched: false,

  fetch: async () => {
    set({ loading: true })
    try {
      const items = await showroomApi.listShowrooms()
      set({ items, fetched: true })
    } finally {
      set({ loading: false })
    }
  },

  create: async (name, weddingDate) => {
    const room = await showroomApi.createShowroom({ name, weddingDate })
    set({
      items: [
        { id: room.id, ownerId: room.ownerId, name: room.name, weddingDate: room.weddingDate, itemCount: 0, memberCount: room.memberCount ?? 0 },
        ...get().items
      ]
    })
    return room
  },

  remove: async (id) => {
    await showroomApi.deleteShowroom(id)
    set({ items: get().items.filter((s) => s.id !== id) })
  }
}))

/** 结算 wedding date 自动带入（F-077/决策 20.6）：最新创建房的婚期，无房/无婚期 → undefined */
export async function getDefaultWeddingDate(): Promise<string | undefined> {
  if (!useAuthStore.getState().isAuthenticated) return undefined
  try {
    const items = await showroomApi.listShowrooms()
    return items.find((s) => !!s.weddingDate)?.weddingDate
  } catch {
    return undefined
  }
}

// ===== 详情（双态守卫，PAGE-SHR-S02 三态判定） =====

export type ShowroomGateState =
  | 'loading'
  | 'ready'          // 已获详情（owner 或 guest）
  | 'join'           // 未登录 + URL 带 invite → 昵称加入态
  | 'login-required' // 未登录无 invite 无会话 → 跳登录
  | 'not-found'      // 404101 通用「不存在或无权访问」
  | 'invite-revoked' // 410101 邀请失效
  | 'guest-expired'  // 401101 guest 凭证失效（提示重开邀请链接）
  | 'error'

interface ShowroomDetailState {
  room: ShowroomDetail | null
  identity: 'owner' | 'guest'
  gate: ShowroomGateState
  guestSession: StoredGuestSession | null
  errorCode: number | null
  /** 三态启动序（PAGE-SHR-S02）：authed/guest 缓存/invite 逐级判定 */
  resolve: (id: number, inviteToken: string | null) => Promise<void>
  join: (id: number, inviteToken: string, nickname: string) => Promise<void>
  refetch: (id: number) => Promise<void>
  vote: (itemId: number, vote: 'like' | 'dislike') => Promise<void>
  comment: (itemId: number, content: string) => Promise<void>
  addItem: (productId: number, color?: string) => Promise<void>
  removeItem: (itemId: number) => Promise<void>
  assign: (memberId: number, assignedItemId: number, email?: string) => Promise<void>
  remind: (memberId: number) => Promise<void>
  updateProfile: (name: string, weddingDate?: string) => Promise<void>
  resetInvite: () => Promise<string>
}

export const useShowroomDetailStore = create<ShowroomDetailState>((set, get) => ({
  room: null,
  identity: 'owner',
  gate: 'loading',
  guestSession: null,
  errorCode: null,

  resolve: async (id, inviteToken) => {
    set({ gate: 'loading', errorCode: null })
    const authed = useAuthStore.getState().isAuthenticated

    // ① 已登录 → 先按 owner/成员身份读
    if (authed) {
      try {
        const room = await showroomApi.getShowroom(id)
        set({ room, identity: room.isOwner ? 'owner' : 'guest', gate: 'ready', guestSession: null })
        return
      } catch (err) {
        const code = err instanceof ApiError ? err.code : null
        if ((code === 404101 || code === 403102) && inviteToken) {
          // 本人非 owner 的受邀者 → 转 guest 加入流
          set({ gate: 'join' })
          return
        }
        set({ gate: code === 404101 || code === 403102 ? 'not-found' : 'error', errorCode: code })
        return
      }
    }

    // ② 未登录 + 本地 guest 会话
    let session = loadGuestSession(id)
    if (session) {
      if (isGuestSessionExpired(session)) {
        session = await rejoinGuestSession(id)
      }
      if (session) {
        try {
          const room = await showroomApi.getShowroom(id, { guestToken: session.guestToken })
          set({ room, identity: 'guest', gate: 'ready', guestSession: session })
          return
        } catch (err) {
          const code = err instanceof ApiError ? err.code : null
          if (code === 401101) {
            // 过期/重置 → 自动 rejoin 一次（FORM-SHR-S09），仍失败回加入态
            const renewed = await rejoinGuestSession(id)
            if (renewed) {
              try {
                const room = await showroomApi.getShowroom(id, { guestToken: renewed.guestToken })
                set({ room, identity: 'guest', gate: 'ready', guestSession: renewed })
                return
              } catch {
                /* fallthrough */
              }
            }
            clearGuestSession(id)
            set({ gate: inviteToken ? 'join' : 'guest-expired', guestSession: null })
            return
          }
          set({ gate: code === 404101 || code === 403102 ? 'not-found' : 'error', errorCode: code })
          return
        }
      }
    }

    // ③ 未登录 + URL 带 invite → 昵称加入态；④ 否则跳登录
    set({ gate: inviteToken ? 'join' : 'login-required' })
  },

  join: async (id, inviteToken, nickname) => {
    const session = await showroomApi.createGuestSession(inviteToken, nickname)
    const stored = saveGuestSession(id, session, inviteToken)
    const room = await showroomApi.getShowroom(id, { guestToken: stored.guestToken })
    set({ room, identity: 'guest', gate: 'ready', guestSession: stored })
  },

  refetch: async (id) => {
    const { guestSession, identity } = get()
    try {
      const room = await showroomApi.getShowroom(id, {
        guestToken: identity === 'guest' && guestSession ? guestSession.guestToken : undefined
      })
      set({ room })
    } catch {
      /* 局部刷新失败保留现场 */
    }
  },

  vote: async (itemId, vote) => {
    const { room, guestSession, identity } = get()
    if (!room) return
    // 乐观高亮
    const prev = room
    set({
      room: {
        ...room,
        items: room.items.map((it) => (it.id === itemId ? { ...it, myVote: vote } : it))
      }
    })
    try {
      const res = await showroomApi.voteShowroomItem(room.id, itemId, vote, {
        guestToken: identity === 'guest' && guestSession ? guestSession.guestToken : undefined
      })
      const current = get().room
      if (!current) return
      set({
        room: {
          ...current,
          items: current.items.map((it) =>
            it.id === itemId ? { ...it, likeCount: res.likeCount, dislikeCount: res.dislikeCount, myVote: res.myVote } : it
          )
        }
      })
    } catch (err) {
      set({ room: prev })
      throw err
    }
  },

  comment: async (itemId, content) => {
    const { room, guestSession, identity } = get()
    if (!room) return
    const created = await showroomApi.commentShowroomItem(room.id, itemId, content, {
      guestToken: identity === 'guest' && guestSession ? guestSession.guestToken : undefined
    })
    const current = get().room
    if (!current) return
    set({
      room: {
        ...current,
        items: current.items.map((it) =>
          it.id === itemId ? { ...it, comments: [...(it.comments ?? []), created] } : it
        )
      }
    })
  },

  addItem: async (productId, color) => {
    const { room } = get()
    if (!room) return
    const item = await showroomApi.addShowroomItem(room.id, productId, color)
    const current = get().room
    if (!current) return
    set({ room: { ...current, items: [...current.items, item], itemCount: (current.itemCount ?? current.items.length) + 1 } })
  },

  removeItem: async (itemId) => {
    const { room } = get()
    if (!room) return
    await showroomApi.removeShowroomItem(room.id, itemId)
    // 被指派成员回 unassigned 由 refetch 同步
    await get().refetch(room.id)
  },

  assign: async (memberId, assignedItemId, email) => {
    const { room } = get()
    if (!room) return
    const member = await showroomApi.assignShowroomMember(room.id, memberId, { assignedItemId, email })
    const current = get().room
    if (!current) return
    set({ room: { ...current, members: current.members.map((m) => (m.id === memberId ? member : m)) } })
  },

  remind: async (memberId) => {
    const { room } = get()
    if (!room) return
    const member = await showroomApi.remindShowroomMember(room.id, memberId)
    const current = get().room
    if (!current) return
    set({ room: { ...current, members: current.members.map((m) => (m.id === memberId ? member : m)) } })
  },

  updateProfile: async (name, weddingDate) => {
    const { room } = get()
    if (!room) return
    const updated = await showroomApi.updateShowroom(room.id, { name, weddingDate })
    set({ room: updated })
  },

  resetInvite: async () => {
    const { room } = get()
    if (!room) throw new Error('no room')
    const token = await showroomApi.resetShowroomInvite(room.id)
    set({ room: { ...room, inviteToken: token } })
    return token
  }
}))
