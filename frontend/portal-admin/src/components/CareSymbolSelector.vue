<script setup lang="ts">
// COMP-FC-02: CareSymbolSelector.vue
// 功能: 护理标签选择器（按 category 分组展示），支持多选，显示 Unicode 符号 + 标签文本
// 证据锚点: catalog-fabric-care-frontend-detail.md § 1.2 / acceptance.yml s-050~s-052

import { computed, onMounted, ref, watch } from 'vue'
import { catalogApi } from '@/api'
import type { CareInstruction } from '@/api/types'

// [L2-COMP-FC-02] Props 定义
interface Props {
  modelValue: number[]        // v-model 绑定的 care_instruction_ids
  readonly?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  readonly: false
})

// [L2-COMP-FC-02] Emits 定义
const emit = defineEmits<{
  'update:modelValue': [value: number[]]
}>()

// [L2-COMP-FC-02] 状态管理
const careInstructions = ref<CareInstruction[]>([])
const selectedIds = ref<Set<number>>(new Set())
const loading = ref(false)
const loadError = ref(false)

// 分类名称映射（CareCategory IntEnum）
const categoryNames: Record<number, string> = {
  1: '水洗',
  2: '漂白',
  3: '烘干',
  4: '熨烫',
  5: '干洗'
}

// [L2-COMP-FC-02] 按 category 分组（computed）
// 证据锚点: acceptance.yml s-050
const instructionsByCategory = computed(() => {
  const groups = new Map<number, CareInstruction[]>()
  careInstructions.value.forEach(instr => {
    if (!groups.has(instr.category)) groups.set(instr.category, [])
    groups.get(instr.category)!.push(instr)
  })
  // 同组按 sortOrder 排序
  groups.forEach(items => items.sort((a, b) => a.sortOrder - b.sortOrder))
  return groups
})

// [L2-INTERACTION-FC-05] 初始化加载护理标签字典
// 证据锚点: B-FC-010 (仅加载 status=active)
onMounted(async () => {
  await loadCareInstructions()
})

async function loadCareInstructions() {
  loading.value = true
  loadError.value = false
  try {
    const response = await catalogApi.listAdminCareInstructions()
    // 仅加载 active 标签
    careInstructions.value = response.items.filter(i => i.status === 1)
  } catch (error) {
    loadError.value = true
    console.error('Failed to load care instructions:', error)
  } finally {
    loading.value = false
  }
}

// [L2-INTERACTION-FC-06] 切换选中状态
// 证据锚点: acceptance.yml s-051 (多选支持)
function toggleSelection(id: number) {
  if (props.readonly) return

  if (selectedIds.value.has(id)) {
    selectedIds.value.delete(id)
  } else {
    selectedIds.value.add(id)
  }
  emit('update:modelValue', Array.from(selectedIds.value))
}

// [L2-INTERACTION-FC-07] 同步 props 变化到本地状态
watch(() => props.modelValue, (newIds) => {
  selectedIds.value = new Set(newIds)
}, { immediate: true })
</script>

<template>
  <div class="care-symbol-selector">
    <!-- 加载中 -->
    <div v-if="loading" class="flex justify-center items-center py-8">
      <div class="text-gray-500">加载护理标签中...</div>
    </div>

    <!-- 加载失败降级态 -->
    <div v-else-if="loadError" class="bg-yellow-50 border border-yellow-200 rounded-lg p-4">
      <p class="text-yellow-800 mb-3">护理标签加载失败</p>
      <button
        type="button"
        @click="loadCareInstructions"
        class="px-4 py-2 bg-yellow-600 text-white rounded hover:bg-yellow-700 text-sm"
      >
        重试
      </button>
    </div>

    <!-- 正常内容 -->
    <div v-else class="category-groups space-y-6">
      <!-- 按 category 分组渲染 -->
      <div v-for="category in [1, 2, 3, 4, 5]" :key="category" class="category-group">
        <h4 class="text-sm font-medium text-gray-700 mb-3">{{ categoryNames[category] }}</h4>

        <div class="symbol-grid grid grid-cols-4 gap-3">
          <div
            v-for="instr in instructionsByCategory.get(category)"
            :key="instr.id"
            :class="[
              'symbol-card border rounded-lg p-3 cursor-pointer transition relative',
              selectedIds.has(instr.id)
                ? 'border-blue-500 bg-blue-50'
                : 'border-gray-300 hover:border-blue-500'
            ]"
            role="checkbox"
            :aria-checked="selectedIds.has(instr.id)"
            :aria-label="`${instr.labelZh} 护理标签`"
            tabindex="0"
            @click="toggleSelection(instr.id)"
            @keydown.enter="toggleSelection(instr.id)"
            @keydown.space.prevent="toggleSelection(instr.id)"
          >
            <!-- Unicode 符号 -->
            <span
              class="symbol-icon text-3xl block text-center"
              aria-hidden="true"
            >{{ instr.symbolUnicode }}</span>

            <!-- 标签文本 -->
            <span class="symbol-label text-sm text-center block mt-2 text-gray-700">
              {{ instr.labelZh }}
            </span>

            <!-- 选中指示器 -->
            <span
              v-if="selectedIds.has(instr.id)"
              class="check-icon absolute top-2 right-2 text-blue-600 font-bold"
            >✓</span>
          </div>

          <!-- 空状态 -->
          <div
            v-if="!instructionsByCategory.has(category) || instructionsByCategory.get(category)!.length === 0"
            class="col-span-4 text-sm text-gray-500 italic py-4"
          >
            暂无{{ categoryNames[category] }}标签
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 移动端适配 */
@media (max-width: 640px) {
  .symbol-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

/* 键盘导航焦点样式 */
.symbol-card:focus {
  @apply outline-none ring-2 ring-blue-500 ring-offset-2;
}
</style>
