<script setup>
import { Switch } from '@headlessui/vue'
const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue'])
</script>

<template>
  <Switch
    as="button"
    type="button"
    :model-value="modelValue"
    @update:model-value="emit('update:modelValue', $event)"
    :class="modelValue ? 'bg-gold' : 'bg-line'"
    class="relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full transition-colors focus:outline-none"
  >
    <span
      :class="modelValue ? 'translate-x-4' : 'translate-x-0.5'"
      class="inline-block h-4 w-4 translate-y-0.5 transform rounded-full bg-white shadow transition-transform"
    />
  </Switch>
</template>
