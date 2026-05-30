<script setup>
import { ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import Toggle from '@/components/Toggle.vue'
import { banners } from '@/data/mock'
import { PlusIcon, PencilSquareIcon, TrashIcon, RocketLaunchIcon, CursorArrowRaysIcon } from '@heroicons/vue/24/outline'

const list = ref(banners.map((b) => ({ ...b })))
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Site Builder" title="Banner 管理" subtitle="配置首页 Hero、推荐位、顶部条等广告位图文与上下线">
      <template #actions>
        <button class="btn-gold"><RocketLaunchIcon class="h-4 w-4" />保存并发布</button>
        <button class="btn-primary"><PlusIcon class="h-4 w-4" />新增 Banner</button>
      </template>
    </PageHeader>

    <div class="panel overflow-hidden">
      <div class="overflow-x-auto">
        <table class="data-table">
          <thead>
            <tr><th>Banner</th><th>广告位置</th><th>投放时间</th><th class="text-center">上线</th><th class="text-right"><CursorArrowRaysIcon class="ml-auto h-4 w-4" /></th><th>排序</th><th class="text-right">操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="b in list" :key="b.id">
              <td>
                <div class="flex items-center gap-3">
                  <img :src="b.img" class="h-12 w-20 shrink-0 rounded-luxe object-cover" />
                  <span class="font-medium text-ink">{{ b.name }}</span>
                </div>
              </td>
              <td><span class="badge bg-ink/8 text-ink-soft" :dot="false">{{ b.position }}</span></td>
              <td class="text-[12px] text-ink-soft">{{ b.start }}<br />→ {{ b.end }}</td>
              <td class="text-center"><Toggle v-model="b.online" /></td>
              <td class="text-right text-ink-soft">{{ b.clicks.toLocaleString() }}</td>
              <td><input class="field w-16 px-2 py-1 text-center text-[12px]" :value="b.sort" /></td>
              <td>
                <div class="flex items-center justify-end gap-1">
                  <button class="btn-ghost"><PencilSquareIcon class="h-4 w-4" />编辑</button>
                  <button class="btn-danger-ghost"><TrashIcon class="h-4 w-4" /></button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
