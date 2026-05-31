<script setup>
import { ref, reactive } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { authConfig } from '@/data/mock'
import {
  CheckBadgeIcon, KeyIcon, LinkIcon, LockClosedIcon, InformationCircleIcon
} from '@heroicons/vue/24/outline'

// 深拷贝，避免直接改 mock
const methods = ref(authConfig.methods.map((m) => ({ ...m })))
const otp = reactive({ ...authConfig.otp })
const linking = reactive({ ...authConfig.linking })
const oauth = authConfig.oauth

const toast = ref('')

function toggleMethod(m) {
  if (m.locked) return
  m.enabled = !m.enabled
}
function save() {
  toast.value = '登录与认证配置已保存，变更已记入操作日志'
  setTimeout(() => (toast.value = ''), 2600)
}
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="系统管理" title="登录与认证" subtitle="配置消费端登录方式、验证码策略与账户关联规则">
      <template #actions>
        <button class="btn-primary" @click="save">保存配置</button>
      </template>
    </PageHeader>

    <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
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
              class="relative mt-0.5 inline-flex h-6 w-10 shrink-0 items-center rounded-full transition-colors"
              :class="[m.enabled ? 'bg-ok' : 'bg-ink-faint', m.locked ? 'cursor-not-allowed opacity-60' : 'cursor-pointer']"
              :title="m.locked ? '主登录方式不可关闭' : ''"
              @click="toggleMethod(m)"
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
            <select v-model.number="otp.length" class="field w-full">
              <option :value="4">4 位</option>
              <option :value="6">6 位</option>
              <option :value="8">8 位</option>
            </select>
          </div>
          <div>
            <label class="mb-1 block text-[13px] font-medium text-ink">有效期（分钟）</label>
            <input v-model.number="otp.ttlMinutes" type="number" min="1" max="30" class="field w-full" />
          </div>
          <div>
            <label class="mb-1 block text-[13px] font-medium text-ink">重发间隔（秒）</label>
            <input v-model.number="otp.resendSeconds" type="number" min="10" max="120" class="field w-full" />
          </div>
          <div>
            <label class="mb-1 block text-[13px] font-medium text-ink">最大尝试次数</label>
            <input v-model.number="otp.maxAttempts" type="number" min="3" max="10" class="field w-full" />
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
            <input v-model.number="linking.minMethods" type="number" min="1" max="3" class="field w-20 text-center" />
          </div>
        </div>
      </div>

      <!-- OAuth 凭据 -->
      <div class="panel p-6">
        <h3 class="mb-1 flex items-center gap-1.5 font-display text-base font-semibold text-ink"><LockClosedIcon class="h-4 w-4 text-gold-deep" />OAuth 凭据</h3>
        <p class="mb-4 text-[12px] text-ink-faint">第三方登录服务端凭据（演示占位，正式环境从密钥管理读取）。</p>
        <div class="space-y-4">
          <div>
            <div class="mb-1 flex items-center justify-between">
              <label class="text-[13px] font-medium text-ink">Google Client ID</label>
              <span v-if="oauth.google.configured" class="inline-flex items-center gap-0.5 text-[11px] text-ok"><CheckBadgeIcon class="h-3.5 w-3.5" />已配置</span>
            </div>
            <input :value="oauth.google.clientId" readonly class="field w-full cursor-not-allowed bg-canvas-warm font-mono text-[12px] text-ink-faint" />
          </div>
          <div>
            <div class="mb-1 flex items-center justify-between">
              <label class="text-[13px] font-medium text-ink">Apple Service ID</label>
              <span v-if="oauth.apple.configured" class="inline-flex items-center gap-0.5 text-[11px] text-ok"><CheckBadgeIcon class="h-3.5 w-3.5" />已配置</span>
            </div>
            <input :value="oauth.apple.serviceId" readonly class="field w-full cursor-not-allowed bg-canvas-warm font-mono text-[12px] text-ink-faint" />
          </div>
          <div class="flex items-start gap-2 rounded-luxe bg-info/8 px-3 py-2.5 text-[12px] text-ink-soft">
            <InformationCircleIcon class="mt-0.5 h-4 w-4 shrink-0 text-info" />
            <p>Apple 仅在用户<strong>首次授权</strong>时返回邮箱与姓名，且支持 Hide My Email；系统以 Apple <code class="rounded bg-ink/6 px-1">sub</code> 作为稳定主键，首次即落库保存。</p>
          </div>
        </div>
      </div>
    </div>

    <!-- toast -->
    <Teleport to="body">
      <div v-if="toast" class="fixed bottom-6 left-1/2 z-50 -translate-x-1/2 rounded-luxe bg-ink px-5 py-2.5 text-[13px] text-canvas shadow-2xl">
        {{ toast }}
      </div>
    </Teleport>
  </div>
</template>
