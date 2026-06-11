# shipping 测试设计（L2）

> 角色: l2_test_designer ｜ change: portal-api-integration ｜ domain: shipping
> 多层骨架：单元(UT) / 集成(IT，DB+事务+缓存+锁) / 契约(CT) / API 端到端(AT) / 前端组件(FCT) / 韧性(RST)，统一编号 **TC-SHP-NNN**，AAA 伪代码骨架 + 数据工厂 + P0~P3。
> 覆盖来源：①field-constraint-test-matrix.yml——**本域无行**（矩阵仅含 FLD-CUSTOMERS 系列，显式声明无遗漏）；②boundary-scenarios.yml bs-258~267（carrier/shipping_rate null 场景，§7 逐条映射）；③state-machine.yml carrier_status（TASK-050）；④shipping-api-detail E-SHP-01~09 与域 5 码；⑤SVC-SHP-01 报价算法（trading ShippingQuotePort 消费契约）。
> 数据工厂：`carrierFactory(name='FedEx International Priority', status='enabled')`、`rateFactory(zone, feeUnder=8.00, feeOver=0.00, threshold=200.00)`；种子基线=shipping-data-detail §8.2（4 承运商 + 10 规则行）。

## 1. 单元测试（领域纯逻辑：GeoZoneResolver + 计费 + 组装）

| TC | 内容（AAA） | 溯源 | P |
|---|---|---|---|
| TC-SHP-001 | GeoZoneResolver 码表：US/CA/MX→North America；GB/FR/ES/DE/IT/PL/GR→Europe；AU/NZ→Oceania；JP/BR/ZA/空串/乱码→Rest of World；输入小写 us / 带空白 ' US ' 同样命中（规范化） | api-detail §10.2 | P0 |
| TC-SHP-002 | 国家名别名：'United States'/'USA'→US 桶；'United Kingdom'→Europe；'Australia'→Oceania；未收录名 'Wakanda'→Rest of World | §10.2 别名表 | P1 |
| TC-SHP-003 | 单行计费阈值边界：threshold=200，subtotal=199.99→fee_under；subtotal=200.00→fee_over（**等于取满额**）；subtotal=200.01→fee_over | §10.3 边界语义 | P0 |
| TC-SHP-004 | NULL 语义（DEC-SHP-3）：threshold=null→任意 subtotal 恒 fee_under；fee_under=null→计 0.00；fee_over=null 且 subtotal≥threshold→计 0.00 | CV-SHP-004 | P0 |
| TC-SHP-005 | 多承运商组装：种子下 country=US, subtotal=100 → 3 项（FedEx 8.00/UPS 10.00/DHL 9.00），顺序按 carrier id ASC，lead_time 取各 Carrier；feeUsd scale=2 | §10.3 / MAP-SHP-003 | P0 |
| TC-SHP-006 | 兜底行回退（②）：删除 'Europe / DHL Express' 行后 country=FR → DHL 回退？Europe 无无后缀行 → 走 'Rest of World' 兜底 38.00（③）；新增 'Europe' 无后缀行 25.00 后 → DHL 取 25.00，FedEx/UPS 仍取各自精确行 | DEC-SHP-5 ①②③ | P0 |
| TC-SHP-007 | 全局兜底（③）：country=JP（Rest of World）subtotal=600 → 3 项均 'Rest of World' 行 fee_over=0.00（满额包邮）；subtotal=100 → 均 38.00 | DEC-SHP-5 | P0 |
| TC-SHP-008 | 无报价跳过（④）：清空全部规则行 → quoteOptions 返回空列表不抛错；仅 disabled 承运商专属行存在 → 不为 disabled 承运商出项 | §10.3 步骤 4 | P1 |
| TC-SHP-009 | zone 规范化纯函数：' North  America /  FedEx International Priority ' → 'North America / FedEx International Priority'；唯一比较忽略大小写（'north america' 与 'North America' 判同） | DEC-SHP-1 | P0 |
| TC-SHP-010 | 承运商改名孤行（DEC-SHP-2）：FedEx 改名 'FedEx Intl' 后 country=US → FedEx 项回退路径（精确行不匹配→无 'North America' 无后缀行→Rest of World 38.00），UPS/DHL 不受影响 | E-SHP-03 STEP-03 | P1 |
| TC-SHP-011 | carrier_status 迁移纯 guard：enabled--disable-->disabled 合法（enabledCount>1）；enabledCount==1 时 disable 拒绝（409902 异常类型）；disabled--enable-->enabled 恒合法；同值迁移=幂等短路无副作用 | state-machine carrier_status / TASK-050 | P0 |

## 2. 集成测试（DB + 事务 + 锁 + 缓存）

| TC | 内容 | 溯源 | P |
|---|---|---|---|
| TC-SHP-012 | TX-SHP-001 创建承运方原子：carrier+operation_log 同事务；log 写失败整体回滚无脏行 | E-SHP-02 | P0 |
| TC-SHP-013 | uk_shipping_rate_zone 兜底：绕过判重直插同 zone（忽略大小写变体）→ 唯一冲突→409901 映射，无脏行 | TX-SHP-005 / IDX-SHP-001 | P0 |
| TC-SHP-014 | 409902 并发防护（EC-SHP-001）：种子 3 enabled，并发 3 线程各 disable 一个 → 恰好 2 成功 1 拒绝（409902），终态 enabledCount==1；锁等待超时路径返回 50000 | DEC-SHP-4 | P0 |
| TC-SHP-015 | 禁用+删除竞态：2 enabled（A/B），并发 disable(A)+delete(B) → 串行化后恰一方成功，终态 enabledCount==1 | DEC-SHP-4 | P0 |
| TC-SHP-016 | 缓存失效链：quoteOptions 首调回源写 shipping:rates/carriers → 二调零 DB（SQL 计数断言）；E-SHP-08 改价提交后 → 下一次 quote 读到新价；E-SHP-05 禁用 DHL → 下一次 quote 无 DHL 项 | CACHE-SHP-001/002 / CP-031 | P0 |
| TC-SHP-017 | 失效时机：TX-SHP-006 事务内（提交前）并发 quote 读到旧缓存（无脏读）；事务回滚（模拟 log 失败）→ 缓存未失效仍旧值正确 | CP-031 | P1 |
| TC-SHP-018 | 审计全覆盖：7 个写 action（创建/编辑/删除承运方、承运方状态变更、创建/编辑/删除运费规则）各写一行 operation_log，changes 含 before/after；列表读不写日志 | BE-DIM-7 / api-detail §11 | P1 |
| TC-SHP-019 | E-SHP-05 幂等短路：对 enabled 行提交 status=enabled → 200 返回现行；无 UPDATE、无 operation_log、无缓存失效（SQL/日志计数断言） | E-SHP-05 STEP-02 | P1 |
| TC-SHP-020 | 种子完整性：建表+灌种后 enabledCount==3、规则行 10 行、'Rest of World' 兜底行存在、承运商三 name 与 Order.carrier 枚举逐字相等 | data-detail §8.2 自检 | P1 |

## 3. 契约/API 端到端（AT，9 端点全覆盖）

| TC | 内容 | 溯源 | P |
|---|---|---|---|
| TC-SHP-021 | E-SHP-01/06 列表：200 R 包络 `{code:0,data:{items:[...]}}`；snake_case 字段（lead_time/fee_under）；全量无分页字段 | 契约 R 包络映射 | P0 |
| TC-SHP-022 | E-SHP-02 创建：合法体 201 返回完整 Carrier；缺 name/status → 422 422901 + details.field；name 65 字符 → 422901（bs-259 必填 + CV-SHP-001 长度） | V-SHP-003/004 | P0 |
| TC-SHP-023 | E-SHP-03 编辑：不存在 id → 404 404901；将唯一 enabled 改 disabled → 409 409902；合法整单覆盖 200（zones 提交 null → 落空，bs-260/261） | V-SHP-007 / STEP-02 | P0 |
| TC-SHP-024 | E-SHP-04 删除：disabled 行 204；最后 enabled 行 → 409902；不存在 → 404901；删除后历史订单 carrier 字段不变（联合 trading 订单工厂断言快照） | E-SHP-04 | P0 |
| TC-SHP-025 | E-SHP-05 状态切换：disable 合法 → 200 status=disabled；status 传 'paused' → 422 422901；最后 enabled disable → 409902 | V-SHP-008 | P0 |
| TC-SHP-026 | E-SHP-07 创建规则：合法 201；zone 重复（含大小写/空白变体）→ 409 409901；fee_under=-1 → 422 422901；三费用字段全 null → 201 落空值（bs-265~267） | V-SHP-009/010 | P0 |
| TC-SHP-027 | E-SHP-08 编辑规则：不存在 → 404 404902；改 zone 撞他行 → 409901；改 zone 为自身等值（大小写变体）→ 200 不误判 | STEP-02 排除自身 | P0 |
| TC-SHP-028 | E-SHP-09 删除规则：204；重复删除 → 404902 | E-SHP-09 | P1 |
| TC-SHP-029 | 鉴权矩阵：无 token → 401 40100；store JWT 误用 → 401 40100；无 /shipping 权限的 admin → 403 40300（9 端点抽样 4 个） | §0 鉴权 | P0 |
| TC-SHP-030 | 路径参数：id='abc' → 422 422901 details.field=id；id=0/-1 → 422901 | V-SHP-002 | P2 |

## 4. 跨域消费契约（与 trading 联测）

| TC | 内容 | 溯源 | P |
|---|---|---|---|
| TC-SHP-031 | 端口签名兼容：trading 注入 ShippingQuotePort 调 quoteOptions('US', 100.00) → ShippingOptionQuote{carrier,feeUsd,leadTime} 三字段可直接组装 trading shipping_options[]（编译期 + 字段断言双验证） | trading-api-detail §0 / api-detail §10.1 | P0 |
| TC-SHP-032 | FLOW-P05 端到端：trading quote 端点（POST /api/store/checkout/quote, country=US）→ 返回 3 个 shipping_options，fee 已换算订单币种、carrier 三值与种子一致 | FLOW-P05 / trading STEP-TRD-03/04 | P0 |
| TC-SHP-033 | 规则变更即时生效：管理端改价 → 立即重调 trading quote → 新价（缓存已失效）；契约注记「已创建订单 shipping_fee 快照不变」用既有订单断言 | E-SHP-08 STEP-05 | P1 |

## 5. 前端组件测试（FCT，Vitest + Testing Library）

| TC | 内容 | 溯源 | P |
|---|---|---|---|
| TC-SHP-034 | useShippingStore.fetchAll 并行拉取两列表；toggleCarrier 乐观更新 → mock 409902 → 本地态回滚 + toast 文案断言 | STORE-SHP-01 | P0 |
| TC-SHP-035 | enabledCount===1 预判：唯一 enabled 行 Toggle 与删除按钮置灰 + tooltip；其余行可操作 | STORE-SHP-01 getter | P0 |
| TC-SHP-036 | CarrierFormDrawer：name 空提交 inline 错误不发请求；422901 details.field=name → inline 分发；保存中按钮防双击 | FORM-SHP-01 | P1 |
| TC-SHP-037 | RateFormDrawer：409901 → zone inline「同名规则行已存在」；fee 输入 -1 前端拦截；null 费用渲染 '—'、feeOver=0 渲染「免邮」text-ok | FORM-SHP-02 / COMP-SHP-03 | P1 |
| TC-SHP-038 | 删除兜底行警示：zone='Rest of World' 删除确认弹窗含全局兜底警示文案；普通行无 | FORM-SHP-03 / CV-SHP-006 | P2 |
| TC-SHP-039 | 改名运营提示：saveCarrier name 变更成功 → 追加「同步检查邮费规则行后缀」toast | STORE-SHP-01 / DEC-SHP-2 | P2 |

## 6. 韧性（RST）

| TC | 内容 | 溯源 | P |
|---|---|---|---|
| TC-SHP-040 | Redis 不可用：quoteOptions 缓存层异常 → JetCache 降级直查 DB（两级缓存本地级兜底），报价不中断；写路径锁不可得 → 50000 且无半写 | EC-SHP-001 / BE-DIM-5 | P1 |
| TC-SHP-041 | DB 异常：列表/报价路径 → 50001 不暴露 SQL；trading quote 透传 50001（不吞错，§10.3 失败传播） | error-strategy 分层 | P1 |

## 7. boundary-scenarios 映射自检

| bs | 场景 | 落点 TC |
|---|---|---|
| bs-258 | carrier id null（创建不携带） | TC-SHP-022（创建路径 id 由自增产生） |
| bs-259 | carrier name null 拒绝 | TC-SHP-022 |
| bs-260 | carrier zones null 容忍 | TC-SHP-023 |
| bs-261 | carrier lead_time null 容忍 | TC-SHP-023 |
| bs-262 | carrier status null 拒绝 | TC-SHP-022 |
| bs-263 | shipping_rate id null | TC-SHP-026（自增） |
| bs-264 | shipping_rate zone null 拒绝 | TC-SHP-026 |
| bs-265~267 | fee_under/fee_over/threshold null 容忍并保持为空 | TC-SHP-026 + TC-SHP-004（计费语义） |

覆盖自检：9/9 端点（§3）+ 域 5 码全触发（404901:TC-023/024、404902:TC-027/028、409901:TC-013/026/027、409902:TC-014/023/024/025、422901:TC-022/025/026/030）+ carrier_status 状态机（TC-011/025）+ 报价算法全分支（§1）+ bs-258~267 全映射；TC-SHP-001~041 无重号。
