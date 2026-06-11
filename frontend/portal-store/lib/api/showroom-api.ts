/**
 * showroom 域 — 13 端点客户端封装（showroom-frontend §1）。
 * 双态鉴权：store JWT（owner/已登录）或 guest JWT（authTokenOverride 注入，跳过 refresh 重放）。
 * createGuestSession 走匿名分支但 auth:true——有 store token 则附带（STEP-SHR-05 绑定回填），无 token 不报错。
 */

import { request } from './client'
import type {
  GuestSession,
  ShowroomComment,
  ShowroomDetail,
  ShowroomItem,
  ShowroomMember,
  ShowroomSummary,
  ShowroomUpsert,
  VoteResult
} from './store-types'

export async function listShowrooms(): Promise<ShowroomSummary[]> {
  const res = await request<{ items: ShowroomSummary[] }>('/api/store/showrooms', { auth: true })
  return res.items
}

export function createShowroom(body: ShowroomUpsert): Promise<ShowroomDetail> {
  return request<ShowroomDetail>('/api/store/showrooms', { method: 'POST', auth: true, body })
}

/** E-SHR-03 详情（双态：guestToken 提供时以其为 Authorization） */
export function getShowroom(id: number, opts: { guestToken?: string } = {}): Promise<ShowroomDetail> {
  return request<ShowroomDetail>(`/api/store/showrooms/${id}`, {
    auth: true,
    authTokenOverride: opts.guestToken
  })
}

export function updateShowroom(id: number, body: ShowroomUpsert): Promise<ShowroomDetail> {
  return request<ShowroomDetail>(`/api/store/showrooms/${id}`, { method: 'PUT', auth: true, body })
}

export function deleteShowroom(id: number): Promise<void> {
  return request<void>(`/api/store/showrooms/${id}`, { method: 'DELETE', auth: true })
}

/** E-SHR-06 重置邀请链接（旧 token 410101 / 旧 guest JWT 401101 即时失效） */
export async function resetShowroomInvite(id: number): Promise<string> {
  const res = await request<{ inviteToken: string }>(`/api/store/showrooms/${id}/invite/reset`, {
    method: 'POST',
    auth: true
  })
  return res.inviteToken
}

/** E-SHR-07 访客换取受限 guest JWT（匿名；已登录附带 store token 完成 linked_customer_id 绑定） */
export function createGuestSession(inviteToken: string, nickname: string): Promise<GuestSession> {
  return request<GuestSession>('/api/store/showrooms/guest-session', {
    method: 'POST',
    auth: true,
    retryOnUnauthorized: false,
    body: { inviteToken, nickname }
  })
}

export function addShowroomItem(id: number, productId: number, color?: string): Promise<ShowroomItem> {
  return request<ShowroomItem>(`/api/store/showrooms/${id}/items`, {
    method: 'POST',
    auth: true,
    body: { productId, color }
  })
}

export function removeShowroomItem(id: number, itemId: number): Promise<void> {
  return request<void>(`/api/store/showrooms/${id}/items/${itemId}`, { method: 'DELETE', auth: true })
}

/** E-SHR-10 投票（owner 与 guest 均可投；PUT 幂等覆盖） */
export function voteShowroomItem(
  id: number,
  itemId: number,
  vote: 'like' | 'dislike',
  opts: { guestToken?: string } = {}
): Promise<VoteResult> {
  return request<VoteResult>(`/api/store/showrooms/${id}/items/${itemId}/vote`, {
    method: 'PUT',
    auth: true,
    authTokenOverride: opts.guestToken,
    body: { vote }
  })
}

/** E-SHR-11 留言 */
export function commentShowroomItem(
  id: number,
  itemId: number,
  content: string,
  opts: { guestToken?: string } = {}
): Promise<ShowroomComment> {
  return request<ShowroomComment>(`/api/store/showrooms/${id}/items/${itemId}/comments`, {
    method: 'POST',
    auth: true,
    authTokenOverride: opts.guestToken,
    body: { content }
  })
}

/** E-SHR-12 指派（决策 20.5：email 为提醒邮件收件地址） */
export function assignShowroomMember(
  id: number,
  memberId: number,
  body: { assignedItemId: number; email?: string }
): Promise<ShowroomMember> {
  return request<ShowroomMember>(`/api/store/showrooms/${id}/members/${memberId}/assign`, {
    method: 'POST',
    auth: true,
    body
  })
}

/** E-SHR-13 发送提醒（真发邮件，决策 20.5） */
export function remindShowroomMember(id: number, memberId: number): Promise<ShowroomMember> {
  return request<ShowroomMember>(`/api/store/showrooms/${id}/members/${memberId}/remind`, {
    method: 'POST',
    auth: true
  })
}
