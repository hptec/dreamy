<template>
  <Listbox :model-value="modelValue" as="div" class="relative" @update:model-value="onChange">
    <ListboxButton class="field flex w-full items-center justify-between gap-2 text-left">
      <span :class="['truncate', selectedLabel ? 'text-ink' : 'text-ink-faint']">{{ selectedLabel || placeholder }}</span>
      <ChevronUpDownIcon class="h-4 w-4 shrink-0 text-ink-faint" />
    </ListboxButton>
    <transition
      leave-active-class="transition duration-100 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <ListboxOptions
        class="absolute z-20 mt-1 max-h-72 min-w-full overflow-auto rounded-luxe border border-line bg-white py-1 shadow-luxe focus:outline-none"
      >
        <template v-for="g in groupedOptions" :key="g.label ?? '__root__'">
          <p v-if="g.label" class="px-3 pb-1 pt-2 text-[11px] font-semibold uppercase tracking-wide text-ink-faint">{{ g.label }}</p>
          <ListboxOption
            v-for="opt in g.options"
            :key="String(opt.value)"
            v-slot="{ active, selected }"
            :value="opt.value"
            as="template"
          >
            <li
              :class="[
                active ? 'bg-canvas-warm text-ink' : 'text-ink-soft',
                'relative cursor-pointer select-none py-2 pl-3 pr-9 text-[14px]',
              ]"
            >
              <span :class="['block truncate', selected ? 'font-medium text-gold' : '']">{{ opt.label }}</span>
              <span v-if="selected" class="absolute inset-y-0 right-0 flex items-center pr-3 text-gold">
                <CheckIcon class="h-4 w-4" />
              </span>
            </li>
          </ListboxOption>
        </template>
      </ListboxOptions>
    </transition>
  </Listbox>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Listbox, ListboxButton, ListboxOptions, ListboxOption } from '@headlessui/vue'
import { ChevronUpDownIcon, CheckIcon } from '@heroicons/vue/24/outline'

type OptionValue = string | number | null

interface Option {
  value: OptionValue
  label: string
  group?: string
}

interface Props {
  modelValue: OptionValue
  options: Option[]
  placeholder?: string
}

const props = withDefaults(defineProps<Props>(), { placeholder: '请选择' })
const emit = defineEmits<{ 'update:modelValue': [OptionValue]; change: [OptionValue] }>()

const selectedLabel = computed(() => props.options.find((o) => o.value === props.modelValue)?.label ?? '')

const groupedOptions = computed(() => {
  const groups: { label?: string; options: Option[] }[] = []
  for (const opt of props.options) {
    const key = opt.group
    let g = groups.find((x) => x.label === key)
    if (!g) {
      g = { label: key, options: [] }
      groups.push(g)
    }
    g.options.push(opt)
  }
  return groups
})

const onChange = (val: OptionValue) => {
  emit('update:modelValue', val)
  emit('change', val)
}
</script>
