# showroom 前端详细设计（L2）

> 角色: l2_frontend_designer ｜ change: portal-api-integration ｜ domain: showroom
> 端：**仅 portal-store**（Next.js 15 App Router，port 5173，EN/ES/FR，决策 22 Node standalone）——本域无后台端点，portal-admin 无改动。
> 编号：页面路由(PAGE-SHR) / 状态管理(STORE-SHR) / 组件树(COMP-SHR) / 表单交互(FORM-SHR)。伪代码级 diff 设计——以真实工程现状为基线，**copy-adapt 从 hhspec/prototype 迁移 + 数据层接 API**，不改设计 token 与布局结构（原型强对照约束 1~4，决策 20「复制+适配」）。
> API 契约消费：showroom-api-detail.md E-SHR-01~13；错误处理按 error-strategy 前端呈现约定（401101 guest 凭证失效提示重开邀请链接；403101/403102 权限提示页；404 个人资源通用「不存在或无权访问」；410101 邀请失效专属提示）。

## 0. 真实工程现状基线（设计前提，已核对 /Volumes/MAC/workspace/dreamy/frontend/portal-store）

| 文件 | 现状 | 改造策略 |
|---|---|---|
| app/showroom/（page.tsx + [id]/page.tsx） | **真实工程整目录缺失**（app/ 下无 showroom；原型有完整实现） | 按原型「copy-adapt」**新建**，数据层全接 API |
| components/showroom/（showroom-detail.tsx / add-to-showroom-modal.tsx） | **真实工程缺失**（components/ 下无 showroom 目录） | 同上新建；store-provider 内 showrooms 前端态**不迁移**（mock 态由 API + zustand 替代） |
| lib/api/（client.ts / case.ts / token-store.ts / auth-api.ts） | 已有（R 解包 + snake↔camel + 401→refresh 续期重放 + Accept-Language） | **新增 `lib/api/showroom-api.ts`**；client.ts 增加 `authTokenOverride` 选项（guest token 注入，见 B.1） |
| app/product/[slug]/page.tsx + components/product/ | 已有 PDP（catalog/trading 域已设计接入） | **切面新增** Add to Showroom 入口（图标按钮挂 AddToShowroomModal，原型 PDP 同位） |
| app/checkout/ | trading 域已设计（wedding_date 选填字段） | 本域提供婚期带入数据源（FORM-SHR-S10 交叉引用 trading FORM） |
| lib/i18n/ | next-intl 字典三语机制已有 | 新增 showroom 命名空间 + 11 个错误码三语文案 |

## 1. API 模块（lib/api/showroom-api.ts，复用 client.ts + case.ts deepCamelize）

```
listShowrooms()                                        GET    /api/store/showrooms                          （StoreBearerAuth）
createShowroom({name, weddingDate?})                   POST   /api/store/showrooms
getShowroom(id, {locale, guestToken?})                 GET    /api/store/showrooms/{id}?locale=             （双态：guestToken 提供时以其为 Authorization）
updateShowroom(id, {name, weddingDate?})               PUT    /api/store/showrooms/{id}
deleteShowroom(id)                                     DELETE /api/store/showrooms/{id}
resetShowroomInvite(id)                                POST   /api/store/showrooms/{id}/invite/reset        -> {inviteToken}
createGuestSession({inviteToken, nickname})            POST   /api/store/showrooms/guest-session            （匿名；已登录时仍带 store token——绑定回填 STEP-SHR-05）
addShowroomItem(id, {productId, color?})               POST   /api/store/showrooms/{id}/items
removeShowroomItem(id, itemId)                         DELETE /api/store/showrooms/{id}/items/{itemId}
voteShowroomItem(id, itemId, vote, {guestToken?})      PUT    /api/store/showrooms/{id}/items/{itemId}/vote -> {likeCount, dislikeCount, myVote}
commentShowroomItem(id, itemId, content, {guestToken?}) POST  /api/store/showrooms/{id}/items/{itemId}/comments
assignShowroomMember(id, memberId, {assignedItemId, email?}) POST /api/store/showrooms/{id}/members/{memberId}/assign
remindShowroomMember(id, memberId)                     POST   /api/store/showrooms/{id}/members/{memberId}/remind
```

- **client.ts 扩展（最小 diff）**：`RequestOptions` 新增 `authTokenOverride?: string`——提供时 Authorization 直用该值且 **跳过 401→refresh 续期重放**（guest token 无 refresh 概念；401 直接抛 ApiError 由 guest 流程处理 401101）。既有 store 鉴权请求行为零变化。
- **guest 请求错误分支**：401101 → 清除本房 guest 会话缓存 + 提示「访问凭证已失效，请重新打开邀请链接」；403102 → 权限提示页；store 鉴权请求沿用既有 401→refresh 链路。
- createGuestSession 走匿名分支但 `auth: true`（有 store token 则附带——白名单可选注入，完成 linked_customer_id 绑定；无 token 不报错）。

## 2. 页面路由与渲染策略（PAGE-SHR）

| 编号 | 路由（×3 locale 前缀） | 渲染 | 缓存策略 | API |
|---|---|---|---|---|
| PAGE-SHR-S01 | /showroom | 客户端组件（'use client'，登录守卫） | 不缓存（个人数据；fetch no-store） | E-SHR-01/02/05 |
| PAGE-SHR-S02 | /showroom/[id]（`?invite={token}` 为访客入口） | **动态渲染**（`export const dynamic = 'force-dynamic'`；**原型 generateStaticParams + dynamicParams=false 移除**——静态导出残留，决策 22 Node 运行时下协作数据必须实时）；壳 RSC + 客户端详情组件 | 不缓存（协作数据强一致） | E-SHR-03/04/06/07/08/09/10/11/12/13 |
| PAGE-SHR-S03 | /product/[slug] 与商品卡片（Add to Showroom 切面） | 既有 PDP（catalog PAGE-CAT-S01）客户端切面 | 随 PDP（modal 数据点开时实时 fetch） | E-SHR-02/01/08 |

**邀请链接形态定稿**：`{base}/showroom/{id}?invite={invite_token}`（原型 `?invite=bridal-party` 占位升级为真实 token，URL 结构保真）；三 locale 前缀均可承载（链接按 owner 当前 locale 生成）。

**路由守卫（FORM-SHR-S09 配合）**：
- /showroom：未登录 → 跳 `/account/login?returnTo=/showroom`（与 Wishlist 同口径，不发请求）。
- /showroom/[id] 三态判定（客户端启动序）：①已登录 → 先按 owner 身份 `getShowroom(id)`：200 owner 视图；404101 且 URL 带 invite → 转 guest 流（本人非 owner 的受邀者）；404101 且无 invite → 通用「不存在或无权访问」页。②未登录且本地有未过期 guest 会话（guestSessionStore）→ guest token 拉详情；401101 → 清缓存转 ③。③未登录且 URL 带 invite → 昵称加入态（COMP-SHR-S04）；④未登录无 invite 无会话 → 跳登录（带 returnTo 含完整 query）。

## 3. 状态管理（STORE-SHR，zustand——portal-store 既有范式）

- STORE-SHR-S01 `showroomListStore`：{ items: ShowroomSummary[], loading, fetched, fetch(), create(name, weddingDate)（成功后 unshift 摘要并返回 id）, remove(id)（二次确认后 DELETE → 列表剔除） }
- STORE-SHR-S02 `showroomDetailStore`：{ room: ShowroomDetail | null, identity: 'owner' | 'guest', loading, error: ApiError | null, fetch(id, {guestToken?}), vote(itemId, vote)（乐观置 myVote → 响应 {likeCount, dislikeCount, myVote} 覆盖该 item 聚合；失败回滚）, comment(itemId, content)（成功 push 响应 Comment 至该 item.comments）, addItem/removeItem, assign(memberId, {assignedItemId, email?})（响应 Member 覆盖行；409103 → toast + refetch）, remind(memberId)（同上）, updateProfile, resetInvite()（响应新 inviteToken 覆盖 room.inviteToken） }
- STORE-SHR-S03 `guestSessionStore`（guest token 前端存储定稿）：localStorage key `dreamy.showroom.guest.{showroomId}` → `{ guestToken, expiresAt, memberId, nickname, inviteToken }`；API：load(showroomId)（过期即清）, save(session), clear(showroomId), rejoin(showroomId)（用缓存 inviteToken+nickname 静默重放 createGuestSession——token 过期续命/登录后绑定回填两用）。**不存 cookie**（无 SSR 消费场景，详情为客户端拉取；XSS 面与既有 token-store 同基线）
- STORE-SHR-S04 i18n：next-intl 字典新增 `showroom.*` 命名空间（错误码 401101/403101/403102/404101/404102/404103/409101/409102/409103/410101/422101 三语 + 区块文案：new_showroom/invite_party/copy_link/reset_link/i_am/join/vote/comment_placeholder/assigned_to/send_reminder/reminder_sent/ordered/awaiting_order/browsing/dye_lot_notice/order_this_dress 等）；错误 message 不直渲染，**一律按 code 映射字典**（决策 27）

## 4. 组件树（COMP-SHR）

- COMP-SHR-S01 `app/showroom/page.tsx`（copy-adapt 原型同路径文件）：眉题/标题/New Showroom 按钮/卡片栅格（封面三图 ← items[].product.imageUrl 前 3、名称、婚期 + days-to-go、styles/members 计数 ← itemCount/memberCount）/空态（PartyPopper 虚线框）/删除二次确认（Delete/Keep 行内态保真）——数据源 useStore().showrooms → `showroomListStore`；封面图原型经 products.find 本地查找 → 改为 Summary 无封面数据，**列表卡片封面降级为首图占位**：Summary 不含 items —— 显式偏离①：卡片封面三图改为「无封面占位 + 计数」（契约 ShowroomSummary 无 items 字段；避免列表页 N 次详情请求），其余布局/token 零改动
- COMP-SHR-S02 `CreateShowroomModal`（copy-adapt，S01 内联组件保真）：name + wedding date 双字段 + 行内错误（422101 fields → 字典文案；原型「Please enter a name and wedding date.」改 name 必填/婚期可选——**显式偏离②**：契约 wedding_date 可选，原型双必填校验放宽为 name 必填，归因契约 ShowroomUpsert required:[name]）
- COMP-SHR-S03 `components/showroom/showroom-detail.tsx`（copy-adapt 核心组件）：返回链接/头部（名称、婚期、成员数）/邀请条/dye lot 提示条/款式网格/成员表/Toast 结构全保真；**视图驱动改造（显式偏离③）**：原型 bride/guest 手动切换按钮组（演示装置）**移除**，视图由 API `is_owner` 字段驱动（owner 视图=管理态，guest 视图=参与态）——归因：双态鉴权落地后视图即身份，模拟切换器违背真实语义；编辑入口（铅笔图标 → name/婚期编辑弹窗复用 S02 结构）为新增交互承载 E-SHR-04（原型无编辑入口——**新增组件条款**，复用既有 modal/token）
- COMP-SHR-S04 `GuestJoinGate`（**改造自**原型访客身份条 persona 选择器）：原型「I am + 成员下拉/Someone else 输入」→ **昵称输入 + Join 按钮**（**显式偏离④**：原型下拉选既有成员=免凭据冒名（演示装置），真实流凭 invite_token + 昵称换 guest JWT，决策 20.2；输入框/标签/布局 token 保真）；提交 → createGuestSession → guestSessionStore.save + 拉详情；409101 → 行内「该昵称已被使用，请换一个」；410101 → 失效提示卡（「邀请链接已失效，请向新娘索取新链接」）；401101 → 「链接无效」提示卡
- COMP-SHR-S05 `ShowroomItemCard`（copy-adapt）：商品图（← item.product.imageUrl）/名称/色点+色名+价格（currencyStore 展示换算，trading STORE-TRD-S04 复用）/Assigned to 徽标（members 反查 assignedItemId）/投票双按钮（guest 与 owner **均可投**——原型仅 guest 可投、owner 只读计数 → **显式偏离⑤**：契约 E-SHR-10「owner 与 guest 均可投」+ my_vote 高亮态按 API myVote）/留言折叠区（计数按钮 + 列表 nickname/createdAt/content + 输入框）/owner 删除按钮（→ removeItem，移除后成员回 unassigned 由 refetch 同步）/guest 视图 Order This Dress（→ FORM-SHR-S08）
- COMP-SHR-S06 `MembersTable`（copy-adapt 成员指派表，owner 视图）：Member/Assigned style & color/Status/Reminder 四列保真；指派下拉 ← room.items（label=product.name — color）；**email 字段新增**（**显式偏离⑥**：决策 20.5 指派需填提醒邮箱——下拉旁新增 email 输入（首次指派必出，已有值回显可改），契约 E-SHR-12 body.email 承载；新增输入复用既有 input token）；Status 徽章四态映射 assignStatus（Browsing=unassigned/Awaiting order=assigned/Reminded=reminded 徽章新增一态文案/Ordered=ordered——原型三态 +「Reminded」第四态为契约状态机补全，显式偏离⑦）；Send reminder 按钮 → 真发（FORM-SHR-S06，原型 simulated toast 升级）
- COMP-SHR-S07 `AddToShowroomModal`（copy-adapt components/showroom/add-to-showroom-modal.tsx）：色板选择/已有 Showroom 列表（← listShowrooms）/already-Saved 置灰态（本地比对改为提交后 409102 容错：modal 打开时无房内款式数据 → **提交 409102 视同 Saved 态**，toast「已在该协作空间中」——显式偏离⑧，归因 Summary 无 items）/新建 Create & Add（串联 E-SHR-01 + E-SHR-08）；未登录点击入口 → 跳登录（returnTo 回 PDP）
- COMP-SHR-S08 `InviteLinkBar`（copy-adapt 邀请条 + 扩展）：链接输入框（← `{origin}/showroom/{id}?invite={room.inviteToken}` 实时拼装）+ Copy Link（clipboard + Copied 反馈保真）+ **Reset link 按钮新增**（**显式偏离⑨**：契约 E-SHR-06 重置邀请需 UI 承载；btn-outline 同款式，二次确认「重置后旧链接与访客会话将立即失效」CP-071）
- COMP-SHR-S09 dye lot 提示条（copy-adapt F-071 金底提示条）：显示条件 原型 someoneOrdered（成员 hasOrdered）→ **改 room.items.some(dyeLotNotice)**（契约 items[].dye_lot_notice；文案保真「Order within 24h ... same dye lot」三语化）；命中款式卡片附加小角标（新增节点复用 gold token）
- COMP-SHR-S10 错误/空态：404101 通用「不存在或无权访问」页（复用原型 Showroom not found 结构）；401101/410101 guest 专属提示卡（S04 内）；列表/款式空态保真；5xx 通用错误态 + 重试

## 5. 表单交互（FORM-SHR）

- FORM-SHR-S01 创建/编辑 Showroom：name trim 必填 js_guard（按钮可点但空提交前端拦截红字）；422101 fields → 行内；成功关 modal + 列表/详情刷新
- FORM-SHR-S02 guest 加入：nickname trim 1..32 js_guard（超长计数红字）→ createGuestSession；409101 行内换昵称；410101/401101 提示卡（S04）；成功 → 存会话 + 进 guest 视图（toast「Welcome, {nickname}」新增文案节点）
- FORM-SHR-S03 投票：点击 like/dislike → 乐观高亮 → PUT 响应 {likeCount, dislikeCount, myVote} 覆盖（重复点击同值幂等——服务端覆盖语义，前端不做 toggle-off：契约无取消投票）；401101 → 清会话回加入态；guest 未加入时投票按钮即加入引导（原型「Enter a nickname first to vote」toast 保真）
- FORM-SHR-S04 留言：trim 空禁发（原型 js_guard 保真）；Enter 提交；成功 push 留言行（nickname + 时间）；422101 超长行内计数
- FORM-SHR-S05 指派：下拉选款式 + email 输入（V-SHR-022 前端预校验 email 格式；首次指派 email 空 → 行内提示「填写邮箱以便发送提醒」仍可提交——email 契约可选）→ POST assign → 行覆盖 + toast（原型「{name} assigned {style} · {color}」保真）；409103（ordered）→ toast「成员已下单，不可再指派」+ refetch；404102 → toast「款式已被移除」+ refetch
- FORM-SHR-S06 发送提醒：按钮显示条件 assignStatus ∈ {assigned, reminded} 且 email 非空（js_guard；409103 后端兜底 details.reason 二分文案：not_assigned→「请先指派款式」/email_missing→「请先填写成员邮箱」）→ POST remind → toast「提醒邮件已发送至 {email}」（原型 simulated 文案移除——真发升级，决策 20.5）+ Status 变 Reminded；reminded 态可重发（自环）
- FORM-SHR-S07 重置邀请链接：二次确认（CP-071 危险操作：旧链接与已加入访客的会话将失效）→ POST reset → InviteLinkBar 即时换新 + toast「邀请链接已重置」
- FORM-SHR-S08 guest 下单衔接（决策 20.2 闭环）：guest 视图 Order This Dress → 跳 PDP（`/product/{slug}`）；下单需登录时走既有登录流（returnTo）；**登录成功回调钩子**：检测 localStorage 存在 guest 会话（任意房）→ 对每房静默 `rejoin()`（带 store token 重放 guest-session → 后端 STEP-SHR-05 绑定 linked_customer_id）→ 此后该用户支付成功，order.paid 消费者可定位成员推进 ordered（EVT-SHR-003）；重放失败静默忽略（不阻塞登录主流程）
- FORM-SHR-S09 路由守卫：见 PAGE-SHR-S02 三态判定；guest 会话过期（expiresAt 过期或 401101）→ 自动 rejoin() 一次（缓存 inviteToken+nickname），仍失败 → 回加入态
- FORM-SHR-S10 婚期带入结算（F-077/决策 20.6，交叉引用 trading）：checkout wedding date 字段初值 selector `getDefaultWeddingDate()` = showroomListStore 最新创建房的 weddingDate（无房/无婚期 → 空）；实现归 trading 结算页（COMP-TRD-S02），本域提供 listShowrooms 数据源与 selector；E-SHR-04 改婚期后 selector 即时取新值（无缓存）
- FORM-SHR-S11 PDP/商品卡片入口：Add to Showroom 图标按钮（原型 PDP 同位）→ 未登录跳登录；登录 → 开 COMP-SHR-S07

## 6. 原型对照表（prototype_source + conformance）

| 真实文件（frontend/portal-store/） | prototype_source（hhspec/prototype/） | conformance |
|---|---|---|
| app/showroom/page.tsx（**新建**） | app/showroom/page.tsx | **copy-adapt**：布局/空态/卡片/删除确认零偏离；数据 useStore→API。显式偏离①卡片封面三图→占位（Summary 无 items）、②创建弹窗婚期改可选（契约 required 仅 name） |
| app/showroom/[id]/page.tsx（**新建**） | app/showroom/[id]/page.tsx | **copy-adapt**：壳结构保真；generateStaticParams/dynamicParams=false 移除 → force-dynamic（决策 22 静态导出残留清理，conformance 注记非视觉偏离） |
| components/showroom/showroom-detail.tsx（**新建**） | components/showroom/showroom-detail.tsx | **copy-adapt**：头部/邀请条/dye lot 条/款式网格/成员表/Toast 结构与 token 零偏离。显式偏离③视图切换按钮移除（is_owner 驱动）、④persona 下拉→昵称加入（guest JWT 真实语义）、⑤owner 可投票（契约双态）、⑥成员表新增 email 输入（决策 20.5）、⑦Status 新增 Reminded 四态、⑨邀请条新增 Reset link（契约 E-SHR-06 UI 承载） |
| components/showroom/add-to-showroom-modal.tsx（**新建**） | components/showroom/add-to-showroom-modal.tsx | **copy-adapt**：色板/房列表/新建态保真。显式偏离⑧Saved 置灰改 409102 提交容错 |
| components/showroom/guest-join-gate.tsx（**新建**） | —（改造自 showroom-detail 访客身份条） | **新增组件条款**：复用原型身份条布局与 input token；归因=契约 E-SHR-07 加入流必须有 UI 承载 |
| app/product/[slug]/page.tsx 等 PDP 切面 | app/product/[slug]/page.tsx（showroom 入口） | **layout-keep + 切面**：仅新增入口按钮挂 modal，不动 PDP 结构 |
| lib/api/showroom-api.ts / lib/stores/showroom-*.ts（**新建**） | —（store-provider mock 态） | 数据层新建；client.ts 仅加 authTokenOverride 选项（最小 diff） |

强对照红线复述：不改 tailwind token、不内联硬编码色值、新增 loading/error/empty 态复用既有组件风格（skeleton/EmptyState/toast 同源）；显式偏离 9 处全部标注归因（①~⑨），其中 ③④⑤ 为「原型演示装置 → 真实鉴权语义」类、⑥⑦⑨ 为「契约/决策新增能力承载」类、①②⑧ 为「契约 schema 约束」类。

## 7. 自检

- [x] 仅 portal-store 单端（本域无后台端点，显式声明 portal-admin 零改动）
- [x] 编号体系 PAGE-SHR-S01~S03、STORE-SHR-S01~S04、COMP-SHR-S01~S10、FORM-SHR-S01~S11，无重号
- [x] 13 端点前端消费全部映射到组件/store（E-SHR-01~13 ← §1 API 模块 + §4/§5 落点）
- [x] **guest token 前端存储与路由守卫定稿**（STORE-SHR-S03 localStorage 分房键 + PAGE-SHR-S02 三态守卫 + FORM-SHR-S09 过期 rejoin + FORM-SHR-S08 登录绑定回填重放）
- [x] 邀请链接形态/复制/重置/失效呈现闭环（COMP-SHR-S08 + FORM-SHR-S07 + 410101/401101 专属提示）
- [x] 原型对照表含 prototype_source + conformance；显式偏离 9 处全部归因；渲染策略与决策 22（force-dynamic、不缓存协作数据）一致
- [x] 错误呈现按 error-strategy 约定（401101 重开链接提示 / 403101·403102 权限提示 / 404 防探测通用页 / 409101 换昵称 / 409102 Saved 容错 / 409103 details 二分 / 410101 专属提示 / 422 inline / 5xx 重试）
