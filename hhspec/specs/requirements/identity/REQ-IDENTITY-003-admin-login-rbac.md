# REQ-IDENTITY-003 后台管理员登录与 RBAC 角色权限

> 门户：portal-admin（Vue3）｜后端：/api/admin/*｜原型：Login.vue、AdminList.vue、RoleManagement.vue
> 域：identity｜与消费端登录态完全隔离（独立 admin JWT）

## 功能需求

- **FUNC-014 管理员登录**：邮箱+密码登录，凭据正确且 status=active 时签发 admin JWT；记录登录操作日志。支持登录后按 redirect 跳转。
- **FUNC-015 管理员 CRUD**：超管可新增/编辑/删除管理员；邮箱唯一且创建后不可改；新增需密码（≥6 位）。
- **FUNC-016 禁用/启用管理员**：行内切换状态；超级管理员不可被禁用；不可删除自己；不可删除超级管理员。
- **FUNC-017 重置密码**：为管理员设置新密码（≥6 位，两次一致）。
- **FUNC-018 角色管理**：角色列表（预设/自定义）；可新增/改名/删除自定义角色；预设角色超级管理员 is_locked 不可编辑权限。
- **FUNC-019 权限矩阵**：角色 × 菜单权限点 的二元勾选（菜单可见+路由可访问）；支持分组批量与全选。
- **FUNC-020 删除角色约束**：角色下仍有关联管理员时不可删除，需先迁移。
- **FUNC-021 权限生效**：登录后按角色动态渲染侧边栏；无权限菜单的路由被守卫拦截重定向到工作台。

## 关键枚举

- 预设角色：超级管理员(locked,全部权限)、商品运营、订单客服、内容编辑、数据分析
- 菜单权限点 key 与后台路由一一对应（如 `/system/admins`、`/customers`、`/orders` 等共 22 项）
- 状态：active / disabled

## 验收场景

```gherkin
Scenario: FUNC-014 管理员登录成功
  Given 管理员 admin@dreamy.com 状态 active 密码正确
  When 提交登录表单
  Then 签发 admin JWT 并写入一条 action=登录 的 OperationLog
  And 跳转 redirect 或工作台

Scenario: EDGE-011 禁用账户登录被拒
  Given 管理员 status=disabled
  When 提交正确凭据
  Then 返回 403 ADMIN_DISABLED

Scenario: EDGE-012 邮箱重复新增被拒
  Given 已存在 grace@dreamy.com
  When 超管新增管理员 email=grace@dreamy.com
  Then 返回 409 EMAIL_EXISTS

Scenario: EDGE-013 删除自己被拒
  Given 当前登录管理员为 X
  When X 尝试删除自己
  Then 操作被拒绝

Scenario: EDGE-014 禁用超级管理员被拒
  When 任意人尝试禁用超级管理员
  Then 操作被拒绝

Scenario: FUNC-019 权限矩阵保存生效
  Given 选中"商品运营"角色
  When 勾选 /analytics 并保存
  Then RolePermission 新增该记录，写 action=权限变更 日志

Scenario: EDGE-015 删除有成员的角色被拒
  Given "商品运营"下有 1 名管理员
  When 超管删除该角色
  Then 返回 409 ROLE_IN_USE，提示先迁移成员

Scenario: EDGE-016 无权限路由被守卫拦截
  Given 订单客服无 /system/admins 权限
  When 该角色管理员直接访问 /system/admins
  Then 重定向到工作台
```
