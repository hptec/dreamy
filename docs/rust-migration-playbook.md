# Java → Rust 全量迁移手册（性能标准 · 数据同步 · 测试与验收）

> 生成:2026-09-22 | 依据:identity 域先例(docs/identity-migration-todo.md、docs/login-benchmark-report.md)+ 全仓只读调研
> 适用:backend/(Java 58,126 行,38 子域,241 端点)→ server/(Rust,现有 identity 先例)逐域迁移全过程

---

## 1. 性能与容量标准(硬性门禁)

### 1.1 压测阶梯与达标线(继承 identity 先例,每域必须重跑)

| 项 | 标准 | 来源先例 |
|---|---|---|
| 压测阶梯 | 50 / 100 / 300 / 500 并发 × 20s,逐级爬升 | `server/crates/identity/tests/load.rs` STAGES |
| 数据画像 | 千万级:主档表 ≥1000 万行,流水表 ≥5000 万行,热键表 ≥100 万行 | bench-seed.sh 翻倍法灌库 |
| 读路径达标 | 峰值读端点 ≥3000 QPS,p99 < 300ms @ 300 并发;500 并发零失败 | S1 config:500并发 2805 QPS/p99 634ms |
| 写路径达标 | 最重写链(登录态 refresh 级)≥470 QPS 零错误,p99 < 400ms | S2 refresh:476 QPS p99 399ms |
| 拒绝路径 | 频控/熔断拒绝路径 ≥2000 QPS,攻击流量不击穿 | S3/S5:2373~3999 QPS |
| 热点读 | 认证读热路径 p50 < 15ms | S8 profile p50 5.8ms |
| 千万行分页 | 深分页 p99 < 2s @ 500 并发(COUNT 缓存 30s;超标则 cursor 化) | S7:10s 超时→157ms~1.9s |
| soak | 50 并发 × 30 分钟混合流量,RSS 无单调增长(漂移 <1%),MySQL/Redis 水位稳定 | data/soak-evidence.md 判定 PASS |
| 并发正确性 | 同一竞态资源并发 N 请求仅一成功(乐观锁/唯一约束验证),零孤儿数据 | concurrency.rs 双绿 |

**每域差异**:上述基线以 identity 实测为锚;product 列表/搜索、order 创建、cart 归并等域需在迁移前先定义本域的「最重路径」与达标值(写入 hhspec 变更提案),不允许迁移后再定。

### 1.2 数据量预估(迁移设计与分区的前提)

生产真实量 ≠ 压测合成量。每域迁移提案必须含如下预估表(先在 HK 生产 `information_schema.TABLES` 采集现状,再按业务增长率外推 3 年):

| 表类别 | 增长特征 | 预估口径 |
|---|---|---|
| user / user_identity / identity_* | 随注册慢增长 | 现状 × (1+月增速)^36 |
| login_history / otp_code / browse / operation_log | 流水型,最快膨胀 | 日活 × 单日均次 × 365 × 3 |
| order / payment / refund / shipment | 随成交增长 | 年订单量 × 3 + 状态流水倍数 |
| product / attribute / collection / banner | 低频配置 | 现状 + 运营预算 |
| cart / wishlist | 活跃用户 × 人均条目 | |

### 1.3 分区与路由设计标准(继承 common::partition 先例,写入每域 schema)

| 机制 | 规则 | 来源 |
|---|---|---|
| 时序表按月分区 | login_history/otp_code/operation_log 类流水表 RANGE BY 月;**新域同类表 browse/order 流水照此** | crates/common/src/partition.rs |
| 写入越界自愈 | 分区外 INSERT 显式报错 → 1526 错误捕获 → 动态补建分区 → 重试;预扩水位机制 | 同上,8 个单元测试 |
| 热键哈希路由 | 千万级行(user/identity_email 类)按 KEY 哈希 25 区建索引 | schema/identity.sql |
| 容量分层 | 主档三段 RANGE(400 万/段)起步,水位预扩 | user p0/p1/p2 |
| DDL 纪律 | 自举 DDL 仅 CREATE IF NOT EXISTS;**绝不 DROP/ALTER**;实体改列必须同步出存量库 ALTER 脚本(教训:auth_config 6 列) | identity.sql 头注释 |
| 深分页 | 千万行表分页一律 COUNT 缓存(30s)起步,列表页预研 cursor 方案 | S7 修复 |

---

## 2. 香港生产 → 本地开发服务器 数据与图片同步方案(迁移完成后执行)

### 2.0 现状事实

- 生产:香港单端口 60080 TLS 网关;镜像经 ACR 或本机 daemon;`deploy.sh` 只同步编排不迁数据
- 图片:**Cloudflare R2**(`STORAGE_MODE=real`,S3 兼容预签名,bucket 默认 `dreamy-media`);本地 dev 为 stub 模式,无 MinIO 容器
- 双库:同一 MySQL 实例 `identity`(Java 业务+身份)与 `dreamy_server`(Rust);业务表 user_id 外键指向 dreamy_server 库 user 表 → **两库必须同快照点导出**
- 本地:`.env.deploy` 本地验证栈,自签证书,端口错开

### 2.1 Phase A — 生产盘点(只读)

在 HK 服务器执行并留存报告:
1. `SELECT TABLE_NAME, TABLE_ROWS, ROUND(data_length/index_length)` 两库全表清单 → 确定真实数据量与传输体积
2. R2 `rclone lsjson --recursive` 图片对象清单(数量/总量/按前缀分布:product/blog/showroom/banner)
3. 采集生产 `.env.deploy` 的 S3_*/R2 endpoint 凭据(仅同步用,不入库)

### 2.2 Phase B — 数据库一致性快照

> **决策记录(2026-09-22,用户拍板):全量同步**——会话/OTP/登录历史等敏感表一并同步,不裁剪,原样不脱敏;本地开发机自行承担数据安全。

```
# 维护窗口(或低峰),同一事务点导出两库(全量,含会话/验证表):
mysqldump --single-transaction --set-gtid-purged=OFF \
  --databases identity dreamy_server > dreamy-prod-$(date +%Y%m%d).sql
# 传输:rsync -z --progress → 本地
```

全量同步附加要求:
- login_history/otp_code 等月分区表恢复后必须重建分区结构(自举 DDL + common::partition 自愈可兜底,但仍需人工核对分区数)
- user_session/admin_session 为过期会话数据,恢复后可按需 TRUNCATE(本地登录态与生产不互通,保留亦无害)
- 生产体量未知,Phase A 盘点后确认本地磁盘余量 ≥ 快照体积 × 2(dump + 导入峰值)

### 2.3 Phase C — 图片同步(R2 → 本地)

> **决策记录(2026-09-22,用户拍板):双源模式**——图片留在 Cloudflare R2,库内 URL 不重写,本地开发机直连 R2 公网域名取图。前提:本地开发环境可持续访问公网。

```
rclone sync r2:dreamy-media ./data/media/ --checksum --progress
```
- sync 快照仅作**灾备留存**(R2 无版本化,本地副本即唯一备份),不是运行时依赖
- 本地 nginx 可加 `location /media/` 兜底代理到本地副本,公网不可达时切换(可选,Phase D 实施时定)
- 风险联动:§5-⑨ R2 无版本化 → 本地 rclone 副本承担备份职责,需定期(如每周 cron)重跑 sync

### 2.4 Phase D — 本地恢复

1. 本地栈停服 → bootstrap 自举 DDL 先行(CREATE IF NOT EXISTS 幂等,不会冲突)
2. 导入快照(全量)→ 重建被同步表的分区结构,TRUNCATE 本地过期会话(可选)
3. **URL 策略(已决策:双源)**:库内 R2 公网域名原样保留,不做重写;前端仍从 R2 取图
4. 前端 `.env` 指向本地网关;`NEXT_PUBLIC_*` 按本地域名重配;清 Next ISR/缓存
5. `*_MODE` 保持本地 stub/real 组合(Stripe/GA4/OIDC 本地不可 real,回调域名未注册)

### 2.5 Phase E — 同步验证(§4.4 对账用例全绿才算同步完成)

---

## 3. 测试用例体系(每域迁移必过)

### 3.1 六层测试金字塔

| 层 | 内容 | 载体 |
|---|---|---|
| L1 单元 | 错误码表唯一性(error_site!)、IntEnum 枚举同构、分区自愈、时间/金额工具 | crates/common + 各域 #[test] |
| L2 service 集成 | **真 MySQL,零 Mock** 原则延续;每域 repository+service 全路径 | 各域 tests/,Testcontainers 或本地 MySQL |
| L3 REST 契约 | 241 个端点逐一:请求参数/响应包络 R/错误码/分页结构/SNAKE_CASE 字段/四语言 i18n 消息 | 每域 contract 测试 + OpenAPI 对照 |
| L4 E2E smoke | tests/api-integration/ 每域一个 *-smoke.sh(登录→浏览→加购→下单→支付→退款 主链) | 网关入口全链 |
| L5 UI 验证 | tests/ui-verification/ 每页面 *.verify.mjs(现有 29 个为基线,每域增补) | 真浏览器断言 |
| L6 专项 | 并发竞态/压测/soak/大数据量(环境闸门 env=1) | identity/tests/ 模式复制 |

### 3.2 每域必写的并发竞态用例(识别清单,迁移前评审)

| 域 | 竞态点 | 断言 |
|---|---|---|
| order | 超时关闭 vs 支付回调同时到达 | 仅一终态,金额对账平 |
| product/flashsale | 库存扣减并发 | 不超卖,余量精确 |
| coupon | 优惠券核销并发(同一券码) | 仅一成功,核销记录唯一 |
| cart | 多端并发归并 | 无重复行/丢失,merge 幂等 |
| review | 评价提交 vs 订单状态变更 | 状态机不穿透 |
| identity(已完成) | refresh 旋转/OTP 消费 | 仅一成功(先例复用) |
| payment | Stripe webhook 重放 | 幂等,不重复入账 |
| wishlist/collection | 并发增删同条目 | 终态一致 |

### 3.3 分流期双跑对账(增量要求,高于 identity 先例)

product/order/checkout 三个高复杂度域,切换前增加 shadow 对账:
1. 只读流量双打(Java 真响应 vs Rust 影子响应)
2. diff 口径:HTTP code / R.code / 字段名与类型 / 列表条数 / 关键金额数值
3. 连续 3 天 diff 率 = 0 才允许切流

### 3.4 数据同步验证用例(§2.5 执行)

| # | 用例 | 通过标准 |
|---|---|---|
| V1 | 行数对账 | 每表 TABLE_ROWS 快照 vs 本地 COUNT,误差 0(全量同步,无裁剪豁免) |
| V2 | 抽样 checksum | 每表随机 100 行聚合校验值一致 |
| V3 | 外键完整性 | 业务表 user_id 100% 命中 dreamy_server.user |
| V4 | 图片可达性 | 商品主图/详情图/博客图按前缀抽样 ≥200 张,HTTP 200 + Content-Type 正确(双源模式:直接打 R2 公网域名) |
| V5 | 图片全量探测 | URL 清单逐张 HEAD(后台任务),死链率 = 0 |
| V6 | 金额对账 | order 总额/退款额/优惠券分摊 SUM(prod) = SUM(local) |
| V7 | 主链冒烟 | store 五页 200 链接审计 + 下单到支付 stub 全链 + admin 全菜单遍历 |
| V8 | i18n 抽查 | 四语言各抽 3 页文案渲染无缺 key |

---

## 4. 每域验收门禁(Go/No-Go 清单,全绿才许删 Java 码)

1. L1~L5 测试全绿;L6 三件套(压测/soak/并发)证据归档 data/
2. 网关分流矩阵扩表后 100% PASS(test-gateway-routes.sh)
3. 分流期双跑对账 diff = 0(product/order/checkout 必做)
4. 种子自举收编进 Rust seed.rs(DataInitializer 语义不丢)
5. 错误码/i18n 四语言入 common 全表
6. **删码前库快照留存本地**(backup-<domain>-pre-drop-*.sql,回滚唯一依赖)
7. 回滚演练:IMAGE_TAG 回退 + 快照恢复各跑一次
8. Java 测试语义迁移记录(777 个 @Test 逐域销账,场景 → Rust 等价物)
9. hhspec 变更提案收尾记录(验收证据链接)

---

## 5. 遗漏风险清单(用户之外,执行前必须决策/确认)

| # | 风险 | 建议 |
|---|---|---|
| ① | ~~图片 URL 策略~~ **已决策(2026-09-22):双源保留 R2 域名**,不做重写;联动条件=本地需持续公网可达 | 已定,Phase C/D 按决策执行 |
| ② | ~~敏感数据合规~~ **已决策(2026-09-22):全量原样同步不脱敏**(含会话/OTP/登录历史/用户档案) | 已定;本地开发机需自行做好磁盘加密与访问控制 |
| ③ | 一致性快照需停写窗口或读盘锁,双库分别 dump 有跨库不一致风险 | 用 `--single-transaction` 单次连库导出两库 |
| ④ | 本地 MySQL 8.4 与 HK 版本/字符集/时区差异 | 恢复前 `SELECT @@version/character_set/tz` 三对照 |
| ⑤ | 前端 ISR/NEXT_PUBLIC 指向生产域名,数据同步后仍打生产 | 本地 env 全量重配 + 清缓存 |
| ⑥ | OIDC 回调域名只注册了生产,本地 Google/Apple 登录不可 real | 本地走 stub/demo 种子用户 |
| ⑦ | ADMIN_API_SECRET 本地与生产不同值,书签/脚本跨环境会 404 | 本地脚本统一从 .env 读 |
| ⑧ | HK 服务器**无自动备份机制**记录在案(deploy.md 未提) | 立即补 mysqldump cron + 异地留存 |
| ⑨ | R2 无版本化/备份策略 → 双源决策下本地 rclone 副本即唯一备份 | 每周 cron `rclone sync --checksum` 重跑留存 |
| ⑩ | 删码类不可逆动作(死表 DROP)无双人复核 | GoLive checklist 加第二人确认项 |
| ⑪ | gRPC 生成码 25,660 行不需迁,但 Java 侧 regen-grpc-java.sh 在删码后失效 | 删码同时退役该脚本,proto 消费方只剩 Rust |
| ⑫ | 生产真实数据量未知(压测千万是合成数据) | §2.1 Phase A 盘点先行,同步体积/时长按实测排期 |
| ⑬ | 全量同步含千万级 login_history/otp_code,本地导入时长与磁盘占用可能超预期 | Phase A 体积实测后评估;必要时分段导入 + 分区表后建索引 |
| ⑭ | 双源取图使本地联调依赖公网,R2 故障/迁移会影响本地开发 | 保留 rclone 本地副本,应急可切 `location /media/` 本地兜底 |

---

## 6. 执行顺序总览

```
迁移期(按域批次,每批走 §3+§4 门禁)
  └→ 全域完成,Java 容器退役
数据同步期(§2 A→E)
  └→ HK 生产数据+图片落地本地,对账用例全绿
收尾(§5 逐项销账)
  └→ 备份机制/快照保管/脚本退役
```
