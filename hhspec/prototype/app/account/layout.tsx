'use client'

import { usePathname } from 'next/navigation'
import { AccountSidebar } from '@/components/account/account-sidebar'

export default function AccountLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()
  const isAuth = pathname === '/account/login'

  if (isAuth) return <>{children}</>

  return (
    <div className="container-luxe py-12">
      <div className="grid gap-10 lg:grid-cols-[240px_1fr]">
        <AccountSidebar />
        <div>{children}</div>
      </div>
    </div>
  )
}
