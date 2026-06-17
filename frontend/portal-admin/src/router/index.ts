import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { registerUnauthorizedHandler } from '@/api/client'

// 路由 meta.permission = 该路由所需菜单权限 key（GUARD-02）。
// 非身份认证页（商品/订单/装修等）保留为占位路由，使后台骨架可运行，权限 key 与菜单对齐。
const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: () => import('@/views/Login.vue'), meta: { bare: true } },
  { path: '/403', name: 'forbidden', component: () => import('@/views/Forbidden.vue'), meta: { title: '无权限', group: '系统' } },

  // DEC-ANA-FE-1：meta.permission 由 '/' 修正为 '/dashboard'（对齐契约权限点与 identity 字典种子）
  { path: '/', name: 'dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '工作台', group: '工作台', permission: '/dashboard' } },

  // 商品（占位）
  { path: '/products', name: 'products', component: () => import('@/views/Products.vue'), meta: { title: '商品列表', group: '商品管理', permission: '/products' } },
  { path: '/products/new', name: 'product-new', component: () => import('@/views/ProductEdit.vue'), meta: { title: '新增商品', group: '商品管理', hidden: true, permission: '/products' } },
  { path: '/products/:id/edit', name: 'product-edit', component: () => import('@/views/ProductEdit.vue'), meta: { title: '编辑商品', group: '商品管理', hidden: true, permission: '/products' } },
  { path: '/categories', name: 'categories', component: () => import('@/views/Categories.vue'), meta: { title: '分类管理', group: '商品管理', permission: '/categories' } },
  // FORM-CAT-M01（ALIGN-004，决策 1）：独立属性集页废弃，内容已迁入 /categories Tab 2「属性集与字典」；
  // 保留 redirect 以兼容书签/操作日志旧链接，permission 校验随 /categories
  { path: '/attribute-sets', redirect: { path: '/categories', query: { tab: 'attributes' } } },

  // 订单（占位）
  { path: '/orders', name: 'orders', component: () => import('@/views/Orders.vue'), meta: { title: '订单列表', group: '订单管理', permission: '/orders' } },
  { path: '/orders/:id', name: 'order-detail', component: () => import('@/views/OrderDetail.vue'), meta: { title: '订单详情', group: '订单管理', hidden: true, permission: '/orders' } },
  { path: '/refunds', name: 'refunds', component: () => import('@/views/Refunds.vue'), meta: { title: '退款工单', group: '订单管理', permission: '/refunds' } },

  // 用户身份运营（PAGE-A02/A03，本变更核心）
  { path: '/customers', name: 'customers', component: () => import('@/views/Customers.vue'), meta: { title: '用户列表', group: '用户管理', permission: '/customers' } },
  { path: '/customers/:id', name: 'customer-detail', component: () => import('@/views/CustomerDetail.vue'), meta: { title: '用户详情', group: '用户管理', hidden: true, permission: '/customers' } },

  // 站点装修（占位）
  { path: '/site/home', name: 'home-builder', component: () => import('@/views/HomeBuilder.vue'), meta: { title: '首页装修', group: '站点装修', permission: '/site/home' } },
  { path: '/site/navigation', name: 'navigation-config', component: () => import('@/views/NavigationConfig.vue'), meta: { title: '导航与页脚', group: '站点装修', permission: '/site/navigation' } },
  // PAGE-MKT-A02：Banner 路由对齐契约权限点 /banners（旧 /site/banners 重定向兼容）
  { path: '/banners', name: 'banners', component: () => import('@/views/Banners.vue'), meta: { title: 'Banner 管理', group: '站点装修', permission: '/banners' } },
  { path: '/site/banners', redirect: '/banners' },

  // 营销（占位）
  // PAGE-MKT-A01：促销路由对齐契约权限点 /promotions（旧 /marketing/promotions 重定向兼容）
  { path: '/promotions', name: 'promotions', component: () => import('@/views/Promotions.vue'), meta: { title: '优惠券与促销', group: '营销活动', permission: '/promotions' } },
  { path: '/marketing/promotions', redirect: '/promotions' },
  { path: '/marketing/email', name: 'email', component: () => import('@/views/EmailMarketing.vue'), meta: { title: '邮件营销', group: '营销活动', permission: '/marketing/email' } },

  // 内容（占位）
  { path: '/content/blog', name: 'content-blog', component: () => import('@/views/ContentBlog.vue'), meta: { title: 'Blog 文章', group: '内容管理', permission: '/content/blog' } },
  { path: '/content/weddings', name: 'content-weddings', component: () => import('@/views/ContentWeddings.vue'), meta: { title: 'Real Weddings', group: '内容管理', permission: '/content/weddings' } },
  { path: '/content/lookbook', name: 'content-lookbook', component: () => import('@/views/ContentLookbook.vue'), meta: { title: 'Lookbook 与指南', group: '内容管理', permission: '/content/lookbook' } },

  // PAGE-REV-A01：评价与 Q&A（本变更新增权限点 /reviews）
  { path: '/reviews', name: 'reviews', component: () => import('@/views/Reviews.vue'), meta: { title: '评价与 Q&A', group: '内容管理', permission: '/reviews' } },

  // 数据（占位）
  { path: '/analytics', name: 'analytics', component: () => import('@/views/Analytics.vue'), meta: { title: '数据看板', group: '数据分析', permission: '/analytics' } },

  // 发布（占位）
  { path: '/publish', name: 'publish', component: () => import('@/views/Publish.vue'), meta: { title: '发布中心', group: '发布与系统', permission: '/publish' } },
  { path: '/shipping', name: 'shipping', component: () => import('@/views/Shipping.vue'), meta: { title: '物流配置', group: '发布与系统', permission: '/shipping' } },

  // 系统管理（PAGE-A04~A07，本变更核心）
  { path: '/system/admins', name: 'system-admins', component: () => import('@/views/AdminList.vue'), meta: { title: '管理员管理', group: '系统管理', permission: '/system/admins' } },
  { path: '/system/roles', name: 'system-roles', component: () => import('@/views/RoleManagement.vue'), meta: { title: '角色权限', group: '系统管理', permission: '/system/roles' } },
  { path: '/system/auth', name: 'system-auth', component: () => import('@/views/AuthSettings.vue'), meta: { title: '登录与认证', group: '系统管理', permission: '/system/auth' } },
  { path: '/system/logs', name: 'system-logs', component: () => import('@/views/OperationLogs.vue'), meta: { title: '操作日志', group: '系统管理', permission: '/system/logs' } },

  // i18n-complete-with-ai-assist：外部网关配置（AI 翻译代理仍依赖此网关）
  { path: '/system/gateways', name: 'system-gateways', component: () => import('@/views/system/GatewayConfigList.vue'), meta: { title: '外部网关配置', group: '系统管理', permission: '/system/gateways' } },

  // PAGE-TRD-A04：汇率与结算配置（权限点 /settings，trading 域种子）
  { path: '/settings', name: 'settings', component: () => import('@/views/Settings.vue'), meta: { title: '汇率与结算配置', group: '发布与系统', permission: '/settings' } },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

// GUARD-01~04：全局守卫
// 注意：在守卫内部 import store，避免模块加载期 Pinia 未就绪
router.beforeEach(async (to) => {
  const { useAuthStore } = await import('@/stores/auth')
  const auth = useAuthStore()

  // 公开页（登录）
  if (to.meta?.bare) {
    if (to.name === 'login' && auth.isAuthenticated) return { path: '/' }
    return true
  }

  // GUARD-01：无 token → /login
  if (!auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  // GUARD-01：有 token → 确保已 fetchMe（缓存 permissionKeys）
  if (!auth.loaded) {
    try {
      await auth.fetchMe()
    } catch {
      // token 失效：清理并跳登录（401 拦截器已清 token）
      auth.reset()
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  } else {
    // 已登录：每次导航实时刷新权限，角色权限被改后跳转/刷新即生效（无需重登）
    try {
      await auth.refreshPermissions()
    } catch {
      // 刷新失败不阻断导航（401 由拦截器统一处理跳登录）
    }
  }

  // 403 页无需权限校验
  if (to.name === 'forbidden') return true

  // GUARD-02/04：权限 key 校验（超管短路放行）
  const permission = to.meta?.permission as string | undefined
  if (!auth.hasPermission(permission)) {
    return { name: 'forbidden' }
  }

  return true
})

// 注册 401 处理：API 客户端遇 401 时跳登录
registerUnauthorizedHandler(() => {
  const redirect = router.currentRoute.value.fullPath
  if (router.currentRoute.value.name !== 'login') {
    router.replace({ name: 'login', query: { redirect } })
  }
})

export default router
