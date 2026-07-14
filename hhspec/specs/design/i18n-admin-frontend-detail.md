# i18n Portal-Admin 前端详细设计

> 历史设计：`/system/glossary` 页面已退役；当前仅保留网关管理和 AI 翻译弹窗，详见
> `i18n-runtime-status.md`。

## 元信息

- 变更：i18n-complete-with-ai-assist
- 生成时间：2026-06-16T20:30:00Z
- 技术栈：Vue3 + Pinia + Element Plus（后台保持中文，决策8）
- 覆盖：2新页面 + 1弹窗组件 + 1页面增量

---

## 1. 组件树

```
portal-admin/
├── views/system/
│   ├── GatewayConfigList.vue       (PAGE-001 网关配置列表页)
│   │   ├── GatewayConfigTable.vue  (COMP-001 配置表格)
│   │   ├── GatewayConfigForm.vue   (COMP-002 配置表单弹窗)
│   │   └── TestConnectionBtn.vue   (COMP-003 测试连接按钮)
│   └── GlossaryList.vue            (PAGE-002 术语表页)
│       ├── GlossaryTable.vue       (COMP-004 术语表格)
│       └── GlossaryForm.vue        (COMP-005 术语表单弹窗)
├── components/ai/
│   └── AiTranslateModal.vue        (COMP-006 AI翻译弹窗，11处嵌入)
└── views/catalog/
    └── ProductEdit.vue (增量)      (PAGE-003 商品编辑增加designerNote)
```

---

## 2. 页面路由 (PAGE-NNN)

| 路由ID | path | 组件 | 权限 | 说明 |
|--------|------|------|------|------|
| PAGE-001 | /system/gateways | GatewayConfigList | /system/gateways | 网关配置管理 |
| PAGE-002 | /system/glossary | GlossaryList | /system/glossary | 术语表管理 |
| PAGE-003 | /catalog/products/:id/edit | ProductEdit | /catalog/products | 商品编辑(增量) |

路由守卫：无权限跳转/403（EDGE-008/022）

---

## 3. 状态管理 (STORE-NNN)

### STORE-001: useGatewayStore (Pinia)

```typescript
state:
  configs: GatewayConfig[]      // 配置列表
  currentConfig: GatewayConfig  // 当前编辑配置
  modelList: Model[]            // 当前配置的可用模型
  loading: boolean
  total: number

actions:
  fetchConfigs(params)          // GET /api/admin/gateway/configs
  fetchConfig(id)               // GET /configs/{id}
  createConfig(data)            // POST /configs
  updateConfig(id, data)        // PUT /configs/{id}
  deleteConfig(id)              // DELETE /configs/{id}
  syncModels(id)                // POST /configs/{id}/sync-models
  testConnection(data)          // 测试连接(不落库)
```

### STORE-002: useGlossaryStore (Pinia)

```typescript
state:
  terms: GlossaryTerm[]
  loading: boolean
  total: number

actions:
  fetchTerms(params)
  createTerm(data)
  updateTerm(id, data)
  deleteTerm(id)
```

### STORE-003: useAiTranslateStore (Pinia)

```typescript
state:
  availableModels: Model[]      // 来自启用网关的model_list
  defaultModel: string
  translating: boolean

actions:
  fetchAvailableModels()        // 读取启用AI网关的模型列表
  translate(request)            // POST /api/admin/ai/translate
```

---

## 4. 表单交互 (FORM-NNN)

### FORM-001: 网关配置表单 (GatewayConfigForm)

| 字段 | 组件 | 校验 | 说明 |
|------|------|------|------|
| gateway_type | el-select | 必填 | AI(1)/物流(2)/支付(3) |
| name | el-input | 必填,≤64 | 配置名称 |
| protocol | el-select | 必填 | openai(1) |
| base_url | el-input | 必填,URL格式 | 网关地址 |
| api_key | el-input(password) | 必填 | 编辑时显示掩码sk-****1234 |
| default_model | el-select | 可选 | 来自model_list下拉 |
| model_refresh_strategy | el-radio | - | 手动(1)/定时(2) |
| model_refresh_interval_min | el-input-number | strategy=2时必填 | 分钟数 |
| enabled | el-switch | - | 启用开关 |
| version | hidden | 更新必填 | 从详情响应回传，用于原子乐观锁 |

**交互逻辑**：
1. 保存成功后若gateway_type=AI，自动展示拉取的模型列表（EDGE-014：拉取失败提示"模型列表获取失败，可手动刷新"）
2. API Key编辑：聚焦清空掩码，失焦未修改保持原值（提交掩码时后端保持原密文）
3. 测试连接按钮：调用后端验证URL+Key，展示连通状态（EDGE-023：DNS失败/401/超时具体提示）
4. 编辑保存始终回传最新version；409202提示重新打开配置，避免覆盖并发模型同步或其他编辑。

### FORM-002: 术语表单 (GlossaryForm)

| 字段 | 组件 | 校验 |
|------|------|------|
| term_en | el-input | 必填,≤128 |
| term_es | el-input | 可选,≤128 |
| term_fr | el-input | 可选,≤128 |
| category | el-select | 可选(廓形/领型/面料/工艺) |
| enabled | el-switch | - |

---

## 5. AI翻译弹窗组件 (COMP-006: AiTranslateModal)

**Props**：
```typescript
{
  sourceText: string        // EN主字段内容
  sourceLang: 'en'          // 固定源语言
  targetLang: 'es' | 'fr'   // 目标语言
  bizType: string           // product/category/tag/banner...
  bizRef: string            // 业务标识(如product_id)
  fieldName: string         // 字段名(name/description...)
}
```

**Emits**：
```typescript
{
  confirm: (translatedText: string) => void  // 译文回填
}
```

**UI结构**：
```
┌─ AI翻译弹窗 ──────────────────┐
│ 源文本(EN, 只读)：             │
│ [显示sourceText]              │
│                               │
│ 系统提示词(只读)：             │
│ [固定婚纱领域prompt展示]       │
│                               │
│ 模型：[下拉选择，默认全局模型]  │
│                               │
│ 自定义要求(可选,≤500)：        │
│ [textarea, maxlength=500]     │
│                               │
│ [取消]  [翻译]                │
│                               │
│ 翻译结果：                     │
│ [textarea, 可编辑]            │
│                               │
│ [确认回填]                    │
└───────────────────────────────┘
```

**交互逻辑**：
1. 打开前校验：sourceText为空→提示"请先填写EN主字段内容"不打开（EDGE-002）
2. 网关未配置：translate返回400301→提示"尚未配置AI网关"（EDGE-001）
3. 翻译中：按钮loading禁用（EDGE-011防重复）
4. 翻译成功：结果回填textarea，用户可二次编辑
5. 翻译失败：toast具体错误，弹窗保持打开，不阻塞（EDGE-015/016/017，决策10）
6. 确认回填：emit confirm(译文)，关闭弹窗

**11处嵌入点**（决策对应FUNC-011）：
ProductEdit的name/sellingPoints/description/seoTitle/seoDescription/designerNote各字段ES/FR tab + Category/Tag/Banner/Blog等编辑页

---

## 6. 商品编辑增量 (PAGE-003: ProductEdit)

**增量内容**：「内容详情」ES/FR tab增加designerNote字段

```
ProductEdit「内容详情」tab:
├── EN tab: name/sellingPoints/description/seoTitle/seoDescription/designerNote
├── ES tab: 同上字段 + 每字段AI翻译按钮 → AiTranslateModal
└── FR tab: 同上字段 + 每字段AI翻译按钮
```

designerNote字段：el-input(type=textarea)，三语tab各一份，ES/FR可空（消费端pick回退EN）

---

## 7. 需求追溯

- FUNC-004: PAGE-001网关列表
- FUNC-005: FORM-001配置+自动模型发现
- FUNC-006: FORM-001刷新策略
- FUNC-007: FORM-001 API Key掩码
- FUNC-008: COMP-006翻译按钮+弹窗
- FUNC-009: COMP-006结果回填
- FUNC-010: COMP-006系统prompt+自定义要求
- FUNC-011: COMP-006 11处嵌入
- FUNC-012: 调用记录(可选页面)
- FUNC-022: PAGE-002术语表

---

**设计完成标记**：✅ Portal-Admin前端详设已完成
