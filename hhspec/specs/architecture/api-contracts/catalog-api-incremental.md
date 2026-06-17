# catalog-api.openapi.yml 增量变更

## 变更说明
本文档定义 i18n-complete-with-ai-assist 变更对 catalog-api.openapi.yml 的增量修改。

## 设计依据
- decision.md 决策 12（designerNote 纳入数据级翻译）
- er-diagram.yml：ProductTranslation.designer_note 新增字段
- acceptance.yml：FUNC-017（designerNote 三语字段 + 消费端 pick 回退）

## 变更内容

### 1. ProductTranslation schema 新增字段

在 `components/schemas/ProductTranslation` 中新增 `designer_note` 字段：

```yaml
ProductTranslation:
  type: object
  required: [locale, name, selling_points, description]
  properties:
    locale:
      type: string
      enum: [es, fr]
      description: 目标语言（仅 ES/FR，EN 为主字段）
    name:
      type: string
      maxLength: 255
      description: 商品名称译文
    selling_points:
      type: array
      items:
        type: string
        maxLength: 128
      description: 卖点译文（数组，对应 EN selling_points 顺序）
    description:
      type: string
      description: 描述译文（富文本 HTML）
    seo_title:
      type: string
      maxLength: 120
      description: SEO 标题译文
    seo_description:
      type: string
      maxLength: 300
      description: SEO 描述译文
    designer_note:  # 新增字段
      type: string
      description: |
        设计师备注译文（决策 12）。
        消费端展示在 PDP Description 折叠区斜体区域，缺译文时 pick() 回退 EN。
        后台在「内容详情」三语 tab 中编辑（与 name/description 同级）。
      example: "Esta pieza combina tradición y modernidad con apliques de encaje hechos a mano."
```

### 2. AdminProductDetail schema 响应增加 designer_note

在 `components/schemas/AdminProductDetail` 的 `translations` 数组项中包含新增的 `designer_note` 字段（使用 `ProductTranslation` schema，自动包含）。

### 3. StoreProductDetail schema 响应增加 designer_note

在 `components/schemas/StoreProductDetail` 中新增 `designer_note` 字段：

```yaml
StoreProductDetail:
  allOf:
    - $ref: '#/components/schemas/ProductBase'
    - type: object
      required: [skus, images, categories, tags]
      properties:
        # ... 现有字段 ...
        designer_note:  # 新增字段
          type: string
          description: |
            设计师备注（决策 12）。
            按 locale 应用译文（ES/FR），缺译文时回退 EN 主字段。
            展示在 PDP Description 折叠区斜体区域。
          example: "This piece combines tradition and modernity with handcrafted lace appliqués."
```

### 4. AdminProductUpsert schema 请求体增加 designer_note

在 `components/schemas/AdminProductUpsert` 中新增 `designer_note` 字段：

```yaml
AdminProductUpsert:
  type: object
  required: [name, slug, status, category_ids]
  properties:
    # ... 现有字段 ...
    designer_note:  # 新增字段
      type: string
      maxLength: 2000
      description: |
        设计师备注（EN 主字段，决策 12）。
        在后台「内容详情」编辑，三语独立输入（ES/FR 在 translations 数组中）。
        消费端展示在 PDP Description 折叠区。
      example: "This piece combines tradition and modernity with handcrafted lace appliqués."
    translations:
      type: array
      items:
        $ref: '#/components/schemas/ProductTranslation'
      description: |
        译文数组（ES/FR）。
        现在包含 designer_note 译文字段（决策 12）。
```

## 实现注意事项

### 后端实现
1. Product 实体增加 `designerNote` 字段（String, @Column(length=2000)）
2. ProductTranslation 实体增加 `designerNote` 字段（String, @Column(length=2000)）
3. AdminProductController 的 upsert 接口保存 Product.designerNote + ProductTranslation.designerNote
4. StoreProductController 的 getProductBySlug 接口按 locale 应用 ProductTranslation.designerNote，缺译文时回退 EN

### 前端实现
- portal-admin：ProductEdit「内容详情」表单增加 designerNote 字段（EN tab 主字段 + ES/FR tab 译文字段 + AI 翻译按钮）
- portal-store：PDP Description 折叠区增加 designerNote 展示区（斜体样式，区分正文）

## 数据库迁移
```sql
-- Product 表增加 designer_note 列
ALTER TABLE products 
ADD COLUMN designer_note TEXT COMMENT '设计师备注（EN主字段）';

-- ProductTranslation 表增加 designer_note 列
ALTER TABLE product_translations 
ADD COLUMN designer_note TEXT COMMENT '设计师备注译文（ES/FR）';
```

## API 示例

### 后台保存商品（含 designer_note）
```http
PUT /api/admin/products/123
Authorization: Bearer <admin_jwt>
Content-Type: application/json

{
  "name": "Aurelia",
  "designer_note": "This piece combines tradition and modernity with handcrafted lace appliqués.",
  "translations": [
    {
      "locale": "es",
      "name": "Aurelia",
      "designer_note": "Esta pieza combina tradición y modernidad con apliques de encaje hechos a mano."
    },
    {
      "locale": "fr",
      "name": "Aurelia",
      "designer_note": "Cette pièce combine tradition et modernité avec des appliqués en dentelle faits main."
    }
  ]
}
```

### 消费端获取商品（含 designer_note）
```http
GET /api/store/products/aurelia?locale=es
```

响应（payload 部分）：
```json
{
  "id": 123,
  "slug": "aurelia",
  "name": "Aurelia",
  "designer_note": "Esta pieza combina tradición y modernidad con apliques de encaje hechos a mano.",
  "description": "...",
  "skus": [...],
  "images": [...]
}
```

若 ES 译文缺失，designer_note 回退 EN：
```json
{
  "designer_note": "This piece combines tradition and modernity with handcrafted lace appliqués."
}
```
