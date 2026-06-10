import type { Metadata } from 'next'
import { ALL_SHOWROOM_IDS } from '@/data/showrooms'
import { ShowroomDetail } from '@/components/showroom/showroom-detail'

// 静态导出：预渲染预置 Showroom + 用户自建保留 id 槽位
export function generateStaticParams() {
  return ALL_SHOWROOM_IDS.map((id) => ({ id }))
}

export const dynamicParams = false

export const metadata: Metadata = {
  title: 'Showroom — Bridal Party Collaboration',
  description: 'Vote, comment, and shop your bridal party looks together.'
}

export default async function ShowroomDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  return <ShowroomDetail id={id} />
}
