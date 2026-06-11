<script setup lang="ts">
// STORE-MKT-A06 载体：商品选择器面板（闪购/案例/Lookbook 复用）
// E-CAT-08 搜索（防抖 300ms，composable 内）+ 已选 chip 集合
import { onMounted } from 'vue'
import { MagnifyingGlassIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { useProductPicker } from '@/composables/useProductPicker'
import { toRef } from 'vue'

const props = defineProps<{ modelValue: number[] }>()
const emit = defineEmits<{ (e: 'update:modelValue', ids: number[]): void }>()

const selectedIds = toRef(props, 'modelValue')
const picker = useProductPicker(selectedIds)

function toggle(id: number) {
  const next = [...props.modelValue]
  const idx = next.indexOf(id)
  if (idx >= 0) next.splice(idx, 1)
  else next.push(id)
  emit('update:modelValue', next)
}

function remove(id: number) {
  emit('update:modelValue', props.modelValue.filter((x) => x !== id))
}

onMounted(() => {
  picker.primeKnown()
  picker.doSearch().catch(() => undefined)
})
</script>

<template>
  <div class="rounded-luxe border border-line p-3">
    <!-- 已选 chips -->
    <div v-if="modelValue.length" class="mb-2 flex flex-wrap gap-1.5">
      <span
        v-for="id in modelValue"
        :key="id"
        class="flex items-center gap-1 rounded-full border border-gold bg-gold/10 px-2.5 py-0.5 text-[12px] text-gold-deep"
      >
        {{ picker.labelOf(id) }}
        <button type="button" class="text-gold-deep/70 hover:text-danger" @click="remove(id)">
          <XMarkIcon class="h-3 w-3" />
        </button>
      </span>
    </div>
    <p v-else class="mb-2 text-[12px] italic text-ink-faint">尚未选择商品（可留空）</p>

    <!-- 搜索 -->
    <div class="relative">
      <MagnifyingGlassIcon class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
      <input v-model="picker.keyword.value" class="field pl-9" placeholder="搜索商品名称…" />
    </div>

    <!-- 结果列表 -->
    <div class="mt-2 max-h-48 space-y-1 overflow-y-auto">
      <p v-if="picker.searching.value" class="py-2 text-center text-[12px] text-ink-faint">搜索中…</p>
      <template v-else>
        <button
          v-for="p in picker.results.value"
          :key="p.id"
          type="button"
          class="flex w-full items-center gap-2 rounded-luxe px-2 py-1.5 text-left text-[12.5px] transition-colors hover:bg-canvas-warm"
          :class="modelValue.includes(p.id) ? 'bg-gold/8 text-gold-deep' : 'text-ink-soft'"
          @click="toggle(p.id)"
        >
          <img v-if="p.imageUrl" :src="p.imageUrl" class="h-8 w-6 shrink-0 rounded object-cover" />
          <span v-else class="h-8 w-6 shrink-0 rounded bg-canvas-warm"></span>
          <span class="truncate">{{ p.name }}</span>
          <span v-if="modelValue.includes(p.id)" class="ml-auto text-[11px]">已选</span>
        </button>
        <p v-if="!picker.results.value.length" class="py-2 text-center text-[12px] text-ink-faint">无匹配商品</p>
      </template>
    </div>
  </div>
</template>
