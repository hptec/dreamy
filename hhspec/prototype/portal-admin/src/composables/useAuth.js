import { ref, computed } from 'vue'

const STORAGE_KEY = 'dreamy_admin_session'

// 原型演示账户（任意密码均可，仅校验邮箱格式与非空）
const DEMO_USER = {
  name: 'Super Admin',
  email: 'admin@dreamy.com',
  role: '超级管理员',
  initials: 'SA',
}

function loadSession() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

const user = ref(loadSession())
const isAuthenticated = computed(() => user.value !== null)

function login({ email, password }) {
  const trimmed = (email || '').trim()
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)) {
    return { ok: false, message: '请输入有效的邮箱地址' }
  }
  if (!password) {
    return { ok: false, message: '请输入密码' }
  }
  const session = { ...DEMO_USER, email: trimmed, loginAt: new Date().toISOString() }
  user.value = session
  localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  return { ok: true }
}

function logout() {
  user.value = null
  localStorage.removeItem(STORAGE_KEY)
}

export function useAuth() {
  return { user, isAuthenticated, login, logout }
}
