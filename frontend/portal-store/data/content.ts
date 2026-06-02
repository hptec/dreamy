import type { RealWedding, BlogPost, WeddingGuide } from './types'

const C = '/competitor-refs'

export const realWeddings: RealWedding[] = [
  {
    id: 'rw-1',
    slug: 'coastal-emma-james',
    couple: 'Emma & James',
    location: 'Big Sur, California',
    theme: 'Beach',
    date: 'June 2025',
    cover: `${C}/davidsbridal/wedding-dress-04.jpg`,
    gallery: [`${C}/davidsbridal/wedding-dress-04.jpg`, `${C}/kissprom/wedding-aline-tulle-01.jpg`, `${C}/birdygrey/bridesmaid-pink-bryten-02.jpg`],
    excerpt: 'A cliffside ceremony above the Pacific, with sage bridesmaids and an airy tulle gown that danced in the ocean breeze.',
    story: [
      'When Emma and James decided to marry on the cliffs of Big Sur, they wanted everything to feel light, natural, and unforced — like the fog rolling in off the water.',
      'Emma chose the Aurelia A-Line Tulle Gown for its movement. "I wanted something that would float when the wind picked up, and it did exactly that," she said.',
      'Her bridesmaids wore the Meadow dress in Sage and Dusty Blue, echoing the eucalyptus and ocean tones of the coastline.'
    ],
    shopTheLook: ['p-aurelia', 'p-meadow', 'a-cathedral-veil']
  },
  {
    id: 'rw-2',
    slug: 'garden-sofia-marco',
    couple: 'Sofia & Marco',
    location: 'Sonoma Valley, California',
    theme: 'Vineyard',
    date: 'September 2025',
    cover: `${C}/kissprom/prom-champagne-lace-05.jpg`,
    gallery: [`${C}/kissprom/prom-champagne-lace-05.jpg`, `${C}/kissprom/wedding-aline-lace-02.jpg`, `${C}/davidsbridal/bridesmaid-coral-03.jpg`],
    excerpt: 'Golden hour among the grapevines, with terracotta and coral tones that glowed against the autumn light.',
    story: [
      'Sofia and Marco said their vows beneath an oak tree in the heart of Sonoma wine country, surrounded by rows of golden vines.',
      'The Celeste Lace Gown was a natural choice — its floral appliqué mirrored the botanical details woven throughout the celebration.',
      'Warm coral and petal bridesmaid dresses caught the last of the September sun, creating a palette that felt straight out of a painting.'
    ],
    shopTheLook: ['p-celeste', 'p-petal', 'a-drop-earrings']
  },
  {
    id: 'rw-3',
    slug: 'forest-ava-noah',
    couple: 'Ava & Noah',
    location: 'Redwood Forest, Oregon',
    theme: 'Forest',
    date: 'May 2025',
    cover: `${C}/birdygrey/bridesmaid-pink-bryten-02.jpg`,
    gallery: [`${C}/birdygrey/bridesmaid-pink-bryten-02.jpg`, `${C}/kissprom/wedding-aline-longsleeve-06.jpg`, `${C}/davidsbridal/bridesmaid-sage-01.jpg`],
    excerpt: 'An intimate woodland ceremony among towering redwoods, with long-sleeve chiffon and forest-green details.',
    story: [
      'Ava and Noah wanted their forest wedding to feel like a fairytale, and the towering redwoods of Oregon delivered.',
      'The Willow Long-Sleeve Gown brought the romance, with sheer chiffon sleeves that felt perfectly suited to the cool morning air.',
      'Their bridesmaids in sage and olive blended beautifully with the mossy forest floor.'
    ],
    shopTheLook: ['p-willow', 'p-meadow', 'a-hair-vine']
  }
]

export const blogPosts: BlogPost[] = [
  {
    id: 'b-1',
    slug: 'outdoor-wedding-guide',
    title: 'How to Choose the Perfect Outdoor Wedding Dress',
    category: 'Planning',
    author: 'Dreamy Editorial',
    date: 'April 12, 2026',
    readMinutes: 6,
    cover: `${C}/davidsbridal/wedding-dress-04.jpg`,
    excerpt: 'From beach breezes to forest floors, here is how to pick a gown that works with your venue, not against it.',
    body: [
      'An outdoor wedding is a celebration of nature — but it also comes with practical considerations your dress needs to handle gracefully.',
      'For beach ceremonies, opt for lightweight fabrics like chiffon and tulle that move with the breeze. Skip heavy satin trains that drag through sand.',
      'Garden and vineyard weddings call for shoes that wont sink into grass — block heels or embellished flats are your best friend.',
      'Forest weddings tend to be cooler, so consider long sleeves or a beautiful wrap. Sheer illusion sleeves add romance without the weight.',
      'Whatever your venue, choose a silhouette that lets you move, dance, and breathe. Your wedding day should feel as effortless as it looks.'
    ]
  },
  {
    id: 'b-2',
    slug: 'bridesmaid-color-palettes',
    title: '8 Outdoor Bridesmaid Color Palettes for 2026',
    category: 'Inspiration',
    author: 'Dreamy Editorial',
    date: 'March 28, 2026',
    readMinutes: 5,
    cover: `${C}/birdygrey/bridesmaid-pink-bryten-02.jpg`,
    excerpt: 'Sage, dusty blue, terracotta and more — the palettes defining outdoor weddings this season.',
    body: [
      'The right bridesmaid palette ties your entire celebration together. For 2026, earthy and muted tones continue to dominate outdoor weddings.',
      'Sage green remains the reigning favorite — it photographs beautifully against greenery and pairs with nearly any floral.',
      'For beachfront affairs, dusty blue evokes the ocean without feeling nautical.',
      'Vineyard and autumn weddings shine with terracotta, coral, and champagne — warm tones that glow at golden hour.',
      'Mix two complementary shades for depth, or let each bridesmaid choose her own within a curated family.'
    ]
  },
  {
    id: 'b-3',
    slug: 'fabric-guide',
    title: 'A Bride’s Guide to Wedding Dress Fabrics',
    category: 'Education',
    author: 'Dreamy Editorial',
    date: 'March 10, 2026',
    readMinutes: 7,
    cover: `${C}/kissprom/wedding-mermaid-chiffon-03.jpg`,
    excerpt: 'Tulle, satin, chiffon, lace — understand what each fabric does before you fall in love.',
    body: [
      'Understanding fabric is the secret to choosing a dress that matches your vision and your venue.',
      'Tulle is light, airy, and romantic — ideal for ballgowns and A-lines with volume.',
      'Chiffon flows and drapes, making it perfect for outdoor and destination weddings.',
      'Satin has a luxurious sheen and structure, great for sculpted silhouettes like the mermaid.',
      'Lace adds texture and timeless romance, whether as an overlay or delicate appliqué.'
    ]
  }
]

export const weddingGuides: WeddingGuide[] = [
  { id: 'g-1', phase: 'Phase 1', timeframe: '12+ months out', title: 'Dream & Discover', description: 'Set your vision, budget, and date.', tasks: ['Define your wedding vibe & venue type', 'Set your dress budget', 'Start a moodboard', 'Book a Color Palette consultation'] },
  { id: 'g-2', phase: 'Phase 2', timeframe: '9–12 months out', title: 'Find Your Gown', description: 'The fun part — finding the one.', tasks: ['Browse silhouettes by venue', 'Order fabric swatches', 'Try styles at home', 'Place your gown order (allow custom time)'] },
  { id: 'g-3', phase: 'Phase 3', timeframe: '6–9 months out', title: 'Style Your Party', description: 'Dress your bridesmaids and family.', tasks: ['Choose your bridesmaid palette', 'Share the group link with your party', 'Order mother-of-the-bride dress', 'Select flower girl looks'] },
  { id: 'g-4', phase: 'Phase 4', timeframe: '3–6 months out', title: 'Accessorize', description: 'Complete every look.', tasks: ['Choose your veil & headpiece', 'Pick wedding shoes', 'Add jewelry & finishing touches', 'Plan a second reception look'] },
  { id: 'g-5', phase: 'Phase 5', timeframe: '1–3 months out', title: 'Final Fittings', description: 'Perfect the fit.', tasks: ['Schedule alterations', 'Break in your shoes', 'Final accessory check', 'Confirm delivery dates'] }
]

export function getRealWedding(slug: string) {
  return realWeddings.find((w) => w.slug === slug)
}
export function getBlogPost(slug: string) {
  return blogPosts.find((b) => b.slug === slug)
}
