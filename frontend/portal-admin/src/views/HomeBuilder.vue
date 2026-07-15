<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { useSortable } from '@vueuse/integrations/useSortable'
import PageHeader from '@/components/PageHeader.vue'
import Toggle from '@/components/Toggle.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import MediaUploadCard from '@/components/MediaUploadCard.vue'
import ProductPickerPanel from '@/components/ProductPickerPanel.vue'
import { useHomeSectionStore } from '@/stores/homeSection'
import { useCategoriesStore } from '@/stores/categories'
import { useCollectionsStore } from '@/stores/collections'
import { useToast } from '@/composables/useToast'
import type { HomePageSection } from '@/api/siteBuilder'
import {
  ArrowPathIcon,
  ArrowTopRightOnSquareIcon,
  Bars3Icon,
  CheckIcon,
  ComputerDesktopIcon,
  DevicePhoneMobileIcon,
  EyeIcon,
  PencilSquareIcon,
  PlusIcon,
  TrashIcon,
} from '@heroicons/vue/24/outline'

type WorkspaceTab = 'edit' | 'preview'
type PreviewMode = 'desktop' | 'mobile'
type LocaleCode = 'en' | 'es' | 'fr'

interface HomeBlock {
  id: number
  sectionType: string
  enabled: boolean
  sortOrder: number
  data: Record<string, any>
  i18n: Record<LocaleCode, Record<string, any>>
  version: number
}

const store = useHomeSectionStore()
const categoriesStore = useCategoriesStore()
const collectionsStore = useCollectionsStore()
const toast = useToast()
const router = useRouter()

const STORE_BASE = import.meta.env.VITE_STORE_BASE_URL || 'http://localhost:5173'
const locales: LocaleCode[] = ['en', 'es', 'fr']

const blocks = ref<HomeBlock[]>([])
const activeId = ref<number | null>(null)
const workspaceTab = ref<WorkspaceTab>('edit')
const previewMode = ref<PreviewMode>('desktop')
const localeTab = ref<LocaleCode>('en')
const dirty = ref(false)
const saving = ref(false)
const previewLoading = ref(false)
const previewRevision = ref(0)
const blockListEl = ref<HTMLElement | null>(null)
const showAddBlockModal = ref(false)
const newBlockType = ref('custom')
const deleteTarget = ref<HomeBlock | null>(null)
const deleting = ref(false)
const leaveDialogOpen = ref(false)
const pendingLeaveRoute = ref<string | null>(null)

useSortable(blockListEl, blocks, {
  animation: 180,
  handle: '.drag-handle',
  onUpdate: () => {
    blocks.value.forEach((block, index) => { block.sortOrder = index })
    touch()
  },
})

onMounted(async () => {
  window.addEventListener('beforeunload', guardBrowserLeave)
  await Promise.allSettled([store.fetch(), categoriesStore.fetch(), collectionsStore.fetchGroups()])
  const themeGroup = collectionsStore.groups.find((group) => group.name.toLowerCase() === 'theme')
  if (themeGroup) await Promise.allSettled([collectionsStore.fetchCollections(themeGroup.id)])
  syncFromStore()
})

onBeforeUnmount(() => window.removeEventListener('beforeunload', guardBrowserLeave))

onBeforeRouteLeave((to) => {
  if (!dirty.value) return true

  // Cancel the current navigation and keep a single in-app dialog open. A new
  // menu click while the dialog is visible only replaces the pending target;
  // it must not recreate the dialog or start competing navigations.
  pendingLeaveRoute.value = to.fullPath
  leaveDialogOpen.value = true
  return false
})

watch(localeTab, () => {
  if (workspaceTab.value === 'preview') refreshPreview()
})

const activeBlock = computed(() => blocks.value.find((block) => block.id === activeId.value) ?? null)

const categoryOptions = computed(() => {
  const result: { value: number; label: string }[] = []
  const visit = (items: any[], prefix = '') => {
    for (const item of items) {
      result.push({ value: item.id, label: `${prefix}${item.name}` })
      visit(item.children || [], `${prefix}— `)
    }
  }
  visit(categoriesStore.tree)
  return result
})

const themeOptions = computed(() => {
  const themeGroup = collectionsStore.groups.find((group) => group.name.toLowerCase() === 'theme')
  if (!themeGroup) return []
  return collectionsStore.collectionsByGroup(themeGroup.id)
    .filter((item) => item.status === 1)
    .map((item) => ({
      value: item.id,
      label: item.name,
      imageUrl: item.fallbackCoverUrls?.[0] || null,
    }))
})

const previewUrl = computed(() => {
  const localePath = localeTab.value === 'en' ? '' : `/${localeTab.value}`
  const separator = STORE_BASE.includes('?') ? '&' : '?'
  return `${STORE_BASE}${localePath}${separator}builder_preview=${previewRevision.value}`
})

function syncFromStore() {
  blocks.value = store.sections.map(toBlock)
  if (!blocks.value.some((block) => block.id === activeId.value)) {
    activeId.value = blocks.value[0]?.id ?? null
  }
  dirty.value = false
}

function toBlock(section: HomePageSection): HomeBlock {
  return {
    id: section.id,
    sectionType: section.sectionType,
    enabled: section.enabled,
    sortOrder: section.sortOrder,
    data: normalizeBlockData(section.sectionType, parseJson(section.dataJson, {})),
    i18n: normalizeI18n(parseJson(section.i18nJson, {})),
    version: section.version,
  }
}

function normalizeI18n(raw: Record<string, any>): HomeBlock['i18n'] {
  return {
    en: { ...(raw?.en || {}) },
    es: { ...(raw?.es || {}) },
    fr: { ...(raw?.fr || {}) },
  }
}

function normalizeBlockData(sectionType: string, raw: Record<string, any>) {
  const data = { ...(raw || {}) }
  if (sectionType === 'theme_cards') {
    if (data.limit == null && data.count != null) data.limit = data.count
    delete data.count
    data.mode ||= 'auto'
    data.limit ||= 6
    data.collectionIds ||= []
    if (data.mode === 'manual' && data.collectionIds.length === 0 && data.categoryIds?.length) {
      data.mode = 'auto'
    }
    delete data.categoryIds
  }
  if (sectionType === 'product_rail') {
    data.source ||= 'new_arrival'
    data.limit ||= 8
    if (data.sort === 'new') data.sort = 'newest'
    if (data.sort === 'best') data.sort = 'recommended'
    data.productIds ||= []
  }
  if (sectionType === 'editorial_feature') data.limit ||= 3
  return data
}

function parseJson(value: unknown, fallback: Record<string, any>) {
  if (!value) return fallback
  try {
    return typeof value === 'string' ? JSON.parse(value) : value
  } catch {
    return fallback
  }
}

function typeLabel(type: string) {
  const labels: Record<string, string> = {
    hero: '首屏主视觉',
    theme_cards: '分类卡片',
    product_rail: '商品推荐',
    editorial_feature: '真实婚礼',
    newsletter: '邮件订阅',
    custom: '自定义内容',
  }
  return labels[type] || type
}

function blockTitle(block: HomeBlock) {
  if (block.sectionType === 'hero') return typeLabel(block.sectionType)
  return block.i18n[localeTab.value]?.heading || typeLabel(block.sectionType)
}

function eventValue(event: Event) {
  return (event.target as HTMLInputElement | HTMLTextAreaElement).value
}

function touch() {
  dirty.value = true
}

function guardBrowserLeave(event: BeforeUnloadEvent) {
  if (!dirty.value) return
  event.preventDefault()
}

function cancelLeave() {
  leaveDialogOpen.value = false
  pendingLeaveRoute.value = null
}

async function confirmLeave() {
  const destination = pendingLeaveRoute.value
  if (!destination) {
    cancelLeave()
    return
  }

  // The user explicitly chose to discard the draft. Clear dirty before the
  // new navigation so the route guard lets this one request through.
  dirty.value = false
  leaveDialogOpen.value = false
  pendingLeaveRoute.value = null
  await router.push(destination)
}

function onToggle(block: HomeBlock, enabled: boolean) {
  block.enabled = enabled
  touch()
}

function setProductSource(value: string | number | null | undefined) {
  if (!activeBlock.value) return
  activeBlock.value.data.source = value
  if (value === 'recommend') activeBlock.value.data.productIds ||= []
  touch()
}

function setThemeMode(value: string | number | null | undefined) {
  if (!activeBlock.value) return
  activeBlock.value.data.mode = value
  if (value === 'manual') activeBlock.value.data.collectionIds ||= []
  touch()
}

function toggleThemeCollection(id: number) {
  if (!activeBlock.value) return
  const selected: number[] = activeBlock.value.data.collectionIds || []
  activeBlock.value.data.collectionIds = selected.includes(id)
    ? selected.filter((item) => item !== id)
    : [...selected, id]
  touch()
}

function defaultData(type: string) {
  if (type === 'theme_cards') return { mode: 'auto', limit: 6, collectionIds: [] }
  if (type === 'product_rail') return { source: 'new_arrival', limit: 8, productIds: [] }
  if (type === 'editorial_feature') return { limit: 3 }
  return {}
}

function defaultI18n(type: string) {
  if (type === 'newsletter') {
    return {
      en: { heading: 'Join the Dreamy List', placeholder: 'Your email', cta: 'Subscribe' },
      es: {},
      fr: {},
    }
  }
  return { en: {}, es: {}, fr: {} }
}

async function saveAll(showSuccess = true) {
  if (!dirty.value) return true
  saving.value = true
  try {
    await store.saveAll(blocks.value.map((block) => ({
      id: block.id,
      sectionType: block.sectionType,
      enabled: block.enabled,
      sortOrder: block.sortOrder,
      dataJson: block.data,
      i18nJson: block.i18n,
      version: block.version,
    })))
    syncFromStore()
    if (showSuccess) toast.success('首页已保存并生效')
    return true
  } catch (error: any) {
    toast.error(error.message ?? '首页保存失败')
    return false
  } finally {
    saving.value = false
  }
}

async function switchWorkspaceTab(tab: WorkspaceTab) {
  if (tab === workspaceTab.value) return
  if (tab === 'preview' && !(await saveAll(false))) return
  workspaceTab.value = tab
  if (tab === 'preview') refreshPreview()
}

function refreshPreview() {
  previewLoading.value = true
  previewRevision.value += 1
}

function openPreviewWindow() {
  const localePath = localeTab.value === 'en' ? '' : `/${localeTab.value}`
  window.open(`${STORE_BASE}${localePath}`, '_blank', 'noopener,noreferrer')
}

function openBannerManager() {
  const href = router.resolve('/banners').href
  window.open(href, '_blank', 'noopener,noreferrer')
}

async function addBlock() {
  if (!(await saveAll(false))) return
  try {
    const created = await store.create({
      sectionType: newBlockType.value,
      enabled: true,
      sortOrder: blocks.value.length,
      dataJson: defaultData(newBlockType.value),
      i18nJson: defaultI18n(newBlockType.value),
    })
    syncFromStore()
    activeId.value = created.id
    showAddBlockModal.value = false
    toast.success('区块已添加并生效')
  } catch (error: any) {
    toast.error(error.message ?? '区块添加失败')
  }
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    if (!(await saveAll(false))) return
    const deletedId = deleteTarget.value.id
    await store.remove(deletedId)
    blocks.value = blocks.value.filter((block) => block.id !== deletedId)
    blocks.value.forEach((block, index) => { block.sortOrder = index })
    activeId.value = blocks.value[0]?.id ?? null
    deleteTarget.value = null
    if (blocks.value.length) {
      dirty.value = true
      await saveAll(false)
    }
    toast.success('区块已删除')
  } catch (error: any) {
    toast.error(error.message ?? '删除失败')
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Site Builder" title="首页装修" subtitle="编辑首页区块并在真实商城中预览">
      <template #actions>
        <span v-if="dirty" class="badge bg-warn/14 text-warn">
          <span class="h-1.5 w-1.5 rounded-full bg-current"></span>未保存
        </span>
        <button class="btn-gold disabled:cursor-not-allowed disabled:opacity-40" :disabled="!dirty || saving" @click="saveAll()">
          <CheckIcon class="h-4 w-4" />{{ saving ? '保存中…' : '保存并生效' }}
        </button>
      </template>
    </PageHeader>

    <div class="mb-5 flex border-b border-line" role="tablist" aria-label="首页装修视图">
      <button
        type="button"
        role="tab"
        :aria-selected="workspaceTab === 'edit'"
        class="flex min-h-11 items-center gap-2 border-b-2 px-5 text-[13px] font-medium transition-colors"
        :class="workspaceTab === 'edit' ? 'border-gold text-ink' : 'border-transparent text-ink-faint hover:text-ink'"
        @click="switchWorkspaceTab('edit')"
      >
        <PencilSquareIcon class="h-4 w-4" />模块设置
      </button>
      <button
        type="button"
        role="tab"
        :aria-selected="workspaceTab === 'preview'"
        class="flex min-h-11 items-center gap-2 border-b-2 px-5 text-[13px] font-medium transition-colors"
        :class="workspaceTab === 'preview' ? 'border-gold text-ink' : 'border-transparent text-ink-faint hover:text-ink'"
        @click="switchWorkspaceTab('preview')"
      >
        <EyeIcon class="h-4 w-4" />页面预览
      </button>
    </div>

    <div v-if="store.loading" class="panel p-8 text-center text-ink-faint">加载中...</div>
    <div v-else-if="store.error" class="panel p-8 text-center text-danger">{{ store.error }}</div>

    <div v-else v-show="workspaceTab === 'edit'" class="grid grid-cols-1 gap-5 lg:grid-cols-[280px_minmax(0,1fr)]">
      <aside class="panel self-start p-4 lg:sticky lg:top-5">
        <div class="mb-3 flex items-center justify-between">
          <p class="eyebrow">页面区块</p>
          <button type="button" class="btn-ghost text-gold-deep" @click="showAddBlockModal = true">
            <PlusIcon class="h-4 w-4" />添加
          </button>
        </div>
        <div ref="blockListEl" class="space-y-2">
          <div
            v-for="block in blocks"
            :key="block.id"
            role="button"
            tabindex="0"
            class="group flex w-full items-center gap-2 rounded-luxe border px-3 py-3 text-left text-[13px] transition-colors"
            :class="activeId === block.id ? 'border-gold bg-gold/8' : 'border-line hover:bg-canvas-warm'"
            @click="activeId = block.id"
            @keydown.enter="activeId = block.id"
            @keydown.space.prevent="activeId = block.id"
          >
            <Bars3Icon class="drag-handle h-4 w-4 shrink-0 cursor-grab text-ink-faint active:cursor-grabbing" />
            <span class="min-w-0 flex-1">
              <span class="block truncate font-medium" :class="block.enabled ? 'text-ink' : 'text-ink-faint line-through'">{{ blockTitle(block) }}</span>
              <span class="mt-0.5 block text-[11px] text-ink-faint">{{ typeLabel(block.sectionType) }}</span>
            </span>
            <Toggle :model-value="block.enabled" @update:model-value="onToggle(block, $event)" @click.stop />
          </div>
        </div>
      </aside>

      <section class="panel min-h-[640px] p-5 sm:p-6">
        <div v-if="activeBlock" :key="activeBlock.id">
          <div class="mb-5 flex items-start justify-between gap-4 border-b border-line pb-4">
            <div>
              <p class="eyebrow">区块设置</p>
              <h2 class="mt-1 text-[18px] font-medium text-ink">{{ typeLabel(activeBlock.sectionType) }}</h2>
            </div>
            <button type="button" class="btn-danger-ghost" title="删除区块" @click="deleteTarget = activeBlock">
              <TrashIcon class="h-4 w-4" /><span>删除</span>
            </button>
          </div>

          <div v-if="activeBlock.sectionType !== 'hero'" class="mb-6 flex gap-1 border-b border-line">
            <button
              v-for="locale in locales"
              :key="locale"
              type="button"
              class="border-b-2 px-4 py-2 text-[12px] uppercase transition-colors"
              :class="localeTab === locale ? 'border-gold text-ink' : 'border-transparent text-ink-faint hover:text-ink'"
              @click="localeTab = locale"
            >
              {{ locale }}
            </button>
          </div>

          <div class="max-w-3xl space-y-5">
            <template v-if="['product_rail', 'theme_cards', 'editorial_feature'].includes(activeBlock.sectionType)">
              <div class="grid gap-4 md:grid-cols-2">
                <div>
                  <label class="field-label">眉标题（{{ localeTab }}）</label>
                  <input
                    :value="activeBlock.i18n[localeTab].eyebrow"
                    class="field"
                    placeholder="Explore"
                    @input="activeBlock.i18n[localeTab].eyebrow = eventValue($event); touch()"
                  />
                </div>
                <div>
                  <label class="field-label">区块标题（{{ localeTab }}）</label>
                  <input
                    :value="activeBlock.i18n[localeTab].heading"
                    class="field"
                    placeholder="Section title"
                    @input="activeBlock.i18n[localeTab].heading = eventValue($event); touch()"
                  />
                </div>
              </div>
              <div>
                <label class="field-label">区块说明（{{ localeTab }}）</label>
                <textarea
                  :value="activeBlock.i18n[localeTab].description"
                  class="field"
                  rows="3"
                  @input="activeBlock.i18n[localeTab].description = eventValue($event); touch()"
                ></textarea>
              </div>
            </template>

            <template v-if="activeBlock.sectionType === 'product_rail'">
              <div class="grid gap-4 md:grid-cols-2">
                <div>
                  <label class="field-label">商品来源</label>
                  <SelectMenu
                    :model-value="activeBlock.data.source || 'new_arrival'"
                    :options="[
                      { value: 'new_arrival', label: '新品' },
                      { value: 'best_seller', label: '畅销商品' },
                      { value: 'recommend', label: '人工推荐' },
                      { value: 'category', label: '指定分类' },
                    ]"
                    @change="setProductSource"
                  />
                </div>
                <div>
                  <label class="field-label">展示数量</label>
                  <input v-model.number="activeBlock.data.limit" class="field" type="number" min="1" max="12" @input="touch" />
                </div>
              </div>
              <div v-if="activeBlock.data.source === 'recommend'">
                <label class="field-label">选择商品</label>
                <ProductPickerPanel
                  :model-value="activeBlock.data.productIds || []"
                  @update:model-value="(ids) => { activeBlock!.data.productIds = ids; touch() }"
                />
              </div>
              <div v-if="activeBlock.data.source === 'category'" class="grid gap-4 md:grid-cols-2">
                <div>
                  <label class="field-label">商品分类</label>
                  <SelectMenu
                    :model-value="activeBlock.data.categoryId"
                    :options="categoryOptions"
                    placeholder="请选择分类"
                    @change="(value) => { activeBlock!.data.categoryId = value; touch() }"
                  />
                </div>
                <div>
                  <label class="field-label">排序方式</label>
                  <SelectMenu
                    :model-value="activeBlock.data.sort || 'newest'"
                    :options="[
                      { value: 'newest', label: '最新上架' },
                      { value: 'price_asc', label: '价格从低到高' },
                      { value: 'price_desc', label: '价格从高到低' },
                      { value: 'recommended', label: '推荐顺序' },
                    ]"
                    @change="(value) => { activeBlock!.data.sort = value; touch() }"
                  />
                </div>
              </div>
            </template>

            <template v-else-if="activeBlock.sectionType === 'theme_cards'">
              <div class="grid gap-4 md:grid-cols-2">
                <div>
                  <label class="field-label">展示模式</label>
                  <SelectMenu
                    :model-value="activeBlock.data.mode || 'auto'"
                    :options="[
                      { value: 'auto', label: '自动读取 Theme 集合' },
                      { value: 'manual', label: '手动选择主题' },
                    ]"
                    @change="setThemeMode"
                  />
                </div>
                <div v-if="activeBlock.data.mode !== 'manual'">
                  <label class="field-label">展示数量</label>
                  <input v-model.number="activeBlock.data.limit" class="field" type="number" min="1" max="8" @input="touch" />
                </div>
              </div>
              <div v-if="activeBlock.data.mode === 'manual'">
                <label class="field-label">选择主题</label>
                <div class="grid max-h-72 gap-1 overflow-y-auto border-y border-line py-2 sm:grid-cols-2">
                  <button
                    v-for="option in themeOptions"
                    :key="String(option.value)"
                    type="button"
                    class="flex min-h-10 items-center justify-between px-3 text-left text-[12px] transition-colors"
                    :class="(activeBlock.data.collectionIds || []).includes(option.value) ? 'bg-gold/10 text-gold-deep' : 'text-ink-soft hover:bg-canvas-warm'"
                    @click="toggleThemeCollection(option.value)"
                  >
                    <span class="flex min-w-0 items-center gap-2">
                      <img v-if="option.imageUrl" :src="option.imageUrl" alt="" class="h-8 w-6 shrink-0 rounded-sm object-cover" />
                      <span class="truncate">{{ option.label }}</span>
                    </span>
                    <CheckIcon v-if="(activeBlock.data.collectionIds || []).includes(option.value)" class="h-4 w-4 shrink-0" />
                  </button>
                </div>
                <p v-if="themeOptions.length === 0" class="mt-2 text-[11px] text-ink-faint">请先在分类管理中维护 Theme 集合及集合商品</p>
              </div>
            </template>

            <template v-else-if="activeBlock.sectionType === 'editorial_feature'">
              <div class="max-w-xs">
                <label class="field-label">展示数量</label>
                <input v-model.number="activeBlock.data.limit" class="field" type="number" min="1" max="6" @input="touch" />
              </div>
            </template>

            <template v-else-if="activeBlock.sectionType === 'hero'">
              <div class="flex flex-col gap-4 border-y border-line py-5 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <p class="text-[13px] font-medium text-ink">内容源：Banner 管理</p>
                  <p class="mt-1 text-[12px] text-ink-faint">广告位置：首页 Hero</p>
                </div>
                <button type="button" class="btn-outline shrink-0" @click="openBannerManager">
                  <ArrowTopRightOnSquareIcon class="h-4 w-4" />打开 Banner 管理
                </button>
              </div>
            </template>

            <template v-else-if="activeBlock.sectionType === 'newsletter'">
              <div class="grid gap-4 md:grid-cols-2">
                <div>
                  <label class="field-label">标题（{{ localeTab }}）</label>
                  <input
                    :value="activeBlock.i18n[localeTab].heading"
                    class="field"
                    placeholder="Join the Dreamy List"
                    @input="activeBlock.i18n[localeTab].heading = eventValue($event); touch()"
                  />
                </div>
                <div>
                  <label class="field-label">输入框提示（{{ localeTab }}）</label>
                  <input
                    :value="activeBlock.i18n[localeTab].placeholder"
                    class="field"
                    placeholder="Your email"
                    @input="activeBlock.i18n[localeTab].placeholder = eventValue($event); touch()"
                  />
                </div>
              </div>
              <div>
                <label class="field-label">说明（{{ localeTab }}）</label>
                <textarea
                  :value="activeBlock.i18n[localeTab].description"
                  class="field"
                  rows="3"
                  @input="activeBlock.i18n[localeTab].description = eventValue($event); touch()"
                ></textarea>
              </div>
              <div class="max-w-md">
                <label class="field-label">按钮文案（{{ localeTab }}）</label>
                <input
                  :value="activeBlock.i18n[localeTab].cta"
                  class="field"
                  placeholder="Subscribe"
                  @input="activeBlock.i18n[localeTab].cta = eventValue($event); touch()"
                />
              </div>
            </template>

            <template v-else-if="activeBlock.sectionType === 'custom'">
              <div class="grid gap-4 md:grid-cols-2">
                <div>
                  <label class="field-label">标题（{{ localeTab }}）</label>
                  <input
                    :value="activeBlock.i18n[localeTab].heading"
                    class="field"
                    @input="activeBlock.i18n[localeTab].heading = eventValue($event); touch()"
                  />
                </div>
                <div>
                  <label class="field-label">副标题（{{ localeTab }}）</label>
                  <input
                    :value="activeBlock.i18n[localeTab].subtitle"
                    class="field"
                    @input="activeBlock.i18n[localeTab].subtitle = eventValue($event); touch()"
                  />
                </div>
              </div>
              <div>
                <label class="field-label">内容（{{ localeTab }}）</label>
                <textarea
                  :value="activeBlock.i18n[localeTab].content"
                  class="field"
                  rows="5"
                  @input="activeBlock.i18n[localeTab].content = eventValue($event); touch()"
                ></textarea>
              </div>
              <div>
                <label class="field-label">图片</label>
                <MediaUploadCard
                  :model-value="activeBlock.data.imageUrl"
                  scope="content"
                  aspect="aspect-[16/9]"
                  label="上传区块图片"
                  @update:model-value="(url) => { activeBlock!.data.imageUrl = url; touch() }"
                />
              </div>
              <div class="grid gap-4 md:grid-cols-2">
                <div>
                  <label class="field-label">按钮文案</label>
                  <input v-model="activeBlock.data.ctaText" class="field" @input="touch" />
                </div>
                <div>
                  <label class="field-label">按钮链接</label>
                  <input v-model="activeBlock.data.ctaLink" class="field" placeholder="/about" @input="touch" />
                </div>
              </div>
            </template>
          </div>
        </div>
        <div v-else class="flex min-h-[560px] items-center justify-center text-[13px] text-ink-faint">暂无区块</div>
      </section>
    </div>

    <section v-if="!store.loading && !store.error && workspaceTab === 'preview'" class="overflow-hidden rounded-luxe border border-line bg-white">
      <div class="flex flex-col gap-3 border-b border-line px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
        <div class="flex items-center gap-1">
          <button
            v-for="locale in locales"
            :key="locale"
            type="button"
            class="min-h-9 px-3 text-[12px] uppercase transition-colors"
            :class="localeTab === locale ? 'border-b-2 border-gold text-ink' : 'text-ink-faint hover:text-ink'"
            @click="localeTab = locale"
          >
            {{ locale }}
          </button>
        </div>
        <div class="flex items-center gap-1">
          <button
            type="button"
            class="rounded-luxe p-2 transition-colors"
            :class="previewMode === 'desktop' ? 'bg-ink text-canvas' : 'text-ink-faint hover:bg-canvas-warm'"
            title="桌面预览"
            @click="previewMode = 'desktop'"
          >
            <ComputerDesktopIcon class="h-4 w-4" />
          </button>
          <button
            type="button"
            class="rounded-luxe p-2 transition-colors"
            :class="previewMode === 'mobile' ? 'bg-ink text-canvas' : 'text-ink-faint hover:bg-canvas-warm'"
            title="手机预览"
            @click="previewMode = 'mobile'"
          >
            <DevicePhoneMobileIcon class="h-4 w-4" />
          </button>
          <button type="button" class="btn-ghost ml-1" title="刷新预览" @click="refreshPreview">
            <ArrowPathIcon class="h-4 w-4" />
          </button>
          <button type="button" class="btn-ghost" title="在新窗口打开" @click="openPreviewWindow">
            <ArrowTopRightOnSquareIcon class="h-4 w-4" />
          </button>
        </div>
      </div>
      <div class="relative flex min-h-[680px] justify-center overflow-auto bg-canvas-warm/50 p-3 sm:p-5">
        <div v-if="previewLoading" class="absolute inset-0 z-10 flex items-center justify-center bg-white/80 text-[13px] text-ink-faint">加载预览...</div>
        <iframe
          :key="previewRevision"
          :src="previewUrl"
          title="商城首页预览"
          class="bg-white shadow-panel transition-[width] duration-200"
          :class="previewMode === 'mobile' ? 'h-[780px] w-[390px] max-w-full' : 'h-[calc(100vh-250px)] min-h-[720px] w-full'"
          @load="previewLoading = false"
        ></iframe>
      </div>
    </section>

    <div v-if="showAddBlockModal" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40 px-4 backdrop-blur-sm" @click="showAddBlockModal = false">
      <div class="panel w-full max-w-md p-6" @click.stop>
        <h3 class="font-display text-lg text-ink">添加区块</h3>
        <div class="mt-5">
          <label class="field-label">区块类型</label>
          <SelectMenu
            v-model="newBlockType"
            :options="[
              { value: 'hero', label: '首屏主视觉', disabled: blocks.some((block) => block.sectionType === 'hero') },
              { value: 'theme_cards', label: '分类卡片' },
              { value: 'product_rail', label: '商品推荐' },
              { value: 'editorial_feature', label: '真实婚礼' },
              { value: 'newsletter', label: '邮件订阅' },
              { value: 'custom', label: '自定义内容' },
            ]"
          />
        </div>
        <div class="mt-6 flex justify-end gap-2">
          <button type="button" class="btn-outline" @click="showAddBlockModal = false">取消</button>
          <button
            type="button"
            class="btn-gold"
            :disabled="newBlockType === 'hero' && blocks.some((block) => block.sectionType === 'hero')"
            @click="addBlock"
          >
            添加
          </button>
        </div>
      </div>
    </div>

    <ConfirmDialog
      :open="!!deleteTarget"
      title="删除首页区块"
      :message="deleteTarget ? `确认删除“${typeLabel(deleteTarget.sectionType)}”？保存后首页将立即移除该区块。` : ''"
      confirm-text="删除"
      danger
      :busy="deleting"
      @cancel="deleteTarget = null"
      @confirm="confirmDelete"
    />
    <ConfirmDialog
      :open="leaveDialogOpen"
      title="有未保存的修改"
      message="Shop by theme 等首页设置尚未保存。现在离开将丢失这些修改，是否继续？"
      confirm-text="放弃修改并离开"
      danger
      @cancel="cancelLeave"
      @confirm="confirmLeave"
    />
  </div>
</template>
