import type { Metadata } from 'next'
import { Suspense } from 'react'
import { ShowroomDetailView } from '@/components/showroom/showroom-detail'

/**
 * /showroom/[id]（PAGE-SHR-S02，copy-adapt 自原型）。
 * 原型 generateStaticParams + dynamicParams=false（静态导出残留）移除 →
 * force-dynamic（决策 22 Node 运行时下协作数据强一致，不缓存）。
 * `?invite={token}` 为访客入口（邀请链接形态定稿）。
 */

export const dynamic = 'force-dynamic'

export const metadata: Metadata = {
  title: 'Showroom — Bridal Party Collaboration',
  description: 'Vote, comment, and shop your bridal party looks together.'
}

export default async function ShowroomDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  return (
    <Suspense fallback={<div className="container-luxe py-24 text-center text-ink-soft">Loading…</div>}>
      <ShowroomDetailView id={Number(id)} />
    </Suspense>
  )
}
