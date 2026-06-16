// COMP-FC-04: FabricCareSection.tsx
// 功能: 商品详情页的面料与护理标签展示区块，显示面料成分列表 + 护理符号 + 护理备注
// 证据锚点: catalog-fabric-care-frontend-detail.md § 2.1 / acceptance.yml s-060~s-064

import { useMemo } from 'react'
import type { FabricComposition, CareInstruction } from '@/lib/api/store-types'

// [L2-COMP-FC-04] Props 定义
interface Props {
  fabricCompositions: FabricComposition[]
  careInstructions: CareInstruction[]
  fabricCareNote?: string
}

// 枚举映射（消费端英文）
const layerNames: Record<number, string> = {
  1: 'Shell',
  2: 'Lining',
  3: 'Overlay',
  4: 'Trim'
}

const materialNames: Record<number, string> = {
  1: 'Cotton',
  2: 'Polyester',
  3: 'Lace',
  4: 'Satin',
  5: 'Chiffon',
  6: 'Tulle',
  7: 'Silk',
  8: 'Organza',
  9: 'Spandex',
  10: 'Nylon'
}

export function FabricCareSection({
  fabricCompositions,
  careInstructions,
  fabricCareNote
}: Props) {
  // [L2-COMP-FC-04] 按 layer 分组
  // 证据锚点: acceptance.yml s-060
  const compositionsByLayer = useMemo(() => {
    const groups = new Map<number, FabricComposition[]>()
    fabricCompositions?.forEach(comp => {
      if (!groups.has(comp.layer)) groups.set(comp.layer, [])
      groups.get(comp.layer)!.push(comp)
    })
    // 同层按 sortOrder 排序
    groups.forEach(items => items.sort((a, b) => a.sortOrder - b.sortOrder))
    return groups
  }, [fabricCompositions])

  // [L2-COMP-FC-04] 无数据时整个区块不渲染
  // 证据锚点: acceptance.yml s-066 / B-FC-014
  if ((!fabricCompositions || fabricCompositions.length === 0) && (!careInstructions || careInstructions.length === 0)) {
    return null
  }

  return (
    <section className="fabric-care-section py-8 border-t border-gray-200">
      <h2 className="text-2xl font-semibold mb-4 text-gray-900">Fabric & Care</h2>

      {/* 面料成分 */}
      {fabricCompositions && fabricCompositions.length > 0 && (
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
                    {materialNames[comp.material] || `Material ${comp.material}`}: {comp.percentage}%
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}

      {/* 护理标签 */}
      {careInstructions && careInstructions.length > 0 && (
        <div className="care-instructions mb-6">
          <h3 className="text-lg font-medium mb-3 text-gray-800">Care Instructions</h3>
          <div className="care-symbols flex flex-wrap gap-4">
            {careInstructions.map(instr => (
              <div key={instr.id} className="care-symbol-item flex flex-col items-center">
                <span className="symbol-icon text-4xl" aria-label={instr.label}>
                  {instr.symbolUnicode}
                </span>
                <span className="symbol-label text-sm text-gray-600 mt-1">
                  {instr.label}
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
