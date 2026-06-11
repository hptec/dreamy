# showroom 测试设计（L2）

> 角色: l2_test_designer ｜ change: portal-api-integration ｜ domain: showroom
> 多层骨架：单元(UT) / 集成(IT，DB+事务+MQ) / 状态机(SM) / 契约(CT) / API 端到端(AT) / 前端组件(FCT) / 韧性(RST) / 网络边界(NBT)，统一编号 **TC-SHR-NNN**，AAA 伪代码骨架 + 数据工厂 + P0~P3。
> 覆盖来源：①field-constraint-test-matrix.yml —— **本域无行**（矩阵仅含 FLD-CUSTOMERS-*，归 identity/trading；本域字段约束以 boundary-scenarios er 行为权威源，显式声明无遗漏）；②boundary-scenarios.yml 本域场景（null 族 bs-351~374、extreme 族 bs-535~542、concurrent 族 bs-602~604、auth 族 bs-638~640、network 族 bs-670~672、integrity 族 bs-721~730、state 族 bs-832~841，逐条映射见第 10 节）；③state-machine.yml showroom_member_assignment（6 转换 + guard）；④showroom-api-detail E-SHR-01~13 与 11 个域错误码 + 透传 404501。

## 1. 单元测试（领域纯逻辑）

| TC | 内容（AAA） | 溯源 | P |
|---|---|---|---|
| TC-SHR-001 | guest 操作白名单匹配器：`GET:/api/store/showrooms/{id}` 命中；`PUT .../vote`、`POST .../comments` 命中；GET 列表/POST items/DELETE/invite/reset/members 全部不命中；METHOD:pattern 解析与公开白名单共用实现（method 缺省全匹配向后兼容） | 0.2-b；REV-IMPL-FILTER 兼容 | P0 |
| TC-SHR-002 | guest JWT claims 构造与解析：typ=guest/sub=member_id/showroom_id/member_id/inv_ver 齐全；TTL=配置值；storeKey 签名可被 store 解析器拒绝（typ≠store）；过期 token 读 claims 分型（typ=guest→401101 路径，typ=store→40100 路径） | 0.2-1/④ | P0 |
| TC-SHR-003 | color 归一化：null/''/'  ' → 落库 ''；'Dusty Rose' trim 原样；出参空串省略字段 | CV-SHR-003；MAP-SHR-004 | P1 |
| TC-SHR-004 | dye lot 窗口纯函数：last_ordered_at=now-23h59m → true；now-24h01m → false；NULL → false；窗口配置 48h 时 now-30h → true | CV-SHR-011 | P0 |
| TC-SHR-005 | owner 自动建 member 昵称规则：getUserName 截断 32；uk 冲突追加 `#`+subject 末 4 位后 ≤32；二次确定性（同输入同输出） | E-SHR-10 STEP-SHR-03 | P1 |
| TC-SHR-006 | 字段边界族：name 65 拒/64 过（bs-535/353）；nickname 33 拒/32 过、trim 空拒（bs-538/362）；content 501 拒/500 过、trim 空拒（bs-542/374）；email 255 拒/非法格式拒（bs-539/363）；color 65 拒（bs-537/359）；invite_token 65 拒（bs-536/355）；vote `__invalid__` 拒（bs-541/370）；wedding_date 非法日期拒——全部 422101 fields 结构断言 | V-SHR-001~023；CV-SHR-001/002 | P0 |
| TC-SHR-007 | 邮件事件 payload 构造：showroom.invite/remind 字段齐全（event_id UUID/invite_url 拼装/locale 透传/occurred_at）；日志输出断言 invite_url 与 token `[REDACTED]` | EVT-SHR-001/002；脱敏规则 | P1 |
| TC-SHR-008 | 邀请触发条件：assign 首填 email → 发 invite；email 变更 → 发；email 未提供（COALESCE 保留）→ 不发；同值重提 → 不发 | E-SHR-12 STEP-SHR-04 | P0 |

## 2. 集成测试（DB + 事务 + MQ）

| TC | 内容 | 溯源 | P |
|---|---|---|---|
| TC-SHR-009 | TX-SHR-001 创建：invite_token 为合法 UUID 且 uk 唯一、invite_version=1；响应含 invite_token 且 items/members 空 | E-SHR-01 | P0 |
| TC-SHR-010 | **并发昵称唯一**：同 token 同昵称并发双 guest-session → 仅 1 行 member 落库（uk_sm_room_nickname 兜底），两请求均 200 且 member.id 相同（冲突回读复用裁决）；不同昵称并发 → 2 行 | E-SHR-07 STEP-SHR-04；CV-SHR-004；bs-362 | P0 |
| TC-SHR-011 | 受保护昵称 409101：member 已绑定 linked_customer_id=U1 → 匿名以同昵称 guest-session → 409101；U1 本人带 store token 重放 → 200 复用；未绑定昵称 → 200 复用 | E-SHR-07 STEP-SHR-03；CV-SHR-009 | P0 |
| TC-SHR-012 | 绑定回填：登录态（store token）guest-session → member.linked_customer_id=subject；重放幂等同值；已绑定他人 → 409101 不覆盖 | E-SHR-07 STEP-SHR-05；RM-SHR-041 | P0 |
| TC-SHR-013 | **invite 重置级联失效**：reset 后 ①旧 token guest-session → 410101（invite_token_prev 命中）②新 token → 200 ③重置前签发的 guest JWT 访问详情 → 401101（inv_ver 不等）④新会话 JWT → 200 ⑤再次 reset 后上一代 prev 被覆盖（仅单代）；invite_version 自增断言 | E-SHR-06；0.2-d；CV-SHR-008 | P0 |
| TC-SHR-014 | **投票 PUT 幂等**：同 member 同 item 连投 like×3 → 1 行、like_count=1；改投 dislike → 仍 1 行、vote 覆盖、聚合 {0,1}；并发同 member 双投（like+dislike）→ 1 行终值为其一、计数总和=1（uk + UPSERT 仲裁） | E-SHR-10 STEP-SHR-04；RM-SHR-050；CV-SHR-005 | P0 |
| TC-SHR-015 | owner 首次互动自动建 member：owner 投票 → member 自动创建（linked_customer_id=owner、nickname=用户名）且 vote 落该 member；再次互动复用不重建；昵称被访客占用 → 后缀重试成功 | E-SHR-10/11 STEP-SHR-03 | P0 |
| TC-SHR-016 | 添加款式 uk：同房同 product+color 二次添加 → 409102；同 product 不同 color → 201；color 未选（''）与显式同色并发双加 → 仅 1 行 | E-SHR-08；IDX-SHR-003 | P0 |
| TC-SHR-017 | TX-SHR-007 移除款式级联：被指派成员（assigned/reminded）回 unassigned + assigned_item_id NULL；ordered 成员保持 ordered 仅清引用；该 item 的 votes/comments 全删；其余 item 数据不受影响 | E-SHR-09 STEP-SHR-03/04 | P0 |
| TC-SHR-018 | TX-SHR-003 删除 Showroom 级联：5 表全清无残留；删除后 guest JWT 访问 → 401101（行不存在）；中途注入失败 → 整体回滚无半删 | E-SHR-05；EC-SHR-001 | P0 |
| TC-SHR-019 | CAS 指派并发：unassigned 行并发双 assign → 双方 affected 由 CAS 仲裁（终态 assigned 且 item 为其一，bs-602）；assigned 行并发 reassign+remind → 仅一方成功（bs-603）；reminded 行并发 reassign+（消费者）place_order → 仅一方成功（bs-604） | TX-SHR-010/011/012；RM-SHR-035/036/037 | P0 |
| TC-SHR-020 | **order.paid 消费推进**：绑定成员 + assigned_item 命中订单行 → assign_status=ordered；订单行不含被指派款式 → 不推进（bs-836/837 guard）；未绑定成员（linked_customer_id NULL）→ 不可达不推进；已 ordered 重放 → affected=0 幂等；同 event_id 重投 → SETNX 拦截空操作 | EVT-SHR-003 ①②；TX-SHR-012 | P0 |
| TC-SHR-021 | **dye lot 回写与判定**：order.paid（参与房含同款式）→ last_ordered_at 更新 → 详情 dye_lot_notice=true；24h 后（窗口外造数）→ false；非参与房不回写；DyeLotPort.hintProductIds 返回命中 product_ids、无参与/无命中空数组——与详情判定同源一致 | EVT-SHR-003 ③；RM-SHR-024/025；CV-SHR-011 | P0 |
| TC-SHR-022 | 提醒 guard：unassigned → 409103 details.reason=not_assigned；assigned 无 email → 409103 details.reason=email_missing；assigned 有 email → 200 reminded + MQ showroom.remind 发布断言；reminded 重发 → 200 保持 reminded（自环）再发一条事件 | E-SHR-13；RM-SHR-036；bs-835 | P0 |
| TC-SHR-023 | 邮件事件 → MailRecord 对接联测：showroom.invite → type=showroom_invite、showroom.remind → type=showroom_assign；event_id 幂等键（同 event 重投不重发）；locale 透传渲染 | EVT-SHR-001/002；FLOW-P11 | P0 |
| TC-SHR-024 | 逻辑外键：product 不存在/draft 添加款式 → 404501 透传（bs-722）；item/member 跨房 id（属另一 showroom）→ 404102/404103（bs-723/725/726）；vote/comment 的 member_id/showroom_item_id 仅取主体与 path（请求体夹带字段被忽略，bs-727~730 不可达断言） | CV-SHR-006 | P0 |
| TC-SHR-025 | 不缓存断言：详情/投票/留言响应头 `Cache-Control: private, no-store`；写后立读即时可见（无 JetCache 介入，投票后另一身份立刻读到新计数） | CACHE-SHR-001 | P1 |
| TC-SHR-026 | 列表派生计数：含 0/多 items/members 的房 → item_count/member_count 精确；仅返回 owner 自己的房（他人房不可见） | E-SHR-02；RM-SHR-009 | P1 |

## 3. 状态机测试（showroom_member_assignment 全转换 + guard）

| TC | 内容 | 溯源 | P |
|---|---|---|---|
| TC-SHR-027 | unassigned→assigned（assign，owner+item 属本房）；guard 拒绝：非 owner → 404101（跨用户）/403102（guest）、item 不属本房 → 404102（bs-832） | E-SHR-12；state-machine assign | P0 |
| TC-SHR-028 | assigned→assigned（reassign 覆盖改派）；reminded→assigned（reassign 后提醒状态重置——改派后 assign_status 回 assigned 断言）；guard 拒绝同 TC-SHR-027（bs-833/834） | E-SHR-12；reassign | P0 |
| TC-SHR-029 | assigned→reminded（send_reminder，email 非空 + MQ 投递断言）；guard 拒绝：email 空 409103（bs-835）；reminded 重发自环（契约口径定稿断言：状态保持 + 事件再发） | E-SHR-13 | P0 |
| TC-SHR-030 | assigned|reminded→ordered（place_order，经 q.showroom 消费）；guard 拒绝：未登录绑定/订单行不含指派款式不推进（bs-836/837） | EVT-SHR-003 | P0 |
| TC-SHR-031 | 非法事件拒绝：ordered 后 assign/remind → 409103（bs-841）；unassigned remind → 409103（bs-838 同型）；assigned/reminded 重复合法自环外的越序操作全拒（bs-839/840） | CV-SHR-010 | P0 |

## 4. 契约测试（CT）

| TC | 内容 | P |
|---|---|---|
| TC-SHR-032 | 13 端点请求/响应 schema 对齐 showroom-api.openapi.yml（含 R 包络映射：契约建模 data 载荷、线上 {code,message,data}；ShowroomDetail allOf 平铺） | P0 |
| TC-SHR-033 | **视图裁剪断言**：owner 详情含 invite_token + members[].email/linked_customer_id；guest 详情三者全不出现（字段不存在而非 null）；Summary 无 invite_token；GuestSession.member 不含 linked_customer_id | P0 |
| TC-SHR-034 | 11 个域错误码逐一断言 code↔HTTP（401101/403101/403102/404101/404102/404103/409101/409102/409103/410101/422101）+ details 结构（422101 fields 字典、409103 reason 二分）+ 透传 404501 | P0 |
| TC-SHR-035 | 枚举前向兼容：assign_status/vote 新增枚举值时既有反序列化不崩（@JsonEnumDefaultValue 兜底，review TC-REV-032 同型）；dye_lot_notice 缺省 false 容忍 | P2 |

## 5. API 端到端（AT）

| TC | 内容 | 溯源 | P |
|---|---|---|---|
| TC-SHR-036 | **双态鉴权矩阵穷举**（13 端点 × 5 身份，0 节矩阵逐格）：匿名（仅 guest-session 200，余 401 40100）/guest 绑定本房（3 端点放行，余 403102）/guest 他房 token（{id} 不等全 403102）/owner（全通）/其他登录用户（创建与列表 200 己态，余 404101）——65 格断言 | §0 矩阵；bs-638/640 | P0 |
| TC-SHR-037 | **guest 全旅程 E2E（FLOW-P12）**：owner 建房→加 2 款式→复制邀请链接→匿名凭 token+昵称换 guest JWT（200 GuestSession）→guest 读详情（无 invite_token）→投票 like→留言→owner 视图实时见计数与留言→owner 指派该 guest（email）→invite 邮件事件→remind→reminded→guest 登录（绑定回填）→下单支付（order.paid 桩）→ordered + dye_lot_notice=true | TASK 级流程面 | P0 |
| TC-SHR-038 | **invite 失效 E2E**：reset → 旧链接 guest-session 410101 专属提示语义；旧 guest JWT 任意 3 个放行端点全 401101；新链接全流程恢复 | E-SHR-06/07 | P0 |
| TC-SHR-039 | 防探测：他人 showroom id 的 GET/PUT/DELETE/items/members 全 404101（响应体不泄露存在性差异——与不存在 id 同响应）；guest token 用于 /api/store/cart 等非 showroom 路径 → 401 40100 | CV-SHR-007；0.2-③a；bs-640 | P0 |
| TC-SHR-040 | 投票/留言双身份等价：owner 与 guest 各自投票留言按各自 member 去重；my_vote 按身份正确区分；vote 后响应聚合与详情聚合一致 | E-SHR-10/11 | P0 |
| TC-SHR-041 | null 族穷举（bs-351~374 提交面收口）：必填缺失（name/invite_token/nickname/product_id/vote/content/assigned_item_id）→ 422101；服务端生成字段（owner_id/invite_token/assign_status/member_id/id）客户端夹带被忽略（bs-352/355/361/365/366/369/373 提交面不可达→「忽略未知字段」断言）；可选字段缺省通过（wedding_date/color/email，bs-354/359/363） | boundary null 族 | P0 |
| TC-SHR-042 | guest-session 边界：token 不存在 401101；email 格式假 token（非 UUID 形态）401101 同口径不泄露；nickname 大小写敏感性定稿断言（utf8mb4_0900_ai_ci 不区分大小写 → 'Emma'/'emma' 视为同昵称复用） | E-SHR-07 | P1 |
| TC-SHR-043 | 婚期联动：PUT 改 wedding_date → 详情/列表即时新值（不缓存）；清空（null）→ 字段消失；结算带入 selector 数据源正确（前端联测衔接 FORM-SHR-S10） | E-SHR-04 | P1 |

## 6. 前端组件测试（FCT，断言逻辑非视觉——视觉归 ui-test-spec）

| TC | 内容 | P |
|---|---|---|
| TC-SHR-044 | 列表页：未登录跳登录带 returnTo；创建成功 unshift；删除二次确认 Delete/Keep；空态渲染 | P0 |
| TC-SHR-045 | 详情三态守卫：owner token → owner 视图（管理操作可见）；guest 会话 → guest 视图（无邀请条/无成员表管理列）；带 invite 未登录 → 加入态；404101 → 通用无权页 | P0 |
| TC-SHR-046 | GuestJoinGate：nickname trim js_guard；409101 行内换昵称；410101/401101 专属提示卡；成功存 localStorage 分房键并进 guest 视图 | P0 |
| TC-SHR-047 | 投票交互：乐观高亮 + 响应聚合覆盖；失败回滚；guest 未加入点击引导加入；owner 可投（偏离⑤断言） | P0 |
| TC-SHR-048 | 成员表：指派下拉 + email 输入预校验；409103 toast + refetch；remind 按钮显隐条件（assigned|reminded 且 email 非空）；Reminded 四态徽章 | P0 |
| TC-SHR-049 | InviteLinkBar：链接拼装含真实 token；Copy 反馈；Reset 二次确认 → 新 token 即时替换 | P1 |
| TC-SHR-050 | AddToShowroomModal：未登录入口跳登录；房列表加载；409102 → Saved 容错 toast；Create & Add 串联两请求 | P1 |
| TC-SHR-051 | guest 会话生命周期：expiresAt 过期自动 rejoin 一次；rejoin 失败回加入态；登录成功钩子对全部缓存房静默重放绑定（FORM-SHR-S08）；网络中断恢复后 refetch 重同步（bs-672） | P0 |
| TC-SHR-052 | dye lot 条：items 任一 dyeLotNotice → 金底提示条出现；全 false 隐藏；命中卡片角标 | P1 |

## 7. 韧性测试（RST，BE-DIM-5）

| TC | 内容 | 溯源 | P |
|---|---|---|---|
| TC-SHR-053 | SMTP 不可用：remind 仍 200（reminded 已落库）、MailRecord failed→重试×3→dead 告警，主流程不阻塞（bs-670）；MQ 重投同 event_id 不重发邮件（bs-671 幂等） | EVT-SHR-002；FLOW-P11 | P0 |
| TC-SHR-054 | MQ publish 失败：assign/remind 本地事务不回滚（状态已推进）、告警日志、用户可重触发重发（EC-SHR-002）；q.showroom 消费异常 → nack 重试 5s/30s/180s ×3 → dreamy.dlq 告警 + SETNX 键释放允许重放 | EC-SHR-002；EVT-SHR-003 ④ | P0 |
| TC-SHR-055 | order.paid 乱序/迟到：dye lot 回写晚于 24h 提交（窗口已无意义）→ 回写仍执行但 notice 判定按当前时刻正确；消费与 owner 同时改派竞争 → CAS 仲裁无脏态（TC-SHR-019 互补） | TX-SHR-012 | P1 |
| TC-SHR-056 | Redis 不可用：SETNX 幂等闸降级——消费暂停 nack 重试（不绕过幂等闸盲消费）；guest 链路不受影响（无 Redis 依赖） | EVT-SHR-003 ① | P2 |

## 8. 网络边界测试（NBT，强制——5173 ↔ backend 前后端分离）

| TC | 内容 | P |
|---|---|---|
| TC-SHR-057 | CORS Preflight：OPTIONS /api/store/showrooms 与 .../vote 返回正确 ACAO/Methods/Headers（含 **PUT/DELETE**——本域齐用，易漏） | P0 |
| TC-SHR-058 | **method-aware 白名单安全回归**：`POST /api/store/showrooms/guest-session` 匿名 200；**`GET /api/store/showrooms` 匿名 401**（同前缀不同路径不放行）；白名单未过度放行其他 store 端点（review TC-REV-054 同型互补） | P0 |
| TC-SHR-059 | guest 旁路边界：guest token 打 /api/store/wishlists → 401 40100；guest token 打本房 POST items → 403102（作用域内操作白名单外）；Authorization 缺失打 /showrooms/{id} → 401 40100；伪造 typ=guest 但 admin key 签名 → 401 40100 | P0 |
| TC-SHR-060 | 跨域实际请求：5173 匿名 guest-session 成功；5173 带 guest token 三放行端点成功；带 store token 全动词成功 | P0 |

## 9. 测试数据工厂（FACTORY-SHR）

- F-Showroom（含/无 wedding_date；invite_version 1/N 变体；invite_token_prev 有/无——重置驱动）
- F-ShowroomItem（color ''/具体色；last_ordered_at NULL/窗口内/窗口外梯度——dye lot 驱动；同房多款式）
- F-ShowroomMember（四态 assign_status 全象限 × email 有/无 × linked_customer_id 绑定/未绑定——guard 与 409101 驱动；昵称大小写/多语言字符变体）
- F-ShowroomVote（like/dislike 分布集——聚合断言驱动，如 [like×3, dislike×1] → {3,1}）
- F-ShowroomComment（长度边界 1/500 变体；多 member 混排时序）
- F-GuestJwt（有效/过期/inv_ver 落后/伪造签名/typ 篡改 五变体——过滤器分型驱动）
- F-OrderPaidEvent（lines 命中/不命中指派款式；重复 event_id；多行多商品——消费推进与 dye lot 驱动）
- F-StoreToken（owner/他人/已绑定访客本人 三变体）

## 10. 优先级矩阵与覆盖核对

| 优先级 | 用例 |
|---|---|
| P0 | TC-SHR-001/002/004/006/008/009~024/027~034/036~041/044~048/051/053/054/057~060 |
| P1 | TC-SHR-003/005/007/025/026/042/043/049/050/052/055 |
| P2 | TC-SHR-035/056 |

- [x] field-constraint-test-matrix：本域 0 行（仅 FLD-CUSTOMERS-*，归属他域）——已显式核对，无漏测项
- [x] boundary-scenarios 本域全映射：null 族 bs-351~374 → TC-SHR-006/041；extreme 族 bs-535~542 → TC-SHR-006；concurrent 族 bs-602~604 → TC-SHR-019；auth 族 bs-638~640 → TC-SHR-036/039（bs-639 会话内权限撤销 → 本域无 RBAC，对应 guest 会话失效 TC-SHR-013/038 + store 会话撤销走 identity 既有 SessionValidator 断言）；network 族 bs-670~672 → TC-SHR-053/051；integrity 族 bs-721~730 → TC-SHR-024（bs-721/724 不可达声明）；state 族 bs-832~841 → TC-SHR-027~031
- [x] 状态机 showroom_member_assignment 6 转换全迁移 + guard 拒绝 + 并发 + 自环/终态（TC-SHR-027~031）；place_order 消费侧推进（TC-SHR-020/030）
- [x] 11 个域错误码 + 透传 404501 全部出现在至少一个 TC；**双态鉴权矩阵 65 格穷举**（TC-SHR-036）为本域 P0 之首；越权防探测（TC-SHR-039）、invite 失效双通道（TC-SHR-013/038）、投票幂等（TC-SHR-014）、并发昵称（TC-SHR-010/011）四大重点全覆盖
- [x] method-aware 白名单 + guest 旁路安全回归（TC-SHR-058/059）落实 error-strategy L2 要求 2 测试面；order.paid 消费与 trading EVT-TRD-001 对接联测（TC-SHR-020/021）+ 邮件事件与 q.mail/MailRecord 对接联测（TC-SHR-023）落实本域两大跨域协作
