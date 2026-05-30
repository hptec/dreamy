<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { LockClosedIcon, EnvelopeIcon, ExclamationCircleIcon } from '@heroicons/vue/24/outline'
import { useAuth } from '@/composables/useAuth'

const router = useRouter()
const route = useRoute()
const { login } = useAuth()

const email = ref('admin@dreamy.com')
const password = ref('')
const remember = ref(true)
const error = ref('')
const loading = ref(false)

function submit() {
  error.value = ''
  const result = login({ email: email.value, password: password.value })
  if (!result.ok) {
    error.value = result.message
    return
  }
  loading.value = true
  // 模拟鉴权请求延迟
  setTimeout(() => {
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.replace(redirect)
  }, 500)
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
            <label class="field-label">邮箱</label>
            <div class="relative">
              <EnvelopeIcon class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
              <input v-model="email" type="email" class="field pl-9" placeholder="admin@dreamy.com" />
            </div>
          </div>
          <div>
            <label class="field-label">密码</label>
            <div class="relative">
              <LockClosedIcon class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
              <input v-model="password" type="password" class="field pl-9" placeholder="请输入密码" />
            </div>
          </div>

          <div v-if="error" class="flex items-center gap-2 rounded-luxe bg-danger/10 px-3 py-2 text-[12.5px] text-danger">
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
          原型演示 · 邮箱 + 任意密码即可进入 · 与消费端完全隔离
        </p>
      </div>
    </div>
  </div>
</template>
