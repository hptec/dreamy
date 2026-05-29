import { PRODUCT_IMAGES, TILE_IMAGES, HERO_IMAGES } from './images.js'

export { PRODUCT_IMAGES, TILE_IMAGES, HERO_IMAGES }

/* ----------------------------------------------------------------------------
 * Maison Eden — prototype catalog (mock data)
 * Images are curated competitor-reference media for prototype use only.
 * -------------------------------------------------------------------------- */

export const COLORS = [
  { id: 'diamond-white', name: 'Diamond White', hex: '#FBFBF6', family: 'white' },
  { id: 'ivory', name: 'Ivory', hex: '#F4EAD5', family: 'white' },
  { id: 'champagne', name: 'Champagne', hex: '#E6CFA8', family: 'neutral' },
  { id: 'nude', name: 'Nude', hex: '#E3C9B5', family: 'neutral' },
  { id: 'blush', name: 'Blush', hex: '#E8C6C0', family: 'pink' },
  { id: 'dusty-rose', name: 'Dusty Rose', hex: '#C99C9A', family: 'pink' },
  { id: 'mauve', name: 'Mauve', hex: '#B0808C', family: 'pink' },
  { id: 'cabernet', name: 'Cabernet', hex: '#5E2330', family: 'red' },
  { id: 'burgundy', name: 'Burgundy', hex: '#6E2435', family: 'red' },
  { id: 'terracotta', name: 'Terracotta', hex: '#C57B53', family: 'orange' },
  { id: 'gold', name: 'Gold', hex: '#C2A175', family: 'neutral' },
  { id: 'dusty-blue', name: 'Dusty Blue', hex: '#8FA3B8', family: 'blue' },
  { id: 'steel-blue', name: 'Steel Blue', hex: '#5C7390', family: 'blue' },
  { id: 'navy', name: 'Navy', hex: '#2A3247', family: 'blue' },
  { id: 'dusty-sage', name: 'Dusty Sage', hex: '#9AA68C', family: 'green' },
  { id: 'eucalyptus', name: 'Eucalyptus', hex: '#7E8C72', family: 'green' },
  { id: 'emerald', name: 'Emerald', hex: '#1F4D3A', family: 'green' },
  { id: 'lavender', name: 'Lavender', hex: '#B3A6C9', family: 'purple' },
  { id: 'plum', name: 'Plum', hex: '#5A3A52', family: 'purple' },
  { id: 'dusty-lilac', name: 'Dusty Lilac', hex: '#9E8FA8', family: 'purple' },
  { id: 'silver', name: 'Silver', hex: '#C9CBD0', family: 'neutral' },
  { id: 'charcoal', name: 'Charcoal', hex: '#3A3A3A', family: 'black' },
  { id: 'black', name: 'Black', hex: '#1A1A1A', family: 'black' },
  { id: 'dusty-rose-2', name: 'Rose Quartz', hex: '#D8B4B0', family: 'pink' },
]
export const COLOR_MAP = Object.fromEntries(COLORS.map((c) => [c.id, c]))

export const COLOR_FAMILIES = [
  { id: 'white', name: 'Whites' },
  { id: 'neutral', name: 'Neutrals' },
  { id: 'pink', name: 'Pinks' },
  { id: 'red', name: 'Reds' },
  { id: 'blue', name: 'Blues' },
  { id: 'green', name: 'Greens' },
  { id: 'purple', name: 'Purples' },
  { id: 'black', name: 'Blacks' },
  { id: 'orange', name: 'Warm' },
]

export const FABRICS = ['Chiffon', 'Satin', 'Lace', 'Tulle', 'Crepe', 'Velvet', 'Sequins', 'Floral Jacquard', 'Stretch Satin', 'Organza', 'Mikado']
export const SILHOUETTES = ['A-Line', 'Mermaid', 'Ball Gown', 'Sheath', 'Two-Piece', 'Jumpsuit', 'Fit & Flare']
export const NECKLINES = ['Sweetheart', 'V-Neck', 'Square', 'Off-the-Shoulder', 'Strapless', 'Scoop', 'High Neck', 'Halter']
export const LENGTHS = ['Floor', 'Chapel Train', 'Tea', 'Ankle', 'Mini']
export const SLEEVES = ['Sleeveless', 'Cap Sleeve', 'Short Sleeve', 'Long Sleeve', 'Off-the-Shoulder', 'Spaghetti Strap']
export const FEATURES = ['With Pockets', 'Leg Slit', 'Corset Back', 'Convertible', 'Bra-Friendly', 'Plus Size']
export const OCCASIONS = ['Beach', 'Garden', 'Ballroom', 'Courthouse', 'Reception', 'Rehearsal', 'Bridal Shower', 'Black Tie', 'Cocktail']

export const CATEGORIES = {
  wedding: {
    id: 'wedding', route: '/wedding-dresses', label: 'Wedding Gowns',
    headline: 'Wedding Gowns', tagline: 'Hand-finished bridal couture, made to your measure.',
    hero: HERO_IMAGES[0],
  },
  evening: {
    id: 'evening', route: '/evening-dresses', label: 'Evening & Cocktail',
    headline: 'Evening & Cocktail', tagline: 'Floor-sweeping gowns and cocktail silhouettes for every soirée.',
    hero: HERO_IMAGES[5] || HERO_IMAGES[1],
  },
  bridesmaid: {
    id: 'bridesmaid', route: '/bridesmaid-dresses', label: 'Bridesmaids',
    headline: 'Bridesmaid Dresses', tagline: 'Ninety shades, one curated palette for your whole party.',
    hero: HERO_IMAGES[1],
  },
  mother: {
    id: 'mother', route: '/special-occasions', label: 'Mother of the Bride',
    headline: 'Mother of the Bride', tagline: 'Refined elegance for the most important guests.',
    hero: HERO_IMAGES[2],
  },
  guest: {
    id: 'guest', route: '/special-occasions', label: 'Wedding Guest',
    headline: 'Wedding Guest', tagline: 'Effortless dresses for every celebration on the calendar.',
    hero: HERO_IMAGES[6] || HERO_IMAGES[2],
  },
  flowergirl: {
    id: 'flowergirl', route: '/special-occasions', label: 'Flower Girl',
    headline: 'Flower Girl', tagline: 'Little dresses for the smallest members of the party.',
    hero: HERO_IMAGES[3],
  },
  accessories: {
    id: 'accessories', route: '/accessories', label: 'Accessories',
    headline: 'Veils & Accessories', tagline: 'The finishing touches — veils, belts, and fine jewelry.',
    hero: HERO_IMAGES[7] || HERO_IMAGES[4],
  },
}

// price-ending policy already handled in currency.formatMoney; store raw USD here.
const NAMES_F = ['Geneva', 'Florentina', 'Seraphine', 'Eloise', 'Marisol', 'Celestine', 'Ophelia', 'Vivienne', 'Isolde', 'Beatrice', 'Anouk', 'Delphine', 'Cosima', 'Liliane', 'Romy', 'Esmé', 'Adeline', 'Margaux', 'Noor', 'Calla', 'Wren', 'Solène', 'Maeve', 'Édith', 'Lucia', 'Brigitte', 'Saskia', 'Tamsin', 'Yara', 'Zara', 'Anaïs', 'Mireille']

let _imgCursor = 0
function takeImages(n) {
  const arr = []
  for (let i = 0; i < n; i++) {
    arr.push(PRODUCT_IMAGES[_imgCursor % PRODUCT_IMAGES.length])
    _imgCursor++
  }
  return arr
}

function slugify(s) {
  return s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')
}

// blueprint: [category, silhouette, fabric, neckline, priceUSD, originalUSD|0, tags[], colorIds[], galleryCount]
const BLUEPRINTS = [
  // Wedding (12)
  ['wedding', 'A-Line', 'Sequins', 'Sweetheart', 689, 0, ['new'], ['diamond-white', 'ivory', 'champagne'], 4],
  ['wedding', 'Ball Gown', 'Floral Jacquard', 'Square', 920, 0, ['best-seller'], ['diamond-white', 'ivory'], 4],
  ['wedding', 'Mermaid', 'Crepe', 'Strapless', 760, 0, [], ['diamond-white', 'ivory', 'nude'], 3],
  ['wedding', 'A-Line', 'Lace', 'Off-the-Shoulder', 845, 990, ['ships-now'], ['diamond-white', 'ivory', 'champagne'], 4],
  ['wedding', 'Sheath', 'Satin', 'V-Neck', 595, 0, [], ['diamond-white', 'champagne'], 3],
  ['wedding', 'Ball Gown', 'Tulle', 'Sweetheart', 1180, 0, ['best-seller'], ['diamond-white', 'ivory'], 4],
  ['wedding', 'Mermaid', 'Sequins', 'V-Neck', 980, 0, ['new'], ['diamond-white', 'champagne'], 3],
  ['wedding', 'A-Line', 'Lace', 'Scoop', 720, 0, [], ['diamond-white', 'ivory', 'nude'], 3],
  ['wedding', 'Two-Piece', 'Mikado', 'High Neck', 1340, 0, ['new'], ['diamond-white'], 3],
  ['wedding', 'Fit & Flare', 'Stretch Satin', 'Sweetheart', 640, 0, ['ships-now'], ['diamond-white', 'ivory'], 3],
  ['wedding', 'A-Line', 'Organza', 'Off-the-Shoulder', 560, 720, [], ['diamond-white', 'champagne'], 3],
  ['wedding', 'Ball Gown', 'Lace', 'Strapless', 1620, 0, ['best-seller'], ['diamond-white', 'ivory'], 4],
  // Evening (8)
  ['evening', 'Mermaid', 'Sequins', 'V-Neck', 420, 0, ['best-seller'], ['black', 'cabernet', 'navy', 'emerald'], 3],
  ['evening', 'Sheath', 'Velvet', 'Halter', 380, 0, ['new'], ['black', 'burgundy', 'emerald'], 3],
  ['evening', 'A-Line', 'Chiffon', 'V-Neck', 320, 420, ['ships-now'], ['dusty-blue', 'blush', 'black'], 3],
  ['evening', 'Mermaid', 'Satin', 'Strapless', 460, 0, [], ['cabernet', 'navy', 'emerald'], 3],
  ['evening', 'Two-Piece', 'Sequins', 'Square', 540, 0, ['new'], ['gold', 'silver', 'black'], 3],
  ['evening', 'Sheath', 'Crepe', 'Off-the-Shoulder', 290, 0, ['ships-now'], ['black', 'cabernet'], 2],
  ['evening', 'A-Line', 'Organza', 'Sweetheart', 360, 0, [], ['blush', 'lavender', 'dusty-blue'], 3],
  ['evening', 'Mermaid', 'Velvet', 'Halter', 480, 0, ['best-seller'], ['emerald', 'burgundy', 'navy'], 3],
  // Bridesmaid (6)
  ['bridesmaid', 'A-Line', 'Chiffon', 'V-Neck', 149, 0, ['best-seller'], ['dusty-blue', 'dusty-rose', 'dusty-sage', 'mauve', 'navy', 'cabernet', 'eucalyptus', 'terracotta'], 3],
  ['bridesmaid', 'Sheath', 'Satin', 'Square', 169, 0, ['new'], ['dusty-sage', 'cabernet', 'navy', 'champagne', 'mauve'], 3],
  ['bridesmaid', 'A-Line', 'Chiffon', 'Halter', 139, 0, ['ships-now'], ['dusty-rose', 'dusty-blue', 'eucalyptus', 'lavender'], 3],
  ['bridesmaid', 'Mermaid', 'Stretch Satin', 'V-Neck', 179, 0, [], ['cabernet', 'emerald', 'navy', 'plum'], 3],
  ['bridesmaid', 'Two-Piece', 'Chiffon', 'Scoop', 159, 0, [], ['dusty-blue', 'dusty-sage', 'terracotta'], 2],
  ['bridesmaid', 'A-Line', 'Satin', 'Off-the-Shoulder', 165, 0, ['best-seller'], ['mauve', 'dusty-rose', 'champagne', 'steel-blue'], 3],
  // Mother of the Bride (3)
  ['mother', 'Sheath', 'Floral Jacquard', 'Scoop', 289, 0, ['new'], ['navy', 'champagne', 'cabernet', 'steel-blue'], 3],
  ['mother', 'A-Line', 'Chiffon', 'V-Neck', 259, 0, [], ['dusty-blue', 'plum', 'eucalyptus'], 2],
  ['mother', 'Jumpsuit', 'Crepe', 'High Neck', 310, 0, ['ships-now'], ['navy', 'black', 'champagne'], 2],
  // Guest (3)
  ['guest', 'A-Line', 'Chiffon', 'Halter', 189, 0, ['ships-now'], ['terracotta', 'dusty-blue', 'emerald', 'blush'], 2],
  ['guest', 'Sheath', 'Satin', 'Square', 220, 0, ['new'], ['cabernet', 'navy', 'dusty-sage'], 2],
  ['guest', 'Fit & Flare', 'Crepe', 'V-Neck', 175, 240, [], ['lavender', 'blush', 'dusty-blue'], 2],
  // Flower Girl (2)
  ['flowergirl', 'Ball Gown', 'Tulle', 'Scoop', 119, 0, ['best-seller'], ['diamond-white', 'ivory', 'blush'], 2],
  ['flowergirl', 'A-Line', 'Lace', 'Cap Sleeve', 99, 0, [], ['diamond-white', 'champagne'], 2],
]

// Accessories defined separately (use tile imagery)
const ACCESSORY_BLUEPRINTS = [
  ['Cathedral Veil', 'Veil', 240, 0, ['best-seller']],
  ['Crystal Hair Vine', 'Hair', 95, 0, ['new']],
  ['Pearl Drop Earrings', 'Jewelry', 120, 0, []],
  ['Satin Bridal Belt', 'Belt', 85, 0, ['ships-now']],
  ['Embroidered Gloves', 'Gloves', 70, 0, []],
  ['Silk Bridal Robe', 'Robe', 130, 0, ['best-seller']],
  ['Beaded Clutch', 'Bags', 110, 0, ['new']],
  ['Chapel Train Veil', 'Veil', 180, 0, []],
]

function buildReviews(productId, count, gallery) {
  const authors = ['Emily R.', 'Sophia L.', 'Olivia M.', 'Ava T.', 'Isabella K.', 'Mia C.', 'Charlotte W.', 'Amelia H.', 'Harper S.', 'Evelyn D.']
  const sizes = ['US 2', 'US 4', 'US 6', 'US 8', 'US 10', 'US 12', 'Custom']
  const bodies = [
    'Absolutely breathtaking in person. The fabric is far more luxurious than I expected and the fit was perfect.',
    'I ordered custom sizing and it fit like a glove. The atelier even followed up about my measurements.',
    'The color is exactly as shown in the swatch. Received so many compliments at my wedding.',
    'Beautiful craftsmanship — the hand-beading detail is stunning. Worth every penny.',
    'Shipping was faster than expected and the packaging felt very premium. The gown exceeded expectations.',
    'Tried three other brands and this was by far the best quality. The lace is exquisite.',
  ]
  const titles = ['Dream gown', 'Exceeded expectations', 'Perfect fit', 'Stunning quality', 'Worth it', 'My favorite']
  const n = Math.min(count, 8)
  const out = []
  for (let i = 0; i < n; i++) {
    const rating = i % 7 === 0 ? 4 : 5
    out.push({
      id: `${productId}-r${i}`,
      author: authors[i % authors.length],
      rating,
      title: titles[i % titles.length],
      body: bodies[i % bodies.length],
      sizeWorn: sizes[i % sizes.length],
      verified: true,
      images: i % 2 === 0 ? [gallery[i % gallery.length]] : [],
      helpful: 3 + ((i * 7) % 40),
      date: `2026-0${(i % 5) + 1}-1${i % 9}`,
      reply: i === 0 ? 'Thank you so much for sharing — we are honoured to have been part of your day. — Maison Eden' : null,
    })
  }
  return out
}

function buildQuestions(productId) {
  return [
    { id: `${productId}-q1`, asker: 'Hannah', body: 'Does this run true to size?', answer: 'It runs true to our size chart — we recommend ordering by your measurements. — Maison Eden', date: '2026-03-12', byMerchant: true },
    { id: `${productId}-q2`, asker: 'Grace', body: 'Can the train length be customized?', answer: 'Yes, custom train length is available at checkout under Custom Size. — Maison Eden', date: '2026-02-28', byMerchant: true },
    { id: `${productId}-q3`, asker: 'Lily', body: 'Is the lining included or sold separately?', answer: 'All gowns are fully lined as standard. — Maison Eden', date: '2026-02-05', byMerchant: true },
  ]
}

function makeProduct(bp, idx) {
  const [category, silhouette, fabric, neckline, price, original, tags, colorIds, galleryCount] = bp
  const name = NAMES_F[idx % NAMES_F.length]
  const slug = slugify(`${name} ${silhouette} ${fabric} ${CATEGORIES[category].id}`) + `-${1000 + idx}`
  const gallery = takeImages(galleryCount)
  const reviewCount = 18 + ((idx * 37) % 260)
  const rating = [4.6, 4.7, 4.8, 4.9, 5.0][idx % 5]
  const length = category === 'evening' || category === 'guest' ? ['Floor', 'Tea', 'Mini'] : ['Floor', 'Chapel Train']
  const id = `p-${category}-${idx}`
  return {
    id,
    slug,
    name: `${name}`,
    fullName: `Maison Eden ${name}`,
    category,
    silhouette,
    neckline,
    fabric,
    fabrics: [fabric, ...(idx % 2 ? ['Lace'] : ['Satin'])],
    lengths: length,
    sleeve: SLEEVES[idx % SLEEVES.length],
    features: [FEATURES[idx % FEATURES.length], FEATURES[(idx + 2) % FEATURES.length]],
    occasions: [OCCASIONS[idx % OCCASIONS.length], OCCASIONS[(idx + 3) % OCCASIONS.length]],
    basePrice: price,
    originalPrice: original || null,
    colorIds,
    colorCount: colorIds.length,
    tags: tags || [],
    shipsNow: (tags || []).includes('ships-now'),
    gallery,
    thumb: gallery[0],
    hoverImg: gallery[1] || gallery[0],
    rating,
    reviewCount,
    questionCount: 3 + (idx % 10),
    isCustomizable: true,
    description:
      `The ${name} is a ${silhouette.toLowerCase()} silhouette cut from sumptuous ${fabric.toLowerCase()}, ` +
      `finished with a ${neckline.toLowerCase()} neckline. Each gown is made to order in our atelier and hand-finished by a single seamstress, ` +
      `then pressed and inspected before its journey to you.`,
    fabricCare:
      `Primary fabric: ${fabric}. Fully lined. Dry clean only. Steam on low to release travel creases. ` +
      `Store flat or padded-hanger in a breathable garment bag away from direct light.`,
    modelNote: 'Model is 5\'9" and wears a US 4.',
    reviews: buildReviews(id, reviewCount, gallery),
    questions: buildQuestions(id),
  }
}

export const PRODUCTS = BLUEPRINTS.map(makeProduct)

// Curated CLEAN tiles (some scraped squares carried promo text/off-brand imagery).
const CLEAN_TILES = [3, 4, 5, 6, 7, 8, 9, 11, 13, 14, 15, 17, 22, 28, 32, 34, 35, 37].map((i) => TILE_IMAGES[i] || TILE_IMAGES[0])

// Accessories defined separately (use clean tile imagery)
export const ACCESSORIES = ACCESSORY_BLUEPRINTS.map((bp, i) => {
  const [name, sub, price, original, tags] = bp
  const img = CLEAN_TILES[i % CLEAN_TILES.length]
  const id = `p-acc-${i}`
  return {
    id,
    slug: slugify(name) + `-${2000 + i}`,
    name,
    fullName: `Maison Eden ${name}`,
    category: 'accessories',
    subcategory: sub,
    silhouette: '—',
    neckline: '—',
    fabric: sub,
    fabrics: [sub],
    lengths: [],
    colorIds: ['diamond-white', 'ivory', 'champagne'],
    colorCount: 3,
    tags: tags || [],
    shipsNow: (tags || []).includes('ships-now'),
    basePrice: price,
    originalPrice: original || null,
    gallery: [img, CLEAN_TILES[(i + 9) % CLEAN_TILES.length]],
    thumb: img,
    hoverImg: CLEAN_TILES[(i + 9) % CLEAN_TILES.length],
    rating: [4.7, 4.8, 4.9][i % 3],
    reviewCount: 12 + ((i * 19) % 120),
    questionCount: 2 + (i % 5),
    isCustomizable: false,
    description: `The ${name} is a hand-finished accessory crafted to complete your Maison Eden look.`,
    fabricCare: 'Handle with care. Store in the provided pouch away from direct light.',
    modelNote: '',
    reviews: buildReviews(id, 24, [img]),
    questions: buildQuestions(id),
  }
})

export const ALL_PRODUCTS = [...PRODUCTS, ...ACCESSORIES]

/* ----------------------------- helper functions ---------------------------- */

export function getProductBySlug(slug) {
  return ALL_PRODUCTS.find((p) => p.slug === slug) || null
}
export function getProductById(id) {
  return ALL_PRODUCTS.find((p) => p.id === id) || null
}
export function getByCategory(cat) {
  if (cat === 'special-occasions') return ALL_PRODUCTS.filter((p) => ['mother', 'guest', 'flowergirl'].includes(p.category))
  return ALL_PRODUCTS.filter((p) => p.category === cat)
}
export function searchProducts(q) {
  if (!q) return []
  const s = q.toLowerCase()
  return ALL_PRODUCTS.filter(
    (p) =>
      p.name.toLowerCase().includes(s) ||
      p.category.toLowerCase().includes(s) ||
      p.silhouette.toLowerCase().includes(s) ||
      p.fabric.toLowerCase().includes(s) ||
      (p.colorIds || []).some((c) => c.includes(s)),
  )
}
export function getRelated(product, n = 6) {
  return ALL_PRODUCTS.filter((p) => p.category === product.category && p.id !== product.id).slice(0, n)
}
export function getOftenBoughtWith(product, n = 4) {
  if (product.category === 'accessories') return PRODUCTS.slice(0, n)
  return ACCESSORIES.slice(0, n)
}
export function sortProducts(list, sort) {
  const arr = [...list]
  switch (sort) {
    case 'priceAsc': return arr.sort((a, b) => a.basePrice - b.basePrice)
    case 'priceDesc': return arr.sort((a, b) => b.basePrice - a.basePrice)
    case 'newest': return arr.sort((a, b) => (b.tags.includes('new') ? 1 : 0) - (a.tags.includes('new') ? 1 : 0))
    case 'bestSelling': return arr.sort((a, b) => b.reviewCount - a.reviewCount)
    default: return arr
  }
}
export function filterProducts(list, f) {
  return list.filter((p) => {
    if (f.colors?.length && !f.colors.some((c) => p.colorIds.includes(c))) {
      const fam = COLORS.filter((c) => f.colors.includes(c.family)).map((c) => c.id)
      if (!fam.some((c) => p.colorIds.includes(c)) && !f.colors.some((c) => p.colorIds.includes(c))) return false
    }
    if (f.fabrics?.length && !f.fabrics.includes(p.fabric)) return false
    if (f.silhouettes?.length && !f.silhouettes.includes(p.silhouette)) return false
    if (f.necklines?.length && !f.necklines.includes(p.neckline)) return false
    if (f.shipsNow && !p.shipsNow) return false
    if (f.priceMin != null && p.basePrice < f.priceMin) return false
    if (f.priceMax != null && p.basePrice > f.priceMax) return false
    return true
  })
}

/* ------------------------------ home collections --------------------------- */

export const COLLECTIONS = [
  { id: 'new', title: 'New Arrivals', sub: 'The latest from the atelier', img: PRODUCT_IMAGES[2], route: '/wedding-dresses?sort=newest' },
  { id: 'bridal', title: 'Bridal Couture', sub: 'Made-to-order wedding gowns', img: PRODUCT_IMAGES[10], route: '/wedding-dresses' },
  { id: 'evening', title: 'Evening & Cocktail', sub: 'For every soirée', img: PRODUCT_IMAGES[66], route: '/evening-dresses' },
  { id: 'lwd', title: 'Little White Dresses', sub: 'Rehearsal to reception', img: PRODUCT_IMAGES[18], route: '/wedding-dresses' },
]

export const HOME_CATEGORY_TILES = [
  { label: 'Wedding', route: '/wedding-dresses', img: PRODUCT_IMAGES[3] },
  { label: 'Evening', route: '/evening-dresses', img: PRODUCT_IMAGES[41] },
  { label: 'Bridesmaids', route: '/bridesmaid-dresses', img: PRODUCT_IMAGES[42] },
  { label: 'Mother', route: '/special-occasions', img: PRODUCT_IMAGES[78] },
  { label: 'Guest', route: '/special-occasions', img: PRODUCT_IMAGES[84] },
  { label: 'Accessories', route: '/accessories', img: PRODUCT_IMAGES[9] },
]

export const PROMO_CODES = {
  WELCOME10: { code: 'WELCOME10', type: 'percent', value: 10, label: '10% off your first order' },
  VIP20: { code: 'VIP20', type: 'percent', value: 20, label: '20% VIP discount' },
  EDEN50: { code: 'EDEN50', type: 'fixed', value: 50, label: '$50 off' },
}

export const SHIPPING_METHODS = [
  { id: 'standard', label: 'Standard Atelier', detail: 'Made to order · 3–4 weeks', feeUSD: 0 },
  { id: 'express', label: 'Express', detail: 'Made to order · 2–3 weeks', feeUSD: 15 },
  { id: 'rush', label: 'Rush', detail: 'Priority · 4–8 days', feeUSD: 30 },
]

export const PRODUCTION_TIMES = [
  { id: 'standard', label: 'Standard', detail: '3–4 weeks', feeUSD: 0 },
  { id: 'express', label: 'Express', detail: '2–3 weeks', feeUSD: 15 },
  { id: 'rush', label: 'Rush', detail: '4–8 days', feeUSD: 30 },
]

export const STANDARD_SIZES = ['US 0', 'US 2', 'US 4', 'US 6', 'US 8', 'US 10', 'US 12', 'US 14', 'US 16', 'US 18', 'US 20', 'US 22', 'US 24', 'US 26']

// Sample orders for account area
export const SAMPLE_ORDERS = [
  {
    id: 'ME-2026-04812', status: 'shipped', date: '2026-05-02', currency: 'USD', total: 920,
    items: [{ name: 'Maison Eden Florentina', color: 'Diamond White', size: 'US 4', qty: 1, price: 920, img: PRODUCT_IMAGES[1] }],
    estimatedDelivery: '2026-05-30',
  },
  {
    id: 'ME-2026-04655', status: 'inProduction', date: '2026-04-20', currency: 'USD', total: 318,
    items: [{ name: 'Maison Eden Lucia', color: 'Cabernet', size: 'Custom', qty: 1, price: 420, img: PRODUCT_IMAGES[66] }],
    estimatedDelivery: '2026-06-10',
  },
  {
    id: 'ME-2026-04120', status: 'delivered', date: '2026-02-11', currency: 'USD', total: 447,
    items: [
      { name: 'Maison Eden Anouk', color: 'Dusty Blue', size: 'US 6', qty: 3, price: 149, img: PRODUCT_IMAGES[40] },
    ],
    estimatedDelivery: '2026-03-05',
  },
]

export const SAMPLE_ADDRESSES = [
  { id: 'a1', label: 'home', firstName: 'Isabella', lastName: 'Moreau', street1: '124 Greenwich Street', street2: 'Apt 9B', city: 'New York', state: 'NY', postalCode: '10006', country: 'United States', phone: '+1 212 555 0148', isDefault: true },
  { id: 'a2', label: 'office', firstName: 'Isabella', lastName: 'Moreau', street1: '450 W 15th Street', street2: '', city: 'New York', state: 'NY', postalCode: '10011', country: 'United States', phone: '+1 212 555 0192', isDefault: false },
]
