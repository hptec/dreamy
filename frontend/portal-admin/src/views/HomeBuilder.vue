<script setup lang="ts">
import { ref, computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { useSortable } from '@vueuse/integrations/useSortable'
import PageHeader from '@/components/PageHeader.vue'
import Toggle from '@/components/Toggle.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import BannerFormDrawer from '@/components/drawers/BannerFormDrawer.vue'
import MediaUploadCard from '@/components/MediaUploadCard.vue'
import ProductPickerPanel from '@/components/ProductPickerPanel.vue'
import { useHomeSectionStore } from '@/stores/homeSection'
import { useBannersStore } from '@/stores/banners'
import { useCategoriesStore } from '@/stores/categories'
import { useToast } from '@/composables/useToast'
import {
  createHomePreviewToken,
  getHomePublicationStatus,
  getStoreHomePreview,
  listHomeReleases,
  publishHomePage,
  rollbackHomePage,
  type HomePagePublicationStatus,
  type HomePageRelease,
  type StoreHomePage,
} from '@/api/siteBuilder'
import { BannerPosition, BannerStatus, type Banner } from '@/api/types'
import {
  Bars3Icon, EyeIcon, PencilSquareIcon, RocketLaunchIcon,
  ComputerDesktopIcon, DevicePhoneMobileIcon, ArchiveBoxArrowDownIcon,
  ArrowPathIcon, ClockIcon
} from '@heroicons/vue/24/outline'

const store = useHomeSectionStore()
const bannerStore = useBannersStore()
const categoriesStore = useCategoriesStore()
const toast = useToast()

// 前台商城地址（EN 无前缀，ES/FR 带 locale 前缀）
const STORE_BASE = import.meta.env.VITE_STORE_BASE_URL || 'http://localhost:5173'

// 本地可编辑副本（从 store 同步）
const blocks = ref<any[]>([])
const activeId = ref<number | null>(null)
const preview = ref('desktop')
const dirty = ref(false)
const saving = ref(false)
const publishing = ref(false)

// locale tab（EN/ES/FR）
const localeTab = ref('en')

// 真实预览数据
const previewData = ref<StoreHomePage | null>(null)
const previewLoading = ref(false)
const publicationStatus = ref<HomePagePublicationStatus | null>(null)
const releases = ref<HomePageRelease[]>([])
const showHistory = ref(false)
const showPublishModal = ref(false)
const publishName = ref('')
const rollbackTarget = ref<HomePageRelease | null>(null)
const rollbackBusy = ref(false)
const bannerDrawer = ref(false)
const editingBanner = ref<Banner | null>(null)
const bannerIdsBeforeCreate = ref<Set<number>>(new Set())

// 拖拽排序
const blockListEl = ref<HTMLElement | null>(null)

// 添加区块模态框
const showAddBlockModal = ref(false)
const newBlockType = ref('custom')

useSortable(blockListEl, blocks, {
  animation: 200,
  handle: '.drag-handle',
  onUpdate: () => {
    // 更新 sortOrder
    blocks.value.forEach((b, idx) => {
      b.sortOrder = idx
    })
    dirty.value = true
  }
})

onMounted(async () => {
  window.addEventListener('beforeunload', guardBrowserLeave)
  await store.fetch()
  await Promise.allSettled([fetchHeroBanners(), categoriesStore.fetch(), fetchPublication()])
  syncFromStore()
  await fetchPreviewData()
})

onBeforeUnmount(() => window.removeEventListener('beforeunload', guardBrowserLeave))

onBeforeRouteLeave(() => {
  if (!dirty.value) return true
  return window.confirm('当前有未保存的首页草稿，确定离开吗？')
})

// 监听 locale 变化，重新获取预览数据
watch(localeTab, async () => {
  await fetchPreviewData()
})

async function fetchPreviewData() {
  previewLoading.value = true
  try {
    previewData.value = await getStoreHomePreview(localeTab.value)
    syncSelectedHeroPreview()
  } catch (e: any) {
    console.error('Failed to fetch preview data:', e)
    toast.error('预览数据加载失败')
  } finally {
    previewLoading.value = false
  }
}

async function fetchHeroBanners() {
  bannerStore.positionFilter = BannerPosition.HERO
  await bannerStore.fetch()
}

async function fetchPublication() {
  try {
    const [status, history] = await Promise.all([
      getHomePublicationStatus(),
      listHomeReleases(20),
    ])
    publicationStatus.value = status
    releases.value = history.items
  } catch (e: any) {
    toast.error(e.message ?? '发布状态加载失败')
  }
}

function syncFromStore() {
  blocks.value = store.sections.map((s) => ({
    id: s.id,
    sectionType: s.sectionType,
    type: typeLabel(s.sectionType),
    label: s.label || typeLabel(s.sectionType),
    enabled: s.enabled,
    sortOrder: s.sortOrder,
    data: normalizeBlockData(s.sectionType, parseJson(s.dataJson, {})),
    i18n: parseJson(s.i18nJson, {}),
    version: s.version,
  }))
  if (blocks.value.length > 0 && !activeId.value) {
    activeId.value = blocks.value[0].id
  }
  dirty.value = false
}

function normalizeBlockData(sectionType: string, raw: any) {
  const data = { ...(raw || {}) }
  if (sectionType === 'theme_cards') {
    if (data.limit == null && data.count != null) data.limit = data.count
    delete data.count
    data.mode ||= 'auto'
    data.categoryIds ||= []
  }
  if (sectionType === 'product_rail') {
    data.source ||= 'new_arrival'
    if (data.sort === 'new') data.sort = 'newest'
    if (data.sort === 'best') data.sort = 'recommended'
    data.productIds ||= []
  }
  return data
}

function parseJson(str: any, fallback: any) {
  if (!str) return fallback
  try { return typeof str === 'string' ? JSON.parse(str) : str } catch { return fallback }
}

function typeLabel(t: string) {
  const map: Record<string, string> = { hero: '首屏主视觉', theme_cards: 'ThemeCards', product_rail: 'ProductRail',
    editorial_feature: 'EditorialFeature', newsletter: 'Newsletter', custom: 'Custom' }
  return map[t] || t
}

const active = () => blocks.value.find((b) => b.id === activeId.value)
const heroBanners = computed(() => bannerStore.list.filter((b) => b.position === BannerPosition.HERO))
const selectedHeroBanner = computed(() => {
  const selectedId = active()?.sectionType === 'hero' ? active()?.data?.bannerId : null
  return heroBanners.value.find((banner) => banner.id === selectedId)
    || heroBanners.value.find((banner) => banner.status === BannerStatus.PUBLISHED)
    || heroBanners.value[0]
})
const heroBannerOptions = computed(() => heroBanners.value.map((banner) => ({
  value: banner.id,
  label: `${banner.name} · ${banner.status === BannerStatus.DRAFT ? '草稿' : banner.status === BannerStatus.PUBLISHED ? '已发布' : '已下线'}`,
})))
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

function touch() { dirty.value = true }

function guardBrowserLeave(event: BeforeUnloadEvent) {
  if (!dirty.value) return
  event.preventDefault()
}

function currentLabel(b: any) {
  if (!b) return ''
  return (b.i18n && b.i18n[localeTab.value] && b.i18n[localeTab.value].label) || b.label
}

async function saveDraft(showSuccess = true) {
  if (!dirty.value) return true
  saving.value = true
  try {
    await store.saveDraft(blocks.value.map((b) => ({
        id: b.id,
        sectionType: b.sectionType,
        enabled: b.enabled,
        sortOrder: b.sortOrder,
        dataJson: b.data,
        i18nJson: b.i18n,
        label: b.label,
        version: b.version,
      })))
    syncFromStore()
    await fetchPublication()
    await fetchPreviewData()
    if (showSuccess) toast.success('草稿已保存，线上内容未改变')
    return true
  } catch (e: any) {
    toast.error(e.message ?? '草稿保存失败')
    return false
  } finally {
    saving.value = false
  }
}

function onToggle(b: any, val: boolean) {
  b.enabled = val
  touch()
}

function setProductSource(value: string | number | null | undefined) {
  if (!active()) return
  active().data.source = value
  if (value === 'recommend') active().data.productIds ||= []
  touch()
}

function setThemeMode(value: string | number | null | undefined) {
  if (!active()) return
  active().data.mode = value
  if (value === 'manual') active().data.categoryIds ||= []
  touch()
}

function toggleThemeCategory(id: number) {
  if (!active()) return
  const selected: number[] = active().data.categoryIds || []
  active().data.categoryIds = selected.includes(id)
    ? selected.filter((item) => item !== id)
    : [...selected, id]
  touch()
}

async function addBlock() {
  if (!(await saveDraft(false))) return
  try {
    const newBlock = await store.create({
      sectionType: newBlockType.value,
      enabled: true,
      sortOrder: blocks.value.length,
      dataJson: newBlockType.value === 'hero' && heroBanners.value[0]
        ? { bannerId: heroBanners.value[0].id }
        : {},
      i18nJson: { en: {}, es: {}, fr: {} },
      label: typeLabel(newBlockType.value),
    })
    syncFromStore()
    activeId.value = newBlock.id
    showAddBlockModal.value = false
    await Promise.all([fetchPublication(), fetchPreviewData()])
    toast.success('区块已添加')
  } catch (e: any) {
    toast.error(e.message ?? '添加失败')
  }
}

function selectHeroBanner(value: number) {
  if (!active()) return
  active().data = { bannerId: Number(value) }
  touch()
  syncSelectedHeroPreview()
}

function editSelectedBanner() {
  if (!selectedHeroBanner.value) return
  editingBanner.value = selectedHeroBanner.value
  bannerDrawer.value = true
}

function createHeroBanner() {
  bannerIdsBeforeCreate.value = new Set(heroBanners.value.map((banner) => banner.id))
  editingBanner.value = null
  bannerDrawer.value = true
}

async function onBannerDrawerClose() {
  bannerDrawer.value = false
  await fetchHeroBanners()
  const hero = active()?.sectionType === 'hero' ? active() : blocks.value.find((b) => b.sectionType === 'hero')
  const createdBanner = heroBanners.value.find((banner) => !bannerIdsBeforeCreate.value.has(banner.id))
  if (hero && createdBanner) {
    hero.data = { bannerId: createdBanner.id }
    touch()
  } else if (hero && !hero.data.bannerId && heroBanners.value[0]) {
    hero.data = { bannerId: heroBanners.value[0].id }
    touch()
  }
  bannerIdsBeforeCreate.value = new Set()
  await Promise.all([fetchPublication(), fetchPreviewData()])
}

function syncSelectedHeroPreview() {
  const banner = selectedHeroBanner.value
  const hero = previewData.value?.sections.find((section) => section.sectionType === 'hero')
  if (!banner || !hero) return
  const translated = localeTab.value === 'en'
    ? null
    : banner.translations?.find((item) => item.locale === localeTab.value)
  hero.data = {
    ...hero.data,
    bannerId: banner.id,
    imageUrl: banner.imageUrl,
    title: translated?.title || banner.title,
    subtitle: translated?.subtitle || banner.subtitle,
    ctaText: translated?.ctaText || banner.ctaText,
    ctaLink: banner.ctaLink,
    ctaTextSecondary: translated?.ctaTextSecondary || banner.ctaTextSecondary,
    ctaLinkSecondary: banner.ctaLinkSecondary,
  }
}

function bannerStatusLabel(status: BannerStatus) {
  if (status === BannerStatus.PUBLISHED) return '已发布素材'
  if (status === BannerStatus.ARCHIVED) return '已下线素材'
  return '草稿素材'
}

function formatDate(value: string | null | undefined) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function eventValue(event: Event) {
  return (event.target as HTMLInputElement | HTMLTextAreaElement).value
}

async function openPrivatePreview() {
  if (!(await saveDraft(false))) return
  try {
    const result = await createHomePreviewToken()
    const localePath = localeTab.value === 'en' ? '' : `/${localeTab.value}`
    const url = `${STORE_BASE}${localePath}/preview/home?token=${encodeURIComponent(result.token)}`
    window.open(url, '_blank', 'noopener,noreferrer')
    toast.info('已生成 30 分钟私有预览链接')
  } catch (e: any) {
    toast.error(e.message ?? '生成预览链接失败')
  }
}

async function requestPublish() {
  if (!(await saveDraft(false))) return
  await fetchPublication()
  if (!publicationStatus.value?.draftModified) {
    toast.info('当前草稿与线上版本一致，无需重复发布')
    return
  }
  publishName.value = `首页发布 ${new Date().toLocaleString('zh-CN', { hour12: false })}`
  showPublishModal.value = true
}

async function confirmPublish() {
  publishing.value = true
  try {
    if (!publicationStatus.value?.draftRevision) {
      toast.error('草稿状态已过期，请刷新后重试')
      return
    }
    const release = await publishHomePage(
      publishName.value.trim() || undefined,
      publicationStatus.value.draftRevision,
    )
    showPublishModal.value = false
    await fetchPublication()
    toast.success(`首页 V${release.releaseNo} 已原子发布`)
  } catch (e: any) {
    if (e?.code === 409807) {
      showPublishModal.value = false
      await fetchPublication()
      toast.error('草稿在确认期间已被修改，请重新预览后再发布')
    } else if (e?.code === 409806) {
      showPublishModal.value = false
      await fetchPublication()
      toast.info('当前草稿与线上版本一致，无需重复发布')
    } else {
      toast.error(e.message ?? '发布失败')
    }
  } finally {
    publishing.value = false
  }
}

async function confirmRollback() {
  if (!rollbackTarget.value) return
  rollbackBusy.value = true
  try {
    const release = await rollbackHomePage(rollbackTarget.value.id)
    rollbackTarget.value = null
    await Promise.all([store.fetch(), fetchPublication()])
    syncFromStore()
    await fetchPreviewData()
    toast.success(`已回滚并发布为 V${release.releaseNo}`)
  } catch (e: any) {
    toast.error(e.message ?? '回滚失败')
  } finally {
    rollbackBusy.value = false
  }
}
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Site Builder" title="首页装修" subtitle="先保存为草稿并私密预览，确认后再整页发布到线上">
      <template #actions>
        <button class="btn-outline" @click="showHistory = !showHistory"><ClockIcon class="h-4 w-4" />版本记录</button>
        <button class="btn-outline" :disabled="saving" @click="openPrivatePreview"><EyeIcon class="h-4 w-4" />私密预览</button>
        <button class="btn-outline" :disabled="!dirty || saving" @click="saveDraft()">
          <ArchiveBoxArrowDownIcon class="h-4 w-4" />{{ saving ? '保存中…' : '保存草稿' }}
        </button>
        <button class="btn-gold" :disabled="dirty || saving || publishing || !publicationStatus?.draftModified" @click="requestPublish">
          <RocketLaunchIcon class="h-4 w-4" />发布到线上
        </button>
      </template>
    </PageHeader>

    <div class="mb-5 flex flex-col gap-3 rounded-luxe border border-line bg-canvas px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
      <div class="flex flex-wrap items-center gap-3 text-[12px]">
        <span v-if="dirty" class="badge bg-warn/14 text-warn"><span class="h-1.5 w-1.5 rounded-full bg-current"></span>有未保存编辑</span>
        <span v-else-if="publicationStatus?.draftModified" class="badge bg-info/12 text-info"><span class="h-1.5 w-1.5 rounded-full bg-current"></span>草稿待发布</span>
        <span v-else class="badge bg-sage/14 text-sage-deep"><span class="h-1.5 w-1.5 rounded-full bg-current"></span>草稿与线上一致</span>
        <span class="text-ink-soft">
          线上版本：<strong class="font-medium text-ink">{{ publicationStatus?.activeReleaseNo ? `V${publicationStatus.activeReleaseNo}` : '尚未发布' }}</strong>
          <template v-if="publicationStatus?.activeReleaseName"> · {{ publicationStatus.activeReleaseName }}</template>
        </span>
      </div>
      <p class="text-[11px] text-ink-faint">
        {{ dirty ? '先保存草稿，才能生成可靠预览或发布。' : publicationStatus?.draftModified ? '草稿只在后台和私密链接可见，线上访客仍看到当前版本。' : `线上最近发布于 ${formatDate(publicationStatus?.publishedAt)}` }}
      </p>
    </div>

    <div v-if="showHistory" class="panel mb-5 overflow-hidden">
      <div class="flex items-center justify-between border-b border-line px-5 py-3">
        <div>
          <p class="text-[13px] font-medium text-ink">首页版本记录</p>
          <p class="mt-0.5 text-[11px] text-ink-faint">回滚会创建一个新版本并立即上线，同时将该版本恢复为当前草稿。</p>
        </div>
        <button class="btn-ghost" title="刷新版本记录" @click="fetchPublication"><ArrowPathIcon class="h-4 w-4" /></button>
      </div>
      <div v-if="releases.length" class="divide-y divide-line">
        <div v-for="release in releases" :key="release.id" class="flex flex-col gap-3 px-5 py-3 sm:flex-row sm:items-center sm:justify-between">
          <div class="min-w-0">
            <div class="flex flex-wrap items-center gap-2">
              <strong class="text-[13px] text-ink">V{{ release.releaseNo }}</strong>
              <span v-if="release.active" class="badge bg-sage/14 text-sage-deep">当前线上</span>
              <span v-if="release.sourceReleaseId" class="badge bg-ink/8 text-ink-soft">回滚版本</span>
              <span class="truncate text-[13px] text-ink-soft">{{ release.name }}</span>
            </div>
            <p class="mt-1 text-[11px] text-ink-faint">{{ formatDate(release.publishedAt) }}<template v-if="release.publishedBy"> · 管理员 #{{ release.publishedBy }}</template></p>
          </div>
          <button v-if="!release.active" class="btn-outline shrink-0" :disabled="dirty" @click="rollbackTarget = release">
            <ArrowPathIcon class="h-4 w-4" />回滚到此版本
          </button>
        </div>
      </div>
      <p v-else class="px-5 py-8 text-center text-[12px] text-ink-faint">暂无发布记录</p>
    </div>

    <div v-if="store.loading" class="panel p-8 text-center text-ink-faint">加载中...</div>
    <div v-else-if="store.error" class="panel p-8 text-center text-danger">{{ store.error }}</div>
    <div v-else class="grid grid-cols-1 gap-6 lg:grid-cols-[260px_minmax(0,1fr)_320px]">
      <!-- 区块列表 -->
      <div class="panel p-4">
        <div class="flex items-center justify-between mb-3">
          <p class="eyebrow">页面区块</p>
          <button @click="showAddBlockModal = true" class="text-[11px] text-gold hover:text-gold-deep">+ 添加区块</button>
        </div>
        <div ref="blockListEl" class="space-y-2">
          <div v-for="(b, i) in blocks" :key="b.id"
            @click="activeId = b.id"
            class="group flex cursor-pointer items-center gap-2 rounded-luxe border px-3 py-2.5 text-[13px] transition-colors"
            :class="activeId === b.id ? 'border-gold bg-gold/8' : 'border-line hover:bg-canvas-warm'">
            <Bars3Icon class="drag-handle h-4 w-4 cursor-grab active:cursor-grabbing text-ink-faint" />
            <div class="min-w-0 flex-1">
              <p class="truncate font-medium" :class="b.enabled ? 'text-ink' : 'text-ink-faint line-through'">{{ currentLabel(b) }}</p>
              <p class="text-[11px] text-ink-faint">{{ b.type }}</p>
            </div>
            <Toggle :model-value="b.enabled" @update:model-value="onToggle(b, $event)" @click.stop />
          </div>
        </div>
      </div>

      <!-- 实时预览 -->
      <div class="panel overflow-hidden">
        <div class="flex items-center justify-between border-b border-line px-4 py-2.5">
          <p class="text-[12px] text-ink-faint">草稿画布 · {{ localeTab.toUpperCase() }}<span v-if="dirty">（含未保存编辑）</span></p>
          <div class="flex gap-1">
            <button @click="preview = 'desktop'" class="rounded-luxe p-1.5" :class="preview === 'desktop' ? 'bg-ink text-canvas' : 'text-ink-faint hover:bg-canvas-warm'"><ComputerDesktopIcon class="h-4 w-4" /></button>
            <button @click="preview = 'mobile'" class="rounded-luxe p-1.5" :class="preview === 'mobile' ? 'bg-ink text-canvas' : 'text-ink-faint hover:bg-canvas-warm'"><DevicePhoneMobileIcon class="h-4 w-4" /></button>
          </div>
        </div>
        <div class="max-h-[640px] overflow-y-auto bg-canvas-warm/40 p-4">
          <div v-if="previewLoading" class="flex items-center justify-center py-12 text-ink-faint text-[13px]">
            加载预览数据...
          </div>
          <div v-else-if="!previewData" class="flex items-center justify-center py-12 text-danger text-[13px]">
            预览数据加载失败
          </div>
          <div v-else class="mx-auto bg-canvas transition-all" :class="preview === 'mobile' ? 'max-w-[380px]' : 'max-w-full'">
            <template v-for="(section, idx) in previewData.sections" :key="idx">
              <!-- Hero 区块 -->
              <div v-if="section.sectionType === 'hero'" class="grid min-h-[260px] sm:grid-cols-2">
                <div class="flex items-center bg-canvas p-6">
                  <div>
                    <p v-if="section.data.eyebrow" class="text-[10px] uppercase tracking-luxe text-gold-deep">{{ section.data.eyebrow }}</p>
                    <h2 class="mt-2 whitespace-pre-line font-display text-2xl font-medium leading-tight text-ink">{{ section.data.title || '首屏主视觉标题' }}</h2>
                    <p v-if="section.data.subtitle" class="mt-2 text-[13px] text-ink-faint">{{ section.data.subtitle }}</p>
                    <div v-if="section.data.ctaText" class="mt-4 flex gap-2">
                      <button class="btn-gold">{{ section.data.ctaText }}</button>
                      <button v-if="section.data.ctaTextSecondary" class="btn-outline">{{ section.data.ctaTextSecondary }}</button>
                    </div>
                  </div>
                </div>
                <div class="min-h-[220px] bg-cover bg-center" :style="section.data.imageUrl ? `background-image: url(${section.data.imageUrl})` : ''" :class="!section.data.imageUrl && 'bg-canvas-warm/40 flex items-center justify-center text-ink-faint text-[12px]'">
                  <span v-if="!section.data.imageUrl">首屏主视觉图片</span>
                </div>
              </div>

              <!-- Newsletter 区块 -->
              <div v-else-if="section.sectionType === 'newsletter'" class="bg-sage/12 p-6 text-center">
                <h3 class="font-display text-lg text-ink">{{ section.data.heading || 'Newsletter' }}</h3>
                <div class="mx-auto mt-4 flex max-w-md gap-2">
                  <input type="email" :placeholder="section.data.placeholder || 'Your email'" class="field flex-1 text-[13px]" />
                  <button class="btn-gold">{{ section.data.cta || 'Subscribe' }}</button>
                </div>
              </div>

              <!-- ThemeCards 区块 -->
              <div v-else-if="section.sectionType === 'theme_cards'" class="p-6">
                <h3 class="font-display text-xl text-ink text-center mb-4">{{ section.data.heading || 'Shop by Theme' }}</h3>
                <div class="grid grid-cols-2 gap-3 sm:grid-cols-3">
                  <div v-for="card in section.data.cards" :key="card.id" class="rounded-luxe border border-line p-3 hover:border-gold transition-colors cursor-pointer">
                    <p class="text-[13px] font-medium text-ink">{{ card.name }}</p>
                    <p class="text-[11px] text-ink-faint mt-0.5">{{ card.productCount }} 件商品</p>
                  </div>
                </div>
              </div>

              <!-- ProductRail 区块 -->
              <div v-else-if="section.sectionType === 'product_rail'" class="p-6">
                <h3 class="font-display text-xl text-ink text-center mb-4">{{ section.data.heading || 'Products' }}</h3>
                <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
                  <div v-for="product in section.data.products" :key="product.id" class="group cursor-pointer">
                    <div class="aspect-[3/4] rounded-luxe bg-canvas-warm/60 bg-cover bg-center" :style="product.imageUrl ? `background-image: url(${product.imageUrl})` : ''"></div>
                    <p class="mt-2 text-[12px] font-medium text-ink group-hover:text-gold transition-colors">{{ product.name }}</p>
                    <div class="flex items-center gap-2 mt-1">
                      <p class="text-[13px] text-ink-faint">${{ product.price }}</p>
                      <span v-if="product.isNew" class="text-[9px] uppercase tracking-wider px-1.5 py-0.5 rounded bg-gold/14 text-gold-deep">New</span>
                      <span v-if="product.isBest" class="text-[9px] uppercase tracking-wider px-1.5 py-0.5 rounded bg-sage/14 text-sage-deep">Best</span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- EditorialFeature 区块 -->
              <div v-else-if="section.sectionType === 'editorial_feature'" class="p-6">
                <h3 class="font-display text-xl text-ink text-center mb-4">{{ section.data.heading || 'Real Weddings' }}</h3>
                <div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
                  <div v-for="story in section.data.stories" :key="story.id" class="group cursor-pointer">
                    <div class="aspect-[4/5] rounded-luxe bg-canvas-warm/60 bg-cover bg-center" :style="story.cover ? `background-image: url(${story.cover})` : ''"></div>
                    <p class="mt-2 text-[13px] font-medium text-ink group-hover:text-gold transition-colors">{{ story.title || story.couple }}</p>
                    <p class="text-[11px] text-ink-faint mt-0.5">{{ story.location }} · {{ story.theme }}</p>
                  </div>
                </div>
              </div>

              <!-- 未知类型区块 -->
              <div v-else class="p-6">
                <div v-if="section.data.imageUrl" class="aspect-[16/9] rounded-luxe bg-cover bg-center mb-4" :style="`background-image: url(${section.data.imageUrl})`"></div>
                <div class="text-center max-w-2xl mx-auto">
                  <h3 v-if="section.data.heading" class="font-display text-xl text-ink">{{ section.data.heading }}</h3>
                  <p v-if="section.data.subtitle" class="mt-2 text-[13px] text-ink-faint">{{ section.data.subtitle }}</p>
                  <p v-if="section.data.content" class="mt-3 text-[13px] text-ink whitespace-pre-line">{{ section.data.content }}</p>
                  <button v-if="section.data.ctaText" class="btn-gold mt-4">{{ section.data.ctaText }}</button>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>

      <!-- 区块属性编辑 -->
      <div class="panel p-4">
        <p class="eyebrow mb-3 flex items-center gap-1.5"><PencilSquareIcon class="h-3.5 w-3.5" />区块属性</p>
        <div v-if="active()" :key="activeId ?? 'none'" class="space-y-4">
          <p class="text-[13px] font-medium text-ink">{{ active().type }} 区块</p>

          <!-- i18n locale tab -->
          <div class="flex gap-1 border-b border-line">
            <button v-for="loc in ['en', 'es', 'fr']" :key="loc" @click="localeTab = loc"
              class="px-3 py-1 text-[11px] uppercase"
              :class="localeTab === loc ? 'border-b-2 border-gold text-ink' : 'text-ink-faint'">
              {{ loc }}
            </button>
          </div>

          <div>
            <label class="field-label">标签（{{ localeTab }}）</label>
            <input :value="(active().i18n[localeTab] || {}).label || active().label"
              @input="((active().i18n[localeTab] = active().i18n[localeTab] || {}), (active().i18n[localeTab].label = eventValue($event)), touch())"
              class="field text-[12px]" />
          </div>

          <template v-if="['product_rail', 'theme_cards', 'editorial_feature'].includes(active().sectionType)">
            <div>
              <label class="field-label">眉标题（{{ localeTab }}）</label>
              <input :value="(active().i18n[localeTab] || {}).eyebrow"
                @input="((active().i18n[localeTab] = active().i18n[localeTab] || {}), (active().i18n[localeTab].eyebrow = eventValue($event)), touch())"
                class="field text-[12px]" placeholder="Explore" />
            </div>
            <div>
              <label class="field-label">区块标题（{{ localeTab }}）</label>
              <input :value="(active().i18n[localeTab] || {}).heading"
                @input="((active().i18n[localeTab] = active().i18n[localeTab] || {}), (active().i18n[localeTab].heading = eventValue($event)), touch())"
                class="field text-[12px]" placeholder="Shop by Theme" />
            </div>
            <div>
              <label class="field-label">区块说明（{{ localeTab }}）</label>
              <textarea :value="(active().i18n[localeTab] || {}).description"
                @input="((active().i18n[localeTab] = active().i18n[localeTab] || {}), (active().i18n[localeTab].description = eventValue($event)), touch())"
                class="field text-[12px]" rows="2" />
            </div>
          </template>

          <template v-if="active().sectionType === 'product_rail'">
            <div>
              <label class="field-label">商品来源</label>
              <SelectMenu :model-value="active().data.source || 'new_arrival'"
                :options="[
                  { value: 'new_arrival', label: '新品 New Arrivals' },
                  { value: 'best_seller', label: '畅销 Best Sellers' },
                  { value: 'recommend', label: '人工推荐 Manual' },
                  { value: 'category', label: '按分类 By Category' }
                ]"
                @change="setProductSource" />
            </div>
            <div>
              <label class="field-label">展示数量（1-12）</label>
              <input v-model.number="active().data.limit" @input="touch" type="number" min="1" max="12" class="field text-[12px]" />
            </div>
            <div v-if="active().data.source === 'recommend'">
              <label class="field-label">选择商品</label>
              <ProductPickerPanel :model-value="active().data.productIds || []" @update:model-value="(ids) => { active().data.productIds = ids; touch() }" />
              <p class="mt-1 text-[11px] text-ink-faint">按选择顺序展示；已下架商品会在预览和发布时自动忽略。</p>
            </div>
            <div v-if="active().data.source === 'category'">
              <label class="field-label">商品分类</label>
              <SelectMenu :model-value="active().data.categoryId" :options="categoryOptions" placeholder="请选择分类" @change="(v) => { active().data.categoryId = v; touch() }" />
            </div>
            <div v-if="active().data.source === 'category'">
              <label class="field-label">排序方式</label>
              <SelectMenu :model-value="active().data.sort || 'newest'"
                :options="[
                  { value: 'newest', label: '最新上架' },
                  { value: 'price_asc', label: '价格从低到高' },
                  { value: 'price_desc', label: '价格从高到低' },
                  { value: 'recommended', label: '推荐顺序' }
                ]"
                @change="(v) => { active().data.sort = v; touch() }" />
            </div>
          </template>

          <template v-else-if="active().sectionType === 'theme_cards'">
            <div>
              <label class="field-label">展示模式</label>
              <SelectMenu :model-value="active().data.mode || 'auto'"
                :options="[
                  { value: 'auto', label: '自动（根分类）' },
                  { value: 'manual', label: '手动选择' }
                ]"
                @change="setThemeMode" />
            </div>
            <div v-if="active().data.mode !== 'manual'">
              <label class="field-label">展示数量（1-8）</label>
              <input v-model.number="active().data.limit" @input="touch" type="number" min="1" max="8" class="field text-[12px]" />
            </div>
            <div v-if="active().data.mode === 'manual'">
              <label class="field-label">选择分类</label>
              <div class="max-h-52 space-y-1 overflow-y-auto rounded-luxe border border-line p-2">
                <button v-for="option in categoryOptions" :key="String(option.value)" type="button"
                  class="flex w-full items-center justify-between rounded-luxe px-2.5 py-2 text-left text-[12px] transition-colors"
                  :class="(active().data.categoryIds || []).includes(option.value) ? 'bg-gold/10 text-gold-deep' : 'text-ink-soft hover:bg-canvas-warm'"
                  @click="toggleThemeCategory(option.value)">
                  <span>{{ option.label }}</span>
                  <span v-if="(active().data.categoryIds || []).includes(option.value)">已选</span>
                </button>
              </div>
              <p class="text-[11px] text-ink-faint mt-1">按点击选择的顺序展示，取消后可重新选择调整顺序。</p>
            </div>
          </template>

          <template v-else-if="active().sectionType === 'editorial_feature'">
            <div>
              <label class="field-label">展示数量（1-6）</label>
              <input v-model.number="active().data.limit" @input="touch" type="number" min="1" max="6" class="field text-[12px]" />
            </div>
            <p class="rounded-luxe bg-canvas-warm/60 px-3 py-2 text-[11px] leading-relaxed text-ink-faint">按婚礼日期从新到旧展示。当前案例数据没有可靠的人气指标，因此不提供会误导结果的“人气/随机”选项。</p>
          </template>

          <template v-else-if="active().sectionType === 'hero'">
            <div>
              <label class="field-label">当前主视觉</label>
              <SelectMenu :model-value="active().data.bannerId" :options="heroBannerOptions" placeholder="请选择主视觉" @change="(v) => selectHeroBanner(Number(v))" />
            </div>
            <div v-if="selectedHeroBanner" class="overflow-hidden rounded-luxe border border-line">
              <img :src="selectedHeroBanner.imageUrl" :alt="selectedHeroBanner.name" class="aspect-[16/7] w-full object-cover" />
              <div class="space-y-2 p-3">
                <div class="flex items-start justify-between gap-2">
                  <div class="min-w-0">
                    <p class="truncate text-[12px] font-medium text-ink">{{ selectedHeroBanner.name }}</p>
                    <p class="text-[11px] text-ink-faint">{{ bannerStatusLabel(selectedHeroBanner.status) }} · 发布首页时会冻结图片和三语文案</p>
                  </div>
                  <button class="shrink-0 text-[11px] text-gold-deep hover:text-gold" @click="editSelectedBanner">编辑内容</button>
                </div>
                <p v-if="selectedHeroBanner.title" class="line-clamp-2 text-[12px] text-ink-soft">{{ selectedHeroBanner.title }}</p>
              </div>
            </div>
            <button class="btn-outline w-full justify-center" @click="createHeroBanner">+ 新增主视觉</button>
            <p class="rounded-luxe bg-info/8 px-3 py-2 text-[11px] leading-relaxed text-ink-soft">这里已合并首页 Hero 与 Banner 的操作：选择、创建和编辑都在当前页面完成。素材自身状态不会单独控制首页发布；首页以整页版本为准。</p>
          </template>

          <template v-else-if="active().sectionType === 'newsletter'">
            <div>
              <label class="field-label">标题文案（{{ localeTab }}）</label>
              <input :value="(active().i18n[localeTab] || {}).heading"
                @input="((active().i18n[localeTab] = active().i18n[localeTab] || {}), (active().i18n[localeTab].heading = eventValue($event)), touch())"
                class="field text-[12px]" placeholder="Join the Dreamy List" />
            </div>
            <div>
              <label class="field-label">说明文案（{{ localeTab }}）</label>
              <input :value="(active().i18n[localeTab] || {}).description"
                @input="((active().i18n[localeTab] = active().i18n[localeTab] || {}), (active().i18n[localeTab].description = eventValue($event)), touch())"
                class="field text-[12px]" placeholder="Get exclusive offers..." />
            </div>
            <div>
              <label class="field-label">输入框占位符（{{ localeTab }}）</label>
              <input :value="(active().i18n[localeTab] || {}).placeholder"
                @input="((active().i18n[localeTab] = active().i18n[localeTab] || {}), (active().i18n[localeTab].placeholder = eventValue($event)), touch())"
                class="field text-[12px]" placeholder="Your email" />
            </div>
            <div>
              <label class="field-label">按钮文案（{{ localeTab }}）</label>
              <input :value="(active().i18n[localeTab] || {}).cta"
                @input="((active().i18n[localeTab] = active().i18n[localeTab] || {}), (active().i18n[localeTab].cta = eventValue($event)), touch())"
                class="field text-[12px]" placeholder="Subscribe" />
            </div>
          </template>

          <!-- 自定义区块 -->
          <template v-else-if="active().sectionType === 'custom'">
            <div>
              <label class="field-label">标题（{{ localeTab }}）</label>
              <input :value="(active().i18n[localeTab] || {}).heading"
                @input="((active().i18n[localeTab] = active().i18n[localeTab] || {}), (active().i18n[localeTab].heading = eventValue($event)), touch())"
                class="field text-[12px]" placeholder="自定义区块标题" />
            </div>
            <div>
              <label class="field-label">副标题（{{ localeTab }}）</label>
              <input :value="(active().i18n[localeTab] || {}).subtitle"
                @input="((active().i18n[localeTab] = active().i18n[localeTab] || {}), (active().i18n[localeTab].subtitle = eventValue($event)), touch())"
                class="field text-[12px]" placeholder="自定义区块副标题" />
            </div>
            <div>
              <label class="field-label">内容（{{ localeTab }}）</label>
              <textarea :value="(active().i18n[localeTab] || {}).content"
                @input="((active().i18n[localeTab] = active().i18n[localeTab] || {}), (active().i18n[localeTab].content = eventValue($event)), touch())"
                class="field text-[12px]" rows="4" placeholder="自定义区块内容..."></textarea>
            </div>
            <div>
              <label class="field-label">图片（选填）</label>
              <MediaUploadCard :model-value="active().data.imageUrl" scope="content" aspect="aspect-[16/9]" label="上传区块图片" @update:model-value="(url) => { active().data.imageUrl = url; touch() }" />
            </div>
            <div>
              <label class="field-label">按钮文案（选填）</label>
              <input v-model="active().data.ctaText" @input="touch" class="field text-[12px]" placeholder="了解更多" />
            </div>
            <div>
              <label class="field-label">按钮链接（选填）</label>
              <input v-model="active().data.ctaLink" @input="touch" class="field text-[12px]" placeholder="/about" />
            </div>
          </template>
        </div>
      </div>
    </div>

    <!-- 添加区块模态框 -->
    <div v-if="showAddBlockModal" @click="showAddBlockModal = false" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40 backdrop-blur-sm">
      <div @click.stop class="panel w-full max-w-md p-6 mx-4">
        <h3 class="text-lg font-display text-ink mb-4">添加新区块</h3>
        <div class="space-y-4">
          <div>
            <label class="field-label">区块类型</label>
            <SelectMenu v-model="newBlockType"
              :options="[
                { value: 'hero', label: '首屏主视觉', disabled: blocks.some((item) => item.sectionType === 'hero') },
                { value: 'theme_cards', label: 'ThemeCards - 分类卡片' },
                { value: 'product_rail', label: 'ProductRail - 商品横排' },
                { value: 'editorial_feature', label: 'EditorialFeature - 真实婚礼' },
                { value: 'newsletter', label: 'Newsletter - 订阅表单' },
                { value: 'custom', label: 'Custom - 自定义区块' }
              ]" />
          </div>
          <div class="text-[12px] text-ink-faint">
            <p v-if="newBlockType === 'hero'">展示大图 Banner，数据来自 Banner 管理模块</p>
            <p v-else-if="newBlockType === 'theme_cards'">展示分类卡片，引导用户按主题浏览</p>
            <p v-else-if="newBlockType === 'product_rail'">横向展示商品列表</p>
            <p v-else-if="newBlockType === 'editorial_feature'">展示真实婚礼故事卡片</p>
            <p v-else-if="newBlockType === 'newsletter'">邮件订阅表单</p>
            <p v-else>自定义 HTML/富文本内容区块</p>
          </div>
        </div>
        <div class="mt-6 flex justify-end gap-2">
          <button @click="showAddBlockModal = false" class="btn-outline">取消</button>
          <button @click="addBlock" class="btn-gold" :disabled="newBlockType === 'hero' && blocks.some((item) => item.sectionType === 'hero')">添加</button>
        </div>
      </div>
    </div>

    <ConfirmDialog
      :open="showPublishModal"
      title="发布首页到线上"
      message="本次操作会把当前 EN、ES、FR 草稿作为一个不可变版本同时切换上线。"
      confirm-text="确认发布"
      :busy="publishing"
      @cancel="showPublishModal = false"
      @confirm="confirmPublish"
    >
      <div class="mt-4">
        <label class="field-label">版本说明</label>
        <input v-model="publishName" maxlength="128" class="field" placeholder="例如：七月户外婚礼主题更新" />
      </div>
    </ConfirmDialog>

    <ConfirmDialog
      :open="!!rollbackTarget"
      title="回滚并重新发布"
      :message="rollbackTarget ? `将 V${rollbackTarget.releaseNo} 的完整页面快照重新发布为一个新版本，当前草稿也会被该版本替换。` : ''"
      confirm-text="确认回滚"
      danger
      :busy="rollbackBusy"
      @cancel="rollbackTarget = null"
      @confirm="confirmRollback"
    />

    <BannerFormDrawer :open="bannerDrawer" :editing="editingBanner" home-hero @close="onBannerDrawerClose" />
  </div>
</template>
