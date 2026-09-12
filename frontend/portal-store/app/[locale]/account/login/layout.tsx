/** 登录页 client 组件无法声明 dynamic；以路由段 layout 强制请求时渲染，保证 header/footer 导航与全站一致（不走 build 期静态回退）。 */
export const dynamic = 'force-dynamic'

export default function LoginLayout({ children }: { children: React.ReactNode }) {
  return children
}
