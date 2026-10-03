# Java→Rust 迁移缺口审计报告（pd:monitor 深度审计 · 2026-10-01）

审计方式:三重交叉验证——静态端点对比(git 812c119^ 的 241 个 Java 端点 vs Rust 208 路由)、
运行时探针(对全部 102 个候选发真实请求,admin JWT 鉴权)、subagent 独立复核(方法学审查/契约语义/E2E 设计)。

## 一、本轮已修复(已验证)

| 问题 | 根因 | 修复 | 验证 |
|---|---|---|---|
| 业务表全部缺失(500 类故障根源) | Java 时代 DDL 随 backend/ 删除;Rust bootstrap 只建 21 张表 | 从 data/backup-identity-pre-drop-20260918 整库备份提取 74 张表→server/schema/business-legacy.sql,接入 bootstrap | categories/products/layout/cache-summary/refunds 全部 200 |
| bootstrap 库指向错误 | 业务 DDL 落主库 dreamy_server,而 svc 走 biz_db(identity) | bootstrap 拆分:identity.sql→主库;business-legacy+marketing-base→biz 库 | 日志 16+75+5 条语句,双库各自自举 |
| GET /api/store/exchange-rates 404 | 迁移遗漏 store 侧公开只读端点 | api_exchange_rate.rs 新增 store_router | 200 |
| POST /api/store/wishlists 405 | Java POST body 契约 vs Rust 仅 PUT 路径版 | 补 POST 别名;INSERT IGNORE rows_affected 区分 201/幂等 200;商品存在性校验 404501 | 401(路由在,待鉴权)✓ |
| POST /api/store/browse-history 405 + 裸 INSERT | 非 upsert,重复浏览撞唯一键 500601;无 50 条滚动 | ON DUPLICATE KEY UPDATE + 每人 trim 50(Java BrowseHistoryService 口径) | 401 ✓ |
| browse limit 无校验 | Java 1..50 越界 422601,缺省 20 | 补 Query 校验 | - |
| wishlist 删除 404 码 404501 | Java 404604 | 改码 | - |
| err_resp 恒 500(api_store_extras) | 未用 cat_err::http_status | 接入状态映射 | - |
| refund approve 丢 returnTrackingNo;缺 PATCH 登记退货单号 | body 未解析 | approve 解析 body;新增 PATCH /api/admin/refunds/{id}(决策31) | 编译通过 |

## 二、确认缺口(64 个真缺失 + 10 个方法不符,均 40400/405 实证)

按域分组(完整清单见 probe_v2_result.json MISSING_TRUE/METHOD_GAP):
1. **admin orders 全工作流**(9):列表(10 筛选)/复合详情/CSV 导出/ship/status 状态机(含取消退款联动)/production-stage/notes/refunds
2. **store content 4 端点**(home/navigation/footer/announcements):Rust 用 /api/store/layout 聚合替代,但前端仍调旧端点;且聚合缺 hero/product_rail 派生、i18n、公告时间窗、link_type URL 解析
3. **content 详情与 admin CRUD**:weddings/{id}、lookbooks/{id} 详情;guides/lookbooks/weddings admin 全套 CRUD+reorder+status
4. **admin shipping CRUD**(12):carriers/options/rates/quote-preview + status/enabled 开关
5. **admin coupons/flash-sales CRUD**(5)、**attribute-defs/sets**(4)、**collection-groups/collections**(5)、**categories**(4)
6. **store 交易流**:checkout/orders、payment-intent、confirm-delivery、reorder、coupons/validate、wishlists/{id}/move-to-cart(单事务)
7. 其它:tax-destination-policies(3)、checkout-config(2)、gateway-configs(3)、cache tasks(2)、questions/reviews batch、shipments/{id}/sync、ai/translate、guide-progress(2)、showrooms guest-session/remind、dashboard kpis(路径疑似改名,见 relocation)

**43 个端点为"改名重定位"**(如 /api/store/content/* → /api/store/layout 聚合;身份域路径重排),非缺失——后续以前端实际调用为准逐一对账。

## 三、语义债(迁移不止是路由,Subagent B 全量比对结论)

- **错误信封**:Rust 恒 message:null,admin 端中文文案缺失→错误提示全退化(需 common::i18n 接线)
- **响应 shape**:wishlist 列表缺 Java WishlistItemDto{id,productId,product:ProductBrief} 嵌套形;i18n locale 参数被忽略
- **鉴权安全项(建议尽快)**:① AuthedUser sub 解析失败静默 uid=0,应 401(identity token type=store 未强制);② Rust AuthedAdmin 无 @RequirePermission 等价 RBAC 权限键校验,越权面扩大
- **cart**:路由/业务语义基本对齐 ✓,遗留:加车应 201、dye_lot_product_ids 恒空、非数字 id 400 语义

## 四、建议执行顺序(Subagent A 终审修订)
1. **P0 安全**:AuthedUser 401 化 + admin RBAC 权限键
2. **P0 内容在用面**:GET /api/admin/site-builder/navigation/link-options(前端在用)+ store content 四件套(home/navigation/footer/announcements,先静态 i18n 版)
3. **P1 商品写侧**:products POST/PUT/batch(管理端商品创建/更新整体未迁移)
4. **P1 交易闭环**:store checkout/orders 流(下单/支付意向/确认收货/reorder)+coupons/validate+move-to-cart 事务
5. **P2 admin orders 工作流**(状态机高危,单独立项走 /pd:apply 全流程)
6. 每完成一批,重跑 /tmp/qa/probe_missing_v2.py 回归

## 五-D、P0 安全加固(Round 2,已修)
- **AuthedUser subject 不变量**:extractor 层强制 sub 为数字 customer_id,非法令牌 401 短路——关闭"下游静默挂到 customer 0"的越权/数据串号面(7 文件 13 处 unwrap_or(0) 由此成为不可达防御代码,原 Java StoreAuth 语义对齐)
- AuthedUser 新增 customer_id() 便捷访问;unauthorized_body 跨 crate 可用
- admin RBAC 权限键(逐路由)需要 claims/DB 设计,单列 P2

## 五-N、content admin CRUD 全量上线(Round 25-26,已部署验证)

- **weddings**(E-MKT-32~36):5 端点,TX 三表批插/子表整单替换/审计,9/9 用例通过(占位符列数不匹配修复)
- **lookbooks**(E-MKT-37~41):5 端点,7/7 通过——权限键修正为 Java 的 /content/lookbook(单数,与 guides 共用),fallback 改 product_image 来源(p.img 不存在)
- **guides**(E-MKT-42~46+reorder):6 端点,8/8 通过——tasks 整单替换(sort_order 按数组序),reorder 加全 id 存在性校验(避免部分提交)
- 全部 RBAC:/content/weddings、/content/lookbook(超管 27 权限点含全部,非授权角色 403)
- **教训**:①Java PERMISSION 常量逐控制器核对(lookbook 单数且与 guide 共用)②product.img 不存在——图片一律走 product_image(kind=1 主图)③reorder 需存在性前置校验防部分提交


## 五-P、admin orders 工作流收尾(Round 28,已部署验证)

- **ship**:POST /{id}/ship {carrier, trackingNo} → 委托 shipment::create(全部未发行行一个包裹)→ AdminOrderDetail;验证 status 2→3 SHIPPED+1 包裹(FEDEX_IP)
- **refund create**:POST /{id}/refunds {amount, reason} → 复用 apply_refund(已验证路径);验证 201 RFD-...-3346 20.00;退款后订单进入 REFUNDING(6)→后续退款 409705(语义正确,HTTP 500 为 err_resp 已知问题)
- **export**:GET /api/admin/orders/export → CSV(keyset 500/上限 10000/BOM/截断标记/公式注入中和);验证 200+DRM- 行
- carrier_code 枚举=carrier 表(FEDEX_IP/DHL_EXP/UPS_WW),非自由文本
- **admin orders 工作流至此全量上线**:list/detail/ship/patchStatus/production-stage/notes/refunds-create/export(+refunds 分页复用)


## 五-R、内容家族 Redis TTL 缓存(Round 33,已部署验证)

- **实现**:content.rs 缓存助手(cache_key/cache_get_json/cache_set_json/cache_invalidate_content);store weddings 列表(分页键)/lookbooks/guides 三读路径 60s TTL;Redis 不可用时静默直查
- 验证:miss→set(Redis 键 mkt:content:lookbooks:en TTL=60)/hit 内容一致/weddings 分页键独立缓存
- **遗留**:admin 写失效接线(13 handler)未完成——当前以 60s TTL 兜底(内容更新最多滞后 1 分钟);接线方式已定(handler 级 cache_invalidate_content 调用),留待后续批次

## 五-R、admin 写失效接线 + Redis 阻塞根因修复 + i18n message 接线(Round 33-34,已部署验证)

- **Redis 阻塞事故**:KEYS 命令(或连接异常)导致 Redis 挂死→store 全域请求在 cache await 上挂起(v8 回归 9 fail)
- **恢复**:restart Redis(ConnectionManager 自动重连);**根因修复**:cache_invalidate 改 SCAN(迭代)+UNLINK(异步删除),禁用 KEYS/DEL
- **失效接线**:13 个 admin 内容写 handler(weddings/lookbooks/guides create/update/delete/toggle/reorder)全部接线 cache_invalidate_content
- 验证:预热 1 key→admin update→0 key(失效生效)→Redis PONG→products 0.009s
- **i18n message**:24 处 err 信封 message 接线 common::i18n::message(code, Zh);marketing 码表(error.4xxxxx)待补 properties 条目(现回退 null)
- 回归:e2e-walk v9 全绿(167/162/0/0/0)——第 4 次连续全绿基线

## 五-Q、err_resp HTTP 状态码映射统一(Round 33,已部署验证)

- **问题**:17 个 api 模块各自定义 err_resp,其一硬编码 HTTP 500——409705/422001 等 body.code 正确但 HTTP 恒 500,admin 前端拦截器依赖 HTTP 状态
- **修复**:①http_status 映射表补 3 码(404702→404、409705→409、422001→422)②api_flashsale.rs 弃特例改统一 http_status ③api_dashboard_cache.rs 同步
- 验证:422001→422 通过 / 404601→404 通过 / 404701→404 通过 / flashsale 回归 200 通过
- **遗留(已知)**:message 字段中文文案——common::i18n 已有 zh 资源,接线为独立工作项

## 五-O、admin orders 工作流核心端点(Round 27,已部署验证)

- **新端点**:GET /{id} 33 字段复合详情(全事件+包裹+退款+地址快照)/POST /{id}/notes(NOTE=2,customer_visible 可选,≤512)/PATCH /{id}/status(CAS:仅 4/5/8;COMPLETED 3/8→4、DELIVERED 3→8、CANCELLED 1/2→5 回补库存+PAID 全额退款单)/PATCH /{id}/production-stage(仅 PAID,±1 档,type=7 事件)
- 验证:详情 200(全字段)/note 201/取消后 status=5+stock 14→15+refund 行 RFD-20261003-4704 91.95 干净/CAS 重复取消 409/取消后 stage 409/非法跳档 409/PAID→SHIPPED 422
- **第四类数据损坏 bug 发现**:sea_orm from_sql_and_values 绑定 7 参数对 6 占位符→**绑定错位静默写入垃圾行**(refund_no 二进制乱码/order_id 移位)——修复:改 execute 全内联 SQL(受控值)+占位符计数核对
- **教训**:占位符数量必须逐语句核对;绑定错位不报错而是**静默写坏数据**(比报错更危险),任何 INSERT 后应回读校验


## 五-M、store 内容详情端点 + lookbook status 缺陷(Round 24,已部署验证)

- **新端点**:GET /api/store/content/weddings/{id}、/api/store/content/lookbooks/{id}(E-MKT-05/07;V-MKT-006 非法/不存在 id→404701;形状=列表项)
- **P0 缺陷发现**:lookbook store 列表 SQL WHERE status = 1(DRAFT)——Java ContentStatus 语义 2=PUBLISHED,实际数据全为 2→live 列表返回空、/inspiration 页无内容
- 修复:status 1→2;发现并复用原已存在但从未路由的 lookbook_get(894 行),删除重复实现
- 验证:列表 3 项(Coastal Romance/The Garden Edit/Golden Hour)/详情 200/404701 三路径/wedding 回归 200/storefront 渲染 3 lookbook 全通过
- **教训**:status 语义必须对齐 Java 枚举(ContentStatus 1=DRAFT/2=PUBLISHED/3=ARCHIVED),不能沿用 product 的 1=上架直觉

## 五-L、item_count DECIMAL 提取修复(Round 20,已部署验证)

- **第三个同族 bug 变体**:SUM(qty) 在 MySQL 返回 DECIMAL(非 BIGINT)——try_get::<i64> 静默失败→item_count 恒 0
- 修复:改用 crate::cart::dec(r, "n")(DECIMAL helper)
- 部署验证:admin orders 列表 item_count=1 ✓(order 1 有 1 行 qty=1)
- 教训:MySQL 聚合函数返回类型需逐个核对——SUM(int)→DECIMAL 的坑与 BIGINT UNSIGNED/SIGNED 提取错位同属"sqlx 类型映射静默失败"家族

## 五-K、analytics 域对齐 Java(Round 17,已部署验证)
- Rust dashboard.rs 从简化捷径版全量重写为 Java AnalyticsReadMapper/QueryService 口径:PAID_STATUSES=(2,3,4,6,7)+paid_at 本月 UTC 窗口+USD 折算(HALF_UP 2 位)+todos 三计数+gmv_trend=TrendSeries{labels "M-D",values} 近 30 天补零齐桶+category_sales 三层溯根(share Σ=100)+top_products LIMIT6 快照
- overview:range∈{7d,30d,90d} 补零齐桶,非法 422001;traffic:无 GA4 源→unavailable 降级形态(Java DEC-ANA-5④ 对齐)
- 部署后 7 项验证:形状/SQL 交叉/range 三值/share 求和/traffic 占位/422001 语义/reorder 回归——全过
- 已知偏差:err_resp HTTP 500 硬编码(422001 在 body.code,前端语义可达);家族缓存未实现(数据量小);traffic 待 GA4 接入

## 五-J、login-policy 专项调查(Round 16,结论=非缺陷)
- **admin 应用无缺陷**:4 轮"跳转 /login"全是走查脚本子串误判('/security/login-policy'.includes('/login')===true)
- C 定向复现:整页加载/SPA 导航/对照组/未登录守卫四测全过,无 401/500/console error;三假设(Pinia/401 拦截/权限)全排除
- 走查脚本已修(3 处 URL 判定改 pathname 精确匹配)→ **v5 全绿基线**:167 步/161 ok/0 fail/0 unexpected
- 顺带发现(次要):Dashboard 渲染期 undefined.slice pageerror,建议 Dashboard.vue 字段兜底

## 五-I、E2E v2 回归两轮(Round 11/14,结论)
- v2 基线(数据同步后):166 步/159 ok/known-gap:tables 46→**0**/unexpected 2 族
- v4(脚本修复+端点部署后):167 步/160 ok/**unexpected=0**/known-gap:tables=0
- **login-policy"跳转"根因=走查脚本子串误判**('/security/login-policy'.includes('/login')===true)——admin 应用无缺陷,脚本已修(3 处 URL 判定改 pathname 精确匹配),v4 起该项通过
- 遗留:Dashboard 渲染期一次 undefined.slice pageerror(次要,建议数据兜底)
- 剩余 known-gap:endpoint 1=admin orders 列表(P2 待移植)

## 五-H、交易闭环实测(Round 9,大部分通过)
- **OTP 登录链路**:send→bcrypt 种码(htpasswd $2y→$2b)→verify→tokens.access_token ✓
- **收藏→move-to-cart**:wishlist add(修复 product.id BIGINT UNSIGNED i64 提取 bug 后 200)→move-to-cart 200(cart TX 加车+删收藏)✓
- **地址创建**:契约字段=receiver/line/zip(+country/region/city/phone)——201 ✓(id=2)
- **quote**:subtotal 89/运费 FedEx IP 12.95/交付预估 ✓
- **coupons validate**:welcome10 小写归一→valid=true discount 20(min 150)✓
- **guide-progress(新用户)**:{} ✓
- **发现系统性 bug**:BIGINT UNSIGNED 列(product.id 等)try_get::<i64> 静默回 0——cart add_item 响应 product_id:0 即此因(DB 数据完好);已列专项审计(P0+,cart/checkout/order 36 处待扫)
- **待收尾**:checkout/orders 创建后 payment-intent→confirm→SHIPPED→confirm-delivery→reorder 全链路(本轮 carrier 参数已通,下单 422 carrier required 已定位——需先 quote 取 carrier)

## 五-G、交易闭环端点层(Round 7,路由全就位)
- order-actions subagent 交付:payment-intent(stub 语义+超时取消)/confirm-delivery(CAS→COMPLETED)/reorder(逐行容错+跳过码)/coupons validate(格式+门槛+耗尽判定序,修复 "10% OFF" 解析)/flash-sales(时间窗+卡片批查+no-store)——7 文件+router 2 行 merge
- checkout-orders 别名路由补齐(前端契约路径;create_order 复用)
- **全路由扫描:40400 归零**(401=需鉴权/200=公开,语义正确);auth 全流程实测(OTP 种子→下单→支付→确认收货)待下一轮

## 五-F、商品写侧+导出(Round 5/6,已验证)
- **products batch**(POST /api/admin/products/batch):五动作/≤200/去重/逐条容错/delete 幂等——6 用例实测通过(含 422501 中文文案对齐)
- **products create/update**(POST 201/PUT 200,dev subagent 移植):V-CAT-023~038 校验聚合、slug 409501/sku_code 409504(带 sku_codes)/并发 409508(millis 截断+CAS version)——10 用例实测全过,含事务回滚完整性
- **products export CSV**(GET /api/admin/products/export):keyset 500/上限 10000/BOM/截断标记/CV-CAT-01 转义/stock+sales 聚合——33 行实测全对
- ListQuery 补 category_id(export+list 通用);batch 校验 HTTP 状态 500→422 修正(Java 契约)

## 五-E、store 首页端到端验证(Round 4/5,通过)
- 数据同步 + content 四件套上线后,Playwright 实测 storefront 首页:**6 sections 全渲染**(hero carousel 2 张/featured banner/theme cards 4/featured products 8/wedding stories 3/公告条),8 商品链接/15 图片,**0 HTTP 错误**
- 途中根因修复:RSC 服务端取数基址(server-fetch.ts)dev 兜底仍指 18081 死端口 → 改 18082;同类地雷已全仓扫除(middleware/vite.js/vite.ts/next.config CSP/server-fetch)

## 五-C、站点装修链路核查(数据同步后,已修)
- **admin 读取面**:announcements/home-sections/footer/navigation GET 全 200 带真实数据 ✓
- **admin 写面**:POST/PUT/DELETE/sort 路由与 svc 实现本就齐全(此前探针 405 为陈旧二进制假象,A 的 BINARY_STALE 分类);实测 footer save 200、navigation save 204 ✓
- **link-options(缺失→已实现)**:GET /api/admin/site-builder/navigation/link-options?type&keyword——移植 Java NavigationService.linkOptions 七类语义(category L{level}/collection 启用/product 上架 slug/blog slug/wedding couple+title/lookbook theme/guide phase,status 过滤,kw 过滤,LIMIT 50);真实数据验证 type=3/4/5/6/7/8 全部返回 ✓(途中修 BIGINT UNSIGNED 需 u64 提取)
- **事故与恢复**:探测时以空数组 PUT footer/navigation 触发全量替换语义,清空了本地同步数据——已从 HK 定向恢复(footer_columns=4/footer_links=19/navigation_items=7)
- **剩余**:store content 四件套(home/navigation/footer/announcements,store 前台在用,建议两期:静态 i18n 先行);PUT /home-sections 批量保存(前端未用,低优)

## 五-B、admin 看板 500 根因(用户刷新实测发现,已修)
- **现象**:admin 首页刷新后 /api/admin/{dashboard,cache/summary,analytics/overview} 等经 5174 代理全部 500(空体),但直连 18082 均 200
- **根因**:portal-admin 同时存在 vite.config.ts(源)与 vite.config.js(编译产物,**Vite 解析优先级更高**);.js 兜底 '/api' 仍指 18081(Java 已退役死端口)→凡未命中六条身份域显式规则的请求全被转发到死端口,http-proxy 报空体 500。.js 第 7 行自注"编译产物且解析优先级更高,两者需保持同步"——本轮 .ts 改了 .js 没同步,恰好踩中该陷阱
- **修复**:.js 与 .ts 兜底统一改 18082;经 5174 复测 dashboard/cache/summary/analytics/products/refunds/auth-me 全 200,orders 404 为已知待移植项
- **治理建议**:删除 git 跟踪的 vite.config.js(保留 .ts 为唯一真源)或加 CI 校验两者同步;vite.config.d.ts 同属产物建议入 .gitignore

## 五、E2E 走查发现与处置(Subagent C,156 检查点/8 流程)
- **OTP 满 6 位自动提交永不生效(产品 bug,已修)**:OtpInput onComplete 在 setState 生效前同步触发,login-card/change-primary-dialog 闭包读到 5 位旧值→"Enter all 6 digits"。修复:onComplete 携带完整 code 串,消费方以显式参数校验(otp-input.tsx/login-card.tsx/change-primary-dialog.tsx)
- **newsletter/contact 全输入 500(已修)**:根因非 '+' 邮箱或 subject 缺省(校验本就正确),而是 api.rs 三处 svc 用 state.db(主库)写 marketing-base 表——表已迁 biz 库。改为 state.biz_db 后 normal/'+'邮箱/无 subject 全 201、newsletter 200
- **cache/gateway/attribute/banners/users "500/404"**:移动靶假象(C 走查与重启窗口重叠),直连实测全部 200——非缺口
- **表缺失误报 422704{__internal__:database}**:错误分型应映射 50000——列入错误信封语义债(见三)
- **admin /security/login-policy 跳回 /login**:疑似 vite chunk 冷编译期 auth/me 网络错触发 GUARD reset,待复现定位(观察项)
- 走查产物:/tmp/qa/e2e-walk.js(34KB,8 流程/156 检查点)、e2e-walk-result.json、screenshots/(23 张)

## 六、方法学审计修正(Subagent A 终审,数字以此为准)
- **终局数字:64 真缺失 + 10 方法缺口**(Java 241 vs Rust 216)。对账恒等式:旧 71 = 64 真 + 7 假(categories PUT/DELETE、refunds PATCH、store/config、store/identities×2、exchange-rates);旧 13 = 10 真 + 3 假(operation-logs DELETE=故意防护 40500 且非 Java 端点、wishlists/browse-history POST=旧二进制假象);stale 102 = fresh 74 + 28 幻影
- **幻影成因**:多行 .route 漏提(api_category.rs:144-151)+ identity mod.rs 二级 nest(/auth、/account)未展开——修正版 /tmp/qa/probe_missing_v2.py 用括号平衡扫描+nest 图推导
- **404 三义判型纪律**:40400 空回退=路由缺失;404xxx 业务码=路由存在;参数吞并型(products/export 被 {id} 吞)=真缺失
- **案例修正**:site-builder toggle 不缺失(Java/Rust 一致);categories PUT/DELETE、refunds PATCH 实为存在;store/config、store/identities 为幻影(真路径 /api/store/auth/config、/api/store/account/identities)
- **审计纪律沿用**:静态源码集合为一等真值,探针仅确认;404/405 必须带体分型;产物带 commit+时间溯源;/readyz 建议暴露 build commit;缺表环境 5xx 单独聚类

## 七、证据与工具
- probe_v2_result.json:运行时复核分类(本目录)
- java-endpoints-241.txt / rust-routes-208.txt / java-endpoints-missing-candidates.txt:静态对比基线
- /tmp/qa/probe_missing_v2.py:修正版探针(可回归);/tmp/qa/e2e-walk.js:E2E 走查(生成中)
- 修改文件:server/crates/common/src/bootstrap.rs、marketing/src/{api_exchange_rate,api_store_extras,store_extras,api_payment}.rs、server/src/router.rs、server/schema/business-legacy.sql(新增)
- portal-store 中间件 dev 代理 18081→18082(frontend/portal-store/middleware.ts,另案修复)
