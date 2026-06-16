// FabricCareSection.tsx
// 商品详情页面料与护理展示区块：面料成分（按层分组）+ 护理符号 + 护理备注。
// 数据源：product.fabric_compositions / care / fabric_care_note（内联 JSON 列，无专用表）。

import { useMemo } from 'react'
import type { FabricComposition, CareItem } from '@/lib/api/store-types'

interface Props {
  fabricCompositions: FabricComposition[]
  care: CareItem[]
  fabricCareNote?: string
}

// layer 数字 → 展示名（前端分组标签）
const layerNames: Record<number, string> = {
  1: 'Shell',
  2: 'Lining',
  3: 'Overlay',
  4: 'Trim'
}

export function FabricCareSection({
  fabricCompositions,
  care,
  fabricCareNote
}: Props) {
  // 按 layer 分组（保持输入顺序）
  const compositionsByLayer = useMemo(() => {
    const groups = new Map<number, FabricComposition[]>()
    fabricCompositions?.forEach(comp => {
      if (!groups.has(comp.layer)) groups.set(comp.layer, [])
      groups.get(comp.layer)!.push(comp)
    })
    return groups
  }, [fabricCompositions])

  const hasFabric = fabricCompositions && fabricCompositions.length > 0
  const hasCare = care && care.length > 0

  // 无任何数据时整块不渲染
  if (!hasFabric && !hasCare && !fabricCareNote) {
    return null
  }

  return (
    <section className="fabric-care-section py-8 border-t border-gray-200">
      <h2 className="text-2xl font-semibold mb-4 text-gray-900">Fabric &amp; Care</h2>

      {/* 面料成分 */}
      {hasFabric && (
        <div className="fabric-composition mb-6">
          <h3 className="text-lg font-medium mb-3 text-gray-800">Composition</h3>
          {Array.from(compositionsByLayer.entries()).map(([layer, items]) => (
            <div key={layer} className="layer-group mb-4">
              <h4 className="text-base font-medium text-gray-700 mb-2">
                {layerNames[layer] || `Layer ${layer}`}
              </h4>
              <ul className="list-disc list-inside text-gray-700 space-y-1">
                {items.map((comp, idx) => (
                  <li key={idx} className="text-sm">
                    {comp.material}: {comp.percentage}%
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}

      {/* 护理标签 */}
      {hasCare && (
        <div className="care-instructions mb-6">
          <h3 className="text-lg font-medium mb-3 text-gray-800">Care Instructions</h3>
          <div className="care-symbols flex flex-wrap gap-4">
            {care.map((item, idx) => (
              <div key={idx} className="care-symbol-item flex flex-col items-center">
                <span className="symbol-icon text-4xl" aria-label={item.label}>
                  {item.symbol}
                </span>
                <span className="symbol-label text-sm text-gray-600 mt-1">
                  {item.label}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 护理备注 */}
      {fabricCareNote && (
        <div className="care-note mt-4 p-4 bg-gray-50 rounded-lg">
          <p className="text-sm text-gray-700">{fabricCareNote}</p>
        </div>
      )}
    </section>
  )
}
