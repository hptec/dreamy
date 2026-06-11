# marketing 前端详细设计（L2）

> 角色: l2_frontend_designer ｜ change: portal-api-integration ｜ domain: marketing
> 两端：portal-admin（Vue3 + Pinia + Vite + Tailwind/Headless-UI，port 5174，中文）+ portal-store（Next.js 15 App Router，port 5173，EN/ES/FR，决策 22 Node standalone + ISR）。
> 编号：页面路由(PAGE-MKT) / 状态管理(STORE-MKT) / 组件树(COMP-MKT) / 表单交互(FORM-MKT)。伪代码级 diff 设计——**以真实工程现状为基线，仅替换数据源（mock → API）与补齐编辑表单，不改设计 token 与布局结构**（原型强对照约束 1~4）。
> API 契约消费：marketing-api-detail.md E-MKT-01~46；错误处理按 error-strategy 前端呈现约定（admin 按 code toast/表单分发，store 按 code 映射 next-intl 字典）。

## 0. 真实工程现状基线（设计前提，已核对 /Volumes/MAC/workspace/dreamy/frontend）

| 文件 | 现状 | 改造策略 |
|---|---|---|
| portal-admin/src/views/Promotions.vue（66 行） | 券卡片 + 闪购表格双 tab，`@/data/mock`，「新建/编辑/删除」按钮无表单 | 保留布局，数据层换 API；新增 CouponFormDrawer/FlashSaleFormDrawer（原型无表单——新增功能落点，复用 panel/field/三语 tab 风格） |
| portal-admin/src/views/Banners.vue（52 行） | 表格 + Toggle online + 排序输入 + 「保存并发布」，mock | 保留表格结构；Toggle→E-MKT-25、sort blur→E-MKT-23、新增/编辑→BannerFormDrawer、删除→确认弹窗 |
| portal-admin/src/views/ContentBlog.vue（37 行） | 卡片网格 + filter tabs + 发布按钮（draft 行），mock | 保留卡片网格；filter→服务端 status 参数；「写文章/编辑」→BlogEditDrawer（含正文 + 三语 tab）；发布→E-MKT-31 |
| portal-admin/src/views/ContentWeddings.vue（36 行） | 案例卡片网格 + Shop the Look 件数，mock | 保留布局；编辑→WeddingFormDrawer（含商品选择器）；删除/发布接 API |
| portal-admin/src/views/ContentLookbook.vue（41 行） | lookbook/guide 双 tab 卡片，mock | 保留布局；两 tab 各自 FormDrawer；发布/删除接 API |
| portal-admin/src/api/ | client.ts（axios + R 解包 + snake↔camel + 401 跳登录）已有 | **新增 `src/api/marketing.ts`**，复用 client 全部拦截器 |
| portal-store/app/page.tsx（186 行） | hero/themeCards 硬编码，Real Weddings 区块 `@/data/content` | hero/featured 区块 data-swap → E-MKT-01；Real Weddings 区块 → E-MKT-04；新增 FlashSaleRail（空数据不渲染） |
| portal-store/components/layout/site-header.tsx | announcements 轮播来自 `@/data/navigation` | data-swap → E-MKT-01 position=topbar（title 文案轮播；空回退既有静态文案） |
| portal-store/components/layout/site-footer.tsx | Newsletter 输入框纯前端 | 接 E-MKT-11（source=footer） |
| portal-store/components/marketing/newsletter-modal.tsx（49 行） | 4s 定时弹窗，"Reveal My Code" 文案，preventDefault 模拟 | 接 E-MKT-11（source=modal）；文案改纯订阅确认（决策 26 显式降级，三语 i18n）；新增 exit-intent 触发（source=exit_intent） |
| portal-store/app/blog/page.tsx + blog/[slug]/page.tsx | `@/data/content` mock | RSC + ISR 接 E-MKT-02/03 |
| portal-store/app/real-weddings/page.tsx + [slug]/page.tsx | mock（slug 路由） | 接 E-MKT-04/05；**路由段目录名保持 `[slug]`，参数值改为数字 id**（契约按 id 取详情，链接 href=`/real-weddings/{id}`，视觉零变化——data-swap 标注） |
| portal-store/app/inspiration/page.tsx | mock lookbooks | 接 E-MKT-06/07 |
| portal-store/app/wedding-guides/page.tsx | mock guides | 接 E-MKT-08 |
| portal-store/app/contact/page.tsx（70 行） | preventDefault 模拟提交 | 接 E-MKT-12（name/email/message 受控 + 校验） |
| portal-store/lib/api/ | client.ts / case.ts（deepSnakeize/deepCamelize）已有 | **新增 `lib/api/marketing-api.ts`** |

## A. portal-admin（Vue3 + Pinia）

### A.1 页面路由（PAGE-MKT-A）

| 编号 | 路由 | 视图 | 权限 key（meta.permission） | 消费端点 |
|---|---|---|---|---|
| PAGE-MKT-A01 | /promotions | Promotions.vue | /promotions | E-MKT-13~16（券）、E-MKT-17~20（闪购）、E-CAT-31（闪购商品选择器标签维度备选——经 catalog api） |
| PAGE-MKT-A02 | /banners | Banners.vue | /banners | E-MKT-21~25、E-CAT-35 presign（scope=banner） |
| PAGE-MKT-A03 | /content/blog | ContentBlog.vue | /content/blog | E-MKT-26~31、E-CAT-35 presign（scope=content，封面） |
| PAGE-MKT-A04 | /content/weddings | ContentWeddings.vue | /content/weddings | E-MKT-32~36、E-CAT-08（商品选择器搜索）、E-CAT-35 presign（scope=content） |
| PAGE-MKT-A05 | /content/lookbook | ContentLookbook.vue | /content/lookbook | E-MKT-37~41（lookbook）、E-MKT-42~46（guide）、E-CAT-08（商品选择器） |

路由守卫沿用 identity GUARD-01~04（meta.permission ∉ permissionKeys 且非超管 → /403；菜单按权限过滤）；五个权限 key 即 BE-DIM-6 本域新增权限点。

### A.2 API 模块（src/api/marketing.ts，复用 client.ts）

```
listCoupons(params{page,pageSize,status,search})   GET    /admin/promotions/coupons        -> PageResult<Coupon>
createCoupon(body) / updateCoupon(id, body) / deleteCoupon(id)
listFlashSales(status?)                            GET    /admin/promotions/flash-sales    -> {items}
createFlashSale(body) / updateFlashSale(id, body) / deleteFlashSale(id)
listBanners(position?)                             GET    /admin/banners                   -> {items}
createBanner(body) / updateBanner(id, body) / deleteBanner(id) / toggleBannerStatus(id, status)
listBlogs(params{page,pageSize,status,search})     GET    /admin/content/blogs             -> PageResult<BlogPost>
getBlog(id) / createBlog(body) / updateBlog(id, body) / deleteBlog(id) / patchBlogStatus(id, status)
listWeddings(params{page,pageSize,status})         GET    /admin/content/weddings          -> PageResult<RealWedding>
createWedding(body) / updateWedding(id, body) / deleteWedding(id) / patchWeddingStatus(id, status)
listLookbooks(status?) / createLookbook / updateLookbook / deleteLookbook / patchLookbookStatus(id, status)
listGuides(status?) / createGuide / updateGuide / deleteGuide / patchGuideStatus(id, status)
```

分页消费 `PageResult`（totalElements ← total_elements，拦截器自动 camelize）；错误统一 ApiError{code, message, details}；上传复用 catalog `uploadViaPresign(file, scope)`（COMP-CAT-A06/STORE-CAT-A05 同一 composable，不重复实现）。

### A.3 状态管理（STORE-MKT-A，Pinia）

- STORE-MKT-A01 `usePromotionsStore`：{ coupons, couponsTotal, couponPage, couponFilters{status,search}, flashSales, flashStatus, loading, fetchCoupons(), saveCoupon()（create/update 分流）, removeCoupon(id), fetchFlashSales(), saveFlashSale(), removeFlashSale(id) }；写成功后列表 refetch + toast；删除 409703 → toast「当前发布状态不允许该操作」（券：仅草稿/已过期且未核销可删；闪购：仅草稿可删）
- STORE-MKT-A02 `useBannersStore`：{ list, positionFilter, loading, fetch(), save(), remove(id), toggleStatus(row, status)（乐观更新失败回滚+toast）, patchSort(row, sort)（blur 提交走 updateBanner 整单——携带行现值） }
- STORE-MKT-A03 `useBlogStore`：{ list, totalElements, page, pageSize, filters{status,search}, editing:BlogPost|null, loading, fetch(), openEdit(id?)（id 给定先 getBlog 全量回读）, save(), remove(id), patchStatus(id,status) }
- STORE-MKT-A04 `useWeddingsStore`：{ list, totalElements, page, statusFilter, fetch(), save(), remove(id), patchStatus(id,status) }
- STORE-MKT-A05 `useLookbookStore`：{ lookbooks, guides, statusFilter, fetchLookbooks(), fetchGuides(), saveLookbook()/removeLookbook()/patchLookbookStatus(), saveGuide()/removeGuide()/patchGuideStatus() }
- STORE-MKT-A06 `useProductPicker()` composable：商品选择器共享逻辑（闪购/案例/Lookbook 三处复用）——E-CAT-08 search 防抖 300ms + 已选 chip 集合（productIds 去重）+ 选中行缩略图/名称展示

### A.4 组件树（COMP-MKT-A）

- COMP-MKT-A01 `Promotions.vue`（PAGE-MKT-A01）：双 tab 布局保持（券卡片网格 / 闪购表格）。券 tab：tone/label 映射沿用（active/expiring/draft/scheduled + 补 expired→neutral『已过期』）；status 下拉 + search 输入（防抖 300ms 服务端）+ Pagination；卡片「编辑」→ CouponFormDrawer、「删除」→ 确认弹窗（draft/expired 以外置灰 + tooltip，js_guard 前端预判，409703 兜底 toast）；已用进度 `used/total`（total>9999 显示「不限」，DEC-MKT-5 展示规则）。闪购 tab：status 下拉；行「编辑」→ FlashSaleFormDrawer（ended 行编辑按钮置灰 + tooltip『已结束活动不可编辑』）；「删除」仅 draft 行显示；商品数列 = productIds.length
- COMP-MKT-A02 `CouponFormDrawer`（Headless-UI Dialog，**根组件传 class 必配 `as`——CP-072**）：字段 code（大写自动转换 + pattern 即时提示）/name/type 三选/value（按 type 占位提示 '15% OFF' 或 '$50 OFF'，pattern 即时校验 DEC-MKT-4）/min_amount/total_limit（>9999 提示「视为不限」）/start_at·end_at（datetime 选择，end>start 即时 js_guard）/status 五选（expiring/expired 创建时禁选）+ **三语 tab（EN/ES/FR）**：EN 写主字段 name/description，ES/FR 写 translations[]；used_count 只读展示（编辑态）
- COMP-MKT-A03 `FlashSaleFormDrawer`：name/discount/start_at·end_at（必填 + js_guard）/status 四选（ended 禁选）+ 商品选择器（useProductPicker，product_ids chip）+ 三语 name tab
- COMP-MKT-A04 `Banners.vue`（PAGE-MKT-A02）：表格结构保持（Banner 图/位置/投放时间/上线 Toggle/点击/排序/操作）。Toggle v-model=online 改为 status 三态映射：online=status==='published'；Toggle on → toggleStatus('published')（draft/archived 均→published，对应 publish/republish）；Toggle off → toggleStatus('archived')（take_offline）；409703 回滚 + toast。排序 input blur → patchSort；「已过窗」行追加灰色角标（now>endTime 前端派生，DEC-MKT-2）；「新增 Banner」/「编辑」→ BannerFormDrawer；「保存并发布」按钮语义=逐行变更已即时提交，按钮改为整页 refetch 提示（保留视觉，行为标注）
- COMP-MKT-A05 `BannerFormDrawer`：name/image_url（MediaUploadCard 复用，scope=banner，502501 降级提示——catalog 域 presign 端点错误码，跨域消费标注）/position 三选/start_time·end_time（js_guard）/status/sort + EN 文案区（title/subtitle/cta_text，DEC-MKT-1 可选）+ ES/FR 三语 tab（title/subtitle/cta_text）
- COMP-MKT-A06 `ContentBlog.vue`（PAGE-MKT-A03）：filter tabs（全部/已发布/草稿 + 补『已归档』tab——与 API status 枚举对齐，沿用 tab 样式）改服务端参数 + search 输入 + Pagination；卡片操作：「编辑」→ openEdit(id)（getBlog 回读）；「预览」→ 新窗口打开 `{storeBase}/blog/{slug}`（slug 空则置灰）；「发布」按钮（draft 行）→ patchStatus('published')（slug 空 → 422704 提示「发布前需填写 slug」并打开编辑抽屉）；published 行追加「下线」ghost 按钮 → patchStatus('archived')；archived 行「重新发布」；删除 → 确认弹窗
- COMP-MKT-A07 `BlogEditDrawer`（大抽屉，Dialog `as` 配齐）：title/slug（pattern 即时提示 + published 必填星标联动）/category/author/cover（上传 scope=content）/content（textarea 富文本基线——沿用既有 field 风格，不引入新编辑器依赖）/status + **三语 tab**：EN 主字段，ES/FR translations（title/excerpt/body/seo_title/seo_description）；views/published_at 只读展示
- COMP-MKT-A08 `ContentWeddings.vue`（PAGE-MKT-A04）：卡片网格保持（cover/theme/couple/location/date/StatusBadge/Shop the Look 件数）；「新增婚礼故事」/「编辑」→ WeddingFormDrawer；StatusBadge 点击或操作区「发布/下线」→ patchStatus（draft↔published 双向）；删除确认
- COMP-MKT-A09 `WeddingFormDrawer`：couple/location/theme/wedding_date/cover（上传）/status + EN 文案区（title/story）+ ES/FR 三语 tab（title/story）+ Shop the Look 商品选择器（useProductPicker）
- COMP-MKT-A10 `ContentLookbook.vue`（PAGE-MKT-A05）：双 tab 保持。lookbook 卡片：「编辑」→ LookbookFormDrawer（title/theme/description EN + 三语 tab + 商品选择器）；guide 行：「编辑」→ GuideFormDrawer（phase/timeframe/title/tasks_count/body EN + 三语 tab）；两类均含「发布/下线」与删除
- COMP-MKT-A11 空/加载态：列表 loading 骨架行 + EmptyState 复用既有组件风格（强对照约束 2）

### A.5 表单交互（FORM-MKT-A）

- FORM-MKT-A01 券保存：前端预校验（code 必填 pattern/name/type/value pattern 按 type/end>start/status 与时间窗一致性即时提示）→ 提交 → 409701 code 字段 inline「券码已存在」；409703 toast「已上线券不可修改券码」；422704 details.fields 逐字段分发 inline
- FORM-MKT-A02 闪购保存：start/end 必填 + js_guard；商品选择器空集允许（仅展示活动不挂品）；422704 fields.product_ids → 选择器区 inline「包含已删除商品，请移除」
- FORM-MKT-A03 Banner Toggle：乐观更新 → 失败回滚 + toast；同态重复点击不发请求（幂等预判）
- FORM-MKT-A04 文章发布：slug 空时「发布」前端预判提示（js_guard）+ 后端 422704 兜底；slug 重复 409702 字段 inline
- FORM-MKT-A05 三语 tab 通用：ES/FR 空字段允许提交（缺翻译消费端回退 EN）；tab 标注翻译完整度圆点（任一字段非空即绿）——与 catalog FORM-CAT-A06 同构
- FORM-MKT-A06 删除确认：全部删除走二次确认弹窗（CP-071）；guard 类 409703 toast 文案按 code 映射中文
- FORM-MKT-A07 内容写保存成功 toast 统一附注「已触发缓存失效链」（published 面写操作；s-758 用户感知）

## B. portal-store（Next.js 15 App Router，决策 22 Node standalone）

### B.1 API 模块（lib/api/marketing-api.ts，复用 client.ts + case.ts deepCamelize）

```
fetchStoreBanners(position, locale)        GET  /api/store/content/banners        （RSC fetch + next.revalidate）
fetchStoreBlogs(params, locale)            GET  /api/store/content/blogs
fetchStoreBlog(slug, locale)               GET  /api/store/content/blogs/{slug}
fetchStoreWeddings(params, locale)         GET  /api/store/content/weddings
fetchStoreWedding(id, locale)              GET  /api/store/content/weddings/{id}
fetchStoreLookbooks(locale)                GET  /api/store/content/lookbooks
fetchStoreLookbook(id, locale)             GET  /api/store/content/lookbooks/{id}
fetchStoreGuides(locale)                   GET  /api/store/content/guides
fetchStoreFlashSales(locale)               GET  /api/store/promotions/flash-sales
validateCoupon(code, subtotal)             POST /api/store/promotions/coupons/validate（StoreBearerAuth，客户端 fetch）
subscribeNewsletter({email,source,locale}) POST /api/store/newsletter（匿名）
submitContact({name,email,subject,message}) POST /api/store/contact（匿名）
```

内容读全部匿名公开（无 Authorization）；locale 取自路由前缀段（/es /fr，EN 无前缀，决策 27）。

### B.2 页面路由与 ISR/revalidate 策略（PAGE-MKT-S）

| 编号 | 路由（×3 locale 前缀） | 渲染 | 缓存/再生策略 | API |
|---|---|---|---|---|
| PAGE-MKT-S01 | /（首页营销切面） | RSC + ISR | `revalidate = 300`；on-demand：banner_changed/flash_sale_changed/wedding_changed → revalidatePath('/') ×3（EVT-MKT-002）。hero 区：fetchStoreBanners('hero') 首条（空回退现有静态 hero——冷启动安全）；featured 区块同理；FlashSaleRail：fetchStoreFlashSales 空 items 整段不渲染；Real Weddings 区块：fetchStoreWeddings({page:1,pageSize:3}) | E-MKT-01/04/09 |
| PAGE-MKT-S02 | layout 级 topbar（site-header） | RSC props 下传 | layout fetchStoreBanners('topbar') `revalidate = 300`；空 → 回退 data/navigation announcements 静态文案（视觉零变化） | E-MKT-01 |
| PAGE-MKT-S03 | /blog | RSC + ISR | `revalidate = 300` + on-demand（blog_changed → /blog）；分页 searchParams ?page= 驱动 | E-MKT-02 |
| PAGE-MKT-S04 | /blog/[slug] | RSC + ISR | **删除 generateStaticParams（如有）**，`dynamicParams=true` + `revalidate = 300`；on-demand revalidatePath('/blog/{slug}') ×3（s-758）；404701 → `notFound()` | E-MKT-03 |
| PAGE-MKT-S05 | /real-weddings | RSC + ISR | `revalidate = 300` + on-demand | E-MKT-04 |
| PAGE-MKT-S06 | /real-weddings/[slug]（参数值=数字 id，目录名保持） | RSC + ISR | `dynamicParams=true` + `revalidate = 300` + on-demand；404701 → notFound()；Shop the Look 区块用响应 products[]（ProductCard 复用） | E-MKT-05 |
| PAGE-MKT-S07 | /inspiration | RSC + ISR | `revalidate = 300` + on-demand（lookbook_changed）；卡片点开详情（页内展开或锚点，沿用现有交互形态）拉 E-MKT-07 关联商品 | E-MKT-06/07 |
| PAGE-MKT-S08 | /wedding-guides | RSC + ISR | `revalidate = 300` + on-demand（guide_changed） | E-MKT-08 |
| PAGE-MKT-S09 | /contact | client（表单页壳静态） | 不缓存提交；成功态沿用现有「Thank you」面板 | E-MKT-12 |
| PAGE-MKT-S10 | Newsletter 切面（footer + modal + exit-intent，全站 layout 级） | client | 不缓存 | E-MKT-11 |
| PAGE-MKT-S11 | /checkout 券码切面（trading 页面内本域调用） | client | 不缓存；「Apply」即时校验后写 checkoutStore.couponCode 触发 requestQuote（权威计算仍在 quote/order，trading FORM-TRD-S02/S03） | E-MKT-10 |

### B.3 状态管理（STORE-MKT-S）

- STORE-MKT-S01 服务端内容数据不进客户端 store（RSC props 直传，与 catalog STORE-CAT-S01 同构）
- STORE-MKT-S02 `useNewsletter()`：{ email, status:idle|submitting|done|error, submit(source) }——成功置 done 渲染确认文案；422704 → email 红框（字典 errors.422704_email）；重复订阅与首次订阅 UI 完全一致（后端不泄露存在性，前端无差异分支）
- STORE-MKT-S03 `useContactForm()`：受控字段 name/email/subject/message + 前端预校验（必填/长度/email 格式，不通过不发请求）+ 422704 details.fields 字段红框 + 成功置 sent
- STORE-MKT-S04 `useCouponField()`（checkout 内）：{ code, applying, result:CouponValidateResponse|null, apply() }——valid=false → reasonCode 映射 next-intl 文案行内提示（422701 不可用/422702 未达门槛 min_amount 透出/422703 已领完），**不阻断结算**；valid=true → 写 checkoutStore.couponCode + 展示 discountAmount（USD 基准，展示换算走 currencyStore）；401 → 引导登录（券校验需登录态）
- STORE-MKT-S05 FlashSale 倒计时：`useCountdown(endAt)` 客户端 hook（RSC 传 endAt，client 组件每秒 tick；到期本地隐藏区块——后端 SCHED 下线 + 60s 缓存兜底间隙的前端补偿）
- STORE-MKT-S06 i18n：next-intl 字典新增 marketing 命名空间（错误码 404701/422701/422702/422703/422704 文案 ×3 语 + newsletter 订阅确认文案（决策 26 新文案节点）+ contact 校验文案 + flash 倒计时标签）

### B.4 组件树（COMP-MKT-S）

- COMP-MKT-S01 首页 hero（既有 section）：props 由硬编码切 StoreBanner（imageUrl/title/subtitle/ctaText camelCase 对齐）；banner.title 空回退现有静态文案（DEC-MKT-1 EN 列空容错）；视觉零改动
- COMP-MKT-S02 `AnnouncementBar`（site-header 内既有轮播）：announcements 数组 ← topbar banners title 列表；空回退静态；轮播逻辑/样式不变
- COMP-MKT-S03 `FlashSaleRail`（**新增组件，token 同源**）：SectionHeading + 倒计时徽章（useCountdown）+ ProductCard 横轨（products ProductRef → 卡片 props 子集映射）；items 空整段不渲染（冷启动安全，与 catalog RecommendationRail 同口径）
- COMP-MKT-S04 Blog 列表卡片（既有）：字段映射 title/cover/category/author/excerpt/publishedAt/views；分页「加载更多/页码」按现有形态接 Paginated
- COMP-MKT-S05 Blog 详情（既有 [slug] 页）：content 渲染（现 mock 为段落数组 → API 为单字符串，按换行 split 渲染段落——data-swap 标注）；seo_title/seo_description 进 metadata export（generateMetadata 用 fetchStoreBlog）
- COMP-MKT-S06 RealWedding 卡片/详情（既有）：列表字段 couple/location/theme/weddingDate/cover/title；详情 story 段落渲染 + Shop the Look 区块（products → ProductCard）；原 mock gallery 多图字段契约无 → 详情图区仅 cover 主图（data-swap 显式偏离标注：gallery 字段未建模，原型多图画廊降级单图，归因 er-diagram 字段集）
- COMP-MKT-S07 Lookbook 网格 + Guide 时间轴（既有 inspiration/wedding-guides 页）：字段映射 title/theme/description、phase/timeframe/title/body/tasksCount；空列表 → EmptyState 风格空态
- COMP-MKT-S08 `NewsletterModal`（既有，改造）：文案 "Take 10% off / Reveal My Code / Check your inbox for your code" → 纯订阅确认三语文案（决策 26 显式功能降级，不构成强对照违例）；submit → useNewsletter.submit('modal')；新增 exit-intent 监听（document mouseleave 顶缘，sessionStorage 同 key 防重弹）→ 同 modal 复用，source='exit_intent'
- COMP-MKT-S09 site-footer Newsletter 输入（既有）：submit → useNewsletter.submit('footer')；成功行内确认文案（sage 信息条风格复用）
- COMP-MKT-S10 Contact 表单（既有）：Field 组件受控化 + 错误红框（eyebrow/field 类复用）；成功态面板不变
- COMP-MKT-S11 Checkout 券码输入（trading COMP-TRD-S02 Shipping 步内，本域切面）：输入 + Apply 按钮 + 结果行（valid → 绿色减免行；invalid → reasonCode 文案行）；与 quote.couponReasonCode 双通道一致（apply 即时反馈 + quote 权威复核）

### B.5 表单交互（FORM-MKT-S）

- FORM-MKT-S01 Newsletter：email 前端格式预校验（不通过红框不发请求）；提交防重（submitting 禁用）；成功后 sessionStorage 标记不再弹 modal
- FORM-MKT-S02 Contact：必填 name/email/message 预校验；subject 下拉值透传（≤200）；422704 字段分发；5xx → 通用错误态 + 重试按钮
- FORM-MKT-S03 券码 Apply：code 大写归一 + pattern 预校验；Enter 触发；未登录点击 Apply → 跳 /account/login?returnTo=/checkout（401 预判，券校验需登录态）
- FORM-MKT-S04 内容页 404：blog slug / wedding id / lookbook id 失效 → notFound()（Next 404 页，非白屏；CDN null 缓存防穿透由后端承载）

## C. 原型对照表（prototype_source + conformance）

| 真实文件（frontend/） | prototype_source（hhspec/prototype/） | conformance |
|---|---|---|
| portal-admin/src/views/Promotions.vue | portal-admin/src/views/Promotions.vue | **layout-keep + data-swap + form-add**：双 tab/券卡片/闪购表格不变；mock→API + status/search 服务端化；原型无编辑表单——新增 CouponFormDrawer/FlashSaleFormDrawer（新增功能落点，panel/field/Dialog 风格复用） |
| portal-admin/src/views/Banners.vue | portal-admin/src/views/Banners.vue | **layout-keep + data-swap**：表格列/Toggle/排序输入不变；Toggle online ↔ status published/archived 三态映射（E-MKT-25）；新增 BannerFormDrawer |
| portal-admin/src/views/ContentBlog.vue | portal-admin/src/views/ContentBlog.vue | **layout-keep + data-swap**：卡片网格/筛选 tab 不变（补『已归档』tab，API 枚举对齐标注）；新增 BlogEditDrawer 三语编辑 |
| portal-admin/src/views/ContentWeddings.vue | portal-admin/src/views/ContentWeddings.vue | **layout-keep + data-swap**：卡片结构不变；新增 WeddingFormDrawer + 商品选择器 |
| portal-admin/src/views/ContentLookbook.vue | portal-admin/src/views/ContentLookbook.vue | **layout-keep + data-swap**：双 tab 不变；新增两 FormDrawer |
| portal-store/app/page.tsx | app/page.tsx | **layout-keep + data-swap**：hero/featured/RealWeddings 区块数据源切 API（空回退静态，冷启动安全）；新增 FlashSaleRail（空不渲染，token 同源新增组件条款） |
| portal-store/components/layout/site-header.tsx | components/layout/site-header.tsx | **data-swap**：announcements ← topbar banners，空回退静态 |
| portal-store/app/blog/* | app/blog/* | **layout-keep + data-swap**：mock→E-MKT-02/03 + ISR on-demand；content 段落渲染适配标注 |
| portal-store/app/real-weddings/* | app/real-weddings/* | **layout-keep + data-swap**：路由参数值 slug→id（目录名保持）；gallery 多图降级 cover 单图（**显式偏离**：契约/er 无 gallery 字段，归因标注） |
| portal-store/app/inspiration/page.tsx | app/inspiration/page.tsx | **layout-keep + data-swap**：mock lookbooks→E-MKT-06/07 |
| portal-store/app/wedding-guides/page.tsx | app/wedding-guides/page.tsx | **layout-keep + data-swap** |
| portal-store/components/marketing/newsletter-modal.tsx | components/marketing/newsletter-modal.tsx | **data-swap + copy-change**：提交接 E-MKT-11；折扣码话术移除（**决策 26 显式功能降级**，不构成强对照违例）；新增 exit-intent 触发 |
| portal-store/app/contact/page.tsx | app/contact/page.tsx | **layout-keep + data-swap**：preventDefault→E-MKT-12，受控 + 校验 |

强对照红线复述：不改 tailwind token、不内联硬编码色值、新增 loading/error/empty 态复用既有组件风格、Headless-UI 根组件 class 必配 `as`（CP-072）。

## D. 自检

- [x] 两端齐备：portal-admin 5 页（PAGE-MKT-A01~A05）+ portal-store 11 路由/切面（PAGE-MKT-S01~S11）
- [x] 编号体系 COMP-MKT-A01~A11 / COMP-MKT-S01~S11、STORE-MKT-A01~A06 / STORE-MKT-S01~S06、FORM-MKT-A01~A07 / FORM-MKT-S01~S04、PAGE-MKT-A01~A05 / PAGE-MKT-S01~S11，无重号
- [x] 46 端点两端消费全部映射（admin 34 端点 → A01~A05；store 12 端点 → S01~S11；E-MKT-10 落 checkout 切面与 trading 设计衔接声明）
- [x] ISR/revalidate 策略明确（dynamicParams + revalidate 300 TTL 兜底 + EVT-MKT-002 on-demand ×3 locale，对齐 FLOW-P03/决策 22/27）
- [x] 三语 tab 表单（券/闪购/Banner/文章/案例/Lookbook/指南 七类全部含 ES/FR tab + EN 主字段，决策 13 + DEC-MKT-1）
- [x] 原型对照表含 prototype_source + conformance；三处显式偏离（newsletter 话术降级=决策 26、wedding gallery 降级单图、ContentBlog 补 archived tab）已标注归因
- [x] 错误呈现按 error-strategy 两端约定（409701/409702 字段 inline / 409703 toast / 422704 字段分发 / 4227xx reason_code 行内不阻断 / 404 notFound / 5xx 错误态+重试）
