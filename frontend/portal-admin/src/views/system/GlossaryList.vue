<script setup lang="ts">
// PAGE-002 GlossaryList：翻译术语表管理（FUNC-022；EDGE-022 权限守卫由 router meta 控制）
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import Pagination from '@/components/Pagination.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import GlossaryFormDrawer from '@/components/drawers/GlossaryFormDrawer.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import { useGlossaryStore } from '@/stores/glossary'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import type { GlossaryTerm } from '@/api/types'
import { PlusIcon, PencilSquareIcon, TrashIcon, MagnifyingGlassIcon } from '@heroicons/vue/24/outline'
import { normalizeFilter } from '@/utils/validators'

const store = useGlossaryStore()
const toast = useToastStore()

const drawer = ref(false)
const editing = ref<GlossaryTerm | null>(null)

const categoryFilter = ref('all')
const enabledFilter = ref<'all' | 'true' | 'false'>('all')
const keyword = ref('')

const confirm = ref<{ id: number; message: string } | null>(null)
const confirmBusy = ref(false)

const CATEGORY_PRESETS = ['廓形', '领型', '面料', '工艺', '裙摆', '袖型']

function enabledParam(): boolean | undefined {
  if (enabledFilter.value === 'true') return true
  if (enabledFilter.value === 'false') return false
  return undefined
}

function load(page?: number) {
  store
    .fetchTerms({
      category: normalizeFilter(categoryFilter.value),
      enabled: enabledParam(),
      keyword: keyword.value.trim() || undefined,
      page,
    })
    .catch((e) => toast.error(e instanceof BizError ? e.message : '加载术语表失败'))
}

onMounted(() => load(1))

function open(t?: GlossaryTerm) {
  editing.value = t ?? null
  drawer.value = true
}

function onSaved() {
  load(store.page)
}

function askDelete(t: GlossaryTerm) {
  confirm.value = { id: t.id, message: `确认删除术语「${t.termEn}」？删除后将不再注入翻译提示词。` }
}

async function doConfirm() {
  if (!confirm.value) return
  confirmBusy.value = true
  try {
    await store.deleteTerm(confirm.value.id)
    toast.success('已删除')
    confirm.value = null
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '删除失败')
  } finally {
    confirmBusy.value = false
  }
}

const hasTerms = computed(() => store.terms.length > 0)
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="System" title="翻译术语表" subtitle="维护婚纱领域 EN → ES/FR 专业术语对照，启用项注入 AI 翻译提示词保证译法一致">
      <template #actions>
        <button class="btn-gold" @click="open()"><PlusIcon class="h-4 w-4" />新增术语</button>
      </template>
    </PageHeader>

    <div class="panel">
      <!-- 筛选 -->
      <div class="flex flex-wrap items-center gap-3 border-b border-line px-4 py-3">
        <div class="flex items-center gap-1.5">
          <span class="text-[12px] text-ink-faint">分类：</span>
          <SelectMenu
            v-model="categoryFilter"
            class="w-32"
            :options="['all', ...CATEGORY_PRESETS].map((c) => ({ value: c, label: c === 'all' ? '全部' : c }))"
            @change="load(1)"
          />
        </div>
        <div class="flex items-center gap-1.5">
          <span class="text-[12px] text-ink-faint">状态：</span>
          <SelectMenu
            v-model="enabledFilter"
            class="w-28"
            :options="[
              { value: 'all', label: '全部' },
              { value: 'true', label: '启用' },
              { value: 'false', label: '停用' },
            ]"
            @change="load(1)"
          />
        </div>
        <div class="relative ml-auto">
          <MagnifyingGlassIcon class="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
          <input
            v-model="keyword"
            class="field w-56 py-1.5 pl-8 text-[13px]"
            placeholder="搜索术语（EN/ES/FR）"
            @keyup.enter="load(1)"
          />
        </div>
      </div>

      <template v-if="store.loading">
        <div class="space-y-2 p-4">
          <div v-for="i in 5" :key="i" class="h-12 animate-pulse rounded-luxe bg-canvas-warm/50"></div>
        </div>
      </template>
      <EmptyState v-else-if="!hasTerms" title="暂无术语" hint="添加婚纱领域术语（如 A-line / sweetheart neckline）以提升 AI 翻译一致性。" />
      <table v-else class="data-table">
        <thead>
          <tr>
            <th>英文 (EN)</th>
            <th>西语 (ES)</th>
            <th>法语 (FR)</th>
            <th>分类</th>
            <th class="text-center">状态</th>
            <th class="text-right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in store.terms" :key="t.id">
            <td class="font-medium text-ink">{{ t.termEn }}</td>
            <td class="text-ink-soft">{{ t.termEs || '—' }}</td>
            <td class="text-ink-soft">{{ t.termFr || '—' }}</td>
            <td class="text-ink-faint">{{ t.category || '—' }}</td>
            <td class="text-center">
              <StatusBadge :tone="t.enabled ? 'ok' : 'neutral'" :label="t.enabled ? '启用' : '停用'" />
            </td>
            <td>
              <div class="flex items-center justify-end gap-1">
                <button class="btn-ghost" title="编辑" @click="open(t)"><PencilSquareIcon class="h-4 w-4" /></button>
                <button class="btn-danger-ghost" title="删除" @click="askDelete(t)"><TrashIcon class="h-4 w-4" /></button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <Pagination
        v-if="hasTerms"
        :total="store.total"
        :page="store.page"
        :per-page="store.pageSize"
        @change="load($event)"
      />
    </div>

    <GlossaryFormDrawer :open="drawer" :editing="editing" @close="drawer = false" @saved="onSaved" />

    <ConfirmDialog
      :open="!!confirm"
      title="删除确认"
      :message="confirm?.message || ''"
      confirm-text="删除"
      danger
      :busy="confirmBusy"
      @confirm="doConfirm"
      @cancel="confirm = null"
    />
  </div>
</template>
