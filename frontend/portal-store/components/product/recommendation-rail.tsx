/**
 * RecommendationRail（COMP-CAT-S06，RSC）：首页/PDP 推荐位区块（E-CAT-03）。
 * 空 items 整段不渲染（冷启动安全）。
 */

import { fetchRecommendations } from '@/lib/api/catalog-server'
import type { RecommendationBlock } from '@/lib/api/store-types'
import { ProductCard } from '@/components/product/product-card'
import { SectionHeading, TextLink } from '@/components/ui/primitives'

export async function RecommendationRail({
  block,
  eyebrow,
  title,
  productId,
  tagId,
  limit = 4,
  href,
  hrefLabel,
  tone = 'plain'
}: {
  block: RecommendationBlock
  eyebrow: string
  title: string
  productId?: number
  tagId?: number
  limit?: number
  href?: string
  hrefLabel?: string
  tone?: 'plain' | 'muted'
}) {
  const items = await fetchRecommendations(block, { productId, tagId, limit })
  if (items.length === 0) return null

  const inner = (
    <>
      <div className="flex items-end justify-between">
        <SectionHeading align="left" eyebrow={eyebrow} title={title} />
        {href && hrefLabel && <TextLink href={href} className="hidden sm:inline-flex">{hrefLabel}</TextLink>}
      </div>
      <div className="mt-10 grid grid-cols-2 gap-x-5 gap-y-10 sm:gap-x-6 lg:grid-cols-4">
        {items.slice(0, limit).map((p) => <ProductCard key={p.id} product={p} />)}
      </div>
    </>
  )

  if (tone === 'muted') {
    return (
      <section className="bg-muted py-16 lg:py-24">
        <div className="container-luxe">{inner}</div>
      </section>
    )
  }
  return <section className="container-luxe py-16 lg:py-24">{inner}</section>
}
