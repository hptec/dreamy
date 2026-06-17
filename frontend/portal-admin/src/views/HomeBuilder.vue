<script setup>
import { ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import Toggle from '@/components/Toggle.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import { homeBlocks, palette, products } from '@/data/mock'
import {
  Bars3Icon, EyeIcon, PencilSquareIcon, RocketLaunchIcon,
  ChevronUpIcon, ChevronDownIcon, ArrowUpTrayIcon, ComputerDesktopIcon, DevicePhoneMobileIcon
} from '@heroicons/vue/24/outline'

const blocks = ref(homeBlocks.map((b) => ({ ...b, data: { ...b.data } })))
const activeId = ref('hero')
const preview = ref('desktop')
const dirty = ref(false)

const active = () => blocks.value.find((b) => b.id === activeId.value)
function move(i, dir) {
  const j = i + dir
  if (j < 0 || j >= blocks.value.length) return
  const arr = blocks.value
  ;[arr[i], arr[j]] = [arr[j], arr[i]]
  dirty.value = true
}
function touch() { dirty.value = true }
const newArrivals = products.filter((p) => p.isNew).slice(0, 4)
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Site Builder" title="首页装修" subtitle="可视化编排首页区块，实时预览，保存后在发布中心生成静态首页">
      <template #actions>
        <span v-if="dirty" class="badge bg-warn/14 text-warn"><span class="h-1.5 w-1.5 rounded-full bg-current"></span>未发布改动</span>
        <button class="btn-outline"><EyeIcon class="h-4 w-4" />前台预览</button>
        <button class="btn-gold"><RocketLaunchIcon class="h-4 w-4" />保存并生成首页</button>
      </template>
    </PageHeader>

    <div class="grid grid-cols-1 gap-6 lg:grid-cols-[280px_1fr_300px]">
      <!-- 区块列表（可拖拽排序） -->
      <div class="panel p-4">
        <p class="eyebrow mb-3">页面区块</p>
        <div class="space-y-2">
          <div v-for="(b, i) in blocks" :key="b.id"
            @click="activeId = b.id"
            class="group flex cursor-pointer items-center gap-2 rounded-luxe border px-3 py-2.5 text-[13px] transition-colors"
            :class="activeId === b.id ? 'border-gold bg-gold/8' : 'border-line hover:bg-canvas-warm'">
            <Bars3Icon class="h-4 w-4 cursor-grab text-ink-faint" />
            <div class="min-w-0 flex-1">
              <p class="truncate font-medium" :class="b.enabled ? 'text-ink' : 'text-ink-faint line-through'">{{ b.label }}</p>
              <p class="text-[11px] text-ink-faint">{{ b.type }}</p>
            </div>
            <div class="flex flex-col">
              <button @click.stop="move(i, -1)" class="text-ink-faint hover:text-ink"><ChevronUpIcon class="h-3.5 w-3.5" /></button>
              <button @click.stop="move(i, 1)" class="text-ink-faint hover:text-ink"><ChevronDownIcon class="h-3.5 w-3.5" /></button>
            </div>
            <Toggle :model-value="b.enabled" @update:model-value="b.enabled = $event; touch()" @click.stop />
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
              <!-- Hero -->
              <div v-if="b.type === 'Hero'" class="grid min-h-[260px] sm:grid-cols-2">
                <div class="flex items-center bg-canvas p-6">
                  <div>
                    <p class="text-[10px] uppercase tracking-luxe text-gold-deep">{{ b.data.eyebrow }}</p>
                    <h2 class="mt-2 whitespace-pre-line font-display text-2xl font-medium leading-tight text-ink">{{ b.data.title }}</h2>
                    <div class="mt-4 flex gap-2"><span class="rounded bg-ink px-3 py-1.5 text-[11px] text-canvas">{{ b.data.cta1 }}</span><span class="rounded border border-ink px-3 py-1.5 text-[11px]">{{ b.data.cta2 }}</span></div>
                  </div>
                </div>
                <img :src="b.data.image" class="h-full min-h-[200px] w-full object-cover object-top" />
              </div>
              <!-- 公告 -->
              <div v-else-if="b.type === 'Announcement'" class="bg-ink px-4 py-2 text-center text-[11px] text-canvas">{{ b.data.lines[0] }}</div>
              <!-- Shop by Color -->
              <div v-else-if="b.type === 'ShopByColor'" class="p-6 text-center">
                <h3 class="font-display text-xl text-ink">{{ b.data.title }}</h3>
                <div class="mt-4 flex justify-center gap-3">
                  <div v-for="c in palette.slice(0, b.data.count)" :key="c.name" class="text-center">
                    <span class="block h-8 w-8 rounded-full border border-line" :style="{ background: c.hex }"></span>
                  </div>
                </div>
              </div>
              <!-- 主题卡 -->
              <div v-else-if="b.type === 'ThemeCards'" class="grid grid-cols-4 gap-2 p-6">
                <div v-for="c in b.data.cards" :key="c" class="aspect-[3/4] rounded-luxe bg-sage/20 p-2 text-[10px] text-ink-soft">{{ c }}</div>
              </div>
              <!-- 推荐位 -->
              <div v-else-if="b.type === 'ProductRail'" class="p-6">
                <h3 class="mb-3 font-display text-xl text-ink">{{ b.data.title }}</h3>
                <div class="grid grid-cols-4 gap-3">
                  <div v-for="p in newArrivals" :key="p.id"><img :src="p.img" class="aspect-[3/4] w-full rounded-luxe object-cover" /><p class="mt-1 truncate text-[10px]">{{ p.name }}</p></div>
                </div>
              </div>
              <!-- 编辑特写 -->
              <div v-else-if="b.type === 'EditorialFeature'" class="bg-canvas-warm/60 p-6 text-center"><h3 class="font-display text-xl text-ink">{{ b.data.title }}</h3></div>
              <!-- Newsletter -->
              <div v-else-if="b.type === 'Newsletter'" class="bg-sage/12 p-6 text-center"><h3 class="font-display text-lg text-ink">{{ b.data.title }}</h3></div>
            </template>
          </div>
        </div>
      </div>

      <!-- 区块属性编辑 -->
      <div class="panel p-4">
        <p class="eyebrow mb-3 flex items-center gap-1.5"><PencilSquareIcon class="h-3.5 w-3.5" />区块属性</p>
        <div v-if="active()" :key="activeId" class="space-y-4">
          <p class="text-[13px] font-medium text-ink">{{ active().label }}</p>
          <template v-if="active().type === 'Hero'">
            <div><label class="field-label">Eyebrow</label><input v-model="active().data.eyebrow" @input="touch" class="field text-[12px]" /></div>
            <div><label class="field-label">主标题</label><textarea v-model="active().data.title" @input="touch" rows="2" class="field resize-none text-[12px]"></textarea></div>
            <div><label class="field-label">副文案</label><textarea v-model="active().data.subtitle" @input="touch" rows="3" class="field resize-none text-[12px]"></textarea></div>
            <div class="grid grid-cols-2 gap-2">
              <div><label class="field-label">主按钮</label><input v-model="active().data.cta1" @input="touch" class="field text-[12px]" /></div>
              <div><label class="field-label">次按钮</label><input v-model="active().data.cta2" @input="touch" class="field text-[12px]" /></div>
            </div>
            <div><label class="field-label">背景图</label>
              <button class="flex w-full items-center justify-center gap-2 rounded-luxe border-2 border-dashed border-line py-3 text-[12px] text-ink-faint hover:border-gold"><ArrowUpTrayIcon class="h-4 w-4" />更换图片</button>
            </div>
          </template>
          <template v-else-if="active().type === 'Announcement'">
            <div v-for="(l, i) in active().data.lines" :key="i"><label class="field-label">公告 {{ i+1 }}</label><input v-model="active().data.lines[i]" @input="touch" class="field text-[12px]" /></div>
          </template>
          <template v-else-if="active().type === 'ProductRail'">
            <div><label class="field-label">标题</label><input v-model="active().data.title" @input="touch" class="field text-[12px]" /></div>
            <div><label class="field-label">商品来源</label><SelectMenu v-model="active().data.source" :options="[{ value: 'isNew', label: '新品 New Arrivals' }, { value: 'isBest', label: '热销 Best Sellers' }, { value: 'recommend', label: '人工推荐' }]" @change="touch" /></div>
            <div><label class="field-label">展示数量</label><input v-model="active().data.limit" @input="touch" type="number" class="field text-[12px]" /></div>
          </template>
          <template v-else>
            <div><label class="field-label">标题</label><input v-model="active().data.title" @input="touch" class="field text-[12px]" /></div>
            <p class="text-[12px] text-ink-faint">该区块内容从对应数据源自动获取，可在「显示开关」与排序中控制。</p>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>
