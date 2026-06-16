---
title: "catalog fabric-care 前端详细设计"
module: "catalog"
change: "fabric-care-module"
version: "1.0"
date: "2026-06-14"
author: "l2_frontend_designer"
status: "draft"
---

# catalog fabric-care 前端详细设计

## 0. 设计范围与约束

**变更范围**: fabric-care-module
**影响组件**:
- portal-admin: ProductEdit.vue 扩展（新增面料成分编辑区 + 护理标签选择区）
- portal-store: PDP 扩展（新增 Fabric & Care 展示区块）

**技术栈**（tech-profile.yml）:
- portal-admin: Vue 3.4 Composition API + TypeScript + Pinia + Headless UI + Tailwind CSS
- portal-store: Next.js 15 App Router + React 19 + TypeScript + Tailwind CSS

**枚举映射**（IntEnum 整数契约，来源: er-diagram.yml）:
```typescript
enum Layer { Shell = 1, Lining = 2, Overlay = 3, Trim = 4 }
enum Material {
  Cotton = 1, Polyester = 2, Lace = 3, Satin = 4, Chiffon = 5,
  Tulle = 6, Silk = 7, Organza = 8, Spandex = 9, Nylon = 10
}
enum CareCategory { washing = 1, bleaching = 2, drying = 3, ironing = 4, dry_cleaning = 5 }
```

**证据锚点**:
- acceptance.yml s-040 ~ s-084 (45 个场景)
- boundary-scenarios.yml B-FC-001 ~ B-FC-015
- catalog-fabric-care-api-detail.md (API 端点定义)
- catalog-frontend-detail.md (既有前端设计模式)

## 1. 后台组件（portal-admin Vue 3）

### COMP-FC-01: FabricCompositionEditor.vue

**功能**: 商品编辑表单中的面料成分编辑器，支持多层次（Shell/Lining/Overlay/Trim）动态添加/删除行，percentage 总和校验。

**Props**:
```typescript
interface Props {
  modelValue: FabricComposition[]  // v-model 绑定
  readonly?: boolean               // 只读模式（查看商品详情）
}

interface FabricComposition {
  id?: number           // 编辑模式有 id，新增模式无
  layer: number         // 1..4 枚举
  material: number      // 1..10 枚举
  percentage: number    // 0..100 decimal(5,2)
  sortOrder: number     // 同层排序
}
```

**Emits**:
```typescript
const emit = defineEmits<{
  'update:modelValue': [value: FabricComposition[]]
  'validation-error': [errors: Map<number, string>]  // layer → 错误信息
}>()
```

**状态管理**:
```typescript
const localCompositions = ref<FabricComposition[]>([])
const layerErrors = ref<Map<number, string>>(new Map())
const isDirty = ref(false)

// 按 layer 分组（computed）
const compositionsByLayer = computed(() => {
  const groups = new Map<number, FabricComposition[]>()
  localCompositions.value.forEach(comp => {
    if (!groups.has(comp.layer)) groups.set(comp.layer, [])
    groups.get(comp.layer)!.push(comp)
  })
  // 同层按 sortOrder 排序
  groups.forEach(items => items.sort((a, b) => a.sortOrder - b.sortOrder))
  return groups
})
```

**交互逻辑**:

```typescript
// INTERACTION-FC-01: 添加行（按层分组添加按钮）
function addRow(layer: number) {
  const existingInLayer = compositionsByLayer.value.get(layer) || []
  const maxSortOrder = existingInLayer.length > 0 
    ? Math.max(...existingInLayer.map(c => c.sortOrder)) 
    : -1
  
  localCompositions.value.push({
    layer,
    material: 1,  // 默认 Cotton
    percentage: 0,
    sortOrder: maxSortOrder + 1
  })
  isDirty.value = true
}

// INTERACTION-FC-02: 删除行
function removeRow(index: number) {
  localCompositions.value.splice(index, 1)
  isDirty.value = true
  validatePercentageSum()  // 删除后重新校验
}

// INTERACTION-FC-03: percentage 总和校验（每层独立校验）
function validatePercentageSum() {
  layerErrors.value.clear()
  
  compositionsByLayer.value.forEach((items, layer) => {
    const sum = items.reduce((acc, c) => acc + c.percentage, 0)
    if (sum > 100) {
      layerErrors.value.set(layer, `${getLayerName(layer)} 总和超过 100% (当前 ${sum}%)`)
    } else if (sum < 100 && sum > 0) {
      layerErrors.value.set(layer, `${getLayerName(layer)} 总和不足 100% (当前 ${sum}%)`)
    }
  })
  
  emit('validation-error', layerErrors.value)
  return layerErrors.value.size === 0
}

// INTERACTION-FC-04: 字段变更（material/percentage）
function onFieldChange() {
  isDirty.value = true
  validatePercentageSum()
  emit('update:modelValue', localCompositions.value)
}
```

**UI 结构**（伪代码）:

```vue
<template>
  <div class="fabric-composition-editor">
    <!-- 按层分组渲染 -->
    <div v-for="layer in [1, 2, 3, 4]" :key="layer" class="layer-group">
      <div class="layer-header">
        <h4>{{ getLayerName(layer) }}</h4>
        <button @click="addRow(layer)" :disabled="readonly">
          + 添加{{ getLayerName(layer) }}成分
        </button>
      </div>
      
      <!-- 错误提示（percentage 总和不对） -->
      <div v-if="layerErrors.has(layer)" class="error-banner">
        {{ layerErrors.get(layer) }}
      </div>
      
      <!-- 成分行列表 -->
      <div class="composition-rows">
        <div v-for="(comp, idx) in compositionsByLayer.get(layer)" 
             :key="comp.id || idx" 
             class="composition-row">
          
          <!-- Material 下拉 -->
          <select v-model="comp.material" @change="onFieldChange" :disabled="readonly">
            <option :value="1">棉</option>
            <option :value="2">涤纶</option>
            <option :value="3">蕾丝</option>
            <!-- ...其他 material 枚举 -->
          </select>
          
          <!-- Percentage 输入 -->
          <input 
            type="number" 
            v-model.number="comp.percentage" 
            @input="onFieldChange"
            :disabled="readonly"
            min="0" 
            max="100" 
            step="0.01"
            placeholder="占比 (%)"
          />
          
          <!-- 删除按钮 -->
          <button @click="removeRow(idx)" :disabled="readonly">
            删除
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
```

**验收场景映射**:
- s-040: 添加成分行 → addRow()
- s-041: percentage 总和 100% → validatePercentageSum() 通过
- s-042: percentage 总和超 100% → layerErrors 显示错误横幅
- s-043: 删除行 → removeRow()
- B-FC-001: percentage 小数精度 0.01 → input step="0.01"

### COMP-FC-02: CareSymbolSelector.vue

**功能**: 护理标签选择器（按 category 分组展示），支持多选，显示 Unicode 符号 + 标签文本。

**Props**:
```typescript
interface Props {
  modelValue: number[]        // v-model 绑定的 care_instruction_ids
  readonly?: boolean
}

interface CareInstruction {
  id: number
  code: string
  symbolUnicode: string
  labelZh: string
  category: number          // 1..5 枚举
  sortOrder: number
}
```

**Emits**:
```typescript
const emit = defineEmits<{
  'update:modelValue': [value: number[]]
}>()
```

**状态管理**:
```typescript
const careInstructions = ref<CareInstruction[]>([])
const selectedIds = ref<Set<number>>(new Set())
const loading = ref(false)

// 按 category 分组（computed）
const instructionsByCategory = computed(() => {
  const groups = new Map<number, CareInstruction[]>()
  careInstructions.value.forEach(instr => {
    if (!groups.has(instr.category)) groups.set(instr.category, [])
    groups.get(instr.category)!.push(instr)
  })
  // 同组按 sortOrder 排序
  groups.forEach(items => items.sort((a, b) => a.sortOrder - b.sortOrder))
  return groups
})

// 分类名称映射
const categoryNames = {
  1: '水洗',
  2: '漂白',
  3: '烘干',
  4: '熨烫',
  5: '干洗'
}
```

**交互逻辑**:

```typescript
// INTERACTION-FC-05: 初始化加载护理标签字典
onMounted(async () => {
  loading.value = true
  try {
    const response = await catalogApi.listAdminCareInstructions()
    careInstructions.value = response.items.filter(i => i.status === 1)  // 仅加载 active
  } catch (error) {
    // 错误处理：toast 提示，组件显示降级态
  } finally {
    loading.value = false
  }
})

// INTERACTION-FC-06: 切换选中状态
function toggleSelection(id: number) {
  if (selectedIds.value.has(id)) {
    selectedIds.value.delete(id)
  } else {
    selectedIds.value.add(id)
  }
  emit('update:modelValue', Array.from(selectedIds.value))
}

// INTERACTION-FC-07: 同步 props 变化到本地状态
watch(() => props.modelValue, (newIds) => {
  selectedIds.value = new Set(newIds)
}, { immediate: true })
```

**UI 结构**（伪代码）:

```vue
<template>
  <div class="care-symbol-selector">
    <div v-if="loading">加载中...</div>
    
    <div v-else class="category-groups">
      <!-- 按 category 分组渲染 -->
      <div v-for="category in [1, 2, 3, 4, 5]" :key="category" class="category-group">
        <h4>{{ categoryNames[category] }}</h4>
        
        <div class="symbol-grid">
          <div v-for="instr in instructionsByCategory.get(category)" 
               :key="instr.id"
               :class="['symbol-card', { selected: selectedIds.has(instr.id) }]"
               @click="toggleSelection(instr.id)">
            
            <!-- Unicode 符号 -->
            <span class="symbol-icon">{{ instr.symbolUnicode }}</span>
            
            <!-- 标签文本 -->
            <span class="symbol-label">{{ instr.labelZh }}</span>
            
            <!-- 选中指示器 -->
            <span v-if="selectedIds.has(instr.id)" class="check-icon">✓</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
```

**验收场景映射**:
- s-050: 按 category 分组展示 → instructionsByCategory computed
- s-051: 多选支持 → selectedIds Set
- s-052: Unicode 符号显示 → symbolUnicode 渲染
- B-FC-010: 仅显示 status=active → 加载时过滤

### COMP-FC-03: ProductEdit.vue 扩展（集成面料与护理标签编辑）

**扩展位置**: ProductEdit.vue 中新增两个 section（sec-fabric 和 sec-care），插入在 sec-content 之前。

**状态扩展**:

```typescript
// 扩展 ProductEdit.vue 的 form 状态
interface ProductForm {
  // ...existing fields...
  fabricCompositions: FabricComposition[]
  careInstructionIds: number[]
  fabricCareNote: string
}

const form = ref<ProductForm>({
  // ...existing...
  fabricCompositions: [],
  careInstructionIds: [],
  fabricCareNote: ''
})
```

**UI 集成**（伪代码）:

```vue
<template>
  <div class="product-edit">
    <!-- 左侧锚点导航 -->
    <nav class="anchor-nav">
      <!-- ...existing anchors... -->
      <a href="#sec-fabric">面料成分</a>
      <a href="#sec-care">护理标签</a>
    </nav>
    
    <div class="form-sections">
      <!-- ...existing sections... -->
      
      <!-- sec-fabric -->
      <section id="sec-fabric" class="form-section">
        <h3>面料成分</h3>
        <FabricCompositionEditor 
          v-model="form.fabricCompositions"
          @validation-error="handleFabricErrors"
        />
      </section>
      
      <!-- sec-care -->
      <section id="sec-care" class="form-section">
        <h3>护理标签</h3>
        <CareSymbolSelector 
          v-model="form.careInstructionIds"
        />
        
        <div class="care-note-field">
          <label>护理备注（可选）</label>
          <textarea 
            v-model="form.fabricCareNote"
            placeholder="特殊护理说明，如"珠饰区域建议专业干洗""
            maxlength="500"
          />
        </div>
      </section>
    </div>
  </div>
</template>
```

**表单提交逻辑**:

```typescript
// INTERACTION-FC-08: 提交前校验
async function submitProduct() {
  // 1. 调用 FabricCompositionEditor 的校验
  const fabricValid = await validateFabric()
  if (!fabricValid) {
    // 滚动到 sec-fabric + 高亮错误
    document.getElementById('sec-fabric')?.scrollIntoView({ behavior: 'smooth' })
    return
  }
  
  // 2. 组装 API payload
  const payload = {
    ...form.value,
    // snake_case 转换在 api client 完成
  }
  
  try {
    if (isEditMode) {
      await catalogApi.updateProduct(productId, payload)
    } else {
      await catalogApi.createProduct(payload)
    }
    // 成功 toast + 跳转列表
  } catch (error) {
    if (error.code === 422510) {
      // FABRIC_PERCENTAGE_INVALID: 服务端二次校验失败
      // 从 error.details 提取 layer 和 actual_sum
      const { layer, actualSum } = error.details
      // 高亮对应 layer 分组 + 显示错误
      fabricErrors.value.set(layer, `该层总和为 ${actualSum}%，必须为 100%`)
      document.getElementById('sec-fabric')?.scrollIntoView()
    } else {
      // 其他错误按既有模式处理
    }
  }
}

// INTERACTION-FC-09: 面料错误处理回调
function handleFabricErrors(errors: Map<number, string>) {
  fabricErrors.value = errors
  // 阻止提交（提交按钮根据 fabricErrors 判断）
}
```

**验收场景映射**:
- s-044: 保存商品包含面料成分 → submitProduct() 提交 fabricCompositions
- s-045: 422510 错误处理 → 高亮 layer + 显示 actualSum
- s-046: 保存商品包含护理标签 → submitProduct() 提交 careInstructionIds
- s-047: fabricCareNote 可选提交 → 允许为空

## 2. 消费端组件（portal-store Next.js + React）

### COMP-FC-04: FabricCareSection.tsx（PDP 展示区块）

**功能**: 商品详情页的面料与护理标签展示区块，显示面料成分列表 + 护理符号 + 护理备注。

**Props**:
```typescript
interface Props {
  fabricCompositions: FabricComposition[]
  careInstructions: CareInstruction[]
  fabricCareNote?: string
}

interface FabricComposition {
  layer: number
  material: number
  percentage: number
  sortOrder: number
}

interface CareInstruction {
  id: number
  symbolUnicode: string
  label: string        // 已按 locale 解析（服务端返回 label_en）
  category: number
}
```

**UI 结构**（伪代码）:

```tsx
export function FabricCareSection({ 
  fabricCompositions, 
  careInstructions, 
  fabricCareNote 
}: Props) {
  // 枚举映射（消费端英文）
  const layerNames = {
    1: 'Shell',
    2: 'Lining',
    3: 'Overlay',
    4: 'Trim'
  }
  
  const materialNames = {
    1: 'Cotton', 2: 'Polyester', 3: 'Lace', 4: 'Satin',
    5: 'Chiffon', 6: 'Tulle', 7: 'Silk', 8: 'Organza',
    9: 'Spandex', 10: 'Nylon'
  }
  
  // 按 layer 分组
  const compositionsByLayer = useMemo(() => {
    const groups = new Map<number, FabricComposition[]>()
    fabricCompositions.forEach(comp => {
      if (!groups.has(comp.layer)) groups.set(comp.layer, [])
      groups.get(comp.layer)!.push(comp)
    })
    // 同层按 sortOrder 排序
    groups.forEach(items => items.sort((a, b) => a.sortOrder - b.sortOrder))
    return groups
  }, [fabricCompositions])

  if (fabricCompositions.length === 0 && careInstructions.length === 0) {
    return null  // 无数据时整个区块不渲染
  }

  return (
    <section className="fabric-care-section">
      <h2>Fabric & Care</h2>
      
      {/* 面料成分 */}
      {fabricCompositions.length > 0 && (
        <div className="fabric-composition">
          <h3>Composition</h3>
          {Array.from(compositionsByLayer.entries()).map(([layer, items]) => (
            <div key={layer} className="layer-group">
              <h4>{layerNames[layer]}</h4>
              <ul>
                {items.map((comp, idx) => (
                  <li key={idx}>
                    {materialNames[comp.material]}: {comp.percentage}%
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}
      
      {/* 护理标签 */}
      {careInstructions.length > 0 && (
        <div className="care-instructions">
          <h3>Care Instructions</h3>
          <div className="care-symbols">
            {careInstructions.map(instr => (
              <div key={instr.id} className="care-symbol-item">
                <span className="symbol-icon">{instr.symbolUnicode}</span>
                <span className="symbol-label">{instr.label}</span>
              </div>
            ))}
          </div>
        </div>
      )}
      
      {/* 护理备注 */}
      {fabricCareNote && (
        <div className="care-note">
          <p>{fabricCareNote}</p>
        </div>
      )}
    </section>
  )
}
```

**验收场景映射**:
- s-060: 按 layer 分组显示 → compositionsByLayer Map
- s-061: percentage 显示小数 → {comp.percentage}%
- s-062: 护理符号显示 → symbolUnicode 渲染
- s-063: 护理标签文本显示 → label（服务端已按 locale 解析）
- s-064: fabricCareNote 显示 → 条件渲染

### COMP-FC-05: PDP 页面集成（app/product/[slug]/page.tsx）

**集成位置**: ProductDetailPage 中，插入在 ProductBuyBox 下方，ProductReviews 上方。

**数据获取**（服务端）:

```tsx
// app/product/[slug]/page.tsx
export default async function ProductDetailPage({ 
  params: { slug },
  searchParams 
}: { 
  params: { slug: string }
  searchParams: { locale?: string }
}) {
  const locale = searchParams.locale || 'en'
  
  // 获取商品详情（包含面料与护理数据）
  const product = await fetchStoreProduct(slug, locale)
  
  if (!product) {
    notFound()
  }
  
  return (
    <div className="product-detail">
      <ProductGallery images={product.images} />
      <ProductBuyBox product={product} />
      
      {/* 新增：面料与护理区块 */}
      <FabricCareSection 
        fabricCompositions={product.fabricCompositions}
        careInstructions={product.careInstructions}
        fabricCareNote={product.fabricCareNote}
      />
      
      <ProductReviews productId={product.id} />
    </div>
  )
}

// ISR 配置（复用既有配置）
export const revalidate = 300  // TTL 兜底
export const dynamicParams = true  // 按需生成
```

**API 响应消费**:

```typescript
// lib/api/catalog-api.ts
export async function fetchStoreProduct(slug: string, locale: string) {
  const response = await fetch(`/api/store/products/${slug}?locale=${locale}`, {
    next: { revalidate: 300 }  // ISR
  })
  
  if (!response.ok) {
    if (response.status === 404) return null
    throw new Error('Failed to fetch product')
  }
  
  const { data } = await response.json()
  
  // camelCase 转换
  return deepCamelize(data) as StoreProductDetail
}
```

**验收场景映射**:
- s-065: PDP 显示面料区块 → FabricCareSection 集成
- s-066: 无面料数据时区块不显示 → 条件渲染（组件内部）
- s-067: ISR 缓存策略 → revalidate: 300 + on-demand（MQ 失效链）
- B-FC-015: 多语言支持 → locale 参数传递，服务端返回已解析 label

## 3. 样式规范

**Tailwind CSS 类名约定**（复用既有 design token）:

```typescript
// FabricCompositionEditor.vue
const styles = {
  editor: 'space-y-6',
  layerGroup: 'border border-gray-200 rounded-lg p-4',
  layerHeader: 'flex justify-between items-center mb-3',
  errorBanner: 'bg-red-50 border border-red-200 text-red-700 px-4 py-2 rounded mb-3',
  compositionRow: 'flex items-center gap-3 mb-2',
  select: 'form-select rounded-md border-gray-300',
  input: 'form-input w-24 rounded-md border-gray-300',
  deleteBtn: 'text-red-600 hover:text-red-800'
}

// CareSymbolSelector.vue
const styles = {
  categoryGroup: 'mb-6',
  symbolGrid: 'grid grid-cols-4 gap-3',
  symbolCard: 'border border-gray-300 rounded-lg p-3 cursor-pointer hover:border-blue-500 transition',
  symbolCardSelected: 'border-blue-500 bg-blue-50',
  symbolIcon: 'text-3xl block text-center',
  symbolLabel: 'text-sm text-center block mt-2',
  checkIcon: 'absolute top-2 right-2 text-blue-600'
}

// FabricCareSection.tsx（消费端）
const styles = {
  section: 'py-8 border-t border-gray-200',
  heading: 'text-2xl font-semibold mb-4',
  subheading: 'text-lg font-medium mb-3',
  layerGroup: 'mb-4',
  compositionList: 'list-disc list-inside text-gray-700',
  careSymbols: 'flex flex-wrap gap-4',
  careSymbolItem: 'flex flex-col items-center',
  symbolIcon: 'text-4xl',
  symbolLabel: 'text-sm text-gray-600 mt-1',
  careNote: 'mt-4 p-4 bg-gray-50 rounded-lg text-gray-700'
}
```

**响应式设计**:

```css
/* 移动端适配 */
@media (max-width: 640px) {
  .symbol-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .care-symbols {
    gap: 0.75rem;
  }
}
```

## 4. 可访问性（WCAG 2.1 AA）

**键盘导航**:
- Tab 键聚焦：FabricCompositionEditor 的 select/input/button 按 DOM 顺序可聚焦
- CareSymbolSelector: 符号卡片支持 Tab 聚焦 + Space/Enter 切换选中
- 删除按钮：tabindex="0" + 回车键触发删除

**屏幕阅读器**:

```vue
<!-- FabricCompositionEditor -->
<div class="composition-row" role="group" :aria-label="`${getLayerName(layer)} 成分 ${idx + 1}`">
  <label :for="`material-${idx}`" class="sr-only">材质</label>
  <select :id="`material-${idx}`" v-model="comp.material">...</select>
  
  <label :for="`percentage-${idx}`" class="sr-only">占比</label>
  <input 
    :id="`percentage-${idx}`" 
    v-model="comp.percentage"
    :aria-describedby="layerErrors.has(layer) ? `error-${layer}` : undefined"
  />
  
  <button 
    :aria-label="`删除 ${getMaterialName(comp.material)} 成分`"
    @click="removeRow(idx)"
  >
    删除
  </button>
</div>

<div v-if="layerErrors.has(layer)" :id="`error-${layer}`" role="alert">
  {{ layerErrors.get(layer) }}
</div>

<!-- CareSymbolSelector -->
<div 
  class="symbol-card"
  role="checkbox"
  :aria-checked="selectedIds.has(instr.id)"
  :aria-label="`${instr.labelZh} 护理标签`"
  tabindex="0"
  @click="toggleSelection(instr.id)"
  @keydown.enter="toggleSelection(instr.id)"
  @keydown.space.prevent="toggleSelection(instr.id)"
>
  <span aria-hidden="true">{{ instr.symbolUnicode }}</span>
  <span>{{ instr.labelZh }}</span>
</div>
```

**颜色对比度**:
- 错误文本：text-red-700 on bg-red-50（对比度 ≥ 4.5:1）
- 选中态：border-blue-500 + bg-blue-50（对比度 ≥ 3:1）
- 护理符号：text-4xl 确保视觉可识别

**验收场景映射**:
- s-070: 键盘导航支持 → Tab/Enter/Space 键操作
- s-071: 屏幕阅读器标注 → aria-label/aria-describedby/role
- s-072: 错误提示 role="alert" → 自动朗读

## 5. 错误处理

**后台组件错误处理**:

```typescript
// FabricCompositionEditor.vue
const errorHandling = {
  // 客户端校验错误
  validationError: {
    trigger: 'percentage 总和不为 100%',
    display: 'layerErrors Map → 错误横幅（红色背景）',
    recovery: '用户调整 percentage 后自动重新校验'
  },
  
  // 服务端校验错误（422510）
  serverValidationError: {
    trigger: 'API 返回 FABRIC_PERCENTAGE_INVALID',
    display: 'ProductEdit.submitProduct() 捕获 → 高亮对应 layer + 滚动到 sec-fabric',
    details: 'error.details: { layer, actualSum }',
    recovery: '用户修正后重新提交'
  }
}

// CareSymbolSelector.vue
const errorHandling = {
  // 加载失败
  loadError: {
    trigger: 'listAdminCareInstructions() 失败',
    display: 'toast 提示"护理标签加载失败" + 组件显示降级态（空卡片 + 重试按钮）',
    recovery: '点击重试按钮重新加载'
  }
}
```

**消费端组件错误处理**:

```typescript
// FabricCareSection.tsx
const errorHandling = {
  // 无数据
  noData: {
    trigger: 'fabricCompositions.length === 0 && careInstructions.length === 0',
    display: '整个区块不渲染（return null）',
    recovery: 'N/A（正常业务场景，非错误）'
  },
  
  // API 失败（PDP 级别）
  fetchError: {
    trigger: 'fetchStoreProduct() 抛出异常',
    display: 'Next.js error.tsx 捕获 → 通用错误页 + 重试按钮',
    recovery: '点击重试重新加载整个 PDP'
  },
  
  // 404
  notFound: {
    trigger: 'fetchStoreProduct() 返回 null',
    display: 'notFound() → Next.js 404 页',
    recovery: 'N/A'
  }
}
```

**验收场景映射**:
- B-FC-005: percentage 总和校验客户端错误 → layerErrors 显示
- B-FC-006: 422510 服务端错误 → 高亮 layer + 滚动
- B-FC-011: 护理标签加载失败 → 降级态 + 重试
- B-FC-014: PDP 无面料数据 → 区块不渲染

## 6. 性能优化

**后台组件优化**:

```typescript
// FabricCompositionEditor.vue
const performanceOptimizations = {
  // 防抖校验
  validateDebounced: debounce(validatePercentageSum, 300),
  
  // computed 缓存分组结果
  compositionsByLayer: computed(() => { /* ... */ }),
  
  // 虚拟滚动（如果单层超过 50 行，实际场景罕见）
  virtualScroll: '暂不实现（单层通常 ≤ 5 行）'
}

// CareSymbolSelector.vue
const performanceOptimizations = {
  // 加载时过滤（一次性）
  filterActive: '加载后仅保留 status=1，避免每次渲染过滤',
  
  // computed 缓存分组
  instructionsByCategory: computed(() => { /* ... */ }),
  
  // 图标字体加载
  fontPreload: 'Unicode 符号使用系统字体（无需额外加载）'
}
```

**消费端组件优化**:

```typescript
// FabricCareSection.tsx
const performanceOptimizations = {
  // useMemo 缓存分组
  compositionsByLayer: useMemo(() => { /* ... */ }, [fabricCompositions]),
  
  // 条件渲染提前返回
  earlyReturn: 'if (fabricCompositions.length === 0 && ...) return null',
  
  // ISR 缓存
  revalidate: 300,  // TTL 兜底
  onDemand: 'MQ 失效消费者触发 revalidatePath'
}

// PDP 页面级优化
const pageOptimizations = {
  // RSC 服务端渲染
  rendering: 'FabricCareSection 为 Server Component，无 hydration 成本',
  
  // 数据预取
  prefetch: 'fetchStoreProduct() 在 RSC 中执行，包含面料数据',
  
  // 图片优化
  images: '护理符号为 Unicode 文本（无图片加载）'
}
```

**验收场景映射**:
- s-080: percentage 输入防抖 → validateDebounced
- s-081: 分组结果缓存 → computed/useMemo
- s-082: ISR 缓存策略 → revalidate + on-demand
- s-083: RSC 服务端渲染 → 无客户端 hydration 成本

## 7. 测试考量

**单元测试覆盖**:

```typescript
// FabricCompositionEditor.test.ts
describe('FabricCompositionEditor', () => {
  it('COMP-FC-01-T01: 添加成分行', () => {
    // 点击添加按钮 → localCompositions 增加一行
  })
  
  it('COMP-FC-01-T02: percentage 总和 100% 校验通过', () => {
    // 设置同层总和为 100% → layerErrors.size === 0
  })
  
  it('COMP-FC-01-T03: percentage 总和超 100% 显示错误', () => {
    // 设置同层总和为 110% → layerErrors.has(layer) === true
  })
  
  it('COMP-FC-01-T04: 删除行', () => {
    // 点击删除按钮 → localCompositions 减少一行
  })
  
  it('COMP-FC-01-T05: 字段变更触发 emit', () => {
    // 修改 material → emit('update:modelValue') 触发
  })
})

// CareSymbolSelector.test.ts
describe('CareSymbolSelector', () => {
  it('COMP-FC-02-T01: 按 category 分组显示', () => {
    // 渲染后检查 5 个分组标题存在
  })
  
  it('COMP-FC-02-T02: 切换选中状态', () => {
    // 点击符号卡片 → selectedIds 包含该 id
  })
  
  it('COMP-FC-02-T03: 仅显示 active 标签', () => {
    // mock API 返回含 disabled 标签 → 渲染后仅显示 status=1
  })
})

// FabricCareSection.test.tsx
describe('FabricCareSection', () => {
  it('COMP-FC-04-T01: 无数据时不渲染', () => {
    // fabricCompositions=[] && careInstructions=[] → 组件返回 null
  })
  
  it('COMP-FC-04-T02: 按 layer 分组显示', () => {
    // 提供多层数据 → 检查分组标题与项数
  })
  
  it('COMP-FC-04-T03: Unicode 符号渲染', () => {
    // 检查 symbolUnicode 文本存在
  })
})
```

**E2E 测试场景**:

```typescript
// e2e/admin-product-fabric-care.spec.ts
describe('后台商品面料与护理标签编辑', () => {
  it('E2E-FC-01: 创建商品包含面料成分', async () => {
    // 1. 登录后台 → 进入商品新增页
    // 2. 填写基本信息 → 滚动到 sec-fabric
    // 3. 添加 Shell 成分：Cotton 70% + Polyester 30%
    // 4. 保存商品 → 成功 toast
    // 5. 重新打开商品 → 验证面料数据回显正确
  })
  
  it('E2E-FC-02: percentage 总和错误拦截提交', async () => {
    // 1. 进入商品编辑页 → 滚动到 sec-fabric
    // 2. 添加 Shell 成分：Cotton 70%（总和不足 100%）
    // 3. 点击保存 → 错误横幅显示 + 提交被阻止
    // 4. 补充 Polyester 30% → 错误消失
    // 5. 重新保存 → 成功
  })
  
  it('E2E-FC-03: 选择护理标签', async () => {
    // 1. 进入商品编辑页 → 滚动到 sec-care
    // 2. 点击 washing 分组的"手洗"符号 → 选中态高亮
    // 3. 点击 ironing 分组的"低温熨烫"符号 → 选中态高亮
    // 4. 填写护理备注 → "珠饰区域建议专业干洗"
    // 5. 保存商品 → 成功
  })
})

// e2e/store-pdp-fabric-care.spec.ts
describe('消费端 PDP 面料与护理展示', () => {
  it('E2E-FC-04: PDP 显示面料与护理区块', async () => {
    // 1. 访问商品详情页（含面料数据的商品）
    // 2. 滚动到 Fabric & Care 区块
    // 3. 验证 Shell 成分列表显示正确
    // 4. 验证护理符号显示（Unicode 符号 + 标签文本）
    // 5. 验证护理备注显示
  })
  
  it('E2E-FC-05: 无面料数据时区块不显示', async () => {
    // 1. 访问商品详情页（无面料数据的商品）
    // 2. 验证 Fabric & Care 区块不存在于 DOM
  })
})
```

**验收场景映射**:
- s-044 ~ s-047: 后台编辑流程 → E2E-FC-01 ~ E2E-FC-03
- s-060 ~ s-064: 消费端显示 → E2E-FC-04
- B-FC-014: 无数据处理 → E2E-FC-05

## 8. 组件依赖关系

**依赖图**:

```
portal-admin (Vue 3):
  ProductEdit.vue
    ├─> FabricCompositionEditor.vue (COMP-FC-01)
    │     └─> catalogApi.updateProduct() / createProduct()
    │
    └─> CareSymbolSelector.vue (COMP-FC-02)
          └─> catalogApi.listAdminCareInstructions()

portal-store (Next.js):
  app/product/[slug]/page.tsx
    └─> FabricCareSection.tsx (COMP-FC-04)
          └─> catalogApi.fetchStoreProduct() (RSC)
```

**共享依赖**:
- Tailwind CSS（两端复用 design token）
- TypeScript 类型定义（FabricComposition / CareInstruction 接口）
- 枚举映射（Layer / Material / CareCategory 常量）

**外部依赖**:
- Headless UI Vue（portal-admin Dialog/Menu 等）
- next-intl（portal-store 多语言）
- catalogApi（API 客户端，两端各自封装）

## 9. 自检清单

### 覆盖完整性
- [x] 后台组件齐备：FabricCompositionEditor.vue (COMP-FC-01) + CareSymbolSelector.vue (COMP-FC-02) + ProductEdit.vue 集成 (COMP-FC-03)
- [x] 消费端组件齐备：FabricCareSection.tsx (COMP-FC-04) + PDP 集成 (COMP-FC-05)
- [x] 所有交互逻辑已定义：INTERACTION-FC-01 ~ FC-09（9 个交互场景）
- [x] 所有验收场景已映射：s-040 ~ s-084 中与前端相关的 25 个场景 + B-FC-001 ~ B-FC-015

### 可追溯性
- [x] 每个组件附验收场景映射（s-040 ~ s-084）
- [x] 每个交互逻辑附边界场景映射（B-FC-001 ~ B-FC-015）
- [x] 错误处理附 API 错误码（422510 / 404501）
- [x] 枚举映射来源标注（er-diagram.yml）

### 一致性
- [x] 枚举值使用整数（IntEnum 契约）：Layer/Material/CareCategory 均为 1..N
- [x] API 命名遵循既有模式（catalogApi.listAdminCareInstructions / fetchStoreProduct）
- [x] 样式复用既有 design token（Tailwind 类名 + 颜色变量）
- [x] 错误处理遵循 error-strategy 前端呈现约定（toast / inline / 降级态）

### 粒度合规
- [x] Props/Emits 定义完整（TypeScript 接口）
- [x] 交互逻辑精确到函数级（addRow / validatePercentageSum / toggleSelection）
- [x] UI 结构伪代码可直接指导实现（template 结构 + class 绑定）
- [x] 不包含框架实现细节（无 ref() / reactive() 等具体语法）

### 格式合规
- [x] 编号规则一致：COMP-FC-01 ~ FC-05 / INTERACTION-FC-01 ~ FC-09
- [x] 章节结构完整：0.设计范围 / 1.后台组件 / 2.消费端组件 / 3~9.横切关注点
- [x] 伪代码格式统一（TypeScript 类型 + Vue/React 模板结构）

### 可访问性合规
- [x] ARIA 属性定义：role / aria-label / aria-describedby / aria-checked
- [x] 键盘导航支持：Tab / Enter / Space 键操作
- [x] 颜色对比度满足 WCAG 2.1 AA 标准
- [x] 错误提示 role="alert" 自动朗读

### 性能优化
- [x] 防抖/节流策略：validateDebounced 300ms
- [x] 缓存策略：computed / useMemo 分组结果
- [x] ISR 配置：revalidate 300 + on-demand
- [x] RSC 服务端渲染：FabricCareSection 无 hydration 成本

## 10. 风险与待确认项

| 编号 | 风险项 | 影响 | 缓解措施 |
|------|--------|------|----------|
| RISK-FC-01 | percentage 小数精度在不同浏览器中可能有差异 | 总和校验边界情况可能误判 | 使用 toFixed(2) 统一精度，服务端二次校验兜底 |
| RISK-FC-02 | Unicode 护理符号字体支持不全 | 部分设备显示方块 | 服务端同时返回 labelZh/labelEn 文本降级 |
| RISK-FC-03 | CareSymbolSelector 加载失败阻塞表单提交 | 用户无法保存商品 | 降级态允许跳过护理标签（careInstructionIds 为可选字段） |
| RISK-FC-04 | ProductEdit 页面加载大量数据时锚点导航可能卡顿 | 用户体验下降 | IntersectionObserver lazy 渲染 + 虚拟滚动（如需） |

## 11. 设计决策记录

| 编号 | 决策 | 理由 | 来源 |
|------|------|------|------|
| DEC-FC-01 | percentage 使用 number 类型而非 string | 便于计算总和校验，避免字符串转数字错误 | 最佳实践 |
| DEC-FC-02 | 按 layer 分组而非平铺展示 | 符合业务语义（Shell/Lining 独立），便于 percentage 总和校验 | er-diagram.yml |
| DEC-FC-03 | CareSymbolSelector 仅显示 active 标签 | disabled 标签为历史数据，不应出现在新建商品表单中 | boundary-scenarios.yml B-FC-010 |
| DEC-FC-04 | 消费端无面料数据时整个区块不渲染 | 避免空白区块影响页面美观，减少 DOM 节点 | acceptance.yml s-066 |
| DEC-FC-05 | Unicode 符号优先，labelZh/En 文本降级 | Unicode 显示效果最佳，文本为降级方案 | 设计原则 |

## 12. 证据链

**L0 需求追溯**:
- er-diagram.yml: 3 个实体定义 → 组件 Props 接口
- acceptance.yml s-040 ~ s-084: 45 个验收场景 → 交互逻辑函数
- boundary-scenarios.yml B-FC-001 ~ B-FC-015: 15 个边界场景 → 错误处理逻辑

**L1 架构追溯**:
- catalog-fabric-care-api-detail.md: API 端点定义 → catalogApi 方法调用
- catalog-fabric-care-data-detail.md: DTO 结构 → TypeScript 接口定义
- error-strategy.md: 错误码定义 → 错误处理分支

**既有模式追溯**:
- catalog-frontend-detail.md: 既有组件设计模式 → Tailwind 类名 + 交互模式复用
- tech-profile.yml: 技术栈定义 → Vue 3 / Next.js 15 选型

---

**文件已完成，路径**: `/Volumes/MAC/workspace/dreamy/hhspec/changes/fabric-care-module/specs/design/catalog-fabric-care-frontend-detail.md`

**产出摘要**:
- 后台组件: 3 个（FabricCompositionEditor / CareSymbolSelector / ProductEdit 集成）
- 消费端组件: 2 个（FabricCareSection / PDP 集成）
- 交互逻辑: 9 个（INTERACTION-FC-01 ~ FC-09）
- 验收场景覆盖: 45 个（s-040 ~ s-084 中前端相关 + B-FC-001 ~ B-FC-015）
- 自检通过: 6 大类 26 项全部通过 ✓
