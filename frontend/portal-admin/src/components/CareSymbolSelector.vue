<script setup lang="ts">
// CareSymbolSelector.vue - 护理标签多选编辑器（前端硬编码行业通用预设，按分类分组）
// v-model: CareItem[] { symbol, label }

import { computed, ref, watch } from 'vue'
import type { CareItem } from '@/api/types'
import { CheckIcon } from '@heroicons/vue/20/solid'
import CareSymbolIcon from '@/components/CareSymbolIcon.vue'

interface Props {
  modelValue: CareItem[]
  readonly?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  readonly: false
})

const emit = defineEmits<{
  'update:modelValue': [value: CareItem[]]
}>()

// 行业通用护理标签预设（按分类分组）
interface PresetItem {
  symbol: string
  label: string
  category: number
}

const presetCareItems: PresetItem[] = [
  // 水洗
  { symbol: '🫧', label: '冷水手洗', category: 1 },
  { symbol: '🌀', label: '30°C 机洗', category: 1 },
  { symbol: '🚫', label: '禁止水洗', category: 1 },
  // 漂白
  { symbol: '🧊', label: '禁止漂白', category: 2 },
  { symbol: '△', label: '可氯漂', category: 2 },
  // 烘干
  { symbol: '🌡', label: '低温烘干', category: 3 },
  { symbol: '🪝', label: '悬挂晾干', category: 3 },
  { symbol: '❌', label: '禁止烘干', category: 3 },
  // 熨烫
  { symbol: '♨', label: '低温熨烫', category: 4 },
  { symbol: '💨', label: '仅蒸汽', category: 4 },
  { symbol: '🚷', label: '禁止熨烫', category: 4 },
  // 干洗
  { symbol: '⭕', label: '仅限干洗', category: 5 },
  { symbol: '⊗', label: '禁止干洗', category: 5 }
]

const categoryNames: Record<number, string> = {
  1: '水洗',
  2: '漂白',
  3: '烘干',
  4: '熨烫',
  5: '干洗'
}

// 本地选中集合（用 symbol 作为唯一标识）
const selectedSymbols = ref<Set<string>>(new Set())

// 按分类分组
const itemsByCategory = computed(() => {
  const groups = new Map<number, PresetItem[]>()
  presetCareItems.forEach(item => {
    if (!groups.has(item.category)) groups.set(item.category, [])
    groups.get(item.category)!.push(item)
  })
  return groups
})

const selectedCount = computed(() => selectedSymbols.value.size)

// 切换选中
function toggleSelection(item: PresetItem) {
  if (props.readonly) return

  if (selectedSymbols.value.has(item.symbol)) {
    selectedSymbols.value.delete(item.symbol)
  } else {
    selectedSymbols.value.add(item.symbol)
  }
  selectedSymbols.value = new Set(selectedSymbols.value)

  // 同步到 modelValue
  const selected = presetCareItems.filter(i => selectedSymbols.value.has(i.symbol))
  emit('update:modelValue', selected.map(i => ({ symbol: i.symbol, label: i.label })))
}

// 同步 props → 本地状态
watch(() => props.modelValue, (newItems) => {
  selectedSymbols.value = new Set(newItems.map(i => i.symbol))
}, { immediate: true })
</script>

<template>
  <div class="care-symbol-selector space-y-6">
    <!-- 已选汇总 -->
    <div v-if="selectedCount > 0" class="flex items-center gap-2 text-[12px] text-ink-soft">
      <span class="rounded-full bg-gold/10 px-2.5 py-0.5 font-medium text-gold-deep tabular-nums">
        已选 {{ selectedCount }} 项
      </span>
    </div>

    <!-- 按 category 分组渲染 -->
    <div v-for="category in [1, 2, 3, 4, 5]" :key="category">
      <h4 class="mb-3 flex items-center gap-2 text-[12px] font-semibold uppercase tracking-luxe text-gold-deep">
        {{ categoryNames[category] }}
      </h4>

      <div class="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-5">
        <button
          v-for="item in itemsByCategory.get(category)"
          :key="item.symbol"
          type="button"
          :class="[
            'group relative flex flex-col items-center justify-center gap-2 rounded-luxe border px-3 py-4 transition-all',
            selectedSymbols.has(item.symbol)
              ? 'border-gold bg-gold/5 shadow-soft'
              : 'border-line bg-white hover:border-gold-soft hover:bg-canvas-warm/40',
            readonly ? 'cursor-default' : 'cursor-pointer'
          ]"
          role="checkbox"
          :aria-checked="selectedSymbols.has(item.symbol)"
          :aria-label="`${item.label} 护理标签`"
          :disabled="readonly"
          @click="toggleSelection(item)"
        >
          <!-- ISO 护理符号图标（与消费端同构 SVG） -->
          <CareSymbolIcon
            :symbol="item.symbol"
            :class="[
              'h-8 w-8 transition-colors',
              selectedSymbols.has(item.symbol) ? 'text-ink' : 'text-ink-soft group-hover:text-ink'
            ]"
          />

          <!-- 标签文本 -->
          <span
            :class="[
              'text-center text-[12px] leading-tight transition-colors',
              selectedSymbols.has(item.symbol) ? 'font-medium text-ink' : 'text-ink-soft'
            ]"
          >
            {{ item.label }}
          </span>

          <!-- 选中指示器 -->
          <span
            v-if="selectedSymbols.has(item.symbol)"
            class="absolute right-1.5 top-1.5 flex h-4 w-4 items-center justify-center rounded-full bg-gold text-white"
          >
            <CheckIcon class="h-3 w-3" aria-hidden="true" />
          </span>
        </button>

        <!-- 空状态 -->
        <div
          v-if="!itemsByCategory.has(category) || itemsByCategory.get(category)!.length === 0"
          class="col-span-2 rounded-luxe border border-dashed border-line py-4 text-center text-[12px] text-ink-faint sm:col-span-4 lg:col-span-5"
        >
          暂无{{ categoryNames[category] }}标签
        </div>
      </div>
    </div>
  </div>
</template>
