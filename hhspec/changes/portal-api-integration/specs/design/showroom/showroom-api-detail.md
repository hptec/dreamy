# showroom API 详细设计（L2）

> 角色: l2_api_designer ｜ change: portal-api-integration ｜ domain: showroom
> 方法论：每端点四部分 — 入参验证(V-SHR-NNN，全域连续唯一) / 业务步骤(STEP-SHR-NN，每端点独立编号段，溯源以「端点编号 E-SHR-NN + STEP-SHR-NN」组合唯一) / 出参构造 / 错误码映射。
> 来源权威：api-contracts/showroom-api.openapi.yml v1.1.0（13 端点）+ data-flow.md（FLOW-P11/P12 + MQ 拓扑 q.showroom + 缓存矩阵「Showroom 全部不缓存」）+ error-strategy.md（showroom 域段 1，11 码 + L2 设计要求 2 guest 旁路）+ er-diagram.yml（Showroom/ShowroomItem/ShowroomMember/ShowroomVote/ShowroomComment）+ state-machine.yml（showroom_member_assignment）+ decision.md 决策 20（七子决策）/23/3/11/16/BE-DIM-4/6/8。
> 伪代码级，不绑定 Spring 语法。线上响应统一 huihao R 包络 `{code,message,data}`；本域列表全量返回不分页（契约口径：单用户 Showroom 数量小；如后续分页采用 huihao.page.Paginated，与 catalog/trading/review 同形状）；JSON 字段一律 snake_case。

## 0. 全局横切（所有端点适用）

- **鉴权过滤器（双态，决策 20.2）**：全部端点在 `/api/store/showrooms*` 前缀下，经 StoreJwtFilter（STORE_JWT_SECRET）。三类身份：
  - **匿名**：仅 E-SHR-07 guest-session（配置化公开路径白名单，见 0.1）。
  - **owner（StoreBearerAuth）**：登录消费者，`customer_id = JWT subject`（BE-DIM-6）；owner 写路径一律以 `owner_id = subject` 过滤点查，**跨用户访问一律 404 `404101`（防探测，bs-640）**，不返回 403。
  - **guest（ShowroomGuestAuth）**：受限 guest JWT，由 StoreJwtFilter guest 旁路（见 0.2）注入受限主体；仅可读绑定 Showroom + 对其款式投票/留言。
- **双态鉴权矩阵（13 端点 × 5 身份，过滤器层 + 服务层合并裁决）**：

| 端点 | 匿名 | guest(绑定本房) | guest(他房 token) | owner | 其他登录用户 |
|---|---|---|---|---|---|
| E-SHR-01 POST /showrooms | 401 `40100` | 403 `403102` | 403 `403102` | 201 | 201（以己为 owner） |
| E-SHR-02 GET /showrooms | 401 `40100` | 403 `403102` | 403 `403102` | 200（仅自己的） | 200（仅自己的） |
| E-SHR-03 GET /showrooms/{id} | 401 `40100` | 200 | 403 `403102` | 200 | 404 `404101` |
| E-SHR-04 PUT /showrooms/{id} | 401 `40100` | 403 `403102` | 403 `403102` | 200 | 404 `404101` |
| E-SHR-05 DELETE /showrooms/{id} | 401 `40100` | 403 `403102` | 403 `403102` | 204 | 404 `404101` |
| E-SHR-06 POST /showrooms/{id}/invite/reset | 401 `40100` | 403 `403102` | 403 `403102` | 200 | 404 `404101` |
| E-SHR-07 POST /showrooms/guest-session | 公开（白名单） | 公开 | 公开 | 公开（可选注入，绑定回填） | 公开（同左） |
| E-SHR-08 POST /showrooms/{id}/items | 401 `40100` | 403 `403102` | 403 `403102` | 201 | 404 `404101` |
| E-SHR-09 DELETE /showrooms/{id}/items/{itemId} | 401 `40100` | 403 `403102` | 403 `403102` | 204 | 404 `404101` |
| E-SHR-10 PUT .../items/{itemId}/vote | 401 `40100` | 200 | 403 `403102` | 200 | 404 `404101` |
| E-SHR-11 POST .../items/{itemId}/comments | 401 `40100` | 201 | 403 `403102` | 201 | 404 `404101` |
| E-SHR-12 POST .../members/{memberId}/assign | 401 `40100` | 403 `403102` | 403 `403102` | 200 | 404 `404101` |
| E-SHR-13 POST .../members/{memberId}/remind | 401 `40100` | 403 `403102` | 403 `403102` | 200 | 404 `404101` |

  guest 列的 403 `403102` 由过滤器层 guest 操作白名单拦截（0.2-b）；「其他登录用户」列的 404 `404101` 由服务层 owner 强隔离点查产生（CV-SHR-007）。非 owner 触发 owner 专属操作时**不使用** `403101`——`403101 NOT_SHOWROOM_OWNER` 的唯一触发面是「能读到资源但无管理权」的身份，即 **guest 主体经服务层进入 owner 专属分支**的兜底（过滤器白名单已在外层拦截为 403102，403101 为服务层双保险：若未来 guest 白名单扩面，owner 校验仍兜底）。两码并存口径：过滤器=403102（越权访问面），服务层 owner guard=403101（操作权限面）。
- **i18n**：E-SHR-03 的内嵌商品卡片文案按 `locale` query（en/es/fr，缺省 en，决策 13 翻译回退，经 CatalogSnapshotPort 已解析输出）；错误 message 按请求 locale 返回（前端按 code 映射 next-intl 字典，决策 27）。协作数据本身（昵称/留言）不翻译。
- **审计**：本域**无后台端点**，全部为消费端操作，不写 operation_log（与 review store 提交端点同口径，BE-DIM-7 仅约束后台写操作）。
- **缓存（BE-DIM-8，契约缓存标注）**：协作数据强一致，**13 端点全部不缓存**——不建 JetCache key、响应 `Cache-Control: private, no-store`（CACHE-SHR-001 显式声明，见 showroom-data-detail §7）。
- **限流**：guest-session / 投票 / 留言等公开或高频写靠 Cloudflare WAF（决策 11），后端不实现限流。
- **422 字段级错误结构**（error-strategy L2 要求 1）：校验失败 → 422 `422101`，`details` 形如 `{ "fields": { "<field>": "<reason_key>" } }`（线上装入 R.data）；reason_key 由 portal-store next-intl 字典渲染。
- **跨域端口（决策 3，进程内直调防腐层，禁止跨域直查表）**：
  - `CatalogSnapshotPort`（catalog 域实现，本域消费）：`getProductCards(productIds, locale) -> List<ProductCardBrief{id, slug, name(已按 locale 回退解析), price_usd, image_url, custom_size_available, lead_time_days, status}>` —— 商品存在性/published 校验（不存在或未发布 → 透传 404 `404501`，review/trading 同先例，契约 E-SHR-08 404 注记「商品不存在」的落地码）+ ShowroomItem.product 内嵌卡片装配（单次批量防 N+1）。
  - `IdentityQueryPort`（identity 域既有）：`getUserName(customerId) -> String` —— owner 首次互动自动建 member 的昵称来源（E-SHR-10/11 STEP）。
  - `DyeLotPort`（**本域提供**，trading 域消费，trading-api-detail §0 已声明）：`hintProductIds(customerId, productIds) -> List<Long>`（决策 20.4，实现见 showroom-data-detail §8.2）。
- **错误码边界**：本域只产出 showroom 域段 1 的 11 码（401101/403101/403102/404101/404102/404103/409101/409102/409103/410101/422101）+ 透传 catalog `404501`（商品引用校验，review/trading 先例）+ identity 复用码（40100/50000/50001）。

### 0.1 StoreJwtFilter 公开路径白名单（本域登记条目，error-strategy L2 要求 2 第一项）

白名单为配置化 pattern 列表（`dreamy.security.store-public-paths`，AntPath 风格，七域共用同一机制），**条目形式采用 review 域 0.1 定稿的 method-aware `METHOD:pattern`**（无 method 前缀缺省匹配全部 method，向后兼容 catalog/trading 既有条目）。**showroom 域登记 1 条**：

| 白名单条目 | 覆盖端点 | 不放行 |
|---|---|---|
| `POST:/api/store/showrooms/guest-session` | E-SHR-07 访客换发 guest JWT | `/api/store/showrooms*` 其余全部 method/路径（含 `GET /api/store/showrooms`——列表仍强制鉴权） |

白名单路径放行时**principal 可选注入**（catalog §0 既有口径）：请求携带可解析的有效 store JWT → 注入 store principal（E-SHR-07 据此做 linked_customer_id 绑定回填，STEP-SHR-05）；解析失败/无 token → 匿名放行不报错。

### 0.2 StoreJwtFilter guest token 旁路设计（error-strategy L2 要求 2 第二项，与 review method-aware 白名单兼容）

**现状基线**（backend/src/main/java/com/dreamy/identity/security/StoreJwtFilter.java）：对 `/api/store/*` 强制解析 store JWT（typ=store）+ SessionValidator 会话校验，公开路径目前为硬编码 `isPublic()`。本域两项扩展与 review 域 REV-IMPL-FILTER（method-aware 白名单配置化）落在**同一次过滤器升级**：

1. **guest JWT 签发（复用既有 JWT 基建）**：`JwtTokenProvider` 新增 `issueShowroomGuestToken(memberId, showroomId, inviteVersion)` —— 以 **storeKey（STORE_JWT_SECRET）** 签名（同基建复用，不新增密钥；与 CP-020 双密钥隔离不冲突——guest 属消费端体系，按 `typ` claim 区分）；claims：`sub=<member_id>`、`jti=UUID`、`typ=guest`（AuthPrincipal 新增 `TYPE_GUEST`）、`showroom_id`、`member_id`、`inv_ver`（签发时 Showroom.invite_version 快照）；TTL 配置项 `dreamy.showroom.guest-token-ttl-seconds`，缺省 86400（24h，短期）；**无 refresh、不落 user_session**（失效由 inv_ver 等值校验与资源存在性承担，不走 SessionValidator）。
2. **过滤器分支（doFilterInternal 重构为四段裁决）**：
   - ① 命中 method-aware 公开白名单 → 放行（principal 可选注入，0.1）。
   - ② Bearer 解析（storeKey）成功且 `typ=store` → 既有链路（SessionValidator + store principal）不变。
   - ③ Bearer 解析成功且 `typ=guest`：
     - a. 路径不在 guest 旁路作用域 `/api/store/showrooms/**` → 401 `40100`（guest token 用于其他 store 端点 = 跨用途误用，与跨端误用同口径 CP-021）；
     - b. **guest 操作白名单（method-aware，配置项 `dreamy.security.showroom-guest-paths`，条目形式与 0.1 完全一致）**：`GET:/api/store/showrooms/{id}`、`PUT:/api/store/showrooms/{id}/items/*/vote`、`POST:/api/store/showrooms/{id}/items/*/comments` 三条；不匹配（含 POST/PUT/DELETE showrooms、items 增删、members、invite/reset、GET 列表）→ 403 `403102`；
     - c. 路径 `{id}` ≠ claims.showroom_id → 403 `403102`（guest 越权访问非绑定 Showroom）；
     - d. **ShowroomGuestValidator**：按 PK 点查 showroom 行（轻量主键查询，不缓存——协作数据口径）；行不存在（Showroom 已删除）或 `invite_version ≠ claims.inv_ver`（邀请链接已重置）→ 401 `401101`（契约「旧 guest JWT 即时失效」的落地机制）；
     - e. 注入 **guest 受限主体**：`AuthPrincipal(subject=member_id, type=guest, ...)` + 旁路上下文 `GuestContext{showroomId, memberId}`（服务层据此取 my_member 身份，**不查 nickname、不信任请求体身份字段**）。
   - ④ 解析异常：`ExpiredJwtException`（可读未验签 claims）且 claims.typ=guest → 401 `401101`（guest 过期专属码，前端引导重开邀请链接）；其余（签名非法/无 token/typ 缺失）→ 401 `40100`（既有口径）。
3. **与 review method-aware 白名单的兼容声明**：0.1 公开白名单与 0.2-b guest 操作白名单共用同一 `METHOD:pattern` 匹配器实现（一处实现两处配置）；guest 旁路在白名单判定**之后**、store 解析分支**并列**位置插入，不改变 catalog/trading/review 已登记条目的匹配语义；review TC-REV-054 的「白名单未过度放行」回归对本域同样成立（TC-SHR 网络边界组互补断言）。
4. **日志脱敏**（error-strategy 脱敏规则）：invite_token / guest JWT 原文一律 `[REDACTED]`，日志仅记 showroom_id + member_id。

---

## 1. SHOWROOMS（F-066/F-068）

### E-SHR-01 createShowroom — POST /api/store/showrooms （FLOW-P12, F-066）

**StoreBearerAuth**；owner_id = JWT subject。不缓存。

**入参**: body ShowroomUpsert `{ name!, wedding_date? }`
- V-SHR-001 name 必填，trim 后长度 1..64（空/超长 → 422 `422101` fields.name=blank|too_long；bs-353/535）
- V-SHR-002 wedding_date 可选，ISO `yyyy-MM-dd` 合法日期（格式非法 → 422 `422101` fields.wedding_date=invalid_date；不限制过去日期——原型创建弹窗无此限制，保真）

**业务步骤（单事务 TX-SHR-001）**:
- STEP-SHR-01 服务端生成 `invite_token = UUID.randomUUID()`（不可猜，决策 20.2）、`invite_version = 1`
- STEP-SHR-02 INSERT showroom(owner_id=subject, name, wedding_date, invite_token, invite_version)
- STEP-SHR-03 装配 ShowroomDetail（owner 视图：含 invite_token；items/members 空数组；is_owner=true；my_member_id 不返回——owner 尚未互动建 member）；不发 MQ、不写审计、无缓存副作用

**出参**: 201 ShowroomDetail（MAP-SHR-002）
**错误映射**: 401 `40100` / 422 `422101` / 500 `50000`,`50001`

### E-SHR-02 listShowrooms — GET /api/store/showrooms

**StoreBearerAuth**。不缓存（缓存矩阵「Showroom 全部不缓存」）。

**入参**: 无（不分页，契约口径全量返回）

**业务步骤**:
- STEP-SHR-01 `SELECT showroom WHERE owner_id=:subject ORDER BY created_at DESC`（RM-SHR-004，仅返回当前用户创建的——owner 强隔离的读侧形态）
- STEP-SHR-02 批量派生 item_count / member_count（RM-SHR-009 两条 GROUP BY IN 批查，防 N+1）
- STEP-SHR-03 装配 `{ items: ShowroomSummary[] }`（MAP-SHR-001；**不含 invite_token**——Summary schema 无此字段）

**出参**: 200 `{ items: [ShowroomSummary] }`
**错误映射**: 401 `40100` / 500 `50000`

### E-SHR-03 getShowroom — GET /api/store/showrooms/{id} （F-068/F-069，双态）

**StoreBearerAuth 或 ShowroomGuestAuth**（guest 经 0.2 旁路注入，过滤器已保证 {id}=claims.showroom_id）。不缓存。

**入参**: path id；query locale
- V-SHR-003 id 正整数 int64（非法视同不存在 → 404 `404101`）
- V-SHR-004 locale ∈ {en, es, fr} 缺省 en（枚举外 → 422 `422101` fields.locale=invalid_enum）

**业务步骤**:
- STEP-SHR-01 身份分流：store 主体 → `findByIdAndOwner(id, subject)`（RM-SHR-002），未命中 → 404 `404101`（跨用户防探测，CV-SHR-007）；guest 主体 → `findById(GuestContext.showroomId)`（RM-SHR-003，过滤器已校验绑定与版本）
- STEP-SHR-02 `listByShowroom(id)` 取 items（RM-SHR-021）+ members（RM-SHR-033）
- STEP-SHR-03 `CatalogSnapshotPort.getProductCards(productIds, locale)` 批量装配内嵌商品卡片（ProductRef，文案已按 locale 解析）；端口返回缺失的 product_id（商品被物理清除的脏引用）→ 该 item 不输出并记告警日志（容忍降级，不 5xx）
- STEP-SHR-04 票数聚合：`aggregateByItems(itemIds)` 单条 GROUP BY（RM-SHR-051，NP-SHR-002）→ like_count/dislike_count
- STEP-SHR-05 当前身份 member 解析：guest → GuestContext.memberId；owner → `findByShowroomAndLinkedCustomer(id, subject)`（RM-SHR-034，可能为空——未互动）→ my_member_id（空则字段省略）+ `listVotesByMember(memberId, itemIds)` → 各 item.my_vote（RM-SHR-052；无 member 则全部省略）
- STEP-SHR-06 留言批查：`listByItems(itemIds)`（RM-SHR-061，联 member 派生 nickname，单次 IN 防 N+1）→ items[].comments（created_at ASC）
- STEP-SHR-07 dye_lot_notice 派生（决策 20.4）：`item.last_ordered_at > now - dyeLotWindow(24h 配置)` → true（CV-SHR-011，数据来源见 showroom-data-detail EVT-SHR-003）
- STEP-SHR-08 视图裁剪（MAP-SHR-002/003）：owner 视图含 invite_token + members[].email/linked_customer_id；guest 视图**不含 invite_token，members[] 不含 email/linked_customer_id**（契约「仅 owner 返回」）；is_owner 按主体类型赋值

**出参**: 200 ShowroomDetail
**错误映射**: 401 `40100`,`401101`(guest 过期/重置，过滤器层) / 403 `403102`(guest 越权，过滤器层) / 404 `404101` / 422 `422101` / 500 `50000`

### E-SHR-04 updateShowroom — PUT /api/store/showrooms/{id} （改名/婚期，F-077 联动）

**StoreBearerAuth，仅 owner**。

**入参**: path id；body ShowroomUpsert
- V-SHR-005 id（同 V-SHR-003 口径）
- V-SHR-006 name（同 V-SHR-001 口径）
- V-SHR-007 wedding_date（同 V-SHR-002 口径）

**业务步骤（单事务 TX-SHR-002）**:
- STEP-SHR-01 `findByIdAndOwner(id, subject)` 未命中 → 404 `404101`（跨用户防探测；guest 主体已被过滤器 403102 拦截，服务层 owner guard 403101 为双保险——0 节两码口径）
- STEP-SHR-02 `UPDATE showroom SET name=:name, wedding_date=:weddingDate WHERE id=?`（RM-SHR-005；wedding_date 传 null 即清空——PUT 全量覆盖语义）
- STEP-SHR-03 婚期变更后结算自动带入值随之更新（F-077）——无后端联动动作：结算带入为前端读取行为（showroom-frontend-detail FORM-SHR-S10），本步骤仅声明无失效链
- STEP-SHR-04 重新装配 ShowroomDetail（同 E-SHR-03 STEP-SHR-02~08 owner 视图）

**出参**: 200 ShowroomDetail
**错误映射**: 401 `40100` / 403 `403101`(服务层兜底) / 404 `404101` / 422 `422101` / 500 `50000`,`50001`

### E-SHR-05 deleteShowroom — DELETE /api/store/showrooms/{id}

**StoreBearerAuth，仅 owner**。

**入参**: path id
- V-SHR-008 id（同 V-SHR-003 口径）

**业务步骤（单事务 TX-SHR-003，级联）**:
- STEP-SHR-01 `findByIdAndOwner(id, subject)` 未命中 → 404 `404101`
- STEP-SHR-02 级联删除（无物理外键，事务内显式逐表，CP-010）：showroom_comment（经本房 item ids，RM-SHR-062）→ showroom_vote（同，RM-SHR-053）→ showroom_item（RM-SHR-023 deleteByShowroom）→ showroom_member（RM-SHR-042）→ showroom（RM-SHR-008）
- STEP-SHR-03 已签发 guest JWT 即时失效——无主动动作：ShowroomGuestValidator 点查无行 → 后续 guest 请求 401 `401101`（0.2-d 机制天然达成，契约「即时失效」闭环）

**出参**: 204
**错误映射**: 401 `40100` / 403 `403101`(兜底) / 404 `404101` / 500 `50000`,`50001`

### E-SHR-06 resetShowroomInvite — POST /api/store/showrooms/{id}/invite/reset （决策 20.2 级联失效核心）

**StoreBearerAuth，仅 owner**。

**入参**: path id
- V-SHR-009 id（同 V-SHR-003 口径）

**业务步骤（单事务 TX-SHR-004）**:
- STEP-SHR-01 `findByIdAndOwner(id, subject)` 未命中 → 404 `404101`
- STEP-SHR-02 生成新 `UUID` → `UPDATE showroom SET invite_token=:new, invite_version=invite_version+1 WHERE id=?`（RM-SHR-006，单语句原子；invite_version 单调递增 CV-SHR-008）
- STEP-SHR-03 级联失效闭环（设计核心）：①旧 invite_token 不再命中 `findByInviteToken` → 后续 guest-session 提交旧 token：token 查无行且无法区分「从未存在」与「已重置」——**重置识别机制**：guest-session 对查无行的 token 一律 401 `401101`；对**命中行但 token 不等**不可能发生（uk 点查）；因此 `410101 INVITE_TOKEN_REVOKED` 的判定依据为**前端携带的 showroom_id 旁证**不可靠，定稿为：token 查无行 → 401 `401101`（无效）；**携带旧 token 且系统能证明其曾属于某房**的场景由 invite_token 历史不留存而不可证——契约 410101 的触发面收敛为 E-SHR-07 STEP-SHR-02 的**版本化软识别**：invite_token 列保留**当前值**，重置时旧值写入 `invite_token_prev`（单代保留，设计派生列，见 data-detail §1.2）；guest-session 命中 prev → 410 `410101`（「邀请链接已被重置作废」精确语义），命中当前值 → 正常，两者皆不命中 → 401 `401101`
  ②已签发旧 guest JWT：inv_ver < 新 invite_version → 0.2-d 校验不等 → 401 `401101`（即时失效，无需撤销存储）
- STEP-SHR-04 不发 MQ、不写审计

**出参**: 200 `{ invite_token }`（新 token，仅 owner 可见）
**错误映射**: 401 `40100` / 403 `403101`(兜底) / 404 `404101` / 500 `50000`,`50001`

---

## 2. GUEST SESSION（决策 20.2）

### E-SHR-07 createShowroomGuestSession — POST /api/store/showrooms/guest-session （F-068 免注册参与）

**公开端点**：白名单 `POST:/api/store/showrooms/guest-session`（0.1）；principal 可选注入（绑定回填用）。WAF 限流（决策 11）。

**入参**: body `{ invite_token!, nickname! }`
- V-SHR-010 invite_token 必填 ≤64（缺/超长 → 422 `422101` fields.invite_token=required|too_long；bs-355/536）
- V-SHR-011 nickname 必填，trim 后长度 1..32（空/超长 → 422 `422101` fields.nickname=blank|too_long；bs-362/538）

**业务步骤（单事务 TX-SHR-005）**:
- STEP-SHR-01 `findByInviteToken(invite_token)`（RM-SHR-007，uk 点查当前值）命中 → 取 showroom，转 STEP-SHR-03
- STEP-SHR-02 当前值未命中 → 查 `invite_token_prev`（RM-SHR-007b）：命中 → 410 `410101` INVITE_TOKEN_REVOKED（链接已被重置，引导向新娘索取新链接）；仍未命中 → 401 `401101` GUEST_TOKEN_INVALID（token 无效，防探测不区分更多细节）
- STEP-SHR-03 `findByShowroomAndNickname(showroom.id, trimmedNickname)`（RM-SHR-030）：
  - **命中且 linked_customer_id 为空** → 复用该 member 身份（契约「再次进入」）
  - **命中且 linked_customer_id 非空**：若当前请求可选注入的 store principal.subject == linked_customer_id → 复用（本人回访）；否则 → 409 `409101` NICKNAME_TAKEN（**受保护昵称**：已绑定注册客户的去重身份不可被匿名复用，防身份冒用——契约 guest-session 响应未列 409，但域码表 409101 触发注记「昵称唯一作投票/留言去重身份」的 L2 定稿落点即此 + STEP-SHR-04 并发兜底；前端引导换昵称）
  - **未命中** → INSERT showroom_member(showroom_id, nickname=trimmed, assign_status='unassigned')（RM-SHR-031）
- STEP-SHR-04 并发兜底：INSERT 命中 uk_sm_room_nickname 唯一索引冲突（同昵称并发双提交，bs-362 双保险）→ 回读该行按 STEP-SHR-03 复用/409101 规则重新裁决（CV-SHR-004）
- STEP-SHR-05 绑定回填（决策 20.2「访客下单登录后回填」的承载机制，契约 13 端点内闭环）：白名单可选注入存在有效 store principal 且 member.linked_customer_id 为空 → `bindCustomer(member.id, subject)`（RM-SHR-041，条件更新幂等）；已绑定同值 → 跳过；前端在访客登录后静默重放本端点完成绑定（showroom-frontend-detail FORM-SHR-S08）
- STEP-SHR-06 签发 guest JWT：`issueShowroomGuestToken(member.id, showroom.id, showroom.invite_version)`（0.2-1；TTL 24h 配置）
- STEP-SHR-07 装配 GuestSession`{ guest_token, expires_at, showroom_id, member }`（member 按 guest 视图裁剪 MAP-SHR-006——本人会话含自身 email 与 assign 状态，**不含 linked_customer_id**）

**出参**: 200 GuestSession
**错误映射**: 401 `401101` / 409 `409101` / 410 `410101` / 422 `422101` / 500 `50000`,`50001`

---

## 3. ITEMS（F-067）

### E-SHR-08 addShowroomItem — POST /api/store/showrooms/{id}/items （Add to Showroom）

**StoreBearerAuth，仅 owner**。

**入参**: path id；body `{ product_id!, color? }`
- V-SHR-012 id（同 V-SHR-003 口径）
- V-SHR-013 product_id 必填正整数 int64，且经 `CatalogSnapshotPort.getProductCards([id], en)` 校验存在且 published（缺/非法 → 422 `422101` fields.product_id；不存在或未发布 → 404 `404501` 透传，bs-722）
- V-SHR-014 color 可选 ≤64，trim（超长 → 422 `422101` fields.color=too_long；bs-537/359）；落库归一化：未提供/trim 空 → 存空串 `''`（uk 三元唯一生效前提，CV-SHR-003）

**业务步骤（单事务 TX-SHR-006）**:
- STEP-SHR-01 `findByIdAndOwner(id, subject)` 未命中 → 404 `404101`（bs-723 showroom 不存在同落点）
- STEP-SHR-02 INSERT showroom_item(showroom_id, product_id, color=normalized)（RM-SHR-020）；uk_si_room_product_color 冲突 → 409 `409102` ITEM_ALREADY_EXISTS（同房 product_id+color 唯一，js_guard 后端兜底——原型 modal 已置灰 Saved 态）
- STEP-SHR-03 装配 ShowroomItem DTO（MAP-SHR-004：product 卡片经端口、like/dislike=0、my_vote 省略、comments 空、dye_lot_notice 按 last_ordered_at 判定——新插入行为 NULL 即 false）

**出参**: 201 ShowroomItem
**错误映射**: 401 `40100` / 403 `403101`(兜底) / 404 `404101`,`404501`(透传) / 409 `409102` / 422 `422101` / 500 `50000`,`50001`

### E-SHR-09 removeShowroomItem — DELETE /api/store/showrooms/{id}/items/{itemId}

**StoreBearerAuth，仅 owner**。

**入参**: path id + itemId
- V-SHR-015 id/itemId 正整数 int64（非法视同不存在 → 404 `404101`/`404102`）

**业务步骤（单事务 TX-SHR-007）**:
- STEP-SHR-01 `findByIdAndOwner(id, subject)` 未命中 → 404 `404101`
- STEP-SHR-02 `findByIdAndShowroom(itemId, id)`（RM-SHR-022 归属校验）未命中 → 404 `404102` SHOWROOM_ITEM_NOT_FOUND
- STEP-SHR-03 被指派成员回退（契约「对应成员回到 unassigned」）：`UPDATE showroom_member SET assigned_item_id=NULL, assign_status='unassigned' WHERE assigned_item_id=:itemId AND assign_status IN ('assigned','reminded')`（RM-SHR-039）；**ordered 成员**：`UPDATE ... SET assigned_item_id=NULL WHERE assigned_item_id=:itemId AND assign_status='ordered'`（RM-SHR-040，保持 ordered 终态——已购事实由订单承载，状态机无 ordered 出边，设计定稿：仅清悬挂引用不回退状态）
- STEP-SHR-04 级联删除子数据：deleteByItem(vote)（RM-SHR-053）+ deleteByItem(comment)（RM-SHR-062）+ deleteById(item)（RM-SHR-023）

**出参**: 204
**错误映射**: 401 `40100` / 403 `403101`(兜底) / 404 `404101`,`404102` / 500 `50000`,`50001`

---

## 4. VOTES & COMMENTS（F-069，双态）

### E-SHR-10 voteShowroomItem — PUT /api/store/showrooms/{id}/items/{itemId}/vote （PUT 幂等去重）

**StoreBearerAuth 或 ShowroomGuestAuth**。

**入参**: path id + itemId；body `{ vote! }`
- V-SHR-016 id/itemId（同 V-SHR-015 口径，404 `404101`/`404102`）
- V-SHR-017 vote 必填 ∈ {like, dislike}（枚举外 → 422 `422101` fields.vote=invalid_enum；bs-541/370）

**业务步骤（单事务 TX-SHR-008）**:
- STEP-SHR-01 身份分流取 showroom：guest → GuestContext.showroomId（过滤器已校验绑定）；owner → `findByIdAndOwner(id, subject)` 未命中 → 404 `404101`（其他登录用户防探测）
- STEP-SHR-02 `findByIdAndShowroom(itemId, id)` 未命中 → 404 `404102`
- STEP-SHR-03 投票身份解析：guest → GuestContext.memberId；owner → `findByShowroomAndLinkedCustomer(id, subject)`，**为空则自动建 member**（契约 my_member_id「owner 首次互动时自动建 member」）：nickname = `IdentityQueryPort.getUserName(subject)` trim 截断 32；uk 冲突（昵称已被访客占用）→ 追加后缀 `#` + subject 末 4 位再截断 ≤32 重插一次（确定性规则，CV-SHR-004 注记）；linked_customer_id=subject、assign_status='unassigned'（RM-SHR-031）
- STEP-SHR-04 **UPSERT 幂等去重**（PUT 语义，js_guard「重复投票覆盖原票」）：`INSERT showroom_vote(showroom_item_id, member_id, vote) ON DUPLICATE KEY UPDATE vote=:vote`（RM-SHR-050，uk_sv_member_item 承载 member_id+showroom_item_id 唯一；同值重放零变更、改值覆盖，天然幂等可重试）
- STEP-SHR-05 实时聚合回读：`aggregateByItems([itemId])` + my_vote=:vote → 响应（不缓存，FLOW-P12「实时聚合」）

**出参**: 200 `{ like_count, dislike_count, my_vote }`
**错误映射**: 401 `40100`,`401101` / 403 `403102`(过滤器层) / 404 `404101`,`404102` / 422 `422101` / 500 `50000`,`50001`

### E-SHR-11 commentShowroomItem — POST /api/store/showrooms/{id}/items/{itemId}/comments

**StoreBearerAuth 或 ShowroomGuestAuth**。WAF 限流。

**入参**: path id + itemId；body `{ content! }`
- V-SHR-018 id/itemId（同 V-SHR-015 口径）
- V-SHR-019 content 必填，trim 后长度 1..500（空/超长 → 422 `422101` fields.content=blank|too_long；bs-374/542）

**业务步骤（单事务 TX-SHR-009）**:
- STEP-SHR-01~03 同 E-SHR-10 STEP-SHR-01~03（身份分流 / item 归属 / member 解析与 owner 自动建 member）
- STEP-SHR-04 INSERT showroom_comment(showroom_item_id, member_id, content=trimmed)（RM-SHR-060；member_id 一律取鉴权主体解析结果，**不接收请求体身份字段**——bs-728/730 不可达路径由此保证）
- STEP-SHR-05 装配 ShowroomComment（MAP-SHR-005：nickname 联 member 派生、created_at 服务端生成）

**出参**: 201 ShowroomComment
**错误映射**: 401 `40100`,`401101` / 403 `403102`(过滤器层) / 404 `404101`,`404102` / 422 `422101` / 500 `50000`,`50001`

---

## 5. MEMBERS（F-070/F-071）

### E-SHR-12 assignShowroomMember — POST /api/store/showrooms/{id}/members/{memberId}/assign （决策 20.5）

**StoreBearerAuth，仅 owner**。

**入参**: path id + memberId；body `{ assigned_item_id!, email? }`
- V-SHR-020 id/memberId 正整数 int64（非法视同不存在 → 404 `404101`/`404103`）
- V-SHR-021 assigned_item_id 必填正整数 int64，且 `findByIdAndShowroom(assigned_item_id, id)` 必属本 showroom（缺/非法 → 422 `422101` fields.assigned_item_id；不属/不存在 → 404 `404102`，state-machine assign guard「assignedItemId 必须属于本 showroom」bs-832 落点；bs-726）
- V-SHR-022 email 可选，RFC 5322 格式 + ≤254（非法/超长 → 422 `422101` fields.email=invalid_format|too_long；bs-539/363）

**业务步骤（单事务 TX-SHR-010）**:
- STEP-SHR-01 `findByIdAndOwner(id, subject)` 未命中 → 404 `404101`（state-machine guard「仅 owner 可指派」）
- STEP-SHR-02 `findByIdAndShowroom(memberId, id)`（RM-SHR-032 归属校验）未命中 → 404 `404103` SHOWROOM_MEMBER_NOT_FOUND
- STEP-SHR-03 CAS 状态机推进（showroom_member_assignment：assign/reassign，unassigned|assigned|reminded → assigned，**重新指派允许覆盖、改派后提醒状态重置**）：`UPDATE showroom_member SET assigned_item_id=:itemId, email=COALESCE(:email, email), assign_status='assigned' WHERE id=:memberId AND assign_status IN ('unassigned','assigned','reminded')`（RM-SHR-035）；affected=0 → 409 `409103` MEMBER_STATE_INVALID（ordered 后不可再指派，details `{ "assign_status": "ordered" }`；并发 assign×2/assign+remind 互斥由 CAS 仲裁，bs-602/603/604）；email 未提供时保留原值（COALESCE），提供时覆盖
- STEP-SHR-04 邀请/指派通知邮件（决策 20.5）：**本次请求提供了 email 且（成员原 email 为空或与新值不同）** → 事务提交后 publish MQ `showroom.invite`（EVT-SHR-001，payload 含 invite_url——访客凭链接进房查看指派；MailRecord.type=showroom_invite，FLOW-P11）；email 未变更的纯改派不重发（防骚扰）；publish 失败不回滚（EC-SHR-002）
- STEP-SHR-05 回读装配 ShowroomMember（owner 视图 MAP-SHR-006：含 email/linked_customer_id）

**出参**: 200 ShowroomMember
**错误映射**: 401 `40100` / 403 `403101`(兜底) / 404 `404101`,`404102`,`404103` / 409 `409103` / 422 `422101` / 500 `50000`,`50001`

### E-SHR-13 remindShowroomMember — POST /api/store/showrooms/{id}/members/{memberId}/remind （F-070 真发邮件）

**StoreBearerAuth，仅 owner**。

**入参**: path id + memberId
- V-SHR-023 id/memberId（同 V-SHR-020 口径）

**业务步骤（单事务 TX-SHR-011）**:
- STEP-SHR-01 `findByIdAndOwner(id, subject)` 未命中 → 404 `404101`
- STEP-SHR-02 `findByIdAndShowroom(memberId, id)` 未命中 → 404 `404103`
- STEP-SHR-03 前置 guard（契约「assign_status=assigned 或 reminded 且成员 email 已填」）：CAS `UPDATE showroom_member SET assign_status='reminded' WHERE id=:memberId AND assign_status IN ('assigned','reminded') AND email IS NOT NULL`（RM-SHR-036）；affected=0 → 409 `409103`，details 区分 `{ "reason": "not_assigned" | "email_missing", "assign_status": <当前值> }`（契约「details 说明」；bs-835 email 空 guard 落点）。**状态口径定稿**：state-machine 仅列 assigned→reminded，契约 v1.1.0 明示 reminded 可重发——按契约执行，reminded→reminded 为幂等重发自环（不引入新状态，与状态机不冲突的端点级扩展，对应原型「可多次 Send reminder」交互）
- STEP-SHR-04 事务提交后 publish MQ `showroom.remind`（EVT-SHR-002）→ q.mail 消费 INSERT MailRecord(type=**showroom_assign**, 幂等键=event_id) → SMTP 按 locale 渲染真发（FLOW-P11 幂等防重发 + 失败重试 ×3 → 死信；决策 16/20.5 基建复用）；publish 失败不回滚、记告警（EC-SHR-002，SMTP 失败降级见 data-detail §8——bs-670/671）
- STEP-SHR-05 回读装配 ShowroomMember（assign_status=reminded）

**出参**: 200 ShowroomMember（「提醒已入队发送」语义——MQ 入队即成功，发送结果由 MailRecord 状态承载）
**错误映射**: 401 `40100` / 403 `403101`(兜底) / 404 `404101`,`404103` / 409 `409103` / 500 `50000`,`50001`

---

## 6. 自检

- [x] **13 端点全覆盖**（showrooms 4 + invite/reset 1 + guest-session 1 + items 2 + vote/comments 2 + members 2 + 列表 1）＝ E-SHR-01 ~ E-SHR-13，每端点四部分齐全（入参验证 / 业务步骤 / 出参构造 / 错误码映射）
- [x] V-SHR-001 ~ V-SHR-023 全域连续唯一；STEP-SHR-NN 每端点独立编号段（E-SHR-NN 提供唯一溯源前缀）
- [x] 错误码全部出自 showroom-api.openapi.yml 码表，**11 码全部至少出现一次**（401101 E-SHR-03/07/10/11 与过滤器层 / 403101 服务层兜底口径 / 403102 过滤器层 / 404101 全 owner 路径 / 404102 E-SHR-09/10/11/12 / 404103 E-SHR-12/13 / 409101 E-SHR-07 / 409102 E-SHR-08 / 409103 E-SHR-12/13 / 410101 E-SHR-07 / 422101 各端点）+ 透传 catalog 404501（review/trading 先例）+ identity 复用码（40100/50000/50001），无臆造
- [x] guest JWT 设计复用既有 JWT 基建（JwtTokenProvider/storeKey/typ claim 体系），claims 含 showroom_id/member_id/inv_ver，TTL 24h 配置化（0.2-1）
- [x] StoreJwtFilter 旁路扩展具体设计（0.2 四段裁决：公开白名单→store→guest 作用域/操作白名单/showroom_id 等值/invite_version 校验→过期分型 401101/40100），与 review 域 method-aware `METHOD:pattern` 白名单同一匹配器实现，兼容性显式声明
- [x] owner 强隔离：全部 owner 路径 `findByIdAndOwner`，跨用户一律 404 `404101` 防探测（bs-640）；403101/403102 两码分层口径定稿
- [x] 投票 PUT 幂等去重（E-SHR-10 STEP-SHR-04 UPSERT + uk）；昵称唯一 409101 触发面定稿（受保护昵称 + 并发 uk 兜底）；重置邀请链接级联失效双通道（invite_token_prev → 410101；inv_ver → 401101）
- [x] 状态机 showroom_member_assignment 全部转换落到端点 CAS guard（assign/reassign E-SHR-12、send_reminder E-SHR-13、place_order 归 MQ 消费者 EVT-SHR-003——见 data-detail）；reminded 重发自环口径定稿
- [x] 缓存口径：13 端点全部不缓存（契约 BE-DIM-8 标注），响应 no-store；MQ 事件（showroom.invite/showroom.remind 发布 + order.paid 消费）与 data-detail EVT-SHR 一一对应；事务边界 TX-SHR-001 ~ TX-SHR-011 与 data-detail 一一对应
