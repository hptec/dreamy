import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '@/composables/useAuth'
import { roles } from '@/data/mock'

const routes = [
  { path: '/login', name: 'login', component: () => import('@/views/Login.vue'), meta: { bare: true } },
  { path: '/', name: 'dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '工作台', group: '工作台' } },

  // PIM 商品
  { path: '/products', name: 'products', component: () => import('@/views/Products.vue'), meta: { title: '商品列表', group: '商品管理' } },
  { path: '/products/new', name: 'product-new', component: () => import('@/views/ProductEdit.vue'), meta: { title: '新增商品', group: '商品管理', hidden: true, permKey: '/products' } },
  { path: '/products/:id/edit', name: 'product-edit', component: () => import('@/views/ProductEdit.vue'), meta: { title: '编辑商品', group: '商品管理', hidden: true, permKey: '/products' } },
  { path: '/categories', name: 'categories', component: () => import('@/views/Categories.vue'), meta: { title: '分类管理', group: '商品管理' } },
  { path: '/attributes', redirect: '/categories' },

  // OMS 订单
  { path: '/orders', name: 'orders', component: () => import('@/views/Orders.vue'), meta: { title: '订单列表', group: '订单管理' } },
  { path: '/orders/:id', name: 'order-detail', component: () => import('@/views/OrderDetail.vue'), meta: { title: '订单详情', group: '订单管理', hidden: true, permKey: '/orders' } },
  { path: '/refunds', name: 'refunds', component: () => import('@/views/Refunds.vue'), meta: { title: '退款工单', group: '订单管理' } },

  // 用户
  { path: '/customers', name: 'customers', component: () => import('@/views/Customers.vue'), meta: { title: '用户列表', group: '用户管理' } },
  { path: '/customers/:id', name: 'customer-detail', component: () => import('@/views/CustomerDetail.vue'), meta: { title: '用户详情', group: '用户管理', hidden: true, permKey: '/customers' } },

  // 站点装修
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
  { path: '/reviews', name: 'reviews', component: () => import('@/views/Reviews.vue'), meta: { title: '评价与 Q&A', group: '内容管理' } },

  // 数据
  { path: '/analytics', name: 'analytics', component: () => import('@/views/Analytics.vue'), meta: { title: '数据看板', group: '数据分析' } },

  // 发布
  { path: '/publish', name: 'publish', component: () => import('@/views/Publish.vue'), meta: { title: '发布中心', group: '发布与系统' } },
  { path: '/shipping', name: 'shipping', component: () => import('@/views/Shipping.vue'), meta: { title: '物流配置', group: '发布与系统' } },

  // 系统管理（迭代 2 新增）
  { path: '/system/admins', name: 'system-admins', component: () => import('@/views/AdminList.vue'), meta: { title: '管理员管理', group: '系统管理' } },
  { path: '/system/roles', name: 'system-roles', component: () => import('@/views/RoleManagement.vue'), meta: { title: '角色权限', group: '系统管理' } },
  { path: '/system/auth', name: 'system-auth', component: () => import('@/views/AuthSettings.vue'), meta: { title: '登录与认证', group: '系统管理' } },
  { path: '/system/logs', name: 'system-logs', component: () => import('@/views/OperationLogs.vue'), meta: { title: '操作日志', group: '系统管理' } },

  // 废弃路由重定向
  { path: '/settings', redirect: '/system/admins' },

  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

function getUserPermissions() {
  const { user } = useAuth()
  if (!user.value) return []
  // 超管拥有全部权限
  if (user.value.role === '超级管理员') {
    const r = roles.find(r => r.id === 'r-super')
    return r ? r.permissions : []
  }
  const role = roles.find(r => r.name === user.value.role)
  return role ? role.permissions : []
}

// 鉴权守卫：未登录 → /login；无权限 → 重定向到 / 并 toast
router.beforeEach((to) => {
  const { isAuthenticated } = useAuth()

  // 白名单路由直接放行
  if (to.meta?.bare) {
    if (to.name === 'login' && isAuthenticated.value) return { path: '/' }
    return true
  }

  if (!isAuthenticated.value) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  // 权限校验：动态/子路由通过 meta.permKey 继承父级菜单权限
  const permissions = getUserPermissions()
  const permPath = to.meta?.permKey || to.path
  if (permissions.length > 0 && !permissions.includes(permPath)) {
    return { path: '/' }
  }

  return true
})

export default router
