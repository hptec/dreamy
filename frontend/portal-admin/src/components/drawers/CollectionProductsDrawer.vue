<script setup lang="ts">
// COMP-CAT-C01 CollectionProductsDrawer：集合内商品管理
// - 列表展示（主图 + 名称 + 状态 + sort）
// - vuedraggable 拖拽排序，本地暂存脏态，点"保存排序"全量覆盖（E-CAT-36）
// - 单条摘除即时调 API（E-CAT-37）+ 乐观更新
// - 底部 ProductPickerPanel 添加新商品（合并现有 + 新增 → E-CAT-36 全量覆盖）
import { ref, watch, computed } from 'vue'
import draggable from 'vuedraggable'
import { XMarkIcon, Bars3Icon } from '@heroicons/vue/24/outline'
import DrawerShell from '@/components/DrawerShell.vue'
import ProductPickerPanel from '@/components/ProductPickerPanel.vue'
import { useCollectionsStore } from '@/stores/collections'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { ProductStatus } from '@/api/types'
import type { Collection, CollectionProduct } from '@/api/types'

const props = defineProps<{ open: boolean; collection: Collection | null }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'changed'): void }>()

const store = useCollectionsStore()
const toast = useToastStore()

const items = ref<CollectionProduct[]>([])
const loading = ref(false)
const savingOrder = ref(false)
const removingId = ref<number | null>(null)
const adding = ref(false)
const pendingAddedIds = ref<number[]>([])
const dirty = computed(() => {
  if (items.value.length === 0) return false
  return items.value.some((it, i) => it.sort !== i)
})

watch(
  () => [props.open, props.collection?.id],
  ([open]) => {
    if (!open || !props.collection) return
    pendingAddedIds.value = []
    void load()
  },
  { immediate: true },
)

async function load() {
  if (!props.collection) return
  loading.value = true
  try {
    items.value = await store.fetchCollectionProducts(props.collection.id)
  } catch (e) {
    toast.error(bizMsg(e, '加载失败'))
  } finally {
    loading.value = false
  }
}

async function saveOrder() {
  if (!props.collection || !dirty.value) return
  savingOrder.value = true
  const ids = items.value.map((it) => it.productId)
  try {
    await store.replaceCollectionProducts(props.collection.id, ids)
    // 按当前顺序重写 sort，清除脏态
    items.value = items.value.map((it, i) => ({ ...it, sort: i }))
    toast.success('排序已保存')
    emit('changed')
  } catch (e) {
    toast.error(bizMsg(e, '保存失败'))
  } finally {
    savingOrder.value = false
  }
}

async function removeOne(p: CollectionProduct) {
  if (!props.collection) return
  removingId.value = p.productId
  try {
    await store.removeCollectionProduct(props.collection.id, p.productId)
    items.value = items.value.filter((it) => it.productId !== p.productId)
    // 重写本地 sort 保持连续
    items.value = items.value.map((it, i) => ({ ...it, sort: i }))
    toast.success('已摘除')
    emit('changed')
  } catch (e) {
    toast.error(bizMsg(e, '摘除失败'))
  } finally {
    removingId.value = null
  }
}

async function addSelected() {
  if (!props.collection || pendingAddedIds.value.length === 0) return
  adding.value = true
  // 合并现有 + 新增（去重），全量覆盖
  const existing = items.value.map((it) => it.productId)
  const merged = [...existing, ...pendingAddedIds.value.filter((id) => !existing.includes(id))]
  try {
    await store.replaceCollectionProducts(props.collection.id, merged)
    pendingAddedIds.value = []
    await load()
    toast.success(`已添加 ${merged.length - existing.length} 件商品`)
    emit('changed')
  } catch (e) {
    toast.error(bizMsg(e, '添加失败'))
  } finally {
    adding.value = false
  }
}

function bizMsg(e: unknown, fallback: string): string {
  if (e instanceof BizError) return e.message || fallback
  return fallback
}

function statusLabel(s: number): string {
  return s === ProductStatus.PUBLISHED ? '已上架' : '草稿'
}
</script>

<template>
  <DrawerShell
    :open="open"
    :title="collection ? `${collection.name} · 商品管理` : '商品管理'"
    eyebrow="集合内容"
    width="max-w-2xl"
    @close="emit('close')"
  >
    <div v-if="!collection" class="text-[13px] text-ink-faint">未选择集合</div>
    <template v-else>
      <!-- 顶部统计 + 保存排序 -->
      <div class="mb-4 flex items-center justify-between">
        <p class="text-[12.5px] text-ink-soft">
          共 <span class="font-medium text-ink">{{ items.length }}</span> 件商品
          <span v-if="dirty" class="ml-2 text-gold-deep">· 有未保存的排序</span>
        </p>
        <button
          class="btn-gold px-3 py-1.5 text-[12px]"
          :disabled="!dirty || savingOrder"
          @click="saveOrder"
        >{{ savingOrder ? '保存中…' : '保存排序' }}</button>
      </div>

      <!-- 商品列表（拖拽排序） -->
      <div v-if="loading" class="py-10 text-center text-[12.5px] text-ink-faint">加载中…</div>
      <div v-else-if="items.length === 0" class="rounded-luxe border border-dashed border-line py-10 text-center text-[12.5px] text-ink-faint">
        集合内暂无商品，可在下方添加
      </div>
      <draggable
        v-else
        v-model="items"
        item-key="productId"
        handle=".drag-handle"
        class="space-y-2"
        :animation="180"
      >
        <template #item="{ element }">
          <div
            class="flex items-center gap-3 rounded-luxe bg-canvas-warm/30 px-3 py-2.5 transition-all hover:bg-canvas-warm/60"
          >
            <Bars3Icon class="drag-handle h-4 w-4 shrink-0 cursor-grab text-ink-faint" />
            <img
              v-if="element.imageUrl"
              :src="element.imageUrl"
              class="h-10 w-8 shrink-0 rounded object-cover"
              alt=""
            />
            <div v-else class="h-10 w-8 shrink-0 rounded bg-canvas-warm"></div>
            <div class="min-w-0 flex-1">
              <p class="truncate text-[13px] text-ink">{{ element.name }}</p>
              <p class="text-[11px] text-ink-faint">#{{ element.productId }} · {{ element.slug }}</p>
            </div>
            <span
              class="min-w-[3.5rem] rounded px-2 py-0.5 text-center text-[11px] font-medium"
              :class="element.status === ProductStatus.PUBLISHED ? 'bg-ok/15 text-ok' : 'bg-canvas-warm text-ink-faint'"
            >{{ statusLabel(element.status) }}</span>
            <button
              class="rounded p-1 text-ink-faint transition-colors hover:bg-danger/10 hover:text-danger disabled:opacity-40"
              :disabled="removingId === element.productId"
              :title="removingId === element.productId ? '摘除中…' : '从集合摘除'"
              @click="removeOne(element)"
            >
              <XMarkIcon class="h-4 w-4" />
            </button>
          </div>
        </template>
      </draggable>

      <!-- 分隔 -->
      <div class="my-5 border-t border-line"></div>

      <!-- 添加商品 -->
      <div>
        <p class="mb-2 text-[12.5px] font-medium text-ink">添加商品到该集合</p>
        <ProductPickerPanel v-model="pendingAddedIds" />
        <div class="mt-2 flex justify-end">
          <button
            class="btn-outline px-3 py-1.5 text-[12px]"
            :disabled="pendingAddedIds.length === 0 || adding"
            @click="addSelected"
          >{{ adding ? '添加中…' : `添加选中 ${pendingAddedIds.length || ''}`.trim() }}</button>
        </div>
      </div>
    </template>

    <template #footer>
      <button class="btn-outline" @click="emit('close')">关闭</button>
    </template>
  </DrawerShell>
</template>
