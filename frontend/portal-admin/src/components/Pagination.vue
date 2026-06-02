<script setup>
defineProps({ total: Number, page: { type: Number, default: 1 }, perPage: { type: Number, default: 10 } })
</script>

<template>
  <div class="flex flex-wrap items-center justify-between gap-3 px-4 py-3 text-[13px] text-ink-faint">
    <span>共 {{ total }} 条，每页 {{ perPage }} 条</span>
    <div class="flex items-center gap-1">
      <button class="rounded-luxe border border-line px-2.5 py-1 hover:bg-canvas-warm disabled:opacity-40" disabled>上一页</button>
      <button class="rounded-luxe border border-gold bg-gold px-3 py-1 font-medium text-white">1</button>
      <button class="rounded-luxe border border-line px-3 py-1 hover:bg-canvas-warm">2</button>
      <button class="rounded-luxe border border-line px-3 py-1 hover:bg-canvas-warm">3</button>
      <button class="rounded-luxe border border-line px-2.5 py-1 hover:bg-canvas-warm">下一页</button>
    </div>
  </div>
</template>
