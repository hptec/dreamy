<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import Pagination from '@/components/Pagination.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import { cacheApi, type CacheInvalidationLog } from '@/api'
import { BizError } from '@/api/client'
import { useToastStore } from '@/stores/toast'
import { formatDate } from '@/utils/format'
import {
  RocketLaunchIcon, CheckCircleIcon, ArrowPathIcon, ClockIcon,
  XCircleIcon, InformationCircleIcon
} from '@heroicons/vue/24/outline'

const toast = useToastStore()

const logs = ref<CacheInvalidationLog[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(50)
const total = ref(0)

// 过滤器
const filterStatus = ref<number | undefined>()
const filterResourceType = ref<string | undefined>()

const statusOptions = [
  { value: undefined, label: '全部状态' },
  { value: 0, label: '处理中' },
  { value: 1, label: '已完成' },
  { value: 2, label: '失败' },
]

const resourceTypeOptions = [
  { value: undefined, label: '全部类型' },
  { value: 'product', label: '商品' },
  { value: 'blog', label: '博客' },
  { value: 'wedding', label: '真实婚礼' },
  { value: 'category', label: '分类' },
  { value: 'tag', label: '标签' },
]

// 状态徽章配置
const statusConfig = computed(() => ({
  0: { tone: 'warn', label: '处理中', icon: ArrowPathIcon },
  1: { tone: 'ok', label: '已完成', icon: CheckCircleIcon },
  2: { tone: 'error', label: '失败', icon: XCircleIcon },
}))

// 事件类型显示名称
const eventTypeLabel = (type: string): string => {
  const map: Record<string, string> = {
    'product_created': '商品创建',
    'product_updated': '商品更新',
    'product_status_changed': '商品状态变更',
    'product_flags_changed': '商品标记变更',
    'category_changed': '分类变更',
    'tag_changed': '标签变更',
    'blog_changed': '博客变更',
    'wedding_changed': '真实婚礼变更',
  }
  return map[type] || type
}

async function fetchLogs() {
  loading.value = true
  try {
    const result = await cacheApi.getInvalidationLogs({
      page: page.value,
      pageSize: pageSize.value,
      status: filterStatus.value,
      resourceType: filterResourceType.value,
    })
    logs.value = result.records
    total.value = result.total
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p
  fetchLogs()
}

function applyFilters() {
  page.value = 1
  fetchLogs()
}

// 轮询刷新（每 5 秒）
let pollTimer: ReturnType<typeof setInterval> | null = null
function startPolling() {
  pollTimer = setInterval(() => {
    fetchLogs()
  }, 5000)
}
function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onMounted(() => {
  fetchLogs()
  startPolling()
})

// 组件销毁时停止轮询
import { onBeforeUnmount } from 'vue'
onBeforeUnmount(() => {
  stopPolling()
})
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader
      eyebrow="Cache Management"
      title="发布中心"
      subtitle="实时监控 CDN 缓存失效事件 · 自动清除机制"
    >
      <template #actions>
        <button class="btn-outline" @click="fetchLogs">
          <ArrowPathIcon class="h-4 w-4" />刷新
        </button>
      </template>
    </PageHeader>

    <!-- 过滤器 -->
    <div class="panel mb-6 p-4">
      <div class="flex flex-wrap items-center gap-4">
        <div class="flex items-center gap-2">
          <label class="text-[13px] text-ink-soft">状态</label>
          <SelectMenu v-model="filterStatus" :options="statusOptions" class="w-32" @change="applyFilters" />
        </div>
        <div class="flex items-center gap-2">
          <label class="text-[13px] text-ink-soft">资源类型</label>
          <SelectMenu v-model="filterResourceType" :options="resourceTypeOptions" class="w-32" @change="applyFilters" />
        </div>
        <div class="ml-auto flex items-center gap-2 text-[12px] text-ink-faint">
          <InformationCircleIcon class="h-4 w-4" />
          自动刷新：每 5 秒
        </div>
      </div>
    </div>

    <!-- 日志列表 -->
    <div class="panel">
      <div class="border-b border-line px-6 py-4">
        <h3 class="font-display text-lg font-semibold text-ink">缓存失效日志</h3>
        <p class="mt-1 text-[13px] text-ink-faint">最近 {{ total }} 条失效事件</p>
      </div>

      <div v-if="loading && !logs.length" class="p-12 text-center text-ink-faint">加载中…</div>

      <div v-else-if="!logs.length" class="p-12 text-center text-ink-faint">暂无失效记录</div>

      <div v-else class="divide-y divide-line">
        <div v-for="log in logs" :key="log.id" class="px-6 py-4 hover:bg-canvas-warm/40">
          <div class="flex items-start justify-between gap-4">
            <div class="min-w-0 flex-1">
              <!-- 事件类型 + 状态 -->
              <div class="flex items-center gap-2">
                <StatusBadge
                  :tone="statusConfig[log.status]?.tone || 'neutral'"
                  :label="statusConfig[log.status]?.label || '未知'"
                  :dot="log.status === 0"
                />
                <span class="text-[13px] font-medium text-ink">{{ eventTypeLabel(log.eventType) }}</span>
                <span class="text-[12px] text-ink-faint">· {{ log.resourceType }}</span>
              </div>

              <!-- Slug / Resource ID -->
              <div class="mt-1 flex flex-wrap items-center gap-2 text-[12px] text-ink-soft">
                <span v-if="log.slug" class="font-mono">slug: {{ log.slug }}</span>
                <span v-if="log.oldSlug" class="font-mono text-ink-faint">old: {{ log.oldSlug }}</span>
                <span v-if="log.resourceId" class="font-mono">id: {{ log.resourceId }}</span>
              </div>

              <!-- 受影响路径 -->
              <div v-if="log.affectedPaths?.length" class="mt-2 flex flex-wrap gap-1.5">
                <span v-for="path in log.affectedPaths.slice(0, 6)" :key="path"
                      class="rounded bg-canvas-warm px-2 py-0.5 font-mono text-[10px] text-ink-soft">
                  {{ path }}
                </span>
                <span v-if="log.affectedPaths.length > 6" class="text-[10px] text-ink-faint">
                  +{{ log.affectedPaths.length - 6 }} 更多
                </span>
              </div>

              <!-- 错误信息 -->
              <div v-if="log.errorMessage" class="mt-2 rounded bg-error/10 px-3 py-1.5 text-[12px] text-error">
                {{ log.errorMessage }}
              </div>

              <!-- 时间信息 -->
              <div class="mt-2 flex items-center gap-3 text-[11px] text-ink-faint">
                <span>触发时间：{{ formatDate(log.triggeredAt) }}</span>
                <span v-if="log.completedAt">完成时间：{{ formatDate(log.completedAt) }}</span>
                <span>触发者：{{ log.triggeredBy }}</span>
              </div>
            </div>

            <!-- 状态图标 -->
            <component
              :is="statusConfig[log.status]?.icon || InformationCircleIcon"
              class="h-5 w-5 shrink-0"
              :class="{
                'text-warn animate-spin': log.status === 0,
                'text-ok': log.status === 1,
                'text-error': log.status === 2,
              }"
            />
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div v-if="total > pageSize" class="border-t border-line px-6 py-4">
        <Pagination
          :page="page"
          :page-size="pageSize"
          :total="total"
          @update:page="onPageChange"
        />
      </div>
    </div>
  </div>
</template>
