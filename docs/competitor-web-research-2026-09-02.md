# Dreamy 竞品网站联网复核报告

调研日期：2026-09-02（Asia/Shanghai）  
联网方式：公开网页；请求统一通过 `socks5h://127.0.0.1:1080` 代理。  
产品假设：Dreamy 是面向欧美市场的婚纱/伴娘礼服跨境 DTC 电商。

## 结论摘要

1. 赛道已从“卖礼服”转向“降低婚礼团队决策成本”：颜色协同、样衣试穿、定制尺码、Showroom/收藏分享和交期透明是高频能力。
2. Birdy Grey、Azazie、Revelry 构成最直接的伴娘礼服标杆；Dessy、David’s Bridal 是品牌/线下服务与品类宽度标杆；JJ’s House、Cocomelody、KissProm、Kennedy Blue 是价格、跨境规模或尺码包容性的参照。
3. 可切入的差异化不是再增加 SKU，而是把“婚礼方案”产品化：按调色板选款、多人尺码收集、交期风险提示、一次性下单和团队投票。
4. 当前公开页面普遍把颜色放在一级导航或筛选首位；“色板 + 实物试穿 + 明确退换/交期”应作为 Dreamy 首屏和 PDP 的核心转化链路。

## 扩展调研记录（非正式清单）

> 正式竞品清单已收敛为 Azazie、Birdy Grey、JJ’s House 三家，见 `hhspec/competitors.md`。本节保留此前联网发现的其他品牌，仅作为历史研究记录，不再作为 Dreamy 的对标对象。

| 品牌 | 主要定位 | 官网可核验卖点 | 对 Dreamy 的启示 |
|---|---|---|---|
| [Birdy Grey](https://www.birdygrey.com/) | 伴娘礼服垂直 DTC | 首页标题显示 `$89` 起；颜色/风格导购、Try-at-Home、可变穿法 | 颜色驱动导航、轻量明亮品牌调性 |
| [Azazie](https://www.azazie.com/all/bridesmaid-dresses) | 按需定制伴娘/婚纱 | 搜索结果显示 600+ 款、90+ 色、$69 起；免费色板、At-Home Try-On、0–30/定制尺寸 | 免费定制尺码与团队协作是高价值组合；页面有 Cloudflare，数据需人工复核 |
| [Revelry](https://shoprevelry.com/) | 中高端伴娘礼服 | 官网摘要：100+ 款/色、均低于 $200；同色混搭；最多 6 件 Home Try-On；5 份免费色板 | “同色不同版型”应成为配色方案的默认能力 |
| [Dessy](https://dessy.com/bridesmaid-dresses/) | 多品牌婚礼礼服体系 | Plus、Maternity、Junior、Convertible、Under $200、Ready to Ship、按色选购、Showroom | 品牌/系列/场景多层导航；门店与顾问服务可增强信任 |
| [David’s Bridal](https://www.davidsbridal.com/) | 综合婚礼 O2O | 伴娘、婚纱、MOB、花童、Prom；Fit Guarantee、预约门店、复杂筛选 | 将尺码保障、预约和售后承诺前置 |
| [Kennedy Blue](https://kennedyblue.com/collections/bridesmaid-dresses) | 价格友好伴娘礼服 | 官网描述：100+ 款、88 色、00–32；免费色板入口 | “包容尺码 + 大色盘”是可量化的 SEO/转化文案 |
| [JJ’s House](https://www.jjshouse.com/) | 全球综合礼服跨境 | 海量 SKU、Try-Before-You-Buy、Custom Size、全球配送、奖项背书 | 规模化目录与信任背书值得借鉴；需规避物流慢、退款难等体验风险 |
| [Cocomelody](https://www.cocomelody.com/) | 婚纱/伴娘/特殊场合跨品类 | 首页显示 Mix & Match、Plus Size、Under $99、50% off 色板；$159 免邮 | 用价格带和场景扩展获客，再导入伴娘方案 |
| [KissProm](https://www.kissprom.com/) | Prom/晚礼服/婚纱跨境 | 细粒度筛选、Custom Size、快速现货与场景内容 | 筛选字段和 SEO 内容可复用，但 Dreamy 应聚焦婚礼而非泛场合 |
| [Lulus](https://www.lulus.com/) | 大众女装/婚礼宾客 | 低价时尚、每日上新、免费配送等大众电商能力 | 是替代性竞品而非核心同类；可作为价格锚点和内容流量来源 |

## 竞品分类

### A. 直接核心竞品：伴娘团队决策型 DTC

**Birdy Grey、Azazie、Revelry、Kennedy Blue**

共同争夺“新娘为整个伴娘团买礼服”的预算。核心武器是颜色体系、多人混搭、色板和试穿服务。Dreamy 的产品体验、首页信息架构和 PLP/PDP 应优先对标这四家。

### B. 综合婚礼服务型竞品

**David’s Bridal、Dessy**

覆盖婚纱、伴娘、妈妈装、花童、Prom，并通过门店、顾问、预约和 Fit/Alteration 服务建立信任。它们的优势是品类宽度和线下承接，弱点是流程重、品牌层级复杂。Dreamy 初期不宜复制门店成本，应先做在线顾问和量体支持。

### C. 跨境规模与性价比竞品

**JJ’s House、Cocomelody、KissProm**

依靠海量 SKU、较低价格、定制生产和全球配送获取搜索流量。它们适合参考 SEO 分类、价格锚点和供应链能力，但尺码、质量、交期、退款体验是明显风险，Dreamy 应以透明承诺建立反差。

### D. 替代性预算竞品

**Lulus**

不是纯伴娘平台，但会分流“婚礼宾客/低预算伴娘”需求。它代表大众时尚价格和快速上新，对 Dreamy 的影响主要在价格预期和内容获客，而非核心功能。

## 价格与定位分层

| 层级 | 代表品牌 | 用户决策 | Dreamy 应对 |
|---|---|---|---|
| 入门/促销（约 <$100） | Azazie 起价款、Cocomelody、Lulus、部分 JJ’s House | 先看价格，再看款式 | 保留少量引流款，但避免全面价格战 |
| 主流伴娘（约 $100–200） | Birdy Grey、Revelry、Kennedy Blue、Dessy Under $200 | 颜色统一、版型包容、交期可靠 | Dreamy 主战场；用方案和服务提高客单 |
| 中高端/服务溢价（约 $200+） | David’s Bridal 高端线、Dessy 部分系列、Azazie 婚纱线 | 面料、定制、合身保障、顾问服务 | 以定制、顾问、婚期保障做升级包 |

## Dreamy 的竞争优先级

**P0：先解决直接转化问题**

- 首页提供“按婚礼色板选款”，支持同一色系多个版型。
- 建立试色/试穿闭环：色板申请、样衣数量、押金、寄回、转正装抵扣一次说明清楚。
- PDP 在选择尺码前展示：推荐尺码、定制费用、定制不可退规则、预计发货日和婚期风险。
- 提供伴娘团链接：新娘创建方案，成员自行填尺码/身高，统一查看缺货和交期。

**P1：形成服务差异化**

- 结构化评价：身高、体重区间、购买尺码、颜色、修改情况和婚期。
- 在线量体顾问/视频预约，替代 David’s Bridal 的线下优势。
- 婚期倒推器：根据生产、质检、运输和修改时间给出最晚下单日。
- 方案级加购：色板包、全团尺码审核、加急生产、改衣合作。

**P2：扩大流量而不是盲目扩 SKU**

- 以“户外婚礼色板、季节、场地、肤色、身材/孕妇”等场景做 SEO 落地页。
- 用少量高复用基础版型覆盖更多颜色和面料，再根据真实订单扩充款式。
- 通过 Real Weddings、Lookbook、Pinterest/Instagram 内容承接灵感流量。

## 明确的取舍建议

- **优先学习**：Revelry 的同色混搭、Azazie 的定制/试穿、Birdy Grey 的颜色导航、David’s Bridal 的合身保障、Dessy 的 Ready-to-Ship 分类。
- **谨慎学习**：JJ’s House/KissProm 的海量 SKU和复杂筛选；可借鉴目录结构，不要牺牲质量与可理解性。
- **明确规避**：隐藏退换条件、定制规则到结账页才展示、无法预测到货时间、客服工单入口深藏、只用模特图不提供体型数据。

## 建议的竞品研究顺序

1. **Revelry + Azazie**：直接决定 Dreamy 的核心产品模型（色板、试穿、混搭、协作）。
2. **Birdy Grey + Kennedy Blue**：研究颜色运营、价格带和尺码包容性。
3. **David’s Bridal + Dessy**：拆解信任、顾问、门店/预约和多角色导航。
4. **JJ’s House + Cocomelody + KissProm**：研究 SEO、跨境履约、定制生产和促销机制。
5. **Lulus**：仅用于价格、内容和大众审美参照。

## 能力对比

| 能力 | 行业状态 | Dreamy 建议优先级 |
|---|---|---|
| 按颜色购物/色板 | Birdy Grey、Azazie、Revelry、Dessy 等普遍具备 | P0：首页入口、PLP 首筛选、色板寄送 |
| 实物试穿 | Azazie、Revelry、Birdy Grey、JJ’s House | P0：3–6 件样衣、押金/退回规则、交期联动 |
| 定制尺码 | Azazie、JJ’s House、KissProm、Cocomelody | P0：免费/收费规则和“定制不可退”必须在选择前显示 |
| 团队协作 | Azazie Showroom、Revelry 方案思路 | P0：链接分享、投票、成员尺码收集、统一下单 |
| 交期/现货 | KissProm、Dessy Ready to Ship 等 | P0：按婚期反推最晚下单日，分拆缺货风险 |
| 线下顾问/门店 | David’s Bridal、Dessy | P1：在线顾问预约、视频/聊天量体 |
| 奖项/评价 | JJ’s House、各家 Reviews/Real Weddings | P1：结构化评价（身高、尺码、颜色、婚期） |

## 推荐产品策略

- 首屏主 CTA：`Build your bridal party palette`，进入“婚期 → 色板 → 版型 → 成员尺码 → 交期”的向导，而不是直接丢给 SKU 网格。
- PLP 筛选顺序：颜色/色系 → 交期 → 尺码/定制 → 版型 → 面料 → 价格；保留“可试穿/现货”快捷条件。
- PDP 必须同时展示：真实色板、模特三维信息、尺码推荐、定制是否可退、预计发货日、样衣与正装差异。
- 形成两条价格带：可快速决策的 `<$150` 基础系列，以及面料/定制升级系列；不要用海量低质 SKU 取代方案能力。

## 来源与限制

- 直接访问：Birdy Grey、Kennedy Blue、Revelry、Dessy、Cocomelody；页面标题、meta description 或首页导航用于核验。
- Bing RSS 公开搜索结果用于发现候选品牌和摘要，查询包括 `bridesmaid dress market brands 2025`、`Revelry bridesmaid dresses try on home swatches`、`Dessy bridesmaid dresses color customization` 等。
- Azazie、Vow’d 等页面触发 Cloudflare；JJ’s House 部分路径返回 404。因此价格、数量和账户流程以官网当日可见信息为准，不应视为审计或长期稳定数据。
- 本报告未使用登录态、付费流量工具或第三方估算流量；“竞品强弱”是产品能力判断，不是市场份额排名。
