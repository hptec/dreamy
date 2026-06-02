<script setup lang="ts">
// PAGE-A01 / COMP-A02 / FORM-A01：管理员登录，对接 adminLogin
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { LockClosedIcon, EnvelopeIcon, ExclamationCircleIcon } from '@heroicons/vue/24/outline'
import { useAuthStore } from '@/stores/auth'
import { BizError } from '@/api/client'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const email = ref('admin@dreamy.com')
const password = ref('')
const remember = ref(true)
const error = ref('')
const loading = ref(false)

// 前端轻量预校验（提交前）
function preValidate(): string {
  const trimmed = email.value.trim()
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)) return '请输入有效的邮箱地址'
  if (!password.value) return '请输入密码'
  return ''
}

async function submit() {
  error.value = ''
  const pre = preValidate()
  if (pre) {
    error.value = pre
    return
  }
  loading.value = true
  try {
    await auth.login({ email: email.value.trim(), password: password.value })
    // FORM-A01：按 redirect 跳转
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (e) {
    // 401 40103 凭据错误 / 403 40302 账户禁用 → 直显后端中文 message
    error.value = e instanceof BizError ? e.message : '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="grid min-h-screen lg:grid-cols-2">
    <!-- 品牌侧 -->
    <div class="relative hidden overflow-hidden lg:block">
      <img src="/competitor-refs/kissprom/wedding-aline-tulle-01.jpg" alt="" class="absolute inset-0 h-full w-full object-cover object-top" />
      <div class="absolute inset-0 bg-gradient-to-tr from-sidebar/85 via-sidebar/40 to-transparent"></div>
      <div class="absolute bottom-0 left-0 p-12 text-canvas">
        <p class="eyebrow text-gold-soft">Dreamy Admin Console</p>
        <h2 class="mt-3 max-w-sm font-display text-4xl font-medium leading-tight">
          为黄金时刻而生的<br />运营管理后台
        </h2>
        <p class="mt-4 max-w-sm text-sm text-canvas/70">
          配置首页、管理商品订单、一键发布静态站点。
        </p>
      </div>
    </div>

    <!-- 表单侧 -->
    <div class="flex items-center justify-center bg-canvas px-6 py-16">
      <div class="w-full max-w-sm">
        <div class="mb-8 flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-luxe bg-ink font-display text-xl font-semibold text-canvas">D</div>
          <div>
            <p class="font-display text-xl font-semibold text-ink">Dreamy</p>
            <p class="text-[10px] uppercase tracking-luxe text-gold-deep">Admin Console</p>
          </div>
        </div>

        <h1 class="font-display text-2xl font-semibold text-ink">欢迎回来</h1>
        <p class="mt-1 text-[13px] text-ink-soft">登录以管理您的 Dreamy 站点</p>

        <form class="mt-8 space-y-4" @submit.prevent="submit">
          <div>
            <label for="login-email" class="field-label">邮箱</label>
            <div class="relative">
              <EnvelopeIcon class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
              <input id="login-email" v-model="email" type="email" autocomplete="username" class="field pl-9" placeholder="admin@dreamy.com" />
            </div>
          </div>
          <div>
            <label for="login-password" class="field-label">密码</label>
            <div class="relative">
              <LockClosedIcon class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
              <input id="login-password" v-model="password" type="password" autocomplete="current-password" class="field pl-9" placeholder="请输入密码" />
            </div>
          </div>

          <div v-if="error" class="flex items-center gap-2 rounded-luxe bg-danger/10 px-3 py-2 text-[12.5px] text-danger" role="alert" aria-live="assertive">
            <ExclamationCircleIcon class="h-4 w-4 shrink-0" />
            <span>{{ error }}</span>
          </div>

          <div class="flex items-center justify-between text-[13px]">
            <label class="flex items-center gap-2 text-ink-soft">
              <input v-model="remember" type="checkbox" class="h-4 w-4 rounded border-line accent-gold" /> 记住我
            </label>
            <a href="#" class="text-gold-deep hover:underline">忘记密码？</a>
          </div>
          <button type="submit" class="btn-primary w-full py-2.5" :disabled="loading">
            {{ loading ? '登录中…' : '登录后台' }}
          </button>
        </form>

        <p class="mt-8 text-center text-[12px] text-ink-faint">
          管理后台与消费端完全隔离 · 独立 admin 会话
        </p>
      </div>
    </div>
  </div>
</template>
