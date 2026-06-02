<script setup>
import { toneClass } from './ui.js'
defineProps({ tone: { type: String, default: 'neutral' }, label: String, dot: { type: Boolean, default: true } })
</script>

<template>
  <span class="badge" :class="toneClass[tone] || toneClass.neutral">
    <span v-if="dot" class="h-1.5 w-1.5 rounded-full bg-current opacity-70"></span>
    {{ label }}
  </span>
</template>
