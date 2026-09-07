# selling_points 功能实现 - 前端修改清单

## 已完成的后端修改

### 1. 数据库层 ✅
- 创建迁移文件：`db-migration-selling-points.sql`
- product 表：删除 `subtitle`，添加 `selling_points JSON`
- product_translation 表：删除 `subtitle`，添加 `selling_points JSON`

### 2. 实体层 ✅
- Product.java: 删除 subtitle，添加 `List<String> sellingPoints`
- ProductTranslation.java: 同上
- 常量类已更新

### 3. DTO 层 ✅
- StoreProductCard: 删除 subtitle，添加 `List<String> sellingPoints`
- StoreProductDetail: 同上
- AdminProductUpsert: 同上
- AdminProductDetail: 同上
- ProductTranslationDto: 同上
- TradingCatalogSnapshotPort.ProductBrief: 删除 subtitle

### 4. 服务层 ✅
- ProductCardAssembler: 更新 toCard 方法，添加 pickList 方法
- AdminProductService: applyUpsert 和 translation 映射已更新
- TradingPortConfig: ProductBrief 构造已更新

---

## 待完成的前端修改

### 消费端 (portal-store)

#### 1. 类型定义 ✅
- `lib/api/store-types.ts`: StoreProductCard 和 ProductBrief 已移除 subtitle

#### 2. 商品详情页 - 添加 selling_points 渲染
文件：`app/product/[slug]/page.tsx` 或 `components/product/product-buy-box.tsx`

**当前代码**（硬编码三个卖点）：
```tsx
<div className="mt-6 space-y-2.5 border-t border-line pt-5 text-sm text-ink-soft">
  <p className="flex items-center gap-2">
    <Truck className="h-4 w-4 text-gold" />
    Free worldwide shipping over $200
  </p>
  <p className="flex items-center gap-2">
    <Sparkles className="h-4 w-4 text-gold" />
    Order fabric swatches before you commit
  </p>
  <p className="flex items-center gap-2">
    <Ruler className="h-4 w-4 text-gold" />
    Free custom sizing available
  </p>
</div>
```

**修改为**（动态渲染）：
```tsx
{product.sellingPoints && product.sellingPoints.length > 0 && (
  <div className="mt-6 space-y-2.5 border-t border-line pt-5 text-sm text-ink-soft">
    {product.sellingPoints.map((point, i) => (
      <p key={i} className="flex items-center gap-2">
        {getIconForSellingPoint(point)}
        {point}
      </p>
    ))}
  </div>
)}
```

需要添加图标映射函数：
```tsx
function getIconForSellingPoint(point: string) {
  const lower = point.toLowerCase()
  if (lower.includes('shipping') || lower.includes('delivery')) 
    return <Truck className="h-4 w-4 text-gold" />
  if (lower.includes('custom') || lower.includes('sizing')) 
    return <Ruler className="h-4 w-4 text-gold" />
  if (lower.includes('swatch') || lower.includes('sample')) 
    return <Sparkles className="h-4 w-4 text-gold" />
  // 默认图标
  return <Check className="h-4 w-4 text-gold" />
}
```

---

### 后台管理端 (portal-admin)

#### 1. ProductEdit.vue - 完整替换 subtitle 为 selling_points

**需要修改的位置**：

1. **form 数据结构**（第93行）：
```typescript
// 删除
subtitle: '',

// 添加
sellingPoints: [] as string[],
```

2. **Trans 类型定义**（第121行）：
```typescript
// 删除
type Trans = { name: string; subtitle: string; description: string; seoTitle: string; seoDescription: string }

// 替换为
type Trans = { name: string; description: string; sellingPoints: string[]; seoTitle: string; seoDescription: string }
```

3. **emptyTrans 函数**（第122行）：
```typescript
const emptyTrans = (): Trans => ({ 
  name: '', 
  description: '', 
  sellingPoints: [],
  seoTitle: '', 
  seoDescription: '' 
})
```

4. **loadProduct 方法 - 回读数据**（第384行、第420行）：
```typescript
// 删除
subtitle: p.subtitle || '',

// 替换为
sellingPoints: [...(p.sellingPoints || [])],

// trans 回读（第420行）
const toTrans = (l: 'es' | 'fr'): Trans => {
  const t = byLocale(l)
  return {
    name: t?.name || '',
    description: t?.description || '',
    sellingPoints: [...(t?.sellingPoints || [])],
    seoTitle: t?.seoTitle || '',
    seoDescription: t?.seoDescription || '',
  }
}
```

5. **buildTranslations 方法**（第472行）：
```typescript
rows.push({
  locale: l,
  name: t.name.trim() || null,
  description: t.description.trim() || null,
  sellingPoints: t.sellingPoints.filter(p => p.trim()).length > 0 
    ? t.sellingPoints.filter(p => p.trim()) 
    : null,
  seoTitle: t.seoTitle.trim() || null,
  seoDescription: t.seoDescription.trim() || null,
})
```

6. **buildPayload 方法**（第499行）：
```typescript
return {
  name: form.value.name.trim(),
  slug: form.value.slug.trim(),
  sellingPoints: form.value.sellingPoints.filter(p => p.trim()).length > 0
    ? form.value.sellingPoints.filter(p => p.trim())
    : null,
  categoryId: form.value.categoryId,
  // ... 其他字段
}
```

7. **UI 模板 - 基础信息区块**（第664行，替换 subtitle 输入框）：
```vue
<!-- 删除 subtitle 输入 -->
<div class="field-row">
  <label class="field-label">副标题</label>
  <input v-model="form.subtitle" class="field" placeholder="一句话卖点，显示在 PDP 价格下方" />
</div>

<!-- 替换为 selling_points 列表编辑器 -->
<div class="field-row">
  <label class="field-label">商品卖点（最多5个）</label>
  <div class="space-y-2">
    <div v-for="(point, i) in form.sellingPoints" :key="i" class="flex items-center gap-2">
      <input 
        v-model="form.sellingPoints[i]" 
        class="field flex-1" 
        :placeholder="`卖点 ${i + 1}`"
        maxlength="100"
      />
      <button 
        type="button"
        @click="form.sellingPoints.splice(i, 1)" 
        class="btn-sm btn-outline-danger"
      >
        删除
      </button>
    </div>
    <button
      v-if="form.sellingPoints.length < 5"
      type="button"
      @click="form.sellingPoints.push('')"
      class="btn-sm btn-outline"
    >
      + 添加卖点
    </button>
  </div>
  <p class="field-hint">
    示例：Free Custom Sizing / 14-Day Production Time / Free Worldwide Shipping
  </p>
</div>
```

8. **多语言 tab - 翻译卖点**（第1097行）：
```vue
<!-- 删除 -->
<div class="field-row">
  <label class="field-label">副标题</label>
  <input v-model="trans[l].subtitle" class="field" :placeholder="`副标题（${l.toUpperCase()}）`" />
</div>

<!-- 替换为 -->
<div class="field-row">
  <label class="field-label">商品卖点</label>
  <div class="space-y-2">
    <div v-for="(point, i) in trans[l].sellingPoints" :key="i" class="flex items-center gap-2">
      <input 
        v-model="trans[l].sellingPoints[i]" 
        class="field flex-1" 
        :placeholder="`卖点 ${i + 1}（${l.toUpperCase()}）`"
        maxlength="100"
      />
      <button 
        type="button"
        @click="trans[l].sellingPoints.splice(i, 1)" 
        class="btn-sm btn-outline-danger"
      >
        删除
      </button>
    </div>
    <button
      v-if="trans[l].sellingPoints.length < 5"
      type="button"
      @click="trans[l].sellingPoints.push('')"
      class="btn-sm btn-outline"
    >
      + 添加卖点
    </button>
  </div>
</div>
```

---

## 实施步骤

### Step 1: 执行数据库迁移
```bash
cd backend
mysql -u root -p dreamy < src/main/resources/db-migration-selling-points.sql
```

### Step 2: 更新种子数据（可选）
修改 `CatalogSeedInitializer.java`，为种子商品添加 selling_points：
```java
product.setSellingPoints(List.of(
    "Free Custom Sizing",
    "14-Day Production Time", 
    "Free Worldwide Shipping"
));
```

### Step 3: 重启后端验证
```bash
./gradlew bootRun
```

### Step 4: 更新前端代码
按照上述清单逐一修改前端文件

### Step 5: 测试验证
1. 后台创建/编辑商品，添加卖点
2. 消费端 PDP 查看卖点展示
3. 多语言切换测试

---

## 注意事项

1. **Banner 的 subtitle 保留不变**：Banner 组件仍然需要 subtitle 字段用于展示副标题
2. **数据迁移**：现有商品的 subtitle 数据会丢失，建议先导出重要商品的 subtitle 手动迁移到 selling_points
3. **后向兼容**：前端应处理 sellingPoints 为 null/undefined 的情况
4. **图标映射**：selling_points 的图标通过关键词匹配，可根据实际需求扩展
5. **最大数量限制**：后台表单限制最多 5 个卖点，但数据库层面不强制（方便未来扩展）

---

## API 契约变更

### 消费端 API
- `GET /api/store/products` → `StoreProductCard.sellingPoints: string[]`
- `GET /api/store/products/:id` → `StoreProductDetail.sellingPoints: string[]`

### 后台 API
- `POST /api/admin/products` → body 中 `subtitle` 改为 `sellingPoints: string[]`
- `PUT /api/admin/products/:id` → 同上
- `GET /api/admin/products/:id` → 响应中 `subtitle` 改为 `sellingPoints: string[]`

### 翻译 API
- `ProductTranslation.subtitle` → `ProductTranslation.sellingPoints: string[]`
