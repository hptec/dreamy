import type { Showroom } from './types'

/**
 * 静态导出（output: 'export'）约束：/showroom/[id] 必须预渲染全部可能路径。
 * 预置 2 个演示 Showroom + 6 个保留 id 槽位供用户自建（createShowroom 从池中取用）。
 */
export const SEED_SHOWROOM_IDS = ['sr-sarah', 'sr-amelia']
export const CUSTOM_SHOWROOM_ID_POOL = ['custom-1', 'custom-2', 'custom-3', 'custom-4', 'custom-5', 'custom-6']
export const ALL_SHOWROOM_IDS = [...SEED_SHOWROOM_IDS, ...CUSTOM_SHOWROOM_ID_POOL]

/** 预置演示数据：首次访问写入 localStorage，之后以本地状态为准 */
export const seedShowrooms: Showroom[] = [
  {
    id: 'sr-sarah',
    name: "Sarah's Bridal Party",
    weddingDate: '2026-09-19',
    createdAt: '2026-05-28',
    members: [
      { id: 'm-sarah', name: 'Sarah', role: 'bride' },
      { id: 'm-emma', name: 'Emma', role: 'bridesmaid', assignedProductId: 'p-meadow', assignedColor: 'Sage', hasOrdered: true },
      { id: 'm-olivia', name: 'Olivia', role: 'bridesmaid', assignedProductId: 'p-meadow', assignedColor: 'Sage' },
      { id: 'm-mia', name: 'Mia', role: 'bridesmaid', assignedProductId: 'p-seabreeze', assignedColor: 'Blush' },
      { id: 'm-chloe', name: 'Chloe', role: 'bridesmaid' }
    ],
    items: [
      {
        productId: 'p-meadow',
        color: 'Sage',
        upVotes: ['Sarah', 'Emma', 'Olivia', 'Mia'],
        downVotes: [],
        comments: [
          { author: 'Emma', text: 'Obsessed with this sage shade — it photographs beautifully outdoors.', date: '2026-06-02' },
          { author: 'Olivia', text: 'The chiffon is so flowy, and it has pockets-level comfort. Yes from me!', date: '2026-06-03' }
        ]
      },
      {
        productId: 'p-seabreeze',
        color: 'Blush',
        upVotes: ['Emma', 'Mia', 'Chloe'],
        downVotes: [],
        comments: [
          { author: 'Mia', text: 'One-shoulder is so flattering, and I would actually re-wear this.', date: '2026-06-01' }
        ]
      },
      {
        productId: 'p-petal',
        color: 'Coral',
        upVotes: ['Chloe'],
        downVotes: ['Emma', 'Olivia'],
        comments: [
          { author: 'Chloe', text: 'I love the coral for a September garden wedding, but happy either way!', date: '2026-06-04' }
        ]
      }
    ]
  },
  {
    id: 'sr-amelia',
    name: "Amelia's Vineyard Weekend",
    weddingDate: '2026-11-07',
    createdAt: '2026-06-05',
    members: [
      { id: 'm-amelia', name: 'Amelia', role: 'bride' },
      { id: 'm-grace', name: 'Grace', role: 'bridesmaid', assignedProductId: 'p-juliet', assignedColor: 'Champagne' },
      { id: 'm-lily', name: 'Lily', role: 'bridesmaid' }
    ],
    items: [
      {
        productId: 'p-juliet',
        color: 'Champagne',
        upVotes: ['Amelia', 'Grace'],
        downVotes: [],
        comments: [
          { author: 'Grace', text: 'Champagne lace against vineyard greens — dreamy.', date: '2026-06-07' }
        ]
      },
      {
        productId: 'p-aria',
        color: 'Sage',
        upVotes: ['Lily'],
        downVotes: [],
        comments: []
      }
    ]
  }
]
