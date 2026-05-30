<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import Toggle from '@/components/Toggle.vue'
import { productColors, productSizes, products } from '@/data/mock'
import {
  CheckIcon, PhotoIcon, PlusIcon, XMarkIcon, ArrowUpTrayIcon,
  RocketLaunchIcon, ArrowLeftIcon, Bars3BottomLeftIcon
} from '@heroicons/vue/24/outline'

const route = useRoute()
const router = useRouter()
const editing = computed(() => route.name === 'product-edit')
const existing = computed(() => products.find((p) => p.id === route.params.id))

const steps = [
  { key: 'basic', label: '基础信息' },
  { key: 'media', label: '媒体素材' },
  { key: 'sku', label: 'SKU 矩阵' },
  { key: 'size', label: '尺码表' },
  { key: 'price', label: '定价库存' },
  { key: 'seo', label: 'SEO' }
]
const active = ref('basic')
const activeIdx = computed(() => steps.findIndex((s) => s.key === active.value))

const form = ref({
  name: existing.value?.name || '',
  category: existing.value?.category || 'Wedding Dresses',
  sub: existing.value?.sub || '',
  subtitle: '',
  description: '',
  price: existing.value?.price || 0,
  compareAt: existing.value?.compareAt || 0,
  installment: true,
  seoTitle: '',
  seoDesc: '',
  slug: existing.value?.slug || '',
  status: existing.value?.status || 'draft'
})

// SKU 矩阵：选中的颜色 × 尺码
const skuColors = ref(productColors.slice(0, 3).map((c) => c.name))
const skuSizes = ref(['US 2', 'US 4', 'US 6', 'US 8'])
const gallery = ref([existing.value?.img].filter(Boolean))

const themes = ['Beach', 'Garden', 'Boho', 'Forest', 'Vineyard']
const selectedThemes = ref(['Garden', 'Beach'])

function goNext() { if (activeIdx.value < steps.length - 1) active.value = steps[activeIdx.value + 1].key }
function goPrev() { if (activeIdx.value > 0) active.value = steps[activeIdx.value - 1].key }
function saveAndPublish() { router.push('/publish') }
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader
      :eyebrow="editing ? 'Edit Product' : 'New Product'"
      :title="editing ? form.name || '编辑商品' : '新增商品'"
      subtitle="逐步填写商品信息，保存后可在发布中心生成对应静态页">
      <template #actions>
        <button class="btn-ghost" @click="router.push('/products')"><ArrowLeftIcon class="h-4 w-4" />返回列表</button>
        <button class="btn-outline">保存草稿</button>
        <button class="btn-gold" @click="saveAndPublish"><RocketLaunchIcon class="h-4 w-4" />保存并生成静态页</button>
      </template>
    </PageHeader>

    <!-- 步骤条 -->
    <div class="panel mb-4 px-6 py-5">
      <div class="flex items-center">
        <template v-for="(s, i) in steps" :key="s.key">
          <button class="flex items-center gap-2" @click="active = s.key">
            <span class="flex h-8 w-8 items-center justify-center rounded-full border text-[13px] font-medium transition-colors"
              :class="i < activeIdx ? 'border-gold bg-gold text-white' : i === activeIdx ? 'border-ink bg-ink text-canvas' : 'border-line text-ink-faint'">
              <CheckIcon v-if="i < activeIdx" class="h-4 w-4" /><span v-else>{{ i + 1 }}</span>
            </span>
            <span class="hidden text-[13px] sm:block" :class="i === activeIdx ? 'font-medium text-ink' : 'text-ink-faint'">{{ s.label }}</span>
          </button>
          <div v-if="i < steps.length - 1" class="mx-3 h-px flex-1 bg-line"></div>
        </template>
      </div>
    </div>

    <div class="panel p-6">
      <!-- 基础信息 -->
      <div v-show="active === 'basic'" class="grid max-w-3xl gap-5">
        <div>
          <label class="field-label">商品名称 *</label>
          <input v-model="form.name" class="field" placeholder="如 Aurelia A-Line Tulle Gown" />
        </div>
        <div>
          <label class="field-label">副标题</label>
          <input v-model="form.subtitle" class="field" placeholder="一句话卖点" />
        </div>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="field-label">商品品类 *</label>
            <select v-model="form.category" class="field">
              <option>Wedding Dresses</option><option>Special Occasion</option><option>Accessories</option>
            </select>
          </div>
          <div>
            <label class="field-label">子类目</label>
            <input v-model="form.sub" class="field" placeholder="如 A-Line / Bridesmaid / Veils" />
          </div>
        </div>
        <div>
          <label class="field-label">Outdoor 主题标签</label>
          <div class="flex flex-wrap gap-2">
            <button v-for="t in themes" :key="t" @click="selectedThemes.includes(t) ? selectedThemes.splice(selectedThemes.indexOf(t),1) : selectedThemes.push(t)"
              class="rounded-full border px-3 py-1 text-[12.5px] transition-colors"
              :class="selectedThemes.includes(t) ? 'border-gold bg-gold/12 text-gold-deep' : 'border-line text-ink-soft hover:border-ink'">
              {{ t }}
            </button>
          </div>
        </div>
        <div>
          <label class="field-label">商品介绍（富文本）</label>
          <div class="rounded-luxe border border-line">
            <div class="flex items-center gap-1 border-b border-line px-2 py-1.5 text-ink-faint">
              <button class="rounded px-2 py-1 text-[13px] font-bold hover:bg-canvas-warm">B</button>
              <button class="rounded px-2 py-1 text-[13px] italic hover:bg-canvas-warm">I</button>
              <button class="rounded px-2 py-1 hover:bg-canvas-warm"><Bars3BottomLeftIcon class="h-4 w-4" /></button>
              <span class="mx-1 h-4 w-px bg-line"></span>
              <button class="rounded px-2 py-1 text-[12px] hover:bg-canvas-warm">H2</button>
              <button class="rounded px-2 py-1 text-[12px] hover:bg-canvas-warm">链接</button>
            </div>
            <textarea v-model="form.description" rows="5" class="w-full resize-none px-3 py-2 text-[13px] outline-none" placeholder="描述商品的面料、版型、适用场景…"></textarea>
          </div>
        </div>
      </div>

      <!-- 媒体素材 -->
      <div v-show="active === 'media'" class="max-w-3xl">
        <label class="field-label">商品图廊（拖拽排序，第一张为主图）</label>
        <div class="grid grid-cols-3 gap-4 sm:grid-cols-4">
          <div v-for="(g, i) in gallery" :key="i" class="group relative aspect-[3/4] overflow-hidden rounded-luxe border border-line">
            <img :src="g" class="h-full w-full object-cover" />
            <span v-if="i === 0" class="absolute left-2 top-2 rounded bg-ink/80 px-1.5 py-0.5 text-[10px] text-white">主图</span>
            <button class="absolute right-2 top-2 hidden rounded-full bg-white/90 p-1 group-hover:block"><XMarkIcon class="h-3.5 w-3.5" /></button>
          </div>
          <button class="flex aspect-[3/4] flex-col items-center justify-center gap-2 rounded-luxe border-2 border-dashed border-line text-ink-faint transition-colors hover:border-gold hover:text-gold-deep">
            <ArrowUpTrayIcon class="h-6 w-6" /><span class="text-[12px]">上传图片</span>
          </button>
        </div>
        <div class="mt-6 grid grid-cols-2 gap-4">
          <div class="rounded-luxe border border-line p-4">
            <p class="field-label">Lifestyle 户外场景图</p>
            <button class="mt-2 flex h-24 w-full items-center justify-center rounded-luxe border-2 border-dashed border-line text-ink-faint hover:border-gold">
              <PhotoIcon class="h-6 w-6" />
            </button>
          </div>
          <div class="rounded-luxe border border-line p-4">
            <p class="field-label">走秀 / Walking 视频</p>
            <button class="mt-2 flex h-24 w-full items-center justify-center rounded-luxe border-2 border-dashed border-line text-ink-faint hover:border-gold">
              <PlusIcon class="h-6 w-6" />
            </button>
          </div>
        </div>
      </div>

      <!-- SKU 矩阵 -->
      <div v-show="active === 'sku'">
        <div class="mb-4 flex flex-wrap gap-6">
          <div>
            <p class="field-label">颜色 swatch</p>
            <div class="flex flex-wrap gap-2">
              <button v-for="c in productColors" :key="c.name"
                @click="skuColors.includes(c.name) ? skuColors.splice(skuColors.indexOf(c.name),1) : skuColors.push(c.name)"
                class="flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[12px] transition-colors"
                :class="skuColors.includes(c.name) ? 'border-gold' : 'border-line'">
                <span class="h-3.5 w-3.5 rounded-full border border-line" :style="{ background: c.hex }"></span>{{ c.name }}
              </button>
            </div>
          </div>
          <div>
            <p class="field-label">尺码</p>
            <div class="flex flex-wrap gap-2">
              <button v-for="s in productSizes" :key="s"
                @click="skuSizes.includes(s) ? skuSizes.splice(skuSizes.indexOf(s),1) : skuSizes.push(s)"
                class="rounded-luxe border px-2.5 py-1 text-[12px] transition-colors"
                :class="skuSizes.includes(s) ? 'border-gold bg-gold/10 text-gold-deep' : 'border-line text-ink-soft'">{{ s }}</button>
            </div>
          </div>
        </div>
        <div class="overflow-x-auto rounded-luxe border border-line">
          <table class="data-table">
            <thead>
              <tr><th>颜色 \ 尺码</th><th v-for="s in skuSizes" :key="s" class="text-center">{{ s }}</th></tr>
            </thead>
            <tbody>
              <tr v-for="c in skuColors" :key="c">
                <td class="font-medium text-ink">{{ c }}</td>
                <td v-for="s in skuSizes" :key="s" class="text-center">
                  <input class="field w-20 px-2 py-1 text-center text-[12px]" :placeholder="String(Math.floor(Math.random()*40))" />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p class="mt-2 text-[12px] text-ink-faint">单元格内填写该 SKU 库存数量；点击颜色/尺码标签可增删矩阵维度。</p>
      </div>

      <!-- 尺码表 -->
      <div v-show="active === 'size'" class="max-w-3xl">
        <label class="field-label">尺码对照表（US / UK / AU）</label>
        <div class="overflow-x-auto rounded-luxe border border-line">
          <table class="data-table">
            <thead><tr><th>US</th><th>UK</th><th>AU</th><th>胸围 (in)</th><th>腰围 (in)</th><th>臀围 (in)</th></tr></thead>
            <tbody>
              <tr v-for="r in [['0','4','4','32','24','34'],['2','6','6','33','25','35'],['4','8','8','34','26','36'],['6','10','10','35','27','37'],['8','12','12','36','28','38']]" :key="r[0]">
                <td v-for="(v,i) in r" :key="i"><input class="field w-16 px-2 py-1 text-[12px]" :value="v" /></td>
              </tr>
            </tbody>
          </table>
        </div>
        <button class="btn-ghost mt-3"><PlusIcon class="h-4 w-4" />添加一行</button>
      </div>

      <!-- 定价库存 -->
      <div v-show="active === 'price'" class="grid max-w-2xl gap-5">
        <div class="grid grid-cols-2 gap-4">
          <div><label class="field-label">现价 (USD) *</label><input v-model="form.price" type="number" class="field" /></div>
          <div><label class="field-label">原价 (划线价)</label><input v-model="form.compareAt" type="number" class="field" /></div>
        </div>
        <div class="flex items-center justify-between rounded-luxe border border-line px-4 py-3">
          <div><p class="text-[13px] font-medium text-ink">支持 Klarna / Afterpay 分期</p><p class="text-[12px] text-ink-faint">前台 PDP 显示「Pay in 4」</p></div>
          <Toggle v-model="form.installment" />
        </div>
        <div>
          <label class="field-label">多币种价格（自动按汇率换算，可覆盖）</label>
          <div class="grid grid-cols-4 gap-3">
            <div v-for="cur in ['USD','CAD','AUD','GBP']" :key="cur">
              <span class="mb-1 block text-[11px] text-ink-faint">{{ cur }}</span>
              <input class="field text-[13px]" :placeholder="cur === 'USD' ? String(form.price) : 'auto'" />
            </div>
          </div>
        </div>
      </div>

      <!-- SEO -->
      <div v-show="active === 'seo'" class="grid max-w-2xl gap-5">
        <div><label class="field-label">SEO Title</label><input v-model="form.seoTitle" class="field" placeholder="商品 meta 标题" /></div>
        <div><label class="field-label">URL Slug</label>
          <div class="flex items-center rounded-luxe border border-line">
            <span class="px-3 text-[12px] text-ink-faint">dreamy.com/product/</span>
            <input v-model="form.slug" class="flex-1 bg-transparent py-2 pr-3 text-[13px] outline-none" placeholder="aurelia-aline-tulle" />
          </div>
        </div>
        <div><label class="field-label">Meta Description</label><textarea v-model="form.seoDesc" rows="3" class="field resize-none" placeholder="搜索引擎摘要文案（建议 150 字以内）"></textarea></div>
        <div class="rounded-luxe border border-line bg-canvas/40 p-4">
          <p class="mb-1 text-[11px] uppercase tracking-wide text-ink-faint">搜索结果预览</p>
          <p class="text-[15px] text-info">{{ form.seoTitle || form.name || '商品标题' }} | Dreamy</p>
          <p class="text-[12px] text-ok">dreamy.com › product › {{ form.slug || 'slug' }}</p>
          <p class="text-[12.5px] text-ink-soft">{{ form.seoDesc || '商品的 meta 描述将显示在这里…' }}</p>
        </div>
      </div>

      <!-- 步骤导航 -->
      <div class="mt-8 flex items-center justify-between border-t border-line pt-5">
        <button class="btn-outline" :disabled="activeIdx === 0" @click="goPrev">上一步</button>
        <span class="text-[12px] text-ink-faint">第 {{ activeIdx + 1 }} / {{ steps.length }} 步</span>
        <button v-if="activeIdx < steps.length - 1" class="btn-primary" @click="goNext">下一步</button>
        <button v-else class="btn-gold" @click="saveAndPublish"><RocketLaunchIcon class="h-4 w-4" />完成并生成静态页</button>
      </div>
    </div>
  </div>
</template>
