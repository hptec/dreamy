# L2 前端详设 - site_builder 域

> 来源：linked_prototype_snapshots（4 个原型页面）+ site-builder-api.openapi.yml + api-detail.md
> 模式：复制 + 适配（原型已是可运行 Vue3 SFC）
> 技术栈：Vue3 + Pinia + Vue Router + Tailwind CSS + Headless UI

## 复制策略

linked_prototype_snapshots 非空，且原型为 Vue SFC（proto_is_frontend=true），L3 implementer 采用「复制 + 适配」模式：
- 直接复制原型 .vue 文件到 frontend/portal-admin/src/views/
- 适配后端 API（替换 mock 数据为真实 API 调用）
- 保留全部 UI 结构/CSS/文案

## 组件树设计（COMP-NNN）

### HomeBuilder 页面

```
HomeBuilderView (COMP-001)
├── PageHeader (COMP-002, 复用 baseline)
│   ├── H1Title "Home Builder"
│   └── SaveButton (COMP-003)
├── SectionList (COMP-004)
│   └── SectionItem (COMP-005) × N
│       ├── SectionDragHandle (COMP-006)
│       ├── SectionToggle (COMP-007, Headless UI Switch)
│       ├── SectionEditButton (COMP-008)
│       ├── SortUpButton (COMP-009)
│       ├── SortDownButton (COMP-010)
│       └── SectionPreview (COMP-011)
└── SectionEditDrawer (COMP-012, Headless UI Dialog)
    ├── SectionTypeSelect (COMP-013)
    ├── DataJsonEditor (COMP-014, 按 section_type 动态渲染)
    │   ├── HeroDataEditor (COMP-015, 只读 Banner 派生数据)
    │   ├── ThemeCardsDataEditor (COMP-016, count 字段)
    │   ├── ProductRailDataEditor (COMP-017, source + product_ids + limit)
    │   ├── EditorialFeatureDataEditor (COMP-018, limit + wedding_ids)
    │   ├── NewsletterDataEditor (COMP-019, 仅 i18n 文案)
    │   └── CustomDataEditor (COMP-020, 自定义 JSON)
    ├── I18nJsonEditor (COMP-021)
    │   ├── LocaleTabs (COMP-022, EN/ES/FR)
    │   └── LocaleForm (COMP-023) × 3
    └── SaveButton (COMP-003, 复用)
```

### NavigationConfig 页面

```
NavigationConfigView (COMP-024)
├── PageHeader (COMP-002, 复用)
├── NavigationTabs (COMP-025, Headless UI Tab)
│   ├── TabButton "Navigation" (COMP-026)
│   ├── TabButton "Footer" (COMP-027)
│   └── TabButton "Announcement" (COMP-028)
├── NavigationTabPanel (COMP-029)
│   ├── NavItemsTree (COMP-030, 树形组件)
│   │   └── NavItemRow (COMP-031) × N
│   │       ├── NavItemDragHandle (COMP-032)
│   │       ├── NavItemToggle (COMP-007, 复用)
│   │       ├── NavItemEditButton (COMP-033)
│   │       └── NavItemPreview (COMP-034)
│   └── NavItemEditDrawer (COMP-035)
│       ├── LinkTypeSelect (COMP-036, [custom, taxonomy])
│       ├── CustomHrefInput (COMP-037, link_type=custom 时显示)
│       ├── TaxonomyPicker (COMP-038, link_type=taxonomy 时显示)
│       ├── MegaMenuEditor (COMP-039)
│       │   ├── MegaMenuColumn (COMP-040) × N
│       │   │   ├── ColumnTitleInput (COMP-041)
│       │   │   └── MegaMenuLinks (COMP-042)
│       │   │       └── MegaMenuLinkRow (COMP-043) × N
│       │   └── AddColumnButton (COMP-044)
│       ├── I18nJsonEditor (COMP-021, 复用)
│       └── SaveButton (COMP-003, 复用)
├── FooterTabPanel (COMP-045)
│   ├── FooterColumnsList (COMP-046)
│   │   └── FooterColumnRow (COMP-047) × N
│   │       ├── ColumnTitleInput (COMP-041, 复用)
│   │       ├── FooterLinksList (COMP-048)
│   │       │   └── FooterLinkRow (COMP-049) × N
│   │       └── AddLinkButton (COMP-050)
│   └── AddColumnButton (COMP-044, 复用)
└── AnnouncementTabPanel (COMP-051)
    ├── AnnouncementsList (COMP-052)
    │   └── AnnouncementRow (COMP-053) × N
    │       ├── AnnouncementToggle (COMP-007, 复用)
    │       ├── AnnouncementEditButton (COMP-054)
    │       └── AnnouncementPreview (COMP-055)
    ├── AnnouncementEditDrawer (COMP-056)
    │   ├── PriorityInput (COMP-057)
    │   ├── TimeWindowPicker (COMP-058, start_at + end_at)
    │   ├── ContentI18nEditor (COMP-021, 复用)
    │   └── SaveButton (COMP-003, 复用)
    └── CreateAnnouncementButton (COMP-058)
```

### Banners 页面（本变更仅扩展 KD-14 字段）

```
BannersView (COMP-059, 复用 baseline)
└── BannerEditDrawer (COMP-060, 复用 + 扩展)
    ├── CtaTextSecondaryInput (COMP-061, 新增, KD-14)
    └── CtaLinkSecondaryInput (COMP-062, 新增, KD-14)
```

### Publish 页面（KD-1 不改造，复用 baseline）

```
PublishView (COMP-063, 复用 baseline)
├── InvalidateButton (COMP-064, 复用)
└── InvalidateLog (COMP-065, 复用)
```

### 消费端组件（portal-store）

```
# portal-store/app/[locale]/layout.tsx
StoreLayout (COMP-066)
├── StoreHeader (COMP-067, 改造为动态读取导航)
│   ├── MainNav (COMP-068, 从 GET /api/store/content/navigation 派生)
│   │   └── NavItem (COMP-069)
│   │       └── MegaMenu (COMP-070, 从 navigation_item.mega_menu_json 派生)
│   └── AnnouncementBar (COMP-071, 从 GET /api/store/content/announcements 派生)
└── StoreFooter (COMP-072, 改造为动态读取)
    └── FooterColumn (COMP-073) × N
        └── FooterLink (COMP-074) × N

# portal-store/app/[locale]/page.tsx
HomePage (COMP-075, 改造为动态渲染)
└── HomeSection (COMP-076) × N
    ├── HomeHeroCarousel (COMP-077, 从 data.banners[] 渲染多 Banner 轮播)
    ├── ThemeCardsSection (COMP-078, 从 taxonomy type=theme 派生)
    ├── ProductRailSection (COMP-079, 从 product 派生)
    ├── EditorialFeatureSection (COMP-080, 从 weddings 派生)
    ├── NewsletterSection (COMP-081, 内嵌订阅表单)
    └── CustomSection (COMP-082, 按 data_json 渲染)
```

## 状态管理（STORE-NNN, Pinia）

### portal-admin

| ID | Store | state | actions | 用途 |
|----|-------|-------|---------|------|
| STORE-001 | useHomeSectionStore | sections: HomePageSectionDto[], loading: boolean, error: string | fetchSections, createSection, updateSection, deleteSection, toggleSection, sortSections | HomeBuilder 页面状态 |
| STORE-002 | useNavigationStore | items: NavigationItemDto[], loading, error | fetchNavigation, saveNavigation | NavigationConfig 导航 Tab 状态 |
| STORE-003 | useFooterStore | columns: FooterColumnDto[], loading, error | fetchFooter, saveFooter | NavigationConfig 页脚 Tab 状态 |
| STORE-004 | useAnnouncementStore | announcements: AnnouncementDto[], loading, error, pagination | fetchAnnouncements, createAnnouncement, updateAnnouncement, deleteAnnouncement, toggleAnnouncement | NavigationConfig 公告 Tab 状态 |
| STORE-005 | useBannerStore（扩展基线） | banners: BannerDto[], loading, error | fetchBanners, createBanner, updateBanner, deleteBanner | Banners 页面状态（基线已有，扩展 cta_*_secondary 字段） |

### portal-store（消费端）

| ID | Store | state | actions | 用途 |
|----|-------|-------|---------|------|
| STORE-006 | useStoreContentStore | home: StoreHomePageDto, navigation: StoreNavigationDto, footer: StoreFooterDto, announcements: StoreAnnouncementDto[], loading, error | fetchHome, fetchNavigation, fetchFooter, fetchAnnouncements | 消费端站点内容（首页/header/footer/公告） |
| STORE-007 | useNewsletterStore | subscribing: boolean, error | subscribe(email, locale) | Newsletter 订阅 |

**Pinia 持久化**: 消费端 useStoreContentStore 可选持久化到 localStorage（locale 切换时缓存），admin 端不持久化（实时读 DB）。

## 页面路由（PAGE-NNN）

### portal-admin

| ID | 路由 | 组件 | 鉴权 | 权限 |
|----|------|------|------|------|
| PAGE-001 | /admin/site/home-builder | HomeBuilderView | AdminBearerAuth | /site/home |
| PAGE-002 | /admin/site/navigation-config | NavigationConfigView | AdminBearerAuth | /site/navigation |
| PAGE-003 | /admin/marketing/banners | BannersView（基线扩展） | AdminBearerAuth | /banners |
| PAGE-004 | /admin/publish | PublishView（基线复用） | AdminBearerAuth | /publish |

### portal-store

| ID | 路由 | 组件 | 鉴权 |
|----|------|------|------|
| PAGE-005 | / | HomePage | 匿名 |
| PAGE-006 | /es | HomePage (locale=es) | 匿名 |
| PAGE-007 | /fr | HomePage (locale=fr) | 匿名 |
| PAGE-008 | /[locale]/* | StoreLayout 包裹的任意页面 | 匿名 |

## 表单交互（FORM-NNN）

### FORM-001: HomeSection 创建/编辑表单

| 字段 | 控件 | 校验 | 错误提示 |
|------|------|------|---------|
| section_type | Select | 必填，枚举 | "请选择区块类型" |
| enabled | Switch | 必填 | — |
| sort_order | NumberInput | >= 0 | "排序必须 >= 0" |
| data_json | 动态表单（按 section_type） | js_guard | "data_json 结构不合法" |
| i18n_json | I18nJsonEditor | locale 键合法 | "i18n_json locale 键非法" |

**提交**: POST/PUT /api/admin/site-builder/home-sections[/{id}]
**成功**: toast "Saved"，刷新列表
**失败**: toast 显示错误 message，表单保留

### FORM-002: Navigation 保存表单

整体保存语义，无独立表单。点击 SaveButton 提交整个 navigation_items 数组。

**提交**: PUT /api/admin/site-builder/navigation
**成功**: toast "Saved"
**失败**: toast 显示错误

### FORM-003: Footer 保存表单

同 Navigation，整体保存。

**提交**: PUT /api/admin/site-builder/footer

### FORM-004: Announcement 创建/编辑表单

| 字段 | 控件 | 校验 |
|------|------|------|
| enabled | Switch | 必填 |
| priority | NumberInput | >= 0 |
| start_at | DateTimePicker | 可选 |
| end_at | DateTimePicker | > start_at |
| content_i18n_json | I18nJsonEditor | 必填，3 locale |
| i18n_json | I18nJsonEditor | 可选 |

**提交**: POST/PUT /api/admin/site-builder/announcements[/{id}]

### FORM-005: Newsletter 订阅表单（消费端）

| 字段 | 控件 | 校验 |
|------|------|------|
| email | EmailInput | email 格式 |
| locale | Hidden（从 URL 提取） | — |

**提交**: POST /api/store/newsletter body={email, source:HOME_BLOCK, locale}
**成功**: toast "Subscribed"
**失败**: toast 显示错误

### FORM-006: Banner 编辑表单（扩展 KD-14）

基线表单 + 新增字段：
| 字段 | 控件 |
|------|------|
| cta_text_secondary | TextInput |
| cta_link_secondary | TextInput |

**提交**: PUT /api/admin/banners/{id}（基线端点，扩展字段）

## 错误展示策略

| 场景 | 展示方式 |
|------|---------|
| 表单校验失败 | 字段下方红色文字 |
| API 4xx 错误 | toast.error(message) |
| API 5xx 错误 | toast.error("服务异常，请稍后重试") + Sentry 上报 |
| 网络错误 | toast.error("网络异常") |
| 加载中 | Skeleton / Spinner |
| 空数据 | EmptyState 组件 |

## 消费端 SSR/CSR 策略

| 页面 | 渲染模式 | 缓存 |
|------|---------|------|
| 首页 / | force-dynamic（KD-1） | 后端内容缓存；Hero 以 `data.banners[]` 轮播，扁平首张字段仅作滚动部署兼容 |
| 任意页面 header/footer | force-dynamic | 后端缓存，消费端 layout.tsx 调用 API |
| 公告条 | force-dynamic | 后端缓存 |

**Next.js 配置**:
```ts
// portal-store/app/[locale]/page.tsx
export const dynamic = 'force-dynamic';

// portal-store/app/[locale]/layout.tsx
export const dynamic = 'force-dynamic';
```

## 复制清单（L3 implementer 参考）

从原型复制到实际工程的文件：

| 原型文件 | 目标文件 | 适配说明 |
|---------|---------|---------|
| prototype/portal-admin/src/views/HomeBuilder.vue | frontend/portal-admin/src/views/site/HomeBuilder.vue | 替换 mock 为 useHomeSectionStore API 调用 |
| prototype/portal-admin/src/views/NavigationConfig.vue | frontend/portal-admin/src/views/site/NavigationConfig.vue | 替换 mock 为 useNavigationStore + useFooterStore + useAnnouncementStore |
| prototype/portal-admin/src/views/Banners.vue | frontend/portal-admin/src/views/marketing/Banners.vue | 扩展 cta_*_secondary 字段（KD-14） |
| prototype/portal-admin/src/views/Publish.vue | frontend/portal-admin/src/views/publish/Publish.vue | 不改造，直接复用 |

消费端改造文件（无原型，按设计实现）：
- frontend/portal-store/app/[locale]/page.tsx — 改造为动态渲染区块
- frontend/portal-store/app/[locale]/layout.tsx — 改造为读取导航 + 公告 + 页脚
- frontend/portal-store/components/site/StoreHeader.tsx — 新建/改造
- frontend/portal-store/components/site/StoreFooter.tsx — 新建/改造
- frontend/portal-store/components/site/AnnouncementBar.tsx — 新建
- frontend/portal-store/components/site/HomeSection.tsx — 新建（按 section_type 分发）
- frontend/portal-store/stores/useStoreContentStore.ts — 新建
- frontend/portal-store/stores/useNewsletterStore.ts — 新建

## L1 SHOULD_FIX 消化

| SHOULD_FIX | 消化位置 | 说明 |
|-----------|---------|------|
| 路径映射 | PAGE-001/002 路由使用 /admin/site/* 新路径 | 前端路由与后端 API 路径一致 |
| 权限点 | router.meta.permissions = ['/site/home'], ['/site/navigation'] | 路由守卫校验权限点 |
| 跨域缓存失效 | 消费端 force-dynamic + 后端两级缓存 | 前端不缓存，依赖后端缓存失效链 |
