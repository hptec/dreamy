/**
 * portal-store 端点封装（对照 backend/store controller 真实路由 + identity-api-detail）。
 * 路由权威来源：StoreAuthController(/api/store/auth/*) + AccountController(/api/store/account/*)。
 */

import { request } from './client'
import type {
  AuthProvider,
  Identity,
  LoginResponse,
  SendOtpResult,
  StoreAuthConfig,
  UserProfile
} from './types'

// ===== 认证（公开） =====

/** sendOtp — POST /api/store/auth/otp/send（FLOW-01 FUNC-001 / FORM-S01） */
export function sendOtp(email: string, locale: string): Promise<SendOtpResult> {
  return request<SendOtpResult>('/api/store/auth/otp/send', {
    method: 'POST',
    body: { email, locale }
  })
}

/** verifyOtp — POST /api/store/auth/otp/verify（FLOW-02 FUNC-002 / FORM-S02） */
export function verifyOtp(email: string, code: string): Promise<LoginResponse> {
  return request<LoginResponse>('/api/store/auth/otp/verify', {
    method: 'POST',
    body: { email, code }
  })
}

/** oidcCallback — POST /api/store/auth/oidc/{provider}/callback（FLOW-03 / FORM-S03） */
export function oidcCallback(
  provider: Extract<AuthProvider, 'google' | 'apple'>,
  idToken: string,
  nonce?: string
): Promise<LoginResponse> {
  return request<LoginResponse>(`/api/store/auth/oidc/${provider}/callback`, {
    method: 'POST',
    body: { idToken, nonce }
  })
}

/** getStoreAuthConfig — GET /api/store/auth/config（FUNC-003） */
export function getStoreAuthConfig(): Promise<StoreAuthConfig> {
  return request<StoreAuthConfig>('/api/store/auth/config', { method: 'GET' })
}

// ===== 账户安全（需 store JWT） =====

/** getProfile — GET /api/store/account/profile（FUNC-007） */
export function getProfile(): Promise<UserProfile> {
  return request<UserProfile>('/api/store/account/profile', { method: 'GET', auth: true })
}

/** listIdentities — GET /api/store/account/identities（FUNC-010） */
export function listIdentities(): Promise<Identity[]> {
  return request<Identity[]>('/api/store/account/identities', {
    method: 'GET',
    auth: true
  })
}

/** updateProfile — PUT /api/store/account/profile（FUNC-019 / 决策13，持久化 locale_pref / display_name） */
export function updateProfile(input: {
  displayName?: string
  localePref?: string
}): Promise<UserProfile> {
  return request<UserProfile>('/api/store/account/profile', {
    method: 'PUT',
    auth: true,
    body: input
  })
}

/** bindIdentity — POST /api/store/account/identities/bind（FLOW-05 FUNC-008） */
export function bindIdentity(input: {
  provider: AuthProvider
  idToken?: string
  email?: string
  code?: string
}): Promise<Identity[]> {
  return request<Identity[]>('/api/store/account/identities/bind', {
    method: 'POST',
    auth: true,
    body: input
  })
}

/** unbindIdentity — DELETE /api/store/account/identities/{identityId}（FLOW-05 FUNC-009） */
export function unbindIdentity(identityId: number): Promise<void> {
  return request<void>(`/api/store/account/identities/${identityId}`, {
    method: 'DELETE',
    auth: true
  })
}

/** changePrimaryEmail — POST /api/store/account/email/change-primary（FLOW-06 FUNC-026） */
export function changePrimaryEmail(newEmail: string, code: string): Promise<Identity[]> {
  return request<Identity[]>('/api/store/account/email/change-primary', {
    method: 'POST',
    auth: true,
    body: { newEmail, code }
  })
}

/** deleteAccount — POST /api/store/account/delete（FLOW-08 FUNC-027） */
export function deleteAccount(): Promise<void> {
  return request<void>('/api/store/account/delete', {
    method: 'POST',
    auth: true,
    body: { confirm: true }
  })
}
