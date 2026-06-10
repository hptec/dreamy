# 验收基准：portal-api-integration

生成时间：2026-06-10T15:30:00Z（细化轮五更新：决策 25-31 增量，场景 id 按重新生成结果校正）

## FUNC — 功能验收

（alignment 类型：来源 acceptance.yml 的 business-flow FUNC 场景）

| 编号 | 场景标题 | 核心断言 | 来源 |
|------|---------|---------|------|
| FUNC-001 | 消费端登录用户购物下单全链路（Payment Element 多方式 + 礼品包装费 + 锁汇） | http_status=200; state=paid | acceptance.yml #s-752 |
| FUNC-002 | 待支付订单 30 分钟超时自动取消 | state=cancelled | acceptance.yml #s-753 |
| FUNC-003 | 管理端发货到完成 | http_status=200; state=completed | acceptance.yml #s-754 |
| FUNC-004 | 退款申请审核与 Stripe 退款（定制款 24h 投产宽限期校验 + returnTrackingNo 登记） | http_status=200; state=refunded; 已投产定制行 422 拒绝 | acceptance.yml #s-755 |
| FUNC-005 | 评价提交与审核（仅 completed 订单可评，通过/驳回）+ 我的评价查看（F-049） | http_status=200; state=approved；驳回需填理由；我的评价按 user_id 过滤 | acceptance.yml #s-756 |
| FUNC-006 | 评价审核驳回与状态流转 | review_moderation 状态机 reject 转换 | acceptance.yml review_moderation state 场景集 |
| FUNC-007 | 商品/内容发布秒级缓存失效链（含三 locale revalidate + CDN purge） | http_status=200; state=published | acceptance.yml #s-757 |
| FUNC-008 | 订单事件 MQ 分发 | state=dispatched; 死信与消费幂等 | acceptance.yml #s-758 |
| FUNC-009 | 看板实时聚合读取 | http_status=200; JetCache 60s | acceptance.yml #s-759 |
| FUNC-010 | 优惠券领取与结算核销 | http_status=200; 422 三语错误 | acceptance.yml #s-760 |
| FUNC-011 | 闪购到期自动下线 | state=ended | acceptance.yml #s-761 |
| FUNC-012 | 内容三语：locale 读取与缺翻译回退 EN | locale 文案正确; 回退无 500 | acceptance.yml #s-763/s-764 |
| FUNC-013 | 内容三语：管理端保存翻译秒级可见 | ≤5s 缓存失效可见 | acceptance.yml #s-765 |
| FUNC-014 | 多币种：EUR 锁汇下单 + Stripe 原币种收款 | currency=EUR; 汇率锁定 | acceptance.yml #s-766/s-767 |
| FUNC-015 | 多币种：退款按原币种原金额 | 原币种原金额 | acceptance.yml #s-768 |
| FUNC-016 | 交易邮件：确认/发货/退款三类 + 幂等 + locale 渲染 | state=sent; 不重发 | acceptance.yml #s-1038 |
| FUNC-017 | 商品搜索：FULLTEXT 命中 + ES locale 检索 | http_status=200 | acceptance.yml #s-773/s-774 |
| FUNC-018 | Wishlist：加收藏幂等/移入购物车/user_id 隔离 | http_status=200; 未登录引导登录 | acceptance.yml #s-1039 |
| FUNC-019 | Showroom：邀请 token→guest JWT→投票留言→指派→提醒邮件→下单 | 旧 token 重置即失效 401; 投票去重; dye lot 提示 | acceptance.yml #s-1040 |
| FUNC-020 | 尺码推荐：区间匹配 + 松紧偏移 + 一键回填 | http_status=200; 纯函数同输入同输出 | acceptance.yml #s-1041 |
| FUNC-021 | GA4 流量拉取：JetCache 5min + 失败降级 | http_status=200; 凭证不出前端; 降级不影响交易指标 | acceptance.yml #s-1042 |
| FUNC-022 | Recently Viewed：upsert 去重 + 50 条滚动清理 + user_id 隔离 | http_status=200; 匿名不记录 | acceptance.yml #s-1058 |
| FUNC-023 | Newsletter 订阅落表：email 幂等 + 来源/locale 记录 + 纯订阅确认文案 | http_status=200; 非法邮箱 422 三语 | acceptance.yml #s-1088 |
| FUNC-024 | 联系表单落表：校验 + 提交成功 | http_status=200; 校验失败 422 逐字段三语 | acceptance.yml #s-1089 |

## EDGE — 边界/异常验收

来源 boundary-scenarios.yml，共 944 条（2026-06-10 细化轮五重新生成：+NewsletterSubscriber/ContactMessage/giftWrap/returnTrackingNo 等增量），8 类全覆盖：

| 类别 | 条数 |
|------|------|
| null（空值/缺失字段） | 389 |
| extreme（极值/超长输入） | 162 |
| state（非法状态转换） | 109 |
| callsite-compat（调用方兼容） | 103 |
| integrity（数据完整性） | 57 |
| concurrent（并发冲突） | 53 |
| auth（越权/未授权） | 50 |
| network（网络/超时） | 21 |

完整清单见 boundary-scenarios.yml；验收场景全集（1089 条）见 acceptance.yml。

## UI — UI 验收检查点

### 页面清单（范围内 16 页，UI 已实现，本次验收数据对接正确性）

| page_id | 原型文件 | 实现文件 |
|---------|---------|---------|
| Dashboard | prototype/portal-admin/src/views/Dashboard.vue | frontend/portal-admin/src/views/Dashboard.vue |
| Analytics | prototype/portal-admin/src/views/Analytics.vue | frontend/portal-admin/src/views/Analytics.vue |
| Products | prototype/portal-admin/src/views/Products.vue | frontend/portal-admin/src/views/Products.vue |
| ProductEdit | prototype/portal-admin/src/views/ProductEdit.vue | frontend/portal-admin/src/views/ProductEdit.vue |
| Categories | prototype/portal-admin/src/views/Categories.vue | frontend/portal-admin/src/views/Categories.vue |
| AttributeSets | prototype/portal-admin/src/views/AttributeSets.vue | frontend/portal-admin/src/views/AttributeSets.vue |
| Orders | prototype/portal-admin/src/views/Orders.vue | frontend/portal-admin/src/views/Orders.vue |
| OrderDetail | prototype/portal-admin/src/views/OrderDetail.vue | frontend/portal-admin/src/views/OrderDetail.vue |
| Refunds | prototype/portal-admin/src/views/Refunds.vue | frontend/portal-admin/src/views/Refunds.vue |
| Reviews | prototype/portal-admin/src/views/Reviews.vue | frontend/portal-admin/src/views/Reviews.vue |
| Shipping | prototype/portal-admin/src/views/Shipping.vue | frontend/portal-admin/src/views/Shipping.vue |
| Promotions | prototype/portal-admin/src/views/Promotions.vue | frontend/portal-admin/src/views/Promotions.vue |
| Banners | prototype/portal-admin/src/views/Banners.vue | frontend/portal-admin/src/views/Banners.vue |
| ContentBlog | prototype/portal-admin/src/views/ContentBlog.vue | frontend/portal-admin/src/views/ContentBlog.vue |
| ContentWeddings | prototype/portal-admin/src/views/ContentWeddings.vue | frontend/portal-admin/src/views/ContentWeddings.vue |
| ContentLookbook | prototype/portal-admin/src/views/ContentLookbook.vue | frontend/portal-admin/src/views/ContentLookbook.vue |

### 消费端新建页面（迭代 4 落地，决策 20，「复制+适配」自 hhspec/prototype）

| page_id | 原型文件 | 实现文件 |
|---------|---------|---------|
| ShowroomList | hhspec/prototype/app/showroom/page.tsx | frontend/portal-store/app/showroom/page.tsx（新建） |
| ShowroomDetail | hhspec/prototype/app/showroom/[id]/page.tsx | frontend/portal-store/app/showroom/[id]/page.tsx（新建） |
| AddToShowroomModal | hhspec/prototype/components/showroom/add-to-showroom-modal.tsx | frontend/portal-store/components/showroom/（新建） |
| FindMySizeModal | hhspec/prototype/components/product/find-my-size-modal.tsx | frontend/portal-store/components/product/（新建） |
| AccountWishlist | hhspec/prototype/app/account/wishlist/page.tsx | frontend/portal-store/app/account/wishlist/page.tsx（已有，改对接 API） |
| ProductDetail（增强） | hhspec/prototype/app/product/[slug]/page.tsx | frontend/portal-store/app/product/[slug]/page.tsx（增强：Add to Showroom/Find My Size/Custom Size/交期判定条） |
| Checkout（增强） | hhspec/prototype/app/checkout/page.tsx | frontend/portal-store/app/checkout/page.tsx（增强：wedding date + 交期复核 + dye lot 提示） |

消费端（portal-store, Next.js）对应页面：商品列表/详情/搜索、购物车、结算、订单列表/详情、地址簿、收藏（Wishlist）、评价提交、内容页（Blog/Lookbook/Weddings）——以真实 API 替换 mock/本地数据；另新增 Cookie consent banner（GA4 Consent Mode v2，沿用设计 token）。

### 核心约束
→ 见 decision.md 决策 12 与「原型强对照约束」章节（若有）

### 详细字段/交互规格
> 详细页面交互规格将在 L2 前端详设阶段产出（由 ui_test_designer 生成 ui-test-spec.yml），届时请更新此引用。

## PERF — 性能基线

| 指标 | 目标 | 验收方式 |
|------|------|---------|
| 消费端读接口 P95 | ≤ 100ms（源站内） | 压测报告 |
| 并发 | 1000+ | 压测报告 |
| 缓存新鲜度 | 管理端写后消费端 ≤5s 可见 | 失效链端到端测试 |
| Dashboard 聚合 | JetCache 60s 内命中 | 重复请求观测 |

## SEC — 安全要求

- 双端独立 JWT 不可互用；管理端新增权限点全部 @RequirePermission 拦截
- 消费端订单/地址/购物车/收藏/评价按 user_id 强隔离（水平越权返回 403）
- Showroom guest JWT 受限作用域（仅本 Showroom 读+投票/留言）；邀请 token 不可猜且可重置作废；guest 不可访问任何个人数据端点
- GA4 service account 凭证仅后端配置，不出现在前端响应；Cookie consent 拒绝时不发分析 Cookie
- Stripe webhook 签名验证 + event_id 幂等 + 金额按订单币种校验；下单防重提交
- 预签名 URL 上传限制文件类型与大小
- 商品搜索参数化查询防注入（bs-673），关键词长度限制（bs-672）；Showroom 留言长度限制（≤500）与内容转义防 XSS
