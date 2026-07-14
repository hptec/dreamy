<script setup lang="ts">
// FabricCompositionEditor.vue - 面料成分多层编辑器（Shell/Lining/Overlay/Trim）
// v-model: FabricComposition[] { layer: number, material: string, percentage: number }

import { computed, ref, watch } from 'vue'
import {
  Listbox,
  ListboxButton,
  ListboxOptions,
  ListboxOption
} from '@headlessui/vue'
import { CheckIcon, ChevronUpDownIcon, PlusIcon, XMarkIcon } from '@heroicons/vue/20/solid'
import { Layer } from '@/api/types'
import type { FabricComposition } from '@/api/types'

interface Props {
  modelValue: FabricComposition[]
  readonly?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  readonly: false
})

const emit = defineEmits<{
  'update:modelValue': [value: FabricComposition[]]
}>()

// 本地编辑副本
const localCompositions = ref<FabricComposition[]>([])

// Layer 映射
const layerOptions = [
  { value: Layer.Shell, label: 'Shell' },
  { value: Layer.Lining, label: 'Lining' },
  { value: Layer.Overlay, label: 'Overlay' },
  { value: Layer.Trim, label: 'Trim' }
] as const

// Material 预设（字符串，常见面料）
const materialPresets = [
  'Cotton',
  'Polyester',
  'Lace',
  'Satin',
  'Chiffon',
  'Tulle',
  'Silk',
  'Organza',
  'Spandex',
  'Nylon',
  'Viscose',
  'Elastane'
]

function getLayerName(layer: Layer): string {
  return layerOptions.find(o => o.value === layer)?.label || `Layer ${layer}`
}

// 按 layer 分组
const compositionsByLayer = computed(() => {
  const groups = new Map<Layer, FabricComposition[]>()
  localCompositions.value.forEach(comp => {
    if (!groups.has(comp.layer)) groups.set(comp.layer, [])
    groups.get(comp.layer)!.push(comp)
  })
  return groups
})

// 每层百分比总和校验
const layerErrors = computed(() => {
  const errors = new Map<Layer, string>()
  compositionsByLayer.value.forEach((items, layer) => {
    const sum = items.reduce((acc, c) => acc + (c.percentage || 0), 0)
    if (sum > 0 && Math.abs(sum - 100) > 0.01) {
      errors.set(layer, `当前总和 ${sum.toFixed(2)}%，应为 100%`)
    }
  })
  return errors
})

const hasErrors = computed(() => layerErrors.value.size > 0)

// 添加行
function addRow(layer: Layer) {
  if (props.readonly) return
  localCompositions.value.push({
    layer,
    material: materialPresets[0],
    percentage: 0
  })
  emitUpdate()
}

// 删除行
function removeRow(comp: FabricComposition) {
  if (props.readonly) return
  const idx = localCompositions.value.indexOf(comp)
  if (idx > -1) {
    localCompositions.value.splice(idx, 1)
    emitUpdate()
  }
}

// 字段变更
function onFieldChange() {
  emitUpdate()
}

function emitUpdate() {
  emit('update:modelValue', localCompositions.value.map(c => ({ ...c })))
}

// 同步 props → 本地
watch(() => props.modelValue, (newVal) => {
  localCompositions.value = newVal.map(c => ({ ...c }))
}, { immediate: true, deep: true })
</script>

<template>
  <div class="fabric-composition-editor space-y-6">
    <!-- 全局错误提示 -->
    <div
      v-if="hasErrors"
      role="alert"
      aria-live="assertive"
      class="rounded-luxe border border-danger/30 bg-danger/5 p-4 text-[12px] text-danger"
    >
      <p class="mb-1 font-semibold">面料成分占比异常：</p>
      <ul class="list-disc pl-5 space-y-1">
        <li v-for="[layer, msg] in layerErrors" :key="layer">
          <strong>{{ getLayerName(layer) }}</strong>：{{ msg }}
        </li>
      </ul>
    </div>

    <!-- 按 Layer 分组展示 -->
    <div v-for="{ value: layer } in layerOptions" :key="layer" class="space-y-3">
      <h4 class="flex items-center gap-2 text-[12px] font-semibold uppercase tracking-luxe text-gold-deep">
        {{ getLayerName(layer) }}
        <span
          v-if="layerErrors.has(layer)"
          class="rounded-full bg-danger/10 px-2 py-0.5 text-[11px] normal-case text-danger"
        >
          占比异常
        </span>
      </h4>

      <div class="space-y-2">
        <!-- 成分行 -->
        <div
          v-for="(comp, idx) in compositionsByLayer.get(layer)"
          :key="idx"
          class="flex items-center gap-3"
          role="group"
          :aria-label="`${getLayerName(layer)} 成分 ${idx + 1}`"
        >
          <!-- Material 下拉 -->
          <label :for="`material-${layer}-${idx}`" class="sr-only">材质</label>
          <Listbox
            v-model="comp.material"
            :disabled="readonly"
            as="div"
            class="relative flex-1"
            @update:model-value="onFieldChange"
          >
            <ListboxButton
              :id="`material-${layer}-${idx}`"
              class="field flex w-full items-center justify-between pr-2 text-left disabled:cursor-not-allowed disabled:opacity-60"
            >
              <span class="truncate">{{ comp.material || '选择材质' }}</span>
              <ChevronUpDownIcon class="h-4 w-4 shrink-0 text-ink-faint" aria-hidden="true" />
            </ListboxButton>

            <transition
              leave-active-class="transition duration-100 ease-in"
              leave-from-class="opacity-100"
              leave-to-class="opacity-0"
            >
              <ListboxOptions
                class="absolute z-20 mt-1 max-h-60 w-full overflow-auto rounded-luxe border border-line bg-white py-1 shadow-card focus:outline-none"
              >
                <ListboxOption
                  v-for="mat in materialPresets"
                  :key="mat"
                  v-slot="{ active, selected }"
                  :value="mat"
                  as="template"
                >
                  <li
                    :class="[
                      'flex cursor-pointer items-center justify-between px-3 py-2 text-[13px]',
                      active ? 'bg-canvas-warm text-ink' : 'text-ink-soft'
                    ]"
                  >
                    <span :class="selected ? 'font-medium text-ink' : ''">{{ mat }}</span>
                    <CheckIcon v-if="selected" class="h-4 w-4 text-gold-deep" aria-hidden="true" />
                  </li>
                </ListboxOption>
              </ListboxOptions>
            </transition>
          </Listbox>

          <!-- Percentage 输入 -->
          <label :for="`percentage-${layer}-${idx}`" class="sr-only">占比</label>
          <div class="relative w-28">
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
              placeholder="占比"
              class="field pr-7 text-right tabular-nums"
            />
            <span class="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 text-[12px] text-ink-faint">%</span>
          </div>

          <!-- 删除按钮 -->
          <button
            type="button"
            @click="removeRow(comp)"
            :disabled="readonly"
            :aria-label="`删除 ${comp.material} 成分`"
            tabindex="0"
            class="flex h-9 w-9 shrink-0 items-center justify-center rounded-luxe text-ink-faint transition-colors hover:bg-danger/10 hover:text-danger disabled:cursor-not-allowed disabled:opacity-40"
          >
            <XMarkIcon class="h-4 w-4" />
          </button>
        </div>

        <!-- 添加按钮 -->
        <button
          type="button"
          @click="addRow(layer)"
          :disabled="readonly"
          class="flex w-full items-center justify-center gap-2 rounded-luxe border border-dashed border-line py-3 text-[12px] text-ink-faint transition-colors hover:border-gold-soft hover:text-gold-deep disabled:cursor-not-allowed disabled:opacity-50"
        >
          <PlusIcon class="h-4 w-4" />
          添加{{ getLayerName(layer) }}成分
        </button>
      </div>
    </div>
  </div>
</template>
