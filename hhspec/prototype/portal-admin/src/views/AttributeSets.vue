<script setup>
import { ref, computed } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import {
  attributeDict, attributeSets, standardTaxonomy,
  silhouetteOptions, necklineOptions, sleeveOptions, backStyleOptions,
  waistlineOptions, trainOptions, lengthOptions, fabricOptions,
  embellishmentOptions, supportOptions, occasionOptions,
  styleTagOptions, seasonOptions, childOverridesOf
} from '@/data/mock'
import { PlusIcon, PencilSquareIcon, TrashIcon, RocketLaunchIcon, XMarkIcon } from '@heroicons/vue/24/outline'

const tab = ref('dict')

// ===== Tab 1：属性字典 =====
// 把所有选项数组汇聚成一个 map，供字典展示
const optionsMap = {
  silhouetteOptions, necklineOptions, sleeveOptions, backStyleOptions,
  waistlineOptions, trainOptions, lengthOptions, fabricOptions: fabricOptions.map(f => f.name),
  embellishmentOptions, supportOptions, occasionOptions, styleTagOptions, seasonOptions
}

const dict = ref(attributeDict.map(a => ({
  ...a,
  options: a.optionsKey ? [...(optionsMap[a.optionsKey] || [])] : []
})))

const expanded = ref(null)
const editingOption = ref({ attrKey: null, idx: null, val: '' })
const newOption = ref({ attrKey: null, val: '' })

function startAddOption(key) { newOption.value = { attrKey: key, val: '' } }
function confirmAddOption(attr) {
  const v = newOption.value.val.trim()
  if (v && newOption.value.attrKey === attr.key) { attr.options.push(v); newOption.value = { attrKey: null, val: '' } }
}
function removeOption(attr, i) { attr.options.splice(i, 1) }
function startEdit(key, i, val) { editingOption.value = { attrKey: key, idx: i, val } }
function confirmEdit(attr) {
  const v = editingOption.value.val.trim()
  if (v) attr.options[editingOption.value.idx] = v
  editingOption.value = { attrKey: null, idx: null, val: '' }
}

// 子品类覆盖汇总：每个大品类下有覆盖的子品类列表
const subcategoryOverrides = computed(() =>
  standardTaxonomy.map(root => ({
    rootId: root.id,
    rootName: root.name,
    children: (root.children || [])
      .map(c => ({ ...c, overrides: childOverridesOf(c.id) }))
      .filter(c => Object.keys(c.overrides).length > 0)
  })).filter(r => r.children.length > 0)
)

const STATE_LABELS_FULL = { visible: '必填', optional: '可选', hidden: '隐藏' }
const STATES = ['visible', 'optional', 'hidden']
const STATE_LABELS = { visible: '必', optional: '选', hidden: '—' }
const STATE_CLASSES = {
  visible:  'bg-gold/15 text-gold-deep',
  optional: 'bg-info/10 text-info',
  hidden:   'bg-canvas-warm text-ink-faint'
}

// 列 = 属性集（而非每个品类一列）。多个品类共享同一属性集时合并到一列，
// 列数 = 属性集数量，与品类数量无关，品类再多也不会横向爆炸。
const attrSetColumns = computed(() =>
  Object.values(attributeSets).map(set => ({
    id: set.id,
    label: set.label,
    // 该属性集覆盖了哪些标准品类
    categories: standardTaxonomy.filter(c => c.attributeSetId === set.id).map(c => c.name)
  }))
)

const mattrKeys = computed(() => attributeDict.filter(a => a.type !== 'text' && a.type !== 'toggle').map(a => a.key))

// 可编辑副本：按属性集 id 索引（多品类共享，只需维护一份）
const matrix = ref(
  Object.fromEntries(
    Object.values(attributeSets).map(set => [set.id, { ...set.attrs }])
  )
)

function cycleState(setId, key) {
  const cur = matrix.value[setId][key] || 'hidden'
  matrix.value[setId][key] = STATES[(STATES.indexOf(cur) + 1) % STATES.length]
}
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Catalog" title="属性集定义" subtitle="管理商品属性字典与品类×属性可见性矩阵">
      <template #actions>
        <button class="btn-gold"><RocketLaunchIcon class="h-4 w-4" />保存配置</button>
      </template>
    </PageHeader>

    <div class="mb-4 flex gap-1 border-b border-line">
      <button v-for="t in [['dict','属性字典'],['matrix','品类×属性矩阵']]" :key="t[0]"
        @click="tab = t[0]"
        class="border-b-2 px-4 py-2.5 text-[13px] transition-colors"
        :class="tab === t[0] ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'">
        {{ t[1] }}
      </button>
    </div>

    <!-- ① 属性字典 -->
    <div v-show="tab === 'dict'" class="space-y-2">
      <div v-for="attr in dict" :key="attr.key" class="panel overflow-hidden">
        <!-- 属性行 -->
        <div class="flex cursor-pointer items-center gap-3 p-4" @click="expanded = expanded === attr.key ? null : attr.key">
          <div class="flex-1">
            <span class="font-medium text-ink text-[13px]">{{ attr.label }}</span>
            <span class="ml-2 rounded px-1.5 py-0.5 text-[10px]"
              :class="attr.type === 'select' ? 'bg-gold/10 text-gold-deep' : attr.type === 'multiselect' ? 'bg-info/10 text-info' : 'bg-canvas-warm text-ink-faint'">
              {{ attr.type }}
            </span>
          </div>
          <span v-if="attr.options.length" class="text-[12px] text-ink-faint">{{ attr.options.length }} 个选项</span>
          <span v-else class="text-[12px] italic text-ink-faint">无选项（{{ attr.type }}）</span>
          <svg class="h-4 w-4 text-ink-faint transition-transform" :class="expanded === attr.key ? 'rotate-180' : ''" viewBox="0 0 20 20" fill="currentColor"><path d="M5 7l5 5 5-5"/></svg>
        </div>

        <!-- 选项展开区（仅有选项的属性） -->
        <div v-if="expanded === attr.key && attr.options.length" class="border-t border-line px-4 pb-4 pt-3">
          <div class="flex flex-wrap gap-2">
            <span v-for="(opt, i) in attr.options" :key="i"
              class="group flex items-center gap-1 rounded-full border border-line px-2.5 py-1 text-[12px] text-ink-soft">
              <span v-if="editingOption.attrKey === attr.key && editingOption.idx === i">
                <input v-model="editingOption.val" class="w-24 border-b border-gold bg-transparent text-[12px] outline-none" @keyup.enter="confirmEdit(attr)" @keyup.escape="editingOption = { attrKey: null }" />
              </span>
              <span v-else @dblclick="startEdit(attr.key, i, opt)">{{ opt }}</span>
              <button @click.stop="removeOption(attr, i)" class="invisible text-ink-faint hover:text-danger group-hover:visible"><XMarkIcon class="h-3 w-3" /></button>
            </span>
            <!-- 添加选项 -->
            <span v-if="newOption.attrKey === attr.key" class="flex items-center gap-1 rounded-full border border-gold px-2.5 py-1">
              <input v-model="newOption.val" class="w-24 bg-transparent text-[12px] outline-none" placeholder="新选项" @keyup.enter="confirmAddOption(attr)" @keyup.escape="newOption = { attrKey: null }" autofocus />
            </span>
            <button @click.stop="startAddOption(attr.key)" class="btn-ghost text-[12px]"><PlusIcon class="h-3 w-3" />添加</button>
          </div>
          <p class="mt-2 text-[11px] text-ink-faint">双击选项可编辑；回车确认，Esc 取消。</p>
        </div>
        <div v-else-if="expanded === attr.key" class="border-t border-line px-4 py-3 text-[12px] italic text-ink-faint">
          该属性类型（{{ attr.type }}）无预设选项。
        </div>
      </div>
    </div>

    <!-- ② 品类×属性矩阵（按属性集分组，列数=属性集数，与品类数量无关） -->
    <div v-show="tab === 'matrix'">
      <div class="mb-3 flex items-start gap-2 rounded-luxe border border-line bg-canvas-warm/60 px-4 py-2.5 text-[12px] text-ink-soft">
        <span>列按<strong class="text-ink">属性集</strong>聚合：多个品类共享同一属性集时合并为一列，品类再多也不会横向爆炸。点击格子循环切换：<span class="rounded px-1.5 py-0.5 bg-gold/15 text-gold-deep text-[10px]">必</span> → <span class="rounded px-1.5 py-0.5 bg-info/10 text-info text-[10px]">选</span> → <span class="rounded px-1.5 py-0.5 bg-canvas-warm text-ink-faint text-[10px]">—</span>（隐藏）</span>
      </div>
      <div class="overflow-x-auto rounded-luxe border border-line">
        <table class="w-full border-collapse text-[12px]">
          <thead>
            <tr class="border-b border-line bg-canvas-warm/40">
              <th class="sticky left-0 z-10 bg-canvas-warm/40 min-w-[140px] py-3 px-4 text-left font-medium text-ink-soft">属性 \ 属性集</th>
              <th v-for="col in attrSetColumns" :key="col.id" class="min-w-[140px] px-3 py-3 text-center align-top font-medium text-ink-soft">
                <div class="font-display text-[13px] text-ink">{{ col.label }}</div>
                <div class="mt-1 flex flex-wrap justify-center gap-1">
                  <span v-for="c in col.categories" :key="c" class="rounded-full bg-gold/10 px-1.5 py-0.5 text-[10px] text-gold-deep">{{ c }}</span>
                </div>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="attr in dict.filter(a => a.optionsKey || a.type === 'toggle')" :key="attr.key"
              class="border-b border-line/60 last:border-0 hover:bg-canvas-warm/40">
              <td class="sticky left-0 z-10 bg-canvas py-2.5 px-4 text-ink-soft">{{ attr.label }}</td>
              <td v-for="col in attrSetColumns" :key="col.id" class="px-3 py-2 text-center">
                <button @click="cycleState(col.id, attr.key)"
                  class="min-w-[2rem] rounded px-2 py-0.5 text-[11px] font-medium transition-colors"
                  :class="STATE_CLASSES[matrix[col.id]?.[attr.key] || 'hidden']">
                  {{ STATE_LABELS[matrix[col.id]?.[attr.key] || 'hidden'] }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- ③ 子品类覆盖卡片区（仅矩阵 tab 内显示，只读概览，编辑入口在「品类与标签」页） -->
      <div v-if="subcategoryOverrides.length" class="mt-6">
        <h3 class="mb-3 text-[13px] font-medium text-ink">子品类属性覆盖（相对父级基础属性集的 delta）</h3>
        <div class="space-y-4">
          <div v-for="group in subcategoryOverrides" :key="group.rootId">
            <p class="mb-2 text-[11px] font-semibold uppercase tracking-widest text-ink-faint">{{ group.rootName }}</p>
            <div class="flex flex-wrap gap-3">
              <div v-for="child in group.children" :key="child.id"
                class="rounded-luxe border border-line bg-canvas-warm/40 px-4 py-3 min-w-[180px]">
                <p class="mb-2 text-[13px] font-medium text-ink">{{ child.name }}</p>
                <div class="space-y-1">
                  <div v-for="(val, key) in child.overrides" :key="key"
                    class="flex items-center justify-between gap-4 text-[12px]">
                    <span class="text-ink-soft">{{ attributeDict.find(a => a.key === key)?.label || key }}</span>
                    <span class="rounded px-1.5 py-0.5 text-[10px] font-medium"
                      :class="STATE_CLASSES[val]">{{ STATE_LABELS[val] }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
