# 面料材料枚举清单

> 变更名称：fabric-care-module
> 生成时间：2026-06-14T08:16:00Z
> 用途：面料成分编辑器的材料选项数据源

## 材料分类

婚纱礼服常用面料材料，按类型分为：
- **天然纤维** — 来自植物或动物的天然材料
- **合成纤维** — 化学合成的人造纤维
- **半合成纤维** — 天然原料经化学处理制成
- **弹性纤维** — 提供弹性和延展性的材料

## 材料清单

### 天然纤维（Natural Fibers）

| 枚举值 | 英文名称 | 中文名称 | 特性 | 常见用途 |
|--------|----------|----------|------|----------|
| SILK | Silk | 真丝 | 光泽柔滑、透气、昂贵 | 高端婚纱内衬、缎面礼服 |
| COTTON | Cotton | 棉 | 透气吸湿、柔软舒适 | 休闲礼服、衬里 |
| LINEN | Linen | 亚麻 | 清爽透气、易皱 | 夏季户外婚礼礼服 |
| WOOL | Wool | 羊毛 | 保暖、有弹性、易缩水 | 冬季礼服、外套 |

### 合成纤维（Synthetic Fibers）

| 枚举值 | 英文名称 | 中文名称 | 特性 | 常见用途 |
|--------|----------|----------|------|----------|
| POLYESTER | Polyester | 涤纶 | 耐用、抗皱、价格适中 | 婚纱外层、礼服主体 |
| NYLON | Nylon | 尼龙 | 强度高、耐磨、弹性好 | 内衬、蕾丝底布 |
| ACRYLIC | Acrylic | 腈纶 | 类似羊毛、保暖、轻便 | 针织礼服、披肩 |

### 半合成纤维（Semi-Synthetic Fibers）

| 枚举值 | 英文名称 | 中文名称 | 特性 | 常见用途 |
|--------|----------|----------|------|----------|
| RAYON | Rayon | 人造丝（粘胶） | 光滑柔软、类似真丝 | 礼服主体、装饰 |
| VISCOSE | Viscose | 黏胶纤维 | 透气、吸湿、易起皱 | 夏季礼服、内衬 |
| MODAL | Modal | 莫代尔 | 柔软、吸湿、不易变形 | 舒适型礼服 |

### 弹性纤维（Elastic Fibers）

| 枚举值 | 英文名称 | 中文名称 | 特性 | 常见用途 |
|--------|----------|----------|------|----------|
| ELASTANE | Elastane (Spandex) | 氨纶（莱卡） | 高弹性、恢复性好 | 修身礼服、弹力面料 |

### 特殊面料（Specialty Fabrics）

| 枚举值 | 英文名称 | 中文名称 | 特性 | 常见用途 |
|--------|----------|----------|------|----------|
| LACE | Lace | 蕾丝 | 镂空、精致、透明 | 婚纱叠层、装饰 |
| TULLE | Tulle | 薄纱 | 轻薄、透气、蓬松 | 婚纱裙摆、面纱 |
| CHIFFON | Chiffon | 雪纺 | 轻盈飘逸、半透明 | 伴娘礼服、叠层 |
| SATIN | Satin | 缎面 | 光滑有光泽、厚重 | 高端婚纱、礼服主体 |
| ORGANZA | Organza | 欧根纱 | 透明挺括、有光泽 | 婚纱外层、蝴蝶结 |
| TAFFETA | Taffeta | 塔夫绸 | 挺括有光泽、沙沙声 | 复古风格婚纱 |
| CREPE | Crepe | 绉绸 | 质地细腻、轻微皱纹 | 简约风格礼服 |
| VELVET | Velvet | 天鹅绒 | 柔软厚重、绒面 | 冬季礼服、复古风格 |
| BROCADE | Brocade | 锦缎 | 提花织物、华丽 | 传统婚纱、晚礼服 |

## Java 枚举类定义

```java
package com.dreamy.domain.product.enums;

/**
 * 面料材料枚举
 * 用于面料成分编辑器的材料选项
 */
public enum FabricMaterial {
    
    // 天然纤维
    SILK("Silk", "真丝", FabricCategory.NATURAL),
    COTTON("Cotton", "棉", FabricCategory.NATURAL),
    LINEN("Linen", "亚麻", FabricCategory.NATURAL),
    WOOL("Wool", "羊毛", FabricCategory.NATURAL),
    
    // 合成纤维
    POLYESTER("Polyester", "涤纶", FabricCategory.SYNTHETIC),
    NYLON("Nylon", "尼龙", FabricCategory.SYNTHETIC),
    ACRYLIC("Acrylic", "腈纶", FabricCategory.SYNTHETIC),
    
    // 半合成纤维
    RAYON("Rayon", "人造丝", FabricCategory.SEMI_SYNTHETIC),
    VISCOSE("Viscose", "黏胶纤维", FabricCategory.SEMI_SYNTHETIC),
    MODAL("Modal", "莫代尔", FabricCategory.SEMI_SYNTHETIC),
    
    // 弹性纤维
    ELASTANE("Elastane", "氨纶", FabricCategory.ELASTIC),
    
    // 特殊面料
    LACE("Lace", "蕾丝", FabricCategory.SPECIALTY),
    TULLE("Tulle", "薄纱", FabricCategory.SPECIALTY),
    CHIFFON("Chiffon", "雪纺", FabricCategory.SPECIALTY),
    SATIN("Satin", "缎面", FabricCategory.SPECIALTY),
    ORGANZA("Organza", "欧根纱", FabricCategory.SPECIALTY),
    TAFFETA("Taffeta", "塔夫绸", FabricCategory.SPECIALTY),
    CREPE("Crepe", "绉绸", FabricCategory.SPECIALTY),
    VELVET("Velvet", "天鹅绒", FabricCategory.SPECIALTY),
    BROCADE("Brocade", "锦缎", FabricCategory.SPECIALTY);
    
    private final String nameEn;
    private final String nameZh;
    private final FabricCategory category;
    
    FabricMaterial(String nameEn, String nameZh, FabricCategory category) {
        this.nameEn = nameEn;
        this.nameZh = nameZh;
        this.category = category;
    }
    
    public String getNameEn() {
        return nameEn;
    }
    
    public String getNameZh() {
        return nameZh;
    }
    
    public FabricCategory getCategory() {
        return category;
    }
    
    /**
     * 获取本地化名称
     * @param locale 语言环境（en/zh）
     * @return 本地化名称
     */
    public String getLocalizedName(String locale) {
        return "zh".equalsIgnoreCase(locale) ? nameZh : nameEn;
    }
    
    /**
     * 面料分类枚举
     */
    public enum FabricCategory {
        NATURAL("Natural Fibers", "天然纤维"),
        SYNTHETIC("Synthetic Fibers", "合成纤维"),
        SEMI_SYNTHETIC("Semi-Synthetic Fibers", "半合成纤维"),
        ELASTIC("Elastic Fibers", "弹性纤维"),
        SPECIALTY("Specialty Fabrics", "特殊面料");
        
        private final String nameEn;
        private final String nameZh;
        
        FabricCategory(String nameEn, String nameZh) {
            this.nameEn = nameEn;
            this.nameZh = nameZh;
        }
        
        public String getNameEn() {
            return nameEn;
        }
        
        public String getNameZh() {
            return nameZh;
        }
    }
}
```

## 面料层级枚举类定义

```java
package com.dreamy.domain.product.enums;

/**
 * 面料层级枚举
 * 多层面料的层级类型
 */
public enum FabricLayerType {
    
    SHELL("Shell", "外层", "婚纱或礼服的最外层面料"),
    LINING("Lining", "内衬", "贴身穿着的内层面料"),
    OVERLAY("Overlay", "叠层", "覆盖在外层之上的装饰层"),
    TRIM("Trim", "装饰", "边缘、袖口、领口等装饰部分");
    
    private final String nameEn;
    private final String nameZh;
    private final String description;
    
    FabricLayerType(String nameEn, String nameZh, String description) {
        this.nameEn = nameEn;
        this.nameZh = nameZh;
        this.description = description;
    }
    
    public String getNameEn() {
        return nameEn;
    }
    
    public String getNameZh() {
        return nameZh;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 获取本地化名称
     * @param locale 语言环境（en/zh）
     * @return 本地化名称
     */
    public String getLocalizedName(String locale) {
        return "zh".equalsIgnoreCase(locale) ? nameZh : nameEn;
    }
}
```

## TypeScript 类型定义

```typescript
// /src/types/fabric-materials.ts

export enum FabricCategory {
  NATURAL = 'natural',
  SYNTHETIC = 'synthetic',
  SEMI_SYNTHETIC = 'semi_synthetic',
  ELASTIC = 'elastic',
  SPECIALTY = 'specialty'
}

export enum FabricMaterial {
  // Natural
  SILK = 'SILK',
  COTTON = 'COTTON',
  LINEN = 'LINEN',
  WOOL = 'WOOL',
  
  // Synthetic
  POLYESTER = 'POLYESTER',
  NYLON = 'NYLON',
  ACRYLIC = 'ACRYLIC',
  
  // Semi-Synthetic
  RAYON = 'RAYON',
  VISCOSE = 'VISCOSE',
  MODAL = 'MODAL',
  
  // Elastic
  ELASTANE = 'ELASTANE',
  
  // Specialty
  LACE = 'LACE',
  TULLE = 'TULLE',
  CHIFFON = 'CHIFFON',
  SATIN = 'SATIN',
  ORGANZA = 'ORGANZA',
  TAFFETA = 'TAFFETA',
  CREPE = 'CREPE',
  VELVET = 'VELVET',
  BROCADE = 'BROCADE'
}

export interface FabricMaterialOption {
  value: FabricMaterial;
  label: {
    en: string;
    zh: string;
  };
  category: FabricCategory;
}

// 材料选项常量数据
export const FABRIC_MATERIALS: FabricMaterialOption[] = [
  { value: FabricMaterial.SILK, label: { en: 'Silk', zh: '真丝' }, category: FabricCategory.NATURAL },
  { value: FabricMaterial.COTTON, label: { en: 'Cotton', zh: '棉' }, category: FabricCategory.NATURAL },
  { value: FabricMaterial.LINEN, label: { en: 'Linen', zh: '亚麻' }, category: FabricCategory.NATURAL },
  { value: FabricMaterial.WOOL, label: { en: 'Wool', zh: '羊毛' }, category: FabricCategory.NATURAL },
  
  { value: FabricMaterial.POLYESTER, label: { en: 'Polyester', zh: '涤纶' }, category: FabricCategory.SYNTHETIC },
  { value: FabricMaterial.NYLON, label: { en: 'Nylon', zh: '尼龙' }, category: FabricCategory.SYNTHETIC },
  { value: FabricMaterial.ACRYLIC, label: { en: 'Acrylic', zh: '腈纶' }, category: FabricCategory.SYNTHETIC },
  
  { value: FabricMaterial.RAYON, label: { en: 'Rayon', zh: '人造丝' }, category: FabricCategory.SEMI_SYNTHETIC },
  { value: FabricMaterial.VISCOSE, label: { en: 'Viscose', zh: '黏胶纤维' }, category: FabricCategory.SEMI_SYNTHETIC },
  { value: FabricMaterial.MODAL, label: { en: 'Modal', zh: '莫代尔' }, category: FabricCategory.SEMI_SYNTHETIC },
  
  { value: FabricMaterial.ELASTANE, label: { en: 'Elastane', zh: '氨纶' }, category: FabricCategory.ELASTIC },
  
  { value: FabricMaterial.LACE, label: { en: 'Lace', zh: '蕾丝' }, category: FabricCategory.SPECIALTY },
  { value: FabricMaterial.TULLE, label: { en: 'Tulle', zh: '薄纱' }, category: FabricCategory.SPECIALTY },
  { value: FabricMaterial.CHIFFON, label: { en: 'Chiffon', zh: '雪纺' }, category: FabricCategory.SPECIALTY },
  { value: FabricMaterial.SATIN, label: { en: 'Satin', zh: '缎面' }, category: FabricCategory.SPECIALTY },
  { value: FabricMaterial.ORGANZA, label: { en: 'Organza', zh: '欧根纱' }, category: FabricCategory.SPECIALTY },
  { value: FabricMaterial.TAFFETA, label: { en: 'Taffeta', zh: '塔夫绸' }, category: FabricCategory.SPECIALTY },
  { value: FabricMaterial.CREPE, label: { en: 'Crepe', zh: '绉绸' }, category: FabricCategory.SPECIALTY },
  { value: FabricMaterial.VELVET, label: { en: 'Velvet', zh: '天鹅绒' }, category: FabricCategory.SPECIALTY },
  { value: FabricMaterial.BROCADE, label: { en: 'Brocade', zh: '锦缎' }, category: FabricCategory.SPECIALTY }
];
```

## 前端下拉选择器示例

```vue
<template>
  <select v-model="selectedMaterial" class="material-selector">
    <option value="">Select Material / 选择材料</option>
    <optgroup 
      v-for="category in categories" 
      :key="category" 
      :label="getCategoryLabel(category)"
    >
      <option
        v-for="material in getMaterialsByCategory(category)"
        :key="material.value"
        :value="material.value"
      >
        {{ material.label.zh }} / {{ material.label.en }}
      </option>
    </optgroup>
  </select>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { FABRIC_MATERIALS, FabricCategory } from '@/types/fabric-materials';

const selectedMaterial = ref('');

const categories = Object.values(FabricCategory);

const getCategoryLabel = (category: FabricCategory) => {
  const labels = {
    [FabricCategory.NATURAL]: '天然纤维 / Natural Fibers',
    [FabricCategory.SYNTHETIC]: '合成纤维 / Synthetic Fibers',
    [FabricCategory.SEMI_SYNTHETIC]: '半合成纤维 / Semi-Synthetic Fibers',
    [FabricCategory.ELASTIC]: '弹性纤维 / Elastic Fibers',
    [FabricCategory.SPECIALTY]: '特殊面料 / Specialty Fabrics'
  };
  return labels[category];
};

const getMaterialsByCategory = (category: FabricCategory) => {
  return FABRIC_MATERIALS.filter(m => m.category === category);
};
</script>
```

## 扩展说明

- **可扩展性**：如需增加新材料，在枚举中添加即可，前后端保持同步
- **国际化**：所有材料名称支持中英文双语，可扩展到其他语言
- **分类导航**：前端下拉选择器按分类分组，方便用户快速定位
- **业务规则**：某些材料组合可能不常见（如 Velvet + Tulle），可在业务层添加提示或建议
