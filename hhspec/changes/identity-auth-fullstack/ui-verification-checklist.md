# UI 验收清单 — identity-auth-fullstack

> 覆盖两端共 11 个页面（消费端 4 / 后台 7）。原型为验收基准。
> 详细操作路径脚本见 `tests/ui-verification/identity.spec.ts`（后台 14 条 op）。

## portal-store 消费端（Next.js）

### account/login —— /account/login
- [ ] 两步式：输入邮箱 → 进入 6 位验证码步骤
- [ ] 邮箱格式非法时报错 "Please enter a valid email address"
- [ ] 验证码 6 格输入：自动跳格、退格回跳、粘贴自动填充
- [ ] Continue with Google / Continue with Apple 按钮存在
- [ ] "or" 分隔、Apple relay 提示文案、Terms/Privacy 链接
- [ ] 验证成功跳转 /account；resend 倒计时显示

### account —— /account（账户总览）
- [ ] 展示账户概览（资料/订单/导航入口）

### account/settings —— /account/settings
- [ ] 展示账户设置；**不含**任何修改密码/设置密码入口（passwordless）

### account/security —— /account/security（登录与安全）
- [ ] Login methods：email(Primary/Verified 不可解绑)、google、apple 绑定状态
- [ ] Connect/Disconnect 切换；仅剩一种方式时 Disconnect 禁用
- [ ] Devices & sessions：当前设备标记、其他设备 Sign out、Sign out other devices
- [ ] Apple "private relay" 提示

## portal-admin 管理后台（Vue3）

### Login —— /login
- [ ] 邮箱 + 密码表单；记住我；错误提示；登录后按 redirect 跳转

### Customers —— /customers（用户列表）
- [ ] 列：用户/注册时间/订单数/累计消费/等级/状态/操作
- [ ] 搜索（姓名/邮箱）+ 等级筛选（VIP/常规）+ 分页
- [ ] 详情入口跳 /customers/:id

### CustomerDetail —— /customers/:id（用户详情）
- [ ] 资料卡（含 tier/status）、登录方式卡（email/google/apple，primary/verified/relay）
- [ ] 登录记录表（时间/方式/IP/设备/位置/结果）
- [ ] 活跃会话列表 + 单会话下线 + 强制下线全部（确认弹窗）
- [ ] 打标签 / 禁用账户 / 返回 操作按钮

### AdminList —— /system/admins（管理员管理）
- [ ] 列：管理员/角色/创建时间/最近登录/状态/操作
- [ ] 搜索 + 角色筛选 + 状态筛选
- [ ] 新增（姓名/邮箱/密码≥6/角色/状态）；编辑（邮箱不可改）
- [ ] 禁用/启用 Toggle（超管不可禁用）；重置密码（两次一致）
- [ ] 删除（不可删自己/不可删超管）确认弹窗

### RoleManagement —— /system/roles（角色权限）
- [ ] 左侧角色列表（预设/自定义 + 权限数）
- [ ] 右侧权限矩阵：分组复选 + 全选/取消；超管 is_locked 不可编辑
- [ ] 新增/改名/删除自定义角色；删除有成员角色被拒提示
- [ ] 未保存变更提示 + 保存

### AuthSettings —— /system/auth（登录与认证）
- [ ] 登录方式开关（email 主登录 locked、google、apple）
- [ ] OTP 策略（长度 4/6/8、有效期、重发间隔、最大尝试）
- [ ] 账户关联策略（系统自动归并说明、minMethods）
- [ ] OAuth 凭据（Google Client ID / Apple Service ID 只读展示）
- [ ] 保存 toast：变更记入操作日志

### OperationLogs —— /system/logs（操作日志）
- [ ] 列：时间/操作人/操作/对象/IP + 详情
- [ ] 操作人搜索下拉 + 时间范围 + 操作类型多选筛选
- [ ] 详情抽屉：元数据 + 变更前后对比（新增/修改/删除着色）
- [ ] 导出 CSV；日志只读不可删
