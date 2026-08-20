<script setup>
import { computed, ref, reactive, onMounted } from 'vue'
import draggable from 'vuedraggable'
import PageHeader from '@/components/PageHeader.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import Toggle from '@/components/Toggle.vue'
import LocaleTabs from '@/components/LocaleTabs.vue'
import { useNavigationStore, useFooterStore, useAnnouncementStore } from '@/stores/siteBuilder'
import { fetchLinkOptions, LinkType } from '@/api/siteBuilder'
import { useToast } from '@/composables/useToast'
import {
  Bars3Icon, PlusIcon, TrashIcon, RocketLaunchIcon
} from '@heroicons/vue/24/outline'

const navStore = useNavigationStore()
const footerStore = useFooterStore()
const announcementStore = useAnnouncementStore()
const toast = useToast()

const tab = ref('main')
const main = ref([])
const footer = ref([])
const announcements = ref([])
const removedAnnouncementIds = ref([])
const dirty = ref(false)
const localeTab = ref('en')

const LINK_TYPE_OPTIONS = [
  { value: LinkType.CUSTOM, label: '自定义 URL' },
  { value: LinkType.PAGE, label: '系统页' },
  { value: LinkType.CATEGORY, label: '商品分类' },
  { value: LinkType.COLLECTION, label: '合集' },
  { value: LinkType.PRODUCT, label: '商品' },
  { value: LinkType.BLOG_POST, label: '博客文章' },
  { value: LinkType.REAL_WEDDING, label: '真实婚礼' },
  { value: LinkType.LOOKBOOK, label: '画册' },
  { value: LinkType.GUIDE, label: '指南' },
]

// 系统页 page_key（与后端 NavPageKey 对齐）
const PAGE_KEY_OPTIONS = [
  { value: 'home', label: '首页（/）' },
  { value: 'products', label: '全部商品（/products）' },
  { value: 'wedding-dresses', label: '婚纱（/wedding-dresses）' },
  { value: 'special-occasion', label: '特殊场合（/special-occasion）' },
  { value: 'accessories', label: '配饰（/accessories）' },
  { value: 'outdoor-weddings', label: '户外婚礼（/outdoor-weddings）' },
  { value: 'real-weddings', label: '真实婚礼（/real-weddings）' },
  { value: 'inspiration', label: '灵感（/inspiration）' },
  { value: 'blog', label: '博客（/blog）' },
  { value: 'wedding-guides', label: '备婚指南（/wedding-guides）' },
  { value: 'showroom', label: 'Showroom（/showroom）' },
  { value: 'about', label: '关于我们（/about）' },
  { value: 'contact', label: '联系我们（/contact）' },
  { value: 'faq', label: 'FAQ（/faq）' },
  { value: 'search', label: '搜索（/search）' },
  { value: 'cart', label: '购物袋（/cart）' },
  { value: 'account-login', label: '登录（/account/login）' },
  { value: 'account-orders', label: '我的订单（/account/orders）' },
  { value: 'account-wishlist', label: '心愿单（/account/wishlist）' },
]

// 引用型选项按导航项 uid 隔离：两个导航项可能同为「商品」类型，
// 共用一份结果会导致在其中一项里搜索时另一项的下拉列表也跟着变
const refOptionsByUid = reactive({})
// 无关键词的基础列表按 linkType 共享（缓存 Promise 以合并并发的首次拉取）
const baseOptionsCache = new Map()

function isRefType(linkType) {
  return linkType >= LinkType.CATEGORY && linkType <= LinkType.GUIDE
}

// 只有外域链接才允许新标签打开：站内链接开新标签会绕过前端路由退化成整页刷新，
// 还会堆出重复标签页，所以从界面上就不给这个选项
function canOpenInNewTab(url) {
  return /^https?:\/\//i.test(String(url ?? '').trim())
}

function syncTarget(item, url) {
  if (!canOpenInNewTab(url)) item.target = 'self'
  touch()
}

function fetchBaseOptions(linkType) {
  if (!baseOptionsCache.has(linkType)) {
    baseOptionsCache.set(
      linkType,
      fetchLinkOptions(linkType).then((res) => res.options || []).catch((e) => {
        baseOptionsCache.delete(linkType)
        throw e
      })
    )
  }
  return baseOptionsCache.get(linkType)
}

async function loadLinkOptions(item, keyword) {
  const kw = (keyword || '').trim()
  const linkType = item.linkType
  try {
    const options = kw
      ? (await fetchLinkOptions(linkType, kw)).options || []
      : await fetchBaseOptions(linkType)
    // 响应回来时该项可能已切换类型，丢弃过期结果
    if (item.linkType !== linkType) return
    refOptionsByUid[item.uid] = options
  } catch (e) {
    toast.error(e.message ?? '加载选项失败')
  }
}

function refOptions(item) {
  const options = (refOptionsByUid[item.uid] || []).map((o) => ({
    value: o.id,
    label: o.sub ? `${o.label} · ${o.sub}` : o.label,
  }))
  // 当前选中项不在前 50 条时占位显示，避免 SelectMenu 空白
  if (item.refId != null && !options.some((o) => o.value === item.refId)) {
    options.unshift({ value: item.refId, label: `#${item.refId}` })
  }
  return options
}

const searchTimers = new Map()
function onRefKeywordInput(item, event) {
  item._refKeyword = event.target.value
  clearTimeout(searchTimers.get(item.uid))
  searchTimers.set(item.uid, setTimeout(() => loadLinkOptions(item, item._refKeyword), 300))
}

function onLinkTypeChange(item) {
  // 切换类型时清理不适用字段（后端 applyUpsert 也会兜底清理）
  if (item.linkType === LinkType.CUSTOM) {
    item.refId = null
    item.pageKey = null
  } else if (item.linkType === LinkType.PAGE) {
    item.href = ''
    item.refId = null
    item.pageKey = item.pageKey || 'home'
  } else if (isRefType(item.linkType)) {
    item.href = ''
    item.pageKey = null
    item._refKeyword = ''
    refOptionsByUid[item.uid] = []
    loadLinkOptions(item)
  }
  syncTarget(item, item.href)
}

onMounted(async () => {
  await Promise.all([
    navStore.fetch(),
    footerStore.fetch(),
    announcementStore.fetch(),
  ])
  syncFromStores()
})

// 每次从 store 重建列表都会重新发 uid，必须按新 uid 重新填充选项，
// 否则下拉查不到 label，只能退化成 #id 占位。基础列表按 linkType 共享，不会重复请求。
function prefetchRefOptions() {
  for (const key of Object.keys(refOptionsByUid)) delete refOptionsByUid[key]
  for (const item of main.value) {
    if (isRefType(item.linkType)) loadLinkOptions(item)
  }
}

// 拖拽排序需要稳定 key，新建项没有后端 id，用自增 uid 兜底
let uidSeed = 0
function nextUid() { return `u${++uidSeed}` }

function syncFromStores() {
  main.value = navStore.items.map((i) => ({
    uid: nextUid(),
    id: i.id,
    label: i.label,
    href: i.url || '',
    linkType: i.linkType || LinkType.CUSTOM,
    refId: i.refId ?? null,
    pageKey: i.pageKey ?? null,
    target: i.target || 'self',
    columns: parseMegaMenuColumns(i.megaMenuJson),
    enabled: i.enabled,
    sortOrder: i.sortOrder,
    version: i.version,
    i18n: parseJson(i.i18nJson, {}),
    megaMenuJson: parseJson(i.megaMenuJson, null),
  }))
  footer.value = footerStore.columns.map((c) => ({
    uid: nextUid(),
    id: c.id,
    title: c.title,
    links: (c.links || []).map((l) => ({ uid: nextUid(), id: l.id, label: l.label, url: l.url, target: l.target, i18n: parseJson(l.i18nJson, {}) })),
    sortOrder: c.sortOrder,
    enabled: c.enabled,
    i18n: parseJson(c.i18nJson, {}),
  }))
  announcements.value = announcementStore.announcements.map((a) => {
    const row = {
      uid: nextUid(),
      id: a.id,
      content: a.content || '',
      contentI18n: parseJson(a.contentI18nJson, { en: { content: a.content || '' } }),
      enabled: a.enabled,
      priority: a.priority,
      startAt: a.startAt,
      endAt: a.endAt,
      version: a.version,
    }
    row.saved = announcementFingerprint(row)
    return row
  })
  removedAnnouncementIds.value = []
  dirty.value = false
  prefetchRefOptions()
}

// 公告是逐条 PUT 且带乐观锁 version，只提交真正改过的：
// 否则改一个导航项也会把每条公告的 version 推进一格，和别人的编辑撞出假冲突
function announcementFingerprint(a) {
  return JSON.stringify([a.enabled, a.priority, a.startAt, a.endAt, a.contentI18n])
}

function parseJson(str, fallback) {
  if (!str) return fallback
  try { return typeof str === 'string' ? JSON.parse(str) : str } catch { return fallback }
}

function parseMegaMenuColumns(megaMenu) {
  if (!megaMenu) return 0
  const parsed = parseJson(megaMenu, null)
  return parsed?.columns?.length || 0
}

function touch() { dirty.value = true }

// 多语言编辑：EN 写基准字段（label/title/content），ES/FR 写 i18n[locale][field]
// ES/FR 清空时删除该字段，消费端回退 EN（空串否则会遮蔽兜底）
function ensureI18n(obj, locale) {
  if (!obj.i18n) obj.i18n = {}
  if (!obj.i18n[locale]) obj.i18n[locale] = {}
  return obj.i18n[locale]
}

function i18nValue(obj, field) {
  if (localeTab.value === 'en') return obj[field] ?? ''
  return obj.i18n?.[localeTab.value]?.[field] ?? ''
}

function setI18nValue(obj, field, event) {
  const value = event.target.value
  if (localeTab.value === 'en') {
    obj[field] = value
  } else {
    const node = ensureI18n(obj, localeTab.value)
    if (value.trim() === '') {
      delete node[field]
      if (Object.keys(node).length === 0) delete obj.i18n[localeTab.value]
    } else {
      node[field] = value
    }
  }
  touch()
}

function announcementContent(a) {
  return a.contentI18n?.[localeTab.value]?.content ?? ''
}

function setAnnouncementContent(a, event) {
  const value = event.target.value
  if (!a.contentI18n) a.contentI18n = {}
  if (localeTab.value !== 'en' && value.trim() === '') {
    delete a.contentI18n[localeTab.value]
  } else {
    if (!a.contentI18n[localeTab.value]) a.contentI18n[localeTab.value] = { content: '' }
    a.contentI18n[localeTab.value].content = value
  }
  touch()
}

const localeFilled = computed(() => {
  const has = (v) => !!(v && String(v).trim())
  const check = (locale) => {
    if (tab.value === 'main') return main.value.some((i) => has(i.i18n?.[locale]?.label))
    if (tab.value === 'footer') {
      return footer.value.some((c) => has(c.i18n?.[locale]?.title) || (c.links || []).some((l) => has(l.i18n?.[locale]?.label)))
    }
    return announcements.value.some((a) => has(a.contentI18n?.[locale]?.content))
  }
  return { en: true, es: check('es'), fr: check('fr') }
})

function addItem() {
  main.value.push({
    uid: nextUid(),
    label: 'New Item',
    href: '',
    linkType: LinkType.CUSTOM,
    refId: null,
    pageKey: null,
    target: 'self',
    columns: 0,
    enabled: true,
    sortOrder: main.value.length,
    i18n: {},
    megaMenuJson: null,
  })
  dirty.value = true
}

function removeItem(i) {
  main.value.splice(i, 1)
  dirty.value = true
}

function addAnnouncement() {
  announcements.value.push({
    uid: nextUid(),
    content: '',
    contentI18n: { en: { content: '' } },
    enabled: true,
    priority: 0,
    startAt: null,
    endAt: null,
  })
  dirty.value = true
}

// 公告按 priority DESC 展示，拖拽后回写递减 priority 使列表顺序即生效顺序
function reprioritize() {
  const top = announcements.value.length
  announcements.value.forEach((a, idx) => { a.priority = top - idx })
  touch()
}

function removeAnnouncement(i) {
  const [removed] = announcements.value.splice(i, 1)
  if (removed?.id) removedAnnouncementIds.value.push(removed.id)
  dirty.value = true
}

const LINK_TYPE_LABEL = Object.fromEntries(LINK_TYPE_OPTIONS.map((o) => [o.value, o.label]))

// 首页区块/页脚栏目/导航/公告的乐观锁冲突码（error-strategy site_builder 段 409 系列）
const VERSION_CONFLICT_CODES = [409801, 409803, 409805, 409806]

// 提交前本地校验：后端错误只带错误码不带行号，十几项时无法定位是哪一项；
// 且 saveAll 是导航→页脚→公告串行，中途抛错会让后面 tab 的改动一起丢掉
function collectErrors() {
  const errors = []
  main.value.forEach((item, i) => {
    const name = String(item.label ?? '').trim() || `第 ${i + 1} 项`
    if (!String(item.label ?? '').trim()) {
      errors.push({ tab: 'main', msg: `主导航第 ${i + 1} 项未填写名称` })
    }
    if (item.linkType === LinkType.CUSTOM && !String(item.href ?? '').trim()) {
      errors.push({ tab: 'main', msg: `主导航「${name}」选了自定义 URL，请填写链接地址` })
    } else if (item.linkType === LinkType.PAGE && !item.pageKey) {
      errors.push({ tab: 'main', msg: `主导航「${name}」请选择系统页` })
    } else if (isRefType(item.linkType) && item.refId == null) {
      errors.push({ tab: 'main', msg: `主导航「${name}」请选择${LINK_TYPE_LABEL[item.linkType]}目标` })
    }
  })
  footer.value.forEach((col, ci) => {
    const colName = String(col.title ?? '').trim() || `第 ${ci + 1} 栏`
    ;(col.links || []).forEach((link, li) => {
      const linkName = String(link.label ?? '').trim() || `第 ${li + 1} 条链接`
      const url = String(link.url ?? '').trim()
      if (!url) {
        errors.push({ tab: 'footer', msg: `页脚「${colName}」的${linkName}未填写链接地址` })
      } else if (!/^(https?:\/\/|\/)/.test(url)) {
        errors.push({ tab: 'footer', msg: `页脚「${colName}」的${linkName}地址需以 http://、https:// 或 / 开头` })
      }
    })
  })
  announcements.value.forEach((a, i) => {
    if (a.priority == null || a.priority < 0) {
      errors.push({ tab: 'announce', msg: `公告第 ${i + 1} 条的优先级需为不小于 0 的整数` })
    }
  })
  return errors
}

async function saveAll() {
  const errors = collectErrors()
  if (errors.length > 0) {
    tab.value = errors[0].tab
    toast.error(errors.length > 1 ? `${errors[0].msg}（共 ${errors.length} 处待修正）` : errors[0].msg)
    return
  }
  try {
    // 1. 保存导航（整体替换）
    const navUpserts = main.value.map((b, idx) => ({
      id: b.id,
      label: b.label,
      url: b.href,
      linkType: b.linkType,
      refId: b.refId,
      pageKey: b.pageKey,
      target: canOpenInNewTab(b.href) ? (b.target || 'self') : 'self',
      sortOrder: idx,
      enabled: b.enabled,
      i18nJson: b.i18n,
      megaMenuJson: b.megaMenuJson,
    }))
    await navStore.save(navUpserts)

    // 2. 保存页脚（整体替换）
    const footerUpserts = footer.value.map((c, idx) => ({
      id: c.id,
      title: c.title,
      sortOrder: idx,
      enabled: c.enabled,
      i18nJson: c.i18n,
      links: c.links.map((l, lidx) => ({
        id: l.id,
        label: l.label,
        url: l.url,
        target: canOpenInNewTab(l.url) ? (l.target || 'self') : 'self',
        i18nJson: l.i18n,
        sortOrder: lidx,
      })),
    }))
    await footerStore.save(footerUpserts)

    // 3. 保存公告（逐条 delete/create/update，跳过未改动项）
    for (const id of removedAnnouncementIds.value) {
      await announcementStore.remove(id)
    }
    removedAnnouncementIds.value = []
    for (const a of announcements.value) {
      if (a.id && a.saved === announcementFingerprint(a)) continue
      const upsert = {
        enabled: a.enabled,
        priority: a.priority,
        startAt: a.startAt,
        endAt: a.endAt,
        contentI18nJson: a.contentI18n,
      }
      if (a.id) {
        await announcementStore.update(a.id, { ...upsert, version: a.version })
      } else {
        await announcementStore.create(upsert)
      }
    }

    toast.success('保存成功')
    syncFromStores()
  } catch (e) {
    // 乐观锁冲突：本地 version 已陈旧，不重新拉取的话之后每次重试都会撞同一个错
    if (VERSION_CONFLICT_CODES.includes(e?.code)) {
      await Promise.all([navStore.fetch(), footerStore.fetch(), announcementStore.fetch()])
      syncFromStores()
      toast.error('数据已被其他人更新，已载入最新内容；本次未保存的改动请重新填写后再提交')
      return
    }
    toast.error(e.message ?? '保存失败')
  }
}
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Site Builder" title="导航与页脚" subtitle="配置全站主导航、Mega Menu、页脚栏目与顶部公告条">
      <template #actions>
        <span v-if="dirty" class="badge bg-warn/14 text-warn"><span class="h-1.5 w-1.5 rounded-full bg-current"></span>未发布改动</span>
        <button class="btn-gold" @click="saveAll" :disabled="!dirty"><RocketLaunchIcon class="h-4 w-4" />保存</button>
      </template>
    </PageHeader>

    <div class="mb-4 flex gap-1 border-b border-line">
      <button v-for="t in [['main','主导航 & Mega Menu'],['footer','页脚栏目'],['announce','公告条']]" :key="t[0]"
        @click="tab = t[0]" class="border-b-2 px-4 py-2.5 text-[13px] transition-colors"
        :class="tab === t[0] ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'">{{ t[1] }}</button>
    </div>

    <LocaleTabs v-model="localeTab" :filled="localeFilled" />

    <!-- 主导航 -->
    <div v-show="tab === 'main'" class="space-y-3">
      <draggable v-model="main" item-key="uid" handle=".nav-drag-handle" class="space-y-3" :animation="180" @end="touch">
        <template #item="{ element: item, index: i }">
          <div class="panel p-4">
            <div class="flex items-center gap-3">
              <Bars3Icon class="nav-drag-handle h-4 w-4 shrink-0 cursor-grab text-ink-faint active:cursor-grabbing" />
              <input
                :value="i18nValue(item, 'label')"
                @input="setI18nValue(item, 'label', $event)"
                :placeholder="localeTab === 'en' ? '' : (item.label || 'EN 基准文案')"
                class="field w-48 font-medium"
              />
              <div class="flex items-center text-[12px] text-ink-faint">
                <span class="px-2">链接类型</span>
                <SelectMenu v-model="item.linkType" class="w-32" :options="LINK_TYPE_OPTIONS" @update:model-value="onLinkTypeChange(item)" />
              </div>
              <!-- 自定义 URL -->
              <div v-if="item.linkType === LinkType.CUSTOM" class="flex items-center text-[12px] text-ink-faint">
                <span class="px-2">URL</span>
                <input v-model="item.href" @input="syncTarget(item, item.href)" class="field w-56 text-[12px]" placeholder="/path 或 https://..." />
                <label v-if="canOpenInNewTab(item.href)" class="ml-2 flex cursor-pointer items-center gap-1 whitespace-nowrap">
                  <input
                    type="checkbox"
                    class="accent-gold"
                    :checked="item.target === 'blank'"
                    @change="item.target = $event.target.checked ? 'blank' : 'self'; touch()"
                  />
                  新标签打开
                </label>
              </div>
              <!-- 系统页 -->
              <div v-else-if="item.linkType === LinkType.PAGE" class="flex items-center text-[12px] text-ink-faint">
                <span class="px-2">页面</span>
                <SelectMenu v-model="item.pageKey" class="w-56" :options="PAGE_KEY_OPTIONS" placeholder="选择系统页" @update:model-value="touch" />
              </div>
              <!-- 内部资源引用 -->
              <div v-else-if="isRefType(item.linkType)" class="flex items-center gap-1 text-[12px] text-ink-faint">
                <input
                  :value="item._refKeyword || ''"
                  @input="onRefKeywordInput(item, $event)"
                  class="field w-28 text-[12px]"
                  placeholder="搜索..."
                />
                <SelectMenu
                  :model-value="item.refId"
                  @update:model-value="item.refId = $event; touch()"
                  class="w-56"
                  :options="refOptions(item)"
                  placeholder="选择目标"
                />
              </div>
              <div class="ml-auto flex items-center gap-2 text-[12px] text-ink-soft">
                <Toggle :model-value="item.enabled" @update:model-value="item.enabled = $event; touch()" />
                <button class="btn-danger-ghost" @click="removeItem(i)"><TrashIcon class="h-4 w-4" /></button>
              </div>
            </div>
          </div>
        </template>
      </draggable>
      <button class="btn-outline w-full" @click="addItem"><PlusIcon class="h-4 w-4" />添加主导航项</button>
    </div>

    <!-- 页脚 -->
    <draggable
      v-show="tab === 'footer'"
      v-model="footer"
      item-key="uid"
      handle=".col-drag-handle"
      class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4"
      :animation="180"
      @end="touch"
    >
      <template #item="{ element: col }">
        <div class="panel p-4">
          <div class="mb-3 flex items-center gap-2">
            <Bars3Icon class="col-drag-handle h-4 w-4 shrink-0 cursor-grab text-ink-faint active:cursor-grabbing" />
            <input
              :value="i18nValue(col, 'title')"
              @input="setI18nValue(col, 'title', $event)"
              :placeholder="localeTab === 'en' ? '' : (col.title || 'EN 基准文案')"
              class="field font-medium"
            />
          </div>
          <draggable v-model="col.links" item-key="uid" handle=".link-drag-handle" class="space-y-1.5" :animation="180" @end="touch">
            <template #item="{ element: link }">
              <div class="flex items-center gap-1.5 text-[12px]">
                <Bars3Icon class="link-drag-handle h-3 w-3 shrink-0 cursor-grab text-ink-faint active:cursor-grabbing" />
                <input
                  :value="i18nValue(link, 'label')"
                  @input="setI18nValue(link, 'label', $event)"
                  :placeholder="localeTab === 'en' ? '链接文本' : (link.label || 'EN 基准文案')"
                  class="min-w-0 flex-1 border-b border-line bg-transparent py-1 text-ink-soft outline-none focus:border-gold"
                />
                <input v-model="link.url" @input="syncTarget(link, link.url)" class="min-w-0 flex-1 border-b border-line bg-transparent py-1 text-ink-faint outline-none focus:border-gold" placeholder="URL" />
                <input
                  v-if="canOpenInNewTab(link.url)"
                  type="checkbox"
                  class="shrink-0 accent-gold"
                  title="新标签打开"
                  :checked="link.target === 'blank'"
                  @change="link.target = $event.target.checked ? 'blank' : 'self'; touch()"
                />
              </div>
            </template>
          </draggable>
          <button class="btn-ghost text-[11px]" @click="col.links.push({ uid: nextUid(), label: '', url: '', target: 'self', i18n: {} }); touch()"><PlusIcon class="h-3 w-3" />添加</button>
        </div>
      </template>
    </draggable>

    <!-- 公告条 -->
    <div v-show="tab === 'announce'" class="panel max-w-2xl p-6">
      <p class="field-label">顶部公告（按 priority 轮播，时间窗内生效）</p>
      <draggable v-model="announcements" item-key="uid" handle=".ann-drag-handle" class="space-y-2" :animation="180" @end="() => reprioritize()">
        <template #item="{ element: a, index: i }">
          <div class="flex items-center gap-2">
            <Bars3Icon class="ann-drag-handle h-4 w-4 shrink-0 cursor-grab text-ink-faint active:cursor-grabbing" />
            <span class="text-[12px] text-ink-faint">{{ i + 1 }}</span>
            <input
              :value="announcementContent(a)"
              @input="setAnnouncementContent(a, $event)"
              :placeholder="localeTab === 'en' ? '公告内容（EN 基准）' : (a.contentI18n?.en?.content || 'EN 基准文案')"
              class="field text-[13px] flex-1"
            />
            <input v-model.number="a.priority" @input="touch" type="number" class="field w-16 text-[12px]" placeholder="优先级" />
            <Toggle :model-value="a.enabled" @update:model-value="a.enabled = $event; touch()" />
            <button class="btn-danger-ghost" @click="removeAnnouncement(i)"><TrashIcon class="h-4 w-4" /></button>
          </div>
        </template>
      </draggable>
      <button class="btn-ghost mt-3" @click="addAnnouncement"><PlusIcon class="h-4 w-4" />添加公告</button>
      <div class="mt-5 rounded-luxe bg-ink px-4 py-2 text-center text-[12px] text-canvas">
        {{ announcements[0]?.contentI18n?.[localeTab]?.content || announcements[0]?.contentI18n?.en?.content || '（无公告）' }}
      </div>
    </div>
  </div>
</template>
