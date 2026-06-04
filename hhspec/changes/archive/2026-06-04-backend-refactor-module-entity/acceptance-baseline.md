# 验收基准：backend-refactor-module-entity

生成时间：2026-06-02T12:45:00Z
类型：COMPLIANCE（规范对齐重构，无原型/无 UI 新增）

## FUNC — 功能验收（行为不变性）

重构不改变业务行为，验收核心是「行为等价 + 规范达标」。

| 编号 | 场景 | 验收标准 |
|------|------|---------|
| FUNC-001 | 工程编译 | JDK25 + Gradle 单工程 `./gradlew build` 成功，无子模块 |
| FUNC-002 | DDL 自动建表 | 启动后 @EnableMysql 基于 @Table 实体自动建/改表，13 张表结构正确 |
| FUNC-003 | Store 端登录流程 | 邮箱/OTP/OIDC 登录、会话签发、刷新 token 行为与重构前等价 |
| FUNC-004 | Admin 端登录与权限 | 管理员登录、角色权限校验、审计日志行为等价 |
| FUNC-005 | 用户运营 | 用户查询/封禁/合并/匿名化/保留调度行为等价 |
| FUNC-006 | 分布式锁 | 引入 huihao-redis IdLockSupport，并发敏感操作（合并/会话）加锁正确 |
| FUNC-007 | 前后端契约一致 | 两门户 id 改 number 后，登录/列表/详情端到端可用 |

## COMPLIANCE — 规范达标验收

| 编号 | 检查项 | 验收标准 |
|------|--------|---------|
| COMP-001 | 单模块多 domain | 无 common/store/admin/app 子模块；按 domain 分包，无技术层切分残留 |
| COMP-002 | 实体继承基类 | 13 实体全部继承 LongAuditableEntity；删除手写 createdAt/updatedAt |
| COMP-003 | @Table/@Column 注解 | 每实体 @Table(name,comment)，每字段 @Column(name,definition) 含完整 DDL 定义 |
| COMP-004 | DBConst 齐全 | 每实体配套 {Entity}DBConst 列名常量接口，无硬编码列名 |
| COMP-005 | Long 自增主键 | 全部表 Long id + IdType.AUTO；permission/role_permission 加代理主键 + 唯一索引 |
| COMP-006 | huihao 依赖 | huihao-mysql/base/web/redis 0.3.9.45-jdk25 从 Nexus 解析成功 |
| COMP-007 | 统一响应/分页 | 使用 huihao-base R<T> + Paginated<T>，移除自定义 PageResult |
| COMP-008 | LocalDateTime | OffsetDateTime 全量替换为 LocalDateTime（实体+DTO+service） |
| COMP-009 | 禁用配置清理 | application.yml 移除 mapper-locations 等规范禁止项 |

## EDGE — 边界/风险验收

| 编号 | 场景 | 验收标准 |
|------|------|---------|
| EDGE-001 | id 序列化精度 | Long id JSON 序列化无 JS 精度丢失（评估 id→string 策略或确认自增范围安全） |
| EDGE-002 | 外键迁移完整 | userId/roleId/adminId/permissionId 等全部 String→Long，无遗漏 |
| EDGE-003 | IdGenerator 废弃 | 应用层 IdGenerator 移除后，插入取回自增 id 正常 |
| EDGE-004 | 乐观锁保留 | @Version 字段（user/role/session/otp/admin_user）迁移后乐观锁仍生效 |

## 测试要求

- 集成测试用 Testcontainers 真 MySQL + Redis，严格零 Mock（沿用现有 application-it.yml 模式）
- 重构后所有现有测试用例（调整 id 类型断言后）必须通过

## 验证手段

1. `./gradlew clean build` 编译 + 单测
2. 启动应用 + 真 MySQL，验证 DDL-auto 建表
3. Testcontainers 集成测试套件
4. 前端两门户 `pnpm build` / `npm run build` 类型检查通过
5. 端到端：启动后端 + 两门户，跑通登录主流程
