# identity-api.openapi.yml 增量变更

## 变更说明
本文档定义 i18n-complete-with-ai-assist 变更对 identity-api.openapi.yml 的增量修改。

## 设计依据
- decision.md 决策 13（用户语言偏好持久化，后端邮件按 locale 发送）
- er-diagram.yml：User.locale_pref 新增字段
- acceptance.yml：FUNC-019（用户语言偏好持久化）

## 变更内容

### 1. User schema 新增字段

在 `components/schemas/User` 中新增 `locale_pref` 字段：

```yaml
User:
  type: object
  required: [id, email, email_verified, status, created_at]
  properties:
    id:
      type: integer
      format: int64
      example: 1
    email:
      type: string
      format: email
      description: 主邮箱
    email_verified:
      type: boolean
      description: 邮箱是否已验证
    display_name:
      type: string
      maxLength: 64
      description: 显示名称（可选）
    avatar_url:
      type: string
      format: uri
      description: 头像 URL
    locale_pref:  # 新增字段
      type: string
      enum: [en, es, fr]
      description: |
        用户语言偏好（决策 13）。
        登录态用户在前端切换语言时写入，后端发送邮件（订单确认/OTP/退款）时优先按此值选模板。
        匿名用户无此字段，下单时 locale 快照到 orders.locale_snapshot。
      example: "es"
    status:
      type: integer
      enum: [1, 2, 3]
      description: 账户状态（active=1/suspended=2/deleted=3）
    created_at:
      type: string
      format: date-time
    updated_at:
      type: string
      format: date-time
```

### 2. 新增更新用户偏好接口

在 `paths` 中新增 `/api/consumer/auth/profile` 端点（若已存在则扩展）：

```yaml
/api/consumer/auth/profile:
  put:
    summary: 更新用户资料（含语言偏好）
    description: |
      FUNC-019（决策 13）。登录态用户更新资料，包含语言偏好。
      前端在语言切换器切换语言时，若用户已登录，调用此接口持久化 locale_pref。
      匿名用户切换语言仅写 localStorage + cookie（SSR 可读），不调用此接口。
    operationId: updateProfile
    tags: [store-account]
    security:
      - StoreBearerAuth: []
    requestBody:
      required: true
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ProfileUpdateRequest'
    responses:
      '200':
        description: 更新成功
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/User'
      '401':
        $ref: '#/components/responses/Unauthorized'
      '422':
        $ref: '#/components/responses/ValidationError'
```

### 3. 新增 ProfileUpdateRequest schema

```yaml
ProfileUpdateRequest:
  type: object
  properties:
    display_name:
      type: string
      maxLength: 64
      description: 显示名称
    locale_pref:  # 新增字段
      type: string
      enum: [en, es, fr]
      description: |
        语言偏好（决策 13）。
        前端在用户切换语言时调用（若已登录），后端写入 user.locale_pref。
      example: "es"
```

### 4. AuthSession schema 响应包含 locale_pref

在 `components/schemas/AuthSession` 中确保 `user` 对象包含 `locale_pref` 字段（使用 `User` schema，自动包含）。

登录成功（OTP verify / OIDC callback）时响应示例：
```json
{
  "access_token": "eyJ...",
  "refresh_token": "rt_...",
  "expires_in": 7200,
  "user": {
    "id": 1,
    "email": "user@example.com",
    "email_verified": true,
    "locale_pref": "es",  // 新增字段
    "status": 1,
    "created_at": "2026-06-01T10:00:00Z"
  }
}
```

## 实现注意事项

### 后端实现
1. User 实体增加 `localePref` 字段（String, @Column(length=8)）
2. UserController 新增 `PUT /api/consumer/auth/profile` 接口，支持更新 localePref
3. 邮件发送服务（EmailService）调整模板选择逻辑：
   - 优先级：user.localePref → order.localeSnapshot → 默认 EN
   - 模板路径：`classpath:templates/email/{template_name}_{locale}.html`（如 `order_confirmation_es.html`）
   - 若对应语言模板不存在，回退 EN 模板
4. 首次登录（OTP verify / OIDC callback）时，若前端传递了 `locale` 参数（如从 cookie 读取当前语言），初始化 user.localePref

### 前端实现
- portal-store：LanguageSwitcher 组件切换语言时，若用户已登录（JWT 存在），调用 `PUT /api/consumer/auth/profile` 更新 locale_pref
- 匿名用户切换语言仅写 localStorage + cookie（`document.cookie = "locale=es; path=/"`），不调用接口
- 下单流程（CheckoutPage）在提交订单前，将当前 locale 作为 `locale_snapshot` 字段传给后端（见 trading-api 增量变更）

### 邮件模板三语化
在 `backend/src/main/resources/templates/email/` 目录下，每个邮件模板准备三语版本：
- `order_confirmation_en.html`
- `order_confirmation_es.html`
- `order_confirmation_fr.html`
- `otp_en.html` / `otp_es.html` / `otp_fr.html`
- `refund_notification_en.html` / `refund_notification_es.html` / `refund_notification_fr.html`

模板结构一致，仅文案三语化。使用 Thymeleaf 渲染（决策 13）。

## 数据库迁移
```sql
-- User 表增加 locale_pref 列
ALTER TABLE users 
ADD COLUMN locale_pref VARCHAR(8) COMMENT '用户语言偏好(en/es/fr)';

-- 已有用户默认 null（首次切换语言时写入）
-- 若需要给已有用户初始化为 en，可执行：
-- UPDATE users SET locale_pref = 'en' WHERE locale_pref IS NULL;
```

## API 示例

### 用户切换语言并持久化偏好
```http
PUT /api/consumer/auth/profile
Authorization: Bearer <store_jwt>
Content-Type: application/json

{
  "locale_pref": "es"
}
```

响应（payload 部分）：
```json
{
  "id": 1,
  "email": "user@example.com",
  "email_verified": true,
  "locale_pref": "es",
  "status": 1,
  "created_at": "2026-06-01T10:00:00Z",
  "updated_at": "2026-06-16T11:20:00Z"
}
```

### 后端发送邮件的模板选择逻辑（伪代码）
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
