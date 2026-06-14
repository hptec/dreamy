# ISO 3758 护理符号清单

> 国际纺织品护理标签标准
> 生成时间：2026-06-14T08:12:00Z
> 用途：fabric-care-module 护理标签选择器数据源

## 符号分类

ISO 3758 定义了五大护理分类，每个分类使用不同的基础形状：
- **洗涤（Washing）** — 水盆形状
- **漂白（Bleaching）** — 三角形
- **干燥（Drying）** — 正方形
- **熨烫（Ironing）** — 熨斗形状
- **专业护理（Professional Care）** — 圆形

## 符号清单

### 1. 洗涤（Washing）

| 符号ID | 符号代码 | 图标文件 | 英文说明 | 中文说明 |
|--------|----------|----------|----------|----------|
| washing_machine_30 | W-30 | washing-30.svg | Machine wash cold (30°C/85°F) | 机洗冷水，最高30°C |
| washing_machine_40 | W-40 | washing-40.svg | Machine wash warm (40°C/105°F) | 机洗温水，最高40°C |
| washing_machine_50 | W-50 | washing-50.svg | Machine wash hot (50°C/120°F) | 机洗热水，最高50°C |
| washing_machine_60 | W-60 | washing-60.svg | Machine wash very hot (60°C/140°F) | 机洗高温，最高60°C |
| washing_hand | W-HAND | washing-hand.svg | Hand wash only | 只可手洗 |
| washing_gentle_30 | W-30-G | washing-30-gentle.svg | Machine wash cold, gentle cycle | 机洗冷水，柔和程序 |
| washing_gentle_40 | W-40-G | washing-40-gentle.svg | Machine wash warm, gentle cycle | 机洗温水，柔和程序 |
| washing_no | W-NO | washing-no.svg | Do not wash | 不可水洗 |

### 2. 漂白（Bleaching）

| 符号ID | 符号代码 | 图标文件 | 英文说明 | 中文说明 |
|--------|----------|----------|----------|----------|
| bleach_any | B-ANY | bleach-any.svg | Bleach allowed | 可漂白 |
| bleach_non_chlorine | B-NON-CL | bleach-non-chlorine.svg | Non-chlorine bleach only | 只可非氯漂白 |
| bleach_no | B-NO | bleach-no.svg | Do not bleach | 不可漂白 |

### 3. 干燥（Drying）

| 符号ID | 符号代码 | 图标文件 | 英文说 | 中文说明 |
|--------|----------|----------|----------|----------|
| tumble_dry_low | D-LOW | tumble-dry-low.svg | Tumble dry low heat | 滚筒烘干，低温 |
| tumble_dry_medium | D-MED | tumble-dry-medium.svg | Tumble dry medium heat | 滚筒烘干，中温 |
| tumble_dry_high | D-HIGH | tumble-dry-high.svg | Tumble dry high heat | 滚筒烘干，高温 |
| tumble_dry_no | D-NO | tumble-dry-no.svg | Do not tumble dry | 不可滚筒烘干 |
| tumble_dry_gentle | D-GENTLE | tumble-dry-gentle.svg | Tumble dry, gentle cycle | 滚筒烘干，柔和程序 |
| line_dry | D-LINE | line-dry.svg | Line dry / Hang to dry | 晾干 |
| drip_dry | D-DRIP | drip-dry.svg | Drip dry | 滴干 |
| dry_flat | D-FLAT | dry-flat.svg | Dry flat | 平铺晾干 |
| dry_shade | D-SHADE | dry-shade.svg | Dry in shade | 阴干 |

### 4. 熨烫（Ironing）

| 符号ID | 符号代码 | 图标文件 | 英文说明 | 中文说明 |
|--------|----------|----------|----------|----------|
| iron_low | I-LOW | iron-low.svg | Iron low (110°C/230°F) | 低温熨烫（一个点） |
| iron_medium | I-MED | iron-medium.svg | Iron medium (150°C/300°F) | 中温熨烫（两个点） |
| iron_high | I-HIGH | iron-high.svg | Iron high (200°C/390°F) | 高温熨烫（三个点） |
| iron_no_steam | I-NO-STEAM | iron-no-steam.svg | Iron without steam | 熨烫时不可加蒸汽 |
| iron_no | I-NO | iron-no.svg | Do not iron | 不可熨烫 |

### 5. 专业护理（Professional Care）

| 符号ID | 符号代码 | 图标文件 | 英文说明 | 中文说明 |
|--------|----------|----------|----------|----------|
| dry_clean_any | P-ANY | dry-clean-any.svg | Dry clean, any solvent | 可干洗，任何溶剂 |
| dry_clean_petroleum | P-F | dry-clean-petroleum.svg | Dry clean, petroleum solvent only | 可干洗，仅石油溶剂 |
| dry_clean_gentle | P-GENTLE | dry-clean-gentle.svg | Dry clean, gentle process | 可干洗，柔和程序 |
| dry_clean_short_cycle | P-SHORT | dry-clean-short-cycle.svg | Dry clean, short cycle | 可干洗，短周期 |
| dry_clean_low_moisture | P-LOW-M | dry-clean-low-moisture.svg | Dry clean, low moisture | 可干洗，低湿度 |
| dry_clean_no | P-NO | dry-clean-no.svg | Do not dry clean | 不可干洗 |
| wet_clean | W-PROF | wet-clean.svg | Professional wet cleaning | 专业湿洗 |
| wet_clean_gentle | W-PROF-G | wet-clean-gentle.svg | Professional wet cleaning, gentle | 专业湿洗，柔和 |
| wet_clean_no | W-PROF-NO | wet-clean-no.svg | Do not wet clean | 不可湿洗 |

## 数据结构定义

### TypeScript 类型定义

```typescript
// /src/types/care-symbols.ts

export type CareCategory = 
  | 'washing' 
  | 'bleaching' 
  | 'drying' 
  | 'ironing' 
  | 'professional';

export interface CareSymbol {
  id: string;
  code: string;
  category: CareCategory;
  icon: string;
  description: {
    en: string;
    zh: string;
  };
  sortOrder: number;
}

export interface CareSymbolsByCategory {
  washing: CareSymbol[];
  bleaching: CareSymbol[];
  drying: CareSymbol[];
  ironing: CareSymbol[];
  professional: CareSymbol[];
}
```

### 常量数据

```typescript
// /src/constants/care-symbols.ts

export const CARE_SYMBOLS: CareSymbol[] = [
  // Washing
  {
    id: 'washing_machine_30',
    code: 'W-30',
    category: 'washing',
    icon: '/care-symbols/washing-30.svg',
    description: {
      en: 'Machine wash cold (30°C/85°F)',
      zh: '机洗冷水，最高30°C'
    },
    sortOrder: 1
  },
  {
    id: 'washing_machine_40',
    code: 'W-40',
    category: 'washing',
    icon: '/care-symbols/washing-40.svg',
    description: {
      en: 'Machine wash warm (40°C/105°F)',
      zh: '机洗温水，最高40°C'
    },
    sortOrder: 2
  },
  // ... 其余符号省略，完整列表见上表
];

export const CARE_CATEGORIES = [
  { id: 'washing', name: { en: 'Washing', zh: '洗涤' } },
  { id: 'bleaching', name: { en: 'Bleaching', zh: '漂白' } },
  { id: 'drying', name: { en: 'Drying', zh: '干燥' } },
  { id: 'ironing', name: { en: 'Ironing', zh: '熨烫' } },
  { id: 'professional', name: { en: 'Professional Care', zh: '专业护理' } }
] as const;
```

## 符号资源获取

### 推荐 SVG 图标库

1. **The Noun Project**（免费/付费）
   - https://thenounproject.com/
   - 搜索关键词："laundry symbols"、"care label"、"washing instructions"
   - 许可：Creative Commons（需署名）或 Pro 订阅（无署名）

2. **Flaticon**（免费/付费）
   - https://www.flaticon.com/
   - 搜索："textile care"、"ISO 3758"
   - 许可：免费版需署名，Premium 无限制

3. **Font Awesome**（部分免费）
   - https://fontawesome.com/
   - 图标较少，可能需要自行组合或绘制

4. **自行绘制**
   - 使用 Figma/Sketch/Illustrator 按 ISO 3758 标准绘制
   - SVG 格式，单色（黑色），40x40px 画布
   - 保持线条粗细一致（2px stroke）

### SVG 文件规范

- **尺寸**：40x40px 或 48x48px
- **格式**：SVG（优化后，移除不必要的元数据）
- **颜色**：单色黑色（#000000），或使用 `currentColor` 支持主题切换
- **命名**：小写字母 + 连字符，如 `washing-30.svg`
- **存放路径**：`/public/care-symbols/*.svg`

### SVG 优化工具

```bash
# 使用 SVGO 优化 SVG 文件
npm install -g svgo
svgo --multipass --folder=./public/care-symbols
```

## 使用示例

### 前端选择器组件

```vue
<template>
  <div class="care-symbol-selector">
    <div v-for="category in CARE_CATEGORIES" :key="category.id">
      <h3>{{ category.name.zh }}</h3>
      <div class="symbol-grid">
        <div
          v-for="symbol in getSymbolsByCategory(category.id)"
          :key="symbol.id"
          :class="{ selected: isSelected(symbol.id) }"
          @click="toggleSymbol(symbol.id)"
        >
          <img :src="symbol.icon" :alt="symbol.description.zh" />
          <span>{{ symbol.description.zh }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { CARE_SYMBOLS, CARE_CATEGORIES } from '@/constants/care-symbols';

const selectedSymbols = ref<string[]>([]);

const getSymbolsByCategory = (category: string) => {
  return CARE_SYMBOLS.filter(s => s.category === category)
    .sort((a, b) => a.sortOrder - b.sortOrder);
};

const isSelected = (id: string) => selectedSymbols.value.includes(id);

const toggleSymbol = (id: string) => {
  const index = selectedSymbols.value.indexOf(id);
  if (index > -1) {
    selectedSymbols.value.splice(index, 1);
  } else {
    selectedSymbols.value.push(id);
  }
};
</script>
```

## 国际化考虑

- 所有描述文本支持中英文双语
- 符号本身是国际通用的图形语言，无需翻译
- 可扩展到其他语言（日语、韩语、西班牙语等）
- 前端根据用户语言偏好显示对应文本

## 参考资料

- ISO 3758:2012 标准文档（官方）
- GINETEX 国际纺织品护理标签协会
- 美国 FTC 护理标签规则（16 CFR Part 423）
- 欧盟纺织品标签指令（EU 1007/2011）
