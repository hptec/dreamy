<script setup lang="ts">
// COMP-FC-01: FabricCompositionEditor.vue
// 功能: 商品编辑表单中的面料成分编辑器，支持多层次（Shell/Lining/Overlay/Trim）动态添加/删除行，percentage 总和校验
// 证据锚点: catalog-fabric-care-frontend-detail.md § 1.1 / acceptance.yml s-040~s-043

import { computed, ref, watch } from 'vue'

// [L2-COMP-FC-01] Props 定义
interface Props {
  modelValue: FabricComposition[]  // v-model 绑定
  readonly?: boolean               // 只读模式（查看商品详情）
}

export interface FabricComposition {
  id?: number           // 编辑模式有 id，新增模式无
  layer: number         // 1..4 枚举
  material: number      // 1..10 枚举
  percentage: number    // 0..100 decimal(5,2)
  sortOrder: number     // 同层排序
}

const props = withDefaults(defineProps<Props>(), {
  readonly: false
})

// [L2-COMP-FC-01] Emits 定义
const emit = defineEmits<{
  'update:modelValue': [value: FabricComposition[]]
  'validation-error': [errors: Map<number, string>]  // layer → 错误信息
}>()

// [L2-COMP-FC-01] 状态管理
const localCompositions = ref<FabricComposition[]>([])
const layerErrors = ref<Map<number, string>>(new Map())
const isDirty = ref(false)

// 枚举映射（L2 设计 IntEnum 整数契约）
const layerNames: Record<number, string> = {
  1: 'Shell',
  2: 'Lining',
  3: 'Overlay',
  4: 'Trim'
}

const materialNames: Record<number, string> = {
  1: '棉',
  2: '涤纶',
  3: '蕾丝',
  4: '缎面',
  5: '雪纺',
  6: '薄纱',
  7: '丝绸',
  8: '欧根纱',
  9: '氨纶',
  10: '尼龙'
}

function getLayerName(layer: number): string {
  return layerNames[layer] || `Layer ${layer}`
}

function getMaterialName(material: number): string {
  return materialNames[material] || `Material ${material}`
}

// [L2-COMP-FC-01] 按 layer 分组（computed）
const compositionsByLayer = computed(() => {
  const groups = new Map<number, FabricComposition[]>()
  localCompositions.value.forEach(comp => {
    if (!groups.has(comp.layer)) groups.set(comp.layer, [])
    groups.get(comp.layer)!.push(comp)
  })
  // 同层按 sortOrder 排序
  groups.forEach(items => items.sort((a, b) => a.sortOrder - b.sortOrder))
  return groups
})

// [L2-INTERACTION-FC-01] 添加行（按层分组添加按钮）
// 证据锚点: acceptance.yml s-040
function addRow(layer: number) {
  const existingInLayer = compositionsByLayer.value.get(layer) || []
  const maxSortOrder = existingInLayer.length > 0
    ? Math.max(...existingInLayer.map(c => c.sortOrder))
    : -1

  localCompositions.value.push({
    layer,
    material: 1,  // 默认 Cotton
    percentage: 0,
    sortOrder: maxSortOrder + 1
  })
  isDirty.value = true
}

// [L2-INTERACTION-FC-02] 删除行
// 证据锚点: acceptance.yml s-043
function removeRow(comp: FabricComposition) {
  const index = localCompositions.value.indexOf(comp)
  if (index >= 0) {
    localCompositions.value.splice(index, 1)
    isDirty.value = true
    validatePercentageSum()  // 删除后重新校验
  }
}

// [L2-INTERACTION-FC-03] percentage 总和校验（每层独立校验）
// 证据锚点: acceptance.yml s-041 (总和=100%), s-042 (总和超100%)
function validatePercentageSum() {
  layerErrors.value.clear()

  compositionsByLayer.value.forEach((items, layer) => {
    const sum = items.reduce((acc, c) => acc + c.percentage, 0)
    if (sum > 100) {
      layerErrors.value.set(layer, `${getLayerName(layer)} 总和超过 100% (当前 ${sum.toFixed(2)}%)`)
    } else if (sum < 100 && sum > 0) {
      layerErrors.value.set(layer, `${getLayerName(layer)} 总和不足 100% (当 ${sum.toFixed(2)}%)`)
    }
  })

  emit('validation-error', layerErrors.value)
  return layerErrors.value.size === 0
}

// [L2-INTERACTION-FC-04] 字段变更（material/percentage）
function onFieldChange() {
  isDirty.value = true
  validatePercentageSum()
  emit('update:modelValue', localCompositions.value)
}

// 同步 props 变化到本地状态
watch(() => props.modelValue, (newValue) => {
  localCompositions.value = [...newValue]
  validatePercentageSum()
}, { immediate: true, deep: true })
</script>

<template>
  <div class="fabric-composition-editor space-y-6">
    <!-- 按层分组渲染 -->
    <div v-for="layer in [1, 2, 3, 4]" :key="layer" class="layer-group border border-gray-200 rounded-lg p-4">
      <div class="layer-header flex justify-between items-center mb-3">
        <h4 class="text-sm font-medium text-gray-700">{{ getLayerName(layer) }}</h4>
        <button
          type="button"
          @click="addRow(layer)"
          :disabled="readonly"
          class="text-sm text-blue-600 hover:text-blue-800 disabled:text-gray-400 disabled:cursor-not-allowed"
        >
          + 添加{{ getLayerName(layer) }}成分
        </button>
      </div>

      <!-- 错误提示（percentage 总和不对） -->
      <div
        v-if="layerErrors.has(layer)"
        :id="`error-${layer}`"
        class="error-banner bg-red-50 border border-red-200 text-red-700 px-4 py-2 rounded mb-3"
        role="alert"
      >
        {{ layerErrors.get(layer) }}
      </div>

      <!-- 成分行列表 -->
      <div class="composition-rows space-y-2">
        <div
          v-for="(comp, idx) in compositionsByLayer.get(layer)"
          :key="comp.id || idx"
          class="composition-row flex items-center gap-3"
          role="group"
          :aria-label="`${getLayerName(layer)} 成分 ${idx + 1}`"
        >
          <!-- Material 下拉 -->
          <label :for="`material-${layer}-${idx}`" class="sr-only">材质</label>
          <select
            :id="`material-${layer}-${idx}`"
            v-model="comp.material"
            @change="onFieldChange"
            :disabled="readonly"
            class="form-select rounded-md border-gray-300 text-sm flex-1"
          >
            <option :value="1">棉</option>
            <option :value="2">涤纶</option>
            <option :value="3">蕾丝</option>
            <option :value="4">缎面</option>
            <option :value="5">雪纺</option>
            <option :value="6">薄纱</option>
            <option :value="7">丝绸</option>
            <option :value="8">欧根纱</option>
            <option :value="9">氨纶</option>
            <option :value="10">尼龙</option>
          </select>

          <!-- Percentage 输入 -->
          <label :for="`percentage-${layer}-${idx}`" class="sr-only">占比</label>
          <div class="relative">
            <input
              :id="`percentage-${layer}-${idx}`"
              type="number"
              v-model.number="comp.percentage"
              @input="onFieldChange"
              :disabled="readonly"
              :aria-describedby="layerErrors.has(layer) ? `error-${layer}` : undefined"
              min="0"
              max="100"
              step="0.01"
              placeholder="占比 (%)"
              class="form-input w-24 rounded-md border-gray-300 text-sm pr-8"
            />
            <span class="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 text-sm">%</span>
          </div>

          <!-- 删除按钮 -->
          <button
            type="button"
            @click="removeRow(comp)"
            :disabled="readonly"
            :aria-label="`删除 ${getMaterialName(comp.material)} 成分`"
            tabindex="0"
            class="text-red-600 hover:text-red-800 disabled:text-gray-400 disabled:cursor-not-allowed text-sm"
          >
            删除
          </button>
        </div>

        <!-- 空状态 -->
        <div v-if="!compositionsByLayer.has(layer) || compositionsByLayer.get(layer)!.length === 0"
             class="text-sm text-gray-500 italic">
          暂无{{ getLayerName(layer) }}成分
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 确保输入框样式一致 */
.form-select,
.form-input {
  @apply focus:ring-2 focus:ring-blue-500 focus:border-blue-500;
}
</style>
