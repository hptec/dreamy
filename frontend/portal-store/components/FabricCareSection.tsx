'use client'

// FabricCareSection.tsx
// 商品详情页面料与护理展示区块：面料成分（按层分组）+ 护理符号 + 护理备注。
// 数据源：product.fabric_compositions / care / fabric_care_note（内联 JSON 列，无专用表）。
// i18n（决策 12 / FUNC-002）：标题/层级名/材质名走 fabric 命名空间字典，缺失回退原文。

import { useMemo } from 'react'
import type { FabricComposition, CareItem } from '@/lib/api/store-types'
import { useI18n } from '@/lib/i18n/i18n-context'
import type { UiMessages } from '@/lib/i18n/messages'

interface Props {
  fabricCompositions: FabricComposition[]
  care: CareItem[]
  fabricCareNote?: string
}

// layer 数字 → fabric.layers 字典 key
const layerKeyByNum: Record<number, keyof UiMessages['fabric']['layers']> = {
  1: 'shell',
  2: 'lining',
  3: 'overlay',
  4: 'trim'
}

export function FabricCareSection({
  fabricCompositions,
  care,
  fabricCareNote
}: Props) {
  const { t } = useI18n()

  // 材质名 i18n：按 material 文本小写匹配 fabric.materials 字典，无匹配回退原文（决策 12）
  const localizeMaterial = (material: string): string => {
    const key = material.trim().toLowerCase() as keyof UiMessages['fabric']['materials']
    return t.fabric.materials[key] ?? material
  }

  const localizeLayer = (layer: number): string => {
    const key = layerKeyByNum[layer]
    return key ? t.fabric.layers[key] : `Layer ${layer}`
  }

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
      <h2 className="text-2xl font-semibold mb-4 text-gray-900">{t.fabric.headingFabricCare}</h2>

      {/* 面料成分 */}
      {hasFabric && (
        <div className="fabric-composition mb-6">
          <h3 className="text-lg font-medium mb-3 text-gray-800">{t.fabric.headingComposition}</h3>
          {Array.from(compositionsByLayer.entries()).map(([layer, items]) => (
            <div key={layer} className="layer-group mb-4">
              <h4 className="text-base font-medium text-gray-700 mb-2">
                {localizeLayer(layer)}
              </h4>
              <ul className="list-disc list-inside text-gray-700 space-y-1">
                {items.map((comp, idx) => (
                  <li key={idx} className="text-sm">
                    {localizeMaterial(comp.material)}: {comp.percentage}%
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
          <h3 className="text-lg font-medium mb-3 text-gray-800">{t.fabric.headingCare}</h3>
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
