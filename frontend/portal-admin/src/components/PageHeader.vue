<script setup>
defineProps({ title: String, subtitle: String, eyebrow: String })
</script>

<template>
  <div class="mb-6 flex flex-wrap items-end justify-between gap-4">
    <div>
      <p v-if="eyebrow" class="eyebrow mb-1">{{ eyebrow }}</p>
      <h1 class="font-display text-2xl font-semibold text-ink">{{ title }}</h1>
      <p v-if="subtitle" class="mt-1 text-[13px] text-ink-soft">{{ subtitle }}</p>
    </div>
    <div class="flex items-center gap-2">
      <slot name="actions" />
    </div>
  </div>
</template>
