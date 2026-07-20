import type { Metadata } from 'next'
import { Suspense } from 'react'
import { UnsubscribeConfirm } from './unsubscribe-confirm'

export const metadata: Metadata = {
  title: 'Unsubscribe',
  robots: { index: false, follow: false }
}

export default function UnsubscribePage() {
  return (
    <section className="container-luxe flex min-h-[60vh] items-center justify-center py-20">
      {/* React 19 自动 hoist 到 head：退订链接含 bearer token，禁止 referrer 外泄 */}
      <meta name="referrer" content="no-referrer" />
      <Suspense>
        <UnsubscribeConfirm />
      </Suspense>
    </section>
  )
}
