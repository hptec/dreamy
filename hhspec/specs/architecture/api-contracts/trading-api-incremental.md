# trading-api.openapi.yml 增量变更

## 变更说明
本文档定义 i18n-complete-with-ai-assist 变更对 trading-api.openapi.yml 的增量修改。

## 设计依据
- decision.md 决策 13（匿名下单时 locale 快照到 orders.locale_snapshot，后端邮件发信优先级）
- er-diagram.yml：Order.locale_snapshot 新增字段
- acceptance.yml：FUNC-019（locale 快照 + 邮件按 locale 发送）

## 变更内容

### 1. Order 相关 schema 新增字段

在所有包含订单信息的 schema 中新增 `locale_snapshot` 字段：

#### OrderBase schema（若存在）或直接在各订单 schema 中新增
```yaml
locale_snapshot:
  type: string
  enum: [en, es, fr]
  description: |
    下单时的 locale 快照（决策 13）。
    匿名用户下单时，前端将当前 URL locale（/es/ 或 /fr/ 或根路径默认 en）传给后端，落库。
    后端发送订单确认/退款通知邮件时，优先级为：user.locale_pref → order.locale_snapshot → 默认 EN。
    登录用户下单时也记录此字段（双保险，若用户后续改语言偏好，订单邮件仍用下单时语言）。
  example: "es"
```

在以下 schema 中均新增此字段：
- `StoreOrderDetail`（消费端订单详情）
- `AdminOrderDetail`（后台订单详情）
- `AdminOrderListItem`（后台订单列表项）

### 2. CreateOrderRequest schema 新增字段

在 `components/schemas/CreateOrderRequest`（或类似的下单请求体 schema）中新增 `locale_snapshot` 字段：

```yaml
CreateOrderRequest:
  type: object
  required: [items, address, payment_method]
  properties:
    items:
      type: array
      items:
        $ref: '#/components/schemas/OrderItemRequest'
      minItems: 1
      description: 订单行（商品 SKU + 数量）
    address:
      $ref: '#/components/schemas/AddressUpsert'
      description: 收货地址
    payment_method:
      type: string
      enum: [stripe, paypal]
      description: 支付方式
    locale_snapshot:  # 新增字段
      type: string
      enum: [en, es, fr]
      description: |
        下单时的 locale 快照（决策 13）。
        前端从当前 URL 路径提取（/es/ → es，/fr/ → fr，根路径 → en）或从 cookie 读取。
        后端邮件发送时优先级：user.locale_pref → order.locale_snapshot → EN。
      example: "es"
    coupon_code:
      type: string
      maxLength: 32
      description: 优惠券码（可选）
    customer_note:
      type: string
      maxLength: 500
      description: 客户备注（可选）
```

### 3. 订单响应示例

#### 消费端订单详情（含 locale_snapshot）
```http
GET /api/store/orders/123
Authorization: Bearer <store_jwt>
```

响应（payload 部分）：
```json
{
  "id": 123,
  "order_number": "DRM-20260616-001",
  "status": 2,
  "locale_snapshot": "es",  // 新增字段
  "total_amount": 1299.00,
  "currency": "USD",
  "lines": [...],
  "address_snapshot": {...},
  "payment": {...},
  "created_at": "2026-06-16T10:30:00Z"
}
```

#### 后台订单详情（含 locale_snapshot）
```http
GET /api/admin/orders/123
Authorization: Bearer <admin_jwt>
```

响应（payload 部分）：
```json
{
  "id": 123,
  "order_number": "DRM-20260616-001",
  "customer_email": "user@example.com",
  "status": 2,
  "locale_snapshot": "es",  // 新增字段
  "total_amount": 1299.00,
  "lines": [...],
  "address_snapshot": {...},
  "created_at": "2026-06-16T10:30:00Z"
}
```

## 实现注意事项

### 后端实现
1. Order 实体增加 `localeSnapshot` 字段（String, @Column(length=8)）
2. OrderController 的 `POST /api/store/orders`（创建订单）接口：
   - 从请求体读取 `locale_snapshot`，若前端未传或为 null，默认写入 "en"
   - 将 `locale_snapshot` 写入 Order 表
3. 邮件发送服务（EmailService）的模板选择逻辑（与 identity-api 增量一致）：
   ```java
   String selectEmailTemplate(String templateName, User user, Order order) {
       // 优先级：user.localePref → order.localeSnapshot → 默认 EN
       String locale = user != null ? user.getLocalePref() : null;
       if (locale == null && order != null) {
           locale = order.getLocaleSnapshot();
       }
       if (locale == null) {
           locale = "en";
       }
       
       String templatePath = "email/" + templateName + "_" + locale + ".html";
       if (!templateExists(templatePath)) {
           templatePath = "email/" + templateName + "_en.html"; // 回退 EN
       }
       return templatePath;
   }
   ```
4. 订单确认邮件（order placed）、退款通知邮件（refund approved）均调用此逻辑选择模板

### 前端实现
- portal-store：CheckoutPage 提交订单时，从当前路由提取 locale：
  ```typescript
  const locale = useParams().locale || 'en'; // App Router: app/[locale]/checkout
  const localeSnapshot = locale;
  
  await fetch('/api/store/orders', {
    method: 'POST',
    body: JSON.stringify({
      items: [...],
      address: {...},
      payment_method: 'stripe',
      locale_snapshot: localeSnapshot  // 新增字段
    })
  });
  ```
- 匿名用户和登录用户均传递 `locale_snapshot`（登录用户有 user.locale_pref 双保险，匿名用户仅靠此字段）

### 邮件模板三语化（与 identity-api 增量一致）
在 `backend/src/main/resources/templates/email/` 目录下准备三语版本：
- `order_confirmation_en.html` / `order_confirmation_es.html` / `order_confirmation_fr.html`
- `refund_notification_en.html` / `refund_notification_es.html` / `refund_notification_fr.html`

模板结构一致，仅文案三语化。使用 Thymeleaf 渲染（决策 13）。

## 数据库迁移
```sql
-- Order 表增加 locale_snapshot 列
ALTER TABLE orders 
ADD COLUMN locale_snapshot VARCHAR(8) COMMENT '下单时locale快照(en/es/fr)';

-- 已有订单默认 null 或批量初始化为 en：
-- UPDATE orders SET locale_snapshot = 'en' WHERE locale_snapshot IS NULL;
```

## API 示例

### 消费端创建订单（含 locale_snapshot）
```http
POST /api/store/orders
Authorization: Bearer <store_jwt>  # 或匿名
Content-Type: application/json

{
  "items": [
    { "sku_id": 456, "quantity": 1 }
  ],
  "address": {
    "recipient_name": "María García",
    "phone": "+34612345678",
    "address_line1": "Calle Mayor 10",
    "city": "Madrid",
    "state": "Madrid",
    "postal_code": "28013",
    "country": "ES"
  },
  "payment_method": "stripe",
  "locale_snapshot": "es"  // 新增字段
}
```

响应（payload 部分）：
```json
{
  "id": 123,
  "order_number": "DRM-20260616-001",
  "status": 1,
  "locale_snapshot": "es",
  "total_amount": 1299.00,
  "payment_intent_id": "pi_xxx",
  "created_at": "2026-06-16T10:30:00Z"
}
```

### 后端发送订单确认邮件的场景
**场景 1：登录用户（有 user.locale_pref）**
- user.locale_pref = "fr"
- order.locale_snapshot = "es"（下单时在 ES 页面）
- 邮件使用 `order_confirmation_fr.html`（优先用户偏好）

**场景 2：匿名用户**
- user = null
- order.locale_snapshot = "es"
- 邮件使用 `order_confirmation_es.html`（用订单快照）

**场景 3：匿名用户且前端未传 locale_snapshot**
- user = null
- order.locale_snapshot = null
- 邮件使用 `order_confirmation_en.html`（默认 EN）

## 验收场景映射
- FUNC-019：locale_snapshot 落库验证（登录态/匿名态均记录）
- FUNC-020：后端邮件按 locale 发送验证（订单确认/退款通知三语模板选择逻辑）
