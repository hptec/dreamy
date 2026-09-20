<script setup lang="ts">
// PAGE-A06 / COMP-A07：登录与认证配置。email/google/apple 均可开闭（允许全关）；OTP 区间前端预校验 + 422 40002 回显
// 约束: FORM-A05 前端区间预校验→保存→字段级错误回显
import { ref, reactive, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { authConfigApi } from '@/api'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import type { AuthConfig } from '@/api/types'
import {
  CheckBadgeIcon, KeyIcon, LinkIcon, LockClosedIcon, InformationCircleIcon,
  ChevronUpDownIcon, CheckIcon, ShieldExclamationIcon, ClockIcon,
} from '@heroicons/vue/24/outline'
import {
  Listbox, ListboxButton, ListboxOptions, ListboxOption,
} from '@headlessui/vue'

const otpLengthOptions = [4, 6, 8]

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
  adminLoginMaxAttempts: 5,
  adminLoginLockMinutes: 15,
  verifyIpRatePerMinute: 30,
  attackAlertThreshold: 20,
  adminAlertEmail: '',
  storeAccessTtlMinutes: 120,
  storeRefreshTtlDays: 30,
  adminAccessTtlHours: 8,
})
const oauth = reactive({ googleClientId: '', appleServiceId: '' })
const errors = ref<Record<string, string>>({})


function applyConfig(cfg: AuthConfig) {
  form.emailEnabled = cfg.emailEnabled
  form.googleEnabled = cfg.googleEnabled
  form.appleEnabled = cfg.appleEnabled
  form.otpLength = cfg.otpLength
  form.otpTtlMinutes = cfg.otpTtlMinutes
  form.otpResendSeconds = cfg.otpResendSeconds
  form.otpMaxAttempts = cfg.otpMaxAttempts
  form.minMethods = cfg.minMethods
  form.adminLoginMaxAttempts = cfg.adminLoginMaxAttempts ?? 5
  form.adminLoginLockMinutes = cfg.adminLoginLockMinutes ?? 15
  form.verifyIpRatePerMinute = cfg.verifyIpRatePerMinute ?? 30
  form.attackAlertThreshold = cfg.attackAlertThreshold ?? 20
  form.adminAlertEmail = cfg.adminAlertEmail || ''
  form.storeAccessTtlMinutes = cfg.storeAccessTtlMinutes ?? 120
  form.storeRefreshTtlDays = cfg.storeRefreshTtlDays ?? 30
  form.adminAccessTtlHours = cfg.adminAccessTtlHours ?? 8
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
  if (provider === 'email') form.emailEnabled = !form.emailEnabled
  else if (provider === 'google') form.googleEnabled = !form.googleEnabled
  else if (provider === 'apple') form.appleEnabled = !form.appleEnabled
}

// 前端区间预校验（与 openapi AuthConfigUpdate 约束一致）
function validate(): boolean {
  const e: Record<string, string> = {}
  if (![4, 6, 8].includes(form.otpLength)) e.otpLength = '验证码长度只能是 4 / 6 / 8 位'
  if (form.otpTtlMinutes < 1 || form.otpTtlMinutes > 30) e.otpTtlMinutes = '有效期需在 1–30 分钟之间'
  if (form.otpResendSeconds < 10 || form.otpResendSeconds > 120) e.otpResendSeconds = '重发间隔需在 10–120 秒之间'
  if (form.otpMaxAttempts < 3 || form.otpMaxAttempts > 10) e.otpMaxAttempts = '最大尝试次数需在 3–10 之间'
  if (form.minMethods < 1 || form.minMethods > 3) e.minMethods = '至少保留登录方式数需在 1–3 之间'
  if (form.adminLoginMaxAttempts < 3 || form.adminLoginMaxAttempts > 10) e.adminLoginMaxAttempts = '失败锁定阈值需在 3–10 之间'
  if (form.adminLoginLockMinutes < 5 || form.adminLoginLockMinutes > 60) e.adminLoginLockMinutes = '锁定时长需在 5–60 分钟之间'
  if (form.verifyIpRatePerMinute < 5 || form.verifyIpRatePerMinute > 300) e.verifyIpRatePerMinute = '验证频控需在 5–300 次/分钟之间'
  if (form.attackAlertThreshold < 10 || form.attackAlertThreshold > 1000) e.attackAlertThreshold = '告警阈值需在 10–1000 之间'
  if (form.adminAlertEmail && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.adminAlertEmail)) e.adminAlertEmail = '告警通知邮箱格式不正确'
  if (form.storeAccessTtlMinutes < 10 || form.storeAccessTtlMinutes > 1440) e.storeAccessTtlMinutes = '访问令牌有效期需在 10–1440 分钟之间'
  if (form.storeRefreshTtlDays < 1 || form.storeRefreshTtlDays > 90) e.storeRefreshTtlDays = '刷新令牌有效期需在 1–90 天之间'
  if (form.adminAccessTtlHours < 1 || form.adminAccessTtlHours > 24) e.adminAccessTtlHours = '管理员令牌有效期需在 1–24 小时之间'
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
      emailEnabled: form.emailEnabled,
      googleEnabled: form.googleEnabled,
      appleEnabled: form.appleEnabled,
      otpLength: form.otpLength,
      otpTtlMinutes: form.otpTtlMinutes,
      otpResendSeconds: form.otpResendSeconds,
      otpMaxAttempts: form.otpMaxAttempts,
      minMethods: form.minMethods,
      adminLoginMaxAttempts: form.adminLoginMaxAttempts,
      adminLoginLockMinutes: form.adminLoginLockMinutes,
      verifyIpRatePerMinute: form.verifyIpRatePerMinute,
      attackAlertThreshold: form.attackAlertThreshold,
      adminAlertEmail: form.adminAlertEmail || null,
      storeAccessTtlMinutes: form.storeAccessTtlMinutes,
      storeRefreshTtlDays: form.storeRefreshTtlDays,
      adminAccessTtlHours: form.adminAccessTtlHours,
      googleClientId: oauth.googleClientId || null,
      appleServiceId: oauth.appleServiceId || null,
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
      <div class="panel p-6 lg:col-span-2">
        <h3 class="mb-1 flex items-center gap-1.5 font-display text-base font-semibold text-ink"><KeyIcon class="h-4 w-4 text-gold-deep" />登录方式</h3>
        <p class="mb-4 text-[12px] text-ink-faint">控制消费端可用的登录入口。OAuth 凭据开启后即可填写。</p>
        <div class="space-y-3">
          <!-- email（可开闭，无凭据） -->
          <div class="flex items-start justify-between gap-3 rounded-luxe border border-line p-4">
            <div class="min-w-0">
              <div class="flex items-center gap-2">
                <span class="text-[13px] font-medium text-ink">邮箱验证码（Passwordless）</span>
              </div>
              <p class="mt-1 text-[12px] text-ink-soft">向用户邮箱发送一次性验证码，无需密码。关闭后消费端登录页不再展示邮箱入口。</p>
            </div>
            <button
              type="button"
              class="relative mt-0.5 inline-flex h-6 w-10 shrink-0 cursor-pointer items-center rounded-full transition-colors"
              :class="form.emailEnabled ? 'bg-ok' : 'bg-ink-faint'"
              @click="toggleMethod('email')"
            >
              <span class="inline-block h-4 w-4 rounded-full bg-white shadow-sm transition-transform" :class="form.emailEnabled ? 'translate-x-5' : 'translate-x-1'" />
            </button>
          </div>

          <!-- Google -->
          <div class="rounded-luxe border border-line">
            <div class="flex items-start justify-between gap-3 p-4">
              <div class="min-w-0">
                <span class="text-[13px] font-medium text-ink">Google 登录</span>
                <p class="mt-1 text-[12px] text-ink-soft">OAuth 2.0 / OpenID Connect，按 Google sub 标识用户。</p>
              </div>
              <button
                type="button"
                class="relative mt-0.5 inline-flex h-6 w-10 shrink-0 cursor-pointer items-center rounded-full transition-colors"
                :class="form.googleEnabled ? 'bg-ok' : 'bg-ink-faint'"
                @click="toggleMethod('google')"
              >
                <span class="inline-block h-4 w-4 rounded-full bg-white shadow-sm transition-transform" :class="form.googleEnabled ? 'translate-x-5' : 'translate-x-1'" />
              </button>
            </div>
            <div v-if="form.googleEnabled" class="border-t border-line px-4 pb-4 pt-3">
              <label class="mb-1 block text-[12px] font-medium text-ink">Google Client ID</label>
              <div class="flex items-center gap-2">
                <input v-model.trim="oauth.googleClientId" placeholder="输入 Google OAuth Client ID" class="field min-w-0 flex-1 font-mono text-[12px]" />
                <span v-if="oauth.googleClientId" class="inline-flex shrink-0 items-center gap-0.5 text-[11px] text-ok"><CheckBadgeIcon class="h-3.5 w-3.5" />已配置</span>
              </div>
            </div>
          </div>

          <!-- Apple -->
          <div class="rounded-luxe border border-line">
            <div class="flex items-start justify-between gap-3 p-4">
              <div class="min-w-0">
                <span class="text-[13px] font-medium text-ink">Apple 登录</span>
                <p class="mt-1 text-[12px] text-ink-soft">支持 Hide My Email，按 Apple sub 标识；首次授权才返回邮箱/姓名。</p>
              </div>
              <button
                type="button"
                class="relative mt-0.5 inline-flex h-6 w-10 shrink-0 cursor-pointer items-center rounded-full transition-colors"
                :class="form.appleEnabled ? 'bg-ok' : 'bg-ink-faint'"
                @click="toggleMethod('apple')"
              >
                <span class="inline-block h-4 w-4 rounded-full bg-white shadow-sm transition-transform" :class="form.appleEnabled ? 'translate-x-5' : 'translate-x-1'" />
              </button>
            </div>
            <div v-if="form.appleEnabled" class="border-t border-line px-4 pb-4 pt-3">
              <label class="mb-1 block text-[12px] font-medium text-ink">Apple Service ID</label>
              <div class="flex items-center gap-2">
                <input v-model.trim="oauth.appleServiceId" placeholder="输入 Apple Service ID" class="field min-w-0 flex-1 font-mono text-[12px]" />
                <span v-if="oauth.appleServiceId" class="inline-flex shrink-0 items-center gap-0.5 text-[11px] text-ok"><CheckBadgeIcon class="h-3.5 w-3.5" />已配置</span>
              </div>
            </div>
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
            <Listbox v-model="form.otpLength" as="div" class="relative">
              <ListboxButton as="button" class="field flex w-full items-center justify-between text-left">
                <span>{{ form.otpLength }} 位</span>
                <ChevronUpDownIcon class="h-4 w-4 shrink-0 text-ink-faint" />
              </ListboxButton>
              <ListboxOptions as="ul" class="absolute top-full left-0 z-20 mt-1 w-full overflow-hidden rounded-lg border border-line bg-white py-1 shadow-panel focus:outline-none">
                <ListboxOption
                  v-for="opt in otpLengthOptions"
                  :key="opt"
                  :value="opt"
                  v-slot="{ active, selected }"
                  as="template"
                >
                  <li
                    class="flex items-center justify-between px-3 py-2 text-[13px] cursor-pointer"
                    :class="active ? 'bg-canvas-warm text-ink' : 'text-ink-soft'"
                  >
                    <span>{{ opt }} 位</span>
                    <CheckIcon v-if="selected" class="h-4 w-4 text-gold" />
                  </li>
                </ListboxOption>
              </ListboxOptions>
            </Listbox>
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

      <!-- 管理端登录防护 -->
      <div class="panel p-6">
        <h3 class="mb-1 flex items-center gap-1.5 font-display text-base font-semibold text-ink"><LockClosedIcon class="h-4 w-4 text-gold-deep" />管理端登录防护</h3>
        <p class="mb-4 text-[12px] text-ink-faint">管理员密码登录的失败锁定策略（防在线爆破）。</p>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="mb-1 block text-[13px] font-medium text-ink">失败锁定阈值（次）</label>
            <input v-model.number="form.adminLoginMaxAttempts" type="number" min="3" max="10" class="field w-full" />
            <p v-if="errors.adminLoginMaxAttempts" class="mt-1 text-[12px] text-danger">{{ errors.adminLoginMaxAttempts }}</p>
          </div>
          <div>
            <label class="mb-1 block text-[13px] font-medium text-ink">锁定时长（分钟）</label>
            <input v-model.number="form.adminLoginLockMinutes" type="number" min="5" max="60" class="field w-full" />
            <p v-if="errors.adminLoginLockMinutes" class="mt-1 text-[12px] text-danger">{{ errors.adminLoginLockMinutes }}</p>
          </div>
        </div>
        <div class="mt-3 flex items-start gap-2 rounded-luxe bg-info/8 px-4 py-3 text-[12px] text-ink-soft">
          <InformationCircleIcon class="mt-0.5 h-4 w-4 shrink-0 text-info" />
          <p>同一邮箱连续失败达到阈值后锁定指定时长（期间正确密码也不放行）；另有 IP 维度独立熔断（1 小时内失败 20 次）。锁定不区分邮箱是否真实存在，无法用于探测已注册邮箱。</p>
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

      <!-- 消费端防滥用 -->
      <div class="panel p-6">
        <h3 class="mb-1 flex items-center gap-1.5 font-display text-base font-semibold text-ink"><ShieldExclamationIcon class="h-4 w-4 text-gold-deep" />消费端防滥用</h3>
        <p class="mb-4 text-[12px] text-ink-faint">验证码校验接口的频控、递进退避与攻击告警（防持续破解与洪水）。</p>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="mb-1 block text-[13px] font-medium text-ink">验证频控（次/分钟/IP）</label>
            <input v-model.number="form.verifyIpRatePerMinute" type="number" min="5" max="300" class="field w-full" />
            <p v-if="errors.verifyIpRatePerMinute" class="mt-1 text-[12px] text-danger">{{ errors.verifyIpRatePerMinute }}</p>
          </div>
          <div>
            <label class="mb-1 block text-[13px] font-medium text-ink">攻击告警阈值（连续失败）</label>
            <input v-model.number="form.attackAlertThreshold" type="number" min="10" max="1000" class="field w-full" />
            <p v-if="errors.attackAlertThreshold" class="mt-1 text-[12px] text-danger">{{ errors.attackAlertThreshold }}</p>
          </div>
          <div class="col-span-2">
            <label class="mb-1 block text-[13px] font-medium text-ink">告警通知邮箱</label>
            <input v-model.trim="form.adminAlertEmail" type="email" placeholder="留空 = 仅记录告警日志" class="field w-full" />
            <p v-if="errors.adminAlertEmail" class="mt-1 text-[12px] text-danger">{{ errors.adminAlertEmail }}</p>
          </div>
        </div>
        <div class="mt-3 flex items-start gap-2 rounded-luxe bg-info/8 px-4 py-3 text-[12px] text-ink-soft">
          <InformationCircleIcon class="mt-0.5 h-4 w-4 shrink-0 text-info" />
          <p>验证码校验失败按「邮箱 + IP」双维度递进冷却（1–9 次失败冷却 60 秒，10–19 次 5 分钟，≥20 次 30 分钟，登录成功即解除邮箱维度）；连续失败达告警阈值时记录安全日志并邮件通知（同源 1 小时内去重）。</p>
        </div>
      </div>

      <!-- 令牌有效期 -->
      <div class="panel p-6">
        <h3 class="mb-1 flex items-center gap-1.5 font-display text-base font-semibold text-ink"><ClockIcon class="h-4 w-4 text-gold-deep" />令牌有效期</h3>
        <p class="mb-4 text-[12px] text-ink-faint">登录令牌的生命周期（仅新签发的令牌生效，存量自然过期）。</p>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="mb-1 block text-[13px] font-medium text-ink">访问令牌（分钟）</label>
            <input v-model.number="form.storeAccessTtlMinutes" type="number" min="10" max="1440" class="field w-full" />
            <p v-if="errors.storeAccessTtlMinutes" class="mt-1 text-[12px] text-danger">{{ errors.storeAccessTtlMinutes }}</p>
          </div>
          <div>
            <label class="mb-1 block text-[13px] font-medium text-ink">刷新令牌（天）</label>
            <input v-model.number="form.storeRefreshTtlDays" type="number" min="1" max="90" class="field w-full" />
            <p v-if="errors.storeRefreshTtlDays" class="mt-1 text-[12px] text-danger">{{ errors.storeRefreshTtlDays }}</p>
          </div>
          <div class="col-span-2">
            <label class="mb-1 block text-[13px] font-medium text-ink">管理员令牌（小时）</label>
            <input v-model.number="form.adminAccessTtlHours" type="number" min="1" max="24" class="field w-full" />
            <p v-if="errors.adminAccessTtlHours" class="mt-1 text-[12px] text-danger">{{ errors.adminAccessTtlHours }}</p>
          </div>
        </div>
        <div class="mt-3 flex items-start gap-2 rounded-luxe bg-info/8 px-4 py-3 text-[12px] text-ink-soft">
          <InformationCircleIcon class="mt-0.5 h-4 w-4 shrink-0 text-info" />
          <p>访问令牌随每个请求出示，调短可缩小被盗窗口（前端无感续期，用户不受影响）；刷新令牌决定「多少天不访问需重新登录」，被盗危害更大，出安全事件时可收紧到 7 天。会话可在「安全中心 → 会话应急下线」随时撤销。</p>
        </div>
      </div>

    </div>
  </div>
</template>
