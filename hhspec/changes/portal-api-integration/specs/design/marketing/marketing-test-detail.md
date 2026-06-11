# marketing 测试设计（L2）

> 角色: l2_test_designer ｜ change: portal-api-integration ｜ domain: marketing
> 多层骨架：单元(UT) / 集成(IT，DB+事务+缓存+MQ+定时) / 契约(CT) / API 端到端(AT) / 前端组件(FCT) / 韧性(RST) / 网络边界(NBT)，统一编号 **TC-MKT-NNN**，AAA 伪代码骨架 + 数据工厂 + P0~P3。
> 覆盖来源：①field-constraint-test-matrix.yml —— **本域无行**（矩阵仅含 FLD-CUSTOMERS-001/010，归 identity/trading 域；本域字段约束以 boundary-scenarios er 行为权威源，显式声明无遗漏）；②boundary-scenarios.yml 本域场景（bs 编号逐条映射，见 §10）；③state-machine.yml 本域 7 状态机（banner/blog_post/coupon/flash_sale/lookbook/guide/real_wedding）；④marketing-api-detail E-MKT-01~46 与 10 个域错误码。

## 1. 单元测试（领域纯逻辑）

| TC | 内容（AAA） | 溯源 | P |
|---|---|---|---|
| TC-MKT-001 | 券校验顺序：不存在/draft/未开始 → 422701；过期 → 422701；耗尽 → 422703；门槛 → 422702；顺序固定（耗尽优先于门槛） | E-MKT-10 STEP-MKT-02 | P0 |
| TC-MKT-002 | 减免计算：discount '15% OFF' × subtotal=200 → 30.00；fixed '$50 OFF' × subtotal=30 → 30（min 截断）；free_shipping → 0 + free_shipping=true | DEC-MKT-4 / E-MKT-10 STEP-MKT-03 | P0 |
| TC-MKT-003 | value pattern 校验按 type：discount 拒绝 '$50 OFF'、fixed 拒绝 '15% OFF'、free_shipping 任意 ≤32 通过；不可解析存量值兜底 422701 + 告警 | V-MKT-022 / CV-MKT-009 | P0 |
| TC-MKT-004 | total_limit 不限语义：缺省 100000；used_count=9999 & limit=100000 → 可用；used=limit → 422703 | DEC-MKT-5 | P1 |
| TC-MKT-005 | coupon status-时间窗一致性：scheduled 要求 start_at>now（bs-786）；active 要求窗口内；expiring/expired 创建禁入 → 422704 | V-MKT-026 | P0 |
| TC-MKT-006 | 翻译回退合并：es 命中附表逐字段覆盖、缺翻译字段回退 EN、fr 部分缺失逐字段回退（banner/blog/wedding/lookbook/guide/coupon/flash 七类同函数） | 决策 13 / MAP-MKT-001~011 | P0 |
| TC-MKT-007 | blog excerpt 派生：EN content strip 标记截断 200；es 取 translation.excerpt；translation.excerpt 空回退 EN 派生 | MAP-MKT-003 | P1 |
| TC-MKT-008 | banner 窗口过滤谓词：start/end 空端开放；now<start 不出；now>end 不出；窗口内 published 出；archived/draft 不出 | E-MKT-01 STEP-MKT-02 / DEC-MKT-2 | P0 |
| TC-MKT-009 | banner 迁移 guard 纯函数：合法集 {draft→published, published→archived, archived→published}；published→draft / draft→archived 拒绝（409703 异常类型） | E-MKT-25 STEP-MKT-03；bs-733~738 | P0 |
| TC-MKT-010 | blog 迁移 guard：draft→published 记 published_at 且 slug 必填；archived→published 不刷新 published_at；draft→archived 拒绝 | E-MKT-31 STEP-MKT-03 / CV-MKT-012；bs-739~743 | P0 |
| TC-MKT-011 | coupon/flash 时间窗翻转纯函数：SCHED 输入 now 边界（=start_at、=end_at、end-72h）翻转判定正确 | RM-MKT-109/126 / DEC-MKT-3 | P0 |
| TC-MKT-012 | code/email 归一化：code trim+大写后判重与校验；email trim+小写幂等判重 | CV-MKT-008 | P1 |
| TC-MKT-013 | ProductRef 装配剔除：catalogQueryPort 返回缺失 id（下架/删除）→ 静默剔除不报错 | MAP-MKT-012 / CV-MKT-006 | P1 |

## 2. 集成测试（DB + 事务 + 缓存 + MQ + 定时）

| TC | 内容 | 溯源 | P |
|---|---|---|---|
| TC-MKT-014 | TX-MKT-001 创建券原子性：coupon+translation+operation_log 任一失败整体回滚；uk_coupon_code 冲突 → 409701 无脏行 | E-MKT-14 | P0 |
| TC-MKT-015 | TX-MKT-002 编辑券：used_count 提交被忽略（SET 不含）；translation 整单覆盖后数据一致 | V-MKT-029 / RM-MKT-105 | P0 |
| TC-MKT-016 | 核销 CAS：并发 N 线程 redeem 同券（total_limit=5）→ 恰好 5 次成功，第 6 起 affected=0 → 422703；rollbackRedeem 后可再核销且不为负 | SVC-MKT-01 / RM-MKT-107/108 / EC-MKT-001；bs-577~581 | P0 |
| TC-MKT-017 | redeem 参与外部事务：模拟 trading 事务内 redeem 后下单失败回滚 → used_count 不变（不自启事务验证） | SVC-MKT-01 / FLOW-P06 | P0 |
| TC-MKT-018 | TX-MKT-004 闪购整单：flash_sale+product+translation 批插原子；product_ids 引用不存在 → 422704 无脏行 | V-MKT-035；bs-699 | P0 |
| TC-MKT-019 | 缓存命中/失效链（JetCache）：E-MKT-03 首读落库写缓存→二读命中；编辑文章后 `marketing:blog:{slug}:*` 全 locale 失效→读到新值；改 slug 旧 key 一并失效 | CACHE-MKT-003 / TX-MKT-012 | P0 |
| TC-MKT-020 | 穿透保护：不存在 slug/id 首读写 null 缓存（60s）→ 二读不触 DB 仍 404701（blog/wedding/lookbook 三详情） | BE-DIM-8 | P1 |
| TC-MKT-021 | Banner 写失效扇面：toggle/编辑/删除后 `marketing:banners:*` 全 position×locale 失效；MQ content.invalidated 载荷 type=banner_changed + locales×3 | TX-MKT-007~010 | P0 |
| TC-MKT-022 | SCHED-MKT-01 闪购自动下线：active 活动 end_at 过期 → 下一 tick status=ended + `marketing:flash:*` 失效 + MQ flash_sale_changed（s-761，TASK-060） | RM-MKT-126 / FLOW-P15 | P0 |
| TC-MKT-023 | SCHED-MKT-01 券翻转链：scheduled→active（到 start）→expiring（end-72h 内）→expired（过 end）三段推进；**不发 MQ** | RM-MKT-109 / DEC-MKT-3 | P0 |
| TC-MKT-024 | SCHED-MKT-01 banner 窗口穿越：start_time 进入窗口 → 失效+MQ 但 **status 不变**；end_time 移出同理（DEC-MKT-2） | RM-MKT-008 | P0 |
| TC-MKT-025 | SCHED 分布式锁：双实例并发 tick → 仅一方执行翻转（锁拿不到跳过）；翻转幂等（重复执行无二次副作用） | TX-MKT-029；bs-582~584/798~804 同型 | P1 |
| TC-MKT-026 | q.invalidate 消费者：blog_changed 事件 → revalidate 调用含 /blog、/blog/{slug} ×3 locale + purge 同列表；event_id 重复投递 → 幂等空操作 | EVT-MKT-002；TASK-056 消费者侧 | P0 |
| TC-MKT-027 | q.invalidate 重试/死信：revalidate 端点 5xx → nack ×3 指数退避 → dreamy.dlq 落地（告警钩子触发） | EVT-MKT-002 队列参数 | P1 |
| TC-MKT-028 | Newsletter 幂等：同 email（大小写/空白变体）重复订阅 → 单行落库、响应均 200 {subscribed:true}、响应体逐字节一致（不泄露存在性） | E-MKT-11 / RM-MKT-140；bs-379~383/943/944 | P0 |
| TC-MKT-029 | Contact 落表：必填齐全 201 + 行落库 submitted_at=now；同人重复提交均落表（无判重） | E-MKT-12 / TX-MKT-028 | P1 |
| TC-MKT-030 | views 近似计数：详情源站命中 N 次 → Redis 计数 N → SCHED-MKT-02 flush 后 DB views+=N 且 Redis 清零；DB 失败回投补偿 | DEC-MKT-6 / TX-MKT-030 | P1 |
| TC-MKT-031 | 内容删除级联：删案例/Lookbook/闪购 → nm 关联行与 translation 清空；删文章/Banner/指南 → translation 清空 | TX-MKT-006/009/013/017/021/025 | P1 |
| TC-MKT-032 | 操作审计：本域 25 个 action 各写一次 operation_log（Banner Toggle 归入 编辑Banner，changes 含 status before/after） | BE-DIM-7 / api-detail §0 | P1 |
| TC-MKT-033 | 跨域读装配：wedding/lookbook/flash 详情 products 经 catalogQueryPort 单次批调（SQL 计数断言防 N+1）；商品下架后再读 → 剔除该卡片不 5xx | NP-MKT-002 / CV-MKT-006 | P1 |

## 3. 状态机测试（state-machine.yml 本域 7 机全迁移；TASK-032/033/042~046）

| TC | 状态机 | 内容 | 溯源 | P |
|---|---|---|---|---|
| TC-MKT-034 | banner_lifecycle | draft→published（toggle）；published→archived（take_offline）；archived→published（republish）；三态→deleted（删除成功）；published→draft / draft→archived 拒绝 409703；guard publish 必填（name/image_url 由 422704 前移承载，bs-733） | TASK-032；bs-733~738 | P0 |
| TC-MKT-035 | banner schedule_expire | published 且 now>end_time → 消费端列表不出（窗口过滤等效下线）+ SCHED 失效链触发 + DB status 保持 published（DEC-MKT-2 收敛实现，迁移语义验收落读路径） | TASK-032 / FLOW-P15 | P0 |
| TC-MKT-036 | blog_post_lifecycle | draft→published（记 published_at）；published→archived；archived→republish→published（published_at 不变）；三态→deleted；非法迁移 409703；publish 缺 slug 拒绝 | TASK-033；bs-739~743 | P0 |
| TC-MKT-037 | coupon_lifecycle | draft→scheduled（start>now）/draft→active（窗口内）创建落库；SCHED auto_start/near_expiry/auto_expire 三链推进；draft/expired→deleted 成功；scheduled/active/expiring 删除拒绝 409703；guard 不满足拒绝（bs-786~797 逐条） | TASK-042；bs-786~797 | P0 |
| TC-MKT-038 | flash_sale_lifecycle | draft→scheduled（start>now，bs-798）；SCHED scheduled→active→ended；ended 编辑拒绝 409703；非 draft 删除拒绝；ended 终态后任意写 409703 | TASK-043 / TASK-060；bs-798~804 | P0 |
| TC-MKT-039 | lookbook_publish / guide_publish / real_wedding_publish | draft→published / published→draft 双向（PATCH 与 PUT 两入口）；同态幂等短路（仅一次副作用，bs-885~889）；发布后消费端可见、下线后 404701/列表移除 | TASK-044/045/046；bs-805~810/885~889 | P0 |
| TC-MKT-040 | 并发状态变更 | banner 并发 publish+delete 仅一方成功（bs-552~554）；blog 并发 publish+archive（bs-555~557）；coupon 并发 schedule+activate/双 delete（bs-577~581）；flash 并发双 schedule 仅一次副作用（bs-582~584）；lookbook/guide/wedding 并发双 publish 幂等（bs-585~590） | sm concurrent 族 | P0 |

## 4. 契约测试（CT）

| TC | 内容 | P |
|---|---|---|
| TC-MKT-041 | 46 端点请求/响应 schema 对齐 marketing-api.openapi.yml（含 R 包络映射：契约建模 data 载荷、线上 {code,message,data}） | P0 |
| TC-MKT-042 | 分页载荷 = huihao.page.Paginated 六字段 snake_case（blogs/weddings store 列表 + coupons/blogs/weddings admin 列表共 5 处） | P0 |
| TC-MKT-043 | 10 个域错误码逐一断言 code↔HTTP（404701~404703/409701~409703/422701~422704）+ details 结构（422704 fields 字典；409703 reason）；4227xx 在 validate 端点以 200+reason_code 出现、在下单核销以 422 异常出现（双形态断言） | P0 |
| TC-MKT-044 | 枚举前向兼容：banner.position（bs-851/852）、blog.status（bs-853）、coupon.type/status（bs-881/882）、flash.status（bs-883/884）、lookbook/guide/wedding.status（bs-885~889）、newsletter.source（bs-943/944）新增枚举值时既有反序列化不崩（@JsonEnumDefaultValue 兜底） | P2 |
| TC-MKT-045 | DEC-MKT-1 增量字段兼容：Upsert 不带 EN 文案可选字段（title/subtitle/cta_text/description/story/body）→ 正常落 null；带未知字段 → 忽略不 400 | P1 |

## 5. API 端到端（AT，按端点族 + 错误路径穷举）

| TC | 内容 | 溯源 | P |
|---|---|---|---|
| TC-MKT-046 | store 内容读链路：banners(position 三值+缺省)→blogs(category+分页)→blog/{slug}→weddings(分页)→wedding/{id}→lookbooks→lookbook/{id}→guides 全通；en/es/fr 三语各断言翻译/回退 | E-MKT-01~08 | P0 |
| TC-MKT-047 | store 读边界：page_size=100 通过、101 拒绝；page=0 拒绝；locale 非法 422704；position 非法 422704；空结果空集不报错 | V-MKT-001/002/004 | P1 |
| TC-MKT-048 | 内容详情 404 口径：不存在 slug/id、draft 内容、slug pattern 非法、id 非数字 → 一律 404701（防探测同口径） | V-MKT-005/006 | P0 |
| TC-MKT-049 | flash-sales：active 活动返回含 products+end_at；draft/scheduled/ended 不出现；无活动空 items | E-MKT-09 | P0 |
| TC-MKT-050 | 券校验 E2E：valid 三类型各一（金额断言 TC-MKT-002 口径）；422701/422702/422703 三 reason 均 200+valid=false；无 token 401(40100)；code 格式错 422704；不存在券不回显 coupon 对象 | E-MKT-10 | P0 |
| TC-MKT-051 | newsletter：合法三 source×三 locale 通过；email 256 超长/格式错（bs-543/544）、source 非法（bs-545）、locale 非法（bs-546）→ 422704；重复订阅 200（TC-MKT-028 复用断言）；匿名可调且无鉴权要求（bs-649~651 公开端点语义收口：断言无 401 + WAF 层限流声明，不存在 viewer 越权面） | E-MKT-11；bs-379~383/543~546/649~651 | P0 |
| TC-MKT-052 | contact：合法 201；name101/email256/subject201/message5001 超长（bs-547~551）、必填缺失（bs-385/386/388）逐一 422704；subject 缺省通过（bs-387）；匿名可调（bs-652~654 同 TC-MKT-051 收口口径） | E-MKT-12；bs-384~389/547~551/652~654 | P0 |
| TC-MKT-053 | admin 券 CRUD 全链路：create(201)→list(筛选 status/search)→update(200)→delete(204)；draft 删除成功、active 删除 409703、used_count>0 删除 409703、active 改 code 409703、code 重复 409701 | E-MKT-13~16 | P0 |
| TC-MKT-054 | 券必填/极值族穷举：code/name/type/value 缺失（bs-198~201）、可选 null 通过（bs-197/202~207）、code33/name65/value33 超长（bs-480/481/483）、type 非法（bs-482）、min_amount/total_limit 负值（bs-484/485）、start/end 格式与 end≤start（bs-486~488）→ 全部 422704 | bs-197~207/480~488 逐条 | P0 |
| TC-MKT-055 | admin 闪购 CRUD：create→list(status 筛选)→update→delete；ended 编辑 409703、非 draft 删除 409703、id 不存在 404703；必填缺失（bs-208~210）/可选 null（bs-211~213）/name65/discount33/end≤start（bs-489~491）→ 422704；product_ids 含不存在 → 422704（bs-699） | E-MKT-17~20；bs-208~213/489~491/699 | P0 |
| TC-MKT-056 | admin Banner 全链路：create→list(position 筛选)→update→toggle(三合法迁移+三非法 409703)→delete；必填缺失（bs-030~032/036/037 status/sort）/可选 null（bs-029/033/034/035 clicks 只读派生——提交忽略断言）/position 非法（bs-398）/时间格式与 end≤start（bs-399/400）→ 422704 | E-MKT-21~25；bs-029~037/398~400 | P0 |
| TC-MKT-057 | admin 文章全链路：create(draft 无 slug 可)→get→update→patch status（publish 缺 slug 422704；publish 记 published_at）→delete；title 缺失（bs-039）/可选 null（bs-038/040~047）/status 非法（bs-401）/slug 129+pattern（bs-402）→ 422704；slug 重复 409702（创建与编辑排除自身两路径） | E-MKT-26~31；bs-038~047/401/402 | P0 |
| TC-MKT-058 | admin 案例/Lookbook/指南三族 CRUD+status：必填缺失（bs-225 couple/bs-215 title/bs-219·221 phase·title）、可选 null（bs-214/216~218/220/222~224/226~230）、长度极值（bs-492~505 逐条：lookbook title129/theme33、guide phase33/timeframe65/title129/tasks_count 负、wedding couple65/location129/theme33/date17/cover513）、status 非法（bs-494/499/505）→ 422704；product_ids 不存在 → 422704（bs-700/701）；id 不存在 404701 | E-MKT-32~46；bs-214~230/492~505/700/701 | P0 |
| TC-MKT-059 | RBAC/鉴权：无 token 401(40100)、store token 打 admin 端点 401、缺 `/promotions`·`/banners`·`/content/blog`·`/content/weddings`·`/content/lookbook` 权限 key 各 403(40300)（bs-627~632 越权流程含券核销/闪购管理写操作） | bs-627~632 | P0 |
| TC-MKT-060 | 失效链 E2E（s-758/FUNC-006）：发布文章 → 5s 内 ①JetCache 新值 ②MQ 消费者收到 blog_changed ③revalidate 回调被调（mock Next 端点断言 /blog+/blog/{slug} ×3 locale）④purge 被调；Banner toggle 同链路（type=banner_changed，路径 /） | FLOW-P03；TASK-056 | P0 |
| TC-MKT-061 | 闪购下线 E2E：active 活动过期 → SCHED tick → store flash-sales 不再返回 + 失效链四步（TC-MKT-060 口径）；倒计时数据 end_at 与 DB 一致 | TASK-060 / FLOW-P15 | P0 |
| TC-MKT-062 | 结算券链路衔接（与 trading 联测）：quote 携 coupon_code → marketing validate 直调生效（discount 进 quote）；下单核销 used_count+1；超时取消回滚 used_count-1（TASK-059 验收） | FLOW-P05/P06/P08 / SVC-MKT-01 | P0 |

## 6. 前端组件测试（FCT，断言逻辑非视觉——视觉归 ui-test-spec）

| TC | 内容 | P |
|---|---|---|
| TC-MKT-063 | Promotions.vue：券 status/search 服务端参数传递；删除按钮 draft/expired 外置灰预判；409701 code inline；CouponFormDrawer value pattern 按 type 切换占位与校验；三语 tab 部分提交载荷正确（translations 仅含编辑过 locale） | P0 |
| TC-MKT-064 | Banners.vue：Toggle 三态映射（draft→published/published→archived/archived→published）；乐观更新失败回滚；sort blur 提交整单载荷；「已过窗」派生角标 | P0 |
| TC-MKT-065 | ContentBlog.vue + BlogEditDrawer：publish 缺 slug 前端预判提示；409702 slug inline；预览链接 slug 空置灰；archived tab 筛选参数 | P0 |
| TC-MKT-066 | Weddings/Lookbook 表单：商品选择器（搜索防抖/chip 去重/移除）；422704 fields.product_ids 选择器区 inline | P1 |
| TC-MKT-067 | NewsletterModal/footer：email 预校验不发请求；成功确认文案（无折扣码话术）；重复提交 UI 与首次一致；exit-intent 触发 source=exit_intent 载荷；sessionStorage 防重弹 | P0 |
| TC-MKT-068 | Contact 表单：必填预校验；422704 字段红框分发；成功态切换；5xx 重试按钮 | P1 |
| TC-MKT-069 | Checkout 券码切面：Apply 大写归一；valid=false reason_code 三文案行内（不阻断下一步）；valid=true 减免行渲染 + couponCode 写入触发 requestQuote；未登录跳 login | P0 |
| TC-MKT-070 | 首页营销区块：hero 空回退静态；FlashSaleRail 空不渲染；倒计时到期本地隐藏；topbar 轮播数据源切换空回退 | P1 |
| TC-MKT-071 | Blog/Wedding 店面页：ISR 404701→notFound()；es 路径翻译渲染+缺翻译回退 EN；wedding 详情 Shop the Look 卡片渲染、空 products 区块不渲染 | P0 |

## 7. 韧性测试（RST，BE-DIM-5）

| TC | 内容 | 溯源 | P |
|---|---|---|---|
| TC-MKT-072 | MQ publish 失败：内容保存事务不回滚，JetCache 已失效，CDN 靠 TTL 过期（日志告警断言） | EC-MKT-002；bs-667 | P1 |
| TC-MKT-073 | q.invalidate 下游不可用（Next/Cloudflare）：重试 ×3 → DLQ；期间 store 读走 TTL 兜底不 5xx（serve-stale 语义在 CDN 层，源站断言旧缓存可读） | EVT-MKT-002；bs-668/669 | P1 |
| TC-MKT-074 | Redis 不可用：JetCache 两级降级本地 Caffeine + 直查 DB，内容读路径不 5xx；views INCR 失败静默（读路径不受阻） | BE-DIM-8 / DEC-MKT-6 | P2 |
| TC-MKT-075 | SCHED tick 异常：单实体翻转失败不阻塞其余（事务粒度断言）；下一 tick 补偿推进（时间谓词幂等） | TX-MKT-029 | P2 |
| TC-MKT-076 | catalogQueryPort 异常：内容详情商品装配失败 → 5xx（50000）但列表端点（不依赖 port）不受影响；闪购读 port 异常同口径 | MAP-MKT-012 | P2 |

## 8. 网络边界测试（NBT，强制——5173/5174 ↔ backend 前后端分离）

| TC | 内容 | P |
|---|---|---|
| TC-MKT-077 | CORS Preflight：OPTIONS /api/store/content/banners 与 /api/admin/promotions/coupons 返回正确 ACAO/Methods/Headers（含 PATCH——status 端点动词，易漏） | P0 |
| TC-MKT-078 | 跨域实际请求：5173 origin 匿名 GET 内容端点成功（无 Authorization 过 preflight）+ POST newsletter/contact 成功；5174 origin 带 admin token CRUD 成功 | P0 |
| TC-MKT-079 | 白名单拒绝：非白名单 origin 无 ACAO；store token 打 /api/admin/banners 401（跨端隔离） | P0 |
| TC-MKT-080 | 公开路径白名单回归：marketing 4 条 pattern 放行后，**/api/store/promotions/coupons/validate 仍强制鉴权**（精确路径不被 `/api/store/promotions/**` 误放行——本域白名单安全回归核心）；非白名单端点（/api/store/cart）仍强制鉴权 | P0 |

## 9. 测试数据工厂（FACTORY-MKT）

- F-Coupon（五 status 各一、三 type、有/无门槛、limit=5 可耗尽/缺省不限、有/无时间窗、含 es/fr 翻译、used_count 预置变体）
- F-FlashSale（draft/scheduled/active/ended、过期边界 end_at=now±1min、挂 0/3 商品、含翻译）
- F-Banner（三 position、三 status、窗口 前/中/后/空端、含 EN 文案列与 es/fr 翻译、sort 序列）
- F-BlogPost（draft 无 slug / published 有 slug、category 两组、含 es 全量/fr 部分翻译——回退测试、views 预置）
- F-RealWedding / F-Lookbook / F-Guide（draft/published、挂 0/N 商品、含翻译与 EN 文案列变体）
- F-NewsletterSubscriber（三 source×三 locale、大小写变体 email）/ F-ContactMessage（subject 有/无）
- F-MqEvent（content.invalidated 各 type 含重复 event_id 变体）
- F-AdminToken（含/缺 /promotions·/banners·/content/blog·/content/weddings·/content/lookbook key）/ F-StoreToken（券校验登录态 + 跨端误用）
- F-CatalogProduct stub（catalogQueryPort 测试替身：published 集合 + 缺失 id 集合）

## 10. 优先级矩阵与覆盖核对

| 优先级 | 用例 |
|---|---|
| P0 | TC-MKT-001/002/003/005/006/008~011/014~019/021~024/026/028/034~043/046/048~063/064/065/067/069/071/077~080 |
| P1 | TC-MKT-004/007/012/013/020/025/027/029~033/045/047/066/068/070/072/073 |
| P2 | TC-MKT-044/074/075/076 |

- [x] field-constraint-test-matrix：本域 0 行（仅 FLD-CUSTOMERS，归属他域）——已显式核对，无漏测项
- [x] boundary-scenarios 本域全映射：null/必填族 bs-029~047/197~230/379~389 → TC-MKT-051~058；extreme 族 bs-398~402/480~505/543~551 → TC-MKT-054~058/051/052；concurrent 族 bs-552~590 → TC-MKT-016/025/040；auth 族 bs-627~654 → TC-MKT-051/052/059；network 族 bs-667~669 → TC-MKT-072/073；integrity 族 bs-699~701 → TC-MKT-018/055/058；state 族 bs-733~810 → TC-MKT-034~039；callsite-compat 族 bs-851~853/881~889/943/944 → TC-MKT-044
- [x] 状态机 7 机全部迁移 + guard 拒绝 + 并发 + 终态非法事件（TASK-032/033/042~046 验收准则逐条对应；banner schedule_expire 按 DEC-MKT-2 收敛口径单列 TC-MKT-035）
- [x] 10 个域错误码全部出现在至少一个 TC；422701/702/703 双形态（200+reason_code 与 422 异常）断言；公开白名单安全回归（TC-MKT-080）落实 error-strategy L2 要求 2 测试面
- [x] 券边界（耗尽 CAS 并发/门槛/解析）、闪购时间窗（自动下线 E2E）、Newsletter 幂等不泄露存在性、失效链 5s E2E 全部覆盖
