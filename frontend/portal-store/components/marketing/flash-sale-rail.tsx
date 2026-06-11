'use client'

/**
 * FlashSaleRail 客户端区块（COMP-MKT-S03 新增组件，token 同源）：
 * SectionHeading + 倒计时徽章（useCountdown，STORE-MKT-S05）+ ProductCard 横轨。
 * 到期本地隐藏（后端 SCHED 下线 + 60s 缓存兜底间隙的前端补偿）。
 */

import { useEffect, useState } from 'react'
import { Timer } from 'lucide-react'
import type { StoreFlashSale } from '@/lib/api/store-types'
import { ProductCard, productRefToCard } from '@/components/product/product-card'
import { SectionHeading } from '@/components/ui/primitives'

function useCountdown(endAt: string): string | null {
  const [label, setLabel] = useState<string | null>('')
  useEffect(() => {
    const tick = () => {
      const remaining = new Date(endAt).getTime() - Date.now()
      if (Number.isNaN(remaining) || remaining <= 0) {
        setLabel(null)
        return
      }
      const h = Math.floor(remaining / 3_600_000)
      const m = Math.floor((remaining % 3_600_000) / 60_000)
      const s = Math.floor((remaining % 60_000) / 1000)
      setLabel(`${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`)
    }
    tick()
    const t = setInterval(tick, 1000)
    return () => clearInterval(t)
  }, [endAt])
  return label
}

function FlashSaleSection({ sale }: { sale: StoreFlashSale }) {
  const countdown = useCountdown(sale.endAt)
  // 到期本地隐藏区块（STORE-MKT-S05 前端补偿）
  if (countdown === null) return null
  const products = sale.products ?? []
  if (products.length === 0) return null
  return (
    <section className="container-luxe py-16 lg:py-24">
      <div className="flex items-end justify-between">
        <SectionHeading align="left" eyebrow={`Flash Sale · ${sale.discount}`} title={sale.name} />
        <span className="hidden items-center gap-2 rounded-sm bg-ink px-4 py-2 text-sm font-medium tracking-wide text-canvas sm:flex">
          <Timer className="h-4 w-4 text-gold-light" /> Ends in {countdown}
        </span>
      </div>
      <div className="mt-10 grid grid-cols-2 gap-x-5 gap-y-10 sm:gap-x-6 lg:grid-cols-4">
        {products.slice(0, 4).map((p) => <ProductCard key={p.id} product={productRefToCard(p)} />)}
      </div>
    </section>
  )
}

export function FlashSaleRail({ sales }: { sales: StoreFlashSale[] }) {
  if (sales.length === 0) return null
  return (
    <>
      {sales.map((s) => <FlashSaleSection key={s.id} sale={s} />)}
    </>
  )
}
