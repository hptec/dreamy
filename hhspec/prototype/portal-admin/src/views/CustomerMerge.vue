<script setup>
import { ref, computed } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { duplicateAccounts } from '@/data/mock'
import {
  ArrowsRightLeftIcon, InformationCircleIcon, ArrowLongRightIcon,
  CheckCircleIcon, ExclamationTriangleIcon, UserIcon
} from '@heroicons/vue/24/outline'

const candidates = ref([...duplicateAccounts])
const selectedId = ref(candidates.value[0]?.id || null)
const swapped = ref(false)         // 是否对调保留方
const merged = ref([])             // 已合并的候选 id
const showConfirm = ref(false)
const toast = ref('')

const PROVIDER_LABEL = { email: '邮箱', google: 'Google', apple: 'Apple' }
function providerLabel(p) { return PROVIDER_LABEL[p] || p }

const selected = computed(() => candidates.value.find((c) => c.id === selectedId.value) || null)
const keepAccount = computed(() => {
  if (!selected.value) return null
  return swapped.value ? selected.value.secondary : selected.value.primary
})
const mergeAccount = computed(() => {
  if (!selected.value) return null
  return swapped.value ? selected.value.primary : selected.value.secondary
})
const mergedMethods = computed(() => {
  if (!keepAccount.value || !mergeAccount.value) return []
  return [...new Set([...keepAccount.value.methods, ...mergeAccount.value.methods])]
})

const confidenceTone = { high: 'ok', medium: 'warn', low: 'danger' }
const confidenceLabel = { high: '高可信', medium: '需确认', low: '低可信' }

function select(id) {
  selectedId.value = id
  swapped.value = false
}
function doMerge() {
  merged.value.push(selectedId.value)
  candidates.value = candidates.value.filter((c) => c.id !== selectedId.value)
  selectedId.value = candidates.value[0]?.id || null
  swapped.value = false
  showConfirm.value = false
  toast.value = '账户已合并，操作已记入操作日志'
  setTimeout(() => (toast.value = ''), 2600)
}
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="用户管理" title="账户合并" subtitle="将同一个人的多个账户归并为一个，处理多登录方式 / Apple 隐藏邮箱导致的重复账户" />

    <!-- 说明条 -->
    <div class="mb-5 flex items-start gap-2 rounded-luxe bg-info/8 px-4 py-3 text-[12.5px] text-ink-soft">
      <InformationCircleIcon class="mt-0.5 h-4 w-4 shrink-0 text-info" />
      <p>识别依据：相同<strong>已验证邮箱</strong>可高可信归并；Apple <strong>Hide My Email</strong> 生成 relay 邮箱无法按邮箱匹配，需人工确认后以 Apple <code class="rounded bg-ink/6 px-1">sub</code> 为稳定主键合并。合并后订单、地址、收藏、登录方式归集到保留账户，被合并账户停用。</p>
    </div>

    <div v-if="candidates.length" class="grid grid-cols-1 gap-6 lg:grid-cols-[360px_1fr]">
      <!-- 候选列表 -->
      <div class="space-y-3">
        <p class="text-[12px] text-ink-faint">疑似重复账户（{{ candidates.length }}）</p>
        <button
          v-for="c in candidates"
          :key="c.id"
          class="block w-full rounded-luxe border p-4 text-left transition-colors"
          :class="c.id === selectedId ? 'border-gold bg-gold/5' : 'border-line hover:border-gold/50'"
          @click="select(c.id)"
        >
          <div class="flex items-center justify-between">
            <span class="text-[13px] font-medium text-ink">{{ c.primary.name }}</span>
            <StatusBadge :tone="confidenceTone[c.confidence]" :label="confidenceLabel[c.confidence]" />
          </div>
          <p class="mt-1 text-[12px] text-ink-soft">{{ c.reason }}</p>
          <p class="mt-1 font-mono text-[11px] text-ink-faint">{{ c.primary.id }} ↔ {{ c.secondary.id }}</p>
        </button>
      </div>

      <!-- 合并预览 -->
      <div v-if="selected" class="panel p-6">
        <div class="mb-4 flex items-center justify-between">
          <h3 class="font-display text-lg font-semibold text-ink">合并预览</h3>
          <button class="btn-ghost text-[12px]" @click="swapped = !swapped"><ArrowsRightLeftIcon class="h-4 w-4" />对调保留方</button>
        </div>

        <div class="flex items-stretch gap-3">
          <!-- 保留 -->
          <div class="flex-1 rounded-luxe border-2 border-ok/40 bg-ok/4 p-4">
            <div class="mb-2 flex items-center gap-1.5"><CheckCircleIcon class="h-4 w-4 text-ok" /><span class="text-[12px] font-medium text-ok">保留账户</span></div>
            <p class="font-display text-base font-semibold text-ink">{{ keepAccount.name }}</p>
            <p class="font-mono text-[11px] text-ink-faint">{{ keepAccount.id }}</p>
            <p class="mt-1 truncate text-[12px] text-ink-soft">{{ keepAccount.email }}</p>
            <div class="mt-3 flex flex-wrap gap-1">
              <span v-for="m in keepAccount.methods" :key="m" class="rounded-full bg-ink/6 px-2 py-0.5 text-[10px] text-ink-soft">{{ providerLabel(m) }}</span>
            </div>
            <div class="mt-3 border-t border-line pt-2 text-[11px] text-ink-faint">{{ keepAccount.orders }} 订单 · ${{ keepAccount.spent.toLocaleString() }} · 注册 {{ keepAccount.joined }}</div>
          </div>

          <div class="flex items-center"><ArrowLongRightIcon class="h-6 w-6 text-ink-faint" /></div>

          <!-- 合并入 -->
          <div class="flex-1 rounded-luxe border border-line bg-canvas-warm p-4">
            <div class="mb-2 flex items-center gap-1.5"><UserIcon class="h-4 w-4 text-ink-faint" /><span class="text-[12px] font-medium text-ink-soft">被合并（停用）</span></div>
            <p class="font-display text-base font-semibold text-ink line-through decoration-ink-faint/40">{{ mergeAccount.name }}</p>
            <p class="font-mono text-[11px] text-ink-faint">{{ mergeAccount.id }}</p>
            <p class="mt-1 truncate text-[12px] text-ink-soft">{{ mergeAccount.email }}</p>
            <div class="mt-3 flex flex-wrap gap-1">
              <span v-for="m in mergeAccount.methods" :key="m" class="rounded-full bg-ink/6 px-2 py-0.5 text-[10px] text-ink-soft">{{ providerLabel(m) }}</span>
            </div>
            <div class="mt-3 border-t border-line pt-2 text-[11px] text-ink-faint">{{ mergeAccount.orders }} 订单 · ${{ mergeAccount.spent.toLocaleString() }} · 注册 {{ mergeAccount.joined }}</div>
          </div>
        </div>

        <!-- 合并后结果 -->
        <div class="mt-5 rounded-luxe border border-line p-4">
          <p class="mb-3 text-[13px] font-medium text-ink">合并后（{{ keepAccount.name }} · {{ keepAccount.id }}）</p>
          <div class="grid grid-cols-3 gap-3 text-center">
            <div><p class="font-display text-lg font-semibold text-gold-deep">{{ keepAccount.orders + mergeAccount.orders }}</p><p class="text-[11px] text-ink-faint">订单合计</p></div>
            <div><p class="font-display text-lg font-semibold text-gold-deep">${{ (keepAccount.spent + mergeAccount.spent).toLocaleString() }}</p><p class="text-[11px] text-ink-faint">消费合计</p></div>
            <div><p class="font-display text-lg font-semibold text-gold-deep">{{ mergedMethods.length }}</p><p class="text-[11px] text-ink-faint">登录方式</p></div>
          </div>
          <div class="mt-3 flex flex-wrap items-center gap-1.5 border-t border-line pt-3">
            <span class="text-[11px] text-ink-faint">登录方式归集：</span>
            <span v-for="m in mergedMethods" :key="m" class="rounded-full bg-gold/12 px-2 py-0.5 text-[11px] text-gold-deep">{{ providerLabel(m) }}</span>
          </div>
        </div>

        <div v-if="selected.confidence !== 'high'" class="mt-4 flex items-start gap-2 rounded-luxe bg-warn/8 px-4 py-3 text-[12px] text-ink-soft">
          <ExclamationTriangleIcon class="mt-0.5 h-4 w-4 shrink-0 text-warn" />
          <p>{{ selected.note }}</p>
        </div>
        <p v-else class="mt-4 text-[12px] text-ink-faint">{{ selected.note }}</p>

        <div class="mt-6 flex justify-end gap-3">
          <button class="btn-outline" @click="select(selectedId)">重置</button>
          <button class="btn-primary" @click="showConfirm = true"><ArrowsRightLeftIcon class="h-4 w-4" />合并账户</button>
        </div>
      </div>
    </div>

    <!-- 空态 -->
    <div v-else class="panel flex flex-col items-center justify-center py-20 text-center">
      <CheckCircleIcon class="h-10 w-10 text-ok" />
      <p class="mt-3 font-display text-lg font-semibold text-ink">没有待处理的重复账户</p>
      <p class="mt-1 text-[13px] text-ink-faint">系统按已验证邮箱 / Apple sub / 手机号自动检测，发现疑似重复时会出现在这里。</p>
    </div>

    <!-- 确认弹窗 -->
    <Teleport to="body">
      <div v-if="showConfirm" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" @click.self="showConfirm = false">
        <div class="mx-4 w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl">
          <h3 class="font-display text-lg font-semibold text-ink">确认合并账户</h3>
          <p class="mt-2 text-[13px] text-ink-soft">
            将 <strong>{{ mergeAccount.name }}（{{ mergeAccount.id }}）</strong> 合并入
            <strong>{{ keepAccount.name }}（{{ keepAccount.id }}）</strong>。
            订单、地址、收藏与登录方式将归集到保留账户，被合并账户停用且不可恢复。
          </p>
          <div class="mt-6 flex justify-end gap-3">
            <button class="btn-ghost" @click="showConfirm = false">取消</button>
            <button class="btn-primary" @click="doMerge">确认合并</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- toast -->
    <Teleport to="body">
      <div v-if="toast" class="fixed bottom-6 left-1/2 z-50 -translate-x-1/2 rounded-luxe bg-ink px-5 py-2.5 text-[13px] text-canvas shadow-2xl">
        {{ toast }}
      </div>
    </Teleport>
  </div>
</template>
