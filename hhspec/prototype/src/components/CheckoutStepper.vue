<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

const props = defineProps({
  current: { type: Number, default: 1 },
})

const router = useRouter()
const { t } = useI18n()

const steps = computed(() => [
  { num: 1, label: t('checkout.step1'), route: '/checkout/address' },
  { num: 2, label: t('checkout.step2'), route: '/checkout/payment' },
  { num: 3, label: t('checkout.step3'), route: '/checkout/review' },
])

function isDone(s) { return s.num < props.current }
function isActive(s) { return s.num === props.current }

function go(s) {
  if (isDone(s)) router.push(s.route)
}
</script>

<template>
  <div class="bg-white border-b border-ink-100">
    <div class="container-editorial py-8 sm:py-10">
      <ol class="mx-auto max-w-2xl flex items-start justify-between">
        <li
          v-for="(s, i) in steps"
          :key="s.num"
          class="flex-1 flex flex-col items-center relative"
        >
          <!-- connector to next step -->
          <span
            v-if="i < steps.length - 1"
            aria-hidden="true"
            class="absolute top-[18px] left-1/2 w-full h-px transition-colors duration-500"
            :class="s.num < current ? 'bg-ink-950' : 'bg-ink-200'"
          ></span>

          <!-- circle -->
          <button
            type="button"
            :disabled="!isDone(s)"
            :aria-current="isActive(s) ? 'step' : undefined"
            @click="go(s)"
            class="relative z-10 w-9 h-9 grid place-items-center border transition-colors duration-300 bg-canvas"
            :class="[
              isActive(s) && 'bg-ink-950 text-white border-ink-950',
              isDone(s) && 'bg-white text-ink-950 border-ink-950 hover:bg-ink-50 cursor-pointer',
              !isActive(s) && !isDone(s) && 'bg-canvas text-ink-400 border-ink-200 cursor-not-allowed',
            ]"
          >
            <svg v-if="isDone(s)" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <path stroke-linecap="round" stroke-linejoin="round" d="M5 12l5 5 9-11" />
            </svg>
            <span v-else class="text-[12px] tabular-nums font-medium">{{ String(s.num).padStart(2, '0') }}</span>
          </button>

          <!-- label -->
          <span
            class="mt-3 editorial-label text-[10px] text-center px-1 transition-colors duration-300"
            :class="isActive(s) ? 'text-ink-950' : 'text-ink-400'"
          >{{ s.label }}</span>
        </li>
      </ol>
    </div>
  </div>
</template>
