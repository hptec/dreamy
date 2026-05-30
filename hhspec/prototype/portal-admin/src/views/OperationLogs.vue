<script setup>
import { ref, computed } from 'vue'
import {
  Combobox,
  ComboboxInput,
  ComboboxOptions,
  ComboboxOption,
  ComboboxButton,
  Popover,
  PopoverButton,
  PopoverPanel,
} from '@headlessui/vue'
import PageHeader from '@/components/PageHeader.vue'
import { auditLogs, logActionTypes } from '@/data/mock'
import {
  MagnifyingGlassIcon,
  ArrowDownTrayIcon,
  XMarkIcon,
  CheckIcon,
  ChevronUpDownIcon,
} from '@heroicons/vue/24/outline'

const logs = ref([...auditLogs])
const selectedUser = ref(null)
const userQuery = ref('')
const filterAction = ref([])
const filterDateStart = ref('')
const filterDateEnd = ref('')
const showDetail = ref(false)
const detailLog = ref(null)
const page = ref(1)
const pageSize = 15

const operators = computed(() => {
  const names = [...new Set(logs.value.map((l) => l.user))]
  return names.sort()
})

const filteredUsers = computed(() => {
  if (!userQuery.value) return operators.value
  const q = userQuery.value.toLowerCase()
  return operators.value.filter((u) => u.toLowerCase().includes(q))
})

const filteredLogs = computed(() => {
  let list = logs.value
  if (selectedUser.value) {
    list = list.filter((l) => l.user === selectedUser.value)
  }
  if (filterAction.value.length > 0) {
    list = list.filter((l) => filterAction.value.includes(l.action))
  }
  if (filterDateStart.value) {
    list = list.filter((l) => l.time >= filterDateStart.value)
  }
  if (filterDateEnd.value) {
    list = list.filter((l) => l.time <= filterDateEnd.value)
  }
  return list
})

const totalPages = computed(() => Math.ceil(filteredLogs.value.length / pageSize))
const pagedLogs = computed(() => {
  const start = (page.value - 1) * pageSize
  return filteredLogs.value.slice(start, start + pageSize)
})

function onUserChange(val) {
  selectedUser.value = val
}

function clearUser() {
  selectedUser.value = null
  userQuery.value = ''
}

function toggleAction(action) {
  const idx = filterAction.value.indexOf(action)
  if (idx >= 0) filterAction.value.splice(idx, 1)
  else filterAction.value.push(action)
}

function openDetail(log) {
  detailLog.value = log
  showDetail.value = true
}

function changeColor(field, before, after) {
  if (before === '—' && after !== '—') return 'text-ok bg-ok/6'
  if (before !== '—' && after === '—（已删除）') return 'text-danger bg-danger/6'
  return 'text-warn bg-warn/6'
}

function exportCSV() {
  const headers = ['时间', '操作人', '操作类型', '操作对象', 'IP']
  const rows = filteredLogs.value.map((l) => [l.time, l.user, l.action, l.target, l.ip])
  const csv = [headers.join(','), ...rows.map((r) => r.map((c) => `"${c}"`).join(','))].join('\n')
  const blob = new Blob([csv], { type: 'text/csv' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `operation-logs-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="System" title="操作日志" subtitle="记录管理员的所有关键操作，支持筛选和详情查看">
      <template #actions>
        <button class="btn-ghost" @click="exportCSV"><ArrowDownTrayIcon class="h-4 w-4" />导出 CSV</button>
      </template>
    </PageHeader>

    <!-- 筛选栏 -->
    <div class="mb-4 flex flex-wrap items-start gap-3">
      <!-- 操作人搜索下拉 -->
      <Combobox v-model="selectedUser" as="div" @update:model-value="onUserChange" class="relative" style="min-width:210px">
        <div class="relative flex items-center">
          <MagnifyingGlassIcon class="pointer-events-none absolute left-3 h-4 w-4 text-ink-faint z-10" />
          <ComboboxInput
            class="field w-full pl-9 pr-8"
            placeholder="搜索操作人…"
            :display-value="(u) => u || ''"
            @change="userQuery = $event.target.value"
          />
          <ComboboxButton v-if="selectedUser" class="absolute right-1.5" @click="clearUser">
            <XMarkIcon class="h-4 w-4 text-ink-faint hover:text-ink" />
          </ComboboxButton>
          <ComboboxButton v-else class="absolute right-1.5">
            <ChevronUpDownIcon class="h-4 w-4 text-ink-faint" />
          </ComboboxButton>
        </div>
        <ComboboxOptions
          class="absolute top-full left-0 z-20 mt-1 max-h-48 w-full overflow-y-auto rounded-lg border border-line bg-white py-1 shadow-panel"
        >
          <ComboboxOption
            v-for="u in filteredUsers"
            :key="u"
            :value="u"
            v-slot="{ active, selected }"
          >
            <li
              class="flex items-center justify-between px-3 py-2 text-[13px] cursor-pointer"
              :class="active ? 'bg-canvas-warm text-ink' : 'text-ink-soft'"
            >
              <span>{{ u }}</span>
              <CheckIcon v-if="selected" class="h-4 w-4 text-gold" />
            </li>
          </ComboboxOption>
          <li v-if="filteredUsers.length === 0" class="px-3 py-2 text-[13px] text-ink-faint">
            无匹配操作人
          </li>
        </ComboboxOptions>
      </Combobox>

      <div class="relative">
        <input
          v-model="filterDateStart"
          type="datetime-local"
          step="1"
          class="field"
          style="min-width:210px"
          placeholder="开始时间"
        />
      </div>
      <div class="relative">
        <input
          v-model="filterDateEnd"
          type="datetime-local"
          step="1"
          class="field"
          style="min-width:210px"
          placeholder="结束时间"
        />
      </div>
      <Popover as="div" class="relative" style="min-width:200px">
        <PopoverButton class="field flex w-full flex-wrap items-center gap-1.5 text-left" style="min-height:38px">
          <span
            v-for="a in filterAction"
            :key="a"
            class="inline-flex items-center gap-0.5 rounded-full bg-gold/15 px-2 py-0.5 text-[11px] text-gold-deep cursor-pointer"
            @click.stop="toggleAction(a)"
          >{{ a }} <XMarkIcon class="h-3 w-3" /></span>
          <span v-if="filterAction.length === 0" class="text-[13px] text-ink-faint">操作类型筛选</span>
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
          <PopoverPanel class="absolute top-full left-0 z-20 mt-1 max-h-48 w-56 origin-top overflow-y-auto rounded-lg border border-line bg-white py-1 shadow-panel">
            <label
              v-for="a in logActionTypes"
              :key="a"
              class="flex cursor-pointer items-center gap-2 px-3 py-1.5 text-[12px] text-ink-soft hover:bg-canvas-warm"
            >
              <input
                type="checkbox"
                class="h-3.5 w-3.5 rounded accent-gold"
                :checked="filterAction.includes(a)"
                @change="toggleAction(a)"
              />
              {{ a }}
            </label>
          </PopoverPanel>
        </transition>
      </Popover>
      <span class="ml-auto text-[12px] text-ink-faint self-center">共 {{ filteredLogs.length }} 条</span>
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
          <tr v-if="pagedLogs.length === 0">
            <td colspan="6" class="py-12 text-center text-ink-faint">暂无匹配的操作日志</td>
          </tr>
          <tr v-for="l in pagedLogs" :key="l.id" class="cursor-pointer hover:bg-canvas-warm" @click="openDetail(l)">
            <td class="font-mono text-[12px] text-ink-faint whitespace-nowrap">{{ l.time }}</td>
            <td class="text-ink">{{ l.user }}</td>
            <td>
              <span class="rounded bg-canvas-warm px-2 py-0.5 text-[12px] text-ink-soft">{{ l.action }}</span>
            </td>
            <td class="text-ink-soft max-w-[200px] truncate">{{ l.target }}</td>
            <td class="font-mono text-[12px] text-ink-faint">{{ l.ip }}</td>
            <td class="text-right">
              <span class="text-[12px] text-gold hover:underline">详情</span>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 分页 -->
      <div v-if="totalPages > 1" class="flex items-center justify-between border-t border-line px-4 py-3">
        <span class="text-[12px] text-ink-faint">第 {{ page }} / {{ totalPages }} 页</span>
        <div class="flex gap-1">
          <button class="btn-ghost" :disabled="page <= 1" @click="page--">上一页</button>
          <button class="btn-ghost" :disabled="page >= totalPages" @click="page++">下一页</button>
        </div>
      </div>
    </div>

    <!-- 详情抽屉 -->
    <Teleport to="body">
      <div v-if="showDetail" class="fixed inset-0 z-50 flex justify-end" @click.self="showDetail = false">
        <div class="h-full w-full max-w-lg overflow-y-auto border-l border-line bg-white shadow-2xl">
          <div class="sticky top-0 z-10 flex items-center justify-between border-b border-line bg-white px-6 py-4">
            <h3 class="font-display text-lg font-semibold text-ink">操作详情</h3>
            <button class="rounded-lg p-1 text-ink-faint hover:bg-canvas-warm" @click="showDetail = false">
              <XMarkIcon class="h-5 w-5" />
            </button>
          </div>

          <div v-if="detailLog" class="p-6">
            <!-- 元数据 -->
            <div class="rounded-xl bg-canvas-warm p-4 space-y-2">
              <div class="flex justify-between text-[13px]">
                <span class="text-ink-faint">操作时间</span>
                <span class="font-mono text-ink">{{ detailLog.time }}</span>
              </div>
              <div class="flex justify-between text-[13px]">
                <span class="text-ink-faint">操作人</span>
                <span class="text-ink font-medium">{{ detailLog.user }}</span>
              </div>
              <div class="flex justify-between text-[13px]">
                <span class="text-ink-faint">操作类型</span>
                <span class="rounded bg-ink/8 px-2 py-0.5 text-[12px] text-ink">{{ detailLog.action }}</span>
              </div>
              <div class="flex justify-between text-[13px]">
                <span class="text-ink-faint">操作对象</span>
                <span class="text-ink">{{ detailLog.target }}</span>
              </div>
              <div class="flex justify-between text-[13px]">
                <span class="text-ink-faint">IP 地址</span>
                <span class="font-mono text-ink">{{ detailLog.ip }}</span>
              </div>
              <div v-if="detailLog.ua" class="flex justify-between text-[13px]">
                <span class="text-ink-faint">User-Agent</span>
                <span class="font-mono text-ink text-[12px]">{{ detailLog.ua }}</span>
              </div>
            </div>

            <!-- 变更对比 -->
            <div class="mt-6">
              <h4 class="text-[13px] font-medium text-ink mb-3">变更对比</h4>
              <div v-if="detailLog.changes && detailLog.changes.length > 0">
                <table class="w-full rounded-lg border border-line overflow-hidden text-[12px]">
                  <thead>
                    <tr class="bg-canvas-warm">
                      <th class="px-3 py-2 text-left text-ink-faint font-medium">字段</th>
                      <th class="px-3 py-2 text-left text-ink-faint font-medium">变更前</th>
                      <th class="px-3 py-2 text-left text-ink-faint font-medium">变更后</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-line">
                    <tr v-for="(c, i) in detailLog.changes" :key="i">
                      <td class="px-3 py-2 text-ink-soft">{{ c.field }}</td>
                      <td class="px-3 py-2" :class="changeColor(c.field, c.before, c.after)">{{ c.before }}</td>
                      <td class="px-3 py-2" :class="changeColor(c.field, c.before, c.after)">{{ c.after }}</td>
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
