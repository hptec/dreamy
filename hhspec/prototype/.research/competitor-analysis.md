# 竞品深度分析：JJ's House vs Azazie

> 抓取方式：CDP 接管真实 Chrome（你手动通过 Cloudflare 验证后），递归抓取首页/列表页/详情页结构 + 下载媒体素材。
> 抓取日期：2026-05-28 ｜ 目标市场：美国 ｜ 我们的定位：高端婚纱 + 小礼服外贸站
> 原始素材见 `hhspec/prototype/.research/{azazie,jjshouse}/`（home/list-*/pdp-* 的 .html/.png/.json + media/ 图库）

---

## 0. 一句话定位对比

| | **JJ's House** | **Azazie** |
|---|---|---|
| 模式 | 大而全的特殊场合礼服超市，工厂直营、低价、海量 SKU | 婚纱/伴娘聚焦的 DTC 品牌，强体验（家试穿、海量配色）、中端价 |
| 品类宽度 | 极宽（婚纱/伴娘/妈妈装/女童/晚礼/Prom/Homecoming/Cocktail/西装/睡袍/配饰鞋履） | 较聚焦（伴娘为核心 + 婚纱 + 妈妈装 + 花童 + 配饰） |
| 婚纱价位 | 约 **US$289**（低，US$49–999 区间） | 约 **US$569**（中，定位更高） |
| 小礼服价位 | 约 **US$20–699**（special occasion） | 以伴娘/宾客礼服承载，多在 US$99–249 |
| 核心卖点 | 100% Handmade 定制、10 免费色卡、超多买家秀评价、多国站点 | 90+ 配色、免费色卡、Home Try-On 家试穿、尺码 0–30 包容性、混搭伴娘团 |
| 视觉气质 | 信息密集、促销感强、偏实用 | 干净浪漫、摄影驱动、柔和高级 |

**对我们的启示**：用户要"十分高端大气上档次" → 视觉气质应**向 Azazie 看齐甚至更高**（大留白、摄影驱动、克制），但**功能完整度要吸收两家之长**（定制工期/色卡/家试穿/买家秀/尺码包容）。价格带定位应明显高于 JJ's House，对标或略高于 Azazie。

---

## 1. 信息架构 / 导航分类体系

### JJ's House 顶级导航
`NEW ｜ BRIDESMAIDS ｜ MOMS ｜ BRIDES ｜ GIRLS ｜ OCCASION ｜ PROM ｜ PAJAMAS & ROBES ｜ ACCESSORIES & SHOES ｜ SUITS & TUXEDOS ｜ TRY-ON AT HOME`
- 二级补充：Wedding Guest / Flower Girl / Cocktail / Homecoming
- 颜色族导航：Blue/Green/Red/Pink/Purple/Neutral/Yellow/Orange/Grey/Brown/White/Black
- 服务入口：Made to Order（100% Handmade）、Get 10 Free Swatches、Style Gallery、VIP 会员、学生/医护/军人折扣、International Websites、Blog、Reviews

### Azazie 顶级导航（mega menu，极深）
`Bridesmaids ｜ Brides(Wedding Dresses) ｜ Moms(MOB) ｜ Flower Girl ｜ Suits & Ties ｜ Accessories ｜ (free!) Swatches ｜ Home Try-On`
每个大类下展开多维筛选：
- **轮廓 Silhouette**：A-line / Mermaid / Ball-gown / Sheath / Two-piece / Jumpsuit
- **场合 Collection**：Rehearsal / Bridal Shower / Bachelorette / Engagement / Elopement / Beach / Courthouse / Boho / Sexy Glam
- **颜色 Color**：数十种命名色（Dusty Blue/Dusty Rose/Cabernet/Champagne/Eucalyptus/Sage…）+ 色族
- **面料 Fabric**：Chiffon / Satin / Velvet / Tulle / Mesh / Crepe / Sequins / Floral Jacquard / Lace…
- **长度 Length**：Tea / Ankle / Floor
- **袖型 Sleeve**：Off-the-shoulder / Long-sleeve / Strapless…
- **特性 Features**：Leg Slit / With Pockets / Bra-friendly / Convertible / With Pads
- 内容工具：Body Shape Guide、Style Gallery、Lookbook、Color 101、Fabric 101、Shopping Guide

**共性结论**：两家都用「大类 × 多维度筛选」的电商导航。我们的站做 **婚纱 + 小礼服** 两条主线即可，但筛选维度要专业完整（轮廓/颜色/面料/长度/领型/袖型/价格/场合）。

---

## 2. 首页结构

**JJ's House 首页区块**：顶部公告条（Free Shipping）→ 主视觉轮播 Banner（1920×550，Spring/Bridesmaid/Prom）→ 分类入口宫格（300×300 圆角分类图：Wedding/Bridesmaid/Mom/Guest/Girls…）→ 100% Handmade 卖点 → Style Gallery 买家秀 → 促销/会员入口 → 多国站点 → 页脚（客服/政策/折扣/公司）

**Azazie 首页区块**：顶部活动条 → 大幅摄影主视觉 → 精选系列卡片（744×960 竖版大图：New Arrivals/Plus Size/Little White Dresses）→ 配色系统展示 → Home Try-On 推广 → 真实买家（Azazie IRL）→ 页脚（Shopping/Customer Care/Services/Explore/About + 多国 + App + 社媒）

**对我们的启示**：高端站首页 = 全屏摄影主视觉 + 克制的精选系列（竖版大图）+ 工艺/服务故事 + 真实买家墙 + 精致页脚。少促销弹窗，多编辑式排版。

---

## 3. 分类列表页结构

| 元素 | JJ's House | Azazie |
|---|---|---|
| 价格展示 | US$49–999，常带划线原价 | $99–569，部分 "from $" |
| 商品卡 | 模特图 + 名称 + 价格 + 颜色点 + 评分/销量标签 | 竖版模特图(744×960) + 名称 + 价格 + 颜色 swatch 行 + 标签(New/Ships Now) |
| 筛选 facet | 颜色族/尺码/轮廓/价格/领型/面料… | 极丰富：颜色/面料/轮廓/长度/袖型/特性/场合/价格 |
| 排序 | 推荐/新品/价格/销量 | Popularity/New Arrival/Price |
| 特色 | 颜色族快捷入口 | 鼠标悬停切换配色、Ships Now 现货筛选 |

**对我们的启示**：列表页要有左侧/顶部多维筛选 + 商品卡支持「颜色点切换主图」+ 标签体系（新品/现货/畅销）+ 排序。

---

## 4. 商品详情页（PDP）结构 ★关键

### Azazie PDP（以 Geneva 婚纱 $569 为例）
- 左侧**多图画廊**（120+ 资源：正/背/侧/细节/多配色上身图）
- **颜色色卡**：10+ 色，点击切换全套图
- **长度选择**：Floor length / Short-mini
- **尺码**：标准码 + Custom Size（定制尺寸）+ SIZE GUIDE 弹层
- **定制工期**：Standard 3–4 weeks ｜ Rush 4–8 days (+$10) ｜ rush 2–3 weeks (+$10)
- CTA：**Buy Now**
- 内容板块：Tips From The Pros ｜ Product Details ｜ Shipping & Returns ｜ **Often Bought With** ｜ **You May Also Like** ｜ Recently Viewed ｜ **Azazie IRL（买家实拍墙）**
- 多国配送 + 多语言

### JJ's House PDP（以 Antje 婚纱 US$289 为例）
- **多图画廊**（72 资源：多角度 + 多配色）
- **颜色色卡**：9 色
- **尺码**：US 0–16+（标准码）+ Custom（量体定制）
- CTA：**ADD TO BAG | US$289** ｜ **BUY NOW | US$289**
- **Customer Reviews：256 条**，含 **Photos/Videos 166** 买家图、按尺码筛选（US0(10)/US2(7)/US4(12)…）、按评分/最新排序
- **Questions (13)** + Ask A Question 问答区
- Best Sellers ｜ Recently Viewed
- 配饰加购（pocket square / 配件 US$8–32）

**两家 PDP 公共要素（我们必须做全）**：
1. 多角度大图画廊（含细节图、多配色上身图）
2. 颜色色卡切换（联动主图）
3. 标准码 + 量体定制尺寸 + 尺码指南
4. 定制工期/加急选项（made-to-order 心智）
5. Add to Bag / Buy Now 双 CTA
6. 买家秀评价（带照片、可按尺码筛选）+ 问答
7. 关联推荐（Often Bought With / You May Also Like / Recently Viewed）
8. 配送与退换说明

---

## 5. 业务模式与核心功能

| 功能 | JJ's House | Azazie | 我们是否要做 |
|---|---|---|---|
| Made-to-Order 定制工期 | ✅ 100% Handmade | ✅ Standard/Rush | ✅ 必做（高端定制心智）|
| 量体定制尺寸 Custom Size | ✅ | ✅ | ✅ 必做 |
| 免费色卡 Swatches | ✅ 10 免费 | ✅ Free | ✅ 建议做 |
| Home Try-On 家试穿 | ✅ | ✅ 招牌 | ⬜ 视范围 |
| 海量配色 | ✅ 色族 | ✅ 90+ 色 | ✅ 建议做 |
| 买家秀评价 | ✅ 256 条/166 图 | ✅ Azazie IRL | ✅ 必做 |
| 问答 Q&A | ✅ Questions(13) | — | ⬜ 可选 |
| 收藏/心愿单 | ✅ | ✅ My Favorites | ✅ 建议做 |
| 多币种/多语言/多国 | ✅ | ✅ | ✅ 外贸站必做 |
| 会员/折扣体系 | ✅ VIP+人群折扣 | ✅ Coupons | ⬜ 可选 |
| 风格画廊/Lookbook | ✅ Style Gallery | ✅ Lookbook/Color101 | ✅ 建议做（提升高端感）|

---

## 6. 媒体素材库（已下载到本地）

| 站点 | 总数 | 竖版模特图 | 宽幅 Banner | 方形图 | 其他(细节/画廊) |
|---|---|---|---|---|---|
| Azazie | 109 | 65 | 5 | 27 | 12 |
| JJ's House | 114 | 23 | 8 | 14 | 69 |
| **合计** | **223** | **88** | **13** | **41** | **81** |

- 位置：`hhspec/prototype/.research/azazie/media/`、`hhspec/prototype/.research/jjshouse/media/`
- 内容：婚纱（A-line/Mermaid/Ball-gown/蕾丝/一字肩）、小礼服、配饰，多为高质量模特上身图
- 用途：原型阶段作占位/参考素材。⚠️ **版权提示**：竞品原图仅适合内部原型参考；正式上线需替换为自有拍摄或已授权素材。

---

## 7. 对我们高端原型的设计结论

1. **视觉**：对标 Azazie 并再上探——全屏摄影、大留白、衬线标题、克制金属色点缀、几乎无促销噪音。
2. **品类主线**：婚纱（Wedding Dresses）+ 小礼服（Evening / Cocktail / Party）。可含伴娘、宾客礼服作延展。
3. **功能完整度**：PDP 必须做全第 4 节 8 要素；列表页做全多维筛选 + 颜色切换；保留 made-to-order 定制工期与量体尺寸（高端定制核心）。
4. **外贸属性**：多币种/多语言/多国配送、美码尺码体系、英文文案。
5. **信任体系**：买家秀评价 + 工艺故事 + 尺码指南 + 退换政策，建立跨境高客单信任。
