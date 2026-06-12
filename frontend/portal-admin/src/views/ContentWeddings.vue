<script setup lang="ts">
// PAGE-MKT-A04 / COMP-MKT-A08：Real Weddings（卡片网格保持；mock → E-MKT-32~36；
// WeddingFormDrawer + 发布/下线 patchStatus + 删除确认）
import { onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import Pagination from '@/components/Pagination.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import WeddingFormDrawer from '@/components/drawers/WeddingFormDrawer.vue'
import { useWeddingsStore } from '@/stores/weddings'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import {
  PlusIcon, PencilSquareIcon, TrashIcon, ShoppingBagIcon, MapPinIcon, RocketLaunchIcon, ArchiveBoxArrowDownIcon,
} from '@heroicons/vue/24/outline'
import { PublishStatus } from '@/api/types'
import type { RealWedding } from '@/api/types'

const store = useWeddingsStore()
const toast = useToastStore()

const drawer = ref(false)
const editing = ref<RealWedding | null>(null)
const confirm = ref<RealWedding | null>(null)
const confirmBusy = ref(false)

function load() {
  store.fetch().catch((e) => toast.error(e instanceof BizError ? e.message : '加载婚礼故事失败'))
}

/** draft↔published 双向流转 */
async function toggleStatus(w: RealWedding) {
  const next = w.status === PublishStatus.PUBLISHED ? PublishStatus.DRAFT : PublishStatus.PUBLISHED
  try {
    await store.patchStatus(w.id, next)
    toast.success(next === PublishStatus.PUBLISHED ? '已发布，已触发缓存失效链' : '已下线，已触发缓存失效链')
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '操作失败')
  }
}

async function doDelete() {
  if (!confirm.value) return
  confirmBusy.value = true
  try {
    await store.remove(confirm.value.id)
    toast.success('已删除')
    confirm.value = null
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '删除失败')
  } finally {
    confirmBusy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Content · CMS" title="Real Weddings" subtitle="管理真实婚礼故事画廊与 Shop the Look 关联">
      <template #actions>
        <select v-model="store.statusFilter" class="field w-32" @change="load">
          <option value="all">全部状态</option>
          <option :value="PublishStatus.PUBLISHED">已发布</option>
          <option :value="PublishStatus.DRAFT">草稿</option>
        </select>
        <button class="btn-primary" @click="editing = null; drawer = true"><PlusIcon class="h-4 w-4" />新增婚礼故事</button>
      </template>
    </PageHeader>

    <div v-if="store.loading" class="grid grid-cols-1 gap-5 sm:grid-cols-2">
      <div v-for="i in 2" :key="i" class="panel h-44 animate-pulse bg-canvas-warm/40"></div>
    </div>
    <EmptyState v-else-if="!store.list.length" title="暂无婚礼故事" hint="点击右上角「新增婚礼故事」创建。" />
    <div v-else class="grid grid-cols-1 gap-5 sm:grid-cols-2">
      <div v-for="w in store.list" :key="w.id" class="panel overflow-hidden">
        <div class="grid grid-cols-[140px_1fr]">
          <div class="relative">
            <img v-if="w.cover" :src="w.cover" class="h-full w-full object-cover" />
            <div v-else class="flex h-full w-full items-center justify-center bg-canvas-warm text-ink-faint">无封面</div>
          </div>
          <div class="p-4">
            <div class="flex items-start justify-between">
              <div>
                <span class="rounded-full bg-sage/15 px-2 py-0.5 text-[11px] text-sage">{{ w.theme || '—' }}</span>
                <h3 class="mt-1.5 font-display text-xl font-medium text-ink">{{ w.couple }}</h3>
                <p class="flex items-center gap-1 text-[12px] text-ink-soft"><MapPinIcon class="h-3.5 w-3.5" />{{ w.location || '—' }}</p>
                <p class="text-[11px] text-ink-faint">{{ w.weddingDate || '—' }}</p>
              </div>
              <StatusBadge :tone="w.status === PublishStatus.PUBLISHED ? 'ok' : 'neutral'" :label="w.status === PublishStatus.PUBLISHED ? '已发布' : '草稿'" />
            </div>
            <div class="mt-3 flex items-center gap-1 border-t border-line pt-3">
              <span class="flex items-center gap-1 text-[12px] text-ink-faint">
                <ShoppingBagIcon class="h-3.5 w-3.5" />Shop the Look · {{ w.productIds?.length ?? 0 }} 件
              </span>
              <div class="ml-auto flex gap-1">
                <button class="btn-ghost" :title="w.status === PublishStatus.PUBLISHED ? '下线' : '发布'" @click="toggleStatus(w)">
                  <component :is="w.status === PublishStatus.PUBLISHED ? ArchiveBoxArrowDownIcon : RocketLaunchIcon" class="h-4 w-4" />
                </button>
                <button class="btn-ghost" @click="editing = w; drawer = true"><PencilSquareIcon class="h-4 w-4" /></button>
                <button class="btn-danger-ghost" @click="confirm = w"><TrashIcon class="h-4 w-4" /></button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="store.totalElements > store.pageSize" class="panel mt-4">
      <Pagination :total="store.totalElements" :page="store.page" :per-page="store.pageSize" @change="(p) => store.setPage(p)" />
    </div>

    <WeddingFormDrawer :open="drawer" :editing="editing" @close="drawer = false" />

    <ConfirmDialog
      :open="!!confirm"
      title="删除确认"
      :message="`确认删除「${confirm?.couple}」的婚礼故事？`"
      confirm-text="删除"
      danger
      :busy="confirmBusy"
      @confirm="doDelete"
      @cancel="confirm = null"
    />
  </div>
</template>
