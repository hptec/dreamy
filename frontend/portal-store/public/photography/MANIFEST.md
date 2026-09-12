# Dreamy 摄影图库清单（第三轮 · 2026-09-12 · 高级感重做）

本目录 36 张图全部来自 **Unsplash**（Unsplash License：免费商用、无需署名、可修改；已排除 Unsplash+ 付费图）。
第三轮以《视觉标准与艺术指导简报》（quiet luxury / 户外暖米 / 主体先于环境）重做全部营销位：
前两轮 24 张图（含乐高车、风景空景、暗调背光等）已全部移除。

## 选图与调色纪律

- **发现**：Unsplash 官方搜索（真实 Chromium 会话）28 组关键词 × 3 页 → 1,142 张免费图 → PIL 量化预筛（L 120–225 / 黑切 ≤12% / S ≤90 / 近白 R−B ≥ −4）→ 380 张 → **逐张目检**（本会话 Read 可直读图片）→ 36 张落位。
- **主体规则**：每张营销图必须有穿着婚纱/礼服的人物（Accessories 位允许人体局部 + 配饰；About 允许工坊场景）；无人风景零容忍。
- **调色配方（PIL，顺序固定）**：近白像素白平衡至 R−B +8..+16 → 黑点抬升至 14 → gamma 迭代到目标亮度（hero 172 / 卡片 172 / 细节 185）→ 高光柔和回收（>200 段）→ 高光 knee（>205/215 段软压，白切归零）→ HSV 饱和度收敛 25–65 → JPEG q84，长边 hero 2000 / 卡片 1500。
- **门槛（PIL 实测，600px 缩图）**：营销图 L 均值 hero 150–195 / 卡片 140–200；黑切（L<30）≤ 3%（森林位 ≤ 8%）；近白 R−B +4..+22。

## 落位表（36 张）

| 文件 | 用途 / 画面（目检） | Unsplash 页面 | CDN 源 | 调色后实测 | License |
|---|---|---|---|---|---|
| hero-coastal.jpg | 首页 hero #1 Coastal：新娘独立于浅色沙质海崖，白纱与薄雾天空同调，高调柔光，右侧主体左侧留白 | https://unsplash.com/photos/Sa_yng06Weg | https://images.unsplash.com/photo-1664762786271-0de3ba34ae65 | L 176.5 / 黑切 0.3% / S 52.1 / R−B 6.3 | Unsplash License |
| hero-garden.jpg | 首页 hero #2 Garden：新娘在松林公园小径，头纱被风吹起，逆光柔和 | https://unsplash.com/photos/YE4LB51mDk8 | https://images.unsplash.com/photo-1537062223249-aec61b3e4d6d | L 166.6 / 黑切 0.0% / S 36.8 / R−B 9.3 | Unsplash License |
| featured-atelier.jpg | Featured「Cut for You」：裁缝双手为蕾丝婚纱扣扣（制作证据，窗光） | https://unsplash.com/photos/ekHkF65XR-A | https://images.unsplash.com/photo-1632378464836-a6a856632552 | L 167.9 / 黑切 0.0% / S 42.7 / R−B 11.2 | Unsplash License |
| featured-bridesmaids.jpg | Featured「One Dress, Six Ways」：三位伴娘背影（藕粉/淡紫同色系不同款）+ 新娘 | https://unsplash.com/photos/2S6xEssCbt8 | https://images.unsplash.com/photo-1552223412-61c0b0de9eb7 | L 169.2 / 黑切 0.2% / S 51.4 / R−B 10.7 | Unsplash License |
| plp-wedding-dresses.jpg | 分类 hero Wedding Dresses：新娘全身 A 字纱裙立于海岸礁石，金色薄雾 | https://unsplash.com/photos/osjQOp0NFb4 | https://images.unsplash.com/photo-1587318634139-bbc108e44808 | L 186.9 / 黑切 0.0% / S 33.5 / R−B 8.2 | Unsplash License |
| plp-bridesmaids.jpg | 分类 hero Bridesmaids：七位伴娘鼠尾草/薄荷色礼服海滩成组 + 花童 | https://unsplash.com/photos/3-qWXJDgvWU | https://images.unsplash.com/photo-1725044551825-45c484ae6f2d | L 172.1 / 黑切 0.4% / S 24.4 / R−B 10.3 | Unsplash License |
| plp-occasion.jpg | 分类 hero Occasion & Party：香槟色钉珠晚礼服，石材壁龛前 | https://unsplash.com/photos/K-fjUm5X6hU | https://images.unsplash.com/photo-1746025242964-a3e41e3ac049 | L 170.2 / 黑切 0.0% / S 25.6 / R−B 10.2 | Unsplash License |
| plp-accessories.jpg | 分类 hero Accessories：珍珠水滴耳饰 + 手部 + 网纱袖特写 | https://unsplash.com/photos/kCAlBlcsuVA | https://images.unsplash.com/photo-1655048955753-04b75e7622ca | L 170.1 / 黑切 0.0% / S 51.0 / R−B 11.5 | Unsplash License |
| plp-all-styles.jpg | 分类 hero All Styles：新娘与两位藕粉色伴娘背影同框（松林草地） | https://unsplash.com/photos/RiXnsL4kpGY | https://images.unsplash.com/photo-1552221856-cd364b9822a0 | L 167.1 / 黑切 0.1% / S 46.1 / R−B 6.8 | Unsplash License |
| rw-santa-fe.jpg | Real Wedding Santa Fe / Desert Garden：新娘手持蒲苇+酒红花束，沙漠山丘暖光（seed 首位，兼作 Real Weddings 页 hero） | https://unsplash.com/photos/Tp00n0CRjJQ | https://images.unsplash.com/photo-1758565177153-570f99aa43eb | L 168.7 / 黑切 0.0% / S 46.5 / R−B 8.0 | Unsplash License |
| rw-charleston.jpg | Real Wedding Charleston / Coastal Garden：新人在白色花艺拱门下起舞，头顶西班牙苔藓橡树（原竖图裁 4:5） | https://unsplash.com/photos/YDGGXUclqvI | https://images.unsplash.com/photo-1776383081653-604bf98fd168 | L 180.3 / 黑切 0.1% / S 41.1 / R−B 9.3 | Unsplash License |
| rw-tulum.jpg | Real Wedding Tulum / Barefoot Beach：新人赤足牵手漫步浅色沙滩 | https://unsplash.com/photos/UudvF0Zfw9U | https://images.unsplash.com/photo-1747419003011-97b50122a653 | L 179.4 / 黑切 2.7% / S 49.9 / R−B 6.6 | Unsplash License |
| rw-big-sur.jpg | Real Wedding Big Sur / Cliffside：新娘薄纱裙立于海崖草地俯瞰海湾 | https://unsplash.com/photos/A1N59dDK9m4 | https://images.unsplash.com/photo-1653628890170-ea242ff6d1b4 | L 161.4 / 黑切 0.0% / S 42.9 / R−B 6.7 | Unsplash License |
| rw-oregon.jpg | Real Wedding Oregon / Forest & Moss：新人在桉树林光斑中 | https://unsplash.com/photos/vS0e56JrRSA | https://images.unsplash.com/photo-1776267890469-572c13b6f2dc | L 163.9 / 黑切 1.5% / S 50.6 / R−B 7.9 | Unsplash License |
| rw-wisconsin.jpg | Real Wedding Wisconsin / Barn & Meadow：新娘在金色麦田旋转裙摆 | https://unsplash.com/photos/OicvZDXO9kA | https://images.unsplash.com/photo-1672344838703-a5fc22950698 | L 193.0 / 黑切 0.1% / S 57.1 / R−B 9.3 | Unsplash License |
| blog-beach-guide.jpg | Journal 海滩着装指南：浅色长裙女子立于海滩礁石，浪花（r4 后全局 R−B 32→19 收暖） | https://unsplash.com/photos/m389ZhNfsCs | https://images.unsplash.com/photo-1592261393678-c2f439d46890 | L 176.9 / 黑切 0.8% / S 56.2 / R−B 5.5 | Unsplash License |
| blog-fabrics.jpg | Journal 面料指南：蕾丝裙边高调特写 | https://unsplash.com/photos/K-zyVx3Jakw | https://images.unsplash.com/photo-1525169087805-031a4da0623c | L 192.3 / 黑切 0.0% / S 23.2 / R−B 13.0 | Unsplash License |
| blog-wind-veil.jpg | Journal 风中头纱：新娘头纱被风高高吹起，金色黄昏 | https://unsplash.com/photos/VUQpGIA3bhE | https://images.unsplash.com/photo-1621196811441-682f5298fbb5 | L 189.8 / 黑切 3.9% / S 38.3 / R−B 8.6 | Unsplash License |
| blog-barn-meadow.jpg | Journal 谷仓与草地：新娘手捧花束立于金色草地 | https://unsplash.com/photos/Snj3VEksbbE | https://images.unsplash.com/photo-1575011732056-edc3c7a9a631 | L 168.9 / 黑切 1.6% / S 65.4 / R−B 6.5 | Unsplash License |
| blog-aisle-hem.jpg | Journal 沙/草/石 aisle 裙长指南：新娘赤足提裙摆 | https://unsplash.com/photos/PDX5nCjTAaQ | https://images.unsplash.com/photo-1549576269-a563007c10ac | L 192.0 / 黑切 0.6% / S 30.5 / R−B 8.6 | Unsplash License |
| blog-timeline-atelier.jpg | Journal 定制工期：裁缝为蕾丝婚纱背部扣扣（工坊） | https://unsplash.com/photos/s6WAWHo7uts | https://images.unsplash.com/photo-1607007790017-40658637b97b | L 165.3 / 黑切 0.0% / S 57.6 / R−B 15.1 | Unsplash License |
| blog-dress-code.jpg | Journal 户外着装规范：女宾客穿粉色长裙在温室花园 | https://unsplash.com/photos/SuttBWNIgw4 | https://images.unsplash.com/photo-1777612959480-9036a2f65a95 | L 162.0 / 黑切 0.1% / S 45.7 / R−B 14.2 | Unsplash License |
| blog-real-weddings.jpg | Journal Real Weddings 章：新人在林间头纱下相拥 | https://unsplash.com/photos/kWS4fSlZUNI | https://images.unsplash.com/photo-1776267887590-5afc3369a74f | L 186.6 / 黑切 1.0% / S 30.1 / R−B 7.3 | Unsplash License |
| lookbook-coastal.jpg | Lookbook Coastal Romance：新娘在礁石海岸，雾光 | https://unsplash.com/photos/zAvlp9D-lEI | https://images.unsplash.com/photo-1662045470097-7df60e32e7ff | L 166.6 / 黑切 4.9% / S 25.4 / R−B 11.4 | Unsplash License |
| lookbook-garden.jpg | Lookbook Garden Edit：新娘在花园奔跑，裙摆飞扬 | https://unsplash.com/photos/bOAHweaf8us | https://images.unsplash.com/photo-1772404245130-0a45c577bce3 | L 166.6 / 黑切 0.3% / S 48.3 / R−B 7.3 | Unsplash License |
| lookbook-golden.jpg | Lookbook Golden Hour：新娘在麦田逆光 | https://unsplash.com/photos/EE1dIl8DJsI | https://images.unsplash.com/photo-1560082073-7b1b2ccbf9b1 | L 174.9 / 黑切 0.9% / S 56.6 / R−B 6.6 | Unsplash License |
| about-atelier.jpg | About hero：造型师为穿蕾丝婚纱的新娘做最后整理（人 + 婚纱，窗光；文案已改为「first fitting to final touch」跟图） | https://unsplash.com/photos/G2h2LtEhwe0 | https://images.unsplash.com/photo-1665703156168-b9c74332a076 | L 165.1 / 黑切 0.0% / S 48.5 / R−B 16.1 | Unsplash License |
| outdoor-hero.jpg | Outdoor Weddings hero：新娘立于海边岩石，蓝天云 | https://unsplash.com/photos/x0qLKq_dAiA | https://images.unsplash.com/photo-1627010972131-3364d07a759f | L 185.0 / 黑切 1.1% / S 46.6 / R−B 10.5 | Unsplash License |
| tile-beach.jpg | Outdoor tile Beach：新娘蕾丝纱裙立于棕榈大道，头纱飘 | https://unsplash.com/photos/9o7ugDmGKwg | https://images.unsplash.com/photo-1593575619794-1deb0104f65b | L 166.8 / 黑切 0.1% / S 56.0 / R−B 12.5 | Unsplash License |
| tile-garden.jpg | Outdoor tile Garden：新娘在花园小径，柔光 | https://unsplash.com/photos/5zwACOXFiBg | https://images.unsplash.com/photo-1776267034712-1e61ccdd0849 | L 164.2 / 黑切 0.5% / S 53.0 / R−B 9.4 | Unsplash License |
| tile-boho.jpg | Outdoor tile Boho：新人在巨人柱仙人掌与山丘前相拥（自 2.2:1 原图裁 3:4） | https://unsplash.com/photos/NzSHljoOmkY | https://images.unsplash.com/photo-1610703892002-81399073fcdc | L 170.7 / 黑切 0.9% / S 57.3 / R−B 7.0 | Unsplash License |
| tile-forest.jpg | Outdoor tile Forest：新人在高大树林中 | https://unsplash.com/photos/J9zHwm2HWws | https://images.unsplash.com/photo-1776267890276-3776e4d1bd50 | L 158.7 / 黑切 0.4% / S 51.0 / R−B 10.8 | Unsplash License |
| tile-vineyard.jpg | Outdoor tile Vineyard：新人背影俯瞰葡萄园山谷 | https://unsplash.com/photos/Xz3LpZb2gWY | https://images.unsplash.com/photo-1633118287620-f4ba5d7bdc9b | L 166.3 / 黑切 0.9% / S 62.7 / R−B 6.2 | Unsplash License |
| inspiration-hero.jpg | Inspiration hero：伴娘们藕粉色礼服背影成组 | https://unsplash.com/photos/AmSSPYrLriQ | https://images.unsplash.com/photo-1495380802461-f7ca08f6595e | L 165.5 / 黑切 0.0% / S 30.7 / R−B 10.6 | Unsplash License |
| login-bride.jpg | Login 左栏：新娘背影立于白色窗帘窗光前，蕾丝露背 | https://unsplash.com/photos/xfNhe75x_vo | https://images.unsplash.com/photo-1611145678882-edb8ab823bc6 | L 209.0 / 黑切 0.0% / S 25.1 / R−B 8.1 | Unsplash License |
| newsletter-bride.jpg | Newsletter 弹窗：戴头纱新娘高调肖像 | https://unsplash.com/photos/DEc62HFUo-4 | https://images.unsplash.com/photo-1718389827959-5c3b30b10fda | L 203.0 / 黑切 0.1% / S 54.6 / R−B 9.4 | Unsplash License |
## 页面接线

- 首页 hero 两帧、Featured 两卡、Real Weddings 六封面、Journal 八封面、Lookbook 三封面：`scripts/seed/data-content.mjs`（数据层，需重播种）
- 分类 hero：`frontend/portal-store/lib/collection-hero.ts`（按 `cat` 参数切换 title/description/hero；主导航三大分类落到 `/products?cat=…` 时不再共用同图同题）
- About / Outdoor Weddings（hero + 5 tile）/ Inspiration / Login / Newsletter：各页面/组件硬编码路径

## 已移除（第一、二轮 24 张）

hero-01/02/03、wedding-beach/garden/forest/desert、blog-beach-attire、blog-garden-fabrics、blog-atelier-timeline（乐高车）、blog-dress-code（餐桌）、blog-real-weddings（红气球）、lookbook-coastal/garden/golden、featured-atelier（捧花）、bride-hero-01（暗调）、bride-hero-02（悬崖小人）、bride-plp、blog-fabric、blog-beach-bride、blog-wind-veil、blog-lace-detail、blog-hem-length。
移除原因：目检发现内容错误（非婚纱主体/风景空景/静物）或曝光 key 过低（L < 100）破坏高级感。
