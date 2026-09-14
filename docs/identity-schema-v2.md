# identity 域数据架构设计 v2.2（分区化 + Redis 主存会话）

> 状态：**待用户评审**。评审通过后按「§8 代码影响清单」重做 P1 数据层。
> 演进记录：v1 单库单表（Java 原样）→ 讨论定稿本版。

## 0. 设计决策记录（四轮讨论定稿）

| # | 决策 | 要点 |
|---|------|------|
| 1 | 统一分区键 K = user_id | 一切按用户的查询经 K 裁剪单分区 |
| 2 | 路由层按登录类型拆 3 张表 | email / google / apple 各一张，避免超级大表 |
| 3 | 路由表 KEY 哈希分区预建 25 区 | 容量 1 亿（满载每区 400 万）；等值查询自动裁剪；唯一键=分区键，全局唯一由 DB 强制 |
| 4 | 主档表 RANGE(id) 追加式 | user/identity 每 400 万一段，pmax 哨兵 + 空区 REORGANIZE 扩容（秒级） |
| 5 | user_session **Redis 主存** | TTL=令牌有效期、DEL 即撤销、DB 双写冷备、Redis 故障降级 DB 点查 |
| 6 | otp_code **DB 主存**（修订） | Redis 主存有 key-miss 语义歧义（空库重启误判在途码）且收益微小（登录频率=低频）；DB **月分区**，Redis 只做频控 |
| 7 | login_history 月分区 | 自动追加；DROP 清理**暂缓启用**（见决策 12） |
| 8 | admin/字典/配置表不分区 | 量级 ≤ 千行，分区纯开销 |
| 9 | 自动扩展 = Rust 内置调度任务 | tokio 定时，幂等加锁，随服务部署 |
| 10 | 复杂分析查询走 CDC → 分析库预案 | OLTP 表不做列式；MySQL 自建无列式引擎，版本保持 8.4 LTS |
| 11 | (v2.1→v2.2 修订)会话冷备表**不分区** | v2.1 曾定月分区;v2.2 按用户指令改普通表(简单优先,暂不清理则分区收益无从体现) |
| 12 | (v2.2)清理策略**全面暂缓** | OTP 改月分区;会话冷备不分区;**暂不删除任何日志**(保留完整审计);DROP PARTITION 维护路径保留实现但默认不执行,后续按磁盘水位/行数阈值另行下令启用 |

## 1. 总体拓扑

```
登录请求(email/google/apple)
   │ ① 路由解析:凭证键 → user_id   【KEY 哈希分区,裁剪单区】
   ▼
identity_email / identity_google / identity_apple   (各 25 区,1 亿)
   │ ② user_id = K
   ▼
user ──┬─ RANGE(id) 每 400 万段,co-location          【K 裁剪】
       └─ user_identity ── RANGE(user_id) 同边界      【K 裁剪】

会话:  Redis 主存(每请求校验 ~0.1ms) + user_session 冷备表(普通表,不分区)
验证码: otp_code 表 DB 主存(月分区) + Redis 频控
审计:  login_history 月分区
```

> 两张时序表(login_history/otp_code)统一模式:pmax 哨兵建表 + 启动期生成实际月分区 +
> 维护任务自动追加新月分区;**DROP 清理暂缓**(v2.2 决策 12,保留全部审计数据)。

## 2. MySQL DDL 全量（dreamy_server 库 v2）

### 2.1 路由层（3 张，KEY 哈希 25 区）

```sql
CREATE TABLE IF NOT EXISTS `identity_email` (
  `email`      VARCHAR(255) NOT NULL COMMENT '归一化小写;全局唯一由本表强制',
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`email`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='路由:邮箱 → 主账号(邮箱注册/归并域)'
  PARTITION BY KEY (`email`) PARTITIONS 25;

CREATE TABLE IF NOT EXISTS `identity_google` (
  `google_sub` VARCHAR(255) NOT NULL COMMENT 'Google OIDC sub',
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`google_sub`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='路由:Google 凭证 → 主账号'
  PARTITION BY KEY (`google_sub`) PARTITIONS 25;

CREATE TABLE IF NOT EXISTS `identity_apple` (
  `apple_sub`  VARCHAR(255) NOT NULL COMMENT 'Apple OIDC sub',
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `relay_email` VARCHAR(255) NULL COMMENT '隐藏邮箱时的中继地址(展示用,不参与归并)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`apple_sub`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='路由:Apple 凭证 → 主账号'
  PARTITION BY KEY (`apple_sub`) PARTITIONS 25;
```

### 2.2 主档层（RANGE(id)，co-location 400 万段）

```sql
CREATE TABLE IF NOT EXISTS `user` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `email`         VARCHAR(255) NOT NULL COMMENT '展示快照;唯一性由 identity_email 强制',
  `email_verified` TINYINT(1) NOT NULL DEFAULT 0,
  `locale_pref`   VARCHAR(8) NULL,
  `name`          VARCHAR(100) NULL,
  `phone`         VARCHAR(32) NULL,
  `tier`          TINYINT NOT NULL DEFAULT 1,
  `status`        TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 2禁用 3删除 4匿名化',
  `avatar`        VARCHAR(512) NULL,
  `joined_at`     DATETIME NULL,
  `deleted_at`    DATETIME NULL,
  `anonymized`    TINYINT(1) NOT NULL DEFAULT 0,
  `anonymized_at` DATETIME NULL,
  `version`       INT NOT NULL DEFAULT 0,
  `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_created_at` (`created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='主账号(自然人)'
  PARTITION BY RANGE (`id`) (
    PARTITION p0 VALUES LESS THAN (4000000),
    PARTITION p1 VALUES LESS THAN (8000000),
    PARTITION p2 VALUES LESS THAN (12000000),
    PARTITION pmax VALUES LESS THAN MAXVALUE
  );

CREATE TABLE IF NOT EXISTS `user_identity` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'REST 契约暴露(解绑路径参数)',
  `user_id`      BIGINT UNSIGNED NOT NULL,
  `provider`     TINYINT NOT NULL COMMENT '1邮箱 2Google 3Apple',
  `provider_uid` VARCHAR(255) NOT NULL COMMENT '档案冗余;全局唯一由路由表强制',
  `identifier`   VARCHAR(255) NULL,
  `is_primary`   TINYINT(1) NOT NULL DEFAULT 0,
  `verified`     TINYINT(1) NOT NULL DEFAULT 0,
  `connected`    TINYINT(1) NOT NULL DEFAULT 1,
  `hidden_email` TINYINT(1) NOT NULL DEFAULT 0,
  `relay_email`  VARCHAR(255) NULL,
  `relay_valid`  TINYINT(1) NULL,
  `bound_at`     DATETIME NULL,
  `last_login_at` DATETIME NULL,
  `created_at`   DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`, `user_id`),
  UNIQUE KEY `uk_user_provider` (`user_id`, `provider`),
  KEY `idx_provider_uid` (`provider`, `provider_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='凭证档案(登录方式元数据);与 user 分区边界一致(co-location)'
  PARTITION BY RANGE (`user_id`) (
    PARTITION p0 VALUES LESS THAN (4000000),
    PARTITION p1 VALUES LESS THAN (8000000),
    PARTITION p2 VALUES LESS THAN (12000000),
    PARTITION pmax VALUES LESS THAN MAXVALUE
  );
```

### 2.3 时序层（RANGE COLUMNS，pmax 哨兵 + 运行期维护）

```sql
CREATE TABLE IF NOT EXISTS `login_history` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT UNSIGNED NULL COMMENT '弱引用可空(登录失败无 user)',
  `email`         VARCHAR(255) NULL,
  `method`        TINYINT NOT NULL,
  `ip`            VARCHAR(64) NULL,
  `device`        VARCHAR(255) NULL,
  `location`      VARCHAR(255) NULL,
  `result`        TINYINT NOT NULL COMMENT '1成功 2失败',
  `is_new_device` TINYINT(1) NOT NULL DEFAULT 0,
  `notified`      TINYINT(1) NOT NULL DEFAULT 0,
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`, `created_at`),
  KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='登录历史(审计);月分区;清理暂缓(v2.2 决策 12)'
  PARTITION BY RANGE COLUMNS (`created_at`) (
    PARTITION pmax VALUES LESS THAN (MAXVALUE)
  );

CREATE TABLE IF NOT EXISTS `otp_code` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `email`       VARCHAR(255) NOT NULL,
  `code_hash`   VARCHAR(100) NOT NULL COMMENT 'bcrypt',
  `length`      TINYINT NOT NULL,
  `expires_at`  DATETIME NOT NULL,
  `attempts`    INT NOT NULL DEFAULT 0,
  `max_attempts` INT NOT NULL,
  `status`      TINYINT NOT NULL DEFAULT 1 COMMENT '1待验证 2已消费 3过期 4锁定',
  `last_sent_at` DATETIME NULL,
  `version`     INT NOT NULL DEFAULT 0 COMMENT '乐观锁',
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`, `created_at`),
  KEY `idx_email_status` (`email`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='邮箱验证码(DB 主存);月分区;清理暂缓——校验查询必须带 created_at 下界谓词强制分区裁剪(见 §5)'
  PARTITION BY RANGE COLUMNS (`created_at`) (
    PARTITION pmax VALUES LESS THAN (MAXVALUE)
  );
```

> 时序表建表仅带 pmax 哨兵；**启动阶段**分区维护任务即时 REORGANIZE 生成
> 实际分区（空 pmax 分裂=秒级元数据操作），业务流量开始前完成。

### 2.4 会话冷备（普通表不分区，Redis 为权威主存；v2.2 修订）

```sql
CREATE TABLE IF NOT EXISTS `user_session` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'REST 契约数字 session_id 来源(登录时同步 INSERT 取回,写入 Redis 会话 JSON)',
  `user_id`          BIGINT UNSIGNED NOT NULL,
  `token_id`         VARCHAR(64) NOT NULL COMMENT 'JWT jti',
  `refresh_token_id` VARCHAR(64) NULL,
  `access_expires_at`  DATETIME NULL,
  `refresh_expires_at` DATETIME NULL,
  `device`           VARCHAR(255) NULL,
  `browser`          VARCHAR(255) NULL,
  `ip`               VARCHAR(64) NULL,
  `location`         VARCHAR(255) NULL,
  `is_new_device`    TINYINT(1) NOT NULL DEFAULT 0,
  `method`           TINYINT NOT NULL,
  `status`           TINYINT NOT NULL DEFAULT 1,
  `last_active_at`   DATETIME NULL,
  `version`          INT NOT NULL DEFAULT 0,
  `created_at`       DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token` (`token_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='会话冷备/审计(Redis 为权威主存;Redis 故障降级 uk_token 点查;暂不清理=v2.2 决策 12)';
```

> v2.2 修订:不分区普通表(PK/uk 无分区约束,全局唯一天然合法),降级点查走 uk_token。
> 暂不清理意味着该表随登录量持续增长;后续启用清理时可 ALTER TABLE 在线转月分区
> (DDL 重写全表,需窗口)或直接启用批量清理——启用条件与时机由磁盘水位/行数阈值另行下令。

### 2.5 不分区表（量级 ≤ 千行）

`admin_user`、`admin_session`、`role`、`permission`、`role_permission`、`auth_config`
（单行配置）、`email_template`（共享表，留 identity 库）——结构与 v1 一致，原样保留。
`operation_log` 同为共享表留 identity 库（Java 继续写），不本域化。

## 3. Redis 数据结构定义

| 键 | 类型 | 值 | TTL | 用途 |
|---|---|---|---|---|
| `session:{token_id}` | STRING | 会话 JSON{id, user_id, method, refresh_token_id, access_exp, refresh_exp, device, ip, browser, location}（id=冷备表自增 session_id,REST 契约用） | refresh 有效期（30d） | **会话权威主存**；校验 GET+判 access_exp；旋转=新键 SET+旧键 DEL |
| `user_sessions:{user_id}` | SET | token_id 成员 | 30d | 该用户全部会话索引（admin 列表/强制下线遍历 DEL；读取时 GET 探活过滤残留） |
| `otp:resend:{email}` | STRING | "1" | resend_seconds | 重发冷却 |
| `otp:count:email:{email}:h` / `:d` | STRING | 计数 | 1h / 1d | 发码配额（email 5/h、5/d） |
| `otp:count:ip:{ip}:h` | STRING | 计数 | 1h | 发码配额（IP 20/h） |
| `identity:perms:{adminId}` | STRING | JSON 权限码数组 | 60s | 权限缓存（写路径主动失效） |
| `identity:user:{id}` | STRING | 用户行 JSON | 300s | 用户读缓存（写路径主动失效） |

会话写入时序：登录/刷新 → `SET session:{新jti}` + `SADD user_sessions:{uid}` + 集合键 TTL 续期
+ DB 冷备 INSERT/UPDATE（同事务边界外 best-effort，失败仅告警不阻塞登录——冷备缺行时降级路径
以 Redis 为准返回 not-valid，不影响安全）。撤销：DEL + SREM + 冷备 UPDATE status=revoked。

## 4. 分区自动维护任务（Rust 内置调度）

```
任务 partition_maintain（启动时同步执行一次 + 每小时一次，进程内互斥）:
 ① user / user_identity:
    MAX(id) > 最高实分区边界 − 400,000（90% 水位）
      → REORGANIZE PARTITION pmax INTO (p_next VALUES LESS THAN (边界+400万), pmax)
    （pmax 保持空——任务永远提前扩；即便失职溢出进 pmax，REORGANIZE 仍在线正确，仅变慢+告警）
 ② login_history: 确保未来 2 个月月分区存在（REORGANIZE pmax）
 ③ otp_code: 确保未来 2 个月月分区存在（REORGANIZE pmax）
 ④ 全部动作先查 information_schema.PARTITIONS 判幂等；每次动作 INFO 日志留证据
 ⑤ DROP PARTITION 清理动作**全部暂缓执行**（v2.2 决策 12:保留完整审计）——
    代码路径保留、配置开关默认关闭;后续按磁盘水位/行数阈值下令启用
任务 session_cold_retention: 暂缓（会话冷备为普通表且不清理）
```

## 5. 查询路径（K 定位验证）

| 场景 | 路径 | 分区裁剪 |
|---|---|---|
| Google 登录 | identity_google 点查 → user_id → user + user_identity | 每步 1 区 |
| 邮箱登录/归并 | identity_email 点查 → user_id → … | 每步 1 区 |
| 每请求会话校验 | `GET session:{token_id}`（Redis，~0.1ms） | — |
| 会话降级（Redis 故障） | user_session 普通表 uk_token 点查 | 无分区,索引点查 |
| OTP 校验 | otp_code `email+status+created_at≥NOW()-24h`(谓词强制裁剪) | 裁剪至当月 1 分区 |
| 列出登录方式 | user_identity WHERE user_id=? | 1 区（co-location） |
| admin 用户列表 | 优先 K 定位；无 K 全量筛选=分区归并+游标分页 | 全区归并（已确认接受） |
| 用户详情（近 20 登录） | login_history (user_id, created_at) 分区各取 top-20 归并 | 各月分区索引扫,分区数×20 行归并 |

## 6. 归并语义在新结构下的实现

不变项：provider_uid 幂等 → 同邮箱双 verified 自动并 → 未验证 40902 → 新建。
变化项（实现层）：
- "provider_uid 命中" = 路由表点查（identity_google/apple/email）替代旧 uk 扫描
- "同邮箱用户" = identity_email 点查替代旧 user.email 唯一索引
- 新建账号 = user INSERT + 路由表 INSERT + 档案 INSERT（同事务；表同库 ✓）
- email 唯一冲突 = identity_email 主键冲突（DB 强制，取代旧 uk_user_email）
- 换主邮箱 = 更新 identity_email 行 + user.email 快照 + user_identity is_primary 迁移（同事务）

## 7. v1 → v2 数据迁移策略（migrate-identity.sh 重写要点）

1. 建 v2 全套表（本 DDL）
2. `user`：INSERT...SELECT 直拷（分区自动落位）
3. 路由表生成：
   - identity_email ← `SELECT email, id FROM identity.user WHERE email IS NOT NULL`
   - identity_google ← 旧 user_identity WHERE provider=2
   - identity_apple ← 旧 user_identity WHERE provider=3（relay_email 随迁）
4. user_identity / login_history / otp_code / user_session：直拷
5. 校验：各表 COUNT + 路由表行数 vs 旧表分组计数 + 抽样 checksum（沿用 v1 校验框架）
6. 源库只读、回滚=DROP 目标表、无断点续传（沿用 v1 安全细则）

## 8. 代码影响清单（评审通过后执行）

- `server/schema/identity.sql`：替换为 v2 DDL（bootstrap 机制不变，仍 CREATE IF NOT EXISTS）
- `server/schema/identity-reference-13.sql`：重建为 v2 基准（drift 门禁比对 v2）
- SeaORM 实体：新增 identity_email/google/apple 三张；user 去唯一假设；其余不变（分区对 ORM 透明）
- `service/session.rs`：会话校验改读 `session:{token_id}` 主存（替代 30s 缓存键）；写路径加 Redis 主存 + 冷备双写；user_sessions 集合索引
- `service/user_query.rs`：email/provider 查找改走路由表两跳
- `service/merge.rs`（P2 落地时按 §6 语义走新表）
- 新增 `service/partition_maintain.rs`：§4 调度任务
- `migrate-identity.sh`：按 §7 重写
- 性能压测（后续任务，已记标准）：大数据量 × 并发度 × 干扰模型（读写混合/缓存命中/后台任务竞争）→ p50/p95/p99 + 环境说明
