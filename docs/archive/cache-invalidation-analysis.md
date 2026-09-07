# 商品管理缓存失效机制分析报告

## 当前实现概况

### 缓存族定义（Family）

| 缓存族 | Key前缀 | TTL | 说明 |
|--------|---------|-----|------|
| PRODUCTS | `catalog:products:` | 300s | 商品列表（带筛选条件） |
| PRODUCT | `catalog:product:` | 300s | 商品详情（按slug） |
| SEARCH | `catalog:search:` | 60s | 全文搜索结果 |
| RECO | `catalog:reco:` | 300s | 推荐商品（新品/爆款） |
| CATEGORIES | `catalog:categories:` | 600s | 分类树 |
| TAGS | `catalog:tags:` | 600s | 标签列表 |

### 商品管理操作与当前缓存失效

| 操作 | API | 当前缓存失效策略 | 是否完整 |
|------|-----|------------------|----------|
| **创建商品** | POST /api/admin/products | ✅ 失效 PRODUCTS, RECO, CATEGORIES, TAGS | ✅ 完整 |
| **编辑商品** | PUT /api/admin/products/{id} | ✅ 失效旧/新slug详情 + PRODUCTS + RECO | ✅ 完整 |
| **删除商品** | DELETE /api/admin/products/{id} | ✅ 失效 PRODUCTS, RECO, CATEGORIES, TAGS | ✅ 完整 |
| **上架/下架** | PATCH /api/admin/products/{id}/status | ✅ 失效slug详情 + PRODUCTS + RECO + CATEGORIES + TAGS | ✅ 完整 |
| **修改标记** | PATCH /api/admin/products/{id}/flags | ✅ 失效 RECO + PRODUCTS | ✅ 完整 |
| **批量上架** | POST /api/admin/products/batch (publish) | ❌ **无缓存失效** | ❌ **缺失** |
| **批量下架** | POST /api/admin/products/batch (unpublish) | ❌ **无缓存失效** | ❌ **缺失** |
| **批量推荐** | POST /api/admin/products/batch (recommend) | ❌ **无缓存失效** | ❌ **缺失** |
| **批量取消推荐** | POST /api/admin/products/batch (unrecommend) | ❌ **无缓存失效** | ❌ **缺失** |
| **批量删除** | POST /api/admin/products/batch (delete) | ❌ **无缓存失效** | ❌ **缺失** |

## 问题诊断

### 🔴 核心问题

**批量操作没有缓存失效机制**！

`AdminProductBatchService.execute()` 方法循环调用单品操作：
- `adminProductService.toggleStatus()` - 有缓存失效
- `adminProductService.patchFlags()` - 有缓存失效  
- `adminProductService.delete()` - 有缓存失效

但是批量服务本身**不在事务中**（TX-CAT-01：每行独立事务），单品 service 的 `afterCommit.run()` 在各自行级事务提交后执行，导致：

1. **缓存失效碎片化**：200个商品会触发200次 `invalidateFamily(PRODUCTS)`
2. **性能问题**：每个商品独立失效所有缓存族
3. **一致性风险**：用户在批量操作过程中可能看到部分更新的数据

### 用户体验影响场景

**场景1：批量上架100个商品**
- 用户操作：选中100个draft商品 → 批量上架
- 预期：前台立即能看到这100个商品
- **实际**：缓存失效200次重复，性能差；理论上能看到但效率低

**场景2：批量下架促销商品**
- 用户操作：活动结束后批量下架50个商品
- 预期：前台立即看不到这些商品
- **实际**：缓存失效100次重复，性能差；理论上能生效但效率低

**场景3：批量设置推荐**
- 用户操作：批量设置10个新品为推荐
- 预期：首页"推荐商品"模块立即更新
- **实际**：缓存失效20次重复，性能差

## 解决方案

### 方案：收集slug + 批量操作后统一失效（推荐）

在批量操作前收集所有商品slug，操作成功后统一失效：

**优点**：
- ✅ 完整覆盖所有场景（包括DELETE）
- ✅ 避免重复失效，性能最优
- ✅ 不修改单品API
- ✅ 明确的失效范围
- ✅ 代码清晰易维护

## 实施计划

1. 修改 `AdminProductBatchService.execute()`
2. 添加批量缓存失效逻辑
3. 添加单元测试验证
4. 更新缓存失效文档

## 完整缓存失效矩阵

| 操作类型 | PRODUCT详情 | PRODUCTS列表 | SEARCH | RECO | CATEGORIES | TAGS |
|---------|------------|-------------|--------|------|------------|------|
| 创建商品 | - | ✅ | TTL | ✅ | ✅ | ✅ |
| 编辑商品 | ✅ | ✅ | TTL | ✅ | - | - |
| 删除商品 | - | ✅ | TTL | ✅ | ✅ | ✅ |
| 上架 | ✅ | ✅ | TTL | ✅ | ✅ | ✅ |
| 下架 | ✅ | ✅ | TTL | ✅ | ✅ | ✅ |
| 修改标记 | - | ✅ | TTL | ✅ | - | - |

**说明**：
- ✅ = 主动失效
- TTL = 依赖TTL自然过期（SEARCH族60s）
- `-` = 不影响
