# shipping 数据层详细设计（L2）

> 角色: l2_data_designer ｜ change: portal-api-integration ｜ domain: shipping
> 方法论：Entity 与 DDL / Repository 方法(RM-SHP) / DTO↔Entity 映射(MAP-SHP) / 索引(IDX-SHP) / 事务边界(TX-SHP) / 数据校验(CV-SHP) / 缓存设计(CACHE-SHP) / 领域服务落点(SVC-SHP)。
> 来源权威：er-diagram.yml（Carrier/ShippingRate）+ shipping-api.openapi.yml v1.1.0 + data-flow.md 缓存矩阵 shipping 行 + state-machine.yml（carrier_status）+ shipping-api-detail.md（E-SHP-01~09 / DEC-SHP-1~6 / SVC-SHP-01）。
> 实现基线：huihao-mysql `LongAuditableEntity`（Long 自增主键 + created_at/updated_at 审计列）+ MyBatis-Plus；无物理外键（CP-010）；本 DDL 为语义基线，L3 经 huihao `@Table/@Column/@Index` 注解 DdlAuto 派生落地（status 枚举可按 identity 既有落地映射 tinyint 码，取值语义与本文 CHECK 一致）。
> **包结构**：`com.dreamy.shipping/`（单模块多 domain，与 identity/catalog 平级）：`domain/{carrier,rate}/{entity,repository,service,consts}` + `api/`（ShippingQuotePort + ShippingOptionQuote，供 trading 依赖的公开包）+ `controller/` + `dto/` + `config/`。无 mq 包（本域无 MQ 事件）。

## 0. 实体清单（2 表）

| 表 | 实体 | 说明 |
|---|---|---|
| `carrier` | Carrier | 物流承运方（名称/覆盖区域描述/时效/启用状态；ALIGN-015） |
| `shipping_rate` | ShippingRate | 国际邮费分区运费规则行（zone 唯一；「地理区域 / 承运商」约定承载多承运商差异化价格，F-036 不新增实体字段） |

AnalyticsDashboard、Order 等不归本域。Order.carrier 为枚举快照（trading 域 orders 表列），与本表无外键关系；删除/改名承运商不波及历史订单（E-SHP-03/04 STEP 注记）。

---

## 1. Repository 方法（RM-SHP）

### CarrierRepository（domain/carrier/repository）
- RM-SHP-001 `listAll() -> List<Carrier>` —— `ORDER BY id ASC`（E-SHP-01，实时不缓存）
- RM-SHP-002 `findById(id) -> Carrier?` —— 404901 判定点
- RM-SHP-003 `countEnabled() -> long` —— `SELECT COUNT(*) WHERE status='enabled'`（409902 guard，idx_carrier_status；仅在 DEC-SHP-4 锁内调用，计数读写串行无竞态）
- RM-SHP-004 `insert(Carrier)` —— E-SHP-02
- RM-SHP-005 `updateAll(Carrier)` —— E-SHP-03 整单覆盖（name/zones/lead_time/status 全列 SET）
- RM-SHP-006 `updateStatus(id, status) -> affected` —— E-SHP-05
- RM-SHP-007 `deleteById(id) -> affected` —— E-SHP-04
- RM-SHP-008 `listEnabled() -> List<Carrier>` —— `WHERE status='enabled' ORDER BY id ASC`（SVC-SHP-01 报价数据源，CACHE-SHP-001 回源方法）

### ShippingRateRepository（domain/rate/repository）
- RM-SHP-010 `listAll() -> List<ShippingRate>` —— `ORDER BY id ASC`（E-SHP-06 实时；SVC-SHP-01 经 CACHE-SHP-002 读同方法）
- RM-SHP-011 `findById(id) -> ShippingRate?` —— 404902 判定点
- RM-SHP-012 `existsByZoneNormalized(zoneNorm, excludeId?) -> bool` —— `WHERE LOWER(zone)=LOWER(:zoneNorm) [AND id != :excludeId]`（409901 判重；入参已按 DEC-SHP-1 规范化，uk_shipping_rate_zone 唯一索引兜底并发竞态）
- RM-SHP-013 `insert(ShippingRate)` —— E-SHP-07（uk 冲突向上抛 → 409901）
- RM-SHP-014 `updateAll(ShippingRate)` —— E-SHP-08 整单覆盖（提交 null 即清空费用字段，DEC-SHP-3）
- RM-SHP-015 `deleteById(id) -> affected` —— E-SHP-09（affected=0 → 404902）

---

## 2. DTO ↔ Entity 映射（MAP-SHP）

- MAP-SHP-001 Carrier ↔ CarrierDTO：`{id, name, zones, lead_time, status}` 全字段双向；JSON snake_case（lead_time ← leadTime）；审计列 created_at/updated_at 不出 DTO（契约 Schema 无此字段）
- MAP-SHP-002 ShippingRate ↔ ShippingRateDTO：`{id, zone, fee_under, fee_over, threshold}`；DECIMAL NULL → JSON null 原样透出（不补 0，前端按 DEC-SHP-3 语义展示「—」）
- MAP-SHP-003 (Carrier, ShippingRate) → `ShippingOptionQuote{carrier=Carrier.name, fee_usd=计费结果 scale2 HALF_UP, lead_time=Carrier.lead_time}`（SVC-SHP-01 出口，与 trading-api-detail §0 消费侧字段逐一对应）
- MAP-SHP-004 金额 `DECIMAL(10,2)` ↔ BigDecimal ↔ JSON number；时间 DATETIME(3) UTC（审计列，CP-014）；status VARCHAR 枚举 ↔ Java enum `CarrierStatus{ENABLED, DISABLED}`（CP-003 双保险）

---

## 3. 索引设计（IDX-SHP）

| ID | 表 | 索引 | 用途 |
|---|---|---|---|
| IDX-SHP-001 | shipping_rate | `uk_shipping_rate_zone` UNIQUE(zone) | 409901 唯一约束权威闸（DEC-SHP-1 规范化后存储，utf8mb4_0900_ai_ci 排序规则天然忽略大小写比较） |
| IDX-SHP-002 | carrier | `idx_carrier_status` (status) | RM-SHP-003/008 启用计数与启用列表（行数个位数，索引为规范性声明） |

---

## 4. 事务边界（TX-SHP）

> 所有写事务遵循 CP-031：缓存失效在**事务提交后**执行（TransactionSynchronization afterCommit），禁止脏读窗口。

| ID | 端点 | 边界内容 |
|---|---|---|
| TX-SHP-001 | E-SHP-02 | INSERT carrier + operation_log，原子；提交后失效 shipping:carriers |
| TX-SHP-002 | E-SHP-03 | 锁内（EC-SHP-001）：findById → 409902 guard（RM-SHP-003）→ updateAll + operation_log；提交后失效 |
| TX-SHP-003 | E-SHP-04 | 锁内：findById → 409902 guard → deleteById + operation_log；提交后失效 |
| TX-SHP-004 | E-SHP-05 | 锁内：findById → 幂等短路（同值零写）→ 409902 guard → updateStatus + operation_log；提交后失效 |
| TX-SHP-005 | E-SHP-07 | existsByZoneNormalized 判重 → INSERT（uk 兜底捕获→409901）+ operation_log；提交后失效 shipping:rates |
| TX-SHP-006 | E-SHP-08 | findById → 判重(排除自身) → updateAll + operation_log；提交后失效 shipping:rates |
| TX-SHP-007 | E-SHP-09 | deleteById(affected=0→404902) + operation_log；提交后失效 shipping:rates |

- **EC-SHP-001 并发控制（DEC-SHP-4）**：carrier 写路径（TX-SHP-001~004）统一包裹 huihao-redis `onIdLock("shipping:carrier-write")`（全局单 key 串行：等待 3s 超时 → 50000「操作繁忙请重试」语义走通用码；租约 10s）。锁外开启事务会破坏 guard 原子性，故顺序固定为 **锁 → 事务 → guard → 写 → 提交 → 释放锁 → 失效缓存**。rate 写路径无计数不变量，不加锁（uk 索引足够）。
- **回滚语义**：operation_log 与业务写同事务（BE-DIM-7，业务失败审计不留痕）；任一步失败整体回滚，缓存不失效（旧值仍正确）。

---

## 5. 数据校验（CV-SHP）

| ID | 规则 | 层 |
|---|---|---|
| CV-SHP-001 | carrier.name：必填，trim 非空，≤64（er maxlen 与 Order.carrier 枚举值长度兼容） | DTO @Valid + DDL |
| CV-SHP-002 | carrier.status ∈ {enabled, disabled}（CP-003：VARCHAR + CHECK + Java enum） | DTO + DDL + enum |
| CV-SHP-003 | shipping_rate.zone：必填，DEC-SHP-1 规范化后非空 ≤128；全表唯一（忽略大小写，IDX-SHP-001） | Service + uk |
| CV-SHP-004 | fee_under/fee_over/threshold：可空；非空时 ≥0、≤99999999.99、scale ≤2；NULL 语义=DEC-SHP-3（threshold NULL→恒 fee_under；fee NULL→计费按 0.00） | DTO + Service（计费 nvl） |
| CV-SHP-005 | 业务不变量：`COUNT(carrier WHERE status='enabled') >= 1`（409902；EC-SHP-001 锁内校验，种子初始即满足） | Service（锁内） |
| CV-SHP-006 | 运营约束：`Rest of World` 兜底行应恒存在（种子提供；系统不硬阻删除——前端删除兜底行时二次确认追加警示，FORM-SHP-03；缺失后果=未配置区域承运商无报价项，DEC-SHP-5 ④） | 前端警示 + 种子 |
| CV-SHP-007 | carrier.zones ≤255 可空、carrier.lead_time ≤64 可空（纯描述字段，不参与计费；时效进入报价 lead_time 透传） | DTO + DDL |

boundary-scenarios 映射：bs-258~262（carrier id/name/zones/lead_time/status null 行为）→ CV-SHP-001/002/007；bs-263~267（shipping_rate id/zone/fee_under/fee_over/threshold null 行为）→ CV-SHP-003/004（费用字段 null 正常落库保持为空，符合「系统正常处理并保持为空」期望）。

---

## 6. 领域服务落点（SVC-SHP）

- SVC-SHP-01 `ShippingQuoteService implements ShippingQuotePort`（domain/rate/service）：算法、GeoZoneResolver、端口签名见 shipping-api-detail.md §10（权威定义点，与 trading-api-detail §0 消费侧声明逐字一致）。只读无事务；依赖 CACHE-SHP-001/002。
- `GeoZoneResolver`（domain/rate/service）：纯函数静态映射（§10.2 权威表 + 国家名别名表），独立可单测（TC-SHP-001/002）。

---

## 7. 缓存设计（CACHE-SHP，BE-DIM-8）

| ID | key | 载荷 | 策略 | TTL | 失效触发者 |
|---|---|---|---|---|---|
| CACHE-SHP-001 | `shipping:carriers` | RM-SHP-008 listEnabled() 全量 List | JetCache 两级（Caffeine+Redis） | 600s | E-SHP-02~05 写提交后 @CacheInvalidate（进程内，无 MQ） |
| CACHE-SHP-002 | `shipping:rates` | RM-SHP-010 listAll() 全量 List | JetCache 两级 | 600s | E-SHP-07~09 写提交后 @CacheInvalidate |

- 固定单 key 全量缓存（无参数维度、无 locale/currency 维度——载荷为 USD 基准配置），**无穿透面**（key 恒存在，空表缓存空列表即可，无需 null 短 TTL 特殊处理）。
- 消费场景唯一：SVC-SHP-01 报价直调（FLOW-P05）；后台两个列表端点直读 DB 不经缓存（缓存矩阵「后台全部列表/详情不缓存」行）。
- 本地级（Caffeine）TTL 与远程级一致 600s；写失效经 JetCache 两级广播（与 identity profile_config 层同配置基建）。

---

## 8. DDL 与种子数据

### 8.1 DDL（语义基线，L3 经 huihao 注解派生）

```sql
-- 1. carrier 承运方
CREATE TABLE `carrier` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`        VARCHAR(64)  NOT NULL COMMENT '承运方名称（Order.carrier 快照取值源）',
  `zones`       VARCHAR(255) NULL     COMMENT '覆盖区域描述（纯展示）',
  `lead_time`   VARCHAR(64)  NULL     COMMENT '时效描述（进入报价项 lead_time）',
  `status`      VARCHAR(16)  NOT NULL DEFAULT 'enabled' COMMENT '状态：enabled/disabled',
  `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_carrier_status` (`status`),
  CONSTRAINT `chk_carrier_status` CHECK (`status` IN ('enabled','disabled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物流承运方（ALIGN-015）';

-- 2. shipping_rate 分区运费规则行
CREATE TABLE `shipping_rate` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `zone`        VARCHAR(128)  NOT NULL COMMENT '规则行标识（唯一）：<地理区域> 或 <地理区域> / <承运商名>（F-036）',
  `fee_under`   DECIMAL(10,2) NULL COMMENT '基础邮费 USD（subtotal<threshold；NULL 计费按 0）',
  `fee_over`    DECIMAL(10,2) NULL COMMENT '满额邮费 USD（subtotal>=threshold；0 即包邮；NULL 计费按 0）',
  `threshold`   DECIMAL(10,2) NULL COMMENT '满额门槛 USD（NULL=无满额档恒收 fee_under，DEC-SHP-3）',
  `created_at`  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at`  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shipping_rate_zone` (`zone`),
  CONSTRAINT `chk_rate_fee_under` CHECK (`fee_under` IS NULL OR `fee_under` >= 0),
  CONSTRAINT `chk_rate_fee_over`  CHECK (`fee_over`  IS NULL OR `fee_over`  >= 0),
  CONSTRAINT `chk_rate_threshold` CHECK (`threshold` IS NULL OR `threshold` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='国际邮费分区运费规则（结算报价数据源）';
```

### 8.2 种子数据（决策 21：mock 转种子；生产与 dev/staging 同灌——配置类数据）

```sql
-- 承运方：FedEx/UPS/DHL 三启用 + USPS disabled（DEC-SHP-6 原型对照行）
INSERT INTO `carrier` (`name`,`zones`,`lead_time`,`status`) VALUES
  ('FedEx International Priority', '全球',        '3-5 天', 'enabled'),
  ('UPS Worldwide Express',        '北美 / 欧洲', '4-6 天', 'enabled'),
  ('DHL Express',                  '全球',        '3-6 天', 'enabled'),
  ('USPS Priority',                '美国境内',    '2-4 天', 'disabled');

-- 分区规则行：区域 × 承运商差异化价格 + Rest of World 无后缀兜底行（CV-SHP-006）
-- 取值基准：原型 mock 邮费表（$8/$18/$28/$32，门槛 $200~$400）按承运商±档位展开
INSERT INTO `shipping_rate` (`zone`,`fee_under`,`fee_over`,`threshold`) VALUES
  ('North America / FedEx International Priority',  8.00, 0.00, 200.00),
  ('North America / UPS Worldwide Express',        10.00, 0.00, 250.00),
  ('North America / DHL Express',                   9.00, 0.00, 220.00),
  ('Europe / FedEx International Priority',        28.00, 0.00, 400.00),
  ('Europe / UPS Worldwide Express',               26.00, 0.00, 380.00),
  ('Europe / DHL Express',                         27.00, 0.00, 400.00),
  ('Oceania / FedEx International Priority',       32.00, 0.00, 400.00),
  ('Oceania / UPS Worldwide Express',              34.00, 0.00, 420.00),
  ('Oceania / DHL Express',                        33.00, 0.00, 400.00),
  ('Rest of World',                                38.00, 0.00, 500.00);
```

种子自检：①三 enabled 承运商满足 CV-SHP-005；②每区域 3 行精确承运商行验证差异化价格路径；③`Rest of World` 无后缀兜底行验证 DEC-SHP-5 ②/③ 回退路径；④承运商 name 与 er-diagram Order.carrier 枚举三值逐字一致。

### 8.3 权限字典种子（identity permission 表，跨域登记）

```sql
-- 归 identity 域 permission 字典；幂等补登（identity L3 若已按 portal-admin 路由全量补齐则跳过）
INSERT IGNORE INTO `permission` (`key`,`group`,`label`) VALUES
  ('/shipping', '发布与系统', '物流配置');
```

### 8.4 备注

- ① 表名 `carrier`/`shipping_rate` 无 MySQL 保留字冲突，不需反引号转义常量（CP-015 仍配 `CarrierDBConst`/`ShippingRateDBConst extends CommonDBConst`）。
- ② 本域无 translation 附表（决策 13 覆盖清单不含 Carrier/ShippingRate——后台中文管理、报价项 carrier/lead_time 为快照文本直出）。
- ③ 本域无 MQ 事件、无定时任务、无 processed_event 依赖。
