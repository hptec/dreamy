<script setup lang="ts">
// PAGE-A03 / COMP-A04：用户详情 + 身份运营（凭证/会话/登录历史 + 禁用/强制下线，二次确认）
// 约束: FORM-A06 强制下线/禁用→二次确认→刷新；FUNC-022
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { useUsersStore } from '@/stores/users'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import {
  providerLabel, tierLabel, accountStatusLabel, accountStatusTone,
  loginResultLabel, formatDateTime, formatDate, initialsOf,
} from '@/utils/format'
import {
  ArrowLeftIcon, ArrowRightOnRectangleIcon, KeyIcon,
  ComputerDesktopIcon, ShieldCheckIcon, CheckBadgeIcon,
} from '@heroicons/vue/24/outline'

const route = useRoute()
const router = useRouter()
const store = useUsersStore()
const toast = useToastStore()

const userId = computed(() => String(route.params.id))
const detail = computed(() => store.detail)
const user = computed(() => store.detail?.user || null)
const identities = computed(() => store.detail?.identities || [])
const sessions = computed(() => store.detail?.sessions || [])
const loginHistory = computed(() => store.detail?.loginHistory || [])

// 二次确认弹窗状态
const showForceLogout = ref(false)
const forceScope = ref<'single' | 'all'>('all')
const forceSessionId = ref<string | undefined>(undefined)
const showToggleStatus = ref(false)
const acting = ref(false)

const isDisabled = computed(() => (user.value as any)?.status === 2)

async function load() {
  try {
    await store.fetchDetail(userId.value)
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '加载用户详情失败')
  }
}

function askForceLogoutAll() {
  forceScope.value = 'all'
  forceSessionId.value = undefined
  showForceLogout.value = true
}
function askRevokeSession(id: string) {
  forceScope.value = 'single'
  forceSessionId.value = id
  showForceLogout.value = true
}

async function doForceLogout() {
  acting.value = true
  try {
    await store.forceLogout(userId.value, forceScope.value, forceSessionId.value)
    toast.success(forceScope.value === 'all' ? '已强制下线全部会话' : '已下线该会话')
    showForceLogout.value = false
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '操作失败')
  } finally {
    acting.value = false
  }
}

async function doToggleStatus() {
  acting.value = true
  try {
    const next = isDisabled.value ? 1 : 2
    await store.toggleStatus(userId.value, next)
    toast.success(next === 2 ? '账户已禁用' : '账户已启用')
    showToggleStatus.value = false
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '操作失败')
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader :eyebrow="user?.id || userId" title="用户详情">
      <template #actions>
        <button class="btn-ghost" @click="router.push('/customers')"><ArrowLeftIcon class="h-4 w-4" />返回</button>
        <button class="btn-outline" :disabled="!sessions.length" @click="askForceLogoutAll"><ArrowRightOnRectangleIcon class="h-4 w-4" />强制下线</button>
        <button
          class="btn-danger-ghost"
          @click="showToggleStatus = true"
        ><KeyIcon class="h-4 w-4" />{{ isDisabled ? '启用账户' : '禁用账户' }}</button>
      </template>
    </PageHeader>

    <div v-if="store.detailLoading" class="panel py-16 text-center text-[13px] text-ink-faint">加载中…</div>
    <div v-else-if="!detail" class="panel py-16 text-center text-[13px] text-ink-faint">未找到该用户</div>

    <div v-else class="grid grid-cols-1 gap-6 lg:grid-cols-[320px_1fr]">
      <!-- 左：资料卡 + 登录方式 -->
      <div class="space-y-6">
        <div class="panel p-6 text-center">
          <span class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-ink text-xl font-medium text-canvas">{{ initialsOf(user?.name || user?.email) }}</span>
          <p class="mt-3 font-display text-xl font-semibold text-ink">{{ user?.name || '（未设置姓名）' }}</p>
          <p class="text-[12px] text-ink-soft">{{ user?.email || '—' }}</p>
          <div class="mt-2 flex justify-center gap-2">
            <StatusBadge tone="warn" :label="tierLabel(user?.tier)" :dot="false" />
            <StatusBadge :tone="accountStatusTone((user as any)?.status)" :label="accountStatusLabel((user as any)?.status)" />
          </div>
          <p class="mt-4 text-[11px] text-ink-faint">
            注册于 {{ formatDate(user?.joinedAt) }}
            <span v-if="user?.phone"> · {{ user?.phone }}</span>
          </p>
        </div>

        <!-- 登录方式（凭证） -->
        <div class="panel p-5">
          <h3 class="mb-3 flex items-center gap-1.5 font-display text-base font-semibold text-ink"><ShieldCheckIcon class="h-4 w-4 text-gold-deep" />登录方式</h3>
          <div v-if="identities.length" class="space-y-2.5">
            <div v-for="m in identities" :key="m.id" class="rounded-luxe border border-line p-3">
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-2">
                  <span
                    class="flex h-6 w-6 items-center justify-center rounded-full text-[11px] font-semibold"
                    :class="m.provider === 2 ? 'bg-info/12 text-info' : m.provider === 3 ? 'bg-ink/8 text-ink' : 'bg-gold/12 text-gold-deep'"
                  >{{ providerLabel(m.provider).charAt(0) }}</span>
                  <span class="text-[13px] font-medium text-ink">{{ providerLabel(m.provider) }}</span>
                  <span v-if="m.isPrimary" class="rounded-full bg-ink/8 px-1.5 py-0.5 text-[10px] text-ink-soft">主</span>
                  <CheckBadgeIcon v-if="m.verified" class="h-4 w-4 text-ok" title="已验证" />
                </div>
              </div>
              <p class="mt-1 truncate text-[11px] text-ink-soft">{{ m.identifier || '—' }}</p>
              <p v-if="m.hiddenEmail" class="mt-0.5 text-[10px] text-ink-faint">
                Apple 隐藏邮箱{{ m.relayValid ? '' : '（relay 已失效）' }}
              </p>
              <div class="mt-1 flex justify-end text-[10px] text-ink-faint">
                <span>最近登录 {{ formatDateTime(m.lastLoginAt) }}</span>
              </div>
            </div>
          </div>
          <p v-else class="rounded-luxe border border-dashed border-line py-5 text-center text-[12px] text-ink-faint">暂无登录凭证</p>
        </div>
      </div>

      <!-- 右：登录记录 + 活跃会话 -->
      <div class="space-y-6">
        <!-- 登录记录 -->
        <div class="panel">
          <div class="border-b border-line px-6 py-4"><h3 class="font-display text-lg font-semibold text-ink">登录记录</h3></div>
          <table class="data-table">
            <thead><tr><th>时间</th><th>方式</th><th>IP</th><th>设备</th><th>位置</th><th>结果</th></tr></thead>
            <tbody>
              <tr v-if="loginHistory.length === 0"><td colspan="6" class="py-8 text-center text-ink-faint">暂无登录记录</td></tr>
              <tr v-for="h in loginHistory" v-else :key="h.id">
                <td class="font-mono text-[12px] text-ink-faint whitespace-nowrap">{{ formatDateTime(h.createdAt) }}</td>
                <td><span class="rounded bg-canvas-warm px-2 py-0.5 text-[12px] text-ink-soft">{{ providerLabel(h.method) }}</span></td>
                <td class="font-mono text-[12px] text-ink-faint">{{ h.ip || '—' }}</td>
                <td class="text-[12px] text-ink-soft">{{ h.device || '—' }}</td>
                <td class="text-[12px] text-ink-faint">{{ h.location || '—' }}</td>
                <td><StatusBadge :tone="h.result === 1 ? 'ok' : 'danger'" :label="loginResultLabel(h.result)" /></td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 活跃会话 -->
        <div class="panel p-6">
          <div class="mb-4 flex items-center justify-between">
            <h3 class="flex items-center gap-1.5 font-display text-lg font-semibold text-ink"><ComputerDesktopIcon class="h-5 w-5 text-gold-deep" />活跃会话</h3>
            <button v-if="sessions.length" class="btn-ghost text-danger hover:bg-danger/8" @click="askForceLogoutAll">
              <ArrowRightOnRectangleIcon class="h-4 w-4" />强制下线全部
            </button>
          </div>
          <div v-if="sessions.length" class="space-y-2">
            <div v-for="s in sessions" :key="s.id" class="flex items-center justify-between rounded-luxe border border-line p-3">
              <div>
                <p class="text-[13px] text-ink">
                  {{ s.device || '未知设备' }}
                  <span v-if="s.browser" class="text-[11px] text-ink-faint">· {{ s.browser }}</span>
                  <span v-if="s.isCurrent" class="ml-1 rounded-full bg-ok/12 px-1.5 py-0.5 text-[10px] text-ok">当前会话</span>
                </p>
                <p class="text-[11px] text-ink-faint">{{ s.ip || '—' }} · {{ s.location || '—' }} · {{ formatDateTime(s.lastActiveAt) }}</p>
              </div>
              <button class="btn-ghost text-[12px]" @click="askRevokeSession(s.id)">下线</button>
            </div>
          </div>
          <p v-else class="rounded-luxe border border-dashed border-line py-6 text-center text-[13px] text-ink-faint">该用户当前无活跃会话</p>
        </div>
      </div>
    </div>

    <!-- 强制下线确认 -->
    <Teleport to="body">
      <div v-if="showForceLogout" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" @click.self="showForceLogout = false">
        <div class="mx-4 w-full max-w-sm rounded-2xl bg-white p-6 shadow-2xl" role="dialog" aria-modal="true">
          <h3 class="font-display text-lg font-semibold text-ink">{{ forceScope === 'all' ? '强制下线全部会话' : '下线该会话' }}</h3>
          <p class="mt-2 text-[13px] text-ink-soft">
            将使 <strong>{{ user?.name || user?.email }}</strong>
            {{ forceScope === 'all' ? '在所有设备上退出登录' : '在该设备上退出登录' }}，下次需重新通过验证登录。
          </p>
          <div class="mt-6 flex justify-end gap-3">
            <button class="btn-ghost" :disabled="acting" @click="showForceLogout = false">取消</button>
            <button class="rounded-luxe bg-danger px-4 py-2 text-[13px] font-medium text-white hover:bg-danger/90 disabled:opacity-50" :disabled="acting" @click="doForceLogout">{{ acting ? '处理中…' : '确认下线' }}</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 禁用/启用确认 -->
    <Teleport to="body">
      <div v-if="showToggleStatus" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" @click.self="showToggleStatus = false">
        <div class="mx-4 w-full max-w-sm rounded-2xl bg-white p-6 shadow-2xl" role="dialog" aria-modal="true">
          <h3 class="font-display text-lg font-semibold text-ink">{{ isDisabled ? '启用账户' : '禁用账户' }}</h3>
          <p class="mt-2 text-[13px] text-ink-soft">
            确定{{ isDisabled ? '启用' : '禁用' }} <strong>{{ user?.name || user?.email }}</strong> 的账户吗？
            <span v-if="!isDisabled">禁用后将撤销其全部会话，用户无法继续登录。</span>
          </p>
          <div class="mt-6 flex justify-end gap-3">
            <button class="btn-ghost" :disabled="acting" @click="showToggleStatus = false">取消</button>
            <button class="rounded-luxe bg-danger px-4 py-2 text-[13px] font-medium text-white hover:bg-danger/90 disabled:opacity-50" :disabled="acting" @click="doToggleStatus">{{ acting ? '处理中…' : (isDisabled ? '确认启用' : '确认禁用') }}</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
