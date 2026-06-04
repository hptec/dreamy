# 关键决策：backend-orm-compliance-refactor

生成时间：2026-06-04T16:10:00+08:00

## 决策 1：OTP 并发控制 — Redis 分布式锁替代 DB 行锁

- **选择**：删除 `OtpCodeMapper.lockPendingByEmail`（`@Select ... FOR UPDATE`），改为 `IdLockSupport.onIdLock("otp:verify", email, ...)` 阻塞锁，锁范围仅覆盖 `consumeValidCode`（STEP-01~03 校验阶段），`resolveOrMerge`/`openStoreSession` 在锁外执行
- **理由**：DB 行锁在高并发时同一邮箱请求排队阻塞连接；Redisson 阻塞锁将等待转移到 Redis 层，@Version 乐观锁兜底防并发绕过 attempts；Redis 频控（5次/时）已在前面拦截攻击流量，正常用户场景极少触发排队
- **备选**：保留 `FOR UPDATE` 改 `.last("FOR UPDATE")` — 被否决，不解决高并发问题；tryLock 立即失败 — 被否决，正常用户偶发并发会产生误报错误

## 决策 2：OperationLogMapper 流式导出 — 分页轮询替代 ResultHandler

- **选择**：删除 `@Select(<script>)` + `@Options(fetchSize=MIN_VALUE)` + `ResultHandler`，改为 Service 层循环分页轮询（每页 1000 条 `LambdaQueryWrapper`），在调用方追加写出文件
- **理由**：彻底消除 native SQL，符合 PD 规范；operation_log 有 `created_at` 索引，分页游标（`id < lastId`）性能可控；92 天时间窗上限最多约 276 次查询（百万行/月 × 3月 ÷ 1000）
- **备选**：保留 `@Select(<script>)` 加注释 — 用户明确要求消除

## 决策 3：DBConst 全量改造

- **选择**：
  1. 创建 `domain/consts/CommonDBConst.java`，抽取 `ID`/`CREATED_AT`/`UPDATED_AT`
  2. 迁移 13 个 DBConst 文件：`domain/{聚合根}/repository/` → `domain/{聚合根}/consts/`
  3. 补齐 `RoleDBConst`/`PermissionDBConst`/`RolePermissionDBConst` 的基类列（引用 CommonDBConst）
  4. `@Column(name = ...)` 的字符串值改为引用 DBConst 常量（消除 dead code）
  5. `B1` `UpdateWrapper` 硬编码改为 `LambdaUpdateWrapper` + `OtpCodeDBConst` 常量
- **理由**：DBConst 建好但从未被引用等于空转；迁到 `consts/` 子包是规范强制要求；`@Column` 引用常量后重命名列只需改一处

## 决策 4：native SQL 简单查询消除

- **选择**：A2/A4/A5 三个 `@Select` 移入 Service 层用 `LambdaQueryWrapper` 替代；A3（`@Select ... JOIN`）改为分步查询 + 内存聚合（禁 JOIN 原则）
- **理由**：均为简单条件查询，LambdaQueryWrapper 完整表达，无技术障碍

## 决策 5：全表扫描显式化

- **选择**：`permissionMapper.selectList(null)` 改为 `lambdaQuery().isNotNull(PermissionEntity::getId)` 显式条件
- **理由**：消除隐式全表扫描写法，即使体量小也应明确语义

## 保留项（不在本次范围）

- `@Table` + `@TableName` 并存：规范做法，vmedi 样板同样写法，保留
- 分页模型混用（IPage/PageData/Paginated）：保留现状，后续统一

## 后端关键决策

> 来源：Phase 2 深度探索

### BE-DIM-4 状态机/并发/事务

- **决策**：OTP 验证锁范围仅覆盖 STEP-01~03（consumeValidCode），锁工具用 IdLockSupport.onIdLock（阻塞锁），@Version 乐观锁兜底
- **触发信号**：user_decision（高并发优化需求）
- **理由**：resolveOrMerge/openSession 无需互斥，缩短锁持有时间；@Version 防竞态绕过 attempts
- **约束**：`verifyOtp` 方法内，`onIdLock` 包裹 `consumeValidCode` 调用，`@Transactional` 仍覆盖完整方法，锁释放先于事务提交（try-finally 确保）
