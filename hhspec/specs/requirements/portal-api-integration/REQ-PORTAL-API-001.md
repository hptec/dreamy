# REQ-PORTAL-API-001：消费端/管理端前后端对接（数据层 mock → 真实 API）

- **change**：portal-api-integration
- **类型**：alignment（UI 已按原型实现，本次补全后端 API 与前端数据层对接）
- **领域**：catalog / review / trading / shipping / marketing / showroom / analytics（七域细拆，见 decision.md 决策 2）
- **权威产物**：er-diagram.yml（53 实体）、state-machine.yml（21 状态机）、business-flow.yml（16 流程）、acceptance.yml（1058 场景）、feature-gap-report.yml（ALIGN-001~015）

## 背景

后端当前仅有 identity 域（35 端点，两端已 100% 对接）。管理端 15 个页面与消费端商品/购物车/订单/地址等页面的数据层均为 mock，需新建六域后端 API 并完成两端对接。性能架构采用三层缓存（CDN/ISR + JetCache 两级 + 交易数据不缓存），秒级主动失效链，P95≤100ms / 1000 并发压测验收（decision.md 决策 4/5）。

## 需求清单（requirement_ids）

| 编号 | 页面/能力 | 所属域 | 对接内容 | 验收来源 |
|------|----------|--------|---------|---------|
| ALIGN-001 | Dashboard 工作台 | analytics | KPI/待办/趋势实时聚合 + JetCache 60s | acceptance.yml s-759 |
| ALIGN-002 | Products 商品列表 | catalog | 列表/筛选/状态切换 API | product_lifecycle 场景集 |
| ALIGN-003 | ProductEdit 商品编辑 | catalog | 基础信息/定制尺寸/图片（预签名 URL 上传）持久化 | s-757 及 Product 边界场景 |
| ALIGN-004 | Categories 分类管理 | catalog | 三层分类树 + 属性集 + 标签 CRUD | category/tag lifecycle 场景集 |
| ALIGN-005 | Orders 订单列表 | trading | 订单查询/筛选 API | order_lifecycle 场景集 |
| ALIGN-006 | OrderDetail 订单详情 | trading | 跟踪/发货/支付信息（Stripe）| s-752/s-754 |
| ALIGN-007 | Refunds 退款管理 | trading | 审核 + Stripe 退款原子事务 | s-755 |
| ALIGN-008 | Promotions 优惠券/闪购 | marketing | 券 CRUD/核销、闪购到期下线 | s-760/s-761 |
| ALIGN-009 | Banners 管理 | marketing | Banner CRUD/排序/定时投放 | banner_lifecycle 场景集 |
| ALIGN-010 | ContentBlog 博客 | marketing | 文章 CRUD/发布 | blog_post_lifecycle 场景集 |
| ALIGN-011 | ContentWeddings 案例 | marketing | 案例 CRUD/发布 | real_wedding_publish 场景集 |
| ALIGN-012 | ContentLookbook 穿搭 | marketing | Lookbook/Guide CRUD/发布 | lookbook/guide publish 场景集 |
| ALIGN-013 | Analytics 看板 | analytics | GMV 趋势/漏斗/流量聚合 | s-759 |
| ALIGN-014 | Reviews 评价审核 | review | 提交（仅 completed 订单）/审核/越权防护 | s-756/s-762 |
| ALIGN-015 | Shipping 物流配置 | shipping | 承运商/运费规则 CRUD，结算运费计算 | carrier_status 场景集 |

消费端对应：商品列表/详情/搜索、购物车（DB 持久+匿名合并）、结算（Stripe 沙箱）、订单、地址簿、评价提交、内容页——以真实 API 替换 `data/products.ts` 等 mock（见 api-integration-audit.md）。

### 2026-06-10 细化轮补充需求（decision.md 决策 13-17）

| 编号 | 能力 | 所属域 | 内容 | 验收来源 |
|------|------|--------|------|---------|
| ALIGN-016 | 内容数据三语（EN/ES/FR） | catalog/marketing | 13 张 xxx_translation 附表（EN 存主表，缺翻译回退 EN）；管理端表单加语言 tab；消费端 API 按 locale 返回；缓存 key 含 locale 维度 | s-763~s-765, bs-657~bs-661 |
| ALIGN-017 | 多币种结算（五币种） | trading | USD 基准定价 + 后端汇率表（ExchangeRate，管理端维护，补 EUR）；下单锁汇（Order.exchangeRate）；Stripe 按订单币种收款；退款原币种原金额 | s-766~s-768, bs-662~bs-666 |
| ALIGN-018 | 税费立场 | trading | 含税价 + DDU（结算无 Tax 行，加说明文案，与原型一致） | 决策 15 |
| ALIGN-019 | 交易性邮件 | trading | MQ 订单事件消费者发订单确认/发货/退款结果三类邮件；MailRecord 幂等+重试（mail_delivery 状态机）；按 locale 三语渲染；SMTP 抽象 dev 落日志 | s-769~s-772, bs-667~bs-670 |
| ALIGN-020 | 商品搜索后端化 | catalog | MySQL FULLTEXT ngram（商品名/副标题/标签，含 translation 按 locale 检索）+ JetCache 短 TTL；/search 页改后端 API | s-773/s-774, bs-671~bs-674 |

> 数据库架构经确认：全量保留 MySQL（用户曾考虑 Doris 替换，因 OLTP 事务/乐观锁/自增主键能力缺失撤回，性能问题留待后续独立 change）。

### 2026-06-10 细化轮三补充需求（decision.md 决策 18-21）

| 编号 | 能力 | 所属域 | 内容 | 验收来源 |
|------|------|--------|------|---------|
| ALIGN-021 | Wishlist 后端持久化 | trading | WishlistItem 实体（customerId+productId 唯一，幂等）+ /api/store/wishlists（列表/添加/移除/移入购物车）；登录专属，未登录引导登录；user_id 强隔离 | wishlist_manage 场景集（s-1040 及 WishlistItem 边界场景） |
| ALIGN-022 | GA4 流量分析 | analytics | 消费端 gtag 标准电商事件（page_view/view_item/add_to_cart/begin_checkout/purchase）+ Cookie consent banner（Consent Mode v2）；管理端流量图表（trafficSources/funnel/device_share）经后端 GA4 Data API 拉取 + JetCache 5min；失败降级"数据暂不可用"，交易指标不受影响；凭证仅后端配置 | ga4_traffic_fetch 场景集（s-1043） |
| ALIGN-023 | Showroom 伴娘团协作 | showroom（新域） | Showroom/ShowroomItem/ShowroomMember/ShowroomVote/ShowroomComment 5 实体；邀请 UUID token + 受限 guest JWT（免注册访客投票/留言，按 member 去重）；新娘指派款式 + 提醒真发邮件（复用 MailRecord）；24h 同款式 dye lot 提示；下单才要求登录；消费端按「复制+适配」从原型落地 showroom 页面 | showroom_collaboration 场景集（s-1041）+ showroom_member_assignment 状态机场景 |
| ALIGN-024 | 尺码推荐 + PDP 定制表单/婚期交期 | catalog | Find My Size 问卷 → 后端 SizeChartRow 区间匹配纯函数端点（区间说明话术，不虚构买家占比）；PDP Custom Size 表单（依赖 A-007 SKU 字段）+ 购物车/结算/订单透出定制明细；结算 wedding date 选填（Order.weddingDate，Showroom 婚期自动带入）+ 交期复核提示 | size_recommendation 场景集（s-1042） |
| ALIGN-025 | 种子数据 | 全域 | 前端 mock 演示数据转种子导入脚本（含三语 translation 表）；订单类样例仅 dev/staging 灌入，生产只灌配置+内容类 | 决策 21（apply 阶段交付物） |

### 2026-06-10 细化轮四补充需求（decision.md 决策 22-24）

| 编号 | 能力 | 所属域 | 内容 | 验收来源 |
|------|------|--------|------|---------|
| ALIGN-026 | portal-store 部署形态改造 | 基础设施 | 去 `output:'export'` 改 Node standalone（docker 容器 + 健康检查 + restart）；CDN 前置（s-maxage + serve-stale 兜底）；MQ 失效消费者增加 Cloudflare purge 调用；商品页 SSR/ISR 保 SEO，新商品免重建即可访问 | content_publish_invalidate 场景集（s-758，失效链含 purge 断言） |
| ALIGN-027 | Recently Viewed 浏览历史 | trading | BrowseHistory 实体（customerId+productId 唯一，upsert 更新 viewedAt，每用户保留 50 条滚动清理）；登录用户记录、匿名不记录；账户页横滑倒序展示（F-048） | browse_history_track 场景集（s-1058 等） |
| ALIGN-028 | 定制款退款政策 | trading | 现货未发货全额退/已发货退货后退；定制款支付后 24h 宽限期内可退，超时视为投产不可退（422 + 入口置灰 + 三语说明）；按 OrderLine.customSizeData 判定定制行，Order.paidAt+24h 判定投产；结算页/PDP 透出政策文案（纳入 translation） | refund_flow 场景集（order_lifecycle refund_requested guard 场景） |

### 2026-06-10 细化轮五补充需求（decision.md 决策 25-31）

| 编号 | 能力 | 所属域 | 内容 | 验收来源 |
|------|------|--------|------|---------|
| ALIGN-029 | 支付方式 Stripe 全家桶 | trading | Stripe Payment Element 承载卡/Apple Pay/Google Pay/Klarna/Afterpay（F-037）；PayPal 选项卡置灰 coming soon；webhook/退款/幂等链路复用决策 7，BNPL 异步确认沿用幂等链路；多币种下按订单币种初始化 | checkout_order 场景集（n12 Payment Element） |
| ALIGN-030 | Newsletter 订阅落表 | marketing | NewsletterSubscriber 实体（email 唯一幂等 + source footer/modal/exit_intent + locale）+ /api/store/newsletter；不发码不发邮件，弹窗文案改纯订阅确认（折扣码话术移除，显式功能降级）；限流靠 WAF | newsletter_subscribe 场景集 |
| ALIGN-031 | 消费端 UI 文案 i18n + locale 路由 | 前端基础设施 | next-intl 字典管理 UI 静态文案三语；路径前缀 /es /fr（EN 无前缀）+ hreflang + 按 locale 生成 sitemap；语言切换器升三语；ISR/CDN 缓存与 revalidate/purge 按 locale 路径分别执行 | 决策 27（L4 UI 验收按 locale 抽检） |
| ALIGN-032 | 礼品包装选项 | trading | Order.giftWrap + giftWrapFee（下单快照，后台可配置固定价，锁汇换算）；计入订单总额与 Stripe 收款、退款上限；金额拆分展示加 Gift Wrapping 行；管理端 OrderDetail 透出标记（F-036） | checkout_order 场景集（n6 礼品包装） |
| ALIGN-033 | 首页/推荐位数据规则 | catalog | New Arrivals=上架时间倒序；Best Sellers=近30天已支付销量聚合（冷启动回退 recommend）；Shop by Color=色板标签筛选；You May Also Like=同品类+同价段；Complete the Look=同类目规则凑数（不建关联表）；全部 JetCache 缓存（key 含 locale/currency） | 决策 29（F-006/F-031） |
| ALIGN-034 | 联系表单落表 | marketing | ContactMessage 实体 + /api/store/contact；管理端本期不做查看页；限流靠 WAF（F-058） | contact_submit 场景集 |
| ALIGN-035 | 退货登记字段 | trading | 已发货退款维持审核制（线下确认收货后审核通过），不建 RMA 状态节点；Refund.returnTrackingNo 选填登记退货单号 | refund_flow 场景集（决策 31） |

## 核心验收（摘自 acceptance-baseline.md）

```gherkin
Scenario: 消费端下单全链路（FUNC-001）
  Given 已登录消费者购物车含现货 SKU 与定制款，Stripe 沙箱可用
  When 结算并完成支付
  Then 订单 paid，现货库存乐观锁扣减 1，定制款不扣减，购物车清空，webhook 按 event_id 幂等

Scenario: 秒级缓存失效链（FUNC-006）
  Given 管理端发布商品更新，三层缓存有旧值
  When @CacheInvalidate → MQ → revalidatePath → CDN 边缘更新
  Then 消费端 5 秒内可见新值
```

边界/异常 915 条（null/extreme/concurrent/auth/network/integrity/state/callsite-compat 8 类）见 boundary-scenarios.yml。

## 非功能约束

- 性能：消费端读接口 P95≤100ms（源站内）、并发 1000+ 压测验收
- 安全：双端独立 JWT、RBAC 新权限点、user_id 强隔离、webhook 签名验证
- 部署：海外云 + Cloudflare CDN/WAF（写接口限流在 WAF 层）
