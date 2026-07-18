<script setup>
import { computed, ref, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import Toggle from '@/components/Toggle.vue'
import LocaleTabs from '@/components/LocaleTabs.vue'
import { useNavigationStore, useFooterStore, useAnnouncementStore } from '@/stores/siteBuilder'
import { useToast } from '@/composables/useToast'
import {
  Bars3Icon, PlusIcon, TrashIcon, RocketLaunchIcon, EyeIcon, ChevronRightIcon
} from '@heroicons/vue/24/outline'

const navStore = useNavigationStore()
const footerStore = useFooterStore()
const announcementStore = useAnnouncementStore()
const toast = useToast()

const tab = ref('main')
const main = ref([])
const footer = ref([])
const announcements = ref([])
const dirty = ref(false)
const localeTab = ref('en')

onMounted(async () => {
  await Promise.all([
    navStore.fetch(),
    footerStore.fetch(),
    announcementStore.fetch(),
  ])
  syncFromStores()
})

function syncFromStores() {
  main.value = navStore.items.map((i) => ({
    id: i.id,
    label: i.label,
    href: i.url || '',
    linkType: i.linkType || 'custom',
    taxonomyId: i.taxonomyId,
    columns: parseMegaMenuColumns(i.megaMenuJson),
    enabled: i.enabled,
    sortOrder: i.sortOrder,
    version: i.version,
    i18n: parseJson(i.i18nJson, {}),
    megaMenuJson: parseJson(i.megaMenuJson, null),
  }))
  footer.value = footerStore.columns.map((c) => ({
    id: c.id,
    title: c.title,
    links: (c.links || []).map((l) => ({ id: l.id, label: l.label, url: l.url, target: l.target, i18n: parseJson(l.i18nJson, {}) })),
    sortOrder: c.sortOrder,
    enabled: c.enabled,
    i18n: parseJson(c.i18nJson, {}),
  }))
  announcements.value = announcementStore.announcements.map((a) => ({
    id: a.id,
    content: a.content || '',
    contentI18n: parseJson(a.contentI18nJson, { en: { content: a.content || '' } }),
    enabled: a.enabled,
    priority: a.priority,
    startAt: a.startAt,
    endAt: a.endAt,
    version: a.version,
  }))
  dirty.value = false
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
    label: 'New Item',
    href: '',
    linkType: 'custom',
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
    content: '',
    contentI18n: { en: { content: '' } },
    enabled: true,
    priority: 0,
    startAt: null,
    endAt: null,
  })
  dirty.value = true
}

function removeAnnouncement(i) {
  announcements.value.splice(i, 1)
  dirty.value = true
}

async function saveAll() {
  try {
    // 1. 保存导航（整体替换）
    const navUpserts = main.value.map((b, idx) => ({
      id: b.id,
      label: b.label,
      url: b.href,
      linkType: b.linkType,
      taxonomyId: b.taxonomyId,
      target: 'self',
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
        target: l.target || 'self',
        i18nJson: l.i18n,
        sortOrder: lidx,
      })),
    }))
    await footerStore.save(footerUpserts)

    // 3. 保存公告（逐条 create/update）
    for (const a of announcements.value) {
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
    toast.error(e.message ?? '保存失败')
  }
}
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Site Builder" title="导航与页脚" subtitle="配置全站主导航、Mega Menu、页脚栏目与顶部公告条">
      <template #actions>
        <span v-if="dirty" class="badge bg-warn/14 text-warn"><span class="h-1.5 w-1.5 rounded-full bg-current"></span>未发布改动</span>
        <button class="btn-outline"><EyeIcon class="h-4 w-4" />前台预览</button>
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
      <div v-for="(item, i) in main" :key="i" class="panel p-4">
        <div class="flex items-center gap-3">
          <Bars3Icon class="h-4 w-4 cursor-grab text-ink-faint" />
          <input
            :value="i18nValue(item, 'label')"
            @input="setI18nValue(item, 'label', $event)"
            :placeholder="localeTab === 'en' ? '' : (item.label || 'EN 基准文案')"
            class="field w-48 font-medium"
          />
          <div class="flex items-center text-[12px] text-ink-faint">
            <span class="px-2">链接类型</span>
            <SelectMenu v-model="item.linkType" class="w-32" :options="[{ value: 'custom', label: '自定义 URL' }, { value: 'taxonomy', label: '分类引用' }]" @change="touch" />
          </div>
          <div v-if="item.linkType === 'custom'" class="flex items-center text-[12px] text-ink-faint">
            <span class="px-2">URL</span>
            <input v-model="item.href" @input="touch" class="field w-56 text-[12px]" />
          </div>
          <div v-else class="flex items-center text-[12px] text-ink-faint">
            <span class="px-2">taxonomy_id</span>
            <input v-model.number="item.taxonomyId" @input="touch" type="number" class="field w-24 text-[12px]" />
          </div>
          <div class="ml-auto flex items-center gap-2 text-[12px] text-ink-soft">
            <Toggle :model-value="item.enabled" @update:model-value="item.enabled = $event; touch()" />
            <button class="btn-danger-ghost" @click="removeItem(i)"><TrashIcon class="h-4 w-4" /></button>
          </div>
        </div>
      </div>
      <button class="btn-outline w-full" @click="addItem"><PlusIcon class="h-4 w-4" />添加主导航项</button>
    </div>

    <!-- 页脚 -->
    <div v-show="tab === 'footer'" class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <div v-for="(col, i) in footer" :key="i" class="panel p-4">
        <input
          :value="i18nValue(col, 'title')"
          @input="setI18nValue(col, 'title', $event)"
          :placeholder="localeTab === 'en' ? '' : (col.title || 'EN 基准文案')"
          class="field mb-3 font-medium"
        />
        <div class="space-y-1.5">
          <div v-for="(link, lidx) in col.links" :key="lidx" class="flex items-center gap-1.5 text-[12px]">
            <Bars3Icon class="h-3 w-3 text-ink-faint" />
            <input
              :value="i18nValue(link, 'label')"
              @input="setI18nValue(link, 'label', $event)"
              :placeholder="localeTab === 'en' ? '链接文本' : (link.label || 'EN 基准文案')"
              class="flex-1 border-b border-line bg-transparent py-1 text-ink-soft outline-none focus:border-gold"
            />
            <input v-model="link.url" @input="touch" class="flex-1 border-b border-line bg-transparent py-1 text-ink-faint outline-none focus:border-gold" placeholder="URL" />
          </div>
          <button class="btn-ghost text-[11px]" @click="col.links.push({ label: '', url: '', target: 'self', i18n: {} }); touch()"><PlusIcon class="h-3 w-3" />添加</button>
        </div>
      </div>
    </div>

    <!-- 公告条 -->
    <div v-show="tab === 'announce'" class="panel max-w-2xl p-6">
      <p class="field-label">顶部公告（按 priority 轮播，时间窗内生效）</p>
      <div class="space-y-2">
        <div v-for="(a, i) in announcements" :key="i" class="flex items-center gap-2">
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
      </div>
      <button class="btn-ghost mt-3" @click="addAnnouncement"><PlusIcon class="h-4 w-4" />添加公告</button>
      <div class="mt-5 rounded-luxe bg-ink px-4 py-2 text-center text-[12px] text-canvas">
        {{ announcements[0]?.contentI18n?.[localeTab]?.content || announcements[0]?.contentI18n?.en?.content || '（无公告）' }}
      </div>
    </div>
  </div>
</template>
