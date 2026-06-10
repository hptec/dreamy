# UI 验收清单：portal-api-integration
> 生成时间：2026-06-10T07:45:22.084498+00:00
> 来源：14 条操作路径，31 条文本断言

## 页面：admin-list
### 交互操作
- [ ] OP-001 [dialog_open] 新增管理员

### 视觉基线
- [ ] screenshots/admin-list-full-desktop.png 截图对比

## 页面：analytics
### 视觉基线
- [ ] screenshots/analytics-full-desktop.png 截图对比

## 页面：attribute-sets
### 视觉基线
- [ ] screenshots/attribute-sets-full-desktop.png 截图对比

## 页面：auth-settings
### 交互操作
- [ ] OP-002 [api_call] 保存配置

### 文本内容断言
- [ ] 页面应展示文字"保存配置"（来源：button）

### 视觉基线
- [ ] screenshots/auth-settings-full-desktop.png 截图对比

## 页面：banners
### 视觉基线
- [ ] screenshots/banners-full-desktop.png 截图对比

## 页面：categories
### 视觉基线
- [ ] screenshots/categories-full-desktop.png 截图对比

## 页面：content-blog
### 视觉基线
- [ ] screenshots/content-blog-full-desktop.png 截图对比

## 页面：content-lookbook
### 视觉基线
- [ ] screenshots/content-lookbook-full-desktop.png 截图对比

## 页面：content-weddings
### 视觉基线
- [ ] screenshots/content-weddings-full-desktop.png 截图对比

## 页面：customer-detail
### 交互操作
- [ ] OP-003 [api_call] 返回
- [ ] OP-004 [dialog_open] 强制下线

### 视觉基线
- [ ] screenshots/customer-detail-full-desktop.png 截图对比

## 页面：customers
### 文本内容断言
- [ ] 页面应展示文字"搜索姓名 / 邮箱…"（来源：placeholder）
- [ ] 页面应展示文字"用户"（来源：th）
- [ ] 页面应展示文字"注册时间"（来源：th）
- [ ] 页面应展示文字"订单数"（来源：th）
- [ ] 页面应展示文字"累计消费"（来源：th）
- [ ] 页面应展示文字"等级"（来源：th）
- [ ] 页面应展示文字"状态"（来源：th）
- [ ] 页面应展示文字"操作"（来源：th）

### 视觉基线
- [ ] screenshots/customers-full-desktop.png 截图对比

## 页面：dashboard
### 交互操作
- [ ] OP-005 [navigation] 查看完整看板(/analytics)
- [ ] OP-006 [navigation] 发布站点(/publish)

### 视觉基线
- [ ] screenshots/dashboard-full-desktop.png 截图对比

## 页面：email-marketing
### 视觉基线
- [ ] screenshots/email-marketing-full-desktop.png 截图对比

## 页面：home-builder
### 视觉基线
- [ ] screenshots/home-builder-full-desktop.png 截图对比

## 页面：login
### 文本内容断言
- [ ] 页面应展示文字"admin@dreamy.com"（来源：placeholder）
- [ ] 页面应展示文字"请输入密码"（来源：placeholder）
- [ ] 页面应展示文字"邮箱"（来源：label）
- [ ] 页面应展示文字"密码"（来源：label）
- [ ] 页面应展示文字"欢迎回来"（来源：h1）
- [ ] 页面应展示文字"Dreamy Admin Console"（来源：p）
- [ ] 页面应展示文字"配置首页、管理商品订单、一键发布静态站点。"（来源：p）
- [ ] 页面应展示文字"Dreamy"（来源：p）
- [ ] 页面应展示文字"Admin Console"（来源：p）
- [ ] 页面应展示文字"登录以管理您的 Dreamy 站点"（来源：p）

### 视觉基线
- [ ] screenshots/login-full-desktop.png 截图对比

## 页面：navigation-config
### 视觉基线
- [ ] screenshots/navigation-config-full-desktop.png 截图对比

## 页面：operation-logs
### 交互操作
- [ ] OP-007 [download] 导出 CSV

### 视觉基线
- [ ] screenshots/operation-logs-full-desktop.png 截图对比

## 页面：order-detail
### 交互操作
- [ ] OP-008 [api_call] 返回
- [ ] OP-009 [dialog_open] 标记发货

### 视觉基线
- [ ] screenshots/order-detail-full-desktop.png 截图对比

## 页面：orders
### 视觉基线
- [ ] screenshots/orders-full-desktop.png 截图对比

## 页面：product-edit
### 交互操作
- [ ] OP-010 [api_call] 返回列表
- [ ] OP-011 [navigation] 保存并生成静态页

### 文本内容断言
- [ ] 页面应展示文字"保存草稿"（来源：button）

### 视觉基线
- [ ] screenshots/product-edit-full-desktop.png 截图对比

## 页面：products
### 交互操作
- [ ] OP-012 [navigation] 新增商品(/products/new)

### 视觉基线
- [ ] screenshots/products-full-desktop.png 截图对比

## 页面：promotions
### 视觉基线
- [ ] screenshots/promotions-full-desktop.png 截图对比

## 页面：publish
### 视觉基线
- [ ] screenshots/publish-full-desktop.png 截图对比

## 页面：refunds
### 交互操作
- [ ] OP-013 [state_toggle] {{ t[1] }}

### 文本内容断言
- [ ] 页面应展示文字"已处理"（来源：span）
- [ ] 页面应展示文字"工单号"（来源：th）
- [ ] 页面应展示文字"关联订单"（来源：th）
- [ ] 页面应展示文字"客户"（来源：th）
- [ ] 页面应展示文字"退款金额"（来源：th）
- [ ] 页面应展示文字"退款原因"（来源：th）
- [ ] 页面应展示文字"申请时间"（来源：th）
- [ ] 页面应展示文字"状态"（来源：th）
- [ ] 页面应展示文字"操作"（来源：th）

### 视觉基线
- [ ] screenshots/refunds-full-desktop.png 截图对比

## 页面：reviews
### 视觉基线
- [ ] screenshots/reviews-full-desktop.png 截图对比

## 页面：role-management
### 交互操作
- [ ] OP-014 [dialog_open] 新增角色

### 视觉基线
- [ ] screenshots/role-management-full-desktop.png 截图对比

## 页面：settings
### 视觉基线
- [ ] screenshots/settings-full-desktop.png 截图对比

## 页面：shipping
### 视觉基线
- [ ] screenshots/shipping-full-desktop.png 截图对比
