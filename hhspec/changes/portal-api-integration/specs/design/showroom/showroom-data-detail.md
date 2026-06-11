# showroom 数据层详细设计（L2）

> 角色: l2_data_designer ｜ change: portal-api-integration ｜ domain: showroom
> 方法论：Entity Design / Repository 方法(RM-SHR) / DTO 映射(MAP-SHR) / 索引(IDX-SHR) / 事务边界(TX-SHR) / 数据校验(CV-SHR) / 领域事件与端口(EVT-SHR，order.paid 消费 + showroom_invite/showroom_assign 邮件事件) / 缓存设计(CACHE-SHR，显式不缓存) / 完整 DDL。
> 来源权威：er-diagram.yml（5 实体）+ showroom-api.openapi.yml v1.1.0 + data-flow.md（FLOW-P11/P12 + MQ 拓扑 q.showroom/q.mail）+ state-machine.yml（showroom_member_assignment）+ decision.md 决策 20.2/20.4/20.5/16/BE-DIM-4 + 后端样板 /Volumes/MAC/workspace/dreamy/backend（huihao-mysql 基类 + JwtTokenProvider）+ code-patterns.md（CP-001~CP-031）+ trading-data-detail（EVT-TRD-001 order.paid 载荷与 MQ 可靠性参数口径——本域为消费者）。

## 1. Entity Design（基类选型 / 逻辑删除 / 审计字段）

### 1.1 基类与通用约定

- **基类**：全部实体继承 `huihao.mysql.auditable.LongAuditableEntity`（与 identity/catalog/review 同款）——`id BIGINT AUTO_INCREMENT` 主键 + `created_at`/`updated_at DATETIME(3)` 审计列。决策 12：Long 自增主键、标准增表无迁移。
- **注解范式**（CP-015）：`@Table(indexes=...)` + `@TableName(value)` + `@Column(name=<EntityDBConst 常量>)`；每实体配 `{Entity}DBConst extends CommonDBConst`（置于 `com.dreamy.showroom.domain.{聚合根}/consts/`）。
- **逻辑删除**：**不启用**（identity/catalog/review 同口径）。本域删除端点（E-SHR-05/09）为**物理级联删除**——协作空间删除即终结、无恢复诉求（原型 Delete/Keep 二次确认即终态），不留软删行。
- **枚举落地**（CP-003）：assign_status/vote 用 `VARCHAR + Java enum` 双保险（取值与契约字符串一致：unassigned/assigned/reminded/ordered、like/dislike），与 review 同口径。
- **时间**：DATETIME(3) UTC ↔ LocalDateTime ↔ ISO8601（CP-014）。comment.created_at 直接复用基类审计列作为业务时间（契约 ShowroomComment.created_at，无独立业务时间语义，与 review submitted_at 分离场景不同——此处无审核延迟，提交即可见）。
- **包结构**：`com.dreamy.showroom/`（单模块多 domain，与 catalog/review 平级）：`domain/{showroom,member}/{entity,repository,service,consts}` + `controller/` + `dto/` + `mq/`（事件发布器 + q.showroom 消费者）+ `port/`（CatalogSnapshotPort/IdentityQueryPort 出向声明 + DyeLotPort 入向实现）+ `security/`（ShowroomGuestValidator，与 identity StoreJwtFilter 协作）+ `config/`。

### 1.2 实体清单（5 实体 = 5 张表，无 translation 附表——协作数据不翻译）

| 实体 | 表名 | 要点 |
|---|---|---|
| Showroom | showroom | owner_id 强隔离；invite_token uk 不可猜 UUID；**invite_version 设计派生列**（er-diagram 无此字段；溯源：契约 ShowroomGuestAuth claims「invite 版本号」+ 决策 20.2「重置后旧 guest JWT 失效」——版本等值校验为级联失效机制核心，0.2-d）；**invite_token_prev 设计派生列**（溯源：契约 410101「邀请链接已被重置作废」需区分「无效」与「已重置」，单代保留旧 token 供 E-SHR-07 精确返回 410101，E-SHR-06 STEP-SHR-03 定稿） |
| ShowroomItem | showroom_item | showroom_id+product_id+color 三元唯一（409102）；color 归一化空串参与 uk；**last_ordered_at 设计派生列**（溯源：决策 20.4 dye lot 24h 窗口「同款式已付订单」判定需落地存储——order.paid 消费回写，DyeLotPort 与 dye_lot_notice 同源读取） |
| ShowroomMember | showroom_member | showroom_id+nickname 唯一（409101 去重身份）；email 决策 20.5 指派时填写；assign_status 状态机；linked_customer_id 访客登录绑定回填（E-SHR-07 STEP-SHR-05 / EVT-SHR-003） |
| ShowroomVote | showroom_vote | member_id+showroom_item_id 唯一（PUT 幂等覆盖写）；vote 二枚举 |
| ShowroomComment | showroom_comment | item 子表；content ≤500；nickname 联 member 派生不冗余 |

设计派生列共 3 个（invite_version/invite_token_prev/last_ordered_at），全部显式溯源，不出契约 DTO（invite_version 仅入 JWT claims；last_ordered_at 仅派生 dye_lot_notice 布尔）。

## 2. Repository 方法（RM-SHR）

### ShowroomRepository
- RM-SHR-001 `insert(Showroom)` —— E-SHR-01（invite_token uk 冲突即 UUID 重生成重插一次，概率级防御）
- RM-SHR-002 `findByIdAndOwner(id, ownerId) -> Showroom?` —— owner 强隔离点查（404101，CV-SHR-007，全部 owner 路径唯一入口）
- RM-SHR-003 `findById(id) -> Showroom?` —— guest 视图装配 + ShowroomGuestValidator 版本校验（0.2-d）
- RM-SHR-004 `listByOwner(ownerId) -> List<Showroom>` —— E-SHR-02，ORDER BY created_at DESC（IDX-SHR-002）
- RM-SHR-005 `updateProfile(id, name, weddingDate)` —— E-SHR-04（PUT 全量覆盖，weddingDate 可置 NULL）
- RM-SHR-006 `resetInvite(id, newToken) -> affected` —— `UPDATE showroom SET invite_token_prev=invite_token, invite_token=:new, invite_version=invite_version+1 WHERE id=?`（E-SHR-06 单语句原子）
- RM-SHR-007 `findByInviteToken(token) -> Showroom?` —— E-SHR-07 当前值 uk 点查（IDX-SHR-001）
- RM-SHR-007b `existsByInviteTokenPrev(token) -> bool` —— E-SHR-07 STEP-SHR-02 重置识别（410101；IDX-SHR-011）
- RM-SHR-008 `deleteById(id)` —— TX-SHR-003 级联尾步
- RM-SHR-009 `countSummary(showroomIds) -> Map<showroomId, {itemCount, memberCount}>` —— 两条 GROUP BY IN 批查派生（E-SHR-02 STEP-SHR-02，NP-SHR-001）
- RM-SHR-010 `listIdsByCustomerParticipation(customerId) -> List<showroomId>` —— `owner_id=:cid` UNION `member.linked_customer_id=:cid 的 showroom_id`（DyeLotPort 与 EVT-SHR-003 dye lot 回写的参与域判定）

### ShowroomItemRepository
- RM-SHR-020 `insert(item)` —— uk_si_room_product_color 冲突向上抛映射 409102（E-SHR-08）
- RM-SHR-021 `listByShowroom(showroomId) -> List<ShowroomItem>` —— E-SHR-03（uk 左前缀覆盖）
- RM-SHR-022 `findByIdAndShowroom(itemId, showroomId) -> ShowroomItem?` —— 归属校验（404102，E-SHR-09/10/11/12）
- RM-SHR-023 `deleteById(itemId)` / `deleteByShowroom(showroomId)` —— 级联
- RM-SHR-024 `touchLastOrdered(showroomIds, productIds, ts) -> affected` —— `UPDATE showroom_item SET last_ordered_at=:ts WHERE showroom_id IN (...) AND product_id IN (...)`（EVT-SHR-003 dye lot 窗口回写，覆盖写可重入）
- RM-SHR-025 `selectDyeLotProductIds(showroomIds, productIds, windowStart) -> List<Long>` —— `SELECT DISTINCT product_id FROM showroom_item WHERE showroom_id IN (...) AND product_id IN (...) AND last_ordered_at > :windowStart`（DyeLotPort 实现，IDX-SHR-004）

### ShowroomMemberRepository
- RM-SHR-030 `findByShowroomAndNickname(showroomId, nickname) -> Member?` —— E-SHR-07 复用裁决（uk 点查）
- RM-SHR-031 `insert(member)` —— uk_sm_room_nickname 冲突回读重裁决（E-SHR-07 STEP-SHR-04 / E-SHR-10 STEP-SHR-03 owner 自动建 member 后缀重试）
- RM-SHR-032 `findByIdAndShowroom(memberId, showroomId) -> Member?` —— 归属校验（404103，E-SHR-12/13）
- RM-SHR-033 `listByShowroom(showroomId) -> List<Member>` —— E-SHR-03（uk 左前缀覆盖）
- RM-SHR-034 `findByShowroomAndLinkedCustomer(showroomId, customerId) -> Member?` —— owner/已绑定成员的 my_member_id 与互动身份解析（IDX-SHR-006）
- RM-SHR-035 `casAssign(memberId, itemId, email?) -> affected` —— `UPDATE ... SET assigned_item_id=:itemId, email=COALESCE(:email, email), assign_status='assigned' WHERE id=? AND assign_status IN ('unassigned','assigned','reminded')`（E-SHR-12，affected=0 → 409103；CP-016 同型条件更新）
- RM-SHR-036 `casRemind(memberId) -> affected` —— `UPDATE ... SET assign_status='reminded' WHERE id=? AND assign_status IN ('assigned','reminded') AND email IS NOT NULL`（E-SHR-13，affected=0 → 409103 details 二分）
- RM-SHR-037 `casOrder(memberId) -> affected` —— `UPDATE ... SET assign_status='ordered' WHERE id=? AND assign_status IN ('assigned','reminded')`（EVT-SHR-003 place_order 推进；幂等：已 ordered affected=0 空操作）
- RM-SHR-038 `listByLinkedCustomer(customerId) -> List<Member>` —— EVT-SHR-003 消费侧定位（IDX-SHR-006）
- RM-SHR-039 `unassignByItem(itemId) -> affected` —— assigned/reminded 回 unassigned + assigned_item_id 置 NULL（E-SHR-09 STEP-SHR-03；IDX-SHR-007）
- RM-SHR-040 `clearAssignedItemKeepOrdered(itemId) -> affected` —— ordered 行仅清 assigned_item_id（终态保持）
- RM-SHR-041 `bindCustomer(memberId, customerId) -> affected` —— `UPDATE ... SET linked_customer_id=:cid WHERE id=? AND (linked_customer_id IS NULL OR linked_customer_id=:cid)`（E-SHR-07 STEP-SHR-05 幂等绑定）
- RM-SHR-042 `deleteByShowroom(showroomId)` —— 级联

### ShowroomVoteRepository
- RM-SHR-050 `upsert(itemId, memberId, vote)` —— `INSERT ... ON DUPLICATE KEY UPDATE vote=:vote`（E-SHR-10 PUT 幂等核心，uk_sv_member_item）
- RM-SHR-051 `aggregateByItems(itemIds) -> Map<itemId, {likeCount, dislikeCount}>` —— `SELECT showroom_item_id, vote, COUNT(*) ... GROUP BY showroom_item_id, vote` 单查内存汇总（NP-SHR-002，禁止逐 item 逐枚举 COUNT）
- RM-SHR-052 `listByMemberAndItems(memberId, itemIds) -> Map<itemId, vote>` —— my_vote 批查
- RM-SHR-053 `deleteByItems(itemIds)` —— 级联（E-SHR-09/TX-SHR-003）

### ShowroomCommentRepository
- RM-SHR-060 `insert(comment)` —— E-SHR-11
- RM-SHR-061 `listByItems(itemIds) -> List<CommentWithNickname>` —— `JOIN showroom_member ON member_id` 派生 nickname，ORDER BY created_at ASC，单次 IN 批查（NP-SHR-001；IDX-SHR-010）
- RM-SHR-062 `deleteByItems(itemIds)` —— 级联

## 3. DTO ↔ Entity 映射（MAP-SHR）

- MAP-SHR-001 Showroom→ShowroomSummary：id/owner_id/name/wedding_date + item_count/member_count（RM-SHR-009 派生）；**不含 invite_token/invite_version/invite_token_prev**
- MAP-SHR-002 Showroom→ShowroomDetail（**owner 视图**）：Summary 全字段 + invite_token + is_owner=true + my_member_id（RM-SHR-034 命中才输出）+ items[]（MAP-SHR-004）+ members[]（MAP-SHR-006 owner 裁剪：**含 email、linked_customer_id**）
- MAP-SHR-003 Showroom→ShowroomDetail（**guest 视图**）：同上但 **invite_token 不输出**（契约「guest 视图不含此字段」）、is_owner=false、my_member_id=GuestContext.memberId、members[] **不含 email/linked_customer_id**（契约「仅 owner 视图返回」）
- MAP-SHR-004 ShowroomItem→DTO：id/product_id/color（落库空串 `''` → 出参省略该字段，契约 color 可选）+ product（ProductRef ← CatalogSnapshotPort.getProductCards 按 locale 解析：id/slug/name/price(USD 基准)/image_url/custom_size_available/lead_time_days）+ like_count/dislike_count（RM-SHR-051 派生）+ my_vote（RM-SHR-052，未投省略）+ comments[]（MAP-SHR-005）+ dye_lot_notice（CV-SHR-011：last_ordered_at > now-24h）；**不暴露** last_ordered_at 原始值
- MAP-SHR-005 ShowroomComment→DTO：id/showroom_item_id/member_id/content/created_at + nickname（RM-SHR-061 联表派生，不冗余落库——成员改昵称场景不存在：昵称即身份不可改）
- MAP-SHR-006 ShowroomMember→DTO：id/showroom_id/nickname/assigned_item_id/assign_status 恒输出；email/linked_customer_id **仅 owner 视图**；guest-session 回执（E-SHR-07）为本人会话：含自身 email（如有）、**不含 linked_customer_id**（敏感关联不向匿名态回显）
- MAP-SHR-007 GuestSession→DTO：guest_token（裸 JWT，仅响应体出现，日志 [REDACTED]）/expires_at（签发时刻+TTL，ISO8601）/showroom_id/member（MAP-SHR-006 本人裁剪）
- MAP-SHR-008 枚举与时间：Java enum ↔ VARCHAR 契约字符串（unassigned/assigned/reminded/ordered、like/dislike）；LocalDateTime(UTC) ↔ ISO8601 snake_case（CP-001/CP-014）；nickname/content/name 落库前 trim（CV-SHR-001）

## 4. 索引设计（IDX-SHR）

| ID | 表 | 索引 | 支撑路径 |
|---|---|---|---|
| IDX-SHR-001 | showroom | `UNIQUE uk_showroom_invite (invite_token)` | E-SHR-07 token 点查 + 不可猜 token 全局唯一 |
| IDX-SHR-002 | showroom | `idx_showroom_owner (owner_id, created_at)` | E-SHR-02 owner 列表 + 排序 |
| IDX-SHR-003 | showroom_item | `UNIQUE uk_si_room_product_color (showroom_id, product_id, color)` | 409102 三元唯一（color 归一化 `''` 参与）；左前缀覆盖 listByShowroom |
| IDX-SHR-004 | showroom_item | `idx_si_product_ordered (product_id, last_ordered_at)` | RM-SHR-025 dye lot 窗口命中（DyeLotPort 热路径） |
| IDX-SHR-005 | showroom_member | `UNIQUE uk_sm_room_nickname (showroom_id, nickname)` | 409101 同房昵称唯一（并发兜底）；左前缀覆盖 listByShowroom |
| IDX-SHR-006 | showroom_member | `idx_sm_linked (linked_customer_id)` | RM-SHR-034/038（my_member 解析 + order.paid 消费定位） |
| IDX-SHR-007 | showroom_member | `idx_sm_assigned_item (assigned_item_id)` | E-SHR-09 移除款式级联回退 |
| IDX-SHR-008 | showroom_vote | `UNIQUE uk_sv_member_item (member_id, showroom_item_id)` | PUT 幂等 UPSERT 承载（js_guard 唯一） |
| IDX-SHR-009 | showroom_vote | `idx_sv_item (showroom_item_id)` | RM-SHR-051 聚合 / 级联删除 |
| IDX-SHR-010 | showroom_comment | `idx_sc_item (showroom_item_id, created_at)` | RM-SHR-061 批查 + 时序 |
| IDX-SHR-011 | showroom | `idx_showroom_invite_prev (invite_token_prev)` | RM-SHR-007b 重置识别（410101） |

查询优化补充：
- NP-SHR-001 防 N+1：详情装配（items/members/votes/comments/product 卡片）全部单次 IN 批查或批量端口；列表计数 RM-SHR-009 两条 GROUP BY
- NP-SHR-002 票数聚合单条 GROUP BY（RM-SHR-051），禁止逐款式 COUNT ×2
- QP-SHR-001 本域数据量级 = 单用户个位数 Showroom × 房内数十行，无分页无 SLA 热点；唯一跨房高频路径为 DyeLotPort（购物车/结算每次报价调用）——IDX-SHR-004 + RM-SHR-010 参与域先行收敛 showroom_ids 保证毫秒级

## 5. 事务边界（TX-SHR）

| ID | 端点/流程 | 边界与回滚语义 |
|---|---|---|
| TX-SHR-001 | E-SHR-01 创建 | 单事务单表 INSERT；uk_showroom_invite 冲突重生成 UUID 重插一次 |
| TX-SHR-002 | E-SHR-04 编辑 | 单事务：owner 点查 + updateProfile |
| TX-SHR-003 | E-SHR-05 删除 | 单事务级联 5 表（comment→vote→item→member→showroom），任一失败整体回滚无半删状态；guest JWT 失效由读路径自然达成（无补偿动作） |
| TX-SHR-004 | E-SHR-06 重置邀请 | 单事务单语句（token 轮转 + prev 保留 + version 自增原子完成） |
| TX-SHR-005 | E-SHR-07 guest 会话 | 单事务：token 裁决 → member 复用/INSERT（uk 冲突回读重裁决）→ 绑定回填；**JWT 签发在事务提交后**（纯计算无副作用，失败不留脏 member——member 已提交属可接受残留：再次进入复用） |
| TX-SHR-006 | E-SHR-08 添加款式 | 单事务 INSERT；uk 冲突映射 409102 回滚 |
| TX-SHR-007 | E-SHR-09 移除款式 | 单事务：成员回退（assigned/reminded→unassigned + ordered 清引用）+ 级联删 vote/comment + 删 item，整体原子 |
| TX-SHR-008 | E-SHR-10 投票 | 单事务：owner 自动建 member（含 uk 后缀重试）+ UPSERT vote；聚合回读同事务内（read committed 即时可见） |
| TX-SHR-009 | E-SHR-11 留言 | 单事务：owner 自动建 member + INSERT comment |
| TX-SHR-010 | E-SHR-12 指派 | 单事务：CAS casAssign（409103 并发仲裁 bs-602/603/604）；**MQ showroom.invite 在事务提交后**（CP-031 同型口径） |
| TX-SHR-011 | E-SHR-13 提醒 | 单事务：CAS casRemind；**MQ showroom.remind 在事务提交后**；publish 失败状态已是 reminded——可重按重发（自环幂等），告警日志 |
| TX-SHR-012 | EVT-SHR-003 order.paid 消费 | 单事务：成员 casOrder 推进（逐 member）+ touchLastOrdered 回写；任一失败整体回滚 → nack 重试（事件级幂等闸在事务外 Redis SETNX，失败时释放键允许重投） |
| EC-SHR-001 | 级联删除中途失败 | 整体回滚（TX-SHR-003/007 单事务保证）；无缓存副作用（本域不缓存） |
| EC-SHR-002 | MQ publish 失败 | 本地事务不回滚（data-flow 降级矩阵）：邮件类记告警日志人工补偿；assign/remind 状态已落库，用户可重触发（assign 改 email 重发 / remind 自环重发） |

## 6. 数据校验与引用完整性（CV-SHR）

- CV-SHR-001 长度与 trim：name trim 1..64（bs-353/535）、nickname trim 1..32（bs-362/538）、content trim 1..500（bs-374/542）、email RFC+≤254（bs-363/539）、color trim ≤64（bs-359/537）、invite_token ≤64（bs-355/536）；违例 → 422101
- CV-SHR-002 枚举校验：vote ∈ {like,dislike}（bs-541/370）；assign_status ∈ 四态（bs-540/365）但**不接收任何客户端赋值**——仅经状态机 CAS 推进（RM-SHR-035/036/037），请求体夹带 assign_status 字段被忽略
- CV-SHR-003 color 归一化：未提供/trim 空 → 落库空串 `''`（uk_si_room_product_color 三元唯一生效；MySQL uk 对 NULL 不去重的规避）；出参空串省略字段
- CV-SHR-004 同房昵称唯一：uk_sm_room_nickname 兜底（应用层预检 RM-SHR-030 + 索引双保险，409101）；owner 自动建 member 冲突 → 确定性后缀 `#`+subject 末 4 位重试一次（E-SHR-10 STEP-SHR-03）
- CV-SHR-005 投票唯一：uk_sv_member_item + UPSERT 覆盖写（PUT 幂等，重复投票覆盖原票 js_guard）
- CV-SHR-006 逻辑外键（CP-010 无物理 FK）：product_id 写前经 CatalogSnapshotPort 校验存在且 published（404501 透传，bs-722）；item/member 归属一律 `WHERE showroom_id=?` 双键点查（404102/404103，bs-723/725/726）；vote/comment 的 member_id 仅取鉴权主体解析结果、showroom_item_id 仅取归属校验通过的 path 值（bs-727~730 不可达路径由 API 面保证）；owner_id 取 JWT subject（identity 会话有效即存在，bs-721/724 不可达）
- CV-SHR-007 owner 强隔离：owner 读写路径唯一入口 `findByIdAndOwner`（跨用户 404101 防探测，bs-640；BE-DIM-6）
- CV-SHR-008 invite_version 单调递增不变量：仅 RM-SHR-006 自增；guest JWT claims.inv_ver 必须等值（ShowroomGuestValidator，否则 401101）；invite_token_prev 仅单代保留（再次重置覆盖）
- CV-SHR-009 受保护昵称：linked_customer_id 非空的 member 不可被匿名 guest-session 复用（409101，E-SHR-07 STEP-SHR-03；本人持 store token 重放除外）
- CV-SHR-010 assign 引用完整性：assigned_item_id 必属本 showroom（404102，V-SHR-021，state-machine guard bs-832）；ordered 终态不可再指派（409103，CAS 条件承载 bs-838~841 非法事件拒绝）；item 删除时悬挂引用清理（RM-SHR-039/040）
- CV-SHR-011 dye lot 窗口判定（决策 20.4 定稿）：`dye_lot_notice = (last_ordered_at IS NOT NULL AND last_ordered_at > now() - INTERVAL :window HOUR)`，window 配置项 `dreamy.showroom.dye-lot-window-hours` 缺省 24；判定纯展示（不影响履约），与 DyeLotPort（RM-SHR-025）同列同口径——Showroom 视图与购物车/结算提示强一致

## 7. 缓存设计（CACHE-SHR）

| ID | 范围 | 决策 |
|---|---|---|
| CACHE-SHR-001 | **本域 13 端点全部 + DyeLotPort + ShowroomGuestValidator 点查** | **不缓存**（显式声明）：不建任何 JetCache key；响应头 `Cache-Control: private, no-store`；CDN 不缓存 |

依据：契约「缓存标注（BE-DIM-8）：协作数据强一致（投票/留言/指派需即时可见），全部端点不缓存（决策 4 第 3 层个人数据口径）」+ data-flow 缓存矩阵「购物车/订单/.../Showroom 全部：不缓存」。连带口径：①ShowroomGuestValidator 的 invite_version 点查**不缓存**——缓存会破坏「重置后即时失效」语义（主键点查成本可忽略）；②DyeLotPort 不缓存——24h 窗口为时变判定且调用方（trading 报价）本身不缓存；③本域不发 content.invalidated（无 CDN/ISR 缓存面需要失效）。

## 8. 领域事件与跨域端口（EVT-SHR，RabbitMQ topic exchange `dreamy.events`）

### 8.1 本域发布（决策 20.5，FLOW-P11 消费）

| ID | 事件 | routing key | 触发 | payload |
|---|---|---|---|---|
| EVT-SHR-001 | 邀请/指派通知 | `showroom.invite` | TX-SHR-010 提交后（E-SHR-12，仅当本次提供 email 且为新值/首填，防骚扰） | `{event_id(UUID), showroom_id, member_id, email, nickname, showroom_name, wedding_date?, product_name, color?, invite_url, locale, occurred_at}` |
| EVT-SHR-002 | 下单提醒 | `showroom.remind` | TX-SHR-011 提交后（E-SHR-13） | `{event_id(UUID), showroom_id, member_id, email, nickname, showroom_name, wedding_date?, product_name, color?, invite_url, locale, occurred_at}` |

- **MailRecord 映射（FLOW-P11 / 决策 16/20.5 扩展枚举）**：`showroom.invite` → MailRecord.type=**showroom_invite**；`showroom.remind` → MailRecord.type=**showroom_assign**。消费者 q.mail（基建分册）幂等键：showroom 两类型按 **event_id** 唯一（每次 assign 换 email/remind 重发均为独立业务发送，区别于订单类 orderId+type 键——本域定稿并向 q.mail 分册登记）；失败重试 ×3 指数退避 → mail.dlq + MailRecord=dead（bs-670 SMTP 降级：主流程不阻塞；bs-671 幂等：MQ 重投同 event_id 不重发）。
- **invite_url 构造**：`{store-base-url}/showroom/{showroom_id}?invite={invite_token}`（与 showroom-frontend-detail 路由约定一致）；payload 含 token 属业务必要，**日志侧 invite_url/token 一律 [REDACTED]**（error-strategy 脱敏规则）。
- **locale**：取触发请求的 locale（owner 操作语言）——收件访客语言未知，以邀请语境渲染（设计定稿；三语模板归 q.mail 分册按 type×locale 渲染）。
- 生产侧可靠性：事务提交后发布；失败不回滚（EC-SHR-002）。

### 8.2 本域消费（q.showroom ← order.paid，FLOW-P07 扇出 / FLOW-P12 注记）

**EVT-SHR-003 order.paid 消费者**（消费 trading EVT-TRD-001：`{event_id, order_no, order_id, customer_id, locale, currency, total_amount, lines:[{product_id, sku_id?, qty}]}`——**最小依赖面 = event_id + customer_id + lines[].product_id**，其余字段不依赖，与 trading 的兼容基线）：

1. **幂等闸**：Redis SETNX `mq:consumed:q.showroom:{event_id}` TTL 7d（trading §6 可靠性参数口径）；已存在 → ack 空操作；TX-SHR-012 失败 → 释放键 + nack（允许重投重入）。
2. **place_order 推进（state-machine：assigned|reminded → ordered）**：`listByLinkedCustomer(customer_id)`（RM-SHR-038）→ 过滤 assigned_item_id 非空的行 → 联 RM-SHR-022 取 assigned_item.product_id → **product_id ∈ lines[].product_id** 的成员逐一 `casOrder(memberId)`（RM-SHR-037；guard「订单行含被指派款式」bs-836/837 由 ∈ 判定承载；「访客下单需登录」由 linked_customer_id 定位前提天然承载——未绑定成员无法被命中）。已 ordered 行 affected=0 幂等空操作。
3. **dye lot 窗口回写（决策 20.4）**：`listIdsByCustomerParticipation(customer_id)`（RM-SHR-010，买家参与的全部 showroom：自有 + 被绑定）→ `touchLastOrdered(showroomIds, lines[].product_ids, now)`（RM-SHR-024，覆盖写可重入）→ 此后 24h 内该房同款式 dye_lot_notice=true、DyeLotPort 命中。
4. **队列参数（error-strategy L2 要求 3 本域落点，与 trading §6 同口径）**：`q.showroom` durable、prefetch=8、手动 ack；异常 nack → 重试队列 `q.showroom.retry`（x-message-ttl 阶梯 5s/30s/180s，x-dead-letter 回主队列）×3 → 超限路由 DLX `dreamy.dlx` → `dreamy.dlq`（告警 + 人工重放）。绑定：`dreamy.events` topic，binding key `order.paid`。

### 8.3 本域提供的进程内端口（决策 3 通路）

| 端口 | 方法 | 消费方 | 实现 |
|---|---|---|---|
| DyeLotPort | `hintProductIds(customerId, productIds) -> List<Long>` | trading（cart/quote：FLOW-P04/P05 dye_lot_product_ids） | RM-SHR-010 参与域收敛 → RM-SHR-025 窗口命中（CV-SHR-011 同口径）；customerId 无参与房/无命中 → 空数组（trading STEP「空结果返回空数组」对齐）；不缓存 |

### 8.4 本域消费的进程内端口（出向，声明于 `com.dreamy.showroom.port`）

| 端口 | 方法 | 提供方 | 用途 |
|---|---|---|---|
| CatalogSnapshotPort | `getProductCards(productIds, locale)` | catalog | ProductRef 卡片装配（E-SHR-03/08）+ 商品存在性校验（404501 透传）+ 邮件 payload product_name |
| IdentityQueryPort | `getUserName(customerId)` | identity | owner 自动建 member 昵称（E-SHR-10/11） |

## 9. 完整 DDL（MySQL 8.0，utf8mb4_0900_ai_ci，InnoDB；与 huihao-mysql 注解建表等价的权威 SQL）

```sql
-- 1. showroom 协作空间（决策 20，F-066/F-068）
CREATE TABLE showroom (
  id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  owner_id          BIGINT       NOT NULL COMMENT '逻辑外键 user.id（创建者新娘，JWT subject，BE-DIM-6 强隔离）',
  name              VARCHAR(64)  NOT NULL COMMENT '名称 trim 1..64（CV-SHR-001）',
  wedding_date      DATE         NULL COMMENT '婚期（F-077 结算自动带入，可空）',
  invite_token      VARCHAR(64)  NOT NULL COMMENT '不可猜 UUID 邀请 token（决策 20.2）',
  invite_token_prev VARCHAR(64)  NULL COMMENT '设计派生列：上一代 token（重置识别 410101，单代保留）',
  invite_version    INT          NOT NULL DEFAULT 1 COMMENT '设计派生列：邀请版本号（guest JWT inv_ver 等值校验，重置自增，CV-SHR-008）',
  created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_showroom_invite (invite_token),
  KEY idx_showroom_owner (owner_id, created_at),
  KEY idx_showroom_invite_prev (invite_token_prev)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Showroom 协作空间（新娘创建，邀请伴娘团协作）';

-- 2. showroom_item 收藏款式（F-067）
CREATE TABLE showroom_item (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  showroom_id     BIGINT       NOT NULL COMMENT '逻辑外键 showroom.id',
  product_id      BIGINT       NOT NULL COMMENT '逻辑外键 product.id（写前经 CatalogSnapshotPort 校验）',
  color           VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '加入时选择的颜色；未选归一化空串（uk 三元唯一前提，CV-SHR-003）',
  last_ordered_at DATETIME(3)  NULL COMMENT '设计派生列：同房该款式最近已付订单时间（order.paid 消费回写，dye lot 24h 窗口源，决策 20.4）',
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_si_room_product_color (showroom_id, product_id, color),
  KEY idx_si_product_ordered (product_id, last_ordered_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Showroom 收藏款式（同房 product+color 唯一 409102）';

-- 3. showroom_member 成员（F-068/F-070，免注册访客 + 指派状态机）
CREATE TABLE showroom_member (
  id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  showroom_id        BIGINT       NOT NULL COMMENT '逻辑外键 showroom.id',
  nickname           VARCHAR(32)  NOT NULL COMMENT '昵称 trim 1..32，同房唯一（去重身份 409101）',
  email              VARCHAR(254) NULL COMMENT '提醒邮件收件地址（决策 20.5 新娘指派时填写；仅 owner 视图输出）',
  assigned_item_id   BIGINT       NULL COMMENT '逻辑外键 showroom_item.id（被指派款式；item 删除时清理 RM-SHR-039/040）',
  assign_status      VARCHAR(16)  NOT NULL DEFAULT 'unassigned' COMMENT 'unassigned|assigned|reminded|ordered（showroom_member_assignment 状态机，仅 CAS 推进）',
  linked_customer_id BIGINT       NULL COMMENT '逻辑外键 user.id（访客登录后绑定回填，决策 20.2；仅 owner 视图输出）',
  created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_sm_room_nickname (showroom_id, nickname),
  KEY idx_sm_linked (linked_customer_id),
  KEY idx_sm_assigned_item (assigned_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Showroom 成员（访客凭邀请 token+昵称参与；指派/提醒/下单状态机）';

-- 4. showroom_vote 款式投票（F-069，PUT 幂等覆盖）
CREATE TABLE showroom_vote (
  id               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  showroom_item_id BIGINT      NOT NULL COMMENT '逻辑外键 showroom_item.id',
  member_id        BIGINT      NOT NULL COMMENT '逻辑外键 showroom_member.id（鉴权主体解析，不接收请求体）',
  vote             VARCHAR(8)  NOT NULL COMMENT 'like|dislike（重复投票覆盖原票）',
  created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_sv_member_item (member_id, showroom_item_id),
  KEY idx_sv_item (showroom_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='款式投票（member+item 唯一，UPSERT 幂等）';

-- 5. showroom_comment 款式留言（F-069）
CREATE TABLE showroom_comment (
  id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  showroom_item_id BIGINT       NOT NULL COMMENT '逻辑外键 showroom_item.id',
  member_id        BIGINT       NOT NULL COMMENT '逻辑外键 showroom_member.id（nickname 联表派生展示）',
  content          VARCHAR(500) NOT NULL COMMENT '留言 trim 1..500',
  created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '留言时间（契约 created_at，复用审计列）',
  updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_sc_item (showroom_item_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='款式留言（带昵称展示，提交即可见不审核）';
```

> 备注：①本域为 q.showroom 消费者，event_id 幂等采用 Redis SETNX（trading §6 口径），**不落 processed_event 表**（该表归 trading webhook 专用）；②种子数据（决策 21）：prototype data/showrooms.ts 样例（2 房 × 含成员/款式/投票/留言/指派变体）转为 5 表种子行（owner_id 关联 identity 种子用户、product_id 关联 catalog 种子商品；invite_token 重新生成 UUID 不复用原型假值），**仅 dev/staging 灌入**（协作 UGC 属订单类口径），归 L3 种子脚本任务；③guest JWT 无会话表——无状态短期凭证，失效由 invite_version/行存在性承担（0.2-d），不扩展 user_session。

## 10. 自检

- [x] er-diagram 本域 5 实体全部建模；字段/maxlen/枚举/必填与 er-diagram + 契约逐一对齐；设计派生列 3 个（invite_version/invite_token_prev/last_ordered_at）全部显式溯源
- [x] 基类选型（LongAuditableEntity）/ 逻辑删除（不启用，物理级联删除）/ 审计字段（comment.created_at 复用口径声明）显式声明
- [x] RM-SHR-001~062（分段编号，无重号）；MAP-SHR-001~008；IDX-SHR-001~011；TX-SHR-001~012 + EC-SHR-001/002；CV-SHR-001~011；EVT-SHR-001~003 + 双向端口表；CACHE-SHR-001
- [x] **协作数据不缓存显式声明**（CACHE-SHR-001 含 GuestValidator/DyeLotPort 连带口径与依据）
- [x] **order.paid 消费定稿**（EVT-SHR-003：event_id Redis SETNX 幂等 + casOrder 推进 assign_status→ordered + linked_customer_id 定位前提 + last_ordered_at dye lot 回写 + q.showroom 队列参数与 trading §6 同口径）
- [x] **showroom_invite/showroom_assign 邮件事件发布定稿**（EVT-SHR-001/002：触发条件/payload/MailRecord type 映射/event_id 幂等键/locale 口径/invite_url 脱敏）
- [x] **dye lot 24h 窗口判定设计定稿**（CV-SHR-011 + RM-SHR-024/025 + IDX-SHR-004 + DyeLotPort，窗口配置化，Showroom 视图与购物车/结算同源同口径）
- [x] 5 张表完整 DDL；事务边界与 showroom-api-detail TX 引用一一对应；状态机 CAS 全写路径承载（assign/remind/order 三组条件更新）
