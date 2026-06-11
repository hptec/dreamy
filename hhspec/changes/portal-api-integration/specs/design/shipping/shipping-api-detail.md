# shipping API 详细设计（L2）

> 角色: l2_api_designer ｜ change: portal-api-integration ｜ domain: shipping
> 方法论：每端点四部分 — 入参验证(V-SHP-NNN) / 业务逻辑流程(STEP-SHP-NN) / 出参构造 / 错误码映射。
> 来源权威：api-contracts/shipping-api.openapi.yml v1.1.0（9 操作）+ data-flow.md（FLOW-P05 进程内直调 + 缓存矩阵 shipping 行）+ error-strategy.md（shipping 域段 9 共 5 码）+ er-diagram.yml（Carrier/ShippingRate）+ state-machine.yml（carrier_status）+ design/trading/trading-api-detail.md §0（ShippingQuotePort 消费侧声明，本文 §10 为提供侧权威定义点）。
> 伪代码级，不绑定 Spring 语法。所有 JSON 字段 snake_case；契约错误 Schema `{code,message,details}` 在线上装入 R 包络（details → R.data）。
>
> **约束 ID 约定**：`V-SHP-NNN` 全域唯一连续编号（V-SHP-001 ~ V-SHP-011，无重号）；`STEP-SHP-NN` 在端点内编号，全局引用名为 `<operationId>.STEP-SHP-NN`（如 `toggleAdminCarrierStatus.STEP-SHP-03`），与 identity/trading 样板（端点内 STEP 复位）同风格。

## 0. 全局横切（所有端点适用）

- **R 包络**：成功 `R{code:0, message:"ok", data:<payload>}`；失败 `R{code:<6位码>, message, data:<details>}`。本文各端点「出参」均指 data 载荷。本域列表全量返回不分页（承运商/规则行均为小配置集，契约 info 声明）。
- **鉴权**：`/api/admin/shipping/*` 全部走 AdminJwtFilter + RBAC 菜单权限 key **`/shipping`**（BE-DIM-6 新增权限点；权限字典种子见 shipping-data-detail §8.3）。未认证/跨端 token 误用 → 401 `40100`；缺权限 → 403 `40300`。本域**无消费端公开端点**，不触碰 StoreJwtFilter 白名单。
- **V-SHP-001（通用鉴权前置）**：所有端点先过 AdminJwtFilter（40100）再过 RBAC `/shipping` 守卫（40300），后进入业务校验。
- **V-SHP-002（通用路径参数）**：含 `{id}` 的端点，id 必须为 int64 正整数；非法格式（非数字/越界）→ 422 `422901`（details: {field:"id"}）。
- **审计（BE-DIM-7）**：admin 写操作 AOP 写 operation_log，action 枚举（error-strategy 权威清单）：创建承运方 / 编辑承运方 / 删除承运方 / 承运方状态变更 / 创建运费规则 / 编辑运费规则 / 删除运费规则。changes 记 before/after（状态变更记 status 前后值）。
- **缓存（BE-DIM-8，缓存矩阵 shipping 行）**：后台列表端点（E-SHP-01/06）**不缓存**（实时读库）；`shipping:carriers` / `shipping:rates`（JetCache 两级，TTL 600s）仅供 §10 报价直调读取，由本域写操作事务提交后 @CacheInvalidate（进程内，**无 MQ、无 CDN**——消费端无直接读端点）。
- **消费端无 REST 端点（决策 2/3）**：结算运费计算由 trading 域进程内同步直调 §10 `ShippingQuotePort`；对外契约见 trading-api `POST /api/store/checkout/quote` 的 shipping_options/shipping_fee 字段。
- **错误码全集（域段 9）**：404901 CARRIER_NOT_FOUND / 404902 SHIPPING_RATE_NOT_FOUND / 409901 ZONE_EXISTS / 409902 LAST_ENABLED_CARRIER / 422901 FIELD_VALIDATION_FAILED；复用 identity：40100 / 40300 / 50000 / 50001。admin 端 message 固定中文。
- **zone 规范化（DEC-SHP-1）**：zone 文本入库前规范化——trim 首尾空白 + 连续空白折叠为单空格；唯一性比较（409901）与报价匹配（§10）一律对规范化文本做**忽略大小写**比较；存储保留规范化后原始大小写。

### 设计决策（DEC-SHP）

| ID | 决策 | 理由 |
|---|---|---|
| DEC-SHP-1 | zone 规范化 + 忽略大小写唯一/匹配（见上） | 自由文本约定「`<地理区域> / <承运商名>`」依赖字符串精确匹配，规范化消除空白/大小写抖动导致的兜底误回退 |
| DEC-SHP-2 | 承运商改名**不联动**改写规则行 zone 后缀：旧 `"<区域> / <旧名>"` 行成为非匹配行，该承运商报价回退区域兜底行；前端改名保存成功后 toast 提示运营同步维护规则行 | 联动改写会与 uk_shipping_rate_zone 产生级联冲突分支（改写目标已存在），复杂度高于收益；回退兜底行为契约 F-036 既有语义，结果可预期 |
| DEC-SHP-3 | 费用字段 NULL 语义：`threshold IS NULL` → 该行无满额档，恒收 fee_under；`fee_under IS NULL` / `fee_over IS NULL` → 计费按 0.00 | 契约仅 zone 必填（boundary bs-265~267 要求 null 容忍）；NULL threshold 解释为「未配置满额包邮」比「恒满额」符合运营直觉 |
| DEC-SHP-4 | carrier 写操作（E-SHP-02~05）经 huihao-redis 分布式锁 `onIdLock("shipping:carrier-write")` 串行化（等待 3s / 租约 10s），锁内执行「最后启用承运方」guard + 写库 | 409902 是计数型不变量（enabled ≥ 1），并发双禁用/禁用+删除竞态下纯乐观校验会击穿；表仅个位数行，串行化零性能代价（CP-012 同范式） |
| DEC-SHP-5 | 报价匹配优先级：①「`<区域> / <承运商名>`」精确行 → ②「`<区域>`」无后缀兜底行 → ③「`Rest of World`」全局兜底（含其 `/承运商` 行与无后缀行，同①②次序）→ ④仍无 → 该承运商无报价项（跳过，不抛错） | 契约 F-036 第 1/3 条的确定性展开；③ 使「收货国未配置区域行」与「国家无法识别」收敛到同一兜底路径 |
| DEC-SHP-6 | 种子含 USPS Priority（disabled）第 4 行承运商 | 决策 21 mock 转种子 + 原型 Shipping.vue 第 4 行 disabled Toggle 视觉对照；不影响报价（仅 enabled 参与） |

---

## 1. E-SHP-01 listAdminCarriers — GET /api/admin/shipping/carriers

**入参验证**：无业务入参（V-SHP-001 鉴权前置）。

**业务逻辑**:
- STEP-SHP-01 `SELECT carrier ORDER BY id ASC`（RM-SHP-001，全量不分页，不缓存）

**出参**: 200 `{ items: [Carrier{id, name, zones, lead_time, status}] }`（zones/lead_time 可为 null）
**错误映射**: 401 `40100` / 403 `40300` / 500 `50000`

## 2. E-SHP-02 createAdminCarrier — POST /api/admin/shipping/carriers

**入参**: `{ name, zones?, lead_time?, status }`
- V-SHP-003 name 必填，trim 后非空且 ≤64 → 否则 422 `422901`（details: {field:"name"}）
- V-SHP-004 status 必填，∈ {enabled, disabled} → 否则 422 `422901`（details: {field:"status"}）
- V-SHP-005 zones 可空；提供时 trim 后 ≤255 → 否则 422 `422901`
- V-SHP-006 lead_time 可空；提供时 trim 后 ≤64 → 否则 422 `422901`

**业务逻辑**（TX-SHP-001，DEC-SHP-4 锁内）:
- STEP-SHP-01 `INSERT carrier(name, zones, lead_time, status)`（RM-SHP-004）
- STEP-SHP-02 `INSERT operation_log(action=创建承运方, changes.after=载荷)`
- STEP-SHP-03 事务提交后 `@CacheInvalidate shipping:carriers`（CACHE-SHP-001）

**出参**: 201 `Carrier`（完整行）
**错误映射**: 401 / 403 / 422 `422901` / 500 `50000`·`50001`

## 3. E-SHP-03 updateAdminCarrier — PUT /api/admin/shipping/carriers/{id}

**入参**: path id；body `CarrierUpsert`（同 E-SHP-02）
- V-SHP-002（id）；V-SHP-007 body 校验同 V-SHP-003~006（整单覆盖语义，全字段重校验）

**业务逻辑**（TX-SHP-002，DEC-SHP-4 锁内）:
- STEP-SHP-01 `findById(id)`（RM-SHP-002）→ 不存在 404 `404901`
- STEP-SHP-02 最后启用 guard：若现行 status=enabled 且本次提交 status=disabled 且 `countEnabled()==1`（RM-SHP-003）→ 409 `409902`（details: {enabled_count:1}）
- STEP-SHP-03 `UPDATE carrier SET name, zones, lead_time, status`（RM-SHP-005）；name 变更**不联动**规则行（DEC-SHP-2，孤行回退兜底）
- STEP-SHP-04 `operation_log(action=编辑承运方, changes before/after)`
- STEP-SHP-05 事务提交后失效 `shipping:carriers`

**出参**: 200 `Carrier`
**错误映射**: 401 / 403 / 404 `404901` / 409 `409902` / 422 `422901` / 500

## 4. E-SHP-04 deleteAdminCarrier — DELETE /api/admin/shipping/carriers/{id}

**入参**: path id（V-SHP-002）

**业务逻辑**（TX-SHP-003，DEC-SHP-4 锁内）:
- STEP-SHP-01 `findById(id)` → 不存在 404 `404901`
- STEP-SHP-02 最后启用 guard：若该行 status=enabled 且 `countEnabled()==1` → 409 `409902`（契约：最后一个启用承运方不可删；disabled 行可直接删）
- STEP-SHP-03 `DELETE carrier WHERE id=?`（RM-SHP-007）。订单 Order.carrier 为字段快照不受影响（契约声明，不强外键）；指向该承运商的 `"<区域> / <名>"` 规则行成为非匹配行（报价自然不再为其计费），由运营在 /shipping 页面清理
- STEP-SHP-04 `operation_log(action=删除承运方)`
- STEP-SHP-05 事务提交后失效 `shipping:carriers`

**出参**: 204（无 body）
**错误映射**: 401 / 403 / 404 `404901` / 409 `409902` / 500

## 5. E-SHP-05 toggleAdminCarrierStatus — PATCH /api/admin/shipping/carriers/{id}/status

**入参**: path id；body `{ status }`
- V-SHP-002（id）；V-SHP-008 status 必填 ∈ {enabled, disabled} → 否则 422 `422901`

**业务逻辑**（TX-SHP-004，DEC-SHP-4 锁内；carrier_status 状态机 TASK-050 落点）:
- STEP-SHP-01 `findById(id)` → 不存在 404 `404901`
- STEP-SHP-02 幂等短路：提交 status 与现行相同 → 直接 200 返回现行（无写库、无审计、无失效）
- STEP-SHP-03 状态机迁移 guard：`enabled --disable--> disabled` 仅当 `countEnabled() > 1`；`countEnabled()==1` 时禁用 → 409 `409902`；`disabled --enable--> enabled` 无 guard（state-machine.yml carrier_status 两迁移 + 本域 409902 业务 guard 叠加）
- STEP-SHP-04 `UPDATE carrier SET status=:status`（RM-SHP-006）
- STEP-SHP-05 `operation_log(action=承运方状态变更, changes:{status: before→after})`
- STEP-SHP-06 事务提交后失效 `shipping:carriers`

**出参**: 200 `Carrier`
**错误映射**: 401 / 403 / 404 `404901` / 409 `409902` / 422 `422901` / 500

## 6. E-SHP-06 listAdminShippingRates — GET /api/admin/shipping/rates

**入参验证**：无业务入参（V-SHP-001）。

**业务逻辑**:
- STEP-SHP-01 `SELECT shipping_rate ORDER BY id ASC`（RM-SHP-010，全量不分页，不缓存）

**出参**: 200 `{ items: [ShippingRate{id, zone, fee_under, fee_over, threshold}] }`（费用三字段可为 null，语义见 DEC-SHP-3）
**错误映射**: 401 / 403 / 500

## 7. E-SHP-07 createAdminShippingRate — POST /api/admin/shipping/rates

**入参**: `{ zone, fee_under?, fee_over?, threshold? }`
- V-SHP-009 zone 必填，规范化（DEC-SHP-1）后非空且 ≤128 → 否则 422 `422901`（details: {field:"zone"}）
- V-SHP-010 fee_under / fee_over / threshold 可空；提供时为 number 且 ≥0、≤99999999.99、小数位 ≤2 → 否则 422 `422901`（details: {field:"fee_under"|"fee_over"|"threshold"}）

**业务逻辑**（TX-SHP-005）:
- STEP-SHP-01 判重：`existsByZoneNormalized(zoneNorm)`（RM-SHP-012，忽略大小写）→ 已存在 409 `409901`（details: {zone: 规范化文本}）
- STEP-SHP-02 `INSERT shipping_rate(zone=规范化文本, fee_under, fee_over, threshold)`（RM-SHP-013）；uk_shipping_rate_zone 唯一冲突（并发竞态）→ 捕获转 409 `409901`
- STEP-SHP-03 `operation_log(action=创建运费规则)`
- STEP-SHP-04 事务提交后 `@CacheInvalidate shipping:rates`（CACHE-SHP-002，结算报价 600s TTL 内即时生效）

**出参**: 201 `ShippingRate`
**错误映射**: 401 / 403 / 409 `409901` / 422 `422901` / 500

## 8. E-SHP-08 updateAdminShippingRate — PUT /api/admin/shipping/rates/{id}

**入参**: path id；body `ShippingRateUpsert`（同 E-SHP-07）
- V-SHP-002（id）；V-SHP-011 body 校验同 V-SHP-009/010

**业务逻辑**（TX-SHP-006）:
- STEP-SHP-01 `findById(id)`（RM-SHP-011）→ 不存在 404 `404902`
- STEP-SHP-02 zone 变更时判重（排除自身行：`existsByZoneNormalized(zoneNorm, excludeId=id)`）→ 冲突 409 `409901`
- STEP-SHP-03 `UPDATE shipping_rate SET zone, fee_under, fee_over, threshold`（RM-SHP-014，整单覆盖；提交 null 即清空该费用字段，DEC-SHP-3 语义生效）
- STEP-SHP-04 `operation_log(action=编辑运费规则)`
- STEP-SHP-05 事务提交后失效 `shipping:rates`（契约注记：结算报价即时生效；已创建订单 shipping_fee 为快照不变）

**出参**: 200 `ShippingRate`
**错误映射**: 401 / 403 / 404 `404902` / 409 `409901` / 422 `422901` / 500

## 9. E-SHP-09 deleteAdminShippingRate — DELETE /api/admin/shipping/rates/{id}

**入参**: path id（V-SHP-002）

**业务逻辑**（TX-SHP-007）:
- STEP-SHP-01 `DELETE shipping_rate WHERE id=?`（RM-SHP-015）→ affected=0 → 404 `404902`
- STEP-SHP-02 `operation_log(action=删除运费规则)`
- STEP-SHP-03 事务提交后失效 `shipping:rates`。删除 `Rest of World` 兜底行会使未配置区域无报价项（CV-SHP-006 运营约束），前端二次确认弹窗对兜底行追加警示文案（FORM-SHP-03）

**出参**: 204
**错误映射**: 401 / 403 / 404 `404902` / 500

---

## 10. SVC-SHP-01 多承运商报价领域服务（ShippingQuoteService，提供侧权威定义点）

> 消费侧声明（已定稿，逐字对齐）：trading-api-detail.md §0「跨域端口」——
> `ShippingQuotePort`（shipping 域）：`quoteOptions(country, subtotalUsd) -> List<ShippingOptionQuote{carrier, fee_usd, lead_time}>`
> 本节为该端口的**提供侧权威定义**：接口与 DTO 形状以本节为准且与上行声明完全一致；trading 仅注入接口，禁止直查本域表（决策 3）。

### 10.1 端口签名（Java 伪代码）

```java
// com.dreamy.shipping.api（域公开 API 包，trading 依赖此包，不依赖 domain 内部）
public interface ShippingQuotePort {
    /**
     * 多承运商运费报价（FLOW-P05 进程内同步直调，只读，无副作用）。
     * @param country     收货国家（ISO 3166-1 alpha-2 码或国家英文名，内部规范化，见 10.2）
     * @param subtotalUsd 购物车小计（USD 基准口径，决策 14）
     * @return 每个 status=enabled 的 Carrier 至多一项；可能为空列表（无可计费规则行时，DEC-SHP-5 ④）
     */
    List<ShippingOptionQuote> quoteOptions(String country, BigDecimal subtotalUsd);
}

/** 报价项（字段与 trading shipping_options 组装直接对应） */
public record ShippingOptionQuote(
    String carrier,        // Carrier.name（= Order.carrier 枚举快照值）
    BigDecimal feeUsd,     // USD 基准运费，scale=2 HALF_UP（trading 侧负责换算订单币种）
    String leadTime        // Carrier.lead_time 时效描述（可为 null，trading 透传）
) {}
```

### 10.2 GeoZoneResolver — 收货国家 → 地理区域映射（提供侧权威表）

trading STEP-TRD-03 声明的映射（「North America: US/CA/MX；Europe: GB/IE/FR/ES/DE/IT/…；Oceania: AU/NZ；其余 → Rest of World」）以本表为权威全量展开：

| 区域（zone 前缀文本） | alpha-2 国家码 |
|---|---|
| `North America` | US, CA, MX |
| `Europe` | GB, IE, FR, ES, DE, IT, PT, NL, BE, LU, AT, CH, SE, NO, DK, FI, IS, PL, CZ, SK, HU, RO, BG, GR, HR, SI, EE, LV, LT, MT, CY |
| `Oceania` | AU, NZ |
| 其余/无法识别 | `Rest of World` |

- 输入规范化：trim + 大写；先按 alpha-2 码精确匹配；非两位码时按国家英文名别名表二次匹配（`United States`/`USA`→US、`United Kingdom`→GB、`Canada`→CA、`Mexico`→MX、`Australia`→AU、`New Zealand`→NZ、`France`→FR、`Spain`→ES、`Germany`→DE、`Italy`→IT、`Ireland`→IE 等，覆盖 Address.country 自由文本来源）；仍未识别 → `Rest of World`。
- 区域文本为英文规范名（与种子 zone 前缀一致）；映射表为代码内常量（配置化收益低，变更走发版）。

### 10.3 报价算法（STEP 级伪代码）

```
quoteOptions(country, subtotalUsd):
  1. region   = GeoZoneResolver.resolve(country)                     // 10.2
  2. carriers = JC.get("shipping:carriers", () -> RM-SHP-008 listEnabled())   // CACHE-SHP-001
  3. rates    = JC.get("shipping:rates",    () -> RM-SHP-010 listAll())       // CACHE-SHP-002
     ratesIdx = rates 按规范化 zone（忽略大小写）建内存索引
  4. for c in carriers:                                              // 仅 enabled（契约规则 3/4）
       line = ratesIdx["{region} / {c.name}"]                        // ① 区域×承运商精确行
           ?? ratesIdx["{region}"]                                   // ② 区域无后缀兜底行（价格对全部 enabled 生效）
           ?? (region != "Rest of World"
                 ? (ratesIdx["Rest of World / {c.name}"] ?? ratesIdx["Rest of World"])  // ③ 全局兜底（DEC-SHP-5）
                 : null)
       if line == null: continue                                     // ④ 该承运商无报价项，跳过
       fee = (line.threshold == null || subtotalUsd < line.threshold)
               ? nvl(line.fee_under, 0.00)                           // 基础邮费（DEC-SHP-3）
               : nvl(line.fee_over, 0.00)                            // 满额邮费；0.00 即满额包邮
       emit ShippingOptionQuote(c.name, fee.setScale(2, HALF_UP), c.lead_time)
  5. return options                                                  // 顺序 = enabled 承运商 id ASC，稳定可测
```

- **边界语义**：`subtotal == threshold` 取 fee_over（契约「>= threshold 时收 fee_over」）；时效一律取各 Carrier.lead_time（兜底行不带时效，契约规则 1）。
- **事务/缓存**：只读，无事务；两级缓存命中时零 DB 访问（FLOW-P05 时序）；缓存未命中回源 + 回填 TTL 600s。
- **失败传播**：DB 异常 → 50001 由 trading 报价端点统一透出；本服务不吞错、不降级（运费是结算强依赖）。
- **空结果**：返回空列表时由消费侧处理（trading 契约 shipping_options ≥1 依赖 `Rest of World` 兜底行存在——CV-SHP-006 运营约束 + 种子保证）。

---

## 11. 端点 × 错误码 × 审计 × 缓存失效总表（自检）

| 端点 | operationId | 错误码 | 审计 action | 失效 key |
|---|---|---|---|---|
| E-SHP-01 GET /carriers | listAdminCarriers | 40100/40300/50000 | — | — |
| E-SHP-02 POST /carriers | createAdminCarrier | +422901 | 创建承运方 | shipping:carriers |
| E-SHP-03 PUT /carriers/{id} | updateAdminCarrier | +404901/409902/422901 | 编辑承运方 | shipping:carriers |
| E-SHP-04 DELETE /carriers/{id} | deleteAdminCarrier | +404901/409902 | 删除承运方 | shipping:carriers |
| E-SHP-05 PATCH /carriers/{id}/status | toggleAdminCarrierStatus | +404901/409902/422901 | 承运方状态变更 | shipping:carriers |
| E-SHP-06 GET /rates | listAdminShippingRates | 40100/40300/50000 | — | — |
| E-SHP-07 POST /rates | createAdminShippingRate | +409901/422901 | 创建运费规则 | shipping:rates |
| E-SHP-08 PUT /rates/{id} | updateAdminShippingRate | +404902/409901/422901 | 编辑运费规则 | shipping:rates |
| E-SHP-09 DELETE /rates/{id} | deleteAdminShippingRate | +404902 | 删除运费规则 | shipping:rates |

9/9 端点四 Part 齐全；域 5 码（404901/404902/409901/409902/422901）全部有触发路径；V-SHP-001~011 无重号。
