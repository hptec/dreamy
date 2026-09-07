# 管理后台缓存失效全面审查

## 审查范围

基于已识别的管理后台控制器和缓存使用情况，需要审查以下模块：

### 1. Catalog 域（商品目录）
- ✅ **AdminProductController** - 商品管理（已修复批量操作）
- 🔍 **AdminCategoryController** - 分类管理
- 🔍 **AdminTagController** - 标签管理  
- 🔍 **AdminAttributeController** - 属性管理
- 🔍 **AdminQuestionController** - 商品问答管理

### 2. Marketing 域（营销活动）
- 🔍 **AdminBannerController** - Banner管理
- 🔍 **AdminFlashSaleController** - 限时抢购管理
- 🔍 **AdminLookbookController** - Lookbook管理
- 🔍 **AdminBlogController** - 博客管理
- 🔍 **AdminWeddingController** - 真实婚礼管理
- 🔍 **AdminGuideController** - 指南管理
- 🔍 **AdminCouponController** - 优惠券管理

### 3. Review 域（评价）
- 🔍 **AdminReviewController** - 评价管理

### 4. Trading 域（交易）
- ⚪ **AdminOrderController** - 订单管理（通常不缓存）
- ⚪ **AdminRefundController** - 退款管理（通常不缓存）

### 5. Shipping 域（物流）
- ⚪ **AdminCarrierController** - 承运商管理（通常不缓存）
- ⚪ **AdminShippingRateController** - 运费管理（通常不缓存）

### 6. Config 域（配置）
- ⚪ **AdminCheckoutSettingsController** - 结账配置（通常不缓存）

## 审查方法

对每个模块，检查：
1. **是否有消费端缓存**？查看对应的 Store*Service 是否使用 cache
2. **管理后台操作**：CREATE / UPDATE / DELETE / BATCH / STATUS_CHANGE
3. **缓存失效策略**：是否在事务提交后失效相关缓存？
4. **批量操作**：是否有批量操作且缺少统一缓存失效？

## 优先级分类

### P0 - 高优先级（影响用户直接可见）
- 商品管理 ✅
- 分类管理 🔍
- 标签管理 🔍
- Banner管理 🔍
- 限时抢购 🔍
- 评价管理 🔍

### P1 - 中优先级（影响内容展示）
- Lookbook管理 🔍
- 博客管理 🔍
- 真实婚礼管理 🔍
- 指南管理 🔍
- 商品问答 🔍

### P2 - 低优先级（后台功能）
- 属性管理 🔍
- 优惠券管理 🔍

### P3 - 可忽略（通常不缓存）
- 订单、退款、物流、配置等

## 下一步

按优先级逐个审查模块，重点关注：
1. 是否有批量操作
2. 缓存失效是否完整
3. 性能优化机会
