<script setup lang="ts">
// 安全中心 / 会话应急下线：安全事件响应快捷台——直接搜人→活跃会话→单会话/全下线。
// 复用 users 列表 + user_detail + force-logout 既有 API（CustomerDetail 的会话卡为逐个浏览路径，本页为应急直达路径）。
import { ref, computed } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { usersApi } from '@/api'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import type { Session, UserListItem } from '@/api/types'
import {
  MagnifyingGlassIcon, ArrowRightOnRectangleIcon, ShieldExclamationIcon, UserCircleIcon,
} from '@heroicons/vue/24/outline'

const toast = useToastStore()
const keyword = ref('')
const searching = ref(false)
const candidates = ref<UserListItem[]>([])
const selected = ref<UserListItem | null>(null)
const detail = ref<{ sessions: Session[] } | null>(null)
const loadingDetail = ref(false)
const acting = ref(false)

// 下线确认态
const confirmScope = ref<'all' | 'single'>('all')
const confirmSessionId = ref<number | undefined>(undefined)
const showConfirm = ref(false)

const activeSessions = computed(() => (detail.value?.sessions || []).filter((s) => (s.status ?? 1) === 1))

function methodLabel(m: unknown): string {
  const n = Number(m)
  return n === 1 ? '邮箱' : n === 2 ? 'Google' : n === 3 ? 'Apple' : '未知'
}

async function search() {
  const kw = keyword.value.trim()
  if (!kw) return
  searching.value = true
  candidates.value = []
  selected.value = null
  detail.value = null
  try {
    const page = await usersApi.listUsers({ email: kw, page: 1, pageSize: 8 })
    candidates.value = page.data || []
    if (!candidates.value.length) toast.info('未找到匹配用户')
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '搜索失败')
  } finally {
    searching.value = false
  }
}

async function pick(u: UserListItem) {
  selected.value = u
  loadingDetail.value = true
  detail.value = null
  try {
    detail.value = await usersApi.getUserDetail(u.id)
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '加载会话失败')
  } finally {
    loadingDetail.value = false
  }
}

function askRevokeAll() {
  confirmScope.value = 'all'
  confirmSessionId.value = undefined
  showConfirm.value = true
}

function askRevokeSession(id: number) {
  confirmScope.value = 'single'
  confirmSessionId.value = id
  showConfirm.value = true
}

async function doRevoke() {
  if (!selected.value) return
  acting.value = true
  try {
    await usersApi.forceLogout(selected.value.id, {
      scope: confirmScope.value,
      sessionId: confirmSessionId.value,
    })
    toast.success(confirmScope.value === 'all' ? '已强制下线全部会话' : '已下线该会话')
    showConfirm.value = false
    await pick(selected.value)
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '操作失败')
  } finally {
    acting.value = false
  }
}
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="安全中心" title="会话应急下线" subtitle="安全事件响应快捷台：定位用户 → 查看活跃会话 → 即时撤销">
      <template #actions>
        <button
          v-if="selected && activeSessions.length"
          class="btn-outline text-danger"
          :disabled="acting"
          @click="askRevokeAll"
        ><ArrowRightOnRectangleIcon class="h-4 w-4" />下线全部会话</button>
      </template>
    </PageHeader>

    <div class="grid grid-cols-1 gap-6 lg:grid-cols-3">
      <!-- 定位用户 -->
      <div class="panel p-6">
        <h3 class="mb-4 font-display text-base font-semibold text-ink">第一步 · 定位用户</h3>
        <form class="flex gap-2" @submit.prevent="search">
          <input
            v-model.trim="keyword"
            placeholder="邮箱前缀或完整邮箱"
            class="field min-w-0 flex-1"
          />
          <button type="submit" class="btn-primary shrink-0" :disabled="searching">
            <MagnifyingGlassIcon class="h-4 w-4" />{{ searching ? '…' : '搜索' }}
          </button>
        </form>
        <div v-if="candidates.length" class="mt-4 space-y-2">
          <button
            v-for="u in candidates"
            :key="u.id"
            class="flex w-full items-center gap-3 rounded-luxe border p-3 text-left transition-colors"
            :class="selected?.id === u.id ? 'border-gold bg-canvas-warm' : 'border-line hover:bg-canvas-warm/60'"
            @click="pick(u)"
          >
            <UserCircleIcon class="h-8 w-8 shrink-0 text-ink-faint" />
            <div class="min-w-0">
              <div class="truncate text-[13px] font-medium text-ink">{{ u.name || u.email }}</div>
              <div class="truncate text-[12px] text-ink-faint">{{ u.email }} · ID {{ u.id }}</div>
            </div>
          </button>
        </div>
      </div>

      <!-- 活跃会话 -->
      <div class="panel p-6 lg:col-span-2">
        <h3 class="mb-4 flex items-center gap-1.5 font-display text-base font-semibold text-ink">
          <ShieldExclamationIcon class="h-4 w-4 text-gold-deep" />第二步 · 活跃会话
          <span v-if="selected" class="ml-1 rounded-full bg-ink/6 px-2 py-0.5 text-[11px] text-ink-soft">
            {{ activeSessions.length }} 个在线
          </span>
        </h3>

        <div v-if="!selected" class="py-12 text-center text-[13px] text-ink-faint">先在左侧定位目标用户</div>
        <div v-else-if="loadingDetail" class="py-12 text-center text-[13px] text-ink-faint">加载中…</div>
        <div v-else-if="!activeSessions.length" class="py-12 text-center text-[13px] text-ink-faint">该用户当前无活跃会话</div>
        <div v-else class="space-y-2">
          <div
            v-for="s in activeSessions"
            :key="s.id"
            class="flex items-center justify-between gap-3 rounded-luxe border border-line p-3"
          >
            <div class="min-w-0">
              <div class="flex items-center gap-2 text-[13px] font-medium text-ink">
                {{ s.device || s.browser || '未知设备' }}
                <span class="rounded-full bg-ink/6 px-1.5 py-0.5 text-[10px] text-ink-soft">{{ methodLabel(s.method) }}</span>
              </div>
              <div class="mt-0.5 truncate text-[12px] text-ink-faint">
                IP {{ s.ip || '—' }} · 最近活跃 {{ s.lastActiveAt || '—' }}
              </div>
            </div>
            <button
              class="btn-ghost shrink-0 text-[12px] text-danger hover:bg-danger/8"
              :disabled="acting"
              @click="askRevokeSession(s.id)"
            >下线</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 下线确认 -->
    <div v-if="showConfirm" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40 p-4" @click.self="showConfirm = false">
      <div class="panel w-full max-w-sm p-6">
        <h3 class="font-display text-lg font-semibold text-ink">{{ confirmScope === 'all' ? '强制下线全部会话' : '下线该会话' }}</h3>
        <p class="mt-2 text-[13px] text-ink-soft">
          {{ confirmScope === 'all'
            ? `该用户所有在线会话将被立即撤销，需重新登录。确认对「${selected?.name || selected?.email}」执行？`
            : '该会话将被立即撤销，对应设备需重新登录。确认执行？' }}
        </p>
        <div class="mt-5 flex justify-end gap-2">
          <button class="btn-ghost" @click="showConfirm = false">取消</button>
          <button class="rounded-luxe bg-danger px-4 py-2 text-[13px] font-medium text-white hover:bg-danger/90 disabled:opacity-50" :disabled="acting" @click="doRevoke">
            {{ acting ? '处理中…' : '确认下线' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
