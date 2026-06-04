# 关键决策：backend-refactor-module-entity

> 类型：COMPLIANCE（规范对齐重构，无原型/无新功能）
> 权威样板：`/Volumes/MAC/workspace/vmedi/vmedi-backend`
> PD 规范：`hh-factory/.../pd/lib/tech/java/{project-structure,orm/mybatis-plus-mysql,redis,base}`

## 背景

后端 `dreamy/backend`（identity-auth 身份认证）存在三处偏离 PD 规范，需重构对齐：
1. 模块划分与 domain 不清（4 个 Gradle 子模块按技术层切分）
2. 实体设计未按 PD 规范（无 huihao-mysql 注解/基类）
3. mysql/redis 未使用 huihao-* 库

---

## 决策 1：包名保持 com.dreamy.identity

- **选择**：不改为 PD 规范的 `huihao.` 前缀，保留 `com.dreamy.identity`
- **理由**：全量重命名 80+ 源文件包路径风险高、收益低；规范核心价值在结构与实体设计，包前缀非关键
- **备选**：改为 `huihao.dreamy.identity`（被否决：批量重命名成本高于收益）

## 决策 2：Gradle 合并为单工程，按 domain 分包

- **选择**：废除 `common`/`store`/`admin`/`app` 四子模块，合并为**单 Gradle 工程**，无独立 app 子模块；工程内按业务 domain 分包
- **理由**：PD 规范要求"工程 1 个模块，内含多个 domain"；现有按技术层切分（common 放全部实体+service，store/admin 放 controller）导致 domain 边界模糊
- **目标包结构**（参照 PD `02-package-conventions.md` + vmedi 样板）：

```
com.dreamy.identity/
├── IdentityApplication.java        # 启动类（@SpringBootApplication @EnableMysql）
├── config/                         # MyBatisPlusConfig, JetCacheConfig, RedisConfig, AutoFillHandler
├── props/                          # @ConfigurationProperties（JwtProperties 等）
├── enums/                          # 共享枚举
├── error/                          # BizException, ErrorCode, GlobalExceptionHandler, InfraException
├── i18n/                           # MessageResolver, RequestLocaleContext
├── security/                       # AuthContext, JwtTokenProvider, AuthPrincipal, TokenPair
├── util/                           # IdGenerator, OtpGenerator
├── infra/                          # mail/, oidc/, 频控与会话缓存
├── controller/                     # 扁平：store 端 + admin 端 controller 同级
│   ├── mapper/                    # MapStruct 转换器
│   └── pojo/                      # 请求/响应 POJO
└── domain/
    ├── user/{entity,repository,service,dto}        # UserEntity, UserIdentityEntity
    ├── role/{entity,repository,service,dto}        # RoleEntity, PermissionEntity, RolePermissionEntity
    ├── admin/{entity,repository,service,dto}       # AdminUserEntity
    ├── session/{entity,repository,service,dto}     # UserSessionEntity, AdminSessionEntity
    ├── otp/{entity,repository,service,dto}         # OtpCodeEntity
    ├── authconfig/{entity,repository,service,dto}  # AuthConfigEntity, EmailTemplateEntity
    └── audit/{entity,repository,service,dto}       # LoginHistoryEntity, OperationLogEntity
```

- **依赖方向**：单工程内无跨模块依赖问题；domain 之间通过 service 接口协作
- **备选**：服务+API+app 三模块（被否决：本项目无服务间 Feign 调用，API 模块多余）；保留 4 模块（被否决：违背规范核心诉求）

## 决策 3：实体改用 huihao-mysql 注解 + 继承 LongAuditableEntity

- **选择**：所有实体继承 `huihao.mysql.auditable.LongAuditableEntity`，用 `@Table`/`@Column` + MyBatis-Plus `@TableName`/`@TableField` 双注解；每个实体配套 `{Entity}DBConst` 列名常量接口
- **实体改造对照**（每个 @Column 必须含完整 definition + comment）：

| 现状 | 改造后 |
|------|--------|
| `@TableName("user")` 无基类 | `@Table(name="user",comment=...)` + `extends LongAuditableEntity` |
| 手写 `createdAt`/`updatedAt`(OffsetDateTime) | 基类提供（LocalDateTime + @TableField fill 自动填充），删除手写字段 |
| 字段无 `@Column`/`@TableField` | 每字段 `@Column(name,definition)` + `@TableField`（主键除外） |
| `OffsetDateTime` | `LocalDateTime`（全量替换，含 DTO/service） |
| 无 DBConst | `domain/{聚合根}/repository/{Entity}DBConst` 接口 |

- **DDL 自动管理**：启动类加 `@EnableMysql`（auto=update, scanPackages=com.dreamy.identity.domain），由 huihao-mysql 的 DDLInit 扫描 @Table 自动建表/改表

## 决策 4：主键全量迁移 String → Long 自增（breaking change）

- **选择**：**严格全量 Long**。所有表统一 `Long id` + `IdType.AUTO`（bigint AUTO_INCREMENT）
- **特殊表处理**：
  - `permission`：废除业务码 `key` 主键 → 新增 `Long id` 代理主键，`key`(`perm_code`) 改唯一索引
  - `role_permission`：废除复合主键 → 新增 `Long id` 代理主键，`(role_id, permission_id)` 改唯一索引
  - `auth_config`：单例配置 Integer id → Long id（固定 id=1）
- **连锁影响（全链路）**：
  - 所有外键字段 String→Long：`userId`/`roleId`/`adminId`/`permissionKey`→`permissionId` 等
  - `IdGenerator`（应用层 ID 生成）废弃，改由数据库自增；插入后取回自增 id
  - 所有 DTO 的 id/外键字段 String→Long
  - **前端 API 契约**：id 从字符串变数字（见决策 6）
- **理由**：用户明确选择 PD 首选方案，追求最彻底的规范对齐
- **风险**：JS number 安全整数上限 2^53；自增 id 短期无溢出风险，但需注意 JSON 序列化（建议后续评估 id 转 string 序列化策略）
- **备选**：混合主键（被否决：用户要求严格全量）；保留 String（被否决：偏离 PD 首选）

## 决策 5：mysql/redis 引入 huihao-* 库

- **选择**：
  - 引入 `com.huihao:huihao-mysql`（DDL-auto + 审计基类 + @Table/@Column 注解）
  - 引入 `com.huihao:huihao-base`（R<T> 统一响应 + Paginated<T> 分页 + BizException + IntEnum/StrEnum）
  - 引入 `com.huihao:huihao-web`（web 层支持）
  - 引入 `com.huihao:huihao-redis`（IdLockSupport 分布式锁，Redisson 封装）+ 启动类 `@EnableHuiHaoRedis`
  - 版本统一 `0.3.9.45-jdk25-SNAPSHOT`（适配 JDK25）
  - 仓库：Nexus 私仓 `http://116.62.167.155:8081/repository/maven-public/` + `/maven-snapshots/`（allowInsecureProtocol）
- **Redis 缓存保留**：JetCache 两级缓存（消费端只读接口）+ StringRedisTemplate（OTP 频控/会话有效性缓存）**保持不动**
- **理由**：huihao-redis 只提供分布式锁+健康检查，不是缓存框架，无法替代 JetCache 两级缓存与频控场景；锁能力由 huihao-redis 补齐
- **统一响应改造**：现有自定义 `PageResult<T>` → `huihao.base.Paginated<T>`；自定义 `ErrorBody`/`R` → huihao-base `R<T>`
- **备选**：全面改用 huihao-redis（被否决：丢失两级缓存能力，工作量大）；Redis 保持现状不引入 huihao-redis（被否决：未满足"redis 用 huihao-*"诉求）

## 决策 6：前后端一起改（端到端对齐）

- **选择**：本次 change 同步改前端两门户的 id 类型与 API 契约
  - `portal-admin`（Vue3）：TypeScript 类型定义 id String→number，API 调用参数适配
  - `portal-store`（Next.js）：同上
- **理由**：主键 String→Long 破坏 API 契约，分两次改会导致中间态前后端不兼容无法联调
- **备选**：只重构后端（被否决：用户要求前后端一起改，避免契约断裂）

---

## 验收要点

1. 编译通过（JDK25 + Gradle 单工程）
2. `@EnableMysql` DDL-auto 能基于 @Table 实体自动建表
3. 集成测试（Testcontainers 真 MySQL + Redis）通过，零 Mock
4. 所有实体继承 LongAuditableEntity，配套 DBConst 齐全
5. 包结构按 domain 分包，无技术层切分残留
6. huihao-mysql/base/web/redis 依赖可从 Nexus 解析
7. 前后端 id 契约一致（数字类型），端到端登录/会话/权限流程可用

## 决策 7：响应契约全面 R<T> 化（阶段 E 细化，2026-06-03）

- **选择**：全面采用 huihao-base `R<T>` 包络所有响应
- **成功响应**：`R.ok(data)` → `{code:0, message:null, data:...}`；分页数据 data 用 `huihao.page.Paginated<T>`
- **错误响应**：`R.fail` 形态 `{code, message, data}`，但因 huihao `R` 无 details 字段，**字段级错误细节（校验错误/remaining_attempts/remaining_resend_seconds）装进 `R.data`**
- **HTTP 状态码**：**保留现有精细状态码**（40100→401, 40300→403, 40000→422, 40901→409 等），body 包 R。不采用统一 HTTP 200 模式
- **理由**：用户要求彻底对齐 PD 规范；但认证系统的字段级错误细节和精细 HTTP 语义不能丢，故 details 降级装入 data、HTTP 状态码保留
- **前端影响**：所有接口解析改为读 `resp.data.data`（成功）；错误读 `resp.data.code/message/data`（details 从 data 取）
