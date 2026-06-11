# 测试骨架设计（admin-prototype-alignment）

> 范围：ALIGN-001 ~ ALIGN-039 每缺口至少 1 条 AAA 伪代码用例
> 层级标注：UT=前端组件/单元；IT=后端集成；E2E=Playwright UI（与 ui-test-spec.yml 联动）
> acceptance 标注：`s-XXX` 为 acceptance.yml 场景编号；无直接场景者标 source_id 或 `ui-checklist`
> 优先级：P0=决策强制回对/资金链路；P1=功能缺口；P2=豁免验证断言

## Categories / AttributeSets

```
TC-ALIGN-001 [E2E][P0] Categories 三 Tab 结构与矩阵合并（acceptance: source_id=page_categories_view.taxonomy_tab→matrix_tab）
  Arrange: 登录 admin，进入 /categories
  Act:    读取主 Tab 条文案；点击「属性集与字典」→ 点击 sub-tab「品类×属性矩阵」
  Assert: 主 Tab = [标准品类, 属性集与字典, 自定义标签]；矩阵表渲染（列=属性集聚合）；
          矩阵下方存在「子品类属性覆盖（相对父级基础属性集的 delta）」只读卡片区（有 delta 数据时）

TC-ALIGN-001b [UT][P1] 子品类覆盖卡片派生逻辑
  Arrange: 分类树 fixture：root A（child a1 含 2 项 overrides，child a2 无），root B（无 overrides）
  Act:    计算 subcategoryOverrides
  Assert: 仅 root A 入组；a1 显示 2 行 delta；a2/B 不渲染；空集时整区不渲染

TC-ALIGN-002 [E2E][P2] 编辑抽屉三语 name tab 保留（豁免验证）（acceptance: ui-checklist categories）
  Arrange: 打开任一品类编辑抽屉
  Act:    切换 EN/ES/FR LocaleTabs
  Assert: 三语 name 输入框独立编辑、已填标记正确

TC-ALIGN-003 [E2E][P1] 维度删除 409506 引导（acceptance: s-007 tag_dimension_lifecycle.active→deleted 反向 guard）
  Arrange: 存在含标签的维度 dim-X（API mock 删除返回 409506）
  Act:    删除 dim-X
  Assert: 出现「先清空标签」引导提示；维度仍在列表；空维度删除则成功（200 路径）

TC-ALIGN-004 [E2E][P0] /attribute-sets 路由重定向
  Arrange: 登录后直接访问 /attribute-sets
  Act:    等待路由解析
  Assert: location = /categories?tab=attributes；侧边栏无「属性集」菜单项

TC-ALIGN-005 [E2E][P0] 矩阵 sub-tab 文案
  Assert: sub-tab 文案 ===「品类×属性矩阵」（非「属性集×属性矩阵」）

TC-ALIGN-006 [E2E][P2] 矩阵整单保存保留（E-CAT-21 豁免验证）（acceptance: s-008 attribute_visibility_cycle）
  Arrange: 进入矩阵 sub-tab
  Act:    点击任一格子循环态 → 观察按钮；点「保存配置 *」
  Assert: 改动后按钮带 * 且 enabled；保存成功后 * 消失；切 Tab 且有未保存变更时出现确认提示
```

## Products

```
TC-ALIGN-007a [E2E][P0] 勾选 + 批量操作栏（acceptance: source_id=page_products_view.loaded→batch_selected）
  Arrange: 商品列表 ≥2 行
  Act:    勾选 2 行
  Assert: 底部出现「已选 2 项」+ [批量上架/批量下架/设为推荐/批量删除]；取消全部勾选后栏消失

TC-ALIGN-007b [IT][P0] 批量端点逐条容错（acceptance: s-011 draft→published；s-015 published→deleted.guard_fail/409509）
  Arrange: 商品 A(draft)、B(published)
  Act:    POST /api/admin/products/batch {action:'delete', ids:[A,B]}
  Assert: 200；success_ids=[A]；failures=[{id:B, error_code:409509}]；A 已删 B 未删；OperationLog 1 条

TC-ALIGN-007c [IT][P1] 商品 CSV 导出（acceptance: ui-checklist products）
  Arrange: 12000 件商品（造数）
  Act:    GET /api/admin/products/export?status=all
  Assert: text/csv；行数=10000+表头；X-Export-Truncated:true；末行含 TRUNCATED；OperationLog 写入

TC-ALIGN-007d [E2E][P1] 销量列渲染
  Arrange: mock 列表返回 sales_total=132
  Assert: 表头含「销量」；单元格右对齐显示 132；缺省字段显示 0

TC-ALIGN-008 [E2E][P2] 排序列保留（豁免验证）
  Assert: 表头含「排序」；行内 input blur 触发保存请求
```

## Dashboard / Analytics

```
TC-ALIGN-009 [E2E][P0] 发布入口恢复（acceptance: s-884 dashboard_analytics_read；ui-checklist OP-002）
  Arrange: 登录进入 /dashboard
  Assert: PageHeader 含「发布站点」(btn-gold) 与「查看完整看板」；快捷入口 5 项含「编辑首页」「发布站点」；
  Act+Assert: 点击「发布站点」→ 路由 /publish

TC-ALIGN-010 [E2E][P2] KPI 卡无 delta 行（DEC-ANA-FE-2 豁免）（acceptance: source_id=page_dashboard_view.loading→loaded）
  Assert: KPI 卡内不存在趋势箭头/「较昨日」文案

TC-ALIGN-011 [E2E][P2] 待办瓦片 3 列（DEC-ANA-FE-3 豁免）
  Assert: 待办容器 class 含 sm:grid-cols-3；瓦片点击带 query 直达列表

TC-ALIGN-016 [E2E][P2] 商品热度列=销售额（DEC-ANA-FE-7 豁免）（acceptance: source_id=page_analytics_view.loading→loaded）
  Assert: 热度表头 = [排名, 商品, 销量, 销售额]

TC-ALIGN-017 [UT][P1] 来源/漏斗标签中文化
  Arrange: traffic fixture {source:'organic'},{source:'unknown_x'}; funnel 四阶段
  Act:    渲染流量/漏斗 Tab
  Assert: organic→自然搜索；unknown_x 回退原始 key 不报错；漏斗=[商品浏览,加入购物车,进入结算,完成支付]
```

## Orders / OrderDetail

```
TC-ALIGN-012 [IT][P0] 订单 CSV 导出（acceptance: ui-checklist orders；BE-DIM-8）
  Arrange: 多状态订单造数 + status=paid 筛选
  Act:    GET /api/admin/orders/export?status=paid
  Assert: 仅 paid 行；CSV 列含 country,item_count；OperationLog（PII 审计）写入；超 10000 截断头

TC-ALIGN-013a [IT][P0] AdminOrderListItem country/item_count（acceptance: s-026 order_lifecycle.paid→shipped 数据前置）
  Arrange: 订单含 3 行 qty 1/2/4，地址快照国家 US
  Act:    GET /api/admin/orders?page=1
  Assert: 该订单 country='US'，item_count=7；SQL 无逐行 N+1（聚合一次命中 order_id 索引——EXPLAIN 断言）

TC-ALIGN-013b [E2E][P0] 列表列回对原型
  Assert: 表头 = [订单号,客户,地区,商品数,金额,支付方式,状态,下单时间,操作]；无「币种」「承运」列

TC-ALIGN-014 [E2E][P2] 8 状态 Tab 保留（豁免）
  Assert: Tab = [全部,待付款,待发货,已发货,已完成,退款中,已取消,已退款]

TC-ALIGN-015 [IT+E2E][P1] 搜索范围含客户名
  Arrange: 订单客户 name='Grace Chen' email='g@x.com'
  Act:    search='Grace'；search='g@x.com'
  Assert: 两者均命中；placeholder ===「搜索订单号 / 客户名…」

TC-ALIGN-021 [E2E][P1] 操作按钮状态矩阵（acceptance: s-026 paid→shipped；s-029 paid→refunding）
  Arrange: 分别打开 pending/paid/shipped/completed 订单详情
  Assert: 按钮可见性符合 FORM-TRD-D01 矩阵（pending:取消；paid:退款+发货；shipped:退款+完成；completed:仅返回）

TC-ALIGN-022 [E2E][P1] 承运方 API 枚举（acceptance: s-879 admin_ship_complete）
  Arrange: mock listCarriers 返回 enabled=[DHL Express] disabled=[EMS]
  Act:    paid 订单点「标记发货」
  Assert: 下拉仅 [DHL Express]；提交后 ship 请求 carrier='DHL Express'

TC-ALIGN-023 [E2E][P1] 金额拆分行（acceptance: s-877 checkout_order 金额口径）
  Arrange: mock 详情 giftWrap=true giftWrapFee=20 discountAmount=50
  Assert: 行序 小计→运费→Gift Wrapping $20→优惠 -$50→合计；无 giftWrap/优惠时对应行不渲染
```

## Refunds

```
TC-ALIGN-024a [E2E][P0] 行内同意（acceptance: s-043 refund_lifecycle.pending→approved；s-880）
  Arrange: pending 工单（mock approve 成功）
  Act:    点行内「同意」
  Assert: 无弹窗；toast「已同意退款，Stripe 退款已发起」；行状态→已同意

TC-ALIGN-024b [E2E][P0] 行内拒绝展开原因（acceptance: s-045 pending→rejected；s-046 guard_fail）
  Act:    点「拒绝」→ 按钮原位展开输入框；空原因点确认
  Assert: 展示「拒绝原因必填」且不发请求；填原因后确认 → reject(id, reason) 调用、行状态→已拒绝；
          Esc/取消 → 收起恢复按钮；同一时刻仅一行处于展开态

TC-ALIGN-025 [E2E][P1] 已处理行单号展示（acceptance: s-047 approved→refunded）
  Arrange: approved 工单 stripeRefundId='re_123' returnTrackingNo='SF999'
  Assert: 单元格含「已处理」「退款单号 re_123」「退货单号 SF999」；rejected 工单仅「已处理」
```

## Reviews（豁免/对照断言）

```
TC-ALIGN-026 [E2E][P1] 模块全量对照（acceptance: s-049 review pending→approved；s-057/s-058 图片驳回/恢复；s-059 Q&A 回答）
  Assert: 双主 Tab、5 chips、星级筛选、批量条（勾选后出现）、行内通过/拒绝、详情抽屉、
          图片 Lightbox 驳回/恢复、官方回复编辑/删除、Q&A 可见 Toggle + 回答抽屉 —— 全部存在且操作成功

TC-ALIGN-027 [E2E][P2] Q&A 当前页过滤豁免：搜索框 placeholder 含「（当前页）」且 title 标注
TC-ALIGN-028 [E2E][P2] chips 计数豁免：仅「待审核」chip 渲染角标数字
TC-ALIGN-029 [E2E][P2] 删除回复二次确认（CP-071）：点删除 → ConfirmDialog 出现 → 确认后回复消失
```

## Shipping

```
TC-ALIGN-030 [E2E][P1] 模块对照：承运方面板（Toggle/添加承运方）+ 邮费表 4 列存在（acceptance: ui-checklist shipping）
TC-ALIGN-031 [E2E][P2] 行内编辑/删除保留 + 无「保存配置」按钮（DEC-SHP-FE-1/2 豁免）
TC-ALIGN-032 [UT][P1] feeOver=0 → 渲染「免邮」(text-ok)；threshold=null → '—'
```

## Promotions / Banners / Content

```
TC-ALIGN-033 [E2E][P1] 状态枚举补全（acceptance: s-075 coupon active→expired；s-083 flash active→ended）
  Arrange: mock 列表含 expired 券、ended 闪购
  Assert: 徽章「已过期」「已结束」(neutral)；筛选下拉含对应项

TC-ALIGN-034 [E2E][P1] ended 禁编辑（acceptance: s-084 flash active→ended.guard_fail/409703）
  Assert: ended 行编辑按钮 disabled + title「已结束活动不可编辑」；强行 API 编辑 → 409703 toast

TC-ALIGN-035 [E2E][P0] Banner 三态 Toggle + 409703 回滚（acceptance: s-085 draft→published；s-087 published→archived；s-089 archived→published）
  Arrange: draft/published/archived 三条 Banner；一条 mock 返回 409703
  Act:    依次切 Toggle
  Assert: on→published、off→archived；409703 行 Toggle 视觉态还原 + toast「当前发布状态不允许该操作」

TC-ALIGN-036 [E2E][P1] Blog 状态流转按钮（acceptance: s-882 content_publish_invalidate）
  Assert: draft→「发布」、published→「下线」、archived→「重新发布」；操作后状态徽章变化 + toast 含「缓存失效链」

TC-ALIGN-037 [E2E][P1] Blog 预览 slug 校验
  Arrange: 一条无 slug、一条 slug='spring-guide'
  Assert: 无 slug 预览按钮 disabled + title「需先填写 slug」；有 slug 点击 → window.open 新窗口 URL 以 /blog/spring-guide 结尾

TC-ALIGN-038 [E2E][P1] Weddings 发布/下线（acceptance: source_id=real_wedding_publish.draft→published / published→draft）
  Assert: 行操作按状态显示 发布/下线；切换成功状态徽章翻转

TC-ALIGN-039 [E2E][P1] Lookbook/Guide 发布/下线
  Assert: Lookbook 卡片与 Guide 行均含按状态切换的 发布/下线 按钮；操作成功

TC-ALIGN-018 [E2E][P1] SKU 颜色预设 swatch（ProductEdit）
  Arrange: 新建商品进入 SKU 区块
  Assert: 预设颜色按钮组渲染（含色点）；点选两色进入 skuColors 并生成矩阵行；
          自定义输入「Mocha」回车 → 追加 chip；编辑含非预设色商品时颜色不丢失

TC-ALIGN-019 [E2E][P2] 多币种 5 列豁免：USD disabled+主价 placeholder，EUR/CAD/AUD/GBP placeholder='auto'

TC-ALIGN-020 [E2E][P1] 保存并生成静态页跳转（acceptance: source_id=page_product_edit_view.editing→saving）
  Arrange: 合法表单（mock 保存成功）
  Act:    点「保存并生成静态页」
  Assert: 保存请求 status='published'；成功后路由 → /publish；保存失败（mock 422）停留原页展示错误
```

## 测试数据工厂要点

- 订单工厂：可指定 status/行数/qty/地址国家/giftWrap/discount；退款工厂：status + stripeRefundId/returnTrackingNo
- 商品工厂：status/sales_total/sort；批量场景需 draft+published 混合
- Banner/Blog/Wedding/Lookbook 工厂：三态/双态覆盖
- 409 错误注入：MSW/route mock 按端点返回 {code:409506|409509|409703|409803}

## 优先级汇总

P0：TC-ALIGN-001/004/005/007a/007b/009/012/013a/013b/024a/024b/035
P1：功能缺口与 VERIFY 断言主体
P2：豁免清单断言（同时作为 L4 豁免登记依据）
