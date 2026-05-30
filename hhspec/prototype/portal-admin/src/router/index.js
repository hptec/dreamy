import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '@/composables/useAuth'

const routes = [
  { path: '/login', name: 'login', component: () => import('@/views/Login.vue'), meta: { bare: true } },
  { path: '/', name: 'dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '工作台', group: '工作台' } },

  // PIM 商品
  { path: '/products', name: 'products', component: () => import('@/views/Products.vue'), meta: { title: '商品列表', group: '商品管理' } },
  { path: '/products/new', name: 'product-new', component: () => import('@/views/ProductEdit.vue'), meta: { title: '新增商品', group: '商品管理', hidden: true } },
  { path: '/products/:id/edit', name: 'product-edit', component: () => import('@/views/ProductEdit.vue'), meta: { title: '编辑商品', group: '商品管理', hidden: true } },
  { path: '/categories', name: 'categories', component: () => import('@/views/Categories.vue'), meta: { title: '品类与主题', group: '商品管理' } },

  // OMS 订单
  { path: '/orders', name: 'orders', component: () => import('@/views/Orders.vue'), meta: { title: '订单列表', group: '订单管理' } },
  { path: '/orders/:id', name: 'order-detail', component: () => import('@/views/OrderDetail.vue'), meta: { title: '订单详情', group: '订单管理', hidden: true } },
  { path: '/refunds', name: 'refunds', component: () => import('@/views/Refunds.vue'), meta: { title: '退款工单', group: '订单管理' } },

  // 用户
  { path: '/customers', name: 'customers', component: () => import('@/views/Customers.vue'), meta: { title: '用户列表', group: '用户管理' } },
  { path: '/customers/:id', name: 'customer-detail', component: () => import('@/views/CustomerDetail.vue'), meta: { title: '用户详情', group: '用户管理', hidden: true } },

  // 站点装修（CMS 配置层 —— 差异化亮点）
  { path: '/site/home', name: 'home-builder', component: () => import('@/views/HomeBuilder.vue'), meta: { title: '首页装修', group: '站点装修' } },
  { path: '/site/navigation', name: 'navigation-config', component: () => import('@/views/NavigationConfig.vue'), meta: { title: '导航与页脚', group: '站点装修' } },
  { path: '/site/banners', name: 'banners', component: () => import('@/views/Banners.vue'), meta: { title: 'Banner 管理', group: '站点装修' } },

  // 营销
  { path: '/marketing/promotions', name: 'promotions', component: () => import('@/views/Promotions.vue'), meta: { title: '优惠券与促销', group: '营销活动' } },
  { path: '/marketing/email', name: 'email', component: () => import('@/views/EmailMarketing.vue'), meta: { title: '邮件营销', group: '营销活动' } },

  // 内容 CMS
  { path: '/content/blog', name: 'content-blog', component: () => import('@/views/ContentBlog.vue'), meta: { title: 'Blog 文章', group: '内容管理' } },
  { path: '/content/weddings', name: 'content-weddings', component: () => import('@/views/ContentWeddings.vue'), meta: { title: 'Real Weddings', group: '内容管理' } },
  { path: '/content/lookbook', name: 'content-lookbook', component: () => import('@/views/ContentLookbook.vue'), meta: { title: 'Lookbook 与指南', group: '内容管理' } },

  // 数据
  { path: '/analytics', name: 'analytics', component: () => import('@/views/Analytics.vue'), meta: { title: '数据看板', group: '数据分析' } },

  // 发布 + 系统
  { path: '/publish', name: 'publish', component: () => import('@/views/Publish.vue'), meta: { title: '发布中心', group: '发布与系统' } },
  { path: '/shipping', name: 'shipping', component: () => import('@/views/Shipping.vue'), meta: { title: '物流配置', group: '发布与系统' } },
  { path: '/settings', name: 'settings', component: () => import('@/views/Settings.vue'), meta: { title: '系统设置', group: '发布与系统' } },

  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

// 鉴权守卫：未登录访问需鉴权页 -> /login；已登录访问 /login -> 工作台
router.beforeEach((to) => {
  const { isAuthenticated } = useAuth()
  if (to.meta?.bare) {
    if (to.name === 'login' && isAuthenticated.value) return { path: '/' }
    return true
  }
  if (!isAuthenticated.value) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
