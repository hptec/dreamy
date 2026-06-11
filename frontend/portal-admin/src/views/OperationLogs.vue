<script setup lang="ts">
// PAGE-A07 / COMP-A08：操作日志（只读 + 导出，无删除 EDGE-018）。
// 筛选 action/operator/时间；changes JSON 详情展开；导出 CSV。
// Headless-UI 注意：Combobox/Popover 根组件传 class 必须配 as（项目记忆，否则级联崩溃）
import { ref, computed, onMounted } from 'vue'
import {
  Combobox, ComboboxInput, ComboboxOptions, ComboboxOption, ComboboxButton,
  Popover, PopoverButton, PopoverPanel,
} from '@headlessui/vue'
import PageHeader from '@/components/PageHeader.vue'
import { useLogsStore } from '@/stores/logs'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { formatDateTime } from '@/utils/format'
import type { OperationLog } from '@/api/types'
import {
  MagnifyingGlassIcon, ArrowDownTrayIcon, XMarkIcon, CheckIcon, ChevronUpDownIcon,
} from '@heroicons/vue/24/outline'

const store = useLogsStore()
const toast = useToastStore()

// 后端 action 枚举（与 openapi OperationLog.action 一致）
const logActionTypes = [
  '登录', 'Google 登录', 'Apple 登录', '创建管理员', '编辑管理员', '删除管理员',
  '禁用管理员', '重置密码', '创建角色', '编辑角色', '删除角色', '权限变更',
  '账户合并', '强制下线', '认证配置变更',
]

const exporting = ref(false)
const showDetail = ref(false)
const detailLog = ref<OperationLog | null>(null)

// 操作人筛选（按当前页操作人名去重；后端按 operator_id 过滤，这里用名展示，无 id 时不强制）
const operatorQuery = ref('')
const selectedOperator = ref<string | null>(null)
const operators = computed(() => {
  const names = new Set(store.list.map((l) => l.operatorName).filter(Boolean) as string[])
  return Array.from(names).sort()
})
const filteredOperators = computed(() => {
  if (!operatorQuery.value) return operators.value
  const q = operatorQuery.value.toLowerCase()
  return operators.value.filter((u) => u.toLowerCase().includes(q))
})

// changes JSON 解析（OperationLogDTO.changes 为 JSON 字符串）
interface ChangeRow {
  field: string
  before: string
  after: string
}
function parseChanges(raw?: string | null): ChangeRow[] {
  if (!raw) return []
  try {
    const obj = JSON.parse(raw)
    if (Array.isArray(obj)) {
      return obj.map((c: any) => ({
        field: String(c.field ?? ''),
        before: c.before == null ? '—' : String(c.before),
        after: c.after == null ? '—' : String(c.after),
      }))
    }
    if (obj && typeof obj === 'object') {
      // { before:{...}, after:{...} } 或 { field: {before, after} } 兜底
      if (obj.before || obj.after) {
        const before = obj.before || {}
        const after = obj.after || {}
        const keys = new Set([...Object.keys(before), ...Object.keys(after)])
        return Array.from(keys).map((k) => ({
          field: k,
          before: before[k] == null ? '—' : String(before[k]),
          after: after[k] == null ? '—' : String(after[k]),
        }))
      }
      return Object.entries(obj).map(([field, v]: [string, any]) => ({
        field,
        before: v?.before == null ? '—' : String(v.before),
        after: v?.after == null ? '—' : String(v.after),
      }))
    }
  } catch {
    /* 非 JSON：忽略 */
  }
  return []
}

const detailChanges = computed(() => parseChanges(detailLog.value?.changes))

function changeColor(before: string, after: string): string {
  if (before === '—' && after !== '—') return 'text-ok bg-ok/6'
  if (before !== '—' && (after === '—' || after.includes('已删除'))) return 'text-danger bg-danger/6'
  return 'text-warn bg-warn/6'
}

async function load() {
  try {
    await store.fetchList()
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '加载操作日志失败')
  }
}

function applyFilters() {
  store.applyFilters().catch((e) => toast.error(e instanceof BizError ? e.message : '加载失败'))
}

function onOperatorChange(val: string | null) {
  selectedOperator.value = val
  // 服务端按 operator_id 过滤；当前仅有名，作为前端展示筛选辅助，触发刷新保持一致
  applyFilters()
}
function clearOperator() {
  selectedOperator.value = null
  operatorQuery.value = ''
  applyFilters()
}

function setActionFilter(action: string) {
  store.filterAction = store.filterAction === action ? '' : action
  applyFilters()
}

function gotoPage(p: number) {
  store.setPage(p).catch((e) => toast.error(e instanceof BizError ? e.message : '加载失败'))
}
const totalPages = () => Math.max(1, Math.ceil(store.total / store.pageSize))

function openDetail(log: OperationLog) {
  detailLog.value = log
  showDetail.value = true
}

async function exportCSV() {
  exporting.value = true
  try {
    await store.exportCsv()
    toast.success('已开始下载操作日志')
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '导出失败')
  } finally {
    exporting.value = false
  }
}

// 当前页按操作人名做前端过滤展示（服务端已分页）
const displayLogs = computed(() => {
  if (!selectedOperator.value) return store.list
  return store.list.filter((l) => l.operatorName === selectedOperator.value)
})

onMounted(load)
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="System" title="操作日志" subtitle="记录管理员的所有关键操作，支持筛选和详情查看">
      <template #actions>
        <button class="btn-ghost" :disabled="exporting" @click="exportCSV"><ArrowDownTrayIcon class="h-4 w-4" />{{ exporting ? '导出中…' : '导出 CSV' }}</button>
      </template>
    </PageHeader>

    <!-- 筛选栏 -->
    <div class="mb-4 flex flex-wrap items-start gap-3">
      <!-- 操作人搜索下拉（Headless-UI Combobox：根组件传 class 必须配 as） -->
      <Combobox
        v-model="selectedOperator"
        as="div"
        class="relative"
        style="min-width:210px"
        @update:model-value="onOperatorChange"
      >
        <div class="relative flex items-center">
          <MagnifyingGlassIcon class="pointer-events-none absolute left-3 h-4 w-4 text-ink-faint z-10" />
          <ComboboxInput
            class="field w-full pl-9 pr-8"
            placeholder="搜索操作人…"
            :display-value="(u: unknown) => (u as string) || ''"
            @change="operatorQuery = ($event.target as HTMLInputElement).value"
          />
          <ComboboxButton v-if="selectedOperator" as="button" class="absolute right-1.5" @click="clearOperator">
            <XMarkIcon class="h-4 w-4 text-ink-faint hover:text-ink" />
          </ComboboxButton>
          <ComboboxButton v-else as="button" class="absolute right-1.5">
            <ChevronUpDownIcon class="h-4 w-4 text-ink-faint" />
          </ComboboxButton>
        </div>
        <ComboboxOptions
          as="ul"
          class="absolute top-full left-0 z-20 mt-1 max-h-48 w-full overflow-y-auto rounded-lg border border-line bg-white py-1 shadow-panel"
        >
          <ComboboxOption
            v-for="u in filteredOperators"
            :key="u"
            :value="u"
            v-slot="{ active, selected }"
            as="template"
          >
            <li
              class="flex items-center justify-between px-3 py-2 text-[13px] cursor-pointer"
              :class="active ? 'bg-canvas-warm text-ink' : 'text-ink-soft'"
            >
              <span>{{ u }}</span>
              <CheckIcon v-if="selected" class="h-4 w-4 text-gold" />
            </li>
          </ComboboxOption>
          <li v-if="filteredOperators.length === 0" class="px-3 py-2 text-[13px] text-ink-faint">
            无匹配操作人
          </li>
        </ComboboxOptions>
      </Combobox>

      <div class="relative">
        <input
          v-model="store.filterFrom"
          type="datetime-local"
          step="1"
          class="field"
          style="min-width:210px"
          placeholder="开始时间"
          @change="applyFilters"
        />
      </div>
      <div class="relative">
        <input
          v-model="store.filterTo"
          type="datetime-local"
          step="1"
          class="field"
          style="min-width:210px"
          placeholder="结束时间"
          @change="applyFilters"
        />
      </div>

      <!-- 操作类型筛选（Headless-UI Popover：根组件传 class 必须配 as） -->
      <Popover as="div" class="relative" style="min-width:200px">
        <PopoverButton as="button" class="field flex w-full flex-wrap items-center gap-1.5 text-left" style="min-height:38px">
          <span
            v-if="store.filterAction"
            class="inline-flex items-center gap-0.5 rounded-full bg-gold/15 px-2 py-0.5 text-[11px] text-gold-deep cursor-pointer"
            @click.stop="setActionFilter(store.filterAction)"
          >{{ store.filterAction }} <XMarkIcon class="h-3 w-3" /></span>
          <span v-else class="text-[13px] text-ink-faint">操作类型筛选</span>
          <ChevronUpDownIcon class="ml-auto h-4 w-4 shrink-0 text-ink-faint" />
        </PopoverButton>
        <transition
          enter-active-class="transition duration-100 ease-out"
          enter-from-class="transform scale-95 opacity-0"
          enter-to-class="transform scale-100 opacity-100"
          leave-active-class="transition duration-75 ease-in"
          leave-from-class="transform scale-100 opacity-100"
          leave-to-class="transform scale-95 opacity-0"
        >
          <PopoverPanel as="div" class="absolute top-full left-0 z-20 mt-1 max-h-48 w-56 origin-top overflow-y-auto rounded-lg border border-line bg-white py-1 shadow-panel">
            <label
              v-for="a in logActionTypes"
              :key="a"
              class="flex cursor-pointer items-center gap-2 px-3 py-1.5 text-[12px] text-ink-soft hover:bg-canvas-warm"
            >
              <input
                type="radio"
                name="log-action"
                class="h-3.5 w-3.5 accent-gold"
                :checked="store.filterAction === a"
                @change="setActionFilter(a)"
              />
              {{ a }}
            </label>
          </PopoverPanel>
        </transition>
      </Popover>
      <span class="ml-auto self-center text-[12px] text-ink-faint">共 {{ store.total }} 条</span>
    </div>

    <!-- 表格 -->
    <div class="panel overflow-hidden">
      <table class="data-table">
        <thead>
          <tr>
            <th style="width:175px">时间</th>
            <th>操作人</th>
            <th>操作</th>
            <th>操作对象</th>
            <th style="width:130px">IP</th>
            <th style="width:80px" class="text-right"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="store.loading">
            <td colspan="6" class="py-12 text-center text-ink-faint">加载中…</td>
          </tr>
          <tr v-else-if="displayLogs.length === 0">
            <td colspan="6" class="py-12 text-center text-ink-faint">暂无匹配的操作日志</td>
          </tr>
          <tr v-for="l in displayLogs" v-else :key="l.id" class="cursor-pointer hover:bg-canvas-warm" @click="openDetail(l)">
            <td class="font-mono text-[12px] text-ink-faint whitespace-nowrap">{{ formatDateTime(l.createdAt) }}</td>
            <td class="text-ink">{{ l.operatorName || '系统' }}</td>
            <td><span class="rounded bg-canvas-warm px-2 py-0.5 text-[12px] text-ink-soft">{{ l.action }}</span></td>
            <td class="text-ink-soft max-w-[200px] truncate">{{ l.target || '—' }}</td>
            <td class="font-mono text-[12px] text-ink-faint">{{ l.ip || '—' }}</td>
            <td class="text-right"><span class="text-[12px] text-gold hover:underline">详情</span></td>
          </tr>
        </tbody>
      </table>

      <!-- 分页 -->
      <div v-if="totalPages() > 1" class="flex items-center justify-between border-t border-line px-4 py-3">
        <span class="text-[12px] text-ink-faint">第 {{ store.page }} / {{ totalPages() }} 页</span>
        <div class="flex gap-1">
          <button class="btn-ghost" :disabled="store.page <= 1" @click="gotoPage(store.page - 1)">上一页</button>
          <button class="btn-ghost" :disabled="store.page >= totalPages()" @click="gotoPage(store.page + 1)">下一页</button>
        </div>
      </div>
    </div>

    <!-- 详情抽屉 -->
    <Teleport to="body">
      <div v-if="showDetail" class="fixed inset-0 z-50 flex justify-end" v-dismiss="() => (showDetail = false)">
        <div class="h-full w-full max-w-lg overflow-y-auto border-l border-line bg-white shadow-2xl" role="dialog" aria-modal="true">
          <div class="sticky top-0 z-10 flex items-center justify-between border-b border-line bg-white px-6 py-4">
            <h3 class="font-display text-lg font-semibold text-ink">操作详情</h3>
            <button class="rounded-lg p-1 text-ink-faint hover:bg-canvas-warm" @click="showDetail = false">
              <XMarkIcon class="h-5 w-5" />
            </button>
          </div>

          <div v-if="detailLog" class="p-6">
            <div class="rounded-xl bg-canvas-warm p-4 space-y-2">
              <div class="flex justify-between text-[13px]">
                <span class="text-ink-faint">操作时间</span>
                <span class="font-mono text-ink">{{ formatDateTime(detailLog.createdAt) }}</span>
              </div>
              <div class="flex justify-between text-[13px]">
                <span class="text-ink-faint">操作人</span>
                <span class="text-ink font-medium">{{ detailLog.operatorName || '系统' }}</span>
              </div>
              <div class="flex justify-between text-[13px]">
                <span class="text-ink-faint">操作类型</span>
                <span class="rounded bg-ink/8 px-2 py-0.5 text-[12px] text-ink">{{ detailLog.action }}</span>
              </div>
              <div class="flex justify-between text-[13px]">
                <span class="text-ink-faint">操作对象</span>
                <span class="text-ink">{{ detailLog.target || '—' }}</span>
              </div>
              <div class="flex justify-between text-[13px]">
                <span class="text-ink-faint">IP 地址</span>
                <span class="font-mono text-ink">{{ detailLog.ip || '—' }}</span>
              </div>
            </div>

            <div class="mt-6">
              <h4 class="text-[13px] font-medium text-ink mb-3">变更对比</h4>
              <div v-if="detailChanges.length > 0">
                <table class="w-full rounded-lg border border-line overflow-hidden text-[12px]">
                  <thead>
                    <tr class="bg-canvas-warm">
                      <th class="px-3 py-2 text-left text-ink-faint font-medium">字段</th>
                      <th class="px-3 py-2 text-left text-ink-faint font-medium">变更前</th>
                      <th class="px-3 py-2 text-left text-ink-faint font-medium">变更后</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-line">
                    <tr v-for="(c, i) in detailChanges" :key="i">
                      <td class="px-3 py-2 text-ink-soft">{{ c.field }}</td>
                      <td class="px-3 py-2" :class="changeColor(c.before, c.after)">{{ c.before }}</td>
                      <td class="px-3 py-2" :class="changeColor(c.before, c.after)">{{ c.after }}</td>
                    </tr>
                  </tbody>
                </table>
                <div class="mt-3 flex items-center gap-4 text-[11px] text-ink-faint">
                  <span class="flex items-center gap-1"><span class="h-2.5 w-2.5 rounded-sm bg-ok/20 border border-ok/40" /> 新增</span>
                  <span class="flex items-center gap-1"><span class="h-2.5 w-2.5 rounded-sm bg-warn/20 border border-warn/40" /> 修改</span>
                  <span class="flex items-center gap-1"><span class="h-2.5 w-2.5 rounded-sm bg-danger/20 border border-danger/40" /> 删除</span>
                </div>
              </div>
              <div v-else class="rounded-lg border border-line py-8 text-center text-[13px] text-ink-faint">
                该操作无字段变更记录
              </div>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
