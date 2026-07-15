<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import Pagination from '@/components/Pagination.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { cacheApi, CacheTaskStatus, type CacheInvalidationTask, type CacheTaskSummary } from '@/api'
import { BizError } from '@/api/client'
import { useToastStore } from '@/stores/toast'
import { formatUtcDateTime } from '@/utils/format'
import {
  ArrowPathIcon, CheckCircleIcon, ChevronDownIcon, ChevronRightIcon,
  ClockIcon, ExclamationTriangleIcon, PlusIcon, XCircleIcon,
} from '@heroicons/vue/24/outline'

const toast = useToastStore()
const tasks = ref<CacheInvalidationTask[]>([])
const summary = ref<CacheTaskSummary>({ scheduled: 0, pending: 0, running: 0, retrying: 0, succeeded: 0, partial: 0, failed: 0 })
const targets = ref<string[]>([])
const selectedTargets = ref<string[]>([])
const reason = ref('')
const manualOpen = ref(false)
const manualBusy = ref(false)
const loading = ref(false)
const expanded = ref<Set<number>>(new Set())
const page = ref(1)
const pageSize = 30
const total = ref(0)
const filterStatus = ref<number | undefined>()
const filterMode = ref<string | undefined>()
let timer: ReturnType<typeof setInterval> | undefined

const statusOptions = [
  { value: undefined, label: '全部状态' },
  { value: CacheTaskStatus.SCHEDULED, label: '计划中' },
  { value: CacheTaskStatus.PENDING, label: '待执行' },
  { value: CacheTaskStatus.RUNNING, label: '执行中' },
  { value: CacheTaskStatus.RETRYING, label: '等待重试' },
  { value: CacheTaskStatus.SUCCEEDED, label: '成功' },
  { value: CacheTaskStatus.PARTIAL, label: '部分失败' },
  { value: CacheTaskStatus.FAILED, label: '失败' },
  { value: CacheTaskStatus.CANCELLED, label: '已取消' },
]
const modeOptions = [
  { value: undefined, label: '全部来源' },
  { value: 'BUSINESS_WRITE', label: '业务操作' },
  { value: 'SCHEDULED', label: '计划任务' },
  { value: 'SYSTEM_EVENT', label: '系统事件' },
  { value: 'MANUAL', label: '手动清理' },
]

const statusConfig: Record<number, { label: string; tone: string }> = {
  [CacheTaskStatus.PENDING]: { label: '待执行', tone: 'warn' },
  [CacheTaskStatus.SUCCEEDED]: { label: '成功', tone: 'ok' },
  [CacheTaskStatus.FAILED]: { label: '失败', tone: 'danger' },
  [CacheTaskStatus.SCHEDULED]: { label: '计划中', tone: 'neutral' },
  [CacheTaskStatus.RUNNING]: { label: '执行中', tone: 'warn' },
  [CacheTaskStatus.PARTIAL]: { label: '部分失败', tone: 'danger' },
  [CacheTaskStatus.CANCELLED]: { label: '已取消', tone: 'neutral' },
  [CacheTaskStatus.SKIPPED]: { label: '已跳过', tone: 'neutral' },
  [CacheTaskStatus.RETRYING]: { label: '等待重试', tone: 'warn' },
}
const targetLabels: Record<string, string> = {
  CATALOG_PRODUCTS: '商品列表', CATALOG_PRODUCT: '商品详情', CATALOG_SEARCH: '商品搜索',
  CATALOG_RECO: '商品推荐', CATALOG_CATEGORIES: '分类树', CATALOG_COLLECTIONS: '集合',
  MARKETING_BANNERS: 'Banner / Hero', MARKETING_BLOGS: 'Blog 列表', MARKETING_BLOG: 'Blog 详情',
  MARKETING_WEDDINGS: '婚礼案例列表', MARKETING_WEDDING: '婚礼案例详情',
  MARKETING_LOOKBOOKS: 'Lookbook 列表', MARKETING_LOOKBOOK: 'Lookbook 详情',
  MARKETING_GUIDES: '指南', MARKETING_FLASH: '限时促销', REVIEW_REVIEWS: '商品评价',
  SITE_HOME: '首页聚合', SITE_NAVIGATION: '导航', SITE_FOOTER: '页脚', SITE_ANNOUNCEMENTS: '公告',
  REVIEW_QUESTIONS: '商品问答', SHIPPING_CARRIERS: '承运商', SHIPPING_RATES: '运费规则',
  TRADING_EXCHANGE_RATES: '汇率',
}

const activeCount = computed(() => summary.value.pending + summary.value.running + summary.value.retrying)
const failureCount = computed(() => summary.value.failed + summary.value.partial)

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const [taskPage, taskSummary] = await Promise.all([
      cacheApi.getTasks({ page: page.value, pageSize, status: filterStatus.value, triggerMode: filterMode.value }),
      cacheApi.getSummary(),
    ])
    tasks.value = taskPage.data
    total.value = taskPage.totalElements
    summary.value = taskSummary
  } catch (e) {
    if (!silent) toast.error(e instanceof BizError ? e.message : '缓存任务加载失败')
  } finally {
    loading.value = false
  }
}

function toggleExpanded(id: number) {
  const next = new Set(expanded.value)
  next.has(id) ? next.delete(id) : next.add(id)
  expanded.value = next
}

function toggleTarget(target: string) {
  selectedTargets.value = selectedTargets.value.includes(target)
    ? selectedTargets.value.filter((item) => item !== target)
    : [...selectedTargets.value, target]
}

async function submitManual() {
  if (!selectedTargets.value.length) return toast.error('请至少选择一个缓存目标')
  manualBusy.value = true
  try {
    const result = await cacheApi.createManualTask({ targets: selectedTargets.value, reason: reason.value.trim() })
    toast.success(`任务 #${result.taskId} 已进入执行队列`)
    manualOpen.value = false
    selectedTargets.value = []
    reason.value = ''
    await load()
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '创建任务失败')
  } finally {
    manualBusy.value = false
  }
}

async function retry(task: CacheInvalidationTask) {
  try {
    await cacheApi.retryTask(task.id)
    toast.success(`任务 #${task.id} 已重新入队`)
    await load()
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '重试失败')
  }
}

function applyFilters() {
  page.value = 1
  load()
}

onMounted(async () => {
  try { targets.value = await cacheApi.getTargets() } catch { targets.value = [] }
  await load()
  timer = setInterval(() => load(true), 3000)
})
onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="System Operations" title="缓存管理" subtitle="共享业务缓存的计划、执行、重试与逐目标结果">
      <template #actions>
        <button class="btn-outline" :disabled="loading" @click="load()"><ArrowPathIcon class="h-4 w-4" />刷新</button>
        <button class="btn-gold" @click="manualOpen = !manualOpen"><PlusIcon class="h-4 w-4" />手动清理</button>
      </template>
    </PageHeader>

    <div class="mb-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      <div class="panel p-4"><p class="text-[12px] text-ink-faint">执行中</p><p class="mt-1 text-2xl font-semibold text-ink">{{ activeCount }}</p></div>
      <div class="panel p-4"><p class="text-[12px] text-ink-faint">计划任务</p><p class="mt-1 text-2xl font-semibold text-ink">{{ summary.scheduled }}</p></div>
      <div class="panel p-4"><p class="text-[12px] text-ink-faint">累计成功</p><p class="mt-1 text-2xl font-semibold text-ok">{{ summary.succeeded }}</p></div>
      <div class="panel p-4"><p class="text-[12px] text-ink-faint">待处理异常</p><p class="mt-1 text-2xl font-semibold" :class="failureCount ? 'text-error' : 'text-ink'">{{ failureCount }}</p></div>
    </div>

    <div v-if="manualOpen" class="panel mb-6 p-5">
      <div class="flex items-start justify-between"><div><h3 class="font-display text-lg font-semibold">手动清理</h3><p class="mt-1 text-[12px] text-ink-faint">只允许选择已登记的共享业务缓存，不会操作认证、Session、OTP、限流或幂等键。</p></div></div>
      <div class="mt-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
        <label v-for="target in targets" :key="target" class="flex cursor-pointer items-center gap-2 rounded-luxe border border-line px-3 py-2 text-[12px] hover:bg-canvas-warm">
          <input type="checkbox" :checked="selectedTargets.includes(target)" @change="toggleTarget(target)" />
          <span>{{ targetLabels[target] || target }}</span>
        </label>
      </div>
      <div class="mt-4 flex flex-col gap-3 sm:flex-row">
        <input v-model="reason" class="field flex-1" maxlength="255" placeholder="清理原因（建议填写）" />
        <button class="btn-gold" :disabled="manualBusy || !selectedTargets.length" @click="submitManual">{{ manualBusy ? '提交中…' : '创建清理任务' }}</button>
      </div>
    </div>

    <div class="panel mb-4 p-4">
      <div class="flex flex-wrap gap-3">
        <SelectMenu v-model="filterStatus" :options="statusOptions" class="w-36" @change="applyFilters" />
        <SelectMenu v-model="filterMode" :options="modeOptions" class="w-36" @change="applyFilters" />
      </div>
    </div>

    <div class="panel overflow-hidden">
      <div v-if="loading && !tasks.length" class="p-12 text-center text-ink-faint">加载中…</div>
      <div v-else-if="!tasks.length" class="p-12 text-center text-ink-faint">暂无缓存任务</div>
      <div v-else class="divide-y divide-line">
        <div v-for="task in tasks" :key="task.id" class="px-5 py-4">
          <div class="flex items-start gap-3">
            <button class="mt-0.5 text-ink-faint" @click="toggleExpanded(task.id)">
              <ChevronDownIcon v-if="expanded.has(task.id)" class="h-4 w-4" /><ChevronRightIcon v-else class="h-4 w-4" />
            </button>
            <div class="min-w-0 flex-1">
              <div class="flex flex-wrap items-center gap-2">
                <StatusBadge :tone="statusConfig[task.status]?.tone || 'neutral'" :label="statusConfig[task.status]?.label || '未知'" />
                <span class="font-medium text-ink">{{ task.triggerPoint }}</span>
                <span class="text-[12px] text-ink-faint">#{{ task.id }} · {{ task.triggerMode }}</span>
              </div>
              <div class="mt-1 text-[12px] text-ink-soft">
                {{ task.resourceType }}<span v-if="task.resourceId">:{{ task.resourceId }}</span>
                <span v-if="task.resourceLabel"> · {{ task.resourceLabel }}</span>
              </div>
              <div class="mt-2 flex flex-wrap gap-1.5">
                <span v-for="target in task.targets" :key="target" class="rounded bg-canvas-warm px-2 py-0.5 text-[10px] text-ink-soft">{{ targetLabels[target] || target }}</span>
              </div>
              <p v-if="task.errorMessage" class="mt-2 rounded bg-error/10 px-3 py-2 text-[12px] text-error">{{ task.errorMessage }}</p>
              <div class="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-ink-faint">
                <span><ClockIcon class="mr-1 inline h-3.5 w-3.5" />计划 {{ formatUtcDateTime(task.scheduledAt) }}</span>
                <span>创建 {{ formatUtcDateTime(task.triggeredAt) }}</span>
                <span v-if="task.completedAt">完成 {{ formatUtcDateTime(task.completedAt) }}</span>
                <span v-if="task.nextRetryAt">重试 {{ formatUtcDateTime(task.nextRetryAt) }}</span>
                <span>触发者 {{ task.triggeredBy }}</span>
                <span>执行 {{ task.attemptCount }}/{{ task.maxAttempts }} 次</span>
              </div>
            </div>
            <button v-if="task.status === CacheTaskStatus.FAILED || task.status === CacheTaskStatus.PARTIAL" class="btn-outline shrink-0" @click="retry(task)"><ArrowPathIcon class="h-4 w-4" />重试</button>
          </div>

          <div v-if="expanded.has(task.id)" class="ml-7 mt-4 rounded-luxe border border-line bg-canvas-warm/40 p-3">
            <p v-if="!task.steps.length" class="text-[12px] text-ink-faint">尚未开始执行</p>
            <div v-for="step in task.steps" :key="step.id" class="flex items-start gap-3 border-b border-line/70 py-2 last:border-0">
              <CheckCircleIcon v-if="step.status === 1" class="mt-0.5 h-4 w-4 shrink-0 text-ok" />
              <XCircleIcon v-else-if="step.status === 2" class="mt-0.5 h-4 w-4 shrink-0 text-error" />
              <ExclamationTriangleIcon v-else class="mt-0.5 h-4 w-4 shrink-0 text-warn" />
              <div class="min-w-0"><p class="text-[12px] font-medium">{{ targetLabels[step.target] || step.target }} · 第 {{ step.attempt }} 次</p><p class="text-[11px] text-ink-faint">{{ formatUtcDateTime(step.startedAt) }}<span v-if="step.completedAt"> → {{ formatUtcDateTime(step.completedAt) }}</span></p><p v-if="step.resultDetail" class="text-[11px] text-ink-faint">{{ step.resultDetail }}</p><p v-if="step.errorMessage" class="text-[11px] text-error">{{ step.errorMessage }}</p></div>
            </div>
          </div>
        </div>
      </div>
      <div v-if="total > pageSize" class="border-t border-line px-5 py-4"><Pagination :page="page" :page-size="pageSize" :total="total" @update:page="page = $event; load()" /></div>
    </div>
  </div>
</template>
