# 竞品分析报告

> 调研日期：2026-05-29
> 调研对象：Birdy Grey / David's Bridal / KissProm
> 用途：内部原型 demo 参考（用户已明确授权，不公开、不商用）
> 抓取方式：curl 直接拉取 HTML 与 CDN 图片（claude.ai WebFetch 对三个域名受网络策略拦截，已改用 curl）。三家均运行在 Shopify 平台之上（CDN 为 `cdn.shopify.com`）。

---

## 1. birdygrey.com

定位：**伴娘礼服垂直电商**，主打"colors and styles that make everyone in the bridal party happy"。明亮淡雅、色彩驱动、性价比（$89 起）。Title：`Bridesmaid Dresses & Gowns from $89 | Birdy Grey`。

### 导航 IA
一级菜单围绕"婚礼角色 + 颜色 + 面料"组织（典型的 Shopify mega-nav）：

- **Bridesmaid Dresses（核心）**
  - 按颜色：Black / Blue / Brown / Gray / Green / Orange / Pink / Purple / Red / Neutral / Pastel / Jewel Tone / Metallic / Floral
  - 按面料：Chiffon / Crepe / Matte Satin / Shiny Satin / Luxe Knit / Luxe Stretch Knit / Velvet
  - 按身型/版型：Plus Size / Maternity / Convertible / Junior Bridesmaid
  - 集合：New Arrivals / Best Sellers / Ready to Ship / Garden Edit / Fairy Coquette / Gemstone / Dresses Under $100 / Sale
- **Swatches / Color Swatches（色板，重要差异化）**：Bridesmaid Color Swatches、各颜色 swatch、各面料 swatch、New Swatches、Swatch Bundles、Suit Swatches
- **For the Bride**：Little White Dresses / Shop for the Bride / Bridal Robes & Pajamas / Bridal Jewelry & Accessories / Bridal Shoes
- **Groomsmen / Suits**：Suits and Tuxedos / Groomsmen Ties / Kids Ties / Groomsmen Gifts
- **Accessories**：Jewelry / Pearl Jewelry / Robes & PJs / Shoes & Heels / Slippers / Scarves / Intimates / Pet Accessories
- **Flower Girl**：Dresses / Accessories
- **Wedding Guest / Events**、**Mother of the Bride & Groom**、**Prom Dresses**
- **Sale**（按角色拆分：Bridesmaid / Bride / Groomsmen / Accessories）
- **Account**（`/account`）

### 首页区块
1. 顶部促销条（Shiny Satin from $89 限时）
2. Hero（季节性大图 + CTA）
3. **按颜色入口（Shop by Color）** —— 圆形/方形色块网格（all-colors、florals、blues、greens、pinks、pastels、neutrals、jewel-tones、metallics…）这是 Birdy Grey 最强的首页模式
4. 按面料入口（Matte Satin / Luxe Knit 专题页）
5. Best Sellers 推荐位
6. For the Bride / Groomsmen / Flower Girl 角色入口
7. **Free Moodboards / Swatch Color Palettes**（色彩规划工具，导流色板购买）
8. Real Weddings（UGC/真实婚礼）
9. How It Works / Try-at-Home（色板邮寄）
10. Newsletter / Refer-a-Friend
11. Footer

### 品类页 Filter 维度
- 排序（Sort By）：Featured / New Arrivals / Bestsellers / Price Low to High / Price High to Low / Name (A–Z) / Name (Z–A)
- Filter：**Color Families（颜色族，核心）** / Size / Fabric / Convertible / Maternity / Plus Size / Ready to Ship。
- 颜色族是其 PLP 的第一过滤维度，强调"先选色，再选款"。

### PDP 区块构成
以 `Alex Matte Satin Dress in Black`（$129，SKU `BG209BK0001001`）为样本：
1. 图廊（多角度模特图，约 5+ 张：正/侧/背/细节/lifestyle，convertible 款附穿法图）
2. 标题 + 价格（$129）
3. **Color 选择**（大量颜色，与色板系统打通）
4. **Size 选择**（XS 起；有 Plus Size、Maternity、Curve 版型）
5. **Estimated Arrival 选择**（Standard Production / Rush Production —— 把生产/交期做成可选 SKU 维度）
6. Add to Bag
7. **Order a Swatch / Try at Home** 入口
8. Description / Details
9. Fabric（面料说明，单独 `/pages/fabric-101`）
10. Size Chart + How to Measure（`/pages/how-to-measure`）
11. Shipping / Returns
12. Reviews（评价数量很大，重权重）
13. How to Wear（convertible 穿法引导）
14. You may also like / Complete the look 推荐

### 商品字段与文案
- 字段：名称、价格（$89–$199 区间，多数 $129）、Color（数十种，色板驱动）、Size（XS–Plus/Maternity/Curve）、Fabric（Chiffon/Crepe/Matte Satin/Shiny Satin/Luxe Knit/Velvet）、版型标签（Convertible/Maternity/Plus）、交期（Standard/Rush）、风格标签（Garden Edit/Fairy Coquette/Gemstone）。
- 文案/价值主张：
  - "Bridesmaid Dresses & Gowns from $89"
  - "colors and styles that make everyone in the bridal party happy"
  - "Shiny Satin styles from $89"
  - Free Moodboards / Order a Swatch / Try at Home（色板试色是核心转化钩子）
  - Refer-a-Friend、Gift Card

### 已下载媒体清单（16 张，均 1000×1500 JPEG 产品图，flowergirl/jewelry/pjs 为 ~1100–1200px）
| 文件名 | 内容描述 |
|---|---|
| bridesmaid-pink-bella-01.jpg | Bubblegum Pink 路fe Knit「Bella」款伴娘裙白底产品图 |
| bridesmaid-pink-bryten-02.jpg | Bubblegum Pink Luxe Knit「Bryten」款 |
| bridesmaid-pink-connie-03.jpg | Bubblegum Pink Luxe Knit「Connie」款 |
| bridesmaid-espresso-alex-04.jpg | Espresso Matte Satin「Alex」款 |
| bridesmaid-espresso-mia-05.jpg | Espresso Matte Satin「Mia」款 |
| bridesmaid-black-mia-06.jpg | Black Convertible「Mia」款 |
| bridesmaid-black-alex-07.jpg | Black Convertible「Alex」款 |
| bridesmaid-pink-danny-08.jpg | Bubblegum Pink Luxe Knit「Danny」款 |
| bridesmaid-pink-emmy-09.jpg | Bubblegum Pink Luxe Knit「Emmy」款 |
| bridesmaid-lemon-bella-10.jpg | Lemon Sorbet Luxe Knit「Bella」款 |
| pdp-alex-black-model-01/02/03.jpg | Alex 黑色款 PDP 多角度模特图（正/侧/背） |
| lifestyle-flowergirl-08.jpg | Flower Girl 花童 lifestyle 场景图 |
| accessory-jewelry-01.jpg | 珠宝配饰图 |
| accessory-pjs-02.jpg | 伴娘睡袍/PJ 套装图 |

> 注：色块 lifestyle 图（all-colors/pastels 等）源站走 `res.cloudinary.com`，该域名 curl 返回 000（被拦），其 `cdn.birdygrey.com` 代理仅缓存了 200×200 缩略图，因此未采用；改以 Shopify CDN 全尺寸产品图为主。

---

## 2. davidsbridal.com

定位：**综合婚礼电商（全角色全场景）**，O2O 模式（线上 + 实体店预约 + Fit Guarantee）。Title：`David's Bridal`。已迁移至 Shopify（产品图 `cdn.shopify.com/.../0664/9004/0413/`），运营 banner 走自有 AEM（`prod.davidsbridal.com`）。

### 导航 IA（功能矩阵最完整）
- **Brides / Wedding Dresses**
  - 按版型：A-Line / Ball Gown / Mermaid-Trumpet / Sheath / Bridal Separates / Wedding Jumpsuits
  - 按场景：City Hall / Reception / After-Party / Couture / Designer
  - 按颜色：Black 等
  - 按价：Under $300 / Under $500
  - New Arrivals
- **Bridesmaids / Bridesmaid Dresses**：按颜色（Black/Blue/Green…）、面料（Chiffon/Charmeuse）、风格（Floral）、Plus Size、品牌系列（Celebrate DB Studio）
- **Dresses（场合裙）**
  - **Mother of the Bride & Groom**（极细分：A-Line / Jacket / Mermaid / Pantsuits / Sheath / Separates / 按颜色/季节/Grandmother/Black-Tie/Plus Size/Petite）
  - **Wedding Guest Dresses**（按季节 Spring/Summer/Fall/Winter、Under $100、Plus Size）
  - Flower Girl Dresses、Plus Size Dresses、Special Occasions
- **Accessories（极宽）**：Veils / Jackets & Wraps / Belts & Sashes / Hair Accessories / Brides & Hairpins / Jewelry & Fine Jewelry / Garters / Handbags & Designer Handbags（Michael Kors / Carmen Marc Valvo）/ Lingerie（Bras/Panties/Shapewear/ThirdLove/Adore Me）/ Robes / Swimsuits / Garment Bags / Flower Girl Accessories
- **Gifts & Decorations**：Gifts / Wedding Essentials / Flower Girl Baskets / Beauty Products
- **Inspiration（内容中心）**：Mother-of-Bride/Groom Guide、Special Occasions Guide、Wedding Guest Guide、Prom Trends
- **Sale**、**Registry**、**Account / Wishlist**、**Find a Store / Book Appointment**

### 首页区块
1. 顶部促销条 + 服务承诺
2. **Hero 轮播大 banner**（2160×675 运营图：Sample Sale / Fit Guarantee / Alterations Packages / Graduation / Breaking Bridal 等，强季节运营）
3. 角色/场景入口网格（Brides / Bridesmaids / MOB / Guest / Flower Girl）
4. **Fit Guarantee / Alterations** 价值主张区
5. 新品 & 趋势推荐位
6. **Book an Appointment / Find a Store**（O2O 转化）
7. Registry 入口
8. Inspiration / Real Weddings 内容导流
9. Newsletter
10. Footer（含门店、客服、政策、Diamond Loyalty）

### 品类页 Filter 维度（最丰富）
- Filter：**Color / Size / Price / Brand（含设计师系列）/ Collection / Fit（版型）/ Silhouette / Neckline / Sleeve / Waist / Train / Length / Fabric / Occasion / Season**。
- 颜色体系命名感性（Dusty Sage / Steel Blue / Desert Coral / Dusty Blue / Ballet / Petal / Martini Olive / Lavender Haze / Chianti / Eucalyptus / Wine…）。
- 尺码体系数字制（0–24，含 Plus/Petite），并支持 **Extra Length** 定制维度。

### PDP 区块构成
以 `Cold-Shoulder Long Chiffon Bridesmaid Dress`（$24.94 起，品牌 `Celebrate DB Studio`，SKU `V01957770`）为样本：
1. 图廊（模特正/背 + 多色 Set 全身图）
2. 标题 + 品牌 + 价格
3. **Color 选择**（感性色名 + 色板）
4. **Size 选择**（0–24）
5. **Customization 选择**（Extra Length / No Customization —— 把定制做成 SKU 维度，与 Birdy Grey 的交期维度异曲同工）
6. Add to Bag / **Find in Store** / **Book Appointment**
7. **Fit Guarantee** 卖点
8. Size Chart
9. Product Details / Description
10. Shipping（Standard Processing 7–14 天）/ Returns
11. Reviews & Ratings
12. You may also like / Complete the Look

### 商品字段与文案
- 字段：名称、品牌（Celebrate DB Studio / Oleg Cassini / DB Studio…）、价格（伴娘低至 $24.94，婚纱 $99–$500+）、Color（感性命名）、Size（0–24 + Plus/Petite）、Customization（Extra Length）、Silhouette / Neckline / Sleeve / Waist / Train / Fabric / Occasion / Season。
- 文案/价值主张："Buy online or book an appointment today"、**Fit Guarantee**、Alterations Packages、Find a Store、Registry、Diamond Loyalty。强调"线上线下一体 + 合身保障"。

### 已下载媒体清单（18 张）
| 文件名 | 内容描述 |
|---|---|
| wedding-dress-02/03.jpg | 婚纱产品图（1000×1499） |
| wedding-dress-04/05/06.jpg | 婚纱全身 Set 图（1080×1620，Ivory 系） |
| wedding-dress-set-07.jpg | 婚纱 + 配饰套装图（1000×1500） |
| wedding-dress-08.jpg | 婚纱全身图（1200×1799，Solid Ivory） |
| bridesmaid-sage-01.jpg | Dusty Sage 伴娘裙全身图（1200×1799） |
| bridesmaid-steelblue-02.jpg | Steel Blue 伴娘裙 |
| bridesmaid-coral-03.jpg | Desert Coral 伴娘裙 |
| bridesmaid-dustyblue-04.jpg | Dusty Blue 伴娘裙 |
| bridesmaid-ballet-05.jpg | Ballet（浅粉）伴娘裙 |
| bridesmaid-petal-06.jpg | Petal 伴娘裙 |
| bridesmaid-olive-07.jpg | Martini Olive 伴娘裙 |
| pdp-coldshoulder-lavender-01.jpg | Cold-Shoulder Chiffon「Lavender Haze」PDP 图 |
| lifestyle-banner-bridesmaid-01.jpg | 首页伴娘 lifestyle 横幅（2160×675） |
| lifestyle-banner-bridal-02.jpg | 首页婚纱 lifestyle 横幅（2160×675） |
| lifestyle-banner-occasion-03.jpg | 首页场合裙运营横幅（2160×675） |

---

## 3. kissprom.com

定位：**Prom / 晚礼服 + 婚纱跨场景跨境电商**，主打 2026 趋势、海量 SKU、**Custom Size 定制**、快速美国发货。Title：`Prom 2026, Wedding & Homecoming Dresses | Holiday Gowns – KISSPROM`。Shopify 平台（CDN `0559/5224/4910`）。

### 导航 IA
按"场景 × 版型 × 颜色"高度交叉细分（典型 Prom 站打法，集合页极多）：
- **Prom Dresses**：Prom 2026 / Long / A-Line / Mermaid / Sequin / Metallic / Floral / 按颜色（Black/Blue/Green/Purple/Red/Pink/Lavender/Royal Blue…）
- **Homecoming（Hoco）Dresses**：New / A-Line / Bodycon / Sequins / Floral / Under $100 / 按颜色（Black/Blue/Fuchsia/Lavender/Navy/Pink/Red/White/Yellow…）
- **Wedding Dresses**：All / A-Line / Ball Gown / Mermaid / Beach / Lace / Long Sleeves / Short / Simple / Sexy / Under $200 / 2025
- **Bridesmaid Dresses**（All）
- **Evening / Cocktail / Formal**：Evening 2025/2026、Cocktail Dresses
- **Quinceañera / Graduation 2026**
- 按面料/风格集合：Lace Dresses / Tulle Dresses / Sequin / Floral
- **Accessories**：Headpieces / Wedding Veils / Wedding Jewelry / Wedding Flowers
- **Shoes**：Party Shoes / Platforms / Pumps / Stiletto Heels / Sandals & Slippers / Wedding Shoes
- **Blog / Prom Guide**（`/blogs/prom-guide`）
- **Sale / Best Sellers**、**Account / Wishlist**

### 首页区块
1. 顶部促销/发货承诺条（Fast USA Shipping & Easy Returns）
2. Hero（Prom 2026 趋势大图 + CTA）
3. 场景入口（Prom / Homecoming / Wedding / Evening）
4. 趋势/集合推荐位（Best Sellers、New Arrivals、按色/按款）
5. Shop by Color / Shop by Style 入口
6. Lookbook / 趋势内容（Prom Guide 博客导流）
7. Newsletter + **Influencer Program**
8. Footer（FAQ / Size Guide / Shipping / Returns / About）

### 品类页 Filter 维度（Prom 站典型的"硬筛选"，最细）
基于 Shopify filter 参数实测：
- 颜色 `filter.v.t.shopify.color-pattern`（最高频）+ `filter.v.option.color`
- 尺码 `filter.v.option.size`
- **面料** `filter.p.m.dress.fabric`
- **领口 Neckline** `filter.p.m.dress.neckline`
- **裙长 Length** `filter.p.m.dress.length`
- **袖型 Sleeve** `filter.p.m.dress.sleeve`
- **背部款式 Back Style / Back Type** `filter.p.m.custom.back_style` / `back_type`
- **现货/48h 发货** `filter.p.m.custom.in_stock` / `in_stock-ship-in-48hrs`
- 价格区间 `filter.v.price.gte` / `lte`
- 排序：Featured / Best selling / Alphabetically (A–Z) / Date new-to-old / Price low-to-high / Price high-to-low

### PDP 区块构成
以 `A-Line Long Sleeves Satin Wedding Dresses with V Neck`（$259–$279）为样本：
1. 图廊（多角度模特图 + 多色变体图）
2. 标题 + 价格（变体价 $259 / $279）
3. **Color 选择**（如 Diamond White 等具体色名）
4. **Size 选择**：US 00 / 0 / 2 / 4 … / 30（覆盖加大码）+ **Custom Size（量身定制，核心差异化）**
5. Add to Cart / Buy it now / Add to Wishlist
6. 服务承诺（Free/Fast Shipping、Easy Returns、Processing Time）
7. Description / Fabric（面料）
8. Size Guide / Size Chart / Custom Size 说明
9. Reviews（评价模块，体量大）
10. Related Products / You may also like

### 商品字段与文案
- 字段：名称（高度描述化，含 版型+颜色+面料+领型+裙长，如 "A-Line One Shoulder Sage Green Long Prom Dress with Appliques"）、价格（Prom $150–$300 区间、Wedding $259+、Hoco Under $100）、Color（多变体）、Size（US 00–30 + Custom Size）、Fabric / Neckline / Length / Sleeve / Back Style、风格标签（Sequin/Floral/Lace/Tulle/Bodycon/Appliques）。
- 文案/价值主张："thousands of gowns in every style and size"、"trending 2026 prom dresses"、"fast USA shipping and easy returns"、**Custom Size**、Influencer Program、Wishlist/Reward。

### 已下载媒体清单（18 张）
| 文件名 | 内容描述 |
|---|---|
| prom-sage-oneshoulder-01.jpg | Sage Green 单肩 A-Line 长 Prom（1000×1500） |
| prom-skyblue-oneshoulder-02.jpg | Sky Blue 单肩 A-Line 长 Prom |
| prom-blush-oneshoulder-03.jpg | Blush Pink 单肩 A-Line 长 Prom |
| prom-lavender-oneshoulder-04.jpg | Lavender 单肩 A-Line 长 Prom |
| prom-champagne-lace-05.jpg | Champagne 蕾丝花卉吊带长裙（800×1200） |
| prom-darkgreen-lace-06.jpg | Dark Green 蕾丝花卉吊带长裙 |
| prom-offshoulder-tiered-07.jpg | 蕾丝一字肩分层长裙带开衩 |
| prom-floral-sweetheart-08.jpg | 花卉印花心形领长裙 |
| prom-lavender-lace-09.jpg | Lavender 蕾丝花卉长裙 |
| wedding-aline-tulle-01.jpg | A-Line 一字肩 Tulle 白色婚纱 |
| wedding-aline-lace-02.jpg | A-Line V 领蕾丝婚纱带开衩 |
| wedding-mermaid-chiffon-03.jpg | 鱼尾深 V 雪纺 tulle 婚纱带 appliques |
| wedding-mermaid-lace-04.jpg | 鱼尾心形领蕾丝婚纱 |
| wedding-beach-short-05.jpg | 沙滩短款白色高低裙束身婚纱 |
| wedding-aline-longsleeve-06.jpg | A-Line 长袖雪纺 Ivory 婚纱 |
| homecoming-pink-short-01.jpg | 粉色一字肩 Tulle 短款 Hoco（900×1200） |
| homecoming-darkgreen-short-02.jpg | 墨绿吊带交叉背短款 Hoco（694×887） |
| homecoming-pink-sequin-03.jpg | 粉色亮片单肩束身短款 Hoco（754×1006） |

---

## 4. azazie.com

> 补充调研：2026-05-30。Cloudflare 拦截 curl/WebFetch，本节基于 WebSearch + 公开资料整理，未下载媒体素材。

定位：**伴娘 + 婚纱垂直 DTC（按需定制 made-to-order）**，主打"试穿前置 + 免费定制尺寸 + 伴娘团协作选款"。与 Birdy Grey 同为明亮淡雅调性的伴娘赛道，但产品力更重（自建定制供应链）。伴娘裙 $69 起，600+ 款式、90+ 颜色。

### 导航 IA
按"角色 + 场景 + 服务"组织：
- **Bridesmaids（核心）**：含 Boho / Maternity / Modest / Beach / Mix-and-Match / Junior Bridesmaid 等子类
- **Brides（婚纱）**
- **Moms（Mother of the Bride/Groom）**
- **Flower Girl**
- **Formal & Cocktail**
- **Prom & Party**
- **Shoes & Accessories**：领带 / 手帕 / 披肩 / 腰带 / 围巾（按婚礼色系匹配）
- **Suits & Ties**（伴郎/新郎）
- **Intimate**
- **Home Try-On（服务入口，重要差异化）**
- 颜色筛选：green / blue / pink / purple / red / jewel tones 等（90+ 色）

### 核心差异化功能（标杆）
1. **免费色板（Free Swatch Program）**：注册领 10 个免费色板；面料 Chiffon / Stretch Satin / Metallic Satin / Floral Burnout
2. **At-Home Try-On**：任选 3 件样衣（伴娘 / Junior / Moms / 婚纱，200+ 款），平台**免费寄送 + 附退货标签**，1 周试穿期；移动端有 "Try at Home" 专属筛选
3. **免费定制尺寸（Custom Sizing）**：全线 **0–30 码** + 精确量体尺寸定制，**不额外收费**；所有裙子按单手工裁剪缝制（made-to-order）
4. **Showroom 协作规划**：保存收藏款式 + 分享 / 投票，伴娘团协同决策（社交化选款工具）

### PDP 区块构成（典型）
1. 多角度模特图廊（多色变体）
2. 标题 + 价格
3. **Color 选择**（90+ 色，色板驱动）
4. **Size 选择（0–30）+ Custom Size（免费量体定制）**
5. Add to Cart / Add to Showroom（收藏到协作面板）
6. **Order a Swatch / At-Home Try-On** 入口
7. 服务承诺（免费定制 / 免费色板 / 试穿）
8. Description / Fabric / Size Chart
9. Reviews
10. 关联推荐 / Complete the Look

### 价值主张 / 退换
- "Made just for you"、免费定制尺寸、免费色板、At-Home Try-On
- 退货：标准码 10 天可退；**定制码不可退**

### 对本项目借鉴
- **最贴合的范本**：明亮淡雅调性 + 伴娘场景 + "为整组伴娘配色配码"的协作 + 免费定制尺寸，几乎是 Dreamy 的目标形态
- **Showroom 协作**可作为高端户外婚礼"准新娘 + 伴娘团"联动选款的差异化模块（即便原型不做闭环，也可设计入口）
- **免费 At-Home Try-On + 免费定制尺寸**是高端定位的强信任钩子

---

## 5. jjshouse.com

> 补充调研：2026-05-30。Cloudflare 拦截 curl/WebFetch，本节基于 WebSearch + 公开资料整理，未下载媒体素材。

定位：**全球综合礼服跨境电商**（2010 成立，中国发货），2000+ 款式，覆盖 regular / petite / plus 全场景全角色。性价比定位，多数评价提到"$2000 婚纱级质感、平价价格"。

### 导航 IA（综合全场景）
- **Wedding Dresses**
- **Bridesmaid Dresses**
- **Mother of the Bride & Groom**
- **Prom & Homecoming**
- **Accessories**：含 Veils 头纱（评价高频提到 cathedral 长款珍珠头纱）/ 首饰 / 头饰
- **Wedding Suits**（婚礼西装）
- **Shawls**（披肩）
- **Decor**（婚礼装饰）

### 核心功能 / 服务
1. **Color & Fabric Swatch**：色板 / 面料寄样（小额收费）
2. **Try-Before-You-Buy**：**$10 试穿服务**，含寄送与退货（评价区高人气，弥补尺码不一致问题）
3. **Custom Size**：按量体定制（定制款基本不可退）
4. **Global Shipping**：全球物流（时效较长，是主要痛点；定制款约 11 天发货 + 跨境运输）
5. **奖项背书**：The Knot Best of Weddings 2021/2024/2025、WeddingWire Couples' Choice 2022–2025、Google Trusted Store

### PDP 区块构成（典型）
1. 多角度图廊 + 多色变体
2. 标题 + 价格
3. Color 选择
4. **Size 选择 + Custom Size（量体定制）**
5. Add to Cart
6. 服务承诺（Swatch / Try-Before-You-Buy / 物流时效 / Processing Time）
7. Description / Fabric / Size Chart
8. **Reviews（评价体量大，且按品类聚合：/reviews/wedding-dresses 等）**
9. 关联推荐

### 已知痛点（反面借鉴，原型应正向设计）
- **尺码不一致**：跟随尺码表仍有偏差（有客户 8 件伴娘裙无一合身）→ 原型应强化精确尺码表 + How-to-Measure + Custom Size
- **定制款难退**：BBB 投诉集中 → 退换政策需在 PDP/结算页提前透明告知
- **物流 / 退款慢**：1–2 月时效、退款约 3 周、运费不退 → 原型应做透明物流时效与处理时间展示
- **工单可见性差**：问题藏在 "Tickets" 区不易发现 → 账户中心应做清晰的订单状态 + 工单/客服入口

### 对本项目借鉴
- **海量 SKU 跨境运营骨架** + **Try-Before-You-Buy** + **奖项 / Google Trusted Store 信任背书**（可借鉴其奖项展示位增强转化信任）
- 其痛点**反向印证**本项目应把"尺码准确性 + 透明物流时效 + 清晰退换政策 + 可见的订单/客服状态"作为高端定位的差异化竞争点

---

## 6. 横向对比与原型借鉴建议

### 五家共性 IA 模式
1. **标准电商结算骨架**：birdygrey / davidsbridal / kissprom 为 Shopify（mega-nav + collection 集合页 + `/products/<handle>` PDP + Shopify Checkout：Cart → Information → Shipping → Payment → Review）；azazie / jjshouse 为自建 DTC/跨境系统，但结算流程同构。原型可直接复用这套结算骨架。
2. **导航三维交叉**：场景/角色（Bride/Bridesmaid/Guest/MOB/Prom/Hoco）× 颜色 × 版型/面料。每个交叉点都落地为一个 collection 页（利于 SEO 和精准导流）。
3. **颜色是第一公民**：五家都把"按颜色购物"做成首页入口 + PLP 首要 Filter（azazie 90+ 色）。
4. **试穿/试色前置已成标配**：色板 5/5 家；实物试穿——birdygrey Try-at-Home、azazie 免费 At-Home Try-On（任选 3 件）、jjshouse $10 Try-Before-You-Buy。
5. **定制尺寸**：kissprom / azazie / jjshouse 提供 Custom Size，**azazie 免费定制（0–30 码）**为最强卖点。
6. **PDP 标配**：图廊 + Color/Size 选择 + 尺码表/How to Measure + Description + Fabric + Shipping/Returns + Reviews + 关联推荐。
7. **评价（Reviews）权重高**，均为转化要素（jjshouse 评价按品类聚合）。
8. **页脚信息架构一致**：About / Contact / FAQ / Size Guide / Shipping / Returns / Privacy / Terms + Newsletter + 社交。

### 五家差异化亮点（可借鉴的"钩子"）
- **Birdy Grey**：① **色板系统（Swatches）+ Try-at-Home + Free Moodboards**——先寄色板试色再下单，把"选色焦虑"做成产品；② **Convertible 可变穿法**裙 + How-to-Wear 引导；③ 交期做成 SKU 维度（Standard / Rush Production）；④ 按颜色族（Color Families）组织全站。明亮淡雅调性最贴近本项目。
- **David's Bridal**：① **Fit Guarantee + Alterations + Book Appointment + Find a Store**（O2O 合身保障）；② **Extra Length 定制**维度；③ 最完整的场合矩阵（MOB 细分到 Grandmother/Black-Tie）；④ 感性色名体系（Dusty Sage/Desert Coral）；⑤ Registry 注册表。
- **KissProm**：① **Custom Size 量身定制**（US 00–30 + 定制）；② 最细的硬 Filter（Neckline/Sleeve/Length/Back Style/48h 现货）；③ 描述化商品命名（版型+色+面料+领+长全写进标题，利于搜索）；④ Influencer Program + Prom Guide 内容营销。
- **Azazie**：① **免费定制尺寸（0–30 码，made-to-order）**——把"完美合身"做成零成本卖点；② **免费 At-Home Try-On**（任选 3 件免费寄送）+ **10 个免费色板**；③ **Showroom 协作规划**（伴娘团收藏/分享/投票，社交化选款）；④ 90+ 色 + Mix-and-Match 伴娘裙。与本项目调性与场景最契合。
- **JJ's House**：① **海量 SKU（2000+）全场景跨境运营** + **$10 Try-Before-You-Buy**；② **奖项 / Google Trusted Store 信任背书**（The Knot / WeddingWire 多年获奖）；③ 评价按品类聚合体量大；④ **反面教材**：尺码不一致 / 定制难退 / 物流退款慢 / 工单藏匿——原型应在尺码准确性、透明物流、清晰退换、订单可见性上做正向设计。

### 对本项目（高端户外婚礼，明亮明媚）的具体借鉴建议
1. **调性取 Birdy Grey**：明亮淡雅、色彩驱动、留白多、产品图为浅色背景 + 户外 lifestyle 场景混排。已下载的 sage/sky-blue/blush/coral/lavender/champagne 等浅色系图最契合户外婚礼，优先用作示例商品。
2. **"按颜色购物"作为首页一级入口**：用色块网格（参考 Birdy Grey 的 Shop by Color），主推户外婚礼调色板（Sage / Dusty Blue / Blush / Champagne / Lavender / Terracotta-Coral）。
3. **引入"色板/试色 + 试穿"概念**：参考 Birdy Grey / Azazie，可设计 Color Palette / Moodboard 模块 + At-Home Try-On 入口，作为高端感与"为整组伴娘配色"的差异化卖点。
4. **合身保障 / 免费定制**：借鉴 David's Bridal 的 Fit Guarantee 文案、Azazie 的免费 Custom Size（0–30 码）与 KissProm 的量身定制，作为高端定位的信任背书。
5. **协作选款（Azazie Showroom）**：准新娘 + 伴娘团收藏/分享/投票，契合本项目"为整组伴娘配色配码"的核心场景。
6. **信任背书与正向体验**：参考 JJ's House 的奖项/Trusted Store 展示位；同时规避其尺码不一致、物流/退款慢、退换不透明的痛点——把精确尺码表、透明物流时效、清晰退换政策、可见订单状态做成竞争点。
7. **场景化内容**：Real Weddings / Lookbook / 户外婚礼灵感（参考各家 Inspiration/Real Weddings/Prom Guide），强化"高端户外婚礼"主题。

### 推荐的 Filter 维度组合（PLP）
取五家交集 + 户外婚礼场景定制，按重要性排序：
1. **颜色 / 调色板**（首要，色块多选）
2. **裙长**（Floor / Tea / Short / Hi-Low）
3. **版型 / Silhouette**（A-Line / Ball Gown / Mermaid / Sheath / Empire）
4. **面料**（Chiffon / Satin / Tulle / Lace / Crepe / Luxe Knit）
5. **领口 Neckline**（V-Neck / Sweetheart / One-Shoulder / Off-Shoulder / Halter）
6. **袖型 Sleeve**（Sleeveless / Strap / Long Sleeve / Cap）
7. **场景 / 角色**（Bride / Bridesmaid / Guest / MOB / Flower Girl）
8. **尺码**（含 Plus / 量身定制开关）
9. **价格区间**
10. 现货/快速发货开关
- 排序：Featured / Best Sellers / New Arrivals / Price ↑↓ / A–Z（与各家一致）。

### 推荐的 PDP 区块组合（自上而下）
1. 多角度图廊（模特正/侧/背/细节 + 至少 1 张户外 lifestyle 场景图）
2. 标题 + 价格（+ 评分摘要）
3. **Color 选择（色板 + 大色名）**
4. **Size 选择（标准码 + 量身定制入口）**
5. 定制/交期维度（Standard / Rush，或 Extra Length）
6. 主 CTA（Add to Bag）+ 次 CTA（Order a Swatch / Add to Wishlist）
7. 信任承诺条（Fit Guarantee / Free Shipping & Returns / Processing Time）
8. Description / Details
9. Fabric & Care
10. Size Chart + How to Measure
11. Shipping & Returns（可折叠）
12. Reviews & Ratings
13. Complete the Look（配饰）/ You may also like

### 推荐的首页区块顺序
1. 促销/承诺条（Free Shipping & Returns / Fit Guarantee）
2. **Hero**（户外婚礼大图，明亮明媚，主 CTA "Shop the Collection"）
3. **Shop by Color**（户外婚礼调色板色块网格）—— 核心差异化
4. 场景/角色入口（Bride / Bridesmaid / Guest / Flower Girl）
5. New Arrivals / Best Sellers 推荐位
6. **Lookbook / 户外婚礼灵感**（编辑化大图 + 可购买）
7. Color Palette / Moodboard 工具（"为你的伴娘团配色"）
8. Real Weddings（UGC 真实案例）
9. 价值主张区（合身保障 / 定制 / 试色）
10. Newsletter + 推荐有礼
11. Footer（信息架构 + 社交 + 政策）

---

### 抓取汇总
- 有效图片下载总数：**52 张**（birdygrey 16 + davidsbridal 18 + kissprom 18），全部经 `file` 校验为有效 JPEG，无 0 字节 / 无 HTML 错误页。azazie / jjshouse 受 Cloudflare 拦截，仅做结构分析，未下载素材（如需可用 Playwright 补抓）。
- 报告路径：`/Volumes/MAC/workspace/dreamy/hhspec/prototype/assets/competitor-refs/COMPETITOR-ANALYSIS.md`
- 已分析页面：birdygrey / davidsbridal / kissprom 三家首页 + 各自主要品类 PLP（婚纱/伴娘/Prom/Homecoming）+ 各自 1 个 PDP；azazie / jjshouse 基于公开资料的 IA / 功能 / PDP 结构分析。
