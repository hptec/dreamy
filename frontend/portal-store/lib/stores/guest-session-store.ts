/**
 * guestSessionStore（STORE-SHR-S03）：showroom guest token 分房存储定稿。
 * localStorage key `dreamy.showroom.guest.{showroomId}` → { guestToken, expiresAt, memberId, nickname, inviteToken }。
 * 不存 cookie（无 SSR 消费场景；XSS 面与既有 token-store 同基线）。
 * rejoin：用缓存 inviteToken+nickname 静默重放 createGuestSession——token 过期续命 / 登录后绑定回填（FORM-SHR-S08/S09）两用。
 */

import { createGuestSession } from '../api/showroom-api'
import type { GuestSession } from '../api/store-types'

const PREFIX = 'dreamy.showroom.guest.'

export interface StoredGuestSession {
  guestToken: string
  expiresAt?: string
  memberId: number
  nickname: string
  inviteToken: string
}

function isBrowser(): boolean {
  return typeof window !== 'undefined'
}

export function loadGuestSession(showroomId: number | string): StoredGuestSession | null {
  if (!isBrowser()) return null
  try {
    const raw = localStorage.getItem(`${PREFIX}${showroomId}`)
    if (!raw) return null
    const session = JSON.parse(raw) as StoredGuestSession
    if (session.expiresAt && new Date(session.expiresAt).getTime() <= Date.now()) {
      // 过期即清（守卫端调 rejoin 续命）
      return session.inviteToken ? session : null
    }
    return session
  } catch {
    return null
  }
}

export function saveGuestSession(showroomId: number | string, session: GuestSession, inviteToken: string): StoredGuestSession {
  const stored: StoredGuestSession = {
    guestToken: session.guestToken,
    expiresAt: session.expiresAt,
    memberId: session.member.id,
    nickname: session.member.nickname,
    inviteToken
  }
  if (isBrowser()) {
    try {
      localStorage.setItem(`${PREFIX}${showroomId}`, JSON.stringify(stored))
    } catch {
      /* storage 不可用退化为内存态（本次会话仍可用返回值） */
    }
  }
  return stored
}

export function clearGuestSession(showroomId: number | string): void {
  if (!isBrowser()) return
  try {
    localStorage.removeItem(`${PREFIX}${showroomId}`)
  } catch {
    /* ignore */
  }
}

/** guest token 是否已过期（30s 缓冲） */
export function isGuestSessionExpired(session: StoredGuestSession): boolean {
  if (!session.expiresAt) return false
  const exp = new Date(session.expiresAt).getTime()
  return Number.isNaN(exp) || Date.now() >= exp - 30_000
}

/** 静默重放加入（过期续命 / 登录绑定回填）；失败返回 null 并清缓存 */
export async function rejoinGuestSession(showroomId: number | string): Promise<StoredGuestSession | null> {
  const cached = loadGuestSession(showroomId)
  if (!cached?.inviteToken || !cached.nickname) return null
  try {
    const session = await createGuestSession(cached.inviteToken, cached.nickname)
    return saveGuestSession(showroomId, session, cached.inviteToken)
  } catch {
    clearGuestSession(showroomId)
    return null
  }
}

/** 登录成功回调钩子（FORM-SHR-S08）：对每个缓存房静默 rejoin（带 store token 重放 → 后端绑定 linked_customer_id）。 */
export async function rejoinAllGuestSessionsAfterLogin(): Promise<void> {
  if (!isBrowser()) return
  const ids: string[] = []
  try {
    for (let i = 0; i < localStorage.length; i += 1) {
      const key = localStorage.key(i)
      if (key?.startsWith(PREFIX)) ids.push(key.slice(PREFIX.length))
    }
  } catch {
    return
  }
  await Promise.all(
    ids.map(async (id) => {
      try {
        await rejoinGuestSession(id)
      } catch {
        /* 重放失败静默忽略，不阻塞登录主流程 */
      }
    })
  )
}
