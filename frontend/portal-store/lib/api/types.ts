/**
 * portal-store API 类型定义（camelCase 边界，对照 backend/store DTO + identity-api-detail）。
 * 后端 JSON 为 snake_case，经 lib/api/case 转换为以下 camelCase 形态。
 */

export type Locale = 'en' | 'es' | 'fr'
export type AuthProvider = 'email' | 'google' | 'apple'

/** 统一错误响应体 {code,message,details}（shared-contracts error_envelope） */
export interface ApiErrorBody {
  code: number
  message: string
  details?: Record<string, unknown> | null
}

/** TokenPair（StoreAuthController.tokenMap） */
export interface TokenPair {
  accessToken: string
  refreshToken: string
  accessExpiresAt: string
  refreshExpiresAt: string
}

/** 用户资料（UserProfileDTO / MAP-001） */
export interface UserProfile {
  id: string
  email: string | null
  emailVerified: boolean
  name: string | null
  phone: string | null
  tier: string
  avatar: string | null
  joinedAt: string
}

/** 登录响应（verifyOtp / oidcCallback 出参） */
export interface LoginResponse {
  tokens: TokenPair
  user: UserProfile
  isNewAccount: boolean
}

/** sendOtp 出参 */
export interface SendOtpResult {
  resendAfterSeconds: number
  otpLength: number
}

/** 登录方式开关（getStoreAuthConfig 出参） */
export interface StoreAuthConfig {
  emailEnabled: boolean
  googleEnabled: boolean
  appleEnabled: boolean
  otpLength: number
}

/** 凭证（IdentityDTO / MAP-002） */
export interface Identity {
  id: string
  provider: AuthProvider
  identifier: string | null
  isPrimary: boolean
  verified: boolean
  hiddenEmail: boolean
  relayValid: boolean
  lastLoginAt: string | null
}

/** 会话（SessionDTO / MAP-003） */
export interface Session {
  id: string
  device: string | null
  browser: string | null
  ip: string | null
  location: string | null
  isNewDevice: boolean
  isCurrent: boolean
  lastActiveAt: string | null
  createdAt: string
}
