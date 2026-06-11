<script setup lang="ts">
// 服务端分页控件（视觉沿用原型：共 N 条 + 上一页/页码窗口/下一页；接 totalElements 真实翻页）
import { computed } from 'vue'

const props = defineProps<{ total: number; page?: number; perPage?: number }>()
const emit = defineEmits<{ (e: 'change', page: number): void }>()

const page = computed(() => props.page ?? 1)
const perPage = computed(() => props.perPage ?? 10)
const totalPages = computed(() => Math.max(1, Math.ceil((props.total || 0) / perPage.value)))

/** 页码窗口（最多 5 个） */
const windowPages = computed(() => {
  const tp = totalPages.value
  const cur = page.value
  const start = Math.max(1, Math.min(cur - 2, tp - 4))
  const end = Math.min(tp, start + 4)
  const pages: number[] = []
  for (let i = start; i <= end; i++) pages.push(i)
  return pages
})

function go(p: number) {
  if (p < 1 || p > totalPages.value || p === page.value) return
  emit('change', p)
}
</script>

<template>
  <div class="flex flex-wrap items-center justify-between gap-3 px-4 py-3 text-[13px] text-ink-faint">
    <span>共 {{ total }} 条，每页 {{ perPage }} 条</span>
    <div class="flex items-center gap-1">
      <button
        class="rounded-luxe border border-line px-2.5 py-1 hover:bg-canvas-warm disabled:opacity-40"
        :disabled="page <= 1"
        @click="go(page - 1)"
      >上一页</button>
      <button
        v-for="p in windowPages"
        :key="p"
        class="rounded-luxe border px-3 py-1"
        :class="p === page ? 'border-gold bg-gold font-medium text-white' : 'border-line hover:bg-canvas-warm'"
        @click="go(p)"
      >{{ p }}</button>
      <button
        class="rounded-luxe border border-line px-2.5 py-1 hover:bg-canvas-warm disabled:opacity-40"
        :disabled="page >= totalPages"
        @click="go(page + 1)"
      >下一页</button>
    </div>
  </div>
</template>
