<script setup lang="ts">
// PAGE-MKT-A03 / COMP-MKT-A06：Blog 文章（卡片网格保持；filter tabs 改服务端参数 + 补『已归档』tab；
// 发布预判 slug 空 422704；published 行「下线」/archived 行「重新发布」；预览新窗口）
import { onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import Pagination from '@/components/Pagination.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import BlogEditDrawer from '@/components/drawers/BlogEditDrawer.vue'
import { useBlogStore } from '@/stores/blog'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { formatDate } from '@/utils/format'
import {
  PlusIcon, PencilSquareIcon, TrashIcon, RocketLaunchIcon, EyeIcon, ArchiveBoxArrowDownIcon, MagnifyingGlassIcon,
} from '@heroicons/vue/24/outline'
import type { BlogPost } from '@/api/types'

const store = useBlogStore()
const toast = useToastStore()

const drawer = ref(false)
const confirm = ref<BlogPost | null>(null)
const confirmBusy = ref(false)

const STORE_BASE = import.meta.env.VITE_STORE_BASE_URL || 'http://localhost:5173'

const tabs = [
  ['all', '全部'],
  ['published', '已发布'],
  ['draft', '草稿'],
  ['archived', '已归档'], // 与 API status 枚举对齐（显式标注）
] as const

const statusTone: Record<string, string> = { published: 'ok', draft: 'neutral', archived: 'neutral' }
const statusLabel: Record<string, string> = { published: '已发布', draft: '草稿', archived: '已归档' }

function load() {
  store.fetch().catch((e) => toast.error(e instanceof BizError ? e.message : '加载文章失败'))
}

function selectTab(k: string) {
  store.filterStatus = k
  store.applyFilters().catch((e) => toast.error(e instanceof BizError ? e.message : '加载失败'))
}

let searchTimer: ReturnType<typeof setTimeout> | null = null
function onSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    store.applyFilters().catch((e) => toast.error(e instanceof BizError ? e.message : '加载失败'))
  }, 300)
}

async function openEdit(p?: BlogPost) {
  if (!p) {
    await store.openEdit()
    drawer.value = true
    return
  }
  try {
    await store.openEdit(p.id) // getBlog 全量回读（含正文 + translations）
    drawer.value = true
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '加载文章失败')
  }
}

/** 「预览」→ 新窗口 {storeBase}/blog/{slug}（slug 空置灰） */
function preview(p: BlogPost) {
  if (!p.slug) return
  window.open(`${STORE_BASE}/blog/${p.slug}`, '_blank')
}

/** FORM-MKT-A04：slug 空前端预判 → 打开编辑抽屉；后端 422704 兜底 */
async function publish(p: BlogPost) {
  if (!p.slug) {
    toast.error('发布前需填写 slug')
    await openEdit(p)
    return
  }
  try {
    await store.patchStatus(p.id, 'published')
    toast.success('文章已发布，已触发缓存失效链')
  } catch (e) {
    if (e instanceof BizError && e.code === 422704) {
      toast.error('发布前需填写 slug')
      await openEdit(p)
    } else {
      toast.error(e instanceof BizError ? e.message : '操作失败')
    }
  }
}

async function archive(p: BlogPost) {
  try {
    await store.patchStatus(p.id, 'archived')
    toast.success('文章已下线，已触发缓存失效链')
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
    <PageHeader eyebrow="Content · CMS" title="Blog 文章" subtitle="撰写婚礼策划文章，发布后生成静态文章页">
      <template #actions><button class="btn-primary" @click="openEdit()"><PlusIcon class="h-4 w-4" />写文章</button></template>
    </PageHeader>

    <div class="mb-4 flex flex-wrap items-center gap-3">
      <div class="flex gap-1 border-b border-line">
        <button
          v-for="t in tabs"
          :key="t[0]"
          class="border-b-2 px-4 py-2.5 text-[13px] transition-colors"
          :class="store.filterStatus === t[0] ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'"
          @click="selectTab(t[0])"
        >{{ t[1] }}</button>
      </div>
      <div class="relative ml-auto">
        <MagnifyingGlassIcon class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
        <input v-model="store.search" class="field w-56 pl-9" placeholder="搜索标题…" @input="onSearchInput" />
      </div>
    </div>

    <div v-if="store.loading" class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <div v-for="i in 3" :key="i" class="panel h-72 animate-pulse bg-canvas-warm/40"></div>
    </div>
    <EmptyState v-else-if="!store.list.length" title="暂无文章" hint="点击右上角「写文章」开始创作。" />
    <div v-else class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <div v-for="p in store.list" :key="p.id" class="panel overflow-hidden">
        <div class="relative aspect-[16/10]">
          <img v-if="p.cover" :src="p.cover" class="h-full w-full object-cover" />
          <div v-else class="flex h-full w-full items-center justify-center bg-canvas-warm text-ink-faint">无封面</div>
          <StatusBadge class="absolute left-3 top-3" :tone="statusTone[p.status]" :label="statusLabel[p.status]" />
        </div>
        <div class="p-4">
          <span class="text-[11px] uppercase tracking-wide text-gold-deep">{{ p.category || 'Uncategorized' }}</span>
          <h3 class="mt-1 font-display text-lg font-medium leading-snug text-ink">{{ p.title }}</h3>
          <p class="mt-2 text-[12px] text-ink-faint">{{ p.author || '—' }} · {{ formatDate(p.publishedAt) }} · {{ (p.views ?? 0).toLocaleString() }} 阅读</p>
          <div class="mt-3 flex items-center gap-1 border-t border-line pt-3">
            <button class="btn-ghost" @click="openEdit(p)"><PencilSquareIcon class="h-4 w-4" />编辑</button>
            <button class="btn-ghost disabled:opacity-40" :disabled="!p.slug" :title="p.slug ? '前台预览' : '需先填写 slug'" @click="preview(p)"><EyeIcon class="h-4 w-4" />预览</button>
            <button v-if="p.status === 'draft'" class="btn-ghost text-gold-deep" @click="publish(p)"><RocketLaunchIcon class="h-4 w-4" />发布</button>
            <button v-else-if="p.status === 'published'" class="btn-ghost" @click="archive(p)"><ArchiveBoxArrowDownIcon class="h-4 w-4" />下线</button>
            <button v-else class="btn-ghost text-gold-deep" @click="publish(p)"><RocketLaunchIcon class="h-4 w-4" />重新发布</button>
            <button class="btn-danger-ghost ml-auto" @click="confirm = p"><TrashIcon class="h-4 w-4" /></button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="store.totalElements > store.pageSize" class="panel mt-4">
      <Pagination :total="store.totalElements" :page="store.page" :per-page="store.pageSize" @change="(p) => store.setPage(p)" />
    </div>

    <BlogEditDrawer :open="drawer" :editing="store.editing" @close="drawer = false" />

    <ConfirmDialog
      :open="!!confirm"
      title="删除确认"
      :message="`确认删除文章「${confirm?.title}」？删除后不可恢复。`"
      confirm-text="删除"
      danger
      :busy="confirmBusy"
      @confirm="doDelete"
      @cancel="confirm = null"
    />
  </div>
</template>
