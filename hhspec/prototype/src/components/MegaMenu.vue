<script setup>
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'

defineProps({
  data: { type: Object, required: true },
})
const emit = defineEmits(['navigate'])
const { t } = useI18n()
</script>

<template>
  <div class="bg-white border-t border-ink-100 shadow-[0_24px_40px_-24px_rgba(0,0,0,0.25)]">
    <div class="container-editorial py-10">
      <div class="grid grid-cols-12 gap-10">
        <div class="col-span-8 grid grid-cols-3 gap-8">
          <div v-for="(col, ci) in data.columns" :key="ci">
            <h4 class="editorial-label text-ink-400 mb-4">{{ t(col.titleKey) }}</h4>
            <ul class="space-y-2.5">
              <li v-for="link in col.links" :key="link.to + link.label">
                <RouterLink
                  :to="link.to"
                  class="link-underline text-sm text-ink-700 hover:text-ink-950"
                  @click="emit('navigate')"
                >
                  {{ link.label }}
                </RouterLink>
              </li>
            </ul>
          </div>
        </div>
        <div class="col-span-4">
          <RouterLink :to="data.feature.to" class="group block relative overflow-hidden aspect-[4/3]" @click="emit('navigate')">
            <img :src="data.feature.img" :alt="data.feature.label" class="absolute inset-0 w-full h-full object-cover transition-transform duration-700 ease-editorial group-hover:scale-105" />
            <div class="absolute inset-0 bg-gradient-to-t from-ink-950/55 to-transparent"></div>
            <div class="absolute bottom-0 left-0 p-5">
              <p class="editorial-label-light mb-1">{{ t('nav.featured') }}</p>
              <p class="font-serif text-white text-xl">{{ data.feature.label }}</p>
            </div>
          </RouterLink>
        </div>
      </div>
    </div>
  </div>
</template>
