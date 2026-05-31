# REQ-IDENTITY-004 后台身份运营、登录与认证配置、操作日志

> 门户：portal-admin｜后端：/api/admin/*｜原型：CustomerDetail.vue、AuthSettings.vue、OperationLogs.vue
> 域：identity

## 功能需求

- **FUNC-022 用户身份运营**：用户详情展示登录方式卡（email/google/apple，primary/verified/relay）、登录记录（时间/方式/IP/设备/位置/结果）、活跃会话；可强制下线单个或全部会话；可禁用账户（禁用即撤销全部会话）。
- **FUNC-023 登录与认证配置**：超管配置消费端登录方式开关（email 主登录恒开不可关）、OTP 策略（长度4/6/8、有效期1-30min、重发间隔10-120s、最大尝试3-10）、解绑最小方式数 min_methods(1-3)、OAuth 凭据展示；保存写认证配置变更日志，配置供消费端登录读取。
- **FUNC-024 操作日志**：记录管理员关键操作，支持按操作人/时间范围/操作类型筛选，详情含变更前后对比；可导出 CSV；**日志只读不可删**。账户归并由系统自动写入（操作人=系统）。

## 关键约束

- 账户归并固定为系统自动行为（email_verified 且邮箱一致），**无后台人工合并入口、无开关**。
- 强制下线对 passwordless 账户无需重置密码，下次需重新验证码/Google/Apple 登录。
- OTP 策略数值越界（如有效期>30、最大尝试<3）保存被拒。

## 验收场景

```gherkin
Scenario: FUNC-022 强制下线全部会话
  Given 用户 u-1001 有 2 个 active 会话
  When 管理员（含 /customers 权限）点击强制下线全部并确认
  Then 全部 UserSession status=revoked
  And 写一条 action=强制下线 的 OperationLog

Scenario: FUNC-022 禁用账户级联撤销会话
  Given 用户 status=active 且有活跃会话
  When 管理员禁用该账户
  Then User.status=disabled 且其全部会话被撤销，缓存被清除

Scenario: FUNC-023 保存认证配置生效
  Given 超管在登录与认证页
  When 开启 Apple 登录并将 OTP 有效期改为 10 分钟后保存
  Then AuthConfig 更新，写 action=认证配置变更 日志
  And 消费端登录页读取到新配置

Scenario: EDGE-019 OTP 数值越界被拒
  When 超管将最大尝试次数设为 2（<3）并保存
  Then 保存被拒并提示合法区间

Scenario: FUNC-024 操作日志筛选与详情
  Given 存在多条操作日志
  When 按操作人 + 操作类型 + 时间范围筛选并打开某条详情
  Then 展示变更前后对比（新增/修改/删除着色）

Scenario: EDGE-018 操作日志不可删除
  When 任意人尝试删除操作日志
  Then 系统不提供删除入口/接口（只读）

Scenario: FUNC-025 系统自动归并写日志
  Given 一个已验证邮箱与既有账户一致的新第三方登录
  When 系统自动归并
  Then 写一条 操作人=系统、action=账户合并 的 OperationLog
```
