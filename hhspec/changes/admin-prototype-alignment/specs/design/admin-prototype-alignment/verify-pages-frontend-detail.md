# Reviews / Shipping / Promotions / Banners / Content L2 前端详设（admin-prototype-alignment）

> 覆盖缺口：ALIGN-026~029（Reviews）、ALIGN-030~032（Shipping）、ALIGN-033~034（Promotions）、ALIGN-035（Banners）、ALIGN-036~039（Content 三页）
> 本组缺口经实现现状核对，**全部为 VERIFY（已实现，固化规格+测试断言）或 EXEMPT（决策 8/9 豁免）**，无代码变更任务；L3 仅在断言失败时修复。

## A. Reviews（ALIGN-026 ~ 029）

> 原型：`hhspec/prototype/portal-admin/src/views/Reviews.vue`（583 行）；实现：`frontend/portal-admin/src/views/Reviews.vue`（663 行）

| 缺口 | 处置 | 证据 / 断言 |
|---|---|---|
| ALIGN-026 | VERIFY | 页面模块全量对照清单（见下） |
| ALIGN-027 | EXEMPT(FORM-REV-A01②) | Q&A 搜索为当前页内存过滤（impl L417-419 tooltip + placeholder「（当前页）」标注）；L4 豁免 |
| ALIGN-028 | EXEMPT(显式偏离①) | 状态 chips 仅「待审核」带 pendingCount 角标（impl L41-48）；L4 豁免 |
| ALIGN-029 | EXEMPT(CP-071，决策 9) | 删除回复走 ConfirmDialog 二次确认（impl L179/L659）；L4 豁免（原型为直接删除） |

ALIGN-026 模块对照清单（VERIFY，逐项断言存在且可用）：

| 模块 | 原型区块 | impl 现状 |
|---|---|---|
| 双主 Tab 评价审核 / Q&A 管理 | L26-29 | ✓ |
| 状态 chips（全部/待审核/已通过/精选/已拒绝） | L53-59 | ✓（计数豁免①） |
| 星级筛选 + 搜索（商品/买家） | L238-245 | ✓（服务端搜索） |
| 批量勾选 + 批量通过/拒绝条 | L250-254 | ✓ FORM-REV-A02 |
| 行内 通过/拒绝（pending）、设为/取消精选（approved） | L301-308 | ✓ FORM-REV-A03（409803 回滚） |
| 评价详情抽屉（审核操作/完整内容/买家秀图片/官方回复） | L380-476 | ✓ |
| 图片 Lightbox 驳回/恢复（A-070） | L479-502 | ✓ toggleImage |
| Q&A 表格 + 前台可见 Toggle + 回答抽屉 | L320-377 / L505-572 | ✓ |

## B. Shipping（ALIGN-030 ~ 032）

> 原型：`Shipping.vue`（43 行）；实现：`Shipping.vue`（211 行）

| 缺口 | 处置 | 证据 / 断言 |
|---|---|---|
| ALIGN-030 | VERIFY | 模块对照：承运方面板（名称/区域/时效 + 启用 Toggle + 添加承运方）+ 国际邮费表（区域/基础邮费/满额包邮/门槛 4 列）均存在 |
| ALIGN-031 | EXEMPT(DEC-SHP-FE-2，决策 8) | 承运方/邮费表行内编辑/删除为实现增强保留；「保存配置」按钮移除（DEC-SHP-FE-1 即时持久化）一并豁免；L4 豁免 |
| ALIGN-032 | VERIFY | fee_over=0 → 显示「免邮」（text-ok）；threshold null → '—'（impl L107-115 DEC-SHP-3） |

断言要点：邮费表 4 列表头文案与原型逐字（区域/基础邮费/满额包邮/门槛）；免邮单元格 class 含 `text-ok`。

## C. Promotions（ALIGN-033 ~ 034）

> 原型：`Promotions.vue`（66 行，tone/label 仅 4 态）；实现：`Promotions.vue`（230 行）

| 缺口 | 处置 | 证据 / 断言 |
|---|---|---|
| ALIGN-033 | VERIFY | tone/label 含 expired→『已过期』(neutral)、ended→『已结束』(neutral)（impl L24-25）；筛选下拉含对应 option（L125/L179）；与 marketing-api 枚举（coupon: draft/scheduled/active/expiring/expired；flash: …/ended）一致 |
| ALIGN-034 | VERIFY | ended 闪购编辑按钮 disabled + title『已结束活动不可编辑』（impl L197-199）；后端 409703 兜底（marketing-api L697）；券删除预判 draft/expired 可删（L65-67） |

## D. Banners（ALIGN-035）

> 原型：`Banners.vue`（52 行，Toggle 简单开关）；实现：`Banners.vue`（146 行）

| 缺口 | 处置 | 证据 / 断言 |
|---|---|---|
| ALIGN-035 | VERIFY | Toggle on→published / off→archived 三态映射（E-MKT-25，impl onToggle）；409703 → 回滚 Toggle 视觉态 + toast『当前发布状态不允许该操作』；draft Banner Toggle 初始 off，开启即发布 |

补充断言：失败回滚后 `b.status` 不变（store.toggleStatus 内部需保证乐观更新失败还原——若现实现为非乐观（先请求后更新）则天然满足，测试两种路径）。

## E. Content 三页（ALIGN-036 ~ 039）

> 原型：`ContentBlog.vue`（37 行）/`ContentWeddings.vue`（36 行）/`ContentLookbook.vue`（41 行）；实现：188/127/165 行

| 缺口 | 处置 | 证据 / 断言 |
|---|---|---|
| ALIGN-036 | VERIFY | Blog 卡片：draft→「发布」；published→「下线」(archive)；archived→「重新发布」（impl L162-164）；状态枚举 draft/published/archived 与 marketing-api /admin/content/blogs/{id}/status 一致 |
| ALIGN-037 | VERIFY | 预览：slug 为空置灰 + title『需先填写 slug』；有 slug → window.open(`{STORE_BASE}/blog/{slug}`,'_blank')（impl L70-73）；发布前端预判 slug 空 → 提示并打开编辑（FORM-MKT-A04，422704 兜底） |
| ALIGN-038 | VERIFY | Weddings 行操作含 发布/下线 切换（published↔draft，契约枚举无 archived）（impl L33-36/L98-99） |
| ALIGN-039 | VERIFY | Lookbook 卡片与 Guide 行均含 发布/下线 按钮（published↔draft）（impl L46-59/L115-117/L142-143） |

公共断言：发布/下线成功 toast 含「已触发缓存失效链」；409703 兜底文案可读。

## F. 本组结论

无新增代码任务；上述断言全部进入 `test-skeleton.md` 与 `ui-test-spec.yml`。若 L4 UI 对照或测试发现某断言失败，按本档规格修复（规格即原型 + 豁免清单）。
