# review 前端详细设计（L2）

> 角色: l2_frontend_designer ｜ change: portal-api-integration ｜ domain: review
> 两端：portal-admin（Vue3 + Pinia + Vite + Tailwind/Headless-UI，port 5174，中文）+ portal-store（Next.js 15 App Router，port 5173，EN/ES/FR，决策 22 Node standalone + ISR）。
> 编号：页面路由(PAGE-REV) / 状态管理(STORE-REV) / 组件树(COMP-REV) / 表单交互(FORM-REV)。伪代码级 diff 设计——**以真实工程现状为基线，仅替换数据源（mock → API）与补齐缺页/缺交互，不改设计 token 与布局结构**（原型强对照约束 1~4）。
> API 契约消费：review-api-detail.md E-REV-01~15；错误处理按 error-strategy 前端呈现约定（admin 按 code toast/表单分发；store 按 code 映射 next-intl 字典；**403801 = 评价入口隐藏+提示**）。

## 0. 真实工程现状基线（设计前提，已核对 /Volumes/MAC/workspace/dreamy/frontend）

| 文件 | 现状 | 改造策略 |
|---|---|---|
| portal-admin/src/views/Reviews.vue | **真实工程缺失**（views 目录无此文件；原型 583 行版存在） | 按原型「复制+适配」**新建**（评价审核/Q&A 双 tab + 详情抽屉 + Lightbox + 批量条），数据层全接 API |
| portal-admin/src/router/ | 既有守卫 GUARD-01~04（meta.permission） | 新增路由 `/reviews`（meta.permission='/reviews'，本 change 新增权限点）；菜单项「评价与 Q&A」按权限过滤 |
| portal-admin/src/api/ | 已有 client.ts（axios + R 解包 + snake↔camel + 401 跳登录） | **新增 `src/api/reviews.ts`**，复用 client 全部拦截器 |
| portal-store/components/product/product-reviews.tsx | 已有，**与原型逐字节一致**（sampleReviews/sampleQA 硬编码 + 静态 dist 百分比 + 纯展示按钮） | **data-swap + 交互补齐**：评价/Q&A 列表接 API；Write a Review / Ask a Question 按钮从纯展示升级为真实表单弹窗（原型无表单态，新增组件复用既有 modal/token 风格，显式标注） |
| portal-store/app/product/[slug]/page.tsx | RSC 已挂 `<ProductReviews product={product} />` | props 改传 `productId + 首屏评价数据`（RSC fetch，随 PDP ISR 缓存）；翻页/排序/提交走客户端 |
| portal-store/lib/api/ | client.ts / case.ts（deepSnakeize/deepCamelize）/ auth-api.ts / token-store.ts 已有 | **新增 `lib/api/review-api.ts`** |

## A. portal-admin（Vue3 + Pinia）

### A.1 页面路由（PAGE-REV-A）

| 编号 | 路由 | 视图 | 权限 key（meta.permission） | 消费端点 |
|---|---|---|---|---|
| PAGE-REV-A01 | /reviews | Reviews.vue（新建） | /reviews | E-REV-06 列表、E-REV-07 status、E-REV-08 featured、E-REV-09 batch、E-REV-10/11 reply、E-REV-12 image、E-REV-13 Q&A 列表、E-REV-14 answer、E-REV-15 visibility |

路由守卫沿用 identity GUARD-01~04（meta.permission ∉ permissionKeys 且非超管 → /403；菜单按权限过滤）。

### A.2 API 模块（src/api/reviews.ts，复用 client.ts）

```
listReviews(params)                  GET    /admin/reviews                      -> PageResult<AdminReview> & {pendingCount}
patchReviewStatus(id, status)        PATCH  /admin/reviews/{id}/status
patchReviewFeatured(id, featured)    PATCH  /admin/reviews/{id}/featured
batchReviews(ids, action)            POST   /admin/reviews/batch                -> {updatedIds, skippedIds}
putReviewReply(id, replyContent)     PUT    /admin/reviews/{id}/reply
deleteReviewReply(id)                DELETE /admin/reviews/{id}/reply
patchReviewImage(id, imageId, rejected) PATCH /admin/reviews/{id}/images/{imageId}
listQuestions(params)                GET    /admin/questions                    -> PageResult<AdminQuestion>
putQuestionAnswer(id, answer)        PUT    /admin/questions/{id}/answer
patchQuestionVisibility(id, visible) PATCH  /admin/questions/{id}/visibility
```

分页消费 `PageResult`（totalElements ← total_elements，拦截器自动 camelize；pending_count → pendingCount 平铺字段随响应解出——MAP-REV-007 Paginated 子类，消费端零适配）；错误统一 ApiError{code,message,details}。

### A.3 状态管理（STORE-REV-A，Pinia）

- STORE-REV-A01 `useReviewsStore`：{ list, totalElements, pendingCount, page, pageSize, filters{status, rating, featured, productId, search}, selectedIds, loading, fetch(), moderate(row, status)（409802→toast"仅待审核评价可审核"+refetch）, setFeatured(row, val)（乐观更新，409803 回滚+toast）, batch(action)（提交后按 updatedIds/skippedIds toast 汇总"已处理 N 条，跳过 M 条"+refetch+清选）, saveReply(id, content), removeReply(id), toggleImage(id, imageId, rejected) }；chips 映射：全部=status:all、待审核=status:pending、已通过=status:approved、精选=status:all&featured:true、已拒绝=status:rejected
- STORE-REV-A02 `useQuestionsStore`：{ list, totalElements, page, pageSize, filters{productId, answered}, loading, fetch(), saveAnswer(id, answer)（成功后行内 visible 同步为 visible——首答自动可见）, toggleVisible(row, visible)（乐观更新失败回滚） }
- STORE-REV-A03 顶部角标派生：PageHeader「N 条评价待审核」← pendingCount；「N 个提问待回答」← Q&A 列表 answered=unanswered 查询的 totalElements（切 tab 首载取得）

### A.4 组件树（COMP-REV-A）

- COMP-REV-A01 `Reviews.vue`（PAGE-REV-A01 页面壳，原型双 tab 结构保真）：mainTabs（评价审核/Q&A 管理）+ toast（复用原型 Teleport toast 形态）；两 tab 数据互不预载（切换时 fetch）
- COMP-REV-A02 评价审核 tab：状态 chips（5 枚，原型样式保真；**仅「待审核」chip 带计数角标 pendingCount，其余 chips 不带计数**——显式偏离，归因契约仅提供 pending_count，避免 5 路计数查询，见 §C 对照表）+ 星级下拉（rating 参数）+ 搜索框（search 参数，防抖 300ms）+ 批量操作条（已选 N 条 + 批量通过/批量拒绝 → E-REV-09 action=approve/reject）+ 表格（复选列/商品[主图+名，product_name 派生]/买家/星级五星组件/摘要 truncate/图片计数+驳N徽标/StatusBadge[reviewBadge 四态映射保真]/提交时间/操作列——pending 行：通过/拒绝按钮（js_guard `r.status==='pending'` 才显示，409802 后端兜底）；approved 行：设为精选/取消精选；其余「已处理」）+ Pagination（服务端分页）+ EmptyState（复用既有风格）
- COMP-REV-A03 `ReviewDrawer`（评价详情抽屉，原型 Teleport 结构保真）：商品+买家信息卡（customer_name 后台不脱敏）/审核操作区（pending→通过·拒绝；approved→精选切换）/完整评价内容/买家秀九宫格（rejected 图灰度+「已驳回」角标，点击开 Lightbox）/官方回复区——status≠approved 显示占位「评价通过审核后才可追加官方回复」（js_guard，409804 后端兜底）；有回复显示卡片（署名 reply_author + reply_time）+ 编辑/删除；无回复或编辑态显示 textarea + 发布按钮 `:disabled=!replyDraft.trim()`（js_guard，422801 兜底）
- COMP-REV-A04 `ReviewImageLightbox`（原型 Lightbox 保真）：大图 + rejected 灰度态 + 「驳回此图」/「恢复展示」按钮 → E-REV-12（成功后局部更新 images，toast 文案沿用原型「已驳回该图片，前台将不再展示」）
- COMP-REV-A05 Q&A 管理 tab：chips（全部/待回答/已回答 → answered 参数；计数同 A02 口径仅当前查询 totalElements）+ 搜索框（**前端当前页过滤**——契约 /admin/questions 无 search 参数，product_id 筛选透传 API，提问人/内容搜索为当前页内存过滤并 tooltip 标注「当前页过滤」，与 catalog Products.vue 高级筛选同处置先例）+ 表格（商品/提问 truncate/提问人/时间/回答状态 Badge/前台可见 Toggle→E-REV-15/操作「回答·编辑回答」）+ Pagination + EmptyState
- COMP-REV-A06 `QaDrawer`（问答详情抽屉，原型保真）：商品卡 + 可见性 Toggle + 买家提问 + 官方回答区（textarea `:disabled=!answerDraft.trim()` js_guard → E-REV-14；已答显示卡片 + 编辑回答）

### A.5 表单交互（FORM-REV-A）

- FORM-REV-A01 单条审核：通过/拒绝 → PATCH status → 成功 toast（沿用原型文案「评价 N 已通过，已记入操作日志」）+ 行内状态更新 + pendingCount-1；409802 → toast「仅待审核评价可审核」+ refetch（并发双审场景）
- FORM-REV-A02 批量：选中跨状态行允许提交（batchSet 不限当前状态语义）；响应 skippedIds 非空 → toast 附注「M 条因状态限制跳过」；批量后清空选择并 refetch
- FORM-REV-A03 精选：乐观翻转 → 409803 回滚 + toast「仅已通过评价可精选」；前端 js_guard：非 approved 行不渲染精选按钮（双保险）
- FORM-REV-A04 官方回复：trim 非空 js_guard（按钮 disabled）→ PUT → 409804 toast「仅已通过评价可回复」/422801 inline；删除回复二次确认（CP-071 危险操作）→ DELETE → 抽屉回复区回到输入态
- FORM-REV-A05 图片驳回/恢复：Lightbox 内单击操作，无确认弹窗（可逆操作，原型保真）；失败 toast「操作失败」保留现场
- FORM-REV-A06 Q&A 回答：trim js_guard → PUT → 成功 toast「官方回答已保存，已记入操作日志」；首答成功后行内可见 Toggle 自动置开（响应 visible=visible 回写本地行）
- FORM-REV-A07 错误兜底：401 清 token 跳 /login；403(40300) toast「无权限」；404801/404802/404803 toast「数据已变更」+ refetch；5xx toast「操作失败」保留表单现场（error-strategy admin 约定）

## B. portal-store（Next.js 15 App Router，决策 22 Node standalone）

### B.1 API 模块（lib/api/review-api.ts，复用 client.ts + case.ts deepCamelize）

```
fetchStoreReviews(productId, {sort, page, pageSize})   GET  /api/store/reviews     （首屏 RSC fetch + next.revalidate；翻页客户端 fetch，匿名）
createStoreReview(body)                                POST /api/store/reviews     （StoreBearerAuth，客户端）
fetchStoreQuestions(productId, {page, pageSize})       GET  /api/store/questions   （同 reviews 双态）
createStoreQuestion(body)                              POST /api/store/questions   （StoreBearerAuth）
presignReviewUpload({fileName, contentType})           POST /api/store/uploads/presign （StoreBearerAuth）
uploadReviewImage(file): presign → PUT 直传 upload_url（不经后端，FLOW-P17）→ 返回 publicUrl
```

读端点匿名（无 Authorization）；写端点经 client.ts 既有 401→refresh 续期重放链路。

### B.2 页面路由与渲染策略（PAGE-REV-S）

| 编号 | 路由（×3 locale 前缀） | 渲染 | 缓存/再生策略 | API |
|---|---|---|---|---|
| PAGE-REV-S01 | /product/[slug] 评价/Q&A 区（`#reviews` 锚点 section，嵌入 PAGE-CAT-S01） | RSC 首屏（第 1 页 featured_first + Q&A 第 1 页）+ 客户端交互区 | 首屏数据随 PDP ISR（`revalidate=300` TTL 兜底）；**秒级失效**：后台审核/回复/答复写 → `content.invalidated {type:review_changed\|question_changed}` → revalidatePath('/product/{slug}')×3 locale + purge（EVT-REV-002）；翻页/排序客户端直连 API（后端 JetCache 300s + CDN s-maxage 60s） | E-REV-01 / E-REV-03 |

评价内容不做多语翻译（key 不含 locale）：三个 locale 路径展示同一份评价数据，仅区块 chrome 文案（标题/按钮/相对时间）走 next-intl 字典。

### B.3 状态管理（STORE-REV-S）

- STORE-REV-S01 服务端首屏数据 RSC props 直传（不进客户端 store）；`useReviewSection`：{ page, sort, list（首屏 props 初始化）, ratingAvg/ratingCount/ratingBreakdown（聚合平铺字段）, loadingMore, fetchPage(), changeSort() }——切排序/翻页客户端 fetch 替换列表
- STORE-REV-S02 `useWriteReview`：{ open, rating, content, images[]{file, publicUrl, uploading, error}, submitting, submit(), errorCode }；403801 → 关闭表单并在入口处显示提示（见 FORM-REV-S01）；409801 → toast 字典「您已评价过该商品」
- STORE-REV-S03 `useQaSection`：{ page, list, loadingMore, askOpen, question, submitAsk() }；提交成功 → 确认态文案「提问已提交，回答后将展示在此处」（hidden 待审语义）
- STORE-REV-S04 i18n：next-intl 字典新增 review 命名空间（错误码 403801/404501/409801/422801/502801 文案三语 + reviews.* 区块文案：write_review/ask_question/verified_pending/submitted_pending 等）

### B.4 组件树（COMP-REV-S）

- COMP-REV-S01 `ProductReviews`（既有 components/product/product-reviews.tsx，**data-swap**）：双 tab（Reviews(N)/Q&A(N)——N 改 ratingCount 与 Q&A totalElements）；左栏评分汇总：大字均分 ← ratingAvg、Stars ← ratingAvg、`{ratingCount} reviews`、**dist 星级条 ← rating_breakdown 百分比计算**（替换硬编码 [70,22,5,2,1]）；右栏评价列表 ← StoreReview[]（姓名=customerName 已脱敏、日期=submittedAt 格式化、内容=content；原型 title/fit 字段契约无 → 不渲染该两行，布局自然收紧，视觉零 token 改动）；官方回复（replyContent 非空时）渲染署名小卡（新增节点，复用 Q&A 答案排版样式）；「Load more」翻页按钮（list < totalElements 时显示，新增交互复用 btn-outline）
- COMP-REV-S02 `ReviewImageStrip`（**新增**，COMP-REV-S01 子组件）：评价行下方缩略图行（≤9，后端已过滤 rejected）+ 点击放大 Lightbox（复用 portal-store 既有 gallery overlay 风格；新增组件遵循同源 token 条款）
- COMP-REV-S03 `WriteReviewModal`（**新增**——原型仅有「Write a Review」按钮无表单态）：星级选择（1..5 必选）+ content textarea（≤5000 计数）+ 图片上传区（≤9，presign 直传、进度态、502801 卡片错误态「图片服务暂不可用，可先发布评价稍后补图」——决策 9 降级）+ 提交；复用既有 modal（size-guide-modal 同型结构）与 btn-primary token
- COMP-REV-S04 `AskQuestionModal`（**新增**）：question textarea（1..1000）+ 提交 + 成功确认态；同 S03 风格基线
- COMP-REV-S05 Q&A 列表（COMP-REV-S01 内 tab，data-swap）：Q=question / A=answer（仅已答可见数据，后端双条件过滤）/ 署名固定字典文案「Dreamy Stylist」→ 改为不渲染署名行（契约 StoreQuestion 无 author 字段；显式偏离标注）+ asked 时间；「Ask a Question」按钮挂 S04
- COMP-REV-S06 错误/空态：列表空 → 「Be the first to review」空态（复用既有 EmptyState 风格）；fetch 失败 5xx → 通用错误态组件 + 重试

### B.5 表单交互（FORM-REV-S）

- FORM-REV-S01 写评价入口三态（error-strategy：403801 评价入口隐藏+提示）：①未登录 → 点击「Write a Review」跳 /account/login（带 returnTo 锚点）；②已登录提交后 403801 → 入口替换为提示文案「仅已完成订单的购买者可评价」（字典三语）；③409801 → toast「您已评价过该商品」。前端不预判购买资格（无查询端点），以提交时后端码为准
- FORM-REV-S02 写评价校验：rating 未选/越界前端预校验红框不发请求；content 超 5000 计数红字；images>9 阻止追加；提交成功 → modal 关闭 + 顶部确认条「评价已提交，审核通过后展示」（pending 语义，列表不插入本地行）
- FORM-REV-S03 图片上传：逐张 presign→PUT 直传→publicUrl 入 images[]；单张失败可重试/移除，不阻塞整单提交（url 仅取成功项）
- FORM-REV-S04 提问：trim 空禁用提交；成功后确认态（不插入列表——hidden 待回答）；404501 → toast 通用「商品不存在」
- FORM-REV-S05 排序/翻页：sort 四枚举下拉（featured_first 缺省，字典文案）；翻页追加加载（loadingMore 骨架行复用既有风格）

## C. 原型对照表（prototype_source + conformance）

| 真实文件（frontend/） | prototype_source（hhspec/prototype/） | conformance |
|---|---|---|
| portal-admin/src/views/Reviews.vue（**新建**） | portal-admin/src/views/Reviews.vue | **copy-adapt**：真实工程缺页，按原型 583 行版整体迁移（双 tab/chips/表格/抽屉/Lightbox/toast 结构与 token 零偏离）；数据层 mock→API + 服务端分页。**显式偏离 ×2**：①chips 计数仅「待审核」带角标（契约仅 pending_count，避免 5 路计数）；②Q&A 提问人/内容搜索为当前页过滤（契约无 search 参数，tooltip 标注） |
| portal-store/components/product/product-reviews.tsx | components/product/product-reviews.tsx | **layout-keep + data-swap**：tab/评分汇总/列表布局不变；sampleReviews/sampleQA/静态 dist→API 数据；**显式偏离 ×2**：①原型 title/fit/Verified Buyer 行契约无对应字段 → 不渲染（评价行收紧为 Stars+日期+内容+姓名）；②Q&A 署名行移除（契约无 author 字段） |
| portal-store/components/product/write-review-modal.tsx（**新建**） | —（原型仅按钮无表单） | **新增组件条款**：复用既有 modal 结构与 token（size-guide-modal 基线）；归因=契约 E-REV-02 提交端点必须有 UI 承载 |
| portal-store/components/product/ask-question-modal.tsx（**新建**） | —（同上） | 同上，承载 E-REV-04 |
| portal-store/app/product/[slug]/page.tsx | app/product/[slug]/page.tsx | **layout-keep + data-swap**：`<ProductReviews>` 挂载位置不变；props 由 mock product 切换为 productId + RSC 首屏评价/Q&A 数据 |

强对照红线复述：不改 tailwind token、不内联硬编码色值、新增 loading/error/empty 态复用既有组件风格、Headless-UI 根组件 class 必配 `as`（CP-072——ReviewDrawer/QaDrawer 若改用 Headless-UI Dialog 时强制）。

## D. 自检

- [x] 两端齐备：portal-admin 1 页双 tab（PAGE-REV-A01）+ portal-store PDP 评价区（PAGE-REV-S01）
- [x] 编号体系 PAGE-REV-A01/S01、STORE-REV-A01~A03/S01~S04、COMP-REV-A01~A06/S01~S06、FORM-REV-A01~A07/S01~S05，无重号
- [x] 15 端点两端消费全部映射到组件/store（admin 10 端点 ← A.2；store 5 端点 ← B.1）
- [x] 失效链消费侧明确（PDP ISR + content.invalidated on-demand revalidate ×3 locale；评价数据 locale 无关、chrome 文案走字典）
- [x] 原型对照表含 prototype_source + conformance；显式偏离 5 处全部标注归因（chips 计数/QA 搜索/评价行字段/QA 署名/新增表单组件）
- [x] 错误呈现按 error-strategy 两端约定（403801 入口隐藏+提示 / 409801·409802·409803·409804 toast / 422 inline / 502801 上传降级 / 401 续期或跳登录）
