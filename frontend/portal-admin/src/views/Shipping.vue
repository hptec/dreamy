<script setup>
import { ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import Toggle from '@/components/Toggle.vue'
import { carriers, shippingRates } from '@/data/mock'
import { PlusIcon, RocketLaunchIcon, TruckIcon } from '@heroicons/vue/24/outline'

const list = ref(carriers.map((c) => ({ ...c })))
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Settings" title="物流配置" subtitle="管理承运方、国际邮费表与运送规则">
      <template #actions><button class="btn-gold"><RocketLaunchIcon class="h-4 w-4" />保存配置</button></template>
    </PageHeader>
    <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
      <div class="panel p-6">
        <h3 class="mb-4 flex items-center gap-2 font-display text-lg font-semibold text-ink"><TruckIcon class="h-5 w-5 text-gold-deep" />承运方</h3>
        <div class="space-y-2">
          <div v-for="c in list" :key="c.name" class="flex items-center gap-3 rounded-luxe border border-line p-3">
            <div class="min-w-0 flex-1"><p class="text-[13px] font-medium text-ink">{{ c.name }}</p><p class="text-[12px] text-ink-faint">{{ c.zones }} · 时效 {{ c.leadTime }}</p></div>
            <Toggle v-model="c.enabled" />
          </div>
          <button class="btn-ghost"><PlusIcon class="h-4 w-4" />添加承运方</button>
        </div>
      </div>
      <div class="panel p-6">
        <h3 class="mb-4 font-display text-lg font-semibold text-ink">国际邮费表（分区）</h3>
        <table class="data-table">
          <thead><tr><th>区域</th><th class="text-right">基础邮费</th><th class="text-right">满额包邮</th><th class="text-right">门槛</th></tr></thead>
          <tbody>
            <tr v-for="r in shippingRates" :key="r.zone">
              <td class="font-medium text-ink">{{ r.zone }}</td>
              <td class="text-right text-ink-soft">{{ r.under }}</td>
              <td class="text-right text-ok">{{ r.over }}</td>
              <td class="text-right text-ink-soft">{{ r.threshold }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
