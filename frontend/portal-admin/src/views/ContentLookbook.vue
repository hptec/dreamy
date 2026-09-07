<script setup lang="ts">
// PAGE-MKT-A05 / COMP-MKT-A10：Lookbook 与指南（双 tab 保持；mock → E-MKT-37~46；
// 两 tab 各自 FormDrawer + 发布/下线 + 删除）
import { onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import LookbookFormDrawer from '@/components/drawers/LookbookFormDrawer.vue'
import GuideFormDrawer from '@/components/drawers/GuideFormDrawer.vue'
import { useLookbookStore } from '@/stores/lookbook'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import {
  PlusIcon, PencilSquareIcon, TrashIcon, PhotoIcon, RocketLaunchIcon, ArchiveBoxArrowDownIcon,
  EyeIcon,
  ArrowUpIcon, ArrowDownIcon,
} from '@heroicons/vue/24/outline'
import LocaleFlag from '@/components/ui/LocaleFlag.vue'
import { PublishStatus } from '@/api/types'
import type { Guide, Lookbook } from '@/api/types'

const store = useLookbookStore()
const toast = useToastStore()

const tab = ref<'lookbook' | 'guide'>('lookbook')
const lookbookDrawer = ref(false)
const editingLookbook = ref<Lookbook | null>(null)
const guideDrawer = ref(false)
const editingGuide = ref<Guide | null>(null)
const confirm = ref<{ kind: 'lookbook' | 'guide'; id: number; name: string } | null>(null)
const confirmBusy = ref(false)
const STORE_BASE = import.meta.env.VITE_STORE_BASE_URL || 'http://localhost:5173'

function coverForLookbook(l: Lookbook) {
  return l.cover || l.fallbackCover || ''
}

function previewPage() {
  window.open(`${STORE_BASE}/${tab.value === 'lookbook' ? 'inspiration' : 'wedding-guides'}`, '_blank')
}

function load() {
  store.fetchLookbooks().catch((e) => toast.error(e instanceof BizError ? e.message : '加载 Lookbook 失败'))
  store.fetchGuides().catch((e) => toast.error(e instanceof BizError ? e.message : '加载指南失败'))
}

function openNew() {
  if (tab.value === 'lookbook') {
    editingLookbook.value = null
    lookbookDrawer.value = true
  } else {
    editingGuide.value = null
    guideDrawer.value = true
  }
}

async function toggleLookbook(l: Lookbook) {
  const next = l.status === PublishStatus.PUBLISHED ? PublishStatus.DRAFT : PublishStatus.PUBLISHED
  try {
    await store.patchLookbookStatus(l.id, next)
    toast.success(next === PublishStatus.PUBLISHED ? '已发布，已触发缓存失效链' : '已下线，已触发缓存失效链')
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '操作失败')
  }
}

async function toggleGuide(g: Guide) {
  const next = g.status === PublishStatus.PUBLISHED ? PublishStatus.DRAFT : PublishStatus.PUBLISHED
  try {
    await store.patchGuideStatus(g.id, next)
    toast.success(next === PublishStatus.PUBLISHED ? '已发布，已触发缓存失效链' : '已下线，已触发缓存失效链')
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '操作失败')
  }
}

const reorderBusy = ref(false)
async function moveGuide(index: number, delta: number) {
  const target = index + delta
  if (reorderBusy.value || target < 0 || target >= store.guides.length) return
  const ids = store.guides.map((item) => item.id)
  ;[ids[index], ids[target]] = [ids[target], ids[index]]
  reorderBusy.value = true
  try {
    await store.reorderGuides(ids)
    toast.success('指南顺序已保存')
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '调整顺序失败')
  } finally {
    reorderBusy.value = false
  }
}

async function doDelete() {
  if (!confirm.value) return
  confirmBusy.value = true
  try {
    if (confirm.value.kind === 'lookbook') await store.removeLookbook(confirm.value.id)
    else await store.removeGuide(confirm.value.id)
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
    <PageHeader eyebrow="Content · CMS" title="Lookbook 与指南" subtitle="管理主题画册与婚礼筹备时间轴指南">
      <template #actions>
        <button class="btn-ghost" @click="previewPage"><EyeIcon class="h-4 w-4" />预览页面</button>
        <button class="btn-primary" @click="openNew"><PlusIcon class="h-4 w-4" />新增</button>
      </template>
    </PageHeader>

    <div class="mb-4 flex gap-1 border-b border-line">
      <button
        v-for="t in [['lookbook', 'Lookbook 画册'], ['guide', 'Wedding Guides 指南']] as const"
        :key="t[0]"
        class="border-b-2 px-4 py-2.5 text-[13px] transition-colors"
        :class="tab === t[0] ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'"
        @click="tab = t[0]"
      >{{ t[1] }}</button>
    </div>

    <!-- Lookbook tab -->
    <div v-show="tab === 'lookbook'">
      <div v-if="store.loadingLookbooks" class="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div v-for="i in 3" :key="i" class="panel h-40 animate-pulse bg-canvas-warm/40"></div>
      </div>
      <EmptyState v-else-if="!store.lookbooks.length" title="暂无 Lookbook" hint="点击右上角「新增」创建画册。" />
      <div v-else class="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div v-for="l in store.lookbooks" :key="l.id" class="panel overflow-hidden">
          <div class="relative h-44 bg-canvas-warm">
            <img v-if="coverForLookbook(l)" :src="coverForLookbook(l)" :alt="`${l.title} 封面`" class="h-full w-full object-cover" />
            <div v-else class="flex h-full w-full items-center justify-center text-ink-faint"><PhotoIcon class="h-8 w-8" /></div>
            <div class="absolute inset-0 bg-gradient-to-t from-ink/65 via-transparent to-transparent" />
            <div class="absolute right-3 top-3"><StatusBadge :tone="l.status === PublishStatus.PUBLISHED ? 'ok' : 'neutral'" :label="l.status === PublishStatus.PUBLISHED ? '已发布' : '草稿'" /></div>
            <p class="absolute bottom-3 left-4 flex items-center gap-2 text-xs text-canvas/80"><PhotoIcon class="h-4 w-4" />{{ l.cover ? 'Lookbook 封面' : l.fallbackCover ? '自动封面' : '暂无封面' }}</p>
          </div>
          <div class="p-5">
            <h3 class="font-display text-lg font-medium text-ink">{{ l.title }}</h3>
            <p class="text-[12px] text-ink-faint">{{ l.theme || '—' }} · {{ l.productIds?.length ?? 0 }} 件商品锚点</p>
          <div class="mt-2 flex items-center gap-1" aria-label="翻译状态">
            <LocaleFlag v-for="locale in ['es', 'fr'] as const" :key="locale" :locale="locale" :state="l.translations?.some((t) => t.locale === locale && (t.title || t.description)) ? 'filled' : 'missing'" />
          </div>
          <div class="mt-3 flex gap-1 border-t border-line pt-3">
            <button class="btn-ghost" @click="editingLookbook = l; lookbookDrawer = true"><PencilSquareIcon class="h-4 w-4" />编辑</button>
            <button class="btn-ghost" :title="l.status === PublishStatus.PUBLISHED ? '下线' : '发布'" @click="toggleLookbook(l)">
              <component :is="l.status === PublishStatus.PUBLISHED ? ArchiveBoxArrowDownIcon : RocketLaunchIcon" class="h-4 w-4" />
              {{ l.status === PublishStatus.PUBLISHED ? '下线' : '发布' }}
            </button>
            <button class="btn-danger-ghost ml-auto" @click="confirm = { kind: 'lookbook', id: l.id, name: l.title }"><TrashIcon class="h-4 w-4" /></button>
          </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Guide tab -->
    <div v-show="tab === 'guide'" class="space-y-3">
      <div v-if="store.loadingGuides" class="space-y-3">
        <div v-for="i in 3" :key="i" class="panel h-20 animate-pulse bg-canvas-warm/40"></div>
      </div>
      <EmptyState v-else-if="!store.guides.length" title="暂无指南" hint="点击右上角「新增」创建筹备指南。" />
      <div v-for="(g, i) in store.guides" v-else :key="g.id" class="panel flex items-center gap-4 p-4">
        <span class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-gold/12 font-display font-semibold text-gold-deep">{{ i + 1 }}</span>
        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-2">
            <span class="font-display text-base font-medium text-ink">{{ g.title }}</span>
            <span class="rounded-full bg-canvas-warm px-2 py-0.5 text-[11px] text-ink-faint">{{ g.timeframe || '—' }}</span>
          </div>
          <p class="text-[12px] text-ink-soft">{{ g.phase }} · {{ g.tasks?.length ?? 0 }} 个待办任务</p>
          <div class="mt-1 flex items-center gap-1" aria-label="翻译状态">
            <LocaleFlag v-for="locale in ['es', 'fr'] as const" :key="locale" :locale="locale" :state="g.translations?.some((t) => t.locale === locale && (t.title || t.body)) ? 'filled' : 'missing'" />
          </div>
        </div>
        <StatusBadge :tone="g.status === PublishStatus.PUBLISHED ? 'ok' : 'neutral'" :label="g.status === PublishStatus.PUBLISHED ? '已发布' : '草稿'" />
        <div class="flex gap-1">
          <button class="btn-ghost disabled:opacity-40" :disabled="reorderBusy || i === 0" title="上移" @click="moveGuide(i, -1)"><ArrowUpIcon class="h-4 w-4" /></button>
          <button class="btn-ghost disabled:opacity-40" :disabled="reorderBusy || i === store.guides.length - 1" title="下移" @click="moveGuide(i, 1)"><ArrowDownIcon class="h-4 w-4" /></button>
          <button class="btn-ghost" :title="g.status === PublishStatus.PUBLISHED ? '下线' : '发布'" @click="toggleGuide(g)">
            <component :is="g.status === PublishStatus.PUBLISHED ? ArchiveBoxArrowDownIcon : RocketLaunchIcon" class="h-4 w-4" />
          </button>
          <button class="btn-ghost" @click="editingGuide = g; guideDrawer = true"><PencilSquareIcon class="h-4 w-4" /></button>
          <button class="btn-danger-ghost" @click="confirm = { kind: 'guide', id: g.id, name: g.title }"><TrashIcon class="h-4 w-4" /></button>
        </div>
      </div>
    </div>

    <LookbookFormDrawer :open="lookbookDrawer" :editing="editingLookbook" @close="lookbookDrawer = false" />
    <GuideFormDrawer :open="guideDrawer" :editing="editingGuide" @close="guideDrawer = false" />

    <ConfirmDialog
      :open="!!confirm"
      title="删除确认"
      :message="`确认删除「${confirm?.name}」？`"
      confirm-text="删除"
      danger
      :busy="confirmBusy"
      @confirm="doDelete"
      @cancel="confirm = null"
    />
  </div>
</template>
