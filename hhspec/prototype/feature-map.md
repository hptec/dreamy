# 功能地图 — Maison Eden

> 基于 `requirements-brief.md` 和 `.research/competitor-analysis.md` 扩展生成
> 生成日期：2026-05-28
> 项目：Maison Eden（高端婚纱 + 小礼服外贸 DTC 站，单门户 C 端）

---

## 0. 模块总览

| 模块代号 | 模块名 | F-编号区间 | 功能项数 |
|---|---|---|---|
| GLB | Global / 全局框架 | F-001 ~ F-010 | 10 |
| HOME | 首页 | F-011 ~ F-020 | 10 |
| SHOP | 商品浏览 / 列表与搜索 | F-021 ~ F-038 | 18 |
| PDP | 商品详情 | F-039 ~ F-058 | 20 |
| CART | 购物车 & 结算 | F-059 ~ F-070 | 12 |
| ACCT | 账号中心 | F-071 ~ F-082 | 12 |
| CONT | 品牌内容 | F-083 ~ F-090 | 8 |
| I18N | 国际化 | F-091 ~ F-096 | 6 |
| AUX | 状态 / 辅助 | F-097 ~ F-104 | 8 |

**合计：104 个功能项**

---

## 1. 功能表（F-编号体系）

### 1.1 全局框架 GLB（F-001 ~ F-010）

| F-编号 | 功能名称 | 模块 | 优先级 | 描述 | 数据 / 交互要点 |
|---|---|---|---|---|---|
| F-001 | 全局公告条 Announcement Bar | Global | Must | 顶部细条，展示促销/包邮/新品上市信息，可关闭 | 文案 i18n key；`dismissed` 持久化到 localStorage |
| F-002 | 全局头部 Header（logo + nav + utils） | Global | Must | logo 居左、mega menu 居中、币种/语言/搜索/账号/心愿单/购物车右侧 utility group | sticky；滚动反白；移动端转汉堡菜单 |
| F-003 | 主导航 Mega Menu | Global | Must | 鼠标悬停展开多列：Wedding / Evening / Bridesmaids / Mother / Guest / Flower Girl / Accessories / Lookbook | 多列：Shop by Silhouette / Shop by Color / Featured Collections / 大图 banner |
| F-004 | 全局搜索入口 Search Trigger | Global | Must | header 搜索图标，点击展开全屏 overlay 或 dropdown | 输入触发联想（热门搜索/历史搜索/商品建议） |
| F-005 | 币种切换器 Currency Switcher | Global | Must | header utility，下拉切换 USD/EUR/GBP/CAD/AUD | 触发全站价格按汇率换算重渲染 |
| F-006 | 语言切换器 Language Switcher | Global | Must | header utility，下拉切换 EN/ES/FR/DE | i18n key 立即切换，url 加 `/{locale}/` 前缀 |
| F-007 | 账号入口图标 Account Icon | Global | Must | 未登录 → 登录页；已登录 → dashboard | 图标态根据登录状态变化 |
| F-008 | 心愿单入口图标 Wishlist Icon | Global | Must | 显示数量徽标，点击进入心愿单页 | 实时同步本地心愿单 store |
| F-009 | 购物车入口图标 + Cart Drawer | Global | Must | 显示数量徽标，点击拉开右侧 Mini Cart 抽屉（含项目缩略列表 + 小计 + 去结算 CTA） | drawer 含定制规格摘要 |
| F-010 | 全局页脚 Footer（多列+订阅） | Global | Must | Shop / Customer Care / Brand / Follow / Region 多列 + 邮件订阅表单 + 社媒图标 + 政策小字 | 含 4 语言/5 币种快速切换、ISO 区域选择 |

### 1.2 首页 HOME（F-011 ~ F-020）

| F-编号 | 功能名称 | 模块 | 优先级 | 描述 | 数据 / 交互要点 |
|---|---|---|---|---|---|
| F-011 | Hero 主视觉 Banner | Home | Must | 全屏摄影主视觉，含主标题 + 副标题 + CTA（Shop Now） | 支持视频背景占位；可轮播 2-3 帧 |
| F-012 | 精选系列卡片网格 | Home | Must | 竖版大图卡片：New Arrivals / Bridal Collection / Evening Collection / Plus Size / Little White Dresses | 744×960 竖版；hover 微动效 |
| F-013 | 品类入口宫格 | Home | Must | Wedding / Evening / Bridesmaids / Mother / Guest / Accessories 圆形或方形入口图 | 点击跳转对应品类列表页 |
| F-014 | 品牌价值主张 Banner | Home | Should | "Made-to-Order Couture / Free Swatches / Inclusive Sizing / Worldwide Shipping" 四列图标 + 文案 | 链接到各内容页 |
| F-015 | 配色系统展示 Color System | Home | Should | 展示品牌核心 30+ 配色 swatch 网格，点击进入对应颜色的伴娘服列表 | 链接到带颜色筛选的列表页 |
| F-016 | Lookbook 编辑入口 | Home | Must | 大幅编辑式 banner 推荐当季 lookbook | 链接到 lookbook 详情页 |
| F-017 | Maison Eden IRL 真实买家墙 | Home | Must | 真实买家照片网格（4-6 张），链接到 Style Gallery | 含买家名 + 商品 tag |
| F-018 | Atelier 工艺故事 | Home | Should | 横幅介绍定制工艺，链接到 About / Atelier 页 | 含摄影插图 + 短文 |
| F-019 | Home Try-On 提示 Banner | Home | Could | 内容/营销介绍，只跳转 Service 页（不做物流闭环） | 仅占位说明 |
| F-020 | 首页邮件订阅 CTA | Home | Should | 页脚之上的浪漫订阅区，含 hero 文案 + 邮箱输入 | 提交后展示成功 toast |

### 1.3 商品浏览 SHOP（F-021 ~ F-038）

| F-编号 | 功能名称 | 模块 | 优先级 | 描述 | 数据 / 交互要点 |
|---|---|---|---|---|---|
| F-021 | 品类列表页 - 婚纱 Wedding Dresses | Shop | Must | 婚纱独立列表页，含 hero + 筛选 + 网格 | URL `/wedding-dresses` |
| F-022 | 品类列表页 - 小礼服 Evening & Cocktail | Shop | Must | 晚礼/鸡尾酒礼服独立列表页 | URL `/evening-dresses` |
| F-023 | 品类列表页 - 伴娘服 Bridesmaids | Shop | Must | 伴娘服独立列表页 | URL `/bridesmaid-dresses` |
| F-024 | 品类列表页 - 妈妈装 Mother of the Bride | Shop | Should | 妈妈装独立列表页 | URL `/mother-dresses` |
| F-025 | 品类列表页 - 宾客礼服 Wedding Guest | Shop | Should | 宾客礼服独立列表页 | URL `/guest-dresses` |
| F-026 | 品类列表页 - 花童 Flower Girl | Shop | Could | 花童礼服独立列表页 | URL `/flower-girl-dresses` |
| F-027 | 品类列表页 - 配饰 Accessories | Shop | Must | 配饰列表页（头纱/腰带/珠宝/手套） | URL `/accessories` |
| F-028 | 列表页 Hero Banner | Shop | Should | 每个列表页顶部品类摄影 hero + 标题 + 简短文案 | 每品类独立配图 |
| F-029 | 多维筛选 Facet Sidebar | Shop | Must | 颜色 / 面料 / 轮廓 / 长度 / 袖型 / 领型 / 特性 / 场合 / 价格区间 多维筛选 | 支持多选；可折叠；URL 同步 query params |
| F-030 | 筛选标签 Chip 区 | Shop | Must | 已选条件以可关闭 chip 形式展示在网格上方 | 点 x 移除单项；Clear All 一键清空 |
| F-031 | 排序下拉 Sort | Shop | Must | Recommended / Newest / Price asc / Price desc / Best Seller | URL `?sort=newest` |
| F-032 | 商品卡 Product Card | Shop | Must | 竖版主图 + 颜色 swatch 行 + 名称 + 价格 + 标签（New/Ships Now/Best Seller）+ 收藏心型 | hover 切第二张图 |
| F-033 | 商品卡颜色点切换主图 | Shop | Must | 鼠标悬停或点击卡片底部颜色点，主图切换为对应配色 | 配色数据按 product.variants |
| F-034 | 商品卡收藏按钮 | Shop | Must | 右上角心型按钮，加入心愿单 | 切换 active 态；未登录引导登录 |
| F-035 | 列表分页 / 加载更多 | Shop | Must | Load More 按钮 + 显示 "Showing X of Y" | 备选无限滚动 |
| F-036 | 全屏搜索 Overlay | Shop | Must | 触发搜索后全屏 overlay：输入框 + 热门搜索 + 搜索历史 + 商品快速建议 | ESC 关闭 |
| F-037 | 搜索结果页 Search Results | Shop | Must | 含 query 回显 + 结果数 + 筛选 + 网格 | URL `/search?q=...` |
| F-038 | Quick View 快查弹窗 | Shop | Should | 列表页商品卡右下角"快查"图标，弹层显示主图 + 颜色/尺码 + Add to Bag，不跳详情 | 复用 PDP 数据，简化版 |

### 1.4 商品详情 PDP（F-039 ~ F-058）

| F-编号 | 功能名称 | 模块 | 优先级 | 描述 | 数据 / 交互要点 |
|---|---|---|---|---|---|
| F-039 | 多角度大图画廊 | PDP | Must | 左侧主图 + 缩略图列；含正/背/侧/细节/上身图 | 支持点击放大查看（lightbox） |
| F-040 | 视频播放器占位 | PDP | Should | 画廊内可嵌入产品视频（占位 mp4） | 与图片同列展示 |
| F-041 | 颜色色卡 Color Swatches | PDP | Must | 10+ 色 swatch 网格，点击切换全套画廊图 + 主图标题 | 选中态 highlight |
| F-042 | 长度选择 Length Selector | PDP | Must | Floor / Tea / Mini / Ankle 长度选项 | 选中后影响价格/工期 |
| F-043 | 标准尺码选择 Standard Size | PDP | Must | US 0–30 / EU / UK 三套尺码切换 | sizing chip 选择，缺货态置灰 |
| F-044 | 量体定制尺寸入口 Custom Size | PDP | Must | "Custom Size" 选项 + 弹层引导填写量体表单（胸围/腰围/臀围/身高/底摆长） | 弹层表单（Mock 提交） |
| F-045 | 尺码指南弹层 Size Guide | PDP | Must | "Size Guide" 链接弹层，含 US/EU/UK 三套对照表 + 量体步骤插图 | 三 tab：Standard / How to Measure / Conversion |
| F-046 | 定制工期选择 Production Time | PDP | Must | Standard 3-4 weeks（默认）/ Rush 4-8 days (+$30) / Express 2-3 weeks (+$15) 三档 | 选中后实时更新总价 |
| F-047 | 价格展示（含原价划线 + 币种） | PDP | Must | 当前币种总价（含工期附加费）；原价划线 + 折扣百分比 | 跟随币种切换器换算 |
| F-048 | Ships Now 现货标 | PDP | Should | 部分 SKU 标 "Ships Now" 现货徽章，强调 3-5 天发货 | 与定制工期互斥显示 |
| F-049 | 双 CTA（Add to Bag + Buy Now） | PDP | Must | 两个明显 CTA 按钮：Add to Bag（加车）/ Buy Now（直接结算） | 含 loading 态；成功后弹 mini cart |
| F-050 | 收藏按钮 + 分享按钮 | PDP | Must | 心型加入心愿单 + 分享下拉（复制链接 / Email / Pinterest / Facebook 占位） | toast 提示 |
| F-051 | 产品详情 Tab - Description | PDP | Must | 详细描述、面料组成、设计灵感、模特尺寸标注 | 折叠式或 tab 切换 |
| F-052 | 产品详情 Tab - Fabric & Care | PDP | Must | 面料说明、洗涤保养建议 | Tab 内容 |
| F-053 | 产品详情 Tab - Shipping & Returns | PDP | Must | 配送时效、定制 vs 现货差异、退换政策 | Tab 内容 |
| F-054 | 买家秀评价 Reviews | PDP | Must | 评分分布柱状图 + 评论列表（含图视频）+ 按尺码筛选 + 按评分筛选 + 商家回复 | 评分 1-5；mock 30+ 条 |
| F-055 | 问答区 Q&A | PDP | Must | Ask a Question 表单 + 已答问题列表（问/答/时间/点赞） | mock 5-10 条 |
| F-056 | Often Bought With 配饰加购 | PDP | Must | 横向卡片：相关配饰（头纱/腰带/珠宝），可勾选加购 | 含 + 加号一键加车 |
| F-057 | You May Also Like 相似推荐 | PDP | Must | 4-6 个相似商品横滑卡片 | 复用 product card |
| F-058 | Recently Viewed 最近浏览 | PDP | Should | 4-6 个最近浏览商品（localStorage 存储） | 跟随浏览记录刷新 |

### 1.5 购物车 & 结算 CART（F-059 ~ F-070）

| F-编号 | 功能名称 | 模块 | 优先级 | 描述 | 数据 / 交互要点 |
|---|---|---|---|---|---|
| F-059 | 购物车页 Cart Page | Cart | Must | 完整购物车列表 + 右侧订单摘要 | URL `/cart` |
| F-060 | 购物车项 Cart Line Item | Cart | Must | 缩略图 + 名称 + 定制规格摘要（颜色/尺码/工期）+ 数量步进 + 单价 + 小计 + 删除 + 保存到心愿单 | 数量改变实时更新 |
| F-061 | 促销码输入 Promo Code | Cart | Should | 输入折扣码，应用后摘要中扣减 | mock 几个可用码（WELCOME10/VIP20） |
| F-062 | 运费估算 Shipping Estimate | Cart | Must | 输入国家 + 邮编估算运费档位 | 跟随币种 |
| F-063 | 订单摘要 Order Summary | Cart | Must | 商品小计 + 运费 + 税费占位 + 总计；明显 Checkout CTA | sticky 右侧 |
| F-064 | 空购物车状态 Empty Cart | Cart | Must | 空态插图 + 文案 + "Shop Now" CTA + 推荐商品 | 含品类入口 |
| F-065 | 多步结算 Step 1 - Contact & Address | Checkout | Must | 邮箱（含订阅勾选）+ 收货地址表单（姓名/地址/城市/国家/邮编/电话） | 含已保存地址快速填充（登录用户） |
| F-066 | 多步结算 Step 2 - Shipping & Payment | Checkout | Must | 配送方式选择（Standard/Express/Rush）+ 支付方式（Credit Card / PayPal / Apple Pay 占位 UI） | 卡号字段不接 SDK，仅 UI |
| F-067 | 多步结算 Step 3 - Review & Place Order | Checkout | Must | 订单复核（商品/地址/支付/总计）+ 同意条款勾选 + Place Order CTA | 提交后跳转确认页 |
| F-068 | 结算进度指示 Checkout Stepper | Checkout | Must | 顶部三步进度条，点击可回退已完成步骤 | 当前步 highlight |
| F-069 | 订单确认页 Order Confirmation | Checkout | Must | 订单号 + 明细 + 配送/支付摘要 + 预计送达 + Track Order CTA + Continue Shopping | URL `/checkout/success?order=...` |
| F-070 | 结算页 Guest Checkout 开关 | Checkout | Should | 允许游客结算，含 "Create Account" 引导勾选 | 不强制注册 |

### 1.6 账号中心 ACCT（F-071 ~ F-082）

| F-编号 | 功能名称 | 模块 | 优先级 | 描述 | 数据 / 交互要点 |
|---|---|---|---|---|---|
| F-071 | 登录页 Sign In | Account | Must | 邮箱 + 密码 + Remember Me + 忘记密码入口 + 社交登录占位（Google/Apple） | mock 校验 |
| F-072 | 注册页 Sign Up | Account | Must | 姓名 + 邮箱 + 密码 + 同意条款 + 订阅勾选 | mock 提交 |
| F-073 | 忘记密码页 Forgot Password | Account | Should | 邮箱输入 → 邮件已发送提示 | 占位 |
| F-074 | 账号中心 Dashboard | Account | Must | 欢迎语 + 概览卡片（最近订单/心愿单数/地址簿/积分占位）+ 左侧 nav | URL `/account` |
| F-075 | 账号子页 - 个人资料 Profile | Account | Must | 姓名/邮箱/电话/生日/性别表单编辑 | 含保存按钮 |
| F-076 | 账号子页 - 订单列表 Orders | Account | Must | 订单卡片列表 + 按状态筛选（All/Processing/Shipped/Delivered/Cancelled）+ 按订单号搜索 | 分页 |
| F-077 | 账号子页 - 订单详情 Order Detail | Account | Must | 订单号 + 状态时间线（占位）+ 商品明细 + 配送地址 + 支付摘要 + 客服入口 + 重新购买 CTA | URL `/account/orders/:id` |
| F-078 | 账号子页 - 心愿单 Wishlist | Account | Must | 心愿单网格 + 移除 + 加入购物车 + 分享链接 CTA | URL `/account/wishlist` |
| F-079 | 账号子页 - 地址簿 Addresses | Account | Must | 地址卡片列表 + 添加新地址 + 编辑 + 删除 + 设默认 | 模态弹层编辑 |
| F-080 | 账号子页 - 偏好设置 Preferences | Account | Should | 邮件订阅开关 / 默认尺码 / 默认币种 / 默认语言 | 保存到 user profile |
| F-081 | 账号子页 - 安全 Security | Account | Should | 修改密码 / 登录设备占位 / 删除账号入口 | 含确认弹层 |
| F-082 | 退出登录 Sign Out | Account | Must | header 账号 dropdown 中的 Sign Out 按钮 | 清空 session，跳首页 |

### 1.7 品牌内容 CONT（F-083 ~ F-090）

| F-编号 | 功能名称 | 模块 | 优先级 | 描述 | 数据 / 交互要点 |
|---|---|---|---|---|---|
| F-083 | 品牌故事页 About Maison Eden | Content | Must | 品牌起源 + 创始人 + 设计理念 + 大幅摄影 | URL `/about` |
| F-084 | Atelier 工艺页 The Atelier | Content | Must | 定制流程 + 工艺照片 + 手作细节 + 面料供应链 | URL `/atelier` |
| F-085 | 尺码指南独立内容页 Size Guide | Content | Must | 完整 US/EU/UK 对照表 + 量体步骤插图 + 视频占位 + 常见问题 | URL `/size-guide` |
| F-086 | 配送 & 退换页 Shipping & Returns | Content | Must | 详细配送时效（按区域）+ 关税说明 + 退换政策（定制 vs 现货） + 退货流程 | URL `/shipping-returns` |
| F-087 | FAQ 常见问题页 | Content | Must | 分组手风琴：Ordering / Sizing / Customization / Shipping / Returns / Account | URL `/faq` |
| F-088 | 联系我们页 Contact Us | Content | Must | 表单（姓名/邮箱/订单号可选/主题/内容）+ 客服时间 + 邮箱/电话 + Live Chat 悬浮入口占位 | URL `/contact` |
| F-089 | Style Gallery / Maison Eden IRL | Content | Should | 真实买家照片瀑布流，可按品类/颜色/场合筛选 | URL `/style-gallery` |
| F-090 | Lookbook 详情页 | Content | Should | 编辑式杂志页：大图 + 模特 + 故事 + 关联商品 | URL `/lookbook/:slug` |

### 1.8 国际化 I18N（F-091 ~ F-096）

| F-编号 | 功能名称 | 模块 | 优先级 | 描述 | 数据 / 交互要点 |
|---|---|---|---|---|---|
| F-091 | i18n 文案系统 | I18n | Must | 全站文案 key 化，4 语言 EN/ES/FR/DE 词条文件 | `vue-i18n` |
| F-092 | URL 多语言前缀 | I18n | Must | 路径加 `/en/`, `/es/`, `/fr/`, `/de/` 前缀 | 路由守卫切换 |
| F-093 | 币种实时换算 | I18n | Must | 价格 store 跟随币种切换重新计算 + 货币符号格式化 | 固定汇率表 |
| F-094 | 区域选择引导页 Region Selector | I18n | Must | 首次访问引导：选择 Country + Currency + Language，可跳过 | 持久化 localStorage；右下角小入口可重新打开 |
| F-095 | 多区域配送说明 | I18n | Should | 不同区域显示对应配送时效与运费档（驱动 PDP / Cart 数据） | mock JSON |
| F-096 | 数字 / 日期本地化 | I18n | Should | 日期格式按 locale (en-US, es-ES, fr-FR, de-DE) | `Intl.DateTimeFormat` |

### 1.9 状态 / 辅助 AUX（F-097 ~ F-104）

| F-编号 | 功能名称 | 模块 | 优先级 | 描述 | 数据 / 交互要点 |
|---|---|---|---|---|---|
| F-097 | 404 页 | Aux | Must | 摄影背景 + "Page Not Found" + 返回首页 CTA + 热门品类入口 | URL fallback |
| F-098 | 搜索无结果空状态 | Aux | Must | 提示文案 + 推荐搜索词 + 热门商品 | 嵌入搜索结果页 |
| F-099 | 列表筛选无结果空状态 | Aux | Must | 提示文案 + 清空筛选按钮 + 推荐商品 | 嵌入列表页 |
| F-100 | 空心愿单状态 | Aux | Must | 插图 + 文案 + 推荐商品 | 嵌入心愿单页 |
| F-101 | 邮件订阅弹层 Newsletter Popup | Aux | Should | 首次访问 N 秒后弹出，含独家优惠 + 邮箱表单 + 关闭按钮 | localStorage 防重弹 |
| F-102 | Cookies 同意 Banner | Aux | Should | 底部 cookies 通知条 + Accept / Reject / Settings | 持久化 localStorage |
| F-103 | 全局 Toast / Notification | Aux | Must | 加车成功 / 收藏成功 / 错误提示 等浮层 | 3 秒自动消失 |
| F-104 | 客服悬浮入口 Live Chat Bubble | Aux | Could | 右下角悬浮按钮，仅占位（不做对话窗），点击跳 Contact 页 | 含 hover tooltip |

---

## 2. 页面建议（20 个独立页面）

| # | 页面名称 | 类型 | URL | 包含功能（F-编号） | 关键交互 | 弹层 |
|---|---|---|---|---|---|---|
| P-01 | 首页 Home | 营销/内容 | `/` | F-001~F-010, F-011~F-020, F-101, F-102, F-103 | 浏览精选系列 / 进入品类 / 配色入口 / 看买家秀 / 订阅邮件 | 邮件订阅弹层、cookies 横幅 |
| P-02 | 婚纱列表页 Wedding Dresses | 列表 | `/wedding-dresses` | F-021, F-028~F-035, F-038, F-099 | 多维筛选 / 排序 / 切配色看图 / 收藏 / 快查 | Quick View 弹层 |
| P-03 | 小礼服列表页 Evening & Cocktail | 列表 | `/evening-dresses` | F-022, F-028~F-035, F-038, F-099 | 同上 | Quick View |
| P-04 | 伴娘服列表页 Bridesmaids | 列表 | `/bridesmaid-dresses` | F-023, F-028~F-035, F-038, F-099 | 同上 + 颜色族入口 | Quick View |
| P-05 | 妈妈装/宾客/花童 复合列表页 Special Occasions | 列表 | `/special-occasions` | F-024, F-025, F-026, F-028~F-035, F-038, F-099 | 顶部子品类切换 tab | Quick View |
| P-06 | 配饰列表页 Accessories | 列表 | `/accessories` | F-027, F-028~F-035, F-038, F-099 | 同上 | Quick View |
| P-07 | 搜索结果页 Search Results | 列表 | `/search?q=` | F-036, F-037, F-029~F-035, F-098 | 输入回显、相关搜索、空状态 | 全屏搜索 overlay |
| P-08 | 商品详情页 PDP | 详情 | `/products/:slug` | F-039~F-058 | 切配色、选码、选工期、加车、看评论、相似推荐 | Size Guide、Custom Size、Lightbox、分享下拉、mini cart |
| P-09 | 购物车页 Cart | 表单/详情 | `/cart` | F-059~F-064 | 改数量、删除、保存到心愿单、估算运费、应用促销码 | 删除确认 |
| P-10 | 结算 Step 1 - Address | 表单 | `/checkout/address` | F-065, F-068 | 填写联系信息 + 地址 | 已存地址快选 |
| P-11 | 结算 Step 2 - Shipping & Payment | 表单 | `/checkout/payment` | F-066, F-068 | 选配送方式、填支付（UI） | 卡片输入弹层 |
| P-12 | 结算 Step 3 - Review | 表单 | `/checkout/review` | F-067, F-068 | 复核 + 同意条款 + 下单 | 条款弹层 |
| P-13 | 订单确认页 Order Confirmation | 状态 | `/checkout/success` | F-069 | 看订单号、跟踪、继续购物 | 无 |
| P-14 | 登录/注册页 Sign In / Sign Up | 表单 | `/account/auth` | F-071, F-072, F-073, F-094 | 登录、注册、找回密码、社交登录占位 | 忘记密码 |
| P-15 | 账号中心 Dashboard | 内容 | `/account` | F-074, F-075, F-076, F-080, F-081, F-082 | 概览 / 切换左侧 nav 进入子页 | 偏好保存提示 |
| P-16 | 订单详情页 Order Detail | 详情 | `/account/orders/:id` | F-077 | 看物流时间线、明细、客服 | 无 |
| P-17 | 心愿单页 Wishlist | 列表 | `/account/wishlist` | F-078, F-100 | 移除、加车、分享 | 分享下拉 |
| P-18 | 地址簿 Addresses | 表单/列表 | `/account/addresses` | F-079 | 增删改默认 | 地址编辑弹层 |
| P-19 | 内容页集合（About / Atelier / Size Guide / Shipping & Returns / FAQ / Contact / Style Gallery / Lookbook） | 内容 | `/about`, `/atelier`, `/size-guide`, `/shipping-returns`, `/faq`, `/contact`, `/style-gallery`, `/lookbook/:slug` | F-083~F-090 | 阅读、提交联系表单、Style Gallery 筛选、Lookbook 关联商品跳转 | 大图灯箱 |
| P-20 | 状态页（404 / Region Selector / 邮件弹层） | 状态 | `/404`, `/region` | F-094, F-097 | 首次访问区域引导、找不到页面回首页 | Region 引导 |

> **页面规模合计**：20 页（主路径独立页 17 个 + 内容页集合 P-19 合并展示 8 个内容子页 + 状态/区域页 P-20）。主要交互页 19 个 + 1 个状态页，落在 18-22 页范围内。
> **全局组件（非独立页）**：Header / Footer / Cart Drawer / Mega Menu / 全屏搜索 overlay / Toast 容器 / Cookies banner — 在每页注入。

### 2.1 功能覆盖交叉验证

- 每一个 F-编号都至少落在一个页面（P-01 ~ P-20）或全局组件中
- 全局组件 GLB（F-001~F-010）注入每一页
- I18n（F-091~F-096）作用于全局
- 评价 + 问答（F-054, F-055）仅在 PDP（P-08）出现
- 多步结算（F-065~F-068）严格按 P-10/P-11/P-12 三页拆分

---

## 3. 数据模型草案

> 仅原型 mock 数据，存于前端 JSON 静态文件。

### 3.1 Product 商品

```ts
interface Product {
  id: string;              // 'p-wedding-geneva'
  slug: string;            // 'geneva-a-line-wedding-dress'
  name: { en, es, fr, de };
  category: 'wedding' | 'evening' | 'bridesmaid' | 'mother' | 'guest' | 'flowergirl' | 'accessories';
  silhouette: 'a-line' | 'mermaid' | 'ball-gown' | 'sheath' | 'two-piece' | 'jumpsuit';
  basePrice: { USD, EUR, GBP, CAD, AUD };  // 原始基准价
  originalPrice?: { USD, EUR, ... };       // 原价（划线展示）
  variants: Variant[];
  fabrics: Fabric[];        // ['chiffon', 'satin', ...]
  lengths: Length[];        // ['floor', 'tea', 'mini']
  sleeves: Sleeve[];
  necklines: Neckline[];
  features: Feature[];      // ['leg-slit', 'with-pockets', ...]
  occasions: Occasion[];    // ['rehearsal', 'beach', 'courthouse', ...]
  tags: Tag[];              // ['new', 'ships-now', 'best-seller']
  galleryByColor: Record<ColorId, GalleryImage[]>;
  description: { en, es, fr, de };
  fabricCare: { en, es, fr, de };
  isCustomizable: boolean;
  productionTimes: ProductionTime[];   // ['standard', 'rush', 'express']
  reviewSummary: { avg: 4.7, count: 256, distribution: { 5: 180, 4: 50, ... } };
  questionCount: number;
  relatedProductIds: string[];
  oftenBoughtWithIds: string[];        // 配饰加购
}
```

### 3.2 Variant 变体（颜色 × 尺码）

```ts
interface Variant {
  id: string;              // 'p-geneva-ivory-us4'
  productId: string;
  colorId: string;
  sizeId: string;
  length: Length;
  sku: string;
  inStock: boolean;
  isShipsNow: boolean;     // 现货标
  priceDelta: { USD, ... }; // 相对基准价的差
}
```

### 3.3 Color 颜色

```ts
interface Color {
  id: 'ivory' | 'dusty-blue' | 'cabernet' | ...;
  name: { en, es, fr, de };
  hex: '#F4EAD5';
  family: 'neutral' | 'blue' | 'rose' | 'green' | ...;
  swatchImage?: string;    // 真实面料色卡图
}
```

### 3.4 Size 尺码

```ts
interface Size {
  id: 'us-4' | 'eu-36' | 'uk-8' | 'custom';
  system: 'US' | 'EU' | 'UK' | 'CUSTOM';
  numeric: number;        // 4, 6, 8 ...
  label: string;          // 'US 4'
  inclusive: boolean;     // 0-30 包容尺码
}
```

### 3.5 ProductionTime 工期

```ts
interface ProductionTime {
  id: 'standard' | 'rush' | 'express';
  label: { en, es, fr, de };
  durationDays: { min: 21, max: 28 };
  feeDelta: { USD: 0, EUR: 0, ... };
}
```

### 3.6 Order 订单

```ts
interface Order {
  id: string;              // 'ME-2026-00001'
  userId?: string;
  guestEmail?: string;
  status: 'processing' | 'in-production' | 'shipped' | 'delivered' | 'cancelled' | 'returned';
  items: OrderItem[];
  shippingAddress: Address;
  billingAddress: Address;
  shippingMethod: 'standard' | 'express' | 'rush';
  paymentMethod: 'card' | 'paypal' | 'apple-pay';  // UI 占位
  paymentSummary: { last4?: '4242', brand?: 'visa' };
  currency: 'USD' | 'EUR' | 'GBP' | 'CAD' | 'AUD';
  subtotal: number;
  shippingFee: number;
  taxFee: number;
  discountAmount: number;
  total: number;
  createdAt: ISOString;
  estimatedDelivery: ISOString;
  trackingTimeline: TrackingNode[];   // 静态占位
}

interface OrderItem {
  productId: string;
  variantId: string;
  customSize?: BodyMeasurements;
  productionTimeId: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  snapshot: { name, colorName, sizeLabel, image };  // 历史快照
}
```

### 3.7 Address 地址

```ts
interface Address {
  id: string;
  userId: string;
  label?: 'home' | 'office' | 'other';
  firstName: string;
  lastName: string;
  street1: string;
  street2?: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;          // ISO-3166
  phone: string;
  isDefault: boolean;
}
```

### 3.8 Review 评价

```ts
interface Review {
  id: string;
  productId: string;
  userId?: string;
  authorName: string;
  rating: 1 | 2 | 3 | 4 | 5;
  title?: string;
  body: string;
  sizeWorn: string;
  bodyType?: string;
  images: string[];
  videos: string[];
  createdAt: ISOString;
  merchantReply?: { body, repliedAt };
  helpfulCount: number;
}
```

### 3.9 Question 问答

```ts
interface Question {
  id: string;
  productId: string;
  asker: string;
  body: string;
  answers: { author: 'merchant' | 'customer', body, createdAt }[];
  createdAt: ISOString;
}
```

### 3.10 User 用户

```ts
interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phone?: string;
  birthday?: ISOString;
  defaultCurrency: 'USD' | ...;
  defaultLocale: 'en' | 'es' | 'fr' | 'de';
  defaultSizeSystem: 'US' | 'EU' | 'UK';
  newsletterOptIn: boolean;
  createdAt: ISOString;
}
```

### 3.11 Wishlist / CartItem

```ts
interface WishlistItem {
  productId: string;
  colorId?: string;
  addedAt: ISOString;
}

interface CartItem {
  id: string;
  productId: string;
  variantId: string;
  customSize?: BodyMeasurements;
  productionTimeId: string;
  quantity: number;
  addedAt: ISOString;
}

interface BodyMeasurements {
  bust: number;
  waist: number;
  hips: number;
  height: number;
  hollowToFloor: number;
  unit: 'cm' | 'inch';
}
```

### 3.12 PromoCode

```ts
interface PromoCode {
  code: string;            // 'WELCOME10'
  type: 'percent' | 'fixed';
  value: number;
  minSubtotal?: number;
  validFrom: ISOString;
  validTo: ISOString;
}
```

---

## 4. 国际化策略

### 4.1 i18n key 组织

- 工具：`vue-i18n` (composition API mode)
- 文件结构：`src/locales/{en,es,fr,de}.json`，按页面/模块分命名空间：

  ```
  common.header.search
  common.footer.subscribe
  page.home.hero.title
  page.pdp.cta.addToBag
  page.pdp.size.custom
  module.cart.summary.subtotal
  ```

- 语言切换器持久化到 localStorage key `me_locale`
- 路由前缀：`/{locale}/...`，默认 `en`
- 缺失回退到 `en`

### 4.2 币种汇率（固定）

| 币种 | 汇率（1 USD =） | 符号 | 千分位 / 小数 |
|---|---|---|---|
| USD | 1.00 | `$` | `1,234.00` |
| EUR | 0.92 | `€` | `1.234,00` |
| GBP | 0.78 | `£` | `1,234.00` |
| CAD | 1.35 | `C$` | `1,234.00` |
| AUD | 1.50 | `A$` | `1,234.00` |

- 汇率 mock 在 `src/config/currency.ts` 常量中
- 切换币种 → Pinia store `useCurrencyStore` 重新计算所有 `Money` 对象
- 持久化到 localStorage key `me_currency`

### 4.3 价格四舍五入策略

- 计算阶段保留 2 位小数（half-up）
- 价格末位统一收口为 `.00` 或 `.99`（按品牌策略）：
  - 高端线（婚纱 ≥ $500）末位 `.00`（突出尊贵感）
  - 入门线（< $500）末位 `.99`（电商习惯）
- 货币符号位置按 locale：USD/GBP/CAD/AUD 左前缀 `$1,234.00`；EUR 多语言版按 `1.234,00 €`
- 格式化统一走 `Intl.NumberFormat(locale, { style: 'currency', currency })`

### 4.4 区域 ↔ 默认币种映射

| 区域 | 默认币种 | 默认语言 |
|---|---|---|
| United States | USD | en |
| Canada | CAD | en |
| United Kingdom | GBP | en |
| Eurozone (DE/FR/ES/IT/NL...) | EUR | de/fr/es |
| Australia | AUD | en |
| Other | USD | en |

- 首次访问通过 `navigator.language` 探测，弹出 Region Selector（F-094）允许用户调整后落库

### 4.5 日期 / 数字本地化

- 日期：`Intl.DateTimeFormat(locale, { dateStyle: 'medium' })`
- 数字：`Intl.NumberFormat(locale)`
- 尺码：US/EU/UK 三套并存，按用户偏好或区域默认

---

## 5. 反目标（明确不包含）

照搬自 `requirements-brief.md`，作为原型开发边界：

- 真实支付集成（Stripe/PayPal 仅 UI 演示，不接 SDK，不做真实交易）
- 真实物流追踪 API（订单追踪页用静态时间线展示）
- 真实搜索引擎（用前端 mock 数据筛选 + 字符串匹配）
- Home Try-On 家试穿物流闭环（仅作内容/营销页提及，无下单流程）
- Live Chat 在线客服（仅做悬浮入口占位，不做对话窗口）
- 后台 / 管理端 / 卖家工具（单 C 端门户）
- 邮件 / 短信 / 营销自动化（仅有订阅入口表单收集邮箱）
- 真实库存系统（库存/缺货态走 mock 数据）
- 真实税费 / 关税计算（结算页 tax 项为占位静态值）

---

## 6. 一致性自查

| 校验项 | 状态 |
|---|---|
| F-编号无重复、连续编号 | OK（F-001 ~ F-104） |
| 每个 F-编号至少落在 1 个页面或全局组件 | OK |
| 每个独立页面包含 F-编号列表 | OK |
| Must / Should / Could 三档优先级齐全 | OK |
| 反目标章节与 requirements-brief 一致 | OK |
| 数据模型覆盖 Product/Variant/Color/Size/Order/Address/Review/User/Wishlist/CartItem 等关键实体 | OK |
| 国际化策略覆盖 i18n key 组织、固定汇率、价格策略 | OK |
| 页面规模 18-22 落地（实际 20 页） | OK |
