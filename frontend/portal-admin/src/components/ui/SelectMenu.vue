<template>
  <Listbox
    :model-value="modelValue"
    :disabled="disabled"
    as="div"
    class="relative"
    @update:model-value="onChange"
  >
    <ListboxButton
      class="field flex w-full items-center justify-between gap-2 text-left"
      :class="disabled && 'cursor-not-allowed opacity-50'"
    >
      <span :class="['truncate', selectedLabel ? 'text-ink' : 'text-ink-faint']">{{ selectedLabel || placeholder }}</span>
      <ChevronUpDownIcon class="h-4 w-4 shrink-0 text-ink-faint" />
    </ListboxButton>
    <transition
      leave-active-class="transition duration-100 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <ListboxOptions
        class="absolute z-50 mt-1 max-h-72 min-w-full overflow-auto rounded-luxe border border-line bg-white py-1 shadow-luxe focus:outline-none"
      >
        <template v-for="g in groupedOptions" :key="g.label ?? '__root__'">
          <p v-if="g.label" class="px-3 pb-1 pt-2 text-[11px] font-semibold uppercase tracking-wide text-ink-faint">{{ g.label }}</p>
          <ListboxOption
            v-for="opt in g.options"
            :key="String(opt.value)"
            v-slot="{ active, selected }"
            :value="opt.value"
            :disabled="opt.disabled"
            as="template"
          >
            <li
              :class="[
                opt.disabled ? 'cursor-not-allowed text-ink-faint opacity-50' : active ? 'cursor-pointer bg-canvas-warm text-ink' : 'cursor-pointer text-ink-soft',
                opt.legacy && 'line-through',
                'relative select-none py-2 pl-3 pr-9 text-[14px]',
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

type OptionValue = string | number | null | undefined

interface Option {
  value: OptionValue
  label: string
  group?: string
  disabled?: boolean
}

type RawOption = string | number | Option

interface NormalizedOption extends Option {
  legacy?: boolean
}

interface Props {
  modelValue: OptionValue
  options: RawOption[]
  placeholder?: string
  disabled?: boolean
  legacy?: string[]
}

const props = withDefaults(defineProps<Props>(), { placeholder: '请选择', disabled: false })
const emit = defineEmits<{ 'update:modelValue': [OptionValue]; change: [OptionValue] }>()

function toOption(o: RawOption): NormalizedOption {
  return typeof o === 'object' && o !== null ? o : { value: o, label: String(o) }
}

const normalizedOptions = computed<NormalizedOption[]>(() => {
  const base = props.options.map(toOption)
  const legacyOpts = (props.legacy ?? []).map<NormalizedOption>((v) => ({ value: v, label: v, legacy: true }))
  return [...base, ...legacyOpts]
})

const selectedLabel = computed(
  () => normalizedOptions.value.find((o) => o.value === props.modelValue)?.label ?? '',
)

const groupedOptions = computed(() => {
  const groups: { label?: string; options: NormalizedOption[] }[] = []
  for (const opt of normalizedOptions.value) {
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
