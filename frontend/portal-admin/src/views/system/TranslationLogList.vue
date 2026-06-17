<script setup lang="ts">
// PAGE-003 TranslationLogList：AI 翻译调用记录查询（FUNC-012；权限 /system/gateways 由 router meta 控制）
// 列表 + 分页 + 状态筛选；失败记录展示 error_message。沿用 GatewayConfigList 列表结构。
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import Pagination from '@/components/Pagination.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import { aiTranslateApi } from '@/api'
import { BizError } from '@/api/client'
import { AiTranslationStatus } from '@/api/types'
import type { TranslationLog } from '@/api/types'
import { useToastStore } from '@/stores/toast'
import { formatDateTime } from '@/utils/format'
import { normalizeEnumFilter } from '@/utils/validators'

const toast = useToastStore()

const logs = ref<TranslationLog[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const statusFilter = ref<AiTranslationStatus | 'all'>('all')

// 状态展示：标签 + 徽章色调（success→ok / timeout→warn / 其余失败→danger）
const STATUS_LABEL: Record<number, string> = {
  [AiTranslationStatus.SUCCESS]: '成功',
  [AiTranslationStatus.FAILED]: '失败',
  [AiTranslationStatus.TIMEOUT]: '超时',
  [AiTranslationStatus.EMPTY_RESULT]: '空结果',
  [AiTranslationStatus.RATE_LIMITED]: '限流',
}
const STATUS_TONE: Record<number, string> = {
  [AiTranslationStatus.SUCCESS]: 'ok',
  [AiTranslationStatus.FAILED]: 'danger',
  [AiTranslationStatus.TIMEOUT]: 'warn',
  [AiTranslationStatus.EMPTY_RESULT]: 'warn',
  [AiTranslationStatus.RATE_LIMITED]: 'warn',
}

const LANG_LABEL: Record<string, string> = { en: 'EN', es: 'ES', fr: 'FR' }
function langOf(code: string | null): string {
  if (!code) return '—'
  return LANG_LABEL[code] || code.toUpperCase()
}

async function load(p?: number) {
  loading.value = true
  if (p) page.value = p
  try {
    const res = await aiTranslateApi.listTranslationLogs({
      status: normalizeEnumFilter(statusFilter.value) as number | undefined,
      page: page.value,
      pageSize: pageSize.value,
    })
    logs.value = res.data
    total.value = res.totalElements
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '加载翻译记录失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => load(1))

const hasLogs = computed(() => logs.value.length > 0)
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader
      eyebrow="System"
      title="AI 翻译记录"
      subtitle="查看后台 AI 翻译调用历史：操作人、业务来源、模型、状态、耗时与 token 消耗"
    />

    <div class="panel">
      <!-- 状态筛选 -->
      <div class="flex items-center gap-2 border-b border-line px-4 py-3">
        <span class="text-[12px] text-ink-faint">状态：</span>
        <SelectMenu
          v-model="statusFilter"
          class="w-40"
          :options="[
            { value: 'all', label: '全部' },
            { value: AiTranslationStatus.SUCCESS, label: '成功' },
            { value: AiTranslationStatus.FAILED, label: '失败' },
            { value: AiTranslationStatus.TIMEOUT, label: '超时' },
            { value: AiTranslationStatus.EMPTY_RESULT, label: '空结果' },
            { value: AiTranslationStatus.RATE_LIMITED, label: '限流' },
          ]"
          @change="load(1)"
        />
      </div>

      <template v-if="loading">
        <div class="space-y-2 p-4">
          <div v-for="i in 6" :key="i" class="h-12 animate-pulse rounded-luxe bg-canvas-warm/50"></div>
        </div>
      </template>
      <EmptyState v-else-if="!hasLogs" title="暂无翻译记录" hint="后台编辑内容时使用 AI 翻译后，调用记录将在此展示。" />
      <table v-else class="data-table">
        <thead>
          <tr>
            <th>时间</th>
            <th class="text-center">操作人</th>
            <th>业务类型</th>
            <th class="text-center">语向</th>
            <th>模型</th>
            <th class="text-center">状态</th>
            <th class="text-right">耗时</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in logs" :key="row.id">
            <td class="whitespace-nowrap text-ink-soft">{{ formatDateTime(row.createdAt) }}</td>
            <td class="text-center text-ink-soft">{{ row.operatorId ?? '—' }}</td>
            <td class="text-ink-soft">
              {{ row.bizType || '—' }}
              <span v-if="row.bizRef" class="text-ink-faint">#{{ row.bizRef }}</span>
            </td>
            <td class="text-center font-mono text-[12px] text-ink-faint">
              {{ langOf(row.sourceLang) }} → {{ langOf(row.targetLang) }}
            </td>
            <td class="max-w-[180px] truncate text-ink-soft" :title="row.model || ''">{{ row.model || '—' }}</td>
            <td class="text-center">
              <StatusBadge :tone="STATUS_TONE[row.status] || 'neutral'" :label="STATUS_LABEL[row.status] || '未知'" />
              <p
                v-if="row.status !== AiTranslationStatus.SUCCESS && row.errorMessage"
                class="mt-1 max-w-[220px] truncate text-[11px] text-danger"
                :title="row.errorMessage"
              >{{ row.errorMessage }}</p>
            </td>
            <td class="whitespace-nowrap text-right text-ink-soft">{{ row.latencyMs != null ? row.latencyMs + ' ms' : '—' }}</td>
          </tr>
        </tbody>
      </table>

      <Pagination
        v-if="hasLogs"
        :total="total"
        :page="page"
        :per-page="pageSize"
        @change="load($event)"
      />
    </div>
  </div>
</template>
