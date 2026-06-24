<script setup>
import { ref, computed, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import Toggle from '@/components/Toggle.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import { useHomeSectionStore } from '@/stores/homeSection'
import { useToast } from '@/composables/useToast'
import {
  Bars3Icon, EyeIcon, PencilSquareIcon, RocketLaunchIcon,
  ChevronUpIcon, ChevronDownIcon, ArrowUpTrayIcon, ComputerDesktopIcon, DevicePhoneMobileIcon
} from '@heroicons/vue/24/outline'

const store = useHomeSectionStore()
const toast = useToast()

// 本地可编辑副本（从 store 同步）
const blocks = ref([])
const activeId = ref(null)
const preview = ref('desktop')
const dirty = ref(false)

// locale tab（EN/ES/FR）
const localeTab = ref('en')

onMounted(async () => {
  await store.fetch()
  syncFromStore()
})

function syncFromStore() {
  blocks.value = store.sections.map((s) => ({
    id: s.id,
    sectionType: s.sectionType,
    type: typeLabel(s.sectionType),
    label: s.label || typeLabel(s.sectionType),
    enabled: s.enabled,
    sortOrder: s.sortOrder,
    data: parseJson(s.dataJson, {}),
    i18n: parseJson(s.i18nJson, {}),
    version: s.version,
  }))
  if (blocks.value.length > 0 && !activeId.value) {
    activeId.value = blocks.value[0].id
  }
  dirty.value = false
}

function parseJson(str, fallback) {
  if (!str) return fallback
  try { return typeof str === 'string' ? JSON.parse(str) : str } catch { return fallback }
}

function typeLabel(t) {
  const map = { hero: 'Hero', theme_cards: 'ThemeCards', product_rail: 'ProductRail',
    editorial_feature: 'EditorialFeature', newsletter: 'Newsletter', custom: 'Custom' }
  return map[t] || t
}

const active = () => blocks.value.find((b) => b.id === activeId.value)

function move(i, dir) {
  const j = i + dir
  if (j < 0 || j >= blocks.value.length) return
  const arr = blocks.value
  ;[arr[i], arr[j]] = [arr[j], arr[i]]
  // 重新计算 sortOrder
  arr.forEach((b, idx) => { b.sortOrder = idx })
  dirty.value = true
}

function touch() { dirty.value = true }

function currentLabel(b) {
  if (!b) return ''
  return (b.i18n && b.i18n[localeTab.value] && b.i18n[localeTab.value].label) || b.label
}

async function saveAll() {
  try {
    // 1. 批量排序
    await store.sort(blocks.value.map((b) => ({ id: b.id, sortOrder: b.sortOrder })))
    // 2. 逐个更新（如有 dirty 字段）
    for (const b of blocks.value) {
      await store.update(b.id, {
        sectionType: b.sectionType,
        enabled: b.enabled,
        sortOrder: b.sortOrder,
        dataJson: b.data,
        i18nJson: b.i18n,
        label: b.label,
        version: b.version,
      })
    }
    toast.success('保存成功')
    syncFromStore()
  } catch (e) {
    toast.error(e.message ?? '保存失败')
  }
}

async function onToggle(b, val) {
  b.enabled = val
  try {
    await store.toggle(b.id, val)
  } catch (e) {
    b.enabled = !val
    toast.error(e.message ?? '切换失败')
  }
}
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Site Builder" title="首页装修" subtitle="可视化编排首页区块，实时预览，保存后即时生效">
      <template #actions>
        <span v-if="dirty" class="badge bg-warn/14 text-warn"><span class="h-1.5 w-1.5 rounded-full bg-current"></span>未发布改动</span>
        <button class="btn-outline"><EyeIcon class="h-4 w-4" />前台预览</button>
        <button class="btn-gold" @click="saveAll" :disabled="!dirty"><RocketLaunchIcon class="h-4 w-4" />保存</button>
      </template>
    </PageHeader>

    <div v-if="store.loading" class="panel p-8 text-center text-ink-faint">加载中...</div>
    <div v-else-if="store.error" class="panel p-8 text-center text-danger">{{ store.error }}</div>
    <div v-else class="grid grid-cols-1 gap-6 lg:grid-cols-[280px_1fr_300px]">
      <!-- 区块列表 -->
      <div class="panel p-4">
        <p class="eyebrow mb-3">页面区块</p>
        <div class="space-y-2">
          <div v-for="(b, i) in blocks" :key="b.id"
            @click="activeId = b.id"
            class="group flex cursor-pointer items-center gap-2 rounded-luxe border px-3 py-2.5 text-[13px] transition-colors"
            :class="activeId === b.id ? 'border-gold bg-gold/8' : 'border-line hover:bg-canvas-warm'">
            <Bars3Icon class="h-4 w-4 cursor-grab text-ink-faint" />
            <div class="min-w-0 flex-1">
              <p class="truncate font-medium" :class="b.enabled ? 'text-ink' : 'text-ink-faint line-through'">{{ currentLabel(b) }}</p>
              <p class="text-[11px] text-ink-faint">{{ b.type }}</p>
            </div>
            <div class="flex flex-col">
              <button @click.stop="move(i, -1)" class="text-ink-faint hover:text-ink"><ChevronUpIcon class="h-3.5 w-3.5" /></button>
              <button @click.stop="move(i, 1)" class="text-ink-faint hover:text-ink"><ChevronDownIcon class="h-3.5 w-3.5" /></button>
            </div>
            <Toggle :model-value="b.enabled" @update:model-value="onToggle(b, $event)" @click.stop />
          </div>
        </div>
      </div>

      <!-- 实时预览 -->
      <div class="panel overflow-hidden">
        <div class="flex items-center justify-between border-b border-line px-4 py-2.5">
          <p class="text-[12px] text-ink-faint">实时预览 · dreamy.com</p>
          <div class="flex gap-1">
            <button @click="preview = 'desktop'" class="rounded-luxe p-1.5" :class="preview === 'desktop' ? 'bg-ink text-canvas' : 'text-ink-faint hover:bg-canvas-warm'"><ComputerDesktopIcon class="h-4 w-4" /></button>
            <button @click="preview = 'mobile'" class="rounded-luxe p-1.5" :class="preview === 'mobile' ? 'bg-ink text-canvas' : 'text-ink-faint hover:bg-canvas-warm'"><DevicePhoneMobileIcon class="h-4 w-4" /></button>
          </div>
        </div>
        <div class="max-h-[640px] overflow-y-auto bg-canvas-warm/40 p-4">
          <div class="mx-auto bg-canvas transition-all" :class="preview === 'mobile' ? 'max-w-[380px]' : 'max-w-full'">
            <template v-for="b in blocks.filter(x => x.enabled)" :key="b.id">
              <div v-if="b.sectionType === 'hero'" class="grid min-h-[260px] sm:grid-cols-2">
                <div class="flex items-center bg-canvas p-6">
                  <div>
                    <p class="text-[10px] uppercase tracking-luxe text-gold-deep">{{ b.data.eyebrow || 'Hero' }}</p>
                    <h2 class="mt-2 whitespace-pre-line font-display text-2xl font-medium leading-tight text-ink">{{ currentLabel(b) }}</h2>
                  </div>
                </div>
                <div class="bg-canvas-warm/40 flex items-center justify-center text-ink-faint text-[12px]">Hero 图片（派生自 Banner）</div>
              </div>
              <div v-else-if="b.sectionType === 'newsletter'" class="bg-sage/12 p-6 text-center">
                <h3 class="font-display text-lg text-ink">{{ currentLabel(b) }}</h3>
              </div>
              <div v-else class="p-6 text-center">
                <h3 class="font-display text-xl text-ink">{{ currentLabel(b) }}</h3>
                <p class="mt-2 text-[12px] text-ink-faint">{{ b.type }} 区块</p>
              </div>
            </template>
          </div>
        </div>
      </div>

      <!-- 区块属性编辑 -->
      <div class="panel p-4">
        <p class="eyebrow mb-3 flex items-center gap-1.5"><PencilSquareIcon class="h-3.5 w-3.5" />区块属性</p>
        <div v-if="active()" :key="activeId" class="space-y-4">
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
              @input="((active().i18n[localeTab] = active().i18n[localeTab] || {}), (active().i18n[localeTab].label = $event.target.value), touch())"
              class="field text-[12px]" />
          </div>

          <template v-if="active().sectionType === 'product_rail'">
            <div>
              <label class="field-label">商品来源</label>
              <SelectMenu :model-value="active().data.source"
                :options="[{ value: 'new_arrival', label: '新品 New Arrivals' }, { value: 'recommend', label: '人工推荐' }]"
                @change="(v) => { active().data.source = v; touch() }" />
            </div>
            <div>
              <label class="field-label">展示数量（1-12）</label>
              <input v-model="active().data.limit" @input="touch" type="number" min="1" max="12" class="field text-[12px]" />
            </div>
            <div v-if="active().data.source === 'recommend'">
              <label class="field-label">商品 ID 列表（逗号分隔）</label>
              <input :value="(active().data.product_ids || []).join(',')"
                @input="active().data.product_ids = $event.target.value.split(',').map(s=>parseInt(s.trim())).filter(n=>!isNaN(n)); touch()"
                class="field text-[12px]" />
            </div>
          </template>

          <template v-else-if="active().sectionType === 'theme_cards'">
            <div>
              <label class="field-label">展示数量（1-8）</label>
              <input v-model="active().data.count" @input="touch" type="number" min="1" max="8" class="field text-[12px]" />
            </div>
          </template>

          <template v-else-if="active().sectionType === 'editorial_feature'">
            <div>
              <label class="field-label">展示数量（1-6）</label>
              <input v-model="active().data.limit" @input="touch" type="number" min="1" max="6" class="field text-[12px]" />
            </div>
          </template>

          <template v-else-if="active().sectionType === 'hero'">
            <p class="text-[12px] text-ink-faint">Hero 区块数据派生自 Banner position=HERO（KD-2），无需配置 data_json。</p>
          </template>

          <template v-else-if="active().sectionType === 'newsletter'">
            <p class="text-[12px] text-ink-faint">Newsletter 区块仅存 i18n 文案（title/subtitle/cta_text），无 data_json。</p>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>
