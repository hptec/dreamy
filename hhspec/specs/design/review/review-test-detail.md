# review 测试设计（L2）

> 角色: l2_test_designer ｜ change: portal-api-integration ｜ domain: review
> 多层骨架：单元(UT) / 集成(IT，DB+事务+缓存+MQ) / 状态机(SM) / 契约(CT) / API 端到端(AT) / 前端组件(FCT) / 韧性(RST) / 网络边界(NBT)，统一编号 **TC-REV-NNN**，AAA 伪代码骨架 + 数据工厂 + P0~P3。
> 覆盖来源：①field-constraint-test-matrix.yml —— **本域无行**（矩阵仅含 FLD-CUSTOMERS-001/010，归 identity/trading 域；本域字段约束以 boundary-scenarios er 行为权威源，显式声明无遗漏）；②boundary-scenarios.yml 本域场景（null 族 bs-234~257、extreme 族 bs-508~511、concurrent 族 bs-591~597、auth 族 bs-616~618、integrity 族 bs-702~704，逐条映射见第 10 节）；③state-machine.yml 本域 3 状态机（review_moderation/review_image_visibility/question_answer_flow，TASK-047/048/049 验收准则逐条）；④review-api-detail E-REV-01~15 与 10 个域错误码 + 透传 404501。

## 1. 单元测试（领域纯逻辑）

| TC | 内容（AAA） | 溯源 | P |
|---|---|---|---|
| TC-REV-001 | 姓名脱敏规则：`Madison Reyes`→`Madison R.`；单段 `Madison`→原样；空快照→`Guest`；多段取首段+末段首字母 | MAP-REV-001/004 | P1 |
| TC-REV-002 | 批量 action 语义矩阵：approve×{pending→更新, rejected→更新, approved→skipped}；reject×{pending/approved→更新且 featured 清零, rejected→skipped}；feature×{approved+未精选→更新, approved+已精选→skipped, pending/rejected→skipped}；unfeature×{featured=1→更新, featured=0→skipped}；不存在 id→skipped | E-REV-09 STEP-REV-02；state-machine batch_* | P0 |
| TC-REV-003 | 排序映射：featured_first=featured DESC+submitted_at DESC；newest/rating_desc/rating_asc 各自正确；缺省 featured_first | V-REV-002；E-REV-01 STEP-REV-02 | P1 |
| TC-REV-004 | 聚合计算：avg HALF_UP 2 位（如 4.333→4.33）；breakdown 1..5 全档（无数据档=0）；零评价 avg=0/count=0 | RM-REV-002 | P0 |
| TC-REV-005 | images url 白名单：本站 public_url + review/ 前缀通过；外链/其他 scope 前缀（product/）拒绝 422801；>9 张拒绝 | V-REV-007/CV-REV-008 | P0 |
| TC-REV-006 | 字段边界族：rating 0/6 拒绝、1/5 通过（bs-508/509）；status 枚举外 `__invalid__` 拒绝（bs-510）；visible 枚举外拒绝（bs-511）；question 1001/reply·answer 2001/content 5001 超长拒绝；trim 空 reply/answer/question 拒绝（422801） | CV-REV-001/002/003/006 | P0 |
| TC-REV-007 | presign 入参：file_name sanitize（路径穿越剥离）；MIME 三白名单通过、video/mp4 与 image/gif 拒绝（store 与 admin presign 白名单差异断言） | V-REV-012/013 | P1 |

## 2. 集成测试（DB + 事务 + 缓存 + MQ）

| TC | 内容 | 溯源 | P |
|---|---|---|---|
| TC-REV-008 | TX-REV-001 提交评价原子：review+images 批插任一失败整体回滚无脏行；成功后 status=pending/featured=0/submitted_at 落库 | E-REV-02 | P0 |
| TC-REV-009 | **403801 越权防护**：无任何订单 / 订单 paid 未 completed / completed 订单不含该商品 → 403801；completed 含该商品 → 201（TradingPurchaseQueryPort 桩四态；跨 trading 领域服务接口调用断言——禁止直查 orders 表） | E-REV-02 STEP-REV-01；s-756/s-762；bs-616 同型 | P0 |
| TC-REV-010 | 409801 重复评价：先后两次提交同商品 → 第二次 409801；**并发双提交**唯一索引 uk_review_user_product 兜底仅一行落库 | E-REV-02 STEP-REV-02；CV-REV-004 | P0 |
| TC-REV-011 | CAS 审核并发：pending 行并发 approve+reject 仅一方 affected=1，另一方 409802（bs-591）；rejected 行单条审核 → 409802 | TX-REV-002；RM-REV-008 | P0 |
| TC-REV-012 | reject 强制 featured=false：精选行被批量拒绝 → status=rejected 且 featured=0（bs-593 互斥并发：batch_reject 与 set_featured 仅一方成功） | CV-REV-007；state-machine guard | P0 |
| TC-REV-013 | 精选 guard：pending/rejected 设精选 → 409803；approved → 成功；同值幂等短路（不写审计不发事件）；并发双 set_featured 仅一次副作用（bs-592 同型） | TX-REV-003；RM-REV-009 | P0 |
| TC-REV-014 | 回复 guard：非 approved → 409804；approved → reply_author 取配置缺省 "Dreamy Team" + reply_time 服务端生成；编辑覆盖；删除清空三字段；无回复删除幂等 204 | TX-REV-005/006 | P0 |
| TC-REV-015 | 图片驳回/恢复：rejected 翻转落库；imageId 不属于该 review → 404803；并发重复驳回/恢复幂等只执行一次副作用（bs-594/595） | TX-REV-007 | P0 |
| TC-REV-016 | Q&A 首次回答：answer NULL→非空 时 visible 自动置 visible + answer_time 生成；编辑回答（answered→answered）visible 保持手动设定值不被覆盖；并发双 save_answer/edit_answer 幂等（bs-596/597） | TX-REV-008；E-REV-14 STEP-REV-02 | P0 |
| TC-REV-017 | store 读过滤口径：仅 approved 评价入列；rejected 图不入 store images、admin 全量返回；Q&A 仅 visible+answered 入列（visible 未回答不出前台） | RM-REV-001/020/030；CV-REV-009 | P0 |
| TC-REV-018 | 缓存命中/失效链：E-REV-01 首读落库写缓存→二读命中；审核后 `review:reviews:{pid}:*` 全 sort/page 失效→读到新值；E-REV-14 后 questions 同理；空页 cacheNullValue 缓存（不存在 product_id 二读不触 DB） | CACHE-REV-001/002；CP-031 | P0 |
| TC-REV-019 | **review.moderated 与 catalog 对齐**：审核 approve 后事件发布（event_id/product_id 载荷断言）→ catalog q.catalog.rating 消费 → ReviewQueryPort.approvedRatingSummary 回查 → Product.rating_avg/rating_count 覆盖写正确；同 event_id 重复投递幂等空操作；批量 approve 跨 2 商品 → 按 product_id 去重发 2 条 | EVT-REV-001；EVT-CAT-002 对接联测 | P0 |
| TC-REV-020 | content.invalidated 发布：8 个写端点（E-REV-07/08/09/10/11/12/14/15）提交后各发一次，type=review_changed/question_changed、slug、locales×3 正确；精选/回复/图片/可见性**不发** review.moderated（rating 不变） | EVT-REV-002 | P0 |
| TC-REV-021 | 逻辑外键：product 不存在/draft 提交评价、提问 → 404501 透传（bs-702/704）；review_image 仅经聚合根事务写入（无独立 API 入口，repo 层断言 bs-703 不可达路径） | CV-REV-005 | P0 |
| TC-REV-022 | OperationLog：评价审核（含归入项：精选/回复/图片驳回 changes 子类型）、评价批量操作（updated_ids/skipped_ids 入 changes）、回答提问（含可见性归入）三 action 断言；store 提交端点不写审计 | BE-DIM-7；横切 0 节归入规则 | P1 |
| TC-REV-023 | ReviewQueryPort 口径一致性：同一商品 approvedRatingSummary 与 E-REV-01 rating_avg/rating_count 同源相等（含 rejected/pending 不计入） | EVT-REV §8.2；RM-REV-002 | P1 |

## 3. 状态机测试（state-machine.yml 本域 3 机全迁移；TASK-047/048/049 验收准则逐条）

| TC | 状态机 | 内容 | 溯源 | P |
|---|---|---|---|---|
| TC-REV-024 | review_moderation | pending→approved（approve）；pending→rejected（reject，featured 强制 0）；pending guard 失败拒绝（status 枚举外 422801 / 非 pending 409802） | TASK-047 准则 1~3 | P0 |
| TC-REV-025 | review_moderation | rejected→approved（batch_approve 不限当前状态）；approved→rejected（batch_reject + featured 清零）；approved guard 失败（feature 已精选→skipped；单条审核 approved 行→409802） | TASK-047 准则 4~6；bs-592/593 | P0 |
| TC-REV-026 | review_moderation | approved→approved（set_featured/unset_featured 自环）；guard 失败（pending 设精选 409803）；「评价提交后审核通过/驳回」全流程（TASK-055 流程面） | TASK-047 准则 7~10 | P0 |
| TC-REV-027 | review_image_visibility | shown→rejected（reject_image）；rejected→shown（restore_image）；同值幂等 | TASK-048 准则 1~2 | P0 |
| TC-REV-028 | question_answer_flow | unanswered→answered（save_answer + visible 自动翻转）；unanswered guard 失败（trim 空 422801）；answered→answered（edit_answer）；answered guard 失败（trim 空） | TASK-049 准则 1~4 | P0 |

## 4. 契约测试（CT）

| TC | 内容 | P |
|---|---|---|
| TC-REV-029 | 15 端点请求/响应 schema 对齐 review-api.openapi.yml（含 R 包络映射：契约建模 data 载荷、线上 {code,message,data}） | P0 |
| TC-REV-030 | **Paginated 子类平铺断言**：StoreReviewListResponse=六分页字段+rating_avg/rating_count/rating_breakdown **同层无嵌套**；AdminReviewListResponse=六字段+pending_count 同层；StoreQuestion/AdminQuestion 列表为标准六字段（MAP-REV-006/007 序列化形状回归） | P0 |
| TC-REV-031 | 10 个域错误码逐一断言 code↔HTTP（403801/404801/404802/404803/409801/409802/409803/409804/422801/502801）+ details 结构（422801 fields 字典）+ 透传 404501 | P0 |
| TC-REV-032 | 枚举前向兼容：status/visible 新增枚举值时既有反序列化不崩（@JsonEnumDefaultValue 兜底，catalog TC-CAT-044 同型） | P2 |

## 5. API 端到端（AT，按端点族 + 错误路径穷举）

| TC | 内容 | 溯源 | P |
|---|---|---|---|
| TC-REV-033 | store 读链路：列表四 sort 枚举各序正确 + 分页边界（page_size=100 通过/101 拒绝/page=0 拒绝）+ 仅 approved + 图片过滤 + 聚合三字段值正确 + 脱敏姓名 | E-REV-01 | P0 |
| TC-REV-034 | **写评价 E2E（FLOW-P14 全链）**：登录→造 completed 订单→presign→PUT 直传→提交（201 pending）→store 列表不可见→admin 审核通过→store 列表可见+rating 回写（catalog Product 冗余列）+PDP revalidate 回调断言 | TASK-055；FLOW-P14 | P0 |
| TC-REV-035 | Q&A E2E：登录提问（201 hidden）→前台不可见→后台回答（自动 visible）→前台可见（answer 非空）→后台隐藏→前台消失 | E-REV-04/14/15 | P0 |
| TC-REV-036 | 批量 E2E：混合状态 ids（pending/approved/rejected/精选/不存在）×四 action → updated_ids/skipped_ids 精确匹配 TC-REV-002 矩阵；ids 空 422801；ids>200 422801 | E-REV-09 | P0 |
| TC-REV-037 | admin 筛选族：status×rating×featured×product_id×search 组合正确；「精选」chip 语义（status=all&featured=true）；pending_count 不随筛选变化 | E-REV-06 | P0 |
| TC-REV-038 | null 族穷举（bs-234~257）：评价提交必填 product_id/rating 缺失→422801（bs-235/238 提交面）、user_id 由 JWT 注入不可缺（bs-236 语义=无 token 401）、可选 content/images 缺省通过（bs-239 等）；提问必填 question/product_id（bs-253/251）、可选 asker 服务端快照（bs-252）；状态/时间字段服务端生成不接收客户端值（bs-240/242/245/254/256/257 提交面不可达→以「忽略未知字段」断言收口） | boundary null 族 | P0 |
| TC-REV-039 | 鉴权矩阵：匿名 GET reviews/questions 200（白名单）；POST reviews/questions/presign 无 token 401(40100)；store token 打 /api/admin/reviews 401；admin 无 /reviews 权限 key 403(40300)（bs-616）；会话内权限被撤销后续请求 403（bs-617） | 0.1 节；BE-DIM-6 | P0 |
| TC-REV-040 | 隔离与防探测：提交评价 body 夹带 user_id 字段被忽略（以 JWT subject 落库，bs-618 跨租户语义本域落点）；admin 操作不存在 id → 404801/404802 不泄露存在性 | BE-DIM-6 | P1 |
| TC-REV-041 | presign E2E：合法 MIME 200 四字段齐全 + object_key 含 review/ 前缀；非法 MIME/超长 file_name 422801 | E-REV-05 | P1 |
| TC-REV-042 | 失效链 E2E（契约「PDP 同步刷新」）：审核通过 → 5s 内 ①JetCache 新值 ②q.catalog.rating 收到 review.moderated ③q.invalidate 收到 content.invalidated 且 revalidate 回调含 3 locale 路径 ④purge 被调 | EVT-REV-001/002；FUNC-006 同口径 | P0 |

## 6. 前端组件测试（FCT，断言逻辑非视觉——视觉归 ui-test-spec）

| TC | 内容 | P |
|---|---|---|
| TC-REV-043 | Reviews.vue 评价 tab：chips 切换请求参数映射（精选→featured=true）；批量提交后 skippedIds toast 汇总+清选+refetch；409802 toast+refetch；操作列按状态条件渲染（pending=通过/拒绝，approved=精选） | P0 |
| TC-REV-044 | ReviewDrawer：非 approved 回复区占位文案；回复按钮 trim disabled；409804/422801 呈现；删除回复二次确认；Lightbox 驳回/恢复局部更新 images | P1 |
| TC-REV-045 | Q&A tab：answered chips 参数映射；Toggle 可见性乐观更新失败回滚；首答成功行内 visible 自动置开；回答 trim disabled | P1 |
| TC-REV-046 | ProductReviews（store）：rating_breakdown 百分比渲染（替换硬编码 dist）；翻页追加不重复；排序切换重置列表；空列表空态 | P0 |
| TC-REV-047 | WriteReviewModal：rating 未选预校验不发请求；images>9 阻止；403801 → 入口替换提示文案；409801 toast；提交成功确认条且列表不插本地行（pending 语义） | P0 |
| TC-REV-048 | AskQuestionModal：trim 空禁用；成功确认态不插列表；未登录点击入口跳登录（带 returnTo） | P1 |

## 7. 韧性测试（RST，BE-DIM-5）

| TC | 内容 | 溯源 | P |
|---|---|---|---|
| TC-REV-049 | S3 预签名不可达/超时 → 502801；前端上传卡片错误态、评价可不带图提交（降级路径） | 决策 9；FORM-REV-S03 | P0 |
| TC-REV-050 | MQ publish 失败：审核事务不回滚、JetCache 已失效、CDN TTL 兜底（告警日志断言）；rating 回写靠 EVT-CAT-003 每日任务最终收敛 | EC-REV-002 | P1 |
| TC-REV-051 | Redis 不可用：JetCache 降级本地 Caffeine + 直查 DB，store 读路径不 5xx | BE-DIM-8 | P2 |

## 8. 网络边界测试（NBT，强制——5173/5174 ↔ backend 前后端分离）

| TC | 内容 | P |
|---|---|---|
| TC-REV-052 | CORS Preflight：OPTIONS /api/store/reviews 与 /api/admin/reviews 返回正确 ACAO/Methods/Headers（含 **PATCH/PUT/DELETE**——本域三动词齐用，易漏） | P0 |
| TC-REV-053 | 跨域实际请求：5173 origin 匿名 GET 成功（无 Authorization 过 preflight）；5173 带 store token POST 成功；5174 带 admin token 全动词成功 | P0 |
| TC-REV-054 | **method-aware 白名单安全回归**：`GET /api/store/reviews` 匿名 200；**同路径 POST 匿名 401**；questions 同理；/api/store/uploads/presign 不在白名单（匿名 401）；白名单未过度放行其他 store 端点（catalog TC-CAT-081 同型互补） | P0 |

## 9. 测试数据工厂（FACTORY-REV）

- F-Review（pending/approved/rejected × featured 变体 × 带图/无图 × 带回复/无回复；customer_name 多段/单段/空快照变体——脱敏驱动；submitted_at 时间梯度——排序驱动）
- F-ReviewImage（rejected=0/1 混合，≤9 张梯度）
- F-ProductQuestion（unanswered+hidden / answered+visible / answered+hidden / unanswered+visible 四象限——双条件过滤驱动；question 长度边界变体）
- F-CompletedOrder（trading 侧：completed 含目标商品 / completed 不含 / paid 未完成 / 无订单——403801 四态桩）
- F-RatingSet（指定星级分布生成 approved 评价集——聚合断言驱动，如 [5,5,4,3,1] → avg 3.60）
- F-MqEvent（review.moderated 含重复 event_id 变体——幂等驱动）
- F-StoreToken / F-AdminToken（含/缺 /reviews 权限 key；跨端误用变体）
- F-PresignFile（合法三 MIME / video/mp4 / 路径穿越 file_name 变体）

## 10. 优先级矩阵与覆盖核对

| 优先级 | 用例 |
|---|---|
| P0 | TC-REV-002/004/005/006/008~021/024~031/033~039/042/043/046/047/049/052~054 |
| P1 | TC-REV-001/003/007/022/023/040/041/044/045/048/050 |
| P2 | TC-REV-032/051 |

- [x] field-constraint-test-matrix：本域 0 行（仅 FLD-CUSTOMERS，归属他域）——已显式核对，无漏测项
- [x] boundary-scenarios 本域全映射：null 族 bs-234~257 → TC-REV-006/038；extreme 族 bs-508~511 → TC-REV-006；concurrent 族 bs-591~597 → TC-REV-011/012/013/015/016；auth 族 bs-616~618 → TC-REV-009/039/040；integrity 族 bs-702~704 → TC-REV-021
- [x] 状态机 3 机全部迁移 + guard 拒绝 + 并发 + 自环幂等（TASK-047/048/049 验收准则逐条对应 TC-REV-024~028）
- [x] 10 个域错误码 + 透传 404501 全部出现在至少一个 TC；**403801 越权防护四态穷举**（TC-REV-009）为本域 P0 之首
- [x] method-aware 公开白名单安全回归（TC-REV-054）落实 error-strategy L2 要求 2 测试面；review.moderated 与 catalog EVT-CAT-002 对接联测（TC-REV-019）+ Paginated 子类序列化形状回归（TC-REV-030）落实本域两大定稿项
