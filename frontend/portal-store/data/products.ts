import type { Product, PaletteSwatch } from './types'

const C = '/competitor-refs'

// 户外婚礼调色板（Shop by Color 核心差异化，借鉴 Birdy Grey 色彩驱动）
export const palette: PaletteSwatch[] = [
  { name: 'Sage', hex: '#8B9D83', count: 28, theme: 'garden' },
  { name: 'Dusty Blue', hex: '#9FB2C4', count: 24, theme: 'beach' },
  { name: 'Blush', hex: '#E5C1BC', count: 31, theme: 'garden' },
  { name: 'Champagne', hex: '#E8D9BE', count: 22, theme: 'vineyard' },
  { name: 'Lavender', hex: '#C3B6D6', count: 18, theme: 'forest' },
  { name: 'Terracotta', hex: '#C08763', count: 15, theme: 'vineyard' },
  { name: 'Ivory', hex: '#F4EEE2', count: 40, theme: 'beach' },
  { name: 'Espresso', hex: '#5A4636', count: 12, theme: 'forest' }
]

export const products: Product[] = [
  // ============ WEDDING DRESSES ============
  {
    id: 'p-aurelia',
    slug: 'aurelia-gown',
    name: 'Aurelia A-Line Tulle Gown',
    category: 'wedding-dresses',
    subCategory: 'A-Line',
    price: 1280,
    compareAtPrice: 1480,
    rating: 4.9,
    reviewCount: 142,
    gallery: [`${C}/kissprom/wedding-aline-tulle-01.jpg`, `${C}/kissprom/wedding-aline-lace-02.jpg`, `${C}/davidsbridal/wedding-dress-02.jpg`],
    lifestyle: `${C}/davidsbridal/wedding-dress-04.jpg`,
    hasVideo: true,
    colors: [
      { name: 'Ivory', hex: '#F4EEE2', image: `${C}/kissprom/wedding-aline-tulle-01.jpg` },
      { name: 'Champagne', hex: '#E8D9BE', image: `${C}/kissprom/wedding-aline-longsleeve-06.jpg` }
    ],
    sizes: [
      { size: 'US 0', inStock: true }, { size: 'US 2', inStock: true }, { size: 'US 4', inStock: true },
      { size: 'US 6', inStock: true }, { size: 'US 8', inStock: false }, { size: 'US 10', inStock: true },
      { size: 'US 12', inStock: true }, { size: 'Custom', inStock: true }
    ],
    silhouette: 'A-Line', fabric: 'Tulle', neckline: 'One-Shoulder', sleeve: 'Sleeveless', length: 'Floor',
    occasion: ['Bride'], themes: ['Garden', 'Beach', 'Vineyard'],
    badges: ['Best Seller'], isBestSeller: true,
    description: 'A romantic A-line silhouette in airy layered tulle, designed to catch the golden hour light. The one-shoulder neckline and soft sweep train make it a dream for garden and beachfront ceremonies.',
    details: ['One-shoulder neckline', 'Layered soft tulle skirt', 'Sweep train', 'Concealed back zipper with covered buttons', 'Fully lined bodice'],
    fabricCare: ['Shell: 100% nylon tulle', 'Lining: 100% polyester', 'Professional dry clean only', 'Steam to remove wrinkles'],
    pairsWith: ['a-cathedral-veil', 'a-pearl-heels', 'p-seabreeze']
  },
  {
    id: 'p-celeste',
    slug: 'celeste-lace-gown',
    name: 'Celeste V-Neck Lace Gown',
    category: 'wedding-dresses',
    subCategory: 'A-Line',
    price: 1490,
    rating: 4.8,
    reviewCount: 98,
    gallery: [`${C}/kissprom/wedding-aline-lace-02.jpg`, `${C}/davidsbridal/wedding-dress-03.jpg`, `${C}/davidsbridal/wedding-dress-04.jpg`],
    lifestyle: `${C}/davidsbridal/wedding-dress-04.jpg`,
    hasVideo: true,
    colors: [{ name: 'Ivory', hex: '#F4EEE2', image: `${C}/kissprom/wedding-aline-lace-02.jpg` }],
    sizes: [
      { size: 'US 0', inStock: true }, { size: 'US 2', inStock: true }, { size: 'US 4', inStock: true },
      { size: 'US 6', inStock: true }, { size: 'US 8', inStock: true }, { size: 'US 10', inStock: true },
      { size: 'US 14', inStock: true }, { size: 'Custom', inStock: true }
    ],
    silhouette: 'A-Line', fabric: 'Lace', neckline: 'V-Neck', sleeve: 'Sleeveless', length: 'Floor',
    occasion: ['Bride'], themes: ['Garden', 'Forest'], badges: ['New'], isNew: true,
    description: 'Delicate floral lace appliqué cascades over a flattering V-neck bodice with a thigh-high slit for effortless movement. Made for the bride who wants romance with a modern edge.',
    details: ['Plunging V-neckline', 'Floral lace appliqué', 'Thigh-high slit', 'Open scalloped back', 'Buttoned closure'],
    fabricCare: ['Shell: corded lace over satin', 'Spot clean or dry clean', 'Store flat in garment bag'],
    pairsWith: ['a-cathedral-veil', 'a-drop-earrings']
  },
  {
    id: 'p-marina',
    slug: 'marina-mermaid-gown',
    name: 'Marina Mermaid Chiffon Gown',
    category: 'wedding-dresses',
    subCategory: 'Mermaid',
    price: 1620,
    rating: 4.7,
    reviewCount: 64,
    gallery: [`${C}/kissprom/wedding-mermaid-chiffon-03.jpg`, `${C}/kissprom/wedding-mermaid-lace-04.jpg`, `${C}/davidsbridal/wedding-dress-05.jpg`],
    lifestyle: `${C}/davidsbridal/wedding-dress-04.jpg`,
    colors: [{ name: 'Ivory', hex: '#F4EEE2', image: `${C}/kissprom/wedding-mermaid-chiffon-03.jpg` }],
    sizes: [
      { size: 'US 2', inStock: true }, { size: 'US 4', inStock: true }, { size: 'US 6', inStock: true },
      { size: 'US 8', inStock: true }, { size: 'US 10', inStock: false }, { size: 'Custom', inStock: true }
    ],
    silhouette: 'Mermaid', fabric: 'Chiffon', neckline: 'Deep-V', sleeve: 'Sleeveless', length: 'Floor',
    occasion: ['Bride'], themes: ['Beach', 'Vineyard'],
    description: 'A breathtaking mermaid silhouette in flowing chiffon with delicate appliqués, sculpting the figure before flaring into a dramatic train. Designed for the bold, glamorous bride.',
    details: ['Deep-V neckline', 'Fitted mermaid bodice', 'Chiffon godet train', 'Appliqué detailing', 'Boned bodice'],
    fabricCare: ['Shell: 100% chiffon', 'Dry clean only'],
    pairsWith: ['a-drop-earrings', 'a-pearl-heels']
  },
  {
    id: 'p-willow',
    slug: 'willow-longsleeve-gown',
    name: 'Willow Long-Sleeve Chiffon Gown',
    category: 'wedding-dresses',
    subCategory: 'A-Line',
    price: 1380,
    rating: 4.9,
    reviewCount: 77,
    gallery: [`${C}/kissprom/wedding-aline-longsleeve-06.jpg`, `${C}/davidsbridal/wedding-dress-06.jpg`, `${C}/davidsbridal/wedding-dress-08.jpg`],
    lifestyle: `${C}/davidsbridal/wedding-dress-04.jpg`,
    colors: [{ name: 'Ivory', hex: '#F4EEE2', image: `${C}/kissprom/wedding-aline-longsleeve-06.jpg` }],
    sizes: [
      { size: 'US 0', inStock: true }, { size: 'US 2', inStock: true }, { size: 'US 4', inStock: true },
      { size: 'US 6', inStock: true }, { size: 'US 8', inStock: true }, { size: 'Custom', inStock: true }
    ],
    silhouette: 'A-Line', fabric: 'Chiffon', neckline: 'V-Neck', sleeve: 'Long Sleeve', length: 'Floor',
    occasion: ['Bride'], themes: ['Forest', 'Garden'], badges: ['New'], isNew: true,
    description: 'Ethereal long sleeves in sheer chiffon bring a touch of forest-fairytale romance. Lightweight enough for an outdoor ceremony, elegant enough to take your breath away.',
    details: ['Illusion long sleeves', 'V-neckline', 'Flowing A-line skirt', 'Keyhole back'],
    fabricCare: ['Shell: chiffon', 'Dry clean only'],
    pairsWith: ['a-cathedral-veil', 'a-hair-vine']
  },
  {
    id: 'p-coraline',
    slug: 'coraline-beach-gown',
    name: 'Coraline Short Beach Gown',
    category: 'wedding-dresses',
    subCategory: 'Short',
    price: 890,
    compareAtPrice: 1020,
    rating: 4.6,
    reviewCount: 53,
    gallery: [`${C}/kissprom/wedding-beach-short-05.jpg`, `${C}/davidsbridal/wedding-dress-set-07.jpg`],
    lifestyle: `${C}/kissprom/prom-champagne-lace-05.jpg`,
    colors: [{ name: 'Ivory', hex: '#F4EEE2', image: `${C}/kissprom/wedding-beach-short-05.jpg` }],
    sizes: [
      { size: 'US 0', inStock: true }, { size: 'US 2', inStock: true }, { size: 'US 4', inStock: true },
      { size: 'US 6', inStock: true }, { size: 'US 8', inStock: true }
    ],
    silhouette: 'Sheath', fabric: 'Satin', neckline: 'Strapless', sleeve: 'Sleeveless', length: 'Short',
    occasion: ['Bride'], themes: ['Beach'], badges: ['Sale'],
    description: 'A playful high-low silhouette made for sand between your toes. Lightweight satin with a corset back keeps you cool and effortlessly chic for a beachfront I-do.',
    details: ['High-low hem', 'Corset lace-up back', 'Strapless sweetheart neckline'],
    fabricCare: ['Shell: stretch satin', 'Hand wash cold or dry clean'],
    pairsWith: ['a-pearl-heels']
  },

  // ============ SPECIAL OCCASION ============
  {
    id: 'p-seabreeze',
    slug: 'seabreeze-bridesmaid',
    name: 'Seabreeze One-Shoulder Bridesmaid Dress',
    category: 'special-occasion',
    subCategory: 'Bridesmaid',
    price: 168,
    rating: 4.8,
    reviewCount: 311,
    gallery: [`${C}/birdygrey/bridesmaid-pink-bella-01.jpg`, `${C}/birdygrey/bridesmaid-pink-bryten-02.jpg`, `${C}/birdygrey/bridesmaid-pink-connie-03.jpg`],
    lifestyle: `${C}/birdygrey/bridesmaid-pink-bryten-02.jpg`,
    hasVideo: true,
    colors: [
      { name: 'Blush', hex: '#E5C1BC', image: `${C}/birdygrey/bridesmaid-pink-bella-01.jpg` },
      { name: 'Espresso', hex: '#5A4636', image: `${C}/birdygrey/bridesmaid-espresso-mia-05.jpg` },
      { name: 'Black', hex: '#2B2925', image: `${C}/birdygrey/bridesmaid-black-mia-06.jpg` },
      { name: 'Lemon', hex: '#E8DC8A', image: `${C}/birdygrey/bridesmaid-lemon-bella-10.jpg` }
    ],
    sizes: [
      { size: 'US 0', inStock: true }, { size: 'US 2', inStock: true }, { size: 'US 4', inStock: true },
      { size: 'US 6', inStock: true }, { size: 'US 8', inStock: true }, { size: 'US 10', inStock: true },
      { size: 'US 12', inStock: true }, { size: 'US 14', inStock: true }, { size: 'Plus 16', inStock: true },
      { size: 'Plus 18', inStock: true }, { size: 'Custom', inStock: true }
    ],
    silhouette: 'A-Line', fabric: 'Luxe Knit', neckline: 'One-Shoulder', sleeve: 'Sleeveless', length: 'Floor',
    occasion: ['Bridesmaid'], themes: ['Garden', 'Beach'], badges: ['Best Seller'], isBestSeller: true,
    description: 'The bridesmaid dress your whole party will actually re-wear. A buttery luxe-knit one-shoulder style in 18+ shades, designed to flatter every body in the group.',
    details: ['One-shoulder neckline', 'Soft luxe stretch knit', 'Side pockets', 'Available in 18+ colors', 'Standard & Plus sizing'],
    fabricCare: ['Shell: 95% polyester, 5% spandex', 'Machine wash cold, lay flat to dry'],
    pairsWith: ['p-aurelia', 'a-drop-earrings']
  },
  {
    id: 'p-meadow',
    slug: 'meadow-bridesmaid',
    name: 'Meadow Sage Bridesmaid Dress',
    category: 'special-occasion',
    subCategory: 'Bridesmaid',
    price: 158,
    rating: 4.9,
    reviewCount: 204,
    gallery: [`${C}/davidsbridal/bridesmaid-sage-01.jpg`, `${C}/davidsbridal/bridesmaid-olive-07.jpg`, `${C}/davidsbridal/bridesmaid-dustyblue-04.jpg`],
    lifestyle: `${C}/birdygrey/bridesmaid-pink-bryten-02.jpg`,
    colors: [
      { name: 'Sage', hex: '#8B9D83', image: `${C}/davidsbridal/bridesmaid-sage-01.jpg` },
      { name: 'Olive', hex: '#6B7D4F', image: `${C}/davidsbridal/bridesmaid-olive-07.jpg` },
      { name: 'Dusty Blue', hex: '#9FB2C4', image: `${C}/davidsbridal/bridesmaid-dustyblue-04.jpg` },
      { name: 'Steel Blue', hex: '#6E8294', image: `${C}/davidsbridal/bridesmaid-steelblue-02.jpg` }
    ],
    sizes: [
      { size: 'US 0', inStock: true }, { size: 'US 2', inStock: true }, { size: 'US 4', inStock: true },
      { size: 'US 6', inStock: true }, { size: 'US 8', inStock: true }, { size: 'US 10', inStock: true },
      { size: 'US 12', inStock: false }, { size: 'Plus 16', inStock: true }, { size: 'Custom', inStock: true }
    ],
    silhouette: 'A-Line', fabric: 'Chiffon', neckline: 'V-Neck', sleeve: 'Sleeveless', length: 'Floor',
    occasion: ['Bridesmaid'], themes: ['Garden', 'Forest', 'Vineyard'], badges: ['Best Seller'], isBestSeller: true,
    description: 'Flowing chiffon in the most coveted garden shades. The V-neck and flutter movement make this the go-to for sage and earth-toned outdoor weddings.',
    details: ['V-neckline', 'Flowing chiffon skirt', 'Hidden zipper', 'Garden tones'],
    fabricCare: ['Shell: 100% chiffon', 'Dry clean recommended'],
    pairsWith: ['p-aurelia', 'a-hair-vine']
  },
  {
    id: 'p-petal',
    slug: 'petal-bridesmaid',
    name: 'Petal Coral Bridesmaid Dress',
    category: 'special-occasion',
    subCategory: 'Bridesmaid',
    price: 162,
    rating: 4.7,
    reviewCount: 119,
    gallery: [`${C}/davidsbridal/bridesmaid-coral-03.jpg`, `${C}/davidsbridal/bridesmaid-petal-06.jpg`, `${C}/davidsbridal/bridesmaid-ballet-05.jpg`],
    lifestyle: `${C}/birdygrey/bridesmaid-pink-bryten-02.jpg`,
    colors: [
      { name: 'Coral', hex: '#E08A6E', image: `${C}/davidsbridal/bridesmaid-coral-03.jpg` },
      { name: 'Petal', hex: '#E5C1BC', image: `${C}/davidsbridal/bridesmaid-petal-06.jpg` },
      { name: 'Ballet', hex: '#EAD3D0', image: `${C}/davidsbridal/bridesmaid-ballet-05.jpg` }
    ],
    sizes: [
      { size: 'US 2', inStock: true }, { size: 'US 4', inStock: true }, { size: 'US 6', inStock: true },
      { size: 'US 8', inStock: true }, { size: 'US 10', inStock: true }, { size: 'Custom', inStock: true }
    ],
    silhouette: 'A-Line', fabric: 'Chiffon', neckline: 'Halter', sleeve: 'Sleeveless', length: 'Floor',
    occasion: ['Bridesmaid'], themes: ['Beach', 'Vineyard'],
    description: 'Warm sunset shades that glow against golden hour. A halter chiffon style with soft drape, perfect for terracotta and coral palettes.',
    details: ['Halter neckline', 'Chiffon A-line skirt', 'Open back'],
    fabricCare: ['Shell: chiffon', 'Dry clean'],
    pairsWith: ['p-marina']
  },
  {
    id: 'p-aria',
    slug: 'aria-prom-dress',
    name: 'Aria One-Shoulder Prom Gown',
    category: 'special-occasion',
    subCategory: 'Prom',
    price: 248,
    rating: 4.6,
    reviewCount: 88,
    gallery: [`${C}/kissprom/prom-sage-oneshoulder-01.jpg`, `${C}/kissprom/prom-skyblue-oneshoulder-02.jpg`, `${C}/kissprom/prom-blush-oneshoulder-03.jpg`],
    colors: [
      { name: 'Sage', hex: '#8B9D83', image: `${C}/kissprom/prom-sage-oneshoulder-01.jpg` },
      { name: 'Sky Blue', hex: '#9FB2C4', image: `${C}/kissprom/prom-skyblue-oneshoulder-02.jpg` },
      { name: 'Blush', hex: '#E5C1BC', image: `${C}/kissprom/prom-blush-oneshoulder-03.jpg` },
      { name: 'Lavender', hex: '#C3B6D6', image: `${C}/kissprom/prom-lavender-oneshoulder-04.jpg` }
    ],
    sizes: [
      { size: 'US 00', inStock: true }, { size: 'US 0', inStock: true }, { size: 'US 2', inStock: true },
      { size: 'US 4', inStock: true }, { size: 'US 6', inStock: true }, { size: 'US 8', inStock: true },
      { size: 'US 10', inStock: true }, { size: 'Custom', inStock: true }
    ],
    silhouette: 'A-Line', fabric: 'Satin', neckline: 'One-Shoulder', sleeve: 'Sleeveless', length: 'Floor',
    occasion: ['Prom', 'Guest'], themes: ['Garden'], badges: ['New'], isNew: true,
    description: 'A showstopping one-shoulder satin gown with a sculpted bodice and floor-sweeping skirt. Comes in eight dreamy shades for prom, galas, and wedding guests.',
    details: ['One-shoulder neckline', 'Sculpted satin bodice', 'Floor-length A-line skirt', 'Hidden side zipper'],
    fabricCare: ['Shell: stretch satin', 'Dry clean only'],
    pairsWith: ['a-drop-earrings']
  },
  {
    id: 'p-juliet',
    slug: 'juliet-lace-gown',
    name: 'Juliet Lace Evening Gown',
    category: 'special-occasion',
    subCategory: 'Evening',
    price: 286,
    rating: 4.8,
    reviewCount: 71,
    gallery: [`${C}/kissprom/prom-champagne-lace-05.jpg`, `${C}/kissprom/prom-darkgreen-lace-06.jpg`, `${C}/kissprom/prom-lavender-lace-09.jpg`],
    colors: [
      { name: 'Champagne', hex: '#E8D9BE', image: `${C}/kissprom/prom-champagne-lace-05.jpg` },
      { name: 'Dark Green', hex: '#3C4A3A', image: `${C}/kissprom/prom-darkgreen-lace-06.jpg` },
      { name: 'Lavender', hex: '#C3B6D6', image: `${C}/kissprom/prom-lavender-lace-09.jpg` }
    ],
    sizes: [
      { size: 'US 0', inStock: true }, { size: 'US 2', inStock: true }, { size: 'US 4', inStock: true },
      { size: 'US 6', inStock: true }, { size: 'US 8', inStock: false }, { size: 'Custom', inStock: true }
    ],
    silhouette: 'A-Line', fabric: 'Lace', neckline: 'Sweetheart', sleeve: 'Strap', length: 'Floor',
    occasion: ['Evening', 'Guest', 'MOB'], themes: ['Vineyard', 'Forest'],
    description: 'Floral lace appliqué over a flowing slip, with delicate straps and a sweetheart neckline. An elegant choice for mothers of the bride and evening wedding guests.',
    details: ['Sweetheart neckline', 'Floral lace appliqué', 'Adjustable straps', 'Flowing slip skirt'],
    fabricCare: ['Shell: corded lace', 'Dry clean'],
    pairsWith: ['a-drop-earrings']
  },
  {
    id: 'p-bloom',
    slug: 'bloom-cocktail-dress',
    name: 'Bloom Floral Cocktail Dress',
    category: 'special-occasion',
    subCategory: 'Cocktail',
    price: 198,
    rating: 4.5,
    reviewCount: 46,
    gallery: [`${C}/kissprom/prom-floral-sweetheart-08.jpg`, `${C}/kissprom/prom-offshoulder-tiered-07.jpg`, `${C}/kissprom/homecoming-pink-short-01.jpg`],
    colors: [
      { name: 'Floral', hex: '#D9A7B0', image: `${C}/kissprom/prom-floral-sweetheart-08.jpg` },
      { name: 'Pink', hex: '#E5B8C4', image: `${C}/kissprom/homecoming-pink-short-01.jpg` }
    ],
    sizes: [
      { size: 'US 0', inStock: true }, { size: 'US 2', inStock: true }, { size: 'US 4', inStock: true },
      { size: 'US 6', inStock: true }, { size: 'US 8', inStock: true }
    ],
    silhouette: 'Fit & Flare', fabric: 'Tulle', neckline: 'Sweetheart', sleeve: 'Sleeveless', length: 'Short',
    occasion: ['Cocktail', 'Guest'], themes: ['Garden'],
    description: 'A flirty short cocktail dress with a sweetheart neckline and playful tiered tulle. Made for rehearsal dinners, bridal showers, and garden-party guests.',
    details: ['Sweetheart neckline', 'Tiered tulle skirt', 'Short length'],
    fabricCare: ['Shell: tulle over satin', 'Spot clean'],
    pairsWith: ['a-pearl-heels']
  },
  {
    id: 'p-luna',
    slug: 'luna-homecoming-dress',
    name: 'Luna Sequin Homecoming Dress',
    category: 'special-occasion',
    subCategory: 'Cocktail',
    price: 142,
    rating: 4.4,
    reviewCount: 39,
    gallery: [`${C}/kissprom/homecoming-pink-sequin-03.jpg`, `${C}/kissprom/homecoming-darkgreen-short-02.jpg`],
    colors: [
      { name: 'Pink Sequin', hex: '#E5B8C4', image: `${C}/kissprom/homecoming-pink-sequin-03.jpg` },
      { name: 'Dark Green', hex: '#3C4A3A', image: `${C}/kissprom/homecoming-darkgreen-short-02.jpg` }
    ],
    sizes: [
      { size: 'US 00', inStock: true }, { size: 'US 0', inStock: true }, { size: 'US 2', inStock: true },
      { size: 'US 4', inStock: true }, { size: 'US 6', inStock: true }
    ],
    silhouette: 'Bodycon', fabric: 'Sequin', neckline: 'One-Shoulder', sleeve: 'Sleeveless', length: 'Short',
    occasion: ['Cocktail', 'Guest'], themes: ['Vineyard'],
    description: 'All-over sequins and a sculpted one-shoulder bodice make this short style the life of the after-party.',
    details: ['One-shoulder neckline', 'All-over sequins', 'Bodycon fit', 'Short length'],
    fabricCare: ['Shell: sequin mesh', 'Spot clean only'],
    pairsWith: ['a-drop-earrings']
  },

  // ============ ACCESSORIES ============
  {
    id: 'a-cathedral-veil',
    slug: 'cathedral-veil',
    name: 'Aurelle Cathedral Veil',
    category: 'accessories',
    subCategory: 'Veils',
    price: 158,
    rating: 4.9,
    reviewCount: 87,
    gallery: [`${C}/birdygrey/accessory-jewelry-01.jpg`, `${C}/davidsbridal/wedding-dress-04.jpg`],
    colors: [{ name: 'Ivory', hex: '#F4EEE2', image: `${C}/birdygrey/accessory-jewelry-01.jpg` }],
    sizes: [{ size: 'Cathedral 108"', inStock: true }, { size: 'Chapel 90"', inStock: true }, { size: 'Fingertip 36"', inStock: true }],
    occasion: ['Bride'], themes: ['Garden', 'Forest', 'Vineyard'], badges: ['Best Seller'], isBestSeller: true,
    description: 'A breathtaking single-tier cathedral veil with a delicate lace trim that floats behind you down the aisle.',
    details: ['Single-tier tulle', 'Lace-trimmed edge', 'Comb attachment', 'Cathedral length'],
    fabricCare: ['Tulle with corded lace trim', 'Steam to release wrinkles'],
    pairsWith: ['p-aurelia', 'p-celeste']
  },
  {
    id: 'a-pearl-heels',
    slug: 'pearl-heels',
    name: 'Margaux Pearl Block Heels',
    category: 'accessories',
    subCategory: 'Shoes',
    price: 128,
    rating: 4.6,
    reviewCount: 52,
    gallery: [`${C}/birdygrey/accessory-pjs-02.jpg`, `${C}/birdygrey/accessory-jewelry-01.jpg`],
    colors: [{ name: 'Ivory', hex: '#F4EEE2', image: `${C}/birdygrey/accessory-pjs-02.jpg` }],
    sizes: [
      { size: 'US 5', inStock: true }, { size: 'US 6', inStock: true }, { size: 'US 7', inStock: true },
      { size: 'US 8', inStock: true }, { size: 'US 9', inStock: true }, { size: 'US 10', inStock: false }
    ],
    occasion: ['Bride', 'Bridesmaid'], themes: ['Garden', 'Beach'],
    description: 'Comfortable block heels adorned with hand-placed pearls — the perfect "something" for grass, sand, or dance floor.',
    details: ['2.5" block heel', 'Hand-placed pearls', 'Cushioned insole', 'Ankle strap'],
    fabricCare: ['Satin upper', 'Wipe clean'],
    pairsWith: ['p-aurelia', 'p-coraline']
  },
  {
    id: 'a-drop-earrings',
    slug: 'drop-earrings',
    name: 'Estelle Crystal Drop Earrings',
    category: 'accessories',
    subCategory: 'Jewelry',
    price: 68,
    rating: 4.8,
    reviewCount: 134,
    gallery: [`${C}/birdygrey/accessory-jewelry-01.jpg`],
    colors: [{ name: 'Gold', hex: '#C19A6B', image: `${C}/birdygrey/accessory-jewelry-01.jpg` }],
    sizes: [{ size: 'One Size', inStock: true }],
    occasion: ['Bride', 'Bridesmaid', 'Guest'], themes: ['Vineyard', 'Garden'], badges: ['Best Seller'], isBestSeller: true,
    description: 'Delicate crystal drops on a gold-fill hook. Catches the light beautifully in photos.',
    details: ['Gold-fill hooks', 'Crystal drops', 'Lightweight', 'Hypoallergenic'],
    fabricCare: ['Gold-fill & crystal', 'Store dry'],
    pairsWith: ['p-seabreeze', 'p-aria']
  },
  {
    id: 'a-hair-vine',
    slug: 'hair-vine',
    name: 'Fleur Gold Hair Vine',
    category: 'accessories',
    subCategory: 'Headpieces',
    price: 78,
    rating: 4.7,
    reviewCount: 61,
    gallery: [`${C}/birdygrey/accessory-jewelry-01.jpg`, `${C}/birdygrey/lifestyle-flowergirl-08.jpg`],
    colors: [{ name: 'Gold', hex: '#C19A6B', image: `${C}/birdygrey/accessory-jewelry-01.jpg` }],
    sizes: [{ size: 'One Size', inStock: true }],
    occasion: ['Bride', 'Flower Girl'], themes: ['Garden', 'Forest'],
    description: 'A flexible gold hair vine scattered with leaves and pearls — woven through braids or pinned into an updo for that ethereal garden bride look.',
    details: ['Bendable gold wire', 'Pearl & leaf detail', 'Pinnable'],
    fabricCare: ['Gold-tone wire', 'Handle gently'],
    pairsWith: ['p-willow', 'p-meadow']
  }
]

export function getProduct(slug: string) {
  return products.find((p) => p.slug === slug)
}

export function getByCategory(category: Product['category']) {
  return products.filter((p) => p.category === category)
}

export function getByTheme(theme: string) {
  return products.filter((p) => p.themes?.some((t) => t.toLowerCase() === theme.toLowerCase()))
}

export function getRelated(product: Product, limit = 4) {
  return products.filter((p) => p.category === product.category && p.id !== product.id).slice(0, limit)
}

export function getPairsWith(product: Product) {
  return (product.pairsWith ?? []).map((id) => products.find((p) => p.id === id)).filter(Boolean) as Product[]
}
