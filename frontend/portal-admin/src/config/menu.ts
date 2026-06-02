// 左侧导航分组定义（菜单 key = 权限 key，与后端 permission 字典 22 项一一对应）
// 约束: GUARD-03 菜单按 permissionKeys 过滤；菜单 key 对齐 code-structure / openapi permission.key
import {
  Squares2X2Icon,
  ShoppingBagIcon,
  ClipboardDocumentListIcon,
  UsersIcon,
  SwatchIcon,
  MegaphoneIcon,
  DocumentTextIcon,
  ChartBarIcon,
  RocketLaunchIcon,
  ShieldCheckIcon,
} from '@heroicons/vue/24/outline'
import type { FunctionalComponent } from 'vue'

export interface MenuItem {
  title: string
  to: string
}
export interface MenuGroup {
  label: string
  icon: FunctionalComponent
  badge?: string
  items: MenuItem[]
}

export const menuGroups: MenuGroup[] = [
  {
    label: '工作台',
    icon: Squares2X2Icon,
    items: [{ title: '工作台', to: '/' }],
  },
  {
    label: '站点装修',
    icon: SwatchIcon,
    badge: 'CMS',
    items: [
      { title: '首页装修', to: '/site/home' },
      { title: '导航与页脚', to: '/site/navigation' },
      { title: 'Banner 管理', to: '/site/banners' },
    ],
  },
  {
    label: '商品管理',
    icon: ShoppingBagIcon,
    items: [
      { title: '商品列表', to: '/products' },
      { title: '品类与主题', to: '/categories' },
    ],
  },
  {
    label: '订单管理',
    icon: ClipboardDocumentListIcon,
    items: [
      { title: '订单列表', to: '/orders' },
      { title: '退款工单', to: '/refunds' },
    ],
  },
  {
    label: '用户管理',
    icon: UsersIcon,
    items: [{ title: '用户列表', to: '/customers' }],
  },
  {
    label: '营销活动',
    icon: MegaphoneIcon,
    items: [
      { title: '优惠券与促销', to: '/marketing/promotions' },
      { title: '邮件营销', to: '/marketing/email' },
    ],
  },
  {
    label: '内容管理',
    icon: DocumentTextIcon,
    items: [
      { title: 'Blog 文章', to: '/content/blog' },
      { title: 'Real Weddings', to: '/content/weddings' },
      { title: 'Lookbook 与指南', to: '/content/lookbook' },
    ],
  },
  {
    label: '数据分析',
    icon: ChartBarIcon,
    items: [{ title: '数据看板', to: '/analytics' }],
  },
  {
    label: '发布与系统',
    icon: RocketLaunchIcon,
    items: [
      { title: '发布中心', to: '/publish' },
      { title: '物流配置', to: '/shipping' },
    ],
  },
  {
    label: '系统管理',
    icon: ShieldCheckIcon,
    items: [
      { title: '管理员管理', to: '/system/admins' },
      { title: '角色权限', to: '/system/roles' },
      { title: '登录与认证', to: '/system/auth' },
      { title: '操作日志', to: '/system/logs' },
    ],
  },
]
