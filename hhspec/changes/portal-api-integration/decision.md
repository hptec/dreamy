# 关键决策：portal-api-integration

## 决策 1：变更范围与分期
- **选择**：本次纳入 商品+分类+属性集、评价审核、订单+购物车+地址+支付+退款、营销内容（Coupon/FlashSale/Banner/Blog/Lookbook/Weddings/Guide）、Dashboard/Analytics、Shipping 物流配置；细化轮三追加：Wishlist（决策 18）、GA4 流量分析（决策 19）、消费端原型迭代 4（Showroom/尺码推荐/PDP 定制表单与婚期交期，决策 20）、种子数据（决策 21）
- **理由**：后端仅有 identity 域，其余前端页面全部 mock；用户选择全量推进，按域分批实施
- **备选**：HomeBuilder/NavigationConfig/Publish（站点 CMS）与 EmailMarketing 推迟到独立 change——CMS 进阶能力与邮件发送依赖独立设计

## 决策 2：限界上下文划分（六域细拆，细化轮三扩为七域）
- **选择**：新增 7 个限界上下文
  | domain_code | 实体 | API 前缀 |
  |---|---|---|
  | `catalog` | Product/Sku/ProductImage/SizeChartRow/Category/AttributeDef/AttributeSet/AttributeSetItem/TagDimension/Tag/CustomTag | /api/store/products·categories, /api/admin/products·categories·attribute-sets |
  | `review` | Review/ReviewImage/ProductQuestion | /api/store/reviews·questions, /api/admin/reviews |
  | `trading` | CartItem/Address/Order/OrderLine/Payment/Refund/WishlistItem/BrowseHistory | /api/store/cart·addresses·checkout·orders·wishlists·browse-history, /api/admin/orders·refunds |
  | `shipping` | Carrier/ShippingRate | /api/admin/shipping, 结算运费计算由 trading 同步调用 |
  | `marketing` | Coupon/FlashSale/Banner/BlogPost/Lookbook/Guide/RealWedding | /api/store/content·promotions, /api/admin/promotions·banners·content |
  | `showroom` | Showroom/ShowroomItem/ShowroomMember/ShowroomVote/ShowroomComment | /api/store/showrooms*（含 guest token 端点，决策 20） |
  | `analytics` | 无自有实体（只读聚合 + GA4 Data API 代理） | /api/admin/dashboard·analytics |
- **理由**：用户选择最细边界；review/shipping 职责单一独立成域，支撑按域分批 apply
- **备选**：四域方案（review 并 catalog、shipping 并 trading，文档开销更小）；单 commerce 大域（31 实体单域 REQ 不可维护）

## 决策 3：跨域集成方式
- **选择**：同步读走领域服务接口（进程内 Java 直调，禁止跨域直查对方表）；状态变更事件走 RabbitMQ；analytics 聚合查询例外——允许只读跨域 SQL 聚合（实时聚合 + JetCache 60s）
- **理由**：单体多 module 下清晰且为未来拆分留余地；analytics 全表扫描走服务接口反而低效
- **备选**：共享库自由 join——性能直接但域边界形同虚设

## 决策 4：三层缓存性能架构（核心）
- **选择**：
  1. **CDN/边缘层**（Cloudflare）：静态资源 + 商品列表/详情/内容页 Next.js ISR 页面 + 匿名公共 API（`Cache-Control: s-maxage`）
  2. **JetCache 两级**（Caffeine+Redis）：消费端所有只读 API 数据层（商品/分类/Banner/内容/运费规则），写操作 @CacheInvalidate
  3. **不缓存**：购物车、下单、支付、库存扣减、订单、个人数据
- **失效链**（秒级新鲜度）：管理端写操作 → @CacheInvalidate → RabbitMQ 事件 → Next.js `revalidatePath` → CDN 边缘更新；TTL 仅兜底
- **理由**：跨境站海外用户到源站 150-300ms 物理 RTT 只有 CDN 能消除；带登录态/权限数据只能后端缓存；两者分层互补而非二选一
- **备选**：仅 Redis 缓存——海外首字节无改善；仅 CDN——个性化数据无法缓存且有越权风险

## 决策 5：性能目标（验收基准）
- **选择**：消费端读接口 P95 ≤ 100ms（源站内）、并发 1000+，压测验收；缓存命中率纳入观测
- **理由**：用户选择对标真实生产

## 决策 6：库存模型（现货+定制双模式）
- **选择**：现货 SKU 含库存数，下单事务内乐观锁扣减（version 字段）防超卖；定制款无库存限制，仅交期（leadTimeDays）与定制尺寸数据
- **理由**：婚纱礼服主流为按单定制，贴合原型迭代 4 的定制尺寸设计
- **备选**：纯现货库存——模型简单但不贴合业务

## 决策 7：支付（Stripe 沙箱直接接入）
- **选择**：Stripe PaymentIntent + webhook（签名验证、event_id 幂等）、退款走 Stripe Refund 沙箱
- **理由**：用户选择一步到位，跨境支付是外部集成重点
- **备选**：模拟支付网关——联调快但后补真实网关成本高

## 决策 8：购物车存储
- **选择**：登录用户 MySQL 持久化（跨设备），匿名用户 localStorage，登录时合并
- **理由**：电商标准做法
- **备选**：纯 Redis（重启丢车）；不做后端购物车（无跨设备）

## 决策 9：图片/媒体存储
- **选择**：S3 兼容对象存储（Cloudflare R2 类）+ CDN 直出，预签名 URL 上传，后端只存对象 key/URL
- **理由**：图片是跨境站性能大头，与部署形态（Cloudflare）一致
- **备选**：服务器本地磁盘——无 CDN 加速、扩容麻烦

## 决策 10：Dashboard/Analytics 聚合
- **选择**：实时 SQL 聚合 + JetCache 60s 缓存，不建预聚合表
- **理由**：千单量级实时聚合足够，数据量大后再演进
- **备选**：定时任务预聚合表——更稳但多一套基建

## 决策 11：部署形态与限流
- **选择**：海外云 + Cloudflare CDN/WAF；写接口（下单/支付/登录）限流放 WAF 层，后端不引入限流依赖；JetCache 开启穿透保护
- **理由**：读靠 CDN+缓存扛量，限流在边缘拦截代码零成本
- **备选**：后端 Resilience4j——不依赖部署环境但多一套依赖

## 决策 12：沿用项（全部确认）
- **错误策略**：统一 `{code,message,details}` 数字码（store 三语 EN/ES/FR、admin 中文），错误码按域扩展号段（catalog 5xxxx、trading 6xxxx、marketing 7xxxx、review/shipping/analytics 依次顺延）
- **认证**：双端独立 JWT + 管理端 RBAC @RequirePermission（新增商品/订单/营销/内容/物流/看板权限点）
- **DB**：从零建表、Long 自增主键、huihao-mysql 实体基类、标准增表无迁移
- **无 breaking change**：全部为新增 API，identity 域不改动

## 决策 13：内容数据多语言（三语 EN/ES/FR，2026-06-10 细化轮补充）
- **选择**：所有含文案实体配 `xxx_translation` 附表（entity_id + locale + 文案字段）；EN 为基准语存主表，ES/FR 存附表，缺翻译回退 EN；管理端编辑表单加 EN/ES/FR 语言 tab；消费端 API 按 `Accept-Language`/locale 参数返回对应语言
- **覆盖范围**：Product（名称/副标题/描述/SEO）、Category、AttributeDef（label/options）、Tag/CustomTag/TagDimension、Banner、BlogPost、Lookbook、Guide、RealWedding、Coupon、FlashSale 共 13 张 translation 表
- **理由**：用户明确要求内容数据三语无遗漏；附表方案查询规范、可索引、可校验翻译完整度
- **备选**：主表 JSON 列（{en,es,fr}）——结构简单但无法索引单语搜索、完整度难校验；内容仅英文——被用户否决
- **连带约束**：消费端只读缓存 key（JetCache/CDN）必须含 locale 维度；ISR 页面按 locale 分别 revalidate

## 决策 14：多币种结算（USD 基准 + 锁汇，2026-06-10 细化轮补充）
- **选择**：商品定价单 USD；后端汇率表（管理端可维护）替换前端硬编码汇率；下单时按当前汇率锁定生成订单币种金额（Order 已有 currency 字段，新增 exchangeRate 字段）；Stripe 按订单币种收款；退款按原币种原金额；币种清单补 EUR → USD/EUR/CAD/AUD/GBP 五币种
- **理由**：单基准价免去运营维护多套价格；锁汇保证订单金额与收款一致、退款无汇差争议
- **备选**：每币种独立定价（心理定价可控但每品 5 套价格维护重）；纯展示换算+USD 结算（展示价与扣款价不一致引发客诉）
- **连带约束**：消费端只读缓存 key 含 currency 维度（或价格换算放客户端、缓存仅存 USD 基准价——L1 定夺）；Stripe webhook 金额校验按订单币种

## 决策 15：税费立场（含税价 + DDU，2026-06-10 细化轮补充）
- **选择**：商品价即终价，结算不出 Tax 行（与原型结算明细一致）；跨境关税/进口税 DDU 由收件人承担，结算页加一行说明文案
- **理由**：与原型 alignment；小型跨境 DTC 常规做法，免税率表基建
- **备选**：按收货地区估算销售税——需税率表且偏离原型结算页结构

## 决策 16：交易性邮件通知（纳入本次，2026-06-10 细化轮补充）
- **选择**：复用 RabbitMQ 订单事件，新增邮件消费者发送订单确认/发货通知/退款结果三类邮件；邮件按用户 locale 三语渲染；SMTP 抽象接口，dev 环境落日志，生产配置化（SES/Resend 等）；发送记录落 MailRecord 表（幂等防重发 + 失败重试）
- **理由**：电商闭环必需；MQ 拓扑已决策，增量小
- **备选**：推迟到 EmailMarketing change——用户下单后无任何邮件反馈，体验不完整
- **边界**：营销邮件（EmailMarketing 页面）仍排除在外，维持决策 1

## 决策 17：商品搜索（MySQL FULLTEXT，2026-06-10 细化轮补充）
- **选择**：MySQL FULLTEXT ngram 索引，覆盖商品名/副标题/标签（EN 主表 + translation 附表按 locale 检索）；结果走 JetCache 短 TTL 缓存；消费端 /search 页从前端内存 filter 改为后端 API
- **理由**：千级 SKU 下性能充足、零新基建
- **备选**：Apache Doris（倒排索引 + Analytics 一体）——用户曾倾向全量 Doris 替换 MySQL，经确认 Doris 缺多表 ACID 事务/行级乐观锁/自增主键，与决策 6/12/BE-DIM-4 冲突，**最终撤回，全量保留 MySQL，性能问题留待后续独立 change 解决**；Meilisearch/ES——千级商品过度设计

## 决策 18：Wishlist 后端持久化（2026-06-10 细化轮三补充）
- **选择**：纳入 trading 域，新增 WishlistItem 实体（user_id + product_id 唯一）+ `/api/store/wishlists` 端点（列表/添加/移除/移入购物车）；登录用户专属（未登录点击收藏引导登录），不做匿名合并
- **理由**：实现端 `/account/wishlist` 页面已存在且 feature-map F-047 标 Must，但原范围 0 覆盖；与 CartItem 同 user_id 隔离模式（BE-DIM-6），增量约 1 实体 + 4 端点
- **备选**：维持 localStorage——零工作量但跨设备丢失，与购物车持久化体验不一致；匿名+登录合并——体验最优但多一套合并逻辑，收藏场景低频不值得

## 决策 19：Analytics 流量数据接 GA4（2026-06-10 细化轮三补充）
- **选择**：消费端挂 gtag 脚本并上报标准电商事件（page_view/view_item/add_to_cart/begin_checkout/purchase）；管理端 Analytics 页流量图表（trafficSources/funnel）由后端经 GA4 Data API 拉取 + JetCache 缓存（5min TTL）渲染进现有图表，保持原型视觉；GMV/订单/退款等交易指标仍从业务表实时聚合（决策 10 不变）
- **理由**：er-diagram 标注流量指标"由埋点聚合派生"但原 17 项决策无任何埋点机制，图表将永久空白；GA4 免费且电商事件体系成熟
- **备选**：本期降级显示暂无数据——零基建但原型对齐缺口永存；Plausible——无 Cookie 合规负担但电商漏斗能力弱于 GA4；自建埋点——数据自主但写入量与表膨胀成本高
- **连带约束**：面向 EU 用户必须新增 Cookie consent banner（Consent Mode v2，拒绝时不发分析 Cookie）；banner 为新增组件，沿用设计 token（同 loading/error 态新增组件条款，不构成强对照违例）；GA4 凭证（service account）走后端配置，不暴露前端

## 决策 20：消费端原型迭代 4 全量纳入（2026-06-10 细化轮三补充）
- **选择**：Showroom 协作（F-066~071）、尺码推荐（F-072~073）、PDP 定制尺寸表单与婚期交期（F-074~077）全部纳入本次，消费端按「复制+适配」从 hhspec/prototype 落地到 portal-store
- **理由**：原型迭代 4 全 Must 功能，原范围既未纳入也未显式排除（悬空）；用户确认全量推进
- **备选**：建独立 change——范围可控但功能悬空状态延续；仅纳入 PDP 定制表单——数据模型已有但协作域继续缺失
- **子决策**：
  1. **域归属**：新增第 7 个限界上下文 `showroom`（实体 Showroom/ShowroomItem/ShowroomMember/ShowroomVote/ShowroomComment；API `/api/store/showrooms*`），与决策 2 最细边界一致
  2. **免注册访客鉴权**：邀请链接含不可猜 UUID token；访客凭 token+昵称换取短期受限 guest JWT（仅可读该 Showroom + 投票/留言），复用现有 JWT 基建；投票按 member 去重；新娘可重置邀请链接作废旧 token；写操作限流沿用 WAF 层（决策 11）
  3. **尺码推荐**：基于商品 SizeChartRow 尺码表区间匹配（身高/胸/腰/臀落入码段 + 松紧偏好偏移），后端纯函数端点可测；置信话术改为区间说明（如"您的三围均落在 US 8 区间"），不虚构"N% 买家"统计，冷启动无依赖
  4. **dye lot 提示**：同一 Showroom 内 24h 窗口存在同款式已付订单 → 购物车/结算/Showroom 视图透出"同染色批次保证"提示条（纯展示规则，不影响履约）
  5. **提醒升级真发邮件**：F-070「发送提醒」从原型前端模拟升级为真发，复用决策 16 的 MQ+MailRecord+SMTP 基建，新增 Showroom 邀请/指派提醒邮件类型（访客邮箱由新娘指派时填写）
  6. **PDP 定制表单/婚期交期**：依赖后台 A-007 SKU 字段（定制尺寸开关/发货周期/加急开关，决策 6 已建模）；结算新增 wedding date 选填字段做交期复核，Showroom 婚期自动带入

## 决策 21：种子数据策略（2026-06-10 细化轮三补充）
- **选择**：将前端 mock 演示数据（portal-store data/*.ts + portal-admin data/mock.js：商品/分类/订单样例/内容/Banner/券）转为种子导入脚本（SQL 或 CommandLineRunner），含三语 translation 表数据，建表后灌入；订单类样例仅 dev/staging 环境灌入，生产只灌配置类+内容类
- **理由**：决策 12 从零建表，六域对接完成后整站无数据，联调与 L4 UI 回归比对（原型强对照）无数据可对
- **备选**：仅最小骨架数据——干净但验收前需人工造数；不导入——16 个管理页+消费端验收全部依赖手工录入，不可行

## 决策 22：portal-store 部署形态改 Node 运行时（2026-06-10 细化轮四补充）
- **选择**：去掉 `next.config.mjs` 的 `output: 'export'`，改 standalone 模式以 Node 服务运行（docker 容器，restart=always + 健康检查），CDN 前置挡量；页面带 `Cache-Control: s-maxage` 由 CDN 边缘缓存，更新走「MQ 消费者 → Next.js revalidatePath + Cloudflare purge API → 边缘更新」秒级失效链 + TTL 兜底；Cloudflare 开启 serve-stale（源站故障时继续吐旧缓存，商城不白屏）
- **理由**：现状 `output: 'export'` 是纯静态导出，**revalidatePath/ISR 不可用，决策 4 的秒级失效链无法落地**；且新增商品需重新构建才可访问、商品页 SEO 依赖构建时数据。Node 运行时下 CDN 命中率几乎不变（90%+），Node 只承担缓存未命中的渲染（实际 QPS 很小，512MB~1GB 内存足够），稳定性靠容器自动拉起 + CDN serve-stale 双兜底
- **备选**：维持静态导出 + 全 CSR——部署最简但商品页 SEO 退化为骨架页（跨境独立站获客不利）；静态导出 + 发布触发 CI 重建——SEO 保留但分钟级新鲜度与决策 4/5 冲突，商品量大后构建变慢
- **连带约束**：apply 阶段需调整 next.config.mjs（删 output:'export'、启用 standalone）、新增前端 docker 部署单元（与 Java/MySQL/Redis/MQ 并列）、MQ 失效消费者增加 Cloudflare purge 调用（zone token 后端配置）

## 决策 23：Recently Viewed 浏览历史后端持久化（2026-06-10 细化轮四补充）
- **选择**：新增 BrowseHistory 实体（customerId + productId + viewedAt，同商品 upsert 更新时间，每用户保留最近 50 条滚动清理）；登录用户浏览商品详情时记录；账户页横滑按 viewedAt 倒序拉取（F-048）；匿名用户不记录（登录前浏览不回溯）
- **理由**：用户选择后端持久化（跨设备一致，与 Wishlist 同体验基准）；F-048 标 Must 但原范围 0 覆盖
- **备选**：localStorage 纯前端——零后端但不跨设备；排除——与 feature-map Must 冲突
- **归属**：trading 域（与 WishlistItem 同为用户-商品关系数据，同 user_id 隔离模式）

## 决策 24：退款政策——定制款投产后不可退（2026-06-10 细化轮四补充）
- **选择**：现货款：未发货可全额退，已发货退货后退款；定制款：**支付后 24h 宽限期内可申请全额退款，超过即视为已投产不可退**（宽限期可配置）；消费端订单详情按行判定，含已投产定制行时退款申请入口置灰 + 说明文案（三语）；后端审核与申请接口均按订单行校验（OrderLine.customSizeData 非空 = 定制行，Order.paidAt + 24h 判定投产）
- **理由**：按单制作的婚纱投产后无法二次销售，行业常规（Azazie 等同类做法）；原型 OrderDetail 时间线无"投产"节点、无生产操作按钮，采用「支付时间 + 宽限期」纯规则判定可零 UI 改动满足强对照约束
- **备选**：不区分纯人工审核——审核无依据、消费端无预期管理，定制款纠纷风险高；退款扣 remake 费——需扣费规则与部分退款计算，本期增量大
- **连带约束**：结算页与 PDP 定制选项处需透出退款政策说明文案（新增文案节点，纳入三语 translation 范围）

## 决策 25：支付方式范围——Stripe 全家桶（2026-06-10 细化轮五补充）
- **选择**：结算页支付方式用 Stripe Payment Element 一套集成承载：卡 / Apple Pay / Google Pay / Klarna / Afterpay（沙箱均支持）；PayPal 选项卡保留但置灰标 coming soon；webhook 签名验证、event_id 幂等、退款链路完全复用决策 7，不区分支付方式
- **理由**：原型结算页（F-037 Must）展示六种支付选项卡，决策 7 仅决策了卡支付造成范围悬空；Payment Element 一次集成多方式增量最小
- **备选**：仅 Stripe 卡——其余选项卡全部置灰，结算页观感与原型差距大；Stripe + PayPal Checkout SDK——覆盖最全但多一套独立 webhook/退款/对账链路
- **连带约束**：Order.paymentMethod 枚举沿用 er-diagram 既有取值（Stripe/PayPal/Apple Pay/Klarna/Afterpay/Google Pay），PayPal 本期不会产生数据；BNPL（Klarna/Afterpay）异步确认场景沿用 webhook 幂等链路；多币种（决策 14）下 Payment Element 按订单币种初始化

## 决策 26：Newsletter 订阅——仅落表收集（2026-06-10 细化轮五补充）
- **选择**：新增 NewsletterSubscriber 实体（email 唯一 + source 来源 footer/modal/exit_intent + locale + 订阅时间）+ `/api/store/newsletter` 订阅端点；**不发折扣码、不发邮件**，首访弹窗/Exit Intent 文案由 "Reveal My Code" 调整为纯订阅确认（属新增文案节点，纳入三语范围）；提交限流靠 WAF（决策 11）
- **理由**：F-008/F-056 标 Must 但原范围零覆盖，订阅邮箱不落地等于白白流失；折扣码与营销邮件能力归属被排除的 EmailMarketing change，本期仅收集
- **备选**：落表+发码邮件——体验完整但需折扣码发放与一次性核销逻辑，本期增量大；本期排除——Must 功能悬空
- **边界**：弹窗文案偏离原型一处（折扣码话术移除），属功能降级的显式决策，不构成强对照违例；EmailMarketing change 落地后可无缝升级发码

## 决策 27：消费端 UI 静态文案 i18n 与 locale 路由（2026-06-10 细化轮五补充）
- **选择**：UI 静态文案（按钮/标签/提示/错误展示）用 next-intl 字典管理三语 EN/ES/FR；locale 路由用路径前缀 `/es`、`/fr`（EN 无前缀）；每语言独立 URL + hreflang 标注；语言切换器从原型 EN/ES 升级为三语；内容数据仍走决策 13 的 translation 附表 API
- **理由**：决策 13 只覆盖内容数据层，界面 chrome 文案与路由策略悬空会导致语言切换后中英混杂；路径前缀与决策 22 Node 运行时 + CDN 按 URL 缓存天然契合（无需按 cookie 分版），SEO 每语言独立收录
- **备选**：Cookie/Header 切换不分路由——CDN 缓存按 cookie 分版命中率劣化，与决策 4/22 冲突；UI 仅英文——三语体验不完整
- **连带约束**：ISR/CDN 缓存 key 含 locale 路径维度（决策 13 连带约束自然满足）；revalidate/purge 失效链按三个 locale 路径分别执行；sitemap 按 locale 生成并带 hreflang

## 决策 28：礼品包装——订单级开关 + 固定费（2026-06-10 细化轮五补充）
- **选择**：Order 新增 giftWrap（bool）+ giftWrapFee（下单时快照金额）字段；费用为后台可配置固定价（配置项形式 L1 定夺，默认 USD 基准价按决策 14 锁汇换算），计入订单总额与 Stripe 收款；管理端 OrderDetail 透出礼品包装标记供发货执行
- **理由**：F-036（Must）结算物流步骤含 gift wrapping 选项但零建模；订单级开关 + 固定费增量最小（2 字段 + 1 配置项）
- **备选**：免费勾选仅标记——婚纱礼盒包装成本不低，原型展示费用则有出入；本期排除——结算页与原型可见差异
- **连带约束**：退款金额上限含礼品包装费；订单金额拆分展示（结算复核/订单详情/管理端）增加 Gift Wrapping 行

## 决策 29：首页/推荐位数据规则——规则化 + 搭配降级（2026-06-10 细化轮五补充）
- **选择**：
  | 区块 | 数据规则 |
  |---|---|
  | New Arrivals | 上架时间倒序 |
  | Best Sellers | 近 30 天已支付订单销量聚合（JetCache 缓存；冷启动无销量时回退 recommend 手动标记） |
  | Shop by Color | 色板标签（Tag 颜色维度）筛选 |
  | You May Also Like | 同品类 + 同价格段规则推荐 |
  | Complete the Look | 同类目规则凑数（婚纱页推配饰/面纱等关联品类），**不建搭配关联表** |
- **理由**：F-006/F-031 标 Must 但区块数据来源无规则，实现时必然分歧；纯规则方案零新表、可缓存、可测试
- **备选**：全手动标记——Best Sellers 名不副实且运营维护重；手动维护搭配关联表——搭配质量最优但多一张自关联表与 ProductEdit 选择器，本期降级
- **连带约束**：以上全部为只读规则查询，纳入决策 4 JetCache 缓存层（key 含 locale/currency 维度）

## 决策 30：联系表单落表收集（2026-06-10 细化轮五补充）
- **选择**：新增 ContactMessage 实体（姓名/邮箱/主题/内容/提交时间）+ `/api/store/contact` 提交端点；管理端本期不做查看页（运营直查库，后续 change 可补）；提交限流靠 WAF（决策 11）
- **理由**：F-058（Should）原型表单 preventDefault 纯模拟，用户消息丢失；落表增量极小（1 实体 + 1 端点）
- **备选**：保持前端模拟——零成本但消息白白流失

## 决策 31：退货寄回——审核制 + 登记字段（2026-06-10 细化轮五补充）
- **选择**：已发货订单退款维持现有审核制流程：管理员线下确认收到退货后才点「审核通过」触发 Stripe 退款，**系统不建 RMA 寄回/收货状态节点**；Refund 工单新增 returnTrackingNo 选填字段供登记退货物流单号
- **理由**：原型 Refunds 页无任何 RMA UI（寄回地址/收货确认操作），建完整 RMA 流程需两端 UI 改动、违背强对照约束；审核制 + 登记字段零流程改动
- **备选**：完整 RMA 流程（approved→待寄回→已收货→打款）——流程严谨但消费端需寄回单号录入 UI、管理端需收货确认操作，均偏离原型
- **连带约束**：refund_flow 与 refund_lifecycle 不变；returnTrackingNo 仅为 Refund 实体字段级增量

## 后端关键决策


> 来源：Phase 2.3.1 后端实现深度探索
> 下游消费：L0 flow_modeler（已映射到 business-flow.yml 节点）、L1 architect（映射到 error-strategy.md / data-flow.md）

### BE-DIM-4 状态机/并发/事务
- **决策**：待支付订单 30 分钟超时自动取消并回补现货库存（Spring @Scheduled）
- **触发信号**：backend_inference[定时任务]
- **理由**：电商常规做法，避免现货库存被长期锁占
- **约束**：L1 架构须提供定时扫描任务与取消-回补的原子事务边界

- **决策**：下单原子事务：订单创建+现货库存乐观锁扣减+购物车清空；退款审核原子：退款记录+订单状态+库存回补
- **触发信号**：backend_inference[事务]
- **理由**：防超卖、防状态不一致
- **约束**：L1 架构须在领域服务层声明事务边界，SKU 表含 version 乐观锁字段

- **决策**：Stripe webhook 按 event_id 幂等消费；下单接口订单号预生成防重提交
- **触发信号**：backend_inference[幂等]
- **理由**：Stripe 至少一次投递语义
- **约束**：L1 契约须包含幂等键存储（processed_event 表或 Redis setnx）

- **决策**：引入 RabbitMQ：订单事件（支付成功/发货/取消）→ 缓存失效、ISR revalidate、通知消费者
- **触发信号**：backend_inference[消息队列]
- **理由**：用户选择 MQ 解耦订单事件；Spring AMQP 生态成熟，死信/重试开箱即用
- **约束**：L1 架构须定义 exchange/queue 拓扑、死信队列与消费幂等规范

### BE-DIM-5 外部集成与第三方依赖
- **决策**：Stripe 沙箱（PaymentIntent+webhook 签名验证）；S3 兼容对象存储（预签名 URL 上传）；物流单号手填无真实物流 API
- **触发信号**：backend_inference[外部集成]
- **理由**：跨境支付与图片 CDN 是核心集成点
- **约束**：L1 契约须定义集成端口防腐层与超时/失败降级路径

### BE-DIM-6 安全与权限
- **决策**：管理端沿用 RBAC @RequirePermission 并新增商品/订单/营销/内容/物流权限点；消费端订单/地址/购物车/评价按 user_id 强隔离
- **触发信号**：backend_inference[权限]
- **理由**：防水平越权
- **约束**：L1 OpenAPI 契约须在所有消费端个人数据端点注入 user_id 过滤，管理端端点标注权限点

### BE-DIM-7 可观测性与运维
- **决策**：管理端改价/上下架/发货/退款审核/内容发布等写操作沿用现有 OperationLog 机制
- **触发信号**：backend_inference[审计日志]
- **理由**：复用 identity 域已验证的审计基建
- **约束**：L1 须为新域写操作枚举审计动作类型

### BE-DIM-8 性能与可扩展性
- **决策**：写接口限流放 Cloudflare WAF 层，后端不引入限流依赖；JetCache 开启缓存穿透保护
- **触发信号**：backend_inference[限流降级]
- **理由**：读靠 CDN+缓存扛量，代码零成本
- **约束**：L1 须输出各只读接口的缓存 key 设计与穿透保护配置

- **决策**：三层缓存（CDN/ISR + JetCache + 不缓存交易数据）；秒级主动失效链 @CacheInvalidate→MQ→revalidatePath→CDN；P95≤100ms、1000 并发压测验收
- **触发信号**：keyword
- **理由**：跨境站物理 RTT 只能靠 CDN 消除，鉴权数据只能靠后端缓存
- **约束**：L1 须为每个只读端点标注缓存层级（CDN/JetCache/none）与失效触发者

## 原型强对照约束

> 触发：linked_prototype_snapshots 非空且原型含自定义样式 token（tailwind.config.js，editorial-luxe-coastal 风格，与消费端同源设计 token）。

1. **样式 token 不可偏离**：L3 改造 portal-admin 页面接入真实数据时，不得修改/内联覆盖 tailwind.config.js 中的设计 token（colors/字体/间距），不得引入与 token 体系冲突的硬编码色值
2. **布局结构保持**：仅替换数据源（mock → API），不得改动既有组件结构、栅格布局与交互形态；新增的 loading/error/empty 态必须复用既有 EmptyState/骨架组件风格
3. **消费端同理**：portal-store 替换 data/products.ts 等 mock 时不得改动页面视觉，新增状态组件遵循同源 token
4. **L2/L3/L4 约束**：L2 前端详设以现有实现文件为基线产出 diff 级设计；L3 实现遵循「复制+适配」模式仅动数据层；L4 验收含 UI 回归比对（关键页面截图对照）

## 裁决记录（L3 修复轮）

- **裁决（L3 修复轮）**：acceptance.yml s-760 「取消订单不回补 usedCount」与 L2 设计 RM-TRD-113 回补语义冲突，实现随 L2（回补，GREATEST 防负），理由：用户公平性与库存型资源一致语义；L0 断言以本裁决为准（2026-06-10，functional-review warning 项裁决归一；按 AC-06 不修改 acceptance.yml）
