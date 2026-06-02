'use client'

import { usePathname } from 'next/navigation'
import { AccountSidebar } from '@/components/account/account-sidebar'
import { AuthGuard } from '@/components/account/auth-guard'

export default function AccountLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()
  const isAuth = pathname === '/account/login'

  // 登录页不套守卫/侧栏
  if (isAuth) return <>{children}</>

  // 其余账户页：守卫（未登录跳登录）+ 侧栏布局
  return (
    <AuthGuard>
      <div className="container-luxe py-12">
        <div className="grid gap-10 lg:grid-cols-[240px_1fr]">
          <AccountSidebar />
          <div>{children}</div>
        </div>
      </div>
    </AuthGuard>
  )
}
