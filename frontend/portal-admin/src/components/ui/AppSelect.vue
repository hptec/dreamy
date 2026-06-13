<script setup lang="ts">
import { ref } from 'vue'

type Opt = string | { value: string | number; label: string; disabled?: boolean }

const props = defineProps<{
  modelValue: string | number
  options: Opt[]
  legacy?: string[]
  placeholder?: string
  disabled?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [string | number] }>()

const open = ref(false)
function itemVal(o: Opt) { return typeof o === 'string' ? o : o.value }
function itemLabel(o: Opt) { return typeof o === 'string' ? o : o.label }
function itemDisabled(o: Opt) { return typeof o === 'string' ? false : (o.disabled ?? false) }

function pick(v: string | number) { emit('update:modelValue', v); open.value = false }
function onFocusOut() { setTimeout(() => { open.value = false }, 150) }

const displayLabel = () => {
  const found = props.options.find(o => itemVal(o) === props.modelValue)
  return found ? itemLabel(found) : (props.legacy?.includes(String(props.modelValue)) ? String(props.modelValue) : '')
}
</script>

<template>
  <div class="relative" @focusout="onFocusOut">
    <button
      type="button"
      class="field flex items-center justify-between text-left"
      :class="[!modelValue && 'text-ink-faint', disabled && 'cursor-not-allowed opacity-50']"
      :disabled="disabled"
      @click="!disabled && (open = !open)"
    >
      <span>{{ displayLabel() || placeholder || '请选择…' }}</span>
      <svg class="h-4 w-4 shrink-0 text-ink-soft transition-transform" :class="open && 'rotate-180'" viewBox="0 0 20 20" fill="currentColor">
        <path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 011.06.02L10 11.168l3.71-3.938a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z" clip-rule="evenodd"/>
      </svg>
    </button>
    <ul v-if="open" class="absolute inset-x-0 top-full z-50 mt-1 max-h-60 overflow-y-auto rounded-luxe border border-line bg-white py-1 shadow-panel">
      <li v-if="placeholder !== undefined">
        <button type="button" class="w-full px-3 py-2 text-left text-[13px] text-ink-faint hover:bg-canvas-warm" @click="pick('')">{{ placeholder || '请选择…' }}</button>
      </li>
      <li v-for="o in options" :key="itemVal(o)">
        <button
          type="button"
          class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] hover:bg-canvas-warm"
          :class="itemDisabled(o) ? 'cursor-not-allowed opacity-40 text-ink-soft' : 'text-ink'"
          :disabled="itemDisabled(o)"
          @click="!itemDisabled(o) && pick(itemVal(o))"
        >
          <svg v-if="modelValue === itemVal(o)" class="h-3.5 w-3.5 shrink-0 text-gold" viewBox="0 0 20 20" fill="currentColor">
            <path fill-rule="evenodd" d="M16.704 4.153a.75.75 0 01.143 1.052l-8 10.5a.75.75 0 01-1.127.075l-4.5-4.5a.75.75 0 011.06-1.06l3.894 3.893 7.48-9.817a.75.75 0 011.05-.143z" clip-rule="evenodd"/>
          </svg>
          <span v-else class="w-3.5 shrink-0"/>
          {{ itemLabel(o) }}
        </button>
      </li>
      <li v-for="o in (legacy ?? [])" :key="'l-'+o">
        <button type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-ink-soft line-through hover:bg-canvas-warm" @click="pick(o)">
          <svg v-if="modelValue === o" class="h-3.5 w-3.5 shrink-0 text-gold" viewBox="0 0 20 20" fill="currentColor">
            <path fill-rule="evenodd" d="M16.704 4.153a.75.75 0 01.143 1.052l-8 10.5a.75.75 0 01-1.127.075l-4.5-4.5a.75.75 0 011.06-1.06l3.894 3.893 7.48-9.817a.75.75 0 011.05-.143z" clip-rule="evenodd"/>
          </svg>
          <span v-else class="w-3.5 shrink-0"/>
          {{ o }}
        </button>
      </li>
    </ul>
  </div>
</template>
