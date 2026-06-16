# 属性定义强制删除功能

## 功能概述

当删除被引用的属性定义时，系统会提示引用详情，并要求用户输入 `DELETE` 确认强制删除。强制删除会级联清理所有引用数据。

## 实现细节

### 后端改动

1. **AttributeDefService.delete()** - 增加 `force` 参数
   - `force=false`（默认）：检测到引用时返回 409507 错误，附带引用计数
   - `force=true`：级联删除所有引用数据
   
2. **级联删除逻辑**：
   - 删除属性集中的引用项（`attribute_set_items` 表）
   - 删除所有商品的 EAV 值（`product_attribute_values` 表）
   
3. **Controller** - `DELETE /api/admin/attribute-defs/{id}` 接收 `force` 查询参数

4. **Repository 新增方法**：
   - `AttributeSetRepository.deleteItemsByAttributeId()`
   - `ProductAttributeValueRepository.deleteByAttributeId()`

### 前端改动

1. **两步确认流程**：
   - 第一步：点击删除按钮，发起普通删除请求
   - 如果返回 409507 错误，解析引用详情（属性集数量、商品数量）
   - 第二步：显示引用详情，要求输入 "DELETE" 确认
   - 用户输入 "DELETE" 后，发起 `force=true` 的删除请求

2. **UI 改进**：
   - 显示属性集引用数量（`attribute_set_count`）
   - 显示商品属性值数量（`product_value_count`）
   - 输入框验证：只有输入 "DELETE"（区分大小写）才能执行强制删除
   - 警告提示：此操作不可撤销

## 使用流程

1. 用户点击属性定义的删除按钮
2. 系统检测到该属性被引用（例如：5 个属性集、409 个商品）
3. 弹窗显示引用详情和警告信息
4. 用户需要在输入框中输入 "DELETE" 来确认
5. 点击"强制删除"按钮，系统级联清理所有引用数据
6. 删除成功，属性定义及其所有引用数据被永久删除

## 安全机制

- 输入验证：必须精确输入 "DELETE"（区分大小写）
- 两步确认：防止误删
- 引用详情显示：用户明确了解删除影响范围
- 审计日志：记录强制删除操作及清理的引用数量
- 缓存失效：自动清理相关的商品缓存

## API 契约

### 删除属性定义（支持强制删除）

```http
DELETE /api/admin/attribute-defs/{id}?force=true
Authorization: Bearer {token}
```

**成功响应**：
```http
HTTP/1.1 204 No Content
```

**首次删除遇到引用**（force=false 或未传）：
```http
HTTP/1.1 409 Conflict
Content-Type: application/json

{
  "code": 409507,
  "message": "属性定义被引用，无法删除",
  "data": {
    "attribute_set_count": 5,
    "product_value_count": 409
  }
}
```

## 测试建议

1. **正常删除**：删除未被引用的属性 → 直接删除成功
2. **首次删除被引用属性**：触发 409507 错误，显示引用详情
3. **输入错误确认文本**：输入 "delete"（小写）或其他文本 → 无法执行
4. **输入正确确认文本**：输入 "DELETE" → 强制删除成功
5. **验证级联删除**：检查属性集和商品属性值是否被清理
6. **验证缓存失效**：确认相关商品缓存已失效
