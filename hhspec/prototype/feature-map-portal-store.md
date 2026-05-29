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
