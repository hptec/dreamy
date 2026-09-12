/** 分类页 hero 配置：主导航 CATEGORY 条目落到 /products?cat=<name>，hero 与标题按分类切换，避免全站同图同题。 */

export interface CollectionHero {
  title: string
  description: string
  heroImage: string
  objectPosition?: string
  /** 横图用 wide（21:9 + 悬浮标题带），竖图用 split（左文右图） */
  variant?: 'split' | 'wide'
}

export const ALL_STYLES_HERO: CollectionHero = {
  title: 'All Styles',
  description: 'Every gown, dress, and finishing touch — designed for celebrations under open skies.',
  heroImage: '/photography/plp-all-styles.jpg',
  objectPosition: 'center 45%',
  variant: 'split'
}

const BY_CATEGORY: Record<string, CollectionHero> = {
  'Wedding Dresses': {
    title: 'Wedding Dresses',
    description: 'Airy, light-catching gowns made to move with you — from beachfront vows to garden celebrations.',
    heroImage: '/photography/plp-wedding-dresses.jpg',
    objectPosition: 'center 100%',
    variant: 'split'
  },
  Bridal: {
    title: 'Wedding Dresses',
    description: 'Airy, light-catching gowns made to move with you — from beachfront vows to garden celebrations.',
    heroImage: '/photography/plp-wedding-dresses.jpg',
    objectPosition: 'center 100%',
    variant: 'split'
  },
  Bridesmaids: {
    title: 'Bridesmaids',
    description: 'One dress, six ways. Soft, re-wearable silhouettes in a palette that photographs beautifully together.',
    heroImage: '/photography/plp-bridesmaids.jpg',
    objectPosition: 'center 60%',
    variant: 'wide'
  },
  'Occasion & Party': {
    title: 'Occasion & Party',
    description: 'Evening gowns and celebration dresses for the nights that deserve a train.',
    heroImage: '/photography/plp-occasion.jpg',
    objectPosition: 'center 30%',
    variant: 'split'
  },
  'Special Occasion': {
    title: 'Special Occasion',
    description: 'Bridesmaid, guest, and evening dresses your whole party will love — and actually re-wear.',
    heroImage: '/photography/plp-occasion.jpg',
    objectPosition: 'center 30%',
    variant: 'split'
  },
  Accessories: {
    title: 'Accessories',
    description: 'The finishing touches — veils, pearls, and headpieces to complete every look.',
    heroImage: '/photography/plp-accessories.jpg',
    objectPosition: 'center 30%',
    variant: 'split'
  }
}

export function resolveCollectionHero(categoryName?: string | null): CollectionHero | undefined {
  return categoryName ? BY_CATEGORY[categoryName] : undefined
}
