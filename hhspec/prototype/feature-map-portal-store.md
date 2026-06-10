# Feature Map — portal-store 消费者商城

> Dreamy 消费端完整功能表。门户 ID：`portal-store` | 端口：5173
> 来源：requirements-brief.md + 竞品分析

## 功能表

### M1 导航与首页

| 编号 | 功能名 | 优先级 | 关键交互 |
|------|--------|--------|---------|
| F-001 | 顶部公告条 | Must | 多币种/多语言切换器 + 促销公告轮播 + 隐藏关闭 |
| F-002 | 全局主导航 | Must | Logo + Mega Menu（Wedding Dresses / Special Occasion / Accessories / Outdoor Weddings / Inspiration）+ 搜索 + Wishlist + Account + Cart 图标（含数量角标） |
| F-003 | Mega Menu 下拉 | Must | hover 展开二级品类 + 主题图文导航位 |
| F-004 | Hero 主视觉 | Must | 全屏户外婚礼大图 + 主标题 + 双 CTA（Shop Dresses / Explore Outdoor），可轮播 |
| F-005 | 品类入口卡片区 | Must | 三大品类 + Outdoor 主题，大图卡片 hover 微交互 |
| F-006 | 首页推荐位 | Must | New Arrivals 横滑、Best Sellers 网格、Shop by Color 色板入口 |
| F-007 | 首页内容引流区 | Must | Real Weddings 精选、Lookbook 预览、Bridesmaid Color Palette 入口、品牌价值主张（Free Shipping / Klarna / Global Delivery） |
| F-008 | 全局页脚 | Must | 多列链接（Shop/Help/Company/Legal）+ Newsletter 订阅 + 支付图标 + 社媒 + 币种语言 |

### M2 商品浏览

| 编号 | 功能名 | 优先级 | 关键交互 |
|------|--------|--------|---------|
| F-009 | 品类列表页（婚纱） | Must | 商品网格 + 左侧 Filter 栏 + 顶部排序 + 结果计数 |
| F-010 | 品类列表页（小礼服） | Must | 同上，子类 Tab（Bridesmaid/Mother/Evening/Prom/Cocktail） |
| F-011 | 品类列表页（配饰） | Must | 同上，子类（Shoes/Headpieces/Veils/Jewelry） |
| F-012 | Filter 筛选栏 | Must | 多维筛选：Color、Size（US/UK/AU）、Price Range、Silhouette、Fabric、Neckline、Length、Sleeve、Occasion、Theme（Beach/Garden/Boho…）；多选 chip + 清除全部 |
| F-013 | 排序选项 | Must | Featured / Newest / Price ↑↓ / Best Selling / Top Rated |
| F-014 | 商品卡片 | Must | 主图 + hover 切第二图 + 价格（含分期提示 "or 4× $X with Klarna"）+ 色板小点 + Wishlist 心形 + Quick View 按钮 |
| F-015 | Quick View 弹窗 | Should | 弹出图廊 + 价格 + SKU 选择 + Add to Cart，不离开列表 |
| F-016 | 分页 / 加载更多 | Must | Load More 按钮 + 页码 |
| F-017 | 列表空状态 | Must | 无匹配结果引导（清除筛选 / 推荐热门） |
| F-018 | Outdoor Weddings 主题页 | Must | 主题 Hero + 子主题卡片（Beach/Garden/Boho/Forest/Vineyard）+ 各主题精选商品横滑 + 真实婚礼引流 |
| F-019 | Outdoor 子主题列表 | Must | 进入某子主题后的商品列表（复用 Filter，预置主题标签） |
| F-020 | 搜索结果页 | Must | 搜索框 + 关键词高亮 + 结果网格 + 搜索建议 + 空结果引导 |

### M3 商品详情 PDP

| 编号 | 功能名 | 优先级 | 关键交互 |
|------|--------|--------|---------|
| F-021 | 商品图廊 | Must | 多角度缩略图竖排 + 主图切换 + Hover Zoom 放大镜 |
| F-022 | Lifestyle 场景图 | Must | 图廊中混入户外穿着场景图 |
| F-023 | 走秀视频 | Must | 图廊中嵌入 walking video（点击播放） |
| F-024 | 价格与分期 | Must | 现价 + 划线原价 + Klarna/Afterpay 分期说明 |
| F-025 | SKU 选择 | Must | Color swatch（图片色块）+ Size 选择（含 Size Guide 链接）+ 库存状态 |
| F-026 | 尺码表弹窗 | Must | US/UK/AU 三套尺码对照表 + 测量指引图 |
| F-027 | Add to Cart / Wishlist | Must | 主 CTA Add to Bag + Wishlist + AR 试穿入口（仅按钮，提示 coming soon） |
| F-028 | 商品描述折叠区 | Must | Description / Fabric & Care / Shipping & Delivery 手风琴 |
| F-029 | Bridesmaid Color Palette | Should | 婚纱页推荐搭配的伴娘色板，点击跳对应色礼服 |
| F-030 | 评价与 Q&A | Must | 星级分布 + 评价列表（含买家秀）+ Q&A 问答列表 |
| F-031 | 相关推荐 + Outfit Builder | Must | You May Also Like 横滑 + Complete the Look 搭配（婚纱+鞋+面纱） |

### M4 购物车与结算

| 编号 | 功能名 | 优先级 | 关键交互 |
|------|--------|--------|---------|
| F-032 | Add to Cart Drawer | Must | 加购后右侧滑出抽屉 + 商品摘要 + 小计 + 推荐加购 + 双 CTA |
| F-033 | 购物车页 | Must | 商品列表（改数量/删除/移入 Wishlist）+ 优惠码 + 订单摘要 + 分期提示 |
| F-034 | 购物车空状态 | Must | 空购物车引导继续购物 + 热门推荐 |
| F-035 | 结算-地址步骤 | Must | 邮箱 + 收货地址（含国家选择）+ 账单地址 + 进度指示器 |
| F-036 | 结算-物流步骤 | Must | FedEx / UPS / DHL Express 选项 + 时效与费用 + 礼品包装选项 |
| F-037 | 结算-支付步骤 | Must | Stripe 卡 / PayPal / Apple Pay / Google Pay / Klarna / Afterpay 选项卡 + 卡表单 |
| F-038 | 结算-订单确认 | Must | 订单复核 + 商品/地址/物流/支付摘要 + Place Order |
| F-039 | 下单成功页 | Must | 成功动画 + 订单号 + 预计送达 + 物流跟踪入口 + 继续购物 |
| F-040 | 多币种/多语言生效 | Must | 切换币种后全站价格换算展示；语言切换器（EN/ES） |

### M5 账户中心

| 编号 | 功能名 | 优先级 | 关键交互 |
|------|--------|--------|---------|
| F-041 | 登录 / 注册页 | Must | 邮箱+密码登录 + 注册 Tab + 忘记密码（无社交登录） |
| F-042 | 账户 Dashboard | Must | 欢迎语 + 近期订单 + Wishlist 预览 + 快捷入口 |
| F-043 | 订单列表 | Must | 订单卡片（状态 chip / 缩略图 / 金额）+ 筛选 |
| F-044 | 订单详情 | Must | 商品明细 + 时间线状态 + 金额拆分 + 再次购买 |
| F-045 | 物流跟踪 | Must | 时间轴物流节点 + 地图占位 + 承运信息 |
| F-046 | 收货地址簿 | Must | 地址卡片列表 + 新增/编辑/设默认 |
| F-047 | Wishlist | Must | 收藏网格 + 移入购物车 + 移除 + 空状态 |
| F-048 | Recently Viewed | Must | 浏览历史横滑 |
| F-049 | My Reviews + 账户设置 | Must | 我写的评价 + 个人资料/改密码/通讯偏好 |

### M6 内容栏目

| 编号 | 功能名 | 优先级 | 关键交互 |
|------|--------|--------|---------|
| F-050 | Wedding Inspiration 灵感馆 | Must | 主题灵感网格 + 标签筛选 |
| F-051 | Real Weddings 列表 | Must | 真实婚礼故事卡片网格 + 主题/地点筛选 |
| F-052 | Real Wedding 详情 | Must | 婚礼图文故事 + Shop the Look 商品锚点 |
| F-053 | Blog 列表 | Must | 文章卡片 + 分类 + 搜索 |
| F-054 | Blog 详情 | Must | 文章正文 + 目录 + 相关文章 + 商品嵌入 |
| F-055 | Wedding Planning Guides | Must | 按筹备时间轴的指南列表 + Lookbook 画册预览 |

### M7 营销与全局

| 编号 | 功能名 | 优先级 | 关键交互 |
|------|--------|--------|---------|
| F-056 | Newsletter 弹窗 + Exit Intent | Must | 首访延迟弹出订阅 + 退出意图二次挽留弹窗 + 折扣码 |
| F-057 | Cookie Notice + 全局搜索抽屉 | Must | 底部 Cookie 同意条 + 顶部搜索抽屉（热门词/历史） |
| F-058 | 静态页（About / Contact / FAQ / 404） | Should | 品牌故事 + 联系表单 + FAQ 手风琴 + 404 引导 |

## 页面建议（消费端）

> 每个页面承载的 F-编号见"包含功能"列；导出到 Step 5 作为页面规划起点。

| 页面 | 组件名 | 类型 | 包含功能 | 关键交互 |
|------|--------|------|---------|---------|
| 首页 | Index | 首页 | F-001~F-008 | Hero 轮播、品类入口、推荐横滑、内容引流、Footer |
| 婚纱列表 | WeddingDresses | 列表页 | F-009,F-012~F-017 | Filter + 排序 + 网格 + Quick View |
| 小礼服列表 | SpecialOccasion | 列表页 | F-010,F-012~F-017 | 子类 Tab + Filter |
| 配饰列表 | Accessories | 列表页 | F-011,F-012~F-017 | 子类 + Filter |
| Outdoor 主题页 | OutdoorWeddings | 列表页 | F-018,F-019 | 子主题卡片 + 主题商品横滑 |
| 商品详情 | ProductDetail | 详情页 | F-021~F-031 | 图廊 Zoom/视频 + SKU + 尺码表 + 评价 + 搭配 |
| 搜索结果 | SearchResults | 列表页 | F-020,F-057 | 搜索 + 高亮 + 空结果 |
| 购物车 | Cart | 列表页 | F-033,F-034 | 改数量 + 优惠码 + 摘要 |
| 结算 | Checkout | 表单页 | F-035~F-038,F-040 | 多步进度 + 多支付 |
| 下单成功 | OrderSuccess | 详情页 | F-039 | 成功态 + 跟踪入口 |
| 登录注册 | Auth | 表单页 | F-041 | 登录/注册 Tab |
| 账户总览 | AccountDashboard | 仪表盘 | F-042 | 概览卡片 |
| 订单列表 | AccountOrders | 列表页 | F-043 | 订单卡片 + 筛选 |
| 订单详情 | AccountOrderDetail | 详情页 | F-044,F-045 | 明细 + 物流时间轴 |
| 地址簿 | AccountAddresses | 列表页 | F-046 | 地址 CRUD |
| 收藏夹 | AccountWishlist | 列表页 | F-047,F-048 | 收藏网格 + 浏览历史 |
| 账户设置 | AccountSettings | 设置页 | F-049 | 资料 + 改密 + 评价 |
| 灵感馆 | Inspiration | 列表页 | F-050 | 主题网格 |
| Real Weddings 列表 | RealWeddings | 列表页 | F-051 | 故事卡片 |
| Real Wedding 详情 | RealWeddingDetail | 详情页 | F-052 | 图文 + Shop the Look |
| Blog 列表 | Blog | 列表页 | F-053 | 文章卡片 |
| Blog 详情 | BlogPost | 详情页 | F-054 | 正文 + 相关 |
| 婚礼指南 | WeddingGuides | 列表页 | F-055 | 时间轴指南 + Lookbook |
| 关于/联系/FAQ | About / Contact / Faq | 内容页 | F-058 | 品牌故事 + 表单 + 手风琴 |
| 404 | NotFound | 内容页 | F-058 | 错误引导 |

> 全局组件（非独立页）：NavBar（含 Mega Menu/F-001~F-003）、Footer（F-008）、CartDrawer（F-032）、NewsletterModal（F-056）、CookieNotice（F-057）、QuickViewModal（F-015）、SizeGuideModal（F-026）。

## 消费端页面数小结

核心页面约 **26 个**（含 About/Contact/FAQ 合并视为 3 个）。Quick View / Size Guide / Cart Drawer / Newsletter 等为全局组件，不计入独立页。

## 迭代 3：登录与安全（F-059 ~ F-065 · 2026-05-31）

> Passwordless 登录方案，取代 F-041 的邮箱+密码。已有最大编号 F-058，本轮自 F-059 起。技术栈不变（Next.js App Router，`app/account/`）。

| 编号 | 功能名 | 优先级 | 关键交互 | 承载页面 |
|------|--------|--------|---------|---------|
| F-059 | Passwordless 邮箱验证码登录 | Must | 两步：输入邮箱 → 6 位验证码（自动跳格/粘贴/重发倒计时）；登录注册合并，新邮箱自动建号 | `app/account/login/page.tsx` |
| F-060 | Google 登录 | Must | Continue with Google（品牌图标）| 登录页 |
| F-061 | Apple 登录 | Must | Continue with Apple，标注 Hide My Email 私密代理邮箱仍可登录 | 登录页 |
| F-062 | 登录方式绑定管理 | Must | Email（已验证·主，不可解绑）/ Google / Apple 连接·断开；至少保留一种；主邮箱锁定 | `app/account/security/page.tsx` |
| F-063 | 登录设备与会话管理 | Must | 活跃会话列表（设备/浏览器/位置/登录方式/最近活跃）+ 当前设备标识 + 单个登出 + 一键登出其他设备 | `app/account/security/page.tsx` |
| F-064 | 设置页 passwordless 化 | Must | 移除「修改密码」；邮箱变更提示需重新验证；引导至「登录与安全」 | `app/account/settings/page.tsx` |
| F-065 | 账户中心导航增项 | Must | 侧边栏新增「Login & Security」入口 | `components/account/account-sidebar.tsx` |

### 关联与约束
- F-041「登录注册」的邮箱+密码登录被 F-059~F-061 取代（F-041 标记为被替换）。
- 身份模型：一个人多凭证；解绑保护（≥1 种）；email 已验证才作自动归并锚点；Apple relay 用 sub 主键。

### 不包含（Future）
真实 OAuth/后端、MFA、手机号短信、社交头像同步。

## 迭代 4：垂类竞品缺口补全（F-066 ~ F-077 · 2026-06-10）

> 来源：requirements-brief.md「迭代 4」章节（竞品对标：Azazie Showroom / Birdy Grey Bridal Salon / Revelry / Dessy）。已有最大编号 F-065，本轮自 F-066 起。涵盖 C1 虚拟 Showroom、C5 尺码推荐、C7 定制尺寸、C8 婚期交期。

### C1 虚拟 Showroom / 伴娘团协作

| 编号 | 功能名 | 优先级 | 关键交互 | 承载页面 |
|------|--------|--------|---------|---------|
| F-066 | [新增] Showroom 创建与管理 | Must | 创建 Showroom（命名如 "Sarah's Bridal Party" + 婚期）+ 我的 Showroom 列表 + 编辑/删除；账户中心侧边栏新增入口 | `app/showroom/page.tsx` |
| F-067 | [新增] Add to Showroom 收藏款式 | Must | PDP 与商品卡片新增「Add to Showroom」入口 → 选择目标 Showroom + 颜色后加入；已收藏态标识 | PDP `app/product/[slug]/page.tsx`、商品卡片组件 |
| F-068 | [新增] 邀请链接与访客视图 | Must | 生成/复制邀请链接；伴娘**免注册访客**通过链接浏览 Showroom 款式与颜色（输入昵称即可参与）；下单时才要求登录 | `app/showroom/[id]/page.tsx` |
| F-069 | [新增] 访客投票与留言 | Must | 每款式 喜欢/不喜欢 投票（票数聚合展示）+ 留言区（昵称 + 留言列表） | `app/showroom/[id]/page.tsx` |
| F-070 | [新增] 款式指派与提醒 | Must | 新娘为每位伴娘指派款式/颜色（成员列表 + 指派状态）+「发送提醒」按钮（前端模拟，无真实邮件/短信） | `app/showroom/page.tsx`、`app/showroom/[id]/page.tsx` |
| F-071 | [新增] 各自下单 + dye lot 提示 | Must | 伴娘从 Showroom 将被指派款式加购、各自付款；同一 Showroom 内 24h 同批下单展示「同染色批次（dye lot）保证」提示条（Showroom 视图 + 购物车/结算透出） | `app/showroom/[id]/page.tsx`、`app/cart/page.tsx`、`app/checkout/page.tsx` |

### C5 尺码推荐

| 编号 | 功能名 | 优先级 | 关键交互 | 承载页面 |
|------|--------|--------|---------|---------|
| F-072 | [新增] Find My Size 量体问卷 | Must | PDP 尺码区「Find My Size」入口 → 弹窗问卷（身高/体重/胸/腰/臀/穿着松紧偏好），分步填写 | PDP `app/product/[slug]/page.tsx`（弹窗组件） |
| F-073 | [新增] 尺码推荐结果与一键选中 | Must | 输出推荐尺码 + 置信说明（如 "94% 的相似身材买家选择 US 8"）+「Select This Size」一键选中该码回填 SKU 选择器 | PDP（弹窗组件，联动 F-025 SKU 选择） |

### C7 定制尺寸（Custom Size）

| 编号 | 功能名 | 优先级 | 关键交互 | 承载页面 |
|------|--------|--------|---------|---------|
| F-074 | [新增] Custom Size 选项与量体表单 | Must | PDP 尺码选择新增「Custom Size · Free」选项 → 展开量体表单（胸围/腰围/臀围/中空到地 hollow-to-floor/身高+鞋跟高）；**仅后台 SKU 定制尺寸开关（A-007）开启的商品显示** | PDP `app/product/[slug]/page.tsx` |
| F-075 | [新增] 定制尺寸信息透出 | Must | 购物车行项、结算订单复核、下单成功与订单详情展示定制尺寸明细（可折叠查看量体数据） | `app/cart/page.tsx`、`app/checkout/page.tsx`、订单详情/账户订单页 |

### C8 婚期驱动交期

| 编号 | 功能名 | 优先级 | 关键交互 | 承载页面 |
|------|--------|--------|---------|---------|
| F-076 | [新增] PDP 婚期交期判定 | Must | 「When is your wedding?」日期输入 → 按商品发货周期（A-007 字段）+ 物流时效判定三态：来得及（标准）/ 需加急（Rush Fee）/ 来不及（建议 ready-to-ship 替代款）；结果以提示条展示 | PDP `app/product/[slug]/page.tsx` |
| F-077 | [新增] 结算 wedding date 复核 | Must | 结算地址步骤新增 wedding date 字段（选填）→ 对购物车内商品做交期复核提示；Showroom 已含婚期时自动带入（PDP 与结算均带入） | `app/checkout/page.tsx` |

### 页面建议（迭代 4）

**新增页面（2 个）**：

| 页面 | 路径 | 类型 | 包含功能 | 关键交互 |
|------|------|------|---------|---------|
| Showroom 管理（新娘视图） | `app/showroom/page.tsx` | 列表/管理页 | F-066,F-070 | 我的 Showroom 列表 + 创建弹窗（命名+婚期）+ 成员指派概览 |
| Showroom 详情（含访客邀请视图） | `app/showroom/[id]/page.tsx` | 协作页 | F-068,F-069,F-070,F-071 | 款式网格 + 投票/留言 + 邀请链接 + 指派 + dye lot 提示条；按身份（新娘/访客）差异渲染 |

**修改页面（已有页面增强）**：

| 页面 | 路径 | 涉及功能 | 增强点 |
|------|------|---------|--------|
| 商品详情 PDP | `app/product/[slug]/page.tsx` | F-067,F-072,F-073,F-074,F-076 | Add to Showroom 入口、Find My Size 弹窗、Custom Size 表单、婚期交期判定条 |
| 商品卡片（全局组件） | 列表页/推荐位卡片 | F-067 | Add to Showroom 快捷入口 |
| 购物车 | `app/cart/page.tsx` | F-071,F-075 | dye lot 提示、定制尺寸明细 |
| 结算 | `app/checkout/page.tsx` | F-071,F-075,F-077 | wedding date 字段 + 交期复核、定制尺寸透出、dye lot 提示 |
| 账户中心侧边栏 | `components/account/account-sidebar.tsx` | F-066 | 新增「My Showroom」入口 |

### 关联与约束
- F-074（Custom Size 显示条件）与 F-076（发货周期/加急判定）依赖后台 **A-007 SKU 矩阵已有字段**：定制尺寸开关、发货周期(天数)、加急开关——两端链路打通，无需后台新增字段。
- F-073 一键选中联动 F-025 SKU 选择器；F-077 的 Showroom 婚期带入依赖 F-066 的婚期数据。
- F-071 下单仍走已有结算链路（F-035~F-039），仅增加提示与来源标识。

### 不包含（本轮排除项）
- avatar 虚拟试衣 / fit heatmap（C5 仅问卷推荐）
- Showroom 新娘统一付款 / 代付（仅各自下单各自付）
- 真实邮件/短信提醒发送（F-070 仅前端模拟）
- 后台 Showroom 团体订单管理（A6，进入 backlog）
