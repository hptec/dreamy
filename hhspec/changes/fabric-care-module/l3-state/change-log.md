---
title: "Fabric Care Module - Frontend Implementation Change Log"
change: "fabric-care-module"
phase: "L3"
date: "2026-06-14"
---

# 变更清单

## 新增文件 (added)

### 后台组件 (portal-admin)

1. **frontend/portal-admin/src/components/FabricCompositionEditor.vue**
   - 功能：面料成分编辑器组件
   - 约束：COMP-FC-01, INTERACTION-FC-01~04
   - 行数：228

2. **frontend/portal-admin/src/components/CareSymbolSelector.vue**
   - 功能：护理标签选择器组件
   - 约束：COMP-FC-02, INTERACTION-FC-05~07
   - 行数：187

### 消费端组件 (portal-store)

3. **frontend/portal-store/src/components/FabricCareSection.tsx**
   - 功能：PDP 面料与护理标签展示区块
   - 约束：COMP-FC-04
   - 行数：126

## 修改文件 (modified)

### 后台 (portal-admin)

1. **frontend/portal-admin/src/views/ProductEdit.vue**
   - 修改内容：
     - 导入 FabricCompositionEditor 和 CareSymbolSelector 组件
     - 在 sections 数组中添加 'fabric' 和 'care' 锚点
     - 在 form 状态中添加 fabricCompositions、careInstructionIds、fabricCareNote 字段
     - 添加 fabricErrors 状态和 handleFabricErrors 回调
     - 在 save() 函数中添加 422510 错误处理
     - 在模板中插入 sec-fabric 和 sec-care 两个 section
   - 约束：COMP-FC-03, INTERACTION-FC-08~09
   - 修改行数：~60

2. **frontend/portal-admin/src/api/types.ts**
   - 修改内容：
     - 添加 Layer、Material、CareCategory、CareInstructionStatus 枚举
     - 添加 FabricComposition、CareInstruction、CareInstructionListResponse 接口
     - 扩展 AdminProductUpsert 接口，添加 fabricCompositions、careInstructionIds、fabricCareNote 字段
   - 约束：类型定义
   - 修改行数：63

3. **frontend/portal-admin/src/api/catalog.ts**
   - 修改内容：
     - 添加 listAdminCareInstructions() API 函数
   - 约束：INTERACTION-FC-05
   - 修改行数：8

## 删除文件 (deleted)

无

## 阻塞项 (blocked)

无

## 依赖关系

### 组件依赖

```
ProductEdit.vue
  ├─> FabricCompositionEditor.vue
  │     └─> catalogApi.updateProduct() / createProduct()
  │
  └─> CareSymbolSelector.vue
        └─> catalogApi.listAdminCareInstructions()
```

### API 依赖

- **catalogApi.listAdminCareInstructions()**：依赖后端接口 `/api/admin/care-instructions`
- **catalogApi.createProduct() / updateProduct()**：payload 中新增 fabricCompositions、careInstructionIds、fabricCareNote 字段

### 类型依赖

- FabricComposition 接口
- CareInstruction 接口
- Layer / Material / CareCategory IntEnum 枚举

## 测试覆盖

### 单元测试（待实现）

- FabricCompositionEditor.vue:
  - COMP-FC-01-T01: 添加成分行
  - COMP-FC-01-T02: percentage 总和 100% 校验通过
  - COMP-FC-01-T03: percentage 总和超 100% 显示错误
  - COMP-FC-01-T04: 删除行
  - COMP-FC-01-T05: 字段变更触发 emit

- CareSymbolSelector.vue:
  - COMP-FC-02-T01: 按 category 分组显示
  - COMP-FC-02-T02: 切换选中状态
  - COMP-FC-02-T03: 仅显示 active 标签

- FabricCareSection.tsx:
  - COMP-FC-04-T01: 无数据时不渲染
  - COMP-FC-04-T02: 按 layer 分组显示
  - COMP-FC-04-T03: Unicode 符号渲染

### E2E 测试（待实现）

- E2E-FC-01: 后台创建商品包含面料成分
- E2E-FC-02: percentage 总和错误拦截提交
- E2E-FC-03: 选择护理标签
- E2E-FC-04: PDP 显示面料与护理区块
- E2E-FC-05: 无面料数据时区块不显示

## 构建与运行

### 后台 (portal-admin)

```bash
cd frontend/portal-admin
pnpm install
pnpm dev  # 启动开发服务器 (http://localhost:5174)
```

### 消费端 (portal-store)

```bash
cd frontend/portal-store
pnpm install
pnpm dev  # 启动开发服务器 (http://localhost:5173)
```

## 配置变更

无新增环境变量或配置项。

## 数据库变更

无。前端仅消费后端 API，数据库变更由后端负责。

## 迁移指南

### 现有商品数据

- 现有商品的 fabricCompositions、careInstructionIds、fabricCareNote 字段默认为空
- 编辑现有商品时可选择性填写面料和护理标签信息
- 无需强制填写，保存时允许为空

### 兼容性

- 后台：兼容既有 ProductEdit 表单流程，新增字段不影响既有功能
- 消费端：FabricCareSection 无数据时自动隐藏，不影响既有 PDP 布局

## 性能影响

- 后台：新增两个组件，不影响页面首屏加载（懒加载）
- 消费端：FabricCareSection 为 React Server Component，无客户端 hydration 成本
- API 调用：listAdminCareInstructions() 仅在编辑商品时调用一次，响应体预期 < 10KB

## 安全考虑

- 输入校验：percentage 范围 0-100，客户端预校验 + 服务端二次校验
- XSS 防护：symbolUnicode 和文本内容由 Vue/React 框架自动转义
- CSRF：复用既有 JWT token 机制

## 浏览器兼容性

- 后台：支持 Chrome/Edge/Firefox/Safari 最新两个主版本
- 消费端：支持 Chrome/Edge/Firefox/Safari 最新两个主版本 + iOS Safari 13+
- Unicode 符号：依赖系统字体，部分老旧设备可能显示为方块（已提供文本降级）

## 已知问题

无

## 后续优化方向

1. **防抖优化**：percentage 输入添加 300ms 防抖，减少校验频率
2. **缓存优化**：护理标签字典结果缓存到 localStorage，减少重复请求
3. **虚拟滚动**：若单层面料成分行数超过 50（罕见场景），可引入虚拟滚动

---

**变更摘要**:
- 新增文件：3 个
- 修改文件：3 个
- 删除文件：0 个
- 总行数变更：+612 行
