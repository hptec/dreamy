<script setup lang="ts">
// PAGE-MKT-A02 / COMP-MKT-A04：Banner 管理（表格结构保持；Toggle online↔status 三态映射 E-MKT-25；
// sort blur→整单 update；「已过窗」灰色角标 DEC-MKT-2；「保存并发布」→ 整页 refetch 提示语义标注）
import { onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import Toggle from '@/components/Toggle.vue'
import EmptyState from '@/components/EmptyState.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import BannerFormDrawer from '@/components/drawers/BannerFormDrawer.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import { useBannersStore } from '@/stores/banners'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { formatDateTime } from '@/utils/format'
import { PlusIcon, PencilSquareIcon, TrashIcon, RocketLaunchIcon, CursorArrowRaysIcon } from '@heroicons/vue/24/outline'
import { BannerPosition, BannerStatus } from '@/api/types'
import type { Banner } from '@/api/types'

const store = useBannersStore()
const toast = useToastStore()

const drawer = ref(false)
const editing = ref<Banner | null>(null)
const confirm = ref<Banner | null>(null)
const confirmBusy = ref(false)

const positionLabel: Record<number, string> = { [BannerPosition.HERO]: '首页 Hero', [BannerPosition.FEATURED]: '推荐位', [BannerPosition.TOPBAR]: '顶部通告条' }

function load() {
  store.fetch().catch((e) => toast.error(e instanceof BizError ? e.message : '加载 Banner 失败'))
}

/** Toggle on → published（publish/republish）；off → archived（take_offline）；409703 回滚 + toast */
async function onToggle(b: Banner, on: boolean) {
  try {
    await store.toggleStatus(b, on ? BannerStatus.PUBLISHED : BannerStatus.ARCHIVED)
  } catch (e) {
    if (e instanceof BizError && e.code === 409703) toast.error('当前发布状态不允许该操作')
    else toast.error(e instanceof BizError ? e.message : '操作失败')
  }
}

async function onSortBlur(b: Banner, e: Event) {
  const v = Number((e.target as HTMLInputElement).value)
  if (Number.isNaN(v)) return
  try {
    await store.patchSort(b, v)
  } catch (err) {
    toast.error(err instanceof BizError ? err.message : '排序保存失败')
  }
}

/** DEC-MKT-2：已过投放窗（now > endTime）灰色角标（前端派生） */
function expired(b: Banner): boolean {
  return !!b.endTime && new Date(b.endTime).getTime() < Date.now()
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

/** 「保存并发布」语义=逐行变更已即时提交，按钮改为整页 refetch（视觉保留，行为标注——COMP-MKT-A04） */
function refreshAll() {
  load()
  toast.info('逐行变更已即时生效，列表已刷新')
}

onMounted(load)
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Site Builder" title="Banner 管理" subtitle="配置首页 Hero、推荐位、顶部条等广告位图文与上下线">
      <template #actions>
        <button class="btn-gold" @click="refreshAll"><RocketLaunchIcon class="h-4 w-4" />保存并发布</button>
        <button class="btn-primary" @click="editing = null; drawer = true"><PlusIcon class="h-4 w-4" />新增 Banner</button>
      </template>
    </PageHeader>

    <div class="panel mb-4 p-4">
      <SelectMenu
        v-model="store.positionFilter"
        class="w-44"
        :options="[
          { value: 'all', label: '全部广告位' },
          { value: BannerPosition.HERO, label: '首页 Hero' },
          { value: BannerPosition.FEATURED, label: '推荐位' },
          { value: BannerPosition.TOPBAR, label: '顶部通告条' },
        ]"
        @change="load"
      />
    </div>

    <div class="panel overflow-hidden">
      <div class="overflow-x-auto">
        <table class="data-table">
          <thead>
            <tr><th>Banner</th><th>广告位置</th><th>投放时间</th><th class="text-center">上线</th><th class="text-right"><CursorArrowRaysIcon class="ml-auto h-4 w-4" /></th><th>排序</th><th class="text-right">操作</th></tr>
          </thead>
          <tbody>
            <tr v-if="store.loading"><td colspan="7" class="py-10 text-center text-ink-faint">加载中…</td></tr>
            <tr v-for="b in store.list" v-else :key="b.id">
              <td>
                <div class="flex items-center gap-3">
                  <img :src="b.imageUrl" class="h-12 w-20 shrink-0 rounded-luxe object-cover" />
                  <div>
                    <span class="font-medium text-ink">{{ b.name }}</span>
                    <span v-if="expired(b)" class="ml-1.5 rounded bg-ink/8 px-1.5 py-0.5 text-[10px] text-ink-faint">已过窗</span>
                  </div>
                </div>
              </td>
              <td><span class="badge bg-ink/8 text-ink-soft">{{ positionLabel[b.position] || b.position }}</span></td>
              <td class="text-[12px] text-ink-soft">{{ formatDateTime(b.startTime) }}<br />→ {{ formatDateTime(b.endTime) }}</td>
              <td class="text-center"><Toggle :model-value="b.status === BannerStatus.PUBLISHED" @update:model-value="onToggle(b, $event)" /></td>
              <td class="text-right text-ink-soft">{{ (b.clicks ?? 0).toLocaleString() }}</td>
              <td><input class="field w-16 px-2 py-1 text-center text-[12px]" type="number" :value="b.sort ?? 0" @blur="onSortBlur(b, $event)" /></td>
              <td>
                <div class="flex items-center justify-end gap-1">
                  <button class="btn-ghost" @click="editing = b; drawer = true"><PencilSquareIcon class="h-4 w-4" />编辑</button>
                  <button class="btn-danger-ghost" @click="confirm = b"><TrashIcon class="h-4 w-4" /></button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState v-if="!store.loading && !store.list.length" title="暂无 Banner" hint="点击右上角「新增 Banner」创建。" />
    </div>

    <BannerFormDrawer :open="drawer" :editing="editing" @close="drawer = false" />

    <ConfirmDialog
      :open="!!confirm"
      title="删除确认"
      :message="`确认删除 Banner「${confirm?.name}」？`"
      confirm-text="删除"
      danger
      :busy="confirmBusy"
      @confirm="doDelete"
      @cancel="confirm = null"
    />
  </div>
</template>
