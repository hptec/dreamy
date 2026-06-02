<script setup lang="ts">
// PAGE-A06 / COMP-A07：登录与认证配置。email 强制只读 on；OTP 区间前端预校验 + 422 40002 回显；OAuth 只读
// 约束: FORM-A05 前端区间预校验→保存→字段级错误回显
import { ref, reactive, computed, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { authConfigApi } from '@/api'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import type { AuthConfig } from '@/api/types'
import {
  CheckBadgeIcon, KeyIcon, LinkIcon, LockClosedIcon, InformationCircleIcon,
} from '@heroicons/vue/24/outline'

const toast = useToastStore()
const loading = ref(true)
const saving = ref(false)

// 表单态（与 AuthConfig 对齐）
const form = reactive({
  emailEnabled: true,
  googleEnabled: true,
  appleEnabled: true,
  otpLength: 6,
  otpTtlMinutes: 10,
  otpResendSeconds: 30,
  otpMaxAttempts: 5,
  minMethods: 1,
})
const oauth = reactive({ googleClientId: '', appleServiceId: '' })
const errors = ref<Record<string, string>>({})

// 登录方式开关列表（email 锁定 on）
const methods = computed(() => [
  { provider: 'email', label: '邮箱验证码（Passwordless）', enabled: form.emailEnabled, locked: true, desc: '主登录方式，向用户邮箱发送一次性验证码，无需密码。' },
  { provider: 'google', label: 'Google 登录', enabled: form.googleEnabled, locked: false, desc: 'OAuth 2.0 / OpenID Connect，按 Google sub 标识用户。' },
  { provider: 'apple', label: 'Apple 登录', enabled: form.appleEnabled, locked: false, desc: '支持 Hide My Email，按 Apple sub 标识；首次授权才返回邮箱/姓名。' },
])

function applyConfig(cfg: AuthConfig) {
  form.emailEnabled = cfg.emailEnabled
  form.googleEnabled = cfg.googleEnabled
  form.appleEnabled = cfg.appleEnabled
  form.otpLength = cfg.otpLength
  form.otpTtlMinutes = cfg.otpTtlMinutes
  form.otpResendSeconds = cfg.otpResendSeconds
  form.otpMaxAttempts = cfg.otpMaxAttempts
  form.minMethods = cfg.minMethods
  oauth.googleClientId = cfg.googleClientId || ''
  oauth.appleServiceId = cfg.appleServiceId || ''
}

async function load() {
  loading.value = true
  try {
    const cfg = await authConfigApi.getAuthConfig()
    applyConfig(cfg)
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '加载认证配置失败')
  } finally {
    loading.value = false
  }
}

function toggleMethod(provider: string) {
  if (provider === 'google') form.googleEnabled = !form.googleEnabled
  else if (provider === 'apple') form.appleEnabled = !form.appleEnabled
  // email 锁定，不可切换
}

// 前端区间预校验（与 openapi AuthConfigUpdate 约束一致）
function validate(): boolean {
  const e: Record<string, string> = {}
  if (![4, 6, 8].includes(form.otpLength)) e.otpLength = '验证码长度只能是 4 / 6 / 8 位'
  if (form.otpTtlMinutes < 1 || form.otpTtlMinutes > 30) e.otpTtlMinutes = '有效期需在 1–30 分钟之间'
  if (form.otpResendSeconds < 10 || form.otpResendSeconds > 120) e.otpResendSeconds = '重发间隔需在 10–120 秒之间'
  if (form.otpMaxAttempts < 3 || form.otpMaxAttempts > 10) e.otpMaxAttempts = '最大尝试次数需在 3–10 之间'
  if (form.minMethods < 1 || form.minMethods > 3) e.minMethods = '至少保留登录方式数需在 1–3 之间'
  errors.value = e
  return Object.keys(e).length === 0
}

async function save() {
  if (!validate()) {
    toast.error('请修正表单中的错误项')
    return
  }
  saving.value = true
  try {
    const updated = await authConfigApi.updateAuthConfig({
      googleEnabled: form.googleEnabled,
      appleEnabled: form.appleEnabled,
      otpLength: form.otpLength,
      otpTtlMinutes: form.otpTtlMinutes,
      otpResendSeconds: form.otpResendSeconds,
      otpMaxAttempts: form.otpMaxAttempts,
      minMethods: form.minMethods,
    })
    applyConfig(updated)
    toast.success('登录与认证配置已保存，变更已记入操作日志')
  } catch (e) {
    if (e instanceof BizError && e.code === 40002) {
      // 字段级错误回显（details 形如 { field: message }）
      const details = e.details
      if (details && typeof details === 'object') {
        errors.value = Object.fromEntries(
          Object.entries(details).map(([k, v]) => [
            k.replace(/_([a-z0-9])/g, (_m, c: string) => c.toUpperCase()),
            String(v),
          ]),
        )
      }
      toast.error(e.message)
    } else {
      toast.error(e instanceof BizError ? e.message : '保存失败')
    }
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="系统管理" title="登录与认证" subtitle="配置消费端登录方式、验证码策略与账户关联规则">
      <template #actions>
        <button class="btn-primary" :disabled="saving || loading" @click="save">{{ saving ? '保存中…' : '保存配置' }}</button>
      </template>
    </PageHeader>

    <div v-if="loading" class="panel py-16 text-center text-[13px] text-ink-faint">加载中…</div>

    <div v-else class="grid grid-cols-1 gap-6 lg:grid-cols-2">
      <!-- 登录方式 -->
      <div class="panel p-6">
        <h3 class="mb-1 flex items-center gap-1.5 font-display text-base font-semibold text-ink"><KeyIcon class="h-4 w-4 text-gold-deep" />登录方式</h3>
        <p class="mb-4 text-[12px] text-ink-faint">控制消费端可用的登录入口。</p>
        <div class="space-y-3">
          <div v-for="m in methods" :key="m.provider" class="flex items-start justify-between gap-3 rounded-luxe border border-line p-4">
            <div class="min-w-0">
              <div class="flex items-center gap-2">
                <span class="text-[13px] font-medium text-ink">{{ m.label }}</span>
                <span v-if="m.locked" class="inline-flex items-center gap-0.5 rounded-full bg-ink/6 px-1.5 py-0.5 text-[10px] text-ink-soft"><LockClosedIcon class="h-3 w-3" />主登录</span>
              </div>
              <p class="mt-1 text-[12px] text-ink-soft">{{ m.desc }}</p>
            </div>
            <button
              type="button"
              class="relative mt-0.5 inline-flex h-6 w-10 shrink-0 items-center rounded-full transition-colors"
              :class="[m.enabled ? 'bg-ok' : 'bg-ink-faint', m.locked ? 'cursor-not-allowed opacity-60' : 'cursor-pointer']"
              :title="m.locked ? '主登录方式不可关闭' : ''"
              @click="toggleMethod(m.provider)"
            >
              <span class="inline-block h-4 w-4 rounded-full bg-white shadow-sm transition-transform" :class="m.enabled ? 'translate-x-5' : 'translate-x-1'" />
            </button>
          </div>
        </div>
      </div>

      <!-- 验证码策略 -->
      <div class="panel p-6">
        <h3 class="mb-1 flex items-center gap-1.5 font-display text-base font-semibold text-ink"><CheckBadgeIcon class="h-4 w-4 text-gold-deep" />验证码（OTP）策略</h3>
        <p class="mb-4 text-[12px] text-ink-faint">Passwordless 登录的一次性验证码规则。</p>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="mb-1 block text-[13px] font-medium text-ink">验证码长度</label>
            <select v-model.number="form.otpLength" class="field w-full">
              <option :value="4">4 位</option>
              <option :value="6">6 位</option>
              <option :value="8">8 位</option>
            </select>
            <p v-if="errors.otpLength" class="mt-1 text-[12px] text-danger">{{ errors.otpLength }}</p>
          </div>
          <div>
            <label class="mb-1 block text-[13px] font-medium text-ink">有效期（分钟）</label>
            <input v-model.number="form.otpTtlMinutes" type="number" min="1" max="30" class="field w-full" />
            <p v-if="errors.otpTtlMinutes" class="mt-1 text-[12px] text-danger">{{ errors.otpTtlMinutes }}</p>
          </div>
          <div>
            <label class="mb-1 block text-[13px] font-medium text-ink">重发间隔（秒）</label>
            <input v-model.number="form.otpResendSeconds" type="number" min="10" max="120" class="field w-full" />
            <p v-if="errors.otpResendSeconds" class="mt-1 text-[12px] text-danger">{{ errors.otpResendSeconds }}</p>
          </div>
          <div>
            <label class="mb-1 block text-[13px] font-medium text-ink">最大尝试次数</label>
            <input v-model.number="form.otpMaxAttempts" type="number" min="3" max="10" class="field w-full" />
            <p v-if="errors.otpMaxAttempts" class="mt-1 text-[12px] text-danger">{{ errors.otpMaxAttempts }}</p>
          </div>
        </div>
      </div>

      <!-- 账户关联策略 -->
      <div class="panel p-6">
        <h3 class="mb-1 flex items-center gap-1.5 font-display text-base font-semibold text-ink"><LinkIcon class="h-4 w-4 text-gold-deep" />账户关联策略</h3>
        <p class="mb-4 text-[12px] text-ink-faint">多登录方式归并为同一个人的系统规则。</p>
        <div class="space-y-3">
          <div class="flex items-start gap-2 rounded-luxe bg-info/8 px-4 py-3 text-[12px] text-ink-soft">
            <InformationCircleIcon class="mt-0.5 h-4 w-4 shrink-0 text-info" />
            <p>账户合并由系统<strong>自动</strong>完成，无需人工干预或开关配置：用户注册 / 登录时，凡 <code class="rounded bg-ink/6 px-1">email_verified=true</code> 且邮箱一致即自动归并到同一人；邮箱未验证或与已有账户冲突时不静默合并，提示用户用原方式登录后再绑定，避免账户被劫持。</p>
          </div>
          <div class="flex items-center justify-between rounded-luxe border border-line p-4">
            <span class="text-[13px] font-medium text-ink">用户解绑时至少保留的登录方式数</span>
            <div class="text-right">
              <input v-model.number="form.minMethods" type="number" min="1" max="3" class="field w-20 text-center" />
              <p v-if="errors.minMethods" class="mt-1 text-[12px] text-danger">{{ errors.minMethods }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- OAuth 凭据（只读） -->
      <div class="panel p-6">
        <h3 class="mb-1 flex items-center gap-1.5 font-display text-base font-semibold text-ink"><LockClosedIcon class="h-4 w-4 text-gold-deep" />OAuth 凭据</h3>
        <p class="mb-4 text-[12px] text-ink-faint">第三方登录服务端凭据（只读展示，正式环境从密钥管理读取）。</p>
        <div class="space-y-4">
          <div>
            <div class="mb-1 flex items-center justify-between">
              <label class="text-[13px] font-medium text-ink">Google Client ID</label>
              <span v-if="oauth.googleClientId" class="inline-flex items-center gap-0.5 text-[11px] text-ok"><CheckBadgeIcon class="h-3.5 w-3.5" />已配置</span>
            </div>
            <input :value="oauth.googleClientId || '未配置'" readonly class="field w-full cursor-not-allowed bg-canvas-warm font-mono text-[12px] text-ink-faint" />
          </div>
          <div>
            <div class="mb-1 flex items-center justify-between">
              <label class="text-[13px] font-medium text-ink">Apple Service ID</label>
              <span v-if="oauth.appleServiceId" class="inline-flex items-center gap-0.5 text-[11px] text-ok"><CheckBadgeIcon class="h-3.5 w-3.5" />已配置</span>
            </div>
            <input :value="oauth.appleServiceId || '未配置'" readonly class="field w-full cursor-not-allowed bg-canvas-warm font-mono text-[12px] text-ink-faint" />
          </div>
          <div class="flex items-start gap-2 rounded-luxe bg-info/8 px-3 py-2.5 text-[12px] text-ink-soft">
            <InformationCircleIcon class="mt-0.5 h-4 w-4 shrink-0 text-info" />
            <p>Apple 仅在用户<strong>首次授权</strong>时返回邮箱与姓名，且支持 Hide My Email；系统以 Apple <code class="rounded bg-ink/6 px-1">sub</code> 作为稳定主键，首次即落库保存。</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
