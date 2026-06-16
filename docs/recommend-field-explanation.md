# 商品 recommend 字段说明文档

## 字段用途

`recommend`（推荐标记）是一个**内部标记字段**，用于推荐算法的**冷启动回退机制**。

## 具体场景

### 1. Best Sellers（爆款商品）推荐位

**API 端点**：
```
GET /api/store/products/recommendations?block=best_sellers&limit=8
```

**推荐逻辑**：

#### 正常情况（有销量数据）
```sql
SELECT * FROM product 
WHERE status = 2 (PUBLISHED)
  AND sales_30d > 0
ORDER BY sales_30d DESC
LIMIT 8
```
→ 按最近30天销量排序，返回真实爆款商品

#### 冷启动回退（无销量数据）
当系统刚上线或所有商品 `sales_30d = 0` 时：
```sql
SELECT * FROM product 
WHERE status = 2 (PUBLISHED)
  AND recommend = 1
ORDER BY sort ASC
LIMIT 8
```
→ 按 `recommend` 标记 + `sort` 排序，返回运营手工推荐的商品

**代码实现**：
```java
case BEST_SELLERS -> {
    // sales_30d DESC；全 0 冷启动 → 回退 recommend=true ORDER BY sort（决策 29）
    List<Product> bySales = productRepository.listRecoBestSellers(limit);
    yield bySales.isEmpty() ? productRepository.listRecoRecommendFallback(limit) : bySales;
}
```

## 显示位置

`recommend` 字段影响的是 **Best Sellers（爆款商品）推荐位**，通常显示在：

1. **首页**：
   - "Best Sellers" 区块
   - "热门商品" 区块
   - "爆款推荐" 区块

2. **分类页**：
   - "本类别热销商品" 区块

3. **详情页**：
   - "热门商品" 推荐区

## 其他推荐位（不使用 recommend）

系统支持 5 种推荐位，只有 **Best Sellers** 使用 `recommend` 作为回退：

| 推荐位 | Key | 算法规则 | 是否使用 recommend |
|--------|-----|----------|-------------------|
| 新品上架 | `new_arrivals` | 按创建时间倒序 | ❌ 否 |
| **爆款商品** | **`best_sellers`** | **按销量倒序，冷启动回退 recommend** | **✅ 是（仅冷启动）** |
| 按颜色购物 | `shop_by_color` | 按标签筛选 | ❌ 否 |
| 你可能喜欢 | `you_may_also_like` | 同分类±30%价格段 | ❌ 否 |
| 搭配推荐 | `complete_the_look` | 同根分类下其他叶子分类 | ❌ 否 |

## 字段对比

| 字段 | 用途 | 前台显示 | 使用场景 |
|------|------|----------|----------|
| `is_new` | 新品标记 | ✅ 显示徽章 | 商品列表、详情页显示"NEW"标签 |
| `is_best` | 爆款标记 | ✅ 显示徽章 | 商品列表、详情页显示"HOT"标签 |
| `recommend` | 推荐标记 | ❌ 不显示 | 仅用于 Best Sellers 冷启动回退算法 |

## 使用建议

### 何时设置 recommend = true？

**冷启动阶段**（系统刚上线，无真实销量数据）：
1. 精选 8-12 件高质量商品
2. 设置 `recommend = true`
3. 通过 `sort` 字段控制展示顺序（sort 越小越靠前）

**有销量数据后**：
- Best Sellers 会自动按真实销量排序
- `recommend` 字段不再生效（仅作为回退机制）
- 可以保留或清除 `recommend` 标记，不影响正常运营

### 批量操作

**后台操作路径**：
商品管理 → 选中多个商品 → 批量操作 → 推荐/取消推荐

**API**：
```bash
POST /api/admin/products/batch
{
  "action": "recommend",
  "ids": [1, 2, 3, 4, 5]
}
```

**缓存失效**：
- ✅ 已修复：批量操作后自动失效 RECO 和 PRODUCTS 缓存
- ✅ 前台立即生效

## 验证方式

### 1. 验证冷启动回退

```bash
# 1. 清空所有商品销量
UPDATE product SET sales_30d = 0;

# 2. 设置推荐商品
UPDATE product SET recommend = 1, sort = 10 WHERE id = 1;
UPDATE product SET recommend = 1, sort = 20 WHERE id = 2;

# 3. 清空缓存
docker exec -i pd-redis redis-cli FLUSHDB

# 4. 调用 Best Sellers API
curl "http://localhost:8080/api/store/products/recommendations?block=best_sellers&limit=8"

# 预期结果：返回 id=1,2 的商品（按 sort 排序）
```

### 2. 验证正常销量模式

```bash
# 1. 设置销量数据
UPDATE product SET sales_30d = 100 WHERE id = 3;
UPDATE product SET sales_30d = 50 WHERE id = 4;

# 2. 清空缓存
docker exec -i pd-redis redis-cli FLUSHDB

# 3. 调用 Best Sellers API
curl "http://localhost:8080/api/store/products/recommendations?block=best_sellers&limit=8"

# 预期结果：返回 id=3,4 的商品（按 sales_30d 排序），不再使用 recommend
```

## 总结

- **`recommend`** = 运营推荐标记，仅用于 Best Sellers 冷启动回退
- **显示位置** = Best Sellers 推荐位（首页、分类页、详情页）
- **前台不显示** = 不在商品卡片上显示徽章，仅影响推荐算法
- **冷启动必需** = 系统刚上线时，手工设置推荐商品
- **有销量后自动切换** = 有真实销量数据后，自动按销量排序

**简单理解**：`recommend` 是给推荐算法看的，不是给用户看的。
