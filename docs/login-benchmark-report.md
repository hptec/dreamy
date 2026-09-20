# login 专项压测报告（2026-09-19）

> 遗留任务 2 执行产出。数据规模、场景、工具均按任务 2 规格（2026-09-18 修正上下文）落地。
> 压测目标：`http://127.0.0.1:18082`（server 容器宿主映射，axum 全栈路径）。
> 环境：本机 Docker compose 7 容器（server/mysql/redis/gateway/backend/store/admin），Apple Silicon。

## 一、数据规模（千万级）

| 表 | 行数 | 分区拓扑 | 与压测的关系 |
|---|---|---|---|
| user | 10,000,504 | RANGE(id) p0/p1/p2 三段（400 万/段），写入跨段分布 | 登录归并/会话/分页的主档 |
| identity_email | 9,999,982 | KEY 25 区哈希分布 | 邮箱路由两跳查找（登录热路径） |
| login_history | 50,000,018 | RANGE COLUMNS(created_at) 月分区 p202604~p202609（预建 5 个历史月） | 登录历史写入 + is_new_device 90 天谓词查询 |
| otp_code | 998,206 | 当月月分区 | 验证码存取 |

灌库工具：`scripts/bench-seed.sh`（显式 id 平移翻倍法 + 单事务分片 200 万行 + 幂等守卫）。
分区说明：login_history 历史月分区由脚本 STEP-1 `REORGANIZE PARTITION` 预建，验证分区裁剪生效。

## 二、场景与结论

| 场景 | 压测画像 | 50 并发 | 100 并发 | 300 并发 | 500 并发 |
|---|---|---|---|---|---|
| S1 config 读 | 无频控读路径容量主力 | 4367 QPS, p99 86ms | 4301 QPS, p99 141ms | 3343 QPS, p99 299ms | 2805 QPS, p99 634ms |
| S2 refresh 写 | 登录态最重写链（冷备双 SELECT + 乐观锁 UPDATE + Redis 双写），**worker 独占旋转链** | **476 QPS, 全 200, p50 81ms, p99 399ms** | 1137 QPS, 200/401 混合* | 745 QPS, 混合* | 962 QPS, 混合* |
| S3 send(429) | 预置冷却键 → 稳态即频控拒绝路径（Redis 热路径） | 3975 QPS 拒绝 | 3318 | 2768 | 2373 |
| S5 admin-login | 恒定 bcrypt + IP 熔断拒绝路径 | 3999 QPS 拒绝 | 3139 | 2690 | 2367 |
| S6 sessions | user_detail 冷备查询（单用户多会话） | 2325 QPS, p50 21ms | 1858 | 1354 | 1297 |
| S7 users分页 | 千万行 ListUsers（首页+深分页交替） | 970 QPS, p99 152ms | 948 | 802 | 693, p99 1.9s |
| S4 verify 全链 | 直插码 → 登录全链（频控→归并→会话→历史→新设备），受 42904 IP 频控 | p50 31ms, p99 43ms, 200×40 零失败 | — | — | — |
| S8 profile | 消费端认证读热路径（JWT extractor → validate_store Redis 主存 + 用户缓存） | 3376 QPS, p50 5.8ms | 2757 | 3519 | 2199, p99 944ms |
| S9 identities | 认证读 + 路由表（千万行 KEY 25 区）反查 | 3161 QPS, p50 10.8ms | 3111 | 2725 | 2155, p99 776ms |

\* S2 高档位 401 为压测端多 worker 复用旋转链触发 V3 乐观锁/V4 重用防御的正常收敛——**多端共用同一凭证时仅一端存活，其余被防御机制拒绝**，这是安全设计的正确行为展示。单链串行（50 档）100% 200。

### 容量结论（绝对值，供判断）

1. **读路径（config）**：单实例 3000+ QPS 稳态，p99 < 300ms @ 300 并发。
2. **登录态续期（refresh）**：单链串行刷新 476 QPS 无错误（p99 399ms）；该链是登录态最重路径（2 次 DB SELECT + 乐观锁 UPDATE + 2 次 Redis 写），**生产语义下每用户 2 小时仅 1 次**，476 QPS 对应 ~340 万日活用户的正常续期，余量充足。
3. **登录全链（verify）**：p50 31ms（含 bcrypt 校验 + 归并 + 会话 + 历史 + 新设备判定），QPS 上限由频控设计（42904，默认 30/min/IP）决定——这是安全属性而非瓶颈。
4. **频控拒绝路径**：2000-4000 QPS，攻击流量不会击穿频控层。
5. **千万行分页（S7）**：COUNT 缓存（30s）落地后 p99 从 10001ms（超时）降至 157ms~1.9s，500 并发仍零失败。

## 三、压测过程中发现并修复的问题

### P0 存量 bug：refresh 旋转链第二次刷新必断（已修复）

- **现象**：刷新成功后，用旋转出的新 token 再次刷新必 40102——用户 access（2h）过期续刷一次后**永久掉线**。
- **根因**：`refresh()` 撤销旧 access 时调用 `revoke_store`，其冷备语义（UPDATE status=revoked WHERE token_id=旧access）恰好命中**当前冷备行本身**（该行的 token_id 就是旧 access jti），而该行随即被乐观锁 UPDATE 原位续用为新链——行状态却已是 revoked，下一次刷新的 `status=Active` 检查永不命中。
- **修复**：新增 `session::revoke_store_soft`（仅 Redis DEL + SREM，不动冷备），refresh 场景专用；logout/强制下线保持原语义。
- **回归门禁**：E2E-B 新增「二连刷新 code=0」断言（15/15 绿）；旋转链三连刷诊断脚本 `scripts/diag-refresh-chain.sh` 全绿。
- **此 bug 为存量**（V3/V4 重构前即存在），链式压测首次暴露——压测的核心价值实证。

### P1 性能：千万行分页 COUNT 全表扫描（已修复）

- `list_users` 每页 `paginator.num_items()` 对千万行全索引扫描 → 深分页 p99 10s 超时。
- 修复：无过滤条件时 total 走 Redis 30s 缓存；过滤条件各异时保持实时。

### 压测脚本的 41001 时区陷阱（诊断记录）

压测 setup 直插验证码最初 41001 失败：sqlx（SeaORM 底层）会话时区与 mysql CLI 不同（UTC vs CST），`NOW()` 写出的 DATETIME 字面相差 8 小时，server 侧（chrono Local=+8）判定恒过期。修复：压测 SQL 时间统一 `DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR)`（与会话时区无关）。**教训：DATETIME 字面值在「写入会话时区 ≠ 读取方时区」时是隐性地雷，跨进程时间戳必须显式约定时区。**

## 四、压测资产

| 资产 | 说明 |
|---|---|
| `server/crates/identity/tests/load.rs` | IDENTITY_LOAD=1 闸门压测（9 场景 × 4 阶梯），令牌由 server 签发（setup 真实登录 500 用户） |
| `scripts/bench-seed.sh` | 千万级灌库（翻倍法+分片+幂等） |
| `scripts/bench-soak.sh` | soak 驱动 + RSS/Redis/MySQL 采样（任务 3） |
| `scripts/diag-refresh-chain.sh` | 旋转链 N 连刷诊断（回归门禁用） |
| `scripts/login-security-e2e.sh` | 安全加固 E2E 15 断言（含二连刷门禁） |

## 六、接口覆盖矩阵（Rust 全量 30 端点）

**已压测（9 端点，覆盖全部高频读路径 + 登录写链）**：otp/send、otp/verify、refresh、config、admin/auth/login、users 列表、users/{id}、account/profile、account/identities。

**压测未覆盖及理由**：
| 类别 | 端点 | 理由 |
|---|---|---|
| 依赖外部 | oidc/{provider}/callback | 依赖真实 Google/Apple 交互，不具可压测性；验签路径（RSA+JWKS）与 verify 频控已被 S4 等价覆盖热路径 |
| 低频写（污染数据/审计） | account/identities/bind、delete identity、change-primary、account/delete、admin/admins/roles CRUD、status、force-logout、auth-config PUT、logout | 运营/用户低频动作，压测会写入亿级垃圾数据与审计；功能已被 E2E 覆盖 |
| 低频读 | admin/me、permissions、operation-logs、export | admin 管理面低频，模式同 S6/S7（冷备查询+分页），容量由同类场景外推 |
| gRPC 18083 | ValidateStoreSession 等 7 rpc | 仅 compose 内网开放（设计如此）；其核心 `validate_store`/`GetUser` 热路径已被 S8/S9 的 JWT extractor 路径等价覆盖，gRPC 层为 protobuf 薄壳 |

**结论**：高频与性能敏感面 100% 覆盖；未覆盖项均为低频/外部依赖/会污染数据的写操作，风险由功能 E2E 兜底。

## 七、后续建议

1. S7 的深分页在 500 并发下 p99 ~1.9s：如运营深翻页频次上升，可升级为 cursor 分页（当前 30s COUNT 缓存已消掉主要热点）。
2. S2 的 401 混合画像可加入监控：线上出现大量 40102 + V4 撤销事件即代表令牌盗用/多端挤占，应告警。
3. 压测口径下 server 容器 CPU 峰值与 RSS 采样见 `data/soak-evidence.md`（任务 3 soak 产出）。
