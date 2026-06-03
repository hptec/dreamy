// API DTO 类型（边界处 camelCase；与 OpenAPI identity-api 对齐，后端返回 snake_case 由 client 统一转换）
// 约束: STORE-A01~A04 / PAGE-A01~A07 数据形状来源 = identity-api.openapi.yml + backend/admin DTO

// ===== 通用 =====
// 对应 huihao.page.Paginated<T>（snake→camel 后）
export interface PageResult<T> {
  data: T[]
  totalElements: number
  pageNumber: number
  pageSize: number
  totalPages: number
  numberOfElements: number
}

export interface ApiError {
  code: number
  message: string
  data?: Record<string, unknown>
}

// ===== 鉴权（PAGE-A01 / STORE-A01）=====
export interface AdminProfile {
  id: number
  name: string
  email: string
  roleId: number | null
  roleName: string | null
  status: string
  lastLoginAt: string | null
}

export interface AdminLoginResult {
  token: string
  admin: AdminProfile
  permissionKeys: string[]
  isSuper: boolean
}

export interface AdminMe {
  admin: AdminProfile
  roleName: string
  isSuper: boolean
  permissionKeys: string[]
}

// ===== 管理员 CRUD（PAGE-A04）=====
export interface Admin {
  id: number
  name: string
  email: string
  roleId: number | null
  roleName: string | null
  status: string
  lastLoginAt: string | null
}

export interface AdminCreatePayload {
  name: string
  email: string
  password: string
  roleId: number
}

export interface AdminUpdatePayload {
  name: string
  roleId: number
}

// ===== 角色 / 权限（PAGE-A05）=====
export interface Role {
  id: number
  name: string
  type: string
  isLocked: boolean
  memberCount: number
  permissionKeys: string[]
}

export interface Permission {
  key: string
  group: string
  label: string
}

// ===== 用户身份运营（PAGE-A02 / A03）=====
export interface UserListItem {
  id: number
  email: string | null
  emailVerified?: boolean
  name: string | null
  phone?: string | null
  tier: string
  avatar?: string | null
  joinedAt: string | null
}

export interface Identity {
  id: number
  provider: string
  identifier: string | null
  isPrimary: boolean
  verified: boolean
  hiddenEmail: boolean
  relayValid: boolean
  lastLoginAt: string | null
}

export interface Session {
  id: number
  device: string | null
  browser: string | null
  ip: string | null
  location: string | null
  isNewDevice: boolean
  isCurrent: boolean
  lastActiveAt: string | null
  createdAt: string | null
}

export interface LoginHistoryItem {
  id: number
  method: string
  ip: string | null
  device: string | null
  location: string | null
  result: string
  isNewDevice?: boolean
  createdAt: string | null
}

export interface UserDetail {
  user: UserListItem
  identities: Identity[]
  sessions: Session[]
  loginHistory: LoginHistoryItem[]
}

// ===== 认证配置（PAGE-A06）=====
export interface AuthConfig {
  emailEnabled: boolean
  googleEnabled: boolean
  appleEnabled: boolean
  otpLength: number
  otpTtlMinutes: number
  otpResendSeconds: number
  otpMaxAttempts: number
  minMethods: number
  googleClientId?: string | null
  appleServiceId?: string | null
  updatedAt?: string | null
}

export interface AuthConfigUpdatePayload {
  googleEnabled: boolean
  appleEnabled: boolean
  otpLength: number
  otpTtlMinutes: number
  otpResendSeconds: number
  otpMaxAttempts: number
  minMethods: number
}

// ===== 操作日志（PAGE-A07）=====
export interface OperationLog {
  id: number
  operatorName: string | null
  action: string
  target: string | null
  ip: string | null
  changes: string | null
  createdAt: string | null
}
