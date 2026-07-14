---
layer: L2
role: l2_data_designer
artifact: identity-physical-schema
change_name: identity-auth-fullstack
domain_code: identity
db_engine: mysql
charset: utf8mb4
collation: utf8mb4_0900_ai_ci
mode: bootstrap
source_authority:
  - er-diagram.yml (13 实体，权威字段源)
  - domain-model.md (不变量/聚合/状态机)
  - data-flow.md (FLOW-01~17 事务边界/查询路径/保留清理)
  - tech-profile.yml (MySQL + MyBatis-Plus + JetCache)
generated_at: "2026-05-31"
---

# identity 物理数据库详细设计（MySQL）

> 本文档为 L2 物理库设计，给出 13 张表的结构、主键、唯一键、索引、外键策略、保留/匿名化方案与迁移说明。
> 完整可执行 DDL 见同目录 `identity-ddl.sql`；数据保留/匿名化方案见 `identity-retention-anonymization.md`。
> 数据库引擎 MySQL 8.0+，存储引擎 InnoDB，字符集 `utf8mb4`，排序规则 `utf8mb4_0900_ai_ci`。

## 0. 全局约定

### 0.1 命名与类型映射

| 领域类型 | 物理类型 | 说明 |
|---------|---------|------|
| uuid (主键) | `CHAR(36)` | 存 UUID v4 字符串；与 MyBatis-Plus `ASSIGN_UUID` 一致；不用 BINARY(16) 以便排查与日志可读 |
| ref→entity (外键引用) | `CHAR(36)` | 逻辑外键，列名 `xxx_id`；不建物理 FOREIGN KEY（见 0.4） |
| str / string | `VARCHAR(n)` | 长度按字段语义（见各表）；JWT jti 类用 `VARCHAR(64)` |
| 长文本（邮件正文） | `TEXT` | EmailTemplate.body |
| bool | `TINYINT(1)` | 0/1；MyBatis-Plus 自动映射 Java boolean |
| int | `INT` / `SMALLINT` | 计数与配置数值 |
| enum | `VARCHAR(n)` + CHECK | 不用 MySQL 原生 ENUM（变更需 DDL、不利演进）；用 VARCHAR + CHECK 约束 + 应用层枚举 |
| datetime | `DATETIME(3)` | 毫秒精度；统一存 UTC，应用层负责时区转换 |
| json | `JSON` | OperationLog.changes |

> 决策：枚举一律 `VARCHAR + CHECK` 而非 MySQL `ENUM`。原因：MySQL ENUM 增删值需 `ALTER TABLE` 锁表、跨 ORM 兼容差、不利 Bootstrap 后续演进；CHECK 约束（MySQL 8.0.16+ 强制生效）+ 应用层 Java enum 双重保障。

### 0.2 公共审计列（所有可变表均含）

| 列 | 类型 | 默认 | 说明 |
|----|------|------|------|
| `created_at` | `DATETIME(3)` | `CURRENT_TIMESTAMP(3)` | 创建时间（DB 默认 + MyBatis-Plus `INSERT` 填充） |
| `updated_at` | `DATETIME(3)` | `CURRENT_TIMESTAMP(3)` ON UPDATE | 更新时间（追加型表 LoginHistory/OperationLog 无此列） |

> MyBatis-Plus `@TableField(fill=...)` 与 DB 默认双保险。追加型审计表（login_history、operation_log）仅 `created_at`，无 `updated_at`（不可变更）。

### 0.3 乐观锁

- 涉及并发竞争的表（`otp_code`、`user_session`、`user`）增加 `version INT NOT NULL DEFAULT 0` 列，配合 MyBatis-Plus `@Version`。
  - `otp_code`：FLOW-02 attempts 自增防并发绕过（虽然主路径用 `SELECT ... FOR UPDATE` 行锁，version 作为二级保障）。
  - `user_session`：FLOW-04 滑动续期并发。
  - `user`：归并/状态切换并发（FLOW-03/08/12）。

### 0.4 外键策略（逻辑外键，禁止物理 FOREIGN KEY）

**全表统一不创建 MySQL `FOREIGN KEY` 约束**，引用完整性由应用层（领域服务 + 事务）维护。理由：
1. 物理外键在高并发会话/审计写入下产生锁竞争与级联锁，影响吞吐。
2. 匿名化/保留清理涉及跨表批量更新与软删，物理外键级联难以表达「软删保留审计」的语义。
3. 分库分表演进（用户量增长后）物理外键无法跨分片。
4. 与企业 MyBatis-Plus 实践一致。

引用完整性保障手段（详见各表「引用完整性」与 `identity-retention-anonymization.md`）：
- 写入前应用层校验被引用行存在（如 RolePermission 写入前校验 role/permission 存在）。
- 删除/匿名化在领域服务事务内级联处理（如禁用 admin 级联 revoke admin_session）。
- 审计表（login_history/operation_log）user_id/operator_id 为**弱引用可空**，被引用主体匿名化/删除后保留记录（正当利益，EDGE-026）。

### 0.5 时区与软删

- 所有 `DATETIME(3)` 存 UTC。
- 软删除：`user` 用 `status=deleted` + `deleted_at` + `anonymized`/`anonymized_at`（非 MyBatis-Plus `@TableLogic` 全局软删，因需区分 deleted 与 anonymized 两阶段，由领域服务显式控制）。其余实体物理删除（清理任务）或状态置位（session revoked）。

---

## 1. user（自然人账户 / 聚合根）

来源：er-diagram.yml#user、domain-model.md#User。FLOW-02/03/08/12。

### 表结构

| 列 | 类型 | 约束 | 默认 | 注释 |
|----|------|------|------|------|
| id | CHAR(36) | PK | — | 用户 UUID |
| email | VARCHAR(255) | NULL（匿名化后清空） | NULL | 主邮箱；匿名化置空 |
| email_verified | TINYINT(1) | NOT NULL | 0 | 主邮箱是否已验证（归并判定关键） |
| name | VARCHAR(80) | NULL | NULL | 姓名（≤80） |
| phone | VARCHAR(32) | NULL | NULL | 电话（≤32） |
| tier | VARCHAR(16) | NOT NULL, CHECK in(vip,regular) | 'regular' | 会员等级 |
| status | VARCHAR(16) | NOT NULL, CHECK in(active,disabled,deleted,anonymized) | 'active' | 账户状态机 |
| avatar | VARCHAR(512) | NULL | NULL | 头像 URL |
| joined_at | DATETIME(3) | NULL | NULL | 注册时间 |
| deleted_at | DATETIME(3) | NULL | NULL | 软删除时间（注销） |
| anonymized | TINYINT(1) | NOT NULL | 0 | 是否已匿名化 |
| anonymized_at | DATETIME(3) | NULL | NULL | 匿名化时间 |
| version | INT | NOT NULL | 0 | 乐观锁 |
| created_at | DATETIME(3) | NOT NULL | CURRENT_TIMESTAMP(3) | |
| updated_at | DATETIME(3) | NOT NULL | CURRENT_TIMESTAMP(3) ON UPDATE | |

- **主键**: `id`
- **唯一键**: `uk_user_email (email)` —— **部分唯一语义**：MySQL 唯一索引允许多个 NULL，匿名化后 email=NULL 不冲突，正好满足「匿名化用户邮箱清空且不互斥」。活跃用户 email 唯一由应用层 + 此唯一索引共同保障。
  > 注：归并核心唯一性在 user_identity `(provider, provider_uid)`，而非 user.email；user.email 唯一索引防止活跃期重复主邮箱。
- **索引**:
  - `idx_user_status (status)` —— 保留清理 `WHERE status=deleted`（FLOW-16）、运营按状态筛选。
  - `idx_user_status_deleted_at (status, deleted_at)` —— 匿名化任务 `WHERE status=deleted AND deleted_at < now-30d`（FLOW-08/16）覆盖扫描。
  - `idx_user_tier (tier)` —— 后台按等级筛选（Customers 页）。
- **外键策略**: 无物理 FK。被 user_identity/user_session/login_history 弱引用。
- **引用完整性**: 注销软删 + 30 天后匿名化由领域服务事务控制；匿名化时级联匿名化关联 user_identity（见保留方案）。
- **迁移说明**: 新建表（Bootstrap）。需预置无（用户运行时产生）。

---

## 2. user_identity（登录凭证 / User 聚合内实体）

来源：er-diagram.yml#user_identity、domain-model.md#UserIdentity。FLOW-03/05/06。

### 表结构

| 列 | 类型 | 约束 | 默认 | 注释 |
|----|------|------|------|------|
| id | CHAR(36) | PK | — | 凭证 UUID |
| user_id | CHAR(36) | NOT NULL（逻辑外键→user.id） | — | 所属用户 |
| provider | VARCHAR(16) | NOT NULL, CHECK in(email,google,apple) | — | 认证渠道 |
| provider_uid | VARCHAR(255) | NOT NULL | — | 渠道内唯一标识（email=邮箱小写；OIDC=sub） |
| identifier | VARCHAR(255) | NULL | NULL | 展示用标识（邮箱/账号名） |
| is_primary | TINYINT(1) | NOT NULL | 0 | 是否主邮箱凭证 |
| verified | TINYINT(1) | NOT NULL | 0 | 是否已验证 |
| connected | TINYINT(1) | NOT NULL | 1 | 是否已连接（解绑置 0） |
| hidden_email | TINYINT(1) | NULL | 0 | Apple Hide My Email |
| relay_email | VARCHAR(255) | NULL | NULL | Apple relay 邮箱 |
| relay_valid | TINYINT(1) | NULL | 1 | relay 是否有效（FUNC-029，relay 失效不锁账户）[INFERRED] |
| bound_at | DATETIME(3) | NULL | NULL | 绑定时间 |
| last_login_at | DATETIME(3) | NULL | NULL | 最近登录时间 |
| created_at | DATETIME(3) | NOT NULL | CURRENT_TIMESTAMP(3) | |
| updated_at | DATETIME(3) | NOT NULL | CURRENT_TIMESTAMP(3) ON UPDATE | |

- **主键**: `id`
- **唯一键**:
  - `uk_identity_provider_uid (provider, provider_uid)` —— **核心唯一约束**：domain R1/不变量「(provider, provider_uid) 全局唯一，防账户劫持」；OIDC 重复回调幂等保障（FLOW-03）。
- **索引**:
  - `idx_identity_user_id (user_id)` —— 按 user 查全部凭证（安全页、归并、min_methods 计数）。
  - `idx_identity_user_primary (user_id, is_primary)` —— 定位主邮箱凭证（换主邮箱 FLOW-06）。
  - `idx_identity_provider_uid` 已含 provider_uid，OIDC 登录 `WHERE provider=? AND provider_uid=?` 走唯一键（FLOW-03 最高频路径）。
- **外键策略**: 无物理 FK（user_id 逻辑外键）。
- **引用完整性**: 绑定前应用层校验 (provider,provider_uid) 未占用；解绑校验非主 + 剩余 connected ≥ min_methods（R2）；user 匿名化时本表 provider_uid/identifier/relay_email 一并匿名化。
- **[INFERRED] relay_valid**: er-diagram 未列出，但 domain-model.md#UserIdentity 不变量与 data-flow FLOW-03 明确「Apple relay 失效标记 relay_valid=false 但 sub 仍可登录」（FUNC-029），故新增此列承载该状态。置信度高。
- **迁移说明**: 新建表。无预置数据。

---

## 3. otp_code（一次性验证码 / 独立聚合）

来源：er-diagram.yml#otp_code、domain-model.md#OtpCode。FLOW-01/02/16。

### 表结构

| 列 | 类型 | 约束 | 默认 | 注释 |
|----|------|------|------|------|
| id | CHAR(36) | PK | — | UUID |
| email | VARCHAR(255) | NOT NULL | — | 目标邮箱 |
| code_hash | VARCHAR(255) | NOT NULL | — | OTP 哈希（绝不存明文） |
| length | TINYINT | NOT NULL, CHECK in(4,6,8) | — | 验证码长度 |
| expires_at | DATETIME(3) | NOT NULL | — | 过期时间 |
| attempts | INT | NOT NULL, CHECK >=0 | 0 | 已尝试次数 |
| max_attempts | INT | NOT NULL, CHECK between 3 and 10 | — | 最大尝试次数 |
| status | VARCHAR(16) | NOT NULL, CHECK in(pending,consumed,expired,locked) | 'pending' | 状态机 |
| last_sent_at | DATETIME(3) | NULL | NULL | 最近发送时间（重发间隔判定） |
| version | INT | NOT NULL | 0 | 乐观锁（防并发绕过 attempts） |
| created_at | DATETIME(3) | NOT NULL | CURRENT_TIMESTAMP(3) | |

- **主键**: `id`
- **唯一键**: 无强唯一（同邮箱历史多条）。发码时「失效旧 pending」由应用层 `UPDATE ... WHERE email=? AND status=pending` 完成。
- **索引**:
  - `idx_otp_email_status (email, status)` —— FLOW-02 `SELECT WHERE email=? AND status=pending FOR UPDATE`（最高频校验路径）。
  - `idx_otp_status_created (status, created_at)` —— FLOW-16 清理 `WHERE status in(consumed,expired,locked) AND created_at < now-24h`。
- **外键策略**: 无（email 仅字符串，OTP 先于 user 存在）。
- **引用完整性**: 无外部引用。
- **迁移说明**: 新建表。高写高删（24h 保留），建议后续按需评估分区（Bootstrap 暂不分区）。

---

## 4. user_session（消费端会话 / User 弱聚合）

来源：er-diagram.yml#user_session、domain-model.md#UserSession。FLOW-02/04/07/08/12/16。

### 表结构

| 列 | 类型 | 约束 | 默认 | 注释 |
|----|------|------|------|------|
| id | CHAR(36) | PK | — | UUID |
| user_id | CHAR(36) | NOT NULL（逻辑外键→user.id） | — | 所属用户 |
| token_id | VARCHAR(64) | NOT NULL | — | access JWT jti |
| refresh_token_id | VARCHAR(64) | NULL | NULL | refresh JWT jti |
| access_expires_at | DATETIME(3) | NULL | NULL | access 过期（2h） |
| refresh_expires_at | DATETIME(3) | NULL | NULL | refresh 过期（30d 滑动） |
| device | VARCHAR(128) | NULL | NULL | 设备 |
| browser | VARCHAR(128) | NULL | NULL | 浏览器 |
| ip | VARCHAR(45) | NULL | NULL | IP（兼容 IPv6） |
| location | VARCHAR(128) | NULL | NULL | 地理位置 |
| is_new_device | TINYINT(1) | NULL | 0 | 是否新设备 |
| method | VARCHAR(16) | NOT NULL, CHECK in(email,google,apple) | — | 登录方式 |
| status | VARCHAR(16) | NOT NULL, CHECK in(active,revoked) | 'active' | 会话状态 |
| last_active_at | DATETIME(3) | NULL | NULL | 最近活跃 |
| version | INT | NOT NULL | 0 | 乐观锁（滑动续期并发） |
| created_at | DATETIME(3) | NOT NULL | CURRENT_TIMESTAMP(3) | |

- **主键**: `id`
- **唯一键**: `uk_session_token_id (token_id)` —— jti 全局唯一。
- **索引**:
  - `idx_session_user_status (user_id, status)` —— FLOW-07/12 按用户查/撤销活跃会话；安全页会话列表。
  - `idx_session_refresh (refresh_token_id)` —— FLOW-04 `WHERE refresh_token_id=? AND status=active`。
  - `idx_session_status_created (status, created_at)` —— FLOW-16 清理 `WHERE status=revoked AND created_at < now-30d`。
- **外键策略**: 无物理 FK（user_id 逻辑外键）。会话有效性以 DB session/admin 状态为准；Redis 单级缓存仅作正缓存提示。
- **引用完整性**: 禁用/注销/强制下线在领域服务事务内 `UPDATE status=revoked`（FLOW-08/12）。
- **迁移说明**: 新建表。高写表。

---

## 5. login_history（登录记录 / 追加型）

来源：er-diagram.yml#login_history、domain-model.md#LoginHistory。FLOW-02/03/14/16。保留 1 年。

### 表结构

| 列 | 类型 | 约束 | 默认 | 注释 |
|----|------|------|------|------|
| id | CHAR(36) | PK | — | UUID |
| user_id | CHAR(36) | NULL（弱引用→user.id） | NULL | 用户（失败时可能无） |
| email | VARCHAR(255) | NULL | NULL | 登录邮箱 |
| method | VARCHAR(16) | NOT NULL, CHECK in(email,google,apple) | — | 登录方式 |
| ip | VARCHAR(45) | NULL | NULL | IP |
| device | VARCHAR(128) | NULL | NULL | 设备 |
| location | VARCHAR(128) | NULL | NULL | 位置 |
| result | VARCHAR(16) | NOT NULL, CHECK in(success,failed) | — | 结果 |
| is_new_device | TINYINT(1) | NULL | 0 | 是否新设备 |
| notified | TINYINT(1) | NULL | 0 | 新设备通知是否已发（FLOW-14 唯一可更新字段） |
| created_at | DATETIME(3) | NOT NULL | CURRENT_TIMESTAMP(3) | 登录时间 |

- **主键**: `id`
- **唯一键**: 无。
- **索引**:
  - `idx_login_user_created (user_id, created_at)` —— 按用户查登录历史（安全页/运营），新设备判定（同 user 是否出现过该 device）。
  - `idx_login_created (created_at)` —— FLOW-16 清理 `WHERE created_at < now-1y`；按时间筛选。
  - `idx_login_email_created (email, created_at)` —— 失败登录按邮箱审计/频控辅助。
- **外键策略**: 无（user_id 弱引用，可空，用户匿名化后保留记录但 email/ip 在保留期内自然过期清理）。
- **引用完整性**: 追加写；仅 notified 可更新。user 匿名化不回溯改写历史（1 年保留期内由清理任务删除）。
- **迁移说明**: 新建表。追加型高写。1 年后清理（FLOW-16）。

---

## 6. admin_user（后台操作员 / 聚合根）

来源：er-diagram.yml#admin_user、domain-model.md#AdminUser。FLOW-09/10。

### 表结构

| 列 | 类型 | 约束 | 默认 | 注释 |
|----|------|------|------|------|
| id | CHAR(36) | PK | — | UUID |
| name | VARCHAR(80) | NOT NULL | — | 姓名（≤80） |
| email | VARCHAR(255) | NOT NULL | — | 登录邮箱（唯一，创建后不可改） |
| password_hash | VARCHAR(255) | NOT NULL | — | 密码哈希（BCrypt，≤60 实际，留余量） |
| role_id | CHAR(36) | NOT NULL（逻辑外键→role.id） | — | 所属角色 |
| status | VARCHAR(16) | NOT NULL, CHECK in(active,disabled) | 'active' | 状态 |
| last_login_at | DATETIME(3) | NULL | NULL | 最近登录 |
| version | INT | NOT NULL | 0 | 乐观锁 |
| created_at | DATETIME(3) | NOT NULL | CURRENT_TIMESTAMP(3) | |
| updated_at | DATETIME(3) | NOT NULL | CURRENT_TIMESTAMP(3) ON UPDATE | |

- **主键**: `id`
- **唯一键**: `uk_admin_email (email)` —— 邮箱全局唯一（不可改）。
- **索引**:
  - `idx_admin_role (role_id)` —— FLOW-11 删除角色前校验「角色有成员」（ROLE_IN_USE）；按角色统计成员。
  - `idx_admin_status (status)` —— 管理员列表筛选。
- **外键策略**: 无物理 FK（role_id 逻辑外键）。
- **引用完整性**: role_id 写入前校验 role 存在；删除角色前校验无成员（应用层）。超管保护、不可删自己在领域服务校验（R3）。
- **迁移说明**: 新建表。不预置带固定公开密码的账户；新环境首次启动必须显式提供 `DREAMY_BOOTSTRAP_ADMIN_EMAIL` 与 `DREAMY_BOOTSTRAP_ADMIN_PASSWORD`（至少 12 字符），应用幂等创建并关联超管角色。仅 `DEMO_SEED_ENABLED=true` 的本地演示环境允许使用演示凭据。

---

## 7. role（角色 / 聚合根）

来源：er-diagram.yml#role、domain-model.md#Role。FLOW-11。

### 表结构

| 列 | 类型 | 约束 | 默认 | 注释 |
|----|------|------|------|------|
| id | CHAR(36) | PK | — | UUID |
| name | VARCHAR(40) | NOT NULL | — | 角色名（≤40） |
| type | VARCHAR(16) | NOT NULL, CHECK in(preset,custom) | 'custom' | 角色类型 |
| is_locked | TINYINT(1) | NOT NULL | 0 | 是否锁定（超管=1，不可改/删/降权） |
| version | INT | NOT NULL | 0 | 乐观锁 |
| created_at | DATETIME(3) | NOT NULL | CURRENT_TIMESTAMP(3) | |
| updated_at | DATETIME(3) | NOT NULL | CURRENT_TIMESTAMP(3) ON UPDATE | |

- **主键**: `id`
- **唯一键**: `uk_role_name (name)` —— 角色名唯一 [INFERRED]（domain 未显式声明，但 RBAC 通用约束 + UI 创建角色防重名；置信度中）。
- **索引**: 无额外（角色数量少，全表扫描可接受）。
- **外键策略**: 无物理 FK。被 admin_user.role_id、role_permission.role_id 引用。
- **引用完整性**: 删除前校验无关联 admin_user（ROLE_IN_USE，FLOW-11）；is_locked=true 不可删/改权限（R3）。
- **迁移说明**: 新建表。预置：超级管理员角色（type=preset, is_locked=1）+ 可选普通角色。见 DDL 种子。

---

## 8. permission（菜单权限点 / 引用数据）

来源：er-diagram.yml#permission、domain-model.md#Permission。22 项菜单 key。

### 表结构

| 列 | 类型 | 约束 | 默认 | 注释 |
|----|------|------|------|------|
| `key` | VARCHAR(64) | PK | — | 权限点 key（业务主键，如 /system/admins） |
| `group` | VARCHAR(64) | NOT NULL | — | 权限分组 |
| label | VARCHAR(80) | NOT NULL | — | 显示名 |

- **主键**: `key`（业务主键，非 UUID；与 domain-model 一致）。
- **唯一键**: 主键即唯一。
- **索引**: `idx_permission_group (group)` —— 按分组渲染权限矩阵。
- **外键策略**: 无物理 FK。被 role_permission.permission_key 引用。
- **引用完整性**: 字典表，由系统初始化写入；role_permission 写入前校验 key 存在。
- **注意**: `key`、`group` 为 MySQL 保留字，DDL 中必须反引号包裹。
- **迁移说明**: 新建表。**必须预置 22 项菜单权限种子数据**（见 DDL；具体 key 清单由 L3 按 portal-admin 路由补全，本层给出结构与示例）。

---

## 9. role_permission（角色-权限关联 / Role 组合）

来源：er-diagram.yml#role_permission、domain-model.md#RolePermission。FLOW-11（全量重写）。

### 表结构

| 列 | 类型 | 约束 | 默认 | 注释 |
|----|------|------|------|------|
| role_id | CHAR(36) | NOT NULL（逻辑外键→role.id） | — | 角色 |
| permission_key | VARCHAR(64) | NOT NULL（逻辑外键→permission.key） | — | 权限点 |

- **主键**: 复合主键 `PRIMARY KEY (role_id, permission_key)` —— 天然防重复授权，无需额外 id。
- **唯一键**: 复合主键即唯一。
- **索引**:
  - 主键 `(role_id, permission_key)` 已覆盖「按角色查权限」（登录时加载 permission keys，FLOW-09）。
  - `idx_rp_permission (permission_key)` —— 反查「哪些角色拥有某权限」（权限影响分析，可选）[INFERRED 低频，建议保留]。
- **外键策略**: 无物理 FK。
- **引用完整性**: FLOW-11 保存为事务内 `DELETE WHERE role_id=? ` + 批量 `INSERT`（全量重写）；写入前校验 role 非 is_locked、permission_key 均存在。
- **迁移说明**: 新建表。预置：超管角色 × 全部 22 permission（或由应用层「超管隐式全权限」处理，见下注）。
  > 注：domain-model 称「超管 Role 隐式拥有全部 22 项 permission key」。两种实现：(a) 显式写满 role_permission；(b) 应用层对 is_locked 角色短路返回全权限。推荐 (b) 减少维护，但 DDL 提供 (a) 的种子选项以便审计可见。L3 定夺。

---

## 10. admin_session（后台会话 / AdminUser 弱聚合）

来源：er-diagram.yml#admin_session、domain-model.md#AdminSession。FLOW-09/10。access 8h 无 refresh。

### 表结构

| 列 | 类型 | 约束 | 默认 | 注释 |
|----|------|------|------|------|
| id | CHAR(36) | PK | — | UUID |
| admin_id | CHAR(36) | NOT NULL（逻辑外键→admin_user.id） | — | 所属管理员 |
| token_id | VARCHAR(64) | NOT NULL | — | JWT jti |
| ip | VARCHAR(45) | NULL | NULL | IP |
| device | VARCHAR(128) | NULL | NULL | 设备 |
| status | VARCHAR(16) | NOT NULL, CHECK in(active,revoked) | 'active' | 状态 |
| last_active_at | DATETIME(3) | NULL | NULL | 最近活跃 |
| created_at | DATETIME(3) | NOT NULL | CURRENT_TIMESTAMP(3) | |

- **主键**: `id`
- **唯一键**: `uk_admin_session_token (token_id)` —— jti 唯一。
- **索引**:
  - `idx_admin_session_admin_status (admin_id, status)` —— 禁用管理员级联 revoke（FLOW-10）；查活跃后台会话。
- **外键策略**: 无物理 FK（admin_id 逻辑外键）。
- **引用完整性**: admin 禁用即事务内级联 `UPDATE status=revoked`（FLOW-10）。
- **迁移说明**: 新建表。无 refresh 字段（admin 无续期）。建议纳入保留清理（revoked 30d，[INFERRED] 复用会话保留策略）。

---

## 11. operation_log（操作日志 / 追加型，只读不可删）

来源：er-diagram.yml#operation_log、domain-model.md#OperationLog。FLOW-17。保留 1–3 年，注销不删。

### 表结构

| 列 | 类型 | 约束 | 默认 | 注释 |
|----|------|------|------|------|
| id | CHAR(36) | PK | — | UUID |
| operator_id | CHAR(36) | NULL（弱引用→admin_user.id；系统操作为 NULL） | NULL | 操作者 |
| operator_name | VARCHAR(80) | NOT NULL | — | 操作者名（系统归并=「系统」） |
| action | VARCHAR(32) | NOT NULL, CHECK in(14 种枚举) | — | 操作类型 |
| target | VARCHAR(255) | NULL | NULL | 操作目标 |
| ip | VARCHAR(45) | NULL | NULL | IP |
| user_agent | VARCHAR(512) | NULL | NULL | UA |
| changes | JSON | NULL | NULL | 变更前后对比 {before, after} |
| created_at | DATETIME(3) | NOT NULL | CURRENT_TIMESTAMP(3) | 操作时间 |

- **action CHECK 取值**: 登录 / Google 登录 / Apple 登录 / 创建管理员 / 编辑管理员 / 删除管理员 / 禁用管理员 / 重置密码 / 创建角色 / 编辑角色 / 删除角色 / 权限变更 / 账户合并 / 强制下线 / 认证配置变更（与 er-diagram 一致，共 15 个值——含「Google 登录/Apple 登录」拆分）。
  > 修正：er-diagram action 列实含 15 个值（登录、Google 登录、Apple 登录…认证配置变更）。CHECK 按 15 项落地。
- **主键**: `id`
- **唯一键**: 无。
- **索引**:
  - `idx_oplog_created (created_at)` —— FLOW-17/操作日志页按时间倒序分页（最高频）；保留清理按时间。
  - `idx_oplog_operator_created (operator_id, created_at)` —— 按操作者筛选。
  - `idx_oplog_action_created (action, created_at)` —— 按操作类型筛选。
- **外键策略**: 无物理 FK（operator_id 弱引用，系统操作为 NULL）。
- **引用完整性**: **只读不可删**——不提供 update/delete 接口（EDGE-018）。operator 即使删除/匿名化，日志保留 operator_name 快照（正当利益 EDGE-026）。
- **迁移说明**: 新建表。追加型超高写、长保留（1–3 年）。**建议按 created_at RANGE 分区**（按月/季）以支持高效保留清理与查询——Bootstrap 阶段先不分区，于 DDL 注释中给出分区演进建议。

---

## 12. auth_config（认证配置 / 单例聚合）

来源：er-diagram.yml#auth_config、domain-model.md#AuthConfig。FLOW-13。

### 表结构

| 列 | 类型 | 约束 | 默认 | 注释 |
|----|------|------|------|------|
| id | INT | PK（单例固定=1） | 1 | 单例主键 |
| email_enabled | TINYINT(1) | NOT NULL | 1 | 邮箱登录（恒开，不可关） |
| google_enabled | TINYINT(1) | NOT NULL | 1 | Google 登录开关 |
| apple_enabled | TINYINT(1) | NOT NULL | 1 | Apple 登录开关 |
| otp_length | TINYINT | NOT NULL, CHECK in(4,6,8) | 6 | OTP 长度 |
| otp_ttl_minutes | INT | NOT NULL, CHECK between 1 and 30 | 10 | OTP 有效期（分钟） |
| otp_resend_seconds | INT | NOT NULL, CHECK between 10 and 120 | 60 | 重发间隔（秒） |
| otp_max_attempts | INT | NOT NULL, CHECK between 3 and 10 | 5 | 最大尝试次数 |
| min_methods | INT | NOT NULL, CHECK between 1 and 3 | 1 | 最少连接方式数 |
| google_client_id | VARCHAR(255) | NULL | NULL | Google 客户端 ID（只读展示） |
| apple_service_id | VARCHAR(255) | NULL | NULL | Apple Service ID（只读展示） |
| updated_at | DATETIME(3) | NOT NULL | CURRENT_TIMESTAMP(3) ON UPDATE | |

- **主键**: `id`（单例，固定 id=1）。
- **唯一键**: 主键即唯一；应用层强制只读 id=1 行。
- **索引**: 无（单行）。
- **外键策略**: 无。
- **引用完整性**: email_enabled 恒为 1（应用层 + 默认值保障，R 不变量）；OTP 数值越界由 CHECK + 应用层双重拒绝（EDGE-019，422 CONFIG_OUT_OF_RANGE）。
- **迁移说明**: 新建表。**必须预置单行 id=1 默认配置**（见 DDL 种子）。保存触发 store:authconfig 缓存失效（FLOW-13，应用层）。

---

## 13. email_template（邮件模板 / 配置实体）

来源：er-diagram.yml#email_template、domain-model.md#EmailTemplate。三语 × 4 类。

### 表结构

| 列 | 类型 | 约束 | 默认 | 注释 |
|----|------|------|------|------|
| id | CHAR(36) | PK | — | UUID |
| code | VARCHAR(32) | NOT NULL, CHECK in(otp,new_device,change_primary,account_deleted) | — | 模板类型 |
| locale | VARCHAR(8) | NOT NULL, CHECK in(en,es,fr) | — | 语言 |
| subject | VARCHAR(255) | NOT NULL | — | 邮件主题 |
| body | TEXT | NOT NULL | — | 邮件正文（HTML/文本） |
| updated_at | DATETIME(3) | NOT NULL | CURRENT_TIMESTAMP(3) ON UPDATE | |

- **主键**: `id`
- **唯一键**: `uk_template_code_locale (code, locale)` —— 同类型同语言唯一（4 code × 3 locale = 12 条）。
- **索引**: 唯一键覆盖「按 code+locale 取模板」（发邮件最高频路径，FLOW-01/06/08/14）。
- **外键策略**: 无。
- **引用完整性**: 配置表；发送时按 (code, locale) 精确命中，缺失回退默认 locale（应用层）。
- **迁移说明**: 新建表。**预置 12 条模板种子**（4 类 × 3 语；DDL 给出结构与 en 示例，全文案由 L3/内容方补全）。

---

## 14. 表清单与依赖关系汇总

| # | 表名 | 聚合 | 主键 | 关键唯一键 | 类型 |
|---|------|------|------|-----------|------|
| 1 | user | User(根) | id(uuid) | uk(email) | 可变+软删 |
| 2 | user_identity | User(内) | id(uuid) | uk(provider,provider_uid) | 可变 |
| 3 | otp_code | OtpCode(根) | id(uuid) | — | 高写高删 |
| 4 | user_session | User(弱) | id(uuid) | uk(token_id) | 高写 |
| 5 | login_history | LoginHistory | id(uuid) | — | 追加 |
| 6 | admin_user | AdminUser(根) | id(uuid) | uk(email) | 可变 |
| 7 | role | Role(根) | id(uuid) | uk(name) | 可变 |
| 8 | permission | Permission | key | pk(key) | 字典 |
| 9 | role_permission | Role(组合) | (role_id,permission_key) | 复合 PK | 关联 |
| 10 | admin_session | AdminUser(弱) | id(uuid) | uk(token_id) | 高写 |
| 11 | operation_log | OperationLog | id(uuid) | — | 追加只读 |
| 12 | auth_config | AuthConfig(单例) | id(int=1) | — | 单行 |
| 13 | email_template | EmailTemplate | id(uuid) | uk(code,locale) | 配置 |

**逻辑外键依赖图**（无物理 FK，应用层维护）:

```
user ←── user_identity (user_id)
user ←── user_session (user_id)
user ←·· login_history (user_id, 弱可空)
role ←── admin_user (role_id)
role ←── role_permission (role_id)
permission ←── role_permission (permission_key)
admin_user ←── admin_session (admin_id)
admin_user ←·· operation_log (operator_id, 弱可空)
```

---

## 15. 索引覆盖 vs 高频查询路径对照（自检矩阵）

| 查询路径 | 来源 | 命中索引 |
|---------|------|---------|
| OIDC 登录按 (provider,provider_uid) 查 identity | FLOW-03 | uk_identity_provider_uid |
| OTP 校验按 (email,status=pending) 查 otp | FLOW-02 | idx_otp_email_status |
| refresh 按 refresh_token_id 查 session | FLOW-04 | idx_session_refresh |
| 按 user_id 查会话/撤销 | FLOW-07/12 | idx_session_user_status |
| 按 user_id 查全部凭证 | FLOW-05/06 | idx_identity_user_id |
| 管理员登录按 email 查 admin | FLOW-09 | uk_admin_email |
| 角色登录加载 permission keys | FLOW-09 | role_permission PK(role_id,..) |
| 删角色前查成员 | FLOW-11 | idx_admin_role |
| OperationLog 按时间倒序分页 | FLOW-17 | idx_oplog_created |
| 匿名化任务 deleted+deleted_at<30d | FLOW-08/16 | idx_user_status_deleted_at |
| OTP 清理 status+created_at<24h | FLOW-16 | idx_otp_status_created |
| 会话清理 revoked+created_at<30d | FLOW-16 | idx_session_status_created |
| 登录历史清理 created_at<1y | FLOW-16 | idx_login_created |
| 取邮件模板 (code,locale) | FLOW-01/14 | uk_template_code_locale |

全部高频路径均有索引覆盖 ✓

---

## 16. 推断设计清单（[INFERRED]）

| 项 | 表 | 推断依据 | 置信度 |
|----|----|---------|--------|
| relay_valid 列 | user_identity | domain-model 不变量 + FLOW-03「Apple relay 失效标记 relay_valid=false」(FUNC-029)，er-diagram 漏列 | 高 |
| uk_role_name 唯一 | role | RBAC 通用约束 + UI 创建角色防重名；domain 未显式声明 | 中 |
| version 乐观锁列 | user/otp_code/user_session | data-flow BE-DIM-4「乐观锁版本号串行化」「滑动续期并发」 | 高 |
| admin_session 纳入 30d 清理 | admin_session | 复用 user_session 保留策略；R6 未单列 admin_session | 中 |
| idx_rp_permission 反查索引 | role_permission | 权限影响分析（低频），可选保留 | 低 |
| 默认值（tier=regular/otp_length=6 等） | 多表 | 取 AuthConfig 区间中常见默认；L3 可调 | 中 |

---

## 17. 风险记录与后续跟进

1. **CHAR(36) UUID vs BINARY(16)**：本设计用 CHAR(36) 优先可读性/排查便利，代价是索引体积更大。若后续用户量级达千万级，建议评估 BINARY(16) + 应用层转换。由 L3/性能压测确认。
2. **operation_log 分区**：长保留 + 高写，Bootstrap 不分区。达百万行/月后建议按 created_at RANGE 月分区。已在 DDL 注释标注。
3. **超管全权限实现方式**：role_permission 显式写满 vs 应用层短路（见表 9 注），需 L3 定夺；影响是否预置超管 × 22 权限种子。
4. **permission 22 项 key 清单**：本层给结构与示例，具体 key 由 portal-admin 路由清单补全（L3）。
5. **email_template 文案**：本层给结构 + en 示例，三语全文案由内容方/L3 补全。
6. **otp_code 唯一性**：未设 (email,status) 唯一（允许历史多条 pending 前先 UPDATE 失效旧码）。若并发发码需强一致，可考虑应用层加 Redis 锁（已有 otp:resend:{email} 频控键，FLOW-01）。

---

## 18. 自检清单执行结果

- [x] er-diagram.yml 13 实体全部覆盖（user/user_identity/otp_code/user_session/login_history/admin_user/role/permission/role_permission/admin_session/operation_log/auth_config/email_template）
- [x] 每表给出字段名/类型/约束/默认值/注释、主键、唯一键、索引、外键策略、迁移说明
- [x] 唯一约束覆盖核心不变量：(provider,provider_uid)、admin email、token_id、(code,locale)
- [x] 索引覆盖全部高频查询路径（见第 15 节矩阵，14/14 命中）
- [x] 保留/清理路径均有支撑索引（otp 24h / session 30d / login 1y / 匿名化 30d）
- [x] 无物理 FOREIGN KEY（逻辑外键 + 应用层维护，符合 l2-database-check 外键禁止）
- [x] utf8mb4 + DATETIME(3) UTC + 软删/匿名化字段（deleted_at/anonymized/anonymized_at）齐备
- [x] 枚举用 VARCHAR+CHECK（不用 MySQL ENUM），利于演进
- [x] 乐观锁 version 列覆盖并发竞争表
- [x] 单例 auth_config / 字典 permission / 配置 email_template 预置种子已规划
- [x] 推断设计均标注 [INFERRED] 并附依据与置信度
- [x] 完整 DDL 见 identity-ddl.sql；保留匿名化方案见 identity-retention-anonymization.md
