# catalog 测试设计（L2）

> 角色: l2_test_designer ｜ change: portal-api-integration ｜ domain: catalog
> 多层骨架：单元(UT) / 集成(IT，DB+事务+缓存+MQ) / 契约(CT) / API 端到端(AT) / 前端组件(FCT) / 韧性(RST) / 网络边界(NBT)，统一编号 **TC-CAT-NNN**，AAA 伪代码骨架 + 数据工厂 + P0~P3。
> 覆盖来源：①field-constraint-test-matrix.yml —— **本域无行**（矩阵仅含 FLD-CUSTOMERS-001/010，归 identity/trading 域；本域字段约束以 boundary-scenarios er 行为权威源，显式声明无遗漏）；②boundary-scenarios.yml 本域场景（bs 编号逐条映射）；③state-machine.yml 本域 5 状态机（product/category/tag/tag_dimension/attribute_visibility）；④catalog-api-detail E-CAT-01~35 与 17 个域错误码。

## 1. 单元测试（领域纯逻辑）

| TC | 内容（AAA） | 溯源 | P |
|---|---|---|---|
| TC-CAT-001 | 尺码区间匹配：三围均落同行 → matched=true 且 recommended_row 正确 | E-CAT-05 STEP-03/05；s-1042 | P0 |
| TC-CAT-002 | 尺码跨码段：bust 落 US6、hips 落 US8 → 取大码 US8，dimension_notes 透出各维度 matched_us | 决策 20.3 | P0 |
| TC-CAT-003 | fit_preference 偏移：snug 下移一档不破下界；relaxed 上移一档不破上界；regular 不偏移 | V-CAT-016 | P1 |
| TC-CAT-004 | 任一维度超全表最大值 → matched=false + 建议话术 key（区别于 422502） | E-CAT-05 STEP-06 | P0 |
| TC-CAT-005 | 商品无尺码表 → matched=false（200，不报错） | E-CAT-05 STEP-02 | P0 |
| TC-CAT-006 | 体征越界（height=200in / bust=10in）→ 422502；缺必填/非数值 → 422501 | V-CAT-014/015；bs-433~439 数值族 | P0 |
| TC-CAT-007 | attr_overrides 合并：子分类 delta 覆盖父属性集三态；key 不属于生效集 → 校验失败 | V-CAT-047/CV-CAT-008 | P1 |
| TC-CAT-008 | 分类层级计算：根=1、子=parent+1；level=4 拒绝（409505 异常类型） | V-CAT-045；bs-391/392 | P0 |
| TC-CAT-009 | 推荐位规则纯函数：new_arrivals 时间倒序 / best_sellers sales_30d 倒序 / 全 0 回退 recommend / ymal 同品类±30% 价格段 / ctl 跨叶子分类 | E-CAT-03 STEP-02，决策 29 | P0 |
| TC-CAT-010 | SKU 矩阵提交校验：集内 sku_code 重复、(color,size) 重复、pattern 不匹配 → 各自字段错误 | V-CAT-032；bs-428~432 | P0 |
| TC-CAT-011 | compare_at js_guard：null 通过、=price 通过、<price 拒绝 | V-CAT-027；bs-412 | P0 |
| TC-CAT-012 | attribute_def options guard：select/multiselect 必填非空；text/toggle 提交 options 拒绝；译文 options 等长校验 | V-CAT-056/058；bs-394 | P1 |
| TC-CAT-013 | 翻译回退合并：es 命中附表覆盖、缺翻译回退 EN、fr 部分字段缺失逐字段回退 | 决策 13；MAP-CAT-002 | P0 |
| TC-CAT-014 | 主图不变量：gallery sort=0 至多一张；swatch 需 color_name | V-CAT-031/CV-CAT-010；bs-425~427 | P1 |
| TC-CAT-015 | presign 入参：file_name sanitize（路径穿越剥离）、MIME 白名单外拒绝、scope 缺省 product | V-CAT-069~071 | P1 |

## 2. 集成测试（DB + 事务 + 缓存 + MQ）

| TC | 内容 | 溯源 | P |
|---|---|---|---|
| TC-CAT-016 | TX-CAT-001 创建商品原子性：六表批插任一失败整体回滚；slug 唯一索引冲突 → 409501 无脏行 | E-CAT-09；bs-058~099 必填族 | P0 |
| TC-CAT-017 | TX-CAT-002 整单覆盖：images/size_chart/tag/translation DELETE+INSERT 后数据一致；缺席 SKU 被删、新 SKU version=0；冗余列（sales_30d/rating_*）不被覆盖 | E-CAT-11 STEP-04/05 | P0 |
| TC-CAT-018 | SKU version CAS：并发两编辑同 SKU，后者 affected=0 → 409508 整体回滚（前者生效） | RM-CAT-122/TX-CAT-002；bs-573/574 同型 | P0 |
| TC-CAT-019 | 删除守卫事务内复查：并发"挂商品+删分类" → 仅一方成功（409502 或商品 422501） | TX-CAT-008；bs-558 | P0 |
| TC-CAT-020 | 引用完整性（逻辑外键）：product.category_id / product_tag.tag_id / category.parent_id / category.attribute_set_id / attribute_set_item.attribute_id / tag.dimension_id 引用不存在 → 各端点拒绝 | CV-CAT-005；**bs-676~686 逐条** | P0 |
| TC-CAT-021 | 删除级联：删 tag → product_tag 摘除（bs-680 反向）；删商品 → 六子表清空；删属性集 → item 清空 | TX-CAT-003/011/020 | P1 |
| TC-CAT-022 | FULLTEXT 检索：EN 主表 ngram 命中 name/subtitle；es/fr 附表命中并 UNION；标签名命中经 product_tag 并入；仅 published 入结果 | RM-CAT-084/103/064；决策 17 | P0 |
| TC-CAT-023 | 缓存命中/失效链（JetCache）：E-CAT-04 首读落库写缓存→二读命中；编辑商品后 `catalog:product:{slug}:*` 全 locale 失效→读到新值 | CACHE-CAT-002/CP-031 | P0 |
| TC-CAT-024 | 穿透保护：不存在 slug 首读写 null 缓存（60s）→ 二读不触 DB 仍 404501 | BE-DIM-8 | P1 |
| TC-CAT-025 | 上下架失效扇面：toggle 后 products/product/reco/categories/tags 键全失效；MQ content.invalidated 载荷含 slug+locales×3 | TX-CAT-004/FLOW-P03 | P0 |
| TC-CAT-026 | EVT-CAT-001 销量回写：order.paid 消费 → sumPaidQty 重算 → sales_30d 更新 + reco 缓存失效；同 event_id 重复投递 → 幂等空操作 | MQ 拓扑 q.catalog.sales | P0 |
| TC-CAT-027 | EVT-CAT-002 评分回写：review.moderated → rating_avg/count 覆盖写 + product 缓存失效；幂等同上 | q.catalog.rating；FLOW-P14 | P0 |
| TC-CAT-028 | 消费失败重试 ×3 → dreamy.dlq 死信落地（告警钩子触发） | EVT 重试参数 | P1 |
| TC-CAT-029 | EVT-CAT-003 定时窗口刷新：30 天前订单滑出窗口 → 定时任务后 sales_30d 下降 | 决策 29 滚动窗口 | P1 |
| TC-CAT-030 | product_count 两口径：store 树/标签仅 published；admin 含 draft；自底向上累加正确 | RM-CAT-096/097/145 | P1 |
| TC-CAT-031 | 属性集矩阵全量重写原子：DELETE+INSERT 中途失败回滚保留旧矩阵 | TX-CAT-010 | P1 |
| TC-CAT-032 | 操作审计：本域 19 个 action 各写一次 operation_log（含 changes before/after；flags 归入"编辑商品"） | BE-DIM-7 | P1 |

## 3. 状态机测试（state-machine.yml 本域 5 机全迁移；TASK-034~037/040）

| TC | 状态机 | 内容 | 溯源 | P |
|---|---|---|---|---|
| TC-CAT-033 | product_lifecycle | draft→published（toggle）；published→draft；draft→deleted（删除成功） | TASK-040 | P0 |
| TC-CAT-034 | product_lifecycle | published→deleted 直删被拒 → 409509（guard"先下架"）；deleted 终态后任意操作 404501 | bs-775/776/777/778 | P0 |
| TC-CAT-035 | product_lifecycle | 同态幂等：published 重复 publish → 200 短路不写审计（publish_again 语义） | E-CAT-13 STEP-02；bs-776 | P1 |
| TC-CAT-036 | category_lifecycle | active→deleted 成功（count=0）；guard 不满足（product_count>0）拒绝保持 active | TASK-034；bs-744/745/746 | P0 |
| TC-CAT-037 | tag_lifecycle | enabled↔disabled 双向；enabled→deleted、disabled→deleted；deleted 后操作 404505 | TASK-035；bs-747/748/749 | P0 |
| TC-CAT-038 | tag_dimension_lifecycle | active→deleted（无标签）；有标签 guard → 409506 | TASK-036；bs-750/751 | P0 |
| TC-CAT-039 | attribute_visibility_cycle | hidden→visible→optional→hidden 整单提交循环各态落库正确 | TASK-037；bs-752/753/754 | P1 |
| TC-CAT-040 | 并发状态变更 | category 并发双 delete 仅一次副作用（bs-558）；tag 并发 toggle_off+delete 仅一方成功（bs-559/560）；dimension 并发双 delete（bs-561）；attribute_set_item 并发 cycle_click（整单保存互斥，bs-562/563/564）；product 并发 publish+delete / unpublish+delete（bs-573/574） | sm concurrent 族 | P0 |

## 4. 契约测试（CT）

| TC | 内容 | P |
|---|---|---|
| TC-CAT-041 | 35 端点请求/响应 schema 对齐 catalog-api.openapi.yml（含 R 包络映射：契约建模 data 载荷、线上 {code,message,data}）| P0 |
| TC-CAT-042 | 分页载荷 = huihao.page.Paginated 六字段（data/total_elements/page_number/page_size/number_of_elements/total_pages）snake_case | P0 |
| TC-CAT-043 | 17 个域错误码逐一断言 code↔HTTP（404501~404505/409501~409509/422501/422502/502501）+ details 结构（422501 fields 字典；409504 sku_codes；409502 reason） | P0 |
| TC-CAT-044 | 枚举新增前向兼容：attribute_def.type / visibility / tag.status / product.status / image.kind 新增枚举值时既有反序列化不崩（@JsonEnumDefaultValue 兜底） | bs-844/845/848/854/857（callsite-compat 族）| P2 |
| TC-CAT-045 | 实体结构演进兼容：translation/子表增列后读路径不崩（autoResultMap 宽松映射） | bs-842/843/846/847/849/850/855/856/858~860 | P2 |

## 5. API 端到端（AT，按端点族 + 错误路径穷举）

| TC | 内容 | 溯源 | P |
|---|---|---|---|
| TC-CAT-046 | store 读链路：categories→products(列表筛选 category/tag/color/size/price/sort 六参组合)→product/{slug}→tags | E-CAT-01/04/06/07 | P0 |
| TC-CAT-047 | 列表边界：page_size=100 通过、101 拒绝；page=0 拒绝；price_min>price_max 拒绝；空结果空 data | V-CAT-002/003 | P1 |
| TC-CAT-048 | 搜索：q 必填/超 80 拒绝；en/es/fr 三语各命中；60s 缓存窗口内重复请求命中（响应耗时断言） | E-CAT-02 | P0 |
| TC-CAT-049 | 推荐位：五 block 正常；ymal/ctl 缺 product_id → 422501；shop_by_color 缺 tag_id → 422501；基准品不存在 → 空 items；limit 越界拒绝 | V-CAT-008~011 | P0 |
| TC-CAT-050 | 尺码推荐 E2E：matched/true、matched/false、422502、商品不存在 404501、未发布 404501 | E-CAT-05 | P0 |
| TC-CAT-051 | admin 商品 CRUD 全链路：create(201)→get→update(200)→toggle→flags→delete(204)；草稿删除成功、已发布删除 409509 | E-CAT-08~14 | P0 |
| TC-CAT-052 | 必填族穷举（创建商品）：name/slug/category_id/price/lead_time_days/status 缺失逐一 422501 | bs-059/060/062/066/070/075；可选字段 null 不报错 bs-058~099 其余 | P0 |
| TC-CAT-053 | 长度/数值/枚举极值族（商品）：name129/slug129+pattern/subtitle256/product_type65/price-1/compare_at-1/status 非法/sort-1/lead_time_days0/fabric_composition129/model_*超长/country65/style_no33/seo 超长 → 全部 422501 | **bs-406~423 逐条** | P0 |
| TC-CAT-054 | 子资源极值族：image url513/kind 非法/color_name33/sort-1（bs-424~427）；sku 族（bs-428~432）；size_chart 族（bs-433~439） | boundary extreme | P0 |
| TC-CAT-055 | 分类族：name 必填/65 超长（bs-002/390）、level 0/4（bs-391/392）、可选字段 null（bs-001/003~008）、product_count 派生非负（bs-393/397 只读派生口径——提交侧无此字段，断言响应非负） | E-CAT-15~18 | P0 |
| TC-CAT-056 | 分类树规则：根缺 attribute_set_id → 422501；attribute_set 不存在 → 404503；父不存在 → 404502；三层下再建子 → 409505；parent_id 变更 → 422501 | V-CAT-044/045/048 | P0 |
| TC-CAT-057 | 分类删除：有商品 409502、有子分类 409502(reason=has_children)、清空后 204 | E-CAT-18 | P0 |
| TC-CAT-058 | 属性集/字典族：label/key/type 必填（bs-010~012/015）、items 必填行校验（bs-017~019）、visibility 非法枚举（bs-395）、key 重复 422501、被引用删除 409503/409507、404503/404504 | E-CAT-19~26；bs-009~019/394/395 | P0 |
| TC-CAT-059 | 标签族：dimension_id/name/status 必填（bs-021/024/025/027）、cover/description null 通过（bs-022/026）、status 非法（bs-396）、name65/cover513 超长（bs-506 同型——custom_tag 场景按合并声明落在 tag 端点验证）、维度不存在 404505、删除维度有标签 409506、删除标签级联摘除挂载 | E-CAT-27~34；bs-020~028/231~233/396/397/506/507 | P0 |
| TC-CAT-060 | slug/sku_code 唯一冲突：重复创建 409501/409504；编辑排除自身不误报 | E-CAT-09/11 | P0 |
| TC-CAT-061 | RBAC/鉴权：无 token 401(40100)、store token 打 admin 端点 401、无 `/products`·`/categories`·`/attribute-sets` 权限 key 403(40300)、会话内权限被撤销后续请求 403 | bs-605/606/619/620 | P0 |
| TC-CAT-062 | 越权防探测：store 端读他人不可见资源（draft 商品 slug）→ 404501 同口径（bs-607 tenant 语义本域落点=draft 不可见） | BE-DIM-6 | P1 |
| TC-CAT-063 | 尺码推荐公开性：无 token 可调（bs-641 viewer 场景=匿名可用，无写副作用断言 DB 零变更；bs-642/643 不适用本端点——纯函数无会话/租户资源，以"无副作用"断言收口） | FLOW-P19① | P1 |
| TC-CAT-064 | presign：合法 MIME 200 四字段齐全；非法 MIME/超长 file_name 422501；scope 五枚举 key 前缀正确 | E-CAT-35 | P1 |
| TC-CAT-065 | 失效链 E2E（s-758/FUNC-006）：编辑已发布商品 → 5s 内 ①JetCache 新值 ②MQ 消费者收到 content.invalidated ③revalidate 回调被调（mock Next 端点断言 3 locale 路径）④purge 被调 | FLOW-P03；TASK-056 本域触发侧 | P0 |

## 6. 前端组件测试（FCT，断言逻辑非视觉——视觉归 ui-test-spec）

| TC | 内容 | P |
|---|---|---|
| TC-CAT-066 | Products.vue：服务端分页参数传递；Toggle 乐观更新失败回滚；删除 409509 toast 文案 | P1 |
| TC-CAT-067 | ProductEdit.vue：resolveAttributeConfig 显隐/必填渲染；compare_at<price 即时提示；409508 弹窗重新加载流程；三语 tab 部分提交载荷正确（translations 仅含编辑过的 locale） | P0 |
| TC-CAT-068 | Categories.vue：canDelete 预判置灰；saveDrawer delta 仅提交与父级差异项；409502/409506 toast | P1 |
| TC-CAT-069 | AttributeSets.vue：cycleState 三态循环；hasUnsavedChanges 离开拦截；整单保存载荷=完整矩阵 | P1 |
| TC-CAT-070 | FindMySizeModal：必填预校验不发请求；matched=false 渲染建议话术；422502 字段红框；推荐码一键选中 BuyBox | P0 |
| TC-CAT-071 | CollectionView：URL searchParams 驱动筛选；分页加载更多追加不重复 | P1 |
| TC-CAT-072 | PDP ISR 渲染：404501 → notFound()；skus stock=0 置灰；customSizeAvailable=false 不渲染定制表单 | P0 |

## 7. 韧性测试（RST，BE-DIM-5）

| TC | 内容 | 溯源 | P |
|---|---|---|---|
| TC-CAT-073 | S3 预签名不可达/超时 → 502501；前端表单其余字段仍可保存（降级路径） | bs-661；决策 9 | P0 |
| TC-CAT-074 | presign 客户端重试幂等：同文件重复 presign 生成不同 object_key，无副作用冲突 | bs-662 | P1 |
| TC-CAT-075 | 上传中断恢复：PUT 失败后重新 presign 重传，落库仅最终 public_url，无脏引用 | bs-663 | P2 |
| TC-CAT-076 | MQ publish 失败：商品保存事务不回滚，JetCache 已失效，CDN 靠 TTL 过期（日志告警断言） | EC-CAT-002 | P1 |
| TC-CAT-077 | Redis 不可用：JetCache 两级降级本地 Caffeine + 直查 DB，读路径不 5xx | BE-DIM-8 | P2 |

## 8. 网络边界测试（NBT，强制——5173/5174 ↔ backend 前后端分离）

| TC | 内容 | P |
|---|---|---|
| TC-CAT-078 | CORS Preflight：OPTIONS /api/store/products 与 /api/admin/products 返回正确 ACAO/Methods/Headers（含 PATCH——本域新增动词，**易漏**） | P0 |
| TC-CAT-079 | 跨域实际请求：5173 origin 匿名 GET store 端点成功（无 Authorization 也过 preflight）；5174 origin 带 admin token CRUD 成功 | P0 |
| TC-CAT-080 | 白名单拒绝：非白名单 origin 无 ACAO 头；store token 经 CORS 放行但打 /api/admin/* 鉴权 401（跨端隔离） | P0 |
| TC-CAT-081 | 公开路径白名单回归：catalog 3 条 pattern 放行后，**非白名单 store 端点（如 /api/store/cart）仍强制鉴权**（白名单未过度放行——安全回归核心） | P0 |

## 9. 测试数据工厂（FACTORY-CAT）

- F-Category（根/二级/三级、绑定属性集、含 attr_overrides 变体、含 es/fr 翻译）
- F-AttributeDef（select 带 options / text / toggle / multiselect + 译文等长与错位变体）
- F-AttributeSet（含三态矩阵行）
- F-TagDimension + F-Tag（enabled/disabled、含 cover/无 cover、含翻译）
- F-Product（draft/published、含/不含 compare_at、定制款 custom_size_available、多币种覆盖价、全 36 可选字段 null 变体——bs null 族驱动）
- F-Sku（矩阵 3 色 ×4 码、version 序列、stock=0 变体）/ F-ProductImage（四 kind + 主图）/ F-SizeChartRow（US2~US16 全梯 + 缺 hollow_to_floor 变体）
- F-ProductTranslation（es 全量 / fr 部分字段——回退测试）
- F-MqEvent（order.paid / review.moderated 含重复 event_id 变体）
- F-AdminToken（含/缺 /products·/categories·/attribute-sets key）/ F-StoreToken（跨端误用）

## 10. 优先级矩阵与覆盖核对

| 优先级 | 用例 |
|---|---|
| P0 | TC-CAT-001/002/004/005/006/008/009/010/011/013/016~020/022/023/025~027/033/034/036~038/040~043/046/048~063(部分P1除外)/065/067/070/072/073/078~081 |
| P1 | TC-CAT-003/007/012/014/015/021/024/028~032/035/039/047/062~064/066/068/069/071/074/076 |
| P2 | TC-CAT-044/045/075/077 |

- [x] field-constraint-test-matrix：本域 0 行（仅 FLD-CUSTOMERS，归属他域）——已显式核对，无漏测项
- [x] boundary-scenarios 本域全映射：null 族 bs-001~028/058~121/231~233 → TC-CAT-052~055/059；extreme 族 bs-390~397/406~439/506~507 → TC-CAT-053/054/055/058/059；concurrent bs-558~564/573/574 → TC-CAT-040；auth bs-605~607/619/620/641~643 → TC-CAT-061~063；network bs-655~657（归 trading 下单流，本域不重复）/661~663 → TC-CAT-073~075；integrity bs-676~686 → TC-CAT-020/021；state bs-744~754/775~778 → TC-CAT-033~039；callsite-compat bs-842~860 → TC-CAT-044/045
- [x] 状态机 5 机全部迁移 + guard 拒绝 + 并发 + 终态非法事件（TASK-034~037/040 验收准则逐条对应）
- [x] 17 个域错误码全部出现在至少一个 TC；公开白名单安全回归（TC-CAT-081）落实 error-strategy L2 要求 2 的测试面
